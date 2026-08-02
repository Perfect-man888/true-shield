package com.trueshield.app.ui.callguard

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeResponse
import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.ui.onboarding.FirstUseGuideDialog

/**
 * 号码风险筛查、通话护航提醒和一键家庭求助页面。
 */
@Composable
fun CallGuardScreen(
    state: CallGuardUiState,
    onBackClick: () -> Unit,
    onRefreshRoleStatus: () -> Unit,
    onRefreshFamilies: () -> Unit,
    onSelectFamily: (String) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onAnalyzeNumber: () -> Unit,
    onRequestFamilyHelp: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    FirstUseGuideDialog(
        guideKey = "call_guard_guide_v1",
        title = "通话安全护航",
        description = "启用系统通话筛查角色后，真信盾可在陌生来电时提供风险提醒。",
        steps = listOf(
            "先授权真信盾成为系统通话筛查应用",
            "选择需要通知的家庭",
            "遇到可疑来电时先挂断，再通过官方渠道核验",
        ),
    )

    val context = LocalContext.current
    val roleLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            onRefreshRoleStatus()
        }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 20.dp,
                vertical = 34.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBackClick) {
                Text("返回")
            }

            Text(
                text = "通话安全护航",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        SystemRoleCard(
            state = state,
            onEnableClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager =
                        context.getSystemService(
                            RoleManager::class.java,
                        )
                    if (
                        roleManager?.isRoleAvailable(
                            RoleManager.ROLE_CALL_SCREENING,
                        ) == true
                    ) {
                        roleLauncher.launch(
                            roleManager.createRequestRoleIntent(
                                RoleManager.ROLE_CALL_SCREENING,
                            ),
                        )
                    }
                }
            },
            onRefreshClick = onRefreshRoleStatus,
        )

        PrivacyCard()

        FamilySelectionCard(
            state = state,
            onRefreshFamilies = onRefreshFamilies,
            onSelectFamily = onSelectFamily,
        )

        ManualNumberCard(
            state = state,
            onPhoneNumberChange = onPhoneNumberChange,
            onAnalyzeNumber = onAnalyzeNumber,
        )

        state.analyzeResult?.let { result ->
            NumberResultCard(
                result = result,
                selectedFamilyName = state.selectedFamilyName,
                isSendingHelp = state.isSendingHelp,
                helpSent = state.helpSent,
                onRequestFamilyHelp = onRequestFamilyHelp,
            )
        }

        state.errorMessage?.let { message ->
            MessageCard(
                message = message,
                isError = true,
            )
        }

        state.successMessage?.let { message ->
            MessageCard(
                message = message,
                isError = false,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SystemRoleCard(
    state: CallGuardUiState,
    onEnableClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "系统号码筛查权限",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )

            val statusText = when {
                !state.roleSupported ->
                    "当前 Android 版本不支持系统通话筛查角色。正式使用建议 Android 10 及以上。"

                !state.roleAvailable ->
                    "当前设备未提供通话筛查角色，可能是模拟器或系统电话组件不完整。"

                state.roleHeld ->
                    "已启用。陌生来电到达时，真信盾会即时筛查号码并显示护航提醒。"

                else ->
                    "尚未启用。需要由你在系统授权页面中将真信盾设为号码筛查应用。"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
            )

            if (
                state.roleSupported &&
                state.roleAvailable &&
                !state.roleHeld
            ) {
                Button(
                    onClick = onEnableClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("开启系统号码筛查")
                }
            }

            OutlinedButton(
                onClick = onRefreshClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("刷新权限状态")
            }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "隐私保护原则",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("• 不录制或监听通话音频。")
            Text("• 不申请读取完整通话记录和系统通讯录。")
            Text("• 当前号码只用于即时筛查，不保存为通话历史。")
            Text("• 只有你主动点击家庭求助时，才保存脱敏号码和必要风险证据。")
        }
    }
}

@Composable
private fun FamilySelectionCard(
    state: CallGuardUiState,
    onRefreshFamilies: () -> Unit,
    onSelectFamily: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "一键求助家庭",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "高风险通话时，系统通知中的“一键家庭求助”会通知这里选中的家庭。",
            )

            if (state.isLoadingFamilies) {
                CircularProgressIndicator()
            } else if (state.families.isEmpty()) {
                Text("当前还没有家庭，请先到“家庭中心”创建或加入家庭。")
            } else {
                state.families.forEach { family ->
                    FamilyChoiceButton(
                        family = family,
                        selected =
                            family.id == state.selectedFamilyId,
                        onClick = {
                            onSelectFamily(family.id)
                        },
                    )
                }
            }

            OutlinedButton(
                onClick = onRefreshFamilies,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoadingFamilies,
            ) {
                Text("刷新家庭列表")
            }
        }
    }
}

@Composable
private fun FamilyChoiceButton(
    family: FamilyDto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("${family.name}（已选择）")
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(family.name)
        }
    }
}

@Composable
private fun ManualNumberCard(
    state: CallGuardUiState,
    onPhoneNumberChange: (String) -> Unit,
    onAnalyzeNumber: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "手动号码筛查",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "用于测试号码规则。开发测试高风险号码：+8612345678901",
            )

            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = onPhoneNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("电话号码")
                },
                singleLine = true,
            )

            Button(
                onClick = onAnalyzeNumber,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isAnalyzing,
            ) {
                if (state.isAnalyzing) {
                    CircularProgressIndicator()
                } else {
                    Text("筛查号码风险")
                }
            }
        }
    }
}

@Composable
private fun NumberResultCard(
    result: CallNumberAnalyzeResponse,
    selectedFamilyName: String?,
    isSendingHelp: Boolean,
    helpSent: Boolean,
    onRequestFamilyHelp: () -> Unit,
) {
    val levelText = when (result.riskLevel) {
        "high" -> "高风险"
        "medium" -> "需谨慎"
        else -> "低风险"
    }

    val containerColor = when (result.riskLevel) {
        "high" -> MaterialTheme.colorScheme.errorContainer
        "medium" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "筛查结果：$levelText",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("号码：${result.maskedNumber}")
            Text("风险分：${result.score}/100")
            Text(result.summary)

            if (result.isTrustedContact) {
                Text(
                    text = "可信联系人：${result.trustedContactName.orEmpty()}",
                    fontWeight = FontWeight.Bold,
                )
            }

            if (result.signals.isNotEmpty()) {
                Text(
                    text = "风险证据",
                    fontWeight = FontWeight.Bold,
                )
                result.signals.forEach { signal ->
                    Text("• ${signal.title}：${signal.explanation}")
                }
            }

            Button(
                onClick = onRequestFamilyHelp,
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    !isSendingHelp &&
                        !helpSent &&
                        !selectedFamilyName.isNullOrBlank(),
            ) {
                if (isSendingHelp) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        when {
                            helpSent -> "家庭求助已发送"
                            selectedFamilyName.isNullOrBlank() ->
                                "请先选择求助家庭"
                            else ->
                                "一键向“$selectedFamilyName”求助"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    isError: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
        )
    }
}
