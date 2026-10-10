# Stage 3: Implementation Plan - ATT-3100: Anchor navigation hints over top of map view when map is present in tracking tab

**Ticket**: [ATT-3100](https://atrainingtracker.atlassian.net/browse/ATT-3100)  
**Sub-task**: [ATT-3125](https://atrainingtracker.atlassian.net/browse/ATT-3125) (`[Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-335`  
**Test Mapping**: `TST-UI-295`  
**Branch**: `feature/ATT-3100`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

In the main tracking tab (`SensorGridScreen.kt`), real-time route navigation alerts (`TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`) are currently hoisted at the very top of the root container (`Alignment.TopCenter`).
When an embedded map view is displayed alongside the sensor grid:
1. The navigation HUD overlays the topmost row(s) of sensor tiles (speed, heart rate, power, cadence), obscuring vital real-time biometric metrics right when navigational guidance is being delivered.
2. The map view beneath the sensor grid remains unobstructed, while navigation alerts logically belong over the map.
3. When the map is hidden (`showMap == false`), anchoring at the top of the screen tab remains the appropriate and expected fallback behavior.

To solve this, the navigation overlay must dynamically adjust its visual anchor:
* When an active map is visible (`showMap == true`), the navigation hints overlay must be anchored over the top edge of the map container, leaving the entire sensor grid completely unobstructed.
* When no map is present (`showMap == false`), the navigation hints overlay must remain anchored at `Alignment.TopCenter` of the tracking tab root container.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-335` (*Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View*)
* **Test Mapping**: `TST-UI-295` (*Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Sensor Grid Layout Shift**: The sensor grid's layout bounds, scroll state, aspect ratios, and cell measurements must experience zero layout shift or vertical jitter when navigation hints appear or disappear.
2. **Mutual Exclusion & Z-Order Priority**: Exactly one navigation hint container is rendered at any moment (Priority: `ForkDecisionCard` > `ReturnNavigationHud` > `TurnPromptBanner`). The navigation hints must never be rendered simultaneously in both the map container and root container.
3. **Map-Absent Backward Compatibility**: When `state.mapState.showMap == false`, navigation hints remain rendered at the top of the root container without functional degradation.
4. **Touch & Interaction Pass-Through**: Areas of the map container outside the navigation hint card must remain fully responsive to map pan, pinch-to-zoom, and tilt gestures.
5. **Human Gate Governance**: Terminal resolution of parent ticket `ATT-3100` remains strictly reserved for the human user in `Final Review (Human)` (Rule 2 / Rule 15).

---

## 4. Proposed Architectural Changes

### Component 1: Encapsulated Navigation Hints Overlay (`SensorGridScreen.kt`)
Create a private composable `NavigationHintsOverlay`:
```kotlin
@Composable
private fun NavigationHintsOverlay(
    state: SensorGridUiState,
    onNavigateBackToRoute: () -> Unit,
    onContinueAlongRoute: () -> Unit,
    onMuteReturnNavigation: () -> Unit,
    onSelectForkBranch: (CandidateRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val forkNav = state.forkNavigationState
        val isOffRoute = state.navigationState.isOffRoute
        val activeTurn = state.activeTurnPrompt

        if (forkNav != null) {
            ForkDecisionCard(
                forkState = forkNav,
                onSelectBranch = onSelectForkBranch,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isOffRoute) {
            ReturnNavigationHud(
                navigationState = state.navigationState,
                onNavigateBackToRoute = onNavigateBackToRoute,
                onContinueAlongRoute = onContinueAlongRoute,
                onMuteReturnNavigation = onMuteReturnNavigation,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (activeTurn != null) {
            TurnPromptBanner(
                prompt = activeTurn,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
```

### Component 2: Dynamic Anchoring in Tracking Tab Layout (`SensorGridScreen.kt`)
In `SensorGridScreen`:
1. Inside the map container branch when `state.mapState.showMap == true`:
```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .weight(mapWeight)
) {
    TrackingMapView(
        mapState = state.mapState,
        sportType = state.sportType,
        workoutState = state.workoutState,
        isSpeedAndLocationConnected = state.isSpeedAndLocationConnected,
        showRiderLocationOnMap = state.showRiderLocationOnMap,
        onCenterMap = onCenterMap,
        modifier = Modifier.fillMaxSize()
    )

    NavigationHintsOverlay(
        state = state,
        onNavigateBackToRoute = onNavigateBackToRoute,
        onContinueAlongRoute = onContinueAlongRoute,
        onMuteReturnNavigation = onMuteReturnNavigation,
        onSelectForkBranch = onSelectForkBranch,
        modifier = Modifier.align(Alignment.TopCenter)
    )
}
```
2. At the root tracking container level, conditionally render `NavigationHintsOverlay` only when `!state.mapState.showMap`:
```kotlin
if (!state.mapState.showMap) {
    NavigationHintsOverlay(
        state = state,
        onNavigateBackToRoute = onNavigateBackToRoute,
        onContinueAlongRoute = onContinueAlongRoute,
        onMuteReturnNavigation = onMuteReturnNavigation,
        onSelectForkBranch = onSelectForkBranch,
        modifier = Modifier.align(Alignment.TopCenter)
    )
}
```
3. Remove redundant standalone invocations of `ForkDecisionCard`, `ReturnNavigationHud`, and `TurnPromptBanner` in the root `Box`.

### UI Consistency (Rule 23 — Mandatory UI Audit)
* **Reference screen / component**: `SensorGridScreen.kt`, `TrackingMapView.kt`, `TurnPromptBanner.kt`, `ReturnNavigationHud.kt`, `ForkDecisionCard.kt`.
* **Reused components**: Existing `TurnPromptBanner`, `ReturnNavigationHud`, `ForkDecisionCard`, `Box`.
* **Theme tokens**: Existing elevation and card borders preserved intact.
* **Shapes**: `RoundedCornerShape` defined in child navigation banners preserved.
* **New one-off styles & justification**: None. Encapsulation purely refactors placement without modifying child visual styling.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Encapsulate Navigation Hints into `NavigationHintsOverlay`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/sensorgrid/SensorGridScreen.kt`
* Implement private composable `NavigationHintsOverlay` managing mutual exclusion and priority.

### Step 2: Anchor `NavigationHintsOverlay` in Map Container and Root Container
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/sensorgrid/SensorGridScreen.kt`
* Add `NavigationHintsOverlay` inside `TrackingMapView`'s enclosing `Box(modifier = Modifier.fillMaxWidth().weight(mapWeight))` aligned to `Alignment.TopCenter`.
* Anchor `NavigationHintsOverlay` at root `Box` level conditioned on `!state.mapState.showMap`.
* Remove old duplicate root overlay invocations.

### Step 3: Implement Unit & Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/sensorgrid/SensorGridNavigationAnchoringContractTest.kt`
* Verify structural contract:
  * When `showMap == true`, navigation overlay resides inside map container.
  * When `showMap == false`, navigation overlay resides in root container.
  * Mutual exclusion between `ForkDecisionCard`, `ReturnNavigationHud`, and `TurnPromptBanner` is maintained.
* Execute targeted tests via `./gradlew testDebugUnitTest --tests "*SensorGrid*"`.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`SensorGridNavigationAnchoringContractTest.kt`), followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-3100`) allows clean revert if needed without polluting sprint branch.
