package com.trueshield.app.ui.alert

import com.trueshield.app.data.model.alert.FamilyAlertResponse
import com.trueshield.app.data.model.family.FamilyDto

/**
 * 家庭告警列表筛选条件。
 */
enum class FamilyAlertFilter {

    /**
     * 显示全部告警。
     */
    ALL,

    /**
     * 后端状态 pending：
     * 告警已经创建，但还没有被家庭成员确认。
     */
    PENDING,

    /**
     * 后端状态 acknowledged：
     * 家庭成员已经确认并开始处理。
     */
    ACKNOWLEDGED,

    /**
     * 后端状态 resolved：
     * 告警已经处理完成。
     */
    RESOLVED,
}

/**
 * 家庭告警列表页面状态。
 */
data class FamilyAlertUiState(

    /**
     * 当前账户可以访问的家庭。
     */
    val families: List<FamilyDto> =
        emptyList(),

    /**
     * 当前选中的家庭编号。
     */
    val selectedFamilyId: String? = null,

    /**
     * 当前选中家庭的全部告警。
     */
    val alerts: List<FamilyAlertResponse> =
        emptyList(),

    /**
     * 后端返回的告警总数。
     */
    val totalAlerts: Int = 0,

    /**
     * 当前筛选条件。
     */
    val selectedFilter: FamilyAlertFilter =
        FamilyAlertFilter.ALL,

    /**
     * 是否已经完成过至少一次初始化。
     *
     * 即使家庭列表或告警列表为空，
     * 也可以防止 Compose 页面重组时重复请求。
     */
    val hasLoadedOnce: Boolean = false,

    /**
     * 第一次进入页面时是否正在加载。
     */
    val isInitialLoading: Boolean = false,

    /**
     * 是否正在刷新全部家庭和告警。
     */
    val isRefreshing: Boolean = false,

    /**
     * 是否正在读取所选家庭的告警。
     */
    val isLoadingAlerts: Boolean = false,

    /**
     * 操作成功提示。
     *
     * 当前列表阶段暂时使用较少，
     * 后面确认告警、发送告警时会用到。
     */
    val successMessage: String? = null,

    /**
     * 页面错误提示。
     */
    val errorMessage: String? = null,

    /**
     * Token 是否已经过期。
     */
    val sessionExpired: Boolean = false,
) {

    /**
     * 根据 selectedFamilyId 找到完整家庭信息。
     */
    val selectedFamily: FamilyDto?
        get() =
            families.firstOrNull {
                it.id == selectedFamilyId
            }

    /**
     * 待确认告警数量。
     */
    val pendingCount: Int
        get() =
            alerts.count {
                it.status.equals(
                    other = "pending",
                    ignoreCase = true,
                )
            }

    /**
     * 已确认、正在处理的告警数量。
     */
    val acknowledgedCount: Int
        get() =
            alerts.count {
                it.status.equals(
                    other = "acknowledged",
                    ignoreCase = true,
                )
            }

    /**
     * 已处理完成的告警数量。
     */
    val resolvedCount: Int
        get() =
            alerts.count {
                it.status.equals(
                    other = "resolved",
                    ignoreCase = true,
                )
            }

    /**
     * 根据当前筛选条件，得到页面真正显示的告警。
     *
     * 这里只在客户端筛选，不会再次请求后端。
     */
    val visibleAlerts: List<FamilyAlertResponse>
        get() =
            when (selectedFilter) {

                FamilyAlertFilter.ALL -> {
                    alerts
                }

                FamilyAlertFilter.PENDING -> {
                    alerts.filter {
                        it.status.equals(
                            other = "pending",
                            ignoreCase = true,
                        )
                    }
                }

                FamilyAlertFilter.ACKNOWLEDGED -> {
                    alerts.filter {
                        it.status.equals(
                            other = "acknowledged",
                            ignoreCase = true,
                        )
                    }
                }

                FamilyAlertFilter.RESOLVED -> {
                    alerts.filter {
                        it.status.equals(
                            other = "resolved",
                            ignoreCase = true,
                        )
                    }
                }
            }

    /**
     * 当前页面是否正在执行网络操作。
     */
    val isBusy: Boolean
        get() =
            isInitialLoading ||
                    isRefreshing ||
                    isLoadingAlerts
}