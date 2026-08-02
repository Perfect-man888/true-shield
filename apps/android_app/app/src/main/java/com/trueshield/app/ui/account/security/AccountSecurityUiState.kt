package com.trueshield.app.ui.account.security

/**
 * 账户安全页面状态。
 */
data class AccountSecurityUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val currentPasswordVisible: Boolean = false,
    val newPasswordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val passwordChanged: Boolean = false,
    val sessionExpired: Boolean = false,
)
