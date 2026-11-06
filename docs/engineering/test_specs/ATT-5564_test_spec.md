# Stage 2: Requirement & Test Specification - ATT-5564: Workout Peek Bottom Sheet Drag Handle

**Ticket**: [ATT-5564](https://rainerblind.atlassian.net/browse/ATT-5564)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5564`  
**Requirement Mapping**: `REQ-UI-221` (referencing `REQ-UI-196`) | **Test Spec ID**: `TST-UI-175`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-221`)

### 1.1 Problem Statement & Rationale
In `WorkoutClusterHeatmapScreen.kt`, tapping a workout on the cluster map or progression chart opens a peek bottom sheet hosting `TrackOnMapScreen`. Because `BottomSheetScaffold` explicitly configures `sheetDragHandle = null`, the sheet lacks a visual drag handle affordance, preventing athletes from realizing the peek sheet can be pulled up to inspect full workout telemetry and lap analytics (Sprint Review 2026-45.7 / ATT-5485).

### 1.2 Functional & Architectural Requirements
1. **Visual Drag Affordance (`REQ-UI-221`)**: In `WorkoutClusterHeatmapScreen.kt`, `BottomSheetScaffold` SHALL display a visual drag handle via `sheetDragHandle = if (peekedWorkoutDataWithTrack != null && !isEditingFingerprint) { { MinimumDragHandle() } } else null`.
2. **Compact Vertical Footprint (`REQ-UI-196`)**: The drag handle SHALL use `MinimumDragHandle()` (32dp x 3dp, 15dp total vertical footprint) from `com.atrainingtracker.trainingtracker.ui.components.core` rather than the bulky Material 3 default `BottomSheetDefaults.DragHandle()` (48dp vertical footprint), preserving header layout space within `PeekHeightWorkout` (140dp).
3. **Suppression in Idle/Edit Modes**: When `peekedWorkoutDataWithTrack == null` or `isEditingFingerprint == true`, `sheetDragHandle` SHALL evaluate to `null` to prevent phantom drag touch captures or visual leaks while editing cluster fingerprints.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Workout Peek Affordance)**: *Given* an athlete selecting a workout member on `WorkoutClusterHeatmapScreen`, *When* the bottom sheet peeks or expands, *Then* a visual `MinimumDragHandle()` pill SHALL be displayed at the top center of the sheet.
* **AC-2 (Idle/Edit Suppression)**: *Given* an athlete viewing the cluster map without a peeked workout or entering fingerprint editing mode (`isEditingFingerprint == true`), *When* the bottom sheet is collapsed/hidden, *Then* `sheetDragHandle` SHALL be `null`.
* **AC-3 (9-Language Parity & Invariance)**: *Given* the 9-language application locales, *When* displaying the drag handle, *Then* zero new untranslated string resources are required and existing string parity is 100% preserved.

### 1.4 System Invariants
`BottomSheetDesign.PeekHeightWorkout` baseline (140dp) remains intact; surface container background colors preserved; zero database, ViewModel, or clustering engine mutations; 100% unit test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-UI-221` (*Standardized Bottom Sheet Peek Height Baselines*) targeting `WorkoutClusterHeatmapScreen.kt` and `BottomSheetVisualContractTest.kt`, referencing `REQ-UI-196` (*Subtle Drag Handle Affordance & Unified Sheet Styling*).
* `Historical Origin & Commit Trace`: Sprint `2026-40.8` (Commit `9c3dd09e`, ATT-1645) and Sprint Review `2026-45.7` (ATT-5485 / ATT-5564).
* `Root Reason for Existing Formulation`: `sheetDragHandle = null` was originally set to keep the top of `TrackOnMapScreen` flush, overlooking that athletes need a visual drag affordance to discover expandable workout details.
* `Preservation of Core Invariants`: Status bar boundary constraints (`REQ-SET-069`), navigation bar padding additions, fallback tokens, `MinimumDragHandle` design dimensions, and fingerprint editing state transitions remain 100% strictly preserved.

---

## 2. Test Specification (`TST-UI-175`)

### Test Case 1: Drag Handle Affordance & Scaffold Configuration (`TST-UI-175.7`)
* **Scope**: Contract tests in `BottomSheetVisualContractTest.kt`.
* **Procedure**: Verify `WorkoutClusterHeatmapScreen.kt` binds `sheetDragHandle` with `MinimumDragHandle()` when `peekedWorkoutDataWithTrack != null && !isEditingFingerprint`, and does not set `sheetDragHandle = null` unconditionally.

### Test Case 2: Clean-Room Governance & Regression (`TST-UI-175.8`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetVisualContractTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandleTest"`
* **Expected Result**: Exit code 0, 100% test pass rate.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-221` | Workout peek bottom sheet drag handle affordance | `TST-UI-175.7` | Contract Test (`BottomSheetVisualContractTest`) |
| `REQ-UI-196` | Subtle `MinimumDragHandle` design token alignment | `TST-UI-175.7` | Unit Test (`MinimumDragHandleTest`) |
| `REQ-UI-221` | Requirement Governance & Architecture Invariance | `TST-UI-175.8` | Governance & Unit Test Suite |
