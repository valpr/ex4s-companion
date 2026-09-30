package com.valpr.bikecompanion

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import com.valpr.bikecompanion.workout.WorkoutImportUseCase
import com.valpr.bikecompanion.workout.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class WorkoutImportUseCaseTest {

    private lateinit var tempDir: File
    private lateinit var useCase: WorkoutImportUseCase

    private val validXml = """
        <workout_file>
            <name>Imported Ride</name>
            <workout><SteadyState Duration="300" Power="0.80"/></workout>
        </workout_file>
    """.trimIndent()

    @Before
    fun setUp() {
        tempDir = File.createTempFile("import_test_", "").apply {
            delete()
            mkdirs()
        }
        useCase = WorkoutImportUseCase(WorkoutRepository(tempDir))
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun import_validDisplayName_savesRefreshesAndPreviews() {
        val result = useCase.import("my_ride.zwo", "garbled123") {
            ByteArrayInputStream(validXml.toByteArray())
        }
        assertTrue(result.isSuccess)
        val imported = result.getOrThrow()
        assertEquals("my_ride.zwo", imported.filename)
        assertEquals("Imported Ride", imported.workout.name)
        // Refresh equivalent: file landed in the repo and parses back.
        assertTrue(File(tempDir, "my_ride.zwo").exists())
        assertEquals(1, WorkoutRepository(tempDir).getCachedWorkouts().size)
    }

    @Test
    fun import_fallbackSegmentUsedWhenNoDisplayName() {
        val result = useCase.import(null, "drive_blob.zwo") {
            ByteArrayInputStream(validXml.toByteArray())
        }
        assertTrue(result.isSuccess)
        assertEquals("drive_blob.zwo", result.getOrThrow().filename)
    }

    @Test
    fun import_nonZwo_rejectedBeforeTouchingStream() {
        var opened = false
        val result = useCase.import("notes.txt", null) {
            opened = true
            ByteArrayInputStream(validXml.toByteArray())
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not a .zwo") == true)
        assertTrue("Stream must not open when filename validation fails", !opened)
        assertTrue(tempDir.listFiles()?.isEmpty() ?: true)
    }

    @Test
    fun import_nullStream_reportsReadError() {
        val result = useCase.import("ride.zwo", null) { null }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.startsWith("Error reading file") == true)
    }

    @Test
    fun import_throwingStream_reportsReadErrorWithoutClobber() {
        val result = useCase.import("ride.zwo", null) {
            throw IOException("drive disconnected")
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.startsWith("Error reading file") == true)
        assertTrue(tempDir.listFiles()?.isEmpty() ?: true)
    }

    @Test
    fun import_malformedXml_reportsParseErrorWithoutClobber() {
        val before = useCase.import("good.zwo", null) {
            ByteArrayInputStream(validXml.toByteArray())
        }
        assertTrue(before.isSuccess)

        val bad = useCase.import("good.zwo", null) {
            ByteArrayInputStream("<workout_file><broken".toByteArray())
        }
        assertTrue(bad.isFailure)
        assertTrue(bad.exceptionOrNull()?.message?.startsWith("Failed to parse .zwo") == true)
        // Original file untouched: still parses to the good workout.
        val reloaded = WorkoutRepository(tempDir).loadWorkout("good.zwo")
        assertTrue(reloaded.isSuccess)
        assertEquals("Imported Ride", reloaded.getOrThrow().name)
    }

    // SAF cursor wiring (ContentResolver -> DISPLAY_NAME), MockK fakes in plain JUnit.

    private fun resolverReturning(cursor: Cursor?): ContentResolver {
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { uri.toString() } returns "content://test"
        return resolver
    }

    @Test
    fun queryDisplayName_rowPresent_returnsName() {
        val cursor = mockk<Cursor>()
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "Drive Ride.zwo"
        every { cursor.close() } returns Unit

        val name = WorkoutImportUseCase.queryDisplayName(resolverReturning(cursor), mockk())
        assertEquals("Drive Ride.zwo", name)
    }

    @Test
    fun queryDisplayName_emptyCursor_returnsNull() {
        val cursor = mockk<Cursor>()
        every { cursor.moveToFirst() } returns false
        every { cursor.close() } returns Unit

        assertEquals(null, WorkoutImportUseCase.queryDisplayName(resolverReturning(cursor), mockk()))
    }

    @Test
    fun queryDisplayName_nullCursor_returnsNull() {
        assertEquals(null, WorkoutImportUseCase.queryDisplayName(resolverReturning(null), mockk()))
    }

    @Test
    fun queryDisplayName_throwingProvider_returnsNull() {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } throws SecurityException("no grant")
        assertEquals(null, WorkoutImportUseCase.queryDisplayName(resolver, mockk()))
    }
}
