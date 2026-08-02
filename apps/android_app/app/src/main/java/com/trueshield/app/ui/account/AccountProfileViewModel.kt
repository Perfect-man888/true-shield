package com.trueshield.app.ui.account

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
 * 个人中心业务状态管理。
 */
class AccountProfileViewModel(
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
            AccountProfileUiState(),
        )

    val uiState: StateFlow<AccountProfileUiState> =
        _uiState.asStateFlow()

    fun loadProfile() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result = repository.getCurrentUser()
            ) {
                is AccountOperationResult.Success -> {
                    val user = result.data

                    _uiState.update {
                        it.copy(
                            user = user,
                            displayName = user.displayName,
                            phone = user.phone.orEmpty(),
                            isLoading = false,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is AccountOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            sessionExpired =
                                result.requiresLogin,
                        )
                    }
                }
            }
        }
    }

    fun onDisplayNameChange(
        displayName: String,
    ) {
        _uiState.update {
            it.copy(
                displayName = displayName,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun onPhoneChange(phone: String) {
        _uiState.update {
            it.copy(
                phone = phone,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun resetDraft() {
        val user = _uiState.value.user
            ?: return

        _uiState.update {
            it.copy(
                displayName = user.displayName,
                phone = user.phone.orEmpty(),
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun saveProfile() {
        val current = _uiState.value
        val displayName =
            current.displayName.trim()
        val phone = current.phone.trim()

        when {
            displayName.length !in 2..64 -> {
                showError(
                    "昵称长度需要在 2 到 64 个字符之间。",
                )
                return
            }

            phone.isNotBlank() &&
                    phone.length !in 6..32 -> {
                showError(
                    "手机号长度需要在 6 到 32 个字符之间。",
                )
                return
            }
        }

        if (
            current.isSaving ||
            current.isLoading
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isSaving = true,
                errorMessage = null,
                successMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    repository.updateCurrentUser(
                        displayName = displayName,
                        phone = phone,
                    )
            ) {
                is AccountOperationResult.Success -> {
                    val updatedUser = result.data

                    _uiState.update {
                        it.copy(
                            user = updatedUser,
                            displayName =
                                updatedUser.displayName,
                            phone =
                                updatedUser.phone.orEmpty(),
                            isSaving = false,
                            errorMessage = null,
                            successMessage =
                                "个人资料保存成功。",
                            sessionExpired = false,
                        )
                    }
                }

                is AccountOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.message,
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
            it.copy(
                sessionExpired = false,
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
