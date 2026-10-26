package com.idlix.tv.ui.screens.detail

import androidx.lifecycle.ViewModel
import com.idlix.data.model.EpisodeItem
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.Season
import com.idlix.data.model.SeriesDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetailUiState(
    val isLoading: Boolean = false,
    val movie: MovieDetail? = null,
    val series: SeriesDetail? = null,
    val selectedSeason: Season? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val error: String? = null
)

class DetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState(isLoading = false))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadDetail(contentType: String, slug: String) {
        // Direct source not yet wired: empty-state safe, no backend call.
        _uiState.value = DetailUiState(
            isLoading = false,
            movie = null,
            series = null,
            selectedSeason = null,
            episodes = emptyList()
        )
    }

    fun selectSeason(seriesSlug: String, season: Season) {
        if (_uiState.value.selectedSeason?.id == season.id) {
            return
        }
        // No episode fetch without direct source: keep current episodes, just switch selection.
        _uiState.value = _uiState.value.copy(
            selectedSeason = season,
            isLoadingEpisodes = false
        )
    }
}
