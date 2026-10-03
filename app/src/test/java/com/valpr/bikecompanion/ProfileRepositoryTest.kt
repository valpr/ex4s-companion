package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.Profile
import com.valpr.bikecompanion.data.ProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo(): ProfileRepository = ProfileRepository(tmp.newFolder())

    @Test
    fun ensureInitialized_createsDefaultProfile() {
        val r = repo()
        val active = r.ensureInitialized()
        assertEquals("Rider 1", active.name)
        assertEquals(1, r.currentProfiles().size)
        assertEquals(active.id, r.activeProfile()?.id)
    }

    @Test
    fun createProfile_validatesAndCapsAtMax() {
        val r = repo()
        r.ensureInitialized()
        assertTrue(r.createProfile("Alex", Profile.DEFAULT_COLORS[0]).isSuccess)
        // Duplicate (case-insensitive) rejected with user-facing message
        val dup = r.createProfile("alex", Profile.DEFAULT_COLORS[1])
        assertTrue(dup.isFailure)
        assertEquals("That name is already used", dup.exceptionOrNull()?.message)
        // Blank rejected
        assertTrue(r.createProfile("   ", Profile.DEFAULT_COLORS[1]).isFailure)
        // Fill to max
        for (i in 3..Profile.MAX_PROFILES) {
            assertTrue(r.createProfile("Rider $i", Profile.DEFAULT_COLORS[0]).isSuccess)
        }
        assertEquals(Profile.MAX_PROFILES, r.currentProfiles().size)
        assertTrue(r.createProfile("Overflow", Profile.DEFAULT_COLORS[0]).isFailure)
    }

    @Test
    fun switchTo_unknownReturnsFalseAndKeepsActive() {
        val r = repo()
        val first = r.ensureInitialized()
        assertFalse(r.switchTo("missing"))
        assertEquals(first.id, r.activeProfile()?.id)
    }

    @Test
    fun switchTo_updatesActiveAndPersists() {
        val dir = tmp.newFolder()
        val r = ProfileRepository(dir)
        r.ensureInitialized()
        val second = r.createProfile("Sam", Profile.DEFAULT_COLORS[1]).getOrThrow()
        assertTrue(r.switchTo(second.id))
        assertEquals(second.id, r.activeProfile()?.id)
        // New instance over the same dir sees the same active profile.
        val reopened = ProfileRepository(dir)
        assertEquals(second.id, reopened.activeProfile()?.id)
    }

    @Test
    fun renameProfile_rejectsClashes() {
        val r = repo()
        r.ensureInitialized()
        val sam = r.createProfile("Sam", Profile.DEFAULT_COLORS[1]).getOrThrow()
        val clash = r.renameProfile(sam.id, "rider 1")
        assertTrue(clash.isFailure)
        val ok = r.renameProfile(sam.id, "Samuel")
        assertTrue(ok.isSuccess)
        assertEquals("Samuel", r.getProfile(sam.id)?.name)
    }

    @Test
    fun deleteProfile_refusesLastAndFallsBackActive() {
        val r = repo()
        val first = r.ensureInitialized()
        // Last profile cannot be deleted.
        assertFalse(r.deleteProfile(first.id))
        val second = r.createProfile("Sam", Profile.DEFAULT_COLORS[1]).getOrThrow()
        assertTrue(r.switchTo(second.id))
        assertTrue(r.deleteProfile(second.id))
        assertEquals(1, r.currentProfiles().size)
        assertEquals(first.id, r.activeProfile()?.id)
        assertNull(r.getProfile(second.id))
    }

    @Test
    fun storeNameAndHistoryDir_areNamespaced() {
        val base = tmp.newFolder()
        val dir = ProfileRepository.historyDirFor(base, "abc-123")
        assertTrue(dir.path.contains("abc-123"))
        assertNotNull(ProfileRepository.userProfileStoreName("abc-123"))
    }
}
