package com.trueshield.app.ui.risk.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.risk.RiskEvidenceDto
import com.trueshield.app.data.model.risk.RiskEventDetailResponse
import com.trueshield.app.ui.risk.common.formatAiConfidence
import com.trueshield.app.ui.risk.common.formatRiskScore
import com.trueshield.app.ui.risk.common.riskAnalysisEngineName
import com.trueshield.app.ui.risk.common.riskLevelDisplayName
import com.google.gson.JsonElement

/**
 * 通用风险事件详情页面。
 *
 * 后续文本、图片、URL、历史记录都复用该页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskEventDetailScreen(
    state: RiskEventDetailUiState,
    onFeedbackClick: (String) -> Unit,
    onFamilySelected: (String) -> Unit,
    onCreateFamilyAlertClick: () -> Unit,
    onViewCreatedAlertClick: (
        familyId: String,
        alertId: String,
    ) -> Unit,
    onOpenFamilyCenterClick: () -> Unit,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    LaunchedEffect(
        state.sessionExpired,
    ) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "风险事件详情",
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBackClick,
                    ) {
                        Text(
                            text = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    horizontal = 20.dp,
                    vertical = 16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading -> {
                    item {
                        LoadingContent()
                    }
                }

                state.errorMessage != null -> {
                    item {
                        ErrorContent(
                            message =
                                state.errorMessage,
                            onRetryClick =
                                onRetryClick,
                        )
                    }
                }

                state.detail != null -> {
                    val detail =
                        state.detail

                    item {
                        RiskOverviewCard(
                            detail = detail,
                        )
                    }

                    if (
                        detail.riskLevel.equals(
                            other = "high",
                            ignoreCase = true,
                        )
                    ) {
                        item {
                            FamilyAlertActionCard(
                                state = state,
                                onFamilySelected =
                                    onFamilySelected,
                                onCreateFamilyAlertClick =
                                    onCreateFamilyAlertClick,
                                onViewCreatedAlertClick =
                                    onViewCreatedAlertClick,
                                onOpenFamilyCenterClick =
                                    onOpenFamilyCenterClick,
                            )
                        }
                    }

                    item {
                        InformationCard(
                            title = "检测摘要",
                            content = detail.summary,
                        )
                    }

                    item {
                        InformationCard(
                            title = "原始检测内容",
                            content =
                                detail.sourceText,
                        )
                    }

                    formatSourceMetadata(
                        metadata = detail.sourceMetadata,
                    )?.let { metadataText ->
                        item {
                            InformationCard(
                                title = "附加信息",
                                content = metadataText,
                            )
                        }
                    }

                    if (
                        detail.evidence.isNotEmpty()
                    ) {
                        item {
                            Text(
                                text = "风险证据",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge,
                                fontWeight =
                                    FontWeight.Bold,
                            )
                        }

                        detail.evidence
                            .forEachIndexed {
                                    index,
                                    evidence ->

                                item {
                                    EvidenceCard(
                                        index = index,
                                        evidence =
                                            evidence,
                                    )
                                }
                            }
                    } else {
                        item {
                            InformationCard(
                                title = "风险证据",
                                content =
                                    "当前事件没有命中明确的风险规则。",
                            )
                        }
                    }

                    if (
                        detail.actions.isNotEmpty()
                    ) {
                        item {
                            ActionsCard(
                                actions =
                                    detail.actions,
                            )
                        }
                    }

                    item {
                        EventInformationCard(
                            detail = detail,
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                onFeedbackClick(
                                    detail.eventId,
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text =
                                    if (
                                        detail.sourceType
                                            .lowercase() == "image"
                                    ) {
                                        "风险反馈与 OCR 修正"
                                    } else {
                                        "提交或修改风险反馈"
                                    },
                            )
                        }
                    }
                }

                else -> {
                    item {
                        InformationCard(
                            title = "暂无数据",
                            content =
                                "没有可以显示的风险事件详情。",
                        )
                    }
                }
            }
        }
    }
}

/**
 * 加载状态。
 */
@Composable
private fun LoadingContent() {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
    ) {
        Spacer(
            modifier =
                Modifier.height(80.dp),
        )

        CircularProgressIndicator(
            modifier =
                Modifier.size(42.dp),
        )

        Spacer(
            modifier =
                Modifier.height(16.dp),
        )

        Text(
            text = "正在读取风险事件详情……",
        )
    }
}

/**
 * 错误状态。
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .errorContainer,
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(20.dp),
        ) {
            Text(
                text = "读取失败",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.Bold,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text = message,
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp),
            )

            Button(
                onClick = onRetryClick,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "重新加载",
                )
            }
        }
    }
}

/**
 * 风险等级和分数。
 */
@Composable
private fun RiskOverviewCard(
    detail: RiskEventDetailResponse,
) {
    val levelName =
        when (
            detail.riskLevel.lowercase()
        ) {
            "high" -> "高风险"
            "medium" -> "中风险"
            "low" -> "低风险"
            else -> detail.riskLevel
        }

    val containerColor =
        when (
            detail.riskLevel.lowercase()
        ) {
            "high" ->
                MaterialTheme
                    .colorScheme
                    .errorContainer

            "medium" ->
                MaterialTheme
                    .colorScheme
                    .secondaryContainer

            else ->
                MaterialTheme
                    .colorScheme
                    .primaryContainer
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    containerColor,
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(20.dp),
        ) {
            Text(
                text = "风险评估",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text = levelName,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold,
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        "风险分数：${detail.score}/100",
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                )

                Text(
                    text =
                        "证据 ${detail.evidence.size} 条",
                )
            }
        }
    }
}

/**
 * 普通信息卡片。
 */
@Composable
private fun InformationCard(
    title: String,
    content: String,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text = content,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )
        }
    }
}

/**
 * 单条风险证据。
 */
@Composable
private fun EvidenceCard(
    index: Int,
    evidence: RiskEvidenceDto,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
        ) {
            Text(
                text =
                    "${index + 1}. ${evidence.title}",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    "风险类别：${evidence.category}",
            )

            Text(
                text =
                    "规则编号：${evidence.ruleId}",
            )

            Text(
                text =
                    "风险分值：${evidence.score}",
            )

            if (
                evidence.matchedTerms
                    .isNotEmpty()
            ) {
                Spacer(
                    modifier =
                        Modifier.height(8.dp),
                )

                Text(
                    text =
                        "命中内容：" +
                                evidence
                                    .matchedTerms
                                    .joinToString("、"),
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    evidence.explanation,
            )

            if (evidence.tags.isNotEmpty()) {
                Spacer(
                    modifier =
                        Modifier.height(8.dp),
                )

                Text(
                    text =
                        "标签：" +
                                evidence.tags
                                    .joinToString("、"),
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )
            }
        }
    }
}

/**
 * 行动建议。
 */
@Composable
private fun ActionsCard(
    actions: List<String>,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
        ) {
            Text(
                text = "建议采取的措施",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            actions.forEachIndexed {
                    index,
                    action ->

                Text(
                    text =
                        "${index + 1}. $action",
                    modifier =
                        Modifier.padding(
                            vertical = 4.dp,
                        ),
                )
            }
        }
    }
}

/**
 * 事件基础信息。
 */
@Composable
private fun EventInformationCard(
    detail: RiskEventDetailResponse,
) {
    val sourceTypeName =
        when (
            detail.sourceType.lowercase()
        ) {
            "text" -> "文本"
            "image" -> "图片"
            "url" -> "链接"
            "voice" -> "语音"
            "call" -> "通话护航"
            else -> detail.sourceType
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
        ) {
            Text(
                text = "事件信息",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    "事件编号：${detail.eventId}",
            )

            Text(
                text =
                    "检测类型：$sourceTypeName",
            )

            Text(
                text =
                    "分析引擎：" +
                        riskAnalysisEngineName(
                            detail.analysisMode,
                        ),
            )

            detail.aiModel?.let { aiModel ->
                Text(
                    text = "AI 模型：$aiModel",
                )
            }

            detail.ruleScore?.let { ruleScore ->
                Text(
                    text =
                        "规则评分：" +
                            formatRiskScore(ruleScore),
                )
            }

            detail.aiScore?.let { aiScore ->
                Text(
                    text =
                        "AI 语义评分：" +
                            formatRiskScore(aiScore),
                )
            }

            detail.aiRiskLevel?.let { aiRiskLevel ->
                Text(
                    text =
                        "AI 语义等级：" +
                            riskLevelDisplayName(
                                aiRiskLevel,
                            ),
                )
            }

            if (detail.fusionApplied) {
                Text(
                    text =
                        "最终融合评分：" +
                            formatRiskScore(
                                detail.score,
                            ),
                )
            }

            detail.aiConfidence?.let { confidence ->
                Text(
                    text =
                        "AI 置信度：" +
                            formatAiConfidence(
                                confidence,
                            ),
                )
            }

            detail.fusionReason?.let { reason ->
                Text(
                    text = "评分融合说明：$reason",
                )
            }

            detail.aiSummary?.let { summary ->
                Text(
                    text = "AI 语义复核：$summary",
                )
            }

            Text(
                text =
                    "规则版本：${detail.ruleVersion}",
            )

            Text(
                text =
                    "创建时间：${detail.createdAt}",
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            Text(
                text = detail.disclaimer,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
            )
        }
    }
}

/**
 * high 风险事件的家庭告警操作卡片。
 */
@Composable
private fun FamilyAlertActionCard(
    state: RiskEventDetailUiState,
    onFamilySelected: (String) -> Unit,
    onCreateFamilyAlertClick: () -> Unit,
    onViewCreatedAlertClick: (
        familyId: String,
        alertId: String,
    ) -> Unit,
    onOpenFamilyCenterClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .errorContainer,
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(18.dp),
        ) {
            Text(
                text = "家庭紧急告警",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.Bold,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    "该事件已达到高风险等级。你可以选择一个家庭生成告警，随后在告警详情中确认并发送给可信联系人。",
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp),
            )

            when {
                state.isLoadingFamilies -> {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                        )

                        Text(
                            text = "正在读取家庭列表……",
                        )
                    }
                }

                state.availableFamilies.isEmpty() -> {
                    Text(
                        text =
                            state.familyAlertErrorMessage
                                ?: "当前账号还没有可用家庭，请先创建家庭或接受家庭邀请。",
                        color =
                            if (
                                state.familyAlertErrorMessage != null
                            ) {
                                MaterialTheme
                                    .colorScheme
                                    .error
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onErrorContainer
                            },
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp),
                    )

                    OutlinedButton(
                        onClick =
                            onOpenFamilyCenterClick,
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "前往家庭中心",
                        )
                    }
                }

                else -> {
                    Text(
                        text = "选择接收告警的家庭",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.Bold,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp),
                    )

                    state.availableFamilies.forEach {
                            family ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled =
                                        !state
                                            .isCreatingFamilyAlert,
                                ) {
                                    onFamilySelected(
                                        family.id,
                                    )
                                }
                                .padding(
                                    vertical = 6.dp,
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected =
                                    state.selectedFamilyId ==
                                            family.id,
                                onClick = {
                                    onFamilySelected(
                                        family.id,
                                    )
                                },
                                enabled =
                                    !state
                                        .isCreatingFamilyAlert,
                            )

                            Column(
                                modifier =
                                    Modifier.padding(
                                        start = 8.dp,
                                    ),
                            ) {
                                Text(
                                    text = family.name,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium,
                                    fontWeight =
                                        FontWeight.SemiBold,
                                )

                                Text(
                                    text =
                                        "我的角色：${formatFamilyRole(family.myRole)}",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall,
                                )
                            }
                        }
                    }

                    state.familyAlertErrorMessage
                        ?.let { message ->
                            Spacer(
                                modifier =
                                    Modifier.height(8.dp),
                            )

                            Text(
                                text = message,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,
                            )
                        }

                    state.familyAlertSuccessMessage
                        ?.let { message ->
                            Spacer(
                                modifier =
                                    Modifier.height(8.dp),
                            )

                            Text(
                                text = message,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,
                                fontWeight =
                                    FontWeight.SemiBold,
                            )
                        }

                    Spacer(
                        modifier =
                            Modifier.height(14.dp),
                    )

                    val createdAlert =
                        state.createdFamilyAlert

                    if (createdAlert == null) {
                        Button(
                            onClick =
                                onCreateFamilyAlertClick,
                            enabled =
                                state.selectedFamilyId != null &&
                                        !state.isCreatingFamilyAlert,
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            if (
                                state.isCreatingFamilyAlert
                            ) {
                                CircularProgressIndicator(
                                    modifier =
                                        Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )

                                Spacer(
                                    modifier =
                                        Modifier.size(10.dp),
                                )

                                Text(
                                    text = "正在创建告警……",
                                )
                            } else {
                                Text(
                                    text = "创建家庭告警",
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                onViewCreatedAlertClick(
                                    createdAlert.familyId,
                                    createdAlert.id,
                                )
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "查看并发送家庭告警",
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 将后端家庭角色转换为中文。
 */
private fun formatFamilyRole(
    role: String,
): String {
    return when (role.lowercase()) {
        "owner" -> "家庭创建者"
        "admin" -> "管理员"
        "member" -> "家庭成员"
        else -> role
    }
}

/**
 * 将不同来源的附加信息转换为可显示文本。
 *
 * 文本事件通常为 null；
 * 图片和 URL 事件通常为 JSON 对象。
 */
private fun formatSourceMetadata(
    metadata: JsonElement?,
): String? {
    if (
        metadata == null ||
        metadata.isJsonNull
    ) {
        return null
    }

    if (
        metadata.isJsonObject &&
        metadata.asJsonObject.size() == 0
    ) {
        return null
    }

    return metadata.toString()
}