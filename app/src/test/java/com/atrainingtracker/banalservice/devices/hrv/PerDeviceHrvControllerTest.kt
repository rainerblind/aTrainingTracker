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

package com.atrainingtracker.banalservice.devices.hrv

import android.content.Context
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [PerDeviceHrvController] covering Rule 52 lazy single-batch registration,
 * disconnect `newValue(null)`-while-activated guard, Rule 51 `onAccumulatorsReset()`, scheduler
 * cancellation & reconnect re-arming, and concurrent thread safety (`REQ-HRV-050.3` / `TST-HRV-050.3`).
 */
class PerDeviceHrvControllerTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager

    private class FakeHrmHost(
        context: Context,
        sensorManager: MySensorManager,
        private val deviceName: String
    ) : MyDevice(context, sensorManager, DeviceType.HRM) {
        val batchRegisterCalls = AtomicInteger(0)
        val unregisteredTypes = mutableListOf<SensorType>()
        val removedTypes = mutableListOf<SensorType>()

        override fun addSensors() {}
        override fun getName(): String = deviceName
        override fun notifySensorsChanged() {}

        fun callbacks(): PerDeviceHrvController.HostCallbacks = object : PerDeviceHrvController.HostCallbacks {
            override fun addAndRegisterSensors(sensors: List<MySensor<*>>) {
                batchRegisterCalls.incrementAndGet()
                for (s in sensors) {
                    addSensor(s)
                    mMySensorManager.registerSensor(s)
                    s.activateSensor()
                }
            }

            override fun unregisterSensor(sensor: MySensor<*>) {
                unregisteredTypes.add(sensor.sensorType)
                mMySensorManager.unregisterSensor(sensor)
            }

            override fun removeSensor(sensorType: SensorType) {
                removedTypes.add(sensorType)
                this@FakeHrmHost.removeSensor(sensorType)
            }
        }
    }

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        context = mockk(relaxed = true)
        sensorManager = object : MySensorManager(context) {
            override fun notifySensorsChanged() {}
        }
    }

    @After
    fun tearDown() {
        sensorManager.shutDown()
        unmockkAll()
    }

    @Test
    fun testLazySingleBatchRegistration_andDisconnectNullWhileActivatedGuard_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val host = FakeHrmHost(context, sensorManager, "Polar H10")
        val baseTs = 1_000_000L
        var nowMs = baseTs + 120_000L
        val controller = PerDeviceHrvController(
            context = context,
            hostDevice = host,
            hostCallbacks = host.callbacks(),
            timeProvider = { nowMs },
            startScheduler = false,
            artifactGateThresholdPercent = 5.0
        )

        // Rule 52: Zero sensors registered before first RR beat
        assertFalse(controller.hasRegisteredSensors())
        assertEquals(0, host.batchRegisterCalls.get())
        assertNull(host.getSensor(SensorType.HRV_DFA_A1))
        assertNull(host.getSensor(SensorType.HRV_SDNN))

        // Feed 150 beats -> single batch registration of all 4 HRV sensors
        for (i in 0 until 150) {
            val rr = 800.0 + 20.0 * Math.sin(2.0 * Math.PI * i / 12.0)
            controller.onRrBeat(RrBeatEvent(baseTs + i * 800L, rr, i, ProtocolType.BLE))
        }
        assertTrue(controller.hasRegisteredSensors())
        assertEquals("All 4 HRV sensors must register in one batch call", 1, host.batchRegisterCalls.get())

        @Suppress("UNCHECKED_CAST")
        val dfaSensor = host.getSensor(SensorType.HRV_DFA_A1) as MySensor<Double>
        @Suppress("UNCHECKED_CAST")
        val rmssdSensor = host.getSensor(SensorType.HRV_RMSSD) as MySensor<Double>
        @Suppress("UNCHECKED_CAST")
        val artSensor = host.getSensor(SensorType.HRV_ARTIFACT_RATE) as MySensor<Double>
        @Suppress("UNCHECKED_CAST")
        val sdnnSensor = host.getSensor(SensorType.HRV_SDNN) as MySensor<Double>

        // Attach listeners capturing every delivered value (simulating FilterManager)
        val dfaEvents = mutableListOf<Any?>()
        val rmssdEvents = mutableListOf<Any?>()
        val artEvents = mutableListOf<Any?>()
        val sdnnEvents = mutableListOf<Any?>()
        dfaSensor.addSensorListener { dfaEvents.add(it) }
        rmssdSensor.addSensorListener { rmssdEvents.add(it) }
        artSensor.addSensorListener { artEvents.add(it) }
        sdnnSensor.addSensorListener { sdnnEvents.add(it) }

        controller.calculateMetrics()
        assertNotNull(dfaSensor.value)
        assertNotNull(rmssdSensor.value)
        assertNotNull(artSensor.value)
        assertNotNull(sdnnSensor.value)

        // Disconnect MUST push null while sensors are STILL activated BEFORE deactivation/removal
        controller.onDisconnect()
        assertNull("Listener must receive null on disconnect for DFA a1", dfaEvents.last())
        assertNull("Listener must receive null on disconnect for rMSSD", rmssdEvents.last())
        assertNull("Listener must receive null on disconnect for Artifact Rate", artEvents.last())
        assertNull("Listener must receive null on disconnect for SDNN", sdnnEvents.last())
        assertFalse(dfaSensor.isActivated)
        assertFalse(controller.hasRegisteredSensors())
        assertNull(host.getSensor(SensorType.HRV_DFA_A1))
        assertNull(controller.latestArtifactRatePercent)
        assertEquals(HrvSignalQualityStatus.INSUFFICIENT_DATA, controller.signalQualityResult.status)

        // Reconnect re-arms cleanly with fresh sensors
        nowMs += 10_000L
        controller.onRrBeat(RrBeatEvent(nowMs, 810.0, 200, ProtocolType.BLE))
        assertTrue(controller.hasRegisteredSensors())
        assertEquals(2, host.batchRegisterCalls.get())
        assertNotNull(host.getSensor(SensorType.HRV_SDNN))

        controller.shutDown()
        assertFalse(controller.hasRegisteredSensors())
    }

    @Test
    fun testOnAccumulatorsReset_pushesNullKeepsSensorsRegisteredAndPausesScheduler_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val host = FakeHrmHost(context, sensorManager, "Polar H10")
        val baseTs = 1_000_000L
        val nowMs = baseTs + 120_000L
        val controller = PerDeviceHrvController(
            context = context,
            hostDevice = host,
            hostCallbacks = host.callbacks(),
            timeProvider = { nowMs },
            startScheduler = true,
            artifactGateThresholdPercent = 5.0
        )

        try {
            assertFalse("Scheduler must stay inactive before first RR beat", controller.isSchedulerActive())
            for (i in 0 until 60) {
                controller.onRrBeat(RrBeatEvent(baseTs + 60_000L + i * 800L, 800.0 + (i % 2) * 20.0, i, ProtocolType.BLE))
            }
            assertTrue("Scheduler must start lazily on first RR beat", controller.isSchedulerActive())
            controller.calculateMetrics()

            @Suppress("UNCHECKED_CAST")
            val sdnnSensor = host.getSensor(SensorType.HRV_SDNN) as MySensor<Double>
            val received = mutableListOf<Any?>()
            sdnnSensor.addSensorListener { received.add(it) }
            assertNotNull(sdnnSensor.value)

            // Accumulator reset pushes null, resets engine, pauses scheduler, WITHOUT tearing down sensors
            controller.onAccumulatorsReset()
            assertNull("onAccumulatorsReset must push null while still activated", received.last())
            assertTrue("Sensors must remain registered while HRM is connected", controller.hasRegisteredSensors())
            assertTrue("Sensor must stay activated", sdnnSensor.isActivated)
            assertFalse("Scheduler must pause until next RR beat arrives", controller.isSchedulerActive())
            assertFalse(controller.hasReceivedRrData)

            // Next RR beat re-arms scheduler without re-registering sensors
            controller.onRrBeat(RrBeatEvent(nowMs + 1000L, 790.0, 61, ProtocolType.BLE))
            assertTrue("Scheduler must re-arm on next RR beat", controller.isSchedulerActive())
            assertEquals("Sensors must not be re-registered after accumulator reset", 1, host.batchRegisterCalls.get())
        } finally {
            controller.shutDown()
            assertFalse(controller.isSchedulerActive())
        }
    }

    @Test
    fun testConcurrentBeatsCalculateAndReset_remainsThreadSafeWithoutExceptions_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val host = FakeHrmHost(context, sensorManager, "Polar Concurrent")
        val baseTs = 5_000_000L
        val controller = PerDeviceHrvController(
            context = context,
            hostDevice = host,
            hostCallbacks = host.callbacks(),
            timeProvider = { baseTs + 60_000L },
            startScheduler = false,
            artifactGateThresholdPercent = 5.0
        )

        val pool = Executors.newFixedThreadPool(3)
        val latch = CountDownLatch(3)
        val errors = AtomicInteger(0)

        pool.submit {
            try {
                for (i in 0 until 120) {
                    controller.onRrBeat(RrBeatEvent(baseTs + i * 500L, 750.0 + (i % 4) * 15.0, i, ProtocolType.BLE))
                }
            } catch (_: Throwable) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }
        pool.submit {
            try {
                for (i in 0 until 40) {
                    controller.calculateMetrics()
                }
            } catch (_: Throwable) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }
        pool.submit {
            try {
                for (i in 0 until 10) {
                    controller.onAccumulatorsReset()
                }
            } catch (_: Throwable) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()
        controller.shutDown()
        assertEquals("Zero concurrency exceptions expected", 0, errors.get())
    }

    @Test
    fun testRacingOnRrBeatVsShutDown_leavesZeroOrphanedSensors_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val host = FakeHrmHost(context, sensorManager, "Polar RaceShutDown")
        val enteredRegister = CountDownLatch(1)
        val allowRegisterFinish = CountDownLatch(1)
        val activeRegistered = java.util.Collections.synchronizedSet(mutableSetOf<MySensor<*>>())
        val baseCallbacks = host.callbacks()

        val gatedCallbacks = object : PerDeviceHrvController.HostCallbacks {
            override fun addAndRegisterSensors(sensors: List<MySensor<*>>) {
                enteredRegister.countDown()
                allowRegisterFinish.await(5, TimeUnit.SECONDS)
                activeRegistered.addAll(sensors)
                baseCallbacks.addAndRegisterSensors(sensors)
            }

            override fun unregisterSensor(sensor: MySensor<*>) {
                activeRegistered.remove(sensor)
                baseCallbacks.unregisterSensor(sensor)
            }

            override fun removeSensor(sensorType: SensorType) {
                baseCallbacks.removeSensor(sensorType)
            }
        }

        val controller = PerDeviceHrvController(
            context = context,
            hostDevice = host,
            hostCallbacks = gatedCallbacks,
            timeProvider = { 1_000_000L },
            startScheduler = false
        )

        val tBeat = Thread {
            controller.onRrBeat(RrBeatEvent(1_000_000L, 800.0, 1, ProtocolType.BLE))
        }
        tBeat.start()
        assertTrue("onRrBeat must reach addAndRegisterSensors", enteredRegister.await(5, TimeUnit.SECONDS))

        val tShutDown = Thread { controller.shutDown() }
        tShutDown.start()
        // Allow registration to complete; shutDown serialized on publishLock must then cleanly detach all 4
        allowRegisterFinish.countDown()

        tBeat.join(5_000L)
        tShutDown.join(5_000L)

        assertFalse("Controller must hold zero registered sensors after racing shutDown", controller.hasRegisteredSensors())
        assertTrue("Zero orphaned sensors may remain registered: $activeRegistered", activeRegistered.isEmpty())
        assertNull(host.getSensor(SensorType.HRV_DFA_A1))
        assertNull(host.getSensor(SensorType.HRV_RMSSD))
        assertNull(host.getSensor(SensorType.HRV_ARTIFACT_RATE))
        assertNull(host.getSensor(SensorType.HRV_SDNN))

        // Post-shutdown beat must also never orphan sensors
        controller.onRrBeat(RrBeatEvent(1_001_000L, 800.0, 2, ProtocolType.BLE))
        assertTrue("Post-shutdown beat must not register any sensors", activeRegistered.isEmpty())
        assertNull(host.getSensor(SensorType.HRV_SDNN))
    }

    @Test
    fun testRacingOnDisconnectVsReconnectOnRrBeat_neverClobbersReconnectSensors_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val host = FakeHrmHost(context, sensorManager, "Polar RaceReconnect")
        val enteredUnregister = CountDownLatch(1)
        val allowDisconnectFinish = CountDownLatch(1)
        val eventLog = java.util.Collections.synchronizedList(mutableListOf<String>())
        val baseCallbacks = host.callbacks()

        val gatedCallbacks = object : PerDeviceHrvController.HostCallbacks {
            override fun addAndRegisterSensors(sensors: List<MySensor<*>>) {
                eventLog.add("register:${sensors.size}")
                baseCallbacks.addAndRegisterSensors(sensors)
            }

            override fun unregisterSensor(sensor: MySensor<*>) {
                eventLog.add("unregister:${sensor.sensorType.name}")
                if (enteredUnregister.count > 0) {
                    enteredUnregister.countDown()
                    allowDisconnectFinish.await(5, TimeUnit.SECONDS)
                }
                baseCallbacks.unregisterSensor(sensor)
            }

            override fun removeSensor(sensorType: SensorType) {
                eventLog.add("remove:${sensorType.name}")
                baseCallbacks.removeSensor(sensorType)
            }
        }

        val controller = PerDeviceHrvController(
            context = context,
            hostDevice = host,
            hostCallbacks = gatedCallbacks,
            timeProvider = { 2_000_000L },
            startScheduler = false
        )

        // Initial cold-start registration
        controller.onRrBeat(RrBeatEvent(2_000_000L, 800.0, 1, ProtocolType.BLE))
        val initialSdnnSensor = host.getSensor(SensorType.HRV_SDNN)
        assertNotNull(initialSdnnSensor)

        // Start disconnect and pause mid-detachment (before removeSensor completes)
        val tDisconnect = Thread { controller.onDisconnect() }
        tDisconnect.start()
        assertTrue("onDisconnect must reach unregisterSensor", enteredUnregister.await(5, TimeUnit.SECONDS))

        // Concurrently fire reconnect beat while disconnect is mid-flight
        val tReconnect = Thread {
            controller.onRrBeat(RrBeatEvent(2_005_000L, 810.0, 2, ProtocolType.BLE))
        }
        tReconnect.start()
        // Release disconnect; reconnect serialized on publishLock must run strictly AFTER all removeSensor calls
        allowDisconnectFinish.countDown()

        tDisconnect.join(5_000L)
        tReconnect.join(5_000L)

        assertTrue("Controller must hold newly registered reconnect sensors", controller.hasRegisteredSensors())
        val reconnectedSdnn = host.getSensor(SensorType.HRV_SDNN)
        assertNotNull("Reconnect HRV_SDNN must NOT be clobbered by racing disconnect", reconnectedSdnn)
        assertNotNull("Reconnect HRV_DFA_A1 must NOT be clobbered", host.getSensor(SensorType.HRV_DFA_A1))
        assertNotNull("Reconnect HRV_RMSSD must NOT be clobbered", host.getSensor(SensorType.HRV_RMSSD))
        assertNotNull("Reconnect HRV_ARTIFACT_RATE must NOT be clobbered", host.getSensor(SensorType.HRV_ARTIFACT_RATE))
        org.junit.Assert.assertNotSame("Reconnect must register a fresh sensor instance", initialSdnnSensor, reconnectedSdnn)
        assertTrue("Reconnected sensor must remain activated", reconnectedSdnn!!.isActivated)

        val lastRemoveIdx = eventLog.indexOfLast { it.startsWith("remove:") }
        val reconnectRegisterIdx = eventLog.indexOfLast { it.startsWith("register:") }
        assertTrue(
            "Reconnect register ($reconnectRegisterIdx) must occur strictly after disconnect remove ($lastRemoveIdx): $eventLog",
            reconnectRegisterIdx > lastRemoveIdx
        )
        controller.shutDown()
    }

    @Test
    fun testDelayedOnAccumulatorsReset_afterFreshPostResetBeatsDoesNotOverwriteValidMetricsWithNull_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val host = FakeHrmHost(context, sensorManager, "Polar RaceReset")
        val baseTs = 3_000_000L
        var nowMs = baseTs + 40_000L
        val controller = PerDeviceHrvController(
            context = context,
            hostDevice = host,
            hostCallbacks = host.callbacks(),
            timeProvider = { nowMs },
            startScheduler = false,
            artifactGateThresholdPercent = 5.0
        )

        // Seed initial beats and register sensors
        for (i in 0 until 40) {
            controller.onRrBeat(RrBeatEvent(baseTs + i * 800L, if (i % 2 == 0) 780.0 else 820.0, i, ProtocolType.BLE))
        }
        controller.calculateMetrics()

        @Suppress("UNCHECKED_CAST")
        val sdnnSensor = host.getSensor(SensorType.HRV_SDNN) as MySensor<Double>
        assertNotNull(sdnnSensor.value)

        val observed = mutableListOf<Any?>()
        sdnnSensor.addSensorListener { observed.add(it) }

        // Deterministically simulate preemption right after onAccumulatorsReset resets state under lock,
        // before it acquires publishLock to push nulls: fresh post-reset beats + calculateMetrics run first!
        controller.onBeforeResetNullPublishHook = {
            val postResetBase = baseTs + 200_000L
            nowMs = postResetBase + 40_000L
            for (i in 0 until 40) {
                controller.onRrBeat(RrBeatEvent(postResetBase + i * 800L, if (i % 2 == 0) 760.0 else 840.0, 100 + i, ProtocolType.BLE))
            }
            controller.calculateMetrics()
        }

        controller.onAccumulatorsReset()

        // Delayed reset thread must NOT overwrite the freshly computed post-reset SDNN with null!
        assertNotNull("Fresh post-reset SDNN must not be overwritten by delayed reset null", sdnnSensor.value)
        assertNotNull("Listener's latest observed value must remain the fresh post-reset metric", observed.last())
        assertTrue("Post-reset SDNN should reflect new +/-40ms variability (~40ms, was ${sdnnSensor.value})", sdnnSensor.value!! > 35.0)

        controller.shutDown()
    }

    @Test
    fun testRacingColdStartOnRrBeatVsOnAccumulatorsReset_neverWedgesSensorsUnregistered_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val baseTs = 4_000_000L
        val nowMs = baseTs + 40_000L

        // Case A: Cold-start onRrBeat enters registration while onAccumulatorsReset races concurrently
        val hostA = FakeHrmHost(context, sensorManager, "Polar ColdResetA")
        val enteredRegister = CountDownLatch(1)
        val allowRegisterFinish = CountDownLatch(1)
        val baseCallbacksA = hostA.callbacks()
        val gatedCallbacksA = object : PerDeviceHrvController.HostCallbacks {
            override fun addAndRegisterSensors(sensors: List<MySensor<*>>) {
                enteredRegister.countDown()
                allowRegisterFinish.await(5, TimeUnit.SECONDS)
                baseCallbacksA.addAndRegisterSensors(sensors)
            }
            override fun unregisterSensor(sensor: MySensor<*>) = baseCallbacksA.unregisterSensor(sensor)
            override fun removeSensor(sensorType: SensorType) = baseCallbacksA.removeSensor(sensorType)
        }
        val controllerA = PerDeviceHrvController(
            context = context,
            hostDevice = hostA,
            hostCallbacks = gatedCallbacksA,
            timeProvider = { nowMs },
            startScheduler = false,
            artifactGateThresholdPercent = 5.0
        )

        val tBeatA = Thread { controllerA.onRrBeat(RrBeatEvent(baseTs, 800.0, 0, ProtocolType.BLE)) }
        tBeatA.start()
        assertTrue(enteredRegister.await(5, TimeUnit.SECONDS))

        val tResetA = Thread { controllerA.onAccumulatorsReset() }
        tResetA.start()
        allowRegisterFinish.countDown()

        tBeatA.join(5_000L)
        tResetA.join(5_000L)

        assertTrue(controllerA.hasRegisteredSensors())
        assertNotNull("HRV_DFA_A1 must remain registered (never wedged)", hostA.getSensor(SensorType.HRV_DFA_A1))
        assertNotNull("HRV_RMSSD must remain registered", hostA.getSensor(SensorType.HRV_RMSSD))
        assertNotNull("HRV_ARTIFACT_RATE must remain registered", hostA.getSensor(SensorType.HRV_ARTIFACT_RATE))
        assertNotNull("HRV_SDNN must remain registered", hostA.getSensor(SensorType.HRV_SDNN))

        // Post-race RR beats must immediately produce valid metrics
        for (i in 1..40) {
            controllerA.onRrBeat(RrBeatEvent(baseTs + i * 800L, if (i % 2 == 0) 780.0 else 820.0, i, ProtocolType.BLE))
        }
        controllerA.calculateMetrics()
        assertNotNull("Post-race HRV_SDNN must compute valid value", hostA.getSensor(SensorType.HRV_SDNN)?.value)
        assertNotNull("Post-race HRV_RMSSD must compute valid value", hostA.getSensor(SensorType.HRV_RMSSD)?.value)
        controllerA.shutDown()

        // Case B: onAccumulatorsReset holds publishLock first while cold-start onRrBeat races concurrently
        val hostB = FakeHrmHost(context, sensorManager, "Polar ColdResetB")
        val enteredReset = CountDownLatch(1)
        val allowResetFinish = CountDownLatch(1)
        val controllerB = PerDeviceHrvController(
            context = context,
            hostDevice = hostB,
            hostCallbacks = hostB.callbacks(),
            timeProvider = { nowMs },
            startScheduler = false,
            artifactGateThresholdPercent = 5.0
        )
        controllerB.onBeforeResetNullPublishHook = {
            enteredReset.countDown()
            allowResetFinish.await(5, TimeUnit.SECONDS)
        }

        val tResetB = Thread { controllerB.onAccumulatorsReset() }
        tResetB.start()
        assertTrue(enteredReset.await(5, TimeUnit.SECONDS))

        val tBeatB = Thread { controllerB.onRrBeat(RrBeatEvent(baseTs, 800.0, 0, ProtocolType.BLE)) }
        tBeatB.start()
        allowResetFinish.countDown()

        tResetB.join(5_000L)
        tBeatB.join(5_000L)

        assertTrue(controllerB.hasRegisteredSensors())
        assertEquals(1, hostB.batchRegisterCalls.get())
        assertNotNull("Case B: HRV_SDNN must be registered", hostB.getSensor(SensorType.HRV_SDNN))
        for (i in 1..40) {
            controllerB.onRrBeat(RrBeatEvent(baseTs + i * 800L, if (i % 2 == 0) 770.0 else 830.0, i, ProtocolType.BLE))
        }
        controllerB.calculateMetrics()
        assertNotNull("Case B: Post-race HRV_SDNN must compute valid value", hostB.getSensor(SensorType.HRV_SDNN)?.value)
        controllerB.shutDown()
    }
}
