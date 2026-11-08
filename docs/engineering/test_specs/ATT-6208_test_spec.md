# Stage 2: Requirement & Test Specification - ATT-6208: WorkoutShareBottomSheet Automated Map Snapshot & Share

**Ticket**: [ATT-6208](https://rainerblind.atlassian.net/browse/ATT-6208)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-PER-029` (*WorkoutShareBottomSheet Option A Automated Snapshot & Share Delegation*)  
**Test Spec ID**: `TST-PER-037`  
**Branch**: `feature/ATT-6208`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-PER-029)

### 1.1 Problem Statement & Rationale
Selecting Option A ("Karten- & Analyse-Snapshot") in `WorkoutShareBottomSheet`:
1. In `WorkoutSummary.kt`, invokes `onMapClick()`, navigating to workout details without capturing a snapshot or launching the Android Share intent.
2. In `WorkoutDetailTabbedContent.kt`, animates the pager to `mapTabIndex` without setting any trigger state to initiate `isSharing` on `MapDetailLayout`.
3. In `MapDetailLayout.kt`, `isSharing` was strictly internal, and `showFloatingShareButton` was default-suppressed when `workoutData != null` (ATT-5809 / REQ-UI-500).
Selecting Option A must programmatically trigger composite map/chart snapshot generation and immediately open the Android Share sheet, while strictly preserving `showFloatingShareButton: Boolean = workoutData == null`.

### 1.2 Functional & Architectural Requirements
* **REQ-PER-029.1** (*Programmatic Snapshot Trigger in MapDetailLayout*): `MapDetailLayout` SHALL accept `externalSnapshotTrigger: Boolean = false` and `onSnapshotComplete: (() -> Unit)? = null`. When `externalSnapshotTrigger == true` and `!isSharing`, it initiates composite snapshot capture (`isSharing = true`) and launches `combineWorkoutAndShare`. On finish or error, it resets `isSharing` and calls `onSnapshotComplete`.
* **REQ-PER-029.2** (*WorkoutDetailTabbedContent Automated Handoff*): Selecting Option A in `WorkoutDetailTabbedContent` SHALL dismiss the bottom sheet, switch/animate the pager to `mapTabIndex`, and trigger `externalSnapshotTrigger = true` once active.
* **REQ-PER-029.3** (*WorkoutSummary List-to-Detail Auto-Share Bridge*): Selecting Option A in `WorkoutSummary` SHALL navigate to the workout detail screen with an auto-share intent (`initialAutoShareSnapshot = true`), switching directly to the Map tab and capturing the snapshot.
* **REQ-PER-029.4** (*Preservation of REQ-UI-500 / ATT-6214 Map Controls Invariant*): Default `showFloatingShareButton: Boolean = workoutData == null` in `MapDetailLayout` and `top = 16.dp` docking of `topEndActions` MUST remain preserved intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Detail Tab Option A Share)**:
  - *Given* an athlete viewing a workout in `WorkoutDetailTabbedContent`,
  - *When* tapping Share in `WorkoutHeader` and selecting 'Karten- & Analyse-Snapshot',
  - *Then* the sheet closes, the view navigates to the Map tab, and the composite snapshot is automatically captured and launched via the Android Share chooser.
* **Criterion 2 (Workout List Option A Share)**:
  - *Given* an athlete viewing a workout card in `WorkoutSummary`,
  - *When* selecting 'Karten- & Analyse-Snapshot',
  - *Then* the detail screen opens with `initialAutoShareSnapshot = true`, navigating to the Map tab, capturing the snapshot, and launching the Android Share chooser.
* **Criterion 3 (Map Tab Controls Invariant)**:
  - *Given* `MapDetailLayout` with `workoutData != null`,
  - *When* the Map tab renders,
  - *Then* `showFloatingShareButton` is false and top-end actions dock at `16.dp` per `REQ-UI-500`.

---

## 2. Test Specification (TST-PER-037)

### Test Case 1: MapDetailLayout Programmatic Snapshot Trigger Contract (`TST-PER-037.1`)
* **Scope**: Contract / Unit Test (`MapDetailLayoutTest.kt`)
* **Assertion**: Verify `MapDetailLayout` accepts `externalSnapshotTrigger` and `onSnapshotComplete`, triggering snapshot generation when activated.

### Test Case 2: WorkoutDetailTabbedContent Option A Automated Handoff (`TST-PER-037.2`)
* **Scope**: Contract / Unit Test (`WorkoutDetailTabbedContentTest.kt`)
* **Assertion**: Verify selecting Option A scrolls to `mapTabIndex` and activates `externalSnapshotTrigger = true`.

### Test Case 3: WorkoutSummary List-to-Detail Auto-Share Bridge (`TST-PER-037.3`)
* **Scope**: Contract / Unit Test (`WorkoutSummaryTest.kt`)
* **Assertion**: Verify `WorkoutSummary` delegates Option A selection with `initialAutoShareSnapshot = true`.

### Test Case 4: Map Controls Invariant Audit (`TST-PER-037.4`)
* **Scope**: Source Code Audit Test
* **Assertion**: Verify `showFloatingShareButton: Boolean = workoutData == null` is preserved and no duplicate share FAB appears.

### Test Case 5: Tier 1 Targeted Unit Test Regression Suite (`TST-PER-037.5`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-037.1` | Contract | `MapDetailLayoutTest.kt` | `REQ-PER-029.1` | Specified |
| `TST-PER-037.2` | Contract | `WorkoutDetailTabbedContentTest.kt` | `REQ-PER-029.2` | Specified |
| `TST-PER-037.3` | Contract | `WorkoutSummaryTest.kt` | `REQ-PER-029.3` | Specified |
| `TST-PER-037.4` | Audit | `MapDetailLayout.kt` | `REQ-PER-029.4`, `REQ-UI-500` | Specified |
| `TST-PER-037.5` | Regression | `ui.map.*`, `ui.aftermath.*` | `REQ-PRO-001` | Specified |
