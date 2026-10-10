# Stage 2: Requirement & Test Specification - ATT-2938: Prevent unsolicited ReturnNavigationHud activation during regular route navigation and gate by tab navigation hints

**Ticket**: [ATT-2938](https://atrainingtracker.atlassian.net/browse/ATT-2938)  
**Sub-task**: [ATT-2968](https://atrainingtracker.atlassian.net/browse/ATT-2968) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-MAP-039` (*Strict "Take Me Home" Return Navigation Activation Decoupling, Tab Navigation Hints Gating, and Non-Displacing Cockpit Overlay Integration*)  
**Test Spec ID**: `TST-MAP-041`  
**Branch**: `feature/ATT-2938`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-MAP-039)

### 1.1 Problem Statement & Rationale
During workout tracking on physical devices (Google Pixel 10), athletes following a selected route frequently observe an unsolicited "Zu Hause" Return Navigation HUD card (`ReturnNavigationHud.kt`) anchored at the top of their cockpit screen (`🏠 Zu Hause • 8.2 km • +137 m • 17:32 (~41 min)`), even though they never activated "Take Me Home" ("Heimweg") mode.

Furthermore, because `ReturnNavigationHud` is rendered as an in-flow composable inside the primary layout `Column` in `SensorGridScreen.kt`, its appearance vertically displaces all underlying sensor grid tiles downwards. Finally, the card appears across all tracking tabs, ignoring whether navigation hints are enabled or disabled on that tab.

### 1.2 Functional & Architectural Requirements
The system SHALL strictly decouple "Take Me Home" return navigation from ordinary route navigation in `ReturnNavigationRepository.kt`, conditionally gate `ReturnNavigationHud` behind `state.showNavigationHints` in `SensorGridScreen.kt`, host the HUD as a top-level non-displacing overlay anchored at `Alignment.TopCenter`, and enforce clean lifecycle state reset invariants (ATT-2938, amending `REQ-MAP-029` and `REQ-UI-282`):

1. **Strict "Take Me Home" Activation Mode (`ReturnNavigationRepository.kt`)**:
   * `ReturnNavigationRepository.recalculateNavigationMetrics` SHALL evaluate `isActive = isTakeMeHomeMode`.
   * Following or selecting a regular navigation route (`currentActiveRoute != null`) without explicitly activating "Take Me Home" SHALL NOT activate return navigation metrics (`isActive = false`, `hasRemainingMetrics = false`).
   * When `isTakeMeHomeMode == false`, `recalculateNavigationMetrics` SHALL immediately emit `ReturnNavigationState(isActive = false)` and bypass corridor snapping.
2. **Non-Hijacking Destination Resolution (`ReturnCorridorSnapper.kt` / `ReturnNavigationRepository.kt`)**:
   * `cachedHomeDestination` SHALL only be supplied to corridor snapping when `isTakeMeHomeMode == true`.
   * When following a regular route without "Take Me Home", the route summary title SHALL NOT be overridden with the Home destination name ("Zu Hause").
3. **Lifecycle State Reset Invariants**:
   * When return navigation is stopped (`stopTakeMeHome()`), when the active route is deselected/cleared (`activeNavigatedRouteId == null`), or when a workout session terminates, `isTakeMeHomeMode` and `isReverseReturn` SHALL reset to `false`, and `_navigationState` SHALL transition to `ReturnNavigationState(isActive = false)`.
4. **Per-Tab Navigation Hints Gating & Non-Displacing Top-Level Overlay (`SensorGridScreen.kt`)**:
   * `ReturnNavigationHud` SHALL be moved out of the in-flow base cockpit `Column`.
   * `ReturnNavigationHud` SHALL be hosted within a top-level non-displacing overlay anchored at `Alignment.TopCenter`, strictly gated by `if (state.showNavigationHints)`.
   * On tracking tabs where `state.showNavigationHints == false`, `ReturnNavigationHud` SHALL be completely excluded from the Compose composition tree, ensuring 0% touch interception and complete suppression of return banners.
   * The appearance, animation, or dismissal of `ReturnNavigationHud` SHALL NOT alter the vertical position or scroll coordinates of the underlying sensor grid tiles.
5. **Preservation of System Invariants**:
   * Mid-ride "Take Me Home" entry in `RouteSelectorSheet.kt` (`REQ-UI-282`), reverse route return, elevation-aware dynamic ETA calculations, and 100% test pass rate across the full test suite MUST be strictly preserved.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-MAP-029` Clause 4 (*Cockpit HUD Integration*) and interfaces with `REQ-UI-282` Clause 2 (*Harmonized ReturnNavigationHud*).
2. *Historical Origin & Commit Trace*:
   - `REQ-MAP-029` was introduced in Sprint 2026-40.16 (`ATT-1953`, commit `c8ac2c5e` / `0994f794`).
   - `REQ-UI-282` was introduced in Sprint 2026-41.1 (`ATT-2462`, commit `6d0a5d24`).
3. *Root Reason for Existing Formulation*: In ATT-1953, Clause 4 originally stated: *"when Return Navigation is active or an active route has remaining progress, render a high-visibility HUD banner"*. The intent was to show remaining distance/climb for any loaded route. However, because round-trip routes end near home, this caused `snapActiveRoute` to label the destination as "Zu Hause" and display the Home icon, misleading athletes into thinking "Take Me Home" was unexpectedly active. Furthermore, in-flow layout placement in `SensorGridScreen` pushed cockpit tiles down, creating unwanted visual displacement.
4. *Preservation of Core Invariants*: Explicit "Take Me Home" activation (`startTakeMeHome()`), reverse route return (`setReverseReturn()`), elevation-aware dynamic ETA calculations, and 100% test pass rate across the full test suite MUST be strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
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

---

## 2. Test Specification (TST-MAP-041)

### 2.1 Scope & Verification Strategy
Verification will validate both backend reactive state flow behavior in `ReturnNavigationRepository` and frontend composable layout assertions in `SensorGridScreen`.

### 2.2 Test Cases & Traceability Matrix

| Test Case ID | Test Target | Verification Description | Traceability |
| :--- | :--- | :--- | :--- |
| `TST-MAP-041.1` | `ReturnNavigationRepositoryTest.kt` | Verify selecting a route terminating near home when `isTakeMeHomeMode == false` results in `isActive == false` and `hasRemainingMetrics == false`. | `REQ-MAP-039.1` |
| `TST-MAP-041.2` | `ReturnNavigationRepositoryTest.kt` | Verify calling `startTakeMeHome()` activates return metrics and resolves destination name to home with dynamic ETA. | `REQ-MAP-039.1`, `REQ-MAP-039.2` |
| `TST-MAP-041.3` | `ReturnNavigationRepositoryTest.kt` | Verify lifecycle reset: calling `stopTakeMeHome()` or clearing the active route resets `isTakeMeHomeMode` and emits inactive state. | `REQ-MAP-039.3` |
| `TST-MAP-041.4` | `SensorGridScreenRouteIntegrationTest.kt` | Verify `ReturnNavigationHud` is NOT located in the scrollable in-flow sensor grid `Column`. | `REQ-MAP-039.4` |
| `TST-MAP-041.5` | `SensorGridScreenRouteIntegrationTest.kt` | Verify `ReturnNavigationHud` is placed in top-level overlay at `Alignment.TopCenter` and gated by `state.showNavigationHints`. | `REQ-MAP-039.4` |
| `TST-MAP-041.6` | Full Test Suite | Clean-room execution of `./gradlew testDebugUnitTest` asserting 100% pass rate. | `REQ-PRO-001` |

---

## 3. Detailed Test Design

### 3.1 `ReturnNavigationRepositoryTest.kt`
```kotlin
@Test
fun testRegularRouteNavigation_doesNotActivateReturnNavigationState() = runTest {
    val route = createRouteNearHome(1L, "Home Loop")
    val repo = createReturnNavigationRepository()

    fakeActiveRouteIdFlow.value = 1L
    fakeRoutesFlow.value = listOf(route)
    fakeLocationFlow.value = LatLng(48.05, 9.0)

    advanceUntilIdle()

    val state = repo.navigationState.value
    assertFalse("ReturnNavigationState must be inactive during regular route navigation", state.isActive)
    assertFalse("hasRemainingMetrics must be false", state.hasRemainingMetrics)
}

@Test
fun testTakeMeHomeActivation_activatesReturnMetricsWithHomeDestination() = runTest {
    val route = createRouteNearHome(1L, "Home Loop")
    val repo = createReturnNavigationRepository()

    fakeActiveRouteIdFlow.value = 1L
    fakeRoutesFlow.value = listOf(route)
    fakeLocationFlow.value = LatLng(48.05, 9.0)

    repo.startTakeMeHome()
    advanceUntilIdle()

    val state = repo.navigationState.value
    assertTrue("ReturnNavigationState must be active after startTakeMeHome()", state.isActive)
    assertTrue("hasRemainingMetrics must be true", state.hasRemainingMetrics)
    assertEquals("Zu Hause", state.destinationName)
}

@Test
fun testStopTakeMeHome_resetsStateToInactive() = runTest {
    val repo = createReturnNavigationRepository()
    repo.startTakeMeHome()
    advanceUntilIdle()

    repo.stopTakeMeHome()
    advanceUntilIdle()

    val state = repo.navigationState.value
    assertFalse("State must be inactive after stopTakeMeHome()", state.isActive)
    assertFalse("hasRemainingMetrics must be false", state.hasRemainingMetrics)
}
```

### 3.2 `SensorGridScreenRouteIntegrationTest.kt`
```kotlin
@Test
fun testSensorGridScreen_returnNavigationHud_floatsAsTopCenterOverlayAndGatedByNavigationHints() {
    val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
    val content = file.readText()

    assertFalse(
        "ReturnNavigationHud must not be rendered inside the in-flow sensor grid Column",
        content.substringAfter("Column(modifier = Modifier.fillMaxSize())")
               .substringBefore("Column(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .verticalScroll")
               .contains("ReturnNavigationHud(")
    )
    assertTrue(
        "ReturnNavigationHud must be gated behind state.showNavigationHints",
        content.contains("if (state.showNavigationHints)") && content.contains("ReturnNavigationHud(")
    )
}
```
