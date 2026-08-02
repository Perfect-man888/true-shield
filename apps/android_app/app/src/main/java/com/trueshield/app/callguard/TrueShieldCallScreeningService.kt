package com.trueshield.app.callguard

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeRequest
import com.trueshield.app.data.repository.CallGuardOperationResult
import com.trueshield.app.data.repository.CallGuardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android 系统来电筛查入口。
 *
 * 第一版不会拦截电话，也不会录音；只在系统回调中取得当前号码、
 * 运营商验证状态和来去电方向，并显示风险提醒。
 */
class TrueShieldCallScreeningService : CallScreeningService() {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(
        callDetails: Call.Details,
    ) {
        val direction =
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                callDetails.callDirection ==
                Call.Details.DIRECTION_OUTGOING
            ) {
                "outgoing"
            } else {
                "incoming"
            }

        /*
         * 来电必须在 5 秒内回应。第一版始终允许，不因网络结果延迟响铃。
         */
        if (direction == "incoming") {
            val responseBuilder =
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)

            // setSilenceCall 从 Android 10（API 29）开始提供。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                responseBuilder.setSilenceCall(false)
            }

            respondToCall(
                callDetails,
                responseBuilder.build(),
            )
        }

        val number =
            callDetails.handle
                ?.schemeSpecificPart
                ?.takeIf { it.isNotBlank() }
                ?: "unknown"

        val verificationStatus =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (
                    callDetails
                        .callerNumberVerificationStatus
                ) {
                    Connection.VERIFICATION_STATUS_PASSED ->
                        "passed"

                    Connection.VERIFICATION_STATUS_FAILED ->
                        "failed"

                    else -> "not_verified"
                }
            } else {
                "not_verified"
            }

        val application =
            applicationContext as TrueShieldApplication
        val store = CallGuardStore(applicationContext)
        val localResult =
            CallGuardLocalEvaluator.evaluate(
                phoneNumber = number,
                direction = direction,
                verificationStatus = verificationStatus,
            )

        CallGuardNotificationHelper.showRiskNotification(
            context = applicationContext,
            originalNumber = number,
            result = localResult,
            canRequestHelp =
                application.tokenStore.hasToken() &&
                    !store.getSelectedFamilyId().isNullOrBlank(),
        )

        if (!application.tokenStore.hasToken()) {
            return
        }

        val repository = CallGuardRepository(
            callGuardApi = application.apiClient.callGuardApi,
        )

        scope.launch {
            when (
                val result = repository.analyzeNumber(
                    CallNumberAnalyzeRequest(
                        phoneNumber = number,
                        direction = direction,
                        verificationStatus = verificationStatus,
                    ),
                )
            ) {
                is CallGuardOperationResult.Success -> {
                    CallGuardNotificationHelper
                        .showRiskNotification(
                            context = applicationContext,
                            originalNumber = number,
                            result = result.data,
                            canRequestHelp =
                                !store
                                    .getSelectedFamilyId()
                                    .isNullOrBlank(),
                        )
                }

                is CallGuardOperationResult.Error -> {
                    // 网络失败时保留本地判断，不影响来电流程。
                }
            }
        }
    }
}
