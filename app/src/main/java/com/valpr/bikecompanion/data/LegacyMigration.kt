package com.valpr.bikecompanion.data

import java.io.File

/**
 * One-shot move of pre-profile single-user data (`filesDir/history/` +
 * `datastore/user_profile.preferences_pb`) into the owning profile's scoped
 * locations. Framework-free (plain `File` ops) so it stays plain-JUnit
 * testable per AGENTS.md §8.
 *
 * Idempotent: history copies are skipped when the destination already holds
 * rides, and the athlete store is copied only when the destination file does
 * not exist yet. All failures are best-effort — a partially migrated profile
 * remains usable with defaults.
 */
object LegacyMigration {
    data class MigrationResult(
        val historyFilesCopied: Int,
        val athleteStoreCopied: Boolean
    )

    /** True when any pre-profile data exists (either history rides or athlete store). */
    fun hasLegacyData(filesDir: File, legacyHistoryDirName: String = "history"): Boolean = hasLegacyHistory(filesDir, legacyHistoryDirName) || hasLegacyStore(filesDir)

    fun hasLegacyHistory(filesDir: File, legacyHistoryDirName: String = "history"): Boolean {
        val dir = File(filesDir, legacyHistoryDirName)
        return dir.exists() && (dir.listFiles()?.any { it.isFile && isRideFile(it.name) } == true)
    }

    fun hasLegacyStore(filesDir: File): Boolean = ProfilePaths.legacyStoreFile(filesDir).exists()

    fun migrate(
        filesDir: File,
        profileId: String,
        legacyHistoryDirName: String = "history"
    ): MigrationResult {
        var historyCopied = 0
        var storeCopied = false
        try {
            historyCopied = migrateHistory(filesDir, profileId, legacyHistoryDirName)
        } catch (_: Exception) { }
        try {
            storeCopied = migrateAthleteStore(filesDir, profileId)
        } catch (_: Exception) { }
        return MigrationResult(historyFilesCopied = historyCopied, athleteStoreCopied = storeCopied)
    }

    /** Ride payload files, excluding stale index/tmp leftovers. */
    internal fun isRideFile(name: String): Boolean = name.startsWith("ride_") && name.endsWith(".json") && !name.endsWith(".tmp")

    private fun migrateHistory(filesDir: File, profileId: String, legacyHistoryDirName: String): Int {
        val legacyHistory = File(filesDir, legacyHistoryDirName)
        if (!legacyHistory.exists() || !legacyHistory.isDirectory) return 0
        val destHistory = ProfilePaths.historyDirFor(filesDir, profileId)
        val destHasRides = destHistory.exists() &&
            (destHistory.listFiles()?.any { it.isFile && isRideFile(it.name) } == true)
        if (destHasRides) return 0
        // Only ride payloads migrate: stale index/tmp files stay behind and are
        // rebuilt from the full files on first read.
        val legacyFiles = legacyHistory.listFiles()
            ?.filter { it.isFile && isRideFile(it.name) } ?: return 0
        if (legacyFiles.isEmpty()) return 0
        if (!destHistory.exists()) destHistory.mkdirs()
        var copied = 0
        for (f in legacyFiles) {
            try {
                val dest = File(destHistory, f.name)
                if (!dest.exists()) {
                    f.copyTo(dest)
                    copied++
                }
            } catch (_: Exception) { }
        }
        return copied
    }

    private fun migrateAthleteStore(filesDir: File, profileId: String): Boolean {
        // DataStore files are protobuf; key-level copy via repository read would
        // need async — instead attempt a file copy when the dest has no file yet.
        val legacyStoreFile = ProfilePaths.legacyStoreFile(filesDir)
        val destStoreFile = ProfilePaths.userProfileStoreFile(filesDir, profileId)
        if (!legacyStoreFile.exists() || destStoreFile.exists()) return false
        return try {
            destStoreFile.parentFile?.mkdirs()
            legacyStoreFile.copyTo(destStoreFile)
            true
        } catch (_: Exception) {
            false
        }
    }
}
