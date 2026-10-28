package com.idlix.tv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
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
import com.idlix.data.model.MovieItem
import com.idlix.tv.ui.theme.NetflixWhite

@Composable
fun TvSwimlaneRow(
    title: String,
    items: List<MovieItem>,
    modifier: Modifier = Modifier,
    onItemFocus: ((MovieItem) -> Unit)? = null,
    onItemClick: (MovieItem) -> Unit,
    // Same-card refocus: the owning screen passes its pending key plus this
    // rail's id. The matching card requests focus on return from detail; the
    // screen clears the key via [onFocusKeyConsumed]. Null key = no-op, so
    // cold launch and unrelated recompositions are untouched.
    rowId: String = "",
    focusKey: String? = null,
    onFocusKeyConsumed: () -> Unit = {},
    // Scroll state, hoisted by the owning screen when rail scroll must survive
    // a route round-trip (detail return scrolls back to the originating card).
    // Defaults to a local state, preserving current behavior elsewhere.
    listState: LazyListState = rememberLazyListState(),
    // Content-to-bar escape for every card (see topEdgeBarEscape): the owning
    // screen passes its bar link; position-gated, so only a top-edge card
    // escapes and in-rail UP is untouched.
    barEscapeLink: FocusRequester? = null,
    // Scroll-to-top hook forwarded to topEdgeBarEscape (see there): entry
    // screens pass `{ listState.scrollToItem(0) }` so the hero returns with
    // the tabs. Null = focus-only (default).
    onBarEscape: (suspend () -> Unit)? = null,
    // Explicit UP chain (STUCK-1): the owning screen passes the previous
    // rail's link (hero for the first rail). Preview escape wins while the
    // card sits under the bar; otherwise this deterministic link wins over
    // plain search, so UP never dies when the row above is disposed.
    upLink: FocusRequester? = null,
    // Explicit DOWN chain (mirror of upLink): the owning screen passes the
    // next rail's link (null for the last rail). Deterministic link wins
    // over geometry + focusRestorer, so DOWN never jumps sideways.
    downLink: FocusRequester? = null,
    // Rail link for the NEXT rail's UP/DOWN: attached to this rail's LazyRow
    // container (stable while the column is composed) — never to a card,
    // whose node is disposed when the row scrolls right.
    railLink: FocusRequester? = null,
    // Disposal-proof hop (DISPOSE-1, see railHopKeys): the owning screen's
    // outer list state plus this rail's neighbors' column indices. The hop
    // only fires when the target rail is off-screen/disposed; in-viewport
    // targets keep the declarative up/down links above untouched.
    outerListState: LazyListState? = null,
    upRailIndex: Int = -1,
    downRailIndex: Int = -1,
    // Column-preserving UP/DOWN (same-column hop, see columnHopKeys): the
    // owning screen passes each neighbor rail as a RailColumnTarget
    // (target listState + card registry + count). The hop sits before
    // railHopKeys in the card chain, consumes UP/DOWN first, retries
    // scroll-then-focus bounded, and stays on a final miss — it never
    // touches the container restorer, so col-0 stays col-0.
    // Null = legacy path (declarative link + railHopKeys) untouched.
    upColumn: RailColumnTarget? = null,
    downColumn: RailColumnTarget? = null,
    // This rail's column registry (index -> card requester), hoisted by the
    // owning screen and shared with neighbor rails' targets. Plain map (not
    // state): written during composition, read only on key events.
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
            itemsIndexed(items, key = { _, m -> m.id }) { index, movie ->
                // Same-card refocus target: a plain focusRequester is inert
                // until requested, so attaching it to every card disturbs
                // neither focusRestorer nor the railFocusFrame bring-into-view.
                val cardFocusRequester = remember { FocusRequester() }
                if (focusKey != null && focusKey == "$rowId/${movie.contentType}/${movie.slug}") {
                    LaunchedEffect(focusKey) {
                        try {
                            cardFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                        onFocusKeyConsumed()
                    }
                }
                val isLast = index == items.lastIndex
                columnCards?.put(index, cardFocusRequester)
                // 16dp vertical headroom (TvSizing.RailFocusVertical): the
                // explicit bring-into-view frame settles with the wrapper
                // (not the zoomed card) visible, so the 1.12x ring never
                // clips at the scroll-viewport edge. Horizontal headroom
                // lives inside railFocusFrame and doubles as inter-card gap,
                // so the card below carries no end padding.
                Box(
                    Modifier
                        .railFocusFrame()
                        .padding(vertical = TvSizing.RailFocusVertical)
                        .topEdgeBarEscape(barEscapeLink, onEscape = onBarEscape)
                        .columnHopKeys(outerListState, index, upColumn, downColumn)
                        .railHopKeys(outerListState, upLink, upRailIndex, downLink, downRailIndex)
                ) {
                    TvMovieCard(
                        title = movie.title,
                        posterUrl = movie.fullPosterUrl,
                        rating = movie.formattedRating,
                        quality = movie.quality,
                        modifier = Modifier
                            .focusRequester(cardFocusRequester)
                            .focusProperties {
                                if (upLink != null) up = upLink
                                // Last rail DOWN stays (never geometry): no below
                                // candidate, so an unset link would rely on the
                                // search no-op. Explicit Cancel parks focus.
                                if (downLink != null) down = downLink else down = FocusRequester.Cancel
                                // Col-0 LEFT stays (never leaps to the gear):
                                // first card has no left neighbor in-rail.
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
