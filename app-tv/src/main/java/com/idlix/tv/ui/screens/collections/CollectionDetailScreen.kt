package com.idlix.tv.ui.screens.collections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.components.TvErrorState
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.components.TvSwimlaneRow
import com.idlix.tv.ui.components.TvTopBarHeight
import com.idlix.tv.ui.components.topEdgeBarEscape
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

/**
 * Collection detail: one rail per phase section (section titles map to TV
 * rows 1:1), cards navigate via the existing content-detail flow. Mirrors
 * [com.idlix.tv.ui.screens.genres.GenreDetailScreen] (Back-owned entry
 * focus, same-card refocus, section rails) — sections are dynamic (phase
 * rails) instead of the fixed movies/series pair.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionSlug: String,
    collectionName: String,
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionDetailViewModel = viewModel(),
    // Same-card refocus key, hoisted in AppNavigation (see MoviesScreen).
    refocusKey: String? = null,
    onRefocusKeyChange: (String?) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    railListStates: (String) -> LazyListState = { LazyListState() },
    // Bar DOWN target on the Back button (see AppNavigation.contentEntryLink).
    entryLinkRequester: FocusRequester? = null,
    // Content-to-bar escape for Back + rails (see topEdgeBarEscape).
    barLinkRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val backFocusRequester = remember { FocusRequester() }
    val navigateToDetail: (String, String, String) -> Unit = { railId, type, slug ->
        onRefocusKeyChange("$railId/$type/$slug")
        onNavigateToDetail(type, slug)
    }
    var refocusConsumed by remember { mutableStateOf(false) }
    val clearFocusKey: () -> Unit = {
        refocusConsumed = true
        onRefocusKeyChange(null)
    }
    val latestKey = rememberUpdatedState(refocusKey)

    // Detail rails render MovieItem; section entries map once per load.
    val sectionRails = remember(uiState.sections) {
        uiState.sections.map { section ->
            val railId = section.slug.ifBlank { section.id }
            railId to section.items.map { it.toMovieItem() }
        }
    }

    LaunchedEffect(collectionSlug) {
        refocusConsumed = false
        viewModel.loadCollection(collectionSlug)
    }

    // No pending key: park on Back (this screen has no hero Play to park on).
    // `firstLoad` gates this to the first settled run of THIS collection instance.
    // The empty guard is load-bearing: the ViewModel's initial state is
    // (not-loading, empty) and loadCollection() is dispatched from an effect,
    // so an unguarded first read burns firstLoad before loading starts and
    // entry focus never fires. The delay lets the nav transition settle on
    // instant (cached) loads, mirroring DetailScreen.
    var firstLoad by remember(collectionSlug) { mutableStateOf(true) }
    LaunchedEffect(uiState.isLoading, uiState.sections) {
        if (uiState.isLoading) return@LaunchedEffect
        if (uiState.sections.isEmpty()) return@LaunchedEffect
        val first = firstLoad
        firstLoad = false
        if (uiState.sections.isNotEmpty() && refocusKey == null && first) {
            delay(120)
            try {
                backFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Return from content detail: scroll to the originating rail + card so it
    // recomposes; its match effect then requests focus and clears the key.
    // Rail order MUST mirror the LazyColumn below (header, then sections).
    LaunchedEffect(refocusKey, uiState.isLoading) {
        val key = refocusKey ?: return@LaunchedEffect
        if (uiState.isLoading) return@LaunchedEffect
        if (key == "header") {
            onRefocusKeyChange(null)
            try {
                backFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        val parts = key.split("/", limit = 3)
        if (parts.size != 3) {
            onRefocusKeyChange(null)
            try {
                backFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        val (railId, type, slug) = parts
        val railOrder = listOf("header") + sectionRails.map { it.first }
        val railIndex = railOrder.indexOf(railId)
        val cardIndex = sectionRails.firstOrNull { it.first == railId }
            ?.second?.indexOfFirst { it.contentType == type && it.slug == slug } ?: -1
        if (railIndex == -1 || cardIndex == -1) {
            onRefocusKeyChange(null)
            try {
                backFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        try {
            listState.scrollToItem(railIndex)
            railListStates(railId).scrollToItem(cardIndex)
        } catch (_: Exception) {
            onRefocusKeyChange(null)
            try {
                backFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        // Watchdog: if the card never consumes the key, park on Back.
        delay(1500)
        if (!refocusConsumed && latestKey.value == key) {
            onRefocusKeyChange(null)
            try {
                backFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        if (uiState.isLoading) {
            TvLoadingSpinner(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (uiState.error != null || uiState.errorRes != null) {
            val errorMessage = uiState.error
                ?: uiState.errorRes?.let { stringResource(it) }.orEmpty()
            TvErrorState(
                message = errorMessage,
                onRetry = { viewModel.loadCollection(collectionSlug) },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = TvTopBarHeight + 12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixDarkCard,
                                contentColor = NetflixLightGray,
                                focusedContainerColor = NetflixRed,
                                focusedContentColor = NetflixWhite
                            ),
                            scale = ButtonDefaults.scale(focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, NetflixWhite)
                                )
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            modifier = Modifier
                                .focusRequester(backFocusRequester)
                                .then(if (entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier)
                                .topEdgeBarEscape(barLinkRequester)
                        ) {
                            Text(text = stringResource(R.string.back), fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = collectionName.ifBlank { collectionSlug },
                            color = NetflixWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                items(sectionRails, key = { it.first.ifBlank { it.second.firstOrNull()?.id ?: "" } }) { (railId, movies) ->
                    TvSwimlaneRow(
                        title = uiState.sections.firstOrNull {
                            it.slug.ifBlank { it.id } == railId
                        }?.title?.ifBlank { railId } ?: railId,
                        items = movies,
                        onItemClick = { navigateToDetail(railId, it.contentType, it.slug) },
                        rowId = railId,
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates(railId),
                        barEscapeLink = barLinkRequester
                    )
                }
            }
        }
    }
}
