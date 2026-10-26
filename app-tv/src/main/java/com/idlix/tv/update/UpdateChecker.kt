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
import java.util.concurrent.TimeUnit

/**
 * Checks the latest stable GitHub release against [BuildConfig.VERSION_CODE].
 *
 * Fail-closed and offline-safe: airplane mode, DNS failure, timeouts, HTTP
 * errors and malformed payloads all surface as [UpdateState.Failed], never throw.
 */
object UpdateChecker {

    const val LATEST_RELEASE_URL = "https://api.github.com/repos/trefeon/idlix-tv/releases/latest"

    @Serializable
    private data class ReleaseAsset(
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
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .get()
                .build()
            checkClient.newCall(request).execute().use { response ->
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
                // Prefer semantic comparison: the installed app is up to date
                // when its versionName is equal to or newer than the tag.
                // versionCode is only a fallback for non-semver tags.
                val tagVersion = parseSemver(release.tagName)
                val appVersion = parseSemver(BuildConfig.VERSION_NAME)
                if (tagVersion != null && appVersion != null) {
                    if (compareSemver(appVersion, tagVersion) >= 0) {
                        return@withContext UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                    }
                } else {
                    val latestCode = trailingVersionCode(release.tagName)
                    if (latestCode != null) {
                        if (latestCode <= currentVersionCode) {
                            return@withContext UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                        }
                    } else {
                        // Tag has no trailing int (e.g. "release-abc"): fall back to
                        // versionName comparison; equal means up to date.
                        val stripped = release.tagName.trimStart('v', 'V')
                        if (stripped.isBlank()) {
                            return@withContext UpdateState.Failed(context.getString(R.string.update_err_bad_data))
                        }
                        if (stripped == BuildConfig.VERSION_NAME) {
                            return@withContext UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                        }
                    }
                }
                // Prefer the APK asset: releases may also attach checksums or
                // bundles, and blindly taking the first asset can hand the
                // downloader a non-installable file.
                val asset = release.assets.firstOrNull {
                    it.browserDownloadUrl.substringBefore('?')
                        .endsWith(".apk", ignoreCase = true)
                } ?: release.assets.firstOrNull()
                if (asset == null || asset.browserDownloadUrl.isBlank()) {
                    return@withContext UpdateState.Failed(context.getString(R.string.update_err_no_asset))
                }
                UpdateState.Available(
                    version = release.tagName.trimStart('v', 'V').ifBlank { release.tagName },
                    notes = release.body,
                    assetUrl = asset.browserDownloadUrl,
                    sizeBytes = asset.size
                )
            }
        } catch (e: IOException) {
            UpdateState.Failed(context.getString(R.string.update_err_network, e.message?.take(120) ?: "—"))
        } catch (e: Exception) {
            UpdateState.Failed(context.getString(R.string.update_err_failed, e.message?.take(120) ?: "—"))
        }
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
