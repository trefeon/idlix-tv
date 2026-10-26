package com.idlix.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The direct source is not wired yet: every read path must stay empty and
 * [IdlixRepository.resolveStream] must fail closed. Fully offline.
 */
class IdlixRepositoryStubTest {

    private val repository = IdlixRepository()

    @Test
    fun moviesReadEmpty() = runBlocking {
        val response = repository.getMovies()
        assertTrue(response.data.isEmpty())
        assertEquals(1, response.pagination?.page ?: 1)
    }

    @Test
    fun seriesReadEmpty() = runBlocking {
        val response = repository.getSeries()
        assertTrue(response.data.isEmpty())
    }

    @Test
    fun trendingMoviesEmpty() = runBlocking {
        assertTrue(repository.getTrendingMovies().isEmpty())
    }

    @Test
    fun popularMoviesEmpty() = runBlocking {
        assertTrue(repository.getPopularMovies().isEmpty())
    }

    @Test
    fun popularSeriesEmpty() = runBlocking {
        assertTrue(repository.getPopularSeries().isEmpty())
    }

    @Test
    fun sortAndGenreReadsEmpty() = runBlocking {
        assertTrue(repository.getMoviesBySort("popular").isEmpty())
        assertTrue(repository.getMoviesByGenre("action").isEmpty())
        assertTrue(repository.getSeriesBySort("popular").isEmpty())
        assertTrue(repository.getSeriesByGenre("drama").isEmpty())
    }

    @Test
    fun movieDetailNull() = runBlocking {
        assertNull(repository.getMovieDetail("fuori-2025"))
    }

    @Test
    fun seriesDetailNull() = runBlocking {
        assertNull(repository.getSeriesDetail("breaking-bad-2008"))
    }

    @Test
    fun episodeDetailNull() = runBlocking {
        assertNull(repository.getEpisodeDetail("breaking-bad-2008", 1, 1))
    }

    @Test
    fun searchEmpty() = runBlocking {
        assertTrue(repository.search("batman").isEmpty())
        assertTrue(repository.search("").isEmpty())
    }

    @Test
    fun resolveStreamFailsClosed() = runBlocking {
        val result = repository.resolveStream("movie", "some-id", "Some Title")
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("Direct source"))
    }

    @Test
    fun prewarmStreamNeverThrows() {
        repository.prewarmStream("movie", "some-id", "Some Title")
        repository.prewarmStream("episode", "other-id", "Other Title")
    }
}
