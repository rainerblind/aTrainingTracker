# Stage 3: Implementation Plan - ATT-908: Aftermath: Move lap based on the map

**Ticket**: [ATT-908](https://rainerblind.atlassian.net/browse/ATT-908)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-908`  
**Requirement Mapping**: `REQ-UI-450` (`REQ-UI-450.1` - `REQ-UI-450.5`)  
**Test Mapping**: `TST-UI-450` (`TST-UI-450.1` - `TST-UI-450.5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Description & Background
In post-workout Aftermath, athletes cannot adjust an existing boundary between two adjacent recorded laps ($k$ and $k+1$). This plan details the architectural construction of interactive lap boundary moving on the GPS route (`MoveLapBoundaryOnMapSheet`), atomic sample reassignment for the shifted range in `WorkoutSamples.db`, and reactive synchronization with `Laps.db` and UI components.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-450.1` (Entry points & pre-seeding), `REQ-UI-450.2` (Interactive `MoveLapBoundaryOnMapSheet`), `REQ-UI-450.3` (Boundary clamping & zero-phantom guard), `REQ-UI-450.4` (Transactional persistence), `REQ-UI-450.5` (9-language localization parity).
* **Test Mapping**: `TST-UI-450.1` (`LapBoundaryMoveCalculatorTest.kt`), `TST-UI-450.2` (`WorkoutRepositoryBoundaryMoveTest.kt`), `TST-UI-450.3` (`MoveLapBoundaryOnMapSheetTest.kt`), `TST-UI-450.4` (`TranslationParityTest.kt`), `TST-UI-450.5` (Targeted regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Phantom Laps (`REQ-TRK-002`)**: Shifting boundary cannot reduce Lap $k$ or Lap $k+1$ to duration $\le 0$ or distance $\le 0.0$; position is strictly clamped to $[startIndex(k) + 1, endIndex(k+1) - 1]$.
2. **Neighboring Lap Immutability**: All laps prior to $k$ and subsequent to $k+1$ remain completely untouched.
3. **Session Metric Invariance**: Total workout duration, active time, distance, elevation, polyline, and extrema remain unaltered.
4. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 is approved.

---

## 4. Proposed Architectural Changes
### Component 1: `LapSplitAndMoveCalculator.kt` (Domain Layer)
* Add `validateBoundaryMove` enforcing $[startIndex(k) + 1, endIndex(k+1) - 1]$ clamping, `calculateBoundaryMovePreview` for live metrics, and `recalculateLapsAfterBoundaryMove` updating only the two affected laps.

### Component 2: `WorkoutSamplesDatabaseManager.java` & `WorkoutRepository.kt`
* Add `updateLapNumberForSampleRange(fileBaseName, startRowId, endRowId, newLapNr)` executing `UPDATE <table> SET LAP_NR = ? WHERE _id >= ? AND _id <= ?` in an atomic SQLite transaction.
* Implement `WorkoutRepository.moveLapBoundary(workoutId, boundaryLapNr, newBoundarySampleIndex)` on `Dispatchers.IO`: computes shifted sample range, updates `WorkoutSamples.db`, recalculates affected laps, persists via `replaceCroppedLaps`, invalidates export cache, and triggers `reloadWorkoutData`.

### Component 3: `MoveLapBoundaryOnMapSheet.kt` (`AppModalBottomSheet`)
* Interactive sheet (`scrollable = false`) with: track polyline, clamped slider over $[startIndex(k) + 1, endIndex(k+1) - 1]$, movable junction pin, boundary selector (when laps $> 2$), and live 2-lap preview cards.

### Component 4: Entry Points & UI Integration
* Add "Move Boundary on Map" trigger in `LapEditBottomSheet.kt`, `WorkoutLaps.kt`, and `LapSplitVisualizer.kt` when workout has $\ge 2$ laps.
* Add 7 localized strings across all 9 `strings.xml` resource files.

### UI Consistency (Rule 23)
* **Reused components**: `AppModalBottomSheet`, `PathPreviewMap`, `MetricItem`, canonical formatters.
* **Theme tokens**: `MaterialTheme.colorScheme.surfaceVariant`, `TTColor`, standard typography. New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Implement Boundary Move Math in `LapSplitAndMoveCalculator.kt` (`TST-UI-450.1`)
* Add boundary validation, preview generation, and lap update math; verify with `LapBoundaryMoveCalculatorTest.kt`.

### Step 2: Implement Range Updates in Database & Repository (`TST-UI-450.2`)
* Add `updateLapNumberForSampleRange` in `WorkoutSamplesDatabaseManager.java` and `moveLapBoundary` in `WorkoutRepository.kt`; verify with repository unit tests.

### Step 3: Implement `MoveLapBoundaryOnMapSheet.kt` & Localization (`TST-UI-450.3`, `TST-UI-450.4`)
* Construct `MoveLapBoundaryOnMapSheet.kt` and add strings across all 9 locales; verify with `TranslationParityTest`.

### Step 4: Wire UI Entry Points in Aftermath Screens
* Integrate boundary move action into `LapEditBottomSheet.kt`, `WorkoutLaps.kt`, `TrackOnMapScreen.kt`, and `WorkoutSummary.kt`.

### Step 5: Verification & Regression Gate (`TST-UI-450.5`)
* Run targeted test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.domain.laps.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.i18n.TranslationParityTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: `LapBoundaryMoveCalculatorTest`, `WorkoutRepositoryBoundaryMoveTest`, `MoveLapBoundaryOnMapSheetTest`, `TranslationParityTest`.
* **Rollback**: `git checkout feature/ATT-908 -- .` cleanly reverts branch changes.
