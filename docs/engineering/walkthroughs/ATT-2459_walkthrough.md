# Stage 5: Walkthrough & Verification - ATT-2459: Remove tabs and Heimweg card from route selector in favor of clean proximity recency list

**Ticket**: [ATT-2459](https://atrainingtracker.atlassian.net/browse/ATT-2459)  
**Sub-task**: [ATT-2571](https://atrainingtracker.atlassian.net/browse/ATT-2571) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-280`  
**Test Mapping**: `TST-UI-240`  
**Branch**: `feature/ATT-2459`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary & Verification Overview

ATT-2459 streamlined the Route Selector bottom sheet ("Route wählen", `RouteSelectorSheet.kt`) by eliminating unnecessary controls and context-mismatched actions:
1. **Removed Sort/Filter Tabs**: Completely removed `TabRow` and the `uiState.showFilterTabs` conditional guard from `RouteSelectorSheet.kt`. In `RouteSelectorViewModel.kt`, route ranking defaults directly and unconditionally to `RouteProximityRanker.rankRoutes(...)` using the athlete's current location, movement bearing, and recency tie-breaking.
2. **Removed Pinned Heimweg Card**: Removed the pinned "Heimweg" / "Take Me Home" card (`Card(onClick = onTakeMeHome, ...)`) and the `onTakeMeHome` parameter from `RouteSelectorModalBottomSheet` and `RouteSelectorContent`. Updated the invocation site in `TrackingTabsScreen.kt`.
3. **Preserved Invariants**: In-ride auto-detection candidate prompt (`AutoDetectedRouteBanner`), active route banner and cancellation (`ActiveRouteBanner`), and clean empty state rendering remain 100% operational.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-280` | `TST-UI-240.1` | Contract Tests (`RouteSelectorSheetTest`) | **PASSED** | `Verified` |
| `REQ-UI-280` | `TST-UI-240.2` | ViewModel Tests (`RouteSelectorViewModelTest`) | **PASSED** | `Verified` |
| `REQ-UI-280` | `TST-UI-240.3` | Localization Parity Audit (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-240.4` | Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100% in 9m 7s) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 9m 7s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Contract & Unit Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"
BUILD SUCCESSFUL in 39s

./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"
BUILD SUCCESSFUL in 36s
```

All contract tests in `RouteSelectorSheetTest` passed:
- `testRouteSelectorSheet_exposesModalBottomSheetAndContent`: PASSED
- `testRouteSelectorModalBottomSheet_consumesDesignTokens`: PASSED
- `testRouteSelectorContent_doesNotRenderFilterTabsOrTabRow`: PASSED
- `testRouteSelectorContent_doesNotRenderTakeMeHomeCard`: PASSED

All unit tests in `RouteSelectorViewModelTest` passed:
- `testInitialStateRanksByRecencyWhenLocationIsNull`: PASSED
- `testLocationChangeTriggersProximityReRanking`: PASSED
- `testLocationUpdateTriggersAutoDetection`: PASSED
- `testDismissAutoDetectedCandidateClearsState`: PASSED
- `testActivateAutoDetectedCandidateSetsRoute`: PASSED
- `testUiStateExposesTotalRouteCountWithoutFilterTabs`: PASSED
- `testInitialStateLoadsRoutesAndObservesActiveRoute`: PASSED
- `testSelectRouteDelegatesToRepository`: PASSED
- `testStopRouteClearsActiveNavigation`: PASSED
- `testClearRouteClearsActiveNavigation`: PASSED

---

## 4. Hardware / Physical Verification & UI Consistency

### UI Consistency (Rule 23)
* **Reference Component**: Material 3 Bottom Sheet in `RouteSelectorSheet.kt`.
* **Structural Streamlining**:
  - Removed top pinned card for Heimweg (`Card(onClick = onTakeMeHome)`).
  - Removed middle `TabRow` filter chips bar.
  - The sheet now presents a clean, unobstructed list:
    1. Title: "Route wählen"
    2. Active Route Banner (if navigating)
    3. Auto-Detected Route Banner (if candidate detected)
    4. Proximity-Recency Route Cards (or clean empty state)
* **Design Token Conformance**:
  - Sheet container: `BottomSheetDesign.SheetShape`
  - Container color: `MaterialTheme.colorScheme.surface`
  - Card shape: `RoundedCornerShape(12.dp)`
  - Spacing & Padding: Standard 16.dp horizontal padding, 8.dp vertical separation.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room unit test suite executed with 100% pass rate in 9m 7s.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-280`) and `docs/tests.md` (`TST-UI-240`) updated to `Verified`.
3. **Subtask Direct Completion**: Stage 5 subtask `ATT-2571` audited via Gate 5 and transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review Handover**: Parent ticket `ATT-2459` transitioned to `Final Review (Human)` and assigned to `human` for agile sprint review.
5. **Continuous Sprint Branch Integration (Strategy A)**: Branch `feature/ATT-2459` merged cleanly into `sprint/2026-41.1` via `--no-ff`.
