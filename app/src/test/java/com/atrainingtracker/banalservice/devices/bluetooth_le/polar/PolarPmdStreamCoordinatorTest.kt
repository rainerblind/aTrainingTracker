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

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import com.polar.androidcommunications.api.ble.exceptions.BleControlPointCommandError
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [PolarPmdStreamCoordinator] atomic stream slot guards, job-identity cleanup,
 * bounded PMD `ERROR_ALREADY_IN_STATE` recovery, and multi-device warning ownership
 * (`ATT-9161`, `REQ-CON-053`, `TST-CON-053`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolarPmdStreamCoordinatorTest {

    @Before
    fun setUp() {
        PolarOfflineGattCoordinator.resetForTesting()
        PolarPmdStreamCoordinator.resetWarningsForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineGattCoordinator.resetForTesting()
        PolarPmdStreamCoordinator.resetWarningsForTesting()
    }

    @Test
    fun testLaunchStream_enforcesAtomicSingleFlightAndPreAcquireRollback_TST_CON_053_1() = runTest {
        // REQ-CON-053.1 / TST-CON-053.1
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val coordinator = PolarPmdStreamCoordinator(
            scope = scope,
            deviceId = 1L,
            deviceNameProvider = { "Polar Verity Sense 24825632" },
            polarIdProvider = { "24825632" },
            macAddress = "24:AC:AC:24:82:56",
            isConnectedProvider = { true },
            isShutDownProvider = { false }
        )

        val accStarts = AtomicInteger(0)
        val offlineLocked = AtomicBoolean(true)

        // Pre-acquire failure must roll back CAS so subsequent launch succeeds once lock clears
        assertFalse(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                preAcquire = { !offlineLocked.get() }
            ) { accStarts.incrementAndGet() }
        )
        assertFalse(coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))

        offlineLocked.set(false)
        val gate = CompletableDeferred<Unit>()
        assertTrue(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                preAcquire = { !offlineLocked.get() }
            ) {
                accStarts.incrementAndGet()
                gate.await()
            }
        )
        // Re-entrant calls while suspended must be atomic no-ops
        assertFalse(coordinator.launchStream(PolarPmdStreamCoordinator.StreamSlot.ACC) { accStarts.incrementAndGet() })
        assertFalse(coordinator.launchStream(PolarPmdStreamCoordinator.StreamSlot.ACC) { accStarts.incrementAndGet() })

        testScheduler.runCurrent()
        assertEquals(1, accStarts.get())
        assertTrue(coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))

        gate.complete(Unit)
        testScheduler.runCurrent()
        assertFalse(coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
    }

    @Test
    fun testCancelledJobFinally_neverClobbersNewerSlotOrCleanup_TST_CON_053_1() = runTest {
        // REQ-CON-053.1 / TST-CON-053.1
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val coordinator = PolarPmdStreamCoordinator(
            scope = scope,
            deviceId = 2L,
            deviceNameProvider = { "Polar Verity Sense 24825632" },
            polarIdProvider = { "24825632" },
            macAddress = "24:AC:AC:24:82:56",
            isConnectedProvider = { true },
            isShutDownProvider = { false }
        )

        val cleanupCount = AtomicInteger(0)
        assertTrue(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                onFinalCleanup = { cleanupCount.incrementAndGet() }
            ) {
                awaitCancellation()
            }
        )
        testScheduler.runCurrent()

        // Cancel clears slot ownership immediately; launching replacement before old job's finally finishes
        coordinator.cancelAllStreams()
        assertTrue(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                onFinalCleanup = { cleanupCount.incrementAndGet() }
            ) {
                awaitCancellation()
            }
        )
        testScheduler.runCurrent()

        assertTrue("Newer ACC slot must remain active after cancelled job's finally runs", coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
        assertTrue("Newer ACC job must remain active", coordinator.isJobActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
        assertEquals("Cancelled job must NOT invoke onFinalCleanup for newer slot", 0, cleanupCount.get())

        coordinator.cancelAllStreams()
        testScheduler.runCurrent()
    }

    @Test
    fun testCollectPmdRecovery_handlesTypedCauseMessageAndResetsCounterOnSample_TST_CON_053_2() = runTest {
        // REQ-CON-053.2 / TST-CON-053.2
        assertTrue(
            PolarPmdStreamCoordinator.isPmdAlreadyInStateError(
                BleControlPointCommandError(
                    "pmd cp command REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE",
                    PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
                )
            )
        )
        assertTrue(
            PolarPmdStreamCoordinator.isPmdAlreadyInStateError(
                IllegalStateException(
                    "wrapped",
                    BleControlPointCommandError(
                        "cp error",
                        PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
                    )
                )
            )
        )
        assertTrue(
            PolarPmdStreamCoordinator.isPmdAlreadyInStateError(
                RuntimeException("REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE")
            )
        )
        assertFalse(
            PolarPmdStreamCoordinator.isPmdAlreadyInStateError(
                BleControlPointCommandError(
                    "not supported",
                    PmdControlPointResponse.PmdControlPointResponseCode.ERROR_NOT_SUPPORTED
                )
            )
        )

        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val observedDelays = mutableListOf<Long>()
        val coordinator = PolarPmdStreamCoordinator(
            scope = scope,
            deviceId = 3L,
            deviceNameProvider = { "Polar Verity Sense 24825632" },
            polarIdProvider = { "24825632" },
            macAddress = "24:AC:AC:24:82:56",
            isConnectedProvider = { true },
            isShutDownProvider = { false },
            delayFn = { observedDelays.add(it) }
        )

        val stopCount = AtomicInteger(0)
        val factoryCalls = AtomicInteger(0)
        val delivered = mutableListOf<Int>()

        // Fail 3 times (retries 1, 2, 3), deliver sample 42 (resetting retry counter), then fail 1 more time and deliver 99
        coordinator.collectPmdWithAlreadyInStateRecovery(
            pmdType = PmdMeasurementType.GYRO,
            sessionId = "24825632",
            stopStreaming = { stopCount.incrementAndGet() },
            streamFactory = {
                val call = factoryCalls.incrementAndGet()
                flow {
                    when (call) {
                        1, 2, 3 -> throw BleControlPointCommandError(
                            "pmd cp command REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE",
                            PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
                        )
                        4 -> {
                            emit(42)
                            throw BleControlPointCommandError(
                                "pmd cp command REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE",
                                PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
                            )
                        }
                        else -> emit(99)
                    }
                }
            },
            onSample = { delivered.add(it) }
        )

        assertEquals(listOf(42, 99), delivered)
        assertEquals(4, stopCount.get())
        assertEquals(listOf(500L, 1_000L, 2_000L, 500L), observedDelays)
        assertNull("Recovered stream must not emit blocked warning", PolarPmdStreamCoordinator.streamWarning.value)

        // Non-already-in-state error exits immediately with zero retries and zero warning
        val nonRetryStops = AtomicInteger(0)
        coordinator.collectPmdWithAlreadyInStateRecovery(
            pmdType = PmdMeasurementType.ACC,
            sessionId = "24825632",
            stopStreaming = { nonRetryStops.incrementAndGet() },
            streamFactory = {
                flow<Int> {
                    throw BleControlPointCommandError(
                        "invalid parameter",
                        PmdControlPointResponse.PmdControlPointResponseCode.ERROR_INVALID_PARAMETER
                    )
                }
            },
            onSample = {}
        )
        assertEquals(0, nonRetryStops.get())
        assertNull(PolarPmdStreamCoordinator.streamWarning.value)
    }

    @Test
    fun testExhaustionDeduplicatesWarningAndRespectsMultiDeviceOwnership_TST_CON_053_3() = runTest {
        // REQ-CON-053.3 / TST-CON-053.3
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val coordinatorA = PolarPmdStreamCoordinator(
            scope = scope,
            deviceId = 10L,
            deviceNameProvider = { "Polar Verity Sense 24825632" },
            polarIdProvider = { "24825632" },
            macAddress = "24:AC:AC:24:82:56",
            isConnectedProvider = { true },
            isShutDownProvider = { false },
            delayFn = {}
        )
        val coordinatorB = PolarPmdStreamCoordinator(
            scope = scope,
            deviceId = 11L,
            deviceNameProvider = { "Polar H10 AABBCCDD" },
            polarIdProvider = { "AABBCCDD" },
            macAddress = "AA:BB:CC:DD:EE:FF",
            isConnectedProvider = { true },
            isShutDownProvider = { false },
            delayFn = {}
        )

        val alwaysBlocked: () -> kotlinx.coroutines.flow.Flow<Int> = {
            flow {
                throw BleControlPointCommandError(
                    "pmd cp command REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE",
                    PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
                )
            }
        }

        coordinatorA.collectPmdWithAlreadyInStateRecovery(
            pmdType = PmdMeasurementType.ACC,
            sessionId = "24825632",
            stopStreaming = {},
            streamFactory = alwaysBlocked,
            onSample = {}
        )
        val firstWarning = PolarPmdStreamCoordinator.streamWarning.value
        assertNotNull(firstWarning)
        assertEquals("24825632", firstWarning!!.polarId)
        assertEquals(PmdMeasurementType.ACC, firstWarning.measurementType)
        assertEquals(PolarPmdStreamCoordinator.MAX_ALREADY_IN_STATE_RETRIES, firstWarning.retryCount)

        // Second exhausted stream in same session must be deduplicated (does not overwrite ACC warning)
        coordinatorA.collectPmdWithAlreadyInStateRecovery(
            pmdType = PmdMeasurementType.GYRO,
            sessionId = "24825632",
            stopStreaming = {},
            streamFactory = alwaysBlocked,
            onSample = {}
        )
        assertEquals(PmdMeasurementType.ACC, PolarPmdStreamCoordinator.streamWarning.value?.measurementType)

        // Disconnecting sibling sensor B must NOT clobber sensor A's active warning
        coordinatorB.resetSessionWarningState("AABBCCDD")
        assertNotNull(PolarPmdStreamCoordinator.streamWarning.value)
        assertEquals("24825632", PolarPmdStreamCoordinator.streamWarning.value?.polarId)

        // Disconnecting sensor A clears its warning and re-arms session dedup for next connect
        coordinatorA.resetSessionWarningState("24825632")
        assertNull(PolarPmdStreamCoordinator.streamWarning.value)

        coordinatorA.collectPmdWithAlreadyInStateRecovery(
            pmdType = PmdMeasurementType.PPI,
            sessionId = "24825632",
            stopStreaming = {},
            streamFactory = alwaysBlocked,
            onSample = {}
        )
        assertEquals(PmdMeasurementType.PPI, PolarPmdStreamCoordinator.streamWarning.value?.measurementType)
    }

    @Test
    fun testAtomicSlotCleanupAndPreAcquireException_preservesAccChannelOwnership_TST_CON_053_1() = runTest {
        // REQ-CON-053.1 / TST-CON-053.1
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val polarId = "24825639"
        val coordinator = PolarPmdStreamCoordinator(
            scope = scope,
            deviceId = 12L,
            deviceNameProvider = { "Polar Verity Sense $polarId" },
            polarIdProvider = { polarId },
            macAddress = "24:AC:AC:24:82:59",
            isConnectedProvider = { true },
            isShutDownProvider = { false }
        )

        // 1. Unexpected exception in preAcquire must roll back active CAS cleanly
        var threw = false
        try {
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                preAcquire = { throw IllegalStateException("Transient preAcquire failure") }
            ) {}
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue("preAcquire exception must propagate", threw)
        assertFalse("Slot must NOT stay wedged active after preAcquire throws", coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))

        // 2. Natural completion executes onFinalCleanup strictly BEFORE slot re-opens (isActive == true inside cleanup)
        var slotActiveDuringCleanup = false
        val firstGate = CompletableDeferred<Unit>()
        assertTrue(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                preAcquire = {
                    PolarOfflineGattCoordinator.tryAcquireAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
                },
                onFinalCleanup = {
                    slotActiveDuringCleanup = coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC)
                    PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
                }
            ) {
                firstGate.await()
            }
        )
        testScheduler.runCurrent()
        assertEquals(PolarOfflineGattCoordinator.ACC_OWNER_ONLINE, PolarOfflineGattCoordinator.getAccChannelOwner(polarId))

        firstGate.complete(Unit)
        testScheduler.runCurrent()
        assertTrue("onFinalCleanup must execute under slotLock BEFORE slot flips inactive", slotActiveDuringCleanup)
        assertFalse(coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
        assertNull(PolarOfflineGattCoordinator.getAccChannelOwner(polarId))

        // 3. Re-launching ACC immediately acquires and retains ACC_OWNER_ONLINE across cancelAllStreams atomic cleanup
        val secondGate = CompletableDeferred<Unit>()
        assertTrue(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                preAcquire = {
                    PolarOfflineGattCoordinator.tryAcquireAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
                },
                onFinalCleanup = {
                    PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
                }
            ) {
                secondGate.await()
            }
        )
        testScheduler.runCurrent()
        assertEquals(PolarOfflineGattCoordinator.ACC_OWNER_ONLINE, PolarOfflineGattCoordinator.getAccChannelOwner(polarId))

        // Cancel atomically releases ACC under slotLock, then replacement re-acquires and keeps ACC_OWNER_ONLINE
        coordinator.cancelAllStreams {
            PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        }
        assertNull("cancelAllStreams must release ACC atomically before returning", PolarOfflineGattCoordinator.getAccChannelOwner(polarId))

        assertTrue(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                preAcquire = {
                    PolarOfflineGattCoordinator.tryAcquireAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
                },
                onFinalCleanup = {
                    PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
                }
            ) {
                awaitCancellation()
            }
        )
        testScheduler.runCurrent()
        assertEquals(
            "Replacement ACC launch must retain ACC_OWNER_ONLINE after prior cancelled job finishes",
            PolarOfflineGattCoordinator.ACC_OWNER_ONLINE,
            PolarOfflineGattCoordinator.getAccChannelOwner(polarId)
        )

        coordinator.cancelAllStreams {
            PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        }
        testScheduler.runCurrent()
        assertNull(PolarOfflineGattCoordinator.getAccChannelOwner(polarId))
    }

    // REQ-STB-023.3 / TST-STB-023.3
    @Test
    fun testCollectBounded_dropsOldestFramesWhenConsumerSlow_TST_STB_023_3() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val coordinator = PolarPmdStreamCoordinator(
                scope = scope,
                deviceId = 13L,
                deviceNameProvider = { "Polar H10 AABBCCDD" },
                polarIdProvider = { "AABBCCDD" },
                macAddress = "AA:BB:CC:DD:EE:FF",
                isConnectedProvider = { true },
                isShutDownProvider = { false }
            )

            val capacity = 8
            val totalEmitted = 200
            val firstReceived = CountDownLatch(1)
            val releaseConsumer = CountDownLatch(1)
            val collected = java.util.Collections.synchronizedList(mutableListOf<Int>())

            val source = flow {
                for (i in 1..totalEmitted) {
                    emit(i)
                }
            }

            val job = launch(Dispatchers.Default) {
                coordinator.collectBounded(source, capacity = capacity) { value ->
                    collected.add(value)
                    if (value == 1) {
                        firstReceived.countDown()
                        releaseConsumer.await(5L, TimeUnit.SECONDS)
                    }
                }
            }

            assertTrue("First frame must reach collector", firstReceived.await(5L, TimeUnit.SECONDS))
            // Allow producer coroutine to flood and overflow bounded DROP_OLDEST buffer before releasing consumer
            delay(50L)
            releaseConsumer.countDown()
            job.join()

            assertTrue(
                "DROP_OLDEST must drop intermediate frames under consumer stall (collected=${collected.size})",
                collected.size < totalEmitted
            )
            assertEquals("Newest emitted frame must be preserved at tail", totalEmitted, collected.last())
            assertTrue(
                "Retained post-stall batch must stay bounded by capacity + in-flight element",
                collected.size <= capacity + 2
            )
        } finally {
            scope.cancel()
        }
    }

    // REQ-SET-122.2 / TST-SET-124.2
    @Test
    fun testRenegotiateSlot_siblingCoroutineSurvivesCollectorSelfTriggerReleasesAccLeaseAndDedupes_TST_SET_124_2() = runTest {
        val polarId = "99887766"
        val coordinator = PolarPmdStreamCoordinator(
            scope = backgroundScope,
            deviceId = 14L,
            deviceNameProvider = { "Polar H10 $polarId" },
            polarIdProvider = { polarId },
            macAddress = "AA:BB:CC:DD:EE:14",
            isConnectedProvider = { true },
            isShutDownProvider = { false }
        )

        val stopCalls = AtomicInteger(0)
        val beforeRelaunchCalls = AtomicInteger(0)
        val relaunchCalls = AtomicInteger(0)
        val firstTriggerResult = AtomicBoolean(false)
        val duplicateTriggerResult = AtomicBoolean(true)
        val leaseReleasedBeforeRelaunch = AtomicBoolean(false)

        fun launchAccSlot(triggerSelfRenegotiation: Boolean): Boolean = coordinator.launchStream(
            slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
            preAcquire = {
                PolarOfflineGattCoordinator.tryAcquireAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            },
            onFinalCleanup = {
                PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            }
        ) {
            if (triggerSelfRenegotiation) {
                val r1 = coordinator.renegotiateSlot(
                    slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                    onOldSlotCancelled = {
                        PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
                    },
                    stopStreaming = {
                        leaseReleasedBeforeRelaunch.set(PolarOfflineGattCoordinator.getAccChannelOwner(polarId) == null)
                        stopCalls.incrementAndGet()
                    },
                    beforeRelaunch = { beforeRelaunchCalls.incrementAndGet() },
                    relaunch = {
                        relaunchCalls.incrementAndGet()
                        assertTrue("Replacement ACC launch must re-acquire ACC_OWNER_ONLINE", launchAccSlot(false))
                    }
                )
                val r2 = coordinator.renegotiateSlot(
                    slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                    stopStreaming = { stopCalls.incrementAndGet() },
                    relaunch = { relaunchCalls.incrementAndGet() }
                )
                firstTriggerResult.set(r1)
                duplicateTriggerResult.set(r2)
            }
            awaitCancellation()
        }

        assertTrue(launchAccSlot(triggerSelfRenegotiation = true))
        testScheduler.runCurrent()

        assertTrue("First renegotiation trigger inside ACC collector must succeed", firstTriggerResult.get())
        assertFalse("Concurrent duplicate renegotiation trigger must be rejected (single-flight)", duplicateTriggerResult.get())
        assertTrue("onOldSlotCancelled must release ACC_OWNER_ONLINE before stopStreaming/relaunch", leaseReleasedBeforeRelaunch.get())
        assertEquals("stopStreaming must run exactly once", 1, stopCalls.get())
        assertEquals("beforeRelaunch must run exactly once", 1, beforeRelaunchCalls.get())
        assertEquals("relaunch must run exactly once without self-cancellation", 1, relaunchCalls.get())
        assertTrue("Replacement ACC stream must remain active", coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
        assertFalse("Renegotiating flag must clear after relaunch", coordinator.isRenegotiating(PolarPmdStreamCoordinator.StreamSlot.ACC))
        assertEquals(
            "Replacement ACC stream must retain ACC_OWNER_ONLINE",
            PolarOfflineGattCoordinator.ACC_OWNER_ONLINE,
            PolarOfflineGattCoordinator.getAccChannelOwner(polarId)
        )

        coordinator.cancelAllStreams {
            PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        }
        testScheduler.runCurrent()
        assertNull(PolarOfflineGattCoordinator.getAccChannelOwner(polarId))
    }

    // REQ-SET-122.2 / TST-SET-124.2
    @Test
    fun testRenegotiateSlot_cancelledByCancelAllRenegotiationsAndCancelAllStreamsPreventsLateRelaunch_TST_SET_124_2() = runTest {
        val polarId = "99887767"
        val coordinator = PolarPmdStreamCoordinator(
            scope = backgroundScope,
            deviceId = 15L,
            deviceNameProvider = { "Polar H10 $polarId" },
            polarIdProvider = { polarId },
            macAddress = "AA:BB:CC:DD:EE:15",
            isConnectedProvider = { true },
            isShutDownProvider = { false }
        )
        val relaunchCalls = AtomicInteger(0)

        // 1. cancelAllRenegotiations() cancels pending renegotiation before relaunch executes
        assertTrue(
            coordinator.renegotiateSlot(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                stopStreaming = {},
                relaunch = { relaunchCalls.incrementAndGet() }
            )
        )
        assertTrue(coordinator.isRenegotiating(PolarPmdStreamCoordinator.StreamSlot.ACC))
        coordinator.cancelAllRenegotiations()
        assertFalse(coordinator.isRenegotiating(PolarPmdStreamCoordinator.StreamSlot.ACC))
        testScheduler.runCurrent()
        assertEquals("Cancelled renegotiation must never invoke relaunch", 0, relaunchCalls.get())

        // 2. cancelAllStreams() cancels pending renegotiation and active streams atomically
        assertTrue(
            coordinator.renegotiateSlot(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                stopStreaming = {},
                relaunch = { relaunchCalls.incrementAndGet() }
            )
        )
        assertTrue(coordinator.isRenegotiating(PolarPmdStreamCoordinator.StreamSlot.ACC))
        coordinator.cancelAllStreams()
        assertFalse(coordinator.isRenegotiating(PolarPmdStreamCoordinator.StreamSlot.ACC))
        testScheduler.runCurrent()
        assertEquals("cancelAllStreams must prevent late renegotiation relaunch", 0, relaunchCalls.get())
    }

    // REQ-SET-122.2 / TST-SET-124.2
    @Test
    fun testRenegotiateSlot_atomicSlotLockHandoffAndOnAbortedOnCancelOrYield_TST_SET_124_2() = runBlocking {
        val polarId = "99887768"
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val allowed = AtomicBoolean(true)
            val coordinator = PolarPmdStreamCoordinator(
                scope = scope,
                deviceId = 16L,
                deviceNameProvider = { "Polar H10 $polarId" },
                polarIdProvider = { polarId },
                macAddress = "AA:BB:CC:DD:EE:16",
                isConnectedProvider = { true },
                isShutDownProvider = { false }
            )

            // Part A: Yield/disallowed arriving mid-stopStreaming invokes onAborted without relaunch
            val abortedCalls = AtomicInteger(0)
            val relaunchCalls = AtomicInteger(0)
            val abortedDone = CountDownLatch(1)
            assertTrue(
                coordinator.renegotiateSlot(
                    slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                    isAllowedToRelaunch = { allowed.get() },
                    stopStreaming = { allowed.set(false) },
                    onAborted = {
                        abortedCalls.incrementAndGet()
                        abortedDone.countDown()
                    },
                    relaunch = { relaunchCalls.incrementAndGet() }
                )
            )
            assertTrue("onAborted must fire when stopStreaming flips allowed=false", abortedDone.await(5L, TimeUnit.SECONDS))
            assertEquals("Aborted renegotiation must not relaunch", 0, relaunchCalls.get())
            assertEquals("onAborted must be invoked exactly once", 1, abortedCalls.get())
            assertFalse(coordinator.isRenegotiating(PolarPmdStreamCoordinator.StreamSlot.ACC))

            // Part B: cancelAllRenegotiations() returns cancelled slot set and fires onAborted for active coroutine
            allowed.set(true)
            val inStop = CountDownLatch(1)
            val releaseStop = CountDownLatch(1)
            val abortedOnCancel = CountDownLatch(1)
            assertTrue(
                coordinator.renegotiateSlot(
                    slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                    isAllowedToRelaunch = { allowed.get() },
                    stopStreaming = {
                        inStop.countDown()
                        releaseStop.await(5L, TimeUnit.SECONDS)
                    },
                    onAborted = {
                        abortedCalls.incrementAndGet()
                        abortedOnCancel.countDown()
                    },
                    relaunch = { relaunchCalls.incrementAndGet() }
                )
            )
            assertTrue(inStop.await(5L, TimeUnit.SECONDS))
            val cancelledSlots = coordinator.cancelAllRenegotiations()
            assertEquals(setOf(PolarPmdStreamCoordinator.StreamSlot.ACC), cancelledSlots)
            releaseStop.countDown()
            assertTrue("Cancelled in-flight renegotiation must invoke onAborted", abortedOnCancel.await(5L, TimeUnit.SECONDS))
            assertEquals("Cancelled in-flight renegotiation must not relaunch", 0, relaunchCalls.get())
            assertEquals(2, abortedCalls.get())

            // Part C: Atomic slotLock handoff — concurrent cancelAllStreams() blocks while relaunch() registers replacement ACC job and then cancels it cleanly (zero TOCTOU)
            val enteredRelaunch = CountDownLatch(1)
            val cancelThreadStarted = CountDownLatch(1)
            val cancelFinished = CountDownLatch(1)
            val replacementCancelled = CountDownLatch(1)

            assertTrue(
                coordinator.renegotiateSlot(
                    slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                    isAllowedToRelaunch = { true },
                    stopStreaming = {},
                    onAborted = { abortedCalls.incrementAndGet() },
                    relaunch = {
                        enteredRelaunch.countDown()
                        // Wait briefly so concurrent cancelAllStreams() blocks on slotLock before launchStream registers replacement job
                        cancelThreadStarted.await(5L, TimeUnit.SECONDS)
                        Thread.sleep(30L)
                        assertEquals("cancelAllStreams must block on slotLock until relaunch completes", 1L, cancelFinished.count)
                        assertTrue(
                            coordinator.launchStream(slot = PolarPmdStreamCoordinator.StreamSlot.ACC) {
                                try {
                                    awaitCancellation()
                                } finally {
                                    replacementCancelled.countDown()
                                }
                            }
                        )
                    }
                )
            )
            assertTrue(enteredRelaunch.await(5L, TimeUnit.SECONDS))
            val cancelThread = Thread {
                cancelThreadStarted.countDown()
                coordinator.cancelAllStreams()
                cancelFinished.countDown()
            }.apply { start() }
            assertTrue("cancelAllStreams must complete after relaunch exits slotLock", cancelFinished.await(5L, TimeUnit.SECONDS))
            cancelThread.join(5_000L)
            assertFalse("Atomic slotLock handoff guarantees cancelAllStreams cancels replacement ACC slot", coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
            assertFalse(coordinator.isJobActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
            assertEquals("Successful relaunch must not invoke onAborted", 2, abortedCalls.get())
        } finally {
            scope.cancel()
        }
    }

    // REQ-SET-122.2 / TST-SET-124.2
    @Test
    fun testLaunchStream_doesNotSpeculativelyActivateSlotDuringPreAcquire_TST_SET_124_2() = runTest {
        val coordinator = PolarPmdStreamCoordinator(
            scope = backgroundScope,
            deviceId = 17L,
            deviceNameProvider = { "Polar H10 99887769" },
            polarIdProvider = { "99887769" },
            macAddress = "AA:BB:CC:DD:EE:17",
            isConnectedProvider = { true },
            isShutDownProvider = { false }
        )

        // 1. Rejected preAcquire must observe isActive == false during and after invocation
        val activeDuringReject = AtomicBoolean(true)
        assertFalse(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                preAcquire = {
                    activeDuringReject.set(coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
                    false
                }
            ) {
                awaitCancellation()
            }
        )
        assertFalse("isActive(ACC) must stay false inside rejected preAcquire", activeDuringReject.get())
        assertFalse("isActive(ACC) must stay false after rejected preAcquire", coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))

        // 2. Accepted preAcquire observes isActive == false inside preAcquire and flips true only once acquired
        val activeDuringAccept = AtomicBoolean(true)
        assertTrue(
            coordinator.launchStream(
                slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
                preAcquire = {
                    activeDuringAccept.set(coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
                    true
                }
            ) {
                awaitCancellation()
            }
        )
        assertFalse("isActive(ACC) must stay false until preAcquire succeeds", activeDuringAccept.get())
        assertTrue("isActive(ACC) must be true immediately after preAcquire succeeds", coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))

        coordinator.cancelAllStreams()
        assertFalse(coordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC))
    }
}
