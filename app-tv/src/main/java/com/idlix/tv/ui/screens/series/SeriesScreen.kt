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
        val railOrder = listOf("hero") + railLists.keys.toList()
        val railIndex = railOrder.indexOf(railId)
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
                // 12dp top headroom for the hero-absent case (same as home).
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
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Serial TV Terpopuler
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.popular_series),
                        items = uiState.popularSeries,
                        onItemClick = { navigateToDetail("popular", it.contentType, it.slug) },
                        rowId = "popular",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("popular"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Serial TV Terbaru
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.on_the_air_series),
                        items = uiState.latestSeries,
                        onItemClick = { navigateToDetail("latest", it.contentType, it.slug) },
                        rowId = "latest",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("latest"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Serial Drama
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_drama),
                        items = uiState.dramaSeries,
                        onItemClick = { navigateToDetail("drama", it.contentType, it.slug) },
                        rowId = "drama",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("drama"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Serial Aksi & Petualangan
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_action),
                        items = uiState.actionSeries,
                        onItemClick = { navigateToDetail("action", it.contentType, it.slug) },
                        rowId = "action",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("action"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Serial Komedi
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_comedy),
                        items = uiState.comedySeries,
                        onItemClick = { navigateToDetail("comedy", it.contentType, it.slug) },
                        rowId = "comedy",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("comedy"),
                        barEscapeLink = barLinkRequester
                    )
                }
            }
        }
    }
}
