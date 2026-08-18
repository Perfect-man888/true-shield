package com.trueshield.app.data.network

import com.trueshield.app.data.local.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 真信盾后端网络客户端。
 *
 * 整个 App 共用一个 ApiClient 和 Retrofit 实例。
 */
class ApiClient(
    tokenStore: TokenStore,
) {

    /**
     * Android 模拟器通过 10.0.2.2
     * 访问运行后端的开发电脑。
     */
    private companion object {
        const val BASE_URL =
            "http://192.168.0.106:8000/"
    }

    /**
     * BASIC 只记录请求方法、地址和状态码，
     * 不记录登录密码等请求正文。
     */
    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {
            level =
                HttpLoggingInterceptor.Level.BASIC
        }

    private val authInterceptor =
        AuthInterceptor(
            tokenStore = tokenStore,
        )

    private val okHttpClient =
        OkHttpClient.Builder()
            /*
             * 在请求发送前自动添加 JWT。
             */
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(
                15,
                TimeUnit.SECONDS,
            )
            // 本地 Whisper 转写可能需要几十秒。
            .readTimeout(
                180,
                TimeUnit.SECONDS,
            )
            .writeTimeout(
                180,
                TimeUnit.SECONDS,
            )
            .callTimeout(
                210,
                TimeUnit.SECONDS,
            )
            .build()

    private val retrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(),
            )
            .build()

    val authApi: AuthApi by lazy {
        retrofit.create(
            AuthApi::class.java,
        )
    }

    /**
     * 当前用户资料接口。
     */
    val userApi: UserApi by lazy {
        retrofit.create(
            UserApi::class.java,
        )
    }

    /**
     * 风险检测接口。
     */
    val riskApi: RiskApi by lazy {
        retrofit.create(
            RiskApi::class.java,
        )
    }

    /**
     * 通话安全护航接口。
     */
    val callGuardApi: CallGuardApi by lazy {
        retrofit.create(
            CallGuardApi::class.java,
        )
    }

    /**
     * 家庭中心接口。
     */

    /**
     * 可信联系人接口。
     */
    val trustedContactApi:
            TrustedContactApi by lazy {
        retrofit.create(
            TrustedContactApi::class.java,
        )
    }

    val familyApi: FamilyApi by lazy {
        retrofit.create(FamilyApi::class.java)
    }
    /*
     * 后面会继续在这里增加：
     *
     * val riskApi: RiskApi
     * val familyApi: FamilyApi
     * val contactApi: ContactApi
     * val alertApi: AlertApi
     */
}