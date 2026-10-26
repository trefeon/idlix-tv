package com.idlix.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

/**
 * Centered full-screen error with a default-focused Try Again (+ optional Back).
 * Both buttons carry the 2dp white focus ring like every other TV surface.
 * Entering composition re-fires the focus request so D-pad always lands on Retry.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val retryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(message) {
        try {
            retryFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = NetflixWhite,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 4
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onRetry,
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
                modifier = Modifier.focusRequester(retryFocusRequester)
            ) {
                Text(text = stringResource(R.string.try_again), fontSize = 14.sp)
            }
            if (onBack != null) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixLightGray.copy(alpha = 0.3f),
                        contentColor = NetflixWhite,
                        focusedContainerColor = NetflixWhite,
                        focusedContentColor = NetflixLightGray
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1.08f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, NetflixWhite)
                        )
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp))
                ) {
                    Text(text = stringResource(R.string.back), fontSize = 14.sp)
                }
            }
        }
    }
}
