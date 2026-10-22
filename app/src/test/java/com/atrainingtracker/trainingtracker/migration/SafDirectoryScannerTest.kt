/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SafDirectoryScanner] (REQ-UI-363, TST-UI-326, ATT-3491).
 */
class SafDirectoryScannerTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testIsMatchingFormat_matchesFormatsCaseInsensitively() {
        // FIT
        assertTrue(SafDirectoryScanner.isMatchingFormat("workout.fit", "fit"))
        assertTrue(SafDirectoryScanner.isMatchingFormat("WORKOUT.FIT", "fit"))
        assertTrue(SafDirectoryScanner.isMatchingFormat("Activity.Fit", "fit"))
        assertFalse(SafDirectoryScanner.isMatchingFormat("workout.tcx", "fit"))
        assertFalse(SafDirectoryScanner.isMatchingFormat("fit_without_ext", "fit"))

        // TCX
        assertTrue(SafDirectoryScanner.isMatchingFormat("activity.tcx", "tcx"))
        assertTrue(SafDirectoryScanner.isMatchingFormat("RIDE.TCX", "tcx"))
        assertFalse(SafDirectoryScanner.isMatchingFormat("activity.gpx", "tcx"))

        // GPX
        assertTrue(SafDirectoryScanner.isMatchingFormat("track.gpx", "gpx"))
        assertTrue(SafDirectoryScanner.isMatchingFormat("HIKE.GPX", "gpx"))
        assertFalse(SafDirectoryScanner.isMatchingFormat("track.fit", "gpx"))

        // ALL / AUTO
        assertTrue(SafDirectoryScanner.isMatchingFormat("workout.fit", "all"))
        assertTrue(SafDirectoryScanner.isMatchingFormat("activity.tcx", "all"))
        assertTrue(SafDirectoryScanner.isMatchingFormat("track.gpx", "all"))
        assertFalse(SafDirectoryScanner.isMatchingFormat("document.pdf", "all"))
        assertFalse(SafDirectoryScanner.isMatchingFormat("data.csv", "all"))
        assertFalse(SafDirectoryScanner.isMatchingFormat("photo.jpg", "auto"))
    }

    @Test
    fun testScanDocumentFile_nullOrNonDirectoryRoot_returnsEmptyList() = runBlocking {
        val resultNull = SafDirectoryScanner.scanDocumentFile(null, "fit")
        assertTrue(resultNull.isEmpty())

        val mockFile = mockk<DocumentFile>(relaxed = true)
        every { mockFile.isDirectory } returns false

        val resultNonDir = SafDirectoryScanner.scanDocumentFile(mockFile, "fit")
        assertTrue(resultNonDir.isEmpty())
    }

    @Test
    fun testScanDocumentFile_recursiveTraversal_discoversMatchingFiles() = runBlocking {
        val rootUri = mockk<Uri>()
        val subDirUri = mockk<Uri>()

        val file1Uri = mockk<Uri>()
        val file2Uri = mockk<Uri>()
        val file3Uri = mockk<Uri>()

        val mockFile1 = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns false
            every { isFile } returns true
            every { name } returns "workout_1.fit"
            every { uri } returns file1Uri
        }
        val mockFile2 = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns false
            every { isFile } returns true
            every { name } returns "workout_2.tcx"
            every { uri } returns file2Uri
        }
        val mockFile3 = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns false
            every { isFile } returns true
            every { name } returns "workout_3.FIT"
            every { uri } returns file3Uri
        }

        val mockSubDir = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns true
            every { isFile } returns false
            every { name } returns "2024"
            every { uri } returns subDirUri
            every { listFiles() } returns arrayOf(mockFile3)
        }

        val mockRootDir = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns true
            every { isFile } returns false
            every { name } returns "Workouts"
            every { uri } returns rootUri
            every { listFiles() } returns arrayOf(mockFile1, mockFile2, mockSubDir)
        }

        // Scan for FIT
        val fitResults = SafDirectoryScanner.scanDocumentFile(mockRootDir, "fit")
        assertEquals(2, fitResults.size)
        assertTrue(fitResults.contains(file1Uri))
        assertTrue(fitResults.contains(file3Uri))
        assertFalse(fitResults.contains(file2Uri))

        // Scan for TCX
        val tcxResults = SafDirectoryScanner.scanDocumentFile(mockRootDir, "tcx")
        assertEquals(1, tcxResults.size)
        assertTrue(tcxResults.contains(file2Uri))

        // Scan for ALL
        val allResults = SafDirectoryScanner.scanDocumentFile(mockRootDir, "all")
        assertEquals(3, allResults.size)
    }

    @Test
    fun testScanDocumentFile_depthLimitBound_ceasesTraversalAtMaxDepth() = runBlocking {
        val rootUri = mockk<Uri>()
        val level1Uri = mockk<Uri>()
        val level2Uri = mockk<Uri>()

        val shallowFileUri = mockk<Uri>()
        val deepFileUri = mockk<Uri>()

        val mockShallowFile = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns false
            every { isFile } returns true
            every { name } returns "shallow.fit"
            every { uri } returns shallowFileUri
        }
        val mockDeepFile = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns false
            every { isFile } returns true
            every { name } returns "deep.fit"
            every { uri } returns deepFileUri
        }

        val mockLevel2Dir = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns true
            every { isFile } returns false
            every { name } returns "level2"
            every { uri } returns level2Uri
            every { listFiles() } returns arrayOf(mockDeepFile)
        }

        val mockLevel1Dir = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns true
            every { isFile } returns false
            every { name } returns "level1"
            every { uri } returns level1Uri
            every { listFiles() } returns arrayOf(mockShallowFile, mockLevel2Dir)
        }

        val mockRootDir = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns true
            every { isFile } returns false
            every { name } returns "root"
            every { uri } returns rootUri
            every { listFiles() } returns arrayOf(mockLevel1Dir)
        }

        // With maxDepth = 1: root is depth 0, level1 is depth 1, level2 would be depth 2 (pruned)
        val results = SafDirectoryScanner.scanDocumentFile(mockRootDir, "fit", maxDepth = 1)
        assertEquals(1, results.size)
        assertTrue(results.contains(shallowFileUri))
        assertFalse(results.contains(deepFileUri))
    }

    @Test
    fun testScanDocumentFile_cycleDetection_preventsInfiniteLoop() = runBlocking {
        val dirAUri = mockk<Uri>()
        val dirBUri = mockk<Uri>()

        val mockDirA = mockk<DocumentFile>(relaxed = true)
        val mockDirB = mockk<DocumentFile>(relaxed = true)

        val fileUri = mockk<Uri>()
        val mockFile = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns false
            every { isFile } returns true
            every { name } returns "activity.fit"
            every { uri } returns fileUri
        }

        every { mockDirA.isDirectory } returns true
        every { mockDirA.isFile } returns false
        every { mockDirA.name } returns "dirA"
        every { mockDirA.uri } returns dirAUri
        every { mockDirA.listFiles() } returns arrayOf(mockFile, mockDirB)

        every { mockDirB.isDirectory } returns true
        every { mockDirB.isFile } returns false
        every { mockDirB.name } returns "dirB"
        every { mockDirB.uri } returns dirBUri
        every { mockDirB.listFiles() } returns arrayOf(mockDirA) // Cyclic reference!

        val results = SafDirectoryScanner.scanDocumentFile(mockDirA, "fit")
        assertEquals(1, results.size)
        assertEquals(fileUri, results[0])
    }

    @Test
    fun testScanDocumentFile_cancelSignal_abortsTraversalImmediately() = runBlocking {
        val rootUri = mockk<Uri>()
        val mockFile = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns false
            every { isFile } returns true
            every { name } returns "first.fit"
            every { uri } returns mockk()
        }

        val mockRootDir = mockk<DocumentFile>(relaxed = true) {
            every { isDirectory } returns true
            every { isFile } returns false
            every { name } returns "root"
            every { uri } returns rootUri
            every { listFiles() } returns arrayOf(mockFile)
        }

        var shouldCancel = true
        val results = SafDirectoryScanner.scanDocumentFile(
            mockRootDir,
            "fit",
            cancelSignal = { shouldCancel }
        )
        assertTrue(results.isEmpty())
    }
}
