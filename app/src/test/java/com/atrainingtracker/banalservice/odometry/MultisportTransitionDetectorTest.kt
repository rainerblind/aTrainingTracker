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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [MultisportTransitionDetector] (`ATT-8917`, `REQ-FIL-031.1`, `TST-FIL-031.1`).
 */
// Linked test specs: TST-FIL-031, TST-FIL-044, TST-FIL-047
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
        isUserLocked: Boolean = false,
        hasActiveRunFootstrike: Boolean? = null
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
            isUserSportLocked = isUserLocked,
            hasActiveRunFootstrike = hasActiveRunFootstrike
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

    // REQ-FIL-044.2, REQ-FIL-044.3 / TST-FIL-044.3: Bounded trailing active-unpaused sliding-window RUN guardrails:
    // (a) Window 1 start low-speed (0.46 m/s, 10.9 m) rejection, (b) Window 2 zero-stride 47.8 m roll rejection,
    // (c) Phase 1 (1 s < 2 s) & Phase 2 (5 s < 9 s) corroboration-dilution immunity after 60 s uncorroborated roll,
    // (d) Phase 1 & Phase 2 post-low-speed recovery (enterAnchor at qualifying start, exitAnchor intact) & pause immunity,
    // (e) NaN/negative distance & zero-duration safety, reset() cleanup, and @JvmStatic @JvmOverloads 5/6-arg bridge interop.
    @Test
    fun testRunEstablishmentAndBikeToRunGuardrails_blockLowSpeedAndZeroStrideRollsWhilePreservingSlowT2_TST_FIL_044_3() {
        // (a) Window 1 (2026-11-21_081228 rows 1..25): 24 s at 0.46 m/s (10.9 m), 0 footstrikes -> NEVER locks RUN,
        //     then genuine cycling establishes BIKE with 0 phantom Laufen->Rad splits.
        val startDet = MultisportTransitionDetector()
        for (s in 0..24) {
            val ev = tick(
                startDet, s, PhoneCarriageMode.RUN_HANDHELD, kinematicSport = BSportType.UNKNOWN,
                distM = s * 0.454, hasActiveRunFootstrike = false
            )
            assertNull("Low-speed start must emit 0 transitions at t=$s", ev)
        }
        assertEquals("0.46 m/s bike pushing must never lock establishedSport = RUN", BSportType.UNKNOWN, startDet.currentEstablishedSport)
        for (s in 25..45) {
            val ev = tick(
                startDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, kinematicSport = BSportType.BIKE,
                distM = 10.9 + (s - 24) * 6.5, hasActiveRunFootstrike = false
            )
            assertNull("Establishing BIKE after low-speed start must emit 0 phantom Laufen->Rad splits", ev)
        }
        assertEquals(BSportType.BIKE, startDet.currentEstablishedSport)

        // (b) Window 2 (2026-11-21_081228 rows 310..340, Metzger -> Bank): established BIKE rolls 47.8 m at
        //     2.0..4.0 m/s for 19 s with 0 footstrikes -> 0 BIKE->RUN transitions (both false and null footstrike arg).
        for (footstrikeArg in listOf(false, null)) {
            val rollDet = MultisportTransitionDetector()
            for (s in 0..30) {
                assertNull(tick(rollDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, distM = s * 6.5, hasActiveRunFootstrike = false))
            }
            assertEquals(BSportType.BIKE, rollDet.currentEstablishedSport)
            for (s in 31..40) {
                assertNull(tick(rollDet, s, PhoneCarriageMode.STATIONARY, BSportType.BIKE, distM = 30 * 6.5, hasActiveRunFootstrike = false))
            }
            var dist = 30 * 6.5
            for (s in 41..60) {
                dist += 2.52 // ~47.8 m over 19 s
                assertNull(
                    "Zero-stride 47.8 m roll (footstrike=$footstrikeArg) must NOT confirm BIKE->RUN at t=$s",
                    tick(rollDet, s, PhoneCarriageMode.RUN_HANDHELD, BSportType.BIKE, distM = dist, hasActiveRunFootstrike = footstrikeArg)
                )
            }
            assertEquals(BSportType.BIKE, rollDet.currentEstablishedSport)
        }

        // (c1) Phase 1 corroboration-dilution immunity: 60 s uncorroborated roll + brief 1 s (< 2 s) noise burst -> UNKNOWN
        val p1DiluteDet = MultisportTransitionDetector()
        for (s in 0..60) {
            tick(p1DiluteDet, s, PhoneCarriageMode.RUN_HANDHELD, BSportType.UNKNOWN, distM = s * 3.0, hasActiveRunFootstrike = false)
        }
        assertEquals(BSportType.UNKNOWN, p1DiluteDet.currentEstablishedSport)
        // Brief 1 s noise burst (t = 61) -> trailing 3 s window has only 1_000 ms < 2_000 ms -> stays UNKNOWN
        tick(p1DiluteDet, 61, PhoneCarriageMode.RUN_HANDHELD, BSportType.RUN, distM = 61 * 3.0, hasActiveRunFootstrike = true)
        assertEquals("Phase 1 60 s uncorroborated + 1 s burst must NOT lock RUN", BSportType.UNKNOWN, p1DiluteDet.currentEstablishedSport)

        // (c2) Phase 2 corroboration-dilution immunity: 60 s uncorroborated roll + brief 5 s (< 9 s) noise burst -> 0 transitions
        val p2DiluteDet = MultisportTransitionDetector()
        for (s in 0..20) tick(p2DiluteDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, distM = s * 6.0, hasActiveRunFootstrike = false)
        assertEquals(BSportType.BIKE, p2DiluteDet.currentEstablishedSport)
        var dDilute = 20 * 6.0
        for (s in 21..85) {
            dDilute += 3.0
            val burst = s in 81..85 // 5 s burst (< 9 s threshold of 15 s trailing window)
            val ev = tick(
                p2DiluteDet, s, PhoneCarriageMode.RUN_HANDHELD,
                kinematicSport = if (burst) BSportType.RUN else BSportType.BIKE,
                distM = dDilute,
                hasActiveRunFootstrike = burst
            )
            assertNull("Phase 2 60 s uncorroborated + 5 s noise burst must NOT emit BIKE->RUN at t=$s", ev)
        }
        assertEquals(BSportType.BIKE, p2DiluteDet.currentEstablishedSport)

        // (d1) Phase 2 post-low-speed recovery: 15 s slow RUN ticks (0.5 m/s) followed by 15 s slow T2 jog (2.0 m/s,
        //      corroborated via hasActiveRunFootstrike = true even while kinematicSport = BIKE) -> confirms cleanly at +15 s
        //      with enterAnchor at qualifying jog start and exitAnchor preserved intact!
        val recoveryDet = MultisportTransitionDetector()
        for (s in 0..99) {
            assertNull(tick(recoveryDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, distM = s * 7.0, hasActiveRunFootstrike = false))
        }
        assertEquals(BSportType.BIKE, recoveryDet.currentEstablishedSport)
        // Dismount into T2 at t = 100
        assertNull(tick(recoveryDet, 100, PhoneCarriageMode.WALKING_TRANSITION, BSportType.BIKE, distM = 700.0, hasActiveRunFootstrike = false))
        var dRec = 700.0
        // 15 s slow RUN ticks (101..115 at 0.5 m/s) -> must NOT confirm
        for (s in 101..115) {
            dRec += 0.5
            assertNull(
                "Slow 0.5 m/s RUN ticks must not confirm at t=$s",
                tick(recoveryDet, s, PhoneCarriageMode.RUN_HANDHELD, BSportType.BIKE, distM = dRec, hasActiveRunFootstrike = true)
            )
        }
        // Genuine 2.0 m/s T2 slow jog begins at t = 116 -> must recover cleanly within <= 15 s of genuine jogging
        // (no 40-60 s cumulative drag), preserving exitAnchor = 100 and anchoring enterAnchor to the qualifying 15 s window head
        var recoveredEvent: MultisportTransitionEvent? = null
        for (s in 116..132) {
            if (s > 116) dRec += 2.0
            val ev = tick(recoveryDet, s, PhoneCarriageMode.RUN_HANDHELD, BSportType.BIKE, distM = dRec, hasActiveRunFootstrike = true)
            if (ev != null) {
                recoveredEvent = ev
                break
            }
        }
        assertNotNull("Phase 2 must recover from 15 s slow start within <= 15 s of genuine 2.0 m/s jogging", recoveredEvent)
        val recEv = recoveredEvent!!
        assertEquals("exitAnchor must stay preserved at T2 dismount (t=100)", 100, recEv.exitAnchor.activeTimeS)
        assertEquals(
            "enterAnchor must anchor to the qualifying 15 s window head (confirm - 15)",
            recEv.confirmAnchor.activeTimeS - 15,
            recEv.enterAnchor.activeTimeS
        )
        assertTrue(
            "enterAnchor must advance past stale slow-start t=101 (was ${recEv.enterAnchor.activeTimeS})",
            recEv.enterAnchor.activeTimeS >= 113
        )
        assertTrue(
            "Must confirm within <= 15 s of genuine 2.0 m/s jogging (confirm=${recEv.confirmAnchor.activeTimeS})",
            recEv.confirmAnchor.activeTimeS <= 131
        )
        assertTrue(
            "Qualifying window displacement must meet >= 24.0 m floor",
            (recEv.confirmAnchor.totalDistanceM - recEv.enterAnchor.totalDistanceM) >= 24.0
        )
        assertEquals(BSportType.RUN, recoveryDet.currentEstablishedSport)

        // (d2) Phase 1 post-low-speed recovery: 10 s at 0.5 m/s -> 3 s at 2.0 m/s corroborated -> establishes RUN in 3 s
        val p1RecDet = MultisportTransitionDetector()
        var dP1 = 0.0
        for (s in 0..10) {
            dP1 += 0.5
            tick(p1RecDet, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = dP1, hasActiveRunFootstrike = true)
        }
        assertEquals(BSportType.UNKNOWN, p1RecDet.currentEstablishedSport)
        for (s in 11..14) {
            if (s > 11) dP1 += 2.0
            tick(p1RecDet, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = dP1, hasActiveRunFootstrike = true)
        }
        assertEquals("Phase 1 must recover cleanly within 3 s of 2.0 m/s running", BSportType.RUN, p1RecDet.currentEstablishedSport)

        // (d3) Pause-immune active average speed: 2.0 m/s jog paused for 20 s mid-confirmation (flat dist during pause) confirms at 15 s active
        val pauseDet = MultisportTransitionDetector()
        for (s in 0..19) tick(pauseDet, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE, distM = s * 6.0)
        tick(pauseDet, 20, PhoneCarriageMode.WALKING_TRANSITION, BSportType.BIKE, distM = 120.0)
        var dPause = 120.0
        for (s in 21..29) { // 8 s active RUN at 2.0 m/s (+16 m)
            if (s > 21) dPause += 2.0
            assertNull(tick(pauseDet, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, activeS = s, distM = dPause, isPaused = false, hasActiveRunFootstrike = true))
        }
        for (s in 30..49) { // 20 s auto-pause with flat distance
            assertNull(tick(pauseDet, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, activeS = 29, distM = dPause, isPaused = true, hasActiveRunFootstrike = true))
        }
        var pauseConfirm: MultisportTransitionEvent? = null
        for (s in 50..58) { // 7 more active s at 2.0 m/s (+14 m -> total 30 m / 15 s active = 2.0 m/s)
            dPause += 2.0
            val ev = tick(pauseDet, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, activeS = 29 + (s - 49), distM = dPause, isPaused = false, hasActiveRunFootstrike = true)
            if (ev != null) { pauseConfirm = ev; break }
        }
        assertNotNull("Pause-immune 2.0 m/s slow jogger must confirm after 15 s active unpaused", pauseConfirm)

        // (e) NaN/negative distance safety, zero-duration spanMs=0L safety, reset() cleanup & @JvmStatic @JvmOverloads bridge interop
        val safeDet = MultisportTransitionDetector()
        tick(safeDet, 0, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = Double.NaN, hasActiveRunFootstrike = true)
        tick(safeDet, 1, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = -5.0, hasActiveRunFootstrike = true)
        tick(safeDet, 2, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = Double.POSITIVE_INFINITY, hasActiveRunFootstrike = true)
        tick(safeDet, 3, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = 1.0, hasActiveRunFootstrike = true)
        assertEquals("Non-finite/negative distance deltas must not establish RUN", BSportType.UNKNOWN, safeDet.currentEstablishedSport)
        safeDet.reset()
        assertEquals(0, safeDet.anchorHistorySize())

        val zeroSpanDet = MultisportTransitionDetector(confirmationWindowMs = 0L, establishWindowMs = 0L)
        tick(zeroSpanDet, 0, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = 0.0, hasActiveRunFootstrike = true)
        assertEquals(BSportType.RUN, zeroSpanDet.currentEstablishedSport)

        val bridgeMethods = MultisportTransitionBridge::class.java.declaredMethods
            .filter { it.name == "evaluateAndDispatch" && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.parameterCount }
            .toSet()
        assertEquals(
            "MultisportTransitionBridge.evaluateAndDispatch must preserve @JvmStatic @JvmOverloads 5-arg & 6-arg signatures",
            setOf(5, 6),
            bridgeMethods
        )
    }

    // REQ-FIL-047.3 / TST-FIL-047.3: Symmetric sliding-window BIKE Phase 1 & Phase 2 guardrails with bounded SportSpeedPriors
    @Test
    fun testBikeEstablishmentAndRunToBikeGuardrails_blockWalkingSpeedsAndRespectPriorsAndHardware_tstFil047_3() {
        // (a) Phase 1 walking-speed BIKE_FRONT_POCKET (0.53–1.80 m/s, mirroring Essen gehen rows 3..10 & brisk walk)
        //     must NEVER lock establishedSport = BIKE even after 30 s!
        val walkDet = MultisportTransitionDetector()
        var dist = 0.0
        for (s in 0..30) {
            if (s > 0) dist += if (s <= 10) 0.70 else 1.80
            assertNull(tick(walkDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, distM = dist))
        }
        assertEquals("Walking-speed BIKE_FRONT_POCKET must never lock BIKE", BSportType.UNKNOWN, walkDet.currentEstablishedSport)

        // (b) Custom SportSpeedPriors (Einkaufen minBike = 2.78 m/s): 2.5 m/s rejected in Phase 1, 3.0 m/s establishes BIKE
        val customPriors = SportSpeedPriors(maxWalkSpeedMps = 2.0, minBikeSpeedMps = 2.78, maxRunSpeedMps = 4.0)
        val priorDet = MultisportTransitionDetector(sportSpeedPriors = customPriors)
        var dPrior = 0.0
        for (s in 0..10) {
            if (s > 0) dPrior += 2.5
            assertNull(tick(priorDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, distM = dPrior))
        }
        assertEquals("2.5 m/s < configured minBike 2.78 m/s must not establish BIKE", BSportType.UNKNOWN, priorDet.currentEstablishedSport)
        for (s in 11..15) {
            dPrior += 3.0
            assertNull(tick(priorDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, distM = dPrior))
        }
        assertEquals("3.0 m/s >= configured minBike 2.78 m/s must establish BIKE", BSportType.BIKE, priorDet.currentEstablishedSport)

        // (c) Phase 2 RUN -> BIKE rejects walking speeds (1.5 m/s & 2.4 m/s < 2.78 m/s), then recovers cleanly at 4.5 m/s
        //     anchoring enterAnchor to qualifying 15 s window head while preserving T1 exitAnchor!
        val runToBikeDet = MultisportTransitionDetector(sportSpeedPriors = customPriors)
        var dRb = 0.0
        for (s in 0..29) {
            if (s > 0) dRb += 3.2
            assertNull(tick(runToBikeDet, s, PhoneCarriageMode.RUN_WAIST_HIP, BSportType.RUN, distM = dRb, hasActiveRunFootstrike = true))
        }
        assertEquals(BSportType.RUN, runToBikeDet.currentEstablishedSport)
        // T1 dismount at t = 30
        assertNull(tick(runToBikeDet, 30, PhoneCarriageMode.WALKING_TRANSITION, BSportType.RUN, distM = dRb))
        // 20 s walking-speed BIKE_FRONT_POCKET at 1.5 m/s -> 0 transitions
        for (s in 31..50) {
            dRb += 1.5
            assertNull("1.5 m/s walking must not confirm RUN->BIKE at t=$s", tick(runToBikeDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, distM = dRb))
        }
        assertEquals(BSportType.RUN, runToBikeDet.currentEstablishedSport)
        // Genuine 4.5 m/s triathlon bike leg starts at t = 51 -> confirms cleanly within <= 15 s with exitAnchor=30
        var confirmedBike: MultisportTransitionEvent? = null
        for (s in 51..68) {
            dRb += 4.5
            val ev = tick(runToBikeDet, s, PhoneCarriageMode.BIKE_FRONT_POCKET, BSportType.BIKE, distM = dRb)
            if (ev != null) {
                confirmedBike = ev
                break
            }
        }
        assertNotNull("Genuine 4.5 m/s RUN->T1->BIKE must confirm cleanly after slow start", confirmedBike)
        assertEquals(BSportType.RUN, confirmedBike!!.fromSport)
        assertEquals(BSportType.BIKE, confirmedBike.toSport)
        assertEquals("T1 exitAnchor must stay preserved at t=30", 30, confirmedBike.exitAnchor.activeTimeS)
        assertEquals(confirmedBike.confirmAnchor.activeTimeS - 15, confirmedBike.enterAnchor.activeTimeS)
        assertTrue(
            "enterAnchor must advance past slow-start t=31 (was ${confirmedBike.enterAnchor.activeTimeS})",
            confirmedBike.enterAnchor.activeTimeS >= 42
        )
        assertEquals(BSportType.BIKE, runToBikeDet.currentEstablishedSport)

        // (d) Active bike hardware bypasses low-speed floor (e.g., 1.8 m/s steep MTB climb with wheel/power sensor)
        val hwDet = MultisportTransitionDetector(sportSpeedPriors = customPriors)
        val activeBikeHw = MultisportHardwareSignals(hasActiveBikeHardware = true, hardwareSportType = BSportType.BIKE)
        for (s in 0..4) {
            assertNull(tick(hwDet, s, PhoneCarriageMode.BIKE_HANDLEBAR, BSportType.BIKE, hw = activeBikeHw, distM = s * 1.5))
        }
        assertEquals("Active bike hardware must bypass low-speed floor", BSportType.BIKE, hwDet.currentEstablishedSport)

        hwDet.reset()
        assertEquals("reset() must retain loaded SportSpeedPriors", customPriors, hwDet.currentSportSpeedPriors)
    }
}
