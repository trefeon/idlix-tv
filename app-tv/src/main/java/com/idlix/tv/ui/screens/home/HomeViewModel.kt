package com.idlix.tv.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.MovieItem
import com.idlix.data.model.SeriesItem
import com.idlix.data.model.WatchHistoryItem
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val heroMovie: MovieItem? = null,
    val continueWatching: List<WatchHistoryItem> = emptyList(),
    val trending: List<MovieItem> = emptyList(),
    val popularMovies: List<MovieItem> = emptyList(),
    val popularSeries: List<SeriesItem> = emptyList(),
    val actionMovies: List<MovieItem> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = IdlixRepository()
    private val historyStore = WatchHistoryStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = false))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Collect unfinished watch history items reactively
        viewModelScope.launch {
            historyStore.historyFlow.collect { allItems ->
                val unfinished = allItems.filter { it.isUnfinished }
                _uiState.value = _uiState.value.copy(continueWatching = unfinished)
            }
        }
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                var trending = repository.getTrendingMovies()
                var popular = repository.getPopularMovies()
                var series = repository.getPopularSeries()

                // If all returned empty, wait 1s and retry once (network socket warmup on startup)
                if (trending.isEmpty() && popular.isEmpty() && series.isEmpty()) {
                    kotlinx.coroutines.delay(1000)
                    trending = repository.getTrendingMovies()
                    popular = repository.getPopularMovies()
                    series = repository.getPopularSeries()
                }

                if (trending.isEmpty() && popular.isEmpty() && series.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Gagal memuat rekomendasi film & serial. Silakan periksa koneksi internet."
                    )
                    return@launch
                }

                val action = repository.getMoviesByGenre("action")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    heroMovie = trending.firstOrNull() ?: popular.firstOrNull(),
                    trending = trending,
                    popularMovies = popular,
                    popularSeries = series,
                    actionMovies = action,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to load data"
                )
            }
        }
    }

    fun refreshContinueWatching() {
        val unfinished = historyStore.getUnfinishedItems()
        _uiState.value = _uiState.value.copy(continueWatching = unfinished)
    }

    fun removeFromHistory(contentId: String) {
        historyStore.removeItem(contentId)
        refreshContinueWatching()
    }

    fun setHeroMovie(movie: MovieItem) {
        _uiState.value = _uiState.value.copy(heroMovie = movie)
    }
}
