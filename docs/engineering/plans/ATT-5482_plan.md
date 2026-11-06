# Stage 3: Implementation Plan - ATT-5482: Aftermath Delete / Merge Lap

**Ticket**: [ATT-5482](https://rainerblind.atlassian.net/browse/ATT-5482)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5482`  
**Requirement Mapping**: `REQ-UI-454` (`REQ-UI-454.1` - `REQ-UI-454.5`)  
**Test Mapping**: `TST-UI-454` (`TST-UI-454.1` - `TST-UI-454.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In Aftermath (`WorkoutLaps`, `LapEditBottomSheet`, `TrackOnMapScreen`), athletes cannot remove recorded lap boundaries or consolidate adjacent laps. This plan details the architectural construction of Option A lap boundary removal, transactional sample `LAP_NR` decrement in `WorkoutSamples.db`, atomic lap summary re-aggregation in `Laps.db`, interactive preview dialog with `SingleChoiceSegmentedButtonRow`, and screen wiring.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-454.1` (Option A direction selection), `REQ-UI-454.2` (Interactive combined preview), `REQ-UI-454.3` (Transactional persistence), `REQ-UI-454.4` (Core invariants & $N \ge 2$ guard), `REQ-UI-454.5` (9-language parity).
* **Test Mapping**: `TST-UI-454.1` (`LapSplitAndMoveCalculatorTest.kt`), `TST-UI-454.2` (`WorkoutRepositoryLapDeleteTest.kt`), `TST-UI-454.3` (`DeleteLapConfirmationDialogTest.kt` / `LapEditBottomSheetTest.kt`), `TST-UI-454.4` (`TranslationParityTest.kt`), `TST-UI-454.5` (Targeted regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Phantom Laps (`REQ-TRK-002`)**: Merged lap aggregates two valid laps, guaranteeing positive duration and distance.
2. **Contiguous Lap Sequencing**: `LAP_NR` in `WorkoutSamples.db` and `Laps.db` strictly resequenced to $1 \dots N-1$ with zero gaps.
3. **Session Metric Invariance**: Total workout duration, active time, distance, elevation, polyline, and extrema remain completely unaltered.
4. **Minimum 1-Lap Guard**: Deletion/merging is strictly disabled and rejected when $N < 2$.
5. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 is approved.

---

## 4. Proposed Architectural Changes
### Component 1: `LapSplitAndMoveCalculator.kt` (Domain Layer)
* Add `calculateMergePreview(lapA: LapData, lapB: LapData): LapData` computing summed duration/distance, average speed via `CropWorkoutMath.calculateAverageSpeed`, and combining non-empty names/descriptions (`"${lapA.name} / ${lapB.name}"`).
* Add `recalculateLapsAfterMerge(workoutId, existingLaps, removedBoundaryLapNr, updatedSamples): List<LapData>` producing contiguous $1 \dots N-1$ sequence.

### Component 2: `WorkoutSamplesDatabaseManager.java` & `WorkoutRepository.kt` (Data Layer)
* Add `decrementLapNumbersAbove(fileBaseName, boundaryLapNr)` executing `UPDATE <table> SET LAP_NR = LAP_NR - 1 WHERE LAP_NR > ?` in an atomic SQLite transaction.
* Implement `WorkoutRepository.deleteLap(workoutId, removedBoundaryLapNr)` on `Dispatchers.IO`: validates $N \ge 2$, updates `WorkoutSamples.db`, recalculates laps, persists to `Laps.db` via `replaceCroppedLaps`, invalidates export cache, and triggers `reloadWorkoutData`.

### Component 3: `DeleteLapConfirmationDialog.kt` & `LapEditBottomSheet.kt` (UI Layer)
* Construct `DeleteLapConfirmationDialog.kt` implementing Option A: fixed boundary for $k=1$ and $k=N$; `SingleChoiceSegmentedButtonRow` direction toggle (per `docs/design_guidelines.md` Section 1.1) for interior laps ($1 < k < N$); live preview card using `MetricItem`.
* Add "Delete / Merge Lap" action button in `LapEditBottomSheet.kt` when `totalLaps >= 2`.

### Component 4: Entry Points & Localization
* Wire `onDeleteLap` in `TrackOnMapScreen.kt` and `WorkoutSummary.kt`.
* Add 10 localized strings across all 9 `strings.xml` resource files.

### UI Consistency (Rule 23)
* **Reused components**: `AlertDialog`, `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `MetricItem`, `LocalMetricFormatter`.
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.error` / `errorContainer` for destructive CTA, standard typography. New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Implement Merge Math in `LapSplitAndMoveCalculator.kt` (`TST-UI-454.1`)
* Add `calculateMergePreview` and `recalculateLapsAfterMerge`; verify with `LapSplitAndMoveCalculatorTest.kt`.

### Step 2: Implement Database Decrement & Repository Persistence (`TST-UI-454.2`)
* Add `decrementLapNumbersAbove` in `WorkoutSamplesDatabaseManager.java` and `deleteLap` in `WorkoutRepository.kt`; verify with `WorkoutRepositoryLapDeleteTest.kt`.

### Step 3: Implement `DeleteLapConfirmationDialog.kt` & 9-Locale Strings (`TST-UI-454.3`, `TST-UI-454.4`)
* Construct `DeleteLapConfirmationDialog.kt` and add strings across all 9 locales; verify with `TranslationParityTest`.

### Step 4: Wire UI Actions in `LapEditBottomSheet.kt`, `TrackOnMapScreen.kt`, and `WorkoutSummary.kt`
* Integrate Delete/Merge CTA in `LapEditBottomSheet.kt` and wire repository invocation in host screens.

### Step 5: Verification & Regression Gate (`TST-UI-454.5`)
* Run targeted test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.domain.laps.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: `LapSplitAndMoveCalculatorTest`, `WorkoutRepositoryLapDeleteTest`, `DeleteLapConfirmationDialogTest`, `TranslationParityTest`.
* **Rollback**: `git checkout feature/ATT-5482 -- .` cleanly reverts branch changes.
