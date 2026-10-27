package com.idlix.tv.ui.screens.genres

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.idlix.tv.ui.components.TvTopBarHeight
import com.idlix.tv.ui.components.railFocusFrame
import com.idlix.tv.ui.components.topEdgeBarEscape
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GenresScreen(
    onNavigateToGenre: (slug: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GenresViewModel = viewModel(),
    // Genre-detail return key: the originating genre slug. Hoisted in
    // AppNavigation (same reason as the Movies/Series tab keys — the grid
    // disposes on genre-detail push, so an in-screen key would die there).
    refocusKey: String? = null,
    onRefocusKeyChange: (String?) -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
    // Bar DOWN target on the first tile (see AppNavigation.contentEntryLink).
    entryLinkRequester: FocusRequester? = null,
    // Content-to-bar escape for first-row tiles (see topEdgeBarEscape).
    barLinkRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val firstTileFocusRequester = remember { FocusRequester() }
    val navigateToGenre: (String, String) -> Unit = { slug, name ->
        onRefocusKeyChange(slug)
        onNavigateToGenre(slug, name)
    }
    // Screen-local consume flag: the NavHost graph does not rebuild on
    // same-branch recomposition, so the consume never propagates back to
    // THIS composition (see MoviesScreen).
    var refocusConsumed by remember { mutableStateOf(false) }
    val clearFocusKey: () -> Unit = {
        refocusConsumed = true
        onRefocusKeyChange(null)
    }

    // No pending key: park on the first tile. `firstLoad` gates this to the
    // first settled run of THIS screen instance so a late data re-emission
    // never steals a restored tile's focus.
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(uiState.isLoading, uiState.genres) {
        if (uiState.isLoading) return@LaunchedEffect
        val first = firstLoad
        firstLoad = false
        if (uiState.genres.isNotEmpty() && refocusKey == null && first) {
            try {
                firstTileFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Return from genre detail: scroll the originating tile back into view;
    // its match effect requests focus and clears the key.
    LaunchedEffect(refocusKey, uiState.isLoading) {
        val key = refocusKey ?: return@LaunchedEffect
        if (uiState.isLoading || uiState.genres.isEmpty()) return@LaunchedEffect
        val index = uiState.genres.indexOfFirst { it.slug == key }
        if (index == -1) {
            onRefocusKeyChange(null)
            try {
                firstTileFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        try {
            gridState.scrollToItem(index)
        } catch (_: Exception) {
            onRefocusKeyChange(null)
            try {
                firstTileFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        // Watchdog: if the tile never consumes the key, park on first tile.
        kotlinx.coroutines.delay(1500)
        if (!refocusConsumed) {
            onRefocusKeyChange(null)
            try {
                firstTileFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .padding(horizontal = 48.dp, vertical = 20.dp)
            .padding(top = TvTopBarHeight)
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
                onRetry = { viewModel.loadGenres() },
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.genres),
                    color = NetflixWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(180.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.genres, key = { it.slug.ifBlank { it.id } }) { genre ->
                        val isFirst = genre == uiState.genres.firstOrNull()
                        val tileFocusRequester = remember { FocusRequester() }
                        if (refocusKey != null && refocusKey == genre.slug) {
                            LaunchedEffect(refocusKey) {
                                try {
                                    tileFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                                clearFocusKey()
                            }
                        }
                        // 12dp focus frame: same 1.12x-ring headroom idiom as
                        // the search-results grid.
                        Box(
                            Modifier
                                .railFocusFrame()
                                .padding(vertical = 12.dp)
                        ) {
                            Button(
                                onClick = { navigateToGenre(genre.slug, genre.name) },
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
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .focusRequester(tileFocusRequester)
                                    .then(if (isFirst) Modifier.focusRequester(firstTileFocusRequester) else Modifier)
                                    .then(if (isFirst && entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier)
                                    .topEdgeBarEscape(barLinkRequester)
                            ) {
                                Text(
                                    text = genre.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
