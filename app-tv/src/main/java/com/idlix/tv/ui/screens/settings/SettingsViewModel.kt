package com.idlix.tv.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.idlix.data.local.WatchHistoryStore
import com.idlix.data.net.IdlixDomainResolver
import com.idlix.data.repository.SubdlRepository
import com.idlix.tv.BuildConfig
import com.idlix.tv.R
import com.idlix.tv.startup.ErrorLog
import com.idlix.tv.update.UpdateChecker
import com.idlix.tv.update.UpdateDownloader
import com.idlix.tv.update.UpdateInstaller
import com.idlix.tv.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Hoisted state for the TV Settings screen.
 *
 * USER section state (update, subtitle language, caches, history, version) is
 * safe for everyone. ADVANCED state (SubDL key, resolver override, error log)
 * is gated behind [advancedUnlocked], which is session-only: it is recorded in
 * prefs file `idlix_tv_advanced` but reset to locked on every process start.
 *
 * Update wiring follows the UpdateEngine contract
 * (`com.idlix.tv.update`: [UpdateState], [UpdateChecker], [UpdateDownloader],
 * [UpdateInstaller]). Check delegates to
 * `UpdateChecker.checkForUpdate(context, currentVersionCode)`, download collects
 * `UpdateDownloader.download(context, assetUrl)`, install routes through
 * `UpdateInstaller.installApk` (falling back to the unknown-sources settings
 * screen when installs from this source are blocked). No update channel
 * toggle: the engine tracks stable releases only.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val ADV_PREFS = "idlix_tv_advanced"
        const val KEY_UNLOCKED = "advanced_unlocked"

        const val USER_PREFS = "idlix_tv_prefs"
        const val KEY_SUBTITLE_LANG = "subtitle_language"

        const val LANG_INDONESIAN = "id"
        const val LANG_ENGLISH = "en"
        const val LANG_OFF = "off"

        private const val UNLOCK_CLICKS = 7
        private const val UNLOCK_WINDOW_MS = 3_000L
        private const val DPAD_UP = 1
        private const val DPAD_DOWN = 2
    }

    private val app: Application get() = getApplication()

    private val advPrefs: SharedPreferences =
        app.getSharedPreferences(ADV_PREFS, Context.MODE_PRIVATE)
    private val userPrefs: SharedPreferences =
        app.getSharedPreferences(USER_PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ unlock
    private val _advancedUnlocked = MutableStateFlow(false)
    val advancedUnlocked: StateFlow<Boolean> = _advancedUnlocked.asStateFlow()

    private val versionClickTimes = ArrayDeque<Long>()
    private val dpadTrail = ArrayDeque<Int>()

    fun unlockAdvanced() {
        advPrefs.edit().putBoolean(KEY_UNLOCKED, true).apply()
        _advancedUnlocked.value = true
    }

    /** Test-seam alias for [registerVersionClick]: one version-row tap. */
    fun recordAdvancedClick(): Boolean = registerVersionClick()

    /** Re-locks the advanced section (e.g. when leaving Settings). */
    fun relockAdvanced() {
        advPrefs.edit().putBoolean(KEY_UNLOCKED, false).apply()
        _advancedUnlocked.value = false
    }

    /** Returns true when this tap completes the 7-taps-in-3s unlock. */
    fun registerVersionClick(): Boolean {
        val now = System.currentTimeMillis()
        versionClickTimes.addLast(now)
        while (versionClickTimes.isNotEmpty() && now - versionClickTimes.first() > UNLOCK_WINDOW_MS) {
            versionClickTimes.removeFirst()
        }
        if (versionClickTimes.size >= UNLOCK_CLICKS) {
            versionClickTimes.clear()
            dpadTrail.clear()
            unlockAdvanced()
            return true
        }
        return false
    }

    /** Returns true when this press completes the Up-Up-Down-Down unlock. */
    fun onDpadUp(): Boolean = pushDpad(DPAD_UP)

    /** Returns true when this press completes the Up-Up-Down-Down unlock. */
    fun onDpadDown(): Boolean = pushDpad(DPAD_DOWN)

    private fun pushDpad(dir: Int): Boolean {
        dpadTrail.addLast(dir)
        while (dpadTrail.size > 4) dpadTrail.removeFirst()
        val trail = dpadTrail.toList()
        if (trail == listOf(DPAD_UP, DPAD_UP, DPAD_DOWN, DPAD_DOWN)) {
            dpadTrail.clear()
            versionClickTimes.clear()
            unlockAdvanced()
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ update
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates() {
        val cur = _updateState.value
        if (cur is UpdateState.Checking || cur is UpdateState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = try {
                UpdateChecker.checkForUpdate(app, BuildConfig.VERSION_CODE.toLong())
            } catch (e: Exception) {
                UpdateState.Failed(app.getString(R.string.update_err_failed, e.localizedMessage?.take(120) ?: "—"))
            }
        }
    }

    fun downloadUpdate(assetUrl: String) {
        if (_updateState.value !is UpdateState.Available) return
        viewModelScope.launch {
            try {
                UpdateDownloader.download(app, assetUrl).collect { state ->
                    _updateState.value = state
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Failed(app.getString(R.string.dl_err_query, e.localizedMessage?.take(120) ?: "—"))
            }
        }
    }

    fun installUpdate(fileUri: String) {
        try {
            val ctx = app
            if (!UpdateInstaller.canInstallUnknownApps(ctx)) {
                ctx.startActivity(UpdateInstaller.unknownAppSourcesIntent(ctx))
                return
            }
            UpdateInstaller.installApk(ctx, fileUri)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Failed(app.getString(R.string.update_err_install, e.localizedMessage?.take(120) ?: "—"))
        }
    }

    // ------------------------------------------------------- subtitle language
    private val _subtitleLanguage = MutableStateFlow(LANG_INDONESIAN)
    val subtitleLanguage: StateFlow<String> = _subtitleLanguage.asStateFlow()

    fun setSubtitleLanguage(code: String) {
        if (code != LANG_INDONESIAN && code != LANG_ENGLISH && code != LANG_OFF) return
        userPrefs.edit().putString(KEY_SUBTITLE_LANG, code).apply()
        _subtitleLanguage.value = code
    }

    // ------------------------------------------------------------- user care
    private val _cacheMessage = MutableStateFlow<String?>(null)
    val cacheMessage: StateFlow<String?> = _cacheMessage.asStateFlow()

    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = app
                runCatching { Coil.imageLoader(ctx).diskCache?.clear() }
                withContext(Dispatchers.Main) {
                    runCatching { Coil.imageLoader(ctx).memoryCache?.clear() }
                }
                runCatching {
                    val dir = ctx.cacheDir.resolve("image_cache")
                    if (dir.exists()) dir.deleteRecursively()
                }
                _cacheMessage.value = "done"
            } catch (_: Exception) {
                _cacheMessage.value = "failed"
            }
        }
    }

    fun consumeCacheMessage() {
        _cacheMessage.value = null
    }

    private val _historyCleared = MutableStateFlow(false)
    val historyCleared: StateFlow<Boolean> = _historyCleared.asStateFlow()

    fun clearWatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { WatchHistoryStore.getInstance(app).clearAll() }
            _historyCleared.value = true
        }
    }

    fun consumeHistoryCleared() {
        _historyCleared.value = false
    }

    val appVersionName: String = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("1.0.0")
    val appVersionCode: Long = runCatching { BuildConfig.VERSION_CODE.toLong() }.getOrDefault(0L)

    // ------------------------------------------------------- advanced: subdl
    private val _subdlKey = MutableStateFlow(SubdlRepository.getApiKey())
    val subdlKey: StateFlow<String> = _subdlKey.asStateFlow()

    private val _subdlTesting = MutableStateFlow(false)
    val subdlTesting: StateFlow<Boolean> = _subdlTesting.asStateFlow()

    private val _subdlTestOk = MutableStateFlow<Boolean?>(null)
    val subdlTestOk: StateFlow<Boolean?> = _subdlTestOk.asStateFlow()

    private val _subdlTestStatus = MutableStateFlow<String?>(null)
    val subdlTestStatus: StateFlow<String?> = _subdlTestStatus.asStateFlow()

    fun setSubdlKeyInput(value: String) {
        _subdlKey.value = value
        _subdlTestStatus.value = null
        _subdlTestOk.value = null
    }

    fun saveSubdlKey() {
        SubdlRepository.setApiKey(_subdlKey.value)
        _subdlKey.value = SubdlRepository.getApiKey()
    }

    fun clearSubdlKey() {
        SubdlRepository.setApiKey("")
        _subdlKey.value = ""
        _subdlTestStatus.value = null
        _subdlTestOk.value = null
    }

    fun testSubdlKey(
        testingLabel: String,
        validLabel: String,
        quotaLabel: String,
        failedLabel: (String) -> String,
    ) {
        if (_subdlTesting.value) return
        viewModelScope.launch {
            _subdlTesting.value = true
            _subdlTestStatus.value = testingLabel
            _subdlTestOk.value = null
            try {
                val results = withContext(Dispatchers.IO) {
                    SubdlRepository.searchAllSubtitles(title = "Avatar", type = "movie")
                }
                if (results.isNotEmpty()) {
                    _subdlTestOk.value = true
                    _subdlTestStatus.value = validLabel
                } else {
                    _subdlTestOk.value = false
                    _subdlTestStatus.value = quotaLabel
                }
            } catch (e: Exception) {
                _subdlTestOk.value = false
                _subdlTestStatus.value = failedLabel(e.localizedMessage.orEmpty())
            }
            _subdlTesting.value = false
        }
    }

    // ----------------------------------------------------- advanced: resolver
    private val _resolverBaseUrl = MutableStateFlow(IdlixDomainResolver.currentBaseUrl())
    val resolverBaseUrl: StateFlow<String> = _resolverBaseUrl.asStateFlow()

    private val _resolverLastChecked = MutableStateFlow<String?>(null)
    val resolverLastChecked: StateFlow<String?> = _resolverLastChecked.asStateFlow()

    private val _resolverRefreshing = MutableStateFlow(false)
    val resolverRefreshing: StateFlow<Boolean> = _resolverRefreshing.asStateFlow()

    private val _resolverNotice = MutableStateFlow<String?>(null)
    val resolverNotice: StateFlow<String?> = _resolverNotice.asStateFlow()

    fun refreshResolverInfo() {
        _resolverBaseUrl.value = runCatching { IdlixDomainResolver.currentBaseUrl() }
            .getOrDefault(_resolverBaseUrl.value)
        _resolverLastChecked.value = readResolverTimestamp()
    }

    fun refreshResolverNow() {
        if (_resolverRefreshing.value) return
        viewModelScope.launch {
            _resolverRefreshing.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    IdlixDomainResolver.resolve(forceRefresh = true)
                }
            }
            refreshResolverInfo()
            _resolverRefreshing.value = false
        }
    }

    /**
     * Stores an override address. Takes effect on the next cold start (the
     * resolver loads the cached address at init); a manual Refresh checks the
     * canonical entry point live and may replace it when reachable.
     */
    fun applyResolverOverride(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("https://")) return false
        val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        return try {
            app.getSharedPreferences("idlix_domain", Context.MODE_PRIVATE)
                .edit()
                .putString("resolved_base_url", normalized)
                .putLong("resolved_base_ts", System.currentTimeMillis())
                .apply()
            refreshResolverInfo()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setResolverNotice(notice: String?) {
        _resolverNotice.value = notice
    }

    private fun readResolverTimestamp(): String? {
        return try {
            val prefs = app.getSharedPreferences("idlix_domain", Context.MODE_PRIVATE)
            val ts = prefs.getLong("resolved_base_ts", 0L)
            if (ts <= 0L) return null
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
        } catch (_: Exception) {
            null
        }
    }

    // ----------------------------------------------------- advanced: error log
    private val _errorLogText = MutableStateFlow("")
    val errorLogText: StateFlow<String> = _errorLogText.asStateFlow()

    private val _errorLogCleared = MutableStateFlow(false)
    val errorLogCleared: StateFlow<Boolean> = _errorLogCleared.asStateFlow()
    init {
        // Session-only gate: auto-relock on process death, never persisted.
        advPrefs.edit().putBoolean(KEY_UNLOCKED, false).apply()
        _subtitleLanguage.value = userPrefs.getString(KEY_SUBTITLE_LANG, LANG_INDONESIAN)
            .takeIf { it == LANG_INDONESIAN || it == LANG_ENGLISH || it == LANG_OFF }
            ?: LANG_INDONESIAN
        refreshResolverInfo()
        refreshErrorLog()
    }

    fun refreshErrorLog() {
        viewModelScope.launch(Dispatchers.IO) {
            _errorLogText.value = runCatching { ErrorLog.readHead(app) }.getOrDefault("")
        }
    }

    fun clearErrorLog() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ErrorLog.clear(app) }
            _errorLogText.value = ""
            _errorLogCleared.value = true
        }
    }

    fun consumeErrorLogCleared() {
        _errorLogCleared.value = false
    }
}
