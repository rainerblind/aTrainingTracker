# Stage 3: Implementation Plan - ATT-6724: 30d Cardio-Power Rolling Trend, Reactive Refresh & Hub UX Parity

**Ticket**: [ATT-6724](https://rainerblind.atlassian.net/browse/ATT-6724)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-REV-011`  
**Test Mapping**: `TST-REV-012`  
**Branch**: `feature/ATT-6724`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint 2026-46.1 review identified 4 defects in Cardio-Power trend charting: left-edge truncation bias due to slicing before rolling average calculation, single-shot non-reactive sample loading, SQLite UTC timestamp assumption, and missing hub UX parity (`1M` pill, intermediate date ticks, touch scrubbing).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-REV-011` (*Cardio-Power Longitudinal Fitness Trends in Athlete Profile*)
* **Test Mapping**: `TST-REV-012` (*Cardio-Power Longitudinal Fitness Trends Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Math / Model Alterations**: Output-Error OE solver parameters and equations remain untouched.
2. **Boundary Continuity**: Rolling average at window boundaries matches historical full-series value.
3. **Thread Safety & Zero 1-Hz Loop I/O**: Reactive DB reads execute exclusively on `Dispatchers.IO`.
4. **Parent Human Gate Invariance**: Terminal completion remains reserved for human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* `AthleteProfileViewModel.kt`: Compute 30d rolling trend over complete history first, then slice both points and rolling points to `cardioRange`; collect `workoutRepository.allWorkouts` to trigger reactive reloads.
* `WorkoutSummariesDatabaseManager.java`: Select `w.timeStart AS time_start_str` and parse with `SimpleDateFormat` in local timezone (`TimeZone.getDefault()`).
* `CardioPowerTrendsCard.kt`: Add `1M` pill (`MetricsTimeRange.ONE_MONTH`), intermediate date ticks, and `pointerInput` touch scrubbing with vertical dashed cursor and date/value tooltip via `ChartGestureDisambiguator`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `HrvReadinessTrendCard.kt` (sister Fitness & Physiology hub card).
* **Reused components**: `CompactSelectorPill`, `ChartGestureDisambiguator`, `Card`, `Icon`.
* **Theme tokens**: 12dp card corner, 4dp/6dp spacing, `MaterialTheme.colorScheme.surfaceContainerLow`, `TTColor.Zone2`.
* **New one-off styles & justification**: None; strict adherence to existing hub cards.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Slicing Rolling Trend & Reactive Reload (`AthleteProfileViewModel.kt`)
* In `uiState` combine, compute `allRollingPts = computeRollingTrend(allPts, 30)` first; then slice both `allPts` and `allRollingPts` using `filterPointsByTimeRange`.
* In `init`, launch coroutine collecting `workoutRepository.allWorkouts` on `Dispatchers.IO` to invoke `loadCardioTrendSamples()` and reload load samples.

### Step 2: Local Timezone Parsing (`WorkoutSummariesDatabaseManager.java`)
* In `getCardioPowerDynamicsFitnessTrendSamples`, select `w.timeStart AS time_start_str` and parse with `SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)` in local timezone.

### Step 3: Hub UX Parity (`CardioPowerTrendsCard.kt`)
* Add `MetricsTimeRange.ONE_MONTH to stringResource(R.string.athlete_profile_range_1m)` to range options.
* Add intermediate X-axis date ticks (`dateStep = max(1, points.size / 4)`).
* Implement `pointerInput` with `ChartGestureDisambiguator` touch scrubbing, vertical dashed cursor, and tooltip.

### Step 4: Unit Test Verification & Live Audit
* Update `AthleteProfileViewModelCardioPowerTest.kt`, `WorkoutSummariesCardioPowerTrendTest.kt`, and `CardioPowerTrendsCardTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesCardioPowerTrendTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite and `verify_requirement_governance.py`.
* **Rollback**: Atomic git squash commit under parallel sprint lock; clean revert if needed.
