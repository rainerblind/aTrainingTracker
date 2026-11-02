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

import android.media.ToneGenerator
import com.atrainingtracker.banalservice.BSportType
import android.util.Log
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying AutoPauseManager state transitions, hysteresis, debounce,
 * standstill silence handling, ProxySensor timestamp delegation, manual resume synchronization,
 * and reusable ToneGenerator lifecycle (TST-TRK-010 / REQ-TRK-018, TST-TRK-020 / REQ-TRK-028).
 */
class AutoPauseManagerTest {

    private lateinit var mockAudioNotifier: TestAudioNotifier
    private lateinit var autoPauseManager: AutoPauseManager

    class TestAudioNotifier : AutoPauseAudioNotifier {
        val playedTones = mutableListOf<SoundCue>()
        var releaseCount = 0
        override fun playTone(soundCue: SoundCue) {
            playedTones.add(soundCue)
        }
        override fun release() {
            releaseCount++
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        TrainingApplication.setManualPauseLock(false, null)
        TrainingApplication.setAutoPaused(false, null)
        mockAudioNotifier = TestAudioNotifier()
        autoPauseManager = AutoPauseManager(mockAudioNotifier)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
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
            speedThresholdKmh = 1.5f,
            debounceSec = 1,
            bikeResumeDebounceSec = 2
        )
        val now = 100000L

        // Trigger pause
        autoPauseManager.evaluateTick(0.0, now, now, BSportType.BIKE, config, false)
        assertTrue(autoPauseManager.isAutoPaused())

        // Speed below threshold: 1.2 km/h -> strictly stays AUTO_PAUSED, resume counter remains 0
        val subThreshold = autoPauseManager.evaluateTick(
            currentSpeedMps = 1.2 / 3.6,
            packetTimestampMs = now + 1000L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, subThreshold.newState)
        assertFalse(subThreshold.triggerResume)
        assertEquals(0, autoPauseManager.resumeDebounceCounter)

        // Single tick above threshold: 1.6 km/h (>= 1.5 km/h) -> stays AUTO_PAUSED (resumeDebounceCounter = 1 < 2)
        val jitter1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 1.6 / 3.6,
            packetTimestampMs = now + 2000L,
            currentTimestampMs = now + 2000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, jitter1.newState)
        assertFalse(jitter1.triggerResume)
        assertEquals(1, autoPauseManager.resumeDebounceCounter)

        // Speed drops below threshold again: 1.0 km/h -> resets resumeDebounceCounter to 0
        val drop = autoPauseManager.evaluateTick(
            currentSpeedMps = 1.0 / 3.6,
            packetTimestampMs = now + 3000L,
            currentTimestampMs = now + 3000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, drop.newState)
        assertFalse(drop.triggerResume)
        assertEquals(0, autoPauseManager.resumeDebounceCounter)

        // Two consecutive ticks >= 1.5 km/h -> resumes to TRACKING
        autoPauseManager.evaluateTick(1.6 / 3.6, now + 4000L, now + 4000L, BSportType.BIKE, config, false)
        assertEquals(1, autoPauseManager.resumeDebounceCounter)
        val resume = autoPauseManager.evaluateTick(1.6 / 3.6, now + 5000L, now + 5000L, BSportType.BIKE, config, false)
        assertEquals(AutoPauseState.TRACKING, resume.newState)
        assertTrue(resume.triggerResume)
        assertEquals(SoundCue.AUTO_RESUME, resume.soundCue)
        assertEquals(0, autoPauseManager.resumeDebounceCounter)
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
    fun `TST-TRK-010_6 missing speed source or corrupt speed triggers SENSOR_LOST and recovers on 2 ticks`() {
        val config = AutoPauseConfig(bikeEnabled = true, debounceSec = 2)
        val now = 100000L

        // Active tracking without any registered speed source (hasSpeedSource = false, null speed, 0 timestamp)
        val transition = autoPauseManager.evaluateTick(
            currentSpeedMps = null,
            packetTimestampMs = 0L,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = false
        )

        assertEquals(AutoPauseState.SENSOR_LOST, transition.newState)
        assertFalse("Must NOT assume standstill when no speed sensor is registered", transition.triggerPause)
        assertEquals("SENSOR_LOST_STALE_TTL_EXCEEDED", transition.auditLogEvent)

        // Missing speed source continues to keep SENSOR_LOST
        val keepLost = autoPauseManager.evaluateTick(
            currentSpeedMps = null,
            packetTimestampMs = 0L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = false
        )
        assertEquals(AutoPauseState.SENSOR_LOST, keepLost.newState)

        // Recovery: 1st fresh packet from registered speed source
        val rec1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 5.0,
            packetTimestampMs = now + 2000L,
            currentTimestampMs = now + 2000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
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
            manualPauseLocked = false,
            hasSpeedSource = true
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
            bikeResumeDebounceSec = 2,
            runSpeedThreshold = 2,
            runDebounceSec = 5,
            runResumeDebounceSec = 2,
            otherSpeedThreshold = 1,
            otherDebounceSec = 10,
            otherResumeDebounceSec = 2,
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

    @Test
    fun `TST-TRK-020_1 MySensor tracks and propagates lastUpdateTimestamp`() {
        val sensor = MySensor<Number>(null, SensorType.SPEED_mps)
        assertEquals("Initial timestamp must be 0L before any newValue()", 0L, sensor.lastUpdateTimestamp)
        assertEquals(0L, sensor.sensorData.timestamp)

        val before = System.currentTimeMillis()
        sensor.newValue(5.0)
        val after = System.currentTimeMillis()

        assertTrue("Timestamp must be >= before", sensor.lastUpdateTimestamp >= before)
        assertTrue("Timestamp must be <= after", sensor.lastUpdateTimestamp <= after)
        assertEquals(sensor.lastUpdateTimestamp, sensor.sensorData.timestamp)
        assertEquals(5.0, sensor.sensorData.value)
    }

    @Test
    fun `TST-TRK-020_2 ProxySensor delegates timestamp to source sensor and preserves null deviceName`() {
        val sourceSensor = MySensor<Number>(null, SensorType.SPEED_mps)
        val proxySensor = ProxySensor<Number>(null, SensorType.SPEED_mps, sourceSensor)

        assertEquals(0L, proxySensor.lastUpdateTimestamp)
        assertEquals(0L, proxySensor.sensorData.timestamp)
        assertNull("ProxySensor must keep getDeviceName() null so primary DB columns remain unsuffixed", proxySensor.deviceName)

        sourceSensor.newValue(4.2)
        val expectedTs = sourceSensor.lastUpdateTimestamp
        assertTrue(expectedTs > 0L)

        val proxyData = proxySensor.sensorData
        assertEquals(expectedTs, proxySensor.lastUpdateTimestamp)
        assertEquals(expectedTs, proxyData.timestamp)
        assertEquals(4.2, proxyData.value)
        assertNull(proxyData.deviceName)

        // Unbound ProxySensor safely returns 0L and null value without NPE
        proxySensor.setSourceSensor(null)
        assertEquals(0L, proxySensor.lastUpdateTimestamp)
        assertEquals(0L, proxySensor.sensorData.timestamp)
        assertNull(proxySensor.sensorData.value)
    }

    @Test
    fun `TST-TRK-020_3 standstill silence triggers AUTO_PAUSED without entering SENSOR_LOST`() {
        val config = AutoPauseConfig(bikeEnabled = true, speedThresholdKmh = 1.5f, debounceSec = 3)
        val t0 = 100_000L

        // Athlete stops at t0 (speed 0.0 m/s), then GPS/ANT+ sensor goes silent for 10s (> 5000ms TTL)
        val tick1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.0,
            packetTimestampMs = t0,
            currentTimestampMs = t0 + 10_000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, tick1.newState)
        assertEquals(1, autoPauseManager.debounceCounter)
        assertFalse(tick1.triggerPause)

        // Even if sensor value is null while source sensor is registered (e.g. awaiting first fix at standstill)
        val tick2 = autoPauseManager.evaluateTick(
            currentSpeedMps = null,
            packetTimestampMs = 0L,
            currentTimestampMs = t0 + 11_000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, tick2.newState)
        assertEquals(2, autoPauseManager.debounceCounter)
        assertFalse(tick2.triggerPause)

        val tick3 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.0,
            packetTimestampMs = t0,
            currentTimestampMs = t0 + 12_000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, tick3.newState)
        assertTrue(tick3.triggerPause)
        assertEquals(SoundCue.AUTO_PAUSE, tick3.soundCue)
    }

    @Test
    fun `TST-TRK-020_4 standstill deceleration silence below resume threshold decays to 0 and triggers AUTO_PAUSED`() {
        val config = AutoPauseConfig(bikeEnabled = true, speedThresholdKmh = 1.5f, debounceSec = 2)
        val t0 = 200_000L

        // Last packet had slow decelerating speed 0.3 m/s (<= resume threshold) at t0, and sensor stopped transmitting for > 5000ms
        val tick1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.3,
            packetTimestampMs = t0,
            currentTimestampMs = t0 + 6_000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, tick1.newState)
        assertEquals(1, autoPauseManager.debounceCounter)
        assertFalse(tick1.triggerPause)

        val tick2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.3,
            packetTimestampMs = t0,
            currentTimestampMs = t0 + 7_000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, tick2.newState)
        assertTrue(tick2.triggerPause)
    }

    @Test
    fun `TST-TRK-020_5 onManualResume transitions AUTO_PAUSED to TRACKING and allows subsequent auto-pause`() {
        val config = AutoPauseConfig(bikeEnabled = true, speedThresholdKmh = 1.5f, debounceSec = 2)
        val now = 300_000L

        // Enter AUTO_PAUSED
        autoPauseManager.evaluateTick(0.0, now, now, BSportType.BIKE, config, false, true)
        val pauseTrans = autoPauseManager.evaluateTick(0.0, now + 1000L, now + 1000L, BSportType.BIKE, config, false, true)
        assertEquals(AutoPauseState.AUTO_PAUSED, pauseTrans.newState)
        assertTrue(pauseTrans.triggerPause)

        // User manually resumes while still stationary
        autoPauseManager.onManualResume()
        assertEquals(AutoPauseState.TRACKING, autoPauseManager.currentState)
        assertEquals(0, autoPauseManager.debounceCounter)

        // Subsequent stationary ticks re-evaluate debounce and re-trigger AUTO_PAUSED
        val sub1 = autoPauseManager.evaluateTick(0.0, now + 2000L, now + 2000L, BSportType.BIKE, config, false, true)
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, sub1.newState)
        assertFalse(sub1.triggerPause)

        val sub2 = autoPauseManager.evaluateTick(0.0, now + 3000L, now + 3000L, BSportType.BIKE, config, false, true)
        assertEquals(AutoPauseState.AUTO_PAUSED, sub2.newState)
        assertTrue(sub2.triggerPause)
    }

    @Test
    fun `TST-TRK-020_6 DefaultAutoPauseAudioNotifier reuses single ToneGenerator and releases cleanly`() {
        var createdCount = 0
        val mockToneGenerator = mockk<ToneGenerator>(relaxed = true)
        every { mockToneGenerator.startTone(any(), any()) } returns true

        val notifier = DefaultAutoPauseAudioNotifier {
            createdCount++
            mockToneGenerator
        }

        notifier.playTone(SoundCue.AUTO_PAUSE)
        notifier.playTone(SoundCue.AUTO_RESUME)
        notifier.playTone(SoundCue.AUTO_PAUSE)

        assertEquals("Must allocate at most 1 ToneGenerator across multiple playTone calls", 1, createdCount)
        verify(exactly = 2) { mockToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200) }
        verify(exactly = 1) { mockToneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200) }

        notifier.release()
        verify(exactly = 1) { mockToneGenerator.release() }

        // Playing after release lazily creates a new instance
        notifier.playTone(SoundCue.AUTO_RESUME)
        assertEquals(2, createdCount)
    }

    @Test
    fun `TST-TRK-020_7 onManualResume respects manualPauseLocked and release is idempotent on IllegalStateException`() {
        // 1. Verify onManualResume is a no-op when TrainingApplication.isManualPauseLocked() is true
        TrainingApplication.setManualPauseLock(true, null)
        autoPauseManager.setState(AutoPauseState.MANUAL_PAUSED)
        autoPauseManager.onManualResume()
        assertEquals(
            "onManualResume must not override MANUAL_PAUSED while manualPauseLocked is active",
            AutoPauseState.MANUAL_PAUSED,
            autoPauseManager.currentState
        )
        TrainingApplication.setManualPauseLock(false, null)

        // 2. Verify DefaultAutoPauseAudioNotifier.release() is idempotent and swallows IllegalStateException
        val throwingToneGen = mockk<ToneGenerator>(relaxed = true)
        every { throwingToneGen.startTone(any(), any()) } returns true
        every { throwingToneGen.release() } throws IllegalStateException("Already released")

        val notifier = DefaultAutoPauseAudioNotifier { throwingToneGen }
        notifier.playTone(SoundCue.AUTO_PAUSE)

        // First release catches IllegalStateException and nulls reference
        notifier.release()
        // Second release is a no-op and does not invoke release() again
        notifier.release()
        verify(exactly = 1) { throwingToneGen.release() }

        // Also verify AutoPauseManager.release() delegates to audioNotifier.release()
        autoPauseManager.release()
        assertEquals(1, mockAudioNotifier.releaseCount)
    }

    @Test
    fun `TST-TRK-030_1 in-motion dropout above resumeThreshold transitions to SENSOR_LOST without pausing`() {
        val config = AutoPauseConfig(bikeEnabled = true, speedThresholdKmh = 1.5f, debounceSec = 2)
        val t0 = 100_000L

        // Cruising at 8.0 m/s (28.8 km/h > resume threshold ~2.0 km/h)
        val tick0 = autoPauseManager.evaluateTick(
            currentSpeedMps = 8.0,
            packetTimestampMs = t0,
            currentTimestampMs = t0,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.TRACKING, tick0.newState)
        assertEquals(8.0, autoPauseManager.lastValidSpeedMps)

        // Enter tunnel: packets stop arriving for 6 seconds (> 5000ms TTL)
        val tick1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 8.0,
            packetTimestampMs = t0,
            currentTimestampMs = t0 + 6000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.SENSOR_LOST, tick1.newState)
        assertEquals("SENSOR_LOST_IN_MOTION_DROPOUT", tick1.auditLogEvent)
        assertEquals(0, autoPauseManager.debounceCounter)
        assertFalse(tick1.triggerPause)
        assertEquals(8.0, autoPauseManager.lastValidSpeedMps)

        // Subsequent stale ticks in tunnel remain in SENSOR_LOST, NOT transitioning to AUTO_PAUSED
        val tick2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 8.0,
            packetTimestampMs = t0,
            currentTimestampMs = t0 + 7000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.SENSOR_LOST, tick2.newState)
        assertFalse(tick2.triggerPause)
    }

    @Test
    fun `TST-TRK-030_2 standstill packet silence debounces to AUTO_PAUSED`() {
        val config = AutoPauseConfig(bikeEnabled = true, speedThresholdKmh = 1.5f, debounceSec = 2)
        val t0 = 100_000L

        // Decelerating at red light: speed is 0.5 m/s (1.8 km/h > pause threshold 1.5 km/h, <= resume threshold 2.0 km/h)
        val tick0 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.5,
            packetTimestampMs = t0,
            currentTimestampMs = t0,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.TRACKING, tick0.newState)
        assertEquals(0.5, autoPauseManager.lastValidSpeedMps)

        // Sensor ceases transmission at standstill (> 5000ms TTL)
        val tick1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.5,
            packetTimestampMs = t0,
            currentTimestampMs = t0 + 6000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, tick1.newState)
        assertEquals(1, autoPauseManager.debounceCounter)
        assertFalse(tick1.triggerPause)

        // Second tick debounces to AUTO_PAUSED
        val tick2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 0.5,
            packetTimestampMs = t0,
            currentTimestampMs = t0 + 7000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, tick2.newState)
        assertTrue(tick2.triggerPause)
        assertEquals("AUTO_PAUSED_SPEED_BELOW_THRESHOLD", tick2.auditLogEvent)
    }

    @Test
    fun `TST-TRK-030_3 dynamic unpairing transitions to SENSOR_LOST and clears lastValidSpeedMps`() {
        val config = AutoPauseConfig(bikeEnabled = true, speedThresholdKmh = 1.5f, debounceSec = 2)
        val t0 = 100_000L

        // Actively moving
        autoPauseManager.evaluateTick(
            currentSpeedMps = 6.0,
            packetTimestampMs = t0,
            currentTimestampMs = t0,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(6.0, autoPauseManager.lastValidSpeedMps)

        // Sensor unpairs or powers off mid-session
        val unpairTick = autoPauseManager.evaluateTick(
            currentSpeedMps = null,
            packetTimestampMs = 0L,
            currentTimestampMs = t0 + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = false
        )
        assertEquals(AutoPauseState.SENSOR_LOST, unpairTick.newState)
        assertEquals("SENSOR_LOST_STALE_TTL_EXCEEDED", unpairTick.auditLogEvent)
        assertNull(autoPauseManager.lastValidSpeedMps)

        // Reset also clears lastValidSpeedMps
        autoPauseManager.evaluateTick(
            currentSpeedMps = 5.0,
            packetTimestampMs = t0 + 2000L,
            currentTimestampMs = t0 + 2000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(5.0, autoPauseManager.lastValidSpeedMps)
        autoPauseManager.reset()
        assertNull(autoPauseManager.lastValidSpeedMps)
    }

    @Test
    fun `TST-TRK-030_4 sensor recovery hysteresis requires two consecutive valid packets`() {
        val config = AutoPauseConfig(bikeEnabled = true, speedThresholdKmh = 1.5f, debounceSec = 2)
        val t0 = 100_000L

        // Force SENSOR_LOST state
        autoPauseManager.setState(AutoPauseState.SENSOR_LOST)

        // 1st valid packet arrives: remains SENSOR_LOST with recovery count 1
        val recoveryTick1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 7.0,
            packetTimestampMs = t0,
            currentTimestampMs = t0,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.SENSOR_LOST, recoveryTick1.newState)
        assertEquals(1, autoPauseManager.sensorLostRecoveryTicks)

        // 2nd valid packet arrives: recovers to TRACKING
        val recoveryTick2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 7.0,
            packetTimestampMs = t0 + 1000L,
            currentTimestampMs = t0 + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.TRACKING, recoveryTick2.newState)
        assertEquals("SENSOR_LOST_RECOVERED", recoveryTick2.auditLogEvent)
        assertEquals(0, autoPauseManager.sensorLostRecoveryTicks)
    }

    @Test
    fun `TST-TRK-030_5 decoupled onManualResume respects manualPauseLocked parameter`() {
        // In AUTO_PAUSED state
        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)

        // Calling with manualPauseLocked = true is a no-op
        autoPauseManager.onManualResume(manualPauseLocked = true)
        assertEquals(AutoPauseState.AUTO_PAUSED, autoPauseManager.currentState)

        // Calling with manualPauseLocked = false resumes to TRACKING
        autoPauseManager.onManualResume(manualPauseLocked = false)
        assertEquals(AutoPauseState.TRACKING, autoPauseManager.currentState)

        // Default overload (manualPauseLocked = false) resumes to TRACKING
        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)
        autoPauseManager.onManualResume()
        assertEquals(AutoPauseState.TRACKING, autoPauseManager.currentState)
    }

    @Test
    fun `TST-TRK-043_1 multi-tick resume debounce on Bike, Run, Other`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            runEnabled = true,
            otherEnabled = true,
            bikeSpeedThreshold = 3,
            bikeResumeDebounceSec = 2,
            runSpeedThreshold = 2,
            runResumeDebounceSec = 5,
            otherSpeedThreshold = 1,
            otherResumeDebounceSec = 10,
            isMetric = true
        )
        val now = 100000L

        // 1. BIKE: requires 2 ticks >= 3 km/h (0.833 m/s)
        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)
        val bikeTick1 = autoPauseManager.evaluateTick(
            currentSpeedMps = 3.5 / 3.6,
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, bikeTick1.newState)
        assertFalse(bikeTick1.triggerResume)
        assertEquals(1, autoPauseManager.resumeDebounceCounter)

        val bikeTick2 = autoPauseManager.evaluateTick(
            currentSpeedMps = 3.5 / 3.6,
            packetTimestampMs = now + 1000L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.TRACKING, bikeTick2.newState)
        assertTrue(bikeTick2.triggerResume)
        assertEquals(SoundCue.AUTO_RESUME, bikeTick2.soundCue)
        assertEquals(0, autoPauseManager.resumeDebounceCounter)

        // 2. RUN: requires 5 ticks >= 2 km/h (0.556 m/s)
        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)
        for (i in 1..4) {
            val runTick = autoPauseManager.evaluateTick(
                currentSpeedMps = 2.5 / 3.6,
                packetTimestampMs = now + (i * 1000L),
                currentTimestampMs = now + (i * 1000L),
                sportType = BSportType.RUN,
                config = config,
                manualPauseLocked = false
            )
            assertEquals(AutoPauseState.AUTO_PAUSED, runTick.newState)
            assertEquals(i, autoPauseManager.resumeDebounceCounter)
        }
        val runTick5 = autoPauseManager.evaluateTick(
            currentSpeedMps = 2.5 / 3.6,
            packetTimestampMs = now + 5000L,
            currentTimestampMs = now + 5000L,
            sportType = BSportType.RUN,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.TRACKING, runTick5.newState)
        assertTrue(runTick5.triggerResume)

        // 3. OTHER: requires 10 ticks >= 1 km/h (0.278 m/s)
        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)
        for (i in 1..9) {
            val otherTick = autoPauseManager.evaluateTick(
                currentSpeedMps = 1.5 / 3.6,
                packetTimestampMs = now + (i * 1000L),
                currentTimestampMs = now + (i * 1000L),
                sportType = BSportType.UNKNOWN,
                config = config,
                manualPauseLocked = false
            )
            assertEquals(AutoPauseState.AUTO_PAUSED, otherTick.newState)
            assertEquals(i, autoPauseManager.resumeDebounceCounter)
        }
        val otherTick10 = autoPauseManager.evaluateTick(
            currentSpeedMps = 1.5 / 3.6,
            packetTimestampMs = now + 10000L,
            currentTimestampMs = now + 10000L,
            sportType = BSportType.UNKNOWN,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.TRACKING, otherTick10.newState)
        assertTrue(otherTick10.triggerResume)
    }

    @Test
    fun `TST-TRK-043_2 transient GPS spike rejection and counter reset on stationary or stale tick`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            bikeSpeedThreshold = 3,
            bikeResumeDebounceSec = 3,
            isMetric = true
        )
        val now = 100000L

        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)

        // Tick 1: GPS glitch spike at 15 km/h
        autoPauseManager.evaluateTick(15.0 / 3.6, now, now, BSportType.BIKE, config, false)
        assertEquals(1, autoPauseManager.resumeDebounceCounter)
        assertEquals(AutoPauseState.AUTO_PAUSED, autoPauseManager.currentState)

        // Tick 2: drops back to 0.0 m/s -> resets resumeDebounceCounter to 0
        autoPauseManager.evaluateTick(0.0, now + 1000L, now + 1000L, BSportType.BIKE, config, false)
        assertEquals(0, autoPauseManager.resumeDebounceCounter)
        assertEquals(AutoPauseState.AUTO_PAUSED, autoPauseManager.currentState)

        // Tick 3: 2 ticks above threshold
        autoPauseManager.evaluateTick(5.0 / 3.6, now + 2000L, now + 2000L, BSportType.BIKE, config, false)
        autoPauseManager.evaluateTick(5.0 / 3.6, now + 3000L, now + 3000L, BSportType.BIKE, config, false)
        assertEquals(2, autoPauseManager.resumeDebounceCounter)

        // Tick 4: Stale packet (> 5000ms) resets resumeDebounceCounter to 0
        autoPauseManager.evaluateTick(5.0 / 3.6, now + 3000L, now + 9000L, BSportType.BIKE, config, false)
        assertEquals(0, autoPauseManager.resumeDebounceCounter)
        assertEquals(AutoPauseState.AUTO_PAUSED, autoPauseManager.currentState)
    }

    @Test
    fun `TST-TRK-043_3 exact single threshold evaluation without hysteresis offset`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            bikeSpeedThreshold = 3, // exactly 3 km/h (0.833 m/s)
            bikeResumeDebounceSec = 2,
            isMetric = true
        )
        val now = 100000L
        val exactThresholdMps = 3.0 / 3.6

        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)

        // Tick 1: Exactly 3.0 km/h (no +0.5 offset required) -> counts toward resume!
        val tick1 = autoPauseManager.evaluateTick(
            currentSpeedMps = exactThresholdMps,
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, tick1.newState)
        assertEquals(1, autoPauseManager.resumeDebounceCounter)

        // Tick 2: Exactly 3.0 km/h -> reaches resumeDebounceSec=2 and resumes!
        val tick2 = autoPauseManager.evaluateTick(
            currentSpeedMps = exactThresholdMps,
            packetTimestampMs = now + 1000L,
            currentTimestampMs = now + 1000L,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false
        )
        assertEquals(AutoPauseState.TRACKING, tick2.newState)
        assertTrue(tick2.triggerResume)
    }

    @Test
    fun `TST-TRK-044_1 shouldStartInAutoPause contract`() {
        val disabledConfig = AutoPauseConfig(
            bikeEnabled = true,
            startInAutoPauseEnabled = false
        )
        assertFalse(
            "Disabled globally -> must return false",
            AutoPauseManager.shouldStartInAutoPause(disabledConfig, BSportType.BIKE, 0.0, hasSpeedSource = true)
        )

        val enabledConfig = AutoPauseConfig(
            bikeEnabled = true,
            runEnabled = false,
            otherEnabled = true,
            startInAutoPauseEnabled = true,
            bikeSpeedThreshold = 3,
            isMetric = true
        )

        assertFalse(
            "hasSpeedSource false -> must return false immediately (indoor HR-only)",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.BIKE, null, hasSpeedSource = false)
        )
        assertFalse(
            "hasSpeedSource false with 0.0 -> must return false immediately",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.BIKE, 0.0, hasSpeedSource = false)
        )
        assertFalse(
            "hasSpeedSource default false -> must return false",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.BIKE, 0.0)
        )

        assertFalse(
            "Sport disabled -> must return false",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.RUN, 0.0, hasSpeedSource = true)
        )

        assertTrue(
            "hasSpeedSource true with Speed null -> stationary GPS acquiring -> true",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.BIKE, null, hasSpeedSource = true)
        )

        assertTrue(
            "hasSpeedSource true with Speed 0.0 -> stationary -> true",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.BIKE, 0.0, hasSpeedSource = true)
        )

        assertTrue(
            "hasSpeedSource true with Speed below threshold -> stationary -> true",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.BIKE, 1.0 / 3.6, hasSpeedSource = true)
        )

        assertFalse(
            "hasSpeedSource true with Speed equal to threshold -> moving -> false",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.BIKE, 3.0 / 3.6, hasSpeedSource = true)
        )
        assertFalse(
            "hasSpeedSource true with Speed above threshold -> moving -> false",
            AutoPauseManager.shouldStartInAutoPause(enabledConfig, BSportType.BIKE, 10.0 / 3.6, hasSpeedSource = true)
        )
    }

    @Test
    fun `TST-TRK-044_3 mid-workout speed source removal transitions out of AUTO_PAUSED to SENSOR_LOST`() {
        val config = AutoPauseConfig(
            bikeEnabled = true,
            bikeSpeedThreshold = 3,
            isMetric = true
        )
        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)
        val now = 200000L

        // Tick with hasSpeedSource = false (speed sensor unpaired/disconnected)
        val transition = autoPauseManager.evaluateTick(
            currentSpeedMps = null,
            packetTimestampMs = 0L,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = false
        )

        assertEquals(AutoPauseState.AUTO_PAUSED, transition.previousState)
        assertEquals(AutoPauseState.SENSOR_LOST, transition.newState)
        assertTrue("Must trigger resume to recover tracking recording", transition.triggerResume)
        assertEquals("SENSOR_LOST_SPEED_SOURCE_REMOVED", transition.auditLogEvent)
        assertEquals(0, autoPauseManager.debounceCounter)
        assertEquals(0, autoPauseManager.resumeDebounceCounter)

        // Contrast with corrupt speed while hasSpeedSource = true (should remain AUTO_PAUSED)
        autoPauseManager.setState(AutoPauseState.AUTO_PAUSED)
        val corruptTransition = autoPauseManager.evaluateTick(
            currentSpeedMps = Double.NaN,
            packetTimestampMs = now,
            currentTimestampMs = now,
            sportType = BSportType.BIKE,
            config = config,
            manualPauseLocked = false,
            hasSpeedSource = true
        )
        assertEquals(AutoPauseState.AUTO_PAUSED, corruptTransition.previousState)
        assertEquals(AutoPauseState.AUTO_PAUSED, corruptTransition.newState)
        assertFalse(corruptTransition.triggerResume)
    }
}


