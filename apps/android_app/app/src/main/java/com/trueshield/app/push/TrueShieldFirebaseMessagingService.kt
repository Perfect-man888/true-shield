package com.trueshield.app.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.trueshield.app.TrueShieldApplication

/**
 * 接收 FCM 注册变化与前台推送消息。
 */
class TrueShieldFirebaseMessagingService :
    FirebaseMessagingService() {

    override fun onRegistered(
        installationId: String,
    ) {
        super.onRegistered(installationId)

        Log.i(
            TAG,
            "FCM onRegistered callback received: " +
                "length=${installationId.length}",
        )

        val application =
            applicationContext as
                TrueShieldApplication

        application
            .pushRegistrationManager
            .onInstallationRegistered(
                installationId,
            )
    }

    override fun onMessageReceived(
        remoteMessage: RemoteMessage,
    ) {
        super.onMessageReceived(remoteMessage)

        Log.i(
            TAG,
            "FCM message received: " +
                "messageId=${remoteMessage.messageId}",
        )

        val data = remoteMessage.data

        if (data["type"] != "family_alert") {
            return
        }

        val familyId = data["family_id"]
            ?.trim()
            .orEmpty()
        val alertId = data["alert_id"]
            ?.trim()
            .orEmpty()

        if (
            familyId.isBlank() ||
            alertId.isBlank()
        ) {
            Log.w(
                TAG,
                "Family alert push missing ids",
            )
            return
        }

        val title =
            remoteMessage.notification?.title
                ?: when (data["event_type"]) {
                    "created" ->
                        "真信盾：新的家庭高风险告警"
                    "acknowledged" ->
                        "家庭告警已确认"
                    "resolved" ->
                        "家庭告警已处理完成"
                    else ->
                        "家庭告警状态更新"
                }

        val body =
            remoteMessage.notification?.body
                ?: "点击查看家庭告警详情。"

        NotificationHelper
            .showFamilyAlertNotification(
                context = this,
                title = title,
                body = body,
                familyId = familyId,
                alertId = alertId,
            )
    }

    private companion object {
        const val TAG = "TrueShieldFCM"
    }
}
