package com.valpr.bikecompanion

import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** On-device: WorkoutTrackingService must declare connectedDevice (Android 14+ invariant). */
@RunWith(AndroidJUnit4::class)
class ForegroundServiceTypeTest {

    @Test
    fun trackingService_declaresConnectedDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val info = ctx.packageManager.getServiceInfo(
            android.content.ComponentName(ctx, "com.valpr.bikecompanion.service.WorkoutTrackingService"),
            PackageManager.GET_META_DATA
        )
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue(
                "foregroundServiceType must include CONNECTED_DEVICE, was ${info.foregroundServiceType}",
                (info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) != 0
            )
        }
        assertEquals(ctx.packageName, info.packageName)
    }
}
