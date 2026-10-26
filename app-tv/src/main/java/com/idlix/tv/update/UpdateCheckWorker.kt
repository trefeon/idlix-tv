package com.idlix.tv.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.idlix.tv.BuildConfig
import com.idlix.tv.R
import java.util.concurrent.TimeUnit

/**
 * Daily background update check. Notifies ONLY when a newer release is
 * [UpdateState.Available]; every other state (up to date, offline, failed)
 * stays silent so the TV never nags.
 *
 * Requires `androidx.work:work-runtime-ktx` (see build-gap note in the
 * slice report). Schedule once from app startup via [schedule].
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val state = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE.toLong())
        if (state is UpdateState.Available) {
            showAvailableNotification(state)
        }
        return Result.success()
    }

    private fun showAvailableNotification(state: UpdateState.Available) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.update_channel_desc)
                }
            )
        }
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { intent ->
                android.app.PendingIntent.getActivity(
                    context, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
            }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(
                context.getString(R.string.update_available_title, state.version)
            )
            .setContentText(context.getString(R.string.update_channel_desc))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .apply { if (launch != null) setContentIntent(launch) }
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "idlix_update_check"
        private const val CHANNEL_ID = "idlix_updates"
        private const val NOTIFICATION_ID = 9401

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
