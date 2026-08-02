package com.trueshield.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trueshield.app.push.PushNavigationTarget
import com.trueshield.app.ui.login.LoginScreen
import com.trueshield.app.ui.login.LoginViewModel
import com.trueshield.app.ui.navigation.TrueShieldNavHost
import com.trueshield.app.ui.onboarding.OnboardingScreen
import com.trueshield.app.ui.settings.UiSettingsViewModel
import com.trueshield.app.ui.theme.TrueShieldTheme
import com.trueshield.app.ui.theme.TrueShieldThemeMode

class MainActivity : ComponentActivity() {

    private var pendingPushTarget by
        mutableStateOf<PushNavigationTarget?>(
            null,
        )

    private var pendingCallGuardNavigation by
        mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestPermission(),
        ) {
            // 用户拒绝后仍可继续使用全部非推送功能。
        }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        pendingPushTarget =
            PushNavigationTarget
                .fromIntent(intent)

        pendingCallGuardNavigation =
            intent.getBooleanExtra(
                EXTRA_OPEN_CALL_GUARD,
                false,
            )

        enableEdgeToEdge()

        setContent {
            val uiSettingsViewModel:
                    UiSettingsViewModel = viewModel()

            val uiSettingsState by
                uiSettingsViewModel
                    .uiState
                    .collectAsState()

            val systemDarkTheme = isSystemInDarkTheme()
            val useDarkSystemBarIcons =
                when (uiSettingsState.themeMode) {
                    TrueShieldThemeMode.SYSTEM -> !systemDarkTheme
                    TrueShieldThemeMode.LIGHT -> true
                    TrueShieldThemeMode.DARK -> false
                }

            SideEffect {
                WindowCompat
                    .getInsetsController(
                        window,
                        window.decorView,
                    )
                    .apply {
                        isAppearanceLightStatusBars =
                            useDarkSystemBarIcons
                        isAppearanceLightNavigationBars =
                            useDarkSystemBarIcons
                    }
            }

            TrueShieldTheme(
                themeMode = uiSettingsState.themeMode,
                elderMode = uiSettingsState.elderModeEnabled,
            ) {
                val loginViewModel:
                        LoginViewModel = viewModel()

                val loginState by
                loginViewModel
                    .uiState
                    .collectAsState()

                LaunchedEffect(
                    loginState.isLoggedIn,
                ) {
                    if (loginState.isLoggedIn) {
                        requestNotificationPermission()

                        val trueShieldApplication =
                            application as
                                TrueShieldApplication

                        trueShieldApplication
                            .pushRegistrationManager
                            .registerWithFirebase()
                    }
                }

                Surface(
                    modifier =
                        Modifier.fillMaxSize(),
                ) {
                    if (uiSettingsState.onboardingVisible) {
                        OnboardingScreen(
                            elderModeEnabled =
                                uiSettingsState.elderModeEnabled,
                            onElderModeChange =
                                uiSettingsViewModel::setElderModeEnabled,
                            onComplete =
                                uiSettingsViewModel::completeOnboarding,
                        )
                    } else if (loginState.isLoggedIn) {
                        TrueShieldNavHost(
                            onLogout = {
                                loginViewModel.logout()
                            },
                            pushNavigationTarget =
                                pendingPushTarget,
                            onPushNavigationConsumed = {
                                consumePushNavigationTarget()
                            },
                            callGuardNavigationRequested =
                                pendingCallGuardNavigation,
                            onCallGuardNavigationConsumed = {
                                consumeCallGuardNavigation()
                            },
                            themeMode =
                                uiSettingsState.themeMode,
                            elderModeEnabled =
                                uiSettingsState.elderModeEnabled,
                            onThemeModeChange =
                                uiSettingsViewModel::setThemeMode,
                            onElderModeChange =
                                uiSettingsViewModel::setElderModeEnabled,
                            highRiskVoiceAlertEnabled =
                                uiSettingsState.highRiskVoiceAlertEnabled,
                            onHighRiskVoiceAlertChange =
                                uiSettingsViewModel::setHighRiskVoiceAlertEnabled,
                            onShowOnboarding =
                                uiSettingsViewModel::showOnboarding,
                        )
                    } else {
                        LoginScreen(
                            state = loginState,
                            onDisplayNameChange =
                                loginViewModel::
                                onDisplayNameChange,
                            onPhoneChange =
                                loginViewModel::
                                onPhoneChange,
                            onEmailChange =
                                loginViewModel::
                                onEmailChange,
                            onPasswordChange =
                                loginViewModel::
                                onPasswordChange,
                            onConfirmPasswordChange =
                                loginViewModel::
                                onConfirmPasswordChange,
                            onResetCodeChange =
                                loginViewModel::
                                onResetCodeChange,
                            onNewPasswordChange =
                                loginViewModel::
                                onNewPasswordChange,
                            onResetConfirmPasswordChange =
                                loginViewModel::
                                onResetConfirmPasswordChange,
                            onTogglePasswordVisibility =
                                loginViewModel::
                                togglePasswordVisibility,
                            onToggleAuthMode =
                                loginViewModel::
                                toggleAuthMode,
                            onForgotPasswordClick =
                                loginViewModel::
                                startPasswordReset,
                            onBackToLoginClick =
                                loginViewModel::
                                backToLogin,
                            onResendResetCodeClick =
                                loginViewModel::
                                resendPasswordResetCode,
                            onSubmit =
                                loginViewModel::submit,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(
        intent: Intent,
    ) {
        super.onNewIntent(intent)
        setIntent(intent)

        pendingPushTarget =
            PushNavigationTarget
                .fromIntent(intent)

        pendingCallGuardNavigation =
            intent.getBooleanExtra(
                EXTRA_OPEN_CALL_GUARD,
                false,
            )
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        notificationPermissionLauncher.launch(
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    private fun consumeCallGuardNavigation() {
        pendingCallGuardNavigation = false
        intent?.removeExtra(EXTRA_OPEN_CALL_GUARD)
    }

    private fun consumePushNavigationTarget() {
        pendingPushTarget = null

        intent?.apply {
            removeExtra(
                PushNavigationTarget.EXTRA_TYPE,
            )
            removeExtra(
                PushNavigationTarget.EXTRA_FAMILY_ID,
            )
            removeExtra(
                PushNavigationTarget.EXTRA_ALERT_ID,
            )
        }
    }

    companion object {
        const val EXTRA_OPEN_CALL_GUARD =
            "open_call_guard"
    }

}
