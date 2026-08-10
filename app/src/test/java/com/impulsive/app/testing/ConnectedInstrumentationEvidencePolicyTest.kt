package com.impulsive.app.testing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUD-005: an instrumentation test's name must describe only what its
 * assertions actually establish.
 *
 * The suite previously carried scenario names — accessibility, font scale, dark
 * mode, process recreation, IME — whose bodies proved only that the application
 * package exists, plus a release identity pinned to a version that had already
 * moved on. These locks stop both from returning.
 */
class ConnectedInstrumentationEvidencePolicyTest {

    private fun androidTestRoot(): File = listOf(
        File("src/androidTest/java"),
        File("app/src/androidTest/java"),
    ).firstOrNull { it.isDirectory }
        ?: error("Unable to locate Android instrumentation source root.")

    private fun androidTestSource(): String = androidTestRoot()
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }

    @Test
    fun `instrumentation tests contain no stale release identity pins`() {
        val source = androidTestSource()

        assertFalse(source.contains("assertEquals(27, BuildConfig.VERSION_CODE)"))
        assertFalse(source.contains("assertEquals(\"1.0.0\", BuildConfig.VERSION_NAME)"))

        /*
         * Generic too: no test should hard-pin release identity, or it needs
         * hand-editing every version bump and rots into a false failure.
         */
        assertFalse(
            Regex("""assertEquals\(\s*\d+\s*,\s*BuildConfig\.VERSION_CODE""")
                .containsMatchIn(source),
        )
        assertFalse(
            Regex("""assertEquals\(\s*"[^"]+"\s*,\s*BuildConfig\.VERSION_NAME""")
                .containsMatchIn(source),
        )
    }

    @Test
    fun `legacy shallow instrumentation helpers are removed`() {
        val source = androidTestSource()

        listOf(
            "assertRefinementRuntime",
            "assertTipsRuntime",
            "assertImpulsiveDebugTarget",
            "assertAppIdentity",
        ).forEach {
            assertFalse("Legacy shallow helper remains: $it", source.contains(it))
        }
    }

    @Test
    fun `package availability is not labelled as accessibility verification`() {
        val root = androidTestRoot()

        assertFalse(
            File(
                root,
                "com/impulsive/app/finalrepair/FinalUiAccessibilityInstrumentedSmokeTests.kt",
            ).exists(),
        )

        assertTrue(
            File(
                root,
                "com/impulsive/app/accessibility/" +
                    "RecoveryGameAccessibilityInstrumentedTest.kt",
            ).isFile,
        )
    }

    @Test
    fun `the accessibility suite uses real Compose semantics APIs`() {
        val test = File(
            androidTestRoot(),
            "com/impulsive/app/accessibility/RecoveryGameAccessibilityInstrumentedTest.kt",
        ).readText()

        listOf(
            "createComposeRule",
            "onNodeWithContentDescription",
            "performClick",
            "fetchSemanticsNode",
            "SemanticsActions.CustomActions",
            "performKeyInput",
            "assertIsNotEnabled",
        ).forEach {
            assertTrue("Accessibility suite must use $it", test.contains(it))
        }

        /*
         * A disabled-state test that only asserts the node exists proves
         * nothing about what assistive tech sees. Rhythm and Skyline must each
         * assert the semantic disabled state; more is fine as coverage grows.
         */
        val disabledAssertions = Regex("""\.assertIsNotEnabled\(\)""")
            .findAll(test)
            .count()

        assertTrue(
            "Rhythm and Skyline must both assert disabled semantics",
            disabledAssertions >= 2,
        )
    }

    @Test
    fun `Compose UI test dependencies remain declared`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val buildFile = File("build.gradle.kts").readText()

        assertTrue(catalog.contains("ui-test-junit4"))
        assertTrue(catalog.contains("ui-test-manifest"))
        assertTrue(buildFile.contains("androidTestImplementation(libs.compose.ui.test.junit4)"))
        assertTrue(buildFile.contains("debugImplementation(libs.compose.ui.test.manifest)"))
        assertTrue(buildFile.contains("androidTestImplementation(platform(libs.compose.bom))"))
    }

    @Test
    fun `semantic callbacks stay wired to the real production game actions`() {
        val rhythm = gameScreen("RhythmTilesScreen.kt")
        val skyline = gameScreen("SkylineResetScreen.kt")
        val cascade = gameScreen("BlockCascadeScreen.kt")

        // The extracted lane layer must still reach the real hit/miss path.
        assertTrue(rhythm.contains("RhythmLaneInteractionLayer("))
        assertTrue(rhythm.contains("onLaneActivated = activateLane"))
        assertTrue(rhythm.contains("viewModel.tapLane(lane)"))
        assertTrue(rhythm.contains("viewModel.tapEmpty()"))

        assertTrue(skyline.contains("onDrop = viewModel::drop"))

        assertTrue(cascade.contains("onMoveLeft = viewModel::moveLeft"))
        assertTrue(cascade.contains("onMoveRight = viewModel::moveRight"))
        assertTrue(cascade.contains("onRotate = viewModel::rotate"))
        assertTrue(
            Regex("""onSoftDrop\s*=\s*(viewModel::softDrop|\{[^}]*viewModel\.softDrop\(\))""")
                .containsMatchIn(cascade),
        )
    }

    private fun gameScreen(fileName: String): String = File(
        "src/main/java/com/impulsive/app/frontend/screens/games/$fileName",
    ).readText()
}
