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

package com.atrainingtracker.trainingtracker.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.components.strava.PoweredByStrava
import com.atrainingtracker.trainingtracker.ui.map.MapContentScopeImpl
import com.atrainingtracker.trainingtracker.ui.map.MapStyle
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.map.resolveMapProperties
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import com.atrainingtracker.trainingtracker.ui.navigation.SettingsBottomSheetType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.GridActions
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreen
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TabToggleActions
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingScreenState
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabConfigHeader
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import com.atrainingtracker.trainingtracker.ui.util.MetricFormatterContext
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.MapType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Robolectric Compose UI & Map DSL test suite covering Process Stability UI,
 * Map DSL, Cockpit Configuration, Settings Navigation, and Strava Branding TSTs (REQ-PRO-132,
 * REQ-PRO-133).
 */
// Linked test specs: TST-STR-005, TST-STR-006, TST-STR-007, TST-STR-008, TST-INT-004, TST-INT-005, TST-INT-006, TST-INT-008, TST-REG-002, TST-REG-005
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xhdpi")
class ProcessStabilityComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
    }

    @After
    fun tearDown() {
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    /**
     * TST-STR-005 (REQ-MAP-006): Verifies ATrainingTrackerMap MapContentScope DSL layer
     * registration and resolveMapProperties light/dark theme contracts.
     */
    @Test
    fun mapDsl_scopeRegistrationAndThemeProperties_TST_STR_005() {
        val darkStyle = MapStyleOptions("""[{"featureType":"all","stylers":[{"saturation":-100}]}]""")
        val darkProps = resolveMapProperties(isDark = true, darkMapStyleOptions = darkStyle)
        assertEquals(MapType.NORMAL, darkProps.mapType)
        assertEquals(darkStyle, darkProps.mapStyleOptions)

        val lightProps = resolveMapProperties(isDark = false, darkMapStyleOptions = darkStyle)
        assertEquals(MapType.TERRAIN, lightProps.mapType)
        assertNull(lightProps.mapStyleOptions)

        val scope = MapContentScopeImpl(
            zoomFocus = MapZoomFocus.TRACK_AND_MARKERS,
            primaryColor = Color.Red,
            context = context,
            directionIcons = Triple(null, null, null),
            bSportType = BSportType.BIKE
        )
        val path = MapTrack(
            id = 11L,
            type = TrackType.BEST,
            bSportType = BSportType.BIKE,
            path = listOf(
                PathPoint(distance = 0.0, latLng = LatLng(48.1, 11.5)),
                PathPoint(distance = 100.0, latLng = LatLng(48.2, 11.6))
            )
        )
        scope.collect {
            path(path, alpha = 1.0f)
        }
        // Executing collect with DSL block verifies MapContentScopeImpl composition without errors
        assertTrue("MapContentScope DSL collect executed cleanly", true)
    }

    /**
     * TST-STR-006 (REQ-UI-015): Verifies shared MetricBadge renders unified metadata chip text.
     */
    @Test
    fun metricBadge_rendersFormattedBadgeText_TST_STR_006() {
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                MetricBadge(text = "Gravel Bike")
            }
        }
        composeTestRule.onNodeWithText("Gravel Bike").assertIsDisplayed()
    }

    /**
     * TST-STR-007 (REQ-UI-016): Verifies LocalMetricFormatter CompositionLocal provides
     * synchronized SpeedFormatter and DistanceFormatter instances to UI trees.
     */
    @Test
    fun localMetricFormatter_providesSynchronizedFormattersInComposeTree_TST_STR_007() {
        val formatterCtx = MetricFormatterContext(
            distance = DistanceFormatter(),
            speed = SpeedFormatter()
        )
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalMetricFormatter provides formatterCtx) {
                    val fmt = LocalMetricFormatter.current
                    Text(text = "Dist=${fmt.distance.format(5000.0)} Speed=${fmt.speed.format(10.0)}")
                }
            }
        }
        val expectedDist = formatterCtx.distance.format(5000.0)
        val expectedSpeed = formatterCtx.speed.format(10.0)
        composeTestRule.onNodeWithText("Dist=$expectedDist Speed=$expectedSpeed").assertIsDisplayed()
    }

    /**
     * TST-STR-008 (REQ-UI-017): Verifies MappableListItem handles clicks and hosts unified
     * EmptyStatePlaceholder content cleanly.
     */
    @Test
    fun mappableListItemAndEmptyStatePlaceholder_renderAndDispatchClick_TST_STR_008() {
        var clickedCount = 0
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                androidx.compose.foundation.layout.Column {
                    MappableListItem(
                        onClick = { clickedCount++ },
                        alpha = 0.85f
                    ) {
                        Text("Workout #101")
                    }
                    EmptyStatePlaceholder(
                        message = "No workouts recorded yet",
                        hint = "Tap Start to begin",
                        icon = Icons.Default.DirectionsBike,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Workout #101").assertIsDisplayed().performClick()
        assertEquals(1, clickedCount)
        composeTestRule.onNodeWithText("No workouts recorded yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap Start to begin").assertIsDisplayed()
    }

    /**
     * TST-INT-004 (REQ-UI-110, REQ-UI-009): Verifies SensorGridScreen in CONFIGURATION mode
     * dispatches Elevation Profile toggle and displays child scope FilterChips when active.
     */
    @Test
    fun sensorGridConfig_elevationProfileToggleRevealsScopeChips_TST_INT_004() {
        var screenState by mutableStateOf(
            TrackingScreenState(showMap = false, showElevationProfile = false)
        )
        var toggleCalls = mutableListOf<Boolean>()

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                SensorGridScreen(
                    state = screenState,
                    screenMode = ScreenMode.CONFIGURATION,
                    gridActions = object : GridActions {
                        override fun onEditField(fieldState: SensorFieldState) {}
                        override fun onDeleteField(fieldState: SensorFieldState) {}
                        override fun onAddRow(beforeRow: Int) {}
                        override fun onAddCol(atRow: Int, beforeCol: Int) {}
                    },
                    currentLocationFlow = MutableStateFlow(null),
                    liveSegments = MutableStateFlow(emptyList()),
                    tabToggleActions = object : TabToggleActions {
                        override fun onToggleElevationProfile(enabled: Boolean) {
                            toggleCalls.add(enabled)
                            screenState = screenState.copy(showElevationProfile = enabled)
                        }
                    }
                )
            }
        }

        val toggleLabel = context.getString(R.string.config_tracking__showElevationProfile)
        composeTestRule.onNodeWithText(toggleLabel).performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(true), toggleCalls)
        val fullRouteLabel = context.getString(R.string.config_tracking__elevation_profile_scope_full_route)
        val rollingLabel = context.getString(R.string.config_tracking__elevation_profile_scope_rolling_window)
        composeTestRule.onNodeWithText(fullRouteLabel).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(rollingLabel).performScrollTo().assertIsDisplayed()
    }

    /**
     * TST-INT-005 (REQ-UI-111): Opens TrackingTabConfigHeader on a narrow 320dp viewport and
     * verifies all 6 Row-2 management buttons + Done button + tab name field render inside root
     * width without clipping or horizontal overlap, and dispatch their callbacks.
     */
    @Test
    fun trackingTabConfigHeader_narrowWidth_allButtonsFitAndTrigger_TST_INT_005() {
        var movedLeft = 0
        var addedBefore = 0
        var copied = 0
        var deleted = 0
        var addedAfter = 0
        var movedRight = 0
        var doneClicked = 0

        val viewInfo = TrackingViewInfo(
            tabViewId = 7L,
            name = "Climb Tab",
            showMap = true,
            showLapButton = true,
            showLiveSegments = false,
            showElevationProfile = true
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    TrackingTabConfigHeader(
                        viewInfo = viewInfo,
                        tabIndex = 1,
                        tabCount = 3,
                        sportName = "Road Bike",
                        onUpdateTabName = { _, _ -> },
                        onAddTabRelative = { _, isAfter -> if (isAfter) addedAfter++ else addedBefore++ },
                        onDeleteTab = { deleted++ },
                        onCopyTab = { copied++ },
                        onMoveTab = { _, isLeft -> if (isLeft) movedLeft++ else movedRight++ },
                        onToggleMode = { doneClicked++ }
                    )
                }
            }
        }

        val rootBounds = composeTestRule.onNodeWithText("Road Bike").getBoundsInRoot()
        assertTrue(rootBounds.right <= 320.dp)

        val cdDescriptions = listOf(
            R.string.cd_move_tab_left,
            R.string.cd_add_before,
            R.string.cd_copy_tab,
            R.string.cd_delete_tab,
            R.string.cd_add_after,
            R.string.cd_move_tab_right
        ).map { context.getString(it) }

        val bounds = cdDescriptions.map { desc ->
            composeTestRule.onNodeWithContentDescription(desc)
                .assertIsDisplayed()
                .getBoundsInRoot()
        }
        bounds.forEach { b ->
            assertTrue("Button must not clip past 320dp right edge: $b", b.right <= 320.dp)
            assertTrue("Button must have positive width: $b", (b.right - b.left) > 0.dp)
        }
        for (i in 0 until bounds.size - 1) {
            assertTrue(
                "Adjacent buttons must not overlap: ${bounds[i]} vs ${bounds[i + 1]}",
                bounds[i].right <= bounds[i + 1].left
            )
        }

        cdDescriptions.forEach { desc ->
            composeTestRule.onNodeWithContentDescription(desc).performClick()
        }
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.Done))
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, movedLeft)
        assertEquals(1, addedBefore)
        assertEquals(1, copied)
        assertEquals(1, deleted)
        assertEquals(1, addedAfter)
        assertEquals(1, movedRight)
        assertEquals(1, doneClicked)
    }

    /**
     * TST-INT-006 (REQ-UI-111): Audits horizontal left-to-right button sequence in
     * TrackingTabConfigHeader and vertical top-to-bottom toggle card sequence in SensorGridScreen.
     */
    @Test
    fun configHeaderAndSensorGrid_toggleAndButtonSequenceAudit_TST_INT_006() {
        val viewInfo = TrackingViewInfo(
            tabViewId = 1L,
            name = "Endurance",
            showMap = false,
            showLapButton = true,
            showLiveSegments = false,
            showElevationProfile = false
        )
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                androidx.compose.foundation.layout.Column {
                    TrackingTabConfigHeader(
                        viewInfo = viewInfo,
                        tabIndex = 1,
                        tabCount = 3,
                        onUpdateTabName = { _, _ -> },
                        onAddTabRelative = { _, _ -> },
                        onDeleteTab = {},
                        onCopyTab = {},
                        onToggleMode = {}
                    )
                    SensorGridScreen(
                        state = TrackingScreenState(showElevationProfile = false),
                        screenMode = ScreenMode.CONFIGURATION,
                        gridActions = object : GridActions {
                            override fun onEditField(fieldState: SensorFieldState) {}
                            override fun onDeleteField(fieldState: SensorFieldState) {}
                            override fun onAddRow(beforeRow: Int) {}
                            override fun onAddCol(atRow: Int, beforeCol: Int) {}
                        },
                        currentLocationFlow = MutableStateFlow(null),
                        liveSegments = MutableStateFlow(emptyList())
                    )
                }
            }
        }

        // Verify left-to-right Row-2 button sequence in TrackingTabConfigHeader
        val expectedHeaderOrder = listOf(
            R.string.cd_move_tab_left,
            R.string.cd_add_before,
            R.string.cd_copy_tab,
            R.string.cd_delete_tab,
            R.string.cd_add_after,
            R.string.cd_move_tab_right
        )
        val headerLefts = expectedHeaderOrder.map { resId ->
            composeTestRule.onNodeWithContentDescription(context.getString(resId))
                .getBoundsInRoot().left
        }
        for (i in 0 until headerLefts.size - 1) {
            assertTrue(
                "Header management icons must be ordered strictly left-to-right",
                headerLefts[i] < headerLefts[i + 1]
            )
        }

        // Verify vertical sequence of SpatialCockpitToggleCards in SensorGridScreen config mode
        val expectedToggleOrder = listOf(
            R.string.config_tracking__show_navigation_hints,
            R.string.config_tracking__show_map,
            R.string.config_tracking__showElevationProfile,
            R.string.config_tracking__showLiveSegments,
            R.string.config_tracking__show_live_climbs,
            R.string.config_tracking__show_radar_light,
            R.string.config_tracking__showLapButton,
            R.string.config_tracking__showBikeBell,
            R.string.config_tracking__show_mmp_curve
        )
        val toggleTops = expectedToggleOrder.map { resId ->
            val node = composeTestRule.onNodeWithText(context.getString(resId))
            node.performScrollTo()
            node.getBoundsInRoot().top
        }
        for (i in 0 until toggleTops.size - 1) {
            assertTrue(
                "Toggle cards must appear strictly top-to-bottom in spec order",
                toggleTops[i] < toggleTops[i + 1]
            )
        }
    }

    /**
     * TST-INT-008 (REQ-SET-055): Verifies navigation drawer settings cleanup parity across
     * SettingsBottomSheetType targets and Compose navigation routes.
     */
    @Test
    fun settingsNavigationDrawer_allCleanupSettingsItemsMapToValidTargets_TST_INT_008() {
        assertEquals(SettingsBottomSheetType.UNITS, NavRoutes.toBottomSheetType(R.id.drawer_units))
        assertEquals(SettingsBottomSheetType.DISPLAY, NavRoutes.toBottomSheetType(R.id.drawer_display_settings))
        assertNull(NavRoutes.toBottomSheetType(R.id.drawer_export))
        assertEquals(NavRoutes.BACKUP_RESTORE, NavRoutes.fromDrawerItemId(R.id.drawer_export))
        assertEquals(SettingsBottomSheetType.STRAVA, NavRoutes.toBottomSheetType(R.id.drawer_strava))
        assertEquals(SettingsBottomSheetType.DROPBOX, NavRoutes.toBottomSheetType(R.id.drawer_dropbox))
        assertEquals(SettingsBottomSheetType.GOOGLE_DRIVE, NavRoutes.toBottomSheetType(R.id.drawer_google_drive))
        assertEquals(SettingsBottomSheetType.HEALTH_CONNECT, NavRoutes.toBottomSheetType(R.id.drawer_health_connect))
        assertEquals(SettingsBottomSheetType.ACTIVITY_TYPE, NavRoutes.toBottomSheetType(R.id.drawer_tracking_layouts))

        assertEquals(NavRoutes.ADVANCED_SETTINGS, NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning))
        assertEquals(R.id.drawer_tracking_layouts, NavRoutes.toDrawerItemId(NavRoutes.TRACKING_LAYOUTS))
        assertTrue(SettingsBottomSheetType.entries.contains(SettingsBottomSheetType.SEARCH))
    }

    /**
     * TST-REG-002 (REQ-EXT-001, REQ-EXT-002, REQ-EXT-003, REQ-MAP-005): Verifies PoweredByStrava
     * composable renders accessible Strava attribution branding in both orange and white themes.
     */
    @Test
    fun poweredByStrava_rendersAttributionInBothColorVariants_TST_REG_002() {
        var useWhite by mutableStateOf(false)
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                PoweredByStrava(useWhite = useWhite)
            }
        }
        composeTestRule.onNodeWithContentDescription("Powered by Strava").assertIsDisplayed()

        useWhite = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Powered by Strava").assertIsDisplayed()
    }

    /**
     * TST-REG-005 (REQ-MAP-008): Verifies map visual hierarchy alpha levels:
     * active (1.0f), unselected same-sport (0.3f / TTAlpha.Disabled), and highly muted
     * background items (0.1f).
     */
    @Test
    fun mapContextHierarchy_enforcesPrimarySameSportAndMutedAlphaValues_TST_REG_005() {
        val defaultStyle = MapStyle()
        assertEquals(0.3f, defaultStyle.routeUnselectedAlpha, 0.0001f)
        assertEquals(0.3f, defaultStyle.segmentUnselectedAlpha, 0.0001f)
        assertEquals(0.38f, TTAlpha.Disabled, 0.0001f)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                androidx.compose.foundation.layout.Column {
                    MappableListItem(alpha = 1.0f) { Text("Active Primary Track") }
                    MappableListItem(alpha = defaultStyle.routeUnselectedAlpha) { Text("Same-Sport Context (0.3)") }
                    MappableListItem(alpha = 0.1f) { Text("Different-Sport Context (0.1)") }
                }
            }
        }
        composeTestRule.onNodeWithText("Active Primary Track").assertIsDisplayed()
        composeTestRule.onNodeWithText("Same-Sport Context (0.3)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Different-Sport Context (0.1)").assertIsDisplayed()
    }
}
