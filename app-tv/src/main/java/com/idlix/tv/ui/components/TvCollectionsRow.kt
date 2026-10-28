package com.idlix.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.idlix.data.model.CollectionItem
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

/**
 * Collections rail: text tiles (the list payload carries no per-collection
 * poster to render — title + count + kind only). Same [TvSwimlaneRow]
 * refocus contract (`rowId`/`focusKey`/`onFocusKeyConsumed`, hoisted
 * `listState`) so return-from-detail restores the originating tile.
 * Kind label renders only when the payload provides one.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCollectionsRow(
    title: String,
    items: List<CollectionItem>,
    modifier: Modifier = Modifier,
    onItemClick: (CollectionItem) -> Unit,
    rowId: String = "",
    focusKey: String? = null,
    onFocusKeyConsumed: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    // Content-to-bar escape for every tile (see topEdgeBarEscape).
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
            // Plain rail title: never focusable, so D-pad skips straight to tiles.
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            modifier = Modifier.then(if (railLink != null) Modifier.focusRequester(railLink) else Modifier).focusRestorer()
        ) {
            itemsIndexed(items, key = { _, c -> c.id.ifBlank { c.slug } }) { index, collection ->
                val cardFocusRequester = remember { FocusRequester() }
                if (focusKey != null && focusKey == "$rowId/collection/${collection.slug}") {
                    LaunchedEffect(focusKey) {
                        try {
                            cardFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                        onFocusKeyConsumed()
                    }
                }
                val isLast = index == items.lastIndex
                columnCards?.put(index, cardFocusRequester)
                Box(
                    Modifier
                        .railFocusFrame()
                        .padding(vertical = TvSizing.RailFocusVertical)
                        .topEdgeBarEscape(barEscapeLink, onEscape = onBarEscape)
                        .columnHopKeys(outerListState, index, upColumn, downColumn)
                        .railHopKeys(outerListState, upLink, upRailIndex, downLink, downRailIndex)
                ) {
                    val subtitle = buildString {
                        if (collection.itemCount > 0) {
                            append(stringResource(R.string.collection_titles_format, collection.itemCount))
                        }
                        if (collection.kind.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(collection.kind.replaceFirstChar { it.uppercase() })
                        }
                    }
                    Button(
                        onClick = { onItemClick(collection) },
                        colors = ButtonDefaults.colors(
                            containerColor = NetflixDarkCard,
                            contentColor = NetflixWhite,
                            focusedContainerColor = NetflixRed,
                            focusedContentColor = NetflixWhite
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                        border = ButtonDefaults.border(
                            focusedBorder = Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, NetflixWhite)
                            )
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        modifier = Modifier
                            .width(240.dp)
                            .height(104.dp)
                            .focusRequester(cardFocusRequester)
                            .focusProperties {
                                if (upLink != null) up = upLink
                                if (downLink != null) down = downLink else down = FocusRequester.Cancel
                                if (index == 0) left = FocusRequester.Cancel
                                if (isLast) right = FocusRequester.Cancel
                            }
                    ) {
                        Column {
                            Text(
                                text = collection.title,
                                color = NetflixWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subtitle,
                                    color = NetflixLightGray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
