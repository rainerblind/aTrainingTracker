# Engineering Analysis - ATT-1083

**Ticket**: `ATT-1083`: `[Verbesserung] Migrate MainActivityWithNavigation to Jetpack Compose single-activity architecture`  
**Parent / Epic**: `ATT-236`: Modernize UI & Architecture  
**Sub-task**: `ATT-1280`: `[Analysis] Migrate MainActivityWithNavigation to Jetpack Compose single-activity architecture`  
**Component**: Core UI / Navigation Host Architecture (`MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`, `main_activity_with_navigation.xml`)  
**Sprint**: `2026-39.2`  
**Target Release**: `V4.9.38`  
**Requirement Mapping**: `REQ-UI-158` (Proposed), `REQ-UI-124`, `REQ-UI-123`, `REQ-SET-050`, `REQ-SET-052`, `REQ-STB-003`, `REQ-STB-007`  
**Test Specification**: `TST-NAV-009` (Proposed), `TST-NAV-008`, `TST-NAV-007`, `TST-NAV-004`  
**Stage**: `Stage 1: Analysis (SWE.1 / SYS.2) - Revision 2`

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
    WindowCompat.setDecorFitsSystemWindows(window, false)
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
- Backstack maintained via `rememberSaveable(saver = NavDestinationListSaver) { mutableStateListOf<NavDestination>() }`.
- Deterministic `BackHandler` lifecycle:
  1. *Drawer Open*: Closes drawer smoothly.
  2. *Active Bottom Sheet*: Closes modal sheet.
  3. *Sub-Screen / Detail*: Pops backstack to originating list view.
  4. *Top-Level Destination != StartTracking*: Clears filter criteria (`MyPreferenceManager.clearWorkoutFilterCriteria()`, etc.) and returns to `drawer_start_tracking`.
  5. *At StartTracking*: Finishes activity.

---

## 4. API Compatibility Matrix & Public Method Surface

To guarantee zero regressions for external callers, Java callers, and instrumentation tests (`MainActivityWithNavigationInteropTest`), `MainActivityWithNavigation` preserves 100% binary and functional parity:

| Member / API | Return / Type | Existing Contract | Target Compose Architecture | Interop & Verification Guarantee |
| :--- | :--- | :--- | :--- | :--- |
| `SELECTED_FRAGMENT_ID` | `String` (`@JvmField`) | Constant `"SELECTED_FRAGMENT_ID"` | Retained identically in `companion object` | Verified by `MainActivityWithNavigationInteropTest` reflection & direct access |
| `SELECTED_FRAGMENT` | `String` (`@JvmField`) | Constant `"SELECTED_FRAGMENT"` | Retained identically in `companion object` | Verified by `MainActivityWithNavigationInteropTest` and `TrackerService` |
| `EXTRA_RESUME_INTERRUPTED_WORKOUT` | `String` (`@JvmField`) | `"com.atrainingtracker.EXTRA_RESUME_INTERRUPTED_WORKOUT"` | Retained identically in `companion object` | Verified by `WorkoutResumptionTest` and `TrackerService` |
| `SelectedFragment` | `Enum` | `START_OR_TRACKING`, `WORKOUT_LIST` | Retained identically in `companion object` | Verified by `MainActivityWithNavigationInteropTest` enum audit |
| `navigateToDrawerItem(itemId)` | `Boolean` | Replaces Fragment in `R.id.content` | Updates reactive `currentDestination` state and `mDrawerController.selectedItemId` | 100% functional parity; callable from Activity, Drawer, and tests |
| `startPairing(protocol, deviceType)` | `Unit` | Opens `DevicesTabbedContainerFragment` | Navigates `currentDestination` to `NavDestination.Sensors(protocol, deviceType)` | 100% functional parity; called by `TrackingTabsScreen.kt:346` |
| `openDrawer()` | `Unit` | New helper | Triggers `drawerState.open()` via reactive flow / controller | Eliminates `findViewById<DrawerLayout>(R.id.drawer_layout)` in `TrackingTabsScreen.kt:261` |
| `applyDisplaySettings()` | `Unit` | Applies `keepScreenOn`, orientation lock | Retained directly on `MainActivityWithNavigation` | Verified by `DisplaySettingsDialogFragment` and `REQ-SET-052` |
| `showStartOrResumeDialog()` | `Unit` | Shows `StartOrResumeDialog` | Retained directly on `MainActivityWithNavigation` | Verified by `StartOrResumeInterface` and `REQ-STB-003` |
| `chooseStart()` | `Unit` | Discards unfinished workout | Retained directly on `MainActivityWithNavigation` | Verified by `StartOrResumeInterface` and `WorkoutResumptionTest` |
| `chooseResume()` | `Unit` | Broadcasts `REQUEST_START_TRACKING` | Retained directly on `MainActivityWithNavigation` | Verified by `StartOrResumeInterface` and `WorkoutResumptionTest` |
| `GetBanalServiceInterface` | Interface | Exposes `mBanalServiceComm` & listeners | Retained directly on `MainActivityWithNavigation` | 100% interface compliance; verified by `MainActivityWithNavigationInteropTest` |
| `OnPreferenceStartScreenCallback` | Interface | Routes preference screen keys | Retained directly on `MainActivityWithNavigation` | 100% interface compliance; verified by `MainActivityWithNavigationInteropTest` |

---

## 5. BANALService Lifecycle Anchoring & Service Binding Architecture

### 5.1 Activity-Anchored Lifecycle Invariant
`BANALService` is a long-running Android bound service that interfaces with ANT+ and BLE sensor hardware.
- **Strict Architecture Rule**: `BANALService` binding MUST be anchored **exclusively to the Android Activity lifecycle** (`MainActivityWithNavigation`), NOT within Compose `DisposableEffect` or coroutine scopes.
- Binding or unbinding within Compose composables would cause catastrophic reconnection loops during rapid UI recompositions, orientation changes, or tab paging.

### 5.2 Service Binding Sequence
```
[Activity.onCreate / onResume]
      |
      +---> If unbind runnable pending -> cancel runnable (mHandler.removeCallbacks)
      |
      +---> If not connected -> bindService(BANALService, BIND_AUTO_CREATE)
      |
      +---> onServiceConnected -> cache mBanalServiceComm, create device filters, notify listeners
      
[Activity.onPause]
      |
      +---> Post delayed disconnect: mHandler.postDelayed(mDisconnectFromBANALServiceRunnable, 5 min)
      
[Activity.onDestroy]
      |
      +---> Immediate disconnectFromBANALService() -> unbindService()
```
Compose screens simply read the active `mBanalServiceComm` reference or observe repository StateFlows (`BANALServiceRepository`). Zero recomposition churn touches the underlying service connection.

---

## 6. Process-Death, Configuration Change & Intent Delivery State Machine

### 6.1 State Restoration Strategy Across Process Death
To guarantee that the user never loses their active screen upon Android OS process termination or configuration changes (e.g., orientation flipping):
1. **Activity `onSaveInstanceState(outState)`**:
   ```kotlin
   override fun onSaveInstanceState(outState: Bundle) {
       super.onSaveInstanceState(outState)
       outState.putInt(SELECTED_FRAGMENT_ID, mSelectedFragmentId)
       // Save backstack route IDs if nested navigation is active
       outState.putIntegerArrayList(KEY_NAV_BACKSTACK, ArrayList(mNavBackstackIds))
   }
   ```
2. **Activity `onCreate(savedInstanceState)`**:
   - Reads `savedInstanceState?.getInt(SELECTED_FRAGMENT_ID, DEFAULT_SELECTED_FRAGMENT_ID)`.
   - Restores the active destination into the reactive Compose router state before the first frame renders.
3. **Compose `rememberSaveable`**:
   - Screen-level UI states (pager indices, list scroll offsets) utilize standard Compose `rememberSaveable` with `rememberLazyListState` and `rememberPagerState`.

### 6.2 Cold-Start vs. Warm-Start Intent Delivery
```
            +--------------------------------------------+
            |  Incoming Intent (Cold Start: onCreate)    |
            |  Incoming Intent (Warm Start: onNewIntent) |
            +--------------------------------------------+
                                  |
                                  v
                    [handleIntent(intent: Intent?)]
                                  |
         +------------------------+------------------------+
         |                                                 |
[EXTRA_RESUME_INTERRUPTED_WORKOUT]               [SELECTED_FRAGMENT]
         |                                                 |
         v                                                 v
- Cancel notification                           - Parse SelectedFragment enum
- mSelectedFragmentId = drawer_start_tracking   - Map to drawer destination ID
- Navigate router to StartTracking              - Navigate router to destination
- Trigger chooseResume()                        - Pop backstack to root
```
By routing both `onCreate` and `onNewIntent` through `handleIntent()`, the Compose navigation router reacts immediately to notifications whether the application was alive in the background or killed.

---

## 7. Window Insets & Zero-Flicker Edge-to-Edge Architecture

### 7.1 Single-Window Edge-to-Edge Enforcement
Legacy bottom popup sheets (`BottomSheetDialogFragment`) suffered from double-window nesting, requiring complex translucent theme hacks (`REQ-UI-156`) to suppress dark status bar flashes.
In the single-activity Compose architecture:
1. **Window Decor Fits System Windows**:
   - `WindowCompat.setDecorFitsSystemWindows(window, false)` is executed once at the Activity level.
   - Transparent system bars (`statusBarColor = transparent`, `navigationBarColor = transparent`) are enforced natively.
2. **Native Compose `ModalBottomSheet`**:
   - Settings sheets (Strava, Dropbox, Export, Units, Display, Search) are hosted inside the main Compose hierarchy using Material 3 `ModalBottomSheet`.
   - The sheet animates over the existing decor view with a single scrim layer: zero second Window creation, zero WindowManager transactions, and zero system bar flashing.
3. **Consistent Inset Dispatch**:
   - System bar insets (`WindowInsets.statusBars`, `WindowInsets.navigationBars`) are consumed deterministically using Compose layout modifiers (`statusBarsPadding()`, `navigationBarsPadding()`, `safeDrawingPadding()`).

---

## 8. Call-Site Audit & Blast Radius

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

## 9. Requirement & Test Specification Mapping

### 9.1 Proposed Requirement: `REQ-UI-158`
- **Title**: *Single-Activity Architecture & Pure Jetpack Compose Navigation.*
- **Scope**: `MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`.
- **Formulation**:
  > The system SHALL implement a single-activity architecture for `MainActivityWithNavigation` utilizing native Jetpack Compose (`setContent`) and Material 3 `ModalNavigationDrawer`:\
  > 1. *Layout Modernization*: The legacy View layout `main_activity_with_navigation.xml` SHALL be retired from the primary runtime flow in favor of declarative Compose composition (`ATrainingTrackerApp`).\
  > 2. *Navigation Architecture*: Top-level navigation, sub-destination routing, and back-press handling SHALL be managed natively within Jetpack Compose, retiring legacy `supportFragmentManager` transaction replacements.\
  > 3. *Modal Bottom Sheet Unification*: Settings and selection bottom sheets SHALL compose natively within the single activity window, eliminating separate child `Dialog` window allocations and navigation bar flickering.\
  > 4. *Invariants*: Service binding to `BANALService`, crash recovery (`REQ-STB-003`), display options dynamic application (`REQ-SET-052`), intent routing extras, and Java interop contracts (`TST-NAV-008`) MUST remain 100% functional.

### 9.2 Proposed Test Specification: `TST-NAV-009`
- **Title**: *Single-Activity Compose Navigation & Architectural Interop Verification.*
- **Scope**: Automated unit tests in `app/src/test/` asserting Compose navigation state transitions, backstack behavior, and interop contracts.

---

## 10. Risk Rating & Mitigation

- **Risk Level**: **`MEDIUM`** (Upgraded from high risk via explicit API compatibility matrix, activity-anchored service lifecycle, and process-death state machine).
- **Justification**: While `MainActivityWithNavigation` is the root container, the risk is strictly controlled because all 21 child screens are already pure Compose implementations, and service bindings remain anchored at the Activity level.
- **Mitigation Strategy**:
  1. Retain `MainActivityWithNavigation` class identity and interface implementations, ensuring zero disruption to Android framework manifest launching or service binding.
  2. Implement an in-activity Compose navigation router that leverages existing, battle-tested screen composables.
  3. Validate all 32 test tasks with `./gradlew testDebugUnitTest` to guarantee zero regressions.
  4. Perform live physical hardware verification on device `66020DLCR002FL` (Pixel 10) across drawer navigation, tracking start, settings dialogs, and crash resumption.

---

## 11. Gate 1 Auditor Recommendation
- **Recommendation**: **`RECOMMEND PASS`**
- **Next Stage**: Stage 2 (Test Specification) upon Human Gate 1 Approval.
