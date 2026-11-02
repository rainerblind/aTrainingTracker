# Stage 3: Implementation Plan - ATT-4849: GitHub-Style Yearly Activity Contribution Grids

**Ticket**: [ATT-4849](https://rainerblind.atlassian.net/browse/ATT-4849)  
**Sub-task**: [ATT-4977](https://rainerblind.atlassian.net/browse/ATT-4977) (`[Impl-Plan]`)  
**Parent Epic**: Standalone (`ATT-4849`)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-025`  
**Test Spec ID**: `TST-PER-031`  
**Branch**: `feature/ATT-4849`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
In `Periods` (`PeriodType.YEAR`), year cards display only macroscopic scalar metrics and a spatial overview map. We add compact GitHub-style $7 \times 53$ activity contribution grids (Mon..Sun rows $\times$ ISO weeks 1..53 columns, with Jan..Dec month headers) slaved to the master top header selectors (`selectedSports: Set<BSportType>` `[Bike, Run, Other]` and `selectedMetric: PeriodBarMetric`). Grids are separated per active sport with sport-calibrated quartile color ramps, powered by background daily aggregation, and offer interactive day-cell drill-downs.

---

## 2. Requirements & Traceability Mapping
* **REQ-PER-025.1 & .3**: $7 \times 53$ calendar matrix (Mon–Sun $\times$ ISO weeks 1..53) with month headers scoped strictly to `PeriodType.YEAR` (`TST-PER-031.1`).
* **REQ-PER-025.2**: Hoist master `selectedMetric` to `PeriodsViewModel` driving `PeriodBarGraph` and contribution grids (`TST-PER-031.4`).
* **REQ-PER-025.4**: Render dedicated grid per active `BSportType` enabled in `selectedSports` with $>0$ workouts in that year, in Rule 57 canonical order `[Bike, Run, Other]` (`TST-PER-031.3`).
* **REQ-PER-025.5**: Sport semantic color ramps (`BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`) across 5 levels (`Level 0` empty, `Level 1..4` positive volume quartiles) (`TST-PER-031.2`).
* **REQ-PER-025.6**: Interactive day-cell tap drill-down with Rule 36 formatters (`DistanceFormatter`, `TimeFormatter`, `AltitudeFormatter`) and navigation to day workouts via `onStartWorkoutList` (`TST-PER-031.5`).
* **REQ-PER-025.7**: In-memory aggregation off UI thread with zero `PeriodSummaries.db` schema migration and 100% 9-language localization parity (`TST-PER-031.6`, `TST-PER-031.7`).

---

## 3. System Invariants & Invariant Protections
1. **Zero SQLite Migration**: `PeriodSummaries.db` (v27) schema remains completely unchanged; daily contribution maps reside in-memory on `PeriodSummary`.
2. **UI Thread Decoupling**: Daily aggregation runs on `Dispatchers.Default` in `enrich()`; Compose renders memoized state with zero snapshot state writes during composition (Rule 66).
3. **Canonical Formatters & Units (Rule 36)**: Daily metrics use `DistanceFormatter`, `TimeFormatter`, and `AltitudeFormatter` respecting athlete `MyUnits` preference.
4. **Canonical Sport Order (Rule 57)**: Multi-sport grid cards strictly adhere to `[BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN]`.
5. **UI Consistency (Rule 23)**: Reference `PeriodSummaryCard.kt` & `PeriodBarGraph.kt`. Reuses `TTColor.BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`, `MaterialTheme.colorScheme.surfaceContainerHighest`, standard rounded corners (`4.dp` cells, `12.dp` cards).

---

## 4. Proposed Architectural Changes
* **PeriodData.kt**:
  * Add `@Immutable data class DailySportActivity(val date: LocalDate, val bSportType: BSportType, val count: Int, val durationSec: Long, val distanceMeters: Double, val ascentMeters: Long, val workoutIds: List<Long>, val workoutNames: List<String>)`.
  * Add `@Immutable data class YearlySportContribution(val sportType: BSportType, val year: Int, val dailyActivities: Map<LocalDate, DailySportActivity>, val maxDailyDurationSec: Long, val maxDailyDistanceMeters: Double, val maxDailyAscentMeters: Long)`.
  * Add `val yearlySportContributions: Map<BSportType, YearlySportContribution> = emptyMap()` to `PeriodSummary`.
* **PeriodsRepository.kt**:
  * In `enrich()`: When `periodType == PeriodType.YEAR`, group `groupWorkouts` by `(bSportType, localDateTime.toLocalDate())` on `Dispatchers.Default`, calculate per-sport daily stats and maximums, and store in `yearlySportContributions`.
  * In `filterPeriodSummary()`: Preserve `yearlySportContributions.filterKeys { it in sports }`.
* **PeriodsViewModel.kt & PeriodsTabsScreen.kt**:
  * In `PeriodsViewModel`: Hoist `_selectedMetric = MutableStateFlow(PeriodBarMetric.DURATION)`, expose `selectedMetric: StateFlow<PeriodBarMetric>` and `selectMetric(metric: PeriodBarMetric)`.
  * In `PeriodsTabsScreen.kt`: Slave `PeriodBarGraph` and `PeriodSummaryCard` to hoisted `selectedMetric`.
* **YearlyContributionGrid.kt (New)**:
  * Composable rendering 7 rows (Mon..Sun) $\times$ 53 ISO week columns with month labels above, sport color ramps, quartile level calculation, and interactive day selection banner with Rule 36 formatters and workout list launch callback.
* **PeriodSummaryCard.kt**:
  * For `periodType == PeriodType.YEAR`, render `YearlyContributionGrid` for each active sport in `summary.yearlySportContributions` in canonical order `[Bike, Run, Other]`.

---

## 5. Step-by-Step Implementation Sequence
* **Step 1: Domain Models & Background Aggregation (`PeriodData.kt`, `PeriodsRepository.kt`)**:
  * Add `DailySportActivity` and `YearlySportContribution` to `PeriodData.kt`.
  * Implement background aggregation in `enrich()` and reactive filtering in `filterPeriodSummary()`.
* **Step 2: Hoist Master Metric Selector (`PeriodsViewModel.kt`, `PeriodBarGraph.kt`, `PeriodsTabsScreen.kt`)**:
  * Add `selectedMetric` and `selectMetric()` to `PeriodsViewModel`. Connect `PeriodBarGraph` and `PeriodsTabsScreen`.
* **Step 3: Yearly Contribution Grid Component (`YearlyContributionGrid.kt`)**:
  * Implement $7 \times 53$ grid layout, month labels, quartile math, sport color ramps, selection state, and Rule 36 formatters with `@Preview`s.
* **Step 4: Integrate Grids into Year Cards (`PeriodSummaryCard.kt`, `PeriodList.kt`, `PeriodsScreen.kt`)**:
  * Render grids on `PeriodType.YEAR` cards in Rule 57 order; wire day tap navigation to `handleStartWorkoutList`.
* **Step 5: Unit & Contract Tests**:
  * Add `YearlyContributionGridTest.kt` verifying calendar mapping, quartile levels, Rule 57 order, and day selection.
  * Update `PeriodsViewModelTest.kt` verifying `selectedMetric` hoisting and reactive sport projection.
  * Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite and `verify_requirement_governance.py`.
* **Rollback**: Branch `feature/ATT-4849` allows clean rollback without impacting `develop` or active sprint.
