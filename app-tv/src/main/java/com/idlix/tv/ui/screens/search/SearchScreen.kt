package com.idlix.tv.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Border
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.components.TvMovieCard
import com.idlix.tv.ui.components.TvTopBarHeight
import com.idlix.tv.ui.components.railFocusFrame
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
    // Bar DOWN target on the search field (see AppNavigation.contentEntryLink).
    entryLinkRequester: FocusRequester? = null,
    // Content-to-bar escape for the field (see topEdgeBarEscape): at rest the
    // field sits under the transparent bar and plain UP dies.
    barLinkRequester: FocusRequester? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val searchFieldFocusRequester = remember { FocusRequester() }
    val clearButtonFocusRequester = remember { FocusRequester() }
    val firstTagFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val errorRetryFocusRequester = remember { FocusRequester() }

    var isInputFocused by remember { mutableStateOf(false) }
    var isClearFocused by remember { mutableStateOf(false) }
    var pendingFocusOnResults by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val quickSearches = remember(context) {
        context.resources.getStringArray(R.array.quick_searches).toList()
    }

    // Request initial focus on the search input bar when screen is first displayed
    LaunchedEffect(Unit) {
        try {
            searchFieldFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Automatically transition focus to results once loaded if requested by user action
    LaunchedEffect(uiState.isLoading, uiState.results, pendingFocusOnResults) {
        if (pendingFocusOnResults && !uiState.isLoading) {
            if (uiState.results.isNotEmpty()) {
                pendingFocusOnResults = false
                try {
                    firstResultFocusRequester.requestFocus()
                } catch (_: Exception) {}
            } else {
                pendingFocusOnResults = false
                try {
                    searchFieldFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }
    // Land on Retry as soon as a search failure renders, so D-pad is never stranded.
    LaunchedEffect(uiState.error, uiState.errorRes) {
        if (uiState.error != null || uiState.errorRes != null) {
            try {
                errorRetryFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .padding(horizontal = 48.dp, vertical = 20.dp)
            .padding(top = TvTopBarHeight)
    ) {
        // Search Input Bar
        val isBarFocused = isInputFocused || isClearFocused
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(NetflixDarkCard, RoundedCornerShape(8.dp))
                .border(
                    width = if (isBarFocused) 2.dp else 1.dp,
                    color = if (isBarFocused) NetflixWhite else NetflixDarkCard,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                tint = if (isBarFocused) NetflixWhite else NetflixLightGray
            )

            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(it) },
                textStyle = TextStyle(
                    color = NetflixWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(NetflixRed),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        if (uiState.results.isNotEmpty()) {
                            try {
                                firstResultFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        } else {
                            pendingFocusOnResults = true
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFieldFocusRequester)
                    .then(if (entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier)
                    .onPreviewKeyEvent { keyEvent ->
                        // Text fields hold an input session that can veto an
                        // outgoing transfer: release it first, then move UP.
                        // (Top-bar escape: plain search has no strictly-above
                        // candidate once content scrolls under the floating bar.)
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                            val link = barLinkRequester
                            if (link != null) {
                                try {
                                    focusManager.clearFocus(force = true)
                                } catch (_: Exception) {}
                                try {
                                    link.requestFocus()
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                    .focusProperties {
                        down = if (uiState.query.isBlank()) firstTagFocusRequester else firstResultFocusRequester
                        right = if (uiState.query.isNotEmpty()) clearButtonFocusRequester else FocusRequester.Default
                    }
                    .onFocusChanged { isInputFocused = it.isFocused }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    keyboardController?.hide()
                                    if (uiState.query.isBlank()) {
                                        try {
                                            firstTagFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } else if (uiState.results.isNotEmpty()) {
                                        try {
                                            firstResultFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } else {
                                        true
                                    }
                                }
                                Key.DirectionRight -> {
                                    if (uiState.query.isNotEmpty()) {
                                        try {
                                            clearButtonFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                // UP intentionally falls through: the top bar
                                // owns the row above the field.
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                decorationBox = { innerTextField ->
                    if (uiState.query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            color = NetflixLightGray,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )

            if (uiState.query.isNotEmpty()) {
                Button(
                    onClick = {
                        viewModel.onQueryChange("")
                        try {
                            searchFieldFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixBlack,
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
                        .focusRequester(clearButtonFocusRequester)
                        .focusProperties {
                            left = searchFieldFocusRequester
                            if (uiState.results.isNotEmpty()) {
                                down = firstResultFocusRequester
                            }
                        }
                        .onFocusChanged { isClearFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        try {
                                            searchFieldFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (uiState.results.isNotEmpty()) {
                                            try {
                                                firstResultFocusRequester.requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        } else {
                                            true
                                        }
                                    }
                                    // UP goes home to the top bar (same input-
                                    // session release as the field, for safety).
                                    Key.DirectionUp -> {
                                        val link = barLinkRequester
                                        if (link != null) {
                                            try {
                                                focusManager.clearFocus(force = true)
                                            } catch (_: Exception) {}
                                            try {
                                                link.requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                ) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Search Tags for TV Remote (only shown when query is empty, matching mobile UX)
        if (uiState.query.isBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties {
                        up = searchFieldFocusRequester
                    }
            ) {
                Text(
                    text = stringResource(R.string.popular_searches),
                    color = NetflixLightGray,
                            fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.focusRestorer()
                ) {
                    items(quickSearches) { tag ->
                        val isFirstTag = tag == quickSearches.firstOrNull()
                        Button(
                            onClick = {
                                try {
                                    searchFieldFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                                viewModel.onQueryChange(tag)
                                pendingFocusOnResults = true
                            },
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
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
                            modifier = Modifier
                                .then(if (isFirstTag) Modifier.focusRequester(firstTagFocusRequester) else Modifier)
                                .focusProperties {
                                    up = searchFieldFocusRequester
                                }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionUp -> {
                                                try {
                                                    searchFieldFocusRequester.requestFocus()
                                                    true
                                                } catch (_: Exception) {
                                                    false
                                                }
                                            }
                                            Key.DirectionDown -> {
                                                true // Consume to prevent focus drop
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                        ) {
                            Text(text = tag, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Results Section
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                TvLoadingSpinner(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null || uiState.errorRes != null) {
                val errorMessage = uiState.error
                    ?: uiState.errorRes?.let { stringResource(it) }.orEmpty()
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMessage,
                        color = NetflixWhite,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.onQueryChange(uiState.query) },
                        colors = ButtonDefaults.colors(
                            containerColor = NetflixRed,
                            contentColor = NetflixWhite,
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
                        modifier = Modifier.focusRequester(errorRetryFocusRequester)
                    ) {
                        Text(text = stringResource(R.string.try_again), fontSize = 14.sp)
                    }
                }
            } else if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.no_results_for, uiState.query),
                    color = NetflixLightGray,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else if (uiState.results.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    color = NetflixLightGray,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(135.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties {
                            up = searchFieldFocusRequester
                        }
                ) {
                    items(uiState.results, key = { it.id }) { item ->
                        val isFirst = item == uiState.results.firstOrNull()
                        // 12dp focus frame: grid cells sit 48dp from the
                        // overscan edge; the frame keeps the 1.12x ring
                        // intact when a row settles at the viewport edge.
                        Box(
                            Modifier
                                .railFocusFrame()
                                .padding(vertical = 12.dp)
                        ) {
                            TvMovieCard(
                                title = item.title,
                                posterUrl = item.fullPosterUrl,
                                rating = item.formattedRating,
                                quality = item.quality ?: if (item.contentType == "tv_series") {
                                    stringResource(R.string.content_badge_series)
                                } else {
                                    stringResource(R.string.content_badge_movie)
                                },
                                onClick = { onNavigateToDetail(item.contentType, item.slug) },
                                modifier = Modifier
                                    .then(if (isFirst) Modifier.focusRequester(firstResultFocusRequester) else Modifier)
                                    .focusProperties {
                                        up = searchFieldFocusRequester
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp && isFirst) {
                                            try {
                                                searchFieldFocusRequester.requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
