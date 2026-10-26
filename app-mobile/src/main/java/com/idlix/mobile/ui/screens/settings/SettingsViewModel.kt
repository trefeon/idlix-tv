package com.idlix.mobile.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.repository.SubdlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val subdlKey: String = "",
    val isTestingSubdl: Boolean = false,
    val subdlTestResult: String? = null,
    val cacheCleared: Boolean = false
)

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            subdlKey = SubdlRepository.getApiKey()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateSubdlKey(key: String) {
        _uiState.value = _uiState.value.copy(subdlKey = key, subdlTestResult = null)
    }

    fun saveSubdlKey() {
        SubdlRepository.setApiKey(_uiState.value.subdlKey)
    }

    fun testSubdl() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingSubdl = true, subdlTestResult = null)
            try {
                val result = SubdlRepository.searchAllSubtitles(title = "Avatar", type = "movie")
                _uiState.value = _uiState.value.copy(
                    isTestingSubdl = false,
                    subdlTestResult = "SubDL API key is valid (${result.size} subtitles found)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingSubdl = false,
                    subdlTestResult = "Failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val cacheDir = context.cacheDir.resolve("image_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(cacheCleared = true)
                }
            } catch (_: Exception) {}
        }
    }
}
