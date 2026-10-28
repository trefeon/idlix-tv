package com.idlix.tv.ui.screens.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import com.idlix.data.model.SubtitleTrack
import com.idlix.data.net.IdlixDomainResolver
import java.util.ArrayList

@OptIn(UnstableApi::class)
class QueryPreservingDataSource(
    private val upstream: DataSource,
    private val tokenQuery: String
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val uriStr = uri.toString()
        val finalUri = if (tokenQuery.isNotBlank() && !uriStr.contains("t=")) {
            val delimiter = if (uriStr.contains("?")) "&" else "?"
            Uri.parse("$uriStr$delimiter$tokenQuery")
        } else {
            uri
        }
        val finalSpec = if (finalUri != uri) {
            dataSpec.buildUpon().setUri(finalUri).build()
        } else {
            dataSpec
        }
        return upstream.open(finalSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
    }
}

@OptIn(UnstableApi::class)
class QueryPreservingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val tokenQuery: String
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return QueryPreservingDataSource(upstreamFactory.createDataSource(), tokenQuery)
    }
}

@OptIn(UnstableApi::class)
object MediaSourceHelper {

    fun createHttpDataSourceFactory(tokenQuery: String): DataSource.Factory {
        val baseHttp = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            // TV keeps longer 8s timeouts (vs 5s on mobile): the TV backlog is
            // deeper (30s/60s buffers) on steady Wi-Fi, so a slow mirror gets
            // more time before the player fails over. Mobile fails fast for
            // stalled cell networks instead.
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to IdlixDomainResolver.referer(),
                    "Origin" to IdlixDomainResolver.origin()
                )
            )
        return QueryPreservingDataSourceFactory(baseHttp, tokenQuery)
    }

    fun buildMediaSource(
        context: Context,
        masterUrl: String,
        sub: SubtitleTrack?,
        httpDataSourceFactory: DataSource.Factory
    ): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(masterUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val hlsSource = HlsMediaSource.Factory(httpDataSourceFactory)
            // Single-variant IDLIX playlists carry full variant info, so
            // chunkless preparation shortens tune-in without extra fetches.
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
        if (sub == null) return hlsSource

        val mimeType = if (sub.path.endsWith(".srt", ignoreCase = true)) {
            MimeTypes.APPLICATION_SUBRIP
        } else {
            MimeTypes.TEXT_VTT
        }

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.path))
            .setMimeType(mimeType)
            .setLanguage(sub.lang)
            .setLabel(sub.label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()

        val subUri = Uri.parse(sub.path)
        val subDataSourceFactory = if (subUri.scheme == "file" || subUri.scheme == null) {
            DefaultDataSource.Factory(context)
        } else {
            httpDataSourceFactory
        }

        val subtitleSource = SingleSampleMediaSource.Factory(subDataSourceFactory)
            .createMediaSource(subtitleConfig, C.TIME_UNSET)

        return MergingMediaSource(hlsSource, subtitleSource)
    }

    /**
     * Renderers factory with the stage-1 PCM loudness normalizer in the audio
     * chain. The [buildAudioSink] override is the stock body plus
     * `.setAudioProcessors(arrayOf(normalizer))`; the text-renderer block is
     * untouched. Offload is never enabled (default off, and a processor in
     * the chain forces the software PCM path anyway), so the normalizer always
     * sees the decoded PCM. Toggle via [LoudnessNormalizeProcessor.setEnabled]
     * (live, no rebuild); session attach/detach is a no-op for this pure-PCM
     * stage (no player/audio-session reference held).
     */
    fun createRenderersFactory(
        context: Context,
        normalizer: LoudnessNormalizeProcessor,
    ): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(normalizer))
                    .build()
            }

            override fun buildTextRenderers(
                context: Context,
                output: TextOutput,
                outputLooper: Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Normalize cue line so SubtitleView.bottomPaddingFraction applies:
                // it only affects cues with line == DIMEN_UNSET. Horizontal
                // position/alignment are preserved.
                val normalizedOutput = TextOutput { cueGroup ->
                    output.onCues(
                        CueGroup(
                            cueGroup.cues.map { cue ->
                                cue.buildUpon().setLine(Cue.DIMEN_UNSET, cue.lineType).build()
                            },
                            cueGroup.presentationTimeUs
                        )
                    )
                }
                val renderer = TextRenderer(normalizedOutput, outputLooper)
                renderer.experimentalSetLegacyDecodingEnabled(true)
                out.add(renderer)
            }
        }
    }
}
