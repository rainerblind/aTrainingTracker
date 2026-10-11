# Walkthrough: ATT-3167

## Edge-to-Edge System Navigation Bar Inset Padding for Post-Workout Alert Snackbars and Action Controls

### Executive Summary
When finishing an active workout with a low sensor battery ($\le 20\%$), the app displays an in-app alert snackbar prompting the athlete with warning text and an action button (`[Zu Sensoren]`) to navigate to sensor management (`REQ-CON-007`).
However, on devices utilizing system navigation (such as standard 3-button navigation or gesture navigation bars), the snackbar was rendered flush against the physical bottom edge of the display. As a result, the snackbar content and the action button were occluded by the Android system navigation bar, preventing taps or conflicting with system back/home gestures.

Investigation revealed that:
1. `WorkoutSummariesTabbedScreen.kt` configures `contentWindowInsets = WindowInsets(0.dp)` on its root `Scaffold` to maintain full-bleed edge-to-edge drawing for map canvases and tabbed viewports.
2. Under Material 3 Compose, zeroing the Scaffold's `contentWindowInsets` removes system navigation bar insets from the `snackbarHost` slot by default.
3. Because `SnackbarHost(snackbarHostState)` was called without custom modifiers, the snackbar anchored directly to the bottom screen edge without padding for the navigation bar or gesture pill.
4. Identical layout behavior was present in `WorkoutSummariesListFragment.kt` and `RoutesScreen.kt`.

This ticket resolves the issue cleanly:
- **Navigation Bar Inset Padding on SnackbarHost**: Configured `modifier = Modifier.navigationBarsPadding()` on `SnackbarHost` in `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, and `RoutesScreen.kt`. The snackbar and its `[Zu Sensoren]` button now float above the system navigation bar and gesture handle across all display configurations.
- **Edge-to-Edge Canvas Invariant**: Preserved `contentWindowInsets = WindowInsets(0.dp)` on `Scaffold` across all screens to ensure map viewports and tab layouts maintain edge-to-edge rendering without unwanted double padding or layout shifts.
- **Alerting & Navigation Flow Invariants**: Preserved low battery alert evaluation ($\le 20\%$), non-blocking navigation events, and single-click routing to `R.id.drawer_my_sensors`.

---

## Changes Made

### 1. UI Layer & Compose Insets
* [WorkoutSummariesTabbedScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedScreen.kt#L236-L248):
  Added `import androidx.compose.foundation.layout.navigationBarsPadding`.
  Configured `modifier = Modifier.navigationBarsPadding()` on `SnackbarHost` in `Scaffold.snackbarHost`.
* [WorkoutSummariesListFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt#L164-L176):
  Added `import androidx.compose.foundation.layout.navigationBarsPadding`.
  Configured `modifier = Modifier.navigationBarsPadding()` on `SnackbarHost` in `Scaffold.snackbarHost`.
* [RoutesScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt#L108-L121):
  Added `import androidx.compose.foundation.layout.navigationBarsPadding`.
  Configured `modifier = Modifier.navigationBarsPadding()` on `SnackbarHost` in `Scaffold.snackbarHost`.

### 2. Architecture & Contract Tests
* [WorkoutSummariesTabbedScreenTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedScreenTest.kt):
  Created unit and contract test suite verifying that `SnackbarHost` in `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, and `RoutesScreen.kt` configures `Modifier.navigationBarsPadding()` while preserving `contentWindowInsets = WindowInsets(0.dp)` on `Scaffold`.

### 3. Living Documentation & Governance
* [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md):
  Added `REQ-UI-350` (*Edge-to-Edge System Navigation Bar Inset Padding for Post-Workout Alert Snackbars and Action Controls*), marked as `Verified`.
* [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md):
  Added `TST-UI-310` (*Post-Workout Low Battery Alert Snackbar Navigation Bar Inset Padding Verification*), marked as `Verified`.

---

## Verification Results

### Targeted Unit Tests
| Test Class | Test Method | Scope / Target | Result |
| :--- | :--- | :--- | :--- |
| `WorkoutSummariesTabbedScreenTest` | `testWorkoutSummariesTabbedScreen_snackbarHost_appliesNavigationBarsPadding` | Verifies `navigationBarsPadding()` applied on `SnackbarHost` | **PASSED** (100%) |
| `WorkoutSummariesTabbedScreenTest` | `testWorkoutSummariesListFragment_snackbarHost_appliesNavigationBarsPadding` | Verifies `navigationBarsPadding()` applied on `SnackbarHost` | **PASSED** (100%) |
| `WorkoutSummariesTabbedScreenTest` | `testRoutesScreen_snackbarHost_appliesNavigationBarsPadding` | Verifies `navigationBarsPadding()` applied on `SnackbarHost` | **PASSED** (100%) |
| `RoutesScreenContractTest` | (All tests) | Verifies route styling contracts remain intact | **PASSED** (100%) |

### Clean-Room Full Suite Regression
- Executed `./gradlew testDebugUnitTest` across all test suites to confirm 100% test pass rate with zero regressions (BUILD SUCCESSFUL in 2m 27s).
