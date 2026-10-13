# Stage 1 Analysis: ATT-3167 - Post-workout low battery snackbar is occluded by Android system navigation bar

**Ticket**: [ATT-3167](https://atrainingtracker.atlassian.net/browse/ATT-3167)  
**Sub-task**: [ATT-3255](https://atrainingtracker.atlassian.net/browse/ATT-3255) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Branch**: `bugfix/ATT-3167`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation
When an athlete completes and stops a workout session where a connected peripheral sensor (heart rate monitor, cycling power meter, etc.) reported critical battery ($\le 20\%$), the app emits an in-app Material 3 `Snackbar` on `WorkoutSummariesTabbedScreen` (`⚠️ [Sensor]: Akku schwach (15%)` with action button `[Zu Sensoren]`), conforming to `REQ-CON-007`.

However, on devices utilizing gesture navigation or standard 3-button navigation, the `Snackbar` is positioned flush at the extreme bottom edge of the display window and rendered directly behind the Android OS navigation bar / gesture pill handle. This occludes the text and renders the `[Zu Sensoren]` action button difficult or impossible to tap, violating Google Android Edge-to-Edge Design Guidelines and ASPICE usability standards.

---

## 2. Root Cause Analysis (Forensic Investigation)
In `WorkoutSummariesTabbedScreen.kt` (lines 236–248):
```kotlin
Scaffold(
    modifier = modifier,
    snackbarHost = {
        SnackbarHost(snackbarHostState) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                actionColor = MaterialTheme.colorScheme.primary
            )
        }
    },
    contentWindowInsets = WindowInsets(0.dp)
) { paddingValues ->
```
1. `Scaffold` explicitly configures `contentWindowInsets = WindowInsets(0.dp)`. This was originally introduced to prevent double-padding the inner tab contents and map views.
2. Under Material 3 `Scaffold`, `contentWindowInsets` defines the default inset consumption for child slots. When set to `0.dp`, `SnackbarHost` receives zero bottom insets and renders at offset $Y = \text{screenHeight} - \text{snackbarHeight}$.
3. `SnackbarHost(snackbarHostState)` provides no custom modifier applying `Modifier.navigationBarsPadding()` or `Modifier.windowInsetsPadding(WindowInsets.navigationBars)`.
4. As a result, the snackbar sits directly in the system gesture / navigation inset area ($16\text{dp} \dots 48\text{dp}$ from the bottom), occluded behind the OS system UI.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Apply `Modifier.navigationBarsPadding()` to `SnackbarHost` in `WorkoutSummariesTabbedScreen.kt`.
  * Ensure the same inset padding is present in `WorkoutSummariesListFragment.kt` and `RoutesScreen.kt` for visual consistency.
  * Verify that `[Zu Sensoren]` action button and warning text float with comfortable clearance above the navigation bar / gesture handle across all screen orientations and navigation modes.
  * Construct targeted Compose UI tests verifying `navigationBarsPadding` or insets application on the snackbar host.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to battery threshold logic ($\le 20\%$) or notification channels in `SensorBatteryNotificationManager.kt`.
  * No restructuring of `TrackOnMapScreen` or workout summary tab layouts.
  * No modifications to database schemas or sensor communication services.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting (In-App Snackbar & System Notification)*) and introduces `REQ-UI-350` (*Edge-to-Edge Navigation Bar Insets Padding for System-Floating Snackbars*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.16 (`ATT-2192`, commit `ac493d2bdf652746b9177a2c574b220285b3bf8b`), which migrated modal alert dialogs to Material 3 snackbars.
* **Root Reason for Existing Formulation**: `contentWindowInsets = WindowInsets(0.dp)` was set on `Scaffold` to prevent the inner `Box` and `TrackOnMapScreen` from being double-padded below the system status bar and above navigation bars. However, omitting navigation bar padding on `SnackbarHost` left the snackbar pinned behind the OS navigation bar.
* **Preservation of Core Invariants**: The sticky event consumption (`WorkoutNavigationEvents.consumeLowBatteryAlert()`), battery threshold ($\le 20\%$), action navigation to `R.id.drawer_my_sensors`, and 100% full clean-room test suite pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. In `WorkoutSummariesTabbedScreen.kt`:
   Configure `SnackbarHost` with `Modifier.navigationBarsPadding()`:
   ```kotlin
   snackbarHost = {
       SnackbarHost(
           hostState = snackbarHostState,
           modifier = Modifier.navigationBarsPadding()
       ) { data ->
           Snackbar(...)
       }
   }
   ```
2. In `WorkoutSummariesListFragment.kt` and `RoutesScreen.kt`:
   Apply `Modifier.navigationBarsPadding()` to their respective `SnackbarHost` composables.
3. Verify that the snackbar adjusts dynamically when navigation bar insets change (keyboard open/closed, rotation, 3-button vs gesture navigation).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Low battery snackbar continues to trigger only when session sensors report $\le 20\%$ battery.
  2. Tapping `[Zu Sensoren]` continues to dispatch navigation to `R.id.drawer_my_sensors`.
  3. `contentWindowInsets = WindowInsets(0.dp)` on `Scaffold` remains untouched so that the workout detail map canvas and list tabs retain edge-to-edge drawing.
  4. 100% full clean-room unit test pass rate.
* **Risk Assessment**:
  * **Risk Level**: **LOW**. Single composable modifier addition adhering directly to Android M3 edge-to-edge best practices. Zero side effects on business logic, databases, or sensors.
