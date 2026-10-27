package com.idlix.tv.ui.screens.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.Genre
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GenresUiState(
    val isLoading: Boolean = false,
    val genres: List<Genre> = emptyList(),
    val error: String? = null,
    val errorRes: Int? = null
)

class GenresViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenresUiState(isLoading = false))
    val uiState: StateFlow<GenresUiState> = _uiState.asStateFlow()

    init {
        loadGenres()
    }

    fun loadGenres() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, errorRes = null)
            try {
                val genres = repository.getGenres().data
                if (genres.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        errorRes = com.idlix.tv.R.string.error_genres_load
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    genres = genres,
                    error = null,
                    errorRes = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage,
                    errorRes = com.idlix.tv.R.string.error_genres_load
                )
            }
        }
    }
}
