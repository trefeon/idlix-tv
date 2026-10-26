package com.idlix.tv.update

import android.content.Context
import com.idlix.data.net.CoreHttp
import com.idlix.tv.BuildConfig
import com.idlix.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Checks the latest stable GitHub release against [BuildConfig.VERSION_NAME]
 * (semver-first, trailing-int fallback).
 *
 * Fail-closed and offline-safe: airplane mode, DNS failure, timeouts, HTTP
 * errors and malformed payloads all surface as [UpdateState.Failed], never throw.
 *
 * Rate-limit aware: GitHub's unauthenticated REST quota (60 req/hour per IP)
 * 403s on shared/carrier IPs, so the checker (a) sends `If-None-Match` with
 * the cached ETag (a 304 costs no quota) and (b) backs off locally until
 * `X-RateLimit-Reset` instead of burning quota on retries.
 */
object UpdateChecker {

    const val LATEST_RELEASE_URL = "https://api.github.com/repos/trefeon/idlix-tv/releases/latest"

    internal const val UPDATE_PREFS = "idlix_tv_updates"
    internal const val KEY_ETAG = "update_last_etag"
    internal const val KEY_TAG = "update_last_tag"
    internal const val KEY_NOTES = "update_last_notes"
    internal const val KEY_ASSET_URL = "update_last_asset_url"
    internal const val KEY_ASSET_SIZE = "update_last_asset_size"
    internal const val KEY_RATELIMIT_RESET = "update_ratelimit_reset"

    /** Outcome of a pure, network-free release decision. */
    internal sealed interface ReleaseDecision {
        data object UpToDate : ReleaseDecision
        data class Available(
            val version: String,
            val notes: String,
            val assetUrl: String,
            val sizeBytes: Long
        ) : ReleaseDecision
        data object BadData : ReleaseDecision
        data object NoAsset : ReleaseDecision
    }

    @Serializable
    internal data class ReleaseAsset(
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        @SerialName("size") val size: Long = 0L
    )

    @Serializable
    private data class ReleaseResponse(
        @SerialName("tag_name") val tagName: String = "",
        @SerialName("body") val body: String = "",
        @SerialName("assets") val assets: List<ReleaseAsset> = emptyList()
    )

    // 10s budget per check; stacked after CoreHttp's interceptors so the
    // GitHub media type wins over CoreHttp's default Accept header.
    private val checkClient by lazy {
        CoreHttp.okHttpClient.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val withAccept = chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .build()
                chain.proceed(withAccept)
            }
            .build()
    }

    suspend fun checkForUpdate(
        context: Context,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
    ): UpdateState = withContext(Dispatchers.IO) {
        val prefs = try {
            context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
        val nowSec = System.currentTimeMillis() / 1000
        val resetSec = try {
            prefs?.getLong(KEY_RATELIMIT_RESET, 0L) ?: 0L
        } catch (_: Exception) {
            0L
        }
        // Local backoff first: no network while a known rate-limit window holds.
        if (isRateLimitWindowActive(nowSec, resetSec)) {
            return@withContext UpdateState.Failed(
                context.getString(R.string.update_err_rate_limited, formatRetryTime(resetSec))
            )
        }
        val etag = try {
            prefs?.getString(KEY_ETAG, null)
        } catch (_: Exception) {
            null
        }
        try {
            val builder = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .get()
            if (!etag.isNullOrBlank()) {
                builder.header("If-None-Match", etag)
            }
            checkClient.newCall(builder.build()).execute().use { response ->
                val remaining = response.header("X-RateLimit-Remaining")?.trim()?.toIntOrNull()
                val resetHeader = parseRateLimitReset(response.header("X-RateLimit-Reset"))

                if (response.code == 304) {
                    // ETag still fresh: no quota consumed. Serve from cache.
                    val cachedTag = try {
                        prefs?.getString(KEY_TAG, null).orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    val cachedNotes = try {
                        prefs?.getString(KEY_NOTES, null).orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    val cachedAsset = try {
                        prefs?.getString(KEY_ASSET_URL, null).orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    val cachedSize = try {
                        prefs?.getLong(KEY_ASSET_SIZE, 0L) ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                    return@withContext when (
                        val decision = decideCachedRelease(
                            cachedTag, cachedNotes, cachedAsset, cachedSize,
                            BuildConfig.VERSION_NAME, currentVersionCode
                        )
                    ) {
                        is ReleaseDecision.Available -> UpdateState.Available(
                            version = decision.version,
                            notes = decision.notes,
                            assetUrl = decision.assetUrl,
                            sizeBytes = decision.sizeBytes
                        )
                        else -> UpdateState.UpToDate(
                            cachedTag.trimStart('v', 'V')
                                .ifBlank { BuildConfig.VERSION_NAME }
                        )
                    }
                }

                if (response.code == 403 || response.code == 429) {
                    val errBody = try {
                        response.body?.string().orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    val rateLimited = response.code == 429 ||
                        (remaining != null && remaining <= 0) ||
                        isRateLimitedBody(errBody) ||
                        resetHeader > nowSec
                    if (rateLimited) {
                        try {
                            prefs?.edit()?.putLong(KEY_RATELIMIT_RESET, resetHeader)?.apply()
                        } catch (_: Exception) {
                        }
                        return@withContext UpdateState.Failed(
                            if (resetHeader > 0) {
                                context.getString(
                                    R.string.update_err_rate_limited,
                                    formatRetryTime(resetHeader)
                                )
                            } else {
                                context.getString(R.string.update_err_rate_limited_soon)
                            }
                        )
                    }
                    return@withContext UpdateState.Failed(
                        context.getString(R.string.update_err_http, response.code)
                    )
                }

                if (!response.isSuccessful) {
                    return@withContext UpdateState.Failed(context.getString(R.string.update_err_http, response.code))
                }
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) {
                    return@withContext UpdateState.Failed(context.getString(R.string.update_err_empty))
                }
                val release = try {
                    CoreHttp.json.decodeFromString<ReleaseResponse>(payload)
                } catch (e: Exception) {
                    return@withContext UpdateState.Failed(context.getString(R.string.update_err_bad_data))
                }
                // Persist the quota-saving cache on every 200 so a later 304
                // can still offer the download without an asset URL in hand.
                val asset = selectApkAsset(release.assets)
                try {
                    prefs?.edit()
                        ?.putString(KEY_ETAG, response.header("ETag"))
                        ?.putString(KEY_TAG, release.tagName)
                        ?.putString(KEY_NOTES, release.body)
                        ?.putString(KEY_ASSET_URL, asset?.browserDownloadUrl.orEmpty())
                        ?.putLong(KEY_ASSET_SIZE, asset?.size ?: 0L)
                        ?.putLong(KEY_RATELIMIT_RESET, 0L)
                        ?.apply()
                } catch (_: Exception) {
                }
                return@withContext when (
                    val decision = decideRelease(
                        release.tagName, release.body, release.assets,
                        BuildConfig.VERSION_NAME, currentVersionCode
                    )
                ) {
                    is ReleaseDecision.Available -> UpdateState.Available(
                        version = decision.version,
                        notes = decision.notes,
                        assetUrl = decision.assetUrl,
                        sizeBytes = decision.sizeBytes
                    )
                    is ReleaseDecision.UpToDate ->
                        UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                    is ReleaseDecision.BadData ->
                        UpdateState.Failed(context.getString(R.string.update_err_bad_data))
                    is ReleaseDecision.NoAsset ->
                        UpdateState.Failed(context.getString(R.string.update_err_no_asset))
                }
            }
        } catch (e: IOException) {
            UpdateState.Failed(context.getString(R.string.update_err_network, e.message?.take(120) ?: "—"))
        } catch (e: Exception) {
            UpdateState.Failed(context.getString(R.string.update_err_failed, e.message?.take(120) ?: "—"))
        }
    }

    // ------------------------------------------------------------------ pure

    /**
     * Parses an `X-RateLimit-Reset` epoch-seconds value. Returns 0 when the
     * header is absent or malformed (0 = no known window).
     */
    internal fun parseRateLimitReset(headerValue: String?): Long {
        val trimmed = headerValue?.trim().orEmpty()
        if (trimmed.isEmpty()) return 0L
        return trimmed.toLongOrNull()?.takeIf { it > 0 } ?: 0L
    }

    /**
     * True when [nowSec] falls inside a known rate-limit window:
     * [resetSec] is a future epoch (0 = none).
     */
    internal fun isRateLimitWindowActive(nowSec: Long, resetSec: Long): Boolean {
        return resetSec > 0 && nowSec < resetSec
    }

    /** True when a 403 body carries GitHub's rate-limit wording. */
    internal fun isRateLimitedBody(body: String): Boolean {
        if (body.isBlank()) return false
        return body.contains("API rate limit exceeded", ignoreCase = true) ||
            body.contains("secondary rate limit", ignoreCase = true)
    }

    /** Formats an epoch-seconds reset as HH:MM in device locale/timezone. */
    internal fun formatRetryTime(resetEpochSec: Long): String {
        return try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }.format(Date(resetEpochSec * 1000))
        } catch (_: Exception) {
            SimpleDateFormat("HH:mm", Locale.US).format(Date(resetEpochSec * 1000))
        }
    }

    /**
     * Prefers the APK asset: releases may also attach checksums or bundles,
     * and blindly taking the first asset can hand the downloader a
     * non-installable file.
     */
    internal fun selectApkAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        return assets.firstOrNull {
            it.browserDownloadUrl.substringBefore('?')
                .endsWith(".apk", ignoreCase = true)
        } ?: assets.firstOrNull()
    }

    /**
     * Network-free release decision for a 200 payload. Semver-vs-versionName
     * decides; the trailing-int-vs-versionCode scheme is only a fallback for
     * non-semver tags (it once offered updates while on latest, v1.0.2 loop).
     */
    internal fun decideRelease(
        tagName: String,
        body: String,
        assets: List<ReleaseAsset>,
        appVersionName: String,
        currentVersionCode: Long
    ): ReleaseDecision {
        val tagVersion = parseSemver(tagName)
        val appVersion = parseSemver(appVersionName)
        if (tagVersion != null && appVersion != null) {
            if (compareSemver(appVersion, tagVersion) >= 0) {
                return ReleaseDecision.UpToDate
            }
        } else {
            val latestCode = trailingVersionCode(tagName)
            if (latestCode != null) {
                if (latestCode <= currentVersionCode) {
                    return ReleaseDecision.UpToDate
                }
            } else {
                // Tag has no trailing int (e.g. "release-abc"): fall back to
                // versionName comparison; equal means up to date.
                val stripped = tagName.trimStart('v', 'V')
                if (stripped.isBlank()) {
                    return ReleaseDecision.BadData
                }
                if (stripped == appVersionName) {
                    return ReleaseDecision.UpToDate
                }
            }
        }
        val asset = selectApkAsset(assets)
        if (asset == null || asset.browserDownloadUrl.isBlank()) {
            return ReleaseDecision.NoAsset
        }
        return ReleaseDecision.Available(
            version = tagName.trimStart('v', 'V').ifBlank { tagName },
            notes = body,
            assetUrl = asset.browserDownloadUrl,
            sizeBytes = asset.size
        )
    }

    /**
     * Network-free decision from the persisted cache for a 304 response.
     * Offers [ReleaseDecision.Available] only when the stored tag is NEWER
     * than the installed version AND a cached asset URL exists; otherwise
     * the install is already current.
     */
    internal fun decideCachedRelease(
        cachedTag: String,
        cachedNotes: String,
        cachedAssetUrl: String,
        cachedSize: Long,
        appVersionName: String,
        currentVersionCode: Long
    ): ReleaseDecision {
        if (cachedTag.isBlank()) return ReleaseDecision.UpToDate
        val tagVersion = parseSemver(cachedTag)
        val appVersion = parseSemver(appVersionName)
        val isNewer = if (tagVersion != null && appVersion != null) {
            compareSemver(tagVersion, appVersion) > 0
        } else {
            val latestCode = trailingVersionCode(cachedTag)
            if (latestCode != null) {
                latestCode > currentVersionCode
            } else {
                cachedTag.trimStart('v', 'V') != appVersionName
            }
        }
        if (!isNewer || cachedAssetUrl.isBlank()) {
            return ReleaseDecision.UpToDate
        }
        return ReleaseDecision.Available(
            version = cachedTag.trimStart('v', 'V').ifBlank { cachedTag },
            notes = cachedNotes,
            assetUrl = cachedAssetUrl,
            sizeBytes = cachedSize
        )
    }

    /**
     * Parses "1.0.3" (leading 'v' allowed) into [major, minor, patch].
     * Non-numeric suffixes stop parsing (e.g. "1.0.3-beta" -> [1, 0, 3]).
     * Returns null when no leading numeric component exists.
     */
    internal fun parseSemver(version: String): List<Int>? {
        val stripped = version.trim().trimStart('v', 'V')
        if (stripped.isEmpty()) return null
        val parts = stripped.split('.', '-').take(3).map { part ->
            part.takeWhile { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        if (parts.firstOrNull() == null) return null
        return (parts.map { it ?: 0 } + listOf(0, 0, 0)).take(3)
    }

    /** Lexicographic compare; >0 means [a] is newer than [b]. */
    internal fun compareSemver(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrElse(i) { 0 }) - (b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /**
     * Parses the trailing int after the last '.' or '-' of a tag with an
     * optional leading 'v' (e.g. "v1.0.3" -> 3, "release-12" -> 12).
     * Returns null when no trailing int exists.
     */
    internal fun trailingVersionCode(tag: String): Long? {
        val stripped = tag.trim().trimStart('v', 'V')
        if (stripped.isEmpty()) return null
        val tail = stripped.split('.', '-').lastOrNull()?.trim().orEmpty()
        return tail.takeWhile { it.isDigit() }
            .takeIf { it.isNotEmpty() }
            ?.toLongOrNull()
    }
}
