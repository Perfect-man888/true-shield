package com.trueshield.app.ui.risk.dashboard

import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.data.model.risk.dashboard.RiskDashboardResponse

/**
 * 风险统计仪表盘当前统计范围。
 */
enum class RiskDashboardScopeSelection {
    PERSONAL,
    FAMILY,
}

/**
 * 风险统计仪表盘页面状态。
 */
data class RiskDashboardUiState(
    val scope: RiskDashboardScopeSelection =
        RiskDashboardScopeSelection.PERSONAL,
    val families: List<FamilyDto> = emptyList(),
    val selectedFamilyId: String? = null,
    val selectedPeriodDays: Int = 7,
    val dashboard: RiskDashboardResponse? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val familyMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isBusy: Boolean
        get() = isLoading || isRefreshing

    val selectedFamily: FamilyDto?
        get() =
            families.firstOrNull {
                it.id == selectedFamilyId
            }
}
