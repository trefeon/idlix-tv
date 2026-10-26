package com.idlix.tv.ui.screens.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerControls(
    title: String,
    isVisible: Boolean,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    isSubtitleEnabled: Boolean,
    selectedSubtitleLabel: String? = null,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleSubtitle: () -> Unit,
    onOpenSubtitlePicker: () -> Unit,
    subtitleButtonModifier: Modifier = Modifier,
    onScrubbingChanged: ((Boolean) -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    playButtonModifier: Modifier = Modifier,
    bufferedPosMs: Long = 0L,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    qualityLabel: String = "Auto",
    onOpenQualityPicker: () -> Unit = {},
    qualityButtonModifier: Modifier = Modifier,
    speedLabel: String = "1×",
    onOpenSpeedPicker: () -> Unit = {},
    speedButtonModifier: Modifier = Modifier,
) {
    val subtitleFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val internalPlayFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }

    var isSeekFocused by remember { mutableStateOf(false) }
    var scrubPosMs by remember { mutableLongStateOf(currentPosMs) }
    var lastScrubTime by remember { mutableLongStateOf(0L) }
    var scrubStreak by remember { mutableIntStateOf(0) }
    var justCommittedTarget by remember { mutableStateOf<String?>(null) }

    // Sync with real playback position when not actively scrubbing
    LaunchedEffect(currentPosMs, isSeekFocused) {
        if (!isSeekFocused) {
            scrubPosMs = currentPosMs
        }
    }

    LaunchedEffect(justCommittedTarget) {
        if (justCommittedTarget != null) {
            delay(1200)
            justCommittedTarget = null
        }
    }

    fun calculateStep(): Long {
        val now = System.currentTimeMillis()
        if (now - lastScrubTime < 450) {
            scrubStreak++
        } else {
            scrubStreak = 0
        }
        lastScrubTime = now

        return when {
            scrubStreak >= 15 -> 300_000L // 5 menit
            scrubStreak >= 8  -> 120_000L // 2 menit
            scrubStreak >= 3  -> 60_000L  // 1 menit
            durationMs > 1_800_000L -> 30_000L // 30 detik untuk tayangan panjang
            else -> 10_000L // 10 detik
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF2000000),
                            Color(0x29000000),
                            Color(0x33000000),
                            Color(0xF2000000)
                        )
                    )
                )
        ) {
            // Top Bar: Netflix mark + title + HD badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(NetflixRed, RoundedCornerShape(2.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "N",
                            color = NetflixWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = NetflixWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Driven by the live video size when known; falls back to the
                // historic static "HD" until the first frame reports dimensions.
                val badgeText = when {
                    videoHeight >= 2160 || videoWidth >= 3840 -> "4K"
                    videoHeight >= 1080 || videoWidth >= 1920 -> "FHD"
                    videoHeight >= 720 || videoWidth >= 1280 -> "HD"
                    videoWidth > 0 && videoHeight > 0 -> "SD"
                    else -> "HD"
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .border(BorderStroke(1.dp, NetflixLightGray), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = NetflixWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 10s Button
                Button(
                    onClick = {
                        onSeekBackward()
                        onUserInteraction?.invoke()
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixDarkCard.copy(alpha = 0.85f),
                        focusedContainerColor = NetflixRed,
                        contentColor = NetflixWhite,
                        focusedContentColor = NetflixWhite
                    ),
                    shape = ButtonDefaults.shape(shape = CircleShape),
                    scale = ButtonDefaults.scale(focusedScale = 1.12f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .focusProperties { down = seekBarFocusRequester }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = stringResource(R.string.rewind_10s),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Play / Pause Button
                Button(
                    onClick = {
                        onPlayPauseClick()
                        onUserInteraction?.invoke()
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixWhite,
                        contentColor = Color.Black,
                        focusedContainerColor = NetflixRed,
                        focusedContentColor = NetflixWhite
                    ),
                    shape = ButtonDefaults.shape(shape = CircleShape),
                    scale = ButtonDefaults.scale(focusedScale = 1.12f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = playButtonModifier
                        .size(72.dp)
                        .focusRequester(internalPlayFocusRequester)
                        .focusProperties { down = seekBarFocusRequester }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            modifier = Modifier
                                .size(36.dp)
                                .then(if (!isPlaying) Modifier.offset(x = 2.dp) else Modifier)
                        )
                    }
                }

                // Forward 10s Button
                Button(
                    onClick = {
                        onSeekForward()
                        onUserInteraction?.invoke()
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixDarkCard.copy(alpha = 0.85f),
                        focusedContainerColor = NetflixRed,
                        contentColor = NetflixWhite,
                        focusedContentColor = NetflixWhite
                    ),
                    shape = ButtonDefaults.shape(shape = CircleShape),
                    scale = ButtonDefaults.scale(focusedScale = 1.12f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .focusProperties { down = seekBarFocusRequester }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = stringResource(R.string.forward_10s),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Bottom Bar: Interactive Seeker Bar & Subtitle toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 48.dp, vertical = 24.dp)
            ) {
                val targetPos = if (isSeekFocused) scrubPosMs else currentPosMs
                val progress = if (durationMs > 0) (targetPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                val bufferedProgress = if (durationMs > 0) (bufferedPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                val animatedBarHeight by animateDpAsState(
                    targetValue = if (isSeekFocused) 10.dp else 6.dp,
                    label = "barHeight"
                )

                // Interactive Seeker Bar with D-Pad navigation
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(seekBarFocusRequester)
                        .focusProperties {
                            up = internalPlayFocusRequester
                            down = speedFocusRequester
                        }
                        .onFocusChanged { state ->
                            val wasFocused = isSeekFocused
                            isSeekFocused = state.isFocused
                            if (state.isFocused) {
                                scrubPosMs = currentPosMs
                                scrubStreak = 0
                                onScrubbingChanged?.invoke(true)
                            } else if (wasFocused) {
                                onScrubbingChanged?.invoke(false)
                            }
                        }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (durationMs <= 0L) return@onPreviewKeyEvent false
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        val step = calculateStep()
                                        scrubPosMs = (scrubPosMs - step).coerceAtLeast(0L)
                                        onUserInteraction?.invoke()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        val step = calculateStep()
                                        scrubPosMs = (scrubPosMs + step).coerceAtMost(durationMs)
                                        onUserInteraction?.invoke()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        val formatted = formatTime(scrubPosMs)
                                        justCommittedTarget = formatted
                                        onSeekTo(scrubPosMs)
                                        onUserInteraction?.invoke()
                                        true
                                    }
                                    KeyEvent.KEYCODE_BACK -> {
                                        if (scrubPosMs != currentPosMs) {
                                            scrubPosMs = currentPosMs
                                            internalPlayFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_DPAD_RIGHT,
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER -> true
                                    else -> false
                                }
                            } else false
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSeekTo(scrubPosMs)
                            onUserInteraction?.invoke()
                        }
                ) {
                    val totalWidth = maxWidth
                    val thumbSize = 22.dp
                    val maxOffset = (totalWidth - thumbSize).coerceAtLeast(0.dp)
                    val thumbOffset = (maxOffset * progress).coerceIn(0.dp, maxOffset)
                    val tooltipWidth = 180.dp
                    val maxTooltipOffset = (totalWidth - tooltipWidth).coerceAtLeast(0.dp)
                    val tooltipOffset = (thumbOffset + (thumbSize / 2) - (tooltipWidth / 2)).coerceIn(0.dp, maxTooltipOffset)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Floating Tooltip Preview above the Thumb
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (isSeekFocused || justCommittedTarget != null) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = tooltipOffset)
                                        .width(tooltipWidth)
                                        .background(NetflixDarkCard.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.5.dp, NetflixRed), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (justCommittedTarget != null) {
                                        Text(
                                            text = stringResource(R.string.jumping_to, justCommittedTarget!!),
                                            color = NetflixRed,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val deltaMs = scrubPosMs - currentPosMs
                                            val deltaStr = when {
                                                deltaMs >= 1000L -> " (+${formatTime(deltaMs)})"
                                                deltaMs <= -1000L -> " (-${formatTime(-deltaMs)})"
                                                else -> ""
                                            }
                                            Text(
                                                text = "${formatTime(scrubPosMs)}$deltaStr",
                                                color = NetflixWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.press_ok_to_play),
                                                color = NetflixLightGray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Progress Track & Thumb
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Inactive / Base Track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedBarHeight)
                                    .background(
                                        if (isSeekFocused) Color(0x66FFFFFF) else Color(0x4DFFFFFF),
                                        RoundedCornerShape(animatedBarHeight / 2)
                                    )
                            )

                            // Buffered range (visual only — scrub/commit model untouched)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bufferedProgress)
                                    .height(animatedBarHeight)
                                    .background(
                                        Color.White.copy(alpha = 0.35f),
                                        RoundedCornerShape(animatedBarHeight / 2)
                                    )
                            )

                            // Active / Filled Progress Track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(animatedBarHeight)
                                    .background(NetflixRed, RoundedCornerShape(animatedBarHeight / 2))
                            )

                            // Draggable / Focusable Thumb Dot
                            if (isSeekFocused) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = thumbOffset)
                                        .size(thumbSize)
                                        .background(NetflixWhite, CircleShape)
                                        .border(BorderStroke(3.dp, NetflixRed), CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timing Info & Seek Mode Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${formatTime(targetPos)} / ${formatTime(durationMs)}",
                            color = if (isSeekFocused) NetflixWhite else NetflixLightGray,
                            fontSize = 14.sp,
                            fontWeight = if (isSeekFocused) FontWeight.Bold else FontWeight.Medium
                        )

                        if (isSeekFocused) {
                            Box(
                                modifier = Modifier
                                    .background(NetflixRed, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.seek_mode),
                                    color = NetflixWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Quality + Subtitle buttons (focus: seekBar → quality → subtitle)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Speed Picker Button
                        Button(
                            onClick = {
                                onOpenSpeedPicker()
                                onUserInteraction?.invoke()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixDarkCard,
                                contentColor = NetflixWhite,
                                focusedContainerColor = NetflixWhite,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .focusRequester(speedFocusRequester)
                                .then(speedButtonModifier)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    right = qualityFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = stringResource(R.string.playback_speed),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = speedLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Quality Picker Button
                        Button(
                            onClick = {
                                onOpenQualityPicker()
                                onUserInteraction?.invoke()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixDarkCard,
                                contentColor = NetflixWhite,
                                focusedContainerColor = NetflixWhite,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .focusRequester(qualityFocusRequester)
                                .then(qualityButtonModifier)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    left = speedFocusRequester
                                    right = subtitleFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = stringResource(R.string.quality_label),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = qualityLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Subtitle Picker Button
                        Button(
                            onClick = {
                                onOpenSubtitlePicker()
                                onUserInteraction?.invoke()
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSubtitleEnabled) NetflixRed else NetflixDarkCard,
                                contentColor = NetflixWhite,
                                focusedContainerColor = if (isSubtitleEnabled) Color(0xFFB81D24) else NetflixWhite,
                                focusedContentColor = if (isSubtitleEnabled) NetflixWhite else Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            scale = ButtonDefaults.scale(focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .focusRequester(subtitleFocusRequester)
                                .then(subtitleButtonModifier)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    left = qualityFocusRequester
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSubtitleEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                                    contentDescription = stringResource(R.string.subtitles),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val displayLabel = if (!isSubtitleEnabled) {
                                    stringResource(R.string.subtitle_off)
                                } else {
                                    selectedSubtitleLabel ?: stringResource(R.string.subtitle_on)
                                }
                                Text(
                                    text = stringResource(R.string.subtitle_label_format, displayLabel),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
