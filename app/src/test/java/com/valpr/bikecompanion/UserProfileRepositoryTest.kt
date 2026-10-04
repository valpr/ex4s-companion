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
        assertFalse(profile.useKarvonenZones)
    }

    @Test
    fun setUseKarvonenZones_persists() = runTest {
        val r = repo()
        assertFalse(r.userProfileFlow.first().useKarvonenZones)
        r.setUseKarvonenZones(true)
        assertTrue(r.userProfileFlow.first().useKarvonenZones)
        r.setUseKarvonenZones(false)
        assertFalse(r.userProfileFlow.first().useKarvonenZones)
    }

    @Test
    fun updateHeartRateSettings_coercesToRange() = runTest {
        val r = repo()
        r.updateHeartRateSettings(maxHr = 400, criticalHr = 10, restingHr = 20, lthr = 300, useKarvonen = true)
        val p = r.userProfileFlow.first()
        assertEquals(240, p.maxHeartRate)
        assertEquals(100, p.criticalHeartRate)
        assertEquals(30, p.restingHeartRate)
        assertEquals(220, p.lactateThresholdHeartRate)
        assertTrue(p.useKarvonenZones)
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

    @Test
    fun applyHealthImport_updatesVitalsAndStampsTimestamp() = runTest {
        val r = repo()
        val before = System.currentTimeMillis()
        r.applyHealthImport(weightKg = 71.5f, heightCm = 178.0f, restingHeartRate = 54)
        val p = r.userProfileFlow.first()
        assertEquals(71.5f, p.weightKg, 0.001f)
        assertEquals(178.0f, p.heightCm, 0.001f)
        assertEquals(54, p.restingHeartRate)
        assertTrue(p.lastUpdatedEpochMs >= before)
    }

    @Test
    fun applyHealthImport_nullsPreserveExistingVitals() = runTest {
        val r = repo()
        r.updateAthleteBio(age = 30, weightKg = 80.0f, heightCm = 180.0f, sex = BiologicalSex.MALE)
        val before = System.currentTimeMillis()
        r.applyHealthImport(weightKg = null, heightCm = null, restingHeartRate = null)
        val p = r.userProfileFlow.first()
        assertEquals(80.0f, p.weightKg, 0.001f)
        assertEquals(180.0f, p.heightCm, 0.001f)
        assertTrue(p.lastUpdatedEpochMs >= before)
    }

    @Test
    fun toggleFavoriteWorkout_addsAndRemoves() = runTest {
        val r = repo()
        assertTrue(r.userProfileFlow.first().favoriteWorkoutFilenames.isEmpty())

        r.toggleFavoriteWorkout("sweet_spot.zwo")
        assertTrue(r.userProfileFlow.first().favoriteWorkoutFilenames.contains("sweet_spot.zwo"))

        // Toggling same file removes it
        r.toggleFavoriteWorkout("sweet_spot.zwo")
        assertFalse(r.userProfileFlow.first().favoriteWorkoutFilenames.contains("sweet_spot.zwo"))
    }

    @Test
    fun removeFavoriteWorkout_explicitRemoval() = runTest {
        val r = repo()
        r.toggleFavoriteWorkout("workout_a.zwo")
        r.toggleFavoriteWorkout("workout_b.zwo")
        assertEquals(2, r.userProfileFlow.first().favoriteWorkoutFilenames.size)

        r.removeFavoriteWorkout("workout_a.zwo")
        val favs = r.userProfileFlow.first().favoriteWorkoutFilenames
        assertEquals(1, favs.size)
        assertFalse(favs.contains("workout_a.zwo"))
        assertTrue(favs.contains("workout_b.zwo"))

        // Removing non-existent is a no-op
        r.removeFavoriteWorkout("non_existent.zwo")
        assertEquals(1, r.userProfileFlow.first().favoriteWorkoutFilenames.size)
    }

    @Test
    fun allWriters_bumpLastUpdated() = runTest {
        val r = repo()
        val before = System.currentTimeMillis()
        r.updateUnitSystem(UnitSystem.IMPERIAL)
        r.updateEngineTuning()
        r.updateKeepScreenOn(false)
        r.updateAutoEnterPip(false)
        r.setHealthSyncEnabled(true)
        r.updateBeginnerPathDismissed(true)
        r.updateBeginnerPathCollapsed(true)
        assertTrue(r.userProfileFlow.first().lastUpdatedEpochMs >= before)
    }

    @Test
    fun toggleFavorite_preservesCaseMatchesCaseInsensitively() = runTest {
        val r = repo()
        r.toggleFavoriteWorkout("Sweet_Spot.ZWO")
        val favs = r.userProfileFlow.first().favoriteWorkoutFilenames
        assertTrue(favs.contains("Sweet_Spot.ZWO"))
        // Case-insensitive toggle removes the original-cased entry.
        r.toggleFavoriteWorkout("sweet_spot.zwo")
        assertTrue(r.userProfileFlow.first().favoriteWorkoutFilenames.isEmpty())
    }

    @Test
    fun toggleFavorite_blankIsNoOp() = runTest {
        val r = repo()
        r.toggleFavoriteWorkout("   ")
        assertTrue(r.userProfileFlow.first().favoriteWorkoutFilenames.isEmpty())
    }
}
