package com.trueshield.app.ui.risk.report

import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.data.model.risk.report.RiskReportSummaryResponse

enum class RiskReportScopeSelection {
    PERSONAL,
    FAMILY,
}

data class RiskReportUiState(
    val scope: RiskReportScopeSelection =
        RiskReportScopeSelection.PERSONAL,
    val families: List<FamilyDto> = emptyList(),
    val selectedFamilyId: String? = null,
    val selectedPeriodDays: Int = 30,
    val report: RiskReportSummaryResponse? = null,
    val pdfFilePath: String? = null,
    val pdfDisplayName: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
    val familyMessage: String? = null,
    val exportMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isBusy: Boolean
        get() =
            isLoading ||
                isRefreshing ||
                isExporting

    val selectedFamily: FamilyDto?
        get() =
            families.firstOrNull {
                it.id == selectedFamilyId
            }
}
