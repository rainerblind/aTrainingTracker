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

package com.atrainingtracker.trainingtracker.ui.components.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests verifying modernized modal bottom sheet dialogs, selection flows,
 * and centered dialog invariants according to REQ-UI-149 and TST-UI-102 (ATT-900).
 */
class ModalBottomSheetDialogsIntegrityTest {

    @Test
    fun testAppModalBottomSheet_supportsPainterAndVectorIcons() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheetKt")
        val methods = clazz.declaredMethods
        val appModalBottomSheetMethod = methods.find { it.name.startsWith("AppModalBottomSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AppModalBottomSheet composable method must exist", appModalBottomSheetMethod)
        assertTrue(Modifier.isPublic(appModalBottomSheetMethod!!.modifiers))

        // Ensure painter and vector icon parameter types are supported in method signature
        val paramTypes = appModalBottomSheetMethod.parameterTypes
        val hasPainterParam = paramTypes.any { it.name.contains("Painter") }
        val hasImageVectorParam = paramTypes.any { it.name.contains("ImageVector") }
        assertTrue("AppModalBottomSheet must accept Painter parameter for sport logos", hasPainterParam)
        assertTrue("AppModalBottomSheet must accept ImageVector parameter for vector icons", hasImageVectorParam)
    }

    @Test
    fun testExportSettingsDialog_existsAndExposesComposableAndFragment() {
        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "ExportSettingsDialog" }
        assertNotNull("ExportSettingsDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))

        val fragmentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialogFragment")
        assertTrue(
            "ExportSettingsDialogFragment must extend DialogFragment",
            androidx.fragment.app.DialogFragment::class.java.isAssignableFrom(fragmentClass)
        )
    }

    @Test
    fun testUnitsSettingsDialog_existsAndExposesComposableAndFragment() {
        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "UnitsSettingsDialog" }
        assertNotNull("UnitsSettingsDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))

        val fragmentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialogFragment")
        assertTrue(
            "UnitsSettingsDialogFragment must extend DialogFragment",
            androidx.fragment.app.DialogFragment::class.java.isAssignableFrom(fragmentClass)
        )
    }

    @Test
    fun testDisplaySettingsDialog_existsAndExposesComposableAndFragment() {
        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "DisplaySettingsDialog" }
        assertNotNull("DisplaySettingsDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))

        val fragmentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogFragment")
        assertTrue(
            "DisplaySettingsDialogFragment must extend DialogFragment",
            androidx.fragment.app.DialogFragment::class.java.isAssignableFrom(fragmentClass)
        )
    }

    @Test
    fun testDropboxSettingsDialog_existsAndExposesComposableAndFragment() {
        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.dropbox.DropboxSettingsDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "DropboxSettingsDialog" }
        assertNotNull("DropboxSettingsDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))

        val fragmentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.dropbox.DropboxSettingsDialogFragment")
        assertTrue(
            "DropboxSettingsDialogFragment must extend DialogFragment",
            androidx.fragment.app.DialogFragment::class.java.isAssignableFrom(fragmentClass)
        )
    }

    @Test
    fun testStravaSettingsDialog_existsAndExposesComposableAndFragment() {
        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "StravaSettingsDialog" }
        assertNotNull("StravaSettingsDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))

        val fragmentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogFragment")
        assertTrue(
            "StravaSettingsDialogFragment must extend DialogFragment",
            androidx.fragment.app.DialogFragment::class.java.isAssignableFrom(fragmentClass)
        )
    }

    @Test
    fun testSearchSettingsDialog_existsAndExposesComposableAndFragment() {
        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "SearchSettingsDialog" }
        assertNotNull("SearchSettingsDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))

        val fragmentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsDialogFragment")
        assertTrue(
            "SearchSettingsDialogFragment must extend DialogFragment",
            androidx.fragment.app.DialogFragment::class.java.isAssignableFrom(fragmentClass)
        )
    }

    @Test
    fun testActivityTypeSelectionDialog_existsAndExposesComposableAndFragment() {
        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "ActivityTypeSelectionDialog" }
        assertNotNull("ActivityTypeSelectionDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))

        val fragmentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionDialogFragment")
        assertTrue(
            "ActivityTypeSelectionDialogFragment must extend DialogFragment",
            androidx.fragment.app.DialogFragment::class.java.isAssignableFrom(fragmentClass)
        )
    }

    @Test
    fun testSensorSourceDialog_existsAndExposesComposableContract() {
        val dialogClass = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "SensorSourceDialog" }
        assertNotNull("SensorSourceDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))
    }

    @Test
    fun testDeviceTypeSelectionDialog_existsAndExposesComposableContract() {
        val dialogClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.DeviceTypeSelectionDialogKt")
        val composableMethod = dialogClass.declaredMethods.find { it.name == "DeviceTypeSelectionDialog" }
        assertNotNull("DeviceTypeSelectionDialog composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))
    }

    @Test
    fun testTrackingGridDialogs_existAndExposeComposableContracts() {
        val editFieldClass = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialogKt")
        val editFieldMethod = editFieldClass.declaredMethods.find { it.name == "EditSensorFieldDialog" }
        assertNotNull("EditSensorFieldDialog composable function must exist", editFieldMethod)
        assertTrue(Modifier.isPublic(editFieldMethod!!.modifiers))

        val configFilterClass = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.ConfigureFilterDialogKt")
        val configFilterMethod = configFilterClass.declaredMethods.find { it.name == "ConfigureFilterDialog" }
        assertNotNull("ConfigureFilterDialog composable function must exist", configFilterMethod)
        assertTrue(Modifier.isPublic(configFilterMethod!!.modifiers))
    }

    @Test
    fun testSportAndEquipmentDialogs_existAndExposeComposableContracts() {
        val sportTypeClass = Class.forName("com.atrainingtracker.banalservice.ui.sporttype.EditSportTypeDialogKt")
        val sportTypeMethod = sportTypeClass.declaredMethods.find { it.name == "EditSportTypeDialog" }
        assertNotNull("EditSportTypeDialog composable function must exist", sportTypeMethod)
        assertTrue(Modifier.isPublic(sportTypeMethod!!.modifiers))

        val equipClass = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.EditEquipmentDialogKt")
        val equipMethod = equipClass.declaredMethods.find { it.name == "EditEquipmentDialog" }
        assertNotNull("EditEquipmentDialog composable function must exist", equipMethod)
        assertTrue(Modifier.isPublic(equipMethod!!.modifiers))

        val deviceClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogKt")
        val deviceMethod = deviceClass.declaredMethods.find { it.name == "EditDeviceDialog" }
        assertNotNull("EditDeviceDialog composable function must exist", deviceMethod)
        assertTrue(Modifier.isPublic(deviceMethod!!.modifiers))
    }

    @Test
    fun testWorkoutClusterDialogs_existAndExposeComposableContracts() {
        val clusterCompClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterComponentsKt")
        val selectClusterMethod = clusterCompClass.declaredMethods.find { it.name == "WorkoutClusterSelectionDialog" }
        assertNotNull("WorkoutClusterSelectionDialog composable function must exist", selectClusterMethod)
        assertTrue(Modifier.isPublic(selectClusterMethod!!.modifiers))

        val editClusterMethod = clusterCompClass.declaredMethods.find { it.name == "EditWorkoutClusterDialog" }
        assertNotNull("EditWorkoutClusterDialog composable function must exist", editClusterMethod)
        assertTrue(Modifier.isPublic(editClusterMethod!!.modifiers))

        val heatmapClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterHeatmapScreenKt")
        val identityMethod = heatmapClass.declaredMethods.find { it.name == "EditWorkoutClusterIdentityDialog" }
        assertNotNull("EditWorkoutClusterIdentityDialog composable function must exist", identityMethod)
        assertTrue(Modifier.isPublic(identityMethod!!.modifiers))

        val infoClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.ClusterInfoDialogKt")
        val infoMethod = infoClass.declaredMethods.find { it.name == "ClusterInfoDialog" }
        assertNotNull("ClusterInfoDialog composable function must exist", infoMethod)
        assertTrue(Modifier.isPublic(infoMethod!!.modifiers))
    }

    @Test
    fun testExportDetailsDialog_existsAndExposesComposableContract() {
        val exportGroupClass = Class.forName("com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusGroupKt")
        val exportDetailsMethod = exportGroupClass.declaredMethods.find { it.name == "ExportDetailsDialog" }
        assertNotNull("ExportDetailsDialog composable function must exist", exportDetailsMethod)
        assertTrue(Modifier.isPublic(exportDetailsMethod!!.modifiers))
    }

    @Test
    fun testEditWorkoutDialog_existsAndExposesComposableContract() {
        val editWorkoutClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutScreenKt")
        val editWorkoutScreenMethod = editWorkoutClass.declaredMethods.find { it.name == "EditWorkoutScreen" }
        assertNotNull("EditWorkoutScreen composable function must exist", editWorkoutScreenMethod)
        assertTrue(Modifier.isPublic(editWorkoutScreenMethod!!.modifiers))

        val editWorkoutDialogMethod = editWorkoutClass.declaredMethods.find { it.name == "EditWorkoutDialog" }
        assertNotNull("EditWorkoutDialog composable function must exist", editWorkoutDialogMethod)
        assertTrue(Modifier.isPublic(editWorkoutDialogMethod!!.modifiers))
    }

    @Test
    fun testCenteredDialogInvariants_retainedAsExpected() {
        // LapSummaryDialog must remain retained as a centered HUD dialog during active tracking
        val lapSummaryClass = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.LapSummaryDialogKt")
        val lapSummaryMethod = lapSummaryClass.declaredMethods.find { it.name == "LapSummaryDialog" }
        assertNotNull("LapSummaryDialog must be retained", lapSummaryMethod)
        assertTrue(Modifier.isPublic(lapSummaryMethod!!.modifiers))

        // Destructive confirmation alerts must remain retained
        val deleteConfirmClass = Class.forName("com.atrainingtracker.trainingtracker.ui.components.DeleteConfirmationDialogKt")
        val deleteConfirmMethod = deleteConfirmClass.declaredMethods.find { it.name == "DeleteConfirmationDialog" }
        assertNotNull("DeleteConfirmationDialog must be retained", deleteConfirmMethod)
        assertTrue(Modifier.isPublic(deleteConfirmMethod!!.modifiers))

        val workoutDeleteClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutDeleteDialogKt")
        val workoutDeleteMethod = workoutDeleteClass.declaredMethods.find { it.name == "WorkoutDeleteDialog" }
        assertNotNull("WorkoutDeleteDialog must be retained", workoutDeleteMethod)
        assertTrue(Modifier.isPublic(workoutDeleteMethod!!.modifiers))

        val deleteOldClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.DeleteOldWorkoutsDialogKt")
        val deleteOldMethod = deleteOldClass.declaredMethods.find { it.name == "DeleteOldWorkoutsDialog" }
        assertNotNull("DeleteOldWorkoutsDialog must be retained", deleteOldMethod)
        assertTrue(Modifier.isPublic(deleteOldMethod!!.modifiers))
    }
}
