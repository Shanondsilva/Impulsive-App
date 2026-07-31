package com.impulsive.app.backend.service.protection

internal enum class MonitoringNotificationReconciliationAction {
    PostGenericNotification,
    KeepDismissed,
}

internal object MonitoringNotificationReconciliationPolicy {

    fun resolve(
        monitoringNotificationDismissed: Boolean,
        hasPromotedToForeground: Boolean,
    ): MonitoringNotificationReconciliationAction =
        if (
            monitoringNotificationDismissed &&
            hasPromotedToForeground
        ) {
            MonitoringNotificationReconciliationAction.KeepDismissed
        } else {
            MonitoringNotificationReconciliationAction.PostGenericNotification
        }
}
