package com.trueshield.app.ui.login

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.AccountOperationResult
import com.trueshield.app.data.repository.AccountRepository
import com.trueshield.app.data.repository.AuthRepository
import com.trueshield.app.data.repository.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LoginViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val tokenStore =
        trueShieldApplication.tokenStore

    private val pushRegistrationManager =
        trueShieldApplication
            .pushRegistrationManager

    private val authRepository =
        AuthRepository(
            authApi =
                trueShieldApplication
                    .apiClient
                    .authApi,
        )

    private val accountRepository =
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
            LoginUiState(
                isLoggedIn = tokenStore.hasToken(),
            ),
        )

    val uiState: StateFlow<LoginUiState> =
        _uiState.asStateFlow()

    fun onDisplayNameChange(
        displayName: String,
    ) {
        _uiState.update {
            it.copy(
                displayName = displayName,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun onPhoneChange(phone: String) {
        _uiState.update {
            it.copy(
                phone = phone,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun onPasswordChange(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun onConfirmPasswordChange(
        confirmPassword: String,
    ) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun onResetCodeChange(
        resetCode: String,
    ) {
        val normalized = resetCode
            .filter { it.isDigit() }
            .take(6)

        _uiState.update {
            it.copy(
                resetCode = normalized,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun onNewPasswordChange(
        newPassword: String,
    ) {
        _uiState.update {
            it.copy(
                newPassword = newPassword,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun onResetConfirmPasswordChange(
        confirmPassword: String,
    ) {
        _uiState.update {
            it.copy(
                resetConfirmPassword = confirmPassword,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update {
            it.copy(
                isPasswordVisible =
                    !it.isPasswordVisible,
            )
        }
    }

    fun toggleAuthMode() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update { current ->
            current.copy(
                mode = if (current.isRegisterMode) {
                    AuthScreenMode.LOGIN
                } else {
                    AuthScreenMode.REGISTER
                },
                displayName = "",
                phone = "",
                password = "",
                confirmPassword = "",
                resetCode = "",
                newPassword = "",
                resetConfirmPassword = "",
                isPasswordVisible = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun startPasswordReset() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                mode =
                    AuthScreenMode.PASSWORD_RESET_REQUEST,
                password = "",
                confirmPassword = "",
                resetCode = "",
                newPassword = "",
                resetConfirmPassword = "",
                isPasswordVisible = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun backToLogin() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                mode = AuthScreenMode.LOGIN,
                displayName = "",
                phone = "",
                password = "",
                confirmPassword = "",
                resetCode = "",
                newPassword = "",
                resetConfirmPassword = "",
                resetExpiresInSeconds = 0,
                isPasswordVisible = false,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun submit() {
        when (_uiState.value.mode) {
            AuthScreenMode.LOGIN -> login()
            AuthScreenMode.REGISTER -> register()
            AuthScreenMode.PASSWORD_RESET_REQUEST ->
                requestPasswordResetCode()
            AuthScreenMode.PASSWORD_RESET_CONFIRM ->
                confirmPasswordReset()
        }
    }

    fun login() {
        val email = _uiState.value.email
            .trim()
            .lowercase()
        val password = _uiState.value.password

        if (!isValidEmail(email)) {
            showError("请输入正确的邮箱地址。")
            return
        }

        if (password.isBlank()) {
            showError("请输入密码。")
            return
        }

        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result = authRepository.login(
                    email = email,
                    password = password,
                )
            ) {
                is LoginResult.Success -> {
                    completeLogin(
                        result = result,
                    )
                }

                is LoginResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun register() {
        val current = _uiState.value
        val displayName = current.displayName.trim()
        val phone = current.phone.trim()
        val email = current.email
            .trim()
            .lowercase()
        val password = current.password
        val confirmPassword =
            current.confirmPassword

        when {
            displayName.length !in 2..64 -> {
                showError("昵称长度需要在 2 到 64 个字符之间。")
                return
            }

            !isValidEmail(email) -> {
                showError("请输入正确的邮箱地址。")
                return
            }

            phone.isNotBlank() &&
                    phone.length !in 6..32 -> {
                showError("手机号长度需要在 6 到 32 个字符之间。")
                return
            }

            password.length !in 8..128 -> {
                showError("密码长度需要在 8 到 128 个字符之间。")
                return
            }

            password != confirmPassword -> {
                showError("两次输入的密码不一致。")
                return
            }
        }

        if (current.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result = accountRepository.register(
                    email = email,
                    phone = phone,
                    displayName = displayName,
                    password = password,
                )
            ) {
                is AccountOperationResult.Success -> {
                    loginAfterRegistration(
                        email = email,
                        password = password,
                    )
                }

                is AccountOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun requestPasswordResetCode() {
        val email = _uiState.value.email
            .trim()
            .lowercase()

        if (!isValidEmail(email)) {
            showError("请输入正确的邮箱地址。")
            return
        }

        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    accountRepository
                        .requestPasswordResetCode(
                            email = email,
                        )
            ) {
                is AccountOperationResult.Success -> {
                    val debugCode =
                        result.data.debugCode

                    val message = buildString {
                        append(result.data.message)
                        append(
                            " 验证码有效期约 " +
                                    (result.data.expiresIn / 60) +
                                    " 分钟。",
                        )

                        if (!debugCode.isNullOrBlank()) {
                            append("\n开发环境验证码：")
                            append(debugCode)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            mode =
                                AuthScreenMode
                                    .PASSWORD_RESET_CONFIRM,
                            isLoading = false,
                            resetCode =
                                debugCode
                                    ?: it.resetCode,
                            resetExpiresInSeconds =
                                result.data.expiresIn,
                            errorMessage = null,
                            infoMessage = message,
                        )
                    }
                }

                is AccountOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun resendPasswordResetCode() {
        if (!_uiState.value.isPasswordResetConfirmMode) {
            return
        }

        requestPasswordResetCode()
    }

    fun confirmPasswordReset() {
        val current = _uiState.value
        val email = current.email
            .trim()
            .lowercase()
        val code = current.resetCode.trim()
        val newPassword = current.newPassword
        val confirmPassword =
            current.resetConfirmPassword

        when {
            !isValidEmail(email) -> {
                showError("邮箱地址无效，请返回重新填写。")
                return
            }

            code.length != 6 ||
                    code.any { !it.isDigit() } -> {
                showError("请输入邮件中的 6 位数字验证码。")
                return
            }

            newPassword.length !in 8..128 -> {
                showError("新密码长度需要在 8 到 128 个字符之间。")
                return
            }

            newPassword != confirmPassword -> {
                showError("两次输入的新密码不一致。")
                return
            }
        }

        if (current.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    accountRepository
                        .confirmPasswordReset(
                            email = email,
                            code = code,
                            newPassword = newPassword,
                        )
            ) {
                is AccountOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            mode = AuthScreenMode.LOGIN,
                            isLoading = false,
                            password = "",
                            confirmPassword = "",
                            resetCode = "",
                            newPassword = "",
                            resetConfirmPassword = "",
                            resetExpiresInSeconds = 0,
                            isPasswordVisible = false,
                            errorMessage = null,
                            infoMessage =
                                "密码重置成功，请使用新密码登录。",
                        )
                    }
                }

                is AccountOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loginAfterRegistration(
        email: String,
        password: String,
    ) {
        when (
            val loginResult = authRepository.login(
                email = email,
                password = password,
            )
        ) {
            is LoginResult.Success -> {
                completeLogin(
                    result = loginResult,
                )
            }

            is LoginResult.Error -> {
                _uiState.update {
                    it.copy(
                        mode = AuthScreenMode.LOGIN,
                        isLoading = false,
                        password = "",
                        confirmPassword = "",
                        errorMessage = null,
                        infoMessage =
                            "注册成功，请使用新账户登录。",
                    )
                }
            }
        }
    }

    private fun completeLogin(
        result: LoginResult.Success,
    ) {
        tokenStore.saveToken(
            result.token,
        )

        pushRegistrationManager
            .registerWithFirebase()

        _uiState.update {
            it.copy(
                isLoading = false,
                isLoggedIn = true,
                password = "",
                confirmPassword = "",
                resetCode = "",
                newPassword = "",
                resetConfirmPassword = "",
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            withTimeoutOrNull(2_500) {
                pushRegistrationManager
                    .unregisterCurrentDevice()
            }

            tokenStore.clear()
            _uiState.value = LoginUiState()
        }
    }

    private fun isValidEmail(
        email: String,
    ): Boolean {
        return email.isNotBlank() &&
                Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                infoMessage = null,
            )
        }
    }
}
