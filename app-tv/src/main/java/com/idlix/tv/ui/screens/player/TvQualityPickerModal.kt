package com.idlix.tv.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

/**
 * Slide-out quality picker. Clones the [TvSubtitlePickerModal] interaction
 * model: scrim click dismisses, the panel absorbs clicks, the first row takes
 * initial focus, and Back is handled by the host screen (PlayerScreen closes
 * the picker and returns focus to the Quality button).
 *
 * Rows come from [TvPlaybackQuality.rowsForHeights]: the actually-present
 * renditions when the live tracks are known, else the static full list.
 * Selecting a row applies the cap live (no player recreation) via the host.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvQualityPickerModal(
    isVisible: Boolean,
    currentQuality: TvPlaybackQuality,
    availableHeights: Set<Int> = emptySet(),
    isZoom: Boolean = false,
    onSelectQuality: (TvPlaybackQuality) -> Unit,
    onSelectAspect: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    val rows = remember(availableHeights) {
        TvPlaybackQuality.rowsForHeights(availableHeights)
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
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Video Quality",
                        color = NetflixWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TvLazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(rows) { quality ->
                    val isSelected = quality == currentQuality
                    val title = if (quality == TvPlaybackQuality.AUTO) {
                        "${quality.title} (Recommended)"
                    } else {
                        quality.title
                    }
                    QualityOptionCard(
                        title = title,
                        subtitle = quality.detail,
                        badge = if (quality == TvPlaybackQuality.AUTO) "AUTO" else quality.title,
                        isSelected = isSelected,
                        onClick = { onSelectQuality(quality) },
                        modifier = if (quality == rows.first()) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                }

                item {
                    Text(
                        text = "Picture size",
                        color = NetflixWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    QualityOptionCard(
                        title = "Fit",
                        subtitle = "Entire picture · never cropped",
                        badge = "FIT",
                        isSelected = !isZoom,
                        onClick = { onSelectAspect(false) },
                    )
                }
                item {
                    QualityOptionCard(
                        title = "Zoom",
                        subtitle = "Fill screen · crops the long edges",
                        badge = "ZOOM",
                        isSelected = isZoom,
                        onClick = { onSelectAspect(true) },
                    )
                }

                item {
                    Text(
                        text = "Applies instantly — playback keeps playing.",
                        color = NetflixLightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
        }
    }
}
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QualityOptionCard(
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
            .height(50.dp),
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
                                if (badge == "AUTO") NetflixRed else Color(0x66FFFFFF),
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
                        contentDescription = "Selected",
                        tint = NetflixWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
