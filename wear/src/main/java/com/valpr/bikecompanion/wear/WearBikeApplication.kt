package com.valpr.bikecompanion.wear

import android.app.Application
import com.valpr.bikecompanion.wear.haptics.WatchHapticManager
import com.valpr.bikecompanion.wear.health.HealthServicesManager
import com.valpr.bikecompanion.wear.messaging.WearMessageManager

/**
 * Application class for the Wear OS companion app.
 *
 * Owns singleton instances of [WatchHapticManager], [WearMessageManager], and [HealthServicesManager]
 * so their lifecycles outlive individual [MainActivity] instances when the user navigates away
 * while an active workout is being tracked by [service.WearWorkoutTrackingService].
 */
class WearBikeApplication : Application() {
    lateinit var hapticManager: WatchHapticManager
        private set

    lateinit var messageManager: WearMessageManager
        private set

    lateinit var healthServicesManager: HealthServicesManager
        private set

    override fun onCreate() {
        super.onCreate()
        hapticManager = WatchHapticManager(applicationContext)
        messageManager = WearMessageManager(applicationContext, hapticManager)
        healthServicesManager =
            HealthServicesManager(
                context = applicationContext,
                onBatchReady = { batch -> messageManager.sendHeartRateBatch(batch) }
            )
    }

    override fun onTerminate() {
        super.onTerminate()
        healthServicesManager.onDestroy()
        messageManager.onDestroy()
    }
}
