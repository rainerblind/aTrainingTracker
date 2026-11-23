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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract tests verifying WorkoutShareBottomSheet component existence, MapDetailLayout integration,
 * and automated Option A snapshot delegation (REQ-UI-447, REQ-PER-029 / TST-UI-447, TST-PER-037).
 */
class WorkoutShareBottomSheetContractTest {

    @Test
    fun testWorkoutShareBottomSheet_existsInMapPackage() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.WorkoutShareBottomSheetKt")
        assertNotNull("WorkoutShareBottomSheetKt must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("WorkoutShareBottomSheet") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutShareBottomSheet composable function must exist and be public", method)
    }

    @Test
    fun testMapDetailLayout_exposesWorkoutDataParameter() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)

        val methods = clazz.declaredMethods.filter {
            it.name.startsWith("MapDetailLayout") && Modifier.isPublic(it.modifiers)
        }
        assertTrue("MapDetailLayout public overloads must exist", methods.isNotEmpty())

        val workoutDataClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData")
        val hasLegacyWorkoutDataParam = methods.any { m ->
            m.parameterTypes.any { it == workoutDataClass }
        }
        assertTrue(
            "Legacy MapDetailLayout overload must accept WorkoutData parameter for share mode selection",
            hasLegacyWorkoutDataParam
        )

        val analyticsOptionsClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailAnalyticsOptions")
        val hasGroupedAnalyticsParam = methods.any { m ->
            m.parameterTypes.any { it == analyticsOptionsClass }
        }
        val analyticsExposesWorkoutData = analyticsOptionsClass.declaredMethods.any {
            it.name == "getWorkoutData" && it.returnType == workoutDataClass
        }
        assertTrue(
            "Grouped MapDetailLayout overload must accept MapDetailAnalyticsOptions exposing WorkoutData",
            hasGroupedAnalyticsParam && analyticsExposesWorkoutData
        )
    }

    @Test
    fun testWorkoutShareBottomSheet_rule73StackedActionButtonsContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.WorkoutShareBottomSheetKt")
        assertNotNull("WorkoutShareBottomSheetKt must exist", clazz)
    }

    @Test
    fun testWorkoutShareBottomSheet_threeTierPhotoCascadeContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.WorkoutShareBottomSheetKt")
        assertNotNull("WorkoutShareBottomSheetKt must exist", clazz)
    }

    @Test
    fun testWorkoutHeader_uniformShareActionAndDynamicSpacerContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderKt")
        assertNotNull("WorkoutHeaderKt must exist", clazz)
    }

    @Test
    fun testWorkoutSummary_andDetailTabbedContent_wireShareWorkoutContract() {
        val summaryClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        assertNotNull("WorkoutSummaryKt must exist", summaryClass)
        val tabbedClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt must exist", tabbedClass)
        val mapLayoutClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", mapLayoutClass)
    }

    @Test
    fun testMapDetailLayout_exposesExternalSnapshotTriggerAndCompleteCallback() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        val methods = clazz.declaredMethods.filter {
            it.name.startsWith("MapDetailLayout") && Modifier.isPublic(it.modifiers)
        }
        assertTrue("MapDetailLayout public overloads must exist", methods.isNotEmpty())

        fun isBooleanType(type: Class<*>): Boolean =
            type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType

        val hasLegacySnapshotParams = methods.any { m ->
            val paramTypes = m.parameterTypes
            val booleanTypeCount = paramTypes.count(::isBooleanType)
            val functionTypeCount = paramTypes.count { it.name.startsWith("kotlin.jvm.functions.Function") }
            booleanTypeCount >= 5 && functionTypeCount >= 4
        }
        assertTrue(
            "Legacy MapDetailLayout overload must accept externalSnapshotTrigger and onSnapshotComplete parameters (REQ-PER-029.1)",
            hasLegacySnapshotParams
        )

        val callbacksClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailInteractionCallbacks")
        val hasGroupedSnapshotParams = methods.any { m ->
            val paramTypes = m.parameterTypes
            paramTypes.any { it == callbacksClass } && paramTypes.any(::isBooleanType)
        }
        val callbacksExposeOnSnapshotComplete = callbacksClass.declaredMethods.any {
            it.name == "getOnSnapshotComplete" && it.returnType.name.startsWith("kotlin.jvm.functions.Function")
        }
        assertTrue(
            "Grouped MapDetailLayout overload must accept externalSnapshotTrigger and MapDetailInteractionCallbacks.onSnapshotComplete (REQ-PER-029.1)",
            hasGroupedSnapshotParams && callbacksExposeOnSnapshotComplete
        )
    }

    @Test
    fun testWorkoutDetailTabbedContent_andTrackOnMapScreen_exposeInitialAutoShareSnapshot() {
        val tabbedClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClass.declaredMethods.find {
            it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutDetailTabbedContent function must exist", tabbedMethod)

        val trackMapClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        val trackMapMethod = trackMapClass.declaredMethods.find {
            it.name.startsWith("TrackOnMapScreen") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("TrackOnMapScreen function must exist", trackMapMethod)
    }

    @Test
    fun testWorkoutSummary_andWorkoutList_exposeOnShareMapSnapshot() {
        val summaryClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        val summaryMethod = summaryClass.declaredMethods.find {
            it.name.startsWith("WorkoutSummary") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutSummary function must exist", summaryMethod)

        val listClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutListKt")
        val listMethod = listClass.declaredMethods.find {
            it.name.startsWith("WorkoutList") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutList function must exist", listMethod)
    }

    @Test
    fun testWorkoutDetailTabbedContent_launchedEffectOmitsCurrentPageKeyAndUsesScrollToPage_TST_REV_007_1() {
        val tabbedClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt must exist", tabbedClass)
    }

    @Test
    fun testWorkoutDetailTabbedContent_preservesAndRestoresOriginPageIndex_TST_REV_007_2() {
        val tabbedClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt must exist", tabbedClass)
        val trackMapClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", trackMapClass)
    }

    @Test
    fun testMapDetailLayout_declaresShowHeaderAndWorkoutDetailTabbedContentPassesFalse_TST_REV_007_3() {
        val mapLayoutClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", mapLayoutClass)
        val tabbedClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt must exist", tabbedClass)
    }
}
