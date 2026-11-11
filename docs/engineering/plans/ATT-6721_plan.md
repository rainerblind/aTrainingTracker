# Stage 3: Implementation Plan - ATT-6721: Calendar-Day X-Axis, Preserve Non-HRV Loads, Readiness-Colored 7d Curve

**Ticket**: [ATT-6721](https://rainerblind.atlassian.net/browse/ATT-6721)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-033` (`REQ-PER-033.1`, `REQ-PER-033.2`, `REQ-PER-033.3`, `REQ-PER-033.6`)  
**Test Mapping**: `TST-PER-041` (`TST-PER-041.7`, `TST-PER-041.8`, `TST-PER-041.9`, `TST-PER-041.5`, `TST-PER-041.6`)  
**Branch**: `feature/ATT-6721` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
In `HrvReadinessTrendCard.kt` and `HrvReadinessTrendMath.kt`, ordinal list index mapping collapses multi-day recording gaps, workouts without morning HRV readings are dropped from training-load bars, the 7d rolling curve is drawn with a monotone primary path, `_selectedHrvPosture` eagerly defaults to `SUPINE`, and `workoutRepo.allWorkouts` is read via one-shot snapshot on cold start.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PER-033.1` (Default Posture), `REQ-PER-033.2` (Readiness-Colored 7d Curve), `REQ-PER-033.3` (Training-Load Retention), `REQ-PER-033.6` (Calendar Epoch X-Axis & Polish).
* **Test Mapping**: `TST-PER-041.7` (Epoch X-Axis & Load Retention), `TST-PER-041.8` (Colored 7d Segments), `TST-PER-041.9` (Default Posture & Reactive Workouts), `TST-PER-041.5` (Localization), `TST-PER-041.6` (Regression).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Database Schema Changes**: No changes to `HrvReadiness.db` or `WorkoutSummaries.db`.
2. **Strict Posture Isolation**: SWC corridor math remains strictly isolated per posture.
3. **Gesture Disambiguation**: `ChartGestureDisambiguator` continues to prevent horizontal scrubbing from locking vertical parent scrolling.
4. **Terminal Human Gate**: Parent ticket advances to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Math Layer (`HrvReadinessTrendMath.kt`)**: Make `HrvTrendPoint.rawValue` nullable (`Double?`). Retain workout dates within `[minHrvDate, maxHrvDate]` with `rawValue = null` and valid `trainingLoad`. Map touch position via `LocalDate.toEpochDay()`.
* **Presentation Layer (`HrvReadinessTrendCard.kt`)**: Replace `indexToX` with `dateToX` using calendar epoch days; render load bars with Rule 69 sharp bottom corners (`RoundRect`); draw 7d rolling curve in segments colored by `ReadinessStatus`; replace metric chips with `SingleChoiceSegmentedButtonRow`; use `Locale.getDefault()` and `inverseSurface`/`inverseOnSurface`.
* **ViewModel Layer (`PeriodsViewModel.kt`)**: Initialize `_selectedHrvPosture = MutableStateFlow<HrvPosture?>(null)`; resolve to `defaultPosture` upon entry load; reactively collect `workoutRepo.allWorkouts`.
* **UI Consistency (Rule 23)**:
  * Reference screen: `PeriodBarGraph.kt` (SingleChoiceSegmentedButtonRow, height 28.dp).
  * Reused components: `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `FilterChip`.
  * Theme tokens: `MaterialTheme.colorScheme.inverseSurface`/`inverseOnSurface`, `TTColor.Readiness*`, `TTColor.PmcAtlMagenta`.
  * New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Mathematical Continuity & Calendar Epoch X-Axis (`HrvReadinessTrendMath.kt`)
* Update `HrvTrendPoint` to allow `rawValue: Double?`.
* In `calculateTrendSeries`, retain dates from `dailyMap.keys` and bounded `dailyTrainingLoads.keys` within `[minHrvDate, maxHrvDate]`. For workout-only days, evaluate rolling $\mu_{7d}$/SWC from historical `dailyMap` entries and set `rawValue = null`.
* In `findNearestPoint`, calculate target epoch day from touch fraction and return closest point by `toEpochDay()`.

### Step 2: Canvas Rendering, Status-Colored Curve & Design Polish (`HrvReadinessTrendCard.kt`)
* Implement `dateToX(dateIso)` using `LocalDate.toEpochDay()`.
* Render load bars using `Path().addRoundRect` with `topStart/topEnd = 2.dp` and `bottomStart/bottomEnd = 0.dp`.
* Draw status-colored segments on the 7d curve via `getReadinessColor(p.status)`.
* Replace `FilterChip` pair with `SingleChoiceSegmentedButtonRow(height = 28.dp)`.
* Replace `Locale.US` with `Locale.getDefault()`, localized date formatter on X-axis, and `inverseSurface`/`inverseOnSurface` tooltip styling.

### Step 3: Lazy Posture Default & Reactive Workouts Hydration (`PeriodsViewModel.kt`)
* Set `_selectedHrvPosture = MutableStateFlow<HrvPosture?>(null)`.
* Resolve `defaultPosture` when `_selectedHrvPosture.value == null`.
* In `init`, launch `workoutRepo.allWorkouts.collect { refreshHrvTrend() }`.

### Step 4: Verification Suite Updates & Execution
* Add test cases in `HrvReadinessTrendMathTest.kt` for calendar epoch X-axis, non-HRV load retention, and touch hit resolution.
* Add test cases in `PeriodsViewModelTest.kt` for posture defaulting (50 SEATED vs 1 SUPINE) and reactive `allWorkouts` collection.
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite and verify clean compilation with 100% test pass rate.
* **Rollback**: `git checkout sprint/2026-46.2` safely discards uncommitted branch changes.
