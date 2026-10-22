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

package com.atrainingtracker.trainingtracker.build

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModel
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceViewModel
import com.atrainingtracker.banalservice.ui.sporttype.SportTypeViewModel
import com.atrainingtracker.trainingtracker.activities.ZonesSettingsViewModel
import com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModel
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsViewModel
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModel
import com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileViewModel
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModel
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModel
import com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsViewModel
import com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrackViewModel
import com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel
import com.atrainingtracker.trainingtracker.ui.routes.RoutesViewModel
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Validates reflection constructor contracts for AndroidViewModel subclasses (REQ-ARC-004, TST-ARC-004).
 * Ensures that all ViewModels instantiated via default Android framework reflection (AndroidViewModelFactory /
 * viewModel(activity)) expose a public 1-argument (Application.class) constructor.
 */
class AndroidViewModelReflectionContractTest {

    private val defaultInstantiatedViewModels: List<Class<out AndroidViewModel>> = listOf(
        WorkoutClustersViewModel::class.java,
        DeviceListViewModel::class.java,
        EquipmentViewModel::class.java,
        KnownLocationsViewModel::class.java,
        MapFragmentWithTrackViewModel::class.java,
        RoutesViewModel::class.java,
        TrackOnMapAftermathViewModel::class.java,
        BackupRestoreViewModel::class.java,
        ZonesSettingsViewModel::class.java,
        PeriodsViewModel::class.java,
        SportTypeViewModel::class.java,
        AthleteProfileViewModel::class.java,
        EditDeviceViewModel::class.java,
        WorkoutSummariesViewModel::class.java
    )

    /**
     * TST-ARC-004.1: Verifies WorkoutClustersViewModel constructor reflection contract and bytecode overloads.
     */
    @Test
    fun testWorkoutClustersViewModel_exposesPublicSingleArgConstructorAndOverloads() {
        val singleArgConstructor = WorkoutClustersViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull("WorkoutClustersViewModel must expose a 1-arg (Application) constructor", singleArgConstructor)
        assertTrue("1-arg constructor must be public", Modifier.isPublic(singleArgConstructor.modifiers))

        val twoArgConstructor = WorkoutClustersViewModel::class.java.getConstructor(
            Application::class.java,
            CoroutineDispatcher::class.java
        )
        assertNotNull("WorkoutClustersViewModel must expose a 2-arg (Application, CoroutineDispatcher) constructor", twoArgConstructor)
        assertTrue("2-arg constructor must be public", Modifier.isPublic(twoArgConstructor.modifiers))
    }

    /**
     * TST-ARC-004.2: Verifies that every AndroidViewModel subclass instantiated via default framework
     * factory exposes an accessible public 1-arg (Application) constructor.
     */
    @Test
    fun testAllDefaultAndroidViewModels_exposePublicSingleArgConstructor() {
        for (vmClass in defaultInstantiatedViewModels) {
            val constructor = try {
                vmClass.getConstructor(Application::class.java)
            } catch (e: NoSuchMethodException) {
                null
            }
            assertNotNull(
                "Class ${vmClass.name} must expose a public (Application) constructor for AndroidViewModelFactory reflection",
                constructor
            )
            assertTrue(
                "Constructor for ${vmClass.name}(Application) must be public",
                Modifier.isPublic(constructor!!.modifiers)
            )
        }
    }
}
