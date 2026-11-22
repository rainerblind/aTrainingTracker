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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Unit tests verifying numeric `isMinRelevant` evaluation (locale-independent) and
 * [ExtremaDataRow.sensorType] population in [WorkoutDataMapper]
 * (REQ-DAT-048.5, TST-DAT-044.5, ATT-8128).
 */
class WorkoutDataMapperExtremaTest {

    private lateinit var originalLocale: Locale
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaDb = mockk<StravaUploadDbHelper>(relaxed = true)

    private lateinit var mapper: WorkoutDataMapper

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC
        every { mockContext.getString(any()) } answers { "label_${firstArg<Int>()}" }

        mapper = WorkoutDataMapper(
            context = mockContext,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaDb
        )
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        unmockkAll()
    }

    private fun invokeCreateExtremaRow(
        sensorType: SensorType,
        rawMinVal: Double?,
        avgVal: Double? = 50.0,
        rawMaxVal: Double? = 100.0
    ): ExtremaDataRow? {
        val method = WorkoutDataMapper::class.java.getDeclaredMethod(
            "createExtremaRow",
            SensorType::class.java,
            java.lang.Double::class.java,
            LatLng::class.java,
            java.lang.Double::class.java,
            java.lang.Double::class.java,
            LatLng::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(mapper, sensorType, rawMinVal, null, avgVal, rawMaxVal, null, false) as ExtremaDataRow?
    }

    private fun invokeCreateDerivedPaceRow(
        speedMin: Double?,
        speedAvg: Double?,
        speedMax: Double?
    ): ExtremaDataRow? {
        val method = WorkoutDataMapper::class.java.getDeclaredMethod(
            "createDerivedPaceRow",
            java.lang.Double::class.java,
            LatLng::class.java,
            java.lang.Double::class.java,
            java.lang.Double::class.java,
            LatLng::class.java
        )
        method.isAccessible = true
        return method.invoke(mapper, speedMin, null, speedAvg, speedMax, null) as ExtremaDataRow?
    }

    @Test
    fun createExtremaRow_evaluatesIsMinRelevantNumericallyAcrossLocalesAndSetsSensorType_TST_DAT_044_5() {
        for (locale in listOf(Locale.GERMANY, Locale.US)) {
            Locale.setDefault(locale)

            // Non-positive, null, and non-finite rawMinVal -> isMinRelevant == false for speed/power/cadence
            val zeroSpeed = invokeCreateExtremaRow(SensorType.SPEED_mps, rawMinVal = 0.0, avgVal = 3.0, rawMaxVal = 5.0)
            assertNotNull(zeroSpeed)
            assertFalse("0.0 speed min must not be relevant in $locale", zeroSpeed!!.isMinRelevant)
            assertEquals(SensorType.SPEED_mps, zeroSpeed.sensorType)

            val negativePower = invokeCreateExtremaRow(SensorType.POWER, rawMinVal = -5.0, avgVal = 200.0, rawMaxVal = 400.0)
            assertNotNull(negativePower)
            assertFalse("Negative power min must not be relevant in $locale", negativePower!!.isMinRelevant)
            assertEquals(SensorType.POWER, negativePower.sensorType)

            val nanCadence = invokeCreateExtremaRow(SensorType.CADENCE, rawMinVal = Double.NaN, avgVal = 85.0, rawMaxVal = 100.0)
            assertNotNull(nanCadence)
            assertFalse("NaN cadence min must not be relevant in $locale", nanCadence!!.isMinRelevant)
            assertEquals(SensorType.CADENCE, nanCadence.sensorType)

            val nullSpeed = invokeCreateExtremaRow(SensorType.SPEED_mps, rawMinVal = null, avgVal = 3.0, rawMaxVal = 5.0)
            assertNotNull(nullSpeed)
            assertFalse("Null speed min must not be relevant in $locale", nullSpeed!!.isMinRelevant)

            // Positive rawMinVal -> isMinRelevant == true
            val positivePower = invokeCreateExtremaRow(SensorType.POWER, rawMinVal = 120.0, avgVal = 210.0, rawMaxVal = 420.0)
            assertNotNull(positivePower)
            assertTrue("Positive power min must be relevant in $locale", positivePower!!.isMinRelevant)
            assertEquals(SensorType.POWER, positivePower.sensorType)

            // Altitude & Temperature keep else -> true even at 0.0 or negative values
            val freezingTemp = invokeCreateExtremaRow(SensorType.TEMPERATURE, rawMinVal = 0.0, avgVal = 4.0, rawMaxVal = 8.0)
            assertNotNull(freezingTemp)
            assertTrue("0°C temperature min must remain relevant in $locale", freezingTemp!!.isMinRelevant)
            assertEquals(SensorType.TEMPERATURE, freezingTemp.sensorType)

            val seaLevelAlt = invokeCreateExtremaRow(SensorType.ALTITUDE, rawMinVal = -10.0, avgVal = 50.0, rawMaxVal = 120.0)
            assertNotNull(seaLevelAlt)
            assertTrue("Negative altitude min must remain relevant in $locale", seaLevelAlt!!.isMinRelevant)
            assertEquals(SensorType.ALTITUDE, seaLevelAlt.sensorType)
        }
    }

    @Test
    fun createDerivedPaceRow_populatesPaceSensorTypeAndUsesNumericSpeedMaxGuard_TST_DAT_044_5() {
        val paceRow = invokeCreateDerivedPaceRow(speedMin = 2.0, speedAvg = 3.0, speedMax = 4.0)
        assertNotNull(paceRow)
        assertEquals(SensorType.PACE_spm, paceRow!!.sensorType)
        assertTrue(paceRow.isMinRelevant)

        val noMaxSpeedPaceRow = invokeCreateDerivedPaceRow(speedMin = 2.0, speedAvg = 3.0, speedMax = 0.0)
        assertNotNull(noMaxSpeedPaceRow)
        assertEquals(SensorType.PACE_spm, noMaxSpeedPaceRow!!.sensorType)
        assertFalse("speedMax <= 0.001 must mark derived pace min irrelevant", noMaxSpeedPaceRow.isMinRelevant)
    }
}
