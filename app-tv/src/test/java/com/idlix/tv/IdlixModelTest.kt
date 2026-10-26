package com.idlix.tv

import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.SearchResponse
import com.idlix.data.model.SeriesItem
import com.idlix.data.net.CoreHttp
import com.idlix.data.net.IdlixDomainResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdlixModelTest {

    @Test
    fun testMovieListDeserialization() {
        val sampleJson = """
        {
          "data": [
            {
              "id": "a22c5860-fe44-4a14-8ab4-61a23d26ab06",
              "title": "Fuori",
              "slug": "fuori-2025",
              "posterPath": "/gxSkBOfTEOX2f6KhqMFSXf24nMo.jpg",
              "backdropPath": "/k9Oe0KXL1qYnEddxr1WR4VkB8eW.jpg",
              "releaseDate": "2025-05-22",
              "voteAverage": 6.40,
              "viewCount": 259,
              "quality": "WEB-DL",
              "country": "FR",
              "runtime": 117
            }
          ],
          "pagination": {
            "page": 1,
            "limit": 20,
            "total": 100,
            "totalPages": 5
          }
        }
        """.trimIndent()

        val parsed = CoreHttp.json.decodeFromString<MovieListResponse>(sampleJson)
        assertEquals(1, parsed.data.size)
        val movie = parsed.data[0]
        assertEquals("Fuori", movie.title)
        assertEquals("https://image.tmdb.org/t/p/w342/gxSkBOfTEOX2f6KhqMFSXf24nMo.jpg", movie.fullPosterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/k9Oe0KXL1qYnEddxr1WR4VkB8eW.jpg", movie.fullBackdropUrl)
        assertEquals("6.4", movie.formattedRating)
    }

    @Test
    fun testSearchDeserializationWithRealData() {
        val searchJson = """
        {
          "results": [
            {
              "id": "5f561446-98c4-4e26-908c-301dc8113384",
              "contentType": "movie",
              "title": "The Batman",
              "originalTitle": "The Batman",
              "overview": "In his second year of fighting crime...",
              "genres": [
                "Crime",
                "Mystery",
                "Thriller"
              ],
              "releaseDate": "2022-03-01",
              "originalLanguage": "en",
              "voteAverage": 7.66,
              "viewCount": 32089,
              "popularityScore": 978,
              "posterPath": "/74xTEgt7R36Fpooo50r9T25onhq.jpg",
              "backdropPath": "/rvtdN5XkWAfGX6xDuPL6yYS2seK.jpg",
              "quality": "WEB-DL",
              "slug": "the-batman-2022",
              "commentCount": 25
            }
          ],
          "total": 108,
          "order": "popular"
        }
        """.trimIndent()

        val searchResponse = CoreHttp.json.decodeFromString<SearchResponse>(searchJson)
        assertEquals(1, searchResponse.results.size)
        val item = searchResponse.results[0]
        assertEquals("The Batman", item.title)
        assertEquals("the-batman-2022", item.slug)
        assertEquals("7.7", item.formattedRating)
        assertEquals(3, item.genres.size)
        assertEquals("Crime", item.genres[0])
    }

    @Test
    fun testResolvedPlaybackDeserialization() {
        val sampleJson = """
        {
          "title": "Hope (2026)",
          "hlsMasterUrl": "https://cdn.example.com/v/z4/master.m3u8",
          "subtitles": [
            {
              "lang": "id",
              "label": "Indonesian",
              "path": "https://cdn.example.com/v/z4/sub_id.vtt"
            }
          ],
          "durationSec": 7200,
          "videoId": "v-12345"
        }
        """.trimIndent()

        val parsed = CoreHttp.json.decodeFromString<ResolvedPlayback>(sampleJson)
        assertNotNull(parsed)
        assertEquals("Hope (2026)", parsed.title)
        assertEquals("https://cdn.example.com/v/z4/master.m3u8", parsed.hlsMasterUrl)
        assertEquals(1, parsed.subtitles.size)
        assertEquals("id", parsed.subtitles[0].lang)
        assertEquals(7200, parsed.durationSec)
    }

    @Test
    fun testResolverDrivenRefererAndOrigin() {
        // Consumers must derive Referer/Origin from the resolver at runtime —
        // never hardcode a mirror literal. Offline: uses the default flow value.
        val origin = IdlixDomainResolver.origin()
        val referer = IdlixDomainResolver.referer()
        assertTrue(origin.startsWith("https://"))
        assertTrue(!origin.endsWith("/"))
        assertTrue(referer.startsWith("https://"))
        assertTrue(referer.endsWith("/"))
        assertEquals("$origin/", referer)
    }

    @Test
    fun testResolverFallbackBearsIdlixHost() {
        val base = IdlixDomainResolver.currentBaseUrl()
        assertTrue(base.startsWith("https://"))
        assertTrue(base.contains("idlix"))
    }

    @Test
    fun testSeriesItemHelpers() {
        val item = SeriesItem(id = "s1", title = "BB", posterPath = "/p.jpg", voteAverage = "9.5")
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", item.fullPosterUrl)
        assertEquals("9.5", item.formattedRating)
        assertEquals("tv_series", item.toMovieItem().contentType)
    }
}
