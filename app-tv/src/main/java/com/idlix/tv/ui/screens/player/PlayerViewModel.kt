package com.idlix.tv.ui.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.EpisodeItem
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.Season
import com.idlix.data.model.SubdlSubtitle
import com.idlix.data.model.SubtitleTrack
import com.idlix.data.repository.IdlixRepository
import com.idlix.data.repository.SubdlRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NextEpisodeInfo(
    val episode: EpisodeItem,
    val seasonNumber: Int,
    val seriesTitle: String,
    val seriesSlug: String,
    val posterUrl: String?,
    val backdropUrl: String?
)

data class PlayerUiState(
    val isLoading: Boolean = false,
    val countdownSeconds: Int = 0,
    val resolvedStream: ResolvedPlayback? = null,
    val selectedSubtitle: SubtitleTrack? = null,
    val isSubtitleEnabled: Boolean = true,
    val initialPositionMs: Long = 0L,
    val error: String? = null,
    val isSubtitlePickerVisible: Boolean = false,
    val subdlSubtitles: List<SubdlSubtitle> = emptyList(),
    val isSearchingSubdl: Boolean = false,
    val nextEpisode: NextEpisodeInfo? = null,
    val isNextEpisodeDismissed: Boolean = false
)

class PlayerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = IdlixRepository()
    private val historyStore = WatchHistoryStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(PlayerUiState(isLoading = false))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var resolveNextEpisodeJob: Job? = null

    private var currentContentId: String = ""
    private var currentContentType: String = ""
    private var currentContentTitle: String = ""
    private var currentPosterUrl: String? = null
    private var currentBackdropUrl: String? = null
    private var currentSlug: String? = null
    private var currentSeasonNumber: Int? = null
    private var currentEpisodeNumber: Int? = null

    fun loadStream(
        contentType: String,
        contentId: String,
        title: String,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        slug: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        // Cancel in-flight playback and resolve jobs to prevent race conditions.
        playbackJob?.cancel()
        resolveNextEpisodeJob?.cancel()

        // The error-state Retry button re-calls with contentType/contentId/title only;
        // preserve previously stored navigation info for the same content.
        val isNewContent = contentId != currentContentId
        currentContentId = contentId
        currentContentType = contentType
        currentContentTitle = title
        if (isNewContent) {
            currentPosterUrl = posterUrl
            currentBackdropUrl = backdropUrl
            currentSlug = slug
            currentSeasonNumber = seasonNumber
            currentEpisodeNumber = episodeNumber
        } else {
            if (posterUrl != null) currentPosterUrl = posterUrl
            if (backdropUrl != null) currentBackdropUrl = backdropUrl
            if (slug != null) currentSlug = slug
            if (seasonNumber != null) currentSeasonNumber = seasonNumber
            if (episodeNumber != null) currentEpisodeNumber = episodeNumber
        }

        val savedPos = historyStore.getSavedPosition(contentId)

        // Immediately record in history so it tracks the title and images
        historyStore.recordStartPlayback(
            contentId = contentId,
            contentType = contentType,
            title = title,
            posterUrl = currentPosterUrl,
            backdropUrl = currentBackdropUrl,
            slug = currentSlug,
            seasonNumber = currentSeasonNumber,
            episodeNumber = currentEpisodeNumber
        )

        playbackJob = viewModelScope.launch {
            _uiState.value = PlayerUiState(
                isLoading = true,
                countdownSeconds = 0,
                initialPositionMs = savedPos,
                nextEpisode = null,
                isNextEpisodeDismissed = false
            )

            // Asynchronously resolve next episode for Series
            val slugForNext = currentSlug
            if (contentType == "episode" && !slugForNext.isNullOrBlank()) {
                resolveNextEpisodeJob = resolveNextEpisode(
                    seriesSlug = slugForNext,
                    currentEpisodeId = contentId,
                    seasonNumber = currentSeasonNumber,
                    episodeNumber = currentEpisodeNumber,
                    fallbackTitle = title,
                    posterUrl = currentPosterUrl,
                    backdropUrl = currentBackdropUrl
                )
            }

            val result = repository.resolveStream(
                contentType = contentType,
                contentId = contentId,
                contentTitle = title,
                onCountdown = { sec ->
                    _uiState.value = _uiState.value.copy(countdownSeconds = sec)
                }
            )

            result.onSuccess { resolved ->
                val defaultSub = resolved.subtitles.firstOrNull { it.lang == "id" }
                    ?: resolved.subtitles.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resolvedStream = resolved,
                    selectedSubtitle = defaultSub,
                    isSubtitleEnabled = defaultSub != null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = err.localizedMessage
                        ?: getApplication<Application>().getString(com.idlix.tv.R.string.failed_to_play)
                )
            }
        }
    }

    private fun resolveNextEpisode(
        seriesSlug: String,
        currentEpisodeId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
        fallbackTitle: String,
        posterUrl: String?,
        backdropUrl: String?
    ): Job {
        return viewModelScope.launch {
            try {
                val series = repository.getSeriesDetail(seriesSlug) ?: return@launch
                val defaultSeason = series.defaultSeason
                val seriesTitle = series.title.ifBlank { fallbackTitle.substringBefore(" -") }
                val rawSeasons = series.seasons.ifEmpty {
                    if (defaultSeason != null) {
                        listOf(Season(seasonNumber = defaultSeason.seasonNumber, name = defaultSeason.name))
                    } else emptyList()
                }
                val allSeasons = rawSeasons.sortedBy { it.seasonNumber }

                var currentSeasonNum = seasonNumber
                var currentEpNum = episodeNumber
                var seasonEpisodes: List<EpisodeItem> = emptyList()

                // Strategy 1: Check if currentEpisodeId is in defaultSeason
                if (defaultSeason?.episodes?.any { it.id == currentEpisodeId } == true) {
                    currentSeasonNum = defaultSeason.seasonNumber
                    seasonEpisodes = defaultSeason.episodes
                } else if (currentSeasonNum != null && defaultSeason?.seasonNumber == currentSeasonNum && !defaultSeason.episodes.isNullOrEmpty()) {
                    seasonEpisodes = defaultSeason.episodes
                } else if (currentSeasonNum != null) {
                    val epDetail = try {
                        repository.getEpisodeDetail(seriesSlug, currentSeasonNum, 1)
                    } catch (_: Exception) {
                        null
                    }
                    seasonEpisodes = epDetail?.season?.episodes ?: emptyList()
                }

                // Strategy 2: If seasonEpisodes is still empty or currentEpisodeId not found, scan all seasons
                if (seasonEpisodes.isEmpty() || (currentEpisodeId.isNotBlank() && seasonEpisodes.none { it.id == currentEpisodeId } && currentEpNum == null)) {
                    for (s in allSeasons) {
                        if (s.seasonNumber == defaultSeason?.seasonNumber && !defaultSeason.episodes.isNullOrEmpty()) {
                            if (defaultSeason.episodes.any { it.id == currentEpisodeId }) {
                                currentSeasonNum = s.seasonNumber
                                seasonEpisodes = defaultSeason.episodes
                                break
                            }
                        } else {
                            val epDetail = try {
                                repository.getEpisodeDetail(seriesSlug, s.seasonNumber, 1)
                            } catch (_: Exception) {
                                null
                            }
                            val eps = epDetail?.season?.episodes ?: emptyList()
                            if (eps.any { it.id == currentEpisodeId }) {
                                currentSeasonNum = s.seasonNumber
                                seasonEpisodes = eps
                                break
                            }
                        }
                    }
                }

                if (seasonEpisodes.isEmpty()) return@launch

                // Find index of current episode in this season
                var currentIndex = seasonEpisodes.indexOfFirst { it.id == currentEpisodeId }
                if (currentIndex == -1 && currentEpNum != null) {
                    currentIndex = seasonEpisodes.indexOfFirst { it.episodeNumber == currentEpNum }
                }

                if (currentIndex != -1 && currentEpNum == null) {
                    currentEpNum = seasonEpisodes[currentIndex].episodeNumber
                }

                if (currentIndex != -1 && currentIndex + 1 < seasonEpisodes.size) {
                    // Next episode is in the same season!
                    val nextEp = seasonEpisodes[currentIndex + 1]
                    _uiState.value = _uiState.value.copy(
                        nextEpisode = NextEpisodeInfo(
                            episode = nextEp,
                            seasonNumber = currentSeasonNum ?: 1,
                            seriesTitle = seriesTitle,
                            seriesSlug = seriesSlug,
                            posterUrl = nextEp.fullStillUrl.ifBlank { posterUrl },
                            backdropUrl = backdropUrl ?: series.fullBackdropUrl
                        )
                    )
                } else if (currentSeasonNum != null) {
                    // Check if there is a next season
                    val nextSeason = allSeasons.firstOrNull { it.seasonNumber > currentSeasonNum }
                    if (nextSeason != null) {
                        val nextSeasonDetail = try {
                            repository.getEpisodeDetail(seriesSlug, nextSeason.seasonNumber, 1)
                        } catch (_: Exception) {
                            null
                        }
                        val nextEp = nextSeasonDetail?.season?.episodes?.firstOrNull()
                            ?: nextSeasonDetail?.episode
                        if (nextEp != null) {
                            _uiState.value = _uiState.value.copy(
                                nextEpisode = NextEpisodeInfo(
                                    episode = nextEp,
                                    seasonNumber = nextSeason.seasonNumber,
                                    seriesTitle = seriesTitle,
                                    seriesSlug = seriesSlug,
                                    posterUrl = nextEp.fullStillUrl.ifBlank { posterUrl },
                                    backdropUrl = backdropUrl ?: series.fullBackdropUrl
                                )
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(nextEpisode = null)
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(nextEpisode = null)
                    }
                }
            } catch (_: Exception) {
                // Next-episode resolution is best-effort; never fail playback for it.
            }
        }
    }

    fun dismissNextEpisode() {
        _uiState.value = _uiState.value.copy(isNextEpisodeDismissed = true)
    }

    fun playNextEpisode(
        onReadyToPlay: ((NextEpisodeInfo) -> Unit)? = null
    ) {
        val next = _uiState.value.nextEpisode ?: return
        onReadyToPlay?.invoke(next)
    }

    fun openSubtitlePicker() {
        _uiState.value = _uiState.value.copy(isSubtitlePickerVisible = true)
        if (_uiState.value.subdlSubtitles.isEmpty()) {
            searchSubdlSubtitles()
        }
    }

    fun closeSubtitlePicker() {
        _uiState.value = _uiState.value.copy(isSubtitlePickerVisible = false)
    }

    fun searchSubdlSubtitles() {
        val title = currentContentTitle
        if (title.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingSubdl = true)
            val results = SubdlRepository.searchAllSubtitles(
                title = title,
                type = if (currentContentType == "episode") "tv" else "movie",
                season = currentSeasonNumber,
                episode = currentEpisodeNumber
            )
            _uiState.value = _uiState.value.copy(
                isSearchingSubdl = false,
                subdlSubtitles = results
            )
        }
    }

    fun selectSubtitleTrack(track: SubtitleTrack?) {
        _uiState.value = _uiState.value.copy(
            selectedSubtitle = track,
            isSubtitleEnabled = track != null,
            isSubtitlePickerVisible = false
        )
    }

    fun selectSubdlSubtitle(
        sub: SubdlSubtitle,
        onSubtitleReady: (SubtitleTrack) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingSubdl = true)
            val downloaded = SubdlRepository.downloadSubtitle(
                sub = sub,
                context = getApplication<Application>().applicationContext
            )
            _uiState.value = _uiState.value.copy(isSearchingSubdl = false)

            if (downloaded != null) {
                val currentStream = _uiState.value.resolvedStream
                if (currentStream != null) {
                    val updatedSubs = currentStream.subtitles.toMutableList()
                    updatedSubs.removeAll { it.path == downloaded.path }
                    updatedSubs.add(0, downloaded)
                    _uiState.value = _uiState.value.copy(
                        resolvedStream = currentStream.copy(subtitles = updatedSubs),
                        selectedSubtitle = downloaded,
                        isSubtitleEnabled = true,
                        isSubtitlePickerVisible = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        selectedSubtitle = downloaded,
                        isSubtitleEnabled = true,
                        isSubtitlePickerVisible = false
                    )
                }
                onSubtitleReady(downloaded)
            }
        }
    }

    fun toggleSubtitle() {
        val current = _uiState.value.isSubtitleEnabled
        _uiState.value = _uiState.value.copy(isSubtitleEnabled = !current)
    }

    fun saveProgress(currentPosMs: Long, durationMs: Long) {
        if (currentContentId.isNotBlank() && durationMs > 0) {
            historyStore.updateProgress(currentContentId, currentPosMs, durationMs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        resolveNextEpisodeJob?.cancel()
    }
}
