package com.idlix.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
    // Content-to-bar escape for every card (see topEdgeBarEscape).
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
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            modifier = Modifier.focusRestorer()
        ) {
            items(items, key = { it.contentId }) { item ->
                Box(
                    Modifier
                        .railFocusFrame()
                        .padding(vertical = 12.dp)
                        .topEdgeBarEscape(barEscapeLink)
                ) {
                    TvContinueWatchingCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }
    }
}
