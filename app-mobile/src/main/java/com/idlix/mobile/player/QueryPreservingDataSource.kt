package com.idlix.mobile.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import com.idlix.data.model.SubtitleTrack
import com.idlix.data.net.IdlixDomainResolver

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

    fun createRenderersFactory(context: Context): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(context) {
            override fun buildTextRenderers(
                context: Context,
                output: TextOutput,
                outputLooper: Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                val renderer = TextRenderer(output, outputLooper)
                renderer.experimentalSetLegacyDecodingEnabled(true)
                out.add(renderer)
            }
        }
    }
}
