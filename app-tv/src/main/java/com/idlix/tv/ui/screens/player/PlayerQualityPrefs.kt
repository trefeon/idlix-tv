package com.idlix.tv.ui.screens.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

/**
 * Player-local playback-quality state.
 *
 * Lives in the player screen package (not in Settings) so the player stays
 * self-contained: no ViewModel / settings plumbing changes. Persisted in the
 * existing app prefs file (`idlix_tv_prefs`, the same file the Settings screen
 * uses) under a new key. Default is [AUTO].
 *
 * Caps are applied via `setMaxVideoSize` (+ `setMaxVideoBitrate` where noted).
 * Auto clears the size constraints and resets the bitrate cap to unlimited.
 * Applying a quality NEVER recreates ExoPlayer (see [applyPlaybackQuality]).
 */
enum class TvPlaybackQuality(
    val prefsValue: String,
    val title: String,
    val detail: String,
    val maxWidth: Int,
    val maxHeight: Int,
    /** Null = no bitrate cap (reset to unlimited when applied). */
    val maxBitrate: Int?,
) {
    AUTO("auto", "Auto", "Recommended · Best available", 0, 0, null),
    P1080("1080p", "1080p", "Full HD · Up to 1920×1080", 1920, 1080, null),
    P720("720p", "720p", "HD · Up to 1280×720 · ≤3 Mbps", 1280, 720, 3_000_000),
    P480("480p", "480p", "Data saver · Up to 854×480 · ≤1.2 Mbps", 854, 480, 1_200_000);

    companion object {
        fun fromPrefsValue(value: String?): TvPlaybackQuality =
            entries.firstOrNull { it.prefsValue == value } ?: AUTO

        /**
         * Rendition heights (px, from the live tracks) → quality rows worth
         * offering, Auto always first. Empty input (tracks not loaded yet)
         * returns the static full list.
         */
        fun rowsForHeights(heights: Set<Int>): List<TvPlaybackQuality> {
            if (heights.isEmpty()) return entries.toList()
            val rows = mutableListOf(AUTO)
            if (heights.any { it >= 1000 }) rows += P1080
            if (heights.any { it in 640..999 }) rows += P720
            if (heights.any { it in 1..639 }) rows += P480
            // Odd heights only (e.g. a lone 1440p/360p ladder): fall back to the
            // static list rather than showing an Auto-only modal.
            if (rows.size == 1) return entries.toList()
            return rows.toList()
        }
    }
}
private const val PLAYER_PREFS_FILE = "idlix_tv_prefs"
private const val KEY_PLAYBACK_QUALITY = "playback_quality"
private const val KEY_VIDEO_ZOOM = "video_zoom"

fun loadPlaybackQuality(context: Context): TvPlaybackQuality {
    return try {
        val raw = context
            .getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_PLAYBACK_QUALITY, null)
        TvPlaybackQuality.fromPrefsValue(raw)
    } catch (_: Exception) {
        TvPlaybackQuality.AUTO
    }
}

fun savePlaybackQuality(context: Context, quality: TvPlaybackQuality) {
    try {
        context
            .getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYBACK_QUALITY, quality.prefsValue)
            .apply()
    } catch (_: Exception) {
    }
}

/**
 * Applies [quality] to a live player WITHOUT recreating ExoPlayer: only
 * `trackSelectionParameters` change, so playback continues seamlessly and the
 * ABR selector just re-picks within the new caps on the next segment.
 *
 * NEVER uses `setForceHighestSupportedBitrate`.
 */
fun applyPlaybackQuality(player: ExoPlayer, quality: TvPlaybackQuality) {
    val next = player.trackSelectionParameters.buildUpon().apply {
        if (quality == TvPlaybackQuality.AUTO) {
            clearVideoSizeConstraints()
            setMaxVideoBitrate(Int.MAX_VALUE)
        } else {
            setMaxVideoSize(quality.maxWidth, quality.maxHeight)
            setMaxVideoBitrate(quality.maxBitrate ?: Int.MAX_VALUE)
        }
    }.build()
    player.trackSelectionParameters = next
}

/**
 * Opt-in Zoom-fill aspect. FIT (false, default) shows the entire picture
 * without cropping; Zoom (true) fills the screen, cropping the long edges.
 * Persisted in the same app prefs file; applied via PlayerView resizeMode
 * live (no player rebuild). NEVER RESIZE_MODE_FILL (would stretch).
 */
fun loadVideoZoom(context: Context): Boolean {
    return try {
        context
            .getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIDEO_ZOOM, false)
    } catch (_: Exception) {
        false
    }
}

fun saveVideoZoom(context: Context, zoom: Boolean) {
    try {
        context
            .getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIDEO_ZOOM, zoom)
            .apply()
    } catch (_: Exception) {
    }
}

/**
 * Applies the aspect live on an existing PlayerView (no ExoPlayer rebuild).
 * Zoom-aware subtitle rule lives with the caller: Zoom has no letterbox bars,
 * so the subtitle bottom padding falls back to the 0.12 default; FIT keeps
 * the letterbox-derived padding.
 */
fun applyVideoZoom(
    playerView: androidx.media3.ui.PlayerView,
    zoom: Boolean,
) {
    playerView.resizeMode =
        if (zoom) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
}
