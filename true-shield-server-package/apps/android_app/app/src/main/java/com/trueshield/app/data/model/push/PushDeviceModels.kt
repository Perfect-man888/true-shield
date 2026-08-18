package com.trueshield.app.data.model.push

import com.google.gson.annotations.SerializedName

/**
 * 注册当前 Firebase 安装的请求。
 */
data class PushDeviceRegisterRequest(
    @SerializedName("installation_id")
    val installationId: String,

    @SerializedName("platform")
    val platform: String = "android",

    @SerializedName("device_name")
    val deviceName: String? = null,

    @SerializedName("app_version")
    val appVersion: String? = null,
)

/**
 * 后端保存的推送设备。
 */
data class PushDeviceResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("installation_id")
    val installationId: String,

    @SerializedName("platform")
    val platform: String,

    @SerializedName("device_name")
    val deviceName: String? = null,

    @SerializedName("app_version")
    val appVersion: String? = null,

    @SerializedName("status")
    val status: String,

    @SerializedName("last_seen_at")
    val lastSeenAt: String,
)
