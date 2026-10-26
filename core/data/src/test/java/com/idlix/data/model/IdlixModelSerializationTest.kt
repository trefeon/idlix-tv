package com.idlix.data.model

import com.idlix.data.net.CoreHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline-only serialization tests: unknown-key tolerance plus the lenient
 * [FlexibleStringSerializer] paths (number/string/null/missing).
 */
class IdlixModelSerializationTest {

    @Test
    fun movieVoteAverageAcceptsNumber() {
        val parsed = CoreHttp.json.decodeFromString<MovieItem>(
            """{"id":"1","title":"Fuori","voteAverage":6.40}"""
        )
        assertEquals("6.4", parsed.formattedRating)
    }

    @Test
    fun movieVoteAverageAcceptsString() {
        val parsed = CoreHttp.json.decodeFromString<MovieItem>(
            """{"id":"1","title":"Fuori","voteAverage":"7.66"}"""
        )
        assertEquals("7.7", parsed.formattedRating)
    }

    @Test
    fun movieVoteAverageAcceptsNullAndMissing() {
        val fromNull = CoreHttp.json.decodeFromString<MovieItem>(
            """{"id":"1","title":"Fuori","voteAverage":null}"""
        )
        assertNull(fromNull.voteAverage)
        assertEquals("N/A", fromNull.formattedRating)

        val missing = CoreHttp.json.decodeFromString<MovieItem>(
            """{"id":"1","title":"Fuori"}"""
        )
        assertNull(missing.voteAverage)
        assertEquals("N/A", missing.formattedRating)
    }

    @Test
    fun movieIgnoresUnknownKeysAndKeepsDefaults() {
        val parsed = CoreHttp.json.decodeFromString<MovieListResponse>(
            """{"data":[{"id":"1","title":"Fuori","popularityScore":978}],"pagination":null,"extra":true}"""
        )
        assertEquals(1, parsed.data.size)
        assertEquals("Fuori", parsed.data[0].title)
        assertNull(parsed.pagination)
    }

    @Test
    fun moviePosterHelpers() {
        val withPaths = MovieItem(id = "1", title = "T", posterPath = "/p.jpg", backdropPath = "/b.jpg")
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", withPaths.fullPosterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", withPaths.fullBackdropUrl)

        val withoutPaths = MovieItem(id = "1", title = "T")
        assertEquals("", withoutPaths.fullPosterUrl)
        assertEquals("", withoutPaths.fullBackdropUrl)
    }

    @Test
    fun seriesItemDeserializesAndConverts() {
        val parsed = CoreHttp.json.decodeFromString<SeriesItem>(
            """{"id":"s1","title":"BB","slug":"bb","firstAirDate":"2008-01-20","voteAverage":9.5,"viewCount":10}"""
        )
        assertEquals("BB", parsed.title)
        assertEquals("9.5", parsed.formattedRating)
        val movie = parsed.toMovieItem()
        assertEquals("s1", movie.id)
        assertEquals("tv_series", movie.contentType)
        assertEquals("2008-01-20", movie.releaseDate)
    }

    @Test
    fun episodeDeserializesLenientRating() {
        val parsed = CoreHttp.json.decodeFromString<EpisodeItem>(
            """{"id":"e1","episodeNumber":2,"name":"Pilot","voteAverage":8,"hasVideo":true}"""
        )
        assertEquals(2, parsed.episodeNumber)
        assertEquals("Pilot", parsed.name)
    }

    @Test
    fun searchOrderAcceptsStringAndNumber() {
        val fromString = CoreHttp.json.decodeFromString<SearchResponse>(
            """{"results":[],"total":0,"order":"popular"}"""
        )
        assertEquals("popular", fromString.order)

        val fromNumber = CoreHttp.json.decodeFromString<SearchResponse>(
            """{"results":[],"total":0,"order":3}"""
        )
        assertEquals("3", fromNumber.order)
    }

    @Test
    fun searchResultFallsBackToRawVoteAverage() {
        val parsed = CoreHttp.json.decodeFromString<SearchResultItem>(
            """{"id":"1","title":"T","voteAverage":"abc"}"""
        )
        assertEquals("abc", parsed.formattedRating)

        val missing = CoreHttp.json.decodeFromString<SearchResultItem>(
            """{"id":"1","title":"T"}"""
        )
        assertEquals("N/A", missing.formattedRating)
    }

    @Test
    fun subdlResponseDeserializes() {
        val parsed = CoreHttp.json.decodeFromString<SubdlResponse>(
            """{"status":true,"subtitles":[{"release_name":"Fuori WEB-DL","lang":"Indonesian","url":"/dl/abc.zip"}]}"""
        )
        assertTrue(parsed.status)
        assertEquals(1, parsed.subtitles.size)
        assertEquals(
            "https://dl.subdl.com/dl/abc.zip",
            parsed.subtitles[0].fullDownloadUrl
        )
    }

    @Test
    fun subdlAbsoluteUrlPassesThrough() {
        val sub = SubdlSubtitle(url = "https://cdn.example.com/a.srt")
        assertEquals("https://cdn.example.com/a.srt", sub.fullDownloadUrl)
    }

    @Test
    fun playbackModelsCarrySaneDefaults() {
        val playback = ResolvedPlayback()
        assertEquals("", playback.hlsMasterUrl)
        assertTrue(playback.subtitles.isEmpty())

        val track = SubtitleTrack()
        assertEquals("id", track.lang)
        assertEquals("Indonesian", track.label)
    }

    @Test
    fun watchHistoryProgressMath() {
        val half = WatchHistoryItem(contentId = "1", title = "T", positionMs = 30_000L, durationMs = 60_000L)
        assertEquals(0.5f, half.progressFraction, 0.0001f)
        assertEquals(50, half.progressPercent)
        assertTrue(half.isUnfinished)

        val zeroDuration = WatchHistoryItem(contentId = "1", title = "T", positionMs = 0L, durationMs = 0L)
        assertEquals(0f, zeroDuration.progressFraction, 0.0001f)

        val over = WatchHistoryItem(contentId = "1", title = "T", positionMs = 90_000L, durationMs = 60_000L)
        assertEquals(1f, over.progressFraction, 0.0001f)
    }

    @Test
    fun watchHistoryUnfinishedBoundaries() {
        // Under 10s is not "started".
        assertTrue(
            !WatchHistoryItem(contentId = "1", title = "T", positionMs = 9_999L, durationMs = 0L).isUnfinished
        )
        // 10s with unknown duration counts as unfinished.
        assertTrue(
            WatchHistoryItem(contentId = "1", title = "T", positionMs = 10_000L, durationMs = 0L).isUnfinished
        )
        // Past 95% counts as finished.
        assertTrue(
            !WatchHistoryItem(contentId = "1", title = "T", positionMs = 96_000L, durationMs = 100_000L).isUnfinished
        )
    }

    @Test
    fun watchHistoryThumbnailResolution() {
        val absolute = WatchHistoryItem(contentId = "1", title = "T", backdropUrl = "https://cdn.example.com/b.jpg")
        assertEquals("https://cdn.example.com/b.jpg", absolute.resolvedThumbnail)

        val relativeBackdrop = WatchHistoryItem(contentId = "1", title = "T", backdropUrl = "/b.jpg")
        assertEquals("https://image.tmdb.org/t/p/w780/b.jpg", relativeBackdrop.resolvedThumbnail)

        val posterFallback = WatchHistoryItem(contentId = "1", title = "T", posterUrl = "/p.jpg")
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", posterFallback.resolvedThumbnail)

        val empty = WatchHistoryItem(contentId = "1", title = "T")
        assertEquals("", empty.resolvedThumbnail)
    }

    @Test
    fun paginationDefaults() {
        val pagination = Pagination()
        assertEquals(1, pagination.page)
        assertEquals(20, pagination.limit)
    }
}
