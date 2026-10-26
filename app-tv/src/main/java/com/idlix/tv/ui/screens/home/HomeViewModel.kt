package com.idlix.tv.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.MovieItem
import com.idlix.data.model.SeriesItem
import com.idlix.data.model.WatchHistoryItem
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
            val unfinished = historyStore.getUnfinishedItems()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                heroMovie = null,
                continueWatching = unfinished,
                trending = emptyList(),
                popularMovies = emptyList(),
                popularSeries = emptyList(),
                actionMovies = emptyList(),
                error = null
            )
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
