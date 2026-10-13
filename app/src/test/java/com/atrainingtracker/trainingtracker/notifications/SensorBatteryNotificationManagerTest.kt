package com.atrainingtracker.trainingtracker.notifications

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [SensorBatteryNotificationManager] behavior (ATT-2192 / REQ-CON-007 / TST-CON-010).
 */
class SensorBatteryNotificationManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
    private val capturedNotifications = mutableListOf<Notification>()

    private lateinit var manager: SensorBatteryNotificationManager

    @Before
    fun setUp() {
        clearAllMocks()
        every { notificationManager.areNotificationsEnabled() } returns true
        every { context.packageName } returns "com.atrainingtracker"
        every { context.applicationContext } returns context
        every { context.getString(R.string.sensor_battery_notification_title_1) } returns "Sensor battery low"
        every { context.getString(R.string.sensor_battery_notification_title_many) } returns "Low sensor batteries"

        manager = SensorBatteryNotificationManager(
            context = context,
            notificationManager = notificationManager
        )
    }

    @Test
    fun showLowBatteryNotification_emptyList_doesNotPostNotification() {
        manager.showLowBatteryNotification(emptyList())

        verify(exactly = 0) {
            notificationManager.notify(any(), any())
        }
    }

    @Test
    fun showLowBatteryNotification_singleSensor_postsNotificationWithCorrectIdAndContent() {
        val device = DevicesDatabaseManager.NameAndBatteryPercentage("Garmin HRM-Pro", 15, 1L)

        val mockNotification = mockk<Notification>(relaxed = true)
        var capturedBuilder: NotificationCompat.Builder? = null
        manager.notificationFactory = { builder ->
            capturedBuilder = builder
            mockNotification
        }

        manager.showLowBatteryNotification(listOf(device))

        verify(exactly = 1) {
            notificationManager.notify(
                SensorBatteryNotificationManager.NOTIFICATION_ID_SENSOR_BATTERY,
                mockNotification
            )
        }
        assertTrue(capturedBuilder != null)
    }

    @Test
    fun showLowBatteryNotification_multipleSensors_postsGroupNotification() {
        val devices = listOf(
            DevicesDatabaseManager.NameAndBatteryPercentage("Garmin HRM-Pro", 15, 1L),
            DevicesDatabaseManager.NameAndBatteryPercentage("Stages Power", 8, 2L)
        )

        val mockNotification = mockk<Notification>(relaxed = true)
        manager.notificationFactory = { mockNotification }

        manager.showLowBatteryNotification(devices)

        verify(exactly = 1) {
            notificationManager.notify(
                SensorBatteryNotificationManager.NOTIFICATION_ID_SENSOR_BATTERY,
                mockNotification
            )
        }
    }
}
