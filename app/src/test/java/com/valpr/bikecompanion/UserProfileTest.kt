package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.shared.BiologicalSex
import com.valpr.bikecompanion.shared.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {

    @Test
    fun defaultProfile_isFtpConfigured_isFalse() {
        val defaultProfile = UserProfile()
        assertEquals(0, defaultProfile.ftp)
        assertEquals(75.0f, defaultProfile.weightKg)
        assertEquals(60, defaultProfile.cadenceFloorRpm)
        assertEquals(75, defaultProfile.cadenceRecoveryRpm)
        assertEquals(0.05f, defaultProfile.ergKp)
        assertEquals(0.01f, defaultProfile.ergKi)
        assertEquals(30, defaultProfile.age)
        assertEquals(175.0f, defaultProfile.heightCm)
        assertEquals(BiologicalSex.MALE, defaultProfile.biologicalSex)
        assertEquals(60, defaultProfile.restingHeartRate)
        assertEquals(165, defaultProfile.lactateThresholdHeartRate)
        assertEquals(85, defaultProfile.preferredCadenceRpm)
        assertEquals(UnitSystem.METRIC, defaultProfile.unitSystem)
        assertEquals(0.0f, defaultProfile.wattsPerKg, 0.001f)
        assertFalse(defaultProfile.useKarvonenZones)
        assertFalse("Unconfigured FTP must return false", defaultProfile.isFtpConfigured)
    }

    @Test
    fun configuredProfile_isFtpConfigured_isTrue() {
        val athleteProfile = UserProfile(
            ftp = 220,
            weightKg = 72.5f,
            age = 28,
            restingHeartRate = 52,
            useKarvonenZones = true
        )
        assertEquals(220, athleteProfile.ftp)
        assertEquals(72.5f, athleteProfile.weightKg)
        assertEquals(28, athleteProfile.age)
        assertEquals(52, athleteProfile.restingHeartRate)
        assertTrue(athleteProfile.useKarvonenZones)
        assertEquals(3.034f, athleteProfile.wattsPerKg, 0.01f)
        assertTrue("Configured FTP must return true", athleteProfile.isFtpConfigured)
    }

    @Test
    fun calculateHrZone_delegatesToHrZoneWithKarvonenPreference() {
        val standardProfile = UserProfile(maxHeartRate = 200, restingHeartRate = 50, useKarvonenZones = false)
        // Standard % Max HR for 200: <120 = Z1, 120..139 = Z2
        assertEquals(1, standardProfile.calculateHrZone(119))
        assertEquals(2, standardProfile.calculateHrZone(120))

        val karvonenProfile = UserProfile(maxHeartRate = 200, restingHeartRate = 50, useKarvonenZones = true)
        // Karvonen for max 200, rest 50: hrr = 150.
        // Z1 max: 50 + 150*0.60 - 1 = 139. 120 is now Z1 (<60% HRR).
        assertEquals(1, karvonenProfile.calculateHrZone(120))
        assertEquals(2, karvonenProfile.calculateHrZone(140))
    }
}
