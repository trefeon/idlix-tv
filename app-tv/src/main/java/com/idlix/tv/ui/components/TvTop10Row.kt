package com.idlix.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.idlix.data.model.MovieItem
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

/**
 * Netflix-style Top-10 rail: oversized rank numerals (red accent) paired with
 * compact poster cards. Reuses [TvMovieCard] so focus scale (1.08) and the
 * white focus ring stay consistent with every other rail.
 */
@Composable
fun TvTop10Row(
    title: String,
    items: List<MovieItem>,
    modifier: Modifier = Modifier,
    onItemFocus: ((MovieItem) -> Unit)? = null,
    onItemClick: (MovieItem) -> Unit
) {
    if (items.isEmpty()) return
    val top10 = items.take(10)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = NetflixWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )

        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(top10, key = { _, movie -> movie.id }) { index, movie ->
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
                        onFocus = { onItemFocus?.invoke(movie) },
                        onClick = { onItemClick(movie) }
                    )
                }
            }
        }
    }
}
