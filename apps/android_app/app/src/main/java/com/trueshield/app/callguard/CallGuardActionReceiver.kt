package com.trueshield.app.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.model.callguard.CallGuardHelpRequest
import com.trueshield.app.data.repository.CallGuardOperationResult
import com.trueshield.app.data.repository.CallGuardRepository
import com.trueshield.app.push.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 系统通话护航通知中的“一键家庭求助”操作。
 */
class CallGuardActionReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == ACTION_DISMISS_REMINDER) {
            CallGuardNotificationHelper.cancelRiskNotification(context)
            return
        }
        if (intent.action != ACTION_REQUEST_HELP) {
            return
        }

        val pendingResult = goAsync()
        val app =
            context.applicationContext as TrueShieldApplication
        val store = CallGuardStore(context.applicationContext)
        val familyId = store.getSelectedFamilyId()
        val phoneNumber =
            intent.getStringExtra(EXTRA_PHONE_NUMBER)
                ?.takeIf { it.isNotBlank() }
        val summary =
            intent.getStringExtra(EXTRA_SUMMARY)
                ?.takeIf { it.isNotBlank() }

        if (
            familyId.isNullOrBlank() ||
            phoneNumber.isNullOrBlank() ||
            summary.isNullOrBlank()
        ) {
            CallGuardNotificationHelper.showResult(
                context,
                title = "无法发送家庭求助",
                message = "请先在真信盾“通话安全护航”页面选择求助家庭。",
            )
            pendingResult.finish()
            return
        }

        if (!app.tokenStore.hasToken()) {
            CallGuardNotificationHelper.showResult(
                context,
                title = "请重新登录",
                message = "登录状态失效，暂时无法发送家庭求助。",
            )
            pendingResult.finish()
            return
        }

        CoroutineScope(
            SupervisorJob() + Dispatchers.IO,
        ).launch {
            try {
                val repository = CallGuardRepository(
                    callGuardApi = app.apiClient.callGuardApi,
                )

                when (
                    val result = repository.requestFamilyHelp(
                        CallGuardHelpRequest(
                            familyId = familyId,
                            phoneNumber = phoneNumber,
                            direction =
                                intent.getStringExtra(EXTRA_DIRECTION)
                                    ?: "unknown",
                            verificationStatus =
                                intent.getStringExtra(
                                    EXTRA_VERIFICATION_STATUS,
                                ) ?: "not_verified",
                            riskLevel =
                                intent.getStringExtra(EXTRA_RISK_LEVEL)
                                    ?: "high",
                            score = intent.getIntExtra(EXTRA_SCORE, 100),
                            summary = summary,
                        ),
                    )
                ) {
                    is CallGuardOperationResult.Success -> {
                        CallGuardNotificationHelper
                            .cancelRiskNotification(context)

                        NotificationHelper
                            .showFamilyAlertNotification(
                                context = context,
                                title = "家庭求助已发送",
                                body = result.data.message,
                                familyId = result.data.familyId,
                                alertId = result.data.alertId,
                            )
                    }

                    is CallGuardOperationResult.Error -> {
                        CallGuardNotificationHelper.showResult(
                            context,
                            title = "家庭求助发送失败",
                            message = result.message,
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REQUEST_HELP =
            "com.trueshield.app.action.CALL_GUARD_HELP"
        const val ACTION_DISMISS_REMINDER =
            "com.trueshield.app.action.CALL_GUARD_DISMISS"

        const val EXTRA_PHONE_NUMBER = "call_guard_phone_number"
        const val EXTRA_DIRECTION = "call_guard_direction"
        const val EXTRA_VERIFICATION_STATUS =
            "call_guard_verification_status"
        const val EXTRA_RISK_LEVEL = "call_guard_risk_level"
        const val EXTRA_SCORE = "call_guard_score"
        const val EXTRA_SUMMARY = "call_guard_summary"
    }
}
