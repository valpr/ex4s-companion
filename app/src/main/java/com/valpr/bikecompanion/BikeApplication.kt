package com.valpr.bikecompanion

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.valpr.bikecompanion.ble.EchelonBleManager
import com.valpr.bikecompanion.data.UserProfileRepository
import com.valpr.bikecompanion.data.userProfileDataStore
import com.valpr.bikecompanion.health.HealthConnectManager
import com.valpr.bikecompanion.wearable.PhoneWearableManager
import com.valpr.bikecompanion.workout.WorkoutRepository
import com.valpr.bikecompanion.workout.WorkoutSessionManager

class BikeApplication : Application() {

    lateinit var bleManager: EchelonBleManager
        private set

    lateinit var userProfileRepository: UserProfileRepository
        private set

    lateinit var workoutRepository: WorkoutRepository
        private set

    lateinit var workoutSessionManager: WorkoutSessionManager
        private set

    lateinit var phoneWearableManager: PhoneWearableManager
        private set

    lateinit var healthConnectManager: HealthConnectManager
        private set

    override fun onCreate() {
        super.onCreate()
        userProfileRepository = UserProfileRepository(applicationContext.userProfileDataStore)
        workoutRepository = WorkoutRepository(applicationContext).apply {
            importSampleWorkoutsIfEmpty()
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        bleManager = EchelonBleManager(
            context = applicationContext,
            bluetoothAdapter = bluetoothAdapter
        )

        workoutSessionManager = WorkoutSessionManager(
            bleManager = bleManager,
            userProfileRepository = userProfileRepository
        )

        phoneWearableManager = PhoneWearableManager(
            context = applicationContext,
            sessionManager = workoutSessionManager
        )

        healthConnectManager = HealthConnectManager(
            context = applicationContext
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        phoneWearableManager.onDestroy()
        healthConnectManager.onDestroy()
        bleManager.onDestroy()
    }
}
