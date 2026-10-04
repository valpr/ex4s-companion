package com.valpr.bikecompanion.companion.wearos

import com.valpr.bikecompanion.wearable.PhoneWearableManager

/**
 * Wear OS companion provider alias.
 *
 * Implements [com.valpr.bikecompanion.companion.api.CompanionDeviceProvider] and
 * [com.valpr.bikecompanion.companion.api.HeartRateSource] via Google Play Services Wearable.
 */
typealias WearOsCompanionProvider = PhoneWearableManager
