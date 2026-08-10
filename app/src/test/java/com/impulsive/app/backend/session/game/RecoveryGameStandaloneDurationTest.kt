package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.game.boundedDurationMillis
import org.junit.Assert.assertEquals
import org.junit.Test

class ReflexSupportCycleDurationTest {
    @Test fun standaloneReflexDurationUnchanged() =
        assertEquals(90_000L, RecoveryGameLaunchContext.Standalone.boundedDurationMillis(90_000L))
}

class RhythmSupportCycleDurationTest {
    @Test fun standaloneRhythmDurationUnchanged() =
        assertEquals(90_000L, RecoveryGameLaunchContext.Standalone.boundedDurationMillis(90_000L))
}

class BlockCascadeSupportCycleDurationTest {
    @Test fun standaloneBlockCascadeDurationUnchanged() =
        assertEquals(90_000L, RecoveryGameLaunchContext.Standalone.boundedDurationMillis(90_000L))
}

class SkylineSupportCycleDurationTest {
    @Test fun standaloneSkylineDurationUnchanged() =
        assertEquals(90_000L, RecoveryGameLaunchContext.Standalone.boundedDurationMillis(90_000L))
}
