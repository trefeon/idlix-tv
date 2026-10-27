package com.idlix.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.idlix.data.model.MovieItem
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

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
    barEscapeLink: FocusRequester? = null
) {
    if (items.isEmpty()) return
    val top10 = items.take(10)

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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.focusRestorer()
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
                // Same 12dp focus frame as the swimlanes: the oversized
                // numerals + 1.12x zoom must survive viewport-edge settles.
                Box(
                    Modifier
                        .railFocusFrame()
                        .padding(vertical = 12.dp)
                        .topEdgeBarEscape(barEscapeLink)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(end = 12.dp)
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
                            modifier = Modifier.focusRequester(cardFocusRequester),
                            onFocus = { onItemFocus?.invoke(movie) },
                            onClick = { onItemClick(movie) }
                        )
                    }
                }
            }
        }
    }
}
