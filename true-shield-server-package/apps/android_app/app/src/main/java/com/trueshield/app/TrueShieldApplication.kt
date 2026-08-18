package com.trueshield.app

import android.app.Application
import com.trueshield.app.callguard.CallGuardNotificationHelper
import com.trueshield.app.data.local.PushRegistrationStore
import com.trueshield.app.data.local.TokenStore
import com.trueshield.app.data.network.ApiClient
import com.trueshield.app.push.NotificationHelper
import com.trueshield.app.push.PushRegistrationManager

/**
 * 真信盾应用级对象。
 *
 * 整个 App 共用同一个 TokenStore 和 ApiClient。
 */
class TrueShieldApplication : Application() {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var pushRegistrationManager:
        PushRegistrationManager
        private set

    override fun onCreate() {
        super.onCreate()

        tokenStore = TokenStore(
            applicationContext,
        )

        apiClient = ApiClient(
            tokenStore = tokenStore,
        )

        val pushRegistrationStore =
            PushRegistrationStore(
                applicationContext,
            )

        pushRegistrationManager =
            PushRegistrationManager(
                context = applicationContext,
                tokenStore = tokenStore,
                registrationStore =
                    pushRegistrationStore,
                userApi = apiClient.userApi,
            )

        NotificationHelper
            .createNotificationChannels(
                applicationContext,
            )

        CallGuardNotificationHelper
            .createChannel(
                applicationContext,
            )
    }
}