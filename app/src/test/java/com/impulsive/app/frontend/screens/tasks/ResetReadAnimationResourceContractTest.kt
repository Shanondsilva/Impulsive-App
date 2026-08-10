package com.impulsive.app.frontend.screens.tasks

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetReadAnimationResourceContractTest {

    private val modelSource =
        File(
            "src/main/java/com/impulsive/app/backend/domain/model/tasks/" +
                "ResetReadModels.kt",
        ).readText()

    private val screenSource =
        File(
            "src/main/java/com/impulsive/app/frontend/screens/tasks/" +
                "ResetReadScreen.kt",
        ).readText()

    private val rawDirectory =
        File(
            "src/main/res/raw",
        )

    private val keepFile =
        File(
            rawDirectory,
            "com_impulsive_app_reset_reading_keep.xml",
        )

    @Test
    fun `reset reading uses typed animation values instead of dynamic names`() {
        assertTrue(
            modelSource.contains(
                "enum class ResetReadAnimation",
            ),
        )

        assertTrue(
            modelSource.contains(
                "val animation: ResetReadAnimation",
            ),
        )

        assertFalse(
            modelSource.contains(
                "rawResName",
            ),
        )

        assertFalse(
            screenSource.contains(
                "rawResName",
            ),
        )

        assertFalse(
            screenSource.contains(
                "getIdentifier(",
            ),
        )

        assertFalse(
            screenSource.contains(
                "\"Animation unavailable\"",
            ),
        )
    }

    @Test
    fun `every reset reading animation has one direct raw resource mapping`() {
        animationContracts
            .forEach { contract ->
                assertTrue(
                    "Missing model animation ${contract.enumValue}",
                    modelSource.contains(
                        "animation = ResetReadAnimation.${contract.enumValue}",
                    ),
                )

                assertTrue(
                    "Missing R.raw mapping for ${contract.resourceName}",
                    screenSource.contains(
                        "R.raw.${contract.resourceName}",
                    ),
                )

                assertEquals(
                    "Expected one model assignment for ${contract.enumValue}",
                    1,
                    modelSource
                        .windowed(
                            size =
                                "animation = ResetReadAnimation.${contract.enumValue}"
                                    .length,
                            step =
                                1,
                            partialWindows =
                                false,
                        )
                        .count {
                            it ==
                                "animation = ResetReadAnimation.${contract.enumValue}"
                        },
                )

                assertEquals(
                    "Expected one R.raw mapping for ${contract.resourceName}",
                    1,
                    screenSource
                        .windowed(
                            size =
                                "R.raw.${contract.resourceName}"
                                    .length,
                            step =
                                1,
                            partialWindows =
                                false,
                        )
                        .count {
                            it ==
                                "R.raw.${contract.resourceName}"
                        },
                )
            }
    }

    @Test
    fun `all required raw animation files exist`() {
        animationContracts
            .forEach { contract ->
                val animationFile =
                    File(
                        rawDirectory,
                        "${contract.resourceName}.json",
                    )

                assertTrue(
                    "Missing animation file ${animationFile.path}",
                    animationFile.isFile,
                )

                assertTrue(
                    "Animation file is empty ${animationFile.path}",
                    animationFile.length() > 0L,
                )
            }
    }

    @Test
    fun `keep contract names exactly the seven reset reading animations`() {
        assertTrue(
            "Reset Reading keep file is missing",
            keepFile.isFile,
        )

        invalidKeepFiles
            .forEach { invalidFile ->
                assertFalse(
                    "Invalid keep filename must not remain: ${invalidFile.name}",
                    invalidFile.exists(),
                )
            }

        val keepSource =
            keepFile.readText()

        animationContracts
            .forEach { contract ->
                assertTrue(
                    "Keep contract is missing ${contract.resourceName}",
                    keepSource.contains(
                        "@raw/${contract.resourceName}",
                    ),
                )
            }

        val keptRawResources =
            Regex(
                "@raw/[a-z0-9_]+",
            )
                .findAll(
                    keepSource,
                )
                .map {
                    it.value
                }
                .toList()

        assertEquals(
            animationContracts
                .map {
                    "@raw/${it.resourceName}"
                }
                .toSet(),
            keptRawResources.toSet(),
        )

        assertEquals(
            7,
            keptRawResources.size,
        )

        assertFalse(
            keepSource.contains(
                "*",
            ),
        )

        assertFalse(
            keepSource.contains(
                "tools:discard",
            ),
        )
    }

    @Test
    fun `keep filename is resource safe and project specific`() {
        assertEquals(
            "com_impulsive_app_reset_reading_keep.xml",
            keepFile.name,
        )

        assertTrue(
            keepFile.isFile,
        )

        assertTrue(
            ResourceSafeFileName.matches(
                keepFile.nameWithoutExtension,
            ),
        )

        assertFalse(
            keepFile.name.contains(
                ".",
                ignoreCase =
                    false,
            ) &&
                keepFile.name
                    .dropLast(
                        ".xml".length,
                    )
                    .contains(
                        ".",
                    ),
        )

        invalidKeepFiles
            .forEach { invalidFile ->
                assertFalse(
                    invalidFile.exists(),
                )
            }
    }

    private val invalidKeepFiles =
        listOf(
            File(
                rawDirectory,
                "com.impulsive.app.reset_reading.keep.xml",
            ),
            File(
                rawDirectory,
                "com.impulsive.app.reset_reading_keep.xml",
            ),
            File(
                rawDirectory,
                "reset_reading_keep.xml",
            ),
        )

    private data class AnimationContract(
        val enumValue: String,
        val resourceName: String,
    )

    private companion object {
        val ResourceSafeFileName =
            Regex(
                "[a-z0-9_]+",
            )

        val animationContracts =
            listOf(
                AnimationContract(
                    enumValue =
                        "UrgeWaveRiseFall",
                    resourceName =
                        "surf_urge_wave_rise_fall",
                ),
                AnimationContract(
                    enumValue =
                        "NinetySecondPeakSettle",
                    resourceName =
                        "ninety_second_rule_peak_settle",
                ),
                AnimationContract(
                    enumValue =
                        "DopaminePromisePath",
                    resourceName =
                        "dopamine_promise_path",
                ),
                AnimationContract(
                    enumValue =
                        "SlowerBreathing",
                    resourceName =
                        "breathe_slower_inhale_exhale",
                ),
                AnimationContract(
                    enumValue =
                        "WaitingChoiceClock",
                    resourceName =
                        "marshmallow_waiting_choice_clock",
                ),
                AnimationContract(
                    enumValue =
                        "HabitLoop",
                    resourceName =
                        "habit_loop_cue_routine_reward",
                ),
                AnimationContract(
                    enumValue =
                        "WillpowerBattery",
                    resourceName =
                        "willpower_battery_recharge",
                ),
            )
    }
}
