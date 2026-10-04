package com.valpr.bikecompanion

import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.shared.HeartRateBatch
import com.valpr.bikecompanion.shared.PingPongMessage
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.wearable.PhoneWearableManager
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import com.valpr.bikecompanion.workout.WorkoutSessionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneWearableManagerTest {

    private lateinit var context: Context
    private lateinit var sessionManager: WorkoutSessionManager
    private lateinit var messageClient: MessageClient
    private lateinit var nodeClient: NodeClient
    private lateinit var capabilityClient: CapabilityClient
    private lateinit var managerScope: TestScope
    private var currentTime = 10_000L

    private val sessionStateFlow = MutableStateFlow(WorkoutSessionState())
    private val hapticAlertsFlow = MutableSharedFlow<HapticAlertType>(extraBufferCapacity = 16)
    private val sentMessages = mutableListOf<SentPacket>()

    data class SentPacket(val nodeId: String, val path: String, val data: ByteArray)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        every { sessionManager.sessionState } returns sessionStateFlow
        every { sessionManager.hapticAlerts } returns hapticAlertsFlow

        messageClient = mockk(relaxed = true)
        nodeClient = mockk(relaxed = true)
        capabilityClient = mockk(relaxed = true)
        managerScope = TestScope()
        sentMessages.clear()

        every { messageClient.sendMessage(any(), any(), any()) } answers {
            val nodeId = firstArg<String>()
            val path = secondArg<String>()
            val data = thirdArg<ByteArray>()
            sentMessages.add(SentPacket(nodeId, path, data))
            Tasks.forResult(1)
        }
        every { messageClient.addListener(any()) } returns Tasks.forResult(null)
        every { capabilityClient.addListener(any(), any<String>()) } returns Tasks.forResult(null)
        every { capabilityClient.getCapability(any(), any()) } returns Tasks.forResult(mockk(relaxed = true))
        every { nodeClient.connectedNodes } returns Tasks.forResult(emptyList())

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        every { Looper.myLooper() } returns null
    }

    @After
    fun tearDown() {
        managerScope.cancel()
        unmockkStatic(Looper::class)
        unmockkStatic(Log::class)
    }

    private fun createManager(): PhoneWearableManager = PhoneWearableManager(
        context = context,
        sessionManager = sessionManager,
        scope = managerScope,
        messageClientOverride = messageClient,
        nodeClientOverride = nodeClient,
        capabilityClientOverride = capabilityClient,
        clock = { currentTime }
    )

    private fun mockNode(id: String, displayName: String): Node {
        val node = mockk<Node>(relaxed = true)
        every { node.id } returns id
        every { node.displayName } returns displayName
        return node
    }

    private fun mockMessageEvent(path: String, data: ByteArray, sourceNodeId: String = "watch-123"): MessageEvent {
        val event = mockk<MessageEvent>(relaxed = true)
        every { event.path } returns path
        every { event.data } returns data
        every { event.sourceNodeId } returns sourceNodeId
        return event
    }

    @Test
    fun sendPing_noConnectedWatch_setsNoWatchConnected() = runTest {
        val manager = createManager()
        managerScope.runCurrent()

        manager.sendPing()
        managerScope.runCurrent()

        assertFalse(manager.watchState.value.isPinging)
        assertEquals("No watch connected", manager.watchState.value.pingStatusMessage)
        assertTrue(sentMessages.isEmpty())
        manager.onDestroy()
    }

    @Test
    fun sendPing_connectedWatch_dispatchesPingMessage() = runTest {
        val manager = createManager()
        managerScope.runCurrent() // Settle init refreshConnectedNodes
        manager.updateWatchNode(mockNode("watch-456", "My Watch"))

        manager.sendPing()
        managerScope.runCurrent()

        assertTrue(manager.watchState.value.isPinging)
        assertEquals("Pinging watch…", manager.watchState.value.pingStatusMessage)
        assertEquals(1, sentMessages.size)
        assertEquals("watch-456", sentMessages[0].nodeId)
        assertEquals(WearableProtocol.PATH_PING, sentMessages[0].path)

        val pingMsg = PingPongMessage.fromByteArray(sentMessages[0].data)
        assertNotNull(pingMsg)
        assertEquals(currentTime, pingMsg!!.timestampMs)
        manager.onDestroy()
    }

    @Test
    fun sendPing_rapidTaps_debouncedWhilePinging() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updateWatchNode(mockNode("watch-456", "My Watch"))

        manager.sendPing()
        managerScope.runCurrent()
        assertEquals(1, sentMessages.size)

        // Rapid tap while already pinging should be ignored
        manager.sendPing()
        managerScope.runCurrent()
        assertEquals(1, sentMessages.size)
        manager.onDestroy()
    }

    @Test
    fun sendPing_timesOutAfter2500ms_whenNoPongReceived() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updateWatchNode(mockNode("watch-456", "My Watch"))

        manager.sendPing()
        managerScope.runCurrent()
        assertTrue(manager.watchState.value.isPinging)

        // Advance time past 2500ms timeout
        managerScope.testScheduler.advanceTimeBy(2600L)
        managerScope.runCurrent()

        assertFalse(manager.watchState.value.isPinging)
        assertEquals("Ping timed out", manager.watchState.value.pingStatusMessage)
        manager.onDestroy()
    }

    @Test
    fun onMessageReceived_pongWithinTimeout_setsVerifiedLatency() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updateWatchNode(mockNode("watch-456", "My Watch"))

        manager.sendPing()
        managerScope.runCurrent()

        // Advance 42ms and simulate Pong response
        currentTime += 42L
        val pongData = PingPongMessage(10_000L).toByteArray()
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PONG, pongData, "watch-456"))
        managerScope.runCurrent()

        assertFalse(manager.watchState.value.isPinging)
        assertEquals(42L, manager.watchState.value.lastPingRoundTripMs)
        assertEquals("Verified (42ms)", manager.watchState.value.pingStatusMessage)

        // Advancing time past the initial 2500ms should NOT overwrite with "Ping timed out"
        managerScope.testScheduler.advanceTimeBy(3000L)
        managerScope.runCurrent()

        // Status clears after 4000ms schedule
        managerScope.testScheduler.advanceTimeBy(1500L)
        managerScope.runCurrent()
        assertEquals(null, manager.watchState.value.pingStatusMessage)
        manager.onDestroy()
    }

    @Test
    fun onMessageReceived_stalePongAfterTimeout_doesNotOverwriteStatus() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updateWatchNode(mockNode("watch-456", "My Watch"))

        manager.sendPing()
        managerScope.runCurrent()

        // Timeout fires at 2500ms
        managerScope.testScheduler.advanceTimeBy(2600L)
        managerScope.runCurrent()
        assertEquals("Ping timed out", manager.watchState.value.pingStatusMessage)

        // Stale pong arrives at 3000ms
        currentTime += 3000L
        val pongData = PingPongMessage(10_000L).toByteArray()
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PONG, pongData, "watch-456"))
        managerScope.runCurrent()

        // Status should NOT resurrect to "Verified"
        assertEquals("Ping timed out", manager.watchState.value.pingStatusMessage)
        manager.onDestroy()
    }

    @Test
    fun onMessageReceived_pingFromWatch_repliesWithPongAndDeduplicates() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updateWatchNode(mockNode("watch-456", "My Watch"))
        sentMessages.clear()

        val pingData = PingPongMessage(12_345L).toByteArray()
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PING, pingData, "watch-456"))
        managerScope.runCurrent()

        assertEquals(1, sentMessages.size)
        assertEquals("watch-456", sentMessages[0].nodeId)
        assertEquals(WearableProtocol.PATH_PONG, sentMessages[0].path)
        val pongMsg = PingPongMessage.fromByteArray(sentMessages[0].data)
        assertEquals(12_345L, pongMsg?.timestampMs)

        // Duplicate delivery with same timestamp must be ignored
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PING, pingData, "watch-456"))
        managerScope.runCurrent()
        assertEquals(1, sentMessages.size)
        manager.onDestroy()
    }

    @Test
    fun onMessageReceived_clutch_debouncesRapidBailouts() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updateWatchNode(mockNode("watch-456", "My Watch"))

        val clutchEvent = mockMessageEvent(WearableProtocol.PATH_ROTARY_BAILOUT, byteArrayOf(), "watch-456")
        manager.onMessageReceived(clutchEvent)
        managerScope.runCurrent()
        verify(exactly = 1) { sessionManager.toggleClutch() }

        // Second clutch event within 500ms window must be ignored to prevent immediately toggling off
        currentTime += 100L
        manager.onMessageReceived(clutchEvent)
        managerScope.runCurrent()
        verify(exactly = 1) { sessionManager.toggleClutch() }

        // After debounce window expires, new clutch event succeeds
        currentTime += 600L
        manager.onMessageReceived(clutchEvent)
        managerScope.runCurrent()
        verify(exactly = 2) { sessionManager.toggleClutch() }
        manager.onDestroy()
    }

    @Test
    fun staleHeartRate_watchdogClearsHeartRateInSessionManager() = runTest {
        val manager = createManager()
        managerScope.runCurrent()

        val hrPayload = HeartRateBatch(
            timestampMs = currentTime,
            bpmSamples = listOf(150),
            accuracy = 3
        ).toByteArray()
        val event = mockMessageEvent(WearableProtocol.PATH_HEART_RATE, hrPayload)
        manager.onMessageReceived(event)
        managerScope.runCurrent()

        verify { sessionManager.updateHeartRate(150) }

        // Advance time by 11.9s -> watchdog should not have fired yet
        managerScope.testScheduler.advanceTimeBy(11_900L)
        managerScope.runCurrent()
        verify(exactly = 0) { sessionManager.clearHeartRate() }

        // Advance past 12s -> watchdog fires and clears HR in sessionManager
        managerScope.testScheduler.advanceTimeBy(200L)
        managerScope.runCurrent()
        verify(exactly = 1) { sessionManager.clearHeartRate() }
        manager.onDestroy()
    }

    @Test
    fun peerDisconnect_clearsHeartRateInSessionManager() = runTest {
        val manager = createManager()
        managerScope.runCurrent()

        val node = mockNode("watch-456", "My Watch")
        manager.updateWatchNode(node)
        managerScope.runCurrent()

        manager.onPeerDisconnected(node)
        managerScope.runCurrent()

        verify { sessionManager.clearHeartRate() }
        manager.onDestroy()
    }
}
