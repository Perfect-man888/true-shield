package com.trueshield.app.callguard

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.Notification
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
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeResponse

/**
 * 通话安全护航通知。
 */
object CallGuardNotificationHelper {

    const val CHANNEL_ID = "call_guard"

    private const val CHANNEL_NAME = "通话安全护航"
    private const val NOTIFICATION_ID = 9401
    private const val RESULT_NOTIFICATION_ID = 9402

    fun createChannel(
        context: Context,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "陌生来电风险提醒与一键家庭求助"
            enableVibration(true)
        }

        context.getSystemService(
            NotificationManager::class.java,
        ).createNotificationChannel(channel)
    }

    /**
     * 显示当前通话提醒。
     *
     * 原始号码只作为短期 PendingIntent 参数供用户主动点击求助时使用，
     * 不写入 SharedPreferences，也不形成通话历史。
     */
    fun showRiskNotification(
        context: Context,
        originalNumber: String,
        result: CallNumberAnalyzeResponse,
        canRequestHelp: Boolean,
    ) {
        if (!canPostNotifications(context)) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            9410,
            Intent(context, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_CALL_GUARD, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )

        val levelText = when (result.riskLevel) {
            "high" -> "高风险"
            "medium" -> "需谨慎"
            else -> "低风险"
        }

        val builder = NotificationCompat.Builder(
            context,
            CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle("真信盾通话护航：$levelText")
            .setContentText(
                "${result.maskedNumber} · ${result.summary}",
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "号码：${result.maskedNumber}\n"
                        + "风险分：${result.score}/100\n"
                        + result.summary
                        + "\n\n不要转账、共享验证码或开启屏幕共享。",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentIntent)

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            9412,
            Intent(context, CallGuardActionReceiver::class.java).apply {
                action = CallGuardActionReceiver.ACTION_DISMISS_REMINDER
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(
            R.drawable.ic_shield_notification,
            "结束提醒",
            dismissIntent,
        )

        if (canRequestHelp) {
            val helpIntent = PendingIntent.getBroadcast(
                context,
                9411,
                Intent(
                    context,
                    CallGuardActionReceiver::class.java,
                ).apply {
                    action = CallGuardActionReceiver.ACTION_REQUEST_HELP
                    putExtra(
                        CallGuardActionReceiver.EXTRA_PHONE_NUMBER,
                        originalNumber,
                    )
                    putExtra(
                        CallGuardActionReceiver.EXTRA_DIRECTION,
                        result.direction,
                    )
                    putExtra(
                        CallGuardActionReceiver.EXTRA_VERIFICATION_STATUS,
                        result.verificationStatus,
                    )
                    putExtra(
                        CallGuardActionReceiver.EXTRA_RISK_LEVEL,
                        result.riskLevel,
                    )
                    putExtra(
                        CallGuardActionReceiver.EXTRA_SCORE,
                        result.score,
                    )
                    putExtra(
                        CallGuardActionReceiver.EXTRA_SUMMARY,
                        result.summary,
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
            )

            builder.addAction(
                R.drawable.ic_shield_notification,
                "一键家庭求助",
                helpIntent,
            )
        }

        notifySafely(
            context = context,
            notificationId = NOTIFICATION_ID,
            notification = builder.build(),
        )
    }

    fun cancelRiskNotification(
        context: Context,
    ) {
        NotificationManagerCompat.from(context)
            .cancel(NOTIFICATION_ID)
    }

    fun showResult(
        context: Context,
        title: String,
        message: String,
    ) {
        if (!canPostNotifications(context)) {
            return
        }

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(message),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notifySafely(
            context = context,
            notificationId = RESULT_NOTIFICATION_ID,
            notification = notification,
        )
    }


    /**
     * 调用前已经显式检查 Android 13+ 的通知权限，同时兜底处理
     * 用户在通知创建与发送之间撤销权限造成的 SecurityException。
     */
    @SuppressLint("MissingPermission")
    private fun notifySafely(
        context: Context,
        notificationId: Int,
        notification: Notification,
    ) {
        if (!canPostNotifications(context)) {
            return
        }

        try {
            NotificationManagerCompat
                .from(context)
                .notify(notificationId, notification)
        } catch (_: SecurityException) {
            // 权限可能在极短时间内被用户撤销，通知失败不应导致应用崩溃。
        }
    }

    private fun canPostNotifications(
        context: Context,
    ): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
