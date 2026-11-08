# Stage 3: Implementation Plan - ATT-5835: WorkoutDetailTabbedContent & MapDetailLayout: Remove full-header edit trigger in details view and hide bottom-sheet drag handle in full-screen tabs

**Ticket**: [ATT-5835](https://rainerblind.atlassian.net/browse/ATT-5835)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5835`  
**Requirement Mapping**: `REQ-UI-498` (`REQ-UI-498.1`–`REQ-UI-498.4`)  
**Test Mapping**: `TST-UI-498` (`TST-UI-498.1`–`TST-UI-498.4`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Scope
Visual UI audits of the Workout Details view (`audit_05_under_edit.png`) identified two usability flaws:
1. **Accidental Header Edit Trigger**: In `WorkoutDetailTabbedContent.kt:541`, `WorkoutHeader` receives `onClicked = onEditWorkout`, making the entire header surface clickable and accidentally triggering the Edit Workout dialog when inspecting workout details.
2. **Orphan Bottom-Sheet Drag Handle in Full-Screen Tabs**: In `MapDetailLayout.kt:752-763`, `MinimumDragHandle()` and `BottomSheetDesign.SheetShape` are rendered whenever `!useStatusBarsPadding`. In full-screen tabs (`WorkoutDetailTabbedContent` modes A, B, and C), outer insets are already consumed, so `useStatusBarsPadding = false` causes an unwanted grey drag pill (`—`) and rounded top corners beneath the tab row.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-498` (*WorkoutDetailTabbedContent Header Click Decoupling & MapDetailLayout Sheet Chrome Decoupling*)
  * `REQ-UI-498.1`: Set `onClicked = null` on `WorkoutHeader` in `WorkoutDetailTabbedContent.kt`, reserving edit triggering strictly to the dedicated Edit pencil icon (`onEditWorkout`).
  * `REQ-UI-498.2`: Add `isBottomSheet: Boolean = !useStatusBarsPadding` to `MapDetailLayout.kt` and gate `MinimumDragHandle()` and `BottomSheetDesign.SheetShape` on `isBottomSheet`.
  * `REQ-UI-498.3`: Pass `isBottomSheet = false` in `WorkoutDetailTabbedContent.kt` (lines 269, 299, 379) and `TrackOnMapScreen.kt:399`.
  * `REQ-UI-498.4`: Preserve Rule 74 drag handles on modal bottom sheets in `MapScreenWithTrack.kt` and `LiveSegmentSheet.kt`; preserve `ATT-5851` floating controls status-bar offset (`floatingControlsTopOffsetPx`).
* **Test Mapping**: `TST-UI-498` (*Workout Details Header Click & Sheet Chrome Verification Suite*)
  * `TST-UI-498.1`: `WorkoutDetailHeaderClickContractTest.kt` verifying `WorkoutHeader` `onClicked == null` in `WorkoutDetailTabbedContent`.
  * `TST-UI-498.2`: `MapDetailLayoutSheetChromeContractTest.kt` verifying `isBottomSheet` parameter gating of `MinimumDragHandle` and `SheetShape`.
  * `TST-UI-498.3`: Full-screen callers (`WorkoutDetailTabbedContent`, `TrackOnMapScreen`) parity test (`isBottomSheet = false`).
  * `TST-UI-498.4`: Tier 1 targeted regression (`ui.aftermath.*`, `ui.theme.*`, `build.*`).

---

## 3. System Invariants & UI Consistency
1. **Edit Action Invariance**: Tapping the dedicated Edit pencil icon button (`Icons.Default.Edit`) continues to open `EditWorkoutScreen`.
2. **Rule 74 Sheet Chrome Invariance**: Expandable peek bottom sheets in `MapScreenWithTrack.kt` (`ClusterOnMapSheet`) and `LiveSegmentSheet.kt` continue displaying `MinimumDragHandle()` and rounded top corners (`isBottomSheet = true`).
3. **Floating Controls Offset Invariance**: `ATT-5851` status bar clearance for floating overlay controls relies strictly on `useStatusBarsPadding` and remains unchanged.
4. **UI Consistency**: Reuses existing Material3 shapes (`RectangleShape`, `BottomSheetDesign.SheetShape`). Zero new one-off styles.

---

## 4. Proposed Architectural Changes
* **`WorkoutDetailTabbedContent.kt`**: Set `onClicked = null` in `WorkoutHeader` invocation (line 541).
* **`MapDetailLayout.kt`**: Add `isBottomSheet: Boolean = !useStatusBarsPadding` parameter. Replace `if (!useStatusBarsPadding) MinimumDragHandle()` with `if (isBottomSheet) MinimumDragHandle()`, and set header Surface `shape = if (isBottomSheet) BottomSheetDesign.SheetShape else RectangleShape`.
* **Callers**: Pass `isBottomSheet = false` in `WorkoutDetailTabbedContent.kt` (lines 269, 299, 379) and `TrackOnMapScreen.kt` (line 399).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Header Click Decoupling**: Set `onClicked = null` on `WorkoutHeader` in `WorkoutDetailTabbedContent.kt`.
* **Step 2: MapDetailLayout Parameter & Chrome Gating**: Add `isBottomSheet: Boolean = !useStatusBarsPadding` in `MapDetailLayout.kt` and gate drag handle and sheet shape.
* **Step 3: Call-Site Updates**: Pass `isBottomSheet = false` in `WorkoutDetailTabbedContent.kt` and `TrackOnMapScreen.kt`.
* **Step 4: Contract Tests**: Author `WorkoutDetailHeaderClickContractTest.kt` and `MapDetailLayoutSheetChromeContractTest.kt`.
* **Step 5: Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted contract and unit tests in Stage 4; on-device UI walkthrough in Stage 5.
* **Rollback Plan**: Atomic commit on `feature/ATT-5835`; revert via `git reset --hard` if regression occurs.
