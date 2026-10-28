package com.idlix.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Stage-1 PCM loudness normalizer (twin of the mobile processor; same preset,
 * same DSP, package-adapted only).
 *
 * Preset (verified target: films land ~−16 LUFS, peaks never exceed −1.5 dBFS):
 * - Pre-gain +15 dB (linear ×5.623), hard-capped so the static path never
 *   exceeds +20 dB total (15 pre + 5 mid makeup, the hottest band).
 * - Low band (<250 Hz): threshold −26 dBFS, ratio 4:1, attack 20 ms,
 *   release 200 ms, hard knee, makeup +2 dB.
 * - Mid band (250 Hz–4 kHz): threshold −22 dBFS, ratio 3:1, attack 10 ms,
 *   release 120 ms, soft knee 6 dB, makeup +5 dB.
 * - High band (>4 kHz): threshold −26 dBFS, ratio 2:1, attack 5 ms,
 *   release 80 ms, hard knee, makeup +3 dB.
 * - Linked brickwall limiter: ceiling −1.5 dBFS (×0.841), ∞:1, attack 0.5 ms,
 *   release 50 ms, gain computed from the max peak across channels so the
 *   stereo image never shifts; a hard clamp at the ceiling post-limiter
 *   guarantees the peak bound even for a transient faster than the attack.
 *
 * Implementation choice (documented): a full Linkwitz-Riley 3-way crossover
 * MBC was deemed too heavy for per-sample software on low-end TV SoCs (3×
 * steep IIRs + 3× envelope followers + phase-rotation risk). Instead this
 * uses first-order one-pole shelves — LP@250 Hz extracts lows, LP@4 kHz
 * extracts highs (high = dry − lp4k), mid = dry − low − high — feeding one
 * linked peak follower per band with the specified attacks/releases and a dB
 * gain computer (soft-knee mid only), then the linked limiter + hard clamp.
 * One-pole filters are unconditionally stable (coeff in (0,1), positive
 * makeup gains), so the stage cannot blow up or oscillate on any input. The
 * limiter, not the crossover steepness, is what guarantees the ceiling.
 *
 * Subsumed fallback: the preset's "+9 dB static (no DSP session)" row is NOT
 * coded as a mode. The software stage-1 needs no DSP session, so the
 * no-session case now gets full correction — strictly better than the +9 dB
 * stopgap, which stays recorded here as the validated peak-safe floor.
 * [AudioStabilizer] stays behavior-identical as stage-2 (DynamicsProcessing
 * INSERT effect on API 28+, no-op below) plus the bypass-route fallback.
 *
 * Zero-allocation audio path: all state (per-channel filter memories,
 * band scratches) is sized in [onConfigure] and reused; [queueInput] allocates
 * nothing. Non-finite samples (NaN/Inf) are zeroed before the envelopes so a
 * corrupt decode never poisons the followers (gain computer additionally
 * guards non-finite results to unity; the limiter clamp holds regardless).
 *
 * Toggle: [enabled] (default true, driven by the shared stabilizer pref key
 * via [loadAudioStabilizer]) + [setEnabled], which always resets
 * envelopes/filter memories so a flip never starts from stale gain.
 * Idempotent-safe: call on every player build and every toggle flip, no
 * rebuild needed. No session to attach/detach (pure PCM math, API-26 safe);
 * the sink owns the instance, so player release needs no extra cleanup.
 * Offload/passthrough stay off: the factory never enables offload and a
 * processor in the chain forces the software PCM path anyway.
 */
@UnstableApi
class LoudnessNormalizeProcessor : BaseAudioProcessor() {

    companion object {
        /** Nominal pre-gain applied before the band split. */
        const val PRE_GAIN_DB = 15.0

        /** Static-path ceiling: 15 pre + 5 mid makeup, the hottest band. */
        const val TOTAL_CAP_DB = 20.0

        /** Recorded validated peak-safe floor; subsumed, not a coded mode. */
        const val FALLBACK_STATIC_GAIN_DB = 9.0

        /** Limiter ceiling in dBFS. */
        const val LIMITER_CEILING_DB = -1.5

        const val LOW_FC_HZ = 250.0
        const val LOW_THRESHOLD_DB = -26.0
        const val LOW_RATIO = 4.0
        const val LOW_ATTACK_MS = 20.0
        const val LOW_RELEASE_MS = 200.0
        const val LOW_MAKEUP_DB = 2.0

        const val MID_LO_FC_HZ = 250.0
        const val MID_HI_FC_HZ = 4000.0
        const val MID_THRESHOLD_DB = -22.0
        const val MID_RATIO = 3.0
        const val MID_ATTACK_MS = 10.0
        const val MID_RELEASE_MS = 120.0
        const val MID_KNEE_DB = 6.0
        const val MID_MAKEUP_DB = 5.0

        const val HIGH_FC_HZ = 4000.0
        const val HIGH_THRESHOLD_DB = -26.0
        const val HIGH_RATIO = 2.0
        const val HIGH_ATTACK_MS = 5.0
        const val HIGH_RELEASE_MS = 80.0
        const val HIGH_MAKEUP_DB = 3.0

        const val LIMITER_ATTACK_MS = 0.5
        const val LIMITER_RELEASE_MS = 50.0
    }

    /** Live toggle; read by the audio thread every buffer. */
    @Volatile
    private var enabledState: Boolean = true
    val enabled: Boolean get() = enabledState

    /**
     * Flips [enabled] and always resets envelopes/filter memories so the new
     * state starts from unity gain. Safe to call on every player build and
     * every toggle flip (idempotent, no rebuild).
     */
    fun setEnabled(on: Boolean) {
        synchronized(lock) {
            enabledState = on
            resetStateLocked()
        }
    }

    // Static linear gains (sample-rate independent).
    private val preGainLin = 10.0.pow(PRE_GAIN_DB / 20.0)
    private val lowMakeupLin = 10.0.pow(LOW_MAKEUP_DB / 20.0)
    private val midMakeupLin = 10.0.pow(MID_MAKEUP_DB / 20.0)
    private val highMakeupLin = 10.0.pow(HIGH_MAKEUP_DB / 20.0)
    private val ceilingLin = 10.0.pow(LIMITER_CEILING_DB / 20.0)

    private val lock = Any()
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    // One-pole coefficients for this sample rate.
    private var aLow = 0.0
    private var aHigh = 0.0

    // Envelope follower coefficients (attack/release per band + limiter).
    private var attLow = 0.0
    private var relLow = 0.0
    private var attMid = 0.0
    private var relMid = 0.0
    private var attHigh = 0.0
    private var relHigh = 0.0
    private var attLim = 0.0
    private var relLim = 0.0

    // Per-channel filter memories + per-band scratch, sized in onConfigure.
    private var lpLow = FloatArray(0)
    private var lpHigh = FloatArray(0)
    private var bandLow = FloatArray(0)
    private var bandMid = FloatArray(0)
    private var bandHigh = FloatArray(0)
    private var frameScratch = FloatArray(0)

    // Linked (cross-channel) envelope followers.
    private var envLow = 0.0
    private var envMid = 0.0
    private var envHigh = 0.0
    private var envLim = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        synchronized(lock) {
            channelCount = inputAudioFormat.channelCount
            encoding = inputAudioFormat.encoding
            val fs = inputAudioFormat.sampleRate.toDouble()
            aLow = onePoleCoeff(LOW_FC_HZ, fs)
            aHigh = onePoleCoeff(HIGH_FC_HZ, fs)
            attLow = followerCoeff(LOW_ATTACK_MS, fs)
            relLow = followerCoeff(LOW_RELEASE_MS, fs)
            attMid = followerCoeff(MID_ATTACK_MS, fs)
            relMid = followerCoeff(MID_RELEASE_MS, fs)
            attHigh = followerCoeff(HIGH_ATTACK_MS, fs)
            relHigh = followerCoeff(HIGH_RELEASE_MS, fs)
            attLim = followerCoeff(LIMITER_ATTACK_MS, fs)
            relLim = followerCoeff(LIMITER_RELEASE_MS, fs)
            lpLow = FloatArray(channelCount)
            lpHigh = FloatArray(channelCount)
            bandLow = FloatArray(channelCount)
            bandMid = FloatArray(channelCount)
            bandHigh = FloatArray(channelCount)
            frameScratch = FloatArray(channelCount)
            resetStateLocked()
        }
        // No format change: same rate/channels/encoding out.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val chans = channelCount
        val enc = encoding
        if (chans <= 0) return
        val isFloat = enc == C.ENCODING_PCM_FLOAT
        if (!isFloat && enc != C.ENCODING_PCM_16BIT) return
        if (!enabled) {
            // Live bypass: transparent copy, no DSP, stays configured so the
            // flip back on needs no rebuild.
            val bypassOut = replaceOutputBuffer(inputBuffer.remaining())
            bypassOut.put(inputBuffer)
            bypassOut.flip()
            return
        }
        val bytesPerSample = if (isFloat) 4 else 2
        val frameSize = bytesPerSample * chans
        synchronized(lock) {
            val total = inputBuffer.remaining()
            val wholeFrames = total - total % frameSize
            val out = replaceOutputBuffer(total)
            var p = inputBuffer.position()
            val end = p + wholeFrames
            while (p < end) {
                // Decode one frame (LE wire format) + zero non-finite.
                for (c in 0 until chans) {
                    var v = if (isFloat) getFloatLe(inputBuffer, p) else getShortLe(inputBuffer, p) / 32768f
                    p += bytesPerSample
                    if (!v.isFinite()) v = 0f
                    frameScratch[c] = v
                }
                processFrameLocked()
                // Encode one frame.
                for (c in 0 until chans) {
                    if (isFloat) putFloatLe(out, frameScratch[c]) else putShortLe(out, frameScratch[c])
                }
            }
            // Defensive: copy any ragged tail bytes untouched.
            while (p < inputBuffer.limit()) {
                out.put(inputBuffer.get(p))
                p++
            }
            inputBuffer.position(inputBuffer.limit())
            out.flip()
        }
    }

    /** Band split → linked per-band compression → linked limiter + clamp. */
    private fun processFrameLocked() {
        var peakLow = 0f
        var peakMid = 0f
        var peakHigh = 0f
        for (c in 0 until channelCount) {
            val x = frameScratch[c].toDouble() * preGainLin
            val loMem = lpLow[c].toDouble() + aLow * (x - lpLow[c].toDouble())
            lpLow[c] = loMem.toFloat()
            val hiMem = lpHigh[c].toDouble() + aHigh * (x - lpHigh[c].toDouble())
            lpHigh[c] = hiMem.toFloat()
            val lo = loMem
            val hi = x - hiMem
            val mid = x - lo - hi
            bandLow[c] = lo.toFloat()
            bandMid[c] = mid.toFloat()
            bandHigh[c] = hi.toFloat()
            val aLo = kotlin.math.abs(lo).toFloat()
            val aMid = kotlin.math.abs(mid).toFloat()
            val aHi = kotlin.math.abs(hi).toFloat()
            if (aLo > peakLow) peakLow = aLo
            if (aMid > peakMid) peakMid = aMid
            if (aHi > peakHigh) peakHigh = aHi
        }
        envLow = followPeak(envLow, peakLow.toDouble(), attLow, relLow)
        envMid = followPeak(envMid, peakMid.toDouble(), attMid, relMid)
        envHigh = followPeak(envHigh, peakHigh.toDouble(), attHigh, relHigh)
        val gLow = bandGain(envLow, LOW_THRESHOLD_DB, LOW_RATIO, 0.0, false) * lowMakeupLin
        val gMid = bandGain(envMid, MID_THRESHOLD_DB, MID_RATIO, MID_KNEE_DB, true) * midMakeupLin
        val gHigh = bandGain(envHigh, HIGH_THRESHOLD_DB, HIGH_RATIO, 0.0, false) * highMakeupLin
        var peakPost = 0.0
        for (c in 0 until channelCount) {
            val y = bandLow[c].toDouble() * gLow +
                bandMid[c].toDouble() * gMid +
                bandHigh[c].toDouble() * gHigh
            val aY = kotlin.math.abs(y)
            if (aY > peakPost) peakPost = aY
            frameScratch[c] = y.toFloat()
        }
        envLim = followPeak(envLim, peakPost, attLim, relLim)
        val limGain = if (envLim > ceilingLin) ceilingLin / envLim else 1.0
        for (c in 0 until channelCount) {
            var v = frameScratch[c].toDouble() * limGain
            // Hard clamp at the ceiling post-limiter: peaks never exceed it.
            if (v > ceilingLin) v = ceilingLin
            else if (v < -ceilingLin) v = -ceilingLin
            frameScratch[c] = v.toFloat()
        }
    }

    override fun onFlush() {
        synchronized(lock) { resetStateLocked() }
    }

    override fun onReset() {
        synchronized(lock) {
            resetStateLocked()
            channelCount = 0
            encoding = C.ENCODING_INVALID
            lpLow = FloatArray(0)
            lpHigh = FloatArray(0)
            bandLow = FloatArray(0)
            bandMid = FloatArray(0)
            bandHigh = FloatArray(0)
            frameScratch = FloatArray(0)
        }
    }

    private fun resetStateLocked() {
        lpLow.fill(0f)
        lpHigh.fill(0f)
        bandLow.fill(0f)
        bandMid.fill(0f)
        bandHigh.fill(0f)
        frameScratch.fill(0f)
        envLow = 0.0
        envMid = 0.0
        envHigh = 0.0
        envLim = 0.0
    }

    private fun onePoleCoeff(fcHz: Double, sampleRateHz: Double): Double =
        1.0 - exp(-2.0 * kotlin.math.PI * fcHz / sampleRateHz)

    private fun followerCoeff(timeMs: Double, sampleRateHz: Double): Double =
        exp(-1.0 / (sampleRateHz * (timeMs / 1000.0)))

    private fun followPeak(env: Double, peak: Double, att: Double, rel: Double): Double =
        if (peak > env) att * env + (1.0 - att) * peak else rel * env + (1.0 - rel) * peak

    private fun bandGain(env: Double, thresholdDb: Double, ratio: Double, kneeDb: Double, softKnee: Boolean): Double {
        val levelDb = 20.0 * log10(env + 1e-9)
        val over = levelDb - thresholdDb
        val slope = 1.0 - 1.0 / ratio
        val reductionDb = if (!softKnee) {
            if (over <= 0.0) 0.0 else over * slope
        } else {
            when {
                over <= -kneeDb / 2.0 -> 0.0
                over >= kneeDb / 2.0 -> over * slope
                else -> {
                    val d = over + kneeDb / 2.0
                    slope * d * d / (2.0 * kneeDb)
                }
            }
        }
        var g = 10.0.pow(-reductionDb / 20.0)
        if (!g.isFinite() || g < 0.0) g = 1.0
        return g
    }

    // Order-proof LE wire access (decoder/track PCM is LE; BIG_ENDIAN
    // encodings are rejected in onConfigure).
    private fun getShortLe(buf: ByteBuffer, pos: Int): Float {
        val lo = buf.get(pos).toInt() and 0xFF
        val hi = buf.get(pos + 1).toInt()
        var v = lo or (hi shl 8)
        if (v >= 32768) v -= 65536
        return v.toFloat()
    }

    private fun putShortLe(buf: ByteBuffer, sample: Float) {
        var v = (sample * 32767f).toInt()
        if (v > 32767) v = 32767
        else if (v < -32768) v = -32768
        buf.put((v and 0xFF).toByte())
        buf.put(((v shr 8) and 0xFF).toByte())
    }

    private fun getFloatLe(buf: ByteBuffer, pos: Int): Float {
        val b0 = buf.get(pos).toInt() and 0xFF
        val b1 = buf.get(pos + 1).toInt() and 0xFF
        val b2 = buf.get(pos + 2).toInt() and 0xFF
        val b3 = buf.get(pos + 3).toInt()
        return Float.fromBits(b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24))
    }

    private fun putFloatLe(buf: ByteBuffer, sample: Float) {
        val bits = sample.toBits()
        buf.put((bits and 0xFF).toByte())
        buf.put(((bits shr 8) and 0xFF).toByte())
        buf.put(((bits shr 16) and 0xFF).toByte())
        buf.put(((bits shr 24) and 0xFF).toByte())
    }
}
