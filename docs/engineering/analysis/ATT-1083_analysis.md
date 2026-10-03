# Engineering Analysis - ATT-1083

**Ticket**: `ATT-1083`: `[Verbesserung] Migrate MainActivityWithNavigation to Jetpack Compose single-activity architecture`  
**Parent / Epic**: `ATT-236`: Modernize UI & Architecture  
**Sub-task**: `ATT-1280`: `[Analysis] Migrate MainActivityWithNavigation to Jetpack Compose single-activity architecture`  
**Component**: Core UI / Navigation Host Architecture (`MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`, `main_activity_with_navigation.xml`)  
**Sprint**: `2026-39.2`  
**Target Release**: `V4.9.38`  
**Requirement Mapping**: `REQ-UI-159` (Proposed), `REQ-UI-124`, `REQ-UI-123`, `REQ-SET-050`, `REQ-SET-052`, `REQ-STB-003`, `REQ-STB-007`  
**Test Specification**: `TST-NAV-009` (Proposed), `TST-NAV-008`, `TST-NAV-007`, `TST-NAV-004`  
**Stage**: `Stage 1: Analysis (SWE.1 / SYS.2) - Revision 3`

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
3. Replace `supportFragmentManager` transactions with official **Jetpack Compose Navigation (`NavHost` / `rememberNavController`)**, harmonizing with parent issue objectives.
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

### 2.3 Fragment Lifecycle & Result Listener Audit
A forensic audit across the entire codebase (`app/src/`) for fragment result communication:
- `setFragmentResultListener` / `setFragmentResult`: **0 occurrences**. Zero components depend on fragment result channels.
- `supportFragmentManager`: Found exclusively inside `MainActivityWithNavigation.kt` and two calls in `TrackingTabsScreen.kt` invoking `EditDeviceFragmentFactory.create(...).show(context.supportFragmentManager, "EditDeviceDialog")`.
- `MainActivityWithNavigation` will continue extending `AppCompatActivity` (which is a `FragmentActivity`), ensuring 100% backward compatibility for legacy dialogs until they are phased out.

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
            val navController = rememberNavController()
            ATrainingTrackerApp(
                activity = this,
                navController = navController,
                drawerController = mDrawerController
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
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
```

### 3.3 Official Jetpack Compose Navigation (`NavHost` / `NavController`) Alignment
In strict alignment with the parent issue objectives (**ATT-1083: "Replace FragmentManager transactions with Jetpack Compose Navigation (NavHost / NavController)"**), the application adopts official **Jetpack Compose Navigation**:
- **Framework**: `androidx.navigation:navigation-compose:2.8.8` (or active BOM-compatible release).
- **Classpath Alignment with REQ-STB-007**: Adding `navigation-compose` is strictly governed by `app/build.gradle`'s global `resolutionStrategy.force 'androidx.core:core:1.15.0'`, which guarantees that `androidx.core` cannot be transitively bumped to defective versions (API 34 crash immunity is preserved).
- **Navigation State Machine**:
  ```kotlin
  NavHost(
      navController = navController,
      startDestination = NavRoutes.START_TRACKING
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
  ```
- **Top-Level Navigation Pattern**:
  When selecting top-level drawer items:
  ```kotlin
  navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
          saveState = true
      }
      launchSingleTop = true
      restoreState = true
  }
  ```
  This matches official Google Android architecture guidelines: single top-level destination backstack, automatic state saving and restoring, and seamless deep-link support.

---

## 4. API Compatibility Matrix & Public Method Surface

To guarantee zero regressions for external callers, Java callers, and instrumentation tests (`MainActivityWithNavigationInteropTest`), `MainActivityWithNavigation` preserves 100% binary, bytecode, and functional parity:

| Member / API | Return / Type | Existing Contract | Target Compose Architecture | Interop & Verification Guarantee |
| :--- | :--- | :--- | :--- | :--- |
| `SELECTED_FRAGMENT_ID` | `String` (`@JvmField`) | Constant `"SELECTED_FRAGMENT_ID"` | Retained identically in `companion object` | Verified by `MainActivityWithNavigationInteropTest` reflection & direct access |
| `SELECTED_FRAGMENT` | `String` (`@JvmField`) | Constant `"SELECTED_FRAGMENT"` | Retained identically in `companion object` | Verified by `MainActivityWithNavigationInteropTest` and `TrackerService` |
| `EXTRA_RESUME_INTERRUPTED_WORKOUT` | `String` (`@JvmField`) | `"com.atrainingtracker.EXTRA_RESUME_INTERRUPTED_WORKOUT"` | Retained identically in `companion object` | Verified by `WorkoutResumptionTest` and `TrackerService` |
| `SelectedFragment` | `Enum` | `START_OR_TRACKING`, `WORKOUT_LIST` | Retained identically in `companion object` | Verified by `MainActivityWithNavigationInteropTest` enum audit |
| `navigateToDrawerItem(itemId)` | `Boolean` | Replaces Fragment in `R.id.content` | Routes `navController` to target route and updates `mDrawerController.selectedItemId` | 100% functional parity; callable from Activity, Drawer, and tests |
| `startPairing(protocol, deviceType)` | `Unit` | Opens `DevicesTabbedContainerFragment` | Navigates `navController` to `NavRoutes.SENSORS` with arguments | 100% functional parity; called by `TrackingTabsScreen.kt:346` |
| `openDrawer()` | `Unit` | New helper | Triggers `drawerState.open()` via reactive flow / controller | Eliminates `findViewById<DrawerLayout>(R.id.drawer_layout)` in `TrackingTabsScreen.kt:261` |
| `applyDisplaySettings()` | `Unit` | Applies `keepScreenOn`, orientation lock | Retained directly on `MainActivityWithNavigation` | Verified by `DisplaySettingsDialogFragment` and `REQ-SET-052` |
| `showStartOrResumeDialog()` | `Unit` | Shows `StartOrResumeDialog` | Retained directly on `MainActivityWithNavigation` | Verified by `StartOrResumeInterface` and `REQ-STB-003` |
| `chooseStart()` | `Unit` | Discards unfinished workout | Retained directly on `MainActivityWithNavigation` | Verified by `StartOrResumeInterface` and `WorkoutResumptionTest` |
| `chooseResume()` | `Unit` | Broadcasts `REQUEST_START_TRACKING` | Retained directly on `MainActivityWithNavigation` | Verified by `StartOrResumeInterface` and `WorkoutResumptionTest` |
| `GetBanalServiceInterface` | Interface | Exposes `mBanalServiceComm` & listeners | Retained directly on `MainActivityWithNavigation` | 100% interface compliance; verified by `MainActivityWithNavigationInteropTest` |
| `OnPreferenceStartScreenCallback` | Interface | Routes preference screen keys | Retained directly on `MainActivityWithNavigation` | 100% interface compliance; verified by `MainActivityWithNavigationInteropTest` |

---

## 5. BANALService Lifecycle Anchoring & Configuration Change Concurrency Guard

### 5.1 Activity-Anchored Lifecycle Invariant
`BANALService` is a long-running Android bound service that interfaces with ANT+ and BLE sensor hardware.
- **Strict Architecture Rule**: `BANALService` binding MUST be anchored **exclusively to the Android Activity lifecycle** (`MainActivityWithNavigation`), NOT within Compose composable scopes or `DisposableEffect`.
- Binding or unbinding within Compose composables would cause catastrophic reconnection loops during rapid UI recompositions, orientation changes, or tab paging.

### 5.2 Concurrency & Configuration Change Protection
When the user rotates their phone, Android destroys and recreates `MainActivityWithNavigation`. Without an explicit lifecycle guard, `onPause()` posts a 5-minute delayed disconnect runnable while `onDestroy()` abruptly unbinds, creating an unbind/rebind race with the incoming Activity's `onCreate()` / `onResume()`.

**Architectural Remediation**:
1. **In `onPause()`**:
   ```kotlin
   override fun onPause() {
       super.onPause()
       if (!isChangingConfigurations) {
           mHandler.postDelayed(mDisconnectFromBANALServiceRunnable, WAITING_TIME_BEFORE_DISCONNECTING)
       }
   }
   ```
2. **In `onDestroy()`**:
   ```kotlin
   override fun onDestroy() {
       super.onDestroy()
       if (!isChangingConfigurations) {
           disconnectFromBANALService()
       }
   }
   ```
3. **In `onResume()`**:
   ```kotlin
   override fun onResume() {
       super.onResume()
       mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)
       if (mBanalServiceComm == null) {
           bindService(banalServiceIntent, mBanalConnection, Context.BIND_AUTO_CREATE)
       }
       ...
   }
   ```

### 5.3 Configuration Change Sequence Flow
```
Activity 1 (Old)                           Android OS / Service                   Activity 2 (New)
      |                                              |                                   |
      |--- onPause() [isChangingConfig=true] ------->|                                   |
      |    (Skip posting delayed disconnect)         |                                   |
      |                                              |                                   |
      |--- onDestroy() [isChangingConfig=true] ----->|                                   |
      |    (Skip unbindService & stopService)        |                                   |
      |                                              |                                   |
      |                                              |--- onCreate() ------------------->|
      |                                              |    (Attaches to existing process) |
      |                                              |                                   |
      |                                              |--- onResume() ------------------->|
      |                                              |    (Cancel any pending runnable;  |
      |                                              |     Service remains bound cleanly)|
```
Zero reconnection storms occur; sensor streams remain uninterrupted across device rotation.

---

## 6. Process-Death, Configuration Change & Intent Delivery State Machine

### 6.1 State Restoration Strategy Across Process Death
To guarantee that the user never loses their active screen or form data upon Android OS process termination:
1. **`NavHost` / `NavBackStackEntry` SavedState Integration**:
   - Official `NavController` automatically serializes backstack entries into the Activity's saved instance state bundle via `NavBackStackEntry.savedStateRegistry`.
   - Each composable destination in `NavHost` has its own `SavedStateRegistryOwner` and `ViewModelStoreOwner`.
   - Child composables using `rememberSaveable` (e.g. `LazyListState.firstVisibleItemIndex` in workout lists, `PagerState.currentPage` in tracking tabs, text fields in equipment forms) are automatically restored upon recreation.
2. **Activity `onSaveInstanceState(outState)`**:
   - Delegates to `super.onSaveInstanceState(outState)` (which persists the `NavController` state).
   - Persists `mSelectedFragmentId` (`outState.putInt(SELECTED_FRAGMENT_ID, mSelectedFragmentId)`) for legacy interop.
3. **Persistent Filters**:
   - Workout and segment filter criteria are stored in `MyPreferenceManager` (backed by `SharedPreferences`), guaranteeing persistence across process death by definition.

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
- mSelectedFragmentId = drawer_start_tracking   - Map to NavRoutes (e.g. WORKOUTS)
- NavController navigates to START_TRACKING     - NavController navigates to target
- Trigger chooseResume()                        - Pop backstack to root
```
By routing both `onCreate` and `onNewIntent` through `handleIntent()`, the Compose navigation router reacts immediately to notifications whether the application was alive in the background or killed.

---

## 7. Window Insets, IME (Keyboard) & Zero-Flicker Edge-to-Edge Architecture

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

### 7.2 Soft Input Mode & IME (Keyboard) Handling in Modal Bottom Sheets
When settings dialogs (such as Strava token input, search filter entry, or equipment labeling) open inside `ModalBottomSheet`, soft keyboard appearance can cause layout occlusion or clipping:
1. **Manifest Configuration**:
   - `MainActivityWithNavigation` declares `android:windowSoftInputMode="adjustResize"`.
2. **Compose Inset Union**:
   - `ModalBottomSheet` is configured with `windowInsets = WindowInsets.ime.union(WindowInsets.navigationBars)`.
3. **Scrollable Content Scaffolding**:
   - The sheet content column applies `Modifier.verticalScroll(rememberScrollState()).imePadding()`, ensuring the active input field smoothly scrolls above the soft keyboard without obscuring action buttons.

---

## 8. Call-Site Audit & Blast Radius

| Component / File | Interaction Type | Impact Analysis & Remediation |
| :--- | :--- | :--- |
| `AndroidManifest.xml` | Activity Declaration | Preserved: `MainActivityWithNavigation` remains `<activity>` launcher with `singleTask` launch mode and `adjustResize`. |
| `TrackerService.java` | Notification Intent Target | Preserved: Uses `MainActivityWithNavigation.class`, `SELECTED_FRAGMENT`, `EXTRA_RESUME_INTERRUPTED_WORKOUT`. |
| `TrainingApplication.java` | Notification & App Startup | Preserved: Intent targeting `MainActivityWithNavigation.class`. |
| `ExportNotificationManager.kt` | Export Click Intent | Preserved: Sets `SELECTED_FRAGMENT = WORKOUT_LIST`. |
| `TrackingTabsScreen.kt` | Drawer & Pairing Trigger | Remediated: Line 261 calls `openDrawer()` method directly on `MainActivityWithNavigation` instead of `findViewById(R.id.drawer_layout)`. Line 346 calls `startPairing(protocol, deviceType)`. Lines 188 & 359 use `context.supportFragmentManager` which remains valid. |
| `DisplaySettingsDialogFragment.kt` | Display Settings Sync | Preserved: `applyDisplaySettings()` callable on activity. |
| `MainActivityWithNavigationInteropTest.kt` | Unit Verification Suite | Preserved: Class hierarchy, interfaces, constants, and public methods verified by test suite. |

---

## 9. Requirement & Test Specification Mapping

### 9.1 Proposed Requirement: `REQ-UI-159`
- **Title**: *Single-Activity Architecture & Pure Jetpack Compose Navigation.*
- **Scope**: `MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`.
- **Formulation**:
  > The system SHALL implement a single-activity architecture for `MainActivityWithNavigation` utilizing native Jetpack Compose (`setContent`), Material 3 `ModalNavigationDrawer`, and Jetpack Compose Navigation (`NavHost` / `NavController`):\
  > 1. *Layout Modernization*: The legacy View layout `main_activity_with_navigation.xml` SHALL be retired from the primary runtime flow in favor of declarative Compose composition (`ATrainingTrackerApp`).\
  > 2. *Navigation Architecture*: Top-level navigation, sub-destination routing, and back-press handling SHALL be managed natively within Jetpack Compose Navigation (`NavHost`), retiring legacy `supportFragmentManager` transaction replacements.\
  > 3. *Modal Bottom Sheet & IME Unification*: Settings and selection bottom sheets SHALL compose natively within the single activity window with IME keyboard insets (`adjustResize` / `imePadding`), eliminating separate child `Dialog` window allocations and navigation bar flickering.\
  > 4. *Invariants*: Service binding to `BANALService` (with configuration change concurrency guards), crash recovery (`REQ-STB-003`), display options dynamic application (`REQ-SET-052`), intent routing extras, and Java interop contracts (`TST-NAV-008`) MUST remain 100% functional.

### 9.2 Proposed Test Specification: `TST-NAV-009`
- **Title**: *Single-Activity Compose Navigation & Architectural Interop Verification.*
- **Scope**: Automated unit tests in `app/src/test/` asserting Compose navigation state transitions, backstack behavior, IME insets, and interop contracts.

---

## 10. Risk Rating & Mitigation

- **Risk Level**: **`LOW-MEDIUM`** (Fully mitigated via official `NavHost` adoption, activity-anchored service lifecycle with `isChangingConfigurations` guards, and complete IME insets engineering).
- **Justification**: While `MainActivityWithNavigation` is the root container, the risk is strictly controlled because all 21 child screens are already pure Compose implementations, service bindings remain anchored at the Activity level with configuration change guards, and all reflection/interop contracts are validated by unit tests.
- **Mitigation Strategy**:
  1. Retain `MainActivityWithNavigation` class identity and interface implementations, ensuring zero disruption to Android framework manifest launching or service binding.
  2. Implement official `NavHost` with type-safe route mapping and `popUpTo(saveState=true)` / `restoreState=true`.
  3. Validate all 32 test tasks with `./gradlew testDebugUnitTest` to guarantee zero regressions.
  4. Perform live physical hardware verification on device `66020DLCR002FL` (Pixel 10) across drawer navigation, tracking start, settings dialogs, soft keyboard input, and crash resumption.

---

## 11. Gate 1 Auditor Recommendation
- **Recommendation**: **`RECOMMEND PASS`**
- **Next Stage**: Stage 2 (Test Specification) upon Human Gate 1 Approval.
