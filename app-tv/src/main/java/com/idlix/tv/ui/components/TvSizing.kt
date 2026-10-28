package com.idlix.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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

    /**
     * Focus-frame headroom around rail cards: [railFocusFrame] applies
     * [RailFocusHorizontal] itself; call sites add
     * `padding(vertical = RailFocusVertical)` outside it. Worst case is the
     * 220dp-wide continue card at 1.12x (13.2dp overflow per side) plus the
     * 2dp ring, so 16dp keeps the full ring visible with breathing room on
     * every rail, at start/middle/end.
     */
    val RailFocusHorizontal: Dp = 16.dp
    val RailFocusVertical: Dp = 16.dp
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
 * include [TvSizing.RailFocusHorizontal] of headroom on each side plus
 * call-site `padding(vertical = TvSizing.RailFocusVertical)` for the 1.12x
 * focus zoom (worst case 220dp continue card -> 13.2dp overflow per side,
 * plus the 2dp ring).
 *
 * The platform's automatic focus bring-into-view uses the CARD's own layout
 * bounds and ignores the zoom overflow, so an edge arrival that settles the
 * card exactly on the scroll-viewport edge slices the white ring. The
 * explicit request below fires after the automatic one and settles with the
 * padded wrapper fully visible, so the ring + zoom survive on all sides.
 * Apply OUTSIDE the card, e.g.:
 * `Box(Modifier.railFocusFrame().padding(vertical = TvSizing.RailFocusVertical)) { TvMovieCard(...) }`
 *
 * Inter-card spacing lives in this headroom (16dp per side = 32dp gaps), so
 * cards themselves carry no end padding — otherwise gaps double up.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.railFocusFrame(): Modifier = composed {
    val frameRequester = remember { BringIntoViewRequester() }
    val frameScope = rememberCoroutineScope()
    bringIntoViewRequester(frameRequester)
        .padding(horizontal = TvSizing.RailFocusHorizontal)
        .onFocusChanged {
            // NB: hasFocus, not isFocused — the wrapper Box itself is never
            // focusable, so isFocused stays false here and the explicit
            // request below would silently never run. With hasFocus the
            // wrapper settles fully into view whenever its card takes focus,
            // which is what keeps edge rings (first/last card) unclipped.
            if (it.hasFocus) {
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
 *
 * [onEscape] is an optional suspend hook the owner runs on a genuine escape
 * (UP key only — tab LEFT/RIGHT and entry focus never enter this path).
 * Entry screens pass `{ listState.scrollToItem(0) }` so the tabs take focus
 * while the hero scrolls back into view underneath. It launches async in the
 * modifier's own scope, so the focus hop never waits on (or loops from) the
 * scroll — and a null hook keeps the legacy focus-only behavior.
 */
fun Modifier.topEdgeBarEscape(
    barLink: FocusRequester?,
    force: Boolean = false,
    onEscape: (suspend () -> Unit)? = null
): Modifier = composed {
    if (barLink == null) return@composed this
    var topPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val edgePx = with(LocalDensity.current) { (TvTopBarHeight + 12.dp).toPx() }
    val focusManager = LocalFocusManager.current
    val escapeScope = rememberCoroutineScope()
    this
        .onGloballyPositioned { topPx = it.positionInWindow().y }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) {
                return@onPreviewKeyEvent false
            }
            if (force || topPx <= edgePx) {
                try {
                    // Scroll-to-top first (fire-and-forget): the column may
                    // sit mid-list with the hero scrolled off — the bar takes
                    // focus immediately while the list settles to index 0.
                    val scroll = onEscape
                    if (scroll != null) {
                        escapeScope.launch {
                            try {
                                scroll()
                            } catch (_: Exception) {}
                        }
                    }
                    // Text-field precedent (SearchScreen): a held focus session
                    // can veto the outgoing transfer, so release it first —
                    // bare requestFocus() silently no-ops (STUCK-1).
                    try {
                        focusManager.clearFocus(force = true)
                    } catch (_: Exception) {}
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

/**
 * Disposal-proof rail hop for scrollable TV rail lists (DISPOSE-1).
 *
 * Rail UP/DOWN links live on each rail's LazyRow container (stable only
 * while that column item is composed). On a scrolled LazyColumn the target
 * rail is usually disposed, so the declarative `focusProperties` link
 * silently no-ops and focus sticks on the current card (rig: 8x DOWN from
 * hero never leaves Continue).
 *
 * This modifier sits AFTER [topEdgeBarEscape] (escape wins while a card sits
 * under the bar) and AFTER [columnHopKeys]: whenever a column target is
 * valid, the column hop consumes UP/DOWN first, so this container-restorer
 * path can never fire for a column hop — it only serves legacy null-column
 * rails. When the target rail index is already composed (in the viewport,
 * even partially) it returns false so the declarative link wins untouched —
 * no scroll jump, and the container's focusRestorer keeps the current
 * column. Only when the target item is NOT composed does it consume the key,
 * scroll the outer list to the target rail, wait one settle beat, then
 * request the rail container link (restorer routes to that rail's
 * last-focused card — never col-0).
 *
 * [upRailIndex]/[downRailIndex] are outer-[LazyListState] item indices
 * mirroring the owning screen's column order (hero/header = 0); -1 disables
 * that direction (last rail DOWN, or a null link). Null [outerListState]
 * disables the hop entirely, preserving legacy behavior for non-lazy owners.
 */
fun Modifier.railHopKeys(
    outerListState: LazyListState?,
    upLink: FocusRequester?,
    upRailIndex: Int,
    downLink: FocusRequester?,
    downRailIndex: Int
): Modifier = composed {
    if (outerListState == null) return@composed this
    val hopScope = rememberCoroutineScope()
    this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val (link, railIndex) = when (event.key) {
            Key.DirectionUp -> upLink to upRailIndex
            Key.DirectionDown -> downLink to downRailIndex
            else -> return@onPreviewKeyEvent false
        }
        if (link == null || railIndex < 0) return@onPreviewKeyEvent false
        val composedTarget = try {
            outerListState.layoutInfo.visibleItemsInfo.any { it.index == railIndex }
        } catch (_: Exception) {
            false
        }
        // In-viewport target: declarative focusProperties link wins (no jump).
        if (composedTarget) return@onPreviewKeyEvent false
        hopScope.launch {
            try {
                outerListState.scrollToItem(railIndex)
            } catch (_: Exception) {
                return@launch
            }
            delay(120)
            try {
                link.requestFocus()
            } catch (_: Exception) {}
        }
        true
    }
}
/**
 * Column-preserving rail target for [columnHopKeys].
 *
 * [cards] is the target rail's column registry (index -> card requester),
 * hoisted by the owning screen and filled by the rail itself. [fallbackLink]
 * is RETAINED ONLY for call-site shape (owning screens still pass the rail
 * container link) and is NEVER focused by the UP/DOWN path: the container
 * restorer replays the target rail's last-touched card, so touching it here
 * is the col-0 -> far-right jump. It stays attached on the rail LazyRow
 * container solely for the detail-return match-effect path. [itemCount]
 * clamps the source column into range so a short target lands on its last
 * card. [railIndex] is the outer [LazyListState] item index of the target
 * rail; -1 disables the direction.
 */
data class RailColumnTarget(
    val listState: LazyListState? = null,
    val cards: Map<Int, FocusRequester>? = null,
    val fallbackLink: FocusRequester? = null,
    val itemCount: Int = 0,
    val railIndex: Int = -1
)

/**
 * Column-preserving UP/DOWN hop for TV rails.
 *
 * Container links + focusRestorer restore the LAST-focused card, so a rail
 * touched far-right replays far-right on every later UP/DOWN (rig: Action
 * col-0 UP lands on Series far-right). This modifier sits AFTER
 * [topEdgeBarEscape] (escape wins while a card sits under the bar) and BEFORE
 * [railHopKeys], and it CONSUMES DPAD UP/DOWN on every card whenever the
 * target is valid — so the container-restorer path below can never fire for
 * a column hop. It clamps the source column into the target rail
 * (`sourceIndex.coerceAtMost(itemCount - 1)`), scrolls the outer list to the
 * target rail plus the target rail to that column, then focuses the
 * same-column card directly. Null target (e.g. hero UP) or an invalid target
 * (railIndex < 0, itemCount <= 0) returns false so the legacy declarative
 * link wins untouched.
 *
 * No container fallback: when the same-column card requester is missing,
 * the hop re-asserts both scrolls and retries (bounded: 1x120ms settle plus
 * up to 3x200ms) until the freshly composed card registers. A final miss
 * consumes the key and stays — focus never touches the container link, so a
 * col-0 source can never land far-right and a far-right source never wraps
 * to col-0.
 *
 * Stale-registry settle: the card registry holds the requester from the
 * target card's last composition. When the target rail was last left
 * scrolled sideways (e.g. Top-10 visited far-right), the same-column card
 * is disposed, so the first read after scrolling can still return the
 * detached requester (silent no-op) or miss. While the post-beat read is
 * unchanged from the pre-scroll snapshot, the target hasn't recomposed yet
 * — re-assert both scrolls and settle again, so the freshly composed card
 * wins. Focusing a leftover detached instance is a silent no-op (stay),
 * never a jump.
 */
fun Modifier.columnHopKeys(
    outerListState: LazyListState?,
    sourceIndex: Int,
    upTarget: RailColumnTarget?,
    downTarget: RailColumnTarget?
): Modifier = composed {
    if (outerListState == null) return@composed this
    if (upTarget == null && downTarget == null) return@composed this
    val hopScope = rememberCoroutineScope()
    this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val target = when (event.key) {
            Key.DirectionUp -> upTarget
            Key.DirectionDown -> downTarget
            else -> return@onPreviewKeyEvent false
        } ?: return@onPreviewKeyEvent false
        if (target.railIndex < 0 || target.itemCount <= 0) return@onPreviewKeyEvent false
        val targetIndex = sourceIndex.coerceIn(0, target.itemCount - 1)
        hopScope.launch {
            val before = try {
                target.cards?.get(targetIndex)
            } catch (_: Exception) {
                null
            }
            try {
                outerListState.scrollToItem(target.railIndex)
            } catch (_: Exception) {}
            try {
                target.listState?.scrollToItem(targetIndex)
            } catch (_: Exception) {}
            delay(120)
            var card = try {
                target.cards?.get(targetIndex)
            } catch (_: Exception) {
                null
            }
            // Bounded scroll-then-retry: while the registry hasn't turned
            // over (stale detached instance or still missing), the target
            // card hasn't recomposed yet — re-assert both scrolls and settle
            // again, up to 3 extra 200ms beats. The container link is never
            // touched here (restorer would replay far-right). A visibly
            // composed same-instance is live, so it breaks out and focuses
            // immediately with no extra beats.
            var attempts = 0
            while ((card == null || card === before) && attempts < 3) {
                val live = try {
                    card != null &&
                        outerListState.layoutInfo.visibleItemsInfo.any { it.index == target.railIndex } &&
                        (target.listState?.layoutInfo?.visibleItemsInfo?.any { it.index == targetIndex } == true)
                } catch (_: Exception) {
                    false
                }
                if (live) break
                try {
                    outerListState.scrollToItem(target.railIndex)
                } catch (_: Exception) {}
                try {
                    target.listState?.scrollToItem(targetIndex)
                } catch (_: Exception) {}
                delay(200)
                card = try {
                    target.cards?.get(targetIndex)
                } catch (_: Exception) {
                    null
                }
                attempts++
            }
            // Same-column card only; a final miss (or a leftover detached
            // instance, whose request is a silent no-op) stays put — the key
            // is already consumed, so railHopKeys/restorer can never fire.
            try {
                if (card != null) card.requestFocus()
            } catch (_: Exception) {}
        }
        true
    }
}

/**
 * Absolute-control edge trap: explicitly Cancel the given D-pad directions so
 * geometry can never move focus off a terminal edge (col-0 LEFT, last-cell
 * RIGHT, last-row DOWN, fullscreen UP). Non-cancelled directions keep the
 * default search untouched. Apply on the focusable node itself (after
 * focusRequester, inside the same focusProperties chain).
 */
fun Modifier.edgeCancels(
    left: Boolean = false,
    right: Boolean = false,
    up: Boolean = false,
    down: Boolean = false
): Modifier = composed {
    if (!left && !right && !up && !down) return@composed this
    this.focusProperties {
        if (left) this.left = FocusRequester.Cancel
        if (right) this.right = FocusRequester.Cancel
        if (up) this.up = FocusRequester.Cancel
        if (down) this.down = FocusRequester.Cancel
    }
}

/**
 * BACK pop for fullscreen/child routes (detail, genre/collection detail).
 * Key events bubble from the focused child, so a parked loading holder or any
 * focused control still pops — no focus-dependent dead window. Gamepad B is
 * handled in AppNavigation; this covers the system BACK key only.
 */
fun Modifier.dpadBackHandler(onBack: () -> Unit): Modifier = composed {
    this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val code = try {
            event.nativeKeyEvent.keyCode
        } catch (_: Exception) {
            -1
        }
        if (code == android.view.KeyEvent.KEYCODE_BACK) {
            try {
                onBack()
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }
}
