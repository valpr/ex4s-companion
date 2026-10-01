package com.valpr.bikecompanion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.valpr.bikecompanion.shared.AthleteMetrics
import com.valpr.bikecompanion.shared.BiologicalSex
import com.valpr.bikecompanion.shared.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

/**
 * Athlete profile data required for power target calculations and ERG tuning.
 */
data class UserProfile(
    val ftp: Int = 0,
    val weightKg: Float = 75.0f,
    val cadenceFloorRpm: Int = 60,
    val cadenceRecoveryRpm: Int = 75,
    val ergKp: Float = 0.05f,
    val ergKi: Float = 0.01f,
    val maxHeartRate: Int = 190,
    val criticalHeartRate: Int = 181,
    val age: Int = 30,
    val heightCm: Float = 175.0f,
    val biologicalSex: BiologicalSex = BiologicalSex.MALE,
    val restingHeartRate: Int = 60,
    val lactateThresholdHeartRate: Int = 165,
    val preferredCadenceRpm: Int = 85,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val keepScreenOn: Boolean = true,
    val beginnerPathDismissed: Boolean = false,
    val beginnerPathCollapsed: Boolean = false,
    val lastUpdatedEpochMs: Long = 0L
) {
    /**
     * Whether an FTP has been configured. Workouts cannot start without a valid FTP.
     */
    val isFtpConfigured: Boolean
        get() = ftp > 0

    /**
     * Power-to-weight ratio in W/kg based on current FTP and weight.
     */
    val wattsPerKg: Float
        get() = AthleteMetrics.calculateWattsPerKg(ftp, weightKg)

    /**
     * Estimated resting metabolic rate (kcal/day).
     */
    val estimatedBmrKcal: Int
        get() = AthleteMetrics.estimateBmrKcal(weightKg, heightCm, age, biologicalSex)

    /**
     * Calculates the HR Zone (1 to 5) based on the athlete's max heart rate.
     * Zone 1: < 60% (Active Recovery)
     * Zone 2: 60-70% (Endurance)
     * Zone 3: 70-80% (Tempo)
     * Zone 4: 80-90% (Threshold)
     * Zone 5: >= 90% (Anaerobic / VO2 Max)
     */
    fun calculateHrZone(bpm: Int): Int {
        if (bpm <= 0 || maxHeartRate <= 0) return 0
        val pct = bpm.toFloat() / maxHeartRate
        return when {
            pct < 0.60f -> 1
            pct < 0.70f -> 2
            pct < 0.80f -> 3
            pct < 0.90f -> 4
            else -> 5
        }
    }
}

/**
 * Repository backed by Jetpack DataStore Preferences for persisting athlete profile settings.
 */
class UserProfileRepository(private val dataStore: DataStore<Preferences>) {
    companion object {
        val KEY_FTP = intPreferencesKey("athlete_ftp")
        val KEY_WEIGHT_KG = floatPreferencesKey("athlete_weight_kg")
        val KEY_CADENCE_FLOOR = intPreferencesKey("cadence_floor_rpm")
        val KEY_CADENCE_RECOVERY = intPreferencesKey("cadence_recovery_rpm")
        val KEY_ERG_KP = floatPreferencesKey("erg_kp")
        val KEY_ERG_KI = floatPreferencesKey("erg_ki")
        val KEY_MAX_HEART_RATE = intPreferencesKey("max_heart_rate")
        val KEY_CRITICAL_HEART_RATE = intPreferencesKey("critical_heart_rate")
        val KEY_AGE = intPreferencesKey("athlete_age")
        val KEY_HEIGHT_CM = floatPreferencesKey("athlete_height_cm")
        val KEY_BIOLOGICAL_SEX = stringPreferencesKey("athlete_biological_sex")
        val KEY_RESTING_HEART_RATE = intPreferencesKey("athlete_resting_heart_rate")
        val KEY_LTHR = intPreferencesKey("athlete_lthr")
        val KEY_PREFERRED_CADENCE = intPreferencesKey("athlete_preferred_cadence")
        val KEY_UNIT_SYSTEM = stringPreferencesKey("athlete_unit_system")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_BEGINNER_PATH_DISMISSED = booleanPreferencesKey("beginner_path_dismissed")
        val KEY_BEGINNER_PATH_COLLAPSED = booleanPreferencesKey("beginner_path_collapsed")
        val KEY_LAST_UPDATED = longPreferencesKey("profile_last_updated_epoch_ms")

        const val DEFAULT_WEIGHT_KG = 75.0f
        const val DEFAULT_CADENCE_FLOOR = 60
        const val DEFAULT_CADENCE_RECOVERY = 75
        const val DEFAULT_KP = 0.05f
        const val DEFAULT_KI = 0.01f
        const val DEFAULT_MAX_HR = 190
        const val DEFAULT_CRITICAL_HR = 181
        const val DEFAULT_AGE = 30
        const val DEFAULT_HEIGHT_CM = 175.0f
        val DEFAULT_SEX = BiologicalSex.MALE
        const val DEFAULT_RESTING_HR = 60
        const val DEFAULT_LTHR = 165
        const val DEFAULT_PREFERRED_CADENCE = 85
        val DEFAULT_UNIT_SYSTEM = UnitSystem.METRIC
        const val DEFAULT_KEEP_SCREEN_ON = true
        const val DEFAULT_BEGINNER_PATH_DISMISSED = false
        const val DEFAULT_BEGINNER_PATH_COLLAPSED = false
    }

    val userProfileFlow: Flow<UserProfile> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserProfile(
                ftp = preferences[KEY_FTP] ?: 0,
                weightKg = preferences[KEY_WEIGHT_KG] ?: DEFAULT_WEIGHT_KG,
                cadenceFloorRpm = preferences[KEY_CADENCE_FLOOR] ?: DEFAULT_CADENCE_FLOOR,
                cadenceRecoveryRpm = preferences[KEY_CADENCE_RECOVERY] ?: DEFAULT_CADENCE_RECOVERY,
                ergKp = preferences[KEY_ERG_KP] ?: DEFAULT_KP,
                ergKi = preferences[KEY_ERG_KI] ?: DEFAULT_KI,
                maxHeartRate = preferences[KEY_MAX_HEART_RATE] ?: DEFAULT_MAX_HR,
                criticalHeartRate = preferences[KEY_CRITICAL_HEART_RATE] ?: DEFAULT_CRITICAL_HR,
                age = preferences[KEY_AGE] ?: DEFAULT_AGE,
                heightCm = preferences[KEY_HEIGHT_CM] ?: DEFAULT_HEIGHT_CM,
                biologicalSex = preferences[KEY_BIOLOGICAL_SEX]?.let {
                    runCatching { BiologicalSex.valueOf(it) }.getOrDefault(DEFAULT_SEX)
                } ?: DEFAULT_SEX,
                restingHeartRate = preferences[KEY_RESTING_HEART_RATE] ?: DEFAULT_RESTING_HR,
                lactateThresholdHeartRate = preferences[KEY_LTHR] ?: DEFAULT_LTHR,
                preferredCadenceRpm = preferences[KEY_PREFERRED_CADENCE] ?: DEFAULT_PREFERRED_CADENCE,
                unitSystem = preferences[KEY_UNIT_SYSTEM]?.let {
                    runCatching { UnitSystem.valueOf(it) }.getOrDefault(DEFAULT_UNIT_SYSTEM)
                } ?: DEFAULT_UNIT_SYSTEM,
                keepScreenOn = preferences[KEY_KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON,
                beginnerPathDismissed = preferences[KEY_BEGINNER_PATH_DISMISSED] ?: DEFAULT_BEGINNER_PATH_DISMISSED,
                beginnerPathCollapsed = preferences[KEY_BEGINNER_PATH_COLLAPSED] ?: DEFAULT_BEGINNER_PATH_COLLAPSED,
                lastUpdatedEpochMs = preferences[KEY_LAST_UPDATED] ?: 0L
            )
        }

    suspend fun applyHealthImport(
        weightKg: Float? = null,
        heightCm: Float? = null,
        restingHeartRate: Int? = null
    ) {
        dataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            if (weightKg != null) {
                preferences[KEY_WEIGHT_KG] = weightKg.coerceAtLeast(20.0f)
            }
            if (heightCm != null) {
                preferences[KEY_HEIGHT_CM] = heightCm.coerceIn(50.0f, 250.0f)
            }
            if (restingHeartRate != null) {
                preferences[KEY_RESTING_HEART_RATE] = restingHeartRate.coerceIn(30, 120)
            }
            preferences[KEY_LAST_UPDATED] = now
        }
    }

    suspend fun updateFtp(ftp: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_FTP] = ftp.coerceAtLeast(0)
            preferences[KEY_LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun updateWeight(weightKg: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_WEIGHT_KG] = weightKg.coerceAtLeast(20.0f)
            preferences[KEY_LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun updateProfile(ftp: Int, weightKg: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_FTP] = ftp.coerceAtLeast(0)
            preferences[KEY_WEIGHT_KG] = weightKg.coerceAtLeast(20.0f)
            preferences[KEY_LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun updateHeartRateSettings(
        maxHr: Int,
        criticalHr: Int,
        restingHr: Int = DEFAULT_RESTING_HR,
        lthr: Int = DEFAULT_LTHR
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_MAX_HEART_RATE] = maxHr.coerceIn(100, 240)
            preferences[KEY_CRITICAL_HEART_RATE] = criticalHr.coerceIn(100, 240)
            preferences[KEY_RESTING_HEART_RATE] = restingHr.coerceIn(30, 120)
            preferences[KEY_LTHR] = lthr.coerceIn(80, 220)
            preferences[KEY_LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun updateAthleteBio(age: Int, weightKg: Float, heightCm: Float, sex: BiologicalSex) {
        dataStore.edit { preferences ->
            preferences[KEY_AGE] = age.coerceIn(10, 120)
            preferences[KEY_WEIGHT_KG] = weightKg.coerceAtLeast(20.0f)
            preferences[KEY_HEIGHT_CM] = heightCm.coerceIn(50.0f, 250.0f)
            preferences[KEY_BIOLOGICAL_SEX] = sex.name
            preferences[KEY_LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun updatePowerSettings(ftp: Int, preferredCadenceRpm: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_FTP] = ftp.coerceAtLeast(0)
            preferences[KEY_PREFERRED_CADENCE] = preferredCadenceRpm.coerceIn(40, 140)
            preferences[KEY_LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun updateUnitSystem(unitSystem: UnitSystem) {
        dataStore.edit { preferences ->
            preferences[KEY_UNIT_SYSTEM] = unitSystem.name
        }
    }

    suspend fun updateEngineTuning(
        cadenceFloorRpm: Int = DEFAULT_CADENCE_FLOOR,
        cadenceRecoveryRpm: Int = DEFAULT_CADENCE_RECOVERY,
        kp: Float = DEFAULT_KP,
        ki: Float = DEFAULT_KI
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_CADENCE_FLOOR] = cadenceFloorRpm.coerceIn(40, 80)
            preferences[KEY_CADENCE_RECOVERY] = cadenceRecoveryRpm.coerceIn(60, 100)
            preferences[KEY_ERG_KP] = kp.coerceIn(0.001f, 0.5f)
            preferences[KEY_ERG_KI] = ki.coerceIn(0.0001f, 0.1f)
        }
    }

    suspend fun updateKeepScreenOn(keepScreenOn: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_KEEP_SCREEN_ON] = keepScreenOn
        }
    }

    suspend fun updateBeginnerPathDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_BEGINNER_PATH_DISMISSED] = dismissed
        }
    }

    suspend fun updateBeginnerPathCollapsed(collapsed: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_BEGINNER_PATH_COLLAPSED] = collapsed
        }
    }
}
