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

package com.atrainingtracker.trainingtracker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreen
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModel
import com.atrainingtracker.banalservice.ui.sporttype.SportTypeViewModel
import com.atrainingtracker.banalservice.ui.sporttype.SportTypesTabsScreen
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.activities.ZoneSettingsScreen
import com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModel
import com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreen
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsViewModel
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModel
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersScreen
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModel
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentTabsScreen
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModel
import com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrackViewModel
import com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrack
import com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel
import com.atrainingtracker.trainingtracker.ui.routes.RoutesScreen
import com.atrainingtracker.trainingtracker.ui.routes.RoutesViewModel
import com.atrainingtracker.trainingtracker.ui.segments.segmentlist.SegmentListViewModel
import com.atrainingtracker.trainingtracker.ui.segments.segmentlist.StarredSegmentsScreen
import com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialog
import com.atrainingtracker.trainingtracker.ui.settings.dropbox.DropboxSettingsDialog
import com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialog
import com.atrainingtracker.trainingtracker.ui.settings.search.SearchSettingsDialog
import com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialog
import com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionDialog
import com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialog
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreen
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Root Composable hosting the pure Jetpack Compose single-activity architecture (REQ-UI-159).
 *
 * Incorporates Material 3 [ModalNavigationDrawer], [NavHost] mapping all 21 destinations,
 * native [ModalBottomSheet] settings dialogs with IME and system bars insets, and back navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ATrainingTrackerApp(
    activity: MainActivityWithNavigation,
    drawerController: NavigationDrawerController,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Attach NavController to Activity for direct intent / broadcast routing
    DisposableEffect(navController) {
        activity.navController = navController
        onDispose {
            activity.navController = null
        }
    }

    // Bind drawerController to Compose DrawerState as Single Source of Truth (REQ-UI-162)
    DisposableEffect(drawerState, scope) {
        drawerController.bindDrawer(
            open = { scope.launch { drawerState.open() } },
            close = { scope.launch { drawerState.close() } },
            isOpen = { drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed }
        )
        onDispose {
            drawerController.unbindDrawer()
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: NavRoutes.START_TRACKING

    // Keep drawerController.selectedItemId in sync with active route
    LaunchedEffect(currentRoute) {
        val mappedId = NavRoutes.toDrawerItemId(currentRoute)
        drawerController.selectedItemId = mappedId
    }

    // Restore or route initial destination if non-default selectedItemId
    LaunchedEffect(Unit) {
        if (drawerController.selectedItemId != R.id.drawer_start_tracking) {
            activity.navigateToDrawerItem(drawerController.selectedItemId)
        }
    }

    // Authoritative drawer visibility predicate across all animation phases (REQ-UI-162)
    val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed

    // --- Back Handling Hierarchy (LIFO composition ordering) ---
    // Layer 3: Root Screen Navigation (composed first -> evaluated last)
    BackHandler(enabled = !isDrawerVisible && drawerController.activeBottomSheet == null) {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else if (currentRoute != NavRoutes.START_TRACKING) {
            if (currentRoute == NavRoutes.WORKOUTS) {
                MyPreferenceManager(activity).clearWorkoutFilterCriteria()
            } else if (currentRoute == NavRoutes.ROUTES) {
                MyPreferenceManager(activity).clearRouteFilterCriteria()
            } else if (currentRoute == NavRoutes.LOCATIONS) {
                MyPreferenceManager(activity).clearClusterFilterCriteria()
            }
            activity.navigateToDrawerItem(R.id.drawer_start_tracking)
        } else {
            activity.finish()
        }
    }

    // Layer 2: Settings Bottom Sheet Overlay (composed second)
    BackHandler(enabled = drawerController.activeBottomSheet != null) {
        drawerController.activeBottomSheet = null
    }

    // Layer 1: Navigation Drawer Overlay (composed last -> evaluated first)
    BackHandler(enabled = isDrawerVisible) {
        scope.launch {
            try {
                withTimeoutOrNull(400L) {
                    drawerState.close()
                } ?: drawerState.snapTo(DrawerValue.Closed)
            } catch (_: CancellationException) {
                // Cooperative cancellation on rapid consecutive back taps
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // REQ-UI-161: Restrict swipe-to-open gesture detection strictly to the leftmost 40dp edge
        // interceptor when closed, preventing unintended drawer opening during map panning and child
        // scrolling. When open, full-screen gestures are enabled for swipe-to-close and scrim tap dismissal.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.statusBars
            ) {
                AppNavigationDrawer(
                    selectedItemId = drawerController.selectedItemId,
                    startTrackingTitleRes = drawerController.startTrackingTitleRes,
                    onItemSelected = { itemId ->
                        scope.launch { drawerState.close() }
                        activity.navigateToDrawerItem(itemId)
                    }
                )
            }
        },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(drawerState) {
                    val edgeThresholdPx = 40.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        if (down.position.x <= edgeThresholdPx && !drawerState.isOpen) {
                            var isEdgeSwipe = false
                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val totalDx = change.position.x - down.position.x
                                val totalDy = abs(change.position.y - down.position.y)

                                if (!isEdgeSwipe && totalDx > viewConfiguration.touchSlop && totalDx > totalDy) {
                                    isEdgeSwipe = true
                                    change.consume()
                                    scope.launch {
                                        drawerState.open()
                                    }
                                } else if (isEdgeSwipe) {
                                    change.consume()
                                }
                            } while (change.pressed)
                        }
                    }
                }
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp)
            ) { paddingValues ->
                NavHost(
                navController = navController,
                startDestination = NavRoutes.START_TRACKING,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                composable(NavRoutes.START_TRACKING) {
                    val trackingTabsViewModel: TrackingTabsViewModel = viewModel(
                        factory = TrackingTabsViewModelFactory(activity.application)
                    )
                    LaunchedEffect(activity.pendingActivityType) {
                        activity.pendingActivityType?.let { type ->
                            trackingTabsViewModel.setExplicitActivityType(type)
                            trackingTabsViewModel.setScreenMode(ScreenMode.CONFIGURATION)
                            activity.pendingActivityType = null
                        }
                    }
                    TrackingTabsScreen(trackingTabsViewModel = trackingTabsViewModel)
                }

                composable(NavRoutes.WORKOUTS) {
                    val summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)
                    val trackOnMapViewModel: TrackOnMapAftermathViewModel = viewModel(activity)
                    WorkoutSummariesTabbedScreen(
                        viewModel = summariesViewModel,
                        trackOnMapViewModel = trackOnMapViewModel
                    )
                }

                composable(NavRoutes.PERIODS) {
                    val periodsViewModel: PeriodsViewModel = viewModel(activity)
                    PeriodsScreen(
                        viewModel = periodsViewModel,
                        onStartWorkoutList = { periodSummary, bSportType, scrollToWorkoutId ->
                            activity.startWorkoutSummaryListFromPeriod(periodSummary, bSportType, scrollToWorkoutId)
                        }
                    )
                }

                composable(NavRoutes.MAP) {
                    val mapViewModel: MapFragmentWithTrackViewModel = viewModel(activity)
                    MapScreenWithTrack(viewModel = mapViewModel)
                }

                composable(NavRoutes.SEGMENTS) {
                    val segmentViewModel: SegmentListViewModel = viewModel(
                        factory = SegmentListViewModel.SegmentListViewModelFactory(activity)
                    )
                    StarredSegmentsScreen(
                        viewModel = segmentViewModel,
                        onConnectToStrava = { activity.navigateToDrawerItem(R.id.drawer_strava) }
                    )
                }

                composable(NavRoutes.ROUTES) {
                    val routesViewModel: RoutesViewModel = viewModel(activity)
                    RoutesScreen(viewModel = routesViewModel)
                }

                composable(NavRoutes.LOCATIONS) {
                    val clustersViewModel: WorkoutClustersViewModel = viewModel(activity)
                    val summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)
                    val trackOnMapViewModel: TrackOnMapAftermathViewModel = viewModel(activity)
                    WorkoutClustersScreen(
                        viewModel = clustersViewModel,
                        summariesViewModel = summariesViewModel,
                        trackOnMapViewModel = trackOnMapViewModel
                    )
                }

                composable(NavRoutes.SENSORS) {
                    val tabViewModel: DevicesTabbedViewModel = viewModel(activity)
                    DevicesTabbedScreen(
                        tabViewModel = tabViewModel,
                        initialTab = 2,
                        onCheckAntInstallation = {
                            InstallANTShitDialog().show(activity.supportFragmentManager, InstallANTShitDialog.TAG)
                        }
                    )
                }

                composable(NavRoutes.BIKES) {
                    val equipmentViewModel: EquipmentViewModel = viewModel(activity)
                    EquipmentTabsScreen(
                        viewModel = equipmentViewModel,
                        initialTab = 0,
                        onNavigateToWorkouts = { stats ->
                            activity.navigateToFilteredWorkouts(stats)
                        }
                    )
                }

                composable(NavRoutes.SHOES) {
                    val equipmentViewModel: EquipmentViewModel = viewModel(activity)
                    EquipmentTabsScreen(
                        viewModel = equipmentViewModel,
                        initialTab = 1,
                        onNavigateToWorkouts = { stats ->
                            activity.navigateToFilteredWorkouts(stats)
                        }
                    )
                }

                composable(NavRoutes.SPORT_TYPES) {
                    val sportTypeViewModel: SportTypeViewModel = viewModel(activity)
                    SportTypesTabsScreen(
                        viewModel = sportTypeViewModel,
                        onNavigateToWorkouts = { stats ->
                            activity.navigateToFilteredWorkouts(stats)
                        }
                    )
                }

                composable(NavRoutes.TRAINING_ZONES) {
                    ZoneSettingsScreen()
                }

                composable(NavRoutes.BACKUP_RESTORE) {
                    val backupRestoreViewModel: BackupRestoreViewModel = viewModel(activity)
                    ImportBackupTabsScreen(viewModel = backupRestoreViewModel)
                }
            }
        }
    }
    }

    // Native Compose ModalBottomSheet hosting for Settings Dialogs
    drawerController.activeBottomSheet?.let { sheetType ->
        ModalBottomSheet(
            onDismissRequest = { drawerController.activeBottomSheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
            modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            when (sheetType) {
                SettingsBottomSheetType.STRAVA -> StravaSettingsDialog(
                    onDismiss = { drawerController.activeBottomSheet = null }
                )
                SettingsBottomSheetType.DROPBOX -> DropboxSettingsDialog(
                    onDismiss = { drawerController.activeBottomSheet = null }
                )
                SettingsBottomSheetType.EXPORT -> ExportSettingsDialog(
                    onDismiss = { drawerController.activeBottomSheet = null }
                )
                SettingsBottomSheetType.UNITS -> UnitsSettingsDialog(
                    onDismiss = { drawerController.activeBottomSheet = null }
                )
                SettingsBottomSheetType.DISPLAY -> DisplaySettingsDialog(
                    onDismiss = { drawerController.activeBottomSheet = null },
                    onSettingsChanged = { activity.applyDisplaySettings() }
                )
                SettingsBottomSheetType.SEARCH -> SearchSettingsDialog(
                    onDismiss = { drawerController.activeBottomSheet = null }
                )
                SettingsBottomSheetType.ACTIVITY_TYPE -> ActivityTypeSelectionDialog(
                    onTypeSelected = { activityType ->
                        drawerController.activeBottomSheet = null
                        activity.onActivityTypeSelected(activityType)
                    },
                    onDismiss = { drawerController.activeBottomSheet = null }
                )
            }
        }
    }
}
