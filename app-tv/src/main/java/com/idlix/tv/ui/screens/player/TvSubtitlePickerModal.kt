package com.idlix.tv.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.data.model.SubdlSubtitle
import com.idlix.data.model.SubtitleTrack
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

/**
 * One audio rendition from the live `Tracks` groups (language + display label).
 * Language codes ("id", "en") are locale-neutral wire values, not UI copy.
 */
data class TvAudioOption(
    val language: String,
    val label: String
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSubtitlePickerModal(
    isVisible: Boolean,
    availableSubtitles: List<SubtitleTrack>,
    selectedSubtitle: SubtitleTrack?,
    isSubtitleEnabled: Boolean,
    subdlSubtitles: List<SubdlSubtitle>,
    isSearchingSubdl: Boolean,
    onSelectTrack: (SubtitleTrack?) -> Unit,
    onSelectSubdl: (SubdlSubtitle) -> Unit,
    onSearchSubdlClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    availableAudioTracks: List<TvAudioOption> = emptyList(),
    selectedAudioLanguage: String? = null,
    onSelectAudio: (String) -> Unit = {},
) {
    if (!isVisible) return

    val firstItemFocusRequester = remember { FocusRequester() }
    // Bottom edge trap: DOWN on the true-last focusable loops to itself so
    // focus never leaks out of the modal onto the player behind the scrim.
    val lastItemFocusRequester = remember { FocusRequester() }

    // First focusable is the first audio card when live audio tracks exist,
    // else the "Off" card. Last focusable is the last SubDL card when present,
    // else the Search button (when shown), else the last track, else "Off".
    val hasAudio = availableAudioTracks.isNotEmpty()
    val searchShown = !isSearchingSubdl
    val lastIsSearch = subdlSubtitles.isEmpty() && searchShown
    val lastIsOff = subdlSubtitles.isEmpty() && !searchShown && availableSubtitles.isEmpty()

    LaunchedEffect(isVisible) {
        if (isVisible) {
            repeat(5) {
                delay(50)
                try {
                    firstItemFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Slide-out Drawer Panel from Right side
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(420.dp)
                .background(Color(0xF0181818))
                .clickable(enabled = false) {}
                .padding(24.dp)
                .focusGroup()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (availableAudioTracks.isNotEmpty()) {
                            stringResource(R.string.audio_and_subtitles)
                        } else {
                            stringResource(R.string.subtitles)
                        },
                        color = NetflixWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isSearchingSubdl) {
                    TvLoadingSpinner(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Hairline divider under the header (10-foot scan structure).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x33FFFFFF))
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 0. Audio tracks (only when the live Tracks expose audio groups;
                // otherwise the header already falls back to subtitles-only).
                if (availableAudioTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.audio),
                            color = NetflixLightGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }

                    items(availableAudioTracks, key = { it.language }) { audio ->
                        val isSelected = audio.language == selectedAudioLanguage
                        val isFirst = audio == availableAudioTracks.firstOrNull()
                        SubtitleOptionCard(
                            title = audio.label.ifBlank { audio.language },
                            subtitle = null,
                            badge = audio.language.uppercase(),
                            isSelected = isSelected,
                            onClick = { onSelectAudio(audio.language) },
                            modifier = if (isFirst) {
                                Modifier
                                    .focusRequester(firstItemFocusRequester)
                                    .focusProperties { up = firstItemFocusRequester }
                            } else {
                                Modifier
                            }
                        )
                    }
                }

                // 1. Turn Off Subtitles
                item {
                    val isOffSelected = !isSubtitleEnabled || selectedSubtitle == null
                    val offIsFirst = !hasAudio
                    SubtitleOptionCard(
                        title = stringResource(R.string.subtitles_off),
                        subtitle = null,
                        badge = null,
                        isSelected = isOffSelected,
                        onClick = { onSelectTrack(null) },
                        modifier = Modifier
                            .then(if (offIsFirst) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                            .then(if (lastIsOff) Modifier.focusRequester(lastItemFocusRequester) else Modifier)
                            .focusProperties {
                                if (offIsFirst) up = firstItemFocusRequester
                                if (lastIsOff) down = lastItemFocusRequester
                            }
                    )
                }

                // 2. Default & Stream Subtitles
                if (availableSubtitles.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.default_subtitle),
                            color = NetflixLightGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }

                    items(availableSubtitles) { track ->
                        val isSelected = isSubtitleEnabled && selectedSubtitle?.path == track.path
                        val trackIsLast = subdlSubtitles.isEmpty() && !searchShown &&
                            track == availableSubtitles.lastOrNull()
                        SubtitleOptionCard(
                            title = track.label.ifBlank { track.lang.uppercase() },
                            subtitle = null,
                            badge = track.lang.uppercase(),
                            isSelected = isSelected,
                            onClick = { onSelectTrack(track) },
                            modifier = if (trackIsLast) {
                                Modifier
                                    .focusRequester(lastItemFocusRequester)
                                    .focusProperties { down = lastItemFocusRequester }
                            } else {
                                Modifier
                            }
                        )
                    }
                }

                // 3. SubDL.com Subtitles
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.subdl_subtitles_header),
                            color = NetflixLightGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (!isSearchingSubdl) {
                            Button(
                                onClick = onSearchSubdlClick,
                                modifier = if (lastIsSearch) {
                                    Modifier
                                        .focusRequester(lastItemFocusRequester)
                                        .focusProperties { down = lastItemFocusRequester }
                                } else {
                                    Modifier
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = NetflixRed,
                                    focusedContainerColor = NetflixRed,
                                    focusedContentColor = NetflixWhite
                                ),
                                scale = ButtonDefaults.scale(focusedScale = 1.05f),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, NetflixWhite)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(R.string.search_subdl), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (isSearchingSubdl && subdlSubtitles.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.searching_subdl),
                            color = NetflixLightGray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else if (subdlSubtitles.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_subdl_found),
                            color = NetflixLightGray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(subdlSubtitles) { sub ->
                        val isIndo = sub.language.equals("ID", ignoreCase = true) || sub.lang.equals("indonesian", ignoreCase = true)
                        val badgeText = if (isIndo) "ID" else (sub.language ?: "EN")
                        val authorText = sub.author?.let { stringResource(R.string.subdl_author_by, it) } ?: ""
                        val releaseText = sub.releaseName ?: sub.name ?: ""

                        SubtitleOptionCard(
                            title = releaseText,
                            subtitle = authorText.ifBlank { null },
                            badge = badgeText,
                            isSelected = false,
                            onClick = { onSelectSubdl(sub) },
                            modifier = if (sub == subdlSubtitles.lastOrNull()) {
                                Modifier
                                    .focusRequester(lastItemFocusRequester)
                                    .focusProperties { down = lastItemFocusRequester }
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubtitleOptionCard(
    title: String,
    subtitle: String?,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            },
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isSelected) NetflixRed.copy(alpha = 0.25f) else NetflixDarkCard,
            focusedContainerColor = NetflixRed
        ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NetflixWhite)
            ),
            border = if (isSelected) {
                Border(border = BorderStroke(1.dp, NetflixRed))
            } else {
                Border.None
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (badge == "ID") NetflixRed else Color(0x66FFFFFF),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = NetflixWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = NetflixWhite,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = NetflixLightGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(NetflixRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.selected),
                        tint = NetflixWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
