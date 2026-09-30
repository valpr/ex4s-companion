package com.valpr.bikecompanion.shared

/** Pure BLE MAC validation (JVM-testable, no Android dependency). */
object MacValidator {
    private val MAC_REGEX = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    fun normalize(input: String): String = input.trim().uppercase()

    fun isValid(input: String): Boolean = MAC_REGEX.matches(normalize(input))

    fun validate(input: String): Result<String> {
        val normalized = normalize(input)
        return if (isValid(normalized)) {
            Result.success(normalized)
        } else {
            Result.failure(IllegalArgumentException("Invalid MAC address: $input"))
        }
    }
}
