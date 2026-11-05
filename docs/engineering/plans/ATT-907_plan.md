# Stage 3: Implementation Plan - ATT-907: Aftermath: Create lap based on the map

**Ticket**: [ATT-907](https://rainerblind.atlassian.net/browse/ATT-907)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-907`  
**Requirement Mapping**: `REQ-UI-449` (`REQ-UI-449.1` - `REQ-UI-449.5`)  
**Test Mapping**: `TST-UI-449` (`TST-UI-449.1` - `TST-UI-449.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Description & Background
In post-workout Aftermath, athletes cannot retroactively split an existing recorded lap along the GPS route. This plan details the architectural construction of interactive lap creation on the map (`CreateLapOnMapSheet`), atomic sample shifting in `WorkoutSamples.db`, and reactive synchronization with `Laps.db` and UI components, while establishing a reusable foundation for `ATT-908`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-449.1` (Entry point & scrub pre-seeding), `REQ-UI-449.2` (Interactive `CreateLapOnMapSheet`), `REQ-UI-449.3` (Transactional persistence), `REQ-UI-449.4` (ATT-908 synergy), `REQ-UI-449.5` (9-language parity).
* **Test Mapping**: `TST-UI-449.1` (`LapSplitAndMoveCalculatorTest.kt`), `TST-UI-449.2` (`WorkoutSamplesDatabaseManagerLapSplitTest.java` / `WorkoutRepositoryLapSplitTest.kt`), `TST-UI-449.3` (`CreateLapOnMapSheetTest.kt`), `TST-UI-449.4` (`TranslationParityTest.kt`), `TST-UI-449.5` (Targeted test suite).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Phantom Laps (`REQ-TRK-002`)**: No split may create zero-duration or zero-distance laps; split index $s$ is strictly constrained to $start_k < s \le end_k$.
2. **Contiguous Lap Sequencing**: `LAP_NR` in both `WorkoutSamples.db` and `Laps.db` remains strictly contiguous ($1 \dots K+1$) with zero gaps.
3. **Session Metric Invariance**: Total workout time, active time, distance, elevation, polyline, and extrema remain completely unaltered.
4. **Code Lock (Rule 4)**: Zero production edits in `app/src/` until Gate 3 is approved.

---

## 4. Proposed Architectural Changes
### Component 1: `LapSplitAndMoveCalculator.kt` (Domain Layer)
* Pure, testable helper computing split validation ($start_k < s \le end_k$), 2-lap preview metrics (distance, duration, average speed via `CropWorkoutMath.calculateAverageSpeed`), and resequenced `List<LapData>`. Structured to support boundary shifting (`ATT-908`).

### Component 2: `WorkoutSamplesDatabaseManager.java` & `WorkoutRepository.kt`
* Add `incrementLapNumbersFromSample(fileBaseName, splitRowId)` executing `UPDATE <table> SET LAP_NR = LAP_NR + 1 WHERE _id >= ?` in an atomic SQLite transaction with defensive `LAP_NR` column creation.
* Implement `WorkoutRepository.splitLapAtSample(workoutId, sampleIndex)` on `Dispatchers.IO`: loads sample points, updates `WorkoutSamples.db`, recalculates laps, persists to `Laps.db` via `replaceCroppedLaps`, invalidates export cache, and triggers `reloadWorkoutData`.

### Component 3: `CreateLapOnMapSheet.kt` (`AppModalBottomSheet`)
* Interactive sheet (`scrollable = false`) containing: route polyline, lap boundary markers, movable split pin, distance/sample slider, and live 2-lap preview cards formatted via canonical `LocalMetricFormatter` / `DistanceFormatter` / `SpeedFormatter`.

### Component 4: Entry Points & UI Integration
* Add "Split Lap on Map" triggers in `WorkoutLaps.kt`, `LapSplitVisualizerCard.kt`, and `MapDetailLayout.kt` (pre-seeding candidate index from active scrub point).
* Update all 9 `strings.xml` files with action labels, titles, and preview strings.

### UI Consistency (Rule 23)
* **Reused components**: `AppModalBottomSheet`, `ATrainingTrackerMap`, `MetricItem`, canonical formatters.
* **Theme tokens**: `MaterialTheme.colorScheme.surfaceVariant`, `TTColor`, standard typography. New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Implement `LapSplitAndMoveCalculator.kt` & Unit Tests (`TST-UI-449.1`)
* Create `LapSplitAndMoveCalculator.kt` with split validation, preview generation, and lap list recalculation; verify with `LapSplitAndMoveCalculatorTest.kt`.

### Step 2: Implement Database Managers & Repository Persistence (`TST-UI-449.2`)
* Add `incrementLapNumbersFromSample` in `WorkoutSamplesDatabaseManager.java` and `splitLapAtSample` in `WorkoutRepository.kt`; verify with repository/DB unit tests.

### Step 3: Implement `CreateLapOnMapSheet.kt` & Localization (`TST-UI-449.3`, `TST-UI-449.4`)
* Construct `CreateLapOnMapSheet.kt` and add localized strings across all 9 `strings.xml` resource files; verify with `TranslationParityTest`.

### Step 4: Wire UI Entry Points in Aftermath Screens
* Integrate sheet launch action in `WorkoutLaps.kt`, `LapSplitVisualizerCard.kt`, `TrackOnMapScreen.kt`, and `MapDetailLayout.kt`.

### Step 5: Verification & Regression Gate (`TST-UI-449.5`)
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.domain.laps.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.i18n.TranslationParityTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: `LapSplitAndMoveCalculatorTest`, `WorkoutRepositoryLapSplitTest`, `CreateLapOnMapSheetTest`, `TranslationParityTest`.
* **Rollback**: `git checkout feature/ATT-907 -- .` cleanly reverts branch changes.
