package com.idlix.data.repository

import com.idlix.data.api.IdlixDirectApi
import com.idlix.data.api.countdownDelayMs
import com.idlix.data.api.extractDid
import com.idlix.data.cache.DirectSourceCache
import com.idlix.data.cache.DirectSourceCache.Companion.DETAIL_TTL_MS
import com.idlix.data.cache.DirectSourceCache.Companion.GENRE_TTL_MS
import com.idlix.data.cache.DirectSourceCache.Companion.LIST_TTL_MS
import com.idlix.data.cache.DirectSourceCache.Companion.SEARCH_TTL_MS
import com.idlix.data.model.CollectionDetailResponse
import com.idlix.data.model.CollectionListResponse
import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.GenreListResponse
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieItem
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.SearchResultItem
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SeriesItem
import com.idlix.data.model.SeriesListResponse
import com.idlix.data.net.CoreHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Real direct-source repository over the live IDLIX mirror
 * ([IdlixDirectApi] + [IdlixDomainResolver]-derived base URL).
 *
 * Old stub → new implementation mapping (signatures unchanged):
 *
 * | stub (empty/fail-closed) | now |
 * |---|---|
 * | `getMovies` → `MovieListResponse()` | `GET api/movies`, cached [LIST_TTL_MS] |
 * | `getSeries` → `SeriesListResponse()` | `GET api/series`, cached [LIST_TTL_MS] |
 * | `getTrendingMovies` → `emptyList()` | first page, sort `trending` → `popular` → `popularity` fallback |
 * | `getPopularMovies` → `emptyList()` | first page, unfiltered |
 * | `getMoviesBySort` → `emptyList()` | `GET api/movies?sort=` |
 * | `getMoviesByGenre` → `emptyList()` | `GET api/movies?genre=` |
 * | `getPopularSeries` → `emptyList()` | first page, sort `popularity` |
 * | `getSeriesBySort` → `emptyList()` | `GET api/series?sort=` |
 * | `getSeriesByGenre` → `emptyList()` | `GET api/series?genre=` |
 * | `getMovieDetail` → null | `GET api/movies/{slug}`, cached [DETAIL_TTL_MS] |
 * | `getSeriesDetail` → null | `GET api/series/{slug}`, cached [DETAIL_TTL_MS] |
 * | `getEpisodeDetail` → null | `GET api/series/{slug}/season/{s}/episode/{e}`, cached [DETAIL_TTL_MS] |
 * | `getGenres` (new) | `GET api/genres`, cached [GENRE_TTL_MS] |
 * | `getCollections` (new) | `GET api/collections`, cached [GENRE_TTL_MS] |
 * | `getCollectionDetail` (new) | `GET api/collections/{slug}`, cached [DETAIL_TTL_MS] |
 * | `search` → `emptyList()` | `GET api/search?q=`, cached [SEARCH_TTL_MS]; blank query → empty, no network |
 * | `prewarmStream` → no-op | starts the unlock countdown early; result is NOT cached |
 * | `resolveStream` → failure("not yet wired") | full gate chain; `series` normalizes to first episode; failures carry honest copy |
 *
 * Cloudflare risk: when the mirror serves a challenge page instead of JSON,
 * reads throw [com.idlix.data.api.MirrorProtectedException] (not fake
 * offline copy). This path cannot be verified from CI here — validate on
 * device against the live mirror.
 */
class IdlixRepository(
    private val api: IdlixDirectApi = IdlixDirectApi(),
    private val cache: DirectSourceCache = DirectSourceCache()
) {

    // ------------------------------------------------------------ catalog

    suspend fun getMovies(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        genre: String? = null
    ): MovieListResponse = withContext(Dispatchers.IO) {
        val key = "movies|page=$page&limit=$limit&sort=$sort&genre=$genre"
        cache.get(key, MovieListResponse::class.java) ?: api.getMovies(page, limit, sort, genre).also {
            cache.put(key, it, LIST_TTL_MS)
        }
    }

    suspend fun getSeries(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        genre: String? = null
    ): SeriesListResponse = withContext(Dispatchers.IO) {
        val key = "series|page=$page&limit=$limit&sort=$sort&genre=$genre"
        cache.get(key, SeriesListResponse::class.java) ?: api.getSeries(page, limit, sort, genre).also {
            cache.put(key, it, LIST_TTL_MS)
        }
    }

    suspend fun getTrendingMovies(): List<MovieItem> = withContext(Dispatchers.IO) {
        val sorts = listOf("trending", "popular", "popularity")
        for (sort in sorts) {
            val items = runCatching { getMovies(page = 1, limit = 20, sort = sort).data }.getOrNull()
            if (!items.isNullOrEmpty()) return@withContext items
        }
        runCatching { getMovies(page = 1, limit = 20).data }.getOrNull() ?: emptyList()
    }

    suspend fun getPopularMovies(): List<MovieItem> = withContext(Dispatchers.IO) {
        runCatching { getMovies(page = 1, limit = 20).data }.getOrNull() ?: emptyList()
    }

    suspend fun getMoviesBySort(sort: String): List<MovieItem> = withContext(Dispatchers.IO) {
        runCatching { getMovies(page = 1, limit = 20, sort = sort).data }.getOrNull() ?: emptyList()
    }

    suspend fun getMoviesByGenre(genreSlug: String): List<MovieItem> = withContext(Dispatchers.IO) {
        runCatching { getMovies(page = 1, limit = 20, genre = genreSlug).data }.getOrNull() ?: emptyList()
    }

    suspend fun getPopularSeries(): List<SeriesItem> = withContext(Dispatchers.IO) {
        runCatching { getSeries(page = 1, limit = 20, sort = "popularity").data }.getOrNull()
            ?: runCatching { getSeries(page = 1, limit = 20).data }.getOrNull()
            ?: emptyList()
    }

    suspend fun getSeriesBySort(sort: String): List<SeriesItem> = withContext(Dispatchers.IO) {
        runCatching { getSeries(page = 1, limit = 20, sort = sort).data }.getOrNull() ?: emptyList()
    }

    suspend fun getSeriesByGenre(genreSlug: String): List<SeriesItem> = withContext(Dispatchers.IO) {
        runCatching { getSeries(page = 1, limit = 20, genre = genreSlug).data }.getOrNull() ?: emptyList()
    }

    suspend fun getMovieDetail(slug: String): MovieDetail? = withContext(Dispatchers.IO) {
        val key = "movie|$slug"
        cache.get(key, MovieDetail::class.java) ?: api.getMovieDetail(slug).also {
            cache.put(key, it, DETAIL_TTL_MS)
        }
    }

    suspend fun getSeriesDetail(slug: String): SeriesDetail? = withContext(Dispatchers.IO) {
        val key = "series|$slug"
        cache.get(key, SeriesDetail::class.java) ?: api.getSeriesDetail(slug).also {
            cache.put(key, it, DETAIL_TTL_MS)
        }
    }

    suspend fun getEpisodeDetail(slug: String, season: Int, episode: Int): EpisodeResponse? =
        withContext(Dispatchers.IO) {
            val key = "episode|$slug|$season|$episode"
            cache.get(key, EpisodeResponse::class.java) ?: api.getEpisodeDetail(slug, season, episode).also {
                cache.put(key, it, DETAIL_TTL_MS)
            }
        }

    suspend fun getGenres(): GenreListResponse = withContext(Dispatchers.IO) {
        val key = "genres"
        cache.get(key, GenreListResponse::class.java) ?: api.getGenres().also {
            cache.put(key, it, GENRE_TTL_MS)
        }
    }

    /**
     * Curated collections index. Cached [GENRE_TTL_MS]: editorial content
     * that changes as rarely as the genre list, not per-request catalog data.
     */
    suspend fun getCollections(): CollectionListResponse = withContext(Dispatchers.IO) {
        val key = "collections"
        cache.get(key, CollectionListResponse::class.java) ?: api.getCollections().also {
            cache.put(key, it, GENRE_TTL_MS)
        }
    }

    /**
     * One collection with its phase sections and per-title entries. Cached
     * [DETAIL_TTL_MS] like movie/series details (title lists, same staleness).
     */
    suspend fun getCollectionDetail(slug: String): CollectionDetailResponse? =
        withContext(Dispatchers.IO) {
            val key = "collection|$slug"
            cache.get(key, CollectionDetailResponse::class.java)
                ?: api.getCollectionDetail(slug).also {
                    cache.put(key, it, DETAIL_TTL_MS)
                }
        }

    suspend fun search(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val key = "search|$trimmed"
        cache.get(key, List::class.java)?.filterIsInstance<SearchResultItem>()
            ?: api.search(trimmed).results.also {
            cache.put(key, it, SEARCH_TTL_MS)
        }
    }

    // ------------------------------------------------------------ playback

    companion object {
        private const val CLAIM_MAX_ATTEMPTS = 10
        private const val CLAIM_RETRY_DELAY_MS = 600L
        private const val REDEEM_FALLBACK_URL = "https://e2e.majorplay.net/api/play"

        private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val activeResolves = ConcurrentHashMap<String, Deferred<Result<ResolvedPlayback>>>()
    }

    /**
     * Pre-warms the stream unlock while the user reads details. Shares one
     * in-flight job with [resolveStream]; the resolved playback is NOT cached.
     */
    fun prewarmStream(contentType: String, contentId: String, contentTitle: String) {
        val cacheKey = "$contentType:$contentId"
        if (activeResolves.containsKey(cacheKey)) return
        val deferred = repoScope.async {
            try {
                resolveStreamInternal(contentType, contentId, contentTitle, onCountdown = null)
            } finally {
                activeResolves.remove(cacheKey)
            }
        }
        activeResolves.putIfAbsent(cacheKey, deferred)
    }

    suspend fun resolveStream(
        contentType: String,
        contentId: String,
        contentTitle: String,
        onCountdown: ((Int) -> Unit)? = null
    ): Result<ResolvedPlayback> = withContext(Dispatchers.IO) {
        val cacheKey = "$contentType:$contentId"

        // In-flight pre-warm hit: await the same job instead of unlocking twice.
        val ongoing = activeResolves[cacheKey]
        if (ongoing != null) {
            return@withContext try {
                val res = ongoing.await()
                if (res.isSuccess) onCountdown?.invoke(0)
                res
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val deferred = async {
            try {
                resolveStreamInternal(contentType, contentId, contentTitle, onCountdown)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        activeResolves[cacheKey] = deferred
        try {
            deferred.await()
        } finally {
            activeResolves.remove(cacheKey)
        }
    }

    private suspend fun resolveStreamInternal(
        contentType: String,
        contentId: String,
        contentTitle: String,
        onCountdown: ((Int) -> Unit)? = null
    ): Result<ResolvedPlayback> {
        try {
            var targetType = contentType
            var targetId = contentId

            // A TV series id normalizes to its first episode.
            if (contentType == "tv_series") {
                val series = try {
                    getSeriesDetail(contentId)
                } catch (e: Exception) {
                    return Result.failure(e)
                }
                val firstEp = series?.defaultSeason?.episodes?.firstOrNull()
                    ?: series?.seasons?.firstOrNull()?.let { s ->
                        try {
                            getEpisodeDetail(series.slug, s.seasonNumber, 1)?.episode
                        } catch (_: Exception) {
                            null
                        }
                    }
                if (firstEp != null) {
                    targetType = "episode"
                    targetId = firstEp.id
                } else {
                    return Result.failure(Exception("Tidak ada episode yang tersedia untuk serial ini."))
                }
            }

            // Step 1: gate token + server clock pair.
            val playInfo = try {
                api.getPlayInfo(if (targetType == "episode") "episode" else "movie", targetId)
            } catch (e: Exception) {
                return Result.failure(e)
            }
            if (playInfo.gateToken.isBlank()) {
                return Result.failure(Exception("Gagal memperoleh token pemutaran. Coba lagi nanti."))
            }

            // Step 2: server-synchronized countdown.
            val delayMs = countdownDelayMs(playInfo.serverNow, playInfo.unlockAt)
            val didCookie = extractDid(playInfo.gateToken)?.let { "did=$it" }
            var secondsLeft = (delayMs / 1000L).toInt()
            while (secondsLeft > 1) {
                onCountdown?.invoke(secondsLeft)
                delay(1000)
                secondsLeft--
            }
            if (secondsLeft > 0) {
                onCountdown?.invoke(1)
            }

            // Step 3: claim the session, retrying until the claim turns valid.
            var validClaim: com.idlix.data.model.ClaimResponse? = null
            for (attempt in 1..CLAIM_MAX_ATTEMPTS) {
                try {
                    val res = api.claimSession(playInfo.gateToken, didCookie)
                    if (res.kind == "pentos" && res.claim.isNotBlank()) {
                        validClaim = res
                        break
                    }
                } catch (_: Exception) {
                }
                delay(CLAIM_RETRY_DELAY_MS)
            }
            onCountdown?.invoke(0)

            val claimRes = validClaim
            if (claimRes == null || claimRes.claim.isBlank()) {
                return Result.failure(Exception("Gagal mengklaim sesi pemutaran. Silakan coba lagi."))
            }

            // Step 4: redeem the HLS master playlist from the media CDN.
            val redeemUrl = claimRes.redeemUrl.ifBlank { REDEEM_FALLBACK_URL }
            val redeemRes = try {
                api.redeem(redeemUrl, claimRes.claim)
            } catch (e: Exception) {
                return Result.failure(e)
            }
            if (redeemRes.url.isBlank()) {
                return Result.failure(Exception("Gagal mengambil master playlist HLS. Coba lagi nanti."))
            }

            val finalSubtitles = redeemRes.subtitles.toMutableList()
            if (finalSubtitles.none { it.lang == "id" }) {
                try {
                    val context = CoreHttp.applicationContext
                    if (context != null) {
                        val subdlSub = SubdlRepository.fetchIndonesianSubtitle(
                            title = contentTitle,
                            type = if (targetType == "episode") "tv" else "movie",
                            context = context
                        )
                        if (subdlSub != null) finalSubtitles.add(subdlSub)
                    }
                } catch (_: Exception) {
                }
            }

            return Result.success(
                ResolvedPlayback(
                    title = claimRes.title.ifBlank { contentTitle },
                    hlsMasterUrl = redeemRes.url,
                    subtitles = finalSubtitles,
                    durationSec = claimRes.durationSec,
                    videoId = claimRes.videoId
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
