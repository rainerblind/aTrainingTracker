# Stage 3: Implementation Plan - ATT-5809: Uniform Share Button in WorkoutHeader

**Ticket**: [ATT-5809](https://atrainingtracker.atlassian.net/browse/ATT-5809)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5809`  
**Requirement Mapping**: `REQ-UI-447.1` | **Test Mapping**: `TST-UI-447.4`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
Currently, workout image sharing (`WorkoutShareBottomSheet`) cannot be triggered from `WorkoutHeader` in `WorkoutSummary.kt`, is hidden in `WorkoutDetailTabbedContent.kt` across Tabs 1, 3, and 4 because the floating map button only lives on `MapDetailLayout`, and is missing from Option A snapshots due to `header = {}`. Adding a uniform Share button to `WorkoutHeader` centralizes sharing across both views.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-447.1` (*Share Mode Selection Bottom Sheet & Persistent Header Trigger*)
* **Test Mapping**: `TST-UI-447.4` (*Uniform WorkoutHeader Share Action & Rule 73 Contract*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Regression for Non-Workout Callers**: `RouteOnMapScreen` and `SegmentOnMapScreen` (`workoutData == null`) retain their floating map share button.
2. **Interactive Action Continuity**: `WorkoutHeader` edit button, 3-dot export menu (TCX/FIT/GPX/CSV/GC), and Strava upload remain fully functional.
3. **Touch Targets & Contrast**: Action buttons maintain $\ge 32\text{dp}$ touch boxes, `MaterialTheme.colorScheme.primary` tint, and WCAG AAA contrast.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutHeader.kt`
* Expose `onShareWorkout: (() -> Unit)? = null`.
* Render `IconButton(onClick = onShareWorkout, modifier = Modifier.size(32.dp))` with `Icons.Default.Share`, `tint = primary`, and `contentDescription = stringResource(R.string.share)` adjacent to `onEditWorkout`.
* Dynamic portrait spacer: scale spacer width based on active actions (`~36dp` per action, up to `~108dp` when Edit, Share, and Menu are all present) to prevent title collision.

### Component 2: `WorkoutSummary.kt`
* Introduce `showShareBottomSheet` state; pass `onShareWorkout = { showShareBottomSheet = true }` into `WorkoutHeader`.
* Render `WorkoutShareBottomSheet`: Option B shares directly with attached photos; Option A invokes `onMapClick()`.

### Component 3: `WorkoutDetailTabbedContent.kt` & `MapDetailLayout.kt`
* Wire `onShareWorkout` on persistent header to launch `WorkoutShareBottomSheet`.
* Pass non-empty `header` composable to `MapDetailLayout` so `headerLayer` captures `WorkoutHeader` for Option A snapshots.
* In `MapDetailLayout.kt`, hide floating map share button when `workoutData != null` in multi-tab details.

### UI Consistency (Rule 23)
* **Reference component**: `WorkoutHeader.kt` existing Edit `IconButton`.
* **Theme tokens**: `MaterialTheme.colorScheme.primary`, `size(32.dp)`, `Icons.Default.Share`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `WorkoutHeader.kt`
* Add `onShareWorkout` callback, render 32dp Share `IconButton`, and implement dynamic action spacer in portrait mode.

### Step 2: Wire `WorkoutSummary.kt` & `WorkoutDetailTabbedContent.kt`
* Connect `onShareWorkout` to `WorkoutShareBottomSheet` in both screens; pass `WorkoutHeader` into `MapDetailLayout.header`.

### Step 3: Update `MapDetailLayout.kt` & Contract Tests
* Suppress redundant floating map share button when `workoutData != null`.
* Update/add assertions in `WorkoutShareBottomSheetContractTest.kt` verifying header share button and wiring.

### Step 4: Targeted Verification & Governance
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit/contract tests and living docs governance.
* **Rollback**: Branch isolation (`feature/ATT-5809`) allows instant revert.
