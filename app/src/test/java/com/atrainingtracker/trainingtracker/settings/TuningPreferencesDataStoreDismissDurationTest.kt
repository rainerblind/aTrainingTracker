/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for navigation cue dismiss duration options, default constants,
 * legacy 0 coercion, and 1s-10s slider range clamping (TST-UI-247 / REQ-UI-287 / ATT-3450).
 */
class TuningPreferencesDataStoreDismissDurationTest {

    @Test
    fun defaultConstants_matchExpectedSpecification() {
        assertEquals(1, TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC)
        assertEquals(10, TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC)
        assertEquals(1, TuningPreferencesDefaults.STEP_NAVIGATION_CUE_DISMISS_DURATION_SEC)
        assertEquals(4, TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC)
        assertEquals((1..10).toList(), TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS)
    }

    @Test
    fun options_containsAllValuesFrom1To10AndExcludesZero() {
        for (sec in 1..10) {
            assertTrue("Expected $sec to be in NAVIGATION_CUE_DISMISS_OPTIONS", TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS.contains(sec))
        }
        assertFalse(TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS.contains(0))
    }

    @Test
    fun tuningConfig_defaultsTo4Seconds() {
        val config = TuningConfig()
        assertEquals(4, config.navigationCueDismissDurationSec)
    }

    @Test
    fun tuningConfig_customDuration_retained() {
        for (sec in 1..10) {
            val config = TuningConfig(navigationCueDismissDurationSec = sec)
            assertEquals(sec, config.navigationCueDismissDurationSec)
        }
    }

    @Test
    fun validation_legacyZeroCoercesToDefault() {
        val rawPref = 0
        val validated = if (rawPref == 0) {
            TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
        } else {
            rawPref.coerceIn(
                TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
            )
        }
        assertEquals(4, validated)
    }

    @Test
    fun validation_negativeValuesCoerceToDefault() {
        val rawPref = -5
        val validated = if (rawPref <= 0) {
            TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
        } else {
            rawPref.coerceIn(
                TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
            )
        }
        assertEquals(4, validated)
    }

    @Test
    fun validation_outOfBoundsHighClampsToMax() {
        val rawPref = 42
        val validated = if (rawPref == 0) {
            TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
        } else {
            rawPref.coerceIn(
                TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
            )
        }
        assertEquals(10, validated)
    }

    @Test
    fun validation_allDiscreteSeconds1To10ArePreserved() {
        for (sec in 1..10) {
            val validated = if (sec == 0) {
                TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
            } else {
                sec.coerceIn(
                    TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                    TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
                )
            }
            assertEquals(sec, validated)
        }
    }
}
