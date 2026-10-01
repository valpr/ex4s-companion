package com.valpr.bikecompanion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.valpr.bikecompanion.data.UserProfileRepository
import com.valpr.bikecompanion.shared.BiologicalSex
import com.valpr.bikecompanion.shared.UnitSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo(): UserProfileRepository {
        val factory = PreferenceDataStoreFactory.create(
            produceFile = { File(tmp.root, "profile_test.preferences_pb") }
        )
        return UserProfileRepository(factory)
    }

    @Test
    fun defaults_unconfiguredFtp() = runTest {
        val profile = repo().userProfileFlow.first()
        assertFalse(profile.isFtpConfigured)
        assertEquals(75.0f, profile.weightKg, 0.001f)
        assertEquals(30, profile.age)
        assertEquals(175.0f, profile.heightCm, 0.001f)
        assertEquals(BiologicalSex.MALE, profile.biologicalSex)
        assertEquals(60, profile.restingHeartRate)
        assertEquals(165, profile.lactateThresholdHeartRate)
        assertEquals(85, profile.preferredCadenceRpm)
        assertEquals(UnitSystem.METRIC, profile.unitSystem)
        assertTrue(profile.keepScreenOn)
        assertFalse(profile.beginnerPathDismissed)
        assertFalse(profile.beginnerPathCollapsed)
    }

    @Test
    fun updateFtp_persistsAndCoerces() = runTest {
        val r = repo()
        r.updateFtp(220)
        assertTrue(r.userProfileFlow.first().isFtpConfigured)
        assertEquals(220, r.userProfileFlow.first().ftp)
        r.updateFtp(-5)
        assertEquals(0, r.userProfileFlow.first().ftp)
    }

    @Test
    fun updateHeartRateSettings_coercesToRange() = runTest {
        val r = repo()
        r.updateHeartRateSettings(maxHr = 400, criticalHr = 10, restingHr = 20, lthr = 300)
        val p = r.userProfileFlow.first()
        assertEquals(240, p.maxHeartRate)
        assertEquals(100, p.criticalHeartRate)
        assertEquals(30, p.restingHeartRate)
        assertEquals(220, p.lactateThresholdHeartRate)
    }

    @Test
    fun updateAthleteBio_persistsAndCoerces() = runTest {
        val r = repo()
        r.updateAthleteBio(age = 45, weightKg = 82.5f, heightCm = 183.0f, sex = BiologicalSex.FEMALE)
        val p = r.userProfileFlow.first()
        assertEquals(45, p.age)
        assertEquals(82.5f, p.weightKg, 0.001f)
        assertEquals(183.0f, p.heightCm, 0.001f)
        assertEquals(BiologicalSex.FEMALE, p.biologicalSex)
    }

    @Test
    fun updatePowerSettings_persists() = runTest {
        val r = repo()
        r.updatePowerSettings(ftp = 240, preferredCadenceRpm = 92)
        val p = r.userProfileFlow.first()
        assertEquals(240, p.ftp)
        assertEquals(92, p.preferredCadenceRpm)
    }

    @Test
    fun updateUnitSystem_persists() = runTest {
        val r = repo()
        r.updateUnitSystem(UnitSystem.IMPERIAL)
        val p = r.userProfileFlow.first()
        assertEquals(UnitSystem.IMPERIAL, p.unitSystem)
    }

    @Test
    fun updateEngineTuning_coerces() = runTest {
        val r = repo()
        r.updateEngineTuning(cadenceFloorRpm = 5, cadenceRecoveryRpm = 500, kp = 99f, ki = -1f)
        val p = r.userProfileFlow.first()
        assertEquals(40, p.cadenceFloorRpm)
        assertEquals(100, p.cadenceRecoveryRpm)
        assertEquals(0.5f, p.ergKp, 0.0001f)
    }

    @Test
    fun updateKeepScreenOn_persists() = runTest {
        val r = repo()
        assertTrue(r.userProfileFlow.first().keepScreenOn)
        r.updateKeepScreenOn(false)
        assertFalse(r.userProfileFlow.first().keepScreenOn)
        r.updateKeepScreenOn(true)
        assertTrue(r.userProfileFlow.first().keepScreenOn)
    }

    @Test
    fun updateBeginnerPathDismissed_persistsAndRestores() = runTest {
        val r = repo()
        assertFalse(r.userProfileFlow.first().beginnerPathDismissed)
        r.updateBeginnerPathDismissed(true)
        assertTrue(r.userProfileFlow.first().beginnerPathDismissed)
        r.updateBeginnerPathDismissed(false)
        assertFalse(r.userProfileFlow.first().beginnerPathDismissed)
    }

    @Test
    fun updateBeginnerPathCollapsed_persists() = runTest {
        val r = repo()
        assertFalse(r.userProfileFlow.first().beginnerPathCollapsed)
        r.updateBeginnerPathCollapsed(true)
        assertTrue(r.userProfileFlow.first().beginnerPathCollapsed)
    }
}
