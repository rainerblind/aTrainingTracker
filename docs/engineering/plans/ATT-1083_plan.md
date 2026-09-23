# Implementation Plan - ATT-1083

**Ticket**: `ATT-1083`: `[Verbesserung] Migrate MainActivityWithNavigation to Jetpack Compose single-activity architecture`  
**Parent / Epic**: `ATT-236`: Modernize UI & Architecture  
**Sub-task**: `ATT-1305`: `[Impl-Plan] Migrate MainActivityWithNavigation to Jetpack Compose single-activity architecture`  
**Component**: Core UI / Navigation Host Architecture (`MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`, `main_activity_with_navigation.xml`, `app/build.gradle`)  
**Sprint**: `2026-39.2`  
**Target Release**: `V4.9.38`  
**Requirement Mapping**: `REQ-UI-159`, `REQ-UI-124`, `REQ-UI-123`, `REQ-SET-050`, `REQ-SET-052`, `REQ-STB-003`, `REQ-STB-007`  
**Test Specification**: `TST-NAV-009`, `TST-NAV-008`, `TST-NAV-007`, `TST-NAV-004`  
**Stage**: `Stage 3: Implementation Planning (SWE.3)`

---

## 1. Executive Summary & Architectural Overview

The goal of this task is to modernize `MainActivityWithNavigation.kt`—the central cockpit and lifecycle anchor of **aTrainingTracker**—into a pure Jetpack Compose single-activity architecture.

By replacing the legacy Android View `DrawerLayout`, `FrameLayout` fragment replacements, and separate `BottomSheetDialogFragment` child window allocations with native Compose components (`ATrainingTrackerApp`, Material 3 `ModalNavigationDrawer`, `NavHost` / `NavController`, and native `ModalBottomSheet`), the application eliminates:
1. Double-window decor allocations and black status/navigation bar flashes during settings dialog popups.
2. Fragment transaction overhead and boilerplate `ComposeView` fragment wrappers across 12 screens.
3. Asynchronous state-synchronization friction between Android View and Compose lifecycles.
4. Redundant window manager transitions and layout inflation overhead.

---

## 2. Requirement & Test Specification Mapping

| Requirement ID | Component / File | Description | Verification Test ID |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-159`** | `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `app/build.gradle` | Pure Compose single-activity root (`setContent`), Material 3 drawer, `NavHost` routing, native bottom sheets, IME insets, and configuration-change concurrency guards for `BANALService`. | `TST-NAV-009` |
| **`REQ-UI-124`** | `MainActivityWithNavigation.kt`, `MainActivityWithNavigationInteropTest.kt` | Java interoperability contracts (`@JvmField` constants, `SelectedFragment` enum, public methods). | `TST-NAV-008` |
| **`REQ-UI-123`** | `AppNavigationDrawer.kt` | Declarative navigation drawer items, compact density, reactive title updates. | `TST-NAV-007` |
| **`REQ-SET-052`** | `MainActivityWithNavigation.kt` | Dynamic display settings flags (`keepScreenOn`, `FLAG_SHOW_WHEN_LOCKED`, orientation lock). | `TST-NAV-004` |
| **`REQ-STB-003`** | `MainActivityWithNavigation.kt` | Interrupted workout recovery (`EXTRA_RESUME_INTERRUPTED_WORKOUT`, `StartOrResumeInterface`). | `TST-STB-003` |
| **`REQ-STB-007`** | `app/build.gradle` | AndroidX Core `1.15.0` strict pinning and Android 14 insets crash immunity. | `TST-STB-007` |

---

## 3. Impact Analysis & Blast Radius Audit

### 3.1 Call-Site & Caller Inventory

| Caller / Component | Call Site / Interaction | Proposed Architectural Strategy |
| :--- | :--- | :--- |
| `AndroidManifest.xml` | `<activity android:name=".activities.MainActivityWithNavigation" ...>` | Preserved 100%: Class name and manifest configuration unchanged. Add `android:windowSoftInputMode="adjustResize"`. |
| `TrackerService.java` | Intent destination (`MainActivityWithNavigation.class`), extras `SELECTED_FRAGMENT`, `EXTRA_RESUME_INTERRUPTED_WORKOUT` | Preserved 100%: Companion object constants and `SelectedFragment` enum retained identically. Intent handling parsed in `handleIntent()`. |
| `TrainingApplication.java` | Notification and launch intents targeting `MainActivityWithNavigation.class` | Preserved 100%: Launch intent contracts remain identical. |
| `ExportNotificationManager.kt` | Intent targeting `MainActivityWithNavigation` with `SELECTED_FRAGMENT = WORKOUT_LIST` | Preserved 100%: Routes to `NavRoutes.WORKOUTS` via `handleIntent()`. |
| `TrackingTabsScreen.kt:261` | `findViewById<DrawerLayout>(R.id.drawer_layout)` | Remediated: Calls new public helper `(context as? MainActivityWithNavigation)?.openDrawer()` or lambda callback `onOpenDrawer: () -> Unit`. |
| `TrackingTabsScreen.kt:346` | `startPairing(protocol, deviceType)` | Preserved 100%: Public method callable on `MainActivityWithNavigation`, routing `NavController` to `NavRoutes.SENSORS`. |
| `TrackingTabsScreen.kt:188, 359` | `context.supportFragmentManager` | Preserved: `MainActivityWithNavigation` continues to inherit from `AppCompatActivity` / `FragmentActivity`. |
| `MainActivityWithNavigationInteropTest.kt` | Reflection and unit verification of class hierarchy and constants | Preserved 100%: All assertions continue to pass without modification. |

### 3.2 System Invariants ("What MUST NOT Change")

1. **Hardware Telemetry Stream Resilience**:
   - `BANALService` binding MUST remain anchored exclusively to the Android Activity lifecycle (`MainActivityWithNavigation`), NOT within Compose composable scopes.
   - `isChangingConfigurations` checks in `onPause()` and `onDestroy()` MUST shield active ANT+/BLE sensor connections from unbind/rebind thrashing during phone rotation.
2. **Crash Resumption Contract**:
   - `checkUnfinishedWorkout()` in `onResume()` and `EXTRA_RESUME_INTERRUPTED_WORKOUT` in `handleIntent()` MUST restore ongoing sessions without data loss (`REQ-STB-003`).
3. **Display Settings Parity**:
   - Dynamic window decor flags (`keepScreenOn`, `FLAG_SHOW_WHEN_LOCKED`, orientation lock) in `applyDisplaySettings()` MUST remain intact (`REQ-SET-052`).
4. **Android 14 API 34 Crash Immunity**:
   - Gradle dependency alignment MUST strictly resolve `androidx.core:core:1.15.0` via `resolutionStrategy.force`, satisfying `CoreDependencyAlignmentTest` (`REQ-STB-007`).
5. **Java/Bytecode Interoperability**:
   - `@JvmField` constants (`SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`, `EXTRA_RESUME_INTERRUPTED_WORKOUT`), `SelectedFragment` enum, and callback interfaces MUST remain bytecode-accessible (`TST-NAV-008`).

---

## 4. Proposed Implementation Architecture

### 4.1 Dependency Addition (`app/build.gradle`)
Add official Jetpack Compose Navigation:
```groovy
implementation 'androidx.navigation:navigation-compose:2.8.8'
```
Governed strictly by existing `resolutionStrategy.force 'androidx.core:core:1.15.0'` to eliminate API 34 crash risks.

### 4.2 Type-Safe Routes & Destination Model (`NavRoutes.kt`)
Define route constants and mapping from legacy drawer IDs:
```kotlin
object NavRoutes {
    const val START_TRACKING = "start_tracking"
    const val WORKOUTS = "workouts"
    const val MAP = "map"
    const val SEGMENTS = "segments"
    const val ROUTES = "routes"
    const val PERIODS = "periods"
    const val SENSORS = "sensors"
    const val BIKES = "bikes"
    const val SHOES = "shoes"
    const val LOCATIONS = "locations"
    const val SPORT_TYPES = "sport_types"
    const val TRAINING_ZONES = "training_zones"
    const val BACKUP_RESTORE = "backup_restore"

    fun fromDrawerItemId(itemId: Int): String? = when (itemId) {
        R.id.drawer_start_tracking -> START_TRACKING
        R.id.drawer_workouts -> WORKOUTS
        R.id.drawer_map -> MAP
        R.id.drawer_segments -> SEGMENTS
        R.id.drawer_routes -> ROUTES
        R.id.drawer_periods -> PERIODS
        R.id.drawer_my_sensors -> SENSORS
        R.id.drawer_bikes -> BIKES
        R.id.drawer_shoes -> SHOES
        R.id.drawer_my_locations -> LOCATIONS
        R.id.drawer_sport_types -> SPORT_TYPES
        R.id.drawer_training_zones -> TRAINING_ZONES
        R.id.drawer_backup_restore -> BACKUP_RESTORE
        else -> null
    }
}
```

### 4.3 Root Composable Architecture (`ATrainingTrackerApp.kt`)
Houses the top-level Compose structure:
```kotlin
@Composable
fun ATrainingTrackerApp(
    activity: MainActivityWithNavigation,
    navController: NavHostController,
    drawerController: DrawerController,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var activeBottomSheet by rememberSaveable { mutableStateOf<SettingsBottomSheetType?>(null) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: NavRoutes.START_TRACKING

    // BackPress State Machine
    BackHandler {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            activeBottomSheet != null -> activeBottomSheet = null
            navController.previousBackStackEntry != null -> navController.popBackStack()
            currentRoute != NavRoutes.START_TRACKING -> {
                MyPreferenceManager.clearWorkoutFilterCriteria(activity)
                navController.navigate(NavRoutes.START_TRACKING) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            }
            else -> activity.finish()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.statusBars
            ) {
                AppNavigationDrawerContent(
                    selectedItemId = NavRoutes.toDrawerItemId(currentRoute),
                    startTrackingTitleRes = drawerController.startTrackingTitleRes,
                    onItemSelected = { itemId ->
                        scope.launch { drawerState.close() }
                        activity.navigateToDrawerItem(itemId)
                    }
                )
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = NavRoutes.START_TRACKING,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(NavRoutes.START_TRACKING) { TrackingTabsScreen(...) }
                composable(NavRoutes.WORKOUTS) { WorkoutSummariesTabbedScreen(...) }
                composable(NavRoutes.MAP) { MapScreenWithTrack(...) }
                composable(NavRoutes.SEGMENTS) { StarredSegmentsScreen(...) }
                composable(NavRoutes.ROUTES) { RoutesScreen(...) }
                composable(NavRoutes.PERIODS) { PeriodsScreen(...) }
                composable(NavRoutes.SENSORS) { DevicesTabbedContainerContent(...) }
                composable(NavRoutes.BIKES) { EquipmentScreen(equipmentType = 0) }
                composable(NavRoutes.SHOES) { EquipmentScreen(equipmentType = 1) }
                composable(NavRoutes.LOCATIONS) { WorkoutClustersScreen(...) }
                composable(NavRoutes.SPORT_TYPES) { SportTypesTabsScreen(...) }
                composable(NavRoutes.TRAINING_ZONES) { ZoneSettingsScreen(...) }
                composable(NavRoutes.BACKUP_RESTORE) { BackupRestoreScreen(...) }
            }
        }
    }

    // Native Compose ModalBottomSheet hosting for Settings Dialogs
    activeBottomSheet?.let { sheetType ->
        ModalBottomSheet(
            onDismissRequest = { activeBottomSheet = null },
            windowInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
        ) {
            when (sheetType) {
                SettingsBottomSheetType.STRAVA -> StravaSettingsDialog(...)
                SettingsBottomSheetType.DROPBOX -> DropboxSettingsDialog(...)
                SettingsBottomSheetType.EXPORT -> ExportSettingsDialog(...)
                SettingsBottomSheetType.UNITS -> UnitsSettingsDialog(...)
                SettingsBottomSheetType.DISPLAY -> DisplaySettingsDialog(...)
                SettingsBottomSheetType.SEARCH -> SearchSettingsDialog(...)
                SettingsBottomSheetType.ACTIVITY_TYPE -> ActivityTypeSelectionSheet(...)
            }
        }
    }
}
```

### 4.4 `MainActivityWithNavigation.kt` Modernization
1. Replace `setContentView(R.layout.main_activity_with_navigation)` in `onCreate()` with `setContent { ATrainingTrackerTheme { ATrainingTrackerApp(...) } }`.
2. Add public helper `fun openDrawer()` triggering reactive drawer opening.
3. Modernize `navigateToDrawerItem(itemId: Int): Boolean`:
   - If destination is a screen -> `navController.navigate(route) { popUpTo(startId) { saveState = true }; launchSingleTop = true; restoreState = true }`.
   - If destination is a settings dialog -> sets `activeBottomSheet = type`.
   - If external link (privacy policy) -> fires browser intent.
4. Concurrency guards for `BANALService`:
   - In `onPause()`: `if (!isChangingConfigurations) mHandler.postDelayed(mDisconnectFromBANALServiceRunnable, WAITING_TIME_BEFORE_DISCONNECTING)`.
   - In `onDestroy()`: `if (!isChangingConfigurations) disconnectFromBANALService()`.
   - In `onResume()`: `mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)` and verify connection.
5. In `onSaveInstanceState(outState: Bundle)`:
   - Call `super.onSaveInstanceState(outState)` to persist `NavController` backstack and `SavedStateRegistry`.
   - Put `mSelectedFragmentId` into bundle for backward compatibility.

---

## 5. Verification Plan

### 5.1 Automated Unit & Classpath Tests
```bash
# 1. Verify AndroidX Core dependency resolution alignment (REQ-STB-007)
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep "androidx.core:core:"

# 2. Run Java/bytecode interop reflection tests (REQ-UI-124 / TST-NAV-008)
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.MainActivityWithNavigationInteropTest"

# 3. Run Android 14 insets crash immunity unit test (REQ-STB-007 / TST-STB-007)
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.CoreDependencyAlignmentTest"

# 4. Run new Single-Activity Navigation unit tests (REQ-UI-159 / TST-NAV-009)
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest"

# 5. Full repository clean-room regression suite
./gradlew testDebugUnitTest
```

### 5.2 Manual Hardware Verification on Device (`66020DLCR002FL` - Pixel 10)
1. **Cold Start**: Verify launch directly into Start Tracking cockpit with zero layout flicker.
2. **Drawer Navigation**: Open drawer, verify compact density and reactive status title ("Start Tracking" / "Tracking" / "Pause"). Navigate through all 21 destinations.
3. **Settings Bottom Sheets**: Tap Strava, Dropbox, Export, Units, Display, Search. Verify single-window presentation with zero black status bar flashes.
4. **Soft Keyboard Insets**: Open Strava/Search sheets, focus text field, verify `imePadding()` shifts sheet above keyboard without occluding action buttons.
5. **Configuration Change**: Rotate device during live tracking/sensor telemetry; verify telemetry stream continues without disconnect/reconnect loops.
6. **Interrupted Workout Recovery**: Trigger interrupted workout notification and tap notification; verify foreground resumption via `chooseResume()`.

---

## 6. Risk Assessment & Gate 3 Recommendation

- **Risk Level**: **`LOW-MEDIUM`** (Controlled via single top-level `NavHost` architecture, Activity-anchored `BANALService` lifecycle with `isChangingConfigurations` guards, and 100% preservation of reflection contracts).
- **Recommendation**: **`RECOMMEND PASS`**.
