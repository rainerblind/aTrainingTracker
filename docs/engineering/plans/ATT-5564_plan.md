# Stage 3: Implementation Plan - ATT-5564: Workout Peek Bottom Sheet Drag Handle

**Ticket**: [ATT-5564](https://rainerblind.atlassian.net/browse/ATT-5564)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5564`  
**Requirement Mapping**: `REQ-UI-221` (referencing `REQ-UI-196`)  
**Test Mapping**: `TST-UI-175` (`TST-UI-175.7`, `TST-UI-175.8`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `WorkoutClusterHeatmapScreen.kt`, tapping a workout on the cluster map or progression chart opens a peek bottom sheet hosting `TrackOnMapScreen`. Because `BottomSheetScaffold` explicitly specifies `sheetDragHandle = null`, the sheet lacks a visual drag handle affordance, preventing athletes from realizing the sheet can be pulled up to inspect full workout telemetry, splits, and elevation (Sprint Review 2026-45.7 / ATT-5485). This plan specifies binding `sheetDragHandle` to `MinimumDragHandle()` when a workout is peeked, while suppressing it in idle and fingerprint-editing modes.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-221` (Workout peek bottom sheet drag handle affordance), `REQ-UI-196` (Subtle `MinimumDragHandle` design token alignment).
* **Test Mapping**: `TST-UI-175.7` (`BottomSheetVisualContractTest.kt`), `TST-UI-175.8` (Governance & full test suite).

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit (`check-gate`).
2. **Design System & Spacing Tokens (`REQ-UI-196`)**: Must use `MinimumDragHandle()` (32dp x 3dp, 15dp vertical footprint) from `com.atrainingtracker.trainingtracker.ui.components.core`, preserving header layout room inside `PeekHeightWorkout` (140dp).
3. **Idle & Edit Mode Suppression**: When `peekedWorkoutDataWithTrack == null` or `isEditingFingerprint == true`, `sheetDragHandle` must evaluate to `null` to avoid phantom drag captures or visual leaks.
4. **100% Zero String Churn**: Pure UI component wiring; zero new string resources, 100% localization parity preserved.

---

## 4. Proposed Architectural Changes
### Component 1: `WorkoutClusterHeatmapScreen.kt` (`ui/clusters/`)
* Import `com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandle`.
* Update `BottomSheetScaffold`:
  ```kotlin
  sheetDragHandle = if (peekedWorkoutDataWithTrack != null && !isEditingFingerprint) {
      { MinimumDragHandle() }
  } else null,
  ```

### Component 2: `BottomSheetVisualContractTest.kt` (`ui/components/core/`)
* Extend `testPersistentMapScaffolds_consumeBottomSheetDesignTokens()` to assert that `WorkoutClusterHeatmapScreen.kt` binds `MinimumDragHandle()` on `BottomSheetScaffold` when peeked and does not set `sheetDragHandle = null`.

### UI Consistency (Design Guidelines Sec. 5)
* **Reference**: `FilterBottomSheetScaffold.kt`, `AppModalBottomSheet.kt`, `MapDetailLayout.kt`.
* **Reused Component**: `MinimumDragHandle()` from `ui/components/core/`.
* **Tokens**: `BottomSheetDesign.DragHandleWidth` (32dp), `BottomSheetDesign.DragHandleHeight` (3dp), `outlineVariant(alpha = 0.6f)`, top 8dp / bottom 4dp padding. Zero one-off styling.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Update `WorkoutClusterHeatmapScreen.kt` (`REQ-UI-221`)
* Add import and bind conditional `sheetDragHandle` with `MinimumDragHandle()`.

### Step 2: Extend `BottomSheetVisualContractTest.kt` (`TST-UI-175.7`)
* Add contract assertion verifying `sheetDragHandle` integration in `WorkoutClusterHeatmapScreen.kt`.

### Step 3: Targeted Verification & Governance Gate (`TST-UI-175.8`)
* Run targeted contract tests and requirement governance:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetVisualContractTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass on `BottomSheetVisualContractTest`; clean-room regression pass in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-5564` allows full revert via `git checkout feature/ATT-5564 -- .`.
