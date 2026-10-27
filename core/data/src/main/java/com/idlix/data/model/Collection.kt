package com.idlix.data.model

import kotlinx.serialization.Serializable

/**
 * Curated collections (franchises/universes/phases), e.g. Marvel Cinematic
 * Universe. Shapes mirror the verified `GET api/collections` list payload
 * (`{collections:[...]}` with id/slug/title/kind/itemCount/dates) and the
 * verified `GET api/collections/{slug}` detail payload
 * (`{collection, sections[]}` with per-title entries). Every field carries a
 * default so shape drift degrades to empty UI, never a crash
 * (`CoreHttp.json` also ignores unknown keys).
 */
@Serializable
data class CollectionListResponse(
    val collections: List<CollectionItem> = emptyList()
)

@Serializable
data class CollectionItem(
    val id: String = "",
    val translationKey: String? = null,
    val slug: String = "",
    val title: String = "",
    val description: String? = null,
    val kind: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val logoPath: String? = null,
    val itemCount: Int = 0,
    val childCount: Int = 0,
    val firstReleaseDate: String? = null,
    val lastReleaseDate: String? = null,
    val parentId: String? = null,
    val orderMode: String? = null,
    val seoTitle: String? = null,
    val seoDescription: String? = null
) {
    val fullPosterUrl: String
        get() = if (posterPath != null) "https://image.tmdb.org/t/p/w342$posterPath" else ""

    val fullBackdropUrl: String
        get() = if (backdropPath != null) "https://image.tmdb.org/t/p/w1280$backdropPath" else ""
}

@Serializable
data class CollectionDetailResponse(
    val collection: CollectionItem = CollectionItem(),
    val sections: List<CollectionSection> = emptyList(),
    val canonicalSlug: String? = null,
    val isCanonical: Boolean = true,
    val sectionAnchor: String? = null
)

@Serializable
data class CollectionSection(
    val id: String = "",
    val translationKey: String? = null,
    val slug: String = "",
    val title: String = "",
    val description: String? = null,
    val kind: String = "",
    val orderMode: String? = null,
    val itemCount: Int = 0,
    val items: List<CollectionTitle> = emptyList()
)

@Serializable
data class CollectionTitle(
    val id: String = "",
    val title: String = "",
    val itemLabel: String? = null,
    val slug: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val voteAverage: String? = null,
    val viewCount: Long = 0,
    val popularityScore: Long = 0,
    val quality: String? = null,
    val country: String? = null,
    val runtime: Int? = null,
    val numberOfSeasons: Int? = null,
    val contentType: String = "movie",
    val collectionOrder: Int = 0,
    val commentCount: Int = 0
) {
    val fullPosterUrl: String
        get() = if (posterPath != null) "https://image.tmdb.org/t/p/w342$posterPath" else ""

    val fullBackdropUrl: String
        get() = if (backdropPath != null) "https://image.tmdb.org/t/p/w1280$backdropPath" else ""

    /** Detail rails render [com.idlix.data.model.MovieItem]; series pass through as `tv_series`. */
    fun toMovieItem(): MovieItem = MovieItem(
        id = id,
        title = title,
        slug = slug,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate ?: firstAirDate,
        voteAverage = voteAverage,
        viewCount = viewCount,
        quality = quality,
        country = country,
        runtime = runtime,
        contentType = if (contentType == "tv_series" || contentType == "series") "tv_series" else "movie"
    )
}
