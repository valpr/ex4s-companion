package com.valpr.bikecompanion.wear.ui.screens

/**
 * Visual tone for rendering watch standby state on Wear OS.
 * Framework-free enum for plain-JUnit testability (AGENTS.md §8).
 */
enum class StandbyTone {
    DISCONNECTED,
    WARNING,
    READY,
    FEEDBACK_POSITIVE,
    FEEDBACK_WARNING
}

/**
 * Pure presentation model for the StandbyScreen on Wear OS.
 */
data class StandbyUiModel(
    val statusTitle: String,
    val helperText: String,
    val tone: StandbyTone
)

/**
 * Pure status resolver for the StandbyScreen.
 * Framework-free for plain-JUnit testability (AGENTS.md §8).
 */
object StandbyPresentation {

    fun resolve(
        isPhoneConnected: Boolean,
        isPhoneAppReachable: Boolean = true,
        pingFeedbackMessage: String? = null
    ): StandbyUiModel {
        if (pingFeedbackMessage != null) {
            val isWarning = pingFeedbackMessage.contains("NOT FOUND") ||
                pingFeedbackMessage.contains("NO RESPONSE")
            return StandbyUiModel(
                statusTitle = pingFeedbackMessage,
                helperText = "Tap screen to re-test",
                tone = if (isWarning) StandbyTone.FEEDBACK_WARNING else StandbyTone.FEEDBACK_POSITIVE
            )
        }

        if (!isPhoneConnected) {
            return StandbyUiModel(
                statusTitle = "WAITING FOR PHONE",
                helperText = "Ensure phone is paired nearby",
                tone = StandbyTone.DISCONNECTED
            )
        }

        if (!isPhoneAppReachable) {
            return StandbyUiModel(
                statusTitle = "PHONE APP NOT FOUND",
                helperText = "Open companion app on phone",
                tone = StandbyTone.WARNING
            )
        }

        return StandbyUiModel(
            statusTitle = "PHONE READY",
            helperText = "Tap screen to test • Starts on ride",
            tone = StandbyTone.READY
        )
    }
}
