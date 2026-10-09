# Stage 5 Walkthrough: ATT-2938 - Prevent unsolicited ReturnNavigationHud activation during regular route navigation and gate by tab navigation hints

**Ticket**: [ATT-2938](https://atrainingtracker.atlassian.net/browse/ATT-2938)  
**Sub-task**: [ATT-2973](https://atrainingtracker.atlassian.net/browse/ATT-2973) (`[Test]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Requirement Mapping**: `REQ-MAP-039` (*Strict "Take Me Home" Return Navigation Activation Decoupling, Tab Navigation Hints Gating, and Non-Displacing Cockpit Overlay Integration*)  
**Test Spec ID**: `TST-MAP-041`  
**Branch**: `feature/ATT-2938`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Outcome

During route navigation testing, athletes noticed that selecting and navigating an ordinary round-trip route or route terminating near their home address caused `ReturnNavigationHud` to unexpectedly trigger and display "Zu Hause" as the destination, along with a Home icon. Furthermore, because `ReturnNavigationHud` was rendered as an in-flow component within the primary scrollable `Column` in `SensorGridScreen`, its activation vertically displaced all cockpit telemetry tiles downward. Finally, on tracking tabs where navigation hints were disabled (`showNavigationHints = false`), `ReturnNavigationHud` was still displayed.

Ticket `ATT-2938` established formal requirement `REQ-MAP-039` (amending `REQ-MAP-029` Clause 4 and interfacing with `REQ-UI-282` Clause 2), decoupled Return Navigation from ordinary route navigation in `ReturnNavigationRepository`, established a non-displacing top-center spatial overlay container in `SensorGridScreen`, gated `ReturnNavigationHud` strictly behind `state.showNavigationHints`, and enforced complete lifecycle state reset invariants.

### Key Enhancements
1. **Strict "Take Me Home" Activation Mode Decoupling (`ReturnNavigationRepository.kt`)**:
   * Evaluates `val isActive = isTakeMeHomeMode`. Regular route following (`currentActiveRoute != null`) without explicit "Take Me Home" activation immediately yields `isActive = false`, bypassing corridor snapping and emitting an inactive state.
   * `cachedHomeDestination` is only passed to `snapToCorridor` when `isTakeMeHomeMode == true`. Regular routes retain their own route identity and destination summary.
2. **Lifecycle State Resets (`ReturnNavigationRepository.kt`)**:
   * Explicitly stopping return navigation (`stopTakeMeHome()`) resets `isTakeMeHomeMode = false`, `isReverseReturn = false`, `isDismissed = false`, and emits `ReturnNavigationState(isActive = false)`.
   * Deselecting or clearing an active route resets `isReverseReturn = false`.
3. **Non-Displacing Top-Center Spatial Overlay & Tab Gating (`SensorGridScreen.kt`)**:
   * Removed `ReturnNavigationHud` from the base scrollable `Column`.
   * Positioned `ReturnNavigationHud` alongside `ForkDecisionCard` within a dedicated top-center overlay `Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())`.
   * Conditionally gated the entire overlay container strictly behind `if (state.showNavigationHints)`. On navigation-free tabs, HUD components are completely excluded from the Compose composition tree (0% touch interception).
   * Maintained `tuningConfig.navigationCueTransparency` binding per Design Guidelines §5.7.
4. **Preservation of System Invariants**:
   * Explicit "Take Me Home" activation (`startTakeMeHome()`), reverse route return, elevation-aware dynamic ETA, and 100% test pass rate across the full test suite are strictly preserved.

---

## 2. Requirement & Test Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Result | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-MAP-039` (Clause 1 & 2) | `TST-MAP-041.1` | Unit | `ReturnNavigationRepositoryTest.testRecalculateNavigationMetrics_doesNotActivateWhenRegularRouteActiveWithoutTakeMeHome` & `testStartTakeMeHome_activatesReturnNavigationAndSnapsHome` | **PASSED** | `Verified` |
| `REQ-MAP-039` (Clause 3) | `TST-MAP-041.2` | Unit | `ReturnNavigationRepositoryTest.testStopTakeMeHome_resetsModeAndEmitsInactiveState` & `testRouteCleared_resetsReverseReturn` | **PASSED** | `Verified` |
| `REQ-MAP-039` (Clause 4) | `TST-MAP-041.3` | Integration | `SensorGridScreenRouteIntegrationTest.testSensorGridScreen_returnNavigationHud_floatsAsTopCenterOverlayAndGatedByNavigationHints` | **PASSED** | `Verified` |
| `REQ-MAP-039` (Clause 4) | `TST-MAP-041.4` | Contract | `ReturnNavigationHudContractTest.testReturnNavigationHud_structuralVerification` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-MAP-041.5` | Regression | Full clean-room test suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 3. Test Execution Results

### Targeted Unit Tests
```text
ReturnNavigationRepositoryTest > testRecalculateNavigationMetrics_doesNotActivateWhenRegularRouteActiveWithoutTakeMeHome PASSED
ReturnNavigationRepositoryTest > testStartTakeMeHome_activatesReturnNavigationAndSnapsHome PASSED
ReturnNavigationRepositoryTest > testStopTakeMeHome_resetsModeAndEmitsInactiveState PASSED
ReturnNavigationRepositoryTest > testRouteCleared_resetsReverseReturn PASSED
SensorGridScreenRouteIntegrationTest > testSensorGridScreen_returnNavigationHud_floatsAsTopCenterOverlayAndGatedByNavigationHints PASSED
SensorGridScreenRouteIntegrationTest > testSensorGridScreen_forkDecisionCard_floatsAsTopCenterOverlay PASSED
SensorGridScreenRouteIntegrationTest > testSensorGridScreen_containsRouteSelectionButton PASSED
SensorGridScreenRouteIntegrationTest > testSensorGridScreen_routeSelectionButton_hasClearAction PASSED
ReturnNavigationHudContractTest > testReturnNavigationHud_structuralVerification PASSED
ReturnNavigationHudContractTest > testReturnNavigationHud_dismissCallback PASSED
ReturnNavigationHudContractTest > testReturnNavigationHud_elevationTransparency PASSED

BUILD SUCCESSFUL in 15s
11 tests executed, 0 failures, 0 errors, 0 skipped
```

### Full Clean-Room Unit Test Suite
* Command: `./gradlew testDebugUnitTest`
* Result: **BUILD SUCCESSFUL in 2m 35s** (100% pass rate, 0 failures, 0 regressions across all test suites).

---

## 4. Modified Files

* [ReturnNavigationRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/ReturnNavigationRepository.kt): Evaluates `isActive = isTakeMeHomeMode`, bypasses corridor snapping when false, supplies `cachedHomeDestination` only when `isTakeMeHomeMode == true`, and implements lifecycle state resets in `stopTakeMeHome()` and on route clearing.
* [SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt): Moved `ReturnNavigationHud` out of scrollable in-flow `Column` and placed it into top-center overlay `Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())` gated by `if (state.showNavigationHints)`.
* [ReturnNavigationRepositoryTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ReturnNavigationRepositoryTest.kt): Added unit test suite covering activation mode decoupling, home destination resolution, and lifecycle resets.
* [SensorGridScreenRouteIntegrationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt): Added integration tests verifying overlay anchoring, exclusion from in-flow sensor column, and `state.showNavigationHints` gating.
* [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): Updated `REQ-MAP-039` status to `Verified`.
* [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): Updated `TST-MAP-041` status to `Verified`.
