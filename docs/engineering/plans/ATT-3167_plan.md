# Implementation Plan: ATT-3167

## 1. Traceability & Requirements Mapping
* **Parent Ticket**: [ATT-3167](https://atrainingtracker.atlassian.net/browse/ATT-3167) (`[Bug] Post-workout low battery snackbar is occluded by Android system navigation bar`)
* **Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)
* **Primary Requirement**: `REQ-UI-350` (*Edge-to-Edge System Navigation Bar Inset Padding for Post-Workout Alert Snackbars and Action Controls*)
* **Test Case**: `TST-UI-310`

---

## 2. Architecture & Design Decisions (SWE.2)
* **Inset Positioning Strategy**:
  - `Scaffold` in `WorkoutSummariesTabbedScreen.kt` maintains `contentWindowInsets = WindowInsets(0.dp)` to enable full edge-to-edge drawing for map previews and list viewports.
  - The `snackbarHost` lambda slot receives the host composable. Under Material 3, `SnackbarHost` defaults to placing the snackbar at the bottom center of the slot.
  - To respect system navigation bars and gesture handles without affecting the inner screen body, `Modifier.navigationBarsPadding()` must be added directly to the `SnackbarHost` composable:
    ```kotlin
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.navigationBarsPadding()
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionColor = MaterialTheme.colorScheme.primary
        )
    }
    ```
  - This ensures the snackbar automatically lifts by the exact system navigation bar / gesture navigation handle height on any device orientation or display mode.
* **Secondary Surface Parity**:
  - For visual and architectural consistency across all aftermath and management screens with `contentWindowInsets = WindowInsets(0.dp)`, apply `Modifier.navigationBarsPadding()` to `SnackbarHost` in `WorkoutSummariesListFragment.kt` and `RoutesScreen.kt`.

---

## 3. Preserved Invariants & Chesterton's Fence
* **Edge-to-Edge Content Invariant**: The inner map canvas (`TrackOnMapScreen`) and workout list retain `contentWindowInsets = WindowInsets(0.dp)` to prevent unwanted layout shifts.
* **Battery Alerting Invariant (`REQ-CON-007`)**: The evaluation threshold ($\le 20\%$), non-blocking alert flow via `WorkoutNavigationEvents`, and action button navigation to `R.id.drawer_my_sensors` remain strictly intact.
* **Scope Bounding (ATT-1250)**: Confined strictly to `SnackbarHost` navigation bar insets padding. No changes to notification managers, database schemas, or tracking services.

---

## 4. Step-by-Step Atomic Construction Steps

### Step 1: Add Navigation Bar Padding to SnackbarHost in `WorkoutSummariesTabbedScreen.kt`
* In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedScreen.kt`:
  In `Scaffold.snackbarHost`:
  Pass `hostState = snackbarHostState, modifier = Modifier.navigationBarsPadding()` to `SnackbarHost`.

### Step 2: Add Navigation Bar Padding to Secondary Screens
* In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt`:
  In `Scaffold.snackbarHost`:
  Pass `hostState = snackbarHostState, modifier = Modifier.navigationBarsPadding()` to `SnackbarHost`.
* In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`:
  In `Scaffold.snackbarHost`:
  Pass `hostState = snackbarHostState, modifier = Modifier.navigationBarsPadding()` to `SnackbarHost`.

### Step 3: Targeted Unit / Compose Tests
* Construct or expand unit tests in `WorkoutSummariesTabbedScreenTest.kt` asserting that `SnackbarHost` receives `Modifier.navigationBarsPadding()`.
* Run targeted tests via `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesTabbedScreenTest`.

### Step 4: Clean-Room Full Suite Regression
* Run full suite: `./gradlew testDebugUnitTest`.
