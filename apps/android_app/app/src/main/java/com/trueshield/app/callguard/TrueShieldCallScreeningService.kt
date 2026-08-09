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

/** Android 系统来电筛查入口。网络请求永远不会阻塞系统五秒响应窗口。 */
class TrueShieldCallScreeningService : CallScreeningService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val direction =
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                callDetails.callDirection == Call.Details.DIRECTION_OUTGOING
            ) "outgoing" else "incoming"
        val number = callDetails.handle?.schemeSpecificPart
            ?.takeIf { it.isNotBlank() } ?: "unknown"
        val verificationStatus =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (callDetails.callerNumberVerificationStatus) {
                    Connection.VERIFICATION_STATUS_PASSED -> "passed"
                    Connection.VERIFICATION_STATUS_FAILED -> "failed"
                    else -> "not_verified"
                }
            } else {
                "not_verified"
            }

        val application = applicationContext as TrueShieldApplication
        val store = CallGuardStore(applicationContext)
        val localResult = CallGuardLocalEvaluator.evaluate(
            phoneNumber = number,
            direction = direction,
            verificationStatus = verificationStatus,
            ruleBundle = store.getUsableRuleBundle(),
        )

        if (direction == "incoming") {
            val isConfirmedRisk = localResult.signals.any { it.confirmed }
            val shouldBlock =
                store.shouldBlockConfirmedRisk() &&
                    localResult.riskLevel == "high" &&
                    isConfirmedRisk
            val shouldSilence =
                !shouldBlock &&
                    store.shouldSilenceHighRisk() &&
                    localResult.riskLevel == "high"
            val responseBuilder = CallResponse.Builder()
                .setDisallowCall(shouldBlock)
                .setRejectCall(shouldBlock)
                .setSkipCallLog(false)
                .setSkipNotification(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                responseBuilder.setSilenceCall(shouldSilence)
            }
            respondToCall(callDetails, responseBuilder.build())
        }

        CallGuardNotificationHelper.showRiskNotification(
            context = applicationContext,
            originalNumber = number,
            result = localResult,
            canRequestHelp =
                application.tokenStore.hasToken() &&
                    !store.getSelectedFamilyId().isNullOrBlank(),
        )

        if (!application.tokenStore.hasToken()) return
        val repository = CallGuardRepository(application.apiClient.callGuardApi)
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
                is CallGuardOperationResult.Success ->
                    CallGuardNotificationHelper.showRiskNotification(
                        context = applicationContext,
                        originalNumber = number,
                        result = result.data,
                        canRequestHelp =
                            !store.getSelectedFamilyId().isNullOrBlank(),
                    )
                is CallGuardOperationResult.Error -> Unit
            }
        }
    }
}
