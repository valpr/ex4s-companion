package com.valpr.bikecompanion

import com.valpr.bikecompanion.workout.WorkoutRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WorkoutRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        tempDir = File.createTempFile("workout_test_", "").apply {
            delete()
            mkdirs()
        }
        repository = WorkoutRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun saveAndLoadWorkout_validZwo_savesAndRetrievesSuccessfully() {
        val xml = """
            <workout_file>
                <name>Test Ride</name>
                <author>Tester</author>
                <description>A test description</description>
                <workout>
                    <SteadyState Duration="300" Power="0.80"/>
                </workout>
            </workout_file>
        """.trimIndent()

        val saveResult = repository.saveWorkout("test_ride.zwo", xml)
        assertTrue("Save should succeed", saveResult.isSuccess)

        val loadResult = repository.loadWorkout("test_ride.zwo")
        assertTrue("Load should succeed", loadResult.isSuccess)
        val workout = loadResult.getOrThrow()
        assertEquals("Test Ride", workout.name)
        assertEquals("Tester", workout.author)
        assertEquals(300, workout.totalDurationSeconds)
    }

    @Test
    fun getCachedWorkouts_returnsHeadersSortedByDate() {
        val xml1 = """
            <workout_file>
                <name>Workout A</name>
                <workout><SteadyState Duration="600" Power="0.75"/></workout>
            </workout_file>
        """.trimIndent()

        val xml2 = """
            <workout_file>
                <name>Workout B</name>
                <workout><SteadyState Duration="1200" Power="0.85"/></workout>
            </workout_file>
        """.trimIndent()

        repository.saveWorkout("workout_a.zwo", xml1)
        repository.saveWorkout("workout_b.zwo", xml2)

        val workouts = repository.getCachedWorkouts()
        assertEquals(2, workouts.size)
        val names = workouts.map { it.name }
        assertTrue(names.contains("Workout A"))
        assertTrue(names.contains("Workout B"))
    }

    @Test
    fun deleteWorkout_removesFile() {
        val xml = """
            <workout_file>
                <name>To Delete</name>
                <workout><SteadyState Duration="600" Power="0.75"/></workout>
            </workout_file>
        """.trimIndent()

        repository.saveWorkout("delete_me.zwo", xml)
        assertEquals(1, repository.getCachedWorkouts().size)

        val deleted = repository.deleteWorkout("delete_me.zwo")
        assertTrue(deleted)
        assertEquals(0, repository.getCachedWorkouts().size)
    }

    @Test
    fun saveWorkout_malformedXml_returnsFailureAndDoesNotWriteFile() {
        val brokenXml = "<workout_file><name>Broken"

        val result = repository.saveWorkout("broken.zwo", brokenXml)
        assertTrue(result.isFailure)
        assertFalse(File(tempDir, "broken.zwo").exists())
    }

    @Test
    fun importSampleWorkoutsIfEmpty_seedsWorkoutsWhenEmpty() {
        assertEquals(0, repository.getCachedWorkouts().size)

        repository.importSampleWorkoutsIfEmpty()
        val seeded = repository.getCachedWorkouts()
        assertEquals(2, seeded.size)

        // Running a second time should not duplicate files
        repository.importSampleWorkoutsIfEmpty()
        assertEquals(2, repository.getCachedWorkouts().size)
    }

    @Test
    fun sanitizeFilename_blocksTraversalAndNormalizes() {
        assertEquals("workout.zwo", repository.sanitizeFilename("workout.zwo"))
        assertEquals("MY_RIDE.ZWO", repository.sanitizeFilename("MY_RIDE.ZWO"))
        val traversal = repository.sanitizeFilename("../evil.zwo")
        assertFalse(traversal.contains("/"))
        assertTrue(traversal.endsWith(".zwo", ignoreCase = true))
        assertEquals("my_ride.zwo", repository.sanitizeFilename("my ride"))
    }
}
