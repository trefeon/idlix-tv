package com.idlix.tv.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.idlix.tv.R
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
            emit(UpdateState.Failed(appContext.getString(R.string.dl_err_unavailable)))
            return@flow
        }

        // getExternalFilesDir returns null when shared storage is not
        // mounted. File(null, child) would resolve against the process cwd
        // and poison setDestinationUri/FileProvider below — fail fast instead.
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: run {
                emit(UpdateState.Failed(appContext.getString(R.string.dl_err_storage)))
                return@flow
            }
        val updatesDir = File(downloadsDir, "updates")
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            emit(UpdateState.Failed(appContext.getString(R.string.dl_err_storage)))
            return@flow
        }
        val fileName = assetUrl.substringAfterLast('/').substringBefore('?')
            .takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "idlix-tv-update.apk"
        val destFile = File(updatesDir, fileName)
        if (destFile.exists()) destFile.delete()

        val downloadId = try {
            val request = DownloadManager.Request(Uri.parse(assetUrl))
                .setTitle(appContext.getString(R.string.update_download))
                .setDescription(fileName)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverRoaming(false)
                .setDestinationUri(Uri.fromFile(destFile))
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            emit(UpdateState.Failed(appContext.getString(R.string.dl_err_enqueue, e.message?.take(120) ?: "—")))
            return@flow
        }

        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = try {
                downloadManager.query(query)
            } catch (e: Exception) {
                emit(UpdateState.Failed(appContext.getString(R.string.dl_err_query, e.message?.take(120) ?: "—")))
                return@flow
            }
            if (cursor == null) {
                emit(UpdateState.Failed(appContext.getString(R.string.dl_err_lost)))
                return@flow
            }
            var terminal: UpdateState? = null
            var progress = 0f
            var shareFailed = false
            try {
                cursor.use { c ->
                    if (!c.moveToFirst()) {
                        terminal = UpdateState.Failed(appContext.getString(R.string.dl_err_lost))
                        return@use
                    }
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val contentUri = try {
                                FileProvider.getUriForFile(
                                    appContext,
                                    "${appContext.packageName}.updateprovider",
                                    destFile
                                )
                            } catch (e: Exception) {
                                shareFailed = true
                                return@use
                            }
                            terminal = UpdateState.Downloaded(contentUri.toString())
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            terminal = UpdateState.Failed(appContext.getString(R.string.dl_err_failed, reason))
                        }
                        else -> {
                            val soFar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            progress = if (soFar >= 0 && total > 0) {
                                (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                emit(UpdateState.Failed(appContext.getString(R.string.dl_err_query, e.message?.take(120) ?: "—")))
                return@flow
            }
            if (shareFailed) {
                emit(UpdateState.Failed(appContext.getString(R.string.dl_err_share)))
                return@flow
            }
            val done = terminal
            if (done != null) {
                emit(done)
                return@flow
            }
            emit(UpdateState.Downloading(progress))
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)
}
