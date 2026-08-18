package com.trueshield.app.data.network

import com.trueshield.app.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 为需要登录的请求自动添加 JWT。
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(
        chain: Interceptor.Chain,
    ): Response {
        val originalRequest = chain.request()

        /*
         * 登录、注册等认证接口不需要携带旧 Token。
         */
        val isAuthRequest =
            originalRequest.url.encodedPath
                .startsWith("/api/v1/auth/")

        val accessToken =
            tokenStore.getAccessToken()

        if (
            isAuthRequest ||
            accessToken.isNullOrBlank()
        ) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest =
            originalRequest
                .newBuilder()
                .header(
                    "Authorization",
                    "Bearer $accessToken",
                )
                .build()

        return chain.proceed(
            authenticatedRequest,
        )
    }
}