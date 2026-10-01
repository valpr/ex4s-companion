package com.valpr.bikecompanion

import com.valpr.bikecompanion.history.WorkoutHistoryRepository
import com.valpr.bikecompanion.workout.WorkoutMetricSample
import com.valpr.bikecompanion.workout.WorkoutSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WorkoutHistoryRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: WorkoutHistoryRepository

    @Before
    fun setUp() {
        tempDir = File.createTempFile("history_test_", "").apply {
            delete()
            mkdirs()
        }
        repository = WorkoutHistoryRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun sampleSummary(
        name: String = "First Pedals (15 min) - Beginner 1/4",
        startMs: Long = 1_700_000_000_000L,
        duration: Int = 900,
        seconds: IntRange = 1..5
    ) = WorkoutSummary(
        workoutName = name,
        totalDurationSeconds = duration,
        totalDistanceKm = 5.0,
        avgWatts = 120,
        maxWatts = 180,
        avgCadence = 75,
        maxCadence = 90,
        avgHeartRate = 130,
        maxHeartRate = 150,
        totalWorkKj = 108.0,
        totalCaloriesKcal = 108,
        samples = seconds.map {
            WorkoutMetricSample(
                elapsedSeconds = it,
                watts = 120,
                targetWatts = 120,
                cadenceRpm = 75,
                targetCadence = 75,
                resistance = 8,
                speedKmh = 20.0,
                heartRateBpm = 130
            )
        },
        startTimeEpochMs = startMs
    )

    @Test
    fun save_newRide_writesFileAndHeader() {
        assertTrue(repository.save(sampleSummary(), "beginner_01_first_pedals.zwo"))

        val headers = repository.listHeaders()
        assertEquals(1, headers.size)
        assertEquals("First Pedals (15 min) - Beginner 1/4", headers[0].workoutName)
        assertEquals("beginner_01_first_pedals.zwo", headers[0].sourceWorkoutFilename)
        assertEquals(120, headers[0].avgWatts)
    }

    @Test
    fun save_sameSessionTwice_isIdempotent() {
        val summary = sampleSummary()
        assertTrue(repository.save(summary))
        assertFalse(repository.save(summary))

        assertEquals(1, repository.listHeaders().size)
    }

    @Test
    fun listHeaders_newestFirst() {
        repository.save(sampleSummary(name = "Ride A", startMs = 1_700_000_000_000L))
        repository.save(sampleSummary(name = "Ride B", startMs = 1_700_000_001_000L))

        val headers = repository.listHeaders()
        assertEquals(2, headers.size)
        assertEquals("Ride B", headers[0].workoutName)
        assertEquals("Ride A", headers[1].workoutName)
    }

    @Test
    fun loadRide_returnsFullSamples() {
        repository.save(sampleSummary())
        val id = repository.listHeaders().single().id

        val ride = repository.loadRide(id)
        assertNotNull(ride)
        assertEquals(5, ride!!.samples.size)
        assertEquals(120, ride.samples[0].watts)
        assertEquals(130, ride.samples[0].heartRateBpm)
    }

    @Test
    fun loadRide_missing_returnsNull() {
        assertNull(repository.loadRide("ride_0"))
    }

    @Test
    fun loadRide_corruptFile_returnsNull() {
        repository.save(sampleSummary())
        val id = repository.listHeaders().single().id
        File(tempDir, "$id.json").writeText("{ not valid json", Charsets.UTF_8)

        assertNull(repository.loadRide(id))
    }

    @Test
    fun listHeaders_corruptFile_skipsEntry() {
        repository.save(sampleSummary(name = "Good", startMs = 1_700_000_000_000L))
        repository.save(sampleSummary(name = "Bad", startMs = 1_700_000_001_000L))
        val badId = repository.listHeaders().first { it.workoutName == "Bad" }.id
        File(tempDir, "$badId.json").writeText("corrupt", Charsets.UTF_8)
        // Force index rebuild by deleting the index.
        File(tempDir, WorkoutHistoryRepository.INDEX_FILENAME).delete()

        val headers = repository.listHeaders()
        assertEquals(1, headers.size)
        assertEquals("Good", headers[0].workoutName)
    }

    @Test
    fun delete_removesFileAndHeader() {
        repository.save(sampleSummary())
        val id = repository.listHeaders().single().id

        assertTrue(repository.delete(id))
        assertTrue(repository.listHeaders().isEmpty())
        assertNull(repository.loadRide(id))
    }

    @Test
    fun completedFilenames_returnsLowercasedSources() {
        repository.save(sampleSummary(startMs = 1_700_000_000_000L), "Beginner_01_First_Pedals.ZWO")
        repository.save(sampleSummary(name = "Free Ride", startMs = 1_700_000_001_000L))

        assertEquals(setOf("beginner_01_first_pedals.zwo"), repository.completedFilenames())
    }

    @Test
    fun save_zeroLengthSession_clampedToOneSecond() {
        val summary = sampleSummary(duration = 0)
        assertTrue(repository.save(summary))

        val headers = repository.listHeaders()
        assertEquals(1, headers[0].totalDurationSeconds)
    }

    @Test
    fun save_zeroStartTimestamp_isStablyIdempotent() {
        val summary = sampleSummary(startMs = 0L)
        assertTrue(repository.save(summary))
        assertFalse(repository.save(summary))

        assertEquals(1, repository.listHeaders().size)
    }

    @Test
    fun listHeaders_orphanedFile_healsIndex() {
        repository.save(sampleSummary(name = "Ride A", startMs = 1_700_000_000_000L))
        repository.save(sampleSummary(name = "Ride B", startMs = 1_700_000_001_000L))
        // Simulate a crash between file write and index update: drop B from the index.
        // B is newest, so its entry leads the array; strip it plus the separator.
        val indexFile = File(tempDir, WorkoutHistoryRepository.INDEX_FILENAME)
        val rideBId = repository.listHeaders().first { it.workoutName == "Ride B" }.id
        val stored = kotlinx.serialization.json.Json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(
                com.valpr.bikecompanion.history.RideHeader.serializer()
            ),
            indexFile.readText(Charsets.UTF_8)
        ).filter { it.id != rideBId }
        indexFile.writeText(
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    com.valpr.bikecompanion.history.RideHeader.serializer()
                ),
                stored
            ),
            Charsets.UTF_8
        )
        // Sanity: B's entry is gone from the raw index while its file remains.
        assertFalse(indexFile.readText(Charsets.UTF_8).contains(rideBId))
        assertTrue(File(tempDir, "$rideBId.json").exists())

        val headers = repository.listHeaders()
        assertEquals(2, headers.size)
        assertTrue(headers.map { it.workoutName }.contains("Ride B"))
        // Index repaired: a second read still sees both without rescanning luck.
        assertEquals(2, repository.listHeaders().size)
    }

    @Test
    fun loadRide_corruptFile_evictsFromList() {
        repository.save(sampleSummary())
        val id = repository.listHeaders().single().id
        File(tempDir, "$id.json").writeText("corrupt", Charsets.UTF_8)

        assertNull(repository.loadRide(id))
        // List and detail agree again: the garbage record is gone.
        assertTrue(repository.listHeaders().isEmpty())
    }

    @Test
    fun delete_missing_returnsFalse() {
        assertFalse(repository.delete("ride_0"))
    }
}
