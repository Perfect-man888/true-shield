package com.trueshield.app.push

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.trueshield.app.data.local.PushRegistrationStore
import com.trueshield.app.data.local.TokenStore
import com.trueshield.app.data.model.push.PushDeviceRegisterRequest
import com.trueshield.app.data.network.UserApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 负责 Firebase 安装标识与真信盾账户之间的绑定。
 */
class PushRegistrationManager(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val registrationStore: PushRegistrationStore,
    private val userApi: UserApi,
) {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO,
    )

    private val syncMutex = Mutex()

    /**
     * 启动 Firebase 推送注册，并把当前 FID 同步到后端。
     *
     * 关键点：读取 FID 不再依赖 register() 的成功回调。
     * 某些模拟器中 register() 任务可能等待较久，但 FID 已经可以读取。
     */
    fun registerWithFirebase() {
        Log.i(TAG, "Starting Firebase push registration")

        // 本地已有 FID 时，先立即尝试同步。
        syncStoredInstallation()

        // 立即读取 FID，不等待 FCM register() 完成。
        fetchInstallationIdAndSync(
            reason = "immediate",
        )

        FirebaseMessaging
            .getInstance()
            .register()
            .addOnSuccessListener {
                Log.i(TAG, "FCM registration completed")
                fetchInstallationIdAndSync(
                    reason = "register_success",
                )
            }
            .addOnFailureListener { error ->
                Log.w(
                    TAG,
                    "FCM installation registration failed",
                    error,
                )
            }
            .addOnCompleteListener { task ->
                Log.i(
                    TAG,
                    "FCM register task completed: " +
                        "successful=${task.isSuccessful}",
                )
            }

        // 网络或 Google Play 服务刚启动时，第一次 FID 获取可能延迟。
        // 3 秒后再补一次，不影响已成功的首次同步。
        scope.launch {
            delay(3_000)
            fetchInstallationIdAndSync(
                reason = "delayed_retry",
            )
        }
    }

    /**
     * Firebase 创建或轮换 FID 时调用。
     */
    fun onInstallationRegistered(
        installationId: String,
    ) {
        saveAndSyncInstallation(
            installationId = installationId,
            source = "onRegistered",
        )
    }

    /**
     * 登录成功或 App 启动时把本地 FID 同步到后端。
     */
    fun syncStoredInstallation() {
        val installationId =
            registrationStore
                .getInstallationId()

        if (installationId == null) {
            Log.i(TAG, "No stored Firebase installation ID yet")
            return
        }

        if (!tokenStore.hasToken()) {
            Log.i(TAG, "Skip push sync because account is not logged in")
            return
        }

        syncInstallationToBackend(
            installationId = installationId,
        )
    }

    /**
     * 退出账户前停用当前设备，避免登出后继续收到该账户告警。
     */
    suspend fun unregisterCurrentDevice() {
        val installationId =
            registrationStore
                .getInstallationId()
                ?: return

        if (!tokenStore.hasToken()) {
            return
        }

        try {
            userApi.unregisterPushDevice(
                installationId = installationId,
            )
        } catch (error: Exception) {
            // 退出登录不能因为网络问题被阻止。
            Log.w(
                TAG,
                "Push device unregister failed",
                error,
            )
        }
    }

    private fun fetchInstallationIdAndSync(
        reason: String,
    ) {
        Log.i(
            TAG,
            "Fetching Firebase installation ID: $reason",
        )

        FirebaseInstallations
            .getInstance()
            .id
            .addOnSuccessListener { installationId ->
                saveAndSyncInstallation(
                    installationId = installationId,
                    source = reason,
                )
            }
            .addOnFailureListener { error ->
                Log.w(
                    TAG,
                    "Fetching Firebase installation ID failed: " +
                        reason,
                    error,
                )
            }
    }

    private fun saveAndSyncInstallation(
        installationId: String,
        source: String,
    ) {
        val normalized = installationId.trim()

        if (normalized.isBlank()) {
            Log.w(
                TAG,
                "Firebase installation ID is blank: $source",
            )
            return
        }

        registrationStore.saveInstallationId(
            normalized,
        )

        Log.i(
            TAG,
            "Firebase installation ID acquired: " +
                "$source, length=${normalized.length}",
        )

        if (!tokenStore.hasToken()) {
            Log.i(
                TAG,
                "FID saved; waiting for account login before backend sync",
            )
            return
        }

        syncInstallationToBackend(
            installationId = normalized,
        )
    }

    private fun syncInstallationToBackend(
        installationId: String,
    ) {
        scope.launch {
            syncMutex.withLock {
                try {
                    Log.i(
                        TAG,
                        "Syncing push device to backend",
                    )

                    val response =
                        userApi.registerPushDevice(
                            PushDeviceRegisterRequest(
                                installationId =
                                    installationId,
                                deviceName =
                                    buildDeviceName(),
                                appVersion =
                                    getAppVersion(),
                            ),
                        )

                    if (response.isSuccessful) {
                        Log.i(
                            TAG,
                            "Push device synced to backend: " +
                                response.code(),
                        )
                    } else {
                        val errorBody =
                            runCatching {
                                response.errorBody()
                                    ?.string()
                            }.getOrNull()

                        Log.w(
                            TAG,
                            "Push device sync failed: " +
                                "code=${response.code()}, " +
                                "body=$errorBody",
                        )
                    }
                } catch (error: Exception) {
                    Log.w(
                        TAG,
                        "Push device sync failed with exception",
                        error,
                    )
                }
            }
        }
    }

    private fun buildDeviceName(): String {
        return listOf(
            Build.MANUFACTURER,
            Build.MODEL,
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(128)
    }

    @Suppress("DEPRECATION")
    private fun getAppVersion(): String? {
        return try {
            context
                .packageManager
                .getPackageInfo(
                    context.packageName,
                    0,
                )
                .versionName
                ?.take(32)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val TAG = "TrueShieldPush"
    }
}
