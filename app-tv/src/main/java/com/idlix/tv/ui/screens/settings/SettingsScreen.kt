package com.idlix.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.R
import com.idlix.tv.ui.screens.settings.components.UpdateSettingsRow
import com.idlix.tv.ui.components.tvContentSidePadding
import com.idlix.tv.ui.components.TvTopBarHeight
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import com.idlix.tv.update.UpdateState

/**
 * TV Settings: a USER-SAFE section on top, an ADVANCED section hidden behind
 * an unlock gesture (7 taps on the version row within 3s, or D-pad
 * Up-Up-Down-Down anywhere on this screen).
 *
 * The user section never renders raw URLs, API keys, timeouts, UA strings or
 * cache sizes. Everything technical lives in the advanced section, which is
 * session-only (relocked on process death by [SettingsViewModel]).
 *
 * D-pad order is the visual order: update, subtitles, cache, history, about.
 * The unlock affordance itself is not focusable.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    // Bar DOWN target on the update-check button (see AppNavigation.contentEntryLink).
    entryLinkRequester: FocusRequester? = null,
    // Content-to-bar escape for scrolled rows (see topEdgeBarEscape).
    barLinkRequester: FocusRequester? = null
) {
    val scrollState = rememberScrollState()

    val updateState by viewModel.updateState.collectAsState()
    val advancedUnlocked by viewModel.advancedUnlocked.collectAsState()
    val subtitleLang by viewModel.subtitleLanguage.collectAsState()
    val stabilizerEnabled by viewModel.audioStabilizer.collectAsState()
    val cacheMessage by viewModel.cacheMessage.collectAsState()
    val historyCleared by viewModel.historyCleared.collectAsState()

    // Update row owns entry focus: without this the screen opens with no
    // focused node and D-pad input goes nowhere until the user backs out.
    // Delayed like the detail screen: an immediate request fires before the
    // window has focus and is silently dropped.
    val checkFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.delay(200)
            checkFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }
    var showHistoryConfirm by remember { mutableStateOf(false) }
    // Safe option ("No") owns focus while the destructive confirm is visible,
    // so a stray OK press can never land on "Yes, clear".
    val historyNoFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showHistoryConfirm) {
        if (showHistoryConfirm) {
            try {
                historyNoFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .verticalScroll(scrollState)
            // D-pad Up-Up-Down-Down unlock. Never consumes the key (returns
            // false unless the sequence completes) so remote navigation keeps
            // working; focus order is untouched.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> viewModel.onDpadUp()
                    Key.DirectionDown -> viewModel.onDpadDown()
                    else -> false
                }
            }
            .padding(horizontal = tvContentSidePadding(), vertical = 24.dp)
            .padding(top = TvTopBarHeight),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ---- Header ------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = NetflixRed,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.settings),
                    color = NetflixWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_desc),
                    color = NetflixLightGray,
                    fontSize = 13.sp
                )
            }
        }

        // ================================================================
        // USER-SAFE SECTION
        // ================================================================
        SectionHeader(
            icon = Icons.Default.Refresh,
            title = stringResource(R.string.settings_updates_title)
        )
        val stateSnapshot = updateState
        UpdateSettingsRow(
            state = stateSnapshot,
            onCheckClick = viewModel::checkForUpdates,
            checkButtonModifier = Modifier
                .focusRequester(checkFocusRequester)
                .then(if (entryLinkRequester != null) Modifier.focusRequester(entryLinkRequester) else Modifier),
            onDownloadClick = {
                val available = stateSnapshot as? UpdateState.Available ?: return@UpdateSettingsRow
                viewModel.downloadUpdate(available.assetUrl)
            },
            onInstallClick = {
                val downloaded = stateSnapshot as? UpdateState.Downloaded ?: return@UpdateSettingsRow
                viewModel.installUpdate(downloaded.fileUri)
            }
        )

        SettingsCard(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.settings_subtitle_title),
            desc = stringResource(R.string.settings_subtitle_desc)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubtitleOption(
                    label = stringResource(R.string.settings_subtitle_indonesian),
                    selected = subtitleLang == SettingsViewModel.LANG_INDONESIAN,
                    onClick = { viewModel.setSubtitleLanguage(SettingsViewModel.LANG_INDONESIAN) }
                )
                SubtitleOption(
                    label = stringResource(R.string.settings_subtitle_english),
                    selected = subtitleLang == SettingsViewModel.LANG_ENGLISH,
                    onClick = { viewModel.setSubtitleLanguage(SettingsViewModel.LANG_ENGLISH) }
                )
                SubtitleOption(
                    label = stringResource(R.string.settings_subtitle_off),
                    selected = subtitleLang == SettingsViewModel.LANG_OFF,
                    onClick = { viewModel.setSubtitleLanguage(SettingsViewModel.LANG_OFF) }
                )
            }
        }

        SettingsCard(
            icon = Icons.Default.VolumeUp,
            title = stringResource(R.string.settings_audio_title),
            desc = stringResource(R.string.settings_audio_desc)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubtitleOption(
                    label = stringResource(R.string.settings_audio_on),
                    selected = stabilizerEnabled,
                    onClick = { viewModel.setAudioStabilizer(true) }
                )
                SubtitleOption(
                    label = stringResource(R.string.settings_audio_off),
                    selected = !stabilizerEnabled,
                    onClick = { viewModel.setAudioStabilizer(false) }
                )
            }
        }

        SettingsCard(
            icon = Icons.Default.Image,
            title = stringResource(R.string.settings_cache_title),
            desc = stringResource(R.string.settings_cache_desc)
        ) {
            SettingsActionButton(
                label = stringResource(R.string.settings_cache_title),
                icon = Icons.Default.Refresh,
                onClick = { viewModel.clearImageCache() }
            )
            if (cacheMessage != null) {
                StatusLine(
                    ok = cacheMessage == "done",
                    text = if (cacheMessage == "done") {
                        stringResource(R.string.settings_cache_done)
                    } else {
                        stringResource(R.string.loading_error)
                    }
                )
            }
        }

        SettingsCard(
            icon = Icons.Default.Delete,
            title = stringResource(R.string.settings_history_title),
            desc = stringResource(R.string.settings_history_desc)
        ) {
            if (!showHistoryConfirm) {
                SettingsActionButton(
                    label = stringResource(R.string.settings_history_title),
                    icon = Icons.Default.Delete,
                    onClick = { showHistoryConfirm = true }
                )
            } else {
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsActionButton(
                        label = stringResource(R.string.settings_history_no),
                        icon = Icons.Default.Refresh,
                        onClick = { showHistoryConfirm = false },
                        modifier = Modifier.focusRequester(historyNoFocusRequester)
                    )
                    SettingsActionButton(
                        label = stringResource(R.string.settings_history_yes),
                        icon = Icons.Default.Delete,
                        destructive = true,
                        onClick = {
                            showHistoryConfirm = false
                            viewModel.clearWatchHistory()
                        }
                    )
                }
            }
            if (historyCleared) {
                StatusLine(
                    ok = true,
                    text = stringResource(R.string.settings_history_done)
                )
            }
        }

        // About / version row. Tapping (OK on the remote) 7 times within 3s
        // unlocks the advanced section. Focusable, last in the user order.
        SettingsCard(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_about_title),
            desc = stringResource(
                R.string.settings_version_label,
                viewModel.appVersionName,
                viewModel.appVersionCode
            )
        ) {
            SettingsActionButton(
                label = stringResource(R.string.settings_about_title),
                icon = Icons.Default.Info,
                onClick = { viewModel.registerVersionClick() }
            )
            if (advancedUnlocked) {
                StatusLine(
                    ok = true,
                    text = stringResource(R.string.adv_unlocked)
                )
            }
        }

        // ================================================================
        // ADVANCED SECTION (gated, session-only)
        // ================================================================
        if (advancedUnlocked) {
            SectionHeader(
                icon = Icons.Default.Build,
                title = stringResource(R.string.adv_section_title),
                desc = stringResource(R.string.adv_section_desc)
            )
            AdvancedSubdlCard(viewModel = viewModel)
            AdvancedResolverCard(viewModel = viewModel)
            AdvancedErrorLogCard(viewModel = viewModel)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // One-shot flags are consumed a few seconds after they appear.
    if (cacheMessage != null) {
        androidx.compose.runtime.LaunchedEffect(cacheMessage) {
            kotlinx.coroutines.delay(4_000)
            viewModel.consumeCacheMessage()
        }
    }
    if (historyCleared) {
        androidx.compose.runtime.LaunchedEffect(historyCleared) {
            kotlinx.coroutines.delay(4_000)
            viewModel.consumeHistoryCleared()
        }
    }
}

// ------------------------------------------------------------------ pieces

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    desc: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = NetflixRed,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = NetflixWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (desc != null) {
                Text(
                    text = desc,
                    color = NetflixLightGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    desc: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NetflixDarkCard, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = NetflixWhite,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = NetflixWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = desc,
            color = NetflixLightGray,
            fontSize = 12.sp
        )
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (destructive) NetflixRed else NetflixBlack,
            focusedContainerColor = if (destructive) {
                NetflixRed.copy(alpha = 0.8f)
            } else {
                NetflixWhite
            },
            contentColor = NetflixWhite,
            focusedContentColor = if (destructive) NetflixWhite else NetflixBlack
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NetflixWhite)
            )
        ),
        shape = ButtonDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        modifier = modifier.height(38.dp)
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
private fun SubtitleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) NetflixRed else NetflixBlack,
            focusedContainerColor = NetflixWhite,
            contentColor = NetflixWhite,
            focusedContentColor = NetflixBlack
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NetflixWhite)
            )
        ),
        shape = ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        modifier = Modifier.height(38.dp)
    ) {
        Text(
            text = if (selected) "● $label" else label,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = text,
            tint = if (ok) NetflixWhite else NetflixRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = if (ok) NetflixWhite else NetflixRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ------------------------------------------------------- advanced: SubDL key

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AdvancedSubdlCard(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val subdlKey by viewModel.subdlKey.collectAsState()
    val testing by viewModel.subdlTesting.collectAsState()
    val testOk by viewModel.subdlTestOk.collectAsState()
    val testStatus by viewModel.subdlTestStatus.collectAsState()
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    SettingsCard(
        icon = Icons.Default.Key,
        title = stringResource(R.string.adv_subdl_title),
        desc = stringResource(R.string.adv_subdl_desc)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(NetflixBlack, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) NetflixWhite else NetflixDarkCard,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = stringResource(R.string.adv_subdl_title),
                tint = if (focused) NetflixWhite else NetflixLightGray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = subdlKey,
                onValueChange = viewModel::setSubdlKeyInput,
                textStyle = TextStyle(
                    color = NetflixWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(NetflixRed),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                singleLine = true,
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation('*')
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    if (subdlKey.isEmpty()) {
                        Text(
                            text = stringResource(R.string.adv_subdl_hint),
                            color = NetflixLightGray,
                            fontSize = 13.sp
                        )
                    }
                    inner()
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsActionButton(
                label = if (testing) {
                    context.getString(R.string.subdl_testing)
                } else {
                    stringResource(R.string.adv_subdl_test)
                },
                icon = Icons.Default.Refresh,
                onClick = {
                    viewModel.testSubdlKey(
                        testingLabel = context.getString(R.string.subdl_testing),
                        validLabel = context.getString(R.string.subdl_valid),
                        quotaLabel = context.getString(R.string.subdl_quota_error),
                        failedLabel = { detail ->
                            context.getString(R.string.subdl_failed_error, detail)
                        }
                    )
                }
            )
            SettingsActionButton(
                label = stringResource(
                    if (visible) R.string.adv_subdl_hide else R.string.adv_subdl_show
                ),
                icon = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                onClick = { visible = !visible }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsActionButton(
                label = stringResource(R.string.adv_subdl_save),
                icon = Icons.Default.Save,
                onClick = { viewModel.saveSubdlKey() }
            )
            SettingsActionButton(
                label = stringResource(R.string.adv_subdl_clear),
                icon = Icons.Default.Delete,
                onClick = { viewModel.clearSubdlKey() }
            )
        }

        if (testStatus != null) {
            StatusLine(ok = testOk == true, text = testStatus ?: "")
        }
    }
}

// ------------------------------------------------------ advanced: resolver

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AdvancedResolverCard(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val baseUrl by viewModel.resolverBaseUrl.collectAsState()
    val lastChecked by viewModel.resolverLastChecked.collectAsState()
    val refreshing by viewModel.resolverRefreshing.collectAsState()
    val notice by viewModel.resolverNotice.collectAsState()
    var override by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    SettingsCard(
        icon = Icons.Default.Dns,
        title = stringResource(R.string.adv_resolver_title),
        desc = stringResource(R.string.adv_resolver_desc)
    ) {
        Text(
            text = stringResource(R.string.adv_resolver_current, baseUrl),
            color = NetflixWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(
                R.string.adv_resolver_last_checked,
                lastChecked ?: stringResource(R.string.adv_resolver_never)
            ),
            color = NetflixLightGray,
            fontSize = 12.sp
        )
        SettingsActionButton(
            label = stringResource(R.string.adv_resolver_refresh),
            icon = Icons.Default.Refresh,
            onClick = {
                viewModel.setResolverNotice(null)
                viewModel.refreshResolverNow()
            }
        )
        if (refreshing) {
            Text(
                text = stringResource(R.string.subdl_testing),
                color = NetflixLightGray,
                fontSize = 12.sp
            )
        }

        Text(
            text = stringResource(R.string.adv_resolver_override_label),
            color = NetflixWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(NetflixBlack, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) NetflixWhite else NetflixDarkCard,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = override,
                onValueChange = {
                    override = it
                    viewModel.setResolverNotice(null)
                },
                textStyle = TextStyle(color = NetflixWhite, fontSize = 14.sp),
                cursorBrush = SolidColor(NetflixRed),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    if (override.isEmpty()) {
                        Text(
                            text = stringResource(R.string.adv_resolver_override_hint),
                            color = NetflixLightGray,
                            fontSize = 13.sp
                        )
                    }
                    inner()
                }
            )
        }
        SettingsActionButton(
            label = stringResource(R.string.adv_resolver_apply),
            icon = Icons.Default.Save,
            onClick = {
                val ok = viewModel.applyResolverOverride(override)
                viewModel.setResolverNotice(
                    if (ok) {
                        context.getString(R.string.adv_resolver_saved)
                    } else {
                        context.getString(R.string.adv_resolver_invalid)
                    }
                )
                if (ok) override = ""
            }
        )
        if (notice != null) {
            StatusLine(ok = notice == context.getString(R.string.adv_resolver_saved), text = notice ?: "")
        }
    }
}

// ------------------------------------------------------ advanced: error log

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AdvancedErrorLogCard(viewModel: SettingsViewModel) {
    val logText by viewModel.errorLogText.collectAsState()

    SettingsCard(
        icon = Icons.Default.BugReport,
        title = stringResource(R.string.adv_log_title),
        desc = stringResource(R.string.adv_log_desc)
    ) {
        if (logText.isBlank()) {
            Text(
                text = stringResource(R.string.adv_log_empty),
                color = NetflixLightGray,
                fontSize = 12.sp
            )
        } else {
            Text(
                text = logText.take(2_000),
                color = NetflixLightGray,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .background(NetflixBlack, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .padding(10.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsActionButton(
                label = stringResource(R.string.adv_log_refresh),
                icon = Icons.Default.Refresh,
                onClick = { viewModel.refreshErrorLog() }
            )
            SettingsActionButton(
                label = stringResource(R.string.adv_log_clear),
                icon = Icons.Default.Delete,
                onClick = { viewModel.clearErrorLog() }
            )
        }
    }
}
