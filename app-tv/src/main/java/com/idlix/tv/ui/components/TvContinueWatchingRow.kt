package com.idlix.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.idlix.data.model.WatchHistoryItem
import com.idlix.tv.ui.theme.NetflixWhite

@Composable
fun TvContinueWatchingRow(
    title: String = "Lanjutkan Menonton",
    items: List<WatchHistoryItem>,
    onItemClick: (WatchHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
    // Same-card refocus (mirrors TvSwimlaneRow): the owning screen passes its
    // pending key plus this rail's id. The matching card requests focus on
    // return from player; the screen clears the key via [onFocusKeyConsumed].
    // Null key = no-op. Key shape: "$rowId/${contentType}/${contentId}"
    // (contentId — continue items may carry a null slug).
    rowId: String = "",
    focusKey: String? = null,
    onFocusKeyConsumed: () -> Unit = {},
    // Content-to-bar escape for every card (see topEdgeBarEscape).
    barEscapeLink: FocusRequester? = null,
    // Scroll-to-top hook forwarded to topEdgeBarEscape (see there).
    // Null = focus-only (default).
    onBarEscape: (suspend () -> Unit)? = null,
    // Hoisted scroll state (mirrors TvSwimlaneRow) so detail/player return
    // can restore the originating card.
    listState: LazyListState = rememberLazyListState(),
    // Explicit UP/DOWN chain (STUCK-1) + rail link (see TvSwimlaneRow).
    upLink: FocusRequester? = null,
    downLink: FocusRequester? = null,
    railLink: FocusRequester? = null,
    // Disposal-proof hop (DISPOSE-1, see railHopKeys): the owning screen's
    // outer list state plus this rail's neighbors' column indices. The hop
    // only fires when the target rail is off-screen/disposed; in-viewport
    // targets keep the declarative up/down links above untouched.
    outerListState: LazyListState? = null,
    upRailIndex: Int = -1,
    downRailIndex: Int = -1,
    // Column-preserving UP/DOWN (same-column hop, see columnHopKeys).
    // The hop sits before railHopKeys in the card chain, consumes UP/DOWN
    // first, retries scroll-then-focus bounded, and stays on a final miss —
    // it never touches the container restorer, so col-0 stays col-0.
    // Null = legacy path untouched.
    upColumn: RailColumnTarget? = null,
    downColumn: RailColumnTarget? = null,
    // This rail's column registry (index -> card requester), hoisted by the
    // owning screen. Plain map: written during composition, read on key events.
    columnCards: MutableMap<Int, FocusRequester>? = null
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = NetflixWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            // Plain rail title: never focusable, so D-pad skips straight to cards.
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            modifier = Modifier.then(if (railLink != null) Modifier.focusRequester(railLink) else Modifier).focusRestorer()
        ) {
            itemsIndexed(items, key = { _, it -> it.contentId }) { index, item ->
                val cardFocusRequester = remember { FocusRequester() }
                if (focusKey != null && focusKey == "$rowId/${item.contentType}/${item.contentId}") {
                    LaunchedEffect(focusKey) {
                        try {
                            cardFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                        onFocusKeyConsumed()
                    }
                }
                val isLast = index == items.lastIndex
                columnCards?.put(index, cardFocusRequester)
                // Column registry for same-column UP/DOWN (see columnHopKeys).
                Box(
                    Modifier
                        .railFocusFrame()
                        .padding(vertical = TvSizing.RailFocusVertical)
                        .topEdgeBarEscape(barEscapeLink, onEscape = onBarEscape)
                        .columnHopKeys(outerListState, index, upColumn, downColumn)
                        .railHopKeys(outerListState, upLink, upRailIndex, downLink, downRailIndex)
                ) {
                    TvContinueWatchingCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier
                            .focusRequester(cardFocusRequester)
                            .focusProperties {
                                if (upLink != null) up = upLink
                                if (downLink != null) down = downLink else down = FocusRequester.Cancel
                                if (index == 0) left = FocusRequester.Cancel
                                if (isLast) right = FocusRequester.Cancel
                            }
                    )
                }
            }
        }
    }
}
