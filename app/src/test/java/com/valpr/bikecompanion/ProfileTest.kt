package com.valpr.bikecompanion

import com.valpr.bikecompanion.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {

    @Test
    fun sanitizeName_trimsAndCapsLength() {
        assertNull(Profile.sanitizeName(null))
        assertNull(Profile.sanitizeName("   "))
        assertEquals("Alex", Profile.sanitizeName("  Alex  "))
        assertEquals(Profile.MAX_NAME_LENGTH, Profile.sanitizeName("x".repeat(50))!!.length)
    }

    @Test
    fun validateName_rejectsBlankAndDuplicates() {
        assertNotNull(Profile.validateName("", listOf("Alex")))
        assertNotNull(Profile.validateName("   ", listOf("Alex")))
        // Case-insensitive duplicate
        assertEquals(
            "That name is already used",
            Profile.validateName("alex", listOf("Alex"))
        )
        assertNull(Profile.validateName("Sam", listOf("Alex")))
        // Renaming to own name (same case/trim) is fine
        assertNull(Profile.validateName("Alex", listOf("Alex", "Sam"), selfName = "Alex"))
    }

    @Test
    fun initialFor_usesFirstLetterUppercased() {
        assertEquals("A", Profile.initialFor("alex"))
        assertEquals("?", Profile.initialFor("   "))
    }

    @Test
    fun healthSyncLockHolder_onlyOtherHoldersBlock() {
        // Nobody enabled → free
        assertNull(Profile.healthSyncLockHolder(emptyMap(), activeId = "a"))
        // Active profile itself enabled → not blocked
        assertNull(
            Profile.healthSyncLockHolder(mapOf("a" to true, "b" to false), activeId = "a")
        )
        // Another profile enabled → blocked, holder returned deterministically
        assertEquals(
            "b",
            Profile.healthSyncLockHolder(mapOf("a" to false, "b" to true, "c" to true), activeId = "a")
        )
        // Null active (shouldn't happen) → any holder blocks
        assertNotNull(Profile.healthSyncLockHolder(mapOf("a" to true), activeId = null))
    }
}
