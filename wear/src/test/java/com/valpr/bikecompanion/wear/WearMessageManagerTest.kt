package com.valpr.bikecompanion.wear

import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.NodeClient
import com.valpr.bikecompanion.shared.HapticAlertType
import com.valpr.bikecompanion.shared.PingPongMessage
import com.valpr.bikecompanion.shared.WearableProtocol
import com.valpr.bikecompanion.wear.haptics.WatchHapticManager
import com.valpr.bikecompanion.wear.messaging.WearMessageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearMessageManagerTest {

    private lateinit var context: Context
    private lateinit var hapticManager: WatchHapticManager
    private lateinit var messageClient: MessageClient
    private lateinit var nodeClient: NodeClient
    private lateinit var capabilityClient: CapabilityClient
    private lateinit var managerScope: TestScope
    private var currentTime = 10_000L

    private val sentPackets = mutableListOf<SentPacket>()

    data class SentPacket(val nodeId: String, val path: String, val data: ByteArray)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        hapticManager = mockk(relaxed = true)
        messageClient = mockk(relaxed = true)
        nodeClient = mockk(relaxed = true)
        capabilityClient = mockk(relaxed = true)
        managerScope = TestScope()
        sentPackets.clear()

        every { messageClient.sendMessage(any(), any(), any()) } answers {
            val nodeId = firstArg<String>()
            val path = secondArg<String>()
            val data = thirdArg<ByteArray>()
            sentPackets.add(SentPacket(nodeId, path, data))
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

    private fun createManager(): WearMessageManager = WearMessageManager(
        context = context,
        hapticManager = hapticManager,
        scope = managerScope,
        messageClientOverride = messageClient,
        nodeClientOverride = nodeClient,
        capabilityClientOverride = capabilityClient,
        clock = { currentTime }
    )

    private fun mockMessageEvent(path: String, data: ByteArray, sourceNodeId: String = "phone-node"): MessageEvent {
        val event = mockk<MessageEvent>(relaxed = true)
        every { event.path } returns path
        every { event.data } returns data
        every { event.sourceNodeId } returns sourceNodeId
        return event
    }

    @Test
    fun triggerManualSync_noPhoneConnected_handlesGracefully() = runTest {
        val manager = createManager()
        managerScope.runCurrent()

        manager.triggerManualSync()
        managerScope.runCurrent()

        assertEquals("SYNCING…", manager.pingFeedbackMessage.value)
        assertTrue(sentPackets.isEmpty())

        // After 2500ms timeout, shows PHONE APP NOT FOUND
        managerScope.testScheduler.advanceTimeBy(2600L)
        managerScope.runCurrent()
        assertEquals("PHONE APP NOT FOUND", manager.pingFeedbackMessage.value)

        // After 2000ms delay, clears to null
        managerScope.testScheduler.advanceTimeBy(2100L)
        managerScope.runCurrent()
        assertNull(manager.pingFeedbackMessage.value)
        manager.onDestroy()
    }

    @Test
    fun triggerManualSync_phoneConnected_dispatchesPing() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updatePhoneNode("phone-123", "Pixel 9", isAppReachable = true)
        managerScope.runCurrent()

        manager.triggerManualSync()
        managerScope.runCurrent()

        assertEquals("SYNCING…", manager.pingFeedbackMessage.value)
        val pingPackets = sentPackets.filter { it.path == WearableProtocol.PATH_PING }
        assertEquals(1, pingPackets.size)
        assertEquals("phone-123", pingPackets[0].nodeId)

        val ping = PingPongMessage.fromByteArray(pingPackets[0].data)
        assertNotNull(ping)
        assertEquals(currentTime, ping!!.timestampMs)
        manager.onDestroy()
    }

    @Test
    fun triggerManualSync_rapidTaps_debouncedWhileSyncing() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updatePhoneNode("phone-123", "Pixel 9", isAppReachable = true)
        managerScope.runCurrent()

        manager.triggerManualSync()
        managerScope.runCurrent()
        val initialPings = sentPackets.filter { it.path == WearableProtocol.PATH_PING }.size
        assertEquals(1, initialPings)

        // Rapid tap while already SYNCING… must be ignored
        manager.triggerManualSync()
        managerScope.runCurrent()
        val pingsAfterRapidTap = sentPackets.filter { it.path == WearableProtocol.PATH_PING }.size
        assertEquals(1, pingsAfterRapidTap)
        manager.onDestroy()
    }

    @Test
    fun onMessageReceived_pongWithinTimeout_setsConnectedAndHaptic() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updatePhoneNode("phone-123", "Pixel 9", isAppReachable = true)
        managerScope.runCurrent()

        manager.triggerManualSync()
        managerScope.runCurrent()
        assertEquals("SYNCING…", manager.pingFeedbackMessage.value)

        // Pong arrives 50ms later
        currentTime += 50L
        val pongData = PingPongMessage(10_000L).toByteArray()
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PONG, pongData, "phone-123"))
        managerScope.runCurrent()

        assertEquals("CONNECTED TO PHONE", manager.pingFeedbackMessage.value)
        verify(exactly = 1) { hapticManager.playAlert(HapticAlertType.RESUME_TRIGGERED) }

        // Clears feedback after 3000ms
        managerScope.testScheduler.advanceTimeBy(3100L)
        managerScope.runCurrent()
        assertNull(manager.pingFeedbackMessage.value)
        manager.onDestroy()
    }

    @Test
    fun onMessageReceived_stalePongAfterTimeout_isIgnored() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        manager.updatePhoneNode("phone-123", "Pixel 9", isAppReachable = true)
        managerScope.runCurrent()

        manager.triggerManualSync()
        managerScope.runCurrent()

        // Advance past 2500ms timeout
        managerScope.testScheduler.advanceTimeBy(2600L)
        managerScope.runCurrent()
        assertEquals("NO RESPONSE", manager.pingFeedbackMessage.value)

        // Delayed Pong arrives at 3000ms
        currentTime += 3000L
        val pongData = PingPongMessage(10_000L).toByteArray()
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PONG, pongData, "phone-123"))
        managerScope.runCurrent()

        // Should NOT trigger haptic or overwrite message with CONNECTED TO PHONE
        verify(exactly = 0) { hapticManager.playAlert(HapticAlertType.RESUME_TRIGGERED) }
        assertEquals("NO RESPONSE", manager.pingFeedbackMessage.value)
        manager.onDestroy()
    }

    @Test
    fun onMessageReceived_pingFromPhone_repliesWithPongAndHaptic() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        sentPackets.clear()

        val pingData = PingPongMessage(99_999L).toByteArray()
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PING, pingData, "phone-node-789"))
        managerScope.runCurrent()

        verify(exactly = 1) { hapticManager.playAlert(HapticAlertType.RESUME_TRIGGERED) }
        assertEquals("PHONE PING RECEIVED", manager.pingFeedbackMessage.value)

        val pongPackets = sentPackets.filter { it.path == WearableProtocol.PATH_PONG }
        assertEquals(1, pongPackets.size)
        assertEquals("phone-node-789", pongPackets[0].nodeId)
        assertEquals(WearableProtocol.PATH_PONG, pongPackets[0].path)
        val pongMsg = PingPongMessage.fromByteArray(pongPackets[0].data)
        assertEquals(99_999L, pongMsg?.timestampMs)

        // Duplicate delivery with identical timestamp must be ignored
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PING, pingData, "phone-node-789"))
        managerScope.runCurrent()
        val pongPacketsAfterDup = sentPackets.filter { it.path == WearableProtocol.PATH_PONG }
        assertEquals(1, pongPacketsAfterDup.size)
        verify(exactly = 1) { hapticManager.playAlert(HapticAlertType.RESUME_TRIGGERED) }
        manager.onDestroy()
    }

    @Test
    fun onMessageReceived_pingFromPhone_routesToSourceNode_whenPhoneNodeIdBlank() = runTest {
        val manager = createManager()
        managerScope.runCurrent()
        sentPackets.clear()

        // phoneNodeId is not yet populated
        assertNull(manager.phoneNodeId.value)

        val pingData = PingPongMessage(88_888L).toByteArray()
        manager.onMessageReceived(mockMessageEvent(WearableProtocol.PATH_PING, pingData, "direct-phone-node"))
        managerScope.runCurrent()

        val pongPackets = sentPackets.filter { it.path == WearableProtocol.PATH_PONG }
        assertEquals(1, pongPackets.size)
        assertEquals("direct-phone-node", pongPackets[0].nodeId)
        assertEquals(WearableProtocol.PATH_PONG, pongPackets[0].path)
        manager.onDestroy()
    }
}
