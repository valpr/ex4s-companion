package com.valpr.bikecompanion.data

import kotlinx.serialization.Serializable

/**
 * A rider profile on this shared device. Identity is name + color only
 * (no avatar images, no PIN — per product decision).
 */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val createdAtMs: Long = 0L,
    val lastActiveMs: Long = 0L
) {
    companion object {
        const val MAX_PROFILES = 8
        const val MAX_NAME_LENGTH = 20

        /** Default palette offered by the profile switcher. */
        val DEFAULT_COLORS: List<Int> = listOf(
            0xFF00E676.toInt(),
            0xFF29B6F6.toInt(),
            0xFFFFB300.toInt(),
            0xFFEF5350.toInt(),
            0xFFAB47BC.toInt(),
            0xFF26A69A.toInt(),
            0xFFFF7043.toInt(),
            0xFF78909C.toInt()
        )

        /** Normalized display name, or null when the raw name is unusable. */
        fun sanitizeName(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return trimmed.take(MAX_NAME_LENGTH)
        }

        /**
         * Pure validation for create/rename (JVM-testable, no Android deps).
         * Returns null when valid, otherwise a user-facing error string.
         */
        fun validateName(raw: String?, existingNames: Collection<String>, selfName: String? = null): String? {
            val sanitized = sanitizeName(raw) ?: return "Enter a name"
            val lower = sanitized.lowercase()
            val clash = existingNames.any { it.trim().lowercase() == lower && it.trim() != selfName?.trim() }
            if (clash) return "That name is already used"
            return null
        }

        /** Single-char avatar glyph. */
        fun initialFor(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

        /**
         * Exclusive Health Connect sync lock (JVM-testable).
         * Health Connect storage is device-global, so at most one profile may
         * hold `healthSyncEnabled=true`. Returns the holder id when another
         * profile (any id != [activeId] with flag true) owns the lock, else null.
         */
        fun healthSyncLockHolder(
            enabledByProfileId: Map<String, Boolean>,
            activeId: String?
        ): String? = enabledByProfileId
            .filter { (id, enabled) -> enabled && id != activeId }
            .keys
            .sorted()
            .firstOrNull()
    }
}
