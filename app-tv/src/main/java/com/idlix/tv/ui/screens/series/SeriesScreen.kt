package com.idlix.tv.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.tv.R
import com.idlix.tv.ui.components.TvErrorState
import com.idlix.tv.ui.components.TvHeroBillboard
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.components.TvSwimlaneRow
import com.idlix.tv.ui.theme.NetflixBlack

@Composable
fun SeriesScreen(
    onNavigateToDetail: (contentType: String, slug: String) -> Unit,
    onNavigateToPlayer: (contentType: String, contentId: String, title: String, poster: String?, backdrop: String?, slug: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroFocusRequester = remember { FocusRequester() }

    // Direct initial focus to Hero billboard content as soon as loaded
    LaunchedEffect(uiState.isLoading, uiState.heroSeries) {
        if (!uiState.isLoading && uiState.heroSeries != null) {
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
            TvLazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero TV Series Billboard
                item {
                    TvHeroBillboard(
                        movie = uiState.heroSeries,
                        playButtonModifier = Modifier.focusRequester(heroFocusRequester),
                        onPlayClick = {
                            uiState.heroSeries?.let {
                                onNavigateToDetail(it.contentType, it.slug)
                            }
                        },
                        onDetailsClick = {
                            uiState.heroSeries?.let {
                                onNavigateToDetail(it.contentType, it.slug)
                            }
                        }
                    )
                }

                // Swimlane: Serial TV Terpopuler
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.popular_series),
                        items = uiState.popularSeries,
                        onItemClick = { onNavigateToDetail(it.contentType, it.slug) }
                    )
                }

                // Swimlane: Serial TV Terbaru
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.on_the_air_series),
                        items = uiState.latestSeries,
                        onItemClick = { onNavigateToDetail(it.contentType, it.slug) }
                    )
                }

                // Swimlane: Serial Drama
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_drama),
                        items = uiState.dramaSeries,
                        onItemClick = { onNavigateToDetail(it.contentType, it.slug) }
                    )
                }

                // Swimlane: Serial Aksi & Petualangan
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_action),
                        items = uiState.actionSeries,
                        onItemClick = { onNavigateToDetail(it.contentType, it.slug) }
                    )
                }

                // Swimlane: Serial Komedi
                item {
                    TvSwimlaneRow(
                        title = stringResource(R.string.series_comedy),
                        items = uiState.comedySeries,
                        onItemClick = { onNavigateToDetail(it.contentType, it.slug) }
                    )
                }
            }
        }
    }
}
