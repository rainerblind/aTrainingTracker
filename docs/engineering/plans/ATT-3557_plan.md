# Stage 3: Implementation Plan - ATT-3557: Render Longitudinal HRV Readiness Trend in Periods Screen

**Ticket**: [ATT-3557](https://rainerblind.atlassian.net/browse/ATT-3557)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3557`  
**Requirement Mapping**: `REQ-PER-033` (`REQ-PER-033.1`..`REQ-PER-033.6`)  
**Test Mapping**: `TST-PER-041` (`TST-PER-041.1`..`TST-PER-041.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Athletes recording daily morning resting HRV require longitudinal recovery visualization inside `PeriodsScreen` to observe autonomic adaptations relative to their 60-day Smallest Worthwhile Change (SWC) corridor and training load (*Plews et al. 2013*). The solution provides an interactive trend card (`HrvReadinessTrendCard`) featuring posture selector chips, daily primary $\ln(\text{rMSSD})$ / $\text{rMSSD}$ scatter points, 7-day rolling curve, shaded 60-day SWC corridor, optional training-load overlay, scrubbing via `ChartGestureDisambiguator`, Rule 48 scaling, and graceful omission when zero records exist.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PER-033.1` (Posture isolation & default), `REQ-PER-033.2` (Daily scatter, 7d rolling trend & 60d SWC corridor), `REQ-PER-033.3` (Training load overlay), `REQ-PER-033.4` (Touch scrubbing & gesture disambiguation), `REQ-PER-033.5` (Graceful omission when empty), `REQ-PER-033.6` (Rule 48 axis scaling & outlier resilience).
* **Test Mapping**: `TST-PER-041.1` (Posture filtering & series math), `TST-PER-041.2` (7d mean & 60d Bessel SWC corridor), `TST-PER-041.3` (Training load alignment & scrubbing math), `TST-PER-041.4` (PeriodsViewModel integration & empty state), `TST-PER-041.5` (9-language localization), `TST-PER-041.6` (Tier 1 targeted regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Database Regressions**: Zero schema changes to `PeriodSummaries.db` or `HrvReadiness.db`.
2. **Strict Posture Isolation**: SWC baselines never mix readings across postures (`SUPINE`, `SEATED`, `STANDING`).
3. **Gesture Safety**: Dominant vertical swipes pass through to parent list scroll without locking (`design_guidelines.md` §2.2).
4. **Code Lock Until Gate 3 Passes**: No production code in `app/src/main/` modified until Gate 3 is signed off (`Rule 4`).

---

## 4. Proposed Architectural Changes & UI Consistency
* **Pure Math Engine (`HrvReadinessTrendMath.kt`)**: Implements daily deduplication, rolling 7d mean, 60d Bessel sample SD ($N-1$), SWC corridor, Rule 48 outlier Y-clamping, daily training load alignment, and nearest-point touch detection.
* **Trend Composable (`HrvReadinessTrendCard.kt`)**: Jetpack Compose card with posture chips, metric mode toggle (`ln(rMSSD)` vs `rMSSD ms`), training load overlay toggle, Canvas plot (shaded corridor, rolling curve, scatter points, load bars, axis gutter labels), scrubbing tooltip, and Light/Dark/AMOLED `@Preview`s.
* **ViewModel & Screen Integration (`PeriodsViewModel.kt`, `PeriodsTabsScreen.kt`)**: `PeriodsViewModel` observes `HrvReadinessRepository` and exposes `hrvReadinessTrendState`. `PeriodsTabsScreen` renders the card above `PeriodBarGraph` only when entries exist; omits cleanly when empty.
* **UI Consistency**: Follows `WorkoutClusterTrendChartCard.kt` chart patterns and `PeriodBarGraph.kt` card styling. Uses `MaterialTheme.colorScheme`, `RoundedCornerShape(12.dp)`, `16.dp` padding, `TTColor` readiness status colors, and `TrainingTrackerTheme`.
* **SWE.2 Maintenance (`docs/architecture.md`)**: Allocate `HrvReadinessTrendCard` and `HrvReadinessTrendMath` under Aftermath / Periods analytical hub.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Pure Kotlin Math Engine (HrvReadinessTrendMath.kt)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/HrvReadinessTrendMath.kt`
* Implement data structures (`HrvTrendPoint`, `HrvTrendSeries`), posture default resolver, rolling 7d mean, 60d SWC corridor, Y-axis scaling, and touch-hit calculation.

### Step 2: Implement HrvReadinessTrendCard Composable & Previews
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/HrvReadinessTrendCard.kt`
* Build Compose UI with posture filter chips, metric toggle, training load overlay, Canvas drawing (shaded corridor, 7d curve, scatter points, load bars, axis labels), `ChartGestureDisambiguator` scrubbing, and Light/Dark/AMOLED previews.

### Step 3: Integrate with PeriodsViewModel and PeriodsTabsScreen
* Files: `PeriodsViewModel.kt`, `PeriodsTabsScreen.kt`
* Add `hrvReadinessTrendState` to `PeriodsViewModel`, load entries on `loadPeriods()`, and conditionally render `HrvReadinessTrendCard` in `PeriodsTabsScreen` (omitted when empty).

### Step 4: Unit Test Authoring & Targeted Regression Execution
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/HrvReadinessTrendMathTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModelTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests covering deduplication, Bessel SWC calculation, training load alignment, ViewModel state emissions, empty omission, and 9-language translation parity.
* **Rollback**: Branch isolation (`feature/ATT-3557`) ensures immediate revert without affecting `sprint/2026-46.1`.
