package com.idlix.tv.ui.screens.movies

import androidx.lifecycle.ViewModel
import com.idlix.data.model.MovieItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MoviesUiState(
    val isLoading: Boolean = false,
    val heroMovie: MovieItem? = null,
    val popularMovies: List<MovieItem> = emptyList(),
    val latestMovies: List<MovieItem> = emptyList(),
    val actionMovies: List<MovieItem> = emptyList(),
    val comedyMovies: List<MovieItem> = emptyList(),
    val horrorMovies: List<MovieItem> = emptyList(),
    val sciFiMovies: List<MovieItem> = emptyList(),
    val error: String? = null
)

class MoviesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesUiState(isLoading = false))
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    init {
        loadMoviesData()
    }

    fun loadMoviesData() {
        _uiState.value = MoviesUiState(
            isLoading = false,
            heroMovie = null,
            popularMovies = emptyList(),
            latestMovies = emptyList(),
            actionMovies = emptyList(),
            comedyMovies = emptyList(),
            horrorMovies = emptyList(),
            sciFiMovies = emptyList(),
            error = null
        )
    }

    fun setHeroMovie(movie: MovieItem) {
        _uiState.value = _uiState.value.copy(heroMovie = movie)
    }
}
