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

package com.atrainingtracker.banalservice.odometry

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM unit tests for [MultisportTransitionDetector] (`ATT-8917`, `REQ-FIL-031.1`, `TST-FIL-031.1`).
 */
// Linked test specs: TST-FIL-031
class MultisportTransitionDetectorTest {

    private val baseEpochMs = 1_760_000_000_000L

    private fun tick(
        detector: MultisportTransitionDetector,
        sec: Int,
        mode: PhoneCarriageMode,
        kinematicSport: BSportType = BSportType.UNKNOWN,
        hw: MultisportHardwareSignals = MultisportHardwareSignals(),
        activeS: Int = sec,
        totalS: Int = sec,
        distM: Double = sec * 5.0,
        isTracking: Boolean = true,
        isPaused: Boolean = false,
        isUserLocked: Boolean = false
    ): MultisportTransitionEvent? {
        return detector.onTick(
            carriageMode = mode,
            kinematicSport = kinematicSport,
            hardwareSignals = hw,
            anchor = MultisportTelemetryAnchor(
                timestampMs = baseEpochMs + sec * 1_000L,
                activeTimeS = activeS,
                totalTimeS = totalS,
                totalDistanceM = distM
            ),
            isTracking = isTracking,
            isPausedOrAutoPaused = isPaused,
            isUserSportLocked = isUserLocked
        )
    }

    @Test
    fun bikeToRunBrick_with50sT2_confirmsAt1865sWithExactRetroactiveAnchors() {
        val detector = MultisportTransitionDetector()
        var confirmed: MultisportTransitionEvent? = null

        // Bike leg: t = 0..1799 s (8 m/s)
        for (s in 0..1799) {
            val ev = tick(detector, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE, distM = s * 8.0)
            assertNull("No transition during bike leg at t=$s", ev)
        }
        assertEquals(BSportType.BIKE, detector.currentEstablishedSport)

        // T2 walking/racking: t = 1800..1849 s (1 m/s -> +50 m)
        for (s in 1800..1849) {
            val mode = if (s % 5 == 0) PhoneCarriageMode.STATIONARY else PhoneCarriageMode.WALKING_TRANSITION
            val dist = 1800.0 * 8.0 + (s - 1800) * 1.0
            val ev = tick(detector, s, mode, BSportType.BIKE, distM = dist)
            assertNull("No transition during T2 at t=$s", ev)
        }

        // Run leg: t = 1850..1865 s (4 m/s -> +60 m over 15 s)
        for (s in 1850..1865) {
            val dist = 1800.0 * 8.0 + 50.0 + (s - 1850) * 4.0
            val ev = tick(detector, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = dist)
            if (s < 1865) {
                assertNull("Must not confirm before 15 s continuous RUN (t=$s)", ev)
            } else {
                confirmed = ev
            }
        }

        assertNotNull("Expected confirmed BIKE->RUN transition at t=1865 s", confirmed)
        val event = confirmed!!
        assertEquals(BSportType.BIKE, event.fromSport)
        assertEquals(BSportType.RUN, event.toSport)
        assertEquals(baseEpochMs + 1800_000L, event.exitAnchor.timestampMs)
        assertEquals(1800, event.exitAnchor.activeTimeS)
        assertEquals(14400.0, event.exitAnchor.totalDistanceM, 0.01)
        assertEquals(baseEpochMs + 1850_000L, event.enterAnchor.timestampMs)
        assertEquals(1850, event.enterAnchor.activeTimeS)
        assertEquals(14450.0, event.enterAnchor.totalDistanceM, 0.01)
        assertEquals(50_000L, event.transitionWallClockDurationMs)
        assertEquals(baseEpochMs + 1865_000L, event.confirmAnchor.timestampMs)
        assertEquals(1865, event.confirmAnchor.activeTimeS)
        assertEquals(14510.0, event.confirmAnchor.totalDistanceM, 0.01)
        assertEquals(BSportType.RUN, detector.currentEstablishedSport)
    }

    @Test
    fun duathlon_runToT1ToBikeToT2ToRun_detectsBothTransitions() {
        val detector = MultisportTransitionDetector()
        val events = mutableListOf<MultisportTransitionEvent>()

        // Run 1: 0..29 s
        for (s in 0..29) tick(detector, s, PhoneCarriageMode.RUN_BICEP_ARM, BSportType.RUN)?.let(events::add)
        // T1: 30..59 s (30 s)
        for (s in 30..59) tick(detector, s, PhoneCarriageMode.WALKING_TRANSITION, BSportType.RUN)?.let(events::add)
        // Bike: 60..119 s (confirms at 75 s)
        for (s in 60..119) tick(detector, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE)?.let(events::add)
        // T2: 120..149 s (30 s)
        for (s in 120..149) tick(detector, s, PhoneCarriageMode.STATIONARY, BSportType.BIKE)?.let(events::add)
        // Run 2: 150..170 s (confirms at 165 s)
        for (s in 150..170) tick(detector, s, PhoneCarriageMode.RUN_HANDHELD, BSportType.RUN)?.let(events::add)

        assertEquals(2, events.size)
        assertEquals(BSportType.RUN, events[0].fromSport)
        assertEquals(BSportType.BIKE, events[0].toSport)
        assertEquals(30, events[0].exitAnchor.activeTimeS)
        assertEquals(60, events[0].enterAnchor.activeTimeS)
        assertEquals(75, events[0].confirmAnchor.activeTimeS)

        assertEquals(BSportType.BIKE, events[1].fromSport)
        assertEquals(BSportType.RUN, events[1].toSport)
        assertEquals(120, events[1].exitAnchor.activeTimeS)
        assertEquals(150, events[1].enterAnchor.activeTimeS)
        assertEquals(165, events[1].confirmAnchor.activeTimeS)
    }

    @Test
    fun sameSportBakeryStop_160sStationaryAndWalking_emitsZeroTransitions() {
        val detector = MultisportTransitionDetector()

        for (s in 0..199) {
            assertNull(tick(detector, s, PhoneCarriageMode.BIKE_JERSEY_BACK, BSportType.BIKE))
        }
        // 160 s bakery stop (walk in, stand in queue, walk out)
        for (s in 200..359) {
            val mode = if (s in 220..339) PhoneCarriageMode.STATIONARY else PhoneCarriageMode.WALKING_TRANSITION
            assertNull(tick(detector, s, mode, BSportType.BIKE))
        }
        // Resume cycling
        for (s in 360..420) {
            assertNull(tick(detector, s, PhoneCarriageMode.BIKE_JERSEY_BACK, BSportType.BIKE))
        }
        assertEquals(BSportType.BIKE, detector.currentEstablishedSport)
        assertNull(detector.pendingExitAnchor)
    }

    @Test
    fun shortOppositeSportBlip_under15s_rejectedAndDoesNotTrigger() {
        val detector = MultisportTransitionDetector()
        for (s in 0..20) assertNull(tick(detector, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE))
        for (s in 21..30) assertNull(tick(detector, s, PhoneCarriageMode.WALKING_TRANSITION, BSportType.BIKE))
        // 12 s spurious RUN blip
        for (s in 31..42) assertNull(tick(detector, s, PhoneCarriageMode.RUN_HANDHELD, BSportType.RUN))
        // Drops back to WALKING_TRANSITION -> clears candidate
        for (s in 43..48) assertNull(tick(detector, s, PhoneCarriageMode.WALKING_TRANSITION, BSportType.BIKE))
        // Back to BIKE -> clears transition completely
        for (s in 49..60) assertNull(tick(detector, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE))
        assertEquals(BSportType.BIKE, detector.currentEstablishedSport)
        assertNull(detector.pendingEnterAnchor)
    }

    @Test
    fun directSwitchUnder10s_emitsEventWithShortTransitionWallDuration() {
        val detector = MultisportTransitionDetector()
        for (s in 0..19) assertNull(tick(detector, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE))
        for (s in 20..24) assertNull(tick(detector, s, PhoneCarriageMode.WALKING_TRANSITION, BSportType.BIKE))
        var confirmed: MultisportTransitionEvent? = null
        for (s in 25..40) {
            tick(detector, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN)?.let { confirmed = it }
        }
        assertNotNull(confirmed)
        assertEquals(5_000L, confirmed!!.transitionWallClockDurationMs)
    }

    @Test
    fun pausedAndAutoPaused_freezeConfirmationWindowAndNeverConfirmWhilePaused() {
        val detector = MultisportTransitionDetector()
        for (s in 0..19) assertNull(tick(detector, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE))
        for (s in 20..39) assertNull(tick(detector, s, PhoneCarriageMode.WALKING_TRANSITION, BSportType.BIKE))

        // 8 s active RUN (40..48 -> 8 s accumulated)
        for (s in 40..48) {
            assertNull(tick(detector, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, isPaused = false))
        }
        // 20 s PAUSED / AUTO_PAUSED (49..68) -> must NEVER confirm during pause!
        for (s in 49..68) {
            assertNull(
                "Must never confirm while paused/auto-paused (t=$s)",
                tick(detector, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, activeS = 48, isPaused = true)
            )
        }
        // Resume unpaused RUN at t=69: needs 7 more active seconds (69..76 -> confirms at 75)
        var confirmedSec = -1
        for (s in 69..78) {
            val ev = tick(detector, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, activeS = 48 + (s - 68), isPaused = false)
            if (ev != null) {
                confirmedSec = s
                break
            }
        }
        assertEquals(75, confirmedSec)
    }

    @Test
    fun tier1UserLockAndNotTracking_blockAllTransitions() {
        val detector = MultisportTransitionDetector()
        // Not tracking -> never establishes or emits
        for (s in 0..40) {
            assertNull(tick(detector, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE, isTracking = false))
        }
        assertEquals(BSportType.UNKNOWN, detector.currentEstablishedSport)

        // Tracking establishes BIKE, then user locks sport -> blocks RUN confirmation
        for (s in 41..55) assertNull(tick(detector, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE, isTracking = true))
        assertEquals(BSportType.BIKE, detector.currentEstablishedSport)
        for (s in 56..90) {
            assertNull(tick(detector, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, isTracking = true, isUserLocked = true))
        }
        assertEquals(BSportType.BIKE, detector.currentEstablishedSport)
    }

    @Test
    fun parkedSilentBikeSensorWith50HzAccelFootstrikes_confirmsRunDespiteCyclingPrior() {
        val detector = MultisportTransitionDetector()
        val activeBikeHw = MultisportHardwareSignals(hasActiveBikeHardware = true, hardwareSportType = BSportType.BIKE)
        val parkedBikeHw = MultisportHardwareSignals(hasActiveBikeHardware = false, hardwareSportType = BSportType.BIKE)

        // Active cycling with bike power meter
        for (s in 0..19) {
            assertNull(tick(detector, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, hw = activeBikeHw))
        }
        // T2 dismount: bike sensor still BLE-connected but silent (0 W / 0 RPM)
        for (s in 20..39) {
            assertNull(tick(detector, s, PhoneCarriageMode.WALKING_TRANSITION, BSportType.BIKE, hw = parkedBikeHw))
        }
        // Running starts: TYPE_STEP_DETECTOR absent and cyclingPrior biases PhoneCarriageClassifier
        // to WALKING_TRANSITION / BIKE_FRONT_POCKET, while 50-Hz accel footstrikes confirm RUN!
        var confirmed: MultisportTransitionEvent? = null
        for (s in 40..55) {
            val biasedMode = if (s % 2 == 0) PhoneCarriageMode.WALKING_TRANSITION else PhoneCarriageMode.BIKE_FRONT_POCKET
            tick(detector, s, biasedMode, kinematicSport = BSportType.RUN, hw = parkedBikeHw)?.let { confirmed = it }
        }
        assertNotNull("50-Hz accel footstrike RUN must overcome cyclingPrior-biased carriage mode when bike sensor is parked", confirmed)
        val ev = confirmed!!
        assertEquals(BSportType.BIKE, ev.fromSport)
        assertEquals(BSportType.RUN, ev.toSport)
        assertEquals(20, ev.exitAnchor.activeTimeS)
        assertEquals(40, ev.enterAnchor.activeTimeS)
    }

    @Test
    fun phoneOnlyDuathlon_runToT1ToBikeWithKinematicRunCarryover_preservesT1AndBikeMountAnchor() {
        val detector = MultisportTransitionDetector()
        val phoneOnlyHw = MultisportHardwareSignals(
            hasActiveBikeHardware = false,
            hasActiveRunHardware = false,
            hardwareSportType = BSportType.UNKNOWN
        )

        // Leg 1: Phone-only Run (0..899 s)
        for (s in 0..899) {
            assertNull(tick(detector, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, hw = phoneOnlyHw, distM = s * 3.5))
        }
        assertEquals(BSportType.RUN, detector.currentEstablishedSport)

        // T1 transition (900..939 s): KinematicSportTypeDiscriminator evidenceScore stays frozen at RUN
        for (s in 900..939) {
            val mode = if (s % 4 == 0) PhoneCarriageMode.STATIONARY else PhoneCarriageMode.WALKING_TRANSITION
            val dist = 900.0 * 3.5 + (s - 900) * 1.0
            assertNull(tick(detector, s, mode, BSportType.RUN, hw = phoneOnlyHw, distM = dist))
        }

        // Leg 2: Phone-only Bike starts at t=940 s, while KinematicSportTypeDiscriminator carries over
        // kinematicSport == RUN for the first 12 s (940..951 s) before flipping to BIKE at t=952 s!
        var confirmed: MultisportTransitionEvent? = null
        for (s in 940..960) {
            val laggedKinematicSport = if (s < 952) BSportType.RUN else BSportType.BIKE
            val dist = 900.0 * 3.5 + 40.0 + (s - 940) * 8.0
            tick(detector, s, PhoneCarriageMode.BIKE_FRONT_POCKET, laggedKinematicSport, hw = phoneOnlyHw, distM = dist)?.let {
                confirmed = it
            }
        }

        assertNotNull("Phone-only duathlon RUN->T1->BIKE must confirm at t=955 s despite 12 s kinematic RUN carryover", confirmed)
        val ev = confirmed!!
        assertEquals(BSportType.RUN, ev.fromSport)
        assertEquals(BSportType.BIKE, ev.toSport)
        assertEquals("T1 exitAnchor must NOT be erased by lagged kinematic RUN", 900, ev.exitAnchor.activeTimeS)
        assertEquals("Bike mount anchor must NOT shift late", 940, ev.enterAnchor.activeTimeS)
        assertEquals(40_000L, ev.transitionWallClockDurationMs)
        assertEquals(955, ev.confirmAnchor.activeTimeS)
        assertEquals(BSportType.BIKE, detector.currentEstablishedSport)
    }

    @Test
    fun reset_clearsEstablishedSportPendingAnchorsAndHistory() {
        val detector = MultisportTransitionDetector()
        for (s in 0..10) tick(detector, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE)
        tick(detector, 11, PhoneCarriageMode.WALKING_TRANSITION, BSportType.BIKE)
        assertEquals(BSportType.BIKE, detector.currentEstablishedSport)
        assertNotNull(detector.pendingExitAnchor)

        detector.reset()
        assertEquals(BSportType.UNKNOWN, detector.currentEstablishedSport)
        assertNull(detector.pendingExitAnchor)
        assertNull(detector.pendingEnterAnchor)
        assertEquals(0, detector.anchorHistorySize())
    }
}
