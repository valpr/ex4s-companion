package com.valpr.bikecompanion

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.valpr.bikecompanion.data.LegacyMigration
import com.valpr.bikecompanion.data.Profile
import com.valpr.bikecompanion.data.ProfileRepository
import com.valpr.bikecompanion.data.UserProfileRepository
import com.valpr.bikecompanion.data.userProfileDataStoreFor
import com.valpr.bikecompanion.health.HealthConnectManager
import com.valpr.bikecompanion.health.HealthConnectReader
import com.valpr.bikecompanion.history.WorkoutHistoryRepository
import com.valpr.bikecompanion.wearable.PhoneWearableManager
import com.valpr.bikecompanion.workout.WorkoutRepository
import com.valpr.bikecompanion.workout.WorkoutSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

class BikeApplication : Application() {

    lateinit var bike: com.valpr.bikecompanion.bike.api.BikeController
        private set

    val bleManager: com.valpr.bikecompanion.bike.api.BikeController
        get() = bike

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var userProfileRepository: UserProfileRepository
        private set

    lateinit var workoutRepository: WorkoutRepository
        private set

    lateinit var workoutSessionManager: WorkoutSessionManager
        private set

    lateinit var phoneWearableManager: PhoneWearableManager
        private set

    lateinit var heartRateArbiter: com.valpr.bikecompanion.companion.api.HeartRateArbiter
        private set

    lateinit var companionHub: com.valpr.bikecompanion.companion.api.CompanionHub
        private set

    lateinit var healthConnectManager: HealthConnectManager
        private set

    lateinit var healthConnectReader: HealthConnectReader
        private set

    lateinit var workoutHistoryRepository: WorkoutHistoryRepository
        private set

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        profileRepository = ProfileRepository(File(filesDir, "profiles_meta"))
        val active = ensureProfilesAndMigrate()
        _activeProfile.value = active
        bindActiveProfile(active.id, rebindSession = false)

        workoutRepository = WorkoutRepository(applicationContext).apply {
            importSampleWorkoutsIfEmpty()
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        bike = com.valpr.bikecompanion.bike.ble.BleBikeConnection(
            context = applicationContext,
            bluetoothAdapter = bluetoothAdapter,
            drivers = listOf(
                com.valpr.bikecompanion.bike.echelon.EchelonDriver(),
                com.valpr.bikecompanion.bike.ftms.FtmsDriver()
            )
        )

        workoutSessionManager = WorkoutSessionManager(
            bike = bike,
            userProfileRepository = userProfileRepository
        )

        phoneWearableManager = PhoneWearableManager(
            context = applicationContext
        )

        heartRateArbiter = com.valpr.bikecompanion.companion.api.HeartRateArbiter(
            sources = listOf(phoneWearableManager),
            onUpdateHeartRate = { bpm ->
                workoutSessionManager.updateHeartRate(bpm)
            },
            onClearHeartRate = {
                workoutSessionManager.clearHeartRate()
            }
        )

        companionHub = com.valpr.bikecompanion.companion.api.CompanionHub(
            providers = listOf(phoneWearableManager),
            workoutControl = workoutSessionManager,
            sessionState = workoutSessionManager.sessionState,
            hapticAlerts = workoutSessionManager.hapticAlerts
        )

        healthConnectManager = HealthConnectManager(
            context = applicationContext
        )

        healthConnectReader = HealthConnectReader(
            context = applicationContext
        )

        // workoutHistoryRepository already bound by bindActiveProfile(); guard for safety.
        if (!::workoutHistoryRepository.isInitialized) {
            workoutHistoryRepository = WorkoutHistoryRepository(
                ProfileRepository.historyDirFor(filesDir, active.id)
            )
        }
    }

    /**
     * Ensures a profile exists, migrating legacy single-user data
     * (`user_profile` DataStore + `filesDir/history/`) into the first profile.
     */
    private fun ensureProfilesAndMigrate(): Profile {
        val hasLegacy = LegacyMigration.hasLegacyData(filesDir)
        val legacyHistoryDir = File(filesDir, WorkoutHistoryRepository.HISTORY_DIR)
            .takeIf { hasLegacy }
        val profile = profileRepository.ensureInitialized(
            legacyHistoryDir = legacyHistoryDir,
            hasLegacyUserProfile = hasLegacy
        )
        if (hasLegacy) {
            LegacyMigration.migrate(filesDir, profile.id)
        }
        return profile
    }

    private fun bindActiveProfile(profileId: String, rebindSession: Boolean) {
        userProfileRepository = UserProfileRepository(applicationContext.userProfileDataStoreFor(profileId))
        workoutHistoryRepository = WorkoutHistoryRepository(
            ProfileRepository.historyDirFor(filesDir, profileId)
        )
        if (rebindSession && ::workoutSessionManager.isInitialized) {
            workoutSessionManager.bindUserProfileFlow(userProfileRepository.userProfileFlow)
        }
    }

    /** Active profile id for Health Connect record namespacing. */
    fun activeProfileIdOrNull(): String? = _activeProfile.value?.id

    /**
     * Switches profiles. Returns false when unknown. Callers must block this
     * while a workout is RUNNING/PAUSED. Resets Health sync state so the new
     * profile never displays the previous profile's result.
     */
    fun switchProfile(profileId: String): Boolean {
        val ok = profileRepository.switchTo(profileId)
        if (!ok) return false
        val profile = profileRepository.getProfile(profileId) ?: return false
        bindActiveProfile(profile.id, rebindSession = true)
        _activeProfile.value = profile
        if (::healthConnectManager.isInitialized) healthConnectManager.reset()
        return true
    }

    /** Creates + activates a profile. Returns failure with user message on invalid input. */
    fun createAndSwitchProfile(name: String, colorArgb: Int): Result<Profile> {
        val result = profileRepository.createProfile(name, colorArgb)
        if (result.isFailure) return result
        val profile = result.getOrThrow()
        bindActiveProfile(profile.id, rebindSession = true)
        _activeProfile.value = profile
        if (::healthConnectManager.isInitialized) healthConnectManager.reset()
        return Result.success(profile)
    }

    /**
     * Deletes a profile identity + its per-profile data (DataStore file and
     * history dir). Returns false when refused (last profile / unknown).
     * If the deleted profile was active, binds the fallback active profile.
     */
    fun deleteProfile(profileId: String): Boolean {
        val wasActive = _activeProfile.value?.id == profileId
        val ok = profileRepository.deleteProfile(profileId)
        if (!ok) return false
        // Delete per-profile data files (best-effort).
        try {
            com.valpr.bikecompanion.data.ProfilePaths.userProfileStoreFile(filesDir, profileId).delete()
            val histDir = ProfileRepository.historyDirFor(filesDir, profileId)
            // histDir is .../profiles/<safe>/history — remove the profile root.
            histDir.parentFile?.deleteRecursively()
        } catch (_: Exception) { }
        if (wasActive) {
            val fallback = profileRepository.activeProfile()
            if (fallback != null) {
                bindActiveProfile(fallback.id, rebindSession = true)
                _activeProfile.value = fallback
                if (::healthConnectManager.isInitialized) healthConnectManager.reset()
            }
        }
        return true
    }

    /** Refreshes the cached active profile after rename/color edits. */
    fun refreshActiveProfile() {
        val id = _activeProfile.value?.id ?: return
        _activeProfile.value = profileRepository.getProfile(id)
    }

    // ---- Exclusive Health Connect sync lock ----
    // Health Connect storage is device-global with no per-profile partition,
    // so only one profile may hold healthSyncEnabled=true at a time.

    /** Reads every profile's sync flag (IO). Used to enforce the exclusive lock. */
    suspend fun healthSyncFlagsByProfile(): Map<String, Boolean> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val ids = profileRepository.currentProfiles().map { it.id }
        ids.associateWith { id ->
            try {
                UserProfileRepository(applicationContext.userProfileDataStoreFor(id)).userProfileFlow.first().healthSyncEnabled
            } catch (_: Exception) {
                false
            }
        }
    }

    /** The profile currently holding the sync lock, or null when free. */
    suspend fun healthSyncHolder(excludeActiveId: String? = activeProfileIdOrNull()): Profile? {
        val flags = healthSyncFlagsByProfile()
        val holderId = Profile.healthSyncLockHolder(flags, excludeActiveId) ?: return null
        return profileRepository.getProfile(holderId)
    }

    /**
     * Enables/disables Health Connect sync for the active profile.
     * Enabling fails when another profile already holds the lock.
     */
    suspend fun setHealthSyncEnabledForActive(enabled: Boolean): Result<Unit> {
        val activeId = activeProfileIdOrNull()
            ?: return Result.failure(IllegalStateException("No active profile"))
        if (!enabled) {
            userProfileRepository.setHealthSyncEnabled(false)
            return Result.success(Unit)
        }
        val holder = healthSyncHolder(excludeActiveId = activeId)
        if (holder != null) {
            return Result.failure(
                IllegalStateException("Health Connect is already linked to ${holder.name} — disable it there first")
            )
        }
        userProfileRepository.setHealthSyncEnabled(true)
        return Result.success(Unit)
    }

    override fun onTerminate() {
        super.onTerminate()
        companionHub.onDestroy()
        heartRateArbiter.onDestroy()
        phoneWearableManager.onDestroy()
        healthConnectManager.onDestroy()
        bike.onDestroy()
    }
}
