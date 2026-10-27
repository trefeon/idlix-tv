package com.idlix.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.tv.R
import com.idlix.data.model.MovieItem
import com.idlix.data.model.CollectionItem
import com.idlix.tv.ui.components.TvCollectionsRow
import com.idlix.tv.ui.components.TvContinueWatchingRow
import com.idlix.tv.ui.components.TvErrorState
import com.idlix.tv.ui.components.TvHeroBillboard
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.components.TvSwimlaneRow
import com.idlix.tv.ui.components.TvTop10Row
import com.idlix.tv.ui.theme.NetflixBlack
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onNavigateToPlayer: (contentType: String, contentId: String, title: String, poster: String?, backdrop: String?, slug: String?, season: Int?, episode: Int?) -> Unit,
    onNavigateToCollection: (slug: String, name: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    // Same-card refocus key, hoisted in AppNavigation above the dual-NavHost
    // switch (in-screen remember/rememberSaveable dies with the host dispose).
    // "railId/type/slug", "hero" for hero-initiated detail, null when idle.
    refocusKey: String? = null,
    onRefocusKeyChange: (String?) -> Unit = {},
    // Hoisted scroll states (see AppNavigation): the return effect scrolls
    // these back so the originating card recomposes and takes focus.
    listState: LazyListState = rememberLazyListState(),
    railListStates: (String) -> LazyListState = { LazyListState() },
    // Bar DOWN target, attached to the hero Play button alongside the local
    // entry requester (see AppNavigation.contentEntryLink).
    entryLinkRequester: FocusRequester? = null,
    // Content-to-bar escape for hero + every rail (see topEdgeBarEscape).
    barLinkRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroFocusRequester = remember { FocusRequester() }
    // The matching rail card self-requests focus and clears the key via
    // onRefocusKeyChange(null); a stale/gone key falls back to hero Play
    // after a short settle window. Null key = cold launch / tab entry, which
    // parks on hero Play exactly as before.
    val navigateToDetail: (String, String, String) -> Unit = { railId, type, slug ->
        onRefocusKeyChange("$railId/$type/$slug")
        onNavigateToDetail(type, slug)
    }
    val navigateToCollection: (CollectionItem) -> Unit = { collection ->
        onRefocusKeyChange("collections/collection/${collection.slug}")
        onNavigateToCollection(collection.slug, collection.title)
    }
    // The NavHost graph does not rebuild on same-branch recomposition, so a
    // consume never propagates back to THIS composition. This screen-local
    // flag is the reliable consume signal: state reads at effect-check time
    // are always live, no recomposition needed.
    var refocusConsumed by remember { mutableStateOf(false) }
    val clearFocusKey: () -> Unit = {
        refocusConsumed = true
        onRefocusKeyChange(null)
    }
    // Live view of the key for the watchdog (belt and suspenders alongside
    // refocusConsumed, in case a future navigation version propagates).
    val latestKey = rememberUpdatedState(refocusKey)

    // Refresh continue watching list on screen entrance / return
    LaunchedEffect(Unit) {
        viewModel.refreshContinueWatching()
    }

    // No pending key: park on hero Play (cold launch, tab entry, player
    // return). `firstLoad` gates this to the first settled run of THIS screen
    // instance: after a rail-card consume, a late data re-emission must NOT
    // refire a hero request and steal the restored card focus.
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(uiState.isLoading, uiState.heroMovie) {
        if (uiState.isLoading) return@LaunchedEffect
        val first = firstLoad
        firstLoad = false
        if (uiState.heroMovie != null && refocusKey == null && first) {
            try {
                heroFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Return from detail: scroll the outer list to the originating rail and
    // the rail to the originating card so the card recomposes; its match
    // effect then requests focus and clears the key. A stale/gone key (or a
    // scroll that never yields a match) falls back to hero Play.
    // Rail order MUST mirror the LazyColumn below (hero, [continue], rails…).
    LaunchedEffect(refocusKey, uiState.isLoading) {
        val key = refocusKey ?: return@LaunchedEffect
        if (uiState.isLoading || uiState.heroMovie == null) return@LaunchedEffect
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
        val railOrder = buildList {
            add("hero")
            if (uiState.continueWatching.isNotEmpty()) add("continue")
            add("trending")
            add("top10")
            add("series")
            if (uiState.actionMovies.isNotEmpty()) add("action")
            if (uiState.collections.isNotEmpty()) add("collections")
        }
        val railIndex = railOrder.indexOf(railId)
        val cardIndex = when (railId) {
            "trending" -> uiState.trending.indexOfFirst { it.contentType == type && it.slug == slug }
            "top10" -> uiState.popularMovies.take(10).indexOfFirst { it.contentType == type && it.slug == slug }
            "series" -> uiState.popularSeries.indexOfFirst { it.slug == slug }
            "action" -> uiState.actionMovies.indexOfFirst { it.contentType == type && it.slug == slug }
            "collections" -> uiState.collections.indexOfFirst { it.slug == slug }
            else -> -1
        }
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
                onRetry = { viewModel.loadHomeData() },
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 12dp top headroom: when the hero is absent (still loading)
                // the first rail settles at the viewport top — this keeps the
                // 1.12x focus ring off the clip edge. Invisible under the
                // full-bleed hero (black on black).
                contentPadding = PaddingValues(top = 12.dp)
            ) {
                // 1. Dynamic Hero Billboard
                item {
                    TvHeroBillboard(
                        movies = uiState.trending.take(6),
                        playButtonModifier = Modifier
                            .focusRequester(heroFocusRequester)
                            .then(if (entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier),
                        onPlayClick = {
                            if (it.contentType == "tv_series") {
                                onRefocusKeyChange("hero")
                                onNavigateToDetail(it.contentType, it.slug)
                            } else {
                                onNavigateToPlayer(
                                    it.contentType,
                                    it.id,
                                    it.title,
                                    it.fullPosterUrl,
                                    it.fullBackdropUrl,
                                    it.slug,
                                    null,
                                    null
                                )
                            }
                        },
                        onDetailsClick = {
                            onRefocusKeyChange("hero")
                            onNavigateToDetail(it.contentType, it.slug)
                        },
                        barEscapeLink = barLinkRequester
                    )
                }

                // 2. Bar Paling Pertama: Lanjutkan Menonton (hanya muncul jika ada histori yang belum kelar)
                if (uiState.continueWatching.isNotEmpty()) {
                    item {
                        TvContinueWatchingRow(
                            title = stringResource(R.string.continue_watching),
                            items = uiState.continueWatching,
                            onItemClick = { item ->
                                onNavigateToPlayer(
                                    item.contentType,
                                    item.contentId,
                                    item.title,
                                    item.posterUrl,
                                    item.backdropUrl,
                                    item.slug,
                                    item.seasonNumber,
                                    item.episodeNumber
                                )
                            },
                            barEscapeLink = barLinkRequester
                        )
                    }
                }

                // 3. Swimlane: Sedang Tren Sekarang
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.trending_now),
                        items = uiState.trending,
                        onItemClick = { navigateToDetail("trending", it.contentType, it.slug) },
                        rowId = "trending",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("trending"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // 4. Top-10: oversized rank numerals + compact posters
                item {
                    TvTop10Row(
                        title = stringResource(R.string.top_10_movies),
                        items = uiState.popularMovies,
                        onItemClick = { navigateToDetail("top10", it.contentType, it.slug) },
                        rowId = "top10",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("top10"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // 5. Swimlane: Serial TV Pilihan
                item {
                    val seriesAsMovies = remember(uiState.popularSeries) {
                        uiState.popularSeries.map { s ->
                            MovieItem(
                                id = s.id,
                                title = s.title,
                                slug = s.slug,
                                posterPath = s.posterPath,
                                backdropPath = s.backdropPath,
                                voteAverage = s.voteAverage,
                                contentType = "tv_series"
                            )
                        }
                    }
                    TvSwimlaneRow(
                        title = stringResource(R.string.popular_series),
                        items = seriesAsMovies,
                        onItemClick = { navigateToDetail("series", it.contentType, it.slug) },
                        rowId = "series",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("series"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // 6. Swimlane: Film Aksi
                if (uiState.actionMovies.isNotEmpty()) {
                    item {
                        TvSwimlaneRow(
                            title = stringResource(R.string.movies_action),
                            items = uiState.actionMovies,
                            onItemClick = { navigateToDetail("action", it.contentType, it.slug) },
                            rowId = "action",
                            focusKey = refocusKey,
                            onFocusKeyConsumed = clearFocusKey,
                            listState = railListStates("action"),
                            barEscapeLink = barLinkRequester
                        )
                    }

                    // 7. Swimlane: Collections (curated universes/franchises → detail phases)
                    if (uiState.collections.isNotEmpty()) {
                        item {
                            TvCollectionsRow(
                                title = stringResource(R.string.collections),
                                items = uiState.collections,
                                onItemClick = { navigateToCollection(it) },
                                rowId = "collections",
                                focusKey = refocusKey,
                                onFocusKeyConsumed = clearFocusKey,
                                listState = railListStates("collections"),
                                barEscapeLink = barLinkRequester
                            )
                        }
                    }
                }
            }
        }
    }
}
