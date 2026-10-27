package com.idlix.tv.update

import android.content.Context
import com.idlix.data.net.CoreHttp
import com.idlix.tv.BuildConfig
import com.idlix.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Checks the latest stable GitHub release against [BuildConfig.VERSION_NAME]
 * (semver-first, trailing-int fallback).
 *
 * No GitHub REST API: plain HTTPS against github.com release pages, so there
 * is no token and no 60 req/hour REST quota to burn through on shared IPs.
 * `.../releases/latest` is an unmetered server-side redirect to
 * `.../releases/tag/<tag>`; the APK URL is deterministic from the tag and is
 * HEAD-verified before being offered.
 *
 * Fail-closed and offline-safe: airplane mode, DNS failure, timeouts, HTTP
 * errors and malformed redirects all surface as [UpdateState.Failed], never throw.
 */
object UpdateChecker {

    internal const val GITHUB_OWNER = "trefeon"
    internal const val GITHUB_REPO = "idlix-tv"
    internal const val ASSET_PREFIX = "idlix-tv-"

    const val LATEST_RELEASE_URL = "https://github.com/trefeon/idlix-tv/releases/latest"

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

    // No-redirect client, independent from the catalog resolver: redirects ARE
    // the signal here (Location -> tag), so followRedirects must stay off.
    // Used ONLY for the .../releases/latest tag redirect below.
    private val noRedirectClient by lazy {
        CoreHttp.okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // Redirect-following client for the asset probe: .../releases/download/...
    // answers 302 to objects.githubusercontent.com, so a no-redirect probe
    // would always observe 302 and report "no asset" for an existing APK.
    private val followClient by lazy {
        CoreHttp.okHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdate(
        context: Context,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
    ): UpdateState = withContext(Dispatchers.IO) {
        try {
            // 1. Unmetered redirect: .../releases/latest -> .../releases/tag/<tag>.
            val tag: String = noRedirectClient.newCall(
                Request.Builder().url(LATEST_RELEASE_URL).get().build()
            ).execute().use { response ->
                if (!isRedirect(response.code)) {
                    return@withContext UpdateState.Failed(
                        context.getString(R.string.update_err_http, response.code)
                    )
                }
                parseTagFromLocation(response.header("Location"))
                    ?: return@withContext UpdateState.Failed(
                        context.getString(R.string.update_err_bad_data)
                    )
            }
            // 2. Decide BEFORE probing: an up-to-date install needs no asset
            // network at all (and a probe 302 must never mask UpToDate).
            val assetUrl = assetUrlForTag(tag)
            when (
                decideRelease(
                    tagName = tag,
                    appVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = currentVersionCode,
                    assetUrl = assetUrl,
                    sizeBytes = 0L
                )
            ) {
                is ReleaseDecision.UpToDate ->
                    return@withContext UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                is ReleaseDecision.BadData ->
                    return@withContext UpdateState.Failed(
                        context.getString(R.string.update_err_bad_data)
                    )
                else -> {}
            }
            // 3. Probe the asset (redirect-following: download URLs 302);
            // 200 (or range-GET 200/206) = installable.
            val sizeBytes = probeAssetSize(assetUrl)
                ?: return@withContext UpdateState.Failed(
                    context.getString(R.string.update_err_no_asset)
                )
            // 4. Semver-vs-versionName decides; trailing int is only a fallback.
            return@withContext when (
                val decision = decideRelease(
                    tagName = tag,
                    appVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = currentVersionCode,
                    assetUrl = assetUrl,
                    sizeBytes = sizeBytes
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
        } catch (e: IOException) {
            UpdateState.Failed(context.getString(R.string.update_err_network, e.message?.take(120) ?: "—"))
        } catch (e: Exception) {
            UpdateState.Failed(context.getString(R.string.update_err_failed, e.message?.take(120) ?: "—"))
        }
    }

    /**
     * HEAD-verifies the deterministic asset URL, with a single-byte range GET
     * fallback for servers that block HEAD. Returns size bytes (0 = unknown
     * length, still installable), or null when the APK is missing. Network
     * failures throw and surface as network errors, not "no asset".
     */
    private fun probeAssetSize(assetUrl: String): Long? {
        var headCode = 0
        var headLength: String? = null
        followClient.newCall(
            Request.Builder().url(assetUrl).head().build()
        ).execute().use { head ->
            headCode = head.code
            headLength = head.header("Content-Length")
        }
        if (headCode == 200) return parseContentLength(headLength)
        var rangeCode: Int? = null
        var rangeLength: String? = null
        followClient.newCall(
            Request.Builder().url(assetUrl)
                .header("Range", "bytes=0-0")
                .get().build()
        ).execute().use { range ->
            rangeCode = range.code
            rangeLength = range.header("Content-Length")
        }
        return resolveAssetSize(headCode, headLength, rangeCode, rangeLength)
    }

    // ------------------------------------------------------------------ pure

    /** True for the redirect codes .../releases/latest may answer with. */
    internal fun isRedirect(code: Int): Boolean {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308
    }

    /**
     * Extracts the tag from a .../releases/latest redirect Location, absolute
     * or relative, with optional trailing slash or query. Returns null when
     * the Location is absent or does not point at a release tag.
     */
    internal fun parseTagFromLocation(location: String?): String? {
        if (location.isNullOrBlank()) return null
        val path = location.substringBefore('?').substringBefore('#')
        val marker = "/releases/tag/"
        val idx = path.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null
        val after = path.substring(idx + marker.length)
        if (after.isBlank()) return null
        val tag = after.split('/').firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return tag.takeIf { it.isNotEmpty() }
    }

    /**
     * Deterministic APK URL for a tag, mirroring android-release.yml naming
     * (dist/idlix-tv-<tag>.apk, tag keeps its leading 'v').
     */
    internal fun assetUrlForTag(tag: String): String {
        return "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$tag/$ASSET_PREFIX$tag.apk"
    }

    /** Positive Content-Length, else 0 (unknown, still installable). */
    internal fun parseContentLength(headerValue: String?): Long {
        return headerValue?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: 0L
    }

    /**
     * Offline probe decision: HEAD 200 wins on its own length; otherwise a
     * range-GET 200/206 wins on its length; anything else means the APK is
     * missing (null).
     */
    internal fun resolveAssetSize(
        headCode: Int,
        headLength: String?,
        rangeCode: Int?,
        rangeLength: String?
    ): Long? {
        if (headCode == 200) return parseContentLength(headLength)
        if (rangeCode == 200 || rangeCode == 206) return parseContentLength(rangeLength)
        return null
    }

    /**
     * Network-free release decision for a redirect-derived tag. Semver-vs-
     * versionName decides; the trailing-int-vs-versionCode scheme is only a
     * fallback for non-semver tags (it once offered updates while on latest,
     * v1.0.2 loop). Release-page bodies are unavailable without the API, so
     * notes are always empty.
     */
    internal fun decideRelease(
        tagName: String,
        appVersionName: String,
        currentVersionCode: Long,
        assetUrl: String,
        sizeBytes: Long
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
        if (assetUrl.isBlank()) {
            return ReleaseDecision.NoAsset
        }
        return ReleaseDecision.Available(
            version = tagName.trimStart('v', 'V').ifBlank { tagName },
            notes = "",
            assetUrl = assetUrl,
            sizeBytes = sizeBytes
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
