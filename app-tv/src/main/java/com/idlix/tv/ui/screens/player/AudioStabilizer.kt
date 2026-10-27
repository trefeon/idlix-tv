package com.idlix.tv.ui.screens.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Session-bound loudness normalizer ("Audio stabilizer").
 *
 * Mechanism: Android [DynamicsProcessing] (API 28+) as a session-bound INSERT
 * effect. The effect is constructed with the real audio session id observed
 * via [Player.Listener.onAudioSessionIdChanged] — never session 0, never
 * `setAuxEffectInfo` — then `setEnabled(true)`, retained alongside the player,
 * `release()`d in player cleanup, and recreated whenever the session changes.
 *
 * Chain (starting points for ear-tuning):
 * - inputGain makeup +6 dB on all channels;
 * - one full-band MBC: threshold −20 dBFS, ratio 2–3:1 (starting point 2.5:1),
 *   attack 20 ms, release 200 ms, knee 6 dB, noise gate off
 *   (1:1 expander + −90 dBFS floor);
 * - flat EQs (pre/post stages bypassed);
 * - limiter ceiling −1.0 dBFS, ratio 20:1 (floor of the ≥20:1 spec),
 *   attack 2 ms, release 75 ms, postGain 0 dB, shared linkGroup 0.
 *
 * Vendor-config fallback: if the tuned [DynamicsProcessing.Config] is rejected
 * ([IllegalArgumentException] / [UnsupportedOperationException]), falls back
 * to the no-arg default config `DynamicsProcessing(sessionId)`. API < 28 is a
 * no-op with a debug log. Enable/disable is idempotent and keyed to the
 * persisted toggle ([loadAudioStabilizer]).
 *
 * Lifecycle: [attach] registers [listener] and binds the current session;
 * [setEnabled] flips the toggle live (recreating the effect when turning on);
 * [detach] removes the listener and releases the effect. Call [detach] before
 * `ExoPlayer.release()`.
 *
 * Touches nothing else: no track selection, no AudioProcessor, no pre-amp, no
 * offload flags, no reflection.
 */
class AudioStabilizer {

    val listener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            sessionId = audioSessionId
            if (!enabled) return
            attachToSession(audioSessionId)
        }
    }

    var enabled: Boolean = false
        private set

    private var effect: DynamicsProcessing? = null
    private var sessionId: Int = SESSION_UNSET
    private var boundSessionId: Int = SESSION_UNSET

    /**
     * Registers the session listener on [player] and binds the current audio
     * session when [enabled]. Safe to call once per player instance; pair with
     * [detach].
     */
    fun attach(player: ExoPlayer, enabled: Boolean) {
        this.enabled = enabled
        try {
            player.addListener(listener)
        } catch (_: Exception) {
        }
        if (!enabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(TAG, "Audio stabilizer needs API 28+ (this=${Build.VERSION.SDK_INT}); no-op")
            return
        }
        attachToSession(player.audioSessionId)
    }

    /** Idempotent toggle flip; recreates the effect when turning back on. */
    fun setEnabled(enabled: Boolean) {
        if (enabled == this.enabled) return
        this.enabled = enabled
        if (!enabled) {
            releaseEffect()
            return
        }
        attachToSession(sessionId)
    }

    /** Removes the session listener and releases the effect. Idempotent. */
    fun detach(player: ExoPlayer) {
        try {
            player.removeListener(listener)
        } catch (_: Exception) {
        }
        releaseEffect()
        sessionId = SESSION_UNSET
    }

    private fun attachToSession(id: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        // Never session 0 (or unset): an INSERT effect needs a real session.
        if (id <= 0) return
        if (effect != null && id == boundSessionId) return
        releaseEffect()
        val config = try {
            buildConfig()
        } catch (_: Exception) {
            null
        }
        val created: DynamicsProcessing? = try {
            if (config != null) DynamicsProcessing(0, id, config)
            else DynamicsProcessing(id)
        } catch (_: IllegalArgumentException) {
            // Vendor rejected the tuned config: default-config fallback.
            try {
                DynamicsProcessing(id)
            } catch (_: Exception) {
                null
            }
        } catch (_: UnsupportedOperationException) {
            try {
                DynamicsProcessing(id)
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
        if (created == null) {
            Log.d(TAG, "Audio stabilizer unavailable on this device; no-op")
            return
        }
        try {
            created.setEnabled(true)
        } catch (_: Exception) {
            try {
                created.release()
            } catch (_: Exception) {
            }
            return
        }
        effect = created
        boundSessionId = id
    }

    private fun buildConfig(): DynamicsProcessing.Config {
        // Single full-band compressor (20 kHz top = whole spectrum in one band).
        val mbc = DynamicsProcessing.Mbc(true, true, 1)
        mbc.setBand(
            0,
            DynamicsProcessing.MbcBand(
                true, // enabled
                20000f, // cutoffFrequencyHz (full-band top)
                20f, // attackTimeMs
                200f, // releaseTimeMs
                2.5f, // ratio (spec 2–3:1, midpoint starting point)
                -20f, // thresholdDbFS
                6f, // kneeWidthDb
                -90f, // noiseGateThresholdDbFS (gate off: floor …)
                1f, // … + 1:1 expander = no expansion)
                0f, // preGainDb
                0f, // postGainDb
            ),
        )
        val limiter = DynamicsProcessing.Limiter(
            true, // inUse
            true, // enabled
            0, // linkGroup (shared across channels)
            2f, // attackTimeMs
            75f, // releaseTimeMs
            20f, // ratio (spec ≥20:1)
            -1f, // thresholdDbFS (ceiling −1.0 dBFS)
            0f, // postGainDb
        )
        return DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
            2, // channelCount (stereo engine)
            false, // preEqInUse (flat: bypassed)
            0, // preEqBandCount
            true, // mbcInUse
            1, // mbcBandCount (one full-band compressor)
            false, // postEqInUse (flat: bypassed)
            0, // postEqBandCount
            true, // limiterInUse
        )
            .setInputGainAllChannelsTo(6f) // makeup +6 dB
            .setMbcAllChannelsTo(mbc)
            .setLimiterAllChannelsTo(limiter)
            .build()
    }

    private fun releaseEffect() {
        val old = effect
        effect = null
        boundSessionId = SESSION_UNSET
        if (old != null) {
            try {
                old.setEnabled(false)
            } catch (_: Exception) {
            }
            try {
                old.release()
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val TAG = "AudioStabilizer"
        const val SESSION_UNSET = -1
    }
}
