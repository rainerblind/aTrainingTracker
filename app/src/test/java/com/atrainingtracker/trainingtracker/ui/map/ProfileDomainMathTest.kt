/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure mathematical unit test verifying domain resolution, trackless workout evaluation,
 * corrupt track fallback, and horizontal span calculation in [ProfileDomainMath].
 * (REQ-UI-261 / TST-UI-220)
 */
class ProfileDomainMathTest {

    private fun createPoint(distance: Double, timeSec: Long): PathPoint {
        return PathPoint(
            distance = distance,
            latLng = LatLng(48.0, 9.0),
            altitude = 500.0,
            timeSec = timeSec
        )
    }

    // --- isTracklessWorkout Tests ---

    @Test
    fun isTracklessWorkout_withStationaryData_returnsTrue() {
        val points = listOf(
            createPoint(0.0, 0L),
            createPoint(0.0, 600L),
            createPoint(0.0, 1800L)
        )
        assertTrue(ProfileDomainMath.isTracklessWorkout(points))
        assertTrue(ProfileDomainMath.isTracklessWorkout(0.0, 1800L))
    }

    @Test
    fun isTracklessWorkout_withStandardGpsTrack_returnsFalse() {
        val points = listOf(
            createPoint(0.0, 0L),
            createPoint(2500.0, 600L),
            createPoint(5000.0, 1200L)
        )
        assertFalse(ProfileDomainMath.isTracklessWorkout(points))
        assertFalse(ProfileDomainMath.isTracklessWorkout(5000.0, 1200L))
    }

    @Test
    fun isTracklessWorkout_withZeroTime_returnsFalse() {
        val points = listOf(createPoint(0.0, 0L))
        assertFalse(ProfileDomainMath.isTracklessWorkout(points))
        assertFalse(ProfileDomainMath.isTracklessWorkout(0.0, 0L))
    }

    @Test
    fun isTracklessWorkout_withEmptyOrNull_returnsFalse() {
        assertFalse(ProfileDomainMath.isTracklessWorkout(emptyList()))
        assertFalse(ProfileDomainMath.isTracklessWorkout(null))
    }

    // --- isEffectiveTimeDomain Tests ---

    @Test
    fun isEffectiveTimeDomain_forTracklessWorkout_alwaysReturnsTrue() {
        val points = listOf(createPoint(0.0, 1800L))
        // Regardless of whether athlete configured DISTANCE or TIME, stationary must be TIME
        assertTrue(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.DISTANCE, points))
        assertTrue(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, points))
        assertTrue(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.DISTANCE, 0.0, 1800L))
        assertTrue(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, 0.0, 1800L))
    }

    @Test
    fun isEffectiveTimeDomain_forStandardTrack_respectsDomainSetting() {
        val points = listOf(createPoint(5000.0, 1200L))
        assertTrue(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, points))
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.DISTANCE, points))
        assertTrue(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, 5000.0, 1200L))
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.DISTANCE, 5000.0, 1200L))
    }

    @Test
    fun isEffectiveTimeDomain_withCorruptOrMissingTimestamps_safelyFallsBackToDistance() {
        // Track has distance, but timeSec is 0 (missing timestamps)
        val points = listOf(createPoint(3000.0, 0L))
        // Even when athlete configured TIME, absence of timestamps MUST fall back to DISTANCE
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, points))
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.DISTANCE, points))
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, 3000.0, 0L))
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.DISTANCE, 3000.0, 0L))
    }

    @Test
    fun isEffectiveTimeDomain_withEmptyOrNull_returnsFalse() {
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, emptyList()))
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, null))
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.DISTANCE, emptyList()))
        assertFalse(ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.DISTANCE, null))
    }

    // --- calculateTotalSpan Tests ---

    @Test
    fun calculateTotalSpan_forStandardTrack_returnsExpectedDomainValues() {
        val points = listOf(createPoint(5000.0, 1200L))
        assertEquals(5000.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.DISTANCE, points), 0.001)
        assertEquals(1200.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.TIME, points), 0.001)
        assertEquals(5000.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.DISTANCE, 5000.0, 1200L), 0.001)
        assertEquals(1200.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.TIME, 5000.0, 1200L), 0.001)
    }

    @Test
    fun calculateTotalSpan_forTracklessWorkout_alwaysReturnsTimeSpan() {
        val points = listOf(createPoint(0.0, 1800L))
        assertEquals(1800.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.DISTANCE, points), 0.001)
        assertEquals(1800.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.TIME, points), 0.001)
        assertEquals(1800.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.DISTANCE, 0.0, 1800L), 0.001)
        assertEquals(1800.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.TIME, 0.0, 1800L), 0.001)
    }

    @Test
    fun calculateTotalSpan_withCorruptZeroTimestampTrack_fallsBackToDistanceSpan() {
        val points = listOf(createPoint(3000.0, 0L))
        assertEquals(3000.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.TIME, points), 0.001)
        assertEquals(3000.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.DISTANCE, points), 0.001)
    }

    @Test
    fun calculateTotalSpan_withEmptyOrNull_returnsZero() {
        assertEquals(0.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.DISTANCE, emptyList()), 0.001)
        assertEquals(0.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.TIME, emptyList()), 0.001)
        assertEquals(0.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.DISTANCE, null), 0.001)
        assertEquals(0.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.TIME, null), 0.001)
    }

    @Test
    fun calculateTotalSpan_coercesNegativeValuesToZero() {
        assertEquals(0.0, ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.DISTANCE, -100.0, 0L), 0.001)
    }
}
