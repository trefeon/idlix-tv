package com.idlix.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.idlix.data.model.MovieItem
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Netflix-style Top-10 rail: oversized rank numerals (red accent) paired with
 * compact poster cards. Reuses [TvMovieCard] so focus scale (1.12) and the
 * white focus ring stay consistent with every other rail.
 */
@Composable
fun TvTop10Row(
    title: String,
    items: List<MovieItem>,
    modifier: Modifier = Modifier,
    onItemFocus: ((MovieItem) -> Unit)? = null,
    onItemClick: (MovieItem) -> Unit,
    // Same-card refocus (mirrors TvSwimlaneRow): pending key + this rail's id;
    // the matching card self-requests focus and the screen clears the key.
    rowId: String = "",
    focusKey: String? = null,
    onFocusKeyConsumed: () -> Unit = {},
    // Hoisted scroll state (mirrors TvSwimlaneRow).
    listState: LazyListState = rememberLazyListState(),
    // Content-to-bar escape for every card (see topEdgeBarEscape).
    barEscapeLink: FocusRequester? = null,
    // Scroll-to-top hook forwarded to topEdgeBarEscape (see there).
    // Null = focus-only (default).
    onBarEscape: (suspend () -> Unit)? = null,
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
    val top10 = items.take(10)

    // REST-1: when D-pad focus leaves the rail entirely, settle back to the
    // start after a beat so the oversized #1 numeral is never left
    // half-clipped on the viewport edge at rest. The beat (300ms) rides over
    // intra-rail card-to-card moves — focus lands on the next card first, so
    // rowHasFocus is true again and the settle never fires mid-browse.
    // Detail-return still self-heals: the focusKey match-effect focuses the
    // originating card and its railFocusFrame brings it back into view.
    var rowHasFocus by remember { mutableStateOf(false) }
    val settleScope = rememberCoroutineScope()

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
            // Aligned to the rail's leading inset (see LazyRow below).
            modifier = Modifier.padding(start = 64.dp, bottom = 8.dp)
        )

        LazyRow(
            state = listState,
            // Leading inset is wider than the standard 48dp rail padding:
            // the 92sp numeral plus its 10dp poster overlap needs the extra
            // headroom so #1 never sits on the clip edge at rest.
            contentPadding = PaddingValues(start = 64.dp, end = 48.dp, top = 8.dp, bottom = 8.dp),
            modifier = Modifier.then(if (railLink != null) Modifier.focusRequester(railLink) else Modifier).focusRestorer()
                .onFocusChanged {
                    rowHasFocus = it.hasFocus
                    if (!it.hasFocus) {
                        settleScope.launch {
                            delay(300)
                            if (!rowHasFocus) {
                                try {
                                    listState.scrollToItem(0)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
        ) {
            itemsIndexed(top10, key = { _, movie -> movie.id }) { index, movie ->
                val cardFocusRequester = remember { FocusRequester() }
                if (focusKey != null && focusKey == "$rowId/${movie.contentType}/${movie.slug}") {
                    LaunchedEffect(focusKey) {
                        try {
                            cardFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                        onFocusKeyConsumed()
                    }
                }
                val isLast = index == top10.lastIndex
                columnCards?.put(index, cardFocusRequester)
                // Same focus frame as the swimlanes (16dp vertical here,
                // 16dp horizontal inside railFocusFrame): the oversized
                // numerals + 1.12x zoom must survive viewport-edge settles.
                // Headroom doubles as inter-item gap, so spacedBy stays 0
                // and the inner Row carries no end padding.
                Box(
                    Modifier
                        .railFocusFrame()
                        .padding(vertical = TvSizing.RailFocusVertical)
                        .topEdgeBarEscape(barEscapeLink, onEscape = onBarEscape)
                        .columnHopKeys(outerListState, index, upColumn, downColumn)
                        .railHopKeys(outerListState, upLink, upRailIndex, downLink, downRailIndex)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Oversized rank numeral; slight overlap into the poster.
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.offset(x = 10.dp, y = 8.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = NetflixRed,
                                fontSize = if (index == 9) 76.sp else 92.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                        TvMovieCard(
                            title = movie.title,
                            posterUrl = movie.fullPosterUrl,
                            rating = movie.formattedRating,
                            quality = movie.quality,
                            width = 110,
                            height = 165,
                            modifier = Modifier
                                .focusRequester(cardFocusRequester)
                                .focusProperties {
                                    if (upLink != null) up = upLink
                                    if (downLink != null) down = downLink else down = FocusRequester.Cancel
                                    if (index == 0) left = FocusRequester.Cancel
                                    // TELEPORT-1: a short row's last card has no
                                    // right neighbor — plain search leaps to the
                                    // Settings gear. Cancel keeps focus (tabs keep
                                    // their intended RIGHT to the gear).
                                    if (isLast) right = FocusRequester.Cancel
                                },
                            onFocus = { onItemFocus?.invoke(movie) },
                            onClick = { onItemClick(movie) }
                        )
                    }
                }
            }
        }
    }
}
