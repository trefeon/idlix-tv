package com.idlix.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Installs a downloaded APK. Pre-checks install-unknown-apps permission: when
 * blocked, routes straight to the per-app unknown-sources settings screen
 * (the only grant path on TV, no runtime permission dialog exists).
 *
 * Fallback note: if ACTION_VIEW ever stops resolving on a device, drive this
 * through the PackageInstaller Session API instead — open a session with
 * PackageInstaller.SessionParams(MODE_FULL_INSTALL), stream the APK bytes via
 * session.openWrite(), then commit with a PendingIntent to a status receiver.
 * ACTION_VIEW is kept as the primary path: it is the documented TV flow and
 * needs no session plumbing.
 */
object UpdateInstaller {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun canInstallUnknownApps(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun unknownAppSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun installApk(context: Context, apkUri: Uri) {
        if (!canInstallUnknownApps(context)) {
            context.startActivity(unknownAppSourcesIntent(context))
            return
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(view)
    }

    fun installApk(context: Context, apkUriString: String) {
        installApk(context, Uri.parse(apkUriString))
    }
}
