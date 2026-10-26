package com.idlix.tv.ui.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.model.EpisodeItem
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.SubdlSubtitle
import com.idlix.data.model.SubtitleTrack
import com.idlix.data.repository.SubdlRepository
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

    private val historyStore = WatchHistoryStore.getInstance(application.applicationContext)

    private val _uiState = MutableStateFlow(PlayerUiState(isLoading = false))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentContentId: String = ""
    private var currentContentType: String = ""
    private var currentContentTitle: String = ""

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
        currentContentId = contentId
        currentContentType = contentType
        currentContentTitle = title

        val savedPos = historyStore.getSavedPosition(contentId)

        // Immediately record in history so it tracks the title and images
        historyStore.recordStartPlayback(
            contentId = contentId,
            contentType = contentType,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            slug = slug,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )

        // Direct source not yet wired: fail-closed empty state, no backend call.
        // History + SubDL subtitle flows below remain fully functional.
        _uiState.value = PlayerUiState(
            isLoading = false,
            initialPositionMs = savedPos,
            nextEpisode = null,
            isNextEpisodeDismissed = false,
            error = "Konten belum tersedia — sumber langsung segera hadir."
        )
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
                type = if (currentContentType == "episode") "tv" else "movie"
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
}
