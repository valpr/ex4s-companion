package com.valpr.bikecompanion.wear

import com.valpr.bikecompanion.wear.ui.screens.StandbyPresentation
import com.valpr.bikecompanion.wear.ui.screens.StandbyTone
import org.junit.Assert.assertEquals
import org.junit.Test

class StandbyPresentationTest {

    @Test
    fun disconnected_resolvesWaitingForPhone() {
        val model = StandbyPresentation.resolve(
            isPhoneConnected = false,
            isPhoneAppReachable = false,
            pingFeedbackMessage = null
        )

        assertEquals("WAITING FOR PHONE", model.statusTitle)
        assertEquals("Ensure phone is paired nearby", model.helperText)
        assertEquals(StandbyTone.DISCONNECTED, model.tone)
    }

    @Test
    fun connected_appNotReachable_resolvesPhoneAppNotFound() {
        val model = StandbyPresentation.resolve(
            isPhoneConnected = true,
            isPhoneAppReachable = false,
            pingFeedbackMessage = null
        )

        assertEquals("PHONE APP NOT FOUND", model.statusTitle)
        assertEquals("Open companion app on phone", model.helperText)
        assertEquals(StandbyTone.WARNING, model.tone)
    }

    @Test
    fun connected_appReachable_resolvesPhoneReady() {
        val model = StandbyPresentation.resolve(
            isPhoneConnected = true,
            isPhoneAppReachable = true,
            pingFeedbackMessage = null
        )

        assertEquals("PHONE READY", model.statusTitle)
        assertEquals("Tap screen to test • Starts on ride", model.helperText)
        assertEquals(StandbyTone.READY, model.tone)
    }

    @Test
    fun feedbackPositive_resolvesFeedbackPositive() {
        val model = StandbyPresentation.resolve(
            isPhoneConnected = true,
            isPhoneAppReachable = true,
            pingFeedbackMessage = "CONNECTED TO PHONE"
        )

        assertEquals("CONNECTED TO PHONE", model.statusTitle)
        assertEquals("Tap screen to re-test", model.helperText)
        assertEquals(StandbyTone.FEEDBACK_POSITIVE, model.tone)
    }

    @Test
    fun feedbackWarning_notFound_resolvesFeedbackWarning() {
        val model = StandbyPresentation.resolve(
            isPhoneConnected = true,
            isPhoneAppReachable = false,
            pingFeedbackMessage = "PHONE APP NOT FOUND"
        )

        assertEquals("PHONE APP NOT FOUND", model.statusTitle)
        assertEquals("Tap screen to re-test", model.helperText)
        assertEquals(StandbyTone.FEEDBACK_WARNING, model.tone)
    }

    @Test
    fun feedbackWarning_noResponse_resolvesFeedbackWarning() {
        val model = StandbyPresentation.resolve(
            isPhoneConnected = true,
            isPhoneAppReachable = true,
            pingFeedbackMessage = "NO RESPONSE"
        )

        assertEquals("NO RESPONSE", model.statusTitle)
        assertEquals("Tap screen to re-test", model.helperText)
        assertEquals(StandbyTone.FEEDBACK_WARNING, model.tone)
    }
}
