# Stage 1 Analysis: ATT-2943 - Remove duplicate upper active route banner from Route Selector dialog

**Ticket**: [ATT-2943](https://atrainingtracker.atlassian.net/browse/ATT-2943)  
**Sub-task**: [ATT-2994](https://atrainingtracker.atlassian.net/browse/ATT-2994) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2943`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During on-device desk and physical device testing of the Route Selector (`RouteSelectorSheet.kt`, `ATT-2459`), an ergonomic and visual redundancy was observed when an active navigated route is set:
1. **Duplicate Representation**: The active route is rendered simultaneously in two locations within the bottom sheet:
   - First, as a pinned `ActiveRouteBanner` composable at the very top of `RouteSelectorContent` (lines 144–151), directly beneath the sheet header.
   - Second, as a full `RouteCard` inside the scrollable `LazyColumn` list (lines 188–196), which already highlights the route with `secondaryContainer` background styling and a prominent `"ACTIVE"` badge chip.
2. **Visual Clutter & Screen Space Consumption**: On smaller smartphone screens or in landscape orientation, the pinned upper banner occupies ~70–80 dp of vertical real estate, pushing the scrollable candidate route list downward and creating visual dissonance where the user sees the identical route twice on screen.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Component Structure in `RouteSelectorSheet.kt`
In `RouteSelectorSheet.kt:143-154`:
```kotlin
// Active Route Banner
AnimatedVisibility(visible = uiState.activeRoute != null) {
    uiState.activeRoute?.let { activeRoute ->
        ActiveRouteBanner(
            route = activeRoute,
            onStopRoute = { viewModel.stopRoute() }
        )
    }
}

Spacer(modifier = Modifier.height(8.dp))
```
Directly below this pinned banner, the route list is rendered:
```kotlin
items(uiState.routes, key = { it.summary.id }) { route ->
    RouteCard(
        route = route,
        isActive = route.summary.id == uiState.activeRoute?.summary?.id,
        onClick = {
            viewModel.selectRoute(route.summary.id)
            onRouteSelected(route.summary.id)
        }
    )
}
```
Because `uiState.routes` contains all qualifying routes in proximity (or matching the corridor in-ride), the active route is present in `uiState.routes` AND is rendered as `uiState.activeRoute`.

### 2.2 Functional Purpose of `ActiveRouteBanner`
`ActiveRouteBanner` provided a "Clear / Abwählen" button invoking `viewModel.stopRoute()`.
However, `RouteCard` only implemented an `onClick` callback that re-selected the route. Without a stop/clear mechanism directly on `RouteCard`, `ActiveRouteBanner` was originally added as a separate container to house the stop action.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: Amends `REQ-UI-280` (*Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking*, Clause 3, `ATT-2459`).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1835` (Sprint 2026-40.14) and Ticket `ATT-2459` (Sprint 2026-41.1).
3. **Root Reason for Existing Formulation**: In `ATT-1835` and `ATT-2459`, `ActiveRouteBanner` was retained at the top to ensure athletes had an unambiguous 1-tap route cancellation button. However, once `RouteCard` was upgraded to prominently display the `"ACTIVE"` badge chip and `secondaryContainer` styling (`REQ-UI-310`), the top banner became redundant duplicate information.
4. **Preservation of Core Invariants**:
   - The athlete must continue to have a direct 1-tap action to stop/clear the active navigated route (`viewModel.stopRoute()`).
   - The active route must continue to be clearly visually distinguished in the route list with the `"ACTIVE"` badge chip and active container coloring.
   - Selecting any other candidate route immediately replaces the active route.
   - Mid-Ride Heimweg card (`MidRideHeimwegCard`), sport type icon fidelity (`Color.Unspecified`), and empty state handling remain untouched.
   - 100% full-suite unit test pass rate must be preserved.

---

## 4. Proposed Technical Solution & Architecture

### 4.1 Excise Upper `ActiveRouteBanner` from `RouteSelectorContent`
* Remove the `AnimatedVisibility(visible = uiState.activeRoute != null)` block and associated `Spacer` from `RouteSelectorContent` (lines 143–154 in `RouteSelectorSheet.kt`).
* Retain the `ActiveRouteBanner` composable definition in `RouteSelectorSheet.kt` to prevent breaking existing public API signatures or architectural contracts.

### 4.2 Integrate Stop/Clear Action into `RouteCard`
* Update `RouteCard` composable signature:
  ```kotlin
  @Composable
  fun RouteCard(
      route: RouteWithPath,
      isActive: Boolean,
      onClick: () -> Unit,
      onClearRoute: (() -> Unit)? = null
  )
  ```
* When `isActive == true`, render the `"ACTIVE"` badge chip alongside an `IconButton` with `Icons.Default.Close` (`contentDescription = stringResource(id = R.string.route_action_clear)`), allowing immediate 1-tap route cancellation.
* In `RouteSelectorContent`:
  ```kotlin
  items(uiState.routes, key = { it.summary.id }) { route ->
      val isActive = route.summary.id == uiState.activeRoute?.summary?.id
      RouteCard(
          route = route,
          isActive = isActive,
          onClick = {
              if (!isActive) {
                  viewModel.selectRoute(route.summary.id)
              }
              onRouteSelected(route.summary.id)
          },
          onClearRoute = if (isActive) { { viewModel.stopRoute() } } else null
      )
  }
  ```
* This achieves complete functional parity with zero duplicate card rendering.

---

## 5. Scope & Guardrails (`ATT-1250`)

### In-Scope
* Removing the duplicate upper `ActiveRouteBanner` from `RouteSelectorContent` in `RouteSelectorSheet.kt`.
* Adding `onClearRoute: (() -> Unit)? = null` to `RouteCard` to provide 1-tap route stopping directly on the active item in the list.
* Adding contract tests in `RouteSelectorSheetTest.kt` verifying that `RouteSelectorContent` does not compose `ActiveRouteBanner`.
* Maintaining 100% full-suite test pass rate.

### Out-of-Scope
* Modifying `RouteSelectorViewModel` ranking or proximity filtering logic.
* Modifying `RouteSelectionButton` on `ControlTrackingScreen.kt`.
* Modifying localization strings (existing `route_action_clear` is reused).

---

## 6. Verification & Test Strategy

1. **`RouteSelectorSheetTest.kt`**:
   - Add test `testRouteSelectorContent_doesNotRenderUpperActiveRouteBanner`: Verifies that `RouteSelectorContent` does NOT call or compose `ActiveRouteBanner`.
   - Update `testRouteCard_preservesActiveRouteChipAndCardColors` to verify `onClearRoute` button presence and interaction.
2. **`RouteSelectionButtonTest.kt`**:
   - Verify control tracking route button remains functional.
3. **Clean-Room Full Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.
