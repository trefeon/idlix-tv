package com.idlix.tv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
    barEscapeLink: FocusRequester? = null
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
            modifier = Modifier.focusRestorer()
        ) {
            items(items, key = { it.id }) { movie ->
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
                // 12dp vertical headroom: the explicit bring-into-view frame
                // settles with the wrapper (not the zoomed card) visible, so
                // the 1.12x ring never clips at the scroll-viewport edge.
                Box(
                    Modifier
                        .railFocusFrame()
                        .padding(vertical = 12.dp)
                        .topEdgeBarEscape(barEscapeLink)
                ) {
                    TvMovieCard(
                        title = movie.title,
                        posterUrl = movie.fullPosterUrl,
                        rating = movie.formattedRating,
                        quality = movie.quality,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .focusRequester(cardFocusRequester),
                        onFocus = { onItemFocus?.invoke(movie) },
                        onClick = { onItemClick(movie) }
                    )
                }
            }
        }
    }
}
