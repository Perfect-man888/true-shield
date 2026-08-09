package com.trueshield.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trueshield.app.push.PushNavigationTarget
import com.trueshield.app.ui.account.AccountProfileScreen
import com.trueshield.app.ui.account.AccountProfileViewModel
import com.trueshield.app.ui.account.security.AccountSecurityScreen
import com.trueshield.app.ui.account.security.AccountSecurityViewModel
import com.trueshield.app.ui.alert.FamilyAlertDetailScreen
import com.trueshield.app.ui.alert.FamilyAlertDetailViewModel
import com.trueshield.app.ui.alert.FamilyAlertScreen
import com.trueshield.app.ui.alert.FamilyAlertViewModel
import com.trueshield.app.ui.alert.policy.FamilyAlertPolicyScreen
import com.trueshield.app.ui.alert.policy.FamilyAlertPolicyViewModel
import com.trueshield.app.ui.contact.TrustedContactScreen
import com.trueshield.app.ui.contact.TrustedContactViewModel
import com.trueshield.app.ui.callguard.CallGuardScreen
import com.trueshield.app.ui.callguard.CallGuardViewModel
import com.trueshield.app.ui.family.FamilyCenterScreen
import com.trueshield.app.ui.family.FamilyCenterViewModel
import com.trueshield.app.ui.home.HomeScreen
import com.trueshield.app.ui.home.HomeViewModel
import com.trueshield.app.ui.main.FamilyHubScreen
import com.trueshield.app.ui.main.MessageCenterScreen
import com.trueshield.app.ui.main.MessageCenterViewModel
import com.trueshield.app.ui.main.ProfileHubScreen
import com.trueshield.app.ui.main.RiskDetectionHubScreen
import com.trueshield.app.ui.theme.TrueShieldThemeMode
import com.trueshield.app.ui.risk.dashboard.RiskDashboardScreen
import com.trueshield.app.ui.risk.dashboard.RiskDashboardViewModel
import com.trueshield.app.ui.risk.detail.RiskEventDetailScreen
import com.trueshield.app.ui.risk.detail.RiskEventDetailViewModel
import com.trueshield.app.ui.risk.feedback.RiskFeedbackScreen
import com.trueshield.app.ui.risk.feedback.RiskFeedbackViewModel
import com.trueshield.app.ui.risk.history.RiskHistoryScreen
import com.trueshield.app.ui.risk.history.RiskHistoryViewModel
import com.trueshield.app.ui.risk.report.RiskReportScreen
import com.trueshield.app.ui.risk.report.RiskReportViewModel
import com.trueshield.app.ui.risk.statistics.RiskFeedbackStatisticsScreen
import com.trueshield.app.ui.risk.statistics.RiskFeedbackStatisticsViewModel
import com.trueshield.app.ui.risk.image.ImageRiskScreen
import com.trueshield.app.ui.risk.image.ImageRiskViewModel
import com.trueshield.app.ui.risk.text.TextRiskScreen
import com.trueshield.app.ui.risk.text.TextRiskViewModel
import com.trueshield.app.ui.risk.url.UrlRiskScreen
import com.trueshield.app.ui.risk.url.UrlRiskViewModel
import com.trueshield.app.ui.risk.voice.VoiceRiskScreen
import com.trueshield.app.ui.risk.voice.VoiceRiskViewModel
import kotlinx.coroutines.delay

/**
 * 真信盾登录后的主导航系统。
 */
@Composable
fun TrueShieldNavHost(
    onLogout: () -> Unit,
    pushNavigationTarget:
        PushNavigationTarget? = null,
    onPushNavigationConsumed: () -> Unit = {},
    callGuardNavigationRequested: Boolean = false,
    onCallGuardNavigationConsumed: () -> Unit = {},
    themeMode: TrueShieldThemeMode =
        TrueShieldThemeMode.SYSTEM,
    elderModeEnabled: Boolean = false,
    onThemeModeChange: (TrueShieldThemeMode) -> Unit = {},
    onElderModeChange: (Boolean) -> Unit = {},
    highRiskVoiceAlertEnabled: Boolean = true,
    onHighRiskVoiceAlertChange: (Boolean) -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    navController: NavHostController =
        rememberNavController(),
) {
    LaunchedEffect(pushNavigationTarget) {
        val target =
            pushNavigationTarget
                ?: return@LaunchedEffect

        navController.navigate(
            AppRoute
                .FamilyAlertDetail
                .createRoute(
                    familyId = target.familyId,
                    alertId = target.alertId,
                ),
        ) {
            launchSingleTop = true
        }

        onPushNavigationConsumed()
    }

    LaunchedEffect(callGuardNavigationRequested) {
        if (!callGuardNavigationRequested) {
            return@LaunchedEffect
        }

        navController.navigate(
            AppRoute.CallGuard.route,
        ) {
            launchSingleTop = true
        }

        onCallGuardNavigationConsumed()
    }

    val currentBackStackEntry by
        navController.currentBackStackEntryAsState()
    val currentRoute =
        currentBackStackEntry?.destination?.route
    val rootRoutes =
        TrueShieldBottomNavigationItems
            .map { it.route }
            .toSet()

    Column(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    ) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.Home.route,
            modifier = androidx.compose.ui.Modifier.weight(1f),
        ) {

            /** 首页。 */
            composable(
                route = AppRoute.Home.route,
            ) {
                val homeViewModel:
                        HomeViewModel = viewModel()
                val homeState by
                    homeViewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    homeViewModel.refresh()
                }

                LaunchedEffect(homeState.sessionExpired) {
                    if (homeState.sessionExpired) {
                        homeViewModel.consumeSessionExpired()
                        onLogout()
                    }
                }

                HomeScreen(
                    state = homeState,
                    onRefreshClick = homeViewModel::refresh,
                    onCallGuardClick = {
                        navController.navigate(AppRoute.CallGuard.route) {
                            launchSingleTop = true
                        }
                    },
                    onTextRiskClick = {
                        navController.navigate(AppRoute.TextRisk.route) {
                            launchSingleTop = true
                        }
                    },
                    onImageRiskClick = {
                        navController.navigate(AppRoute.ImageRisk.route) {
                            launchSingleTop = true
                        }
                    },
                    onVoiceRiskClick = {
                        navController.navigate(AppRoute.VoiceRisk.route) {
                            launchSingleTop = true
                        }
                    },
                    onUrlRiskClick = {
                        navController.navigate(AppRoute.UrlRisk.route) {
                            launchSingleTop = true
                        }
                    },
                    onRiskHistoryClick = {
                        navController.navigate(AppRoute.RiskHistory.route) {
                            launchSingleTop = true
                        }
                    },
                    onFamilyAlertClick = {
                        navController.navigate(AppRoute.FamilyAlert.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            /** 风险检测聚合页。 */
            composable(
                route = AppRoute.RiskDetectionHub.route,
            ) {
                RiskDetectionHubScreen(
                    onTextRiskClick = {
                        navController.navigate(AppRoute.TextRisk.route)
                    },
                    onImageRiskClick = {
                        navController.navigate(AppRoute.ImageRisk.route)
                    },
                    onUrlRiskClick = {
                        navController.navigate(AppRoute.UrlRisk.route)
                    },
                    onVoiceRiskClick = {
                        navController.navigate(AppRoute.VoiceRisk.route)
                    },
                    onCallGuardClick = {
                        navController.navigate(AppRoute.CallGuard.route)
                    },
                    onRiskHistoryClick = {
                        navController.navigate(AppRoute.RiskHistory.route)
                    },
                    onRiskDashboardClick = {
                        navController.navigate(AppRoute.RiskDashboard.route)
                    },
                    onRiskReportClick = {
                        navController.navigate(AppRoute.RiskReport.route)
                    },
                )
            }

            /** 家庭守护聚合页。 */
            composable(
                route = AppRoute.FamilyHub.route,
            ) {
                FamilyHubScreen(
                    onFamilyCenterClick = {
                        navController.navigate(AppRoute.FamilyCenter.route)
                    },
                    onFamilyAlertClick = {
                        navController.navigate(AppRoute.FamilyAlert.route)
                    },
                    onTrustedContactsClick = {
                        navController.navigate(AppRoute.TrustedContacts.route)
                    },
                )
            }

            /** 消息聚合页。 */
            composable(
                route = AppRoute.MessageCenter.route,
            ) {
                val messageCenterViewModel:
                        MessageCenterViewModel = viewModel()

                val messageCenterState by
                    messageCenterViewModel
                        .uiState
                        .collectAsState()

                LaunchedEffect(Unit) {
                    messageCenterViewModel.refresh()
                }

                MessageCenterScreen(
                    state = messageCenterState,
                    onRefreshClick =
                        messageCenterViewModel::refresh,
                    onFamilyAlertClick = {
                        navController.navigate(AppRoute.FamilyAlert.route)
                    },
                    onFamilyCenterClick = {
                        navController.navigate(AppRoute.FamilyCenter.route)
                    },
                    onRiskHistoryClick = {
                        navController.navigate(AppRoute.RiskHistory.route)
                    },
                    onSessionExpired = {
                        messageCenterViewModel.consumeSessionExpired()
                        onLogout()
                    },
                )
            }

            /** 我的与显示设置聚合页。 */
            composable(
                route = AppRoute.ProfileHub.route,
            ) {
                ProfileHubScreen(
                    themeMode = themeMode,
                    elderModeEnabled = elderModeEnabled,
                    onThemeModeChange = onThemeModeChange,
                    onElderModeChange = onElderModeChange,
                    highRiskVoiceAlertEnabled = highRiskVoiceAlertEnabled,
                    onHighRiskVoiceAlertChange = onHighRiskVoiceAlertChange,
                    onOnboardingClick = onShowOnboarding,
                    onAccountProfileClick = {
                        navController.navigate(AppRoute.AccountProfile.route)
                    },
                    onRiskDashboardClick = {
                        navController.navigate(AppRoute.RiskDashboard.route)
                    },
                    onRiskReportClick = {
                        navController.navigate(AppRoute.RiskReport.route)
                    },
                    onRiskFeedbackStatisticsClick = {
                        navController.navigate(
                            AppRoute.RiskFeedbackStatistics.route,
                        )
                    },
                    onLogoutClick = onLogout,
                )
            }

        /**
         * 号码风险筛查、通话护航提醒和一键家庭求助。
         */
        composable(
            route = AppRoute.CallGuard.route,
        ) {
            val callGuardViewModel:
                    CallGuardViewModel = viewModel()

            val callGuardState by
            callGuardViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(Unit) {
                callGuardViewModel.loadInitial()
            }

            CallGuardScreen(
                state = callGuardState,
                onBackClick = {
                    navController.popBackStack()
                },
                onRefreshRoleStatus =
                    callGuardViewModel::refreshRoleStatus,
                onRefreshFamilies =
                    callGuardViewModel::loadFamilies,
                onSelectFamily =
                    callGuardViewModel::selectFamily,
                onPhoneNumberChange =
                    callGuardViewModel::onPhoneNumberChange,
                onAnalyzeNumber =
                    callGuardViewModel::analyzeNumber,
                  onRequestFamilyHelp =
                      callGuardViewModel::requestFamilyHelp,
                  onSyncRules = callGuardViewModel::syncRules,
                  onSilenceHighRiskChange =
                      callGuardViewModel::setSilenceHighRisk,
                  onBlockConfirmedRiskChange =
                      callGuardViewModel::setBlockConfirmedRisk,
                  onSubmitReport =
                      callGuardViewModel::submitNumberReport,
                onSessionExpired = {
                    callGuardViewModel
                        .consumeSessionExpired()
                    onLogout()
                },
            )
        }

        /**
         * 当前登录用户个人中心。
         */
        composable(
            route = AppRoute.AccountProfile.route,
        ) {
            val accountProfileViewModel:
                    AccountProfileViewModel = viewModel()

            val accountProfileState by
            accountProfileViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(Unit) {
                accountProfileViewModel.loadProfile()
            }

            LaunchedEffect(
                accountProfileState.sessionExpired,
            ) {
                if (
                    accountProfileState
                        .sessionExpired
                ) {
                    accountProfileViewModel
                        .consumeSessionExpired()

                    onLogout()
                }
            }

            AccountProfileScreen(
                state = accountProfileState,
                onDisplayNameChange =
                    accountProfileViewModel::
                    onDisplayNameChange,
                onPhoneChange =
                    accountProfileViewModel::
                    onPhoneChange,
                onSaveClick =
                    accountProfileViewModel::
                    saveProfile,
                onResetClick =
                    accountProfileViewModel::
                    resetDraft,
                onRefreshClick =
                    accountProfileViewModel::
                    loadProfile,
                onSecurityClick = {
                    navController.navigate(
                        AppRoute.AccountSecurity.route,
                    ) {
                        launchSingleTop = true
                    }
                },
                onLogoutClick = onLogout,
                onBackClick = {
                    navController.popBackStack()
                },
            )
        }

        /**
         * 当前登录用户账户安全页面。
         */
        composable(
            route = AppRoute.AccountSecurity.route,
        ) {
            val accountSecurityViewModel:
                    AccountSecurityViewModel = viewModel()

            val accountSecurityState by
            accountSecurityViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(
                accountSecurityState.sessionExpired,
            ) {
                if (accountSecurityState.sessionExpired) {
                    accountSecurityViewModel
                        .consumeSessionExpired()
                    onLogout()
                }
            }

            LaunchedEffect(
                accountSecurityState.passwordChanged,
            ) {
                if (accountSecurityState.passwordChanged) {
                    delay(1_000)
                    onLogout()
                }
            }

            AccountSecurityScreen(
                state = accountSecurityState,
                onCurrentPasswordChange =
                    accountSecurityViewModel::
                    onCurrentPasswordChange,
                onNewPasswordChange =
                    accountSecurityViewModel::
                    onNewPasswordChange,
                onConfirmPasswordChange =
                    accountSecurityViewModel::
                    onConfirmPasswordChange,
                onToggleCurrentPasswordVisibility =
                    accountSecurityViewModel::
                    toggleCurrentPasswordVisibility,
                onToggleNewPasswordVisibility =
                    accountSecurityViewModel::
                    toggleNewPasswordVisibility,
                onToggleConfirmPasswordVisibility =
                    accountSecurityViewModel::
                    toggleConfirmPasswordVisibility,
                onSubmitClick =
                    accountSecurityViewModel::
                    changePassword,
                onReloginClick = onLogout,
                onBackClick = {
                    navController.popBackStack()
                },
            )
        }

        /**
         * 文本风险检测页面。
         */
        composable(
            route = AppRoute.TextRisk.route,
        ) {
            val textRiskViewModel:
                    TextRiskViewModel = viewModel()

            val textRiskState by
            textRiskViewModel
                .uiState
                .collectAsState()

            TextRiskScreen(
                state = textRiskState,
                onTextChange =
                    textRiskViewModel::onTextChange,
                onAnalyzeClick =
                    textRiskViewModel::analyzeText,
                onClearResultClick =
                    textRiskViewModel::clearResult,
                onClearAllClick =
                    textRiskViewModel::clearAll,
                onImmediateHelpClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail.createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onViewDetailClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail
                            .createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    textRiskViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 图片风险检测页面。
         */
        composable(
            route = AppRoute.ImageRisk.route,
        ) {
            val imageRiskViewModel:
                    ImageRiskViewModel = viewModel()

            val imageRiskState by
            imageRiskViewModel
                .uiState
                .collectAsState()

            ImageRiskScreen(
                state = imageRiskState,
                onImageSelected =
                    imageRiskViewModel::onImageSelected,
                onAnalyzeClick =
                    imageRiskViewModel::analyzeImage,
                onClearResultClick =
                    imageRiskViewModel::clearResult,
                onClearAllClick =
                    imageRiskViewModel::clearAll,
                onImmediateHelpClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail.createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onViewDetailClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail
                            .createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onFeedbackClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskFeedback
                            .createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    imageRiskViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 语音转写风险检测页面。
         */
        composable(
            route = AppRoute.VoiceRisk.route,
        ) {
            val voiceRiskViewModel:
                    VoiceRiskViewModel = viewModel()

            val voiceRiskState by
            voiceRiskViewModel
                .uiState
                .collectAsState()

            VoiceRiskScreen(
                state = voiceRiskState,
                onTranscriptChange =
                    voiceRiskViewModel::
                    onTranscriptChange,
                onRecordingStarted =
                    voiceRiskViewModel::
                    onRecordingStarted,
                onRecordingDurationChanged =
                    voiceRiskViewModel::
                    onRecordingDurationChanged,
                onRecordingStopped =
                    voiceRiskViewModel::
                    onRecordingStopped,
                onRecordingError =
                    voiceRiskViewModel::
                    onRecordingError,
                onUploadAndAnalyzeClick =
                    voiceRiskViewModel::
                    uploadAndAnalyze,
                onReanalyzeCorrectionClick =
                    voiceRiskViewModel::
                    reanalyzeCorrectedTranscript,
                onClearResultClick =
                    voiceRiskViewModel::clearResult,
                onClearAllClick =
                    voiceRiskViewModel::clearAll,
                onImmediateHelpClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail.createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onViewDetailClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail
                            .createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    voiceRiskViewModel
                        .consumeSessionExpired()
                    onLogout()
                },
            )
        }

        /**
         * URL 风险检测页面。
         */
        composable(
            route = AppRoute.UrlRisk.route,
        ) {
            val urlRiskViewModel:
                    UrlRiskViewModel = viewModel()

            val urlRiskState by
            urlRiskViewModel
                .uiState
                .collectAsState()

            UrlRiskScreen(
                state = urlRiskState,
                onUrlChange =
                    urlRiskViewModel::onUrlChange,
                onResolveRedirectsChange =
                    urlRiskViewModel::
                    onResolveRedirectsChange,
                onAnalyzeClick =
                    urlRiskViewModel::analyzeUrl,
                onClearResultClick =
                    urlRiskViewModel::clearResult,
                onClearAllClick =
                    urlRiskViewModel::clearAll,
                onImmediateHelpClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail.createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onViewDetailClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail
                            .createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    urlRiskViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 风险历史记录页面。
         */
        composable(
            route = AppRoute.RiskHistory.route,
        ) {
            val historyViewModel:
                    RiskHistoryViewModel = viewModel()

            val historyState by
            historyViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(Unit) {
                historyViewModel.loadInitial()
            }

            RiskHistoryScreen(
                state = historyState,
                onRefreshClick =
                    historyViewModel::refresh,
                onLoadMoreClick =
                    historyViewModel::loadMore,
                onRetryClick =
                    historyViewModel::retry,
                onEventClick = { eventId ->
                    navController.navigate(
                        AppRoute.RiskDetail
                            .createRoute(eventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    historyViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 个人与家庭风险统计仪表盘。
         */
        composable(
            route = AppRoute.RiskDashboard.route,
        ) {
            val dashboardViewModel:
                    RiskDashboardViewModel =
                viewModel()

            val dashboardState by
            dashboardViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(Unit) {
                dashboardViewModel
                    .loadInitial()
            }

            LaunchedEffect(
                dashboardState.sessionExpired,
            ) {
                if (dashboardState.sessionExpired) {
                    dashboardViewModel
                        .consumeSessionExpired()
                    onLogout()
                }
            }

            RiskDashboardScreen(
                state = dashboardState,
                onSelectPersonal =
                    dashboardViewModel::
                    selectPersonalScope,
                onSelectFamily =
                    dashboardViewModel::
                    selectFamily,
                onSelectPeriodDays =
                    dashboardViewModel::
                    selectPeriodDays,
                onRefreshClick =
                    dashboardViewModel::refresh,
                onRetryClick =
                    dashboardViewModel::retry,
                onBackClick = {
                    navController.popBackStack()
                },
            )
        }

        /**
         * 个人与家庭风险报告及 PDF 导出页面。
         */
        composable(
            route = AppRoute.RiskReport.route,
        ) {
            val reportViewModel:
                    RiskReportViewModel =
                viewModel()

            val reportState by
            reportViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(Unit) {
                reportViewModel.loadInitial()
            }

            LaunchedEffect(
                reportState.sessionExpired,
            ) {
                if (reportState.sessionExpired) {
                    reportViewModel
                        .consumeSessionExpired()
                    onLogout()
                }
            }

            RiskReportScreen(
                state = reportState,
                onSelectPersonal =
                    reportViewModel::
                    selectPersonalScope,
                onSelectFamily =
                    reportViewModel::selectFamily,
                onSelectPeriodDays =
                    reportViewModel::
                    selectPeriodDays,
                onRefreshClick =
                    reportViewModel::refresh,
                onExportPdfClick =
                    reportViewModel::exportPdf,
                onBackClick = {
                    navController.popBackStack()
                },
            )
        }

        /**
         * 风险反馈统计页面。
         */
        composable(
            route =
                AppRoute
                    .RiskFeedbackStatistics
                    .route,
        ) {
            val statisticsViewModel:
                    RiskFeedbackStatisticsViewModel =
                viewModel()

            val statisticsState by
            statisticsViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(Unit) {
                statisticsViewModel
                    .loadStatistics()
            }

            RiskFeedbackStatisticsScreen(
                state = statisticsState,
                onRefreshClick =
                    statisticsViewModel::refresh,
                onRetryClick =
                    statisticsViewModel::retry,
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    statisticsViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 风险事件详情页面。
         */
        composable(
            route = AppRoute.RiskDetail.route,
            arguments = listOf(
                navArgument(
                    AppRoute.RiskDetail
                        .EVENT_ID_ARGUMENT,
                ) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->

            val eventId =
                backStackEntry
                    .arguments
                    ?.getString(
                        AppRoute.RiskDetail
                            .EVENT_ID_ARGUMENT,
                    )
                    .orEmpty()

            val detailViewModel:
                    RiskEventDetailViewModel = viewModel()

            val detailState by
            detailViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(eventId) {
                if (eventId.isNotBlank()) {
                    detailViewModel.loadEvent(
                        eventId = eventId,
                    )
                }
            }

            RiskEventDetailScreen(
                state = detailState,
                onFeedbackClick = { currentEventId ->
                    navController.navigate(
                        AppRoute.RiskFeedback
                            .createRoute(currentEventId),
                    ) {
                        launchSingleTop = true
                    }
                },
                onFamilySelected =
                    detailViewModel::selectFamily,
                onCreateFamilyAlertClick =
                    detailViewModel::createFamilyAlert,
                onViewCreatedAlertClick = {
                        familyId,
                        alertId ->

                    navController.navigate(
                        AppRoute
                            .FamilyAlertDetail
                            .createRoute(
                                familyId = familyId,
                                alertId = alertId,
                            ),
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenFamilyCenterClick = {
                    navController.navigate(
                        AppRoute.FamilyCenter.route,
                    ) {
                        launchSingleTop = true
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onRetryClick =
                    detailViewModel::retry,
                onSessionExpired = {
                    detailViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 风险反馈页面。
         */
        composable(
            route = AppRoute.RiskFeedback.route,
            arguments = listOf(
                navArgument(
                    AppRoute.RiskFeedback
                        .EVENT_ID_ARGUMENT,
                ) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->

            val eventId =
                backStackEntry
                    .arguments
                    ?.getString(
                        AppRoute.RiskFeedback
                            .EVENT_ID_ARGUMENT,
                    )
                    .orEmpty()

            val feedbackViewModel:
                    RiskFeedbackViewModel = viewModel()

            val feedbackState by
            feedbackViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(eventId) {
                if (eventId.isNotBlank()) {
                    feedbackViewModel.loadEvent(
                        eventId = eventId,
                    )
                }
            }

            RiskFeedbackScreen(
                state = feedbackState,
                onFeedbackTypeChange =
                    feedbackViewModel::
                    onFeedbackTypeChange,
                onExpectedRiskLevelChange =
                    feedbackViewModel::
                    onExpectedRiskLevelChange,
                onOcrTextAccurateChange =
                    feedbackViewModel::
                    onOcrTextAccurateChange,
                onCorrectedTextChange =
                    feedbackViewModel::
                    onCorrectedTextChange,
                onCommentChange =
                    feedbackViewModel::
                    onCommentChange,
                onSubmitClick =
                    feedbackViewModel::
                    submitFeedback,
                onReanalyzeClick =
                    feedbackViewModel::
                    reanalyzeCorrected,
                onRetryClick =
                    feedbackViewModel::retry,
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    feedbackViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 家庭中心页面。
         */
        composable(
            route = AppRoute.FamilyCenter.route,
        ) {
            val familyCenterViewModel:
                    FamilyCenterViewModel = viewModel()

            val familyCenterState by
            familyCenterViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(Unit) {
                familyCenterViewModel
                    .loadCenter()
            }

            FamilyCenterScreen(
                state = familyCenterState,
                onFamilyNameChange =
                    familyCenterViewModel::
                    onFamilyNameChange,
                onCreateFamilyClick =
                    familyCenterViewModel::
                    createFamily,
                onRefreshClick =
                    familyCenterViewModel::
                    refresh,
                onFamilyClick =
                    familyCenterViewModel::
                    loadFamilyMembers,
                onInviteeEmailChange =
                    familyCenterViewModel::
                    onInviteeEmailChange,
                onSendInvitationClick =
                    familyCenterViewModel::
                    sendInvitation,
                onAcceptInvitationClick =
                    familyCenterViewModel::
                    acceptInvitation,
                onDeclineInvitationClick =
                    familyCenterViewModel::
                    declineInvitation,
                onCloseMembersClick =
                    familyCenterViewModel::
                    clearSelectedFamily,
                onRetryClick =
                    familyCenterViewModel::
                    retry,
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    familyCenterViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 可信联系人页面。
         *
         * 注意：这里只保留一次，不能重复声明同一路由。
         */
        composable(
            route = AppRoute.TrustedContacts.route,
        ) {
            val trustedContactViewModel:
                    TrustedContactViewModel = viewModel()

            val trustedContactState by
            trustedContactViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(Unit) {
                trustedContactViewModel
                    .loadInitial()
            }

            TrustedContactScreen(
                state = trustedContactState,
                onFamilyClick =
                    trustedContactViewModel::
                    selectFamily,
                onRefreshClick =
                    trustedContactViewModel::
                    refresh,
                onStartCreateClick =
                    trustedContactViewModel::
                    startCreate,
                onStartEditClick =
                    trustedContactViewModel::
                    startEdit,
                onCancelEditClick =
                    trustedContactViewModel::
                    cancelEdit,
                onDisplayNameChange =
                    trustedContactViewModel::
                    onDisplayNameChange,
                onRelationshipChange =
                    trustedContactViewModel::
                    onRelationshipChange,
                onPhoneChange =
                    trustedContactViewModel::
                    onPhoneChange,
                onEmailChange =
                    trustedContactViewModel::
                    onEmailChange,
                onPreferredChannelChange =
                    trustedContactViewModel::
                    onPreferredChannelChange,
                onPriorityChange =
                    trustedContactViewModel::
                    onPriorityChange,
                onCanReceiveAlertsChange =
                    trustedContactViewModel::
                    onCanReceiveAlertsChange,
                onSaveContactClick =
                    trustedContactViewModel::
                    saveContact,
                onToggleAlertsClick =
                    trustedContactViewModel::
                    toggleAlerts,
                onDisableContactClick =
                    trustedContactViewModel::
                    disableContact,
                onRetryClick =
                    trustedContactViewModel::
                    retry,
                onBackClick = {
                    navController.popBackStack()
                },
                onSessionExpired = {
                    trustedContactViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }

        /**
         * 家庭告警列表页面。
         */
        composable(
            route = AppRoute.FamilyAlert.route,
        ) {
            val familyAlertViewModel:
                    FamilyAlertViewModel =
                viewModel()

            val familyAlertState by
            familyAlertViewModel
                .uiState
                .collectAsState()

            FamilyAlertScreen(
                state = familyAlertState,

                onBackClick = {
                    navController.popBackStack()
                },

                onRefreshClick = {
                    familyAlertViewModel.refresh()
                },

                onRetryClick = {
                    familyAlertViewModel.retry()
                },

                onFamilySelected = { familyId ->
                    familyAlertViewModel.selectFamily(
                        familyId = familyId,
                    )
                },

                onFilterSelected = { filter ->
                    familyAlertViewModel.selectFilter(
                        filter = filter,
                    )
                },

                onAlertClick = { familyId, alertId ->
                    navController.navigate(
                        AppRoute
                            .FamilyAlertDetail
                            .createRoute(
                                familyId = familyId,
                                alertId = alertId,
                            ),
                    ) {
                        launchSingleTop = true
                    }
                },

                onPolicyClick = {
                    /*
                     * selectedFamilyId 是 String?，
                     * 使用 orEmpty() 转换为非空 String。
                     */
                    val familyId =
                        familyAlertState
                            .selectedFamilyId
                            .orEmpty()

                    if (familyId.isNotBlank()) {
                        navController.navigate(
                            AppRoute
                                .FamilyAlertPolicy
                                .createRoute(
                                    familyId = familyId,
                                ),
                        ) {
                            launchSingleTop = true
                        }
                    }
                },

                onSessionExpired = {
                    familyAlertViewModel
                        .consumeSessionExpired()

                    onLogout()
                },
            )
        }


        /**
         * 家庭告警详情页面。
         */
        composable(
            route =
                AppRoute
                    .FamilyAlertDetail
                    .route,
            arguments = listOf(
                navArgument(
                    name = "familyId",
                ) {
                    type = NavType.StringType
                },
                navArgument(
                    name = "alertId",
                ) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->

            val familyId =
                backStackEntry
                    .arguments
                    ?.getString("familyId")
                    .orEmpty()

            val alertId =
                backStackEntry
                    .arguments
                    ?.getString("alertId")
                    .orEmpty()

            val detailViewModel:
                    FamilyAlertDetailViewModel = viewModel()

            val detailState by
            detailViewModel
                .uiState
                .collectAsState()

            LaunchedEffect(
                familyId,
                alertId,
            ) {
                if (
                    familyId.isNotBlank() &&
                    alertId.isNotBlank()
                ) {
                    detailViewModel.loadAlert(
                        familyId = familyId,
                        alertId = alertId,
                    )
                }
            }

            LaunchedEffect(
                detailState.sessionExpired,
            ) {
                if (detailState.sessionExpired) {
                    detailViewModel
                        .consumeSessionExpired()

                    onLogout()
                }
            }

            FamilyAlertDetailScreen(
                state = detailState,

                onBackClick = {
                    navController.popBackStack()
                },

                onRefreshClick =
                    detailViewModel::refresh,

                /**
                 * 页面加载失败时重新加载。
                 *
                 * 这里直接复用 refresh，
                 * 不要求 ViewModel 另外实现 retry 方法。
                 */
                onRetry =
                    detailViewModel::refresh,

                onAcknowledgeClick =
                    detailViewModel::acknowledgeAlert,

                onDispatchClick =
                    detailViewModel::dispatchAlert,

                onRetryFailedClick =
                    detailViewModel::retryFailedDeliveries,

                onResolutionNoteChange =
                    detailViewModel::onResolutionNoteChange,

                onResolveClick =
                    detailViewModel::resolveAlert,
            )
        }

        /**
         * 家庭告警自动触发策略页面。
         */
        composable(
            route =
                AppRoute
                    .FamilyAlertPolicy
                    .route,
            arguments = listOf(
                navArgument(
                    AppRoute
                        .FamilyAlertPolicy
                        .FAMILY_ID_ARGUMENT,
                ) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->

            val familyId =
                backStackEntry
                    .arguments
                    ?.getString(
                        AppRoute
                            .FamilyAlertPolicy
                            .FAMILY_ID_ARGUMENT,
                    )
                    .orEmpty()

            val policyViewModel:
                    FamilyAlertPolicyViewModel =
                viewModel()

            val policyState by
            policyViewModel
                .uiState
                .collectAsState()

            /*
             * 进入页面后读取当前家庭策略。
             */
            LaunchedEffect(familyId) {
                if (familyId.isNotBlank()) {
                    policyViewModel.loadPolicy(
                        familyId = familyId,
                    )
                }
            }

            /*
             * Token 过期后退出登录。
             */
            LaunchedEffect(
                policyState.sessionExpired,
            ) {
                if (policyState.sessionExpired) {
                    policyViewModel
                        .consumeSessionExpired()

                    onLogout()
                }
            }

            FamilyAlertPolicyScreen(
                familyId = familyId,
                state = policyState,

                onBackClick = {
                    navController.popBackStack()
                },

                /*
                 * 页面主动要求读取指定家庭策略。
                 */
                onLoadPolicy = {
                        requestedFamilyId ->

                    policyViewModel.loadPolicy(
                        familyId =
                            requestedFamilyId,
                    )
                },

                /*
                 * 刷新当前家庭策略。
                 */
                onRefreshClick =
                    policyViewModel::
                    refreshPolicy,

                /*
                 * 是否自动创建家庭告警。
                 */
                onAutoCreateEnabledChange =
                    policyViewModel::
                    setAutoCreateEnabled,

                /*
                 * 是否自动向接收人发送告警。
                 */
                onAutoDispatchEnabledChange =
                    policyViewModel::
                    setAutoDispatchEnabled,

                /*
                 * 修改自动创建告警所需的最低风险等级。
                 */
                onMinimumRiskLevelChange =
                    policyViewModel::
                    setMinimumRiskLevel,

                /*
                 * 选择或取消文本、图片、URL来源。
                 */
                onSourceTypeToggle =
                    policyViewModel::
                    toggleSourceType,

                /*
                 * 修改最多接收人数。
                 */
                onMaxRecipientsChange =
                    policyViewModel::
                    setMaxRecipientsText,

                /*
                 * 保存告警策略。
                 */
                onSaveClick =
                    policyViewModel::
                    savePolicy,

                /*
                 * 将页面草稿恢复为服务器返回的数据。
                 */
                onResetClick =
                    policyViewModel::
                    resetDraft,
            )
        }
        }

        if (currentRoute in rootRoutes) {
            TrueShieldBottomNavigation(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(AppRoute.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
