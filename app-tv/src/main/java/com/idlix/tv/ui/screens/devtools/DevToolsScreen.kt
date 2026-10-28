package com.idlix.tv.ui.screens.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import android.view.KeyEvent as NativeKeyEvent
import com.idlix.tv.BuildConfig
import com.idlix.tv.R
import com.idlix.tv.ui.components.topEdgeBarEscape
import com.idlix.tv.ui.components.edgeCancels
import com.idlix.tv.ui.components.tvContentSidePadding
import com.idlix.tv.ui.navigation.Screen
import com.idlix.tv.ui.screens.settings.SettingsViewModel
import com.idlix.tv.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------------------------------------------------------------------------
// Dev key log: process-wide circular buffer (last 20 DOWN events).
//
// The recorder lives on the DevTools root via onPreviewKeyEvent (never
// consumes, so D-pad/BACK keep working) and each focusable on this screen
// reports its label via [DevKeyLog.noteFocus]. Because preview fires BEFORE
// focus moves, a record captures `focusBefore` immediately and the next focus
// arrival patches `focusAfter` — the row then reads "KEY · before → after".
// ---------------------------------------------------------------------------

data class DevKeyRecord(
    val seq: Long,
    val name: String,
    val focusBefore: String,
    val focusAfter: String?,
    val atMs: Long = System.currentTimeMillis(),
)

object DevKeyLog {
    const val CAPACITY = 20
    const val NO_FOCUS = "—"

    /**
     * A focus arrival only patches the latest pending record when it lands
     * soon after the key (a normal D-pad move settles in milliseconds).
     * Anything later — e.g. re-entering this screen minutes after a BACK
     * that popped it — leaves the record's after-label empty ("…") instead
     * of attributing an unrelated arrival to the old key.
     */
    const val AFTER_PATCH_WINDOW_MS = 1_500L

    private val _records = MutableStateFlow<List<DevKeyRecord>>(emptyList())
    val records: StateFlow<List<DevKeyRecord>> = _records.asStateFlow()

    @Volatile
    var lastFocus: String = NO_FOCUS
        private set

    private var seq = 0L

    @Synchronized
    fun record(name: String) {
        seq += 1
        _records.value = (_records.value + DevKeyRecord(seq, name, lastFocus, null))
            .takeLast(CAPACITY)
    }

    @Synchronized
    fun noteFocus(label: String) {
        lastFocus = label
        val cur = _records.value
        val idx = cur.indexOfLast { it.focusAfter == null }
        if (idx >= 0 &&
            System.currentTimeMillis() - cur[idx].atMs <= AFTER_PATCH_WINDOW_MS
        ) {
            _records.value = cur.toMutableList().also {
                it[idx] = it[idx].copy(focusAfter = label)
            }
        }
    }

    @Synchronized
    fun clear() {
        _records.value = emptyList()
    }
}

// ---------------------------------------------------------------------------
// Dev carousel feed: the home hero carousel owns its index locally
// (remember), so it reports here for the state inspector. Last write wins;
// untouched until Home composes (shows "not observed").
// ---------------------------------------------------------------------------

object DevToolsCarousel {
    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()
    private val _observed = MutableStateFlow(false)
    val observed: StateFlow<Boolean> = _observed.asStateFlow()

    fun report(index: Int, count: Int) {
        _index.value = index
        _count.value = count
        _observed.value = true
    }
}

/**
 * Short remote/gamepad-friendly key names from the platform key code.
 * Compose Key constants are deliberately avoided: the native code covers
 * remotes, gamepads and media keys uniformly with a keyCodeToString fallback.
 */
fun devKeyName(keyCode: Int): String {
    return when (keyCode) {
        NativeKeyEvent.KEYCODE_DPAD_UP -> "UP"
        NativeKeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
        NativeKeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
        NativeKeyEvent.KEYCODE_DPAD_CENTER -> "CENTER(DPAD)"
        NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_NUMPAD_ENTER -> "CENTER(Enter)"
        NativeKeyEvent.KEYCODE_BUTTON_A -> "CENTER(Gamepad-A)"
        NativeKeyEvent.KEYCODE_BACK, NativeKeyEvent.KEYCODE_ESCAPE -> "BACK"
        NativeKeyEvent.KEYCODE_BUTTON_B -> "BACK(Gamepad-B)"
        NativeKeyEvent.KEYCODE_MENU -> "MENU"
        NativeKeyEvent.KEYCODE_BUTTON_START -> "MENU(Gamepad-Start)"
        NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "MEDIA_PLAY_PAUSE"
        NativeKeyEvent.KEYCODE_MEDIA_PLAY -> "MEDIA_PLAY"
        NativeKeyEvent.KEYCODE_MEDIA_PAUSE -> "MEDIA_PAUSE"
        NativeKeyEvent.KEYCODE_MEDIA_STOP -> "MEDIA_STOP"
        NativeKeyEvent.KEYCODE_MEDIA_NEXT -> "MEDIA_NEXT"
        NativeKeyEvent.KEYCODE_MEDIA_PREVIOUS -> "MEDIA_PREV"
        NativeKeyEvent.KEYCODE_MEDIA_REWIND -> "MEDIA_REWIND"
        NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "MEDIA_FFWD"
        NativeKeyEvent.KEYCODE_BUTTON_X -> "GAMEPAD-X"
        NativeKeyEvent.KEYCODE_BUTTON_Y -> "GAMEPAD-Y"
        NativeKeyEvent.KEYCODE_BUTTON_L1 -> "GAMEPAD-L1"
        NativeKeyEvent.KEYCODE_BUTTON_R1 -> "GAMEPAD-R1"
        NativeKeyEvent.KEYCODE_BUTTON_SELECT -> "GAMEPAD-Select"
        else -> NativeKeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }
}

private fun describeUpdateState(state: UpdateState): String = when (state) {
    is UpdateState.Idle -> "Idle"
    is UpdateState.Checking -> "Checking"
    is UpdateState.UpToDate -> "UpToDate(${state.currentVersion})"
    is UpdateState.Available -> "Available(${state.version}, ${state.sizeBytes}B)"
    is UpdateState.Downloading -> "Downloading(${(state.progress * 100).toInt()}%)"
    is UpdateState.Downloaded -> "Downloaded"
    is UpdateState.Failed -> "Failed(${state.reason.take(80)})"
}

/**
 * In-app developer tools, reachable only from the hidden advanced section of
 * Settings (the gate itself is untouched). Read-only inspector rows up top,
 * three manual actions, and the key-event log at the bottom.
 *
 * Focus: entry parks on "Run update check" (delayed like every other screen —
 * an immediate request fires before the window has focus and is dropped).
 * The log rows are display-only Text (never focusable) so D-pad UP/DOWN can
 * never trap inside the log. System BACK (or the back button) pops to
 * Settings; the recorder never consumes keys.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DevToolsScreen(
    modifier: Modifier = Modifier,
    // Own nav-entry instance: resolver/update state derives from the same
    // singletons + prefs as Settings, so refresh/check hit the real engine.
    settingsViewModel: SettingsViewModel = viewModel(),
    currentRoute: String = Screen.DevTools.route,
    // Bar DOWN target on the first action (see AppNavigation.contentEntryLink).
    entryLinkRequester: FocusRequester? = null,
    // Content-to-bar escape for the first action (see topEdgeBarEscape).
    barLinkRequester: FocusRequester? = null,
    onBack: () -> Unit = {},
) {
    val records by DevKeyLog.records.collectAsState()
    val updateState by settingsViewModel.updateState.collectAsState()
    val resolverBase by settingsViewModel.resolverBaseUrl.collectAsState()
    val resolverChecked by settingsViewModel.resolverLastChecked.collectAsState()
    val carouselIndex by DevToolsCarousel.index.collectAsState()
    val carouselCount by DevToolsCarousel.count.collectAsState()
    val carouselObserved by DevToolsCarousel.observed.collectAsState()

    val runUpdateFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.delay(200)
            runUpdateFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    val cfg = LocalConfiguration.current
    val density = LocalDensity.current.density
    val flavor = if (BuildConfig.DEBUG) {
        stringResource(R.string.dev_row_debug)
    } else {
        stringResource(R.string.dev_row_release)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(com.idlix.tv.ui.theme.NetflixBlack)
            .verticalScroll(rememberScrollState())
            // Root preview recorder: sees keys before children consume them,
            // never consumes (returns false) so focus + BACK keep working.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    DevKeyLog.record(devKeyName(event.nativeKeyEvent.keyCode))
                }
                false
            }
            .padding(horizontal = tvContentSidePadding(), vertical = 24.dp)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ---- Header ------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = stringResource(R.string.dev_tools_title),
                tint = com.idlix.tv.ui.theme.NetflixRed,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.dev_tools_title),
                    color = com.idlix.tv.ui.theme.NetflixWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.dev_tools_desc),
                    color = com.idlix.tv.ui.theme.NetflixLightGray,
                    fontSize = 13.sp
                )
            }
        }

        // ---- Actions -----------------------------------------------------
        DevSection(
            icon = Icons.Default.Refresh,
            title = stringResource(R.string.dev_actions_title)
        ) {
            DevButton(
                focusKey = "$currentRoute/action:run-update-check",
                label = stringResource(R.string.dev_action_update),
                icon = Icons.Default.Refresh,
                onClick = { settingsViewModel.checkForUpdates() },
                modifier = Modifier
                    .focusRequester(runUpdateFocus)
                    .then(
                        if (entryLinkRequester != null) {
                            Modifier.focusRequester(entryLinkRequester)
                        } else {
                            Modifier
                        }
                    )
                    .topEdgeBarEscape(barLinkRequester)
            )
            DevButton(
                focusKey = "$currentRoute/action:force-resolver-refresh",
                label = stringResource(R.string.dev_action_resolver),
                icon = Icons.Default.Refresh,
                onClick = { settingsViewModel.refreshResolverNow() },
                modifier = Modifier.topEdgeBarEscape(barLinkRequester)
            )
            DevButton(
                focusKey = "$currentRoute/action:clear-key-log",
                label = stringResource(R.string.dev_keylog_clear),
                icon = Icons.Default.Delete,
                onClick = { DevKeyLog.clear() },
                modifier = Modifier.topEdgeBarEscape(barLinkRequester)
            )
        }

        // ---- State inspector (read-only) ---------------------------------
        DevSection(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.dev_inspector_title)
        ) {
            DevRow(
                label = stringResource(R.string.dev_row_version),
                value = "${settingsViewModel.appVersionName} " +
                    "(${settingsViewModel.appVersionCode}) · $flavor"
            )
            DevRow(
                label = stringResource(R.string.dev_row_screen),
                value = stringResource(
                    R.string.dev_screen_format,
                    cfg.screenWidthDp,
                    cfg.screenHeightDp,
                    String.format("%.2f", density)
                )
            )
            DevRow(
                label = stringResource(R.string.dev_row_resolver),
                value = resolverBase
            )
            DevRow(
                label = stringResource(R.string.dev_row_resolver_checked),
                value = resolverChecked ?: stringResource(R.string.dev_row_never)
            )
            DevRow(
                label = stringResource(R.string.dev_row_update),
                value = describeUpdateState(updateState)
            )
            DevRow(
                label = stringResource(R.string.dev_row_carousel),
                value = if (carouselObserved && carouselCount > 0) {
                    stringResource(
                        R.string.dev_carousel_format,
                        carouselIndex + 1,
                        carouselCount
                    )
                } else {
                    stringResource(R.string.dev_row_never)
                }
            )
            DevRow(
                label = stringResource(R.string.dev_row_route),
                value = currentRoute
            )
        }

        // ---- Key event log (display-only, never focusable) ----------------
        DevSection(
            icon = Icons.Default.Build,
            title = stringResource(R.string.dev_keylog_title),
            desc = stringResource(R.string.dev_keylog_desc)
        ) {
            if (records.isEmpty()) {
                Text(
                    text = stringResource(R.string.dev_keylog_empty),
                    color = com.idlix.tv.ui.theme.NetflixLightGray,
                    fontSize = 12.sp
                )
            } else {
                // Newest first: the latest press is visible without scrolling.
                records.asReversed().forEach { record ->
                    Text(
                        text = "#${record.seq} ${record.name} · " +
                            "${record.focusBefore} → ${record.focusAfter ?: "…"}",
                        color = com.idlix.tv.ui.theme.NetflixWhite,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ---- Back ----------------------------------------------------------
        DevButton(
            focusKey = "$currentRoute/action:back-to-settings",
            label = stringResource(R.string.dev_action_back),
            icon = Icons.Default.Settings,
            onClick = onBack,
            modifier = Modifier.topEdgeBarEscape(barLinkRequester),
            // Last focusable on screen — DOWN stays.
            isLast = true
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ------------------------------------------------------------------ pieces

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DevSection(
    icon: ImageVector,
    title: String,
    desc: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                com.idlix.tv.ui.theme.NetflixDarkCard,
                androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = com.idlix.tv.ui.theme.NetflixWhite,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = com.idlix.tv.ui.theme.NetflixWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (desc != null) {
            Text(
                text = desc,
                color = com.idlix.tv.ui.theme.NetflixLightGray,
                fontSize = 12.sp
            )
        }
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DevButton(
    focusKey: String,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // True-last button (Back) traps DOWN so focus can never leave the screen.
    isLast: Boolean = false,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = com.idlix.tv.ui.theme.NetflixBlack,
            focusedContainerColor = com.idlix.tv.ui.theme.NetflixWhite,
            contentColor = com.idlix.tv.ui.theme.NetflixWhite,
            focusedContentColor = com.idlix.tv.ui.theme.NetflixBlack
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, com.idlix.tv.ui.theme.NetflixWhite)
            )
        ),
        shape = ButtonDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        modifier = modifier
            .height(38.dp)
            .edgeCancels(left = true, right = true, down = isLast)
            .onFocusChanged { if (it.isFocused) DevKeyLog.noteFocus(focusKey) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DevRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = com.idlix.tv.ui.theme.NetflixLightGray,
            fontSize = 12.sp,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            color = com.idlix.tv.ui.theme.NetflixWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
