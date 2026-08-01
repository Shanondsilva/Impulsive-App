package com.impulsive.app.frontend.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundOwnershipSourceTest {
    private val topAppBar = source("frontend/components/ImpulsiveTopAppBar.kt")
    private val momentPlanScreens = source("frontend/screens/adaptive/MomentPlanScreens.kt")
    private val tipsScreens = source("frontend/screens/tips/TipsScreens.kt")
    private val whatWorksForMe = source("frontend/screens/adaptive/WhatWorksForMeScreen.kt")

    @Test
    fun topAppBarExposesContainerColorDefaultingToThemeBackground() {
        assertTrue(topAppBar.contains("containerColor: Color = MaterialTheme.colorScheme.background"))
        assertTrue(topAppBar.contains(".background(containerColor)"))
        assertFalse(topAppBar.contains(".background(MaterialTheme.colorScheme.background)"))
    }

    @Test
    fun momentPlanListScreenHasSingleAmbientBackgroundOwner() {
        val listScreen = momentPlanScreens.section(
            "fun MomentPlanListScreen(",
            "private fun MomentPlanInfoDialog(",
        )
        assertEquals(1, listScreen.count("ImpulsiveAmbientBackground("))
        assertEquals(1, listScreen.count(".background(MaterialTheme.colorScheme.background)"))
        assertTrue(listScreen.contains("containerColor = Color.Transparent"))
    }

    @Test
    fun momentPlanTopRowStaysTransparentWithSingleStatusBarPadding() {
        val listScreen = momentPlanScreens.section(
            "fun MomentPlanListScreen(",
            "private fun MomentPlanInfoDialog(",
        )
        val topBar = listScreen.section("topBar = {", "snackbarHost = {")
        assertFalse(topBar.contains(".background(MaterialTheme.colorScheme.background)"))
        assertEquals(1, topBar.count(".statusBarsPadding()"))
    }

    @Test
    fun momentPlanContentBoxDoesNotReapplySolidBackground() {
        val listScreen = momentPlanScreens.section(
            "fun MomentPlanListScreen(",
            "private fun MomentPlanInfoDialog(",
        )
        val content = listScreen.substring(listScreen.indexOf("{ padding ->"))
        assertFalse(content.contains(".background(MaterialTheme.colorScheme.background)"))
    }

    @Test
    fun tipsScaffoldOwnsExactlyOneAmbientBackgroundBeforeScaffold() {
        assertTrue(
            tipsScreens.contains(
                "import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground",
            ),
        )
        val scaffold = tipsScreens.section(
            "private fun TipsScaffold(",
            "private fun TipListCard(",
        )
        assertEquals(1, scaffold.count("ImpulsiveAmbientBackground("))

        // Search from after the function's own opening brace so the
        // "TipsScaffold(" declaration itself cannot be mistaken for the
        // "Scaffold(" composable call further down.
        val bodyStart = scaffold.indexOf(") {")
        val ambientIndex = scaffold.indexOf("ImpulsiveAmbientBackground(", bodyStart)
        val scaffoldCallIndex = scaffold.indexOf("Scaffold(", bodyStart)
        assertTrue(ambientIndex >= 0)
        assertTrue(scaffoldCallIndex > ambientIndex)
        assertTrue(scaffold.contains(".background(MaterialTheme.colorScheme.background)"))
    }

    @Test
    fun tipsPassesTransparentContainerColorToSharedTopBarAndScaffold() {
        val scaffold = tipsScreens.section(
            "private fun TipsScaffold(",
            "private fun TipListCard(",
        )
        assertEquals(2, scaffold.count("containerColor = Color.Transparent"))
        assertTrue(scaffold.contains("ImpulsiveTopAppBar("))
    }

    @Test
    fun tipsScaffoldContentColorRemainsOnBackground() {
        val scaffold = tipsScreens.section(
            "private fun TipsScaffold(",
            "private fun TipListCard(",
        )
        assertTrue(scaffold.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
    }

    @Test
    fun tipsDoesNotCreateASecondAmbientLayerInEitherScreen() {
        val tipsScreenFunction = tipsScreens.section("fun TipsScreen(", "fun TipDetailScreen(")
        val tipDetailScreenFunction = tipsScreens.section(
            "fun TipDetailScreen(",
            "private fun TipsScaffold(",
        )
        assertFalse(tipsScreenFunction.contains("ImpulsiveAmbientBackground"))
        assertFalse(tipDetailScreenFunction.contains("ImpulsiveAmbientBackground"))
    }

    @Test
    fun whatWorksForMeUsesTransparentTopAppBarColorsOverOuterBackgroundOwner() {
        assertTrue(
            whatWorksForMe.contains(".background(MaterialTheme.colorScheme.background)") ||
                whatWorksForMe.contains(".background(colorScheme.background)"),
        )
        assertTrue(whatWorksForMe.contains("containerColor = Color.Transparent"))
        assertTrue(whatWorksForMe.contains("TopAppBarDefaults.topAppBarColors("))
        val colors = whatWorksForMe.section(
            "colors = TopAppBarDefaults.topAppBarColors(",
            ")",
        )
        assertTrue(colors.contains("containerColor = Color.Transparent"))
        assertTrue(colors.contains("scrolledContainerColor = Color.Transparent"))
    }

    @Test
    fun whatWorksForMeOwnsExactlyOneAmbientBackgroundOwnerOutsideContent() {
        assertTrue(
            whatWorksForMe.contains(
                "import com.impulsive.app.frontend.components.ImpulsiveAmbientBackground",
            ),
        )
        assertEquals(1, whatWorksForMe.count("ImpulsiveAmbientBackground("))
        val content = whatWorksForMe.section(
            "private fun WhatWorksContent(",
            "private fun InterventionCard(",
        )
        assertFalse(content.contains("ImpulsiveAmbientBackground"))
    }

    @Test
    fun whatWorksForMeScaffoldExposesOnBackgroundContentColor() {
        assertTrue(
            whatWorksForMe.contains("contentColor = colorScheme.onBackground") ||
                whatWorksForMe.contains("contentColor = MaterialTheme.colorScheme.onBackground"),
        )
    }

    @Test
    fun whatWorksForMeTopAppBarSpecifiesNavigationAndTitleContentColors() {
        val colors = whatWorksForMe.section(
            "colors = TopAppBarDefaults.topAppBarColors(",
            ")",
        )
        assertTrue(colors.contains("navigationIconContentColor ="))
        assertTrue(colors.contains("titleContentColor ="))
    }

    @Test
    fun whatWorksForMeInformationButtonSitsInsideTheTitleRowNotActions() {
        val titleBlock = whatWorksForMe.section("title = {", "navigationIcon = {")
        assertTrue(titleBlock.contains("Row("))
        assertTrue(titleBlock.contains("IconButton("))
        assertTrue(titleBlock.contains("Icons.Outlined.Info"))
        assertFalse(whatWorksForMe.contains("actions = {"))
    }

    @Test
    fun insightCardSetsOnSurfaceContentColor() {
        val insightCard = whatWorksForMe.section(
            "private fun InsightCard(",
            "private fun SectionHeading(",
        )
        assertTrue(insightCard.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
    }

    @Test
    fun whatWorksForMeExposesInformationActionAndDialog() {
        assertTrue(whatWorksForMe.contains("Icons.Outlined.Info"))
        assertTrue(whatWorksForMe.contains("showInformation"))
        assertTrue(whatWorksForMe.contains("rememberSaveable"))
        assertTrue(whatWorksForMe.contains("WhatWorksForMeInfoDialog"))
        assertTrue(whatWorksForMe.contains("AlertDialog("))
    }

    @Test
    fun whatWorksForMeRetainsNonCausalStatement() {
        assertTrue(
            whatWorksForMe.contains(
                "Later use is a factual observation. It does not show that practice caused the use.",
            ),
        )
    }

    private fun source(path: String): String =
        File("src/main/java/com/impulsive/app/$path").readText()

    private fun String.section(from: String, to: String): String =
        substring(indexOf(from), indexOf(to, indexOf(from) + from.length))

    private fun String.count(value: String): Int =
        windowed(value.length, step = 1).count { it == value }
}
