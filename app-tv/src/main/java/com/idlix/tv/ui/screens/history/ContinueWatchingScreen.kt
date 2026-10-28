package com.idlix.tv.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.focusProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.components.TvContinueWatchingCard
import com.idlix.tv.ui.components.railFocusFrame
import com.idlix.tv.ui.components.topEdgeBarEscape
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinueWatchingScreen(
    onNavigateToPlayer: (contentType: String, contentId: String, title: String, poster: String?, backdrop: String?, slug: String?, season: Int?, episode: Int?) -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContinueWatchingViewModel = viewModel(),
    // Player-return key: contentId of the originating cell. Hoisted in
    // AppNavigation (same reason as the Genres grid key — the grid disposes
    // on player push, so an in-screen key would die there). Also carries the
    // delete-neighbor target so removing a cell never strands focus.
    refocusKey: String? = null,
    onRefocusKeyChange: (String?) -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
    // Bar DOWN target on the first Play button (see AppNavigation.contentEntryLink).
    entryLinkRequester: FocusRequester? = null,
    // Content-to-bar escape for first-row cards (see topEdgeBarEscape): the
    // grid scrolls under the floating bar and plain UP dies there (STUCK-1).
    barLinkRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    // Column-aware grid edges (see SearchScreen): Adaptive(230dp) span varies
    // with panel width. First-col LEFT stays, last-col RIGHT stays, last-row
    // DOWN stays; intra-grid moves stay on geometry.
    val screenWd = LocalConfiguration.current.screenWidthDp
    val cwCols = remember(screenWd) {
        val gridW = (screenWd - 96 - 24).coerceAtLeast(230)
        ((gridW + 24) / (230 + 24)).coerceAtLeast(1)
    }
    // Safe option ("No") owns focus while the destructive confirm is visible,
    // so a stray OK press can never land on "Yes, clear".
    val cancelFocusRequester = remember { FocusRequester() }
    // First Play owns entry focus (empty state parks on Explore instead).
    // Delayed like the detail screens: local history resolves instantly,
    // mid nav-transition, and an immediate request is silently dropped.
    // First-entry gated so deletes/clears never yank focus back to the top.
    val firstPlayFocusRequester = remember { FocusRequester() }
    val exploreFocusRequester = remember { FocusRequester() }
    // Screen-local consume flag: the NavHost graph does not rebuild on
    // same-branch recomposition, so the consume never propagates back to
    // THIS composition (see MoviesScreen).
    var refocusConsumed by remember { mutableStateOf(false) }
    val clearFocusKey: () -> Unit = {
        refocusConsumed = true
        onRefocusKeyChange(null)
    }
    var firstEntry by remember { mutableStateOf(true) }
    // Empty at any time — fresh, last-delete, or clear-all — parks on
    // Explore so deletes/clears never strand focus on a disposed node.
    LaunchedEffect(uiState.items, showClearConfirm) {
        if (showClearConfirm || uiState.items.isNotEmpty()) return@LaunchedEffect
        onRefocusKeyChange(null)
        kotlinx.coroutines.delay(120)
        try {
            exploreFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }
    // First entry with items parks on the first Play. Gated to the first
    // settled run so player returns and deletes never yank focus to the top.
    LaunchedEffect(uiState.items) {
        if (!firstEntry || uiState.items.isEmpty()) return@LaunchedEffect
        firstEntry = false
        kotlinx.coroutines.delay(120)
        try {
            firstPlayFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }
    // Player return / delete-neighbor: scroll the target cell back into view;
    // its Play match effect requests focus and clears the key.
    LaunchedEffect(refocusKey, uiState.items) {
        val key = refocusKey ?: return@LaunchedEffect
        if (uiState.items.isEmpty()) return@LaunchedEffect
        val index = uiState.items.indexOfFirst { it.contentId == key }
        if (index == -1) {
            onRefocusKeyChange(null)
            try {
                firstPlayFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        try {
            gridState.scrollToItem(index)
        } catch (_: Exception) {
            onRefocusKeyChange(null)
            try {
                firstPlayFocusRequester.requestFocus()
            } catch (_: Exception) {}
            return@LaunchedEffect
        }
        // Watchdog: if the cell never consumes the key, park on first Play.
        kotlinx.coroutines.delay(1500)
        if (!refocusConsumed) {
            onRefocusKeyChange(null)
            try {
                firstPlayFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
    LaunchedEffect(showClearConfirm) {
        if (showClearConfirm) {
            try {
                cancelFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .padding(horizontal = 48.dp, vertical = 20.dp)
            .padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.continue_watching),
                        color = NetflixWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.items.isNotEmpty()) {
                            stringResource(R.string.continue_watching_count, uiState.items.size)
                        } else {
                            stringResource(R.string.continue_watching_subtitle)
                        },
                        color = NetflixLightGray,
                        fontSize = 14.sp
                    )
                }

                if (uiState.items.isNotEmpty() && !showClearConfirm) {
                    Button(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.colors(
                            containerColor = NetflixDarkCard,
                            contentColor = NetflixLightGray,
                            focusedContainerColor = NetflixRed,
                            focusedContentColor = NetflixWhite
                        ),
                        border = ButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, NetflixWhite)
                            )
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.clear_all),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.clear_all), fontSize = 14.sp)
                    }
                } else if (uiState.items.isNotEmpty()) {
                    // Confirm row mirroring Settings → Clear watch history.
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.settings_history_confirm_title),
                            color = NetflixWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_history_confirm_desc),
                            color = NetflixLightGray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showClearConfirm = false },
                                colors = ButtonDefaults.colors(
                                    containerColor = NetflixDarkCard,
                                    contentColor = NetflixWhite,
                                    focusedContainerColor = NetflixWhite,
                                    focusedContentColor = NetflixLightGray
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, NetflixWhite)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
                                modifier = Modifier.focusRequester(cancelFocusRequester)
                            ) {
                                Text(text = stringResource(R.string.settings_history_no), fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    showClearConfirm = false
                                    // Null key hands focus to the empty-state
                                    // effect, which parks on Explore.
                                    refocusConsumed = false
                                    onRefocusKeyChange(null)
                                    viewModel.clearAll()
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = NetflixRed,
                                    contentColor = NetflixWhite,
                                    focusedContainerColor = NetflixRed,
                                    focusedContentColor = NetflixWhite
                                ),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, NetflixWhite)
                                    )
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                            ) {
                                Text(text = stringResource(R.string.settings_history_yes), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.items.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = NetflixLightGray.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.no_history_title),
                            color = NetflixWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.no_history_desc),
                            color = NetflixLightGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onNavigateToHome,
                            modifier = Modifier
                                .focusRequester(exploreFocusRequester)
                                .then(if (entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier),
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixRed,
                                contentColor = NetflixWhite,
                                focusedContainerColor = NetflixRed,
                                focusedContentColor = NetflixWhite
                            ),
                            scale = ButtonDefaults.scale(focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                        ) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.explore_home))
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(230.dp),
                    // Scale-allowance insets: 12dp each side/top covers the
                    // 1.08x ring overflow so edge cells settle fully visible.
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .focusRestorer()
                ) {
                    itemsIndexed(uiState.items, key = { _, it -> it.contentId }) { index, item ->
                        val isFirst = index == 0
                        val size = uiState.items.size
                        val cols = cwCols
                        val lastRowCount = if (size % cols == 0) cols else size % cols
                        val lastRowStart = (size - lastRowCount).coerceAtLeast(0)
                        val isFirstCol = index % cols == 0
                        val isLastCol = index % cols == cols - 1 || index == size - 1
                        val isLastRow = index >= lastRowStart
                        val playFocusRequester = remember { FocusRequester() }
                        if (refocusKey != null && refocusKey == item.contentId) {
                            LaunchedEffect(refocusKey) {
                                try {
                                    playFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                                clearFocusKey()
                            }
                        }
                        // Cell-level focus frame: covers the card AND the
                        // Play/Delete buttons below it, so whichever node owns
                        // focus settles with ring + zoom fully visible.
                        Column(
                            Modifier
                                .railFocusFrame()
                                .padding(vertical = 12.dp)
                        ) {
                            Box(
                                Modifier
                                    .topEdgeBarEscape(barLinkRequester)
                            ) {
                                TvContinueWatchingCard(
                                    item = item,
                                    onClick = {
                                        refocusConsumed = false
                                        onRefocusKeyChange(item.contentId)
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
                                    width = 230,
                                    height = 140,
                                    modifier = Modifier.focusProperties {
                                        if (isFirstCol) left = FocusRequester.Cancel
                                        if (isLastCol) right = FocusRequester.Cancel
                                        if (isLastRow) down = FocusRequester.Cancel
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.width(230.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        refocusConsumed = false
                                        onRefocusKeyChange(item.contentId)
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
                                    colors = ButtonDefaults.colors(
                                        containerColor = NetflixRed,
                                        contentColor = NetflixWhite
                                    ),
                                    scale = ButtonDefaults.scale(focusedScale = 1.05f),
                                    border = ButtonDefaults.border(
                                        focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(playFocusRequester)
                                        .then(if (isFirst) Modifier.focusRequester(firstPlayFocusRequester) else Modifier)
                                        .then(if (isFirst && entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier)
                                        .topEdgeBarEscape(barLinkRequester)
                                        .focusProperties {
                                            if (isFirstCol) left = FocusRequester.Cancel
                                            if (isLastRow) down = FocusRequester.Cancel
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.play),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(R.string.play), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        // Neighbor takes the key so focus lands
                                        // on its Play: next cell shifts into
                                        // this index, else the previous one.
                                        // Last-cell delete empties to Explore
                                        // via the empty-state effect (null key).
                                        val current = uiState.items
                                        val idx = current.indexOfFirst { it.contentId == item.contentId }
                                        val neighbor = if (idx != -1) {
                                            current.getOrNull(idx + 1) ?: current.getOrNull(idx - 1)
                                        } else null
                                        refocusConsumed = false
                                        onRefocusKeyChange(neighbor?.contentId)
                                        viewModel.removeItem(item.contentId)
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = NetflixDarkCard,
                                        contentColor = NetflixLightGray,
                                        focusedContainerColor = NetflixRed.copy(alpha = 0.8f),
                                        focusedContentColor = NetflixWhite
                                    ),
                                    scale = ButtonDefaults.scale(focusedScale = 1.05f),
                                    border = ButtonDefaults.border(
                                        focusedBorder = Border(BorderStroke(2.dp, NetflixWhite))
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                    modifier = Modifier.focusProperties {
                                        if (isLastCol) right = FocusRequester.Cancel
                                        if (isLastRow) down = FocusRequester.Cancel
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
