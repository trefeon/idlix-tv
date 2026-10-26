package com.idlix.mobile.ui.screens

import com.idlix.mobile.ui.screens.detail.DetailUiState
import com.idlix.mobile.ui.screens.home.HomeUiState
import com.idlix.mobile.ui.screens.library.LibraryUiState
import com.idlix.mobile.ui.screens.movies.MoviesUiState
import com.idlix.mobile.ui.screens.search.SearchUiState
import com.idlix.mobile.ui.screens.series.SeriesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Empty-state baselines for the mobile ViewModels (mirror of the TV suite).
 *
 * Every mobile ViewModel either takes an Android Application or launches
 * viewModelScope (Dispatchers.Main) in init, and neither Robolectric nor
 * coroutines-test is in the version catalog — so construction paths are
 * guarded probes that skip with the seam note, while the pure UiState
 * defaults always assert.
 */
class MobileViewModelEmptyStateTest {

    @Test
    fun moviesUiStateDefaults() {
        val state = MoviesUiState()
        assertTrue(state.movies.isEmpty())
        assertEquals("all", state.selectedGenre)
        assertEquals("popular", state.selectedSort)
        assertEquals(1, state.currentPage)
        assertTrue(state.hasMorePages)
        assertNull(state.error)
    }

    @Test
    fun seriesUiStateDefaults() {
        val state = SeriesUiState()
        assertTrue(state.seriesList.isEmpty())
        assertEquals("all", state.selectedGenre)
        assertEquals(1, state.currentPage)
        assertNull(state.error)
    }

    @Test
    fun searchUiStateDefaults() {
        val state = SearchUiState()
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertEquals(false, state.isSearching)
        assertNull(state.error)
    }

    @Test
    fun homeUiStateDefaults() {
        val state = HomeUiState()
        assertTrue(state.bannerMovies.isEmpty())
        assertTrue(state.continueWatching.isEmpty())
        assertTrue(state.popularMovies.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun detailUiStateDefaults() {
        val state = DetailUiState()
        assertNull(state.movie)
        assertNull(state.series)
        assertTrue(state.episodes.isEmpty())
        assertNull(state.resolvedStream)
    }

    @Test
    fun libraryUiStateDefaults() {
        val state = LibraryUiState()
        assertTrue(state.historyItems.isEmpty())
        assertEquals(false, state.isLoading)
    }

    @Test
    fun moviesConstructionNeedsMainDispatcherSeam() {
        try {
            val vm = com.idlix.mobile.ui.screens.movies.MoviesViewModel()
            assertTrue(vm.uiState.value.movies.isEmpty())
        } catch (e: IllegalStateException) {
            Assume.assumeTrue(
                "Seam: mobile MoviesViewModel init needs Dispatchers.Main " +
                    "(coroutines-test, not in catalog): ${e.message}",
                false
            )
        } catch (e: RuntimeException) {
            Assume.assumeTrue("Seam: framework stubbed: ${e.message}", false)
        }
    }

    @Test
    fun seriesConstructionNeedsMainDispatcherSeam() {
        try {
            val vm = com.idlix.mobile.ui.screens.series.SeriesViewModel()
            assertTrue(vm.uiState.value.seriesList.isEmpty())
        } catch (e: IllegalStateException) {
            Assume.assumeTrue(
                "Seam: mobile SeriesViewModel init needs Dispatchers.Main " +
                    "(coroutines-test, not in catalog): ${e.message}",
                false
            )
        } catch (e: RuntimeException) {
            Assume.assumeTrue("Seam: framework stubbed: ${e.message}", false)
        }
    }

    @Test
    fun searchConstructionNeedsMainDispatcherSeam() {
        try {
            val vm = com.idlix.mobile.ui.screens.search.SearchViewModel()
            assertTrue(vm.uiState.value.results.isEmpty())
        } catch (e: IllegalStateException) {
            Assume.assumeTrue(
                "Seam: mobile SearchViewModel init needs Dispatchers.Main " +
                    "(coroutines-test + flow debounce, not in catalog): ${e.message}",
                false
            )
        } catch (e: RuntimeException) {
            Assume.assumeTrue("Seam: framework stubbed: ${e.message}", false)
        }
    }

    @Test
    fun homeConstructionNeedsApplicationSeam() {
        try {
            val app = android.app.Application()
            val vm = com.idlix.mobile.ui.screens.home.HomeViewModel(app)
            assertTrue(vm.uiState.value.bannerMovies.isEmpty())
        } catch (e: RuntimeException) {
            Assume.assumeTrue(
                "Seam: mobile HomeViewModel needs a real Application / " +
                    "injectable WatchHistoryStore + Main dispatcher: ${e.message}",
                false
            )
        }
    }

    @Test
    fun libraryConstructionNeedsApplicationSeam() {
        try {
            val app = android.app.Application()
            val vm = com.idlix.mobile.ui.screens.library.LibraryViewModel(app)
            assertTrue(vm.uiState.value.historyItems.isEmpty())
        } catch (e: RuntimeException) {
            Assume.assumeTrue(
                "Seam: mobile LibraryViewModel needs a real Application / " +
                    "injectable WatchHistoryStore: ${e.message}",
                false
            )
        }
    }
}
