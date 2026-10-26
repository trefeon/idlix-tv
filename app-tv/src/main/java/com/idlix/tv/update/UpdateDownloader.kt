package com.idlix.tv.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Downloads a release APK via [DownloadManager] into the app-private
 * `Download/updates` dir and exposes progress as a cold [Flow].
 *
 * Emits [UpdateState.Downloading] (progress 0..1), then exactly one terminal
 * [UpdateState.Downloaded] (FileProvider content-uri string, authority
 * `<package>.updateprovider`) or [UpdateState.Failed].
 */
object UpdateDownloader {

    private const val POLL_INTERVAL_MS = 500L

    fun download(context: Context, assetUrl: String): Flow<UpdateState> = flow {
        val appContext = context.applicationContext
        val downloadManager =
            appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            emit(UpdateState.Failed("Downloads unavailable"))
            return@flow
        }

        val updatesDir = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "updates"
        )
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            emit(UpdateState.Failed("Storage unavailable"))
            return@flow
        }
        val fileName = assetUrl.substringAfterLast('/').substringBefore('?')
            .takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "idlix-tv-update.apk"
        val destFile = File(updatesDir, fileName)
        if (destFile.exists()) destFile.delete()

        val downloadId = try {
            val request = DownloadManager.Request(Uri.parse(assetUrl))
                .setTitle("IDLIX TV update")
                .setDescription(fileName)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverRoaming(false)
                .setDestinationUri(Uri.fromFile(destFile))
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            emit(UpdateState.Failed(e.message?.take(120) ?: "Enqueue failed"))
            return@flow
        }

        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = try {
                downloadManager.query(query)
            } catch (e: Exception) {
                emit(UpdateState.Failed(e.message?.take(120) ?: "Query failed"))
                return@flow
            }
            cursor.use {
                if (!it.moveToFirst()) {
                    emit(UpdateState.Failed("Download lost"))
                    return@flow
                }
                val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonIdx = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                val soFarIdx =
                    it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                when (it.getInt(statusIdx)) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val contentUri = try {
                            FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.updateprovider",
                                destFile
                            )
                        } catch (e: Exception) {
                            emit(UpdateState.Failed("File share failed"))
                            return@flow
                        }
                        emit(UpdateState.Downloaded(contentUri.toString()))
                        return@flow
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = if (reasonIdx >= 0) it.getInt(reasonIdx) else -1
                        emit(UpdateState.Failed("Download failed ($reason)"))
                        return@flow
                    }
                    else -> {
                        val soFar = if (soFarIdx >= 0) it.getLong(soFarIdx) else -1L
                        val total = if (totalIdx >= 0) it.getLong(totalIdx) else -1L
                        val progress = if (soFar >= 0 && total > 0) {
                            (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        emit(UpdateState.Downloading(progress))
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)
}
