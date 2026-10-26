package com.idlix.data.model

import kotlinx.serialization.Serializable

/**
 * Player-consumable subtitle track (UI model, decoupled from any backend).
 */
@Serializable
data class SubtitleTrack(
    val lang: String = "id",
    val label: String = "Indonesian",
    val path: String = ""
)

/**
 * Resolved stream ready for ExoPlayer consumption (UI model).
 *
 * Produced by the direct-source resolver (TODO(new-source)); no backend DTOs.
 */
@Serializable
data class ResolvedPlayback(
    val title: String = "",
    val hlsMasterUrl: String = "",
    val subtitles: List<SubtitleTrack> = emptyList(),
    val durationSec: Long = 0L,
    val videoId: String = ""
)
