package com.trueshield.app.ui.risk.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.FamilyOperationResult
import com.trueshield.app.data.repository.FamilyRepository
import com.trueshield.app.data.repository.RiskReportRepository
import com.trueshield.app.data.repository.RiskReportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class RiskReportViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val reportRepository =
        RiskReportRepository(
            context = application,
            riskApi =
                trueShieldApplication
                    .apiClient
                    .riskApi,
        )

    private val familyRepository =
        FamilyRepository(
            familyApi =
                trueShieldApplication
                    .apiClient
                    .familyApi,
        )

    private val _uiState =
        MutableStateFlow(RiskReportUiState())

    val uiState: StateFlow<RiskReportUiState> =
        _uiState.asStateFlow()

    fun loadInitial(
        force: Boolean = false,
    ) {
        val current = _uiState.value
        if (current.isBusy) {
            return
        }
        if (!force && current.report != null) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                familyMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            loadFamilies()
            if (!_uiState.value.sessionExpired) {
                requestSummary()
            }
        }
    }

    fun selectPersonalScope() {
        if (_uiState.value.isBusy) {
            return
        }
        clearGeneratedPdf()
        _uiState.update {
            it.copy(
                scope =
                    RiskReportScopeSelection.PERSONAL,
                selectedFamilyId = null,
                report = null,
                errorMessage = null,
            )
        }
        reloadForSelection()
    }

    fun selectFamily(
        familyId: String,
    ) {
        val cleanedId = familyId.trim()
        val current = _uiState.value
        if (
            current.isBusy ||
            cleanedId.isBlank() ||
            current.families.none { it.id == cleanedId }
        ) {
            return
        }

        clearGeneratedPdf()
        _uiState.update {
            it.copy(
                scope = RiskReportScopeSelection.FAMILY,
                selectedFamilyId = cleanedId,
                report = null,
                errorMessage = null,
            )
        }
        reloadForSelection()
    }

    fun selectPeriodDays(
        periodDays: Int,
    ) {
        val safeDays =
            when (periodDays) {
                7 -> 7
                90 -> 90
                else -> 30
            }
        if (
            _uiState.value.isBusy ||
            _uiState.value.selectedPeriodDays == safeDays
        ) {
            return
        }

        clearGeneratedPdf()
        _uiState.update {
            it.copy(
                selectedPeriodDays = safeDays,
                report = null,
                errorMessage = null,
            )
        }
        reloadForSelection()
    }

    fun refresh() {
        if (_uiState.value.isBusy) {
            return
        }
        clearGeneratedPdf()
        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                familyMessage = null,
                sessionExpired = false,
            )
        }
        viewModelScope.launch {
            loadFamilies()
            if (!_uiState.value.sessionExpired) {
                requestSummary()
            }
        }
    }

    fun exportPdf() {
        val current = _uiState.value
        if (current.isBusy || current.report == null) {
            return
        }

        _uiState.update {
            it.copy(
                isExporting = true,
                errorMessage = null,
                exportMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            val familyId =
                if (
                    _uiState.value.scope ==
                    RiskReportScopeSelection.FAMILY
                ) {
                    _uiState.value.selectedFamilyId
                } else {
                    null
                }

            when (
                val result =
                    reportRepository.downloadPdf(
                        periodDays =
                            _uiState.value
                                .selectedPeriodDays,
                        familyId = familyId,
                    )
            ) {
                is RiskReportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            pdfFilePath =
                                result.data.file.absolutePath,
                            pdfDisplayName =
                                result.data.displayName,
                            exportMessage =
                                "PDF 已生成，可保存或分享。",
                        )
                    }
                }

                is RiskReportResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
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
            it.copy(sessionExpired = false)
        }
    }

    fun consumeExportMessage() {
        _uiState.update {
            it.copy(exportMessage = null)
        }
    }

    private fun reloadForSelection() {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                sessionExpired = false,
            )
        }
        viewModelScope.launch {
            requestSummary()
        }
    }

    private suspend fun loadFamilies() {
        when (
            val result =
                familyRepository.getFamilies()
        ) {
            is FamilyOperationResult.Success -> {
                val families = result.data.items
                _uiState.update { current ->
                    val selectedStillExists =
                        families.any {
                            it.id == current.selectedFamilyId
                        }
                    current.copy(
                        families = families,
                        scope =
                            if (
                                current.scope ==
                                RiskReportScopeSelection.FAMILY &&
                                !selectedStillExists
                            ) {
                                RiskReportScopeSelection.PERSONAL
                            } else {
                                current.scope
                            },
                        selectedFamilyId =
                            current.selectedFamilyId
                                .takeIf {
                                    selectedStillExists
                                },
                        familyMessage =
                            if (families.isEmpty()) {
                                "你尚未加入家庭，当前只能生成个人报告。"
                            } else {
                                null
                            },
                    )
                }
            }

            is FamilyOperationResult.Error -> {
                _uiState.update {
                    it.copy(
                        familyMessage = result.message,
                        sessionExpired =
                            result.requiresLogin,
                        isLoading =
                            if (result.requiresLogin) {
                                false
                            } else {
                                it.isLoading
                            },
                        isRefreshing =
                            if (result.requiresLogin) {
                                false
                            } else {
                                it.isRefreshing
                            },
                    )
                }
            }
        }
    }

    private suspend fun requestSummary() {
        val current = _uiState.value
        val familyId =
            if (
                current.scope ==
                RiskReportScopeSelection.FAMILY
            ) {
                current.selectedFamilyId
            } else {
                null
            }

        when (
            val result =
                reportRepository.getSummary(
                    periodDays =
                        current.selectedPeriodDays,
                    familyId = familyId,
                )
        ) {
            is RiskReportResult.Success -> {
                _uiState.update {
                    it.copy(
                        report = result.data,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                        sessionExpired = false,
                    )
                }
            }

            is RiskReportResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message,
                        sessionExpired =
                            result.requiresLogin,
                    )
                }
            }
        }
    }

    private fun clearGeneratedPdf() {
        _uiState.value.pdfFilePath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.delete()

        _uiState.update {
            it.copy(
                pdfFilePath = null,
                pdfDisplayName = null,
                exportMessage = null,
            )
        }
    }

    override fun onCleared() {
        _uiState.value.pdfFilePath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.delete()
        super.onCleared()
    }
}
