package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.BikeTelemetry
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.engine.ErgDecision
import com.valpr.bikecompanion.engine.ErgState
import com.valpr.bikecompanion.service.WorkoutNotificationContent
import com.valpr.bikecompanion.workout.SessionStatus
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutSegment
import com.valpr.bikecompanion.workout.WorkoutSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutNotificationContentTest {

    private fun decision(state: ErgState) = ErgDecision(
        state = state,
        targetResistance = 8,
        shouldSendBleCommand = false,
        smoothedCadence = 80.0,
        nominalResistance = 12,
        trimOffset = 0,
        powerErrorWatts = 0
    )

    private fun session(
        status: SessionStatus = SessionStatus.IDLE,
        workoutName: String? = null,
        targetWatts: Int? = null,
        elapsed: Int = 0,
        telemElapsed: Int = 0,
        watts: Int = 150,
        cadence: Int = 85,
        resistance: Int = 12,
        ergState: ErgState? = null
    ): WorkoutSessionState {
        val workout = workoutName?.let {
            Workout(name = it, segments = listOf(WorkoutSegment.SteadyState(600, 0.9f)))
        }
        return WorkoutSessionState(
            status = status,
            workout = workout,
            elapsedSeconds = elapsed,
            targetWatts = targetWatts,
            ergDecision = ergState?.let { decision(it) },
            latestTelemetry = BikeTelemetry(
                cadenceRpm = cadence,
                resistanceLevel = resistance,
                estimatedWatts = watts,
                elapsedSeconds = telemElapsed
            )
        )
    }

    @Test
    fun running_structured_showsNameWattsAndTarget() {
        val title = WorkoutNotificationContent.buildTitle(
            session(SessionStatus.RUNNING, "Sweet Spot", 180, watts = 175),
            BleConnectionState.Connected("EX-4S", "AA:BB")
        )
        assertEquals("Sweet Spot: 175W (Target 180W)", title)
    }

    @Test
    fun running_prefersSmoothedDisplayWatts_overRawTelemetry() {
        val base = session(SessionStatus.RUNNING, "Sweet Spot", 180, watts = 200)
        val smoothed = base.copy(displayWatts = 183)
        val title = WorkoutNotificationContent.buildTitle(
            smoothed,
            BleConnectionState.Connected("EX-4S", "AA:BB")
        )
        assertEquals("Sweet Spot: 183W (Target 180W)", title)
    }

    @Test
    fun running_freeRide_noTargetSuffix() {
        val title = WorkoutNotificationContent.buildTitle(
            session(SessionStatus.RUNNING, null, null, watts = 120),
            BleConnectionState.Disconnected
        )
        assertEquals("Free Ride: 120W", title)
    }

    @Test
    fun paused_showsPausedWattsRpm() {
        val title = WorkoutNotificationContent.buildTitle(
            session(SessionStatus.PAUSED, watts = 90, cadence = 60),
            BleConnectionState.Connected("EX-4S", "AA")
        )
        assertEquals("Workout Paused: 90W | 60 RPM", title)
    }

    @Test
    fun idle_connected_showsConnectedReadout() {
        val title = WorkoutNotificationContent.buildTitle(
            session(watts = 100, cadence = 80),
            BleConnectionState.Connected("EX-4S", "AA")
        )
        assertEquals("EX-4S Connected: 100W | 80 RPM", title)
    }

    @Test
    fun idle_connecting_handshaking_fallback_branches() {
        assertEquals(
            "Connecting to EX-4S...",
            WorkoutNotificationContent.buildTitle(session(), BleConnectionState.Connecting("EX-4S", "AA"))
        )
        assertEquals(
            "Handshaking with EX-4S...",
            WorkoutNotificationContent.buildTitle(session(), BleConnectionState.Handshaking)
        )
        assertEquals(
            "EX-4S Companion",
            WorkoutNotificationContent.buildTitle(session(), BleConnectionState.Disconnected)
        )
        assertEquals(
            "EX-4S Companion",
            WorkoutNotificationContent.buildTitle(session(), BleConnectionState.Scanning)
        )
        // RUNNING/PAUSED take precedence over connection state
        assertTrue(
            WorkoutNotificationContent.buildTitle(
                session(SessionStatus.RUNNING, "W", 200),
                BleConnectionState.Connecting("X", "Y")
            ).startsWith("W:")
        )
    }

    @Test
    fun ergSuffix_matrix() {
        assertEquals("", WorkoutNotificationContent.ergSuffix(session()))
        assertEquals(
            " • BAILOUT (Spin >75)",
            WorkoutNotificationContent.ergSuffix(session(ergState = ErgState.CADENCE_FLOOR_BAILOUT))
        )
        assertEquals(
            " • CLUTCH (Paused)",
            WorkoutNotificationContent.ergSuffix(session(ergState = ErgState.MANUAL_BAILOUT))
        )
        assertEquals(
            " • ERG Active",
            WorkoutNotificationContent.ergSuffix(session(ergState = ErgState.ACTIVE))
        )
        assertEquals("", WorkoutNotificationContent.ergSuffix(session(ergState = ErgState.INACTIVE)))
        assertEquals("", WorkoutNotificationContent.ergSuffix(session(ergState = ErgState.FREE_RIDE)))
    }

    @Test
    fun timeSelection_sessionClockWheneverSessionExists_telemetryOnlyWhenIdle() {
        // RUNNING -> session.elapsedSeconds (125s = 02:05)
        assertEquals(
            "02:05",
            WorkoutNotificationContent.selectedTime(session(SessionStatus.RUNNING, elapsed = 125, telemElapsed = 10))
        )
        // PAUSED/COMPLETED -> frozen session clock, not the ticking bike clock
        assertEquals(
            "02:05",
            WorkoutNotificationContent.selectedTime(session(SessionStatus.PAUSED, elapsed = 125, telemElapsed = 61))
        )
        assertEquals(
            "02:05",
            WorkoutNotificationContent.selectedTime(
                session(SessionStatus.COMPLETED, elapsed = 125, telemElapsed = 61)
            )
        )
        // IDLE (no session) -> telemetry clock (61s = 01:01)
        assertEquals(
            "01:01",
            WorkoutNotificationContent.selectedTime(session(telemElapsed = 61))
        )
    }

    @Test
    fun content_combinesCadenceResistanceTimeAndSuffix() {
        val s =
            session(
                SessionStatus.RUNNING,
                "W",
                200,
                elapsed = 65,
                cadence = 90,
                resistance = 14,
                ergState = ErgState.ACTIVE
            )
        assertEquals(
            "Cadence: 90 RPM • L14 • Time: 01:05 • ERG Active",
            WorkoutNotificationContent.buildContent(s)
        )
        val bailout =
            session(
                SessionStatus.PAUSED,
                elapsed = 45,
                telemElapsed = 30,
                cadence = 40,
                resistance = 8,
                ergState = ErgState.CADENCE_FLOOR_BAILOUT
            )
        assertEquals(
            "Cadence: 40 RPM • L8 • Time: 00:45 • BAILOUT (Spin >75)",
            WorkoutNotificationContent.buildContent(bailout)
        )
    }
}
