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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Fragment navigation verification for [PeriodsFragment.startWorkoutSummaryList]
 * ensuring sport & period boundary arguments propagate cleanly into [WorkoutSummariesListFragment]
 * (REQ-PER-008, TST-PER-009, ATT-8324).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PeriodsNavigationFilterTest {

    @Before
    fun setUp() {
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true) {
            every { allWorkouts } returns MutableStateFlow(emptyList())
        }
        val mockPeriodsRepo = mockk<PeriodsRepository>(relaxed = true) {
            every { groupedPeriods } returns MutableStateFlow(listOf(emptyList(), emptyList(), emptyList(), emptyList()))
            every { migrationStatus } returns MutableStateFlow<MigrationStatus?>(null)
            coEvery { checkIntegrityAndSync() } returns false
        }
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)
    }

    @After
    fun tearDown() {
        PeriodsRepository.resetInstanceForTesting()
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    /**
     * TST-PER-009 (REQ-PER-008): Clicking a sport row on a PeriodSummary launches
     * WorkoutSummariesListFragment with matching ARG_BSPORT_TYPE, ARG_START_S, and ARG_END_S.
     */
    @Test
    fun startWorkoutSummaryList_propagatesSportAndTemporalBoundsArguments_TST_PER_009() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        val container = FrameLayout(activity).apply { id = R.id.content }
        activity.setContentView(container)

        val periodsFragment = PeriodsFragment.newInstance()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.content, periodsFragment)
            .commitNow()

        val summary = PeriodSummary(
            periodLabel = "March 2026",
            periodDateRange = "Mar 1 - Mar 31, 2026",
            periodType = PeriodType.MONTH,
            startTimestampS = 1_772_323_200L,
            endTimestampS = 1_774_915_200L,
            totalWorkouts = 7,
            totalDurationSec = 14_400L,
            totalDistance = 80_000.0,
            sportStats = mapOf(
                BSportType.BIKE to SportStats(4, 9_600L, 60_000.0, 500L, emptyMap(), null),
                BSportType.RUN to SportStats(3, 4_800L, 20_000.0, 150L, emptyMap(), null)
            ),
            sortKey = "2026-03"
        )

        periodsFragment.startWorkoutSummaryList(
            periodSummary = summary,
            bSportType = BSportType.RUN
        )
        activity.supportFragmentManager.executePendingTransactions()

        val launched = activity.supportFragmentManager.findFragmentById(R.id.content)
        val args = (launched as? WorkoutSummariesListFragment)?.arguments
        assertNotNull("Expected WorkoutSummariesListFragment in R.id.content", args)
        assertEquals(
            BSportType.RUN,
            args!!.getSerializable(WorkoutSummariesListFragment.ARG_BSPORT_TYPE)
        )
        assertEquals(summary.startTimestampS, args.getLong(WorkoutSummariesListFragment.ARG_START_S))
        assertEquals(summary.endTimestampS, args.getLong(WorkoutSummariesListFragment.ARG_END_S))
    }
}
