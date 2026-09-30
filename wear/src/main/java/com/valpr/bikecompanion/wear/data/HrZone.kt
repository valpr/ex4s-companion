package com.valpr.bikecompanion.wear.data

import androidx.compose.ui.graphics.Color
import com.valpr.bikecompanion.shared.HrZone as SharedHrZone

enum class HrZone(val zoneNumber: Int, val label: String, val color: Color) {
    ZONE_1(1, "Recovery", Color(0xFF9E9E9E)),
    ZONE_2(2, "Endurance", Color(0xFF29B6F6)),
    ZONE_3(3, "Tempo", Color(0xFF00E676)),
    ZONE_4(4, "Threshold", Color(0xFFFFB300)),
    ZONE_5(5, "Max Effort", Color(0xFFFF1744));

    companion object {
        fun fromBpm(bpm: Int, maxHr: Int = SharedHrZone.DEFAULT_MAX_HR): HrZone {
            return when (SharedHrZone.zoneNumber(bpm, maxHr)) {
                1 -> ZONE_1
                2 -> ZONE_2
                3 -> ZONE_3
                4 -> ZONE_4
                else -> ZONE_5
            }
        }
    }
}
