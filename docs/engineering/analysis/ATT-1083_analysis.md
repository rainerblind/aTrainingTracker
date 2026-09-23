# Engineering Analysis - ATT-1083

**Ticket**: `ATT-1083`: `[Verbesserung] Migrate MainActivityWithNavigation to Jetpack Compose single-activity architecture`  
**Parent / Epic**: `ATT-236`: Modernize UI & Architecture  
**Sub-task**: `ATT-1280`: `[Analysis] Migrate MainActivityWithNavigation to Jetpack Compose single-activity architecture`  
**Component**: Core UI / Navigation Host Architecture (`MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`, `main_activity_with_navigation.xml`)  
**Sprint**: `2026-39.2`  
**Target Release**: `V4.9.38`  
**Requirement Mapping**: `REQ-UI-158` (Proposed), `REQ-UI-124`, `REQ-UI-123`, `REQ-SET-050`, `REQ-SET-052`, `REQ-STB-003`  
**Test Specification**: `TST-NAV-009` (Proposed), `TST-NAV-008`, `TST-NAV-007`, `TST-NAV-004`  
**Stage**: `Stage 1: Analysis (SWE.1 / SYS.2)`

---

## 1. Executive Summary & Problem Statement

### 1.1 Motivation
`MainActivityWithNavigation.kt` is the primary entry point and central lifecycle cockpit of **aTrainingTracker**. While almost the entire application (cockpit, tracking grid, workout lists, period statistics, route lists, cluster heatmaps, sensors, sport types, and settings) has been modernized to Jetpack Compose over recent sprints, the root container remains bound to a legacy Android View hierarchy:
1. **Legacy View Hierarchy**: `MainActivityWithNavigation` inflates `main_activity_with_navigation.xml`, containing an Android View `DrawerLayout`, an inner `LinearLayout`, a `FrameLayout` (`R.id.content`), and a `ComposeView` (`R.id.compose_nav_view`).
2. **Double-Bridge Lifecycle**: Every drawer selection replaces the `FrameLayout` fragment using `supportFragmentManager.beginTransaction().replace(R.id.content, fragment).commit()`. The instantiated `Fragment` immediately executes `onCreateView` to return a `ComposeView` that calls `setContent { ... }`.
3. **Window Nesting & Flicker in Dialogs**: Drawer popups (Strava, Dropbox, Export, Units, Display, Search) extend `BottomSheetDialogFragment` / `DialogFragment`, creating child `Window` instances with separate decor views that require complex theme overlays (`REQ-UI-156`) to suppress black status bar and navigation bar flickers.
4. **State Synchronization Friction**: Backstack state, filter resets, and drawer gestures are split between Android Views, `supportFragmentManager`, and Compose reactive states.

### 1.2 Objectives
1. Modernize `MainActivityWithNavigation` into a **pure Jetpack Compose single-activity architecture** via `setContent { ATrainingTrackerApp(...) }`.
2. Replace legacy Android View `DrawerLayout` with Material 3 `ModalNavigationDrawer`.
3. Replace `supportFragmentManager` transactions with a state-driven, reactive navigation architecture (`NavDestination` / `currentDestination`) with robust `BackHandler` integration.
4. Extract Composable screen content from legacy Fragment wrappers so they can be composed directly in the root Compose tree while preserving backwards compatibility.
5. Provide native Compose `ModalBottomSheet` hosting for drawer settings dialogs (Export, Units, Display, Search, Strava, Dropbox, ActivityTypeSelection), eliminating `DialogFragment` / `WindowManager` window bridging.
6. Guarantee 100% preservation of background service bindings (`BANALService.GetBanalServiceInterface`), crash recovery (`StartOrResumeInterface`, `REQ-STB-003`), intent routing, and Java/bytecode interop contracts (`MainActivityWithNavigationInteropTest`).

---

## 2. Current Architecture Forensic Audit

### 2.1 Layout & Hierarchy Analysis
The active layout file [`main_activity_with_navigation.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/layout/main_activity_with_navigation.xml) consists of:
```xml
<androidx.drawerlayout.widget.DrawerLayout android:id="@+id/drawer_layout" ...>
    <LinearLayout ...>
        <FrameLayout android:id="@+id/content" ... />
    </LinearLayout>
    <androidx.compose.ui.platform.ComposeView android:id="@+id/compose_nav_view" ... />
</androidx.drawerlayout.widget.DrawerLayout>
```
In `MainActivityWithNavigation.onCreate()`:
- `mDrawerLayout = findViewById(R.id.drawer_layout)`
- `val composeNavView: ComposeView = findViewById(R.id.compose_nav_view)`
- `setupComposeNavigationDrawer(composeNavView, mDrawerController) { itemId -> navigateToDrawerItem(itemId) }`

### 2.2 Inventory of Drawer Destinations & Compose Readiness
An audit of all 21 navigation items handled in `navigateToDrawerItem(itemId)` demonstrates that **every single destination is already 100% Compose-ready**:

| Destination ID | Screen Name | Current Fragment Host | Internal Composable | Compose Readiness |
| :--- | :--- | :--- | :--- | :--- |
| `drawer_start_tracking` | Start / Tracking Cockpit | `TrackingTabsFragment.kt` | `TrackingTabsScreen(...)` | **100% Pure Compose** |
| `drawer_map` | Full Track on Map | `MapFragmentWithTrack.kt` | `MapScreenWithTrack(...)` | **100% Pure Compose** |
| `drawer_segments` | Starred Segments | `StarredSegmentsFragment.kt` | `StarredSegmentsScreen(...)` | **100% Pure Compose** |
| `drawer_routes` | Route Library | `RoutesFragment.kt` | `RoutesScreen(...)` | **100% Pure Compose** |
| `drawer_workouts` | Workout Summaries | `WorkoutSummariesTabbedFragment.kt` | `WorkoutSummariesTabbedScreen(...)` | **100% Pure Compose** |
| `drawer_periods` | Period Statistics | `PeriodsFragment.kt` | `PeriodsScreen(...)` | **100% Pure Compose** |
| `drawer_my_sensors` | Sensor & Device Manager | `DevicesTabbedContainerFragment.kt` | `DevicesTabbedContainerContent(...)` | **100% Pure Compose** |
| `drawer_bikes` | Equipment: Bikes | `EquipmentFragment.kt` (type 0) | `EquipmentScreen(...)` | **100% Pure Compose** |
| `drawer_shoes` | Equipment: Shoes | `EquipmentFragment.kt` (type 1) | `EquipmentScreen(...)` | **100% Pure Compose** |
| `drawer_my_locations`| Workout Clusters | `WorkoutClustersFragment.kt` | `WorkoutClustersScreen(...)` | **100% Pure Compose** |
| `drawer_sport_types` | Sport Types Manager | `SportTypeListFragment.kt` | `SportTypesTabsScreen(...)` | **100% Pure Compose** |
| `drawer_training_zones` | Training Zones | `ZoneSettingsFragment.kt` | `ZoneSettingsScreen(...)` | **100% Pure Compose** |
| `drawer_strava` | Strava Settings | `StravaSettingsDialogFragment.kt` | `StravaSettingsDialog(...)` | **100% Pure Compose Sheet** |
| `drawer_dropbox` | Dropbox Settings | `DropboxSettingsDialogFragment.kt` | `DropboxSettingsDialog(...)` | **100% Pure Compose Sheet** |
| `drawer_export` | File Export Preferences | `ExportSettingsDialogFragment.kt` | `ExportSettingsDialog(...)` | **100% Pure Compose Sheet** |
| `drawer_tracking_layouts` | Activity Type Picker | `ActivityTypeSelectionDialogFragment.kt` | `ActivityTypeSelectionSheet(...)` | **100% Pure Compose Sheet** |
| `drawer_units` | Unit System Picker | `UnitsSettingsDialogFragment.kt` | `UnitsSettingsDialog(...)` | **100% Pure Compose Sheet** |
| `drawer_display_settings` | Display Preferences | `DisplaySettingsDialogFragment.kt` | `DisplaySettingsDialog(...)` | **100% Pure Compose Sheet** |
| `drawer_search_settings` | Sensor Search Settings | `SearchSettingsDialogFragment.kt` | `SearchSettingsDialog(...)` | **100% Pure Compose Sheet** |
| `drawer_backup_restore` | Backup & Restore | `BackupRestoreFragment.kt` | `BackupRestoreScreen(...)` | **100% Pure Compose** |
| `drawer_privacy_policy` | Privacy Policy | External browser intent | N/A (Intent redirect) | Complete |

**Key Finding**: The underlying fragments are merely legacy shells that instantiate a `ComposeView`. By hoisting screen composition into a root Compose navigation tree, the application sheds 12 fragment wrapper classes and 7 DialogFragment window bridges.

---

## 3. Target Architecture & Design Specification

### 3.1 Single-Activity Architecture (`ATrainingTrackerApp`)
`MainActivityWithNavigation` transitions its root view binding to Jetpack Compose:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
        ATrainingTrackerTheme {
            ATrainingTrackerApp(
                activity = this,
                drawerController = mDrawerController,
                ...
            )
        }
    }
}
```

### 3.2 Material 3 `ModalNavigationDrawer`
The legacy `DrawerLayout` is replaced by Material 3's `ModalNavigationDrawer`:
```kotlin
val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
val scope = rememberCoroutineScope()

ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        ModalDrawerSheet(
            modifier = Modifier.width(300.dp),
            drawerContainerColor = MaterialTheme.colorScheme.surface,
            windowInsets = WindowInsets.statusBars
        ) {
            AppNavigationDrawerContent(
                selectedItemId = currentDestination.drawerItemId,
                startTrackingTitleRes = drawerController.startTrackingTitleRes,
                onItemSelected = { itemId ->
                    scope.launch { drawerState.close() }
                    navigateToDestination(itemId)
                }
            )
        }
    }
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        MainContentHost(
            currentDestination = currentDestination,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
```

### 3.3 Zero-Dependency, State-Driven Navigation Router
Rather than pulling in external `navigation-compose` libraries that risk classpath collisions with strictly pinned AndroidX Core `1.15.0` (`REQ-STB-007`), the navigation architecture utilizes a **state-driven reactive destination router**:
- Encapsulated in sealed class `NavDestination` (holding destination ID, route title, and optional arguments such as `clusterId` or `activityType`).
- Backstack maintained via `rememberSaveable { mutableStateListOf<NavDestination>() }`.
- Deterministic `BackHandler` lifecycle:
  1. *Drawer Open*: Closes drawer smoothly.
  2. *Active Bottom Sheet*: Closes modal sheet.
  3. *Sub-Screen / Detail*: Pops backstack to originating list view.
  4. *Top-Level Destination != StartTracking*: Clears filter criteria (`MyPreferenceManager.clearWorkoutFilterCriteria()`, etc.) and returns to `drawer_start_tracking`.
  5. *At StartTracking*: Finishes activity.

### 3.4 Native Compose Modal Bottom Sheets
Settings dialogs (Strava, Dropbox, Export, Units, Display, Search, ActivityTypeSelection) are hoisted into the Compose tree as reactive state (`activeBottomSheet: BottomSheetDestination?`):
- When active, the sheet is rendered via Compose `ModalBottomSheet`.
- Operates entirely within the single activity window decor: zero separate `Dialog` windows, zero `WindowManager` bridging, and zero status bar / navigation bar flickering.

---

## 4. Call-Site Audit & Blast Radius

| Component / File | Interaction Type | Impact Analysis & Remediation |
| :--- | :--- | :--- |
| `AndroidManifest.xml` | Activity Declaration | Preserved: `MainActivityWithNavigation` remains `<activity>` launcher with `singleTask` launch mode. |
| `TrackerService.java` | Notification Intent Target | Preserved: Uses `MainActivityWithNavigation.class`, `SELECTED_FRAGMENT`, `EXTRA_RESUME_INTERRUPTED_WORKOUT`. |
| `TrainingApplication.java` | Notification & App Startup | Preserved: Intent targeting `MainActivityWithNavigation.class`. |
| `ExportNotificationManager.kt` | Export Click Intent | Preserved: Sets `SELECTED_FRAGMENT = WORKOUT_LIST`. |
| `TrackingTabsScreen.kt` | Drawer & Pairing Trigger | Remediated: Line 261 calls `openDrawer()` method directly on `MainActivityWithNavigation` instead of `findViewById(R.id.drawer_layout)`. Line 346 calls `startPairing(protocol, deviceType)`. |
| `DisplaySettingsDialogFragment.kt` | Display Settings Sync | Preserved: `applyDisplaySettings()` callable on activity. |
| `MainActivityWithNavigationInteropTest.kt` | Unit Verification Suite | Preserved: Class hierarchy, interfaces, constants, and public methods verified by test suite. |

---

## 5. System Invariants & Safety Verification

1. **Service Binding Contract (`BANALService.GetBanalServiceInterface`)**:
   - `bindService` on startup, 5-minute delayed disconnect in `onPause()`, reconnect in `onResume()`.
   - `mBanalServiceComm` filter creation and status listeners preserved without alteration.
2. **Crash Resumption Contract (`REQ-STB-003`, `StartOrResumeInterface`)**:
   - `checkUnfinishedWorkout()` in `onResume()` checks `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()`.
   - Prompt provides "Workout fortsetzen" (`chooseResume()`) and "Neues Workout starten" (`chooseStart()`).
   - `EXTRA_RESUME_INTERRUPTED_WORKOUT` handled in `handleIntent()`.
3. **Display Settings Dynamic Application (`REQ-SET-052`)**:
   - `applyDisplaySettings()` maintains dynamic application of `window.decorView.keepScreenOn`, `FLAG_SHOW_WHEN_LOCKED`, and `SCREEN_ORIENTATION_PORTRAIT`.
4. **Android 14 API 34 Crash Immunity (`REQ-STB-007`)**:
   - Navigation and window insets handling must strictly avoid calling missing platform methods (`WindowInsets.Type.systemOverlays()`). Verified by `CoreDependencyAlignmentTest.kt`.
5. **Java/Bytecode Interoperability (`TST-NAV-008`)**:
   - Companion object constants (`SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`, `EXTRA_RESUME_INTERRUPTED_WORKOUT`) and `SelectedFragment` enum preserved as `@JvmField`.
   - Public methods `startPairing(protocol, deviceType)` and `navigateToDrawerItem(itemId)` remain accessible.

---

## 6. Requirement & Test Specification Mapping

### 6.1 Proposed Requirement: `REQ-UI-158`
- **Title**: *Single-Activity Architecture & Pure Jetpack Compose Navigation.*
- **Scope**: `MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`.
- **Formulation**:
  > The system SHALL implement a single-activity architecture for `MainActivityWithNavigation` utilizing native Jetpack Compose (`setContent`) and Material 3 `ModalNavigationDrawer`:\
  > 1. *Layout Modernization*: The legacy View layout `main_activity_with_navigation.xml` SHALL be retired from the primary runtime flow in favor of declarative Compose composition (`ATrainingTrackerApp`).\
  > 2. *Navigation Architecture*: Top-level navigation, sub-destination routing, and back-press handling SHALL be managed natively within Jetpack Compose, retiring legacy `supportFragmentManager` transaction replacements.\
  > 3. *Modal Bottom Sheet Unification*: Settings and selection bottom sheets SHALL compose natively within the single activity window, eliminating separate child `Dialog` window allocations and navigation bar flickering.\
  > 4. *Invariants*: Service binding to `BANALService`, crash recovery (`REQ-STB-003`), display options dynamic application (`REQ-SET-052`), intent routing extras, and Java interop contracts (`TST-NAV-008`) MUST remain 100% functional.

### 6.2 Proposed Test Specification: `TST-NAV-009`
- **Title**: *Single-Activity Compose Navigation & Architectural Interop Verification.*
- **Scope**: Automated unit tests in `app/src/test/` asserting Compose navigation state transitions, backstack behavior, and interop contracts.

---

## 7. Risk Rating & Mitigation

- **Risk Level**: **`MEDIUM`**
- **Justification**: `MainActivityWithNavigation` is the root component of the entire application. Modifying its layout and navigation pipeline touches the root window decor, lifecycle callbacks, and service bindings.
- **Mitigation Strategy**:
  1. Retain `MainActivityWithNavigation` class identity and interface implementations, ensuring zero disruption to Android framework manifest launching or service binding.
  2. Implement an in-activity Compose navigation router that leverages existing, battle-tested screen composables.
  3. Validate all 32 test tasks with `./gradlew testDebugUnitTest` to guarantee zero regressions.
  4. Perform live physical hardware verification on device `66020DLCR002FL` (Pixel 10) across drawer navigation, tracking start, settings dialogs, and crash resumption.

---

## 8. Gate 1 Auditor Recommendation
- **Recommendation**: **`RECOMMEND PASS`**
- **Next Stage**: Stage 2 (Test Specification) upon Human Gate 1 Approval.
