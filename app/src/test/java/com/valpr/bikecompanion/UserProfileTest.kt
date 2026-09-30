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
        assertFalse("Unconfigured FTP must return false", defaultProfile.isFtpConfigured)
    }

    @Test
    fun configuredProfile_isFtpConfigured_isTrue() {
        val athleteProfile = UserProfile(
            ftp = 220,
            weightKg = 72.5f,
            age = 28,
            restingHeartRate = 52
        )
        assertEquals(220, athleteProfile.ftp)
        assertEquals(72.5f, athleteProfile.weightKg)
        assertEquals(28, athleteProfile.age)
        assertEquals(52, athleteProfile.restingHeartRate)
        assertEquals(3.034f, athleteProfile.wattsPerKg, 0.01f)
        assertTrue("Configured FTP must return true", athleteProfile.isFtpConfigured)
    }
}
