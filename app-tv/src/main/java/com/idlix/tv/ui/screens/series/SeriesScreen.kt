package com.idlix.tv.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.tv.R
import com.idlix.tv.ui.components.TvErrorState
import com.idlix.tv.ui.components.TvHeroBillboard
import com.idlix.tv.ui.components.RailColumnTarget
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.components.TvSwimlaneRow
import com.idlix.tv.ui.theme.NetflixBlack
import kotlinx.coroutines.delay

@Composable
fun SeriesScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onNavigateToPlayer: (contentType: String, contentId: String, title: String, poster: String?, backdrop: String?, slug: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = viewModel(),
    // Same-card refocus key, hoisted in AppNavigation (see HomeScreen).
    refocusKey: String? = null,
    onRefocusKeyChange: (String?) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    railListStates: (String) -> LazyListState = { LazyListState() },
    // Bar DOWN target on the hero Play button (see AppNavigation.contentEntryLink).
    entryLinkRequester: FocusRequester? = null,
    // Content-to-bar escape for hero + every rail (see topEdgeBarEscape).
    barLinkRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroFocusRequester = remember { FocusRequester() }
    // Browsing signal for the hero carousel (see TvHeroBillboard
    // rotationPaused): true while focus sits anywhere in this screen — hero
    // CTAs or any rail — so the backdrop never swaps mid-browse. Passive
    // observer only; attaches no focus target and touches no focus chain.
    var screenHasFocus by remember { mutableStateOf(false) }
    // Explicit UP chain (STUCK-1, see HomeScreen): one link per rail.
    val popularRailLink = remember { FocusRequester() }
    val latestRailLink = remember { FocusRequester() }
    val dramaRailLink = remember { FocusRequester() }
    val actionRailLink = remember { FocusRequester() }
    val comedyRailLink = remember { FocusRequester() }
    // Column-preserving UP/DOWN (see columnHopKeys): per-rail column
    // registries (index -> card requester), filled by the rails themselves.
    val popularCards = remember { mutableMapOf<Int, FocusRequester>() }
    val latestCards = remember { mutableMapOf<Int, FocusRequester>() }
    val dramaCards = remember { mutableMapOf<Int, FocusRequester>() }
    val actionCards = remember { mutableMapOf<Int, FocusRequester>() }
    val comedyCards = remember { mutableMapOf<Int, FocusRequester>() }
    // Scroll-to-top on genuine content→bar escape (see topEdgeBarEscape):
    // UP from a top-edge card lands on the tabs, so pull the hero back into
    // view underneath. Suspend lambda — the modifier launches it async, so
    // the focus hop never waits on (or loops from) the scroll. Tab
    // LEFT/RIGHT and entry focus never touch this path (UP key only).
    val scrollToTop: suspend () -> Unit = {
        try {
            listState.scrollToItem(0)
        } catch (_: Exception) {}
    }
    // Disposal-proof hop (DISPOSE-1, option b — see HomeScreen): dynamic column
    // order hero + non-empty rails only. Empty rails are skipped entirely
    // (no outer item, no link): neighbors hop directly past them so UP/DOWN
    // never lands on a detached container (dead).
    val railOrder = buildList {
        add("hero")
        if (uiState.popularSeries.isNotEmpty()) add("popular")
        if (uiState.latestSeries.isNotEmpty()) add("latest")
        if (uiState.dramaSeries.isNotEmpty()) add("drama")
        if (uiState.actionSeries.isNotEmpty()) add("action")
        if (uiState.comedySeries.isNotEmpty()) add("comedy")
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
        "hero" -> heroFocusRequester
        "popular" -> popularRailLink
        "latest" -> latestRailLink
        "drama" -> dramaRailLink
        "action" -> actionRailLink
        "comedy" -> comedyRailLink
        else -> null
    }
    fun cardsFor(id: String?): MutableMap<Int, FocusRequester>? = when (id) {
        "popular" -> popularCards
        "latest" -> latestCards
        "drama" -> dramaCards
        "action" -> actionCards
        "comedy" -> comedyCards
        else -> null
    }
    fun sizeFor(id: String?): Int = when (id) {
        "popular" -> uiState.popularSeries.size
        "latest" -> uiState.latestSeries.size
        "drama" -> uiState.dramaSeries.size
        "action" -> uiState.actionSeries.size
        "comedy" -> uiState.comedySeries.size
        else -> 0
    }
    fun railStateFor(id: String?): LazyListState? = when (id) {
        "popular" -> railListStates("popular")
        "latest" -> railListStates("latest")
        "drama" -> railListStates("drama")
        "action" -> railListStates("action")
        "comedy" -> railListStates("comedy")
        else -> null
    }
    fun columnFor(id: String?): RailColumnTarget? {
        if (id == null || id == "hero") return null
        val sz = sizeFor(id)
        if (sz <= 0) return null
        val idx = railIdx(id)
        if (idx < 0) return null
        return RailColumnTarget(railStateFor(id), cardsFor(id), linkFor(id), sz, idx)
    }
    val navigateToDetail: (String, String, String) -> Unit = { railId, type, slug ->
        onRefocusKeyChange("$railId/$type/$slug")
        onNavigateToDetail(type, slug)
    }
    // The NavHost graph does not rebuild on same-branch recomposition, so a
    // consume never propagates back to THIS composition (see HomeScreen).
    var refocusConsumed by remember { mutableStateOf(false) }
    val clearFocusKey: () -> Unit = {
        refocusConsumed = true
        onRefocusKeyChange(null)
    }
    // Live view of the key for the watchdog (belt and suspenders).
    val latestKey = rememberUpdatedState(refocusKey)

    // No pending key: park on hero Play. `firstLoad` gates this to the first
    // settled run of THIS screen instance (see HomeScreen).
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(uiState.isLoading, uiState.heroSeries) {
        if (uiState.isLoading) return@LaunchedEffect
        val first = firstLoad
        firstLoad = false
        if (uiState.heroSeries != null && refocusKey == null && first) {
            try {
                heroFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Return from detail: scroll to the originating rail + card so it
    // recomposes; its match effect then requests focus and clears the key.
    // Rail order MUST mirror the LazyColumn below (hero, popular…).
    LaunchedEffect(refocusKey, uiState.isLoading) {
        val key = refocusKey ?: return@LaunchedEffect
        if (uiState.isLoading || uiState.heroSeries == null) return@LaunchedEffect
        if (key == "hero") {
            onRefocusKeyChange(null)
            try {
                heroFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        val parts = key.split("/", limit = 3)
        if (parts.size != 3) {
            onRefocusKeyChange(null)
            try {
                heroFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        val (railId, type, slug) = parts
        val railLists = mapOf(
            "popular" to uiState.popularSeries,
            "latest" to uiState.latestSeries,
            "drama" to uiState.dramaSeries,
            "action" to uiState.actionSeries,
            "comedy" to uiState.comedySeries
        )
        val returnOrder = buildList {
            add("hero")
            if (uiState.popularSeries.isNotEmpty()) add("popular")
            if (uiState.latestSeries.isNotEmpty()) add("latest")
            if (uiState.dramaSeries.isNotEmpty()) add("drama")
            if (uiState.actionSeries.isNotEmpty()) add("action")
            if (uiState.comedySeries.isNotEmpty()) add("comedy")
        }
        val railIndex = returnOrder.indexOf(railId)
        val cardIndex = railLists[railId]
            ?.indexOfFirst { it.contentType == type && it.slug == slug } ?: -1
        if (railIndex == -1 || cardIndex == -1) {
            onRefocusKeyChange(null)
            try {
                heroFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        try {
            listState.scrollToItem(railIndex)
            railListStates(railId).scrollToItem(cardIndex)
        } catch (_: Exception) {
            onRefocusKeyChange(null)
            try {
                heroFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        // Watchdog: if the card never consumes the key, park on hero Play.
        delay(1500)
        if (!refocusConsumed && latestKey.value == key) {
            onRefocusKeyChange(null)
            try {
                heroFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .onFocusChanged { screenHasFocus = it.hasFocus }
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
                onRetry = { viewModel.loadSeriesData() },
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Settle guard (STUCK-1, see HomeScreen).
                contentPadding = PaddingValues(top = 12.dp)
            ) {
                // Hero TV Series Billboard
                item {
                    TvHeroBillboard(
                        movies = uiState.popularSeries.take(6),
                        playButtonModifier = Modifier
                            .focusRequester(heroFocusRequester)
                            .then(if (entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier),
                        onPlayClick = {
                            onRefocusKeyChange("hero")
                            onNavigateToDetail(it.contentType, it.slug)
                        },
                        onDetailsClick = {
                            onRefocusKeyChange("hero")
                            onNavigateToDetail(it.contentType, it.slug)
                        },
                        barEscapeLink = barLinkRequester,
                        onBarEscape = scrollToTop,
                        rotationPaused = screenHasFocus
                    )
                }

                // Swimlane: Serial TV Terpopuler
                if (uiState.popularSeries.isNotEmpty()) {
                    item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.popular_series),
                        items = uiState.popularSeries,
                        onItemClick = { navigateToDetail("popular", it.contentType, it.slug) },
                        rowId = "popular",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("popular"),
                        barEscapeLink = barLinkRequester,
                        onBarEscape = scrollToTop,
                        upLink = linkFor(prevRailId("popular")),
                        downLink = linkFor(nextRailId("popular")),
                        railLink = popularRailLink,
                        outerListState = listState,
                        upRailIndex = prevRailId("popular")?.let { railIdx(it) } ?: -1,
                        downRailIndex = nextRailId("popular")?.let { railIdx(it) } ?: -1,
                        // UP targets the hero (not a rail): legacy link wins.
                        upColumn = columnFor(prevRailId("popular")),
                        downColumn = columnFor(nextRailId("popular")),
                        columnCards = popularCards
                    )
                    }
                }

                // Swimlane: Serial TV Terbaru
                if (uiState.latestSeries.isNotEmpty()) {
                    item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.on_the_air_series),
                        items = uiState.latestSeries,
                        onItemClick = { navigateToDetail("latest", it.contentType, it.slug) },
                        rowId = "latest",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("latest"),
                        barEscapeLink = barLinkRequester,
                        onBarEscape = scrollToTop,
                        upLink = linkFor(prevRailId("latest")),
                        downLink = linkFor(nextRailId("latest")),
                        railLink = latestRailLink,
                        outerListState = listState,
                        upRailIndex = prevRailId("latest")?.let { railIdx(it) } ?: -1,
                        downRailIndex = nextRailId("latest")?.let { railIdx(it) } ?: -1,
                        upColumn = columnFor(prevRailId("latest")),
                        downColumn = columnFor(nextRailId("latest")),
                        columnCards = latestCards
                    )
                    }
                }

                // Swimlane: Serial Drama
                if (uiState.dramaSeries.isNotEmpty()) {
                    item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_drama),
                        items = uiState.dramaSeries,
                        onItemClick = { navigateToDetail("drama", it.contentType, it.slug) },
                        rowId = "drama",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("drama"),
                        barEscapeLink = barLinkRequester,
                        onBarEscape = scrollToTop,
                        upLink = linkFor(prevRailId("drama")),
                        downLink = linkFor(nextRailId("drama")),
                        railLink = dramaRailLink,
                        outerListState = listState,
                        upRailIndex = prevRailId("drama")?.let { railIdx(it) } ?: -1,
                        downRailIndex = nextRailId("drama")?.let { railIdx(it) } ?: -1,
                        upColumn = columnFor(prevRailId("drama")),
                        downColumn = columnFor(nextRailId("drama")),
                        columnCards = dramaCards
                    )
                    }
                }

                // Swimlane: Serial Aksi & Petualangan
                if (uiState.actionSeries.isNotEmpty()) {
                    item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_action),
                        items = uiState.actionSeries,
                        onItemClick = { navigateToDetail("action", it.contentType, it.slug) },
                        rowId = "action",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("action"),
                        barEscapeLink = barLinkRequester,
                        onBarEscape = scrollToTop,
                        upLink = linkFor(prevRailId("action")),
                        downLink = linkFor(nextRailId("action")),
                        railLink = actionRailLink,
                        outerListState = listState,
                        upRailIndex = prevRailId("action")?.let { railIdx(it) } ?: -1,
                        downRailIndex = nextRailId("action")?.let { railIdx(it) } ?: -1,
                        upColumn = columnFor(prevRailId("action")),
                        downColumn = columnFor(nextRailId("action")),
                        columnCards = actionCards
                    )
                    }
                }

                // Swimlane: Serial Komedi
                if (uiState.comedySeries.isNotEmpty()) {
                    item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_comedy),
                        items = uiState.comedySeries,
                        onItemClick = { navigateToDetail("comedy", it.contentType, it.slug) },
                        rowId = "comedy",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("comedy"),
                        barEscapeLink = barLinkRequester,
                        onBarEscape = scrollToTop,
                        upLink = linkFor(prevRailId("comedy")),
                        downLink = linkFor(nextRailId("comedy")),
                        railLink = comedyRailLink,
                        outerListState = listState,
                        upRailIndex = prevRailId("comedy")?.let { railIdx(it) } ?: -1,
                        downRailIndex = nextRailId("comedy")?.let { railIdx(it) } ?: -1,
                        upColumn = columnFor(prevRailId("comedy")),
                        downColumn = columnFor(nextRailId("comedy")),
                        columnCards = comedyCards
                    )
                    }
                }
            }
        }
    }
}
