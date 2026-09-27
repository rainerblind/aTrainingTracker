# Test Specification - ATT-1456: [Cockpit] Can't change the tracking tabs anymore :(

**Parent Ticket**: [ATT-1456](https://rainerblind.atlassian.net/browse/ATT-1456)  
**Sub-task**: [ATT-1459](https://rainerblind.atlassian.net/browse/ATT-1459) (`[Test-Spec]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-175`  
**Test ID**: `TST-UI-127`  

---

## 1. Traceability & Scope Alignment

| Item | Reference |
|---|---|
| **Parent Ticket** | [ATT-1456](https://rainerblind.atlassian.net/browse/ATT-1456) (*[Bug] Can't change the tracking tabs anymore :(* ) |
| **Requirement Specification** | `REQ-UI-175` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) |
| **Test Catalog** | `TST-UI-127` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) |
| **Analysis Deliverable** | [docs/engineering/analysis/ATT-1456_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-1456_analysis.md) |
| **Target Components** | `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `TrackingTabsScreen.kt`, `TrackingTabPreviewHeader.kt` |

---

## 2. Harmonized Requirement Specification (`REQ-UI-175`)

### REQ-UI-175: Tracking Tabs Configuration Transition and Hierarchical Navigation
The system SHALL ensure that selecting a sport type for tracking tab configuration reliably transitions `TrackingTabsScreen` to `ScreenMode.CONFIGURATION` for that sport type, and provides seamless hierarchical back navigation (ATT-1456):

1. **Activity-Scoped Reactive State Mutation**:
   - In `MainActivityWithNavigation.kt`, `onActivityTypeSelected(activityType: ActivityType)` SHALL retrieve the Activity-scoped `TrackingTabsViewModel` via `ViewModelProvider` and directly invoke `trackingTabsViewModel.setExplicitActivityType(activityType)` and `trackingTabsViewModel.setScreenMode(ScreenMode.CONFIGURATION)`.
   - The unobserved mutable property `pendingActivityType` SHALL be eliminated from `MainActivityWithNavigation.kt`.
2. **SingleTop Recomposition & Lifecycle Resilience**:
   - In `ATrainingTrackerApp.kt`, `composable(NavRoutes.START_TRACKING)` SHALL scope `TrackingTabsViewModel` to the host Activity (`viewModelStoreOwner = activity`).
   - The fragile `LaunchedEffect(activity.pendingActivityType)` bridge SHALL be eliminated, ensuring immediate reactive recomposition into `ScreenMode.CONFIGURATION` even when the athlete is already positioned on `NavRoutes.START_TRACKING`.
3. **Hierarchical Back Navigation & Preview Exit**:
   - In `TrackingTabsScreen.kt`, when in `ScreenMode.CONFIGURATION`, pressing the system back button or back gesture SHALL transition the screen to `ScreenMode.PREVIEW` via `trackingTabsViewModel.handleBackPressToPreview()`.
   - When in `ScreenMode.PREVIEW`, pressing the system back button or back gesture SHALL return the screen to `ScreenMode.TRACKING` via `trackingTabsViewModel.exitConfiguration()`, clearing the explicit activity type and preventing accidental application exit.
   - In `TrackingTabPreviewHeader.kt`, the header SHALL provide an explicit Done/Exit action button (`Icons.Default.Check`) that invokes `trackingTabsViewModel.exitConfiguration()`.
4. **Preserved Invariants**:
   - Normal tracking mode (`ScreenMode.TRACKING`), active telemetry, GPS tracking, and Page 0 Control tab MUST NOT be altered.
   - Tab editing actions (adding, deleting, renaming tabs, and metric toggles) in `TrackingViewsRepository` MUST NOT be affected.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-050` (*Navigation Drawer Structure*) and `REQ-UI-103` (*Customizable Cockpits*).
2. *Historical Origin & Commit Trace*: `REQ-SET-050` organized the drawer; `REQ-UI-103` established tab configuration originally hosted in `ConfigTrackingTabsActivity` before being embedded directly into `TrackingTabsScreen`.
3. *Root Reason for Existing Formulation*: When configuration was embedded, an unobserved Activity property `pendingActivityType` was introduced with a Compose `LaunchedEffect`. However, navigating to `START_TRACKING` with `launchSingleTop = true` when already on that route suppresses recomposition, causing `LaunchedEffect` to never execute.
4. *Preservation of Core Invariants*: Scoping `TrackingTabsViewModel` to the Activity and mutating its `StateFlow` directly guarantees instant reactive recomposition, preserves all tab customization workflows, and completes the back navigation hierarchy without side effects.

**Acceptance Criteria (Given-When-Then)**:
- *Given* an athlete on `NavRoutes.START_TRACKING` (or any other route) opening the navigation drawer,
- *When* the athlete taps "Tracking Tabs" (`drawer_tracking_layouts`) and selects an `ActivityType` (e.g., Radeln),
- *Then* `TrackingTabsScreen` SHALL immediately transition to `ScreenMode.CONFIGURATION` displaying `TrackingTabConfigHeader` for the chosen sport type.
- *Given* the athlete in `ScreenMode.CONFIGURATION`,
- *When* the athlete presses the system Back button/gesture or taps the Check button in the header,
- *Then* the screen SHALL transition to `ScreenMode.PREVIEW`.
- *Given* the athlete in `ScreenMode.PREVIEW`,
- *When* the athlete presses the system Back button/gesture or taps the Done button in `TrackingTabPreviewHeader`,
- *Then* the screen SHALL return to `ScreenMode.TRACKING` with standard telemetry and Page 0 control tabs.

**Invariants**: Active tracking telemetry, Page 0 Control Tracking, `TrackingViewsRepository` database operations, and cockpit theming MUST NOT be altered.

---

## 3. Detailed Test Specification (`TST-UI-127`)

### TST-UI-127: Tracking Tabs Configuration Transition and Hierarchical Navigation Verification

1. **Activity-Scoped ViewModel & Sport Selection Transition Unit Tests (`TrackingTabsViewModelInteropTest.kt`)**:
   - *Test 1.1*: Verify that when `onActivityTypeSelected(activityType)` is invoked on `MainActivityWithNavigation`, the Activity-scoped `TrackingTabsViewModel` receives `setExplicitActivityType(activityType)` and transitions `screenMode` to `ScreenMode.CONFIGURATION`.
   - *Test 1.2*: Verify that `activityType` StateFlow emits the selected `ActivityType` when `setExplicitActivityType` is set.
   - *Test 1.3*: Verify that `trackingViews` flow loads tracking views specific to the configured sport type.

2. **Hierarchical Back Navigation Flow Unit Tests**:
   - *Test 2.1*: In `ScreenMode.CONFIGURATION`, calling `handleBackPressToPreview()` transitions `screenMode` to `ScreenMode.PREVIEW`.
   - *Test 2.2*: In `ScreenMode.PREVIEW`, calling `exitConfiguration()` transitions `screenMode` to `ScreenMode.TRACKING` and resets `explicitActivityType` to `null`.
   - *Test 2.3*: In `ScreenMode.TRACKING`, calling `exitConfiguration()` or `handleBackPressToPreview()` is safe and preserves `ScreenMode.TRACKING`.

3. **Preview Header Exit Action Verification**:
   - *Test 3.1*: Verify `TrackingTabPreviewHeader` renders exit/done button (`Icons.Default.Check`) alongside the edit icon.
   - *Test 3.2*: Verify clicking the exit/done button executes `onExitConfig()`.

4. **On-Device Pixel 10 End-to-End Verification**:
   - *Test 4.1*: Open navigation drawer -> Tap "Tracking Tabs" (`drawer_tracking_layouts`) -> Select sport "Radeln" -> Verify header renders `TrackingTabConfigHeader` with tab name input, add/delete buttons, and metric checkboxes.
   - *Test 4.2*: Tap Check icon in `TrackingTabConfigHeader` -> Verify screen transitions to `ScreenMode.PREVIEW`.
   - *Test 4.3*: Press system Back or tap Done in `TrackingTabPreviewHeader` -> Verify screen transitions to `ScreenMode.TRACKING` without exiting the application.

5. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.
