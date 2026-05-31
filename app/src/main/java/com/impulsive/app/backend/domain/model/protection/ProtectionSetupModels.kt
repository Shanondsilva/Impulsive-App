package com.impulsive.app.backend.domain.model.protection

/**
 * Setup items that decide whether Impulsive can protect the user in real trigger moments.
 *
 * This model is intentionally permission-transparent. It stores the setup state that the app UI
 * can explain and revisit later. Actual Android permission checks should still be performed by
 * platform helpers before starting monitoring or blocking.
 */
enum class ProtectionSetupItem(
    val storageValue: String,
    val title: String,
    val isCoreProtection: Boolean,
) {
    BlockedApps(
        storageValue = "blocked_apps",
        title = "Choose apps to protect",
        isCoreProtection = true,
    ),
    UsageAccess(
        storageValue = "usage_access",
        title = "Enable Usage Access",
        isCoreProtection = true,
    ),
    Notifications(
        storageValue = "notifications",
        title = "Allow protection notifications",
        isCoreProtection = true,
    ),
    UninstallProtection(
        storageValue = "uninstall_protection",
        title = "Enable uninstall protection",
        isCoreProtection = true,
    ),
    InterruptionPermission(
        storageValue = "interruption_permission",
        title = "Allow Impulsive to step in",
        isCoreProtection = true,
    ),
    BackgroundActivity(
        storageValue = "background_activity",
        title = "Allow background protection",
        isCoreProtection = true,
    ),
    WebsiteProtection(
        storageValue = "website_protection",
        title = "Protect risky websites",
        isCoreProtection = false,
    );

    companion object {
        fun fromStorageValue(value: String): ProtectionSetupItem? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class ProtectionSetupState(
    val usageAccessEnabled: Boolean = false,
    val selectedBlockedAppPackageNames: Set<String> = emptySet(),
    val websiteProtectionEnabled: Boolean = false,
    val interruptionPermissionEnabled: Boolean = false,
    val backgroundActivityEnabled: Boolean = false,
    val uninstallProtectionEnabled: Boolean = false,
    val notificationPermissionEnabled: Boolean = false,
    val skippedSetupItems: Set<ProtectionSetupItem> = emptySet(),
) {
    val blockedAppsSelected: Boolean
        get() = selectedBlockedAppPackageNames.isNotEmpty()

    val incompleteCoreProtectionItems: List<ProtectionSetupItem>
        get() = buildList {
            if (!blockedAppsSelected) add(ProtectionSetupItem.BlockedApps)
            if (!usageAccessEnabled) add(ProtectionSetupItem.UsageAccess)
            if (!interruptionPermissionEnabled) add(ProtectionSetupItem.InterruptionPermission)
            if (!backgroundActivityEnabled) add(ProtectionSetupItem.BackgroundActivity)
            if (!notificationPermissionEnabled) add(ProtectionSetupItem.Notifications)
            if (!uninstallProtectionEnabled) add(ProtectionSetupItem.UninstallProtection)
        }

    val skippedCoreProtectionItems: List<ProtectionSetupItem>
        get() = skippedSetupItems
            .filter { it.isCoreProtection }
            .sortedBy { it.ordinal }

    val profileBadgeShouldShow: Boolean
        get() = incompleteCoreProtectionItems.isNotEmpty() || skippedCoreProtectionItems.isNotEmpty()

    fun isComplete(item: ProtectionSetupItem): Boolean = when (item) {
        ProtectionSetupItem.BlockedApps -> blockedAppsSelected
        ProtectionSetupItem.UsageAccess -> usageAccessEnabled
        ProtectionSetupItem.Notifications -> notificationPermissionEnabled
        ProtectionSetupItem.UninstallProtection -> uninstallProtectionEnabled
        ProtectionSetupItem.InterruptionPermission -> interruptionPermissionEnabled
        ProtectionSetupItem.BackgroundActivity -> backgroundActivityEnabled
        ProtectionSetupItem.WebsiteProtection -> websiteProtectionEnabled
    }
}
