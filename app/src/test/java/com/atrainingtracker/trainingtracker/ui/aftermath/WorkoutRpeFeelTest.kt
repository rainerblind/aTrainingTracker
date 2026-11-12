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

package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.physiology.WorkoutTrainingLoadSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying REQ-WKO-035 through REQ-WKO-039 (TST-WKO-035 through TST-WKO-039).
 */
class WorkoutRpeFeelTest {

    // --- TST-WKO-035: Database schema & constants ---
    @Test
    fun testDbVersionAndColumnConstants() {
        assertEquals("DB_VERSION must be 30", 30, WorkoutSummariesDbHelper.DB_VERSION)
        assertEquals("Column constant RPE must be 'rpe'", "rpe", WorkoutSummaries.RPE)
        assertEquals("Column constant FEEL must be 'feel'", "feel", WorkoutSummaries.FEEL)
    }

    // --- TST-WKO-036: RPE & Feel Anchors and Emojis ---
    @Test
    fun testRpeAnchors_coverFullBorgScale1To10() {
        val expectedAnchors = mapOf(
            1 to R.string.rpe_anchor_1,
            2 to R.string.rpe_anchor_2,
            3 to R.string.rpe_anchor_3,
            4 to R.string.rpe_anchor_4,
            5 to R.string.rpe_anchor_5,
            6 to R.string.rpe_anchor_6,
            7 to R.string.rpe_anchor_7,
            8 to R.string.rpe_anchor_8,
            9 to R.string.rpe_anchor_9,
            10 to R.string.rpe_anchor_10
        )
        expectedAnchors.forEach { (score, expectedRes) ->
            assertEquals("RPE $score anchor string resource mismatch", expectedRes, getRpeAnchorStringRes(score))
        }
    }

    @Test
    fun testFeelEmojisAndAnchors_coverScale1To5() {
        val expectedEmojis = mapOf(
            1 to "😫",
            2 to "😕",
            3 to "😐",
            4 to "🙂",
            5 to "🤩"
        )
        val expectedAnchors = mapOf(
            1 to R.string.feel_anchor_1,
            2 to R.string.feel_anchor_2,
            3 to R.string.feel_anchor_3,
            4 to R.string.feel_anchor_4,
            5 to R.string.feel_anchor_5
        )
        expectedEmojis.forEach { (score, expectedEmoji) ->
            assertEquals("Feel $score emoji mismatch", expectedEmoji, getFeelEmoji(score))
        }
        expectedAnchors.forEach { (score, expectedRes) ->
            assertEquals("Feel $score anchor string resource mismatch", expectedRes, getFeelAnchorStringRes(score))
        }
    }

    @Test
    fun testTestTagsConstants() {
        assertEquals("workout_rpe_feel_section", WorkoutRpeFeelTestTags.SECTION)
        assertEquals("workout_rpe_badge", WorkoutRpeFeelTestTags.RPE_BADGE)
        assertEquals("workout_feel_badge", WorkoutRpeFeelTestTags.FEEL_BADGE)
        assertEquals("workout_srpe_badge", WorkoutRpeFeelTestTags.SRPE_BADGE)
        assertEquals("workout_rpe_feel_editor", WorkoutRpeFeelTestTags.EDITOR)
    }

    // --- TST-WKO-037: Reorderable section defaults & tab configuration ---
    @Test
    fun testSectionTypeOrderingDefaults() {
        assertEquals("RPE_AND_FEEL must be index 0 of DEFAULT_ORDER", WorkoutSectionType.RPE_AND_FEEL, WorkoutSectionType.DEFAULT_ORDER[0])

        val overviewTab = WorkoutDetailTabConfig.DEFAULT_TABS.find { it.preset == WorkoutDetailTabPreset.OVERVIEW }
        assertNotNull("Overview tab must exist", overviewTab)
        assertEquals("RPE_AND_FEEL must be index 0 of overview tab", WorkoutDetailSectionType.RPE_AND_FEEL, overviewTab!!.sections[0])
    }

    private fun createTestWorkout(rpe: Int? = null, feel: Int? = null): WorkoutData {
        return WorkoutData(
            id = 1L,
            finished = true,
            fileBaseName = "test",
            workoutName = "Test Workout",
            sportId = 1L,
            sportName = "Running",
            bSportType = com.atrainingtracker.banalservice.BSportType.RUN,
            startTimeS = 1700000000L,
            formattedDate = "2023-11-14",
            formattedTime = "10:00",
            localDateTime = java.time.LocalDateTime.now(),
            equipmentName = null,
            equipmentId = -1L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = 2000.0,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = 3.0,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 100.0,
            maxAltitude = 150.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            rpe = rpe,
            feel = feel
        )
    }

    @Test
    fun testSectionAvailability_hasRpeAndFeel() {
        val withRpe = createTestWorkout(rpe = 5)
        val withFeel = createTestWorkout(feel = 3)
        val withNeither = createTestWorkout()

        val availRpe = WorkoutDetailSectionAvailability.fromWorkoutState(
            workoutData = withRpe,
            showMap = false,
            hasGpsTrack = false,
            hasTracklessAltitude = false,
            hasAltitudeData = false,
            hasTelemetrySeries = false,
            hasZonesData = false,
            hasLapsData = false
        )
        val availFeel = WorkoutDetailSectionAvailability.fromWorkoutState(
            workoutData = withFeel,
            showMap = false,
            hasGpsTrack = false,
            hasTracklessAltitude = false,
            hasAltitudeData = false,
            hasTelemetrySeries = false,
            hasZonesData = false,
            hasLapsData = false
        )
        val availNeither = WorkoutDetailSectionAvailability.fromWorkoutState(
            workoutData = withNeither,
            showMap = false,
            hasGpsTrack = false,
            hasTracklessAltitude = false,
            hasAltitudeData = false,
            hasTelemetrySeries = false,
            hasZonesData = false,
            hasLapsData = false
        )

        assertTrue("hasRpeAndFeel should be true when rpe is set", availRpe.hasRpeAndFeel)
        assertTrue("hasRpeAndFeel should be true when feel is set", availFeel.hasRpeAndFeel)
        assertTrue("hasRpeAndFeel should be false when neither is set", !availNeither.hasRpeAndFeel)
    }

    // --- TST-WKO-038: Foster Session-RPE score calculation ---
    @Test
    fun testSessionRpeCalculation() {
        // 60 minutes at RPE 7 = 420.0
        val srpe = PowerPhysiologyCalculator.calculateSessionRpe(7, 3600.0)
        assertEquals(420.0, srpe, 1e-4)

        // 45 minutes at RPE 4 = 180.0
        val srpe2 = PowerPhysiologyCalculator.calculateSessionRpe(4, 2700.0)
        assertEquals(180.0, srpe2, 1e-4)

        // Invalid durations or out-of-range scores return 0.0
        assertEquals(0.0, PowerPhysiologyCalculator.calculateSessionRpe(5, 0.0), 1e-4)
        assertEquals(0.0, PowerPhysiologyCalculator.calculateSessionRpe(5, -100.0), 1e-4)
        assertEquals(0.0, PowerPhysiologyCalculator.calculateSessionRpe(0, 3600.0), 1e-4)
        assertEquals(0.0, PowerPhysiologyCalculator.calculateSessionRpe(11, 3600.0), 1e-4)
    }

    // --- TST-WKO-039: Tier 4 Session-RPE fallback in PMC training load ---
    @Test
    fun testTier4TrainingLoadFallback() {
        val sampleWithRpe = WorkoutTrainingLoadSample(
            timestampMs = 1_700_000_000_000L,
            activeTimeSeconds = 3600,
            powerTss = null,
            normalizedPowerWatts = null,
            avgPowerWatts = null,
            avgHrBpm = null,
            rpe = 6
        )
        // 6 * (3600 / 60.0) = 360.0
        val resolvedLoad = PowerPhysiologyCalculator.resolveWorkoutTrainingLoad(sampleWithRpe)
        assertEquals(360.0, resolvedLoad, 1e-4)

        // When power is available, power TSS takes precedence
        val sampleWithPowerAndRpe = WorkoutTrainingLoadSample(
            timestampMs = 1_700_000_000_000L,
            activeTimeSeconds = 3600,
            powerTss = 75.0,
            normalizedPowerWatts = 200.0,
            avgPowerWatts = 190.0,
            avgHrBpm = null,
            rpe = 6
        )
        val resolvedPowerLoad = PowerPhysiologyCalculator.resolveWorkoutTrainingLoad(sampleWithPowerAndRpe, ftpWatts = 250.0)
        assertEquals(75.0, resolvedPowerLoad, 1e-4)
    }
}
