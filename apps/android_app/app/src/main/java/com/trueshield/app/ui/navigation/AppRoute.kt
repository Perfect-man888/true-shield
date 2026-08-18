package com.trueshield.app.ui.navigation

/**
 * 真信盾第一版客户端页面路由。
 *
 * 所有页面地址集中管理，避免直接在代码中
 * 到处书写 "home"、"text_risk" 等字符串。
 */
sealed class AppRoute(
    val route: String,
) {

    /**
     * App 首页。
     */
    data object Home : AppRoute(
        route = "home",
    )

    /** 风险检测聚合页。 */
    data object RiskDetectionHub : AppRoute(
        route = "main/detection",
    )

    /** 家庭守护聚合页。 */
    data object FamilyHub : AppRoute(
        route = "main/family",
    )

    /** 告警、邀请与风险提醒聚合页。 */
    data object MessageCenter : AppRoute(
        route = "main/messages",
    )

    /** 账户、外观和风险数据聚合页。 */
    data object ProfileHub : AppRoute(
        route = "main/profile",
    )


    /**
     * 当前登录用户个人中心。
     */
    data object AccountProfile : AppRoute(
        route = "account/profile",
    )

    /**
     * 当前登录用户账户安全页面。
     */
    data object AccountSecurity : AppRoute(
        route = "account/security",
    )

    /**
     * 号码风险筛查与通话安全护航页面。
     */
    data object CallGuard : AppRoute(
        route = "call_guard",
    )

    /**
     * 文本风险检测页面。
     */
    data object TextRisk : AppRoute(
        route = "risk/text",
    )

    /**
     * 图片风险检测页面。
     */
    data object ImageRisk : AppRoute(
        route = "risk/image",
    )

    /**
     * 语音转写风险检测页面。
     */
    data object VoiceRisk : AppRoute(
        route = "risk/voice",
    )

    /**
     * URL 风险检测页面。
     */
    data object UrlRisk : AppRoute(
        route = "risk/url",
    )

    /**
     * 风险历史记录页面。
     */
    data object RiskHistory : AppRoute(
        route = "risk/history",
    )

    /**
     * 个人与家庭风险统计仪表盘。
     */
    data object RiskDashboard : AppRoute(
        route = "risk/dashboard",
    )

    /**
     * 个人与家庭风险报告及 PDF 导出页面。
     */
    data object RiskReport : AppRoute(
        route = "risk/report",
    )

    /**
     * 当前用户风险反馈统计页面。
     */
    data object RiskFeedbackStatistics : AppRoute(
        route = "risk/statistics/feedback",
    )

    /**
     * 通用风险事件详情页面。
     */
    data object RiskDetail : AppRoute(
        route = "risk/detail/{eventId}",
    ) {
        const val EVENT_ID_ARGUMENT =
            "eventId"

        fun createRoute(
            eventId: String,
        ): String {
            return "risk/detail/$eventId"
        }
    }

    /**
     * 风险反馈与 OCR 修正页面。
     */
    data object RiskFeedback : AppRoute(
        route = "risk/feedback/{eventId}",
    ) {
        const val EVENT_ID_ARGUMENT =
            "eventId"

        fun createRoute(
            eventId: String,
        ): String {
            return "risk/feedback/$eventId"
        }
    }

    /**
     * 家庭中心。
     */
    data object FamilyCenter :
        AppRoute("family/center")

    /**
     * 可信联系人。
     */
    data object TrustedContacts :
        AppRoute("contacts/trusted")

    data object FamilyAlert :
        AppRoute("family_alert")

    /**
     * 家庭告警自动触发策略页面。
     */


    data object FamilyAlertDetail :
        AppRoute(
            route =
                "family_alert_detail/{familyId}/{alertId}",
        ) {

        fun createRoute(
            familyId: String,
            alertId: String,
        ): String {
            return (
                    "family_alert_detail/" +
                            "$familyId/" +
                            alertId
                    )
        }
    }

    /**
     * 家庭告警自动触发策略页面。
     */
    data object FamilyAlertPolicy :
        AppRoute(
            route = "family_alert_policy/{familyId}",
        ) {

        const val FAMILY_ID_ARGUMENT =
            "familyId"

        fun createRoute(
            familyId: String,
        ): String {
            return "family_alert_policy/$familyId"
        }
    }

}

