package com.trueshield.app.ui.account.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.AccountOperationResult
import com.trueshield.app.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 账户安全业务状态管理。
 */
class AccountSecurityViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val repository =
        AccountRepository(
            authApi =
                trueShieldApplication
                    .apiClient
                    .authApi,
            userApi =
                trueShieldApplication
                    .apiClient
                    .userApi,
        )

    private val _uiState =
        MutableStateFlow(
            AccountSecurityUiState(),
        )

    val uiState: StateFlow<AccountSecurityUiState> =
        _uiState.asStateFlow()

    fun onCurrentPasswordChange(value: String) {
        updatePasswordField {
            copy(currentPassword = value)
        }
    }

    fun onNewPasswordChange(value: String) {
        updatePasswordField {
            copy(newPassword = value)
        }
    }

    fun onConfirmPasswordChange(value: String) {
        updatePasswordField {
            copy(confirmPassword = value)
        }
    }

    fun toggleCurrentPasswordVisibility() {
        _uiState.update {
            it.copy(
                currentPasswordVisible =
                    !it.currentPasswordVisible,
            )
        }
    }

    fun toggleNewPasswordVisibility() {
        _uiState.update {
            it.copy(
                newPasswordVisible =
                    !it.newPasswordVisible,
            )
        }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update {
            it.copy(
                confirmPasswordVisible =
                    !it.confirmPasswordVisible,
            )
        }
    }

    fun changePassword() {
        val current = _uiState.value

        when {
            current.currentPassword.length !in 8..128 -> {
                showError("当前密码长度应为 8–128 个字符。")
                return
            }

            current.newPassword.length !in 8..128 -> {
                showError("新密码长度应为 8–128 个字符。")
                return
            }

            current.newPassword ==
                    current.currentPassword -> {
                showError("新密码不能与当前密码相同。")
                return
            }

            current.confirmPassword !=
                    current.newPassword -> {
                showError("两次输入的新密码不一致。")
                return
            }

            current.isSubmitting ||
                    current.passwordChanged -> return
        }

        _uiState.update {
            it.copy(
                isSubmitting = true,
                errorMessage = null,
                successMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result = repository.changePassword(
                    currentPassword =
                        current.currentPassword,
                    newPassword = current.newPassword,
                )
            ) {
                is AccountOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            isSubmitting = false,
                            errorMessage = null,
                            successMessage =
                                "密码修改成功，请使用新密码重新登录。",
                            passwordChanged = true,
                            sessionExpired = false,
                        )
                    }
                }

                is AccountOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message,
                            successMessage = null,
                            sessionExpired =
                                result.requiresLogin,
                        )
                    }
                }
            }
        }
    }

    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(sessionExpired = false)
        }
    }

    private fun updatePasswordField(
        transform: AccountSecurityUiState.() ->
        AccountSecurityUiState,
    ) {
        _uiState.update {
            it.transform().copy(
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                successMessage = null,
            )
        }
    }
}
