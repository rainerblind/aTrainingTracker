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

package com.atrainingtracker.banalservice

import android.content.Intent
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.odometry.MultisportTelemetryAnchor
import com.atrainingtracker.banalservice.odometry.MultisportTransitionBridge
import com.atrainingtracker.banalservice.odometry.MultisportTransitionEvent
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests for `BANALService` multisport sport precedence, anti-circular
 * `getAuthoritativeBSportType()`, `mActivityTypeMax` reset, `advanceLapsForMultisport()`,
 * and lifecycle cleanup (`ATT-8917`, `REQ-FIL-031.2`, `TST-FIL-031.2`).
 */
// Linked test specs: TST-FIL-031
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BANALServiceMultisportTransitionTest {

    @After
    fun tearDown() {
        BANALService.setServiceInstanceForTesting(null)
        BANALService.setServiceContextForTesting(null)
        BANALService.setSensorManagerForTesting(null)
        setStaticDeviceManager(null)
        unmockkAll()
    }

    private fun sampleBikeToRunEvent(): MultisportTransitionEvent = MultisportTransitionEvent(
        fromSport = BSportType.BIKE,
        toSport = BSportType.RUN,
        exitAnchor = MultisportTelemetryAnchor(1_760_001_800_000L, 1800, 1800, 14400.0),
        enterAnchor = MultisportTelemetryAnchor(1_760_001_850_000L, 1850, 1850, 14450.0),
        confirmAnchor = MultisportTelemetryAnchor(1_760_001_865_000L, 1865, 1865, 14510.0)
    )

    @Test
    fun confirmedTransition_overridesConflictAndSilentBike_resetsActivityTypeMax_andBroadcastsDedicatedIntent() {
        val controller = Robolectric.buildService(BANALService::class.java).create()
        val service = controller.get()
        try {
            // Simulate changeSportWhenDeviceGetsLost = false (latches mActivityTypeMax to BIKE)
            PreferenceManager.getDefaultSharedPreferences(service).edit()
                .putBoolean("changeSportWhenDeviceGetsLost", false)
                .commit()

            val dmMock = mockk<DeviceManager>(relaxed = true)
            every { dmMock.sportType } returns BSportType.BIKE
            setStaticDeviceManager(dmMock)

            assertEquals(BSportType.BIKE, service.bSportType)
            assertEquals(ActivityType.BIKE_SPEED_AND_CADENCE, service.activityType)

            shadowOf(service).clearBroadcastIntents()

            // Confirm BIKE -> RUN transition while parked bike sensor still reports BIKE
            service.onConfirmedMultisportTransition(sampleBikeToRunEvent())

            // Full getBSportType() switches to RUN while getAuthoritativeBSportType() stays Tier 1/2 (BIKE)
            assertEquals(BSportType.RUN, service.bSportType)
            assertEquals(BSportType.BIKE, service.authoritativeBSportType)
            // mActivityTypeMax was reset so cockpit switches down to RUN_SPEED_AND_CADENCE!
            assertEquals(ActivityType.RUN_SPEED_AND_CADENCE, service.activityType)

            // Also overrides Tier 2 CONFLICT cleanly
            every { dmMock.sportType } returns BSportType.CONFLICT
            assertEquals(BSportType.RUN, service.bSportType)

            // Verify dedicated MULTISPORT_TRANSITION_INTENT broadcast (no USER_CHANGED or LAP_SUMMARY)
            val broadcasts = shadowOf(service).broadcastIntents
            val msBroadcast = broadcasts.firstOrNull { it.action == BANALService.MULTISPORT_TRANSITION_INTENT }
            assertNotNull("Expected MULTISPORT_TRANSITION_INTENT broadcast", msBroadcast)
            assertTrue(broadcasts.none { it.action == BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT })
            assertTrue(broadcasts.none { it.action == BANALService.LAP_SUMMARY })

            val decoded = MultisportTransitionBridge.fromIntent(msBroadcast)
            assertEquals(sampleBikeToRunEvent(), decoded)
        } finally {
            setStaticDeviceManager(null)
            controller.destroy()
        }
    }

    @Test
    fun tier1UserSportLock_blocksConfirmedMultisportTransition() {
        val controller = Robolectric.buildService(BANALService::class.java).create()
        val service = controller.get()
        val binder = service.onBind(Intent()) as BANALService.BANALServiceComm
        try {
            binder.setUserSelectedSportType(BSportType.BIKE)
            shadowOf(service).clearBroadcastIntents()

            service.onConfirmedMultisportTransition(sampleBikeToRunEvent())

            assertEquals(BSportType.BIKE, service.bSportType)
            assertTrue(
                "Tier 1 user lock must suppress MULTISPORT_TRANSITION_INTENT",
                shadowOf(service).broadcastIntents.none { it.action == BANALService.MULTISPORT_TRANSITION_INTENT }
            )
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun advanceLapsForMultisport_callsDeviceManagerDirectlyAndResetsLapAccumulatorsToExactDeltas() {
        val controller = Robolectric.buildService(BANALService::class.java).create()
        val service = controller.get()
        try {
            val timeLapSensor = MyIntegerAccumulatorSensor(null, SensorType.TIME_LAP, false).apply {
                activateSensor()
                increment(1865)
            }
            val distLapSensor = MyDoubleAccumulatorSensor(null, SensorType.DISTANCE_m_LAP, false).apply {
                activateSensor()
                increment(14510.0)
            }
            val dmMock = mockk<DeviceManager>(relaxed = true)
            every { dmMock.getAccumulatorSensorList(SensorType.TIME_LAP) } returns listOf(timeLapSensor)
            every { dmMock.getAccumulatorSensorList(SensorType.DISTANCE_m_LAP) } returns listOf(distLapSensor)
            setStaticDeviceManager(dmMock)

            shadowOf(service).clearBroadcastIntents()

            BANALService.advanceLapsForMultisport(2, 15, 60.0)

            verify(exactly = 2) { dmMock.newLap() }
            assertEquals(15, timeLapSensor.value)
            assertEquals(60.0, distLapSensor.value!!, 1e-6)
            assertTrue(
                "Must not fire raw LAP_SUMMARY broadcast",
                shadowOf(service).broadcastIntents.none { it.action == BANALService.LAP_SUMMARY }
            )
        } finally {
            setStaticDeviceManager(null)
            controller.destroy()
        }
    }

    @Test
    fun resetAccumulatorsIntentAndOnDestroy_clearMultisportBSportType() {
        val controller = Robolectric.buildService(BANALService::class.java).create()
        val service = controller.get()
        try {
            service.onConfirmedMultisportTransition(sampleBikeToRunEvent())
            assertEquals(BSportType.RUN, service.bSportType)

            service.mResetAccumulatorsReceiver.onReceive(service, Intent(BANALService.RESET_ACCUMULATORS_INTENT))
            assertEquals(BSportType.UNKNOWN, service.bSportType)

            service.onConfirmedMultisportTransition(sampleBikeToRunEvent())
            assertEquals(BSportType.RUN, service.bSportType)

            controller.destroy()
            assertEquals(BSportType.UNKNOWN, service.bSportType)
        } catch (t: Throwable) {
            controller.destroy()
            throw t
        }
    }

    private fun setStaticDeviceManager(dm: DeviceManager?) {
        val field = BANALService::class.java.getDeclaredField("cDeviceManager")
        field.isAccessible = true
        field.set(null, dm)
    }
}
