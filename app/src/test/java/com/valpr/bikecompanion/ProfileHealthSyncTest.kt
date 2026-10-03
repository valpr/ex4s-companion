package com.valpr.bikecompanion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.valpr.bikecompanion.data.UserProfileRepository
import com.valpr.bikecompanion.history.CompletedRide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileHealthSyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo(name: String): UserProfileRepository {
        val factory = PreferenceDataStoreFactory.create(
            produceFile = { File(tmp.root, "$name.preferences_pb") }
        )
        return UserProfileRepository(factory)
    }

    @Test
    fun healthClientRecordId_isNamespacedPerProfile() {
        val base = CompletedRide.healthClientRecordIdFor(null, 1000L)
        assertEquals("ride_1000", base)
        val a = CompletedRide.healthClientRecordIdFor("profile-aaa", 1000L)
        val b = CompletedRide.healthClientRecordIdFor("profile-bbb", 1000L)
        assertTrue(a.startsWith("ride_1000_"))
        assertTrue(b.startsWith("ride_1000_"))
        assertTrue(a != b)
        // Same profile + same epoch is stable (dedup works).
        assertEquals(a, CompletedRide.healthClientRecordIdFor("profile-aaa", 1000L))
    }

    @Test
    fun healthSyncEnabled_defaultsFalseAndPersistsPerStore() = runTest {
        val alice = repo("alice")
        val bob = repo("bob")
        assertFalse(alice.userProfileFlow.first().healthSyncEnabled)
        assertFalse(bob.userProfileFlow.first().healthSyncEnabled)
        alice.setHealthSyncEnabled(true)
        assertTrue(alice.userProfileFlow.first().healthSyncEnabled)
        // Bob's store is independent — the exclusive lock lives above this layer.
        assertFalse(bob.userProfileFlow.first().healthSyncEnabled)
        alice.setHealthSyncEnabled(false)
        assertFalse(alice.userProfileFlow.first().healthSyncEnabled)
    }
}
