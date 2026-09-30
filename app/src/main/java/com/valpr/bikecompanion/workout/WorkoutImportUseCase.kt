package com.valpr.bikecompanion.workout

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream

/**
 * SAF import flow for .zwo workouts (displayName + stream -> Result).
 *
 * Extracted from `DashboardViewModel.importWorkoutFile` so the coroutine flow
 * (resolve filename / read stream / validate+save / refresh / preview / error)
 * is plain-JUnit testable against a temp-dir [WorkoutRepository]. The
 * ViewModel keeps only the ContentResolver wiring and state updates, so a
 * failed import can never clobber preview/session state.
 */
class WorkoutImportUseCase(
    private val repository: WorkoutRepository
) {

    data class ImportedWorkout(
        val filename: String,
        val workout: Workout
    )

    /**
     * @param displayName SAF `OpenableColumns.DISPLAY_NAME` (may be null for odd providers).
     * @param fallbackSegment `uri.lastPathSegment` tail (may be null/blank).
     * @param openStream opens the SAF stream; invoked only after filename validation.
     */
    fun import(
        displayName: String?,
        fallbackSegment: String?,
        openStream: () -> InputStream?
    ): Result<ImportedWorkout> {
        val filename = resolveImportFilename(displayName, fallbackSegment)
            .getOrElse { return Result.failure(it) }

        val content = try {
            val stream = openStream()
                ?: return Result.failure(
                    IllegalArgumentException("Error reading file: empty stream")
                )
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return Result.failure(
                IllegalStateException("Error reading file: ${e.message}", e)
            )
        }

        val saved = repository.saveWorkout(filename, content)
        if (saved.isFailure) {
            val cause = saved.exceptionOrNull()
            return Result.failure(
                IllegalStateException("Failed to parse .zwo: ${cause?.message}", cause)
            )
        }
        return Result.success(ImportedWorkout(filename, saved.getOrThrow()))
    }

    companion object {
        /** Pure SAF filename resolution (JVM-testable): DISPLAY_NAME wins, .zwo enforced. */
        fun resolveImportFilename(displayName: String?, fallbackSegment: String?): Result<String> {
            val filename = displayName?.takeIf { it.isNotBlank() }
                ?: fallbackSegment?.takeIf { it.isNotBlank() }
                ?: "imported_workout.zwo"
            return if (filename.endsWith(".zwo", ignoreCase = true)) Result.success(filename)
            else Result.failure(IllegalArgumentException("Selected file is not a .zwo workout ($filename)"))
        }

        /**
         * Reads SAF `DISPLAY_NAME` via [ContentResolver]. Returns null when the
         * provider has no cursor/row or throws (caller falls back to
         * `lastPathSegment`). Never throws.
         */
        fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
            return try {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor: Cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
