package com.idlix.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Shared adaptive sizing for 10-foot screens.
 *
 * All values derive from the REAL screen size ([LocalConfiguration] at the
 * screen root — never BoxWithConstraints inside lazy containers, whose
 * infinite parent constraints clamp to max and crop on short viewports).
 *
 * Viewport facts: phone rig is 731x411dp landscape, 1080p TVs are ~960x540dp
 * at tvdpi, 720p panels are smaller, and overscan can eat ~5% per edge.
 */
object TvSizing {
    /** Minimum side inset that survives overscan on any panel. */
    val MinSideInset: Dp = 32.dp

    /** Rail inset: keeps 1.12x focus-zoom headroom at rail ends. */
    val RailInset: Dp = 48.dp
}

/**
 * Side inset for full-bleed screens: 5% of the real screen width, floored at
 * [TvSizing.MinSideInset]. On the 731dp rig this is ~36.5dp; on a 960dp-wide
 * TV it is 48dp, matching the rail inset everywhere else.
 */
@Composable
fun tvContentSidePadding(): Dp {
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    return (screenW * 0.05f).coerceAtLeast(TvSizing.MinSideInset)
}

/**
 * Detail header height: 78% of the real screen height, clamped so the Play
 * CTA stays above the fold on the 411dp rig (300dp) without stretching on
 * tall panels (420dp). Mirrors the [TvHeroBillboard] pattern (55% - top bar);
 * detail is a fullscreen route with no top bar, so no subtraction.
 */
@Composable
fun tvDetailHeaderHeight(): Dp {
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    return (screenH * 0.78f).coerceIn(300.dp, 420.dp)
}

/**
 * Focus frame for rail cards (swimlane / Top-10 / continue / search grid /
 * detail episodes): a [BringIntoViewRequester] on the WRAPPER bounds, which
 * include 12dp of vertical headroom for the 1.12x focus zoom (worst case
 * 200dp card -> 12dp overflow per side).
 *
 * The platform's automatic focus bring-into-view uses the CARD's own layout
 * bounds and ignores the zoom overflow, so an UP-arrival that settles the
 * card's top edge exactly on the scroll-viewport top slices the white ring.
 * The explicit request below fires after the automatic one and settles with
 * the padded wrapper fully visible, so the ring + zoom survive on all sides.
 * Apply OUTSIDE the card, e.g.:
 * `Box(Modifier.railFocusFrame().padding(vertical = 12.dp)) { TvMovieCard(...) }`
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.railFocusFrame(): Modifier = composed {
    val frameRequester = remember { BringIntoViewRequester() }
    val frameScope = rememberCoroutineScope()
    bringIntoViewRequester(frameRequester)
        .onFocusChanged {
            if (it.isFocused) {
                frameScope.launch { frameRequester.bringIntoView() }
            }
        }
}

/**
 * Top-edge bar escape for scrollable TV content under the floating bar.
 *
 * When D-pad navigation scrolls a focused node up under the transparent bar,
 * plain UP search dies: the bar tabs overlap the node instead of sitting
 * strictly above it, so no candidate qualifies and focus sticks. This modifier
 * watches the node's window top and, only while it sits at/under the bar's
 * bottom edge ([TvTopBarHeight] + 12dp slack), routes preview-UP to [barLink]
 * (the selected tab / gear, attached in TvTopBar). Anywhere lower it returns
 * false, so plain in-content search proceeds untouched.
 *
 * Null [barLink] is a no-op, so call sites can pass their screen link
 * straight through without branching. Pinned top-most controls (search
 * field) that can never scroll under the bar pass [force] = true to skip
 * the position gate and always escape.
 */
fun Modifier.topEdgeBarEscape(barLink: FocusRequester?, force: Boolean = false): Modifier = composed {
    if (barLink == null) return@composed this
    var topPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val edgePx = with(LocalDensity.current) { (TvTopBarHeight + 12.dp).toPx() }
    this
        .onGloballyPositioned { topPx = it.positionInWindow().y }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) {
                return@onPreviewKeyEvent false
            }
            if (force || topPx <= edgePx) {
                try {
                    barLink.requestFocus()
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        }
}
