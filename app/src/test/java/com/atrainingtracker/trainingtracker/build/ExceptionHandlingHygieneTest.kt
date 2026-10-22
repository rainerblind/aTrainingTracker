/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.build

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploader
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.ForkNavigationRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit and architectural policy verification suite for exception handling hygiene (REQ-ARC-002, TST-ARC-002).
 * Verifies behavioral error recovery in uploaders, repositories, and cluster managers alongside static policy enforcement.
 */
class ExceptionHandlingHygieneTest {

    private lateinit var mockContext: Context

    private val auditedRelativePaths = listOf(
        "app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java",
        "app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepository.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/migration/MigrationEngine.kt",
        "app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt",
        "app/src/main/java/com/atrainingtracker/banalservice/MyBanalDebugHelper.java"
    )

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File(relativePath.removePrefix("app/")),
            File("../${relativePath.removePrefix("app/")}")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in ${File(".").absolutePath}")
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        // Clean thread interrupt state if left active
        Thread.interrupted()
    }

    // ------------------------------------------------------------------------
    // 1. Behavioral Unit Tests (REQ-TST-002 / REQ-ARC-002)
    // ------------------------------------------------------------------------

    @Test
    fun testStravaUploader_parseTimeToEpochSeconds_validFormats_returnCorrectEpochSeconds() {
        val uploader = StravaUploader(mockContext)
        val expectedEpoch = 1715508930L // 2024-05-12T10:15:30Z

        // 1. ISO 8601
        assertEquals(expectedEpoch, uploader.parseTimeToEpochSeconds("2024-05-12T10:15:30Z"))

        // 2. SQLite format
        assertEquals(expectedEpoch, uploader.parseTimeToEpochSeconds("2024-05-12 10:15:30"))

        // 3. fileBaseName without inner hyphens
        assertEquals(expectedEpoch, uploader.parseTimeToEpochSeconds("2024-05-12_101530"))

        // 4. fileBaseName with hyphens
        assertEquals(expectedEpoch, uploader.parseTimeToEpochSeconds("2024-05-12_10-15-30"))
    }

    @Test
    fun testStravaUploader_parseTimeToEpochSeconds_malformedInputs_returnsNullGracefully() {
        val uploader = StravaUploader(mockContext)

        assertNull(uploader.parseTimeToEpochSeconds("invalid-date-string"))
        assertNull(uploader.parseTimeToEpochSeconds(""))
        assertNull(uploader.parseTimeToEpochSeconds("   "))
        assertNull(uploader.parseTimeToEpochSeconds("2024/05/12 10:15:30"))
        assertNull(uploader.parseTimeToEpochSeconds("9999-99-99 99:99:99"))

        // Verify warning logging occurred for corrupt strings
        verify(atLeast = 1) { Log.w(any<String>(), match<String> { it.contains("Failed to parse time string") || it.contains("Cannot parse blank") }) }
    }

    @Test
    fun testStravaUploader_sleepStrategy_whenInterrupted_restoresThreadInterruptFlag() {
        val uploader = StravaUploader(mockContext)

        // Set thread interrupt flag prior to sleep
        Thread.currentThread().interrupt()
        assertTrue("Thread should be interrupted before sleepStrategy", Thread.currentThread().isInterrupted)

        uploader.sleepStrategy(5000L)

        // Verify thread interrupt flag was restored
        assertTrue("Thread interrupt flag must remain true after sleepStrategy", Thread.currentThread().isInterrupted)

        // Clear flag
        Thread.interrupted()
    }

    @Test
    fun testWorkoutClusterDatabaseManager_deserializePreviewPaths_validAndMalformedJson() {
        // Valid JSON array
        val validJson = "[\"path1\", \"path2\", \"path3\"]"
        val validList = WorkoutClusterDatabaseManager.deserializePreviewPaths(validJson)
        assertEquals(listOf("path1", "path2", "path3"), validList)

        // Empty string
        val emptyList = WorkoutClusterDatabaseManager.deserializePreviewPaths("")
        assertTrue(emptyList.isEmpty())

        // Malformed JSON (syntax error)
        val malformedJson = "{invalid json: [not array"
        val fallbackList1 = WorkoutClusterDatabaseManager.deserializePreviewPaths(malformedJson)
        assertTrue("Malformed JSON must safely fall back to emptyList()", fallbackList1.isEmpty())

        // Non-array JSON
        val nonArrayJson = "\"a plain string\""
        val fallbackList2 = WorkoutClusterDatabaseManager.deserializePreviewPaths(nonArrayJson)
        assertTrue("Non-array JSON must safely fall back to emptyList()", fallbackList2.isEmpty())

        verify(atLeast = 1) { Log.w(any<String>(), match<String> { it.contains("Failed to deserialize preview paths JSON") }, any<Throwable>()) }
    }

    @Test
    fun testForkNavigationRepository_nullLastSearchPos_executesGracefully() {
        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        every { mockRoutesRepo.allRoutes } returns MutableStateFlow(emptyList())
        every { mockRoutesRepo.activeNavigatedRouteId } returns MutableStateFlow(null)

        val repo = ForkNavigationRepository(mockRoutesRepo)
        try {
            // Initial state has lastSearchPos == null. Updating location should not crash with NPE or force-unwrap.
            repo.onLocationChanged(LatLng(48.137, 11.575))
            assertNull(repo.forkDecisionState.value)
        } finally {
            repo.cancelScope()
        }
    }

    // ------------------------------------------------------------------------
    // 2. Static Architectural Policy Guards (REQ-ARC-002)
    // ------------------------------------------------------------------------

    @Test
    fun testProductionSources_containZeroPrintStackTrace() {
        val printStackTraceRegex = Regex("""\.printStackTrace\s*\(\s*\)""")

        for (path in auditedRelativePaths) {
            val file = findRepoRootFile(path)
            val content = file.readText()
            val matches = printStackTraceRegex.findAll(content).toList()
            assertTrue(
                "Source file ${file.name} must contain 0 printStackTrace() calls, found ${matches.size}",
                matches.isEmpty()
            )
        }
    }

    @Test
    fun testProductionSources_containZeroCatchThrowable() {
        // Matches catch (Throwable t), catch (_: Throwable), catch (ignored: Throwable), etc.
        val catchThrowableRegex = Regex("""catch\s*\(\s*(?:[a-zA-Z0-9_]+:\s*)?Throwable\b""")

        for (path in auditedRelativePaths) {
            val file = findRepoRootFile(path)
            val content = file.readText()
            val matches = catchThrowableRegex.findAll(content).toList()
            assertTrue(
                "Source file ${file.name} must not catch Throwable, found ${matches.size} violations",
                matches.isEmpty()
            )
        }
    }

    @Test
    fun testProductionSources_containZeroEmptyCatchBlocks() {
        // Matches catch (...) {} with only whitespace inside the block
        val emptyCatchRegex = Regex("""catch\s*\([^\)]*\)\s*\{\s*\}""")

        for (path in auditedRelativePaths) {
            val file = findRepoRootFile(path)
            val content = file.readText()
            val matches = emptyCatchRegex.findAll(content).toList()
            assertTrue(
                "Source file ${file.name} must not contain empty catch blocks, found ${matches.size} violations",
                matches.isEmpty()
            )
        }
    }
}
