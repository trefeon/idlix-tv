package com.idlix.tv.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.MovieItem
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesUiState(
    val isLoading: Boolean = false,
    val heroSeries: MovieItem? = null,
    val popularSeries: List<MovieItem> = emptyList(),
    val latestSeries: List<MovieItem> = emptyList(),
    val dramaSeries: List<MovieItem> = emptyList(),
    val actionSeries: List<MovieItem> = emptyList(),
    val comedySeries: List<MovieItem> = emptyList(),
    val error: String? = null
)

class SeriesViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesUiState(isLoading = false))
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    init {
        loadSeriesData()
    }

    fun loadSeriesData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val popular = repository.getPopularSeries().map { it.toMovieItem() }
                val latest = repository.getSeriesBySort("latest").map { it.toMovieItem() }
                val drama = repository.getSeriesByGenre("drama").map { it.toMovieItem() }
                val action = repository.getSeriesByGenre("action").map { it.toMovieItem() }
                val comedy = repository.getSeriesByGenre("comedy").map { it.toMovieItem() }

                if (popular.isEmpty() && latest.isEmpty() && drama.isEmpty() &&
                    action.isEmpty() && comedy.isEmpty()
                ) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Gagal memuat serial TV. Silakan periksa koneksi internet."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    heroSeries = popular.firstOrNull() ?: latest.firstOrNull(),
                    popularSeries = popular,
                    latestSeries = latest,
                    dramaSeries = drama,
                    actionSeries = action,
                    comedySeries = comedy,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to load series"
                )
            }
        }
    }

    fun setHeroMovie(movie: MovieItem) {
        _uiState.value = _uiState.value.copy(heroSeries = movie)
    }
}
