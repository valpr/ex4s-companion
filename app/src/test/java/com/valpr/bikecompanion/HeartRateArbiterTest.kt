package com.valpr.bikecompanion

import com.valpr.bikecompanion.companion.api.HeartRateArbiter
import com.valpr.bikecompanion.companion.api.HeartRateSource
import com.valpr.bikecompanion.companion.api.HrSample
import com.valpr.bikecompanion.companion.api.HrStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeartRateArbiterTest {

    private lateinit var arbiterScope: TestScope

    @Before
    fun setup() {
        arbiterScope = TestScope()
    }

    @After
    fun tearDown() {
        arbiterScope.cancel()
    }

    private class FakeHrSource(
        override val id: String,
        override val displayName: String
    ) : HeartRateSource {
        override val hrSample = MutableStateFlow<HrSample?>(null)
        override val hrStatus = MutableStateFlow(HrStatus.DISCONNECTED)
    }

    @Test
    fun singleSource_receivesSample_updatesStateAndDispatches() = runTest {
        var currentClock = 1000L
        var dispatchedBpm = 0
        var clearCount = 0

        val source = FakeHrSource("watch", "Pixel Watch")
        val arbiter = HeartRateArbiter(
            sources = listOf(source),
            onUpdateHeartRate = { dispatchedBpm = it },
            onClearHeartRate = { clearCount++ },
            scope = arbiterScope,
            clock = { currentClock }
        )

        arbiterScope.runCurrent()
        assertEquals(HrStatus.DISCONNECTED, arbiter.hrStatus.value)

        // Emit sample
        source.hrStatus.value = HrStatus.CONNECTED
        source.hrSample.value = HrSample(bpm = 145, timestampEpochMs = currentClock)
        arbiterScope.runCurrent()

        assertEquals(source, arbiter.activeSource.value)
        assertEquals(145, arbiter.currentBpm.value)
        assertEquals(HrStatus.CONNECTED, arbiter.hrStatus.value)
        assertEquals(145, dispatchedBpm)
        assertEquals(0, clearCount)
    }

    @Test
    fun stalenessWatchdog_clearsHeartRateAfterThreshold() = runTest {
        var currentClock = 10_000L
        var clearCount = 0

        val source = FakeHrSource("watch", "Pixel Watch")
        val arbiter = HeartRateArbiter(
            sources = listOf(source),
            onUpdateHeartRate = {},
            onClearHeartRate = { clearCount++ },
            scope = arbiterScope,
            clock = { currentClock }
        )

        source.hrStatus.value = HrStatus.CONNECTED
        source.hrSample.value = HrSample(bpm = 150, timestampEpochMs = currentClock)
        arbiterScope.runCurrent()

        assertEquals(150, arbiter.currentBpm.value)

        // Advance time by 13 seconds (exceeding 12s threshold)
        currentClock += 13_000L
        arbiterScope.advanceTimeBy(13_000L)
        arbiterScope.runCurrent()

        assertEquals(0, arbiter.currentBpm.value)
        assertEquals(HrStatus.STALE, arbiter.hrStatus.value)
        assertEquals(1, clearCount)
    }

    @Test
    fun disconnect_clearsHeartRateImmediately() = runTest {
        var currentClock = 10_000L
        var clearCount = 0

        val source = FakeHrSource("watch", "Pixel Watch")
        val arbiter = HeartRateArbiter(
            sources = listOf(source),
            onUpdateHeartRate = {},
            onClearHeartRate = { clearCount++ },
            scope = arbiterScope,
            clock = { currentClock }
        )

        source.hrStatus.value = HrStatus.CONNECTED
        source.hrSample.value = HrSample(bpm = 150, timestampEpochMs = currentClock)
        arbiterScope.runCurrent()

        // Disconnect
        source.hrStatus.value = HrStatus.DISCONNECTED
        arbiterScope.runCurrent()

        assertEquals(0, arbiter.currentBpm.value)
        assertEquals(HrStatus.DISCONNECTED, arbiter.hrStatus.value)
        assertEquals(1, clearCount)
    }

    @Test
    fun multiSource_fallsBackToSecondarySourceOnPrimaryStaleness() = runTest {
        var currentClock = 10_000L
        var lastBpm = 0

        val primary = FakeHrSource("strap", "Chest Strap")
        val secondary = FakeHrSource("watch", "Pixel Watch")
        val arbiter = HeartRateArbiter(
            sources = listOf(primary, secondary),
            onUpdateHeartRate = { lastBpm = it },
            scope = arbiterScope,
            clock = { currentClock }
        )

        primary.hrStatus.value = HrStatus.CONNECTED
        primary.hrSample.value = HrSample(bpm = 160, timestampEpochMs = currentClock)
        secondary.hrStatus.value = HrStatus.CONNECTED
        secondary.hrSample.value = HrSample(bpm = 158, timestampEpochMs = currentClock)
        arbiterScope.runCurrent()

        assertEquals(primary, arbiter.activeSource.value)
        assertEquals(160, lastBpm)

        // Primary goes stale: secondary receives fresh sample at +10s
        currentClock += 13_000L
        secondary.hrSample.value = HrSample(bpm = 155, timestampEpochMs = currentClock)
        arbiterScope.advanceTimeBy(13_000L)
        arbiterScope.runCurrent()

        assertEquals(secondary, arbiter.activeSource.value)
        assertEquals(155, arbiter.currentBpm.value)
        assertEquals(155, lastBpm)
    }
}
