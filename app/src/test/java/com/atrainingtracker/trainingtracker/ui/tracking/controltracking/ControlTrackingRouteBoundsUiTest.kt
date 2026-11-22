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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.components.RouteSelectionButton
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.LocationCalibrationStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Compose UI regression tests for ATT-9364:
 * Verifies that the dimmed RouteSelectionButton subtitle (`R.string.route_no_routes_nearby` —
 * "No routes nearby" / "Keine Strecken in der Nähe") never collapses to zero-dimension `(0, 0, 0, 0)`
 * bounds on ControlTrackingScreen under standard or vertically constrained viewports (stacked
 * calibration badge + battery warning banner + bottom slot), and is omitted from semantics when
 * an active route replaces the empty hint.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h851dp-xxhdpi")
class ControlTrackingRouteBoundsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun controlTrackingScreen_compactViewportWithStackedBanners_preservesPositiveBoundsForNoRoutesNearby() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val noRoutesNearby = context.getString(R.string.route_no_routes_nearby)
        val selectRoute = context.getString(R.string.route_action_select)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    // Simulate a vertically constrained viewport (e.g. compact screen / 3-button nav bar
                    // with calibration badge, battery optimization banner, onboarding CTA, and bottom slot)
                    Box(modifier = Modifier.size(width = 393.dp, height = 560.dp)) {
                        ControlTrackingScreen(
                            trackingMode = TrackingMode.READY,
                            searchingFor = null,
                            devices = emptyList(),
                            currentSport = BSportType.UNKNOWN,
                            onSearch = {},
                            onDeviceClick = {},
                            onSportSelected = {},
                            onStart = {},
                            onPause = {},
                            onResume = {},
                            onStop = {},
                            showResearchButton = false,
                            locationCalibrationStatus = LocationCalibrationStatus(
                                locationId = 1L,
                                locationName = "Viktualienmarkt, Altstadt-Lehel",
                                referenceAltitude = 524.0,
                                isCalibrated = true
                            ),
                            bottomContent = {
                                RouteSelectionButton(
                                    activeRoute = null,
                                    isDimmed = true,
                                    emptySubtitleRes = R.string.route_no_routes_nearby,
                                    onClick = {}
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = context.getString(R.string.hrv_morning_check_start_button))
                                }
                            }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val subtitleNode = composeTestRule.onNodeWithText(noRoutesNearby, useUnmergedTree = true)
        subtitleNode.assertExists()
        val bounds = subtitleNode.getBoundsInRoot()
        assertTrue(
            "Expected positive width for '$noRoutesNearby' under compact viewport, got ${bounds.width}",
            bounds.width > 0.dp
        )
        assertTrue(
            "Expected positive height for '$noRoutesNearby' under compact viewport, got ${bounds.height}",
            bounds.height > 0.dp
        )

        subtitleNode.performScrollTo()
        subtitleNode.assertIsDisplayed()

        val titleBounds = composeTestRule
            .onNodeWithText(selectRoute, useUnmergedTree = true)
            .getBoundsInRoot()
        assertTrue("Expected positive width for '$selectRoute'", titleBounds.width > 0.dp)
        assertTrue("Expected positive height for '$selectRoute'", titleBounds.height > 0.dp)
    }

    @Test
    @Config(sdk = [34], application = Application::class, qualifiers = "de-w393dp-h851dp-xxhdpi")
    fun controlTrackingScreen_germanLocalePortrait_displaysPositiveBoundsForKeineStreckenInDerNaehe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val noRoutesNearbyDe = context.getString(R.string.route_no_routes_nearby)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    Box(modifier = Modifier.size(width = 393.dp, height = 720.dp)) {
                        ControlTrackingScreen(
                            trackingMode = TrackingMode.READY,
                            searchingFor = null,
                            devices = emptyList(),
                            currentSport = BSportType.BIKE,
                            onSearch = {},
                            onDeviceClick = {},
                            onSportSelected = {},
                            onStart = {},
                            onPause = {},
                            onResume = {},
                            onStop = {},
                            showResearchButton = false,
                            locationCalibrationStatus = LocationCalibrationStatus(
                                locationId = 1L,
                                locationName = "Viktualienmarkt, Altstadt-Lehel",
                                referenceAltitude = 524.0,
                                isCalibrated = true
                            ),
                            bottomContent = {
                                RouteSelectionButton(
                                    activeRoute = null,
                                    isDimmed = true,
                                    emptySubtitleRes = R.string.route_no_routes_nearby,
                                    onClick = {}
                                )
                            }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val subtitleNode = composeTestRule.onNodeWithText(noRoutesNearbyDe, useUnmergedTree = true)
        subtitleNode.assertIsDisplayed()
        val bounds = subtitleNode.getBoundsInRoot()
        assertTrue(
            "Expected positive width for German '$noRoutesNearbyDe', got ${bounds.width}",
            bounds.width > 0.dp
        )
        assertTrue(
            "Expected positive height for German '$noRoutesNearbyDe', got ${bounds.height}",
            bounds.height > 0.dp
        )
    }

    @Test
    fun routeSelectionButton_whenActiveRouteSelected_omitsNoRoutesNearbyFromSemantics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val noRoutesNearby = context.getString(R.string.route_no_routes_nearby)
        val activeRoute = RouteWithPath(
            summary = RouteSummary(
                id = 42L,
                externalId = "att9364_test",
                name = "Isar Valley Loop",
                description = "Test route",
                isSelected = true,
                distance = 25_000.0,
                elevationGain = 310.0,
                bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX,
                syncedAt = 0L
            ),
            path = emptyList()
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    RouteSelectionButton(
                        activeRoute = activeRoute,
                        isDimmed = false,
                        emptySubtitleRes = R.string.route_no_routes_nearby,
                        onClick = {},
                        onClearRoute = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(noRoutesNearby, useUnmergedTree = true).assertDoesNotExist()
        val routeNode = composeTestRule.onNodeWithText("✓ Isar Valley Loop", useUnmergedTree = true)
        routeNode.assertIsDisplayed()
        val bounds = routeNode.getBoundsInRoot()
        assertTrue("Active route title must have positive width", bounds.width > 0.dp)
        assertTrue("Active route title must have positive height", bounds.height > 0.dp)
    }
}
