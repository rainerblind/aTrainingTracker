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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.tpms.DualWheelTpmsEstimator
import com.atrainingtracker.banalservice.tpms.IndirectTpmsEstimator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Field

/**
 * Unit and contract tests for multi-sensor iTPMS telemetry routing in [BANALServiceRepository]
 * (REQ-SEN-030.2, TST-SEN-030.2, ATT-7753).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BANALServiceRepositoryMultiWheelTpmsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var repository: BANALServiceRepository

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found: $relativePath")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        resetSingleton()
        repository = BANALServiceRepository.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        EquipmentRepository.resetForTesting(null)
        BANALServiceRepository.resetForTesting(null)
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun resetSingleton() {
        try {
            val field: Field = BANALServiceRepository::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
        }
    }

    @Test
    fun testMultiWheelTelemetryRouting_independentEstimators() {
        // Assert repository provides independent per-device estimators and dual-wheel ratio estimator
        assertNotNull(repository.itpmsEstimator)
        assertNotNull(repository.perDeviceItpmsEstimators)
        assertNotNull(repository.dualWheelTpmsEstimator)

        // Simulate two active wheel sensors (Front = 101, Rear = 102)
        val dev1Estimator = repository.perDeviceItpmsEstimators.getOrPut(101L) {
            IndirectTpmsEstimator(baselineCircumferenceMeters = 2.105)
        }
        val dev2Estimator = repository.perDeviceItpmsEstimators.getOrPut(102L) {
            IndirectTpmsEstimator(baselineCircumferenceMeters = 2.102)
        }

        assertEquals(2, repository.perDeviceItpmsEstimators.size)
        assertTrue(repository.perDeviceItpmsEstimators.containsKey(101L))
        assertTrue(repository.perDeviceItpmsEstimators.containsKey(102L))

        // Ingest telemetry into dualWheelTpmsEstimator
        val alert = repository.dualWheelTpmsEstimator.processStep(
            deltaRevsFront = 1.0,
            deltaRevsRear = 1.0,
            speedMps = 5.0,
            yawRateRadPerSec = 0.0,
            stepDistanceMeters = 2.0
        )
        assertEquals(null, alert)

        // Clear breadcrumbs must reset all estimators cleanly
        repository.clearBreadcrumbs()
        assertEquals(1.0, repository.dualWheelTpmsEstimator.startRatio, 0.001)
        assertEquals(false, repository.dualWheelTpmsEstimator.isStartLocked)
    }

    @Test
    fun testRepositorySourceContract_multiWheelTelemetryLoop() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt")
        val content = file.readText()

        assertTrue(
            "Repository must maintain perDeviceItpmsEstimators map",
            content.contains("perDeviceItpmsEstimators")
        )
        assertTrue(
            "Repository must maintain dualWheelTpmsEstimator instance",
            content.contains("val dualWheelTpmsEstimator = DualWheelTpmsEstimator()")
        )
        assertTrue(
            "Repository must route active wheel devices to perDeviceItpmsEstimators",
            content.contains("perDeviceItpmsEstimators.getOrPut(devId)")
        )
        assertTrue(
            "Repository must dispatch dual wheel ratio telemetry when >= 2 wheel sensors present",
            content.contains("dualWheelTpmsEstimator.processTelemetry")
        )
    }

    @Test
    fun testRepositorySourceContract_zeroMainThreadQueriesAndCaching() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt")
        val content = file.readText()

        // Finding 1: Must NOT call uncached getWheelPositionForDevice in 1-Hz loop
        org.junit.Assert.assertFalse(
            "Repository must NOT execute uncached SQLite query eqRepo.getWheelPositionForDevice in 1-Hz loop",
            content.contains("eqRepo.getWheelPositionForDevice")
        )
        // Finding 1 & 2: In-memory cache structures and methods
        assertTrue(
            "Repository must maintain cachedActiveWheelPositions map",
            content.contains("cachedActiveWheelPositions")
        )
        assertTrue(
            "Repository must maintain cachedScopedWheelPositions map",
            content.contains("cachedScopedWheelPositions")
        )
        assertTrue(
            "Repository must provide refreshWheelPositionsCache method",
            content.contains("fun refreshWheelPositionsCache()")
        )
        assertTrue(
            "Repository must provide setCachedWheelPosition method",
            content.contains("fun setCachedWheelPosition(")
        )
        // Finding 4: Post-workout persistence wiring
        assertTrue(
            "Repository must provide persistConvergedCircumferences method",
            content.contains("fun persistConvergedCircumferences(")
        )
    }

    @Test
    fun testPersistConvergedCircumferences_savesLockedEstimatorData() {
        val mockEqRepo = mockk<EquipmentRepository>(relaxed = true)
        every { mockEqRepo.getDeviceWheelPositionsForEquipment(any()) } returns mapOf(
            201L to com.atrainingtracker.trainingtracker.database.WheelPosition.FRONT,
            202L to com.atrainingtracker.trainingtracker.database.WheelPosition.REAR
        )
        every { mockEqRepo.addCircumferenceHistory(any(), any(), any(), any(), any()) } returns 101L
        EquipmentRepository.resetForTesting(mockEqRepo)

        repository.setCachedWheelPosition(201L, com.atrainingtracker.trainingtracker.database.WheelPosition.FRONT, 77L)
        repository.setCachedWheelPosition(202L, com.atrainingtracker.trainingtracker.database.WheelPosition.REAR, 77L)

        val estimator1 = repository.perDeviceItpmsEstimators.getOrPut(201L) { IndirectTpmsEstimator() }
        val estimator2 = repository.perDeviceItpmsEstimators.getOrPut(202L) { IndirectTpmsEstimator() }

        // Lock start baseline on estimator 1 (2.105m -> 2105mm)
        estimator1.onGatedStep(2050.0, 2050.0 / 2.105)
        assertTrue(estimator1.isStartLocked)

        // Estimator 2 is not locked yet
        org.junit.Assert.assertFalse(estimator2.isStartLocked)

        val inserted = repository.persistConvergedCircumferences(equipmentId = 77L, timestampMs = 123456789L)
        assertEquals(1, inserted.size)
        assertEquals(101L, inserted[0])

        io.mockk.verify {
            mockEqRepo.addCircumferenceHistory(
                equipmentId = 77L,
                timestampMs = 123456789L,
                wheelPosition = com.atrainingtracker.trainingtracker.database.WheelPosition.FRONT,
                circumferenceMm = 2105,
                source = com.atrainingtracker.trainingtracker.database.WheelCircumferenceRecord.SOURCE_GNSS_AUTO
            )
        }
    }
}
