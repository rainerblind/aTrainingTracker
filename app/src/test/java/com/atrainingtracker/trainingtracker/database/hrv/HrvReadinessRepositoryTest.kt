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

package com.atrainingtracker.trainingtracker.database.hrv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * TST-PER-038.6: Integration unit tests for [HrvReadinessRepository] verifying bounded window queries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HrvReadinessRepositoryTest {

    private lateinit var context: Context
    private lateinit var dbManager: HrvReadinessDatabaseManager
    private lateinit var repository: HrvReadinessRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(HrvReadinessDbHelper.DB_NAME)
        HrvReadinessDatabaseManager.resetForTesting(null)
        HrvReadinessRepository.resetForTesting(null)

        dbManager = HrvReadinessDatabaseManager.getInstance(context)
        repository = HrvReadinessRepository(dbManager, testDispatcher)
    }

    @After
    fun tearDown() {
        dbManager.clearDatabase()
        HrvReadinessDatabaseManager.resetForTesting(null)
        HrvReadinessRepository.resetForTesting(null)
    }

    @Test
    fun calculateReadiness_queriesOnlyBounded60DayWindow_TST_PER_038_6() = runTest(testDispatcher) {
        val targetDate = LocalDate.parse("2026-11-10")
        val dMinus59 = targetDate.minusDays(59).toString() // "2026-09-12"
        val dMinus60 = targetDate.minusDays(60).toString() // "2026-09-11" (outside window)

        // Reading at target date
        repository.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731222000000L,
                dateIso = targetDate.toString(),
                timezoneId = "UTC",
                restingHrBpm = 52,
                rmssdMs = 60.0,
                lnRmssd = 4.094,
                hrvScore100 = 81.88,
                durationSec = 60,
                posture = HrvPosture.SUPINE
            )
        )

        // Reading at D-59 (inside window boundary)
        repository.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1726124400000L,
                dateIso = dMinus59,
                timezoneId = "UTC",
                restingHrBpm = 50,
                rmssdMs = 62.0,
                lnRmssd = 4.127,
                hrvScore100 = 82.54,
                durationSec = 60,
                posture = HrvPosture.SUPINE
            )
        )

        // Reading at D-60 (outside window)
        repository.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1726038000000L,
                dateIso = dMinus60,
                timezoneId = "UTC",
                restingHrBpm = 51,
                rmssdMs = 61.0,
                lnRmssd = 4.111,
                hrvScore100 = 82.22,
                durationSec = 60,
                posture = HrvPosture.SUPINE
            )
        )

        val result = repository.calculateReadiness(targetDate.toString(), HrvPosture.SUPINE)

        assertNotNull(result)
        assertEquals("Target date must match", targetDate.toString(), result.targetDateIso)
        assertEquals("Posture must match", HrvPosture.SUPINE, result.posture)
        assertEquals("Only readings within [D-59, D] must be counted in 60d window", 2, result.n60d)
        assertEquals(2, result.calibrationDaysCount)
    }
}
