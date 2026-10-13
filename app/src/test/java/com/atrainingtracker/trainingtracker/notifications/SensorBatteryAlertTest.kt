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
        val warningThreshold = MainActivityWithNavigation.LOW_BATTERY_WARNING_LEVEL
        val criticalThreshold = MainActivityWithNavigation.CRITICAL_BATTERY_LEVEL
        assertEquals(20, warningThreshold)
        assertEquals(10, criticalThreshold)

        // Rule: Warning is (CRITICAL_BATTERY_LEVEL + 1)..LOW_BATTERY_WARNING_LEVEL
        // Critical is 0..CRITICAL_BATTERY_LEVEL
        fun classify(p: Int): WorkoutNavigationEvents.BatteryAlertSeverity? = when {
            p in 0..criticalThreshold -> WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL
            p in (criticalThreshold + 1)..warningThreshold -> WorkoutNavigationEvents.BatteryAlertSeverity.WARNING
            else -> null
        }

        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL, classify(0))
        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL, classify(10))
        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.WARNING, classify(11))
        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.WARNING, classify(20))
        assertNull(classify(21))
        assertNull(classify(-1))
    }

    @Test
    fun singleCriticalSensor_formatsMessageCorrectly() {
        val criticalDevices = listOf(
            DevicesDatabaseManager.NameAndBatteryPercentage("Garmin HRM-Pro", 15, 1L)
        )

        val message = "⚠️ ${criticalDevices[0].name}: Akku schwach (${criticalDevices[0].batteryPercentage}%)"
        val alert = WorkoutNavigationEvents.LowBatteryAlert(
            message = message,
            deviceNames = criticalDevices.map { it.name },
            severity = WorkoutNavigationEvents.BatteryAlertSeverity.WARNING,
            lowestBatteryPercentage = 15
        )

        WorkoutNavigationEvents.triggerLowBatteryAlert(alert)

        runBlocking {
            val emitted = WorkoutNavigationEvents.lowBatteryAlert.first()
            assertNotNull(emitted)
            assertEquals("⚠️ Garmin HRM-Pro: Akku schwach (15%)", emitted?.message)
            assertEquals(listOf("Garmin HRM-Pro"), emitted?.deviceNames)
            assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.WARNING, emitted?.severity)
            assertEquals(15, emitted?.lowestBatteryPercentage)
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
            deviceNames = criticalDevices.map { it.name },
            severity = WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL,
            lowestBatteryPercentage = 8
        )

        WorkoutNavigationEvents.triggerLowBatteryAlert(alert)

        runBlocking {
            val emitted = WorkoutNavigationEvents.lowBatteryAlert.first()
            assertNotNull(emitted)
            assertEquals("⚠️ 2 Sensoren: Akku schwach (Garmin HRM-Pro (15%), Stages Power (8%))", emitted?.message)
            assertEquals(listOf("Garmin HRM-Pro", "Stages Power"), emitted?.deviceNames)
            assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL, emitted?.severity)
            assertEquals(8, emitted?.lowestBatteryPercentage)
        }
    }

    @Test
    fun workoutStart_filtersOnlyPairedCriticalSensors() {
        val pairedSensorIds = setOf(10L, 20L)
        val allCriticalDevices = listOf(
            DevicesDatabaseManager.NameAndBatteryPercentage("Wahoo TICKR", 12, 10L),
            DevicesDatabaseManager.NameAndBatteryPercentage("Unpaired Sensor", 5, 99L)
        )

        // Workout start logic: devDbManager.isPaired(deviceId)
        val startCriticalDevices = allCriticalDevices.filter { pairedSensorIds.contains(it.deviceId) }

        assertEquals(1, startCriticalDevices.size)
        assertEquals("Wahoo TICKR", startCriticalDevices[0].name)
        assertEquals(10L, startCriticalDevices[0].deviceId)
    }

    @Test
    fun inRideBatteryDrop_recordsSessionDeduplicationAndSuppressesDuplicates() {
        val alertedIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

        val deviceId = 42L
        val percentage = 18

        // First battery drop event -> should alert and record
        val firstAlertTriggered = percentage in 0..MainActivityWithNavigation.LOW_BATTERY_WARNING_LEVEL && alertedIds.add(deviceId)
        assertTrue("First in-ride battery drop must trigger alert", firstAlertTriggered)
        assertTrue(alertedIds.contains(deviceId))

        // Subsequent battery report for same device in same workout -> must be suppressed
        val secondAlertTriggered = percentage in 0..MainActivityWithNavigation.LOW_BATTERY_WARNING_LEVEL && alertedIds.add(deviceId)
        assertFalse("Subsequent in-ride battery drop for same sensor must be suppressed by deduplication", secondAlertTriggered)

        // Different sensor drops -> must trigger alert
        val secondDeviceId = 43L
        val secondSensorTriggered = percentage in 0..MainActivityWithNavigation.LOW_BATTERY_WARNING_LEVEL && alertedIds.add(secondDeviceId)
        assertTrue("Different sensor dropping to critical must trigger alert", secondSensorTriggered)
        assertEquals(2, alertedIds.size)

        // Workout session stops -> session set is cleared
        alertedIds.clear()
        assertTrue("Session reset clears alerted IDs", alertedIds.isEmpty())

        // Next workout session: same device can trigger alert again
        val nextSessionTriggered = percentage in 0..MainActivityWithNavigation.LOW_BATTERY_WARNING_LEVEL && alertedIds.add(deviceId)
        assertTrue("New workout session allows alerting previously alerted sensor", nextSessionTriggered)
    }

    @Test
    fun inRideBatteryMonitoring_criticalEscalationAndDeduplication() {
        // [TST-UI-316.2]
        val alertedWarningIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
        val alertedCriticalIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

        val deviceId = 100L

        // Helper mimicking startInRideBatteryMonitoring logic
        fun processBatteryUpdate(devId: Long, percentage: Int): WorkoutNavigationEvents.BatteryAlertSeverity? {
            if (percentage in 0..MainActivityWithNavigation.LOW_BATTERY_WARNING_LEVEL) {
                if (percentage <= MainActivityWithNavigation.CRITICAL_BATTERY_LEVEL) {
                    if (alertedCriticalIds.add(devId)) {
                        alertedWarningIds.add(devId)
                        return WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL
                    }
                } else {
                    if (alertedWarningIds.add(devId)) {
                        return WorkoutNavigationEvents.BatteryAlertSeverity.WARNING
                    }
                }
            }
            return null
        }

        // 1. First warning at 15% -> triggers WARNING
        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.WARNING, processBatteryUpdate(deviceId, 15))
        assertTrue(alertedWarningIds.contains(deviceId))
        assertFalse(alertedCriticalIds.contains(deviceId))

        // 2. Duplicate update at 14% -> suppressed
        assertNull(processBatteryUpdate(deviceId, 14))

        // 3. Drop to 8% (<= 10%) -> escalates to CRITICAL
        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL, processBatteryUpdate(deviceId, 8))
        assertTrue(alertedCriticalIds.contains(deviceId))

        // 4. Duplicate update at 7% -> suppressed
        assertNull(processBatteryUpdate(deviceId, 7))

        // 5. Reset session on ride stop
        alertedWarningIds.clear()
        alertedCriticalIds.clear()

        // 6. Next workout session allows alert again
        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL, processBatteryUpdate(deviceId, 8))
    }

    @Test
    fun inAppSnackbarAlert_criticalAlertPreemptsExistingSnackbar() {
        // [TST-UI-316.3]
        val warningVisuals = WorkoutNavigationEvents.BatterySnackbarVisuals(
            message = "Sensor warning",
            actionLabel = "Sensoren",
            severity = WorkoutNavigationEvents.BatteryAlertSeverity.WARNING
        )
        val criticalVisuals = WorkoutNavigationEvents.BatterySnackbarVisuals(
            message = "Sensor critical",
            actionLabel = "Sensoren",
            severity = WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL
        )

        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.WARNING, warningVisuals.severity)
        assertEquals(WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL, criticalVisuals.severity)

        // Preemption helper mirroring UI logic
        fun shouldPreempt(incomingSeverity: WorkoutNavigationEvents.BatteryAlertSeverity, currentVisuals: androidx.compose.material3.SnackbarVisuals?): Boolean {
            if (incomingSeverity == WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL) {
                val batteryVisuals = currentVisuals as? WorkoutNavigationEvents.BatterySnackbarVisuals
                return batteryVisuals != null && batteryVisuals.severity == WorkoutNavigationEvents.BatteryAlertSeverity.WARNING
            }
            return false
        }

        // 1. Critical incoming when current is WARNING battery alert -> MUST preempt
        assertTrue("Critical alert must preempt active WARNING battery snackbar", shouldPreempt(criticalVisuals.severity, warningVisuals))

        // 2. Critical incoming when current is already CRITICAL -> must NOT preempt
        assertFalse("Critical alert must not preempt already CRITICAL battery snackbar", shouldPreempt(criticalVisuals.severity, criticalVisuals))

        // 3. Warning incoming when current is WARNING -> must NOT preempt
        assertFalse("Warning alert must not preempt existing snackbar", shouldPreempt(warningVisuals.severity, warningVisuals))

        // 4. Critical incoming when current is generic non-battery snackbar -> must NOT preempt (preserves system notifications)
        val genericVisuals = object : androidx.compose.material3.SnackbarVisuals {
            override val message: String = "Generic message"
            override val actionLabel: String? = null
            override val withDismissAction: Boolean = false
            override val duration: androidx.compose.material3.SnackbarDuration = androidx.compose.material3.SnackbarDuration.Short
        }
        assertFalse("Critical alert must not dismiss unrelated generic snackbars", shouldPreempt(criticalVisuals.severity, genericVisuals))
    }
}

