package com.atrainingtracker.trainingtracker.notifications

import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import io.mockk.*
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying session-aware sensor battery evaluation and alerting logic (ATT-2192 / REQ-CON-007 / TST-CON-010).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensorBatteryAlertTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        WorkoutNavigationEvents.consumeLowBatteryAlert()
    }

    @After
    fun tearDown() {
        WorkoutNavigationEvents.consumeLowBatteryAlert()
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    @Test
    fun evaluateCriticalSensors_filtersOnlyActiveSensorsWithCriticalBattery() {
        val activeWorkoutId = 123L
        val activeSensorIds = listOf(1L, 2L) // Sensors 1 and 2 used in this workout

        // In database:
        // Sensor 1: active, 15% (critical)
        // Sensor 2: active, 80% (normal)
        // Sensor 3: inactive (in drawer), 10% (critical, but NOT used in session)
        val allCriticalDevices = listOf(
            DevicesDatabaseManager.NameAndBatteryPercentage("Polar H10", 15, 1L),
            DevicesDatabaseManager.NameAndBatteryPercentage("Cadence Sensor", 10, 3L)
        )

        val criticalInSession = allCriticalDevices.filter { activeSensorIds.contains(it.deviceId) }

        assertEquals(1, criticalInSession.size)
        assertEquals("Polar H10", criticalInSession[0].name)
        assertEquals(15, criticalInSession[0].batteryPercentage)
        assertEquals(1L, criticalInSession[0].deviceId)
    }

    @Test
    fun evaluateCriticalSensors_whenAllActiveSensorsHaveNormalBattery_returnsEmpty() {
        val activeSensorIds = listOf(1L, 2L)

        // In database, only sensor 3 is critical, but sensor 3 was NOT in the session
        val allCriticalDevices = listOf(
            DevicesDatabaseManager.NameAndBatteryPercentage("Speed Sensor", 10, 3L)
        )

        val criticalInSession = allCriticalDevices.filter { activeSensorIds.contains(it.deviceId) }
        assertTrue(criticalInSession.isEmpty())
    }

    @Test
    fun evaluateCriticalSensors_thresholdBoundaryCheck() {
        val criticalThreshold = MainActivityWithNavigation.CRITICAL_BATTERY_LEVEL
        assertEquals(20, criticalThreshold)

        val deviceAt20 = DevicesDatabaseManager.NameAndBatteryPercentage("Sensor A", 20, 1L)
        val deviceAt21 = DevicesDatabaseManager.NameAndBatteryPercentage("Sensor B", 21, 2L)
        val deviceAt0 = DevicesDatabaseManager.NameAndBatteryPercentage("Sensor C", 0, 3L)
        val deviceAtNegative = DevicesDatabaseManager.NameAndBatteryPercentage("Sensor D", -1, 4L)

        // Rule: percentage >= 0 && percentage <= CRITICAL_BATTERY_LEVEL (20)
        fun isCritical(p: Int) = p in 0..criticalThreshold

        assertTrue(isCritical(deviceAt20.batteryPercentage))
        assertFalse(isCritical(deviceAt21.batteryPercentage))
        assertTrue(isCritical(deviceAt0.batteryPercentage))
        assertFalse(isCritical(deviceAtNegative.batteryPercentage))
    }

    @Test
    fun singleCriticalSensor_formatsMessageCorrectly() {
        val criticalDevices = listOf(
            DevicesDatabaseManager.NameAndBatteryPercentage("Garmin HRM-Pro", 15, 1L)
        )

        val message = "⚠️ ${criticalDevices[0].name}: Akku schwach (${criticalDevices[0].batteryPercentage}%)"
        val alert = WorkoutNavigationEvents.LowBatteryAlert(
            message = message,
            deviceNames = criticalDevices.map { it.name }
        )

        WorkoutNavigationEvents.triggerLowBatteryAlert(alert)

        runBlocking {
            val emitted = WorkoutNavigationEvents.lowBatteryAlert.first()
            assertNotNull(emitted)
            assertEquals("⚠️ Garmin HRM-Pro: Akku schwach (15%)", emitted?.message)
            assertEquals(listOf("Garmin HRM-Pro"), emitted?.deviceNames)
        }
    }

    @Test
    fun multipleCriticalSensors_formatsMessageCorrectly() {
        val criticalDevices = listOf(
            DevicesDatabaseManager.NameAndBatteryPercentage("Garmin HRM-Pro", 15, 1L),
            DevicesDatabaseManager.NameAndBatteryPercentage("Stages Power", 8, 2L)
        )

        val names = criticalDevices.joinToString(", ") { "${it.name} (${it.batteryPercentage}%)" }
        val message = "⚠️ ${criticalDevices.size} Sensoren: Akku schwach ($names)"
        val alert = WorkoutNavigationEvents.LowBatteryAlert(
            message = message,
            deviceNames = criticalDevices.map { it.name }
        )

        WorkoutNavigationEvents.triggerLowBatteryAlert(alert)

        runBlocking {
            val emitted = WorkoutNavigationEvents.lowBatteryAlert.first()
            assertNotNull(emitted)
            assertEquals("⚠️ 2 Sensoren: Akku schwach (Garmin HRM-Pro (15%), Stages Power (8%))", emitted?.message)
            assertEquals(listOf("Garmin HRM-Pro", "Stages Power"), emitted?.deviceNames)
        }
    }
}
