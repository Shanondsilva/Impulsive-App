package com.impulsive.app.frontend.navigation

import com.impulsive.app.backend.domain.model.protection.BlockLaunchTarget
import com.impulsive.app.backend.domain.model.protection.BlockRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRequestNavigationHandoffTest {
    private val source =
        File(
            "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
        ).readNormalizedText()

    @Test
    fun allBlockTargetsMapToTheirDestinationRoutePatterns() {
        assertEquals(AppRoutes.ImpulsiveBlock, pattern(BlockLaunchTarget.BlockScreen))
        assertEquals(AppRoutes.AdaptiveMoment, pattern(BlockLaunchTarget.AdaptiveMoment))
        assertEquals(AppRoutes.RandomRecoveryGame, pattern(BlockLaunchTarget.RandomRecoveryGame))
        assertEquals(AppRoutes.ResetReadFallbackTask, pattern(BlockLaunchTarget.ReadingReset))
        assertEquals(AppRoutes.FocusRecovery, pattern(BlockLaunchTarget.FocusRecovery))
    }

    @Test
    fun randomRecoverySamePatternAndPackageMatches() {
        assertTrue(
            matches(
                route = AppRoutes.RandomRecoveryGame,
                packageName = "com.example.browser",
                target = BlockLaunchTarget.RandomRecoveryGame,
            ),
        )
    }

    @Test
    fun randomRecoverySamePatternButDifferentPackageDoesNotMatch() {
        assertFalse(
            matches(
                route = AppRoutes.RandomRecoveryGame,
                packageName = "com.other.browser",
                target = BlockLaunchTarget.RandomRecoveryGame,
            ),
        )
    }

    @Test
    fun blockScreenSamePatternPackageAndLabelMatches() {
        assertTrue(
            matches(
                route = AppRoutes.ImpulsiveBlock,
                packageName = "com.example.browser",
                sourceLabel = "Example Browser",
                target = BlockLaunchTarget.BlockScreen,
            ),
        )
    }

    @Test
    fun blockScreenDifferentPackageDoesNotMatch() {
        assertFalse(
            matches(
                route = AppRoutes.ImpulsiveBlock,
                packageName = "com.other.browser",
                sourceLabel = "Example Browser",
                target = BlockLaunchTarget.BlockScreen,
            ),
        )
    }

    @Test
    fun blockScreenDifferentLabelDoesNotMatch() {
        assertFalse(
            matches(
                route = AppRoutes.ImpulsiveBlock,
                packageName = "com.example.browser",
                sourceLabel = "Other Browser",
                target = BlockLaunchTarget.BlockScreen,
            ),
        )
    }

    @Test
    fun readingResetDestinationMatches() {
        assertTrue(
            matches(
                route = AppRoutes.ResetReadFallbackTask,
                target = BlockLaunchTarget.ReadingReset,
            ),
        )
    }

    @Test
    fun focusRecoveryDestinationMatches() {
        assertTrue(
            matches(
                route = AppRoutes.FocusRecovery,
                target = BlockLaunchTarget.FocusRecovery,
            ),
        )
    }

    @Test
    fun differingRoutePatternNeverMatches() {
        BlockLaunchTarget.entries.forEach { target ->
            assertFalse(
                matches(
                    route = AppRoutes.Home,
                    packageName = "com.example.browser",
                    sourceLabel = "Example Browser",
                    target = target,
                ),
            )
        }
    }

    @Test
    fun coldStartRequestIsHandledByTheNormalHandoff() {
        assertTrue(
            matches(
                route = AppRoutes.ImpulsiveBlock,
                packageName = "com.example.browser",
                sourceLabel = "Example Browser",
                target = BlockLaunchTarget.BlockScreen,
            ),
        )
        assertFalse(source.contains("request != mainGraphInitialBlockRequest"))
        assertFalse(source.contains("mainGraphInitialBlockRequest"))
    }

    @Test
    fun mainGraphHasFixedHomeStartDestination() {
        val mainGraph = source.substring(
            source.indexOf("navigation(\n            route = AppRoutes.Graph,"),
            source.indexOf("composable(AppRoutes.Home)"),
        )

        assertTrue(mainGraph.contains("startDestination = AppRoutes.Home"))
        assertFalse(source.contains("startDestination = mainStartDestination"))
        assertFalse(source.contains("val mainStartDestination"))
    }

    @Test
    fun readingFallbackRemainsANormalMainGraphDestination() {
        val mainGraph = source.substring(
            source.indexOf("navigation(\n            route = AppRoutes.Graph,"),
            source.indexOf("private fun ImpulsiveLoadingSurface"),
        )

        assertTrue(mainGraph.contains("composable(AppRoutes.ResetReadFallbackTask)"))
        assertEquals(
            AppRoutes.ResetReadFallbackTask,
            blockRequestDestinationRoutePattern(
                request(BlockLaunchTarget.ReadingReset),
            ),
        )
    }

    @Test
    fun generatedRoutesPreserveBlockSourceValues() {
        val routeBuilder = source.substring(
            source.indexOf("internal fun blockRequestDestinationRoute(request"),
            source.indexOf("internal fun blockRequestDestinationRoutePattern"),
        )

        assertTrue(routeBuilder.contains("AppRoutes.randomRecoveryGame(request.sourcePackageName)"))
        assertTrue(routeBuilder.contains("sourcePackageName = request.sourcePackageName"))
        assertTrue(routeBuilder.contains("sourceLabel = request.sourceLabel"))
    }

    @Test
    fun navigationDoesNotConsumeUntilDestinationReadyEffectRuns() {
        val navigation = source.substring(
            source.indexOf("LaunchedEffect(\n        initialBlockRequest,"),
            source.indexOf("LaunchedEffect(initialJournalNoteId, mainGraphAllowed)"),
        )
        val readyEffect = source.substring(
            source.indexOf("private fun BlockRequestDestinationReadyEffect"),
            source.indexOf("internal fun blockRequestDestinationRoute"),
        )

        assertFalse(navigation.contains("onBlockRequestConsumed()"))
        assertTrue(navigation.contains("val request = initialBlockRequest"))
        assertFalse(navigation.contains("mainGraphInitialBlockRequest"))
        assertTrue(navigation.contains("blockRequestDestinationMatches"))
        assertTrue(navigation.contains("Uri.decode"))
        assertTrue(navigation.contains("""getString("sourcePackageName")"""))
        assertTrue(navigation.contains("""getString("sourceLabel")"""))
        assertTrue(readyEffect.contains("withFrameNanos"))
        assertTrue(readyEffect.contains("lastReadyRequest"))
        assertTrue(readyEffect.contains("latestOnBlockRequestConsumed()"))
        // Seven: the six original destinations plus the protected Moment route.
        assertTrue(source.split("BlockRequestDestinationReadyEffect(").size - 1 == 7)
    }

    @Test
    fun loadingStateDrawsVisibleResetAwareSurface() {
        val loading = source.substring(
            source.indexOf("if (state.isLoading)"),
            source.indexOf("// Keep the protection monitor running"),
        )

        assertTrue(loading.contains("ImpulsiveLoadingSurface"))
        assertTrue(source.contains("CircularProgressIndicator()"))
        assertTrue(source.contains("Opening your reset…"))
        assertTrue(source.contains("Loading Impulsive…"))
    }

    private fun pattern(target: BlockLaunchTarget): String =
        blockRequestDestinationRoutePattern(request(target))

    private fun matches(
        route: String?,
        packageName: String? = null,
        sourceLabel: String? = null,
        target: BlockLaunchTarget,
    ): Boolean =
        blockRequestDestinationMatches(
            currentRoutePattern = route,
            currentSourcePackageName = packageName,
            currentSourceLabel = sourceLabel,
            request = request(target),
        )

    private fun request(target: BlockLaunchTarget) = BlockRequest(
        sourcePackageName = "com.example.browser",
        sourceLabel = "Example Browser",
        detectedAtMillis = 123L,
        launchTarget = target,
    )
}

private fun File.readNormalizedText(): String =
    readText()
        .replace(
            "\r\n",
            "\n",
        )
        .replace(
            '\r',
            '\n',
        )
