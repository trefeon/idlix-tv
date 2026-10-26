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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

private val MatchGreen = Color(0xFF46D369)

// Full-bleed backdrop fade: transparent up top, near-black at the rail edge.
private val HeroBottomBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        NetflixBlack.copy(alpha = 0.55f),
        NetflixBlack.copy(alpha = 0.92f),
        NetflixBlack
    )
)

private val HeroLeftBrush = Brush.horizontalGradient(
    colors = listOf(
        NetflixBlack.copy(alpha = 0.95f),
        NetflixBlack.copy(alpha = 0.6f),
        Color.Transparent
    )
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHeroBillboard(
    movie: MovieItem?,
    modifier: Modifier = Modifier,
    playButtonModifier: Modifier = Modifier,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    if (movie == null) return

    val context = LocalContext.current
    val backdropUrl = movie.fullBackdropUrl
    val backdropRequest = remember(backdropUrl) {
        ImageRequest.Builder(context)
            .data(backdropUrl)
            .size(1280, 720)
            .precision(Precision.INEXACT)
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }

    // Match score derived from TMDB vote average (8.7 -> "87% Match").
    val matchPercent = remember(movie.voteAverage) {
        movie.voteAverage?.toDoubleOrNull()?.let { ((it * 10).toInt()).coerceIn(0, 100) }
    }
    val isSeries = movie.contentType == "tv_series"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        // Full-bleed backdrop
        AsyncImage(
            model = backdropRequest,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

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
                .width(560.dp)
                .background(HeroLeftBrush)
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(520.dp)
                .padding(start = 48.dp, top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Netflix-style kicker: red N mark + tracked-out label
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSeries) {
                        stringResource(R.string.kicker_series)
                    } else {
                        stringResource(R.string.kicker_film)
                    },
                    color = NetflixLightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = movie.title,
                color = NetflixWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Match-score metadata line: match % • year • maturity badge • HD badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (matchPercent != null) {
                    Text(
                        text = stringResource(R.string.match_score_format, matchPercent),
                        color = MatchGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                movie.releaseDate?.take(4)?.let { year ->
                    Text(
                        text = year,
                        color = NetflixLightGray,
                        fontSize = 14.sp
                    )
                }

                // Maturity / age badge (catalog carries no certification; stable 13+ gate)
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: white Play CTA + translucent More Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayClick,
                    modifier = playButtonModifier,
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixWhite,
                        contentColor = NetflixBlack,
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
                            text = stringResource(R.string.play),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Button(
                    onClick = onDetailsClick,
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
        }
    }
}
