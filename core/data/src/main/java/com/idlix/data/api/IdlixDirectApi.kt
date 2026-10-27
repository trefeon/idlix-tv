package com.idlix.data.api

import com.idlix.data.model.ClaimRequest
import com.idlix.data.model.ClaimResponse
import com.idlix.data.model.CollectionDetailResponse
import com.idlix.data.model.CollectionListResponse
import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.GenreListResponse
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.PlayInfoResponse
import com.idlix.data.model.RedeemRequest
import com.idlix.data.model.RedeemResponse
import com.idlix.data.model.SearchResponse
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SeriesListResponse
import com.idlix.data.net.CoreHttp
import com.idlix.data.net.IdlixDomainResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/**
 * Thrown when the live IDLIX mirror answers with a Cloudflare challenge
 * instead of JSON. Callers surface this as an honest "mirror is protected"
 * message — never as fake offline copy.
 */
class MirrorProtectedException(message: String) : IOException(message)

/** Markers of a Cloudflare bot-challenge page served in place of the API. */
private val CHALLENGE_MARKERS = listOf(
    "Just a moment",
    "cf-mitigated",
    "challenges.cloudflare.com"
)

/**
 * Builds `{base}{path}?k=v&…`, skipping null/blank values. Pure, unit-tested.
 */
fun buildApiUrl(
    baseUrl: String,
    path: String,
    params: Map<String, String?> = emptyMap()
): String {
    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    val cleanPath = path.trimStart('/')
    val query = params.entries
        .filter { !it.value.isNullOrBlank() }
        .joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v!!.trim(), "UTF-8")}"
        }
    return if (query.isEmpty()) "$base$cleanPath" else "$base$cleanPath?$query"
}

/**
 * Extracts the owner device id (`did`) from a gate token's first segment
 * (`base64url({"owner":"d:…"})`). Returns null when absent/undecodable. Pure.
 */
fun extractDid(gateToken: String): String? {
    if (gateToken.isBlank()) return null
    val first = gateToken.substringBefore(".")
    if (first.isBlank()) return null
    val decoded = decodeBase64Url(first) ?: return null
    return Regex(""""owner":"d:([^"]+)"""")
        .find(decoded)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}

/** Minimal base64url decoder (no Android/Java8 API dependency; JVM-testable). */
internal fun decodeBase64Url(input: String): String? {
    if (input.isBlank()) return null
    var s = input.trim().replace('-', '+').replace('_', '/')
    val pad = (4 - s.length % 4) % 4
    repeat(pad) { s += "=" }
    val table = IntArray(256) { -1 }
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, c -> table[c.code] = i }
    return try {
        val out = ByteArray(s.length * 3 / 4)
        var o = 0
        var i = 0
        while (i < s.length) {
            val c0 = table[s[i].code and 0xFF]
            val c1 = table[s[i + 1].code and 0xFF]
            val c2 = if (s[i + 2] == '=') 0 else table[s[i + 2].code and 0xFF]
            val c3 = if (s[i + 3] == '=') 0 else table[s[i + 3].code and 0xFF]
            if (c0 < 0 || c1 < 0 || c2 < 0 || c3 < 0) return null
            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            out[o++] = ((triple shr 16) and 0xFF).toByte()
            if (s[i + 2] != '=') out[o++] = ((triple shr 8) and 0xFF).toByte()
            if (s[i + 3] != '=') out[o++] = (triple and 0xFF).toByte()
            i += 4
        }
        String(out, 0, o, Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

/**
 * Server-synchronized countdown: `min(unlockAt - serverNow + 500, 16000)` ms.
 * Already unlocked (`unlockAt <= serverNow`) claims immediately — the server
 * enforces the gate, so no client wait is needed; the claim retry loop covers
 * the race. Falls back to 15s only when the clock pair is unusable.
 * Pure, unit-tested.
 */
fun countdownDelayMs(serverNow: Long, unlockAt: Long): Long {
    if (serverNow <= 0) return 15_000L
    if (unlockAt <= serverNow) return 0L
    return minOf(unlockAt - serverNow + 500L, 16_000L).coerceAtLeast(0L)
}

/**
 * True when an HTTP status plus a body peek looks like a Cloudflare challenge
 * served in place of the API. Pure, unit-tested.
 */
fun isChallenge(statusCode: Int, bodyPeek: String): Boolean {
    if (statusCode != 403) return false
    return CHALLENGE_MARKERS.any { bodyPeek.contains(it, ignoreCase = true) }
}

/**
 * Direct-source client for the live IDLIX mirror.
 *
 * Trivial GET catalog (`Accept: application/json`), `Origin`/`Referer` derived
 * per call from [IdlixDomainResolver], JSON via [CoreHttp.json]. Challenge
 * bodies surface as [MirrorProtectedException]; every other transport problem
 * surfaces as [IOException] with an honest message. Single-attempt calls —
 * retry policy lives in the repository. `search` with a blank query returns an
 * empty response without touching the network.
 */
open class IdlixDirectApi {

    open suspend fun getMovies(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        genre: String? = null
    ): MovieListResponse = withContext(Dispatchers.IO) {
        val body = getJson(
            "api/movies",
            mapOf("page" to page.toString(), "limit" to limit.toString(), "sort" to sort, "genre" to genre)
        )
        CoreHttp.json.decodeFromString(MovieListResponse.serializer(), body)
    }

    open suspend fun getSeries(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        genre: String? = null
    ): SeriesListResponse = withContext(Dispatchers.IO) {
        val body = getJson(
            "api/series",
            mapOf("page" to page.toString(), "limit" to limit.toString(), "sort" to sort, "genre" to genre)
        )
        CoreHttp.json.decodeFromString(SeriesListResponse.serializer(), body)
    }

    open suspend fun getMovieDetail(slug: String): MovieDetail = withContext(Dispatchers.IO) {
        val body = getJson("api/movies/$slug")
        CoreHttp.json.decodeFromString(MovieDetail.serializer(), body)
    }

    open suspend fun getSeriesDetail(slug: String): SeriesDetail = withContext(Dispatchers.IO) {
        val body = getJson("api/series/$slug")
        CoreHttp.json.decodeFromString(SeriesDetail.serializer(), body)
    }

    open suspend fun getEpisodeDetail(slug: String, season: Int, episode: Int): EpisodeResponse =
        withContext(Dispatchers.IO) {
            val body = getJson("api/series/$slug/season/$season/episode/$episode")
            CoreHttp.json.decodeFromString(EpisodeResponse.serializer(), body)
        }

    open suspend fun getGenres(): GenreListResponse = withContext(Dispatchers.IO) {
        val body = getJson("api/genres")
        CoreHttp.json.decodeFromString(GenreListResponse.serializer(), body)
    }

    open suspend fun getCollections(): CollectionListResponse = withContext(Dispatchers.IO) {
        val body = getJson("api/collections")
        CoreHttp.json.decodeFromString(CollectionListResponse.serializer(), body)
    }

    open suspend fun getCollectionDetail(slug: String): CollectionDetailResponse =
        withContext(Dispatchers.IO) {
            val body = getJson("api/collections/$slug")
            CoreHttp.json.decodeFromString(CollectionDetailResponse.serializer(), body)
        }

    open suspend fun search(query: String): SearchResponse {
        if (query.isBlank()) return SearchResponse()
        return withContext(Dispatchers.IO) {
            val body = getJson("api/search", mapOf("q" to query.trim()))
            CoreHttp.json.decodeFromString(SearchResponse.serializer(), body)
        }
    }

    /** `type` is `movie` or `episode`. */
    open suspend fun getPlayInfo(type: String, id: String): PlayInfoResponse =
        withContext(Dispatchers.IO) {
            val body = getJson("api/watch/play-info/$type/$id")
            CoreHttp.json.decodeFromString(PlayInfoResponse.serializer(), body)
        }

    /** Single claim attempt; the repository retries until the claim turns valid. */
    open suspend fun claimSession(gateToken: String, cookie: String?): ClaimResponse =
        withContext(Dispatchers.IO) {
            val body = postJson(
                url = "${IdlixDomainResolver.currentBaseUrl().trimEnd('/')}/api/watch/session/claim",
                jsonBody = CoreHttp.json.encodeToString(ClaimRequest.serializer(), ClaimRequest(gateToken)),
                cookie = cookie
            )
            CoreHttp.json.decodeFromString(ClaimResponse.serializer(), body)
        }

    open suspend fun redeem(redeemUrl: String, claim: String): RedeemResponse =
        withContext(Dispatchers.IO) {
            val body = postJson(
                url = redeemUrl,
                jsonBody = CoreHttp.json.encodeToString(RedeemRequest.serializer(), RedeemRequest(claim)),
                cookie = null
            )
            CoreHttp.json.decodeFromString(RedeemResponse.serializer(), body)
        }

    private fun getJson(path: String, params: Map<String, String?> = emptyMap()): String {
        val url = buildApiUrl(IdlixDomainResolver.currentBaseUrl(), path, params)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Origin", IdlixDomainResolver.origin())
            .header("Referer", IdlixDomainResolver.referer())
            .get()
            .build()
        return executeForJson(request)
    }

    private fun postJson(url: String, jsonBody: String, cookie: String?): String {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Origin", IdlixDomainResolver.origin())
            .header("Referer", IdlixDomainResolver.referer())
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
        if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
        return executeForJson(builder.build())
    }

    private fun executeForJson(request: Request): String {
        try {
            CoreHttp.okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (isChallenge(response.code, body.take(CHALLENGE_PEEK_CHARS))) {
                        throw MirrorProtectedException(
                            "Mirror IDLIX sedang dibatasi Cloudflare. " +
                                "Buka situsnya sekali di browser, lalu coba lagi."
                        )
                    }
                    throw IOException("Mirror IDLIX menjawab HTTP ${response.code}. Coba lagi nanti.")
                }
                if (isChallenge(response.code, body.take(CHALLENGE_PEEK_CHARS))) {
                    throw MirrorProtectedException(
                        "Mirror IDLIX sedang dibatasi Cloudflare. " +
                            "Buka situsnya sekali di browser, lalu coba lagi."
                    )
                }
                if (body.isBlank()) throw IOException("Mirror IDLIX memberi jawaban kosong. Coba lagi nanti.")
                return body
            }
        } catch (e: MirrorProtectedException) {
            throw e
        } catch (e: IOException) {
            if (e.message?.startsWith("Mirror IDLIX") == true) throw e
            throw IOException("Tidak dapat menghubungi mirror IDLIX. Periksa koneksi internet lalu coba lagi.", e)
        }
    }

    companion object {
        private const val CHALLENGE_PEEK_CHARS = 8_192
    }
}
