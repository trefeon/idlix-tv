package com.idlix.tv.update

import com.idlix.data.net.CoreHttp
import com.idlix.tv.BuildConfig
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
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
    ): UpdateState = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .get()
                .build()
            checkClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateState.Failed("HTTP ${response.code}")
                }
                val payload = response.body?.string().orEmpty()
                if (payload.isBlank()) {
                    return@withContext UpdateState.Failed("Empty release response")
                }
                val release = try {
                    CoreHttp.json.decodeFromString<ReleaseResponse>(payload)
                } catch (e: Exception) {
                    return@withContext UpdateState.Failed("Bad release data")
                }
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
                        return@withContext UpdateState.Failed("Bad release data")
                    }
                    if (stripped == BuildConfig.VERSION_NAME) {
                        return@withContext UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                    }
                }
                val asset = release.assets.firstOrNull()
                if (asset == null || asset.browserDownloadUrl.isBlank()) {
                    return@withContext UpdateState.Failed("No APK asset")
                }
                UpdateState.Available(
                    version = release.tagName.trimStart('v', 'V').ifBlank { release.tagName },
                    notes = release.body,
                    assetUrl = asset.browserDownloadUrl,
                    sizeBytes = asset.size
                )
            }
        } catch (e: IOException) {
            UpdateState.Failed(e.message?.take(120) ?: "Network error")
        } catch (e: Exception) {
            UpdateState.Failed(e.message?.take(120) ?: "Check failed")
        }
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
