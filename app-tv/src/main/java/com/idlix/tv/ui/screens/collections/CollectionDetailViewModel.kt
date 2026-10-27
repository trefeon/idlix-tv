package com.idlix.tv.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.CollectionSection
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CollectionDetailUiState(
    val isLoading: Boolean = false,
    val sections: List<CollectionSection> = emptyList(),
    val error: String? = null,
    val errorRes: Int? = null
)

class CollectionDetailViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionDetailUiState(isLoading = false))
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    fun loadCollection(slug: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, errorRes = null)
            try {
                val detail = repository.getCollectionDetail(slug)
                val sections = detail?.sections?.filter { it.items.isNotEmpty() } ?: emptyList()
                if (sections.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        errorRes = com.idlix.tv.R.string.error_collection_detail_load
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sections = sections,
                    error = null,
                    errorRes = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage,
                    errorRes = com.idlix.tv.R.string.error_collection_detail_load
                )
            }
        }
    }
}
