package com.idlix.tv.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.model.EpisodeItem
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.Season
import com.idlix.data.model.SeriesDetail
import com.idlix.data.repository.IdlixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = false,
    val movie: MovieDetail? = null,
    val series: SeriesDetail? = null,
    val selectedSeason: Season? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null
)

class DetailViewModel(
    private val repository: IdlixRepository = IdlixRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState(isLoading = false))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadDetail(contentType: String, slug: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)
            try {
                if (contentType == "tv_series" || contentType == "series") {
                    val series = repository.getSeriesDetail(slug)
                    if (series != null) {
                        val defaultSeason = series.defaultSeason
                        val initialSeason = series.seasons.firstOrNull { it.seasonNumber == defaultSeason?.seasonNumber }
                            ?: defaultSeason?.let { Season(id = it.id, seasonNumber = it.seasonNumber, name = it.name) }
                            ?: series.seasons.firstOrNull()
                            ?: Season(seasonNumber = 1, name = "Season 1")
                        val initialEpisodes = defaultSeason?.episodes ?: emptyList()
                        _uiState.value = DetailUiState(
                            isLoading = false,
                            series = series,
                            selectedSeason = initialSeason,
                            episodes = initialEpisodes
                        )
                    } else {
                        _uiState.value = DetailUiState(
                            isLoading = false,
                            error = null,
                            errorRes = com.idlix.tv.R.string.error_series_detail
                        )
                    }
                } else {
                    val movie = repository.getMovieDetail(slug)
                    if (movie != null) {
                        _uiState.value = DetailUiState(
                            isLoading = false,
                            movie = movie
                        )
                    } else {
                        _uiState.value = DetailUiState(
                            isLoading = false,
                            error = null,
                            errorRes = com.idlix.tv.R.string.error_movie_detail
                        )
                    }
                }
            } catch (e: Exception) {
                val fallbackRes = if (contentType == "tv_series" || contentType == "series") {
                    com.idlix.tv.R.string.error_series_detail
                } else {
                    com.idlix.tv.R.string.error_movie_detail
                }
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = e.localizedMessage,
                    errorRes = fallbackRes
                )
            }
        }
    }

    fun selectSeason(seriesSlug: String, season: Season) {
        if (_uiState.value.selectedSeason?.seasonNumber == season.seasonNumber &&
            _uiState.value.episodes.isNotEmpty()
        ) {
            return
        }
        viewModelScope.launch {
            val series = _uiState.value.series
            val defaultSeason = series?.defaultSeason
            if (defaultSeason != null && defaultSeason.seasonNumber == season.seasonNumber &&
                defaultSeason.episodes.isNotEmpty()
            ) {
                _uiState.value = _uiState.value.copy(
                    selectedSeason = season,
                    episodes = defaultSeason.episodes,
                    isLoadingEpisodes = false
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                selectedSeason = season,
                isLoadingEpisodes = true
            )
            try {
                val epRes = repository.getEpisodeDetail(seriesSlug, season.seasonNumber, 1)
                val newEpisodes = epRes?.season?.episodes ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    episodes = newEpisodes.ifEmpty { _uiState.value.episodes },
                    isLoadingEpisodes = false
                )
            } catch (e: Exception) {
                // Keep current episodes; surface nothing louder than the spinner stopping.
                _uiState.value = _uiState.value.copy(isLoadingEpisodes = false)
            }
        }
    }
}
