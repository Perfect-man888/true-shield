package com.trueshield.app.ui.login

/**
 * 登录页当前显示的账户操作模式。
 */
enum class AuthScreenMode {
    LOGIN,
    REGISTER,
    PASSWORD_RESET_REQUEST,
    PASSWORD_RESET_CONFIRM,
}

/**
 * 登录、注册和密码找回页面的全部界面状态。
 */
data class LoginUiState(
    val mode: AuthScreenMode =
        AuthScreenMode.LOGIN,
    val displayName: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val resetCode: String = "",
    val newPassword: String = "",
    val resetConfirmPassword: String = "",
    val resetExpiresInSeconds: Int = 0,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val isLoginMode: Boolean
        get() = mode == AuthScreenMode.LOGIN

    val isRegisterMode: Boolean
        get() = mode == AuthScreenMode.REGISTER

    val isPasswordResetRequestMode: Boolean
        get() =
            mode == AuthScreenMode.PASSWORD_RESET_REQUEST

    val isPasswordResetConfirmMode: Boolean
        get() =
            mode == AuthScreenMode.PASSWORD_RESET_CONFIRM
}
