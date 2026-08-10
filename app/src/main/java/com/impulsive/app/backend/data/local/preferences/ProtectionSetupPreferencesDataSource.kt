package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.backend.domain.model.protection.WebsiteProtectionDisclosurePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protectionSetupDataStore by preferencesDataStore(
    name = "protection_setup_state",
)

class ProtectionSetupPreferencesDataSource(
    context: Context,
) {
    private val dataStore = context.applicationContext.protectionSetupDataStore

    val state: Flow<ProtectionSetupState> = dataStore.data.map { preferences ->
        ProtectionSetupState(
            isLoaded = true,
            usageAccessEnabled = preferences[UsageAccessEnabledKey] ?: false,
            selectedBlockedAppPackageNames =
                preferences[SelectedBlockedAppPackageNamesKey].toStringSet(),
            websiteProtectedAppPackageNames =
                preferences[WebsiteProtectedAppPackageNamesKey].toStringSet(),
            appProtectionMonitorEnabled = preferences[AppProtectionMonitorEnabledKey] ?: true,
            protectionMonitorTransitionCompleted =
                preferences[ProtectionMonitorTransitionCompletedKey] ?: false,
            websiteProtectionEnabled = preferences[WebsiteProtectionEnabledKey] ?: false,
            websiteProtectionAlwaysOn = preferences[WebsiteProtectionAlwaysOnKey] ?: false,
            websiteProtectionDisclosureConsentVersion =
                preferences[
                    WebsiteProtectionDisclosureConsentVersionKey
                ] ?: 0,
            interruptionPermissionEnabled = preferences[InterruptionPermissionEnabledKey] ?: false,
            backgroundActivityEnabled = preferences[BackgroundActivityEnabledKey] ?: false,
            notificationPermissionEnabled = preferences[NotificationPermissionEnabledKey] ?: false,
            skippedSetupItems = preferences[SkippedSetupItemsKey].toProtectionSetupItemSet(),
        )
    }

    suspend fun setUsageAccessEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UsageAccessEnabledKey] = enabled
            if (enabled) preferences.removeSkippedItem(ProtectionSetupItem.UsageAccess)
        }
    }

    suspend fun setSelectedBlockedAppPackageNames(packageNames: Set<String>) {
        dataStore.edit { preferences ->
            val cleanedPackageNames = packageNames
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSortedSet()
            if (cleanedPackageNames.isEmpty()) {
                preferences.remove(SelectedBlockedAppPackageNamesKey)
            } else {
                preferences[SelectedBlockedAppPackageNamesKey] = cleanedPackageNames.toStoredValue()
                preferences.removeSkippedItem(ProtectionSetupItem.BlockedApps)
            }
        }
    }

    suspend fun setWebsiteProtectedAppPackageNames(packageNames: Set<String>) {
        dataStore.edit { preferences ->
            val cleaned = packageNames
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSortedSet()

            if (cleaned.isEmpty()) {
                preferences.remove(WebsiteProtectedAppPackageNamesKey)
            } else {
                preferences[WebsiteProtectedAppPackageNamesKey] = cleaned.toStoredValue()
            }
        }
    }

    suspend fun setAppProtectionMonitorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AppProtectionMonitorEnabledKey] = enabled
        }
    }

    suspend fun setProtectionMonitorTransitionCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ProtectionMonitorTransitionCompletedKey] = completed
        }
    }

    suspend fun setWebsiteProtectionEnabled(
        enabled:
            Boolean,
    ): Boolean {
        var applied =
            false

        dataStore.edit { preferences ->
            if (enabled) {
                val acceptedVersion =
                    preferences[
                        WebsiteProtectionDisclosureConsentVersionKey
                    ] ?: 0

                if (
                    !WebsiteProtectionDisclosurePolicy
                        .isCurrent(
                            acceptedVersion,
                        )
                ) {
                    return@edit
                }
            }

            preferences[
                WebsiteProtectionEnabledKey
            ] =
                enabled

            if (enabled) {
                preferences
                    .removeSkippedItem(
                        ProtectionSetupItem
                            .WebsiteProtection,
                    )
            }

            applied =
                true
        }

        return applied
    }

    suspend fun setWebsiteProtectionDisclosureConsentVersion(
        version:
            Int,
    ) {
        require(
            version >= 0,
        )

        dataStore.edit { preferences ->
            preferences[
                WebsiteProtectionDisclosureConsentVersionKey
            ] =
                version
        }
    }

    suspend fun setWebsiteProtectionAlwaysOn(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[WebsiteProtectionAlwaysOnKey] = enabled
        }
    }

    suspend fun setInterruptionPermissionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[InterruptionPermissionEnabledKey] = enabled
            if (enabled) preferences.removeSkippedItem(ProtectionSetupItem.InterruptionPermission)
        }
    }

    suspend fun setBackgroundActivityEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BackgroundActivityEnabledKey] = enabled
            if (enabled) preferences.removeSkippedItem(ProtectionSetupItem.BackgroundActivity)
        }
    }



    suspend fun setNotificationPermissionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NotificationPermissionEnabledKey] = enabled
            if (enabled) preferences.removeSkippedItem(ProtectionSetupItem.Notifications)
        }
    }

    suspend fun markSkipped(item: ProtectionSetupItem) {
        dataStore.edit { preferences ->
            val updatedItems = preferences[SkippedSetupItemsKey].toProtectionSetupItemSet() + item
            preferences[SkippedSetupItemsKey] = updatedItems.toSetupItemStoredValue()
        }
    }

    suspend fun clearSkipped(item: ProtectionSetupItem) {
        dataStore.edit { preferences ->
            preferences.removeSkippedItem(item)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun String?.toStringSet(): Set<String> {
        if (isNullOrBlank()) return emptySet()
        return split(StoredListSeparator)
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }

    private fun Set<String>.toStoredValue(): String =
        sorted().joinToString(StoredListSeparator)

    private fun String?.toProtectionSetupItemSet(): Set<ProtectionSetupItem> {
        if (isNullOrBlank()) return emptySet()
        return split(StoredListSeparator)
            .mapNotNull(ProtectionSetupItem::fromStorageValue)
            .toSet()
    }

    private fun Set<ProtectionSetupItem>.toSetupItemStoredValue(): String =
        sortedBy { it.ordinal }
            .joinToString(StoredListSeparator) { it.storageValue }

    private fun androidx.datastore.preferences.core.MutablePreferences.removeSkippedItem(
        item: ProtectionSetupItem,
    ) {
        val updatedItems = this[SkippedSetupItemsKey].toProtectionSetupItemSet() - item
        if (updatedItems.isEmpty()) {
            remove(SkippedSetupItemsKey)
        } else {
            this[SkippedSetupItemsKey] = updatedItems.toSetupItemStoredValue()
        }
    }

    private companion object {
        const val StoredListSeparator = ""
        val UsageAccessEnabledKey = booleanPreferencesKey("usage_access_enabled")
        val SelectedBlockedAppPackageNamesKey =
            stringPreferencesKey("selected_blocked_app_package_names")
        val WebsiteProtectedAppPackageNamesKey =
            stringPreferencesKey("website_protected_app_package_names")
        val AppProtectionMonitorEnabledKey = booleanPreferencesKey("app_protection_monitor_enabled")
        val ProtectionMonitorTransitionCompletedKey =
            booleanPreferencesKey("protection_monitor_transition_completed")
        val WebsiteProtectionEnabledKey = booleanPreferencesKey("website_protection_enabled")
        val WebsiteProtectionAlwaysOnKey = booleanPreferencesKey("website_protection_always_on")
        val WebsiteProtectionDisclosureConsentVersionKey =
            intPreferencesKey(
                "website_protection_disclosure_consent_version",
            )
        val InterruptionPermissionEnabledKey = booleanPreferencesKey("interruption_permission_enabled")
        val BackgroundActivityEnabledKey = booleanPreferencesKey("background_activity_enabled")
        val NotificationPermissionEnabledKey = booleanPreferencesKey("notification_permission_enabled")
        val SkippedSetupItemsKey = stringPreferencesKey("skipped_setup_items")
    }
}
