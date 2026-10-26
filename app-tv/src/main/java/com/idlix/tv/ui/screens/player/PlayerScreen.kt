package com.idlix.tv.ui.screens.player

import android.graphics.Color
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.input.GamepadKeys
import com.idlix.tv.ui.input.GamepadKeys.PlayerActions
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun letterboxBottomPadding(viewW: Float, viewH: Float, videoW: Float, videoH: Float): Float {
    if (viewW <= 0f || viewH <= 0f || videoW <= 0f || videoH <= 0f) return 0.08f
    val videoAspect = videoW / videoH
    if (videoAspect <= 0f) return 0.08f
    val displayedH = viewW / videoAspect
    val barFraction = ((viewH - displayedH) / 2f / viewH).coerceAtLeast(0f)
    if (barFraction <= 0f) return 0.08f
    return (barFraction + 0.04f).coerceIn(0.08f, 0.50f)
}

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    contentType: String,
    contentId: String,
    title: String,
    posterUrl: String? = null,
    backdropUrl: String? = null,
    slug: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var userInteractionCount by remember { mutableIntStateOf(0) }

    val remainingMs = if (duration > 0L) (duration - currentPos).coerceAtLeast(0L) else Long.MAX_VALUE
    val isNearEnd = duration > 60_000L && remainingMs in 0L..90_000L
    val isNextPromptVisible = isNearEnd && uiState.nextEpisode != null && !uiState.isNextEpisodeDismissed

    val rootFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    var activePlayer: ExoPlayer? by remember { mutableStateOf(null) }
    val qualityReturnFocusRequester = remember { FocusRequester() }
    var isQualityPickerVisible by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(loadPlaybackQuality(context)) }
    // Zoom-fill aspect holder as a stable State object so the PlayerView
    // factory's padding listeners always read the live value (FIT math
    // would lift subtitles too high once Zoom removes the letterbox bars).
    val zoomState = remember { mutableStateOf(loadVideoZoom(context)) }
    var isZoom by zoomState
    var availableHeights by remember { mutableStateOf(emptySet<Int>()) }
    var videoW by remember { mutableIntStateOf(0) }
    var videoH by remember { mutableIntStateOf(0) }
    var bufferedPos by remember { mutableLongStateOf(0L) }
    var qualityDismissReturn by remember { mutableStateOf(false) }
    fun closeQualityPicker() {
        isQualityPickerVisible = false
        qualityDismissReturn = true
    }
    val speedReturnFocusRequester = remember { FocusRequester() }
    var subtitleDismissReturn by remember { mutableStateOf(false) }
    val subtitleReturnFocusRequester = remember { FocusRequester() }
    var isSpeedPickerVisible by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableStateOf(loadPlaybackSpeed(context)) }
    var availableAudioTracks by remember { mutableStateOf(emptyList<TvAudioOption>()) }
    var selectedAudioLang by remember { mutableStateOf(loadAudioLanguage(context)) }
    val tryAgainFocusRequester = remember { FocusRequester() }
    var speedDismissReturn by remember { mutableStateOf(false) }
    fun closeSpeedPicker() {
        isSpeedPickerVisible = false
        speedDismissReturn = true
    }

    LaunchedEffect(contentType, contentId) {
        viewModel.loadStream(
            contentType = contentType,
            contentId = contentId,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            slug = slug,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    // Auto-hide controls after 6 seconds of inactivity, paused during modal, scrubbing, or next episode prompt
    LaunchedEffect(showControls, uiState.isSubtitlePickerVisible, isQualityPickerVisible, isSpeedPickerVisible, isScrubbing, userInteractionCount, isNextPromptVisible) {
        if (uiState.isSubtitlePickerVisible || isQualityPickerVisible || isSpeedPickerVisible || isScrubbing || isNextPromptVisible) {
            // Keep controls open or don't steal focus from prompt / modals
            return@LaunchedEffect
        }
        if (showControls) {
            delay(6000)
            showControls = false
        } else {
            try {
                    if (!isNextPromptVisible && !uiState.isSubtitlePickerVisible && !isQualityPickerVisible) {
                    rootFocusRequester.requestFocus()
                }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(50)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }
    // Return focus to the Quality button after the picker closes. Controls
    // recompose visible in the same frame the modal flag flips, so the request
    // is deferred past recomposition instead of firing inside onDismiss.
    LaunchedEffect(isQualityPickerVisible, qualityDismissReturn) {
        if (!isQualityPickerVisible && qualityDismissReturn) {
            qualityDismissReturn = false
            delay(50)
            try {
                qualityReturnFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Return focus to the Speed button after the picker closes (same deferred
    // pattern as the quality picker).
    LaunchedEffect(isSpeedPickerVisible, speedDismissReturn) {
        if (!isSpeedPickerVisible && speedDismissReturn) {
            speedDismissReturn = false
            delay(50)
            try {
                speedReturnFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Return focus to the Subtitle button after the picker closes (same
    // deferred pattern as the quality/speed pickers — previously focus was
    // left wherever the modal had it, a D-pad dead zone).
    LaunchedEffect(uiState.isSubtitlePickerVisible, subtitleDismissReturn) {
        if (!uiState.isSubtitlePickerVisible && subtitleDismissReturn) {
            subtitleDismissReturn = false
            delay(50)
            try {
                subtitleReturnFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Default focus on Try Again as soon as a playback error renders.
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            delay(50)
            try {
                tryAgainFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val rawCode = keyEvent.nativeKeyEvent.keyCode
                    // Gamepad extras first; unknown codes fall through to the
                    // existing remote/media branches below (remote paths untouched).
                    val gamepadConsumed = GamepadKeys.onGamepadKey(
                        rawCode,
                        true,
                        PlayerActions(
                            onConfirm = {
                                if (isNextPromptVisible) {
                                    false
                                } else if (!showControls) {
                                    showControls = true
                                    true
                                } else false
                            },
                            onTogglePlayPause = {
                                val p = activePlayer
                                if (p == null || isNextPromptVisible) {
                                    false
                                } else {
                                    if (p.isPlaying) p.pause() else p.play()
                                    showControls = true
                                    true
                                }
                            },
                            onSeekForward = {
                                val p = activePlayer
                                if (p == null || isNextPromptVisible || uiState.isSubtitlePickerVisible || isQualityPickerVisible || isSpeedPickerVisible) {
                                    false
                                } else {
                                    // duration is TIME_UNSET (< 0) before the first frame;
                                    // fall back to unbounded so the seek never goes negative.
                                    val dur = p.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                    p.seekTo((p.currentPosition + 10000L).coerceAtMost(dur))
                                    showControls = true
                                    true
                                }
                            },
                            onSeekBackward = {
                                val p = activePlayer
                                if (p == null || isNextPromptVisible || uiState.isSubtitlePickerVisible || isQualityPickerVisible || isSpeedPickerVisible) {
                                    false
                                } else {
                                    p.seekTo((p.currentPosition - 10000L).coerceAtLeast(0L))
                                    showControls = true
                                    true
                                }
                            },
                            onOpenSubtitlePicker = {
                                if (isNextPromptVisible || uiState.isSubtitlePickerVisible || isQualityPickerVisible || isSpeedPickerVisible ||
                                    uiState.resolvedStream == null
                                ) {
                                    false
                                } else {
                                    viewModel.openSubtitlePicker()
                                    userInteractionCount++
                                    true
                                }
                            },
                            onToggleControls = {
                                if (isNextPromptVisible || uiState.isSubtitlePickerVisible || isQualityPickerVisible || isSpeedPickerVisible) {
                                    false
                                } else {
                                    showControls = !showControls
                                    userInteractionCount++
                                    true
                                }
                            }
                        )
                    )
                    if (gamepadConsumed) {
                        true
                    } else when (
                        // Gamepad B mirrors system Back through the exact branch below.
                        if (rawCode == KeyEvent.KEYCODE_BUTTON_B) KeyEvent.KEYCODE_BACK
                        else rawCode
                    ) {
                        KeyEvent.KEYCODE_BACK -> {
                            if (isQualityPickerVisible) {
                                closeQualityPicker()
                                true
                            } else if (isSpeedPickerVisible) {
                                closeSpeedPicker()
                                true
                            } else if (uiState.isSubtitlePickerVisible) {
                                viewModel.closeSubtitlePicker()
                                subtitleDismissReturn = true
                                true
                            } else if (isNextPromptVisible) {
                                viewModel.dismissNextEpisode()
                                showControls = true
                                userInteractionCount++
                                true
                            } else if (isScrubbing) {
                                isScrubbing = false
                                playButtonFocusRequester.requestFocus()
                                true
                            } else if (showControls) {
                                showControls = false
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (isNextPromptVisible) {
                                false
                            } else if (!showControls) {
                                showControls = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            activePlayer?.let { p ->
                                if (p.isPlaying) p.pause() else p.play()
                            }
                            showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isNextPromptVisible) {
                                false
                            } else if (!showControls) {
                                activePlayer?.let { p ->
                                    p.seekTo((p.currentPosition - 10000L).coerceAtLeast(0L))
                                }
                                showControls = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isNextPromptVisible) {
                                false
                            } else if (!showControls) {
                                activePlayer?.let { p ->
                                    val dur = p.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                    p.seekTo((p.currentPosition + 10000L).coerceAtMost(dur))
                                }
                                showControls = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (isNextPromptVisible) {
                                false
                            } else if (!showControls) {
                                showControls = true
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TvLoadingSpinner()
                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.countdownSeconds > 0) {
                    Text(
                        text = stringResource(R.string.loading_secure_stream),
                        color = NetflixWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.starting_in_seconds, uiState.countdownSeconds),
                        color = NetflixLightGray,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.connecting_to_media),
                        color = NetflixLightGray,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.error ?: stringResource(R.string.failed_to_play),
                    color = NetflixWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { viewModel.loadStream(contentType, contentId, title) },
                        colors = ButtonDefaults.colors(
                            containerColor = NetflixRed,
                            contentColor = NetflixWhite,
                            focusedContainerColor = NetflixRed,
                            focusedContentColor = NetflixWhite
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                        border = ButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, NetflixWhite)
                            )
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                        modifier = Modifier.focusRequester(tryAgainFocusRequester)
                    ) {
                        Text(text = stringResource(R.string.try_again), fontSize = 14.sp)
                    }

                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(
                            containerColor = NetflixLightGray.copy(alpha = 0.3f),
                            contentColor = NetflixWhite,
                            focusedContainerColor = NetflixWhite,
                            focusedContentColor = NetflixLightGray
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                        border = ButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, NetflixWhite)
                            )
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                    ) {
                        Text(text = stringResource(R.string.back), fontSize = 14.sp)
                    }
                }
            }
        } else if (uiState.resolvedStream != null) {
            val resolved = uiState.resolvedStream!!

            val tokenQuery = remember(resolved.hlsMasterUrl) {
                try {
                    Uri.parse(resolved.hlsMasterUrl).query.orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }

            val httpDataSourceFactory = remember(tokenQuery) {
                MediaSourceHelper.createHttpDataSourceFactory(tokenQuery)
            }

            val exoPlayer = remember(context, resolved.hlsMasterUrl) {
                val renderersFactory = MediaSourceHelper.createRenderersFactory(context)

                // P0 streaming backlog: deeper buffers to ride out mirror stalls.
                val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        30000,
                        60000,
                        2500,
                        5000
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                // Start ABR at 4 Mbps so the first pick is HD on decent links
                // instead of ramping up from the ~1 Mbps default.
                val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
                    .setInitialBitrateEstimate(4_000_000L)
                    .build()

                ExoPlayer.Builder(context, renderersFactory)
                    .setLoadControl(loadControl)
                    .setBandwidthMeter(bandwidthMeter)
                    .build().apply {
                    val activeSub = resolved.subtitles.firstOrNull { it.lang == "id" }
                        ?: resolved.subtitles.firstOrNull()

                    val finalMediaSource = MediaSourceHelper.buildMediaSource(context, resolved.hlsMasterUrl, activeSub, httpDataSourceFactory)
                    setMediaSource(finalMediaSource)

                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setPreferredTextLanguage("id")
                        .setSelectUndeterminedTextLanguage(true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .apply {
                            loadAudioLanguage(context)?.let { setPreferredAudioLanguage(it) }
                        }
                        .build()

                    // Restore the persisted quality cap live (no player recreation).
                    applyPlaybackQuality(this, loadPlaybackQuality(context))

                    // Restore the persisted playback speed (no rebuffer).
                    applyPlaybackSpeed(this, loadPlaybackSpeed(context))

                    prepare()
                    if (uiState.initialPositionMs > 0) {
                        seekTo(uiState.initialPositionMs)
                    }
                    playWhenReady = true
                }
            }

            fun applySubtitleTrack(track: com.idlix.data.model.SubtitleTrack?) {
                val currentPos = exoPlayer.currentPosition
                val wasPlaying = exoPlayer.isPlaying
                val newSource = MediaSourceHelper.buildMediaSource(context, resolved.hlsMasterUrl, track, httpDataSourceFactory)
                exoPlayer.setMediaSource(newSource, currentPos)
                val selector = exoPlayer.trackSelectionParameters.buildUpon()
                if (track != null) {
                    selector
                        .setPreferredTextLanguage(track.lang)
                        .setSelectUndeterminedTextLanguage(true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                } else {
                    selector.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                }
                exoPlayer.trackSelectionParameters = selector.build()
                if (wasPlaying) {
                    exoPlayer.play()
                }
            }

            LaunchedEffect(exoPlayer) {
                activePlayer = exoPlayer
            }

            // Sync playback position & throttle save progress to avoid disk I/O lag
            LaunchedEffect(exoPlayer) {
                var lastSavedPos = 0L
                while (true) {
                    currentPos = exoPlayer.currentPosition
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                    bufferedPos = exoPlayer.bufferedPosition
                    isPlaying = exoPlayer.isPlaying
                    if (Math.abs(currentPos - lastSavedPos) >= 5000L && duration > 0) {
                        viewModel.saveProgress(currentPos, duration)
                        lastSavedPos = currentPos
                    }
                    delay(500)
                }
            }

            DisposableEffect(exoPlayer) {
                val playerListener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            if (uiState.nextEpisode != null && !uiState.isNextEpisodeDismissed) {
                                viewModel.playNextEpisode()
                            }
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        videoW = videoSize.width
                        videoH = videoSize.height
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        try {
                            availableHeights = tracks.groups
                                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
                                .filter { it.sampleMimeType?.startsWith("video/") == true }
                                .mapNotNull { it.height.takeIf { h -> h > 0 } }
                                .toSet()
                            availableAudioTracks = tracks.groups
                                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
                                .filter { it.sampleMimeType?.startsWith("audio/") == true }
                                .mapNotNull { format ->
                                    val lang = format.language?.takeIf { it.isNotBlank() }
                                        ?: return@mapNotNull null
                                    TvAudioOption(
                                        language = lang,
                                        label = format.label?.takeIf { it.isNotBlank() } ?: lang
                                    )
                                }
                                .distinctBy { it.language }
                        } catch (_: Exception) {}
                    }
                }
                exoPlayer.addListener(playerListener)

                onDispose {
                    try {
                        exoPlayer.removeListener(playerListener)
                        val finalPos = exoPlayer.currentPosition
                        val finalDur = exoPlayer.duration
                        if (finalDur > 0) {
                            viewModel.saveProgress(finalPos, finalDur)
                        }
                    } catch (_: Exception) {}
                    activePlayer = null
                    exoPlayer.release()
                }
            }

            // Video Surface with high-visibility Netflix-style subtitles
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        // Saved aspect applies at build; FIT default never crops.
                        applyVideoZoom(this, zoomState.value)
                        subtitleView?.let { subs ->
                            subs.setApplyEmbeddedStyles(false)
                            subs.setStyle(
                                CaptionStyleCompat(
                                    Color.WHITE,
                                    Color.TRANSPARENT,
                                    Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    Color.BLACK,
                                    null
                                )
                            )
                            subs.setFractionalTextSize(0.053f) // Standard Netflix TV subtitle proportion
                            subs.setBottomPaddingFraction(0.08f)
                            fun refreshPadding(videoSize: VideoSize = exoPlayer.videoSize) {
                                // Zoom fills the frame (no letterbox bars), so
                                // subtitles fall back to the 0.08 default;
                                // FIT keeps the letterbox-derived lift.
                                subs.setBottomPaddingFraction(
                                    if (zoomState.value) 0.08f
                                    else letterboxBottomPadding(
                                        width.toFloat(),
                                        height.toFloat(),
                                        videoSize.width.toFloat(),
                                        videoSize.height.toFloat()
                                    )
                                )
                            }
                            val subtitleSizeListener = object : Player.Listener {
                                override fun onVideoSizeChanged(videoSize: VideoSize) {
                                    refreshPadding(videoSize)
                                }
                            }
                            exoPlayer.addListener(subtitleSizeListener)
                            // Stashed for onRelease so the listener is removed with the view.
                            setTag(subtitleSizeListener)
                            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshPadding() }
                        }
                    }
                },
                // Live aspect switch (modal row): padding refreshes on every
                // update so zoom/FIT changes apply even when resizeMode matches.
                update = { view ->
                    applyVideoZoom(view, isZoom)
                    view.subtitleView?.setBottomPaddingFraction(
                        if (isZoom) 0.08f
                        else letterboxBottomPadding(
                            view.width.toFloat(),
                            view.height.toFloat(),
                            exoPlayer.videoSize.width.toFloat(),
                            exoPlayer.videoSize.height.toFloat()
                        )
                    )
                },
                onRelease = { view ->
                    try {
                        (view.getTag() as? Player.Listener)?.let { exoPlayer.removeListener(it) }
                    } catch (_: Exception) {}
                },
            )

            // D-Pad Player Controls
            TvPlayerControls(
                title = resolved.title,
                isVisible = showControls && !uiState.isSubtitlePickerVisible && !isQualityPickerVisible && !isSpeedPickerVisible,
                isPlaying = isPlaying,
                currentPosMs = currentPos,
                durationMs = duration,
                isSubtitleEnabled = uiState.isSubtitleEnabled,
                selectedSubtitleLabel = uiState.selectedSubtitle?.label,
                playButtonModifier = Modifier.focusRequester(playButtonFocusRequester),
                onPlayPauseClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                    showControls = true
                    userInteractionCount++
                },
                onSeekBackward = {
                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000L).coerceAtLeast(0L))
                    showControls = true
                    userInteractionCount++
                },
                onSeekForward = {
                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration))
                    showControls = true
                    userInteractionCount++
                },
                onSeekTo = { targetMs ->
                    exoPlayer.seekTo(targetMs)
                    showControls = true
                    userInteractionCount++
                },
                onScrubbingChanged = { scrubbing ->
                    isScrubbing = scrubbing
                },
                onUserInteraction = {
                    userInteractionCount++
                },
                onToggleSubtitle = {
                    val willBeEnabled = !uiState.isSubtitleEnabled
                    viewModel.toggleSubtitle()
                    val trackSelector = exoPlayer.trackSelectionParameters.buildUpon()
                    if (willBeEnabled) {
                        trackSelector
                            .setPreferredTextLanguage(uiState.selectedSubtitle?.lang ?: "id")
                            .setSelectUndeterminedTextLanguage(true)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    } else {
                        trackSelector.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    }
                    exoPlayer.trackSelectionParameters = trackSelector.build()
                    showControls = true
                    userInteractionCount++
                },
                onOpenSubtitlePicker = {
                    viewModel.openSubtitlePicker()
                    userInteractionCount++
                },
                subtitleButtonModifier = Modifier.focusRequester(subtitleReturnFocusRequester),
                bufferedPosMs = bufferedPos,
                videoWidth = videoW,
                videoHeight = videoH,
                qualityLabel = stringResource(selectedQuality.titleRes),
                onOpenQualityPicker = {
                    isQualityPickerVisible = true
                    userInteractionCount++
                },
                qualityButtonModifier = Modifier.focusRequester(qualityReturnFocusRequester),
                speedLabel = selectedSpeed.label,
                onOpenSpeedPicker = {
                    isSpeedPickerVisible = true
                    userInteractionCount++
                },
                speedButtonModifier = Modifier.focusRequester(speedReturnFocusRequester),
            )

            // Subtitle Picker Modal (IDLIX & SubDL.com third party)
            TvSubtitlePickerModal(
                isVisible = uiState.isSubtitlePickerVisible,
                availableSubtitles = resolved.subtitles,
                selectedSubtitle = uiState.selectedSubtitle,
                isSubtitleEnabled = uiState.isSubtitleEnabled,
                subdlSubtitles = uiState.subdlSubtitles,
                isSearchingSubdl = uiState.isSearchingSubdl,
                onSelectTrack = { track ->
                    viewModel.selectSubtitleTrack(track)
                    applySubtitleTrack(track)
                    subtitleDismissReturn = true
                },
                onSelectSubdl = { subdl ->
                    viewModel.selectSubdlSubtitle(subdl) { newTrack ->
                        applySubtitleTrack(newTrack)
                    }
                    subtitleDismissReturn = true
                },
                onSearchSubdlClick = {
                    viewModel.searchSubdlSubtitles()
                },
                onDismiss = {
                    viewModel.closeSubtitlePicker()
                    subtitleDismissReturn = true
                },
                availableAudioTracks = availableAudioTracks,
                selectedAudioLanguage = selectedAudioLang,
                onSelectAudio = { language ->
                    selectedAudioLang = language
                    saveAudioLanguage(context, language)
                    val selector = exoPlayer.trackSelectionParameters.buildUpon()
                        .setPreferredAudioLanguage(language)
                    exoPlayer.trackSelectionParameters = selector.build()
                    userInteractionCount++
                }
            )
            // Quality Picker Modal (live cap, no player recreation)
            TvQualityPickerModal(
                isVisible = isQualityPickerVisible,
                currentQuality = selectedQuality,
                availableHeights = availableHeights,
                isZoom = isZoom,
                onSelectQuality = { quality ->
                    selectedQuality = quality
                    savePlaybackQuality(context, quality)
                    try {
                        applyPlaybackQuality(exoPlayer, quality)
                    } catch (_: Exception) {}
                    // Brief beat so the checkmark on the selected row is
                    // visible before the modal vanishes (D-pad confirmation).
                    scope.launch {
                        delay(350)
                        closeQualityPicker()
                    }
                },
                // Aspect toggle stays in the modal (D-pad can flip back);
                // the AndroidView update block applies it live.
                onSelectAspect = { zoom ->
                    isZoom = zoom
                    saveVideoZoom(context, zoom)
                    userInteractionCount++
                },
                onDismiss = {
                    closeQualityPicker()
                }
            )
            // Speed Picker Modal (live rate, no player rebuild)
            TvSpeedPickerModal(
                isVisible = isSpeedPickerVisible,
                currentSpeed = selectedSpeed,
                onSelectSpeed = { speed ->
                    selectedSpeed = speed
                    savePlaybackSpeed(context, speed)
                    try {
                        applyPlaybackSpeed(exoPlayer, speed)
                    } catch (_: Exception) {}
                    // Brief beat so the checkmark on the selected row is
                    // visible before the modal vanishes (D-pad confirmation).
                    scope.launch {
                        delay(350)
                        closeSpeedPicker()
                    }
                },
                onDismiss = {
                    closeSpeedPicker()
                }
            )
            // Next Episode Prompt for TV Series (When <= 90s remaining)
            if (uiState.nextEpisode != null) {
                // remainingMs is Long.MAX_VALUE while duration is unknown; clamp
                // before toInt() so the countdown text never wraps negative.
                val remainingSec = ((remainingMs / 1000).coerceAtMost(Int.MAX_VALUE.toLong())).toInt().coerceAtLeast(0)
                TvNextEpisodePrompt(
                    nextEpisode = uiState.nextEpisode!!,
                    remainingSeconds = remainingSec,
                    isVisible = isNextPromptVisible && !uiState.isSubtitlePickerVisible && !isQualityPickerVisible && !isSpeedPickerVisible,
                    onPlayNext = {
                        viewModel.playNextEpisode()
                    },
                    onDismiss = {
                        viewModel.dismissNextEpisode()
                        // Deterministic landing: controls reappear with
                        // Play focused instead of a focus-less frame.
                        showControls = true
                        userInteractionCount++
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 48.dp,
                            bottom = if (showControls) 115.dp else 40.dp
                        )
                )
            }
        }
    }
}
