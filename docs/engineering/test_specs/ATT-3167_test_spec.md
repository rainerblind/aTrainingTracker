# Test Specification: ATT-3167

## 1. System-Level Requirement Traceability
* **Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)
* **Primary Requirement**: `REQ-UI-350` (*Edge-to-Edge System Navigation Bar Inset Padding for Post-Workout Alert Snackbars and Action Controls*)
* **Amends / Refines**: `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting*)
* **Test Case Identifier**: `TST-UI-310`

---

## 2. Requirement Details (REQ-UI-350)
The system SHALL guarantee that in-app snackbars and interactive action buttons displayed on aftermath and management screens render cleanly above the Android system navigation bar / gesture navigation handle without visual occlusion or tap interference:
1. In `WorkoutSummariesTabbedScreen.kt`, the `SnackbarHost` composable SHALL configure `modifier = Modifier.navigationBarsPadding()` so that the low battery alert snackbar (`REQ-CON-007`) floats above the navigation bar / gesture navigation handle across all screen configurations.
2. In `WorkoutSummariesListFragment.kt` and `RoutesScreen.kt`, the `SnackbarHost` composables SHALL also configure `modifier = Modifier.navigationBarsPadding()` for visual and behavioural consistency.
3. The root `Scaffold` in `WorkoutSummariesTabbedScreen.kt` SHALL preserve `contentWindowInsets = WindowInsets(0.dp)` to maintain edge-to-edge drawing of map canvases and tab views without unwanted layout displacement.
4. The low battery warning message and the `[Zu Sensoren]` action button SHALL remain completely unobstructed and effortlessly clickable across 3-button, 2-button, and gesture navigation modes.

---

## 3. Detailed Acceptance Criteria & Test Scenarios

### Scenario 1: WorkoutSummariesTabbedScreen SnackbarHost Insets Padding
* **Given** `WorkoutSummariesTabbedScreen` is rendered in Compose.
* **When** inspecting the `SnackbarHost` node.
* **Then** the `SnackbarHost` has `Modifier.navigationBarsPadding()` (or equivalent navigation bar insets modifier) applied.
* **And** the host layout coordinates are offset from the bottom by the navigation bar insets height.

### Scenario 2: Unobstructed Low Battery Action Target
* **Given** an active workout completes with sensor battery at 15%.
* **When** `WorkoutSummariesTabbedScreen` displays the low battery alert snackbar.
* **Then** the `[Zu Sensoren]` action button is rendered above the system navigation bar boundary.
* **And** tapping the action button successfully dispatches navigation to `R.id.drawer_my_sensors`.

### Scenario 3: Clean-Room Full Suite Regression Execution
* **Given** all architectural improvements and targeted tests are implemented.
* **When** running `./gradlew testDebugUnitTest`.
* **Then** all unit tests pass 100% with zero regressions across the codebase.

---

## 4. Verification Matrix
| Test Case | Method / Component Under Test | Scope / Verification Target | Status |
| :--- | :--- | :--- | :--- |
| `testWorkoutSummariesTabbedScreen_snackbarHost_appliesNavigationBarsPadding` | `WorkoutSummariesTabbedScreenTest` | Verifies `Modifier.navigationBarsPadding()` presence on `SnackbarHost` | Verified |
| Full Suite Regression | `./gradlew testDebugUnitTest` | Zero test regressions | Verified |
