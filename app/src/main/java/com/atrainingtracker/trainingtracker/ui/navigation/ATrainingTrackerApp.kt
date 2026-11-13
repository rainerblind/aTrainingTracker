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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.atrainingtracker.trainingtracker.ui.onboarding.permission.PermissionOnboardingSheet
import com.atrainingtracker.trainingtracker.ui.settings.supporter.SupporterScreen
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.ant.AntServicesStatusSheet
import com.atrainingtracker.banalservice.ui.devices.devicelist.KeepTwoSensorsBottomSheet
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreen
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.ant.AntDialogState
import com.atrainingtracker.trainingtracker.ui.dialogs.FirstJubileeActionHelper
import com.atrainingtracker.trainingtracker.ui.dialogs.FirstJubileeCelebrationDialog
import com.atrainingtracker.trainingtracker.ui.ant.AntMissingAdapterDialog
import com.atrainingtracker.trainingtracker.ui.ant.AntMissingDependencyDialog
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
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteria
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModel
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteria
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersScreen
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModel
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentTabsScreen
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModel
import com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsScreen
import com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsViewModel
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
import com.atrainingtracker.trainingtracker.ui.settings.healthconnect.HealthConnectSettingsDialog
import com.atrainingtracker.trainingtracker.ui.settings.googledrive.GoogleDriveSettingsDialog
import com.atrainingtracker.trainingtracker.ui.help.TipsGuideScreen
import com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningDialog
import com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreen
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
 * Evaluates whether all essential runtime permissions and battery optimization exemption
 * are already granted on this device (REQ-PRI-007 / ATT-4455).
 */
fun checkAreAllPermissionsGranted(context: Context): Boolean {
    val hasLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasLocation) return false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasBtScan = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        val hasBtConnect = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasBtScan || !hasBtConnect) return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasNotification = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasNotification) return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) != true) {
            return false
        }
    }

    return true
}

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
    val context = LocalContext.current
    val tuningDataStore = remember { TuningPreferencesDataStore.getInstance(context) }
    val hasCompletedOnboarding by tuningDataStore.hasCompletedPermissionOnboardingFlow.collectAsState(initial = true)

    // Auto-skip on upgraded install if all permissions are already granted (REQ-PRI-007 / ATT-4455)
    LaunchedEffect(hasCompletedOnboarding) {
        if (!hasCompletedOnboarding && checkAreAllPermissionsGranted(context)) {
            tuningDataStore.setHasCompletedPermissionOnboarding(true)
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
    val isCurrentScreenPushed = currentBackStackEntry?.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true ||
        (currentBackStackEntry?.arguments?.getLong(NavRoutes.ARG_LOCATION_ID, -1L) ?: -1L) > 0 ||
        (currentBackStackEntry?.arguments?.getLong(NavRoutes.ARG_CLUSTER_ID, -1L) ?: -1L) > 0 ||
        (currentBackStackEntry?.arguments?.getString(NavRoutes.ARG_PROMPT_REASON) != null) ||
        (currentBackStackEntry?.arguments?.getString(NavRoutes.ARG_PLAN_ID) != null)
    val currentRoute = currentBackStackEntry?.destination?.route ?: NavRoutes.START_TRACKING

    // Keep drawerController.selectedItemId in sync with active route
    LaunchedEffect(currentRoute) {
        val mappedId = NavRoutes.toDrawerItemId(currentRoute)
        drawerController.selectedItemId = mappedId
    }

    // Authoritative drawer visibility predicate across all animation phases (REQ-UI-162)
    val isDrawerVisible = drawerState.currentValue != DrawerValue.Closed ||
        drawerState.targetValue != DrawerValue.Closed ||
        drawerState.isAnimationRunning

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
            // REQ-TRK-059.8, ATT-6710: Move task to back instead of finish() to preserve Activity and ViewModelStore
            activity.moveTaskToBack(true)
        }
    }

    // Layer 2: Settings Bottom Sheet Overlay (dynamically composed when active)
    if (drawerController.activeBottomSheet != null) {
        BackHandler {
            drawerController.activeBottomSheet = null
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // REQ-UI-161: Restrict swipe-to-open gesture detection strictly to the leftmost 40dp edge
        // interceptor when closed, preventing unintended drawer opening during map panning and child
        // scrolling. When open, full-screen gestures are enabled for swipe-to-close and scrim tap dismissal.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            // Layer 1: Navigation Drawer Overlay BackHandler (REQ-UI-162)
            // Dynamically composed when isDrawerVisible is true, appending callback to the top of OnBackPressedDispatcher LIFO queue
            if (isDrawerVisible) {
                BackHandler {
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
            }
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
                .pointerInput(drawerState, isCurrentScreenPushed) {
                    if (isCurrentScreenPushed) return@pointerInput
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
                        viewModelStoreOwner = activity,
                        factory = TrackingTabsViewModelFactory(activity.application)
                    )
                    TrackingTabsScreen(
                        trackingTabsViewModel = trackingTabsViewModel,
                        onNavigateToMorningHrv = { navController.navigate(NavRoutes.MORNING_HRV) }
                    )
                }

                composable(
                    route = NavRoutes.WORKOUTS_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_PUSHED) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { backStackEntry ->
                    val isPushed = backStackEntry.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true
                    val summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)
                    val trackOnMapViewModel: TrackOnMapAftermathViewModel = viewModel(activity)
                    WorkoutSummariesTabbedScreen(
                        viewModel = summariesViewModel,
                        trackOnMapViewModel = trackOnMapViewModel,
                        isPushed = isPushed,
                        onMenuClick = if (isPushed) null else { { drawerController.openDrawer() } },
                        onBackClick = if (isPushed) {
                            {
                                summariesViewModel.clearFilterCriteria()
                                navController.popBackStack()
                            }
                        } else null
                    )
                }

                composable(NavRoutes.PERIODS) {
                    val periodsViewModel: PeriodsViewModel = viewModel(activity)
                    PeriodsScreen(
                        viewModel = periodsViewModel,
                        onStartWorkoutList = { periodSummary, bSportType, scrollToWorkoutId ->
                            activity.startWorkoutSummaryListFromPeriod(periodSummary, bSportType, scrollToWorkoutId)
                        },
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(
                    route = NavRoutes.MAP_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_LOCATION_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        }
                    )
                ) { backStackEntry ->
                    val locationIdArg = backStackEntry.arguments?.getLong(NavRoutes.ARG_LOCATION_ID)?.takeIf { it > 0 }
                    val mapViewModel: MapFragmentWithTrackViewModel = viewModel(activity)
                    MapScreenWithTrack(
                        viewModel = mapViewModel,
                        targetLocationId = locationIdArg,
                        onMenuClick = if (locationIdArg == null) { { drawerController.openDrawer() } } else null,
                        onNavigateBack = if (locationIdArg != null) { { navController.popBackStack() } } else null
                    )
                }

                composable(NavRoutes.SEGMENTS) {
                    val segmentViewModel: SegmentListViewModel = viewModel(
                        factory = SegmentListViewModel.SegmentListViewModelFactory(activity)
                    )
                    StarredSegmentsScreen(
                        viewModel = segmentViewModel,
                        onConnectToStrava = { activity.navigateToDrawerItem(R.id.drawer_strava) },
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(NavRoutes.ROUTES) {
                    val routesViewModel: RoutesViewModel = viewModel(activity)
                    RoutesScreen(
                        viewModel = routesViewModel,
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(NavRoutes.START_LOCATIONS) {
                    val knownLocationsViewModel: KnownLocationsViewModel = viewModel(activity)
                    val summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)
                    val clustersViewModel: WorkoutClustersViewModel = viewModel(activity)
                    KnownLocationsScreen(
                        viewModel = knownLocationsViewModel,
                        onMenuClick = { drawerController.openDrawer() },
                        onShowOnMap = { locationId ->
                            navController.navigate(NavRoutes.map(locationId))
                        },
                        onShowWorkouts = { locationItem ->
                            val criteria = WorkoutFilterCriteria(
                                startLocationName = locationItem.name,
                                startLocationLat = locationItem.latLng.latitude,
                                startLocationLng = locationItem.latLng.longitude,
                                startLocationRadiusM = locationItem.radius.toDouble().takeIf { it > 0.0 } ?: 200.0
                            )
                            summariesViewModel.setFilterCriteria(criteria)
                            navController.navigate(NavRoutes.workouts(pushed = true))
                        },
                        onShowWorkoutsForSport = { locationItem, bSportType ->
                            val criteria = WorkoutFilterCriteria(
                                startLocationName = locationItem.name,
                                startLocationLat = locationItem.latLng.latitude,
                                startLocationLng = locationItem.latLng.longitude,
                                startLocationRadiusM = locationItem.radius.toDouble().takeIf { it > 0.0 } ?: 200.0,
                                bSportType = bSportType
                            )
                            summariesViewModel.setFilterCriteria(criteria)
                            navController.navigate(NavRoutes.workouts(pushed = true))
                        },
                        onSelectCluster = { clusterId ->
                            navController.navigate(NavRoutes.locations(clusterId))
                        },
                        onShowRoutes = { locationItem ->
                            val criteria = ClusterFilterCriteria(
                                startLocationName = locationItem.name,
                                startLocationLat = locationItem.latLng.latitude,
                                startLocationLng = locationItem.latLng.longitude,
                                startLocationRadiusM = locationItem.radius.toDouble().takeIf { it > 0.0 } ?: 200.0
                            )
                            clustersViewModel.setFilterCriteria(criteria)
                            navController.navigate(NavRoutes.LOCATIONS)
                        }
                    )
                }

                composable(
                    route = NavRoutes.LOCATIONS_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_CLUSTER_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        }
                    )
                ) { backStackEntry ->
                    val clusterIdArg = backStackEntry.arguments?.getLong(NavRoutes.ARG_CLUSTER_ID)?.takeIf { it > 0 }
                    val clustersViewModel: WorkoutClustersViewModel = viewModel(activity)
                    val summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)
                    val trackOnMapViewModel: TrackOnMapAftermathViewModel = viewModel(activity)
                    val clusterFilterCriteria by clustersViewModel.filterCriteria.collectAsState()
                    val isPushed = clusterIdArg != null || clusterFilterCriteria.startLocationName != null
                    WorkoutClustersScreen(
                        viewModel = clustersViewModel,
                        summariesViewModel = summariesViewModel,
                        trackOnMapViewModel = trackOnMapViewModel,
                        initialClusterId = clusterIdArg,
                        isPushed = isPushed,
                        onMenuClick = if (isPushed) null else { { drawerController.openDrawer() } },
                        onBackClick = if (isPushed) {
                            {
                                clustersViewModel.clearFilterCriteria()
                                clustersViewModel.selectCluster(null)
                                navController.popBackStack()
                            }
                        } else null,
                        onBackToNav = {
                            clustersViewModel.clearFilterCriteria()
                            clustersViewModel.selectCluster(null)
                            navController.popBackStack()
                        },
                        onNavigateToAdvancedSettings = {
                            navController.navigate(NavRoutes.advancedSettings(pushed = true))
                        }
                    )
                }

                composable(NavRoutes.SENSORS) {
                    val tabViewModel: DevicesTabbedViewModel = viewModel(activity)
                    val equipmentViewModel: EquipmentViewModel = viewModel(activity)
                    DevicesTabbedScreen(
                        tabViewModel = tabViewModel,
                        equipmentViewModel = equipmentViewModel,
                        initialTab = 2,
                        onNavigateToZones = { initialTab, promptReason ->
                            navController.navigate(NavRoutes.trainingZones(initialTab, promptReason))
                        },
                        onNavigateToSupporter = { planId ->
                            navController.navigate(NavRoutes.supporter(planId, pushed = true))
                        },
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(NavRoutes.EQUIPMENT) {
                    val equipmentViewModel: EquipmentViewModel = viewModel(activity)
                    EquipmentTabsScreen(
                        viewModel = equipmentViewModel,
                        initialTab = 0,
                        onNavigateToWorkouts = { stats ->
                            activity.navigateToFilteredWorkouts(stats)
                        },
                        onNavigateToSupporter = { planId ->
                            navController.navigate(NavRoutes.supporter(planId, pushed = true))
                        },
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(NavRoutes.BIKES) {
                    val equipmentViewModel: EquipmentViewModel = viewModel(activity)
                    EquipmentTabsScreen(
                        viewModel = equipmentViewModel,
                        initialTab = 0,
                        onNavigateToWorkouts = { stats ->
                            activity.navigateToFilteredWorkouts(stats)
                        },
                        onNavigateToSupporter = { planId ->
                            navController.navigate(NavRoutes.supporter(planId, pushed = true))
                        },
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(NavRoutes.SHOES) {
                    val equipmentViewModel: EquipmentViewModel = viewModel(activity)
                    EquipmentTabsScreen(
                        viewModel = equipmentViewModel,
                        initialTab = 1,
                        onNavigateToWorkouts = { stats ->
                            activity.navigateToFilteredWorkouts(stats)
                        },
                        onNavigateToSupporter = { planId ->
                            navController.navigate(NavRoutes.supporter(planId, pushed = true))
                        },
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(NavRoutes.SPORT_TYPES) {
                    val sportTypeViewModel: SportTypeViewModel = viewModel(activity)
                    SportTypesTabsScreen(
                        viewModel = sportTypeViewModel,
                        onNavigateToWorkouts = { stats ->
                            activity.navigateToFilteredWorkouts(stats)
                        },
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(NavRoutes.AUTO_PAUSE) {
                    val autoPauseViewModel: com.atrainingtracker.trainingtracker.ui.settings.autopause.AutoPauseViewModel = viewModel(activity)
                    com.atrainingtracker.trainingtracker.ui.settings.autopause.AutoPauseTabsScreen(
                        viewModel = autoPauseViewModel,
                        onOpenDrawer = { activity.openDrawer() }
                    )
                }

                composable(
                    route = NavRoutes.TRAINING_ZONES_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_INITIAL_TAB) {
                            type = NavType.IntType
                            defaultValue = 0
                        },
                        navArgument(NavRoutes.ARG_PROMPT_REASON) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(NavRoutes.ARG_PUSHED) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { backStackEntry ->
                    val initialTab = backStackEntry.arguments?.getInt(NavRoutes.ARG_INITIAL_TAB) ?: 0
                    val promptReason = backStackEntry.arguments?.getString(NavRoutes.ARG_PROMPT_REASON)
                    val isPushed = backStackEntry.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true || promptReason != null
                    ZoneSettingsScreen(
                        initialTab = initialTab,
                        promptReason = promptReason,
                        onFinish = { navController.popBackStack() },
                        onOpenDrawer = if (isPushed) null else { { activity.openDrawer() } },
                        onNavigateToHrvTest = { navController.navigate(NavRoutes.HRV_TEST) }
                    )
                }

                composable(NavRoutes.ATHLETE_PROFILE) {
                    val athleteProfileViewModel: com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileViewModel = viewModel(activity)
                    com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileScreen(
                        viewModel = athleteProfileViewModel,
                        onOpenDrawer = { activity.openDrawer() },
                        onNavigateToMorningHrv = { navController.navigate(NavRoutes.MORNING_HRV) }
                    )
                }

                composable(NavRoutes.BACKUP_RESTORE) {
                    val backupRestoreViewModel: BackupRestoreViewModel = viewModel(activity)
                    ImportBackupTabsScreen(
                        viewModel = backupRestoreViewModel,
                        onMenuClick = { drawerController.openDrawer() }
                    )
                }

                composable(
                    route = NavRoutes.ADVANCED_SETTINGS_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_PUSHED) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { backStackEntry ->
                    val isPushed = backStackEntry.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true
                    AdvancedTuningScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenDrawer = if (isPushed) null else { { activity.openDrawer() } },
                        onSettingsChanged = { activity.applyDisplaySettings() }
                    )
                }

                composable(
                    route = NavRoutes.SUPPORTER_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_PLAN_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(NavRoutes.ARG_PUSHED) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { backStackEntry ->
                    val planId = backStackEntry.arguments?.getString(NavRoutes.ARG_PLAN_ID)
                    val isPushed = backStackEntry.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true || planId != null
                    SupporterScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenDrawer = if (isPushed) null else { { activity.openDrawer() } },
                        preSelectedPlanId = planId
                    )
                }

                composable(
                    route = NavRoutes.TIPS_GUIDE_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_PUSHED) {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { backStackEntry ->
                    val isPushed = backStackEntry.arguments?.getBoolean(NavRoutes.ARG_PUSHED) == true
                    TipsGuideScreen(
                        onNavigateBack = {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                activity.navigateToDrawerItem(R.id.drawer_start_tracking)
                            }
                        },
                        onOpenDrawer = if (isPushed) null else { { drawerController.openDrawer() } },
                        onNavigateToRoute = { route ->
                            when (route) {
                                NavRoutes.LOCATIONS -> activity.navigateToDrawerItem(R.id.drawer_my_locations)
                                NavRoutes.SENSORS -> activity.navigateToDrawerItem(R.id.drawer_my_sensors)
                                NavRoutes.SPORT_TYPES -> activity.navigateToDrawerItem(R.id.drawer_sport_types)
                                NavRoutes.TRACKING_LAYOUTS -> activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)
                                NavRoutes.SEGMENTS -> activity.navigateToDrawerItem(R.id.drawer_segments)
                                NavRoutes.EQUIPMENT -> activity.navigateToDrawerItem(R.id.drawer_equipment)
                                NavRoutes.BACKUP_RESTORE -> activity.navigateToDrawerItem(R.id.drawer_backup_restore)
                                NavRoutes.SUPPORTER -> activity.navigateToDrawerItem(R.id.drawer_supporter)
                                NavRoutes.TRAINING_ZONES -> navController.navigate(NavRoutes.trainingZones(pushed = true))
                                NavRoutes.ADVANCED_SETTINGS -> navController.navigate(NavRoutes.advancedSettings(pushed = true))
                                else -> navController.navigate(route)
                            }
                        }
                    )
                }

                composable(NavRoutes.MORNING_HRV) {
                    val morningHrvViewModel: com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckViewModel = viewModel()
                    com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckScreen(
                        viewModel = morningHrvViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(NavRoutes.HRV_TEST) {
                    val hrvRampTestViewModel: com.atrainingtracker.trainingtracker.ui.hrv.HrvRampTestViewModel = viewModel()
                    com.atrainingtracker.trainingtracker.ui.hrv.HrvRampTestScreen(
                        viewModel = hrvRampTestViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // Attach NavController to Activity for direct intent / broadcast routing (ATT-7526)
            // Positioned inside Scaffold after NavHost to guarantee graph is attached before callbacks run.
            DisposableEffect(navController) {
                activity.onNavControllerReady(navController)
                onDispose {
                    activity.navController = null
                }
            }

            // Restore or route initial destination if non-default selectedItemId (ATT-7526)
            LaunchedEffect(Unit) {
                if (drawerController.selectedItemId != R.id.drawer_start_tracking) {
                    activity.navigateToDrawerItem(drawerController.selectedItemId)
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
                SettingsBottomSheetType.GOOGLE_DRIVE -> GoogleDriveSettingsDialog(
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
                    onSettingsChanged = { activity.applyDisplaySettings() },
                    onNavigateToTuning = {
                        drawerController.activeBottomSheet = null
                        navController.navigate(NavRoutes.advancedSettings(pushed = true))
                    }
                )
                SettingsBottomSheetType.ADVANCED_TUNING -> AdvancedTuningDialog(
                    onDismiss = { drawerController.activeBottomSheet = null }
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
                SettingsBottomSheetType.HEALTH_CONNECT -> HealthConnectSettingsDialog(
                    onDismiss = { drawerController.activeBottomSheet = null }
                )
            }
        }
    }

    // Material 3 ANT+ Alert Dialogs & Status Sheet (REQ-UI-323 / ATT-2939)
    when (val dialogState = activity.antDialogState) {
        is AntDialogState.MissingAdapter -> {
            val scope = rememberCoroutineScope()
            val tuningDataStore = remember { TuningPreferencesDataStore(activity.applicationContext) }
            AntMissingAdapterDialog(
                onViewStatus = {
                    activity.antDialogState = null
                    activity.showAntStatusSheet = true
                },
                onDismiss = {
                    activity.antDialogState = null
                    scope.launch {
                        tuningDataStore.setSuppressAntMissingAdapterDialog(true)
                    }
                }
            )
        }
        is AntDialogState.MissingDependency -> {
            val context = LocalContext.current
            AntMissingDependencyDialog(
                dependencyName = dialogState.dependencyName,
                onGoToStore = {
                    activity.antDialogState = null
                    val startStore = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=${dialogState.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(startStore)
                    } catch (e: Exception) {
                        val webIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=${dialogState.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                    }
                },
                onDismiss = {
                    activity.antDialogState = null
                }
            )
        }
        null -> Unit
    }

    // Material 3 First-Jubilee Celebration Dialog (REQ-REV-003, REQ-REV-004 / ATT-2308, ATT-5658)
    activity.firstJubileeCelebrationState?.let { jubileeData ->
        val proFeatureManager = remember { ProFeatureManager(context) }
        val effectiveTier by proFeatureManager.effectiveTier.collectAsState()
        val settingsDataStore = remember { SettingsDataStore.getInstance(context) }
        val jubileeScope = rememberCoroutineScope()

        FirstJubileeCelebrationDialog(
            count = jubileeData.count,
            sportName = jubileeData.sportName,
            supporterTier = effectiveTier,
            onRequestInAppReview = {
                activity.firstJubileeCelebrationState = null
                jubileeScope.launch {
                    settingsDataStore.setHasShownFirstJubileePrompt(true)
                }
                FirstJubileeActionHelper.launchInAppReviewOrStore(activity)
            },
            onSendFeedbackEmail = {
                activity.firstJubileeCelebrationState = null
                jubileeScope.launch {
                    settingsDataStore.setHasShownFirstJubileePrompt(true)
                }
                FirstJubileeActionHelper.launchFeedbackEmail(activity)
            },
            onNavigateToSupporter = {
                activity.firstJubileeCelebrationState = null
                jubileeScope.launch {
                    settingsDataStore.setHasShownFirstJubileePrompt(true)
                }
                activity.navigateToDrawerItem(R.id.drawer_supporter)
            },
            onDismiss = {
                activity.firstJubileeCelebrationState = null
                jubileeScope.launch {
                    settingsDataStore.setHasShownFirstJubileePrompt(true)
                }
            }
        )
    }

    if (activity.showAntStatusSheet) {
        AntServicesStatusSheet(
            onDismiss = { activity.showAntStatusSheet = false }
        )
    }

    // Subscription Downgrade Keep-2-Sensors Reconciliation Bottom Sheet (REQ-SET-084.5)
    val reconciliationManager = remember { SensorQuotaReconciliationManager.getInstance(context) }
    val keepTwoSensorsEvent by reconciliationManager.keepTwoSensorsDialogEvent.collectAsState()
    val proFeatureManager = remember { ProFeatureManager.getInstance(context) }
    val isProActive by proFeatureManager.isProActive.collectAsState()

    LaunchedEffect(isProActive) {
        if (!isProActive) {
            reconciliationManager.checkDowngradeReconciliation()
        }
    }

    keepTwoSensorsEvent?.let { event ->
        KeepTwoSensorsBottomSheet(
            currentlyPairedDevices = event.currentlyPairedDevices,
            defaultKeepDeviceIds = event.defaultKeepDeviceIds,
            onConfirmSelection = { selectedIds ->
                reconciliationManager.confirmKeepTwoSensors(selectedIds)
            },
            onNavigateToSupporter = {
                activity.navigateToDrawerItem(R.id.drawer_supporter)
            },
            onDismiss = {
                reconciliationManager.dismissKeepTwoSensors()
            }
        )
    }

    // First-install Permission Onboarding Flow (REQ-PRI-007 / ATT-4455 / Rule 47)
    if (!hasCompletedOnboarding && !checkAreAllPermissionsGranted(context)) {
        PermissionOnboardingSheet(
            onFinish = {
                scope.launch {
                    tuningDataStore.setHasCompletedPermissionOnboarding(true)
                }
            }
        )
    }
}
