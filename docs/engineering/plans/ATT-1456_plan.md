# Implementation Plan - ATT-1456: [Cockpit] Can't change the tracking tabs anymore :(

**Parent Ticket**: [ATT-1456](https://rainerblind.atlassian.net/browse/ATT-1456)  
**Sub-task**: [ATT-1460](https://rainerblind.atlassian.net/browse/ATT-1460) (`[Impl-Plan]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-175`  
**Test ID**: `TST-UI-127`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1456_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1456_test_spec.md`  
**Branch**: `feature/ATT-1456`  

---

## 1. Executive Summary & Architectural Scope

The goal of **ATT-1456** is to restore the tracking tabs configuration workflow from the navigation drawer and guarantee complete hierarchical back navigation within `TrackingTabsScreen`.

The implementation addresses the state scoping and recomposition defects identified in Stage 1 and specified in Stage 2:
1. **Activity-Scoped ViewModel**: Scope `TrackingTabsViewModel` to the host `MainActivityWithNavigation` in `ATrainingTrackerApp.kt` via `viewModelStoreOwner = activity`.
2. **Direct Reactive State Mutation**: Eliminate the unobserved property `pendingActivityType` on `MainActivityWithNavigation`. When the athlete selects a sport type in `ActivityTypeSelectionDialog`, `onActivityTypeSelected(activityType)` directly retrieves the Activity-scoped `TrackingTabsViewModel` and sets `setExplicitActivityType(activityType)` and `setScreenMode(ScreenMode.CONFIGURATION)`.
3. **Recomposition on SingleTop Routes**: Eliminate `LaunchedEffect(activity.pendingActivityType)`. Because `_screenMode` is an observed `StateFlow` collected inside `TrackingTabsScreen`, mutating the ViewModel state triggers instant reactive recomposition into configuration mode, even if `navController.navigate(NavRoutes.START_TRACKING)` with `launchSingleTop = true` evaluates as a NO-OP.
4. **Hierarchical Back Navigation**: In `TrackingTabsScreen.kt`, register a `BackHandler` for `ScreenMode.PREVIEW` that calls `trackingTabsViewModel.exitConfiguration()`. In `TrackingTabPreviewHeader.kt`, provide an explicit Done action button (`Icons.Default.Check`) that invokes `trackingTabsViewModel.exitConfiguration()`, preventing accidental app exits when exiting preview mode.

---

## 2. Step-by-Step Implementation Strategy

### Phase 1: ViewModel Scoping & Reactive State Mutation

1. **[MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)**:
   - Remove line 176: `var pendingActivityType: ActivityType? = null`.
   - Update `onActivityTypeSelected(activityType: ActivityType)` (lines 796–799):
     ```kotlin
     fun onActivityTypeSelected(activityType: ActivityType) {
         val trackingTabsViewModel = androidx.lifecycle.ViewModelProvider(
             this,
             TrackingTabsViewModelFactory(application)
         )[TrackingTabsViewModel::class.java]
         trackingTabsViewModel.setExplicitActivityType(activityType)
         trackingTabsViewModel.setScreenMode(ScreenMode.CONFIGURATION)
         navigateToDrawerItem(R.id.drawer_start_tracking)
     }
     ```

2. **[ATrainingTrackerApp.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt)**:
   - Update `composable(NavRoutes.START_TRACKING)` (lines 265–277):
     ```kotlin
     composable(NavRoutes.START_TRACKING) {
         val trackingTabsViewModel: TrackingTabsViewModel = viewModel(
             viewModelStoreOwner = activity,
             factory = TrackingTabsViewModelFactory(activity.application)
         )
         TrackingTabsScreen(trackingTabsViewModel = trackingTabsViewModel)
     }
     ```
   - Eliminate `LaunchedEffect(activity.pendingActivityType)`.

### Phase 2: Hierarchical Back Navigation & Preview Exit Action

1. **[TrackingTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt)**:
   - Add back handler for `ScreenMode.PREVIEW` below the existing `ScreenMode.CONFIGURATION` handler:
     ```kotlin
     BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
         trackingTabsViewModel.handleBackPressToPreview()
     }
     BackHandler(enabled = screenMode == ScreenMode.PREVIEW) {
         trackingTabsViewModel.exitConfiguration()
     }
     ```
   - Update `TrackingTabPreviewHeader` invocation to pass `onExitConfig`:
     ```kotlin
     ScreenMode.PREVIEW -> {
         if (currentViewInfo != null) {
             TrackingTabPreviewHeader(
                 viewInfo = currentViewInfo,
                 onToggleMode = { trackingTabsViewModel.toggleScreenMode() },
                 onExitConfig = { trackingTabsViewModel.exitConfiguration() }
             )
         }
     }
     ```

2. **[TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt)**:
   - Update signature to accept `onExitConfig: () -> Unit = {}`:
     ```kotlin
     @Composable
     fun TrackingTabPreviewHeader(
         viewInfo: TrackingViewInfo,
         onToggleMode: () -> Unit,
         onExitConfig: () -> Unit = {},
     )
     ```
   - In the action row, render a Done button alongside the Edit button:
     ```kotlin
     // DONE BUTTON
     IconButton(onClick = onExitConfig) {
         Icon(
             imageVector = Icons.Default.Check,
             contentDescription = stringResource(R.string.Done),
             tint = MaterialTheme.colorScheme.onPrimaryContainer
         )
     }
     ```

### Phase 3: Unit Testing & Automated Verification (`TrackingTabsViewModelInteropTest.kt`)

Create unit tests in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/TrackingTabsViewModelInteropTest.kt`:
1. `testActivityTypeSelectionTransitionsToConfiguration`:
   - Verify that setting explicit activity type on `TrackingTabsViewModel` updates `activityType` StateFlow and sets `screenMode` to `ScreenMode.CONFIGURATION`.
2. `testBackNavigationFromConfigurationToPreview`:
   - Verify `handleBackPressToPreview()` transitions `screenMode` from `CONFIGURATION` to `PREVIEW`.
3. `testExitConfigurationFromPreviewReturnsToTracking`:
   - Verify `exitConfiguration()` transitions `screenMode` from `PREVIEW` to `TRACKING` and resets `explicitActivityType` to `null`.
4. `testExitConfigurationSafeInTrackingMode`:
   - Verify `exitConfiguration()` called in `TRACKING` mode preserves `TRACKING` mode without errors.

### Phase 4: Physical Device Verification (Google Pixel 10)

1. Deploy debug build to connected Google Pixel 10 (`66020DLCR002FL`).
2. Live Navigation Trace:
   - Open navigation drawer -> Tap "Tracking Tabs".
   - Select "Radeln" in `ActivityTypeSelectionDialog`.
   - Verify screen immediately displays `TrackingTabConfigHeader` with tab name input, add/delete buttons, and metric checkboxes.
3. Live Back Navigation Trace:
   - Tap Check icon in config header -> Verify screen transitions to `ScreenMode.PREVIEW`.
   - Tap Done button or press system back gesture -> Verify screen returns to `ScreenMode.TRACKING` without exiting the application.
4. Capture screenshots for visual confirmation.

### Phase 5: Clean-Room Full Suite Regression Execution

Execute `./gradlew testDebugUnitTest` across all modules to ensure 100% test pass rate with 0 regressions.

---

## 3. Preserved Invariants & Boundary Verification

1. **Active Telemetry Invariant**: `ScreenMode.TRACKING` active sensor streaming, GPS tracking, and metric calculation pipelines MUST NOT be altered.
2. **Page 0 Control Tracking Invariant**: Start, Pause, Stop buttons and sensor pairing on Page 0 (`ControlTrackingScreen`) MUST NOT be affected.
3. **Data Persistence Invariant**: All tab modifications (name changes, tile ordering, lap button, live segments, map, and elevation profile checkboxes) in `TrackingViewsRepository` MUST continue to operate without side effects.
4. **Theme Invariant**: AMOLED Pure Black and standard Dark/Light theme resolution across tracking and configuration views MUST NOT be altered.
5. **Zero Memory Leaks**: Scoping `TrackingTabsViewModel` to `MainActivityWithNavigation` guarantees clean lifecycle tear-down when the activity finishes.
