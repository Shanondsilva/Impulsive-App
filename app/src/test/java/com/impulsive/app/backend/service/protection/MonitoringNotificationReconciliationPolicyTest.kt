package com.impulsive.app.backend.service.protection

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringNotificationReconciliationPolicyTest {

    @Test
    fun dismissedExistingForeground_keepsNotificationDismissed() {
        assertEquals(
            MonitoringNotificationReconciliationAction.KeepDismissed,
            MonitoringNotificationReconciliationPolicy.resolve(
                monitoringNotificationDismissed = true,
                hasPromotedToForeground = true,
            ),
        )
    }

    @Test
    fun dismissedFreshService_allowsMandatoryForegroundPromotion() {
        assertEquals(
            MonitoringNotificationReconciliationAction.PostGenericNotification,
            MonitoringNotificationReconciliationPolicy.resolve(
                monitoringNotificationDismissed = true,
                hasPromotedToForeground = false,
            ),
        )
    }

    @Test
    fun visibleExistingForeground_allowsNotificationUpdate() {
        assertEquals(
            MonitoringNotificationReconciliationAction.PostGenericNotification,
            MonitoringNotificationReconciliationPolicy.resolve(
                monitoringNotificationDismissed = false,
                hasPromotedToForeground = true,
            ),
        )
    }

    @Test
    fun visibleFreshService_allowsNotificationPromotion() {
        assertEquals(
            MonitoringNotificationReconciliationAction.PostGenericNotification,
            MonitoringNotificationReconciliationPolicy.resolve(
                monitoringNotificationDismissed = false,
                hasPromotedToForeground = false,
            ),
        )
    }
}
