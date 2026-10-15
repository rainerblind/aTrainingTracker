/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.tracker.autopause

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying AutoPauseManager state transitions, hysteresis, debounce,
 * stale packet TTL, sanitization, and manual mutex precedence (TST-TRK-010 / REQ-TRK-018).
 */
class AutoPauseManagerTest {

    private lateinit var mockAudioNotifier: TestAudioNotifier
    private lateinit var autoPauseManager: AutoPauseManager

    class TestAudioNotifier : AutoPauseAudioNotifier {
        val playedTones = mutableListOf<SoundCue>()
        override fun playTone(soundCue: SoundCue) {
            playedTones.add(soundCue)
        }
    }

    @Before
    fun setUp() {
        mockAudioNotifier = TestAudioNotifier()
        autoPauseManager = AutoPauseManager(mockAudioNotifier)
    }

    @Test
    fun `TST-TRK-010_1 sport specific default activation contract`() {
        val defaultConfig = AutoPauseConfig()
        assertTrue("Bike must be enabled by default", defaultConfig.isEnabledForSport(BSportType.BIKE))
        assertFalse("Run must be disabled by default", defaultConfig.isEnabledForSport(BSportType.RUN))
        assertFalse("Unknown must be disabled by default", defaultConfig.isEnabledForSport(BSportType.UNKNOWN))
        assertFalse("Conflict must be disabled by default", defaultConfig.isEnabledForSport(BSportType.CONFLICT))
    }

    @Test
    fun `TST-TRK-010_2 debounce counter and stationary transition`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            speedThresholdKmh = 1.5f,
            debounceSec = 2
        )
        val now = 100000L

        // Tick 1: Speed drops to 0.0 m/s -> should be in PAUSE_DEBOUNCE_PENDING, no pause triggered
        val transition1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.0,
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )

        assertEquals(AutoPauseState.TRACKING, transition1.previousState)
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, transition1.newState)
        assertFalse(transition1.triggerPause)
        assertEquals(1, autoPauseManager.debounceCounter)
        assertEquals(0, mockAudioNotifier.playedTones.size)

        // Tick 2: Speed remains 0.0 m/s for second tick -> transitions to AUTO_PAUSED, triggers pause
        val transition2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.0,
            packetTimestampMs = now + 1000L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )

        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, transition2.previousState)
        assertEquals(AutoPauseState.AUTO_PAUSED, transition2.newState)
        assertTrue(transition2.triggerPause)
        assertEquals(SoundCue.AUTO_PAUSE, transition2.soundCue)
        assertEquals(1, mockAudioNotifier.playedTones.size)
        assertEquals(SoundCue.AUTO_PAUSE, mockAudioNotifier.playedTones[0])
        assertTrue(autoPauseManager.isAutoPaused())
    }

    @Test
    fun `TST-TRK-010_2 single tick debounce immediately pauses`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            speedThresholdKmh = 1.5f,
            debounceSec = 1
        )
        val now = 100000L

        val transition = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.0,
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )

        assertEquals(AutoPauseState.AUTO_PAUSED, transition.newState)
        assertTrue(transition.triggerPause)
        assertEquals(SoundCue.AUTO_PAUSE, transition.soundCue)
    }

    @Test
    fun `TST-TRK-010_3 anti-chatter hysteresis deadband and hard ceiling`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            speedThresholdKmh = 1.5f, // pause threshold = 1.5 km/h -> resume threshold = 2.0 km/h
            debounceSec = 1
        )
        val now = 100000L

        // Trigger pause
        autoPauseManager.evaluateTick(0.0, now, now, BSportType.BIKE, config, false)
        assertTrue(autoPauseManager.isAutoPaused())

        // Speed jitters within deadband: 1.6 km/h = 0.444 m/s -> strictly stays AUTO_PAUSED
        val jitter1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 1.6 / 3.6,
            packetTimestampMs = now + 1000L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, jitter1.newState)
        assertFalse(jitter1.triggerResume)

        // Speed jitters at 1.9 km/h = 0.528 m/s -> strictly stays AUTO_PAUSED
        val jitter2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 1.9 / 3.6,
            packetTimestampMs = now + 2000L,
            currentTimestampMs = now + 2000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, jitter2.newState)
        assertFalse(jitter2.triggerResume)

        // Speed reaches 2.0 km/h = 0.556 m/s (pause threshold + 0.5 km/h) -> triggers resume to TRACKING
        val resume = autoPauseManager.evaluateTick(
            currentSpeedMps = 2.0 / 3.6,
            packetTimestampMs = now + 3000L,
            currentTimestampMs = now + 3000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.TRACKING, resume.newState)
        assertTrue(resume.triggerResume)
        assertEquals(SoundCue.AUTO_RESUME, resume.soundCue)
        assertFalse(autoPauseManager.isAutoPaused())
    }

    @Test
    fun `TST-TRK-010_3 ceiling calculation enforces 6 kmh hard limit`() {
        assertEquals(2.0f, AutoPauseManager.calculateResumeSpeedThresholdKmh(1.5f), 0.001f)
        assertEquals(3.5f, AutoPauseManager.calculateResumeSpeedThresholdKmh(3.0f), 0.001f)
        assertEquals(5.5f, AutoPauseManager.calculateResumeSpeedThresholdKmh(5.0f), 0.001f)
        // Hard ceiling clamp at 6.0 km/h
        assertEquals(6.0f, AutoPauseManager.calculateResumeSpeedThresholdKmh(5.8f), 0.001f)
        assertEquals(6.0f, AutoPauseManager.calculateResumeSpeedThresholdKmh(8.0f), 0.001f)
    }

    @Test
    fun `TST-TRK-010_6 stale packet rejection triggers SENSOR_LOST and recovers on 2 ticks`() {
        val config = AutoPauseConfig(bikeEnabled = true, debounceSec = 2)
        val now = 100000L

        // Active tracking with valid packet at now - 6000ms (> 5000ms TTL)
        val transition = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.0,
            packetTimestampMs = now - 6000L,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )

        assertEquals(AutoPauseState.SENSOR_LOST, transition.newState)
        assertFalse("Must NOT assume standstill on stale packet", transition.triggerPause)
        assertEquals("SENSOR_LOST_STALE_TTL_EXCEEDED", transition.auditLogEvent)

        // Missing packet (null speed or 0 timestamp) keeps SENSOR_LOST
        val keepLost = autoPauseManager.evaluateTick(
            currentSpeedMps = null,
            packetTimestampMs = 0L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.SENSOR_LOST, keepLost.newState)

        // Recovery: 1st fresh packet
        val rec1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 5.0,
            packetTimestampMs = now + 2000L,
            currentTimestampMs = now + 2000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.SENSOR_LOST, rec1.newState)
        assertEquals(1, autoPauseManager.sensorLostRecoveryTicks)

        // Recovery: 2nd fresh packet -> transitions back to TRACKING
        val rec2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 5.0,
            packetTimestampMs = now + 3000L,
            currentTimestampMs = now + 3000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.TRACKING, rec2.newState)
        assertEquals("SENSOR_LOST_RECOVERED", rec2.auditLogEvent)
        assertEquals(0, autoPauseManager.sensorLostRecoveryTicks)
    }

    @Test
    fun `TST-TRK-010_7 mutex precedence blocks auto-resume when manualPauseLocked`() {
        val config = AutoPauseConfig(bikeEnabled = true)
        val now = 100000L

        // Set to manual paused
        val trans = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.0,
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = true
        )

        assertEquals(AutoPauseState.MANUAL_PAUSED, trans.newState)

        // Speed increases to 25 km/h (6.94 m/s) -> auto-resume MUST remain blocked
        val fastTick = autoPauseManager.evaluateTick(
            currentSpeedMps = 25.0 / 3.6,
            packetTimestampMs = now + 1000L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = true
        )

        assertEquals(AutoPauseState.MANUAL_PAUSED, fastTick.newState)
        assertFalse(fastTick.triggerResume)

        // Once manualPauseLocked is false (e.g. user resumes), transitions to TRACKING
        val userResumedTick = autoPauseManager.evaluateTick(
            currentSpeedMps = 25.0 / 3.6,
            packetTimestampMs = now + 2000L,
            currentTimestampMs = now + 2000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )

        assertEquals(AutoPauseState.TRACKING, userResumedTick.newState)
    }

    @Test
    fun `TST-TRK-010_10 debounce resets on speed recovery before expiration`() {
        val config = AutoPauseConfig(bikeEnabled = true, speedThresholdKmh = 1.5f, debounceSec = 3)
        val now = 100000L

        // Tick 1: stationary -> debounce 1
        autoPauseManager.evaluateTick(0.0, now, now, BSportType.BIKE, config, false)
        assertEquals(1, autoPauseManager.debounceCounter)
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, autoPauseManager.currentState)

        // Tick 2: acceleration back to 20 km/h -> debounce resets to 0 and returns to TRACKING
        val acc = autoPauseManager.evaluateTick(20.0 / 3.6, now + 1000L, now + 1000L, BSportType.BIKE, config, false)
        assertEquals(0, autoPauseManager.debounceCounter)
        assertEquals(AutoPauseState.TRACKING, acc.newState)
    }

    @Test
    fun `TST-TRK-010_11 non-finite speed values are safely sanitized`() {
        assertFalse(AutoPauseManager.isSpeedFiniteAndValid(null))
        assertFalse(AutoPauseManager.isSpeedFiniteAndValid(Double.NaN))
        assertFalse(AutoPauseManager.isSpeedFiniteAndValid(Double.POSITIVE_INFINITY))
        assertFalse(AutoPauseManager.isSpeedFiniteAndValid(Double.NEGATIVE_INFINITY))
        assertFalse(AutoPauseManager.isSpeedFiniteAndValid(-0.5))
        assertTrue(AutoPauseManager.isSpeedFiniteAndValid(0.0))
        assertTrue(AutoPauseManager.isSpeedFiniteAndValid(5.5))

        val config = AutoPauseConfig(bikeEnabled = true)
        val now = 100000L

        // Evaluating NaN does not crash and transitions to SENSOR_LOST rather than corrupting debounce
        val nanTick = autoPauseManager.evaluateTick(
            currentSpeedMps = Double.NaN,
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.SENSOR_LOST, nanTick.newState)
        assertEquals(0, autoPauseManager.debounceCounter)
    }

    @Test
    fun `TST-TRK-010_12 resume speed threshold user unit calculation and ceiling`() {
        // Metric: offset 0.5 km/h, ceiling 6.0 km/h
        assertEquals(1.5, AutoPauseManager.calculateResumeSpeedThresholdUserUnit(1.0, true), 0.001)
        assertEquals(3.5, AutoPauseManager.calculateResumeSpeedThresholdUserUnit(3.0, true), 0.001)
        assertEquals(5.5, AutoPauseManager.calculateResumeSpeedThresholdUserUnit(5.0, true), 0.001)
        assertEquals(6.0, AutoPauseManager.calculateResumeSpeedThresholdUserUnit(5.8, true), 0.001)

        // Imperial: offset 0.5 mph, ceiling 6.0 mph
        assertEquals(1.5, AutoPauseManager.calculateResumeSpeedThresholdUserUnit(1.0, false), 0.001)
        assertEquals(2.5, AutoPauseManager.calculateResumeSpeedThresholdUserUnit(2.0, false), 0.001)
        assertEquals(5.5, AutoPauseManager.calculateResumeSpeedThresholdUserUnit(5.0, false), 0.001)
        assertEquals(6.0, AutoPauseManager.calculateResumeSpeedThresholdUserUnit(5.8, false), 0.001)
    }

    @Test
    fun `TST-TRK-010_13 sport specific decoupling for run and other`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            runEnabled = true,
            otherEnabled = true,
            bikeSpeedThreshold = 3,
            bikeDebounceSec = 2,
            runSpeedThreshold = 2,
            runDebounceSec = 5,
            otherSpeedThreshold = 1,
            otherDebounceSec = 10,
            isMetric = true
        )
        val now = 100000L

        // Run profile: 1.5 km/h is below 2.0 km/h threshold
        val runSpeedMps = 1.5 / 3.6

        // Ticks 1 to 4: in PAUSE_DEBOUNCE_PENDING
        for (i in 1..4) {
            val trans = autoPauseManager.evaluateTick(
                currentSpeedMps = runSpeedMps,
                packetTimestampMs = now + (i * 1000L),
                currentTimestampMs = now + (i * 1000L),
                sportType = BSportType.RUN,
                config = config,
                manualPauseLocked = false
            )
            assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, trans.newState)
            assertFalse(trans.triggerPause)
            assertEquals(i, autoPauseManager.debounceCounter)
        }

        // Tick 5: reaches debounceSec=5 -> transitions to AUTO_PAUSED
        val trans5 = autoPauseManager.evaluateTick(
            currentSpeedMps = runSpeedMps,
            packetTimestampMs = now + 5000L,
            currentTimestampMs = now + 5000L,
            sportType = BSportType.RUN,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, trans5.newState)
        assertTrue(trans5.triggerPause)

        // Hysteresis deadband: 2.2 km/h is > 2.0 km/h but <= 2.5 km/h resume threshold -> stays AUTO_PAUSED
        val deadbandTick = autoPauseManager.evaluateTick(
            currentSpeedMps = 2.2 / 3.6,
            packetTimestampMs = now + 6000L,
            currentTimestampMs = now + 6000L,
            sportType = BSportType.RUN,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, deadbandTick.newState)
        assertFalse(deadbandTick.triggerResume)

        // Resume: 2.6 km/h is > 2.5 km/h resume threshold -> resumes
        val resumeTick = autoPauseManager.evaluateTick(
            currentSpeedMps = 2.6 / 3.6,
            packetTimestampMs = now + 7000L,
            currentTimestampMs = now + 7000L,
            sportType = BSportType.RUN,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.TRACKING, resumeTick.newState)
        assertTrue(resumeTick.triggerResume)
    }

    @Test
    fun `TST-TRK-010_14 imperial unit conversion evaluateTick`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            bikeSpeedThreshold = 2, // 2 mph = 0.89408 m/s
            bikeDebounceSec = 2,
            isMetric = false
        )
        val now = 100000L

        // Speed 0.8 m/s is < 2 mph (0.894 m/s)
        val tick1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.8,
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, tick1.newState)

        val tick2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.8,
            packetTimestampMs = now + 1000L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, tick2.newState)
        assertTrue(tick2.triggerPause)

        // Speed 1.0 m/s is > 2.0 mph (0.894 m/s) but < 2.5 mph (1.1176 m/s) -> deadband, stays paused
        val tick3 = autoPauseManager.evaluateTick(
            currentSpeedMps = 1.0,
            packetTimestampMs = now + 2000L,
            currentTimestampMs = now + 2000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, tick3.newState)
        assertFalse(tick3.triggerResume)

        // Speed 1.2 m/s is > 2.5 mph (1.1176 m/s) -> triggers resume
        val tick4 = autoPauseManager.evaluateTick(
            currentSpeedMps = 1.2,
            packetTimestampMs = now + 3000L,
            currentTimestampMs = now + 3000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.TRACKING, tick4.newState)
        assertTrue(tick4.triggerResume)
    }
}

