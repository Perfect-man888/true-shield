package com.trueshield.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteTest {
    @Test
    fun riskDetailBuildsExpectedRoute() {
        assertEquals(
            "risk/detail/event-123",
            AppRoute.RiskDetail.createRoute("event-123"),
        )
    }

    @Test
    fun riskFeedbackBuildsExpectedRoute() {
        assertEquals(
            "risk/feedback/event-123",
            AppRoute.RiskFeedback.createRoute("event-123"),
        )
    }

    @Test
    fun familyAlertDetailBuildsExpectedRoute() {
        assertEquals(
            "family_alert_detail/family-1/alert-2",
            AppRoute.FamilyAlertDetail.createRoute(
                familyId = "family-1",
                alertId = "alert-2",
            ),
        )
    }

    @Test
    fun familyAlertPolicyBuildsExpectedRoute() {
        assertEquals(
            "family_alert_policy/family-1",
            AppRoute.FamilyAlertPolicy.createRoute("family-1"),
        )
    }
}
