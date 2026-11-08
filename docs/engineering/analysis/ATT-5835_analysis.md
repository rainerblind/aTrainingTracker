# Stage 1 Analysis: ATT-5835 - WorkoutDetailTabbedContent & MapDetailLayout: Remove full-header edit trigger in details view and hide bottom-sheet drag handle in full-screen tabs

**Ticket**: [ATT-5835](https://rainerblind.atlassian.net/browse/ATT-5835)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5835`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
Visual inspection of `docs/attachments/audit_05_under_edit.png` (`Rule 71`/`Rule 72`) identified two UX and layout flaws in Workout Details (`WorkoutDetailTabbedContent`):
1. **Accidental Edit Trigger on Entire Header**: In `WorkoutDetailTabbedContent.kt`, clicking anywhere on the `WorkoutHeader` card surface opens the Edit Workout dialog (`Trainingseinheit überarbeiten`), creating accidental triggers when inspecting details even though a dedicated Edit pencil button is already provided.
2. **Orphan Bottom-Sheet Drag Handle in Full-Screen Tabs**: In `MapDetailLayout.kt`, `MinimumDragHandle()` and `BottomSheetDesign.SheetShape` are rendered whenever `useStatusBarsPadding == false`. Because `WorkoutDetailTabbedContent` consumes status bar insets at the outer container and passes `useStatusBarsPadding = false`, an orphan bottom-sheet drag handle pill (`—`) and rounded top sheet corners are displayed directly beneath the tab bar in full-screen tabs.

---

## 2. Root Cause Analysis & Visual Inspection
1. **`WorkoutHeader` Click Binding Inversion (`WorkoutDetailTabbedContent.kt:541, 546`)**:
   - `WorkoutHeader` is invoked with `onClicked = onEditWorkout?.let { edit -> { edit(workoutData.id) } }`.
   - In list cards (`WorkoutSummariesWithMapCard`), full-card clicking navigates into details; in `WorkoutDetailTabbedContent`, this turns the entire header into an unwanted edit trigger.
2. **Sheet Chrome Conflation with Inset Handling (`MapDetailLayout.kt:752-763`)**:
   - `MapDetailLayout` conflates `!useStatusBarsPadding` with bottom-sheet presentation:
     `if (!useStatusBarsPadding) { MinimumDragHandle() }` and `shape = if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape`.
   - Visually confirmed via `read_file` on `audit_05_under_edit.png`: Grey drag pill (`—`) is centered beneath the tab row (`Übersicht` / `Karte & Diagramme`), cluttering the full-screen layout.

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip)
- Audited all callers of `MapDetailLayout`:
  - Modal/peek bottom sheets (`MapScreenWithTrack.kt:763`, `LiveSegmentSheet.kt:49`): require `isBottomSheet = true` (drag handle + rounded top corners per `Rule 74` — `ATT-5485`/`ATT-5564`).
  - Full-screen tabs (`WorkoutDetailTabbedContent.kt:269, 299, 379`, `TrackOnMapScreen.kt:399`): require `isBottomSheet = false`.
- Confirmed `ATT-5851` floating controls status-bar offset (`floatingControlsTopOffsetPx` in `MapDetailLayout.kt:727-732`) relies strictly on `useStatusBarsPadding` and is unaffected by decoupling sheet chrome.

---

## 4. User Scope Grounding (ATT-1250)
* **In-Scope**:
  - Set `onClicked = null` on `WorkoutHeader` in `WorkoutDetailTabbedContent.kt`, reserving edit actions exclusively to the dedicated Edit pencil icon (`onEditWorkout`).
  - Introduce `isBottomSheet: Boolean = !useStatusBarsPadding` parameter on `MapDetailLayout` and gate `MinimumDragHandle()` and `BottomSheetDesign.SheetShape` on `isBottomSheet`.
  - Pass `isBottomSheet = false` from `WorkoutDetailTabbedContent.kt` and `TrackOnMapScreen.kt`.
  - Add contract tests verifying header click decoupling and sheet chrome gating.
* **Out-of-Scope**: Modifying `WorkoutHeader` layout/actions, bottom-sheet scaffold behaviors in `MapScreenWithTrack`, or tab layout modes.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**:
  - `REQ-UI-408` (*Multi-Tab Workout Details*): Refined to ensure full-screen tabbed layouts suppress bottom-sheet drag handles and rounded sheet corners.
  - `REQ-UI-148` & `Rule 74`: Preserves visible drag-handle pills and rounded top sheet shapes on actual peek/modal bottom sheets (`MapScreenWithTrack`).
  - `REQ-UI-491` (`ATT-5851`): Floating controls status bar offset calculation preserved untouched.
* **Preservation of Core Invariants**: Modal bottom sheets retain drag handles; dedicated Edit pencil button continues opening `EditWorkoutScreen`; zero regression in tab navigation.

---

## 6. Architectural Strategy & High-Level Solution
* In `WorkoutDetailTabbedContent.kt`, set `onClicked = null` on `WorkoutHeader`.
* In `MapDetailLayout.kt`, add `isBottomSheet: Boolean = !useStatusBarsPadding` and condition `MinimumDragHandle()` and `BottomSheetDesign.SheetShape` strictly on `isBottomSheet`.
* In `WorkoutDetailTabbedContent.kt` (lines 269, 299, 379) and `TrackOnMapScreen.kt`, pass `isBottomSheet = false`.

---

## 7. System Invariants & Risk Assessment
* **Invariants**: Edit pencil button functionality, `MapScreenWithTrack` bottom-sheet drag handle, and `ATT-5851` floating controls offset remain 100% intact.
* **Risk Rating**: **LOW** (Declarative Compose parameter additions and click listener decoupling; zero database or concurrency impact).
