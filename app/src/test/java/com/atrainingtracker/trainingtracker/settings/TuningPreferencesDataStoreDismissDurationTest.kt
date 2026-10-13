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
 * legacy 0 coercion, and 10s option inclusion (TST-UI-247 / REQ-UI-287 / ATT-3325).
 */
class TuningPreferencesDataStoreDismissDurationTest {

    @Test
    fun defaultConstants_matchExpectedSpecification() {
        assertEquals(4, TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC)
        assertEquals(listOf(2, 3, 4, 5, 8, 10), TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS)
    }

    @Test
    fun options_contains10SecondsAndExcludesZero() {
        assertTrue(TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS.contains(10))
        assertFalse(TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS.contains(0))
    }

    @Test
    fun tuningConfig_defaultsTo4Seconds() {
        val config = TuningConfig()
        assertEquals(4, config.navigationCueDismissDurationSec)
    }

    @Test
    fun tuningConfig_customDuration10s_retained() {
        val config = TuningConfig(navigationCueDismissDurationSec = 10)
        assertEquals(10, config.navigationCueDismissDurationSec)
    }

    @Test
    fun validation_legacyZeroCoercesToDefault() {
        val rawPref = 0
        val validated = if (rawPref in TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS) {
            rawPref
        } else {
            TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
        }
        assertEquals(4, validated)
    }

    @Test
    fun validation_unknownDurationCoercesToDefault() {
        val rawPref = 42
        val validated = if (rawPref in TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS) {
            rawPref
        } else {
            TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
        }
        assertEquals(4, validated)
    }

    @Test
    fun validation_allConfiguredOptionsAreValid() {
        for (sec in listOf(2, 3, 4, 5, 8, 10)) {
            val validated = if (sec in TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS) {
                sec
            } else {
                TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
            }
            assertEquals(sec, validated)
        }
    }
}
