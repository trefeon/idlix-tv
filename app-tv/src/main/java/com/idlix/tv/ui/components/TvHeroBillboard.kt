package com.idlix.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.key
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.idlix.tv.R
import com.idlix.data.model.MovieItem
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.delay

// Web-hero scrims: full-bleed art runs top-to-bottom, so both gradients run
// hotter than the old 55%-hero values — white title text and the red CTA
// must read on bright backdrops, not just dark ones.
private val HeroBottomBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        NetflixBlack.copy(alpha = 0.45f),
        NetflixBlack.copy(alpha = 0.78f),
        NetflixBlack
    )
)

private val HeroLeftBrush = Brush.horizontalGradient(
    colors = listOf(
        NetflixBlack.copy(alpha = 0.85f),
        NetflixBlack.copy(alpha = 0.55f),
        Color.Transparent
    )
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHeroBillboard(
    movies: List<MovieItem>,
    modifier: Modifier = Modifier,
    playButtonModifier: Modifier = Modifier,
    onPlayClick: (MovieItem) -> Unit,
    onDetailsClick: (MovieItem) -> Unit,
    // Content-to-bar escape for both CTAs (see topEdgeBarEscape): at partial
    // scroll the billboard sits under the transparent bar and plain UP dies.
    barEscapeLink: FocusRequester? = null
) {
    if (movies.isEmpty()) return

    // Featured carousel: self-advancing index, 8s per slide. `remember(count)`
    // keeps the position across content refreshes of the same size and resets
    // it only when the slide count itself changes (no yank-to-zero on every
    // list re-emission).
    val count = movies.size
    var index by remember(count) { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        if (count <= 1) return@LaunchedEffect
        while (true) {
            delay(8000L)
            index = (index + 1) % count
        }
    }
    val movie = movies[index.coerceIn(0, count - 1)]

    val context = LocalContext.current
    val backdropUrl = movie.fullBackdropUrl

    // Match score derived from TMDB vote average (8.7 -> "87% Match").
    // Null or zero votes carry no signal — omit instead of "0%".
    val matchPercent = remember(movie.voteAverage) {
        movie.voteAverage?.toDoubleOrNull()?.takeIf { it > 0 }?.let { ((it * 10).toInt()).coerceIn(0, 100) }
    }
    // Star-line rating ("6.4"): null when the catalog carries no vote, so no
    // "N/A" (or "0.0") placeholder ever renders in the meta row.
    val starRating = remember(movie.voteAverage) {
        movie.voteAverage?.toDoubleOrNull()?.takeIf { it > 0 }?.let { String.format("%.1f", it) }
    }
    val year = remember(movie.releaseDate) {
        movie.releaseDate?.take(4)?.takeIf { it.length == 4 }
    }
    val runtimeMinutes = remember(movie.runtime) {
        movie.runtime?.takeIf { it > 0 }
    }
    // Up to two genre names, web-hero style ("Mystery, Thriller").
    val genreLine = remember(movie.genres) {
        movie.genres.map { it.name }.filter { it.isNotBlank() }.take(2)
            .joinToString(", ").takeIf { it.isNotBlank() }
    }
    val isSeries = movie.contentType == "tv_series"

    // Cinematic hero: ~82% of the REAL screen height (LazyColumn items see
    // infinite parent height, so BoxWithConstraints clamps to max and crops).
    // Clamped so the CTA block never crops on short viewports (320dp floor)
    // nor stretches into a fullscreen poster on tall panels (560dp ceiling).
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = (screenH * 0.82f).coerceIn(320.dp, 560.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
        ) {
        // Full-bleed cinematic backdrop. Swapped per slide inside key():
        // a clean dispose/recreate (no transition wrapper to starve layout),
        // while title/meta/buttons recompose in place OUTSIDE — so the Play
        // Button node (and its focus) is never disposed mid-rotation.
        val backdropRequest = remember(backdropUrl) {
            ImageRequest.Builder(context)
                .data(backdropUrl)
                .size(1280, 720)
                .precision(Precision.INEXACT)
                .allowRgb565(true)
                .crossfade(false)
                .build()
        }
        key(backdropUrl) {
            AsyncImage(
                model = backdropRequest,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom gradient fade into the rails
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HeroBottomBrush)
        )

        // Left gradient scrim behind the text block (saves fillrate vs full-screen)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(600.dp)
                .background(HeroLeftBrush)
        )

        // Bottom-left content block, web-hero order:
        // badge / title / meta / CTA. (No tagline or synopsis lines: the
        // list model carries neither — see MovieItem vs MovieDetail.)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(560.dp)
                .padding(start = 48.dp, top = 40.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Content-type badge: red fence covers badges. Plain uppercase
            // label (no logo art exists), matching the web MOVIE pill.
            Box(
                modifier = Modifier
                    .background(NetflixRed, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSeries) "TV SERIES" else "MOVIE",
                    color = NetflixWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Display title: plain text (no logo art available), set large.
            // Sans only — no serif ships in-repo, so no fake-serif styling.
            Text(
                text = movie.title,
                color = NetflixWhite,
                fontSize = 44.sp,
                // Black 44sp needs explicit leading — default tight metrics
                // stack descenders into the next line ("White" collision).
                lineHeight = 52.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Meta row, web order: match% (white) / year / runtime / genres /
            // quality badge. Every slot is conditional — absent fields leave
            // no gap text, no placeholders. (No maturity/HD pills: the list
            // model carries no certification, and a constant "HD" pill is
            // fake data regardless of the stream.)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (matchPercent != null) {
                    Text(
                        text = stringResource(R.string.match_score_format, matchPercent),
                        color = NetflixWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                starRating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = NetflixWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = rating,
                            color = NetflixWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                year?.let {
                    Text(
                        text = it,
                        color = NetflixLightGray,
                        fontSize = 14.sp
                    )
                }

                runtimeMinutes?.let {
                    Text(
                        text = stringResource(R.string.movie_duration_format, it),
                        color = NetflixLightGray,
                        fontSize = 14.sp
                    )
                }

                genreLine?.let {
                    Text(
                        text = it,
                        color = NetflixLightGray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                movie.quality?.takeIf { it.isNotBlank() }?.let { q ->
                    Box(
                        modifier = Modifier
                            .border(BorderStroke(1.dp, NetflixLightGray), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = q.uppercase(),
                            color = NetflixWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Web shows one red CTA plus a ghost Details secondary. Both stay
            // in this fixed slot across slides (outside the artwork Crossfade)
            // so the focused Play node survives rotation. Play stays the focus
            // entry point via playButtonModifier.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onPlayClick(movie) },
                    modifier = playButtonModifier.topEdgeBarEscape(barEscapeLink),
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
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.play_now),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Button(
                    onClick = { onDetailsClick(movie) },
                    modifier = Modifier.topEdgeBarEscape(barEscapeLink),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0x66FFFFFF),
                        contentColor = NetflixWhite,
                        focusedContainerColor = NetflixWhite,
                        focusedContentColor = NetflixBlack
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1.08f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, NetflixWhite)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.more_info),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.more_info),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Carousel position dots: display-only indicators, never focusable
            // (plain Boxes, no clickable/focusable modifier — focus traps
            // forbidden). Active slide = white wide pill, rest = dim dots.
            if (count > 1) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until count) {
                        Box(
                            modifier = Modifier
                                .then(
                                    if (i == index) Modifier.size(width = 24.dp, height = 6.dp)
                                    else Modifier.size(6.dp)
                                )
                                .background(
                                    if (i == index) NetflixWhite
                                    else NetflixLightGray.copy(alpha = 0.4f),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}
