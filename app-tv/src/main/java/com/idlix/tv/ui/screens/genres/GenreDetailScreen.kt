package com.idlix.tv.ui.screens.genres

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.focus.focusProperties
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
import com.idlix.tv.ui.components.RailColumnTarget
import com.idlix.tv.ui.components.TvErrorState
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.components.TvSwimlaneRow
import com.idlix.tv.ui.components.dpadBackHandler
import com.idlix.tv.ui.components.topEdgeBarEscape
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    genreSlug: String,
    genreName: String,
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GenreDetailViewModel = viewModel(),
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
    // Explicit UP chain (STUCK-1, see HomeScreen): one link per rail.
    // Neighbors resolve via prev/nextRailId so empty rails are skipped.
    val moviesRailLink = remember { FocusRequester() }
    val seriesRailLink = remember { FocusRequester() }
    // Column-preserving UP/DOWN (see columnHopKeys): per-rail column
    // registries (index -> card requester), filled by the rails themselves.
    val moviesCards = remember { mutableMapOf<Int, FocusRequester>() }
    val seriesCards = remember { mutableMapOf<Int, FocusRequester>() }
    // Dynamic column order: header + non-empty rails only. Empty rails are
    // skipped entirely (no outer item, no link): neighbors hop directly past
    // them so UP/DOWN never lands on a detached container (dead).
    // Rail order MUST mirror the LazyColumn items below.
    val railOrder = buildList {
        add("header")
        if (uiState.movies.isNotEmpty()) add("movies")
        if (uiState.series.isNotEmpty()) add("series")
    }
    fun railIdx(id: String): Int = railOrder.indexOf(id)
    fun prevRailId(id: String): String? {
        val i = railOrder.indexOf(id)
        if (i <= 0) return null
        return railOrder[i - 1]
    }
    fun nextRailId(id: String): String? {
        val i = railOrder.indexOf(id)
        if (i == -1) return null
        return railOrder.getOrNull(i + 1)
    }
    fun linkFor(id: String?): FocusRequester? = when (id) {
        "header" -> backFocusRequester
        "movies" -> moviesRailLink
        "series" -> seriesRailLink
        else -> null
    }
    fun cardsFor(id: String?): MutableMap<Int, FocusRequester>? = when (id) {
        "movies" -> moviesCards
        "series" -> seriesCards
        else -> null
    }
    fun sizeFor(id: String?): Int = when (id) {
        "movies" -> uiState.movies.size
        "series" -> uiState.series.size
        else -> 0
    }
    fun railStateFor(id: String?): LazyListState? = when (id) {
        "movies" -> railListStates("movies")
        "series" -> railListStates("series")
        else -> null
    }
    fun columnFor(id: String?): RailColumnTarget? {
        if (id == null || id == "header") return null
        val sz = sizeFor(id)
        if (sz <= 0) return null
        val idx = railIdx(id)
        if (idx < 0) return null
        return RailColumnTarget(
            railStateFor(id),
            cardsFor(id),
            linkFor(id),
            sz,
            idx
        )
    }
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

    LaunchedEffect(genreSlug) {
        refocusConsumed = false
        viewModel.loadGenre(genreSlug)
    }

    // No pending key: park on Back (this screen has no hero Play to park on).
    // `firstLoad` gates this to the first settled run of THIS genre instance.
    // The empty guard is load-bearing: the ViewModel's initial state is
    // (not-loading, empty) and loadGenre() is dispatched from an effect, so an
    // unguarded first read burns firstLoad before loading starts and entry
    // focus never fires. The delay lets the nav transition settle on
    // instant (cached) loads, mirroring DetailScreen.
    var firstLoad by remember(genreSlug) { mutableStateOf(true) }
    LaunchedEffect(uiState.isLoading, uiState.movies, uiState.series) {
        if (uiState.isLoading) return@LaunchedEffect
        if (uiState.movies.isEmpty() && uiState.series.isEmpty()) return@LaunchedEffect
        val first = firstLoad
        firstLoad = false
        if ((uiState.movies.isNotEmpty() || uiState.series.isNotEmpty()) &&
            refocusKey == null && first
        ) {
            delay(120)
            try {
                backFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Return from content detail: scroll to the originating rail + card so it
    // recomposes; its match effect then requests focus and clears the key.
    // Rail order MUST mirror the LazyColumn below (header + non-empty).
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
        val railLists = mapOf(
            "movies" to uiState.movies,
            "series" to uiState.series
        )
        val railOrder = buildList {
            add("header")
            if (uiState.movies.isNotEmpty()) add("movies")
            if (uiState.series.isNotEmpty()) add("series")
        }
        val railIndex = railOrder.indexOf(railId)
        val cardIndex = railLists[railId]
            ?.indexOfFirst { it.contentType == type && it.slug == slug } ?: -1
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
            .dpadBackHandler(onBack)
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
                onRetry = { viewModel.loadGenre(genreSlug) },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Shell pads the viewport below the bar (see AppNavigation);
                // this 12dp keeps the settled ring off the clip edge.
                contentPadding = PaddingValues(top = 12.dp)
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
                                .focusProperties { left = FocusRequester.Cancel }
                                .topEdgeBarEscape(barLinkRequester)
                        ) {
                            Text(text = stringResource(R.string.back), fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = genreName,
                            color = NetflixWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (uiState.movies.isNotEmpty()) {
                    item {
                        TvSwimlaneRow(
                            title = stringResource(R.string.movies),
                            items = uiState.movies,
                            onItemClick = { navigateToDetail("movies", it.contentType, it.slug) },
                            rowId = "movies",
                            focusKey = refocusKey,
                            onFocusKeyConsumed = clearFocusKey,
                            listState = railListStates("movies"),
                            // Disposal-proof hop (DISPOSE-1, option b — see
                            // HomeScreen): dynamic order header + non-empty.
                            // In-viewport targets use the declarative links;
                            // off-screen targets scroll-then-focus via
                            // railHopKeys; same-column via columnHopKeys.
                            barEscapeLink = barLinkRequester,
                            upLink = linkFor(prevRailId("movies")),
                            downLink = linkFor(nextRailId("movies")),
                            railLink = moviesRailLink,
                            outerListState = listState,
                            upRailIndex = prevRailId("movies")?.let { railIdx(it) } ?: -1,
                            downRailIndex = nextRailId("movies")?.let { railIdx(it) } ?: -1,
                            upColumn = columnFor(prevRailId("movies")),
                            downColumn = columnFor(nextRailId("movies")),
                            columnCards = moviesCards
                        )
                    }
                }
                if (uiState.series.isNotEmpty()) {
                    item {
                        TvSwimlaneRow(
                            title = stringResource(R.string.series),
                            items = uiState.series,
                            onItemClick = { navigateToDetail("series", it.contentType, it.slug) },
                            rowId = "series",
                            focusKey = refocusKey,
                            onFocusKeyConsumed = clearFocusKey,
                            listState = railListStates("series"),
                            barEscapeLink = barLinkRequester,
                            upLink = linkFor(prevRailId("series")),
                            downLink = linkFor(nextRailId("series")),
                            railLink = seriesRailLink,
                            outerListState = listState,
                            upRailIndex = prevRailId("series")?.let { railIdx(it) } ?: -1,
                            downRailIndex = nextRailId("series")?.let { railIdx(it) } ?: -1,
                            upColumn = columnFor(prevRailId("series")),
                            downColumn = columnFor(nextRailId("series")),
                            columnCards = seriesCards
                        )
                    }
                }
            }
        }
    }
}
