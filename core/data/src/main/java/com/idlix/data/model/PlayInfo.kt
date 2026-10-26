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
 * Produced by the direct-source resolver in `com.idlix.data.api`.
 */
@Serializable
data class ResolvedPlayback(
    val title: String = "",
    val hlsMasterUrl: String = "",
    val subtitles: List<SubtitleTrack> = emptyList(),
    val durationSec: Long = 0L,
    val videoId: String = ""
)

/**
 * Gate payload for one title: the unlock token plus the server clock pair
 * the countdown synchronizes against. Matches upstream 1:1.
 */
@Serializable
data class PlayInfoResponse(
    val kind: String = "",
    val gateToken: String = "",
    val serverNow: Long = 0L,
    val unlockAt: Long = 0L,
    val viewerTier: String = "guest",
    val maxHeight: Int = 720
)

@Serializable
data class ClaimRequest(
    val gateToken: String
)

@Serializable
data class ClaimResponse(
    val kind: String = "",
    val claim: String = "",
    val claimExpiresAt: Long = 0L,
    val redeemUrl: String = "",
    val videoId: String = "",
    val title: String = "",
    val durationSec: Long = 0L,
    val viewerTier: String = "guest",
    val maxHeight: Int = 720,
    val qualityLadder: List<Int> = emptyList()
)

@Serializable
data class RedeemRequest(
    val claim: String,
    val mode: String = "browser"
)

@Serializable
data class RedeemResponse(
    val code: String = "",
    val url: String = "",
    val expiresAt: Long = 0L,
    val mode: String = "browser",
    val ttlSeconds: Long = 3600L,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val videoId: String = ""
)
