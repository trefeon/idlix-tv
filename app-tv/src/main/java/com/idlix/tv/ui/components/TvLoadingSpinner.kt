package com.idlix.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.idlix.tv.ui.theme.NetflixRed
import kotlinx.coroutines.delay

@Composable
fun TvLoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = NetflixRed
) {
    // Parked D-pad holder for loading windows: the spinner is the only node
    // while payloads resolve, so it owns focus (all directions Cancel) and
    // BACK bubbles to the screen root. Without this, UP/DOWN/LEFT/RIGHT from
    // a loading frame has no focused node and focus is lost.
    val parkRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            delay(80)
            parkRequester.requestFocus()
        } catch (_: Exception) {}
    }
    Box(
        modifier = modifier
            .focusRequester(parkRequester)
            .focusable()
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            },
        contentAlignment = Alignment.Center
    ) {
        val transition = rememberInfiniteTransition(label = "spinnerTransition")
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spinnerRotation"
        )

        Canvas(modifier = Modifier.size(size)) {
        // Background track
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = (this.size.minDimension - strokeWidth.toPx()) / 2,
            style = Stroke(width = strokeWidth.toPx())
        )

        // Spinning indicator arc
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 100f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
    }
}
