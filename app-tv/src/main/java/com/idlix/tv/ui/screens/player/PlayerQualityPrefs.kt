package com.idlix.tv.ui.screens.player

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.exoplayer.ExoPlayer
import com.idlix.tv.R
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
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    /** Null = no bitrate cap (reset to unlimited when applied). */
    val maxBitrate: Int?,
) {
    AUTO("auto", R.string.quality_auto_title, R.string.quality_detail_auto, 0, 0, null),
    P1080("1080p", R.string.quality_1080_title, R.string.quality_detail_1080, 1920, 1080, null),
    P720("720p", R.string.quality_720_title, R.string.quality_detail_720, 1280, 720, 3_000_000),
    P480("480p", R.string.quality_480_title, R.string.quality_detail_480, 854, 480, 1_200_000);
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

/**
 * Player-local playback-speed state (P0 speed picker).
 *
 * Same pattern as quality: persisted in `idlix_tv_prefs`, applied live via
 * `ExoPlayer.setPlaybackSpeed` (no rebuild, no rebuffer), default 1x.
 */
enum class TvPlaybackSpeed(
    val prefsValue: String,
    val label: String,
    val factor: Float,
) {
    P050("0.5x", "0.5×", 0.5f),
    P075("0.75x", "0.75×", 0.75f),
    P100("1x", "1×", 1.0f),
    P125("1.25x", "1.25×", 1.25f),
    P150("1.5x", "1.5×", 1.5f),
    P200("2x", "2×", 2.0f);

    companion object {
        fun fromPrefsValue(value: String?): TvPlaybackSpeed =
            entries.firstOrNull { it.prefsValue == value } ?: P100
    }
}

private const val KEY_PLAYBACK_SPEED = "playback_speed"
private const val KEY_AUDIO_LANGUAGE = "preferred_audio_language"

fun loadPlaybackSpeed(context: Context): TvPlaybackSpeed {
    return try {
        val raw = context
            .getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_PLAYBACK_SPEED, null)
        TvPlaybackSpeed.fromPrefsValue(raw)
    } catch (_: Exception) {
        TvPlaybackSpeed.P100
    }
}

fun savePlaybackSpeed(context: Context, speed: TvPlaybackSpeed) {
    try {
        context
            .getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYBACK_SPEED, speed.prefsValue)
            .apply()
    } catch (_: Exception) {
    }
}

fun applyPlaybackSpeed(player: ExoPlayer, speed: TvPlaybackSpeed) {
    try {
        player.setPlaybackSpeed(speed.factor)
    } catch (_: Exception) {
    }
}

/**
 * Preferred audio-track language (ISO-639, e.g. "id"), selected from the live
 * `Tracks` audio groups in the subtitle modal. Null = no override (default
 * track). Applied live via `setPreferredAudioLanguage`, no rebuild.
 */
fun loadAudioLanguage(context: Context): String? {
    return try {
        context
            .getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_AUDIO_LANGUAGE, null)
    } catch (_: Exception) {
        null
    }
}

fun saveAudioLanguage(context: Context, language: String?) {
    try {
        val edit = context
            .getSharedPreferences(PLAYER_PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
        if (language == null) {
            edit.remove(KEY_AUDIO_LANGUAGE)
        } else {
            edit.putString(KEY_AUDIO_LANGUAGE, language)
        }
        edit.apply()
    } catch (_: Exception) {
    }
}
