package com.valpr.bikecompanion.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

/**
 * Single source of truth for per-profile storage paths and id sanitization.
 *
 * Previously the `replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)` snippet was
 * copy-pasted across [ProfileRepository], `UserProfileRepository`,
 * `BikeApplication`, and `CompletedRide` with divergent length caps (48 vs 12
 * vs none). Any drift silently splits one profile across two directories.
 */
object ProfilePaths {
    const val MAX_ID_LENGTH = 48
    const val HEALTH_ID_LENGTH = 12

    const val DATASTORE_DIR = "datastore"
    const val LEGACY_STORE_FILENAME = "user_profile.preferences_pb"
    const val PROFILES_DIR = "profiles"
    const val HISTORY_SUBDIR = "history"

    private val SAFE_ID_REGEX = Regex("[^a-zA-Z0-9_-]")

    /** Safe directory/file token for a profile id (UUIDs pass through). */
    fun sanitizeProfileId(profileId: String, maxLength: Int = MAX_ID_LENGTH): String = profileId.replace(SAFE_ID_REGEX, "_").take(maxLength)

    /** Safe token for ride/history ids. */
    fun sanitizeRideId(rideId: String): String = rideId.replace(SAFE_ID_REGEX, "_")

    /** Short token for Health Connect clientRecordId namespacing. */
    fun sanitizeForHealthRecord(profileId: String): String = sanitizeProfileId(profileId, HEALTH_ID_LENGTH)

    /** Per-profile athlete DataStore base name (without extension). */
    fun userProfileStoreName(profileId: String): String = "user_profile_${sanitizeProfileId(profileId)}"

    /** Per-profile athlete DataStore file. */
    fun userProfileStoreFile(filesDir: File, profileId: String): File = File(File(filesDir, DATASTORE_DIR), "${userProfileStoreName(profileId)}.preferences_pb")

    /** Legacy single-user DataStore file (pre-profile installs). */
    fun legacyStoreFile(filesDir: File): File = File(File(filesDir, DATASTORE_DIR), LEGACY_STORE_FILENAME)

    /** Per-profile history directory: `filesDir/profiles/<safe>/history`. */
    fun historyDirFor(baseFilesDir: File, profileId: String): File {
        val safe = sanitizeProfileId(profileId)
        return File(File(baseFilesDir, PROFILES_DIR), "$safe/$HISTORY_SUBDIR")
    }
}

private val dataStoreCacheLock = Any()
private val dataStoreCache = mutableMapOf<String, DataStore<Preferences>>()

/**
 * Cached per-profile athlete DataStore. DataStore requires a single instance
 * per file — the previous implementation called `Factory.create()` on every
 * access, risking concurrent instances on the same file and lost writes.
 */
fun android.content.Context.userProfileDataStoreFor(profileId: String): DataStore<Preferences> {
    val file = ProfilePaths.userProfileStoreFile(filesDir, profileId)
    val key = file.absolutePath
    synchronized(dataStoreCacheLock) {
        return dataStoreCache.getOrPut(key) {
            PreferenceDataStoreFactory.create(produceFile = { file })
        }
    }
}
