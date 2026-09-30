package com.valpr.bikecompanion.wearable

import android.util.Log
import com.valpr.bikecompanion.BikeApplication
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Foreground / background WearableListenerService to receive Wearable Data Layer events
 * from the Pixel Watch even when MainActivity is not in the foreground.
 */
class PhoneWearableListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearableListenerSvc"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d(TAG, "WearableListenerService onMessageReceived: ${messageEvent.path}")
        val app = application as? BikeApplication ?: return
        app.phoneWearableManager.onMessageReceived(messageEvent)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        val app = application as? BikeApplication ?: return
        app.phoneWearableManager.onCapabilityChanged(capabilityInfo)
    }
}
