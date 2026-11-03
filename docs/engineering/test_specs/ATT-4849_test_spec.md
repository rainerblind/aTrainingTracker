# Stage 2: Requirement & Test Specification - ATT-4849: GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years)

**Ticket**: [ATT-4849](https://rainerblind.atlassian.net/browse/ATT-4849)  
**Sub-task**: [ATT-4976](https://rainerblind.atlassian.net/browse/ATT-4976) (`[Req & Test Spec]`)  
**Parent Epic**: Standalone (`ATT-4849`)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-025` (*GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years)*)  
**Test Spec ID**: `TST-PER-031`  
**Branch**: `feature/ATT-4849`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Requirement Specification (REQ-PER-025)

### 1.1 Scope & Functional Criteria
* `PeriodSummaryCard` SHALL render a compact GitHub-style $7 \times 53$ activity contribution grid exclusively for `PeriodType.YEAR` summaries.
* The active volume metric for day cells and quartile intensity SHALL be slaved to the master metric selector (`PeriodBarMetric`: `DURATION`, `DISTANCE`, `ASCENT`), hoisted to `PeriodsViewModel`.
* Each grid SHALL render a $7 \times 53$ matrix (rows: Mon–Sun, index 0..6; columns: ISO weeks 1..53) with column month headers (`Jan..Dec`).
* Dedicated grids SHALL be rendered per active `BSportType` enabled in `selectedSports` that contains $\ge 1$ workout in that calendar year, ordered by Rule 57 canonical order (`[Bike, Run, Other]`).
* Cell shading SHALL follow sport semantic palettes (`TTColor.BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`) across 5 levels: Level 0 (no activity) and Levels 1..4 (positive volume quartiles $Q_1..Q_4$ calibrated per sport).
* Tapping an active day cell SHALL highlight the date, display daily totals formatted via Rule 36 canonical formatters (`DistanceFormatter`, `TimeFormatter`, `AltitudeFormatter`), and allow drill-down navigation via `onStartWorkoutList`.
* 100% 9-language localization parity SHALL be maintained (`workout_periods__yearly_activity_grid`, `workout_periods__no_activity`). Storing daily contribution structures SHALL be in-memory in `PeriodSummary` without altering `PeriodSummaries.db` (v27) schema.

### 1.2 Acceptance Criteria (Given-When-Then)
* *Given* a Year period summary with Bike and Run workouts, *When* viewed in `Periods` with Distance metric selected, *Then* dedicated Bike and Run $7 \times 53$ grids render with distance-based quartile intensities.
* *Given* the sport filter header toggles Bike off, *When* observing the Year card, *Then* the Bike contribution grid is hidden reactively while the Run grid remains visible.
* *Given* an active day cell is tapped, *When* interacting with the grid, *Then* the cell is highlighted with formatted daily metrics and navigation to that day's workouts is triggered.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PER-025`, refining `REQ-PER-018` (*PeriodBarGraph*) and `REQ-PER-022` (*Sport Filtering*). Targets `YearlyContributionGrid.kt`, `PeriodSummaryCard.kt`, `PeriodsViewModel.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-44.2 (`ATT-4849`).
* **Root Reason for Existing Formulation**: Year period cards historically rendered only high-level cumulative numbers and mini-maps, leaving athletes without seasonal consistency, volume distribution, or streak insights.
* **Preservation of Core Invariants**: In-memory aggregation on `Dispatchers.Default`, zero SQLite migrations, Rule 36 canonical formatting, Rule 57 sport ordering `[Bike, Run, Other]`, and 100% clean-room test pass rate are preserved.

---

## 2. Test Specification (TST-PER-031)

* **`TST-PER-031.1` (Daily Aggregation & Calendar Matrix Mapping)**: `YearlyContributionGridTest.kt` verifies daily grouping, 7-row by 53-column matrix coordinate mapping (Mon–Sun $\times$ ISO weeks 1..53) for standard and leap years.
* **`TST-PER-031.2` (Quartile Shading & Level Assignment)**: `YearlyContributionGridTest.kt` verifies positive volumes partitioned into quartiles $Q_1..Q_4$ (Levels 1..4; Level 0 empty) across `DURATION`, `DISTANCE`, `ASCENT`.
* **`TST-PER-031.3` (Multi-Sport Separation & Rule 57 Ordering)**: `YearlyContributionGridTest.kt` verifies distinct grids per `BSportType` with sport colors (`BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`) ordered `[Bike, Run, Other]`.
* **`TST-PER-031.4` (Master Selector Slaving & Reactivity)**: `PeriodsViewModelTest.kt` verifies hoisting `selectedMetric: StateFlow<PeriodBarMetric>` and reactive filtering of contribution grids when `selectedSports` or `selectedMetric` changes.
* **`TST-PER-031.5` (Interactive Day Tap Drill-Down)**: `YearlyContributionGridTest.kt` verifies tapping a cell dispatches exact day timestamp range and sport to `onDayClick`.
* **`TST-PER-031.6` (9-Language Parity Audit)**: `TranslationParityTest.kt` verifies all added string resources match across all 9 locales.
* **`TST-PER-031.7` (Targeted Regression Gate)**: Targeted unit tests in `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*` pass 100% clean-room.

---

## 3. Traceability Matrix

| Test ID | Scope | Target Test Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-031.1` | Unit | `YearlyContributionGridTest.testCalendarMatrixMapping` | `REQ-PER-025.1`, `REQ-PER-025.3` | Specified |
| `TST-PER-031.2` | Unit | `YearlyContributionGridTest.testQuartileCalculationAndLevels` | `REQ-PER-025.5` | Specified |
| `TST-PER-031.3` | Contract | `YearlyContributionGridTest.testMultiSportSeparationAndRule57Order` | `REQ-PER-025.4` | Specified |
| `TST-PER-031.4` | ViewModel | `PeriodsViewModelTest.testMasterMetricAndSportFilterReactivity` | `REQ-PER-025.2` | Specified |
| `TST-PER-031.5` | Interaction | `YearlyContributionGridTest.testInteractiveDayCellTap` | `REQ-PER-025.6` | Specified |
| `TST-PER-031.6` | Localization | `TranslationParityTest.testAllStringsLocalized` | `REQ-PER-025.7` | Specified |
| `TST-PER-031.7` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` | `REQ-PRO-001` | Specified |
