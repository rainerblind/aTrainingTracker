# Stage 2: Requirement & Test Specification - ATT-5835: WorkoutDetailTabbedContent & MapDetailLayout: Remove full-header edit trigger in details view and hide bottom-sheet drag handle in full-screen tabs

**Ticket**: [ATT-5835](https://rainerblind.atlassian.net/browse/ATT-5835)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5835`  
**Requirement Mapping**: `REQ-UI-498` (`REQ-UI-498.1`–`REQ-UI-498.4`) | **Test Spec ID**: `TST-UI-498` (`TST-UI-498.1`–`TST-UI-498.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-UI-408` (*Multi-Tab Workout Details*), `REQ-UI-148` & `Rule 74` (*Bottom Sheet Drag Handle Contract*), and `REQ-UI-491` (*MapDetailLayout Status Bar Inset Clearance*). Targets `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, and `TrackOnMapScreen.kt`.
* `Historical Origin & Commit Trace`: `WorkoutHeader` click binding inherited from `WorkoutSummariesWithMapCard` in `ATT-4081`; `MapDetailLayout` drag handle and sheet shape heuristic (`!useStatusBarsPadding`) introduced in `ATT-5485` and refined in `ATT-5851`.
* `Root Reason for Existing Formulation`: `WorkoutHeader` originally supported whole-card click for list drill-down; `MapDetailLayout` assumed any layout not needing status bar padding was a modal/peek bottom sheet.
* `Preservation of Core Invariants`: Dedicated edit pencil icon continues opening `EditWorkoutScreen`; modal bottom sheets in `MapScreenWithTrack` retain Rule 74 drag handles; `ATT-5851` floating controls clearance is 100% preserved.

---

## 1. Requirement Specification (`REQ-UI-498`)

### 1.1 Problem Statement & Rationale
Visual UI audits of the Workout Details view (`audit_05_under_edit.png`) identified two usability defects:
1. Tapping anywhere on the `WorkoutHeader` card surface opens the Edit Workout dialog, causing accidental triggers when inspecting metrics or switching tabs.
2. `MapDetailLayout` renders an orphan bottom-sheet drag handle pill (`—`) and rounded top corners directly below the full-screen tab row because it conflates `useStatusBarsPadding == false` with bottom-sheet presentation.

### 1.2 Functional & Architectural Requirements
1. **Header Click Decoupling (`REQ-UI-498.1`)**: In `WorkoutDetailTabbedContent.kt`, `WorkoutHeader` SHALL receive `onClicked = null`, disabling whole-card click navigation and reserving edit actions strictly to the dedicated Edit pencil icon button (`onEditWorkout`).
2. **Bottom-Sheet Chrome Decoupling (`REQ-UI-498.2`)**: `MapDetailLayout` SHALL accept `isBottomSheet: Boolean = !useStatusBarsPadding`. `MinimumDragHandle()` and `BottomSheetDesign.SheetShape` SHALL be rendered strictly when `isBottomSheet == true`. When `false`, the drag handle SHALL be omitted and container shape SHALL be `RectangleShape`.
3. **Full-Screen Tabbed Content Chrome Suppression (`REQ-UI-498.3`)**: In `WorkoutDetailTabbedContent.kt` and `TrackOnMapScreen.kt`, `MapDetailLayout` callers SHALL pass `isBottomSheet = false`, ensuring clean full-screen presentation beneath tab bars.
4. **Preservation of System Invariants (`REQ-UI-498.4`)**: Modal bottom sheets in `MapScreenWithTrack.kt` and `LiveSegmentSheet.kt` SHALL preserve Rule 74 drag handles (`isBottomSheet = true`). `ATT-5851` floating controls status bar offset (`floatingControlsTopOffsetPx`) SHALL remain strictly dependent on `useStatusBarsPadding`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (No Edit on Header Surface Tap)**: *Given* Workout Details in `WorkoutDetailTabbedContent`, *When* tapping on the `WorkoutHeader` surface, *Then* no Edit Workout dialog opens.
* **AC-2 (Edit Pencil Button Retained)**: *Given* Workout Details, *When* tapping the dedicated pencil icon button, *Then* `EditWorkoutScreen` opens.
* **AC-3 (Drag Handle Suppressed in Full-Screen Tabs)**: *Given* `WorkoutDetailTabbedContent` or `TrackOnMapScreen`, *When* rendered, *Then* no drag handle pill appears below the tab bar, and the sheet container uses flat top corners.
* **AC-4 (Bottom Sheet Rule 74 Drag Handle Preserved)**: *Given* modal bottom sheets in `MapScreenWithTrack`, *When* rendered, *Then* the Rule 74 drag handle pill is visible and top corners are rounded.

---

## 2. Test Specification (`TST-UI-498`) & Traceability Matrix

| Test Case | Scope | Target Class / File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-498.1` | WorkoutHeader onClicked Decoupling Contract | `WorkoutDetailHeaderClickContractTest.kt` | `REQ-UI-498.1` | Specified |
| `TST-UI-498.2` | MapDetailLayout isBottomSheet Parameter & Gating Contract | `MapDetailLayoutSheetChromeContractTest.kt` | `REQ-UI-498.2` | Specified |
| `TST-UI-498.3` | Full-Screen Tabbed Callers isBottomSheet Parity Contract | `MapDetailLayoutSheetChromeContractTest.kt` | `REQ-UI-498.3`, `REQ-UI-498.4` | Specified |
| `TST-UI-498.4` | Tier 1 Targeted Regression Execution | `ui.aftermath.*`, `ui.theme.*`, `build.*` | `REQ-PRO-001` | Specified |
