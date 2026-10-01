package com.valpr.bikecompanion.shared

import kotlin.math.roundToInt

/**
 * Biological sex for physiological formulas (Max HR, BMR).
 */
enum class BiologicalSex {
    MALE,
    FEMALE
}

/**
 * Display unit system preference.
 */
enum class UnitSystem {
    METRIC,
    IMPERIAL
}

/**
 * Power training zone specification based on Dr. Andy Coggan's 7-zone model.
 */
data class PowerZone(
    val zoneNumber: Int,
    val name: String,
    val minWatts: Int,
    val maxWatts: Int,
    val percentRangeDescription: String
)

/**
 * Heart rate zone specification with calculated BPM range.
 */
data class CalculatedHrZone(
    val zoneNumber: Int,
    val name: String,
    val minBpm: Int,
    val maxBpm: Int,
    val percentRangeDescription: String
)

/**
 * Cycling fitness classification benchmark based on FTP power-to-weight ratio.
 */
enum class CyclingCategory(val label: String, val minWkg: Float) {
    RECREATIONAL("Recreational (<2.0 W/kg)", 0.0f),
    MODERATE("Fair / Cat 5 (2.0–2.79 W/kg)", 2.0f),
    TRAINED("Trained / Cat 4 (2.8–3.49 W/kg)", 2.8f),
    VERY_GOOD("Very Good / Cat 3 (3.5–4.19 W/kg)", 3.5f),
    EXCELLENT("Excellent / Cat 1-2 (4.2–4.99 W/kg)", 4.2f),
    WORLD_CLASS("World Class / Pro (≥5.0 W/kg)", 5.0f);

    companion object {
        fun fromWkg(wkg: Float): CyclingCategory = when {
            wkg >= 5.0f -> WORLD_CLASS
            wkg >= 4.2f -> EXCELLENT
            wkg >= 3.5f -> VERY_GOOD
            wkg >= 2.8f -> TRAINED
            wkg >= 2.0f -> MODERATE
            else -> RECREATIONAL
        }
    }
}

/**
 * Pure Kotlin calculation engine for athlete biometrics, training zones, and conversions.
 * Framework-free so it runs instantly on plain JUnit in both :app and :wear.
 */
object AthleteMetrics {
    // --- Age-Based Max Heart Rate Estimation Formulas ---

    /**
     * Tanaka formula: HRmax = 208 - (0.7 * age).
     * Validated across wide adult populations, superior to standard 220 - age.
     */
    fun estimateMaxHrTanaka(age: Int): Int {
        val safeAge = age.coerceIn(10, 100)
        return (208.0 - 0.7 * safeAge).roundToInt()
    }

    /**
     * Traditional Fox formula: HRmax = 220 - age.
     */
    fun estimateMaxHrFox(age: Int): Int {
        val safeAge = age.coerceIn(10, 100)
        return 220 - safeAge
    }

    /**
     * Gulati formula for females: HRmax = 206 - (0.88 * age).
     */
    fun estimateMaxHrGulati(age: Int): Int {
        val safeAge = age.coerceIn(10, 100)
        return (206.0 - 0.88 * safeAge).roundToInt()
    }

    /**
     * Recommends Max HR based on age and biological sex (defaults to Tanaka or Gulati for females).
     */
    fun recommendMaxHr(age: Int, sex: BiologicalSex): Int = when (sex) {
        BiologicalSex.FEMALE -> estimateMaxHrGulati(age)
        BiologicalSex.MALE -> estimateMaxHrTanaka(age)
    }

    /**
     * Recommends Critical Safety Heart Rate threshold (default 95% of Max HR).
     * Provides headroom for high-intensity Zone 5 intervals while protecting against supra-maximal strain.
     */
    fun recommendCriticalHr(maxHr: Int): Int = (maxHr.coerceAtLeast(1) * 0.95f).roundToInt()

    // --- Power & W/kg Calculations ---

    /**
     * Power-to-weight ratio in Watts per kilogram.
     */
    fun calculateWattsPerKg(ftp: Int, weightKg: Float): Float {
        if (ftp <= 0 || weightKg <= 0.0f) return 0.0f
        return ftp / weightKg
    }

    /**
     * Resolves Coggan 7-Zone Power breakdown based on FTP.
     */
    fun calculateCogganPowerZones(ftp: Int): List<PowerZone> {
        val baseFtp = ftp.coerceAtLeast(0)
        return listOf(
            PowerZone(1, "Active Recovery", 0, (baseFtp * 0.55).roundToInt(), "< 55%"),
            PowerZone(2, "Endurance", (baseFtp * 0.55).roundToInt() + 1, (baseFtp * 0.75).roundToInt(), "55–75%"),
            PowerZone(3, "Tempo", (baseFtp * 0.75).roundToInt() + 1, (baseFtp * 0.90).roundToInt(), "76–90%"),
            PowerZone(
                4,
                "Lactate Threshold",
                (baseFtp * 0.90).roundToInt() + 1,
                (baseFtp * 1.05).roundToInt(),
                "91–105%"
            ),
            PowerZone(5, "VO₂ Max", (baseFtp * 1.05).roundToInt() + 1, (baseFtp * 1.20).roundToInt(), "106–120%"),
            PowerZone(
                6,
                "Anaerobic Capacity",
                (baseFtp * 1.20).roundToInt() + 1,
                (baseFtp * 1.50).roundToInt(),
                "121–150%"
            ),
            PowerZone(
                7,
                "Neuromuscular Power",
                (baseFtp * 1.50).roundToInt() + 1,
                (baseFtp * 2.50).roundToInt(),
                "> 150%"
            )
        )
    }

    // --- Heart Rate Zones ---

    /**
     * Calculates 5 Heart Rate Zones based on percentage of Max HR.
     */
    fun calculateMaxHrZones(maxHr: Int): List<CalculatedHrZone> {
        val hr = maxHr.coerceAtLeast(1)
        return listOf(
            CalculatedHrZone(1, "Active Recovery", 0, (hr * 0.60f).roundToInt() - 1, "< 60%"),
            CalculatedHrZone(2, "Endurance", (hr * 0.60f).roundToInt(), (hr * 0.70f).roundToInt() - 1, "60–70%"),
            CalculatedHrZone(3, "Tempo", (hr * 0.70f).roundToInt(), (hr * 0.80f).roundToInt() - 1, "70–80%"),
            CalculatedHrZone(4, "Threshold", (hr * 0.80f).roundToInt(), (hr * 0.90f).roundToInt() - 1, "80–90%"),
            CalculatedHrZone(5, "Max Effort", (hr * 0.90f).roundToInt(), hr, "≥ 90%")
        )
    }

    /**
     * Karvonen / Heart Rate Reserve (HRR) zones: Target = RHR + ((MaxHR - RHR) * %intensity).
     */
    fun calculateKarvonenZones(maxHr: Int, restingHr: Int): List<CalculatedHrZone> {
        val max = maxHr.coerceAtLeast(restingHr + 10).coerceAtLeast(40)
        val rest = restingHr.coerceIn(30, max - 1)
        val hrr = max - rest

        fun bpm(pct: Float) = (rest + hrr * pct).roundToInt()

        return listOf(
            CalculatedHrZone(1, "Active Recovery", rest, bpm(0.60f) - 1, "< 60% HRR"),
            CalculatedHrZone(2, "Endurance", bpm(0.60f), bpm(0.70f) - 1, "60–70% HRR"),
            CalculatedHrZone(3, "Tempo", bpm(0.70f), bpm(0.80f) - 1, "70–80% HRR"),
            CalculatedHrZone(4, "Threshold", bpm(0.80f), bpm(0.90f) - 1, "80–90% HRR"),
            CalculatedHrZone(5, "Max Effort", bpm(0.90f), max, "≥ 90% HRR")
        )
    }

    // --- Unit Conversions ---

    fun kgToLbs(kg: Float): Float = kg * 2.20462262f

    fun lbsToKg(lbs: Float): Float = lbs / 2.20462262f

    fun cmToInches(cm: Float): Float = cm / 2.54f

    fun inchesToCm(inches: Float): Float = inches * 2.54f

    /**
     * Formats height in feet and inches (e.g., 5 ft 10 in).
     */
    fun formatHeightImperial(heightCm: Float): String {
        val totalInches = (heightCm / 2.54f).roundToInt()
        val feet = totalInches / 12
        val inches = totalInches % 12
        return "$feet' $inches\""
    }

    // --- Basal Metabolic Rate (BMR) ---

    /**
     * Estimates 24h Basal Metabolic Rate in kcal via Mifflin-St Jeor equation.
     */
    fun estimateBmrKcal(weightKg: Float, heightCm: Float, age: Int, sex: BiologicalSex): Int {
        val base = (10.0f * weightKg) + (6.25f * heightCm) - (5.0f * age)
        val bmr =
            when (sex) {
                BiologicalSex.MALE -> base + 5f
                BiologicalSex.FEMALE -> base - 161f
            }
        return bmr.roundToInt().coerceAtLeast(500)
    }
}
