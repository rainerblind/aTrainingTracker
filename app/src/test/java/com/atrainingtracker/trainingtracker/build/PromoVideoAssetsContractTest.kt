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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the Google Play Store promo video storyboard, composite generator tooling,
 * and rendered video/keyframe assets (REQ-STP-006, TST-STP-006, ATT-2274).
 */
class PromoVideoAssetsContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    private fun getPngDimensions(file: File): Pair<Int, Int> {
        file.inputStream().use { stream ->
            val header = ByteArray(24)
            val read = stream.read(header)
            if (read < 24) throw IllegalArgumentException("File too small for PNG header")
            if (header[0] != 0x89.toByte() || header[1] != 'P'.code.toByte() ||
                header[2] != 'N'.code.toByte() || header[3] != 'G'.code.toByte()) {
                throw IllegalArgumentException("Not a valid PNG file: ${file.name}")
            }
            val width = ((header[16].toInt() and 0xFF) shl 24) or
                    ((header[17].toInt() and 0xFF) shl 16) or
                    ((header[18].toInt() and 0xFF) shl 8) or
                    (header[19].toInt() and 0xFF)
            val height = ((header[20].toInt() and 0xFF) shl 24) or
                    ((header[21].toInt() and 0xFF) shl 16) or
                    ((header[22].toInt() and 0xFF) shl 8) or
                    (header[23].toInt() and 0xFF)
            return Pair(width, height)
        }
    }

    @Test
    fun testPromoVideoStoryboard_existsAndValidatesScenes() {
        val storyboardFile = findRepoRootFile("docs/store_presence/promo_video_storyboard.md")
        assertTrue("promo_video_storyboard.md must exist", storyboardFile.exists() && storyboardFile.length() > 500)

        val content = storyboardFile.readText()

        val mandatoryScenes = listOf(
            "Scene 1 (0:00 – 0:10)",
            "Scene 2 (0:10 – 0:25)",
            "Scene 3 (0:25 – 0:40)",
            "Scene 4 (0:40 – 0:50)",
            "Scene 5 (0:50 – 1:00)"
        )

        for (scene in mandatoryScenes) {
            assertTrue("Storyboard must contain scene heading '$scene'", content.contains(scene))
        }

        assertTrue(
            "Storyboard must detail custom handlebar photo replacement workflow",
            content.contains("Custom Outdoor Handlebar Photo Replacement Guide")
        )
    }

    @Test
    fun testGeneratePromoVideoScript_existsAndSupportsEncoders() {
        val scriptFile = findRepoRootFile("tools/generate_promo_video.py")
        assertTrue("tools/generate_promo_video.py must exist", scriptFile.exists() && scriptFile.canExecute())

        val content = scriptFile.readText()
        assertTrue("Script must declare CLI arguments", content.contains("--bg-photo") && content.contains("--output-video"))
        assertTrue("Script must implement GStreamer encoder backend", content.contains("gst-launch-1.0") && content.contains("x264enc"))
        assertTrue("Script must implement procedural handlebar generator fallback", content.contains("generate_procedural_handlebar_background"))
    }

    @Test
    fun testPromoVideoAssets_existAndMeetResolutionStandards() {
        val videoFile = findRepoRootFile("docs/store_presence/video/att_promo_showcase.mp4")
        assertTrue(
            "Rendered showcase video docs/store_presence/video/att_promo_showcase.mp4 must exist and exceed 100 KB (size=${videoFile.length()})",
            videoFile.exists() && videoFile.length() > 100_000
        )

        val expectedFrames = listOf(
            "scene_01_cockpit.png",
            "scene_02_sensors.png",
            "scene_03_navigation.png",
            "scene_04_clustering.png",
            "scene_05_privacy_export.png"
        )

        for (frameName in expectedFrames) {
            val frameFile = findRepoRootFile("docs/store_presence/video/frames/$frameName")
            assertTrue("Keyframe $frameName must exist", frameFile.exists() && frameFile.length() > 10_000)

            val (width, height) = getPngDimensions(frameFile)
            assertEquals("Keyframe $frameName width must be 1920", 1920, width)
            assertEquals("Keyframe $frameName height must be 1080", 1080, height)
        }

        val walkthroughImg1 = findRepoRootFile("docs/engineering/walkthroughs/img/att2274_scene_01_cockpit.png")
        val walkthroughImg3 = findRepoRootFile("docs/engineering/walkthroughs/img/att2274_scene_03_navigation.png")
        assertTrue("Walkthrough preview image 1 must exist", walkthroughImg1.exists() && walkthroughImg1.length() > 10_000)
        assertTrue("Walkthrough preview image 3 must exist", walkthroughImg3.exists() && walkthroughImg3.length() > 10_000)
    }
}
