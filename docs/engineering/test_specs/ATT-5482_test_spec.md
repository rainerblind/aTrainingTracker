# Stage 2: Requirement & Test Specification - ATT-5482: Aftermath Delete / Merge Lap

**Ticket**: [ATT-5482](https://rainerblind.atlassian.net/browse/ATT-5482)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5482`  
**Requirement Mapping**: `REQ-UI-454` | **Test Spec ID**: `TST-UI-454`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-454`)

### 1.1 Problem Statement & Rationale
Complementing map lap splitting (ATT-907, `REQ-UI-449`) and boundary moving (ATT-908, `REQ-UI-450`), athletes currently cannot remove lap boundaries or merge adjacent laps in Aftermath when $N \ge 2$ laps exist. The system SHALL provide a "Delete / Merge Lap" action in `LapEditBottomSheet.kt` and `WorkoutLaps`, supporting Option A boundary direction selection, live preview of combined metrics, transactional `LAP_NR` decrement in `WorkoutSamples.db`, and atomic persistence in `Laps.db` via `replaceCroppedLaps`.

### 1.2 Functional & Architectural Requirements
1. **Option A Boundary Selection (`REQ-UI-454.1`)**: For $k=1$, merge Lap 1 + Lap 2 (boundary 1). For $k=N$, merge Lap $N-1$ + Lap $N$ (boundary $N-1$). For interior laps $1 < k < N$, provide a `SingleChoiceSegmentedButtonRow` to toggle between previous (Lap $k-1$ + Lap $k$) and next (Lap $k$ + Lap $k+1$).
2. **Interactive Preview (`REQ-UI-454.2`)**: In `DeleteLapConfirmationDialog.kt`, render combined active duration, distance, and speed/pace calculated via `LapSplitAndMoveCalculator.calculateMergePreview`, preserving non-empty names/descriptions.
3. **Transactional Persistence (`REQ-UI-454.3`)**: `WorkoutSamplesDatabaseManager.decrementLapNumbersAbove` atomically executes `UPDATE <table> SET LAP_NR = LAP_NR - 1 WHERE LAP_NR > ?`. `WorkoutRepository.deleteLap` recomputes lap sequence, persists via `LapsDatabaseManager.replaceCroppedLaps`, invalidates export cache, and refreshes reactive `WorkoutData`.
4. **Invariants (`REQ-UI-454.4`)**: Minimum 1 lap retained (action disabled when $N < 2$); zero phantom laps (`REQ-TRK-002`); session totals immutable.
5. **Localization Parity (`REQ-UI-454.5`)**: 100% 9-language translation parity across all `delete_lap_*` string keys without emojis.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (UI Entry & Option A Selection)**: *Given* $N \ge 2$ laps and Lap $k$ opened, *When* "Delete / Merge Lap" is clicked, *Then* `DeleteLapConfirmationDialog` opens showing fixed direction for $k \in \{1, N\}$ and `SingleChoiceSegmentedButtonRow` direction toggle for $1 < k < N$.
* **AC-2 (Live Preview Accuracy)**: *Given* the dialog open, *When* selecting a merge direction, *Then* preview duration, distance, and pace/speed reflect the combined lap values.
* **AC-3 (Transactional Persistence)**: *Given* confirmed boundary deletion $B$, *When* `WorkoutRepository.deleteLap` executes, *Then* samples with `LAP_NR > B` decrement by 1, laps $1 \dots N-1$ persist atomically, and session totals remain unchanged.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-454`, extending `REQ-UI-449` (ATT-907), `REQ-UI-450` (ATT-908), `REQ-TRK-002`, and `REQ-UI-142`. Targets `WorkoutRepository.kt`, `LapSplitAndMoveCalculator.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, and `LapEditBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-907` (map lap creation), `ATT-908` (boundary moving), `ATT-896` (zero phantom laps), `ATT-3274` (transactional sample pruning).
* **Root Reason for Existing Formulation**: Lap boundaries were historically immutable post-workout. Splitting and shifting were introduced first; deleting/merging completes full CRUD lifecycle parity.
* **Preservation of Core Invariants**: No phantom laps (merged duration $> 0$, distance $\ge 0$); contiguous $1 \dots N-1$ ordering; session summary totals and polyline 100% immutable; Rule 47 modal overlay preserved.

---

## 2. Test Specification (`TST-UI-454`)

### 2.1 Test Cases
1. **TC-1: Domain Math & Merge Preview (`TST-UI-454.1`)**:
   - *Scope*: Unit test (`LapSplitAndMoveCalculatorTest.kt`).
   - *Procedure*: Validate `calculateMergePreview` and `recalculateLapsAfterMerge` for interior and boundary laps; assert correct duration/distance summing, pace calculation, contiguous $1 \dots N-1$ renumbering, and invalid boundary rejection.
2. **TC-2: Transactional Decrement & DB Contract (`TST-UI-454.2`)**:
   - *Scope*: Database integration test (`WorkoutSamplesDatabaseManagerLapDeleteTest.java` / `WorkoutRepositoryLapDeleteTest.kt`).
   - *Procedure*: Verify atomic sample `LAP_NR` decrement, `replaceCroppedLaps` persistence, reactive emission, session metrics immutability, and clean rollback on SQLite exception.
3. **TC-3: UI Dialog & Option A Toggle (`TST-UI-454.3`)**:
   - *Scope*: Compose UI test (`DeleteLapConfirmationDialogTest.kt` / `LapEditBottomSheetTest.kt`).
   - *Procedure*: Verify CTA display when $N \ge 2$, direction toggle on interior laps, fixed boundary on edge laps, and delete callback dispatch.
4. **TC-4: 9-Language Localization & Parity (`TST-UI-454.4`)**:
   - *Scope*: Localization test (`TranslationParityTest.kt`).
   - *Procedure*: Assert 100% presence and format token parity across all 9 locales for all `delete_lap_*` string keys.
5. **TC-5: Targeted Regression Suite (`TST-UI-454.5`)**:
   - *Command*: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.domain.laps.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
   - *Expected*: 100% pass rate.

---

## 3. Traceability Matrix
| Requirement ID | Test Case ID | Scope | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-454.1` | `TST-UI-454.3` (TC-3) | Compose UI (`DeleteLapConfirmationDialogTest`) | Planned (`ATT-5482`) |
| `REQ-UI-454.2` | `TST-UI-454.1` (TC-1) | Domain Math (`LapSplitAndMoveCalculatorTest`) | Planned (`ATT-5482`) |
| `REQ-UI-454.3` | `TST-UI-454.2` (TC-2) | DB / Repo (`WorkoutRepositoryLapDeleteTest`) | Planned (`ATT-5482`) |
| `REQ-UI-454.4` | `TST-UI-454.1`, `TST-UI-454.2` (TC-1, TC-2) | Domain & Repo Invariants | Planned (`ATT-5482`) |
| `REQ-UI-454.5` | `TST-UI-454.4` (TC-4) | Localization (`TranslationParityTest`) | Planned (`ATT-5482`) |
| `REQ-PRO-001`  | `TST-UI-454.5` (TC-5) | Regression Suite Execution | Planned (`ATT-5482`) |
