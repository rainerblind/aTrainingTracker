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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract tests verifying UI-thread GraphicsLayer rasterization and exception-safe snapshot cleanup
 * in MapDetailLayout (REQ-REV-006.5 / TST-REV-007.5 / ATT-6685).
 */
class MapDetailLayoutSnapshotContractTest {

    private fun findSourceFile(relativePath: String): File {
        val rootDir = File(".").canonicalFile
        val candidate = File(rootDir, relativePath)
        if (candidate.exists()) return candidate
        val appCandidate = File(rootDir, "app/$relativePath")
        if (appCandidate.exists()) return appCandidate
        val parentCandidate = File(rootDir, "../$relativePath")
        if (parentCandidate.exists()) return parentCandidate
        val parentAppCandidate = File(rootDir, "../app/$relativePath")
        if (parentAppCandidate.exists()) return parentAppCandidate
        throw IllegalArgumentException("Source file not found: $relativePath from ${rootDir.absolutePath}")
    }

    @Test
    fun testMapDetailLayout_onSnapshotReady_rasterizesOnMainThreadWithoutDefaultDispatcher_TST_REV_007_5() {
        val mapLayoutFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        assertTrue("MapDetailLayout.kt must exist", mapLayoutFile.exists())

        val source = mapLayoutFile.readText()

        // Extract the onSnapshotReady block
        val startIndex = source.indexOf("onSnapshotReady = {")
        assertTrue("onSnapshotReady callback must be declared in MapDetailLayout.kt", startIndex != -1)

        val endIndex = source.indexOf("onSnapshotError = {", startIndex)
        assertTrue("onSnapshotError must follow onSnapshotReady", endIndex != -1)

        val onSnapshotReadyBlock = source.substring(startIndex, endIndex)

        // 1. Invariant: GraphicsLayer.toImageBitmap() must NOT be wrapped in withContext(Dispatchers.Default)
        assertFalse(
            "onSnapshotReady must NOT execute toImageBitmap() inside withContext(Dispatchers.Default) " +
                "to prevent multithreaded SnapshotStateObserver crash (REQ-REV-006.5)",
            onSnapshotReadyBlock.contains("withContext(Dispatchers.Default)")
        )

        // 2. Layer rasterization calls must be present
        assertTrue(
            "onSnapshotReady must rasterize headerLayer directly",
            onSnapshotReadyBlock.contains("headerLayer.toImageBitmap().asAndroidBitmap()")
        )
        assertTrue(
            "onSnapshotReady must rasterize elevationLayer directly",
            onSnapshotReadyBlock.contains("elevationLayer.toImageBitmap().asAndroidBitmap()")
        )
        assertTrue(
            "onSnapshotReady must rasterize analyticsLayer directly",
            onSnapshotReadyBlock.contains("analyticsLayer.toImageBitmap().asAndroidBitmap()")
        )

        // 3. Robust lifecycle cleanup: try-catch-finally wrapping
        assertTrue(
            "onSnapshotReady must contain try block",
            onSnapshotReadyBlock.contains("try {")
        )
        assertTrue(
            "onSnapshotReady must contain catch block handling Throwable and showing Toast",
            onSnapshotReadyBlock.contains("catch (t: Throwable)") &&
                onSnapshotReadyBlock.contains("R.string.error_sharing_failed")
        )
        assertTrue(
            "onSnapshotReady must contain finally block resetting isSharing and notifying onSnapshotComplete",
            onSnapshotReadyBlock.contains("finally {") &&
                onSnapshotReadyBlock.contains("isSharing = false") &&
                onSnapshotReadyBlock.contains("onSnapshotComplete?.invoke()")
        )

        // 4. Downstream stitching invocation
        assertTrue(
            "onSnapshotReady must invoke combineWorkoutAndShare with rasterized bitmaps",
            onSnapshotReadyBlock.contains("combineWorkoutAndShare(context, hBmp, mapBitmap, eBmp, aBmp)")
        )
    }
}
