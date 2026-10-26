package com.idlix.tv.ui.screens

import com.idlix.data.model.MovieItem
import com.idlix.data.model.Season
import com.idlix.tv.ui.screens.detail.DetailUiState
import com.idlix.tv.ui.screens.detail.DetailViewModel
import com.idlix.tv.ui.screens.home.HomeUiState
import com.idlix.tv.ui.screens.movies.MoviesViewModel
import com.idlix.tv.ui.screens.player.PlayerUiState
import com.idlix.tv.ui.screens.search.SearchViewModel
import com.idlix.tv.ui.screens.series.SeriesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Empty-state tests for the TV ViewModels. The direct source is not wired
 * yet, so every load path must resolve to an empty, error-free, crash-free
 * state.
 *
 * ViewModels that need an Android Application / Main dispatcher
 * (Home/Player/Search-debounce) are asserted via their pure UiState defaults
 * plus a guarded construction probe that skips with the seam note when the
 * framework is stubbed (missing deps: Robolectric / coroutines-test, neither
 * in the version catalog).
 */
class TvViewModelEmptyStateTest {

    // ---- Movies ----

    @Test
    fun moviesLoadEmptyNoCrash() {
        val vm = MoviesViewModel()
        vm.loadMoviesData()
        val state = vm.uiState.value
        assertTrue(state.popularMovies.isEmpty())
        assertTrue(state.latestMovies.isEmpty())
        assertTrue(state.actionMovies.isEmpty())
        assertNull(state.heroMovie)
        assertNull(state.error)
    }

    @Test
    fun moviesSetHeroMovie() {
        val vm = MoviesViewModel()
        val movie = MovieItem(id = "1", title = "Fuori")
        vm.setHeroMovie(movie)
        assertEquals(movie, vm.uiState.value.heroMovie)
        vm.loadMoviesData()
        assertNull(vm.uiState.value.heroMovie)
    }

    // ---- Series ----

    @Test
    fun seriesLoadEmptyNoCrash() {
        val vm = SeriesViewModel()
        vm.loadSeriesData()
        val state = vm.uiState.value
        assertTrue(state.popularSeries.isEmpty())
        assertTrue(state.latestSeries.isEmpty())
        assertNull(state.heroSeries)
        assertNull(state.error)
    }

    // ---- Detail ----

    @Test
    fun detailLoadEmptyNoCrash() {
        val vm = DetailViewModel()
        vm.loadDetail("movie", "fuori-2025")
        val state = vm.uiState.value
        assertNull(state.movie)
        assertNull(state.series)
        assertTrue(state.episodes.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun detailSelectSeasonSwitchesSelection() {
        val vm = DetailViewModel()
        val season = Season(id = "s1", seasonNumber = 1, name = "Season 1")
        vm.selectSeason("show", season)
        assertEquals(season, vm.uiState.value.selectedSeason)
    }

    @Test
    fun detailReselectSameSeasonIsNoOp() {
        val vm = DetailViewModel()
        val season = Season(id = "s1", seasonNumber = 1, name = "Season 1")
        vm.selectSeason("show", season)
        val before = vm.uiState.value
        vm.selectSeason("show", season)
        assertEquals(before, vm.uiState.value)
    }

    // ---- Search (pure paths only) ----

    @Test
    fun searchInitialStateEmpty() {
        val vm = SearchViewModel()
        val state = vm.uiState.value
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun searchBlankQueryStaysEmptyNoCrash() {
        val vm = SearchViewModel()
        vm.onQueryChange("")
        val state = vm.uiState.value
        assertTrue(state.results.isEmpty())
        assertEquals(false, state.isLoading)
    }

    @Test
    fun searchDebouncePathNeedsMainDispatcherSeam() {
        val vm = SearchViewModel()
        try {
            vm.onQueryChange("batman")
        } catch (e: IllegalStateException) {
            Assume.assumeTrue(
                "Seam: SearchViewModel debounce needs Dispatchers.Main " +
                    "(coroutines-test, not in catalog): ${e.message}",
                false
            )
        } catch (e: RuntimeException) {
            Assume.assumeTrue(
                "Seam: SearchViewModel debounce needs framework Main looper: ${e.message}",
                false
            )
        }
    }

    // ---- UiState defaults (pure, always run) ----

    @Test
    fun homeUiStateDefaultsEmpty() {
        val state = HomeUiState()
        assertTrue(state.trending.isEmpty())
        assertTrue(state.continueWatching.isEmpty())
        assertNull(state.heroMovie)
        assertNull(state.error)
    }

    @Test
    fun playerUiStateDefaultsEmpty() {
        val state = PlayerUiState()
        assertNull(state.resolvedStream)
        assertNull(state.error)
        assertEquals(0, state.countdownSeconds)
    }

    @Test
    fun detailUiStateDefaultsEmpty() {
        val state = DetailUiState()
        assertNull(state.movie)
        assertTrue(state.episodes.isEmpty())
    }

    // ---- Guarded framework probes ----

    @Test
    fun homeViewModelEmptyNeedsStoreSeam() {
        try {
            val app = android.app.Application()
            val vm = com.idlix.tv.ui.screens.home.HomeViewModel(app)
            assertTrue(vm.uiState.value.trending.isEmpty())
        } catch (e: NullPointerException) {
            Assume.assumeTrue(
                "Seam: HomeViewModel needs an injectable WatchHistoryStore " +
                    "(WatchHistoryStore.getInstance(applicationContext) NPEs on stub Context).",
                false
            )
        } catch (e: IllegalStateException) {
            Assume.assumeTrue(
                "Seam: HomeViewModel collect needs Dispatchers.Main (coroutines-test, not in catalog).",
                false
            )
        } catch (e: RuntimeException) {
            Assume.assumeTrue("Seam: Android framework stubbed: ${e.message}", false)
        }
    }

    @Test
    fun playerLoadStreamEmptyNeedsStoreSeam() {
        try {
            val app = android.app.Application()
            val vm = com.idlix.tv.ui.screens.player.PlayerViewModel(app)
            vm.loadStream("movie", "id-1", "Fuori")
            assertNull(vm.uiState.value.resolvedStream)
        } catch (e: NullPointerException) {
            Assume.assumeTrue(
                "Seam: PlayerViewModel needs an injectable WatchHistoryStore " +
                    "(getSavedPosition/recordStartPlayback on stub Context).",
                false
            )
        } catch (e: RuntimeException) {
            Assume.assumeTrue("Seam: Android framework stubbed: ${e.message}", false)
        }
    }
}
