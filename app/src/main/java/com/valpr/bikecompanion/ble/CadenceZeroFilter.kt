package com.valpr.bikecompanion.ble

/**
 * Dropout guard for 0-RPM cadence frames.
 *
 * A single 0xD1 frame reporting 0 cadence is far more likely a BLE dropout,
 * pedal micro-pause, or corrupted byte (RX frames carry no validated checksum)
 * than a genuine stop. Accepting it instantly zeroes watts, trips
 * CADENCE_FLOOR_BAILOUT, and bakes a 0 into history — so the first zero in a
 * streak holds the last good values and only a sustained zero is accepted.
 *
 * Pure logic (no Android types) so it stays plain-JUnit per AGENTS.md §8;
 * [com.valpr.bikecompanion.ble.EchelonBleManager] owns the streak counter.
 */
object CadenceZeroFilter {

    /** Consecutive zero frames required before a 0 is accepted as a genuine stop. */
    const val REQUIRED_CONSECUTIVE_ZEROS = 2

    /**
     * Next zero-streak count given the incoming frame. Any non-zero cadence
     * resets the streak.
     */
    fun nextZeroStreak(currentStreak: Int, cadenceRpm: Int): Int = if (cadenceRpm <= 0) currentStreak + 1 else 0

    /**
     * True when this frame's zero (with [streakIncludingCurrent] from
     * [nextZeroStreak]) should overwrite telemetry. The first zero of a
     * streak returns false — the caller must hold last good values.
     */
    fun shouldAcceptZero(streakIncludingCurrent: Int): Boolean = streakIncludingCurrent >= REQUIRED_CONSECUTIVE_ZEROS
}
