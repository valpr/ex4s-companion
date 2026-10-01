package com.valpr.bikecompanion

import com.valpr.bikecompanion.ui.athletestats.EngineTuningHelp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTuningHelpTest {

    @Test
    fun allTuningHelpEntries_haveNonBlankTitlesTooltipsAndDescriptions() {
        val entries = listOf(
            Triple(EngineTuningHelp.ENGINE_TUNING_TITLE, EngineTuningHelp.ENGINE_TUNING_TOOLTIP, EngineTuningHelp.ENGINE_TUNING_CONTENT_DESC),
            Triple(EngineTuningHelp.KP_TITLE, EngineTuningHelp.KP_TOOLTIP, EngineTuningHelp.KP_CONTENT_DESC),
            Triple(EngineTuningHelp.KI_TITLE, EngineTuningHelp.KI_TOOLTIP, EngineTuningHelp.KI_CONTENT_DESC),
            Triple(EngineTuningHelp.CADENCE_FLOOR_TITLE, EngineTuningHelp.CADENCE_FLOOR_TOOLTIP, EngineTuningHelp.CADENCE_FLOOR_CONTENT_DESC),
            Triple(EngineTuningHelp.RECOVERY_THRESHOLD_TITLE, EngineTuningHelp.RECOVERY_THRESHOLD_TOOLTIP, EngineTuningHelp.RECOVERY_THRESHOLD_CONTENT_DESC)
        )

        for ((title, tooltip, contentDesc) in entries) {
            assertFalse("Title should not be blank", title.isBlank())
            assertFalse("Tooltip text should not be blank", tooltip.isBlank())
            assertFalse("Content description should not be blank", contentDesc.isBlank())
            assertTrue("Tooltip must be descriptive (>20 chars)", tooltip.length > 20)
        }
    }
}
