package com.valpr.bikecompanion.ui.athletestats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valpr.bikecompanion.BikeApplication
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.shared.BiologicalSex
import com.valpr.bikecompanion.shared.UnitSystem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AthleteStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BikeApplication
    private val userProfileRepo = app.userProfileRepository
    val bleManager = app.bleManager

    val userProfile: StateFlow<UserProfile> = userProfileRepo.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    /**
     * One-shot save confirmations emitted only after the DataStore write
     * completes. The screen collects these into a Snackbar so every explicit
     * save surfaces visible feedback.
     */
    private val _saveEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val saveEvents: SharedFlow<String> = _saveEvents.asSharedFlow()

    fun updateFtp(ftp: Int) {
        viewModelScope.launch {
            userProfileRepo.updateFtp(ftp)
        }
    }

    fun updateWeight(weightKg: Float) {
        viewModelScope.launch {
            userProfileRepo.updateWeight(weightKg)
        }
    }

    fun updateAthleteBio(age: Int, weightKg: Float, heightCm: Float, sex: BiologicalSex) {
        viewModelScope.launch {
            userProfileRepo.updateAthleteBio(
                age = age,
                weightKg = weightKg,
                heightCm = heightCm,
                sex = sex
            )
            _saveEvents.tryEmit("Vitals saved")
        }
    }

    fun updatePowerSettings(ftp: Int, preferredCadenceRpm: Int) {
        viewModelScope.launch {
            userProfileRepo.updatePowerSettings(
                ftp = ftp,
                preferredCadenceRpm = preferredCadenceRpm
            )
            _saveEvents.tryEmit("Power settings saved")
        }
    }

    fun updateHeartRateSettings(maxHr: Int, criticalHr: Int, restingHr: Int, lthr: Int) {
        viewModelScope.launch {
            userProfileRepo.updateHeartRateSettings(
                maxHr = maxHr,
                criticalHr = criticalHr,
                restingHr = restingHr,
                lthr = lthr
            )
            _saveEvents.tryEmit("Heart rate settings saved")
        }
    }

    fun updateUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch {
            userProfileRepo.updateUnitSystem(unitSystem)
        }
    }

    fun updateEngineTuning(cadenceFloorRpm: Int, cadenceRecoveryRpm: Int, kp: Float, ki: Float) {
        viewModelScope.launch {
            userProfileRepo.updateEngineTuning(
                cadenceFloorRpm = cadenceFloorRpm,
                cadenceRecoveryRpm = cadenceRecoveryRpm,
                kp = kp,
                ki = ki
            )
            _saveEvents.tryEmit("Engine tuning saved")
        }
    }

    fun resetEngineTuningToDefaults() {
        viewModelScope.launch {
            userProfileRepo.updateEngineTuning(
                cadenceFloorRpm = 60,
                cadenceRecoveryRpm = 75,
                kp = 0.05f,
                ki = 0.01f
            )
            _saveEvents.tryEmit("Engine tuning reset to defaults")
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        bleManager.autoConnect = enabled
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            userProfileRepo.updateKeepScreenOn(enabled)
        }
    }
}
