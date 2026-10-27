package com.idlix.tv.ui.screens.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
fun MoviesScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onNavigateToPlayer: (contentType: String, contentId: String, title: String, poster: String?, backdrop: String?, slug: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoviesViewModel = viewModel(),
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
    // The NavHost graph does not rebuild on same-branch recomposition, so the
    // consume never propagates back to THIS composition. This screen-local
    // flag is the reliable consume signal: state reads at effect-check time
    // are always live, no recomposition needed.
    var refocusConsumed by remember { mutableStateOf(false) }
    val clearFocusKey: () -> Unit = {
        refocusConsumed = true
        onRefocusKeyChange(null)
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

    // Return from detail: scroll to the originating rail + card so it
    // recomposes; its match effect then requests focus and clears the key.
    // Rail order MUST mirror the LazyColumn below (hero, popular…).
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
        val railLists = mapOf(
            "popular" to uiState.popularMovies,
            "latest" to uiState.latestMovies,
            "action" to uiState.actionMovies,
            "comedy" to uiState.comedyMovies,
            "horror" to uiState.horrorMovies,
            "scifi" to uiState.sciFiMovies
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
        // `refocusConsumed` is screen-local (see above); the hoisted key can
        // no longer be trusted here because the consume never propagates back
        // through the frozen NavHost graph.
        delay(1500)
        if (!refocusConsumed) {
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
                onRetry = { viewModel.loadMoviesData() },
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 12dp top headroom for the hero-absent case (same as home).
                contentPadding = PaddingValues(top = 12.dp)
            ) {
                // Hero Movie Billboard
                item {
                    TvHeroBillboard(
                        movies = uiState.popularMovies.take(6),
                        playButtonModifier = Modifier
                            .focusRequester(heroFocusRequester)
                            .then(if (entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier),
                        onPlayClick = {
                            onNavigateToPlayer(it.contentType, it.id, it.title, it.fullPosterUrl, it.fullBackdropUrl, it.slug)
                        },
                        onDetailsClick = {
                            onRefocusKeyChange("hero")
                            onNavigateToDetail(it.contentType, it.slug)
                        },
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Film Terpopuler
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.popular_movies_indonesia),
                        items = uiState.popularMovies,
                        onItemClick = { navigateToDetail("popular", it.contentType, it.slug) },
                        rowId = "popular",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("popular"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Film Terbaru
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.latest_movies),
                        items = uiState.latestMovies,
                        onItemClick = { navigateToDetail("latest", it.contentType, it.slug) },
                        rowId = "latest",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("latest"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Film Aksi
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

                // Swimlane: Film Komedi
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.movies_comedy),
                        items = uiState.comedyMovies,
                        onItemClick = { navigateToDetail("comedy", it.contentType, it.slug) },
                        rowId = "comedy",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("comedy"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Film Horor
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.movies_horror),
                        items = uiState.horrorMovies,
                        onItemClick = { navigateToDetail("horror", it.contentType, it.slug) },
                        rowId = "horror",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("horror"),
                        barEscapeLink = barLinkRequester
                    )
                }

                // Swimlane: Film Fiksi Ilmiah (Sci-Fi)
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.movies_scifi),
                        items = uiState.sciFiMovies,
                        onItemClick = { navigateToDetail("scifi", it.contentType, it.slug) },
                        rowId = "scifi",
                        focusKey = refocusKey,
                        onFocusKeyConsumed = clearFocusKey,
                        listState = railListStates("scifi"),
                        barEscapeLink = barLinkRequester
                    )
                }
            }
        }
    }
}
