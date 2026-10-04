package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.ProfilePaths
import com.valpr.bikecompanion.data.ProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfilePathsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun sanitize_isConsistentAcrossHelpers() {
        val raw = "abc/def:ghi?jklmnopqrstuvwxyz0123456789_extra-long-tail-to-exceed-forty-eight-chars"
        val expected = raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
        assertEquals(expected, ProfilePaths.sanitizeProfileId(raw))
        assertEquals(
            ProfilePaths.userProfileStoreFile(tmp.root, raw).name,
            "${ProfilePaths.userProfileStoreName(raw)}.preferences_pb"
        )
        assertEquals(
            ProfilePaths.historyDirFor(tmp.root, raw),
            ProfileRepository.historyDirFor(tmp.root, raw)
        )
        assertTrue(ProfilePaths.historyDirFor(tmp.root, raw).path.contains(expected))
    }

    @Test
    fun userProfileStoreName_matchesLegacyFormat() {
        assertEquals("user_profile_abc-123", ProfilePaths.userProfileStoreName("abc-123"))
        assertEquals(
            ProfileRepository.userProfileStoreName("abc-123"),
            ProfilePaths.userProfileStoreName("abc-123")
        )
    }

    @Test
    fun historyDir_isNamespacedPerProfile() {
        val base: File = tmp.newFolder()
        val a = ProfilePaths.historyDirFor(base, "profile-a")
        val b = ProfilePaths.historyDirFor(base, "profile-b")
        assertTrue(a.path.contains("profile-a"))
        assertTrue(b.path.contains("profile-b"))
    }

    @Test
    fun healthRecordId_isShortAndStable() {
        val first = ProfilePaths.sanitizeForHealthRecord("profile-aaa-long-suffix-that-gets-cut")
        val second = ProfilePaths.sanitizeForHealthRecord("profile-aaa-long-suffix-that-gets-cut")
        assertEquals(first, second)
        assertTrue(first.length <= ProfilePaths.HEALTH_ID_LENGTH)
    }
}
