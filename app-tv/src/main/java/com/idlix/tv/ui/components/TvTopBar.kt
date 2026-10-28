package com.idlix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkGray
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

/**
 * Central route mapping for the top chrome (replaces the old drawer enum).
 * LIBRARY reuses the existing "My List" label and drives the
 * continue_watching route — no new strings, no ViewModel/route changes.
 */
enum class TopBarTab(val labelRes: Int, val route: String) {
    HOME(R.string.home, "home"),
    MOVIES(R.string.movies, "movies"),
    SERIES(R.string.series, "series"),
    GENRES(R.string.genres, "genres"),
    SEARCH(R.string.search, "search"),
    LIBRARY(R.string.my_list, "continue_watching");

    companion object {
        fun fromRoute(route: String?): TopBarTab? = when {
            route == null -> null
            route.startsWith("search") -> SEARCH
            route.startsWith("continue_watching") -> LIBRARY
            route.startsWith("movies") -> MOVIES
            route.startsWith("series") -> SERIES
            route.startsWith("genre") -> GENRES
            else -> HOME
        }
    }
}

private val TabShape = RoundedCornerShape(4.dp)

/**
 * Netflix-style top bar (~64dp, transparent over hero art with a top
 * gradient scrim for legibility — no solid fill, no blur layer).
 *
 * - Left: red IDLIX wordmark (text idiom, fenced brand red).
 * - Tabs: [TopBarTab] order; selected = white text + red 2dp underline,
 *   unselected = gray text; focused (any tab) = white 2dp border.
 * - Right: settings gear, monochrome (white when selected/focused,
 *   gray otherwise, white 2dp border on focus).
 *
 * Focus: no autofocus here — launch focus stays on the hero Play button.
 * LEFT/RIGHT moves between tabs and the gear, DOWN drops into content,
 * and UP from the first content rail returns to the bar via normal
 * focus order (bar floats above the NavHost in the shell Box).
 */
/** Shared chrome height (64dp): hero bleeds under floating bar while text
 *  screens and the AppNavigation shell use [TvTopBarHeight] as their top
 *  inset (and topEdgeBarEscape threshold) so focused content never traps
 *  under the bar. */
val TvTopBarHeight = 64.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTopBar(
    selectedTab: TopBarTab?,
    onTabSelected: (TopBarTab) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    settingsSelected: Boolean = false,
    // Entry link: D-pad DOWN from any tab/gear parks on the current screen's
    // entry node (hero Play, Back, first tile/card, search field...). Plain
    // focus search dies crossing this overlay boundary into LazyColumn
    // content, so the link is explicit: key-handler first (deterministic),
    // focusProperties as backup for non-key paths.
    downFocusRequester: FocusRequester? = null,
    // Selected-tab link: content scrolled under the bar escapes UP to this
    // requester (see topEdgeBarEscape). Attached only to the selected tab /
    // gear — one node at a time, so the shared instance stays unambiguous.
    tabFocusLink: FocusRequester? = null
) {
    // Transparent over hero art (web-style): legibility comes from the top
    // gradient scrim, not a solid bar. No blur layer (overdraw on low-end).
    Column(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    NetflixBlack.copy(alpha = 0.7f),
                    NetflixBlack.copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
        )
    ) {
        // 5%-or-32dp-min side rule: 24dp sat inside the overscan danger zone.
        val sidePadding = tvContentSidePadding()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TvTopBarHeight)
                .padding(horizontal = sidePadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand wordmark — red is fenced to this + selected indicator.
            Text(
                text = "IDLIX",
                color = NetflixRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.width(32.dp))

            // Fixed tab strip: every tab always composed and visible — no
            // horizontal scroll (the escape link needs its target composed).
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = TopBarTab.values()
                for ((index, tab) in tabs.withIndex()) {
                    TopBarTabItem(
                        tab = tab,
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        downFocusRequester = downFocusRequester,
                        tabFocusLink = if (tab == selectedTab) tabFocusLink else null,
                        isFirst = index == 0,
                        isLast = index == tabs.lastIndex
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            SettingsGear(
                selected = settingsSelected,
                onClick = onSettingsClick,
                downFocusRequester = downFocusRequester,
                tabFocusLink = if (settingsSelected) tabFocusLink else null
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBarTabItem(
    tab: TopBarTab,
    selected: Boolean,
    onClick: () -> Unit,
    downFocusRequester: FocusRequester? = null,
    tabFocusLink: FocusRequester? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false
) {
    val tabInteraction = remember { MutableInteractionSource() }
    val focused by tabInteraction.collectIsFocusedAsState()
    val textColor = if (selected || focused) NetflixWhite else NetflixLightGray

    Column(
        modifier = Modifier
            .border(
                width = 2.dp,
                color = if (focused) NetflixWhite else Color.Transparent,
                shape = TabShape
            )
            .clickable(
                interactionSource = tabInteraction,
                indication = null,
                onClick = onClick
            )
            .then(if (tabFocusLink != null) Modifier.focusRequester(tabFocusLink) else Modifier)
            .focusProperties {
                if (downFocusRequester != null) down = downFocusRequester
                // Col-0 LEFT stays: HOME has no left neighbor in the fixed
                // strip, so geometry would die. Last tab RIGHT goes to the
                // gear via geometry (same Row, always composed) — no Cancel.
                if (isFirst) left = FocusRequester.Cancel
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val target = downFocusRequester ?: return@onPreviewKeyEvent false
                    try {
                        target.requestFocus()
                        true
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(tab.labelRes),
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        // Selected underline — red 2dp; transparent spacer otherwise so
        // rows never shift when selection moves.
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(24.dp)
                .height(2.dp)
                .background(if (selected) NetflixRed else Color.Transparent)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsGear(
    selected: Boolean,
    onClick: () -> Unit,
    downFocusRequester: FocusRequester? = null,
    tabFocusLink: FocusRequester? = null
) {
    val gearInteraction = remember { MutableInteractionSource() }
    val focused by gearInteraction.collectIsFocusedAsState()
    val tint = if (selected || focused) NetflixWhite else NetflixLightGray

    Box(
        modifier = Modifier
            .border(
                width = 2.dp,
                color = if (focused) NetflixWhite else Color.Transparent,
                shape = TabShape
            )
            .clickable(
                interactionSource = gearInteraction,
                indication = null,
                onClick = onClick
            )
            .then(if (tabFocusLink != null) Modifier.focusRequester(tabFocusLink) else Modifier)
            .focusProperties {
                if (downFocusRequester != null) down = downFocusRequester
                // Terminal RIGHT stays: gear is the last node in the bar.
                right = FocusRequester.Cancel
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val target = downFocusRequester ?: return@onPreviewKeyEvent false
                    try {
                        target.requestFocus()
                        true
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.settings),
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
