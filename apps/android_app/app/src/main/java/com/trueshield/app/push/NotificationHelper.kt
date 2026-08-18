package com.trueshield.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.trueshield.app.MainActivity
import com.trueshield.app.R

/**
 * 真信盾系统通知统一入口。
 */
object NotificationHelper {

    const val FAMILY_ALERT_CHANNEL_ID =
        "family_alerts"

    private const val FAMILY_ALERT_CHANNEL_NAME =
        "家庭安全告警"

    fun createNotificationChannels(
        context: Context,
    ) {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel = NotificationChannel(
            FAMILY_ALERT_CHANNEL_ID,
            FAMILY_ALERT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description =
                "接收家庭高风险告警及处理状态变化"
            enableVibration(true)
        }

        val notificationManager =
            context.getSystemService(
                NotificationManager::class.java,
            )

        notificationManager
            .createNotificationChannel(channel)
    }

    fun showFamilyAlertNotification(
        context: Context,
        title: String,
        body: String,
        familyId: String,
        alertId: String,
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val launchIntent = Intent(
            context,
            MainActivity::class.java,
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(
                PushNavigationTarget.EXTRA_TYPE,
                "family_alert",
            )
            putExtra(
                PushNavigationTarget.EXTRA_FAMILY_ID,
                familyId,
            )
            putExtra(
                PushNavigationTarget.EXTRA_ALERT_ID,
                alertId,
            )
        }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                alertId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(
                context,
                FAMILY_ALERT_CHANNEL_ID,
            )
                .setSmallIcon(
                    R.drawable.ic_shield_notification,
                )
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(body),
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH,
                )
                .setCategory(
                    NotificationCompat.CATEGORY_ALARM,
                )
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        NotificationManagerCompat
            .from(context)
            .notify(
                alertId.hashCode(),
                notification,
            )
    }
}
