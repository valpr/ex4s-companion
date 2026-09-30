package com.valpr.bikecompanion.ui.athletestats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valpr.bikecompanion.BikeApplication
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.shared.BiologicalSex
import com.valpr.bikecompanion.shared.UnitSystem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        }
    }

    fun updatePowerSettings(ftp: Int, preferredCadenceRpm: Int) {
        viewModelScope.launch {
            userProfileRepo.updatePowerSettings(
                ftp = ftp,
                preferredCadenceRpm = preferredCadenceRpm
            )
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
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        bleManager.autoConnect = enabled
    }
}
