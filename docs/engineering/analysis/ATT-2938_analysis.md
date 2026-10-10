# Stage 1 Analysis: ATT-2938 - Prevent unsolicited ReturnNavigationHud activation during regular route navigation and gate by tab navigation hints

**Ticket**: [ATT-2938](https://atrainingtracker.atlassian.net/browse/ATT-2938)  
**Sub-task**: [ATT-2966](https://atrainingtracker.atlassian.net/browse/ATT-2966) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2938`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During active workout tracking on physical devices (Google Pixel 10), athletes following a selected navigation route frequently observe an unsolicited "Zu Hause" Return Navigation HUD card (`ReturnNavigationHud.kt`) anchored at the top of their cockpit screen:
> `🏠 Zu Hause • 8.2 km • +137 m • 17:32 (~41 min)`

This card appears even though the athlete never activated "Take Me Home" ("Heimweg") mode. Furthermore, because the card is rendered as an in-flow composable inside the main cockpit `Column` in `SensorGridScreen.kt`, its appearance vertically displaces all underlying sensor grid tiles downwards. Finally, the card appears across all tracking tabs, ignoring whether navigation hints are enabled or disabled on that tab.

### Current State vs. Expected Behavior
* **Current State**:
  1. Selecting any navigation route whose terminus lies within 500 meters of the athlete's home location causes `ReturnNavigationRepository` to automatically engage return navigation metrics, hijack the destination title to "Zu Hause", and display `ReturnNavigationHud`.
  2. In `SensorGridScreen.kt`, `ReturnNavigationHud` is rendered in-flow in `Column`, pushing the sensor grid down and disrupting visual stationarity.
  3. `ReturnNavigationHud` lacks `state.showNavigationHints` gating, appearing on tabs where the user explicitly turned off navigation cues.
* **Expected Behavior**:
  1. Regular route navigation must never trigger a "Take Me Home" / "Zu Hause" Return Navigation HUD unless the athlete explicitly activated "Take Me Home" (`isTakeMeHomeMode = true`).
  2. `ReturnNavigationHud` must be conditionally gated strictly behind `state.showNavigationHints`, ensuring 0% visibility on navigation-free tabs.
  3. `ReturnNavigationHud` must float as a non-displacing top-level overlay (`Alignment.TopCenter`), preserving 100% stationarity of underlying sensor telemetry tiles.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic investigation of `ReturnNavigationRepository.kt`, `ReturnCorridorSnapper.kt`, and `SensorGridScreen.kt` identified three interrelated architectural flaws:

### 2.1 Conflated Activation Trigger (`ReturnNavigationRepository.kt:146`)
In `ReturnNavigationRepository.recalculateNavigationMetrics`:
```kotlin
val activeRoute = currentActiveRoute
val hasActiveRoute = activeRoute != null && activeRoute.path.isNotEmpty()

// Active if either navigating a route OR athlete explicitly requested "Take Me Home"
val isActive = hasActiveRoute || isTakeMeHomeMode
```
`isActive` evaluated to `true` whenever an athlete simply selected a route to follow (`hasActiveRoute == true`). This conflated ordinary route navigation with return navigation, computing return metrics and asserting `isActive = true` in `_navigationState` even when `isTakeMeHomeMode` was false.

### 2.2 Automatic Destination Override to "Home" (`ReturnCorridorSnapper.kt:229-232`)
When evaluating an active route, `ReturnCorridorSnapper.snapActiveRoute` evaluated proximity between the route endpoint and the athlete's home location:
```kotlin
val endLoc = path.last().latLng
val distToHome = if (homeDestination != null) {
    val res = FloatArray(1)
    Location.distanceBetween(
        endLoc.latitude, endLoc.longitude,
        homeDestination.latLng.latitude, homeDestination.latLng.longitude,
        res
    )
    res[0].toDouble()
} else Double.MAX_VALUE

if (distToHome <= MAX_HOME_SEARCH_RADIUS_METERS && homeDestination != null) {
    destName = homeDestination.name
    isHome = true
} else {
    destName = route.summary.name
    isHome = false
}
```
Because endurance athletes typically plan round-trip loops that start and finish near home, `distToHome <= 500m` evaluates to `true` for virtually all outdoor cycling and running workouts. Consequently, whenever `ReturnNavigationRepository` invoked `snapToCorridor` with `cachedHomeDestination`, `destName` was overridden to `"Zu Hause"` and `isHome` was flagged as `true`, completely hijacking the route title.

### 2.3 Missing Tab Navigation Gating & Layout In-Flow Displacement (`SensorGridScreen.kt:475`)
In `SensorGridScreen.kt`, `ReturnNavigationHud` was placed inside the primary layout `Column`:
```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    TurnPromptBanner(
        navigationState = navState,
        promptsEnabled = state.showNavigationHints && tuningConfig.turnPromptsEnabled,
        ...
    )

    // Return Navigation & Dynamic Elevation-Aware ETA HUD Banner (REQ-MAP-029 / ATT-1953)
    ReturnNavigationHud(
        navigationState = returnNavState,
        overlayAlpha = tuningConfig.navigationCueTransparency,
        onDismiss = { returnNavRepo.dismissHud() }
    )

    // 1. The Sensor Grid (Scrollable)
    Column(...) { ... }
}
```
* **Layout In-Flow Displacement**: Whenever `returnNavState.hasRemainingMetrics` transitioned to `true`, `ReturnNavigationHud` expanded inside `Column`, shifting the scrollable sensor grid down by ~60dp.
* **Missing Tab Gating**: While `TurnPromptBanner` and `ForkDecisionCard` are guarded by `state.showNavigationHints`, `ReturnNavigationHud` was rendered unconditionally, polluting telemetry-only cockpit tabs.

---

## 3. Proposed Technical Solution & Architecture

### 3.1 Strict "Take Me Home" Mode Enforcement (`ReturnNavigationRepository.kt`)
Return navigation metrics, ETA calculations, and HUD banner state must be active **strictly and exclusively** when the athlete explicitly engages "Take Me Home" mode (`isTakeMeHomeMode == true`):
```kotlin
val activeRoute = currentActiveRoute
val hasActiveRoute = activeRoute != null && activeRoute.path.isNotEmpty()

// Active ONLY if athlete explicitly requested "Take Me Home"
val isActive = isTakeMeHomeMode

if (!isActive) {
    _navigationState.value = ReturnNavigationState(isActive = false)
    return
}
```
When `isTakeMeHomeMode == false`, `_navigationState` immediately emits `ReturnNavigationState(isActive = false)`. `hasRemainingMetrics` evaluates to `false`, preventing `ReturnNavigationHud` from ever activating during normal route navigation.

### 3.2 State Lifecycle & Session Reset Invariant
To prevent state leakage across workouts or route switching, `ReturnNavigationRepository` enforces a strict lifecycle reset contract:
1. **Explicit Deactivation**:
   ```kotlin
   fun stopTakeMeHome() {
       isTakeMeHomeMode = false
       isReverseReturn = false
       isDismissed = false
       _navigationState.value = ReturnNavigationState(isActive = false)
   }
   ```
2. **Route Cleared / Deselected**:
   When `routesRepository.activeNavigatedRouteId` emits `null` (or the route is cleared via `routeSelectorViewModel.clearRoute()`), if return navigation was following the active route, `isReverseReturn` is reset to `false` and return metrics re-evaluate against saved home routes or direct geodesic vector. If no return was requested, state remains cleanly inactive.
3. **Session Reset**:
   When tracking is stopped or reset, `stopTakeMeHome()` is called to guarantee that subsequent workouts start with a clean, inactive return navigation state.

### 3.3 Method Signature & Home Snapping Decoupling (`ReturnCorridorSnapper.kt`)
The method signature of `snapActiveRoute` is preserved:
```kotlin
@JvmStatic
fun snapActiveRoute(
    currentLat: Double,
    currentLng: Double,
    route: RouteWithPath,
    isReverseReturn: Boolean,
    homeDestination: HomeDestination? = null
): CorridorSnapResult
```
In `ReturnNavigationRepository.recalculateNavigationMetrics`:
* `cachedHomeDestination` is passed into `snapToCorridor` **only** when `isTakeMeHomeMode == true`:
  ```kotlin
  val snap = ReturnCorridorSnapper.snapToCorridor(
      currentLat = currentLocation.latitude,
      currentLng = currentLocation.longitude,
      currentAltitude = 0.0,
      activeRoute = if (hasActiveRoute) activeRoute else null,
      homeDestination = if (isTakeMeHomeMode) cachedHomeDestination else null,
      savedRoutes = allSavedRoutes,
      forceReverseReturn = isReverseReturn
  )
  ```
* When `homeDestination` is `null`, `ReturnCorridorSnapper.snapActiveRoute` uses the pristine route summary name (`destName = route.summary.name`) and sets `isHomeDestination = false`.
* When `isTakeMeHomeMode == true` and `homeDestination != null`, if the route terminates within 500m of home, `destName` resolves to `homeDestination.name` and `isHomeDestination = true`.

### 3.4 State Machine Formalization

| Mode / Condition | `currentActiveRoute` | `isTakeMeHomeMode` | Repository `isActive` | Destination Name in HUD | HUD Card Visible? |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Regular Route Navigation** | `Route A` (terminates near home) | `false` | `false` | None (`ReturnNavigationState()`) | **NO** (0% visible) |
| **Regular Route Navigation** | `Route B` (remote terminus) | `false` | `false` | None (`ReturnNavigationState()`) | **NO** (0% visible) |
| **Mid-Ride "Take Me Home"** | `Route A` (terminates near home) | `true` | `true` | `HomeDestination.name` ("Zu Hause") | **YES** (gated by tab hints) |
| **Mid-Ride "Take Me Home" (Reverse)** | `Route A` (out-and-back) | `true` | `true` | `HomeDestination.name` or `Route A (Start)` | **YES** (gated by tab hints) |
| **Unrouted "Take Me Home"** | `null` | `true` | `true` | `HomeDestination.name` | **YES** (gated by tab hints) |
| **Route Cleared / Deselected** | Transitions to `null` | `false` | `false` | None | **NO** |

### 3.5 Non-Displacing Top-Level Overlay & Tab-Level Gating (`SensorGridScreen.kt`)
1. Remove `ReturnNavigationHud` from the base stationary cockpit `Column`.
2. Host `ReturnNavigationHud` inside the top-level non-displacing overlay container at `Alignment.TopCenter`, stacked harmoniously with `ForkDecisionCard`:
```kotlin
// Top-Level Ambient Navigation Overlays (REQ-MAP-031, REQ-MAP-029, ATT-2874, ATT-2938)
// Floats on top at Alignment.TopCenter, strictly gated by state.showNavigationHints
if (state.showNavigationHints) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
    ) {
        ReturnNavigationHud(
            navigationState = returnNavState,
            overlayAlpha = tuningConfig.navigationCueTransparency,
            onDismiss = { returnNavRepo.dismissHud() }
        )

        ForkDecisionCard(
            decisionState = forkDecisionState,
            overlayAlpha = tuningConfig.navigationCueTransparency,
            onRouteSelected = { routeId ->
                forkNavRepo.selectRouteManually(routeId)
            },
            onDismiss = {
                forkNavRepo.dismissPrompt()
            }
        )
    }
}
```
3. When `state.showNavigationHints == false`, the entire overlay container is excluded from the composition tree, ensuring 0% touch interception and complete suppression on sensor-only tabs.
4. Because the container is hosted in the sibling overlay layer of `Box`, appearance or dismissal of `ReturnNavigationHud` causes 0dp displacement to the underlying sensor grid tiles.

---

## 4. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Modify `ReturnNavigationRepository.kt` to require `isTakeMeHomeMode == true` for `isActive` state and corridor snapping.
  2. Enforce the lifecycle reset rule in `ReturnNavigationRepository` upon `stopTakeMeHome()`, route clearing, and session termination.
  3. Update `SensorGridScreen.kt` to move `ReturnNavigationHud` to the top-level overlay container at `Alignment.TopCenter`.
  4. Gate `ReturnNavigationHud` strictly behind `state.showNavigationHints`.
  5. Author unit and architectural contract tests in `ReturnNavigationRepositoryTest.kt` and `SensorGridScreenRouteIntegrationTest.kt`.
  6. Validate against the full clean-room test suite (`./gradlew testDebugUnitTest`).
* **Out-of-Scope Non-Goals**:
  * No alterations to `TurnPromptBanner`, `ForkRouteMatcher`, or turn-by-turn routing algorithms.
  * No modifications to `RouteSelectorSheet.kt` or mid-ride "Take Me Home" entry points (verified in `RouteSelectorMidRideContractTest`).
  * No SQLite schema alterations or database migrations.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

* **Original Requirement ID & Target**: Amends `REQ-MAP-029` Clause 4 (*Cockpit HUD Integration*) and interfaces with `REQ-UI-282` Clause 2 (*Harmonized ReturnNavigationHud*).
* **Historical Origin & Commit Trace**:
  - `REQ-MAP-029` was introduced in Sprint 2026-40.16 (`ATT-1953`, commit `c8ac2c5e` / `0994f794`).
  - `REQ-UI-282` was introduced in Sprint 2026-41.1 (`ATT-2462`, commit `6d0a5d24`), harmonizing HUD tokens and adding the mid-ride Heimweg card.
* **Root Reason for Existing Formulation**:
  - In ATT-1953, Clause 4 originally stated: *"when Return Navigation is active or an active route has remaining progress, render a high-visibility HUD banner"*. The intent was to show remaining distance/climb for any loaded route.
  - However, because round-trip routes end near home, this caused `snapActiveRoute` to label the destination as "Zu Hause" and display the Home icon, misleading athletes into thinking "Take Me Home" was unexpectedly active.
  - Furthermore, in-flow layout placement in `SensorGridScreen` pushed cockpit tiles down, creating unwanted visual jitter.
* **Preservation of Core Invariants**:
  - Explicit "Take Me Home" activation (`startTakeMeHome()`), reverse route return (`setReverseReturn()`), elevation-aware dynamic ETA calculations, and 100% test pass rate across the full test suite MUST be strictly preserved.

---

## 6. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (No Unsolicited Return HUD during Regular Route Navigation)**:
  * *Given* an athlete navigating a regular route that terminates near home,
  * *When* viewing the tracking screen without having activated "Take Me Home",
  * *Then* `ReturnNavigationRepository.navigationState.value.isActive` SHALL be `false`,
  * *And* `ReturnNavigationHud` SHALL NOT be displayed.
* **Criterion 2 (Explicit "Take Me Home" Activation)**:
  * *Given* an active workout tracking session with or without a route,
  * *When* the athlete activates "Take Me Home" (`isTakeMeHomeMode = true`),
  * *Then* `ReturnNavigationHud` SHALL display destination name, remaining distance, climb, and dynamic ETA.
* **Criterion 3 (Tab Navigation Hints Gating)**:
  * *Given* active "Take Me Home" navigation,
  * *When* viewing a tracking tab with `showNavigationHints = false`,
  * *Then* `ReturnNavigationHud` SHALL be completely excluded from the composition tree.
  * *When* viewing a tracking tab with `showNavigationHints = true`,
  * *Then* `ReturnNavigationHud` SHALL be visible.
* **Criterion 4 (Stationary Sensor Grid Layout)**:
  * *Given* `SensorGridScreen`,
  * *When* `ReturnNavigationHud` appears, expands, or dismisses,
  * *Then* underlying sensor tiles SHALL remain 100% stationary without any layout shift.
* **Criterion 5 (Lifecycle State Reset Invariant)**:
  * *Given* an active "Take Me Home" navigation session,
  * *When* the athlete stops return navigation or deselects the active route,
  * *Then* `isTakeMeHomeMode` SHALL reset to `false`,
  * *And* `_navigationState` SHALL immediately transition to inactive default (`ReturnNavigationState(isActive = false)`).
