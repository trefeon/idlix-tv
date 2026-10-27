package com.idlix.data.repository

import com.idlix.data.api.IdlixDirectApi
import com.idlix.data.api.MirrorProtectedException
import com.idlix.data.api.buildApiUrl
import com.idlix.data.api.countdownDelayMs
import com.idlix.data.api.extractDid
import com.idlix.data.api.isChallenge
import com.idlix.data.cache.DirectSourceCache
import com.idlix.data.cache.DirectSourceCache.Companion.DETAIL_TTL_MS
import com.idlix.data.cache.DirectSourceCache.Companion.GENRE_TTL_MS
import com.idlix.data.cache.DirectSourceCache.Companion.LIST_TTL_MS
import com.idlix.data.cache.DirectSourceCache.Companion.SEARCH_TTL_MS
import com.idlix.data.model.ClaimResponse
import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.GenreListResponse
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.PlayInfoResponse
import com.idlix.data.model.RedeemResponse
import com.idlix.data.model.SearchResponse
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SeriesListResponse
import com.idlix.data.net.CoreHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Offline tests for the direct-source slice: pure URL/did/countdown/challenge
 * helpers, canned-JSON parsing of every catalog/playback shape, the TTL cache,
 * and repository empty/fallback behavior via a fake [IdlixDirectApi].
 * No network, no Android framework.
 */
class IdlixDirectSourceTest {

    // ---------------------------------------------------------- buildApiUrl

    @Test
    fun buildApiUrlJoinsBasePathAndParams() {
        val url = buildApiUrl(
            "https://z2.idlixku.com/",
            "api/movies",
            mapOf("page" to "1", "limit" to "20", "sort" to "popular", "genre" to null)
        )
        assertEquals("https://z2.idlixku.com/api/movies?page=1&limit=20&sort=popular", url)
    }

    @Test
    fun buildApiUrlNormalizesSlashesAndSkipsBlanks() {
        val url = buildApiUrl(
            "https://z2.idlixku.com",
            "/api/series",
            mapOf("page" to "2", "sort" to "", "genre" to "  ")
        )
        assertEquals("https://z2.idlixku.com/api/series?page=2", url)
    }

    @Test
    fun buildApiUrlEncodesQueryValues() {
        val url = buildApiUrl("https://z2.idlixku.com/", "api/search", mapOf("q" to "bat man&robin"))
        assertTrue(url.startsWith("https://z2.idlixku.com/api/search?q="))
        assertTrue(url.contains("bat+man%26robin") || url.contains("bat%20man%26robin"))
    }

    @Test
    fun buildApiUrlOmitsQueryWhenAllBlank() {
        assertEquals(
            "https://z2.idlixku.com/api/genres",
            buildApiUrl("https://z2.idlixku.com/", "api/genres")
        )
    }

    // ---------------------------------------------------------- extractDid

    private fun gateTokenFor(ownerJson: String): String {
        val first = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ownerJson.toByteArray(Charsets.UTF_8))
        return "$first.rest.sig"
    }

    @Test
    fun extractDidFindsOwnerDeviceId() {
        val token = gateTokenFor("""{"owner":"d:abc123","exp":999}""")
        assertEquals("abc123", extractDid(token))
    }

    @Test
    fun extractDidReturnsNullWhenNoOwner() {
        val token = gateTokenFor("""{"sub":"guest"}""")
        assertNull(extractDid(token))
    }

    @Test
    fun extractDidReturnsNullForBlankOrGarbage() {
        assertNull(extractDid(""))
        assertNull(extractDid("   "))
        assertNull(extractDid("not-base64!!!"))
        assertNull(extractDid(".rest.sig"))
    }

    // ---------------------------------------------------------- countdownDelayMs

    @Test
    fun countdownDelayAddsGraceAndCaps() {
        assertEquals(10_500L, countdownDelayMs(serverNow = 1_000L, unlockAt = 11_000L))
        assertEquals(16_000L, countdownDelayMs(serverNow = 0L + 1, unlockAt = 1L + 100_000L))
    }

    @Test
    fun countdownDelayFallsBackOnBadClock() {
        assertEquals(15_000L, countdownDelayMs(serverNow = 0L, unlockAt = 10_000L))
    }

    @Test
    fun countdownDelaySkipsWaitWhenAlreadyUnlocked() {
        assertEquals(0L, countdownDelayMs(serverNow = 5_000L, unlockAt = 5_000L))
        assertEquals(0L, countdownDelayMs(serverNow = 9_000L, unlockAt = 1_000L))
    }

    // ---------------------------------------------------------- isChallenge

    @Test
    fun isChallengeFlagsCloudflareBodiesOn403() {
        assertTrue(isChallenge(403, "<html><title>Just a moment...</title></html>"))
        assertTrue(isChallenge(403, "error code: cf-mitigated"))
        assertTrue(isChallenge(403, "<script src='challenges.cloudflare.com/turnstile'></script>"))
        assertTrue(isChallenge(403, "JUST A MOMENT, please verify"))
    }

    @Test
    fun isChallengeRejectsCleanOrUnrelated() {
        assertFalse(isChallenge(403, """{"data":[],"pagination":{"page":1}}"""))
        assertFalse(isChallenge(403, ""))
        assertFalse(isChallenge(200, "Just a moment..."))
        assertFalse(isChallenge(500, "Just a moment..."))
        assertFalse(isChallenge(404, "not found"))
    }

    @Test
    fun mirrorProtectedExceptionIsIo() {
        val e = MirrorProtectedException("blocked")
        assertTrue(e is java.io.IOException)
        assertEquals("blocked", e.message)
    }

    // ---------------------------------------------------------- canned JSON

    @Test
    fun movieListParses() {
        val body = """{"data":[{"id":"m1","title":"Fuori","slug":"fuori-2025","voteAverage":7.5,"viewCount":10}],"pagination":{"page":1,"limit":20,"total":1,"totalPages":1}}"""
        val parsed = CoreHttp.json.decodeFromString(MovieListResponse.serializer(), body)
        assertEquals(1, parsed.data.size)
        assertEquals("Fuori", parsed.data[0].title)
        assertEquals("7.5", parsed.data[0].voteAverage)
        assertEquals(1, parsed.pagination?.page)
    }

    @Test
    fun seriesListParses() {
        val body = """{"data":[{"id":"s1","title":"Breaking","slug":"breaking-bad-2008","numberOfSeasons":5}],"pagination":{"page":1,"limit":20,"total":1,"totalPages":1}}"""
        val parsed = CoreHttp.json.decodeFromString(SeriesListResponse.serializer(), body)
        assertEquals("Breaking", parsed.data[0].title)
        assertEquals(5, parsed.data[0].numberOfSeasons)
    }

    @Test
    fun movieDetailParses() {
        val body = """{"id":"m1","title":"Fuori","slug":"fuori-2025","overview":"desc","genres":[{"id":"g1","name":"Action","slug":"action"}]}"""
        val parsed = CoreHttp.json.decodeFromString(MovieDetail.serializer(), body)
        assertEquals("desc", parsed.overview)
        assertEquals("Action", parsed.genres[0].name)
    }

    @Test
    fun seriesDetailParsesWithSeasons() {
        val body = """{"id":"s1","title":"Show","slug":"show","numberOfSeasons":2,"seasons":[{"id":"ss1","seasonNumber":1,"name":"Season 1"}],"defaultSeason":{"id":"ss1","seasonNumber":1,"name":"Season 1","episodes":[{"id":"e1","episodeNumber":1,"name":"Pilot"}]}}"""
        val parsed = CoreHttp.json.decodeFromString(SeriesDetail.serializer(), body)
        assertEquals(1, parsed.seasons.size)
        assertEquals("Pilot", parsed.defaultSeason?.episodes?.get(0)?.name)
    }

    @Test
    fun episodeDetailParses() {
        val body = """{"series":{"id":"s1","title":"Show","slug":"show"},"season":{"id":"ss1","seasonNumber":1,"name":"S1","episodes":[{"id":"e1","episodeNumber":1,"name":"Pilot"}]},"episode":{"id":"e1","episodeNumber":1,"name":"Pilot"},"allSeasons":[{"id":"ss1","seasonNumber":1,"name":"S1"}]}"""
        val parsed = CoreHttp.json.decodeFromString(EpisodeResponse.serializer(), body)
        assertEquals("Pilot", parsed.episode?.name)
        assertEquals(1, parsed.season?.episodes?.size)
    }

    @Test
    fun searchResponseParses() {
        val body = """{"results":[{"id":"m1","contentType":"movie","title":"Batman","slug":"batman"}],"total":1,"order":"relevance"}"""
        val parsed = CoreHttp.json.decodeFromString(SearchResponse.serializer(), body)
        assertEquals(1, parsed.results.size)
        assertEquals("Batman", parsed.results[0].title)
    }

    @Test
    fun genreListParses() {
        val body = """{"data":[{"id":"g1","name":"Action","slug":"action"}]}"""
        val parsed = CoreHttp.json.decodeFromString(GenreListResponse.serializer(), body)
        assertEquals("action", parsed.data[0].slug)
    }

    @Test
    fun playbackPayloadsParse() {
        val playInfo = CoreHttp.json.decodeFromString(
            PlayInfoResponse.serializer(),
            """{"kind":"gate","gateToken":"abc.def","serverNow":1000,"unlockAt":9000}"""
        )
        assertEquals("abc.def", playInfo.gateToken)

        val claim = CoreHttp.json.decodeFromString(
            ClaimResponse.serializer(),
            """{"kind":"pentos","claim":"CLAIM","redeemUrl":"","title":"T","durationSec":90,"videoId":"v1"}"""
        )
        assertEquals("pentos", claim.kind)
        assertEquals(90L, claim.durationSec)

        val redeem = CoreHttp.json.decodeFromString(
            RedeemResponse.serializer(),
            """{"code":"ok","url":"https://cdn/x.m3u8","subtitles":[{"lang":"id","label":"Indonesian","path":"https://cdn/id.vtt"}]}"""
        )
        assertEquals("https://cdn/x.m3u8", redeem.url)
        assertEquals("id", redeem.subtitles[0].lang)
    }

    // ---------------------------------------------------------- cache TTL

    @Test
    fun cacheTtlConstants() {
        assertEquals(15L * 60L * 1_000L, LIST_TTL_MS)
        assertEquals(30L * 60L * 1_000L, DETAIL_TTL_MS)
        assertEquals(2L * 60L * 60L * 1_000L, GENRE_TTL_MS)
        assertEquals(10L * 60L * 1_000L, SEARCH_TTL_MS)
    }

    @Test
    fun cacheServesFreshAndExpiresStale() {
        var now = 1_000_000L
        val cache = DirectSourceCache(clock = { now })
        cache.put("k", "v", 60_000L)
        assertEquals("v", cache.get("k", String::class.java))
        now += 60_001L
        assertNull(cache.get("k", String::class.java))
    }

    @Test
    fun cacheRejectsTypeMismatchAndClears() {
        val cache = DirectSourceCache()
        cache.put("k", "v", 60_000L)
        assertNull(cache.get("k", Int::class.java))
        assertEquals("v", cache.get("k", String::class.java))
        cache.clear()
        assertNull(cache.get("k", String::class.java))
        assertEquals(0, cache.size())
    }
    // ---------------------------------------------------------- repository

    private class FakeApi(
        var movies: MovieListResponse = MovieListResponse(),
        var series: SeriesListResponse = SeriesListResponse()
    ) : IdlixDirectApi() {
        var movieCalls = 0
        var seriesCalls = 0
        var failWith: IOExceptionFactory? = null

        override suspend fun getMovies(page: Int, limit: Int, sort: String?, genre: String?): MovieListResponse {
            movieCalls++
            failWith?.let { throw it() }
            return moviesFor(sort)
        }

        private fun moviesFor(sort: String?): MovieListResponse {
            // Simulate: only "popular" sort (and unfiltered) has data.
            return if (sort == null || sort == "popular") movies else MovieListResponse()
        }

        override suspend fun getSeries(page: Int, limit: Int, sort: String?, genre: String?): SeriesListResponse {
            seriesCalls++
            failWith?.let { throw it() }
            return series
        }
    }

    private fun interface IOExceptionFactory {
        operator fun invoke(): java.io.IOException
    }

    @Test
    fun searchBlankShortCircuitsWithoutNetwork() = runBlocking {
        val fake = FakeApi()
        val repo = IdlixRepository(api = fake)
        assertTrue(repo.search("").isEmpty())
        assertTrue(repo.search("   ").isEmpty())
        assertEquals(0, fake.movieCalls)
        assertEquals(0, fake.seriesCalls)
    }

    @Test
    fun trendingFallsBackThroughSorts() = runBlocking {
        val trending = com.idlix.data.model.MovieItem(id = "t1", title = "Trend", slug = "trend")
        val fake = FakeApi(movies = MovieListResponse(data = listOf(trending)))
        val repo = IdlixRepository(api = fake)
        // "trending" sort is empty in the fake, "popular" hits.
        assertEquals(listOf(trending), repo.getTrendingMovies())
        assertTrue(fake.movieCalls >= 2)
    }

    @Test
    fun catalogReadsAreCachedByQuery() = runBlocking {
        val fake = FakeApi(movies = MovieListResponse())
        val repo = IdlixRepository(api = fake)
        repo.getMovies(page = 1, limit = 20)
        repo.getMovies(page = 1, limit = 20)
        assertEquals(1, fake.movieCalls)
        repo.getMovies(page = 2, limit = 20)
        assertEquals(2, fake.movieCalls)
    }

    @Test
    fun convenienceListsStayEmptyOnFailure() = runBlocking {
        val fake = FakeApi()
        fake.failWith = IOExceptionFactory { java.io.IOException("nope") }
        val repo = IdlixRepository(api = fake)
        assertTrue(repo.getPopularMovies().isEmpty())
        assertTrue(repo.getPopularSeries().isEmpty())
        assertTrue(repo.getMoviesByGenre("action").isEmpty())
        assertTrue(repo.getSeriesBySort("popular").isEmpty())
    }

    @Test
    fun prewarmNeverThrowsOffline() {
        val repo = IdlixRepository(api = FakeApi())
        repo.prewarmStream("movie", "some-id", "Some Title")
        repo.prewarmStream("episode", "other-id", "Other Title")
    }
}
