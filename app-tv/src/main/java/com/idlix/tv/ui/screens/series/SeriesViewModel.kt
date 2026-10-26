package com.idlix.tv.ui.screens.series

import androidx.lifecycle.ViewModel
import com.idlix.data.model.MovieItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class SeriesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesUiState(isLoading = false))
    val uiState: StateFlow<SeriesUiState> = _uiState.asStateFlow()

    init {
        loadSeriesData()
    }

    fun loadSeriesData() {
        _uiState.value = SeriesUiState(
            isLoading = false,
            heroSeries = null,
            popularSeries = emptyList(),
            latestSeries = emptyList(),
            dramaSeries = emptyList(),
            actionSeries = emptyList(),
            comedySeries = emptyList(),
            error = null
        )
    }

    fun setHeroMovie(movie: MovieItem) {
        _uiState.value = _uiState.value.copy(heroSeries = movie)
    }
}
