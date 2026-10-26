package com.idlix.data.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * Resolves the live IDLIX mirror from the canonical entry point.
 *
 * Canonical source is `https://idlix.net/` (expected 302 `Location` to the live
 * mirror, e.g. `https://z2.idlixku.com/`). Uses a dedicated no-redirect client
 * ([CoreHttp.noRedirectClient]) so OkHttp never swallows the `Location` header.
 *
 * Order: HEAD → GET → cached-last-known → [FALLBACK_URL] last-resort,
 * fail-closed (never falls back to any legacy backend host).
 *
 * Consumers derive Referer/Origin/redeem/share URLs from [currentBaseUrl],
 * [origin] and [referer] at runtime — never hardcode a mirror literal.
 */
object IdlixDomainResolver {

    const val CANONICAL_URL = "https://idlix.net/"
    const val FALLBACK_URL = "https://z2.idlixku.com/"

    private const val PREFS_NAME = "idlix_domain"
    private const val KEY_BASE_URL = "resolved_base_url"
    private const val KEY_TIMESTAMP = "resolved_base_ts"

    private val _resolvedBaseUrl = MutableStateFlow(FALLBACK_URL)
    val resolvedBaseUrl: StateFlow<String> = _resolvedBaseUrl.asStateFlow()

    @Volatile
    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
        loadCachedIntoFlow()
    }

    /** Current base URL, always with a trailing slash. */
    fun currentBaseUrl(): String = _resolvedBaseUrl.value

    /** Origin header value: base URL without trailing slash. */
    fun origin(): String = currentBaseUrl().trimEnd('/')

    /** Referer header value: base URL with trailing slash. */
    fun referer(): String {
        val base = currentBaseUrl()
        return if (base.lastOrNull() == '/') base else "$base/"
    }

    suspend fun resolve(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = readCached()
            if (cached != null && _resolvedBaseUrl.value == FALLBACK_URL) {
                _resolvedBaseUrl.value = cached
            }
        }

        // HEAD then GET against the canonical URL with redirects disabled.
        val fromHead = captureLocation(method = "HEAD")
        if (fromHead != null) {
            persist(fromHead)
            return@withContext fromHead
        }
        val fromGet = captureLocation(method = "GET")
        if (fromGet != null) {
            persist(fromGet)
            return@withContext fromGet
        }

        // Fallback: cached-last-known, else last-resort mirror. Fail-closed.
        val cached = readCached()
        if (cached != null) {
            _resolvedBaseUrl.value = cached
            return@withContext cached
        }
        _resolvedBaseUrl.value = FALLBACK_URL
        FALLBACK_URL
    }

    private fun captureLocation(method: String): String? {
        return try {
            val builder = Request.Builder().url(CANONICAL_URL)
            when (method) {
                "HEAD" -> builder.head()
                else -> builder.get()
            }
            CoreHttp.noRedirectClient.newCall(builder.build()).execute().use { response ->
                val location = response.header("Location")?.trim().orEmpty()
                if (location.isBlank()) return@use null
                normalizeAndValidate(location)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeAndValidate(location: String): String? {
        return try {
            val url = location.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https") return null
            val host = url.host.lowercase()
            val allowed = host == "idlixku.com" ||
                host.endsWith(".idlixku.com") ||
                host.contains("idlix")
            if (!allowed) return null
            val normalized = url.toString()
            if (normalized.lastOrNull() == '/') normalized else "$normalized/"
        } catch (_: Exception) {
            null
        }
    }

    private fun readCached(): String? {
        return try {
            val prefs = applicationContext
                ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?: CoreHttp.applicationContext
                    ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?: return null
            val base = prefs.getString(KEY_BASE_URL, null)?.trim().orEmpty()
            if (base.isBlank()) return null
            normalizeAndValidate(base)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadCachedIntoFlow() {
        readCached()?.let { _resolvedBaseUrl.value = it }
    }

    private fun persist(baseUrl: String) {
        _resolvedBaseUrl.value = baseUrl
        try {
            val ctx = applicationContext ?: CoreHttp.applicationContext ?: return
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, baseUrl)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
            // In-memory value still wins; cache is best-effort.
        }
    }
}
