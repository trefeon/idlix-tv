package com.idlix.data.repository

import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieItem
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.SearchResultItem
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SeriesItem
import com.idlix.data.model.SeriesListResponse

/**
 * Compile-safe stub behind the future direct-source repository interface.
 *
 * TODO(new-source): wire to the official-site direct source + IdlixDomainResolver.
 * All read methods return empty/null; [resolveStream] fails closed until then.
 * Signatures are intentionally unchanged so app-tv/app-mobile keep compiling.
 */
class IdlixRepository {

    suspend fun getMovies(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        genre: String? = null
    ): MovieListResponse = MovieListResponse()

    suspend fun getSeries(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        genre: String? = null
    ): SeriesListResponse = SeriesListResponse()

    suspend fun getTrendingMovies(): List<MovieItem> = emptyList()

    suspend fun getPopularMovies(): List<MovieItem> = emptyList()

    suspend fun getMoviesBySort(sort: String): List<MovieItem> = emptyList()

    suspend fun getMoviesByGenre(genreSlug: String): List<MovieItem> = emptyList()

    suspend fun getPopularSeries(): List<SeriesItem> = emptyList()

    suspend fun getSeriesBySort(sort: String): List<SeriesItem> = emptyList()

    suspend fun getSeriesByGenre(genreSlug: String): List<SeriesItem> = emptyList()

    suspend fun getMovieDetail(slug: String): MovieDetail? = null

    suspend fun getSeriesDetail(slug: String): SeriesDetail? = null

    suspend fun getEpisodeDetail(slug: String, season: Int, episode: Int): EpisodeResponse? = null

    suspend fun search(query: String): List<SearchResultItem> = emptyList()

    /**
     * Pre-warms the stream resolution. No-op until the direct source lands.
     */
    fun prewarmStream(contentType: String, contentId: String, contentTitle: String) {
        // TODO(new-source): prewarm via direct-source resolver.
    }

    suspend fun resolveStream(
        contentType: String,
        contentId: String,
        contentTitle: String,
        onCountdown: ((Int) -> Unit)? = null
    ): Result<ResolvedPlayback> =
        Result.failure(IllegalStateException("Direct source not yet wired (TODO(new-source))"))
}
