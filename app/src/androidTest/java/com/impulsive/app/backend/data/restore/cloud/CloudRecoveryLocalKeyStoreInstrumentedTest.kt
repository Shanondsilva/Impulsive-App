package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(
    AndroidJUnit4::class,
)
class CloudRecoveryLocalKeyStoreInstrumentedTest {
    private lateinit var store:
        CloudRecoveryLocalKeyStore

    @Before
    fun setUp() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        store =
            CloudRecoveryLocalKeyStore(
                context,
            )

        store.clearPermanently()
    }

    @After
    fun tearDown() {
        store.clearPermanently()
    }

    @Test
    fun storeAndLoadRoundTripUsesAndroidGeneratedGcmIv() {
        val original =
            ByteArray(
                CloudRecoveryDekBytes,
            ) { index ->
                (
                    index +
                        1
                ).toByte()
            }

        store.store(
            original,
        )

        val restored =
            store.load()

        assertNotNull(
            restored,
        )

        assertArrayEquals(
            original,
            restored,
        )

        restored?.fill(
            0,
        )

        original.fill(
            0,
        )
    }
}