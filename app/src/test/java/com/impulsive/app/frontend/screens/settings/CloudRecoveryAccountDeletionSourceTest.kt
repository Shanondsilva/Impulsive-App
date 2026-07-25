package com.impulsive.app.frontend.screens.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryAccountDeletionSourceTest {
    private val source =
        File(
            "src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
        ).readText()

    @Test
    fun `account deletion uses cloud-aware entry point`() {
        assertTrue(source.contains("fun startCloudAwareAccountDeletion()"))
        assertTrue(source.contains("CloudRecoveryDeletionCoordinator"))
        assertTrue(source.contains("startCloudAwareAccountDeletion()"))
    }

    @Test
    fun `upload work is cancelled before Drive deletion`() {
        val startIndex = source.indexOf("fun startCloudAwareAccountDeletion()")
        val startCancelIndex =
            source.indexOf("CloudRecoveryUploadScheduler.cancelAndAwait", startIndex)
        val deleteHelperIndex =
            source.indexOf("fun deleteDriveRecoveryThenStartAccountDeletion")
        val helperCancelIndex =
            source.indexOf("CloudRecoveryUploadScheduler.cancelAndAwait", deleteHelperIndex)
        val deleteIndex = source.indexOf(".deleteAllRecoveryFiles", deleteHelperIndex)

        assertTrue(startIndex >= 0)
        assertTrue(startCancelIndex > startIndex)
        assertTrue(deleteHelperIndex >= 0)
        assertTrue(helperCancelIndex > deleteHelperIndex)
        assertTrue(deleteIndex > helperCancelIndex)
    }

    @Test
    fun `Drive failure messages confirm Firebase account was not deleted`() {
        assertTrue(source.contains("Your Impulsive account has not been deleted"))
        assertTrue(source.contains("CloudRecoveryDeletionResult.AuthorizationRequired"))
        assertTrue(source.contains("is CloudRecoveryDeletionResult.Failed"))
    }

    @Test
    fun `Drive access token is not persisted`() {
        assertFalse(source.contains("setAccessToken"))
        assertFalse(source.contains("accessTokenPreferences"))
        assertFalse(source.contains("\"access_token\""))
    }

    @Test
    fun `account deletion does not rely on reinstall fragile local markers`() {
        val startIndex =
            source.indexOf(
                "fun startCloudAwareAccountDeletion()",
            )

        val functionSource =
            source.substring(
                startIndex,
                source.indexOf(
                    "var showExportPasswordDialog",
                    startIndex,
                ),
            )

        assertFalse(
            functionSource.contains(
                "driveRecoveryMayExist",
            ),
        )

        assertFalse(
            functionSource.contains(
                "localCloudRecoveryMetadataExists",
            ),
        )

        assertTrue(
            functionSource.contains(
                "CloudRecoveryUploadScheduler.cancelAndAwait",
            ),
        )

        assertTrue(
            functionSource.contains(
                "cloudRecoveryDeletionCoordinator.requestAuthorization",
            ),
        )
    }

    @Test
    fun `zero Drive files continues through successful deletion result`() {
        assertTrue(
            source.contains(
                "is CloudRecoveryDeletionResult.Success",
            ),
        )

        assertTrue(
            source.contains(
                "authViewModel.deleteAccount",
            ),
        )
    }
}
