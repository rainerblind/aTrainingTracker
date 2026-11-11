# Stage 2: Requirement & Test Specification - ATT-6724: 30d Cardio-Power Rolling Trend, Reactive Refresh & Hub UX Parity

**Ticket**: [ATT-6724](https://rainerblind.atlassian.net/browse/ATT-6724)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-REV-011` (*Cardio-Power Longitudinal Fitness Trends in Athlete Profile*)  
**Test Spec ID**: `TST-REV-012`  
**Branch**: `feature/ATT-6724`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-REV-011)

### 1.1 Problem Statement & Rationale
Left-edge boundary truncation in longitudinal cardio-power trend lines must be eliminated by computing moving averages across the full historical series prior to time-range slicing. The card must refresh reactively on workout lifecycle events, parse timestamps in local device timezone, and provide full feature parity with the Fitness & Physiology hub (`1M` pill, intermediate date ticks, touch scrubbing).

### 1.2 Functional & Architectural Requirements
1. *Full-History Moving Average*: `AthleteProfileViewModel.kt` SHALL compute `CardioPowerTrendsPlotMath.computeRollingTrend(allPts, windowDays = 30)` over the complete series before applying `filterPointsByTimeRange`.
2. *Reactive Lifecycle Refresh*: `AthleteProfileViewModel.init` SHALL collect `workoutRepository.allWorkouts` on `Dispatchers.IO` to dynamically trigger `loadCardioTrendSamples()` and `loadTrainingLoadSamples()`.
3. *Local Timezone Ingestion*: `WorkoutSummariesDatabaseManager.java` SHALL query `w.timeStart AS time_start_str` and parse timestamps in Java via `SimpleDateFormat` in the device local timezone (`TimeZone.getDefault()`).
4. *Hub UX Parity*: `CardioPowerTrendsCard.kt` SHALL provide `1M` range pill (`MetricsTimeRange.ONE_MONTH`), intermediate X-axis date ticks, and horizontal touch scrubbing with vertical dashed cursor and tooltip via `ChartGestureDisambiguator`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* switching between `1M`, `3M`, `6M`, `1Y`, and `All`,
  * *When* inspecting points at window boundaries,
  * *Then* 30-day rolling average values remain identical because moving averages are computed prior to range slicing.
* *Given* workout creation, import, backfill, or deletion,
  * *When* `workoutRepository.allWorkouts` emits updates,
  * *Then* `CardioPowerTrendsCard` refreshes trend points and rolling curves reactively without requiring app recreation.
* *Given* horizontal drag gesture over `CardioPowerTrendCanvas`,
  * *When* scrubbed horizontally,
  * *Then* `ChartGestureDisambiguator` tracks horizontal scrubbing showing vertical cursor line and date/value tooltip while preserving parent vertical scrolling.

### 1.4 System Invariants
* Zero SQLite query on 1-Hz tracking loop; zero regression in existing athlete profile metrics or PMC calculations.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-REV-011` (*Cardio-Power Longitudinal Fitness Trends in Athlete Profile*), targeting `AthleteProfileViewModel.kt`, `WorkoutSummariesDatabaseManager.java`, and `CardioPowerTrendsCard.kt`.
* `Historical Origin & Commit Trace`: Added in `ATT-3906` (Sprint 2026-46.1).
* `Root Reason for Existing Formulation`: Slicing by time range before rolling trend was implemented for simple in-memory bounding, and WorkoutSummaries `strftime('%s', ...)` assumed standard UTC timestamps.
* `Preservation of Core Invariants`: 30-day window moving average, minimum workout duration filter ($\ge 20\text{ min}$), $R^2 \ge 0.50$ fitness qualification threshold, and vertical parent scrolling remain completely preserved.

---

## 2. Test Specification (TST-REV-012)

### Test Case 1: Pre-Slicing Rolling Trend Continuity (`[TST-REV-012.2a]`)
* **Scope**: ViewModel Unit Test (`AthleteProfileViewModelCardioPowerTest.kt`)
* **Preconditions**: 4 qualified workouts (Day -40, Day -25, Day -10, Day 0).
* **Action**: Toggle between `ALL` and `MetricsTimeRange.ONE_MONTH` (30 days).
* **Expected Result**: In `ONE_MONTH`, the point at Day -25 includes Day -40 in its 30d moving average, matching its rolling value under `ALL`.

### Test Case 2: Reactive Workflow Reload (`[TST-REV-012.2b]`)
* **Scope**: ViewModel Unit Test (`AthleteProfileViewModelCardioPowerTest.kt`)
* **Preconditions**: `AthleteProfileViewModel` initialized with mocked `workoutRepository.allWorkouts`.
* **Action**: Emit a new workout list from `allWorkouts`.
* **Expected Result**: `loadCardioTrendSamples()` executes reactively and `uiState` emits updated trend points.

### Test Case 3: Local Timezone Timestamp Query (`[TST-REV-012.1]`)
* **Scope**: Database Unit Test (`WorkoutSummariesCardioPowerTrendTest.kt`)
* **Preconditions**: Summary row with `time_start = "2026-06-15 14:30:00"`.
* **Action**: Invoke `getCardioPowerDynamicsFitnessTrendSamples(0.50)`.
* **Expected Result**: Returned `startMs` matches `SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)` in local timezone.

### Test Case 4: UI Parity, 1M Pill & Touch Scrubbing (`[TST-REV-012.4]`)
* **Scope**: Compose Contract Test (`CardioPowerTrendsCardTest.kt`)
* **Action**: Verify `1M` pill rendering and click callback; verify touch scrubbing state changes.
* **Expected Result**: `onSelectTimeRange(MetricsTimeRange.ONE_MONTH)` is invoked; touch scrubbing displays active cursor.

### Test Case 5: 9-Locale Localization Audit (`[TST-REV-012.5]`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Expected Result**: `athlete_profile_range_1m` present in all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Test Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-012.1]` | DB Unit | `WorkoutSummariesCardioPowerTrendTest` | `REQ-REV-011.1` | Specified |
| `[TST-REV-012.2a]` | ViewModel | `AthleteProfileViewModelCardioPowerTest.testRollingTrend_preservesBoundaryPreSlicing` | `REQ-REV-011.3a` | Specified |
| `[TST-REV-012.2b]` | ViewModel | `AthleteProfileViewModelCardioPowerTest.testReactiveRefresh_onWorkoutsChange` | `REQ-REV-011.4a` | Specified |
| `[TST-REV-012.4]` | UI Compose | `CardioPowerTrendsCardTest` | `REQ-REV-011.2c`, `REQ-REV-011.3d` | Specified |
| `[TST-REV-012.5]` | Localization | `TranslationParityTest` | `REQ-REV-011.2c`, `REQ-REV-011.4c` | Specified |
