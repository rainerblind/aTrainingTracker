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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [LegacyImportEngine.ImportResult] data structure contract
 * and return code behavior for enriched import operations (ATT-2337 / REQ-MIG-032 / TST-MIG-029).
 */
class LegacyImportEngineResultTest {

    @Test
    fun importResult_successWithWorkoutId_holdsExpectedValues() {
        val result = LegacyImportEngine.ImportResult(
            status = LegacyImportEngine.ImportStatus.SUCCESS,
            workoutId = 9876L
        )

        assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, result.status)
        assertEquals(9876L, result.workoutId)
    }

    @Test
    fun importResult_duplicateSkipped_defaultsWorkoutIdToNull() {
        val result = LegacyImportEngine.ImportResult(
            status = LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED
        )

        assertEquals(LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED, result.status)
        assertNull(result.workoutId)
    }

    @Test
    fun importResult_failed_defaultsWorkoutIdToNull() {
        val result = LegacyImportEngine.ImportResult(
            status = LegacyImportEngine.ImportStatus.FAILED
        )

        assertEquals(LegacyImportEngine.ImportStatus.FAILED, result.status)
        assertNull(result.workoutId)
    }

    @Test
    fun importResult_equalityAndHashCodeContract() {
        val res1 = LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 42L)
        val res2 = LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 42L)
        val res3 = LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 43L)

        assertEquals(res1, res2)
        assertEquals(res1.hashCode(), res2.hashCode())
        assertFalse(res1 == res3)
    }
}
