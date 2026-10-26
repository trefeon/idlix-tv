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

    // Mirrors mobile PrewarmResolve: start the unlock countdown while the user
    // reads details, so the shared in-flight job is done (or nearly) by play
    // press. One key per title; the repository itself also dupe-guards.
    private var lastPrewarmedKey: String? = null

    private fun maybePrewarmStream(contentType: String, contentId: String, contentTitle: String) {
        if (contentId.isBlank()) return
        val key = "$contentType:$contentId"
        if (lastPrewarmedKey == key) return
        lastPrewarmedKey = key
        repository.prewarmStream(contentType, contentId, contentTitle)
    }

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
                        val prewarmEp = initialEpisodes.firstOrNull()
                        if (prewarmEp != null) {
                            maybePrewarmStream("episode", prewarmEp.id, series.title)
                        } else {
                            maybePrewarmStream("tv_series", slug, series.title)
                        }
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
                        maybePrewarmStream("movie", movie.id, movie.title)
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
                defaultSeason.episodes.firstOrNull()?.let { ep ->
                    series?.title?.let { title -> maybePrewarmStream("episode", ep.id, title) }
                }
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                selectedSeason = season,
                isLoadingEpisodes = true
            )
            try {
                val epRes = repository.getEpisodeDetail(seriesSlug, season.seasonNumber, 1)
                val newEpisodes = epRes?.season?.episodes ?: emptyList()
                // Show exactly what the new season returned (even when empty):
                // falling back to the old list would display the wrong season.
                _uiState.value = _uiState.value.copy(
                    episodes = newEpisodes,
                    isLoadingEpisodes = false
                )
                newEpisodes.firstOrNull()?.let { ep ->
                    series?.title?.let { title -> maybePrewarmStream("episode", ep.id, title) }
                }
            } catch (e: Exception) {
                // Keep current episodes; surface nothing louder than the spinner stopping.
                _uiState.value = _uiState.value.copy(isLoadingEpisodes = false)
            }
        }
    }
}
