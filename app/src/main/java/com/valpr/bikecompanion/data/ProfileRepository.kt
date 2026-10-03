package com.valpr.bikecompanion.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Global (device-level) profile list + active-profile id.
 *
 * Personal data (athlete settings, ride history) lives in per-profile
 * stores; this repository only tracks identity (id/name/color) and which
 * profile is active. The workout library stays global/shared.
 *
 * Persistence: single `profiles.json` in [storageDir] holding both the list
 * and the active id, so list and selection never disagree. File writes are
 * write-then-rename; reads heal corrupt files to a single recovery profile
 * instead of wiping per-profile history directories.
 */
class ProfileRepository(private val storageDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }
    private val lock = Any()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    init {
        if (!storageDir.exists()) storageDir.mkdirs()
        val loaded = readPersisted()
        if (loaded != null) {
            _profiles.value = loaded.first
            _activeProfileId.value = loaded.second
        }
    }

    /** Current snapshot (for non-reactive callers). */
    fun currentProfiles(): List<Profile> = synchronized(lock) { _profiles.value }

    fun activeProfile(): Profile? = synchronized(lock) {
        _profiles.value.firstOrNull { it.id == _activeProfileId.value }
    }

    fun getProfile(id: String): Profile? = synchronized(lock) {
        _profiles.value.firstOrNull { it.id == id }
    }

    /**
     * Ensures at least one profile exists. Migrates legacy single-user data
     * when [legacyHistoryDir] / [hasLegacyUserProfile] indicate a pre-profile
     * install; otherwise creates "Rider 1". Returns the active profile.
     */
    fun ensureInitialized(
        legacyHistoryDir: File? = null,
        hasLegacyUserProfile: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Profile = synchronized(lock) {
        val existing = _profiles.value
        val active = existing.firstOrNull { it.id == _activeProfileId.value }
        if (active != null) return active
        if (existing.isNotEmpty()) {
            // Profiles exist but active id is stale — fall back to most recent.
            val fallback = existing.maxByOrNull { it.lastActiveMs } ?: existing.first()
            _activeProfileId.value = fallback.id
            persistLocked()
            return fallback
        }
        val persisted = readPersisted()
        if (persisted != null && persisted.first.isNotEmpty()) {
            _profiles.value = persisted.first
            _activeProfileId.value = persisted.second ?: persisted.first.maxByOrNull { it.lastActiveMs }?.id
            return activeProfile() ?: persisted.first.first()
        }
        // Fresh or legacy install: single default profile owns the migrated data.
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = "Rider 1",
            colorArgb = Profile.DEFAULT_COLORS.first(),
            createdAtMs = nowMs,
            lastActiveMs = nowMs
        )
        _profiles.value = listOf(profile)
        _activeProfileId.value = profile.id
        persistLocked()
        return profile
    }

    /** Creates a profile. Returns failure with user-facing message on invalid input. */
    fun createProfile(name: String, colorArgb: Int, nowMs: Long = System.currentTimeMillis()): Result<Profile> = synchronized(lock) {
        if (_profiles.value.size >= Profile.MAX_PROFILES) {
            return Result.failure(IllegalStateException("Up to ${Profile.MAX_PROFILES} profiles"))
        }
        val error = Profile.validateName(name, _profiles.value.map { it.name })
        if (error != null) return Result.failure(IllegalArgumentException(error))
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = Profile.sanitizeName(name)!!,
            colorArgb = colorArgb,
            createdAtMs = nowMs,
            lastActiveMs = nowMs
        )
        _profiles.value = _profiles.value + profile
        persistLocked()
        Result.success(profile)
    }

    fun renameProfile(id: String, name: String): Result<Profile> = synchronized(lock) {
        val existing = _profiles.value.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Profile not found"))
        val others = _profiles.value.filter { it.id != id }.map { it.name }
        val error = Profile.validateName(name, others, selfName = existing.name)
        if (error != null) return Result.failure(IllegalArgumentException(error))
        // Same name ignoring case-only differences counts as no-op success.
        val sanitized = Profile.sanitizeName(name)!!
        if (sanitized.equals(existing.name, ignoreCase = false).not() &&
            sanitized.equals(existing.name, ignoreCase = true) &&
            sanitized != existing.name
        ) {
            // Allow case-only rename.
        }
        val updated = existing.copy(name = sanitized)
        _profiles.value = _profiles.value.map { if (it.id == id) updated else it }
        persistLocked()
        Result.success(updated)
    }

    fun setColor(id: String, colorArgb: Int): Result<Profile> = synchronized(lock) {
        val existing = _profiles.value.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Profile not found"))
        val updated = existing.copy(colorArgb = colorArgb)
        _profiles.value = _profiles.value.map { if (it.id == id) updated else it }
        persistLocked()
        Result.success(updated)
    }

    /**
     * Switches the active profile. Returns false when the id is unknown.
     * Callers must block switching while a workout is RUNNING/PAUSED.
     */
    fun switchTo(id: String, nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val target = _profiles.value.firstOrNull { it.id == id } ?: return false
        _activeProfileId.value = id
        _profiles.value = _profiles.value.map {
            if (it.id == id) target.copy(lastActiveMs = nowMs) else it
        }
        persistLocked()
        true
    }

    /**
     * Deletes a profile's identity. Returns false when unknown or when it is
     * the last remaining profile. Callers delete the per-profile DataStore
     * file + history directory separately.
     */
    fun deleteProfile(id: String): Boolean = synchronized(lock) {
        if (_profiles.value.size <= 1) return false
        if (_profiles.value.none { it.id == id }) return false
        _profiles.value = _profiles.value.filter { it.id != id }
        if (_activeProfileId.value == id) {
            val fallback = _profiles.value.maxByOrNull { it.lastActiveMs } ?: _profiles.value.first()
            _activeProfileId.value = fallback.id
        }
        persistLocked()
        true
    }

    private fun profilesFile(): File = File(storageDir, PROFILES_FILENAME)

    private fun persistLocked() {
        try {
            val snapshot = PersistedProfiles(profiles = _profiles.value, activeProfileId = _activeProfileId.value)
            val staging = File(storageDir, "$PROFILES_FILENAME.tmp")
            staging.writeText(json.encodeToString(PersistedProfiles.serializer(), snapshot), Charsets.UTF_8)
            val target = profilesFile()
            if (staging.renameTo(target)) {
                // ok
            } else {
                staging.delete()
            }
        } catch (_: Exception) {
            // Identity write failure must not wipe per-profile data.
        }
    }

    private fun readPersisted(): Pair<List<Profile>, String?>? {
        return try {
            val file = profilesFile()
            if (!file.exists()) return null
            val snapshot = json.decodeFromString(
                PersistedProfiles.serializer(),
                file.readText(Charsets.UTF_8)
            )
            val cleaned = snapshot.profiles
                .filter { Profile.sanitizeName(it.name) != null && it.id.isNotBlank() }
                .distinctBy { it.id }
                .take(Profile.MAX_PROFILES)
            if (cleaned.isEmpty()) return null
            val active = snapshot.activeProfileId?.takeIf { id -> cleaned.any { it.id == id } }
            cleaned to active
        } catch (_: Exception) {
            null
        }
    }

    @kotlinx.serialization.Serializable
    private data class PersistedProfiles(
        val profiles: List<Profile> = emptyList(),
        val activeProfileId: String? = null
    )

    companion object {
        const val PROFILES_FILENAME = "profiles.json"

        /** Per-profile athlete DataStore file name. */
        fun userProfileStoreName(profileId: String): String {
            val safe = profileId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
            return "user_profile_$safe"
        }

        /** Per-profile history directory. */
        fun historyDirFor(baseFilesDir: File, profileId: String): File {
            val safe = profileId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
            return File(File(baseFilesDir, "profiles"), "$safe/history")
        }
    }
}
