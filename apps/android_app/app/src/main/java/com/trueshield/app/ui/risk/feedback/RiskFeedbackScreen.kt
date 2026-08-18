package com.trueshield.app.ui.risk.feedback

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.trueshield.app.data.model.risk.feedback.CorrectedRiskReanalysisResponse
import com.trueshield.app.data.model.risk.feedback.RiskAnalysisComparisonDto
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackTypeValue
import com.trueshield.app.data.model.risk.feedback.RiskLevelValue
import com.trueshield.app.data.model.risk.feedback.RiskReanalysisSnapshotDto

/**
 * 图片 OCR 反馈和修正后重新分析页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskFeedbackScreen(
    state: RiskFeedbackUiState,
    onFeedbackTypeChange: (String) -> Unit,
    onExpectedRiskLevelChange: (String) -> Unit,
    onOcrTextAccurateChange: (Boolean) -> Unit,
    onCorrectedTextChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmitClick: () -> Unit,
    onReanalyzeClick: () -> Unit,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit,
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
                        text =
                            if (state.isImageEvent) {
                                "风险反馈与 OCR 修正"
                            } else {
                                "风险反馈"
                            },
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
            if (state.isInitialLoading) {
                item {
                    LoadingContent()
                }

                return@LazyColumn
            }

            state.eventDetail?.let { detail ->
                item {
                    EventSummaryCard(
                        riskLevel =
                            detail.riskLevel,
                        score =
                            detail.score,
                        eventId =
                            detail.eventId,
                        sourceType =
                            detail.sourceType,
                    )
                }

                item {
                    val contentTitle =
                        when (
                            detail.sourceType.lowercase()
                        ) {
                            "image" ->
                                "原始 OCR 文字"

                            "url" ->
                                "原始链接"

                            else ->
                                "原始检测内容"
                        }

                    val emptyMessage =
                        when (
                            detail.sourceType.lowercase()
                        ) {
                            "image" ->
                                "该事件没有保存 OCR 文字。"

                            "url" ->
                                "该事件没有保存原始链接。"

                            else ->
                                "该事件没有保存原始文本。"
                        }

                    InformationCard(
                        title = contentTitle,
                        content =
                            detail.sourceText.ifBlank {
                                emptyMessage
                            },
                    )
                }
            }

            state.savedFeedback?.let {
                    feedback ->

                item {
                    SavedFeedbackCard(
                        updatedAt =
                            feedback.updatedAt,
                    )
                }
            }

            if (state.eventDetail != null) {
                item {
                    SectionTitle(
                        title = "风险判断是否准确",
                        description =
                            "请判断系统给出的风险等级是否符合实际情况。",
                    )
                }

                feedbackOptions.forEach {
                        option ->

                    item {
                        StringSelectionCard(
                            title = option.title,
                            description =
                                option.description,
                            selected =
                                state.feedbackType ==
                                        option.value,
                            enabled =
                                !state.isBusy,
                            onClick = {
                                onFeedbackTypeChange(
                                    option.value,
                                )
                            },
                        )
                    }
                }

                if (
                    state.requiresExpectedRiskLevel
                ) {
                    item {
                        SectionTitle(
                            title = "正确的风险等级",
                            description =
                                "请选择你认为该事件实际应当属于的风险等级。",
                        )
                    }

                    riskLevelOptions.forEach {
                            option ->

                        item {
                            StringSelectionCard(
                                title = option.title,
                                description =
                                    option.description,
                                selected =
                                    state.expectedRiskLevel ==
                                            option.value,
                                enabled =
                                    !state.isBusy,
                                onClick = {
                                    onExpectedRiskLevelChange(
                                        option.value,
                                    )
                                },
                            )
                        }
                    }
                }

                if (state.isImageEvent) {
                    item {
                        SectionTitle(
                            title = "OCR 文字是否准确",
                            description =
                                "请将识别文字与原始截图进行核对。",
                        )
                    }

                    item {
                        BooleanSelectionCard(
                            title = "OCR 文字准确",
                            description =
                                "识别出的文字与截图内容一致。",
                            selected =
                                state.ocrTextAccurate == true,
                            enabled =
                                !state.isBusy,
                            onClick = {
                                onOcrTextAccurateChange(
                                    true,
                                )
                            },
                        )
                    }

                    item {
                        BooleanSelectionCard(
                            title = "OCR 文字不准确",
                            description =
                                "识别文字存在遗漏、错字或内容错误，需要人工修正。",
                            selected =
                                state.ocrTextAccurate == false,
                            enabled =
                                !state.isBusy,
                            onClick = {
                                onOcrTextAccurateChange(
                                    false,
                                )
                            },
                        )
                    }

                    if (state.requiresCorrectedText) {
                        item {
                            OutlinedTextField(
                                value =
                                    state.correctedText,
                                onValueChange =
                                    onCorrectedTextChange,
                                modifier =
                                    Modifier.fillMaxWidth(),
                                label = {
                                    Text(
                                        text =
                                            "修正后的完整文字",
                                    )
                                },
                                placeholder = {
                                    Text(
                                        text =
                                            "请按照原始截图，填写修正后的完整聊天内容。",
                                    )
                                },
                                supportingText = {
                                    Text(
                                        text =
                                            "${state.correctedText.length}" +
                                                    "/$CORRECTED_OCR_TEXT_MAX_LENGTH",
                                    )
                                },
                                minLines = 8,
                                maxLines = 16,
                                enabled =
                                    !state.isBusy,
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.comment,
                        onValueChange =
                            onCommentChange,
                        modifier =
                            Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                text =
                                    "补充说明（可选）",
                            )
                        },
                        placeholder = {
                            Text(
                                text =
                                    "例如：截图中的金额识别错误，或者风险等级判断偏高。",
                            )
                        },
                        supportingText = {
                            Text(
                                text =
                                    "${state.comment.length}" +
                                            "/$RISK_FEEDBACK_COMMENT_MAX_LENGTH",
                            )
                        },
                        minLines = 3,
                        maxLines = 6,
                        enabled =
                            !state.isBusy,
                    )
                }
            }

            state.successMessage?.let {
                    message ->

                item {
                    MessageCard(
                        title = "操作成功",
                        message = message,
                        isError = false,
                    )
                }
            }

            state.errorMessage?.let {
                    message ->

                item {
                    MessageCard(
                        title = "操作失败",
                        message = message,
                        isError = true,
                    )
                }
            }

            if (state.eventDetail != null) {
                item {
                    Button(
                        onClick =
                            onSubmitClick,
                        enabled =
                            state.canSubmit,
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        if (
                            state.isSubmitting ||
                            state.isReanalyzing
                        ) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )

                            Spacer(
                                modifier =
                                    Modifier.size(12.dp),
                            )

                            Text(
                                text =
                                    if (
                                        state.isReanalyzing
                                    ) {
                                        "正在根据修正文字重新分析……"
                                    } else {
                                        "正在保存反馈……"
                                    },
                            )
                        } else {
                            Text(
                                text =
                                    when {
                                        state.isImageEvent &&
                                                state.requiresCorrectedText -> {
                                            "提交反馈并重新分析"
                                        }

                                        state.savedFeedback != null -> {
                                            "更新风险反馈"
                                        }

                                        else -> {
                                            "提交风险反馈"
                                        }
                                    },
                            )
                        }
                    }
                }

                /*
                 * 已经保存过 corrected_text，
                 * 但当前没有重新分析结果时，
                 * 允许用户手动重试。
                 */
                if (
                    state.isImageEvent &&
                    !state.savedFeedback
                        ?.correctedText
                        .isNullOrBlank() &&
                    state.reanalysisResult == null
                ) {
                    item {
                        OutlinedButton(
                            onClick =
                                onReanalyzeClick,
                            enabled =
                                !state.isBusy,
                            modifier =
                                Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text =
                                    "重新执行修正文字分析",
                            )
                        }
                    }
                }
            }

            state.reanalysisResult?.let {
                    result ->

                item {
                    ReanalysisResultContent(
                        result = result,
                    )
                }
            }

            if (
                state.eventDetail == null &&
                !state.isInitialLoading
            ) {
                item {
                    OutlinedButton(
                        onClick =
                            onRetryClick,
                        enabled =
                            !state.isBusy,
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
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
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
            text =
                "正在读取风险事件和已有反馈……",
        )
    }
}

@Composable
private fun EventSummaryCard(
    riskLevel: String,
    score: Int,
    eventId: String,
    sourceType: String,
){
    val levelName =
        formatRiskLevel(
            riskLevel = riskLevel,
        )

    val containerColor =
        when (riskLevel.lowercase()) {
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
                text = "当前风险分析",
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
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    "风险分数：$score/100",
            )

            Text(
                text =
                    "检测类型：" +
                            formatSourceType(
                                sourceType = sourceType,
                            ),
            )

            Text(
                text =
                    "事件编号：$eventId",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
            )
        }
    }
}

@Composable
private fun SavedFeedbackCard(
    updatedAt: String,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer,
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
        ) {
            Text(
                text = "已存在反馈",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold,
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp),
            )

            Text(
                text =
                    "修改后再次提交会更新原反馈，" +
                            "不会创建重复记录。",
            )

            Text(
                text =
                    "最后更新时间：$updatedAt",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    description: String,
) {
    Column {
        Text(
            text = title,
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
            fontWeight =
                FontWeight.Bold,
        )

        Spacer(
            modifier =
                Modifier.height(6.dp),
        )

        Text(
            text = description,
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
        )
    }
}

@Composable
private fun StringSelectionCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                    } else {
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )

            Spacer(
                modifier =
                    Modifier.size(12.dp),
            )

            Column {
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
                        Modifier.height(4.dp),
                )

                Text(
                    text = description,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun BooleanSelectionCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    StringSelectionCard(
        title = title,
        description = description,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
    )
}

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
            )
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    isError: Boolean,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isError) {
                        MaterialTheme
                            .colorScheme
                            .errorContainer
                    } else {
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                    },
            ),
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
                text = message,
            )
        }
    }
}

@Composable
private fun ReanalysisResultContent(
    result: CorrectedRiskReanalysisResponse,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "修正前后分析对比",
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
            fontWeight =
                FontWeight.Bold,
        )

        AnalysisSnapshotCard(
            title = "原始 OCR 分析",
            snapshot =
                result.originalAnalysis,
        )

        AnalysisSnapshotCard(
            title = "修正文字分析",
            snapshot =
                result.correctedAnalysis,
        )

        ComparisonCard(
            comparison =
                result.comparison,
        )

        InformationCard(
            title = "修正后的文字",
            content =
                result.correctedText,
        )
    }
}

@Composable
private fun AnalysisSnapshotCard(
    title: String,
    snapshot: RiskReanalysisSnapshotDto,
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
                text =
                    "风险等级：" +
                            formatRiskLevel(
                                snapshot.riskLevel,
                            ),
            )

            Text(
                text =
                    "风险分数：${snapshot.score}/100",
            )

            Text(
                text =
                    "命中证据：${snapshot.evidence.size} 条",
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text = snapshot.summary,
            )
        }
    }
}

@Composable
private fun ComparisonCard(
    comparison: RiskAnalysisComparisonDto,
) {
    val scoreDeltaText =
        if (comparison.scoreDelta > 0) {
            "+${comparison.scoreDelta}"
        } else {
            comparison.scoreDelta.toString()
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .secondaryContainer,
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
        ) {
            Text(
                text = "分析变化",
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
                    "风险等级是否变化：" +
                            if (
                                comparison
                                    .riskLevelChanged
                            ) {
                                "是"
                            } else {
                                "否"
                            },
            )

            Text(
                text =
                    "分数变化：$scoreDeltaText",
            )

            RuleIdList(
                title = "新增命中规则",
                ruleIds =
                    comparison.addedRuleIds,
            )

            RuleIdList(
                title = "移除命中规则",
                ruleIds =
                    comparison.removedRuleIds,
            )

            RuleIdList(
                title = "保留命中规则",
                ruleIds =
                    comparison.retainedRuleIds,
            )
        }
    }
}

@Composable
private fun RuleIdList(
    title: String,
    ruleIds: List<String>,
) {
    Spacer(
        modifier =
            Modifier.height(8.dp),
    )

    Text(
        text = title,
        fontWeight =
            FontWeight.Bold,
    )

    Text(
        text =
            if (ruleIds.isEmpty()) {
                "无"
            } else {
                ruleIds.joinToString("、")
            },
    )
}

private fun formatRiskLevel(
    riskLevel: String,
): String {
    return when (riskLevel.lowercase()) {
        RiskLevelValue.HIGH ->
            "高风险"

        RiskLevelValue.MEDIUM ->
            "中风险"

        RiskLevelValue.LOW ->
            "低风险"

        else ->
            riskLevel
    }
}

private data class SelectionOption(
    val value: String,
    val title: String,
    val description: String,
)

private val feedbackOptions =
    listOf(
        SelectionOption(
            value =
                RiskFeedbackTypeValue.ACCURATE,
            title = "系统判断准确",
            description =
                "风险等级和风险判断符合实际情况。",
        ),
        SelectionOption(
            value =
                RiskFeedbackTypeValue.FALSE_POSITIVE,
            title = "系统发生误报",
            description =
                "系统认为存在风险，但实际属于低风险。",
        ),
        SelectionOption(
            value =
                RiskFeedbackTypeValue.FALSE_NEGATIVE,
            title = "系统发生漏报",
            description =
                "系统没有充分识别出实际存在的风险。",
        ),
        SelectionOption(
            value =
                RiskFeedbackTypeValue.RISK_TOO_HIGH,
            title = "风险等级偏高",
            description =
                "存在一定风险，但系统给出的等级过高。",
        ),
        SelectionOption(
            value =
                RiskFeedbackTypeValue.RISK_TOO_LOW,
            title = "风险等级偏低",
            description =
                "系统识别到风险，但给出的等级不够高。",
        ),
    )

private val riskLevelOptions =
    listOf(
        SelectionOption(
            value = RiskLevelValue.LOW,
            title = "低风险",
            description =
                "暂未发现明显风险，但仍建议保持警惕。",
        ),
        SelectionOption(
            value = RiskLevelValue.MEDIUM,
            title = "中风险",
            description =
                "存在可疑特征，需要进一步核实。",
        ),
        SelectionOption(
            value = RiskLevelValue.HIGH,
            title = "高风险",
            description =
                "存在明显诈骗或重大安全风险。",
        ),
    )

private fun formatSourceType(
    sourceType: String,
): String {
    return when (
        sourceType.lowercase()
    ) {
        "text" ->
            "文本风险检测"

        "image" ->
            "图片风险检测"

        "url" ->
            "链接风险检测"

        else ->
            sourceType
    }
}