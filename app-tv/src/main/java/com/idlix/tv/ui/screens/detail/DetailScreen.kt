package com.idlix.tv.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import com.idlix.data.local.WatchHistoryStore
import com.idlix.tv.ui.components.TvErrorState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.idlix.tv.R
import com.idlix.data.model.CastMember
import com.idlix.data.model.EpisodeItem
import com.idlix.tv.ui.components.TvLoadingSpinner
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixGold
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    contentType: String,
    slug: String,
    onNavigateToPlayer: (contentType: String, contentId: String, title: String, poster: String?, backdrop: String?, slug: String?, season: Int?, episode: Int?) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = viewModel()
) {
    LaunchedEffect(contentType, slug) {
        viewModel.loadDetail(contentType, slug)
    }

    val uiState by viewModel.uiState.collectAsState()
    val playButtonFocusRequester = remember { FocusRequester() }

    // Latest unfinished history entry for this title (matched by slug, or movie id):
    // drives the Resume label + seeks to the saved position via the player.
    val context = LocalContext.current
    val historyStore = remember(context) { WatchHistoryStore.getInstance(context) }
    val history by historyStore.historyFlow.collectAsState()
    val resumeItem = remember(history, slug, uiState.movie?.id) {
        val movieId = uiState.movie?.id
        history
            .filter { it.slug == slug || (movieId?.isNotBlank() == true && it.contentId == movieId) }
            .maxByOrNull { it.lastWatchedTime }
            ?.takeIf { it.isUnfinished }
    }

    // Place default focus on "Putar Sekarang" / "Putar Episode 1" as soon as media details load
    LaunchedEffect(uiState.isLoading, uiState.movie, uiState.series) {
        if (!uiState.isLoading && (uiState.movie != null || uiState.series != null)) {
            delay(120)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {}
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
                onRetry = { viewModel.loadDetail(contentType, slug) },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val title = uiState.movie?.title ?: uiState.series?.title ?: ""
            val overview = uiState.movie?.overview ?: uiState.series?.overview ?: ""
            val backdropUrl = uiState.movie?.fullBackdropUrl ?: uiState.series?.fullBackdropUrl ?: ""
            val rating = uiState.movie?.voteAverage ?: uiState.series?.voteAverage ?: ""
            val year = (uiState.movie?.releaseDate ?: uiState.series?.firstAirDate)?.take(4) ?: ""
            val genres = (uiState.movie?.genres ?: uiState.series?.genres ?: emptyList()).joinToString(", ") { it.name }
            val castList = uiState.movie?.cast ?: uiState.series?.cast ?: emptyList()

            TvLazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header & Info
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    ) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Bottom gradient fade into the page body
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            NetflixBlack.copy(alpha = 0.55f),
                                            NetflixBlack.copy(alpha = 0.92f),
                                            NetflixBlack
                                        )
                                    )
                                )
                        )

                        // Left scrim behind the text block
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            NetflixBlack.copy(alpha = 0.85f),
                                            NetflixBlack.copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 48.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Netflix kicker: red N mark + tracked label
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(NetflixRed, RoundedCornerShape(2.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "N",
                                        color = NetflixWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (contentType == "movie") {
                                        stringResource(R.string.kicker_film)
                                    } else {
                                        stringResource(R.string.kicker_series)
                                    },
                                    color = NetflixLightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = title,
                                color = NetflixWhite,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Match-score metadata line: match % • year • maturity • HD
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rating.toDoubleOrNull()?.let { vote ->
                                    val percent = ((vote * 10).toInt()).coerceIn(0, 100)
                                    Text(
                                        text = stringResource(R.string.match_score_format, percent),
                                        color = Color(0xFF46D369),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (year.isNotBlank()) {
                                    Text(text = year, color = NetflixLightGray, fontSize = 14.sp)
                                }

                                // Maturity badge (catalog carries no certification; stable 13+ gate)
                                Box(
                                    modifier = Modifier
                                        .border(BorderStroke(1.dp, NetflixLightGray), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "13+",
                                        color = NetflixWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // HD badge
                                Box(
                                    modifier = Modifier
                                        .border(BorderStroke(1.dp, NetflixLightGray), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "HD",
                                        color = NetflixWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Duration (movies) / season + episode counts (series)
                                if (contentType == "movie") {
                                    val runtimeMinutes = uiState.movie?.duration
                                        ?: uiState.movie?.runtime
                                    if (runtimeMinutes != null && runtimeMinutes > 0) {
                                        Text(
                                            text = stringResource(R.string.movie_duration_format, runtimeMinutes),
                                            color = NetflixLightGray,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    val seasonCount = uiState.series?.seasons?.size?.takeIf { it > 0 }
                                        ?: uiState.series?.numberOfSeasons?.takeIf { it > 0 }
                                    val episodeCount = uiState.series?.defaultSeason?.episodes?.size?.takeIf { it > 0 }
                                        ?: uiState.episodes.size.takeIf { it > 0 }
                                        ?: uiState.series?.numberOfEpisodes?.takeIf { it > 0 }
                                    if (seasonCount != null && episodeCount != null) {
                                        Text(
                                            text = stringResource(
                                                R.string.series_counts_format,
                                                seasonCount,
                                                episodeCount
                                            ),
                                            color = NetflixLightGray,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            if (genres.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = genres,
                                    color = NetflixLightGray,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(600.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = overview,
                                color = NetflixLightGray,
                                fontSize = 14.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(600.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Play / Resume Button with default focus and clear white outline ring.
                            // When unfinished history exists for this title, resume the saved
                            // episode (series) at its saved position — the player seeks
                            // automatically via WatchHistoryStore.getSavedPosition(contentId).
                            val resumeMinutes = resumeItem?.remainingMinutes?.takeIf { it > 0 }?.toInt()
                            val playLabel = when {
                                resumeItem != null && resumeMinutes != null -> {
                                    val resume = stringResource(R.string.resume_play)
                                    val remaining = stringResource(R.string.remaining_minutes, resumeMinutes)
                                    "$resume · $remaining"
                                }
                                resumeItem != null -> stringResource(R.string.resume_play)
                                contentType == "movie" -> stringResource(R.string.play_now)
                                else -> stringResource(R.string.play_episode_1)
                            }
                            Button(
                                onClick = {
                                    if (contentType == "movie") {
                                        uiState.movie?.let {
                                            onNavigateToPlayer("movie", it.id, it.title, it.fullPosterUrl, it.fullBackdropUrl, it.slug, null, null)
                                        }
                                    } else {
                                        val resume = resumeItem
                                        if (resume != null) {
                                            onNavigateToPlayer(
                                                resume.contentType,
                                                resume.contentId,
                                                resume.title,
                                                resume.posterUrl,
                                                resume.backdropUrl,
                                                resume.slug,
                                                resume.seasonNumber,
                                                resume.episodeNumber
                                            )
                                        } else {
                                            val firstEp = uiState.episodes.firstOrNull()
                                                ?: uiState.series?.defaultSeason?.episodes?.firstOrNull()
                                            if (firstEp != null) {
                                                onNavigateToPlayer(
                                                    "episode",
                                                    firstEp.id,
                                                    "${uiState.series?.title} - ${firstEp.name}",
                                                    firstEp.fullStillUrl,
                                                    uiState.series?.fullBackdropUrl,
                                                    uiState.series?.slug,
                                                    uiState.selectedSeason?.seasonNumber ?: 1,
                                                    firstEp.episodeNumber
                                                )
                                            }
                                        }
                                    }
                                },
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
                                modifier = Modifier.focusRequester(playButtonFocusRequester)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.play)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = playLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                // If Series: Season selector & Episode list
                if (contentType == "tv_series" && uiState.series != null) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.select_season_episode),
                                color = NetflixWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Seasons Row
                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.series?.seasons ?: emptyList()) { season ->
                                    val isSelected = season.id == uiState.selectedSeason?.id ||
                                            season.seasonNumber == uiState.selectedSeason?.seasonNumber
                                    Button(
                                        onClick = { viewModel.selectSeason(slug, season) },
                                        colors = ButtonDefaults.colors(
                                            containerColor = if (isSelected) NetflixRed else NetflixDarkCard,
                                            contentColor = NetflixWhite,
                                            focusedContainerColor = if (isSelected) NetflixRed else NetflixWhite,
                                            focusedContentColor = if (isSelected) NetflixWhite else NetflixBlack
                                        ),
                                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                                        border = ButtonDefaults.border(
                                            focusedBorder = Border(
                                                border = BorderStroke(2.dp, NetflixWhite)
                                            )
                                        ),
                                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                                    ) {
                                        Text(text = season.name)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Episodes List
                            if (uiState.isLoadingEpisodes) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TvLoadingSpinner()
                                }
                            } else {
                                TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.episodes, key = { it.id }) { ep ->
                                        TvEpisodeCard(
                                            episode = ep,
                                            onClick = {
                                                onNavigateToPlayer(
                                                    "episode",
                                                    ep.id,
                                                    "${uiState.series?.title} - ${ep.name}",
                                                    ep.fullStillUrl,
                                                    uiState.series?.fullBackdropUrl,
                                                    uiState.series?.slug,
                                                    uiState.selectedSeason?.seasonNumber ?: 1,
                                                    ep.episodeNumber
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Cast list
                if (castList.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(start = 48.dp, top = 16.dp, bottom = 32.dp)) {
                            Text(
                                text = stringResource(R.string.cast),
                                color = NetflixWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            TvLazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(castList.take(12), key = { it.id }) { cast ->
                                    TvCastCard(cast = cast)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvEpisodeCard(
    episode: EpisodeItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(180.dp),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = NetflixDarkCard
        ),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NetflixWhite)
            )
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                AsyncImage(
                    model = episode.fullStillUrl,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color(0xBB000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.episode_badge, episode.episodeNumber),
                        color = NetflixWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.episode_title_format,
                        episode.episodeNumber,
                        episode.name
                    ),
                    color = NetflixWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.overview ?: "",
                    color = NetflixLightGray,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TvCastCard(cast: CastMember) {
    // Deliberately NOT focusable: cast rows are informational only (no detail
    // destination), so a focusable no-op Card would trap the D-pad on a dead control.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(105.dp)
            .padding(vertical = 4.dp)
            .background(Color.Transparent, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        AsyncImage(
            model = cast.fullProfileUrl,
            contentDescription = cast.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(NetflixDarkCard)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cast.name,
            color = NetflixWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = cast.character ?: "",
            color = NetflixLightGray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
