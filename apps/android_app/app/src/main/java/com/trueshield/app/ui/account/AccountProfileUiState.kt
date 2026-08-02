package com.trueshield.app.ui.account

import com.trueshield.app.data.model.account.UserAccountResponse

/**
 * 个人中心页面状态。
 */
data class AccountProfileUiState(
    val user: UserAccountResponse? = null,
    val displayName: String = "",
    val phone: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val sessionExpired: Boolean = false,
)
