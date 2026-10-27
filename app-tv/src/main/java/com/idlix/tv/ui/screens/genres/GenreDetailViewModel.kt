package com.idlix.tv.ui.screens.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.MovieItem
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GenreDetailUiState(
    val isLoading: Boolean = false,
    val movies: List<MovieItem> = emptyList(),
    val series: List<MovieItem> = emptyList(),
    val error: String? = null,
    val errorRes: Int? = null
)

class GenreDetailViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreDetailUiState(isLoading = false))
    val uiState: StateFlow<GenreDetailUiState> = _uiState.asStateFlow()

    fun loadGenre(genreSlug: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, errorRes = null)
            try {
                val movies = repository.getMoviesByGenre(genreSlug)
                val series = repository.getSeriesByGenre(genreSlug).map { it.toMovieItem() }
                if (movies.isEmpty() && series.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        errorRes = com.idlix.tv.R.string.error_genre_detail_load
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    movies = movies,
                    series = series,
                    error = null,
                    errorRes = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage,
                    errorRes = com.idlix.tv.R.string.error_genre_detail_load
                )
            }
        }
    }
}
