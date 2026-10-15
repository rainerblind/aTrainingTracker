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

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests verifying athlete weight persistence, clamping, and metric/imperial conversion
 * conforming to REQ-TRK-019 and TST-TRK-011.
 */
class SettingsDataStoreWeightTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestDataStore(): Pair<DataStore<Preferences>, SettingsDataStore> {
        val testFile = tempFolder.newFile("test_weight_${System.nanoTime()}.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )

        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = SettingsDataStore(context, testDataStore)
        return Pair(testDataStore, settingsDataStore)
    }

    @Test
    fun testDefaultAthleteWeight_isZero() = runBlocking {
        val (_, dataStore) = createTestDataStore()
        val weight = dataStore.athleteWeightKgFlow.first()
        assertEquals(0.0f, weight, 0.001f)
    }

    @Test
    fun testSetValidAthleteWeight_persistsAndEmits() = runBlocking {
        val (_, dataStore) = createTestDataStore()
        dataStore.setAthleteWeightKg(75.5f)
        val weight = dataStore.athleteWeightKgFlow.first()
        assertEquals(75.5f, weight, 0.001f)
    }

    @Test
    fun testSetAthleteWeight_clampsBelowMinimum() = runBlocking {
        val (_, dataStore) = createTestDataStore()
        dataStore.setAthleteWeightKg(15.0f) // below 30.0 kg min
        val weight = dataStore.athleteWeightKgFlow.first()
        assertEquals(SettingsDataStore.MIN_ATHLETE_WEIGHT_KG, weight, 0.001f)
    }

    @Test
    fun testSetAthleteWeight_clampsAboveMaximum() = runBlocking {
        val (_, dataStore) = createTestDataStore()
        dataStore.setAthleteWeightKg(350.0f) // above 300.0 kg max
        val weight = dataStore.athleteWeightKgFlow.first()
        assertEquals(SettingsDataStore.MAX_ATHLETE_WEIGHT_KG, weight, 0.001f)
    }

    @Test
    fun testSetAthleteWeight_zeroOrNegative_resetsToDefaultZero() = runBlocking {
        val (_, dataStore) = createTestDataStore()
        dataStore.setAthleteWeightKg(75.0f)
        assertEquals(75.0f, dataStore.athleteWeightKgFlow.first(), 0.001f)

        dataStore.setAthleteWeightKg(0.0f)
        assertEquals(0.0f, dataStore.athleteWeightKgFlow.first(), 0.001f)

        dataStore.setAthleteWeightKg(80.0f)
        dataStore.setAthleteWeightKg(-5.0f)
        assertEquals(0.0f, dataStore.athleteWeightKgFlow.first(), 0.001f)
    }

    @Test
    fun testImperialWeightConversion_roundTrip() {
        val weightKg = 75.0f
        val weightLbs = weightKg * SettingsDataStore.LBS_PER_KG
        assertEquals(165.3467f, weightLbs, 0.01f)

        val convertedBackKg = weightLbs / SettingsDataStore.LBS_PER_KG
        assertEquals(weightKg, convertedBackKg, 0.001f)
    }
}
