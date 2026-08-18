package com.trueshield.app.ui.risk.detail

import com.trueshield.app.data.model.alert.FamilyAlertResponse
import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.data.model.risk.RiskEventDetailResponse

/**
 * 风险事件详情页面状态。
 */
data class RiskEventDetailUiState(
    /**
     * 当前加载的事件编号。
     */
    val eventId: String = "",

    /**
     * 是否正在读取后端数据。
     */
    val isLoading: Boolean = false,

    /**
     * 后端返回的完整风险事件。
     */
    val detail: RiskEventDetailResponse? = null,

    /**
     * 当前用户可以访问的家庭。
     *
     * 仅当风险等级为 high 时读取，
     * 用于选择要接收该告警的家庭。
     */
    val availableFamilies: List<FamilyDto> =
        emptyList(),

    /**
     * 当前选中的家庭编号。
     */
    val selectedFamilyId: String? = null,

    /**
     * 是否正在读取家庭列表。
     */
    val isLoadingFamilies: Boolean = false,

    /**
     * 是否正在根据风险事件创建家庭告警。
     */
    val isCreatingFamilyAlert: Boolean = false,

    /**
     * 创建成功后返回的家庭告警。
     */
    val createdFamilyAlert: FamilyAlertResponse? = null,

    /**
     * 家庭告警操作成功提示。
     */
    val familyAlertSuccessMessage: String? = null,

    /**
     * 家庭列表或家庭告警创建失败提示。
     */
    val familyAlertErrorMessage: String? = null,

    /**
     * 加载风险事件失败信息。
     */
    val errorMessage: String? = null,

    /**
     * Token 是否已经失效。
     */
    val sessionExpired: Boolean = false,
)
