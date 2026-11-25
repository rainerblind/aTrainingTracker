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
 */

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.map.clearSensorMarkerCache
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.map.tryApplyPreviewBounds
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Compose UI and marker-cache verification for [ClusterNamingDialog] migrating from
 * full interactive GoogleMap to Lite-Mode [com.atrainingtracker.trainingtracker.ui.map.PathPreviewMap]
 * (`ATT-9019`, `REQ-MIG-062.1`, `TST-MIG-062.1`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ClusterNamingDialogPathPreviewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearSensorMarkerCache()

        val mockClusterRepo = mockk<WorkoutClusterRepository>(relaxed = true)
        every { mockClusterRepo.allClusters } returns MutableStateFlow(emptyList())
        coEvery { mockClusterRepo.refreshClusters() } returns Unit
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)

        val mockEngine = mockk<WorkoutClusterEngine>(relaxed = true)
        WorkoutClusterEngine.resetForTesting(mockEngine)

        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getUIName(any()) } returns "Cycling"
        every { mockSportRepo.getBSportType(any()) } returns BSportType.BIKE
        SportTypesRepository.resetForTesting(mockSportRepo)
    }

    @After
    fun tearDown() {
        clearSensorMarkerCache()
        SportTypesRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)
        unmockkAll()
    }

    private fun buildInteraction(
        date: String,
        workoutName: String,
        points: List<LatLng>,
        distance: Double
    ): BackupRestoreViewModel.ClusterInteraction = BackupRestoreViewModel.ClusterInteraction(
        date = date,
        start = points.first(),
        end = points.last(),
        apex = points[points.size / 2],
        distance = distance,
        bSportType = BSportType.BIKE,
        polyline = PolyUtil.encode(points),
        deferred = CompletableDeferred(),
        workoutName = workoutName
    )

    @Test
    fun testClusterNamingDialog_rendersPathPreviewMapAcrossConsecutiveQueueItems_TST_MIG_062() {
        val item1 = buildInteraction(
            date = "2026-11-24",
            workoutName = "Morning Forest Loop",
            points = listOf(
                LatLng(48.7083, 9.0028),
                LatLng(48.6950, 9.0450),
                LatLng(48.6570, 9.0640)
            ),
            distance = 9400.0
        )
        val item2 = buildInteraction(
            date = "2026-11-25",
            workoutName = "Evening City Commute",
            points = listOf(
                LatLng(52.5200, 13.4050),
                LatLng(52.5250, 13.4120),
                LatLng(52.5300, 13.4200)
            ),
            distance = 6200.0
        )

        var activeState by mutableStateOf(item1)
        var queueCount by mutableIntStateOf(2)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ClusterNamingDialog(
                        state = activeState,
                        queueCount = queueCount,
                        onConfirm = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val previewNode = composeTestRule.onNodeWithTag(CLUSTER_NAMING_PREVIEW_MAP_TAG)
        previewNode.assertIsDisplayed()
        val bounds1 = previewNode.getBoundsInRoot()
        assertTrue("PathPreviewMap must render at ~200dp height", bounds1.height.value >= 190f)
        assertTrue("PathPreviewMap must span dialog width", bounds1.width.value > 200f)
        composeTestRule.onAllNodesWithText("Morning Forest Loop")
            .assertCountEquals(2)
            .onFirst()
            .assertIsDisplayed()

        // Advance queue to second unmatched workout without unmounting the dialog
        composeTestRule.runOnUiThread {
            activeState = item2
            queueCount = 1
        }
        composeTestRule.waitForIdle()

        previewNode.assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Evening City Commute")
            .assertCountEquals(2)
            .onFirst()
            .assertIsDisplayed()
        assertEquals(bounds1.height.value, previewNode.getBoundsInRoot().height.value, 1f)
    }

    @Test
    fun testCreateSensorMarker_cachesNonNullDescriptorsAcrossConsecutiveQueuePresentations_TST_MIG_062() {
        mockkStatic(BitmapDescriptorFactory::class)
        val startDesc = mockk<BitmapDescriptor>(relaxed = true)
        val endDesc = mockk<BitmapDescriptor>(relaxed = true)
        val apexDesc = mockk<BitmapDescriptor>(relaxed = true)
        every { BitmapDescriptorFactory.fromBitmap(any()) } returnsMany listOf(startDesc, endDesc, apexDesc)

        // Simulate 5 consecutive ClusterNamingDialog presentations (15 marker lookups total)
        repeat(5) {
            assertSame(startDesc, createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint))
            assertSame(endDesc, createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint))
            assertSame(apexDesc, createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint))
        }

        // Only 3 BitmapDescriptorFactory.fromBitmap allocations across all 5 presentations
        verify(exactly = 3) { BitmapDescriptorFactory.fromBitmap(any()) }
    }

    @Test
    fun testCreateSensorMarker_neverCachesNullDescriptorWhenMapsNotInitialized_TST_MIG_062() {
        mockkStatic(BitmapDescriptorFactory::class)
        val recoveredDesc = mockk<BitmapDescriptor>(relaxed = true)
        var attempt = 0
        every { BitmapDescriptorFactory.fromBitmap(any()) } answers {
            attempt++
            if (attempt == 1) {
                throw NullPointerException("IBitmapDescriptorFactory is not initialized")
            } else {
                recoveredDesc
            }
        }

        assertNull(createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint))
        assertSame(recoveredDesc, createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint))
        assertSame(recoveredDesc, createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint))
        verify(exactly = 2) { BitmapDescriptorFactory.fromBitmap(any()) }
    }

    @Test
    fun testTryApplyPreviewBounds_handlesBothIllegalStateAndNullPointerException_TST_MIG_062() {
        assertFalse(tryApplyPreviewBounds { throw IllegalStateException("Map size can't be 0") })
        assertFalse(tryApplyPreviewBounds { throw NullPointerException("CameraUpdateFactory is not initialized") })
        assertTrue(tryApplyPreviewBounds { /* camera updated */ })
    }
}
