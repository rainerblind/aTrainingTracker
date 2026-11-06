# Stage 5 Walkthrough: ATT-5564 — Workout Peek Bottom Sheet Drag Handle

**Ticket**: [ATT-5564](https://rainerblind.atlassian.net/browse/ATT-5564)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5564`  
**Requirement Mapping**: `REQ-UI-221` (referencing `REQ-UI-196`) | **Test Mapping**: `TST-UI-175` (`TST-UI-175.7`, `TST-UI-175.8`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary
In `WorkoutClusterHeatmapScreen.kt`, selecting a workout member from the cluster polyline or the effort progression chart opens a persistent peek bottom sheet hosting `TrackOnMapScreen`. Previously, `BottomSheetScaffold` explicitly specified `sheetDragHandle = null`, omitting a visual drag affordance so athletes could not easily discover that the sheet can be pulled up to inspect full workout telemetry, splits, and elevation charts (Sprint Review 2026-45.7 / ATT-5485).

In this ticket:
1. **Visual Drag Handle Affordance**: Wired `BottomSheetScaffold.sheetDragHandle` to render `MinimumDragHandle()` from `com.atrainingtracker.trainingtracker.ui.components.core` when a workout is peeked (`peekedWorkoutDataWithTrack != null && !isEditingFingerprint`).
2. **Compact Vertical Footprint (`REQ-UI-196`)**: Reused `MinimumDragHandle()` (32dp x 3dp, 15dp total vertical footprint) instead of the bulky Material 3 default `BottomSheetDefaults.DragHandle()` (48dp vertical footprint), preserving header layout space within `PeekHeightWorkout` (140dp).
3. **Suppression in Idle and Fingerprint-Editing Modes**: Implemented `shouldShowWorkoutPeekDragHandle(hasPeekedWorkout, isEditingFingerprint)`, cleanly suppressing the drag handle when no workout is peeked or while editing cluster boundary coordinates.
4. **Behavioral Contract Testing (Section 5.4 Compliance)**: Added `testWorkoutClusterHeatmapScreen_dragHandleBehavior()` in `BottomSheetVisualContractTest.kt` verifying state transitions behaviorally and validating declaration via programmatic reflection, with zero `File.readText()` source-grep tests.

---

## 2. Key Architectural & Code Changes
| File / Component | Summary of Changes |
| :--- | :--- |
| `WorkoutClusterHeatmapScreen.kt` | Added import for `MinimumDragHandle`; added helper `shouldShowWorkoutPeekDragHandle()`; conditionally bound `sheetDragHandle` on `BottomSheetScaffold`. |
| `BottomSheetVisualContractTest.kt` | Added `testWorkoutClusterHeatmapScreen_dragHandleBehavior()` validating drag handle visibility and suppression state logic + reflection check. |
| `docs/requirements/req_ui_design_system.md` | Updated `REQ-UI-221` with drag handle specification and Given-When-Then acceptance criteria. |
| `docs/tests/tst_ui_design_system.md` | Updated `TST-UI-175` contract test specification for `WorkoutClusterHeatmapScreen.kt` drag affordance. |

---

## 3. Verification & Test Execution
* **Tier 1 Targeted Test Scope**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.components.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
  -> `BUILD SUCCESSFUL in 2s` (100% pass rate).
* **Requirement Governance Audit**: `python3 tools/verify_requirement_governance.py` -> `EXIT CODE 0` (100% master-to-shard parity).

---

## 4. Requirement & Test Traceability Matrix
| Requirement ID | Test Case ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-221` | `TST-UI-175.7` | `Verified` | Behavioral contract test & reflection assertion in `BottomSheetVisualContractTest` |
| `REQ-UI-196` | `TST-UI-175.7` | `Verified` | `MinimumDragHandle` design token and proportion tests in `MinimumDragHandleTest` |
| `REQ-UI-221` | `TST-UI-175.8` | `Verified` | Tier 1 targeted unit test suite and requirement governance pass |
