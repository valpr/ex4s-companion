package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.LegacyMigration
import com.valpr.bikecompanion.data.ProfilePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LegacyMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val profileId = "550e8400-e29b-41d4-a716-446655440000"

    private fun seedLegacy(filesDir: File, rides: Int = 2, store: Boolean = true) {
        val history = File(filesDir, "history").apply { mkdirs() }
        repeat(rides) { i ->
            File(history, "ride_${1_700_000_000_000L + i}.json").writeText("{\"id\":\"ride_$i\"}")
        }
        // Stale crash leftovers must not migrate.
        File(history, "history_index.json").writeText("[]")
        File(history, "ride_1.json.tmp").writeText("partial")
        if (store) {
            val legacy = ProfilePaths.legacyStoreFile(filesDir)
            legacy.parentFile?.mkdirs()
            legacy.writeText("protobuf-bytes")
        }
    }

    @Test
    fun hasLegacyData_detectsHistoryOrStore() {
        assertFalse(LegacyMigration.hasLegacyData(tmp.root))
        seedLegacy(tmp.root)
        assertTrue(LegacyMigration.hasLegacyData(tmp.root))
    }

    @Test
    fun migrate_copiesHistoryAndStore() {
        seedLegacy(tmp.root)
        val result = LegacyMigration.migrate(tmp.root, profileId)
        assertEquals(2, result.historyFilesCopied)
        assertTrue(result.athleteStoreCopied)

        val destHistory = ProfilePaths.historyDirFor(tmp.root, profileId)
        assertEquals(2, destHistory.listFiles()!!.size)
        assertTrue(ProfilePaths.userProfileStoreFile(tmp.root, profileId).exists())
    }

    @Test
    fun migrate_isIdempotent() {
        seedLegacy(tmp.root)
        LegacyMigration.migrate(tmp.root, profileId)
        val second = LegacyMigration.migrate(tmp.root, profileId)
        assertEquals(0, second.historyFilesCopied)
        assertFalse(second.athleteStoreCopied)
    }

    @Test
    fun migrate_skipsHistoryWhenDestAlreadyHasRides() {
        seedLegacy(tmp.root)
        val destHistory = ProfilePaths.historyDirFor(tmp.root, profileId).apply { mkdirs() }
        File(destHistory, "ride_9.json").writeText("{}")
        val result = LegacyMigration.migrate(tmp.root, profileId)
        assertEquals(0, result.historyFilesCopied)
        assertEquals(1, destHistory.listFiles()!!.size)
        // Store still migrates independently.
        assertTrue(result.athleteStoreCopied)
    }

    @Test
    fun migrate_noLegacyData_isNoOp() {
        val result = LegacyMigration.migrate(tmp.root, profileId)
        assertEquals(0, result.historyFilesCopied)
        assertFalse(result.athleteStoreCopied)
    }
}
