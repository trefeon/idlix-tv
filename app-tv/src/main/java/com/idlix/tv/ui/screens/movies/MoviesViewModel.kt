package com.idlix.tv.ui.screens.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.MovieItem
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoviesUiState(
    val isLoading: Boolean = false,
    val heroMovie: MovieItem? = null,
    val popularMovies: List<MovieItem> = emptyList(),
    val latestMovies: List<MovieItem> = emptyList(),
    val actionMovies: List<MovieItem> = emptyList(),
    val comedyMovies: List<MovieItem> = emptyList(),
    val horrorMovies: List<MovieItem> = emptyList(),
    val sciFiMovies: List<MovieItem> = emptyList(),
    val error: String? = null,
    val errorRes: Int? = null
)

class MoviesViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesUiState(isLoading = false))
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    init {
        loadMoviesData()
    }

    fun loadMoviesData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, errorRes = null)
            try {
                val popular = repository.getPopularMovies()
                val latest = repository.getMoviesBySort("latest")
                val action = repository.getMoviesByGenre("action")
                val comedy = repository.getMoviesByGenre("comedy")
                val horror = repository.getMoviesByGenre("horror")
                val sciFi = repository.getMoviesByGenre("science-fiction")

                if (popular.isEmpty() && latest.isEmpty() && action.isEmpty() &&
                    comedy.isEmpty() && horror.isEmpty() && sciFi.isEmpty()
                ) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        errorRes = com.idlix.tv.R.string.error_movies_load
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    heroMovie = popular.firstOrNull() ?: latest.firstOrNull(),
                    popularMovies = popular,
                    latestMovies = latest,
                    actionMovies = action,
                    comedyMovies = comedy,
                    horrorMovies = horror,
                    sciFiMovies = sciFi,
                    error = null,
                    errorRes = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage,
                    errorRes = com.idlix.tv.R.string.error_movies_load
                )
            }
        }
    }

    fun setHeroMovie(movie: MovieItem) {
        _uiState.value = _uiState.value.copy(heroMovie = movie)
    }
}
