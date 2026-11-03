# Stage 3: Implementation Plan - ATT-5195: Daily Activity Strip for Months & Weeks (Option A)

**Ticket**: [ATT-5195](https://rainerblind.atlassian.net/browse/ATT-5195)  
**Sub-task**: [ATT-5241](https://rainerblind.atlassian.net/browse/ATT-5241) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PER-027` (`REQ-PER-027.1` .. `REQ-PER-027.8`)  
**Test Mapping**: `TST-PER-035` (`TST-PER-035.1` .. `TST-PER-035.8`)  
**Branch**: `feature/ATT-5195`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Description & Background
In `PeriodSummaryCard.kt`, `SportStatsRow` embeds `YearlyContributionGrid` for `PeriodType.YEAR` (ATT-5076), but lacks daily activity distribution for **Months (`PeriodType.MONTH`)** and **Weeks (`PeriodType.WEEK`)**. This plan embeds a compact daily activity mini-bar strip inside each sport's `SportStatsRow` (Option A), providing visual activity distribution, rest day awareness, and interactive drill-down navigation matching `YearlyContributionGrid`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-027` (*Embedded Daily Activity Intensity / Mini-Bar Strip for Months & Weeks*)
* **Test Mapping**: `TST-PER-035` (`TST-PER-035.1` through `TST-PER-035.8` in `DailyActivityStripTest.kt`, `PeriodsViewModelTest.kt`, and `PeriodsRepositoryTest.kt`).
* **Chesterton's Fence Audit**: Preserves existing `YearlyContributionGrid` ($7 \times 53$ matrix), `PeriodType.YEAR`/`DAY` cards, Rule 57 canonical sport ordering (`[Bike, Run, Other]`), and zero database schema changes (`PeriodSummaries.db` v27).

---

## 3. System Invariants & Preserved Behavior
1. **Section 1.10 Compliance**: No Compose snapshot state writes during composition. `selectedDate` toggle is strictly inside `clickable` lambdas.
2. **Section 5.4 Compliance**: Zero source-text grepping in UI tests. All unit tests assert pure calculations, domain data structures, and ViewModel states.
3. **Rule 4 Code Lock**: Zero production code edits before Gate 3 passes `check-gate`.
4. **In-Memory Hydration**: `dailySportContributions` is hydrated in `PeriodsRepository.enrich()` on background dispatcher without altering DB schema.

---

## 4. Proposed Architectural Changes

### Component 1: Domain Models (`PeriodData.kt`)
* Add `data class DailySportContribution(val sportType: BSportType, val dailyActivities: Map<LocalDate, DailySportActivity> = emptyMap(), val maxDailyDurationSec: Long = 0L, val maxDailyDistanceMeters: Double = 0.0, val maxDailyAscentMeters: Long = 0L)`.
* Add `val dailySportContributions: Map<BSportType, DailySportContribution> = emptyMap()` to `PeriodSummary`.

### Component 2: Repository & ViewModel Hydration (`PeriodsRepository.kt`, `PeriodsViewModel.kt`)
* In `PeriodsRepository.kt:enrich()`: When `summary.periodType in (WEEK, MONTH)` and `groupWorkouts.isNotEmpty()`, aggregate per-sport daily activities and max daily metric values into `dailySportContributions`.
* In `PeriodsViewModel.kt:filterPeriodSummary()`: Filter `dailySportContributions.filterKeys { it in sports }`.

### Component 3: UI Composable (`DailyActivityStrip.kt`)
* Implement pure mathematical helpers: `calculateDailyBarFraction(value: Double, maxValue: Double): Float`, `extractDailyMetricValue(activity: DailySportActivity?, metric: PeriodBarMetric): Double`.
* Implement `DailyActivityStrip`:
  * Week (7 cols, Mon..Sun): Localized day initial, mini-bar ($H_{\text{bar}} \in [0.15, 1.0] \times 24\text{ dp}$), day number.
  * Month (28..31 cols): Proportional mini-bars with milestone day labels (1, 10, 20, end).
  * Rest days: 2 dp muted baseline track (`surfaceContainerHighest`).
  * Colors: Semantic palette (`TTColor.BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`).
  * Tap & Banner: Toggles `selectedDate`, renders formatted detail banner, invokes `onDayClick`.
  * Add `@Preview` for Week and Month in Light/Dark modes.

### Component 4: Card Integration (`PeriodSummaryCard.kt`)
* In `SportStatsRow`: Add `dailyContribution: DailySportContribution? = null`, `periodType`, `startDate`, `endDate`.
* When `dailyContribution != null`, render `DailyActivityStrip` below the main sport row and above `HorizontalDivider`.

### UI Consistency (Rule 23)
* **Reference**: `SportStatsRow` & `YearlyContributionGrid` in `PeriodSummaryCard.kt`.
* **Tokens**: `TTColor.BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`, `MaterialTheme.colorScheme.surfaceContainerHighest`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Model & Repository Hydration
* Files: `PeriodData.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`.
* Changes: Define `DailySportContribution`, hydrate in `enrich()`, filter in `filterPeriodSummary()`.

### Step 2: Implement DailyActivityStrip Composable
* Files: `DailyActivityStrip.kt`.
* Changes: Pure calculation helpers, Week & Month bar layouts, tap banner, Compose `@Preview`s.

### Step 3: Embed in SportStatsRow & PeriodSummaryCard
* Files: `PeriodSummaryCard.kt`.
* Changes: Wire `summary.dailySportContributions[bSportType]` to `SportStatsRow`.

### Step 4: Unit Test Suite
* Files: `DailyActivityStripTest.kt`, `PeriodsViewModelTest.kt`, `PeriodsAggregationTest.kt`.
* Commands: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --max-workers=2`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run unit tests, `verify_requirement_governance.py`, and 9-language translation test.
* **Rollback**: Clean git branch revert via `git restore app/ docs/`.
