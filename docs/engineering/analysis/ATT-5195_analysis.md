# Stage 1 Analysis: ATT-5195 - Periods (Months & Weeks): embed daily activity intensity / mini-bar strip inside each sport's SportStatsRow (Option A)

**Ticket**: [ATT-5195](https://rainerblind.atlassian.net/browse/ATT-5195)  
**Sub-task**: [ATT-5239](https://rainerblind.atlassian.net/browse/ATT-5239) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5195`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Statement & Motivation

In `PeriodSummaryCard.kt`, `SportStatsRow` embeds `YearlyContributionGrid` for `PeriodType.YEAR` (ATT-5076), providing an intuitive, embedded view of daily workout volume across the year.
However, for **Months (`PeriodType.MONTH`)** and **Weeks (`PeriodType.WEEK`)**, no matching embedded daily activity visualization exists inside `SportStatsRow`. Athletes viewing Month or Week summary cards see aggregate sport totals (time, distance, ascent) and longest workouts, but lack a day-by-day training distribution strip showing rest days, workout frequency, and metric intensity per sport.
The objective of ATT-5195 (Option A) is to embed a compact daily activity intensity / mini-bar strip inside each sport's `SportStatsRow` for Months (days 1..28/29/30/31) and Weeks (7 days: Mon..Sun), matching the semantic sport palette and interactive drill-down navigation established in `YearlyContributionGrid`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Data Layer Gap**: In `PeriodsRepository.kt:enrich()`, daily activities and maximum metrics are aggregated into `yearlySportContributions` strictly when `summary.periodType == PeriodType.YEAR` (line 1051). For `PeriodType.WEEK` and `PeriodType.MONTH`, `groupWorkouts` are passed to `enrich()`, but daily per-sport activity maps are discarded after aggregating totals.
* **Domain Model Gap**: `PeriodSummary` contains `yearlySportContributions: Map<BSportType, YearlySportContribution>`, but lacks a model for week/month daily breakdowns (`dailySportContributions: Map<BSportType, DailySportContribution>`).
* **UI Presentation Gap**: `SportStatsRow` conditionally renders only `YearlyContributionGrid` when `yearlyContribution != null`. No component exists for a compact, non-scrolling horizontal daily mini-bar strip suitable for 7-day (Week) and 28..31-day (Month) layouts.
* **Navigation Parity**: `YearlyContributionGrid` supports day cell selection and drill-down navigation via `onDayClick`. A matching daily activity strip must expose the same selection highlight and trigger `onDayCellClick` to navigate to `WorkoutSummariesListFragment`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `DailySportContribution` data model and hydrate `dailySportContributions` in `PeriodsRepository.kt:enrich()` for `PeriodType.WEEK` and `PeriodType.MONTH`.
  2. Preserve `dailySportContributions` across sport filtering in `PeriodsViewModel.kt`.
  3. Create `DailyActivityStrip.kt` composable rendering a compact horizontal mini-bar chart for Week (7 days, Mon..Sun) and Month (1..28/29/30/31 days).
  4. Slave mini-bar fill heights and colors to `PeriodBarMetric` (`DURATION`, `DISTANCE`, `ASCENT`) and semantic sport tokens (`TTColor.BikeSportBarColor`, `TTColor.RunSportBarColor`, `TTColor.OtherSportBarColor`).
  5. Embed `DailyActivityStrip` inside `SportStatsRow` in `PeriodSummaryCard.kt` below the header and above sub-sports / longest workout.
  6. Implement interactive day selection detail banner with drill-down to `WorkoutSummariesListFragment` on active days.
  7. Add headless unit tests in `DailyActivityStripTest.kt` and update living documentation (`REQ-PER-026`, `TST-PER-032`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero changes to `PeriodSummaries.db` (v27) schema — all daily activity collections are in-memory.
  * Do not modify `YearlyContributionGrid.kt` or `PeriodType.YEAR` matrix mapping ($7 \times 53$).
  * Do not alter top-level overview chart `PeriodBarGraph.kt` or day-level cards (`PeriodType.DAY`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Extends `REQ-PER-025` (*Activity Contribution Grids per BSportType*, ATT-4849, ATT-5076) by introducing `REQ-PER-026` (*Daily Activity Mini-Bar Strip for Months & Weeks in SportStatsRow*).
* **Historical Origin & Commit Trace**: In Sprint 2026-45.1 (`ATT-5076`), `YearlyContributionGrid` was relocated inside `SportStatsRow` (Option A). The Sprint 2026-45.1 Review identified the need for visual parity across all aggregate period cards (Months and Weeks).
* **Root Reason for Existing Formulation**: Month and Week cards originally displayed only tabular metric rows to keep cards compact before component nesting was refined.
* **Preservation of Core Invariants**:
  * Rule 57 canonical sport ordering (`[Bike, Run, Other]`).
  * Rule 36 pure calculation decoupling for 100% headless JVM testing without Android mocks.
  * Zero database schema mutation.

---

## 5. Architectural Strategy & High-Level Solution

1. **Domain Data Structure (`DailySportContribution`)**:
   * Encapsulate `sportType: BSportType`, `dailyActivities: Map<LocalDate, DailySportActivity>`, and daily peak values `maxDailyDurationSec`, `maxDailyDistanceMeters`, `maxDailyAscentMeters`.
   * Add `dailySportContributions: Map<BSportType, DailySportContribution> = emptyMap()` to `PeriodSummary`.
2. **Repository Enrichment (`PeriodsRepository.kt:enrich`)**:
   * Compute `dailySportContributions` for all active sports when `periodType in (WEEK, MONTH)` using `groupWorkouts`.
3. **Composable `DailyActivityStrip.kt`**:
   * Pure Compose component receiving `startDate`, `endDate`, `sportType`, `dailyContribution`, `activeMetric`, and `onDayClick`.
   * Computes proportional bar heights $H_{\text{bar}} = \text{clamp}(V_{\text{day}} / V_{\text{max}}, 0.15, 1.0) \times H_{\text{max}}$ for active days, with subtle muted baseline tracks for rest days.
   * Month layout displays compact bars across 28..31 columns with milestone day labels (1, 10, 20, end). Week layout displays 7 columns with localized day-of-week initials (Mon..Sun).
   * Interactive banner on day tap matches `YearlyContributionGrid`.
4. **Integration (`PeriodSummaryCard.kt`)**:
   * Pass `dailyContribution` to `SportStatsRow` for `WEEK` and `MONTH`. Render `DailyActivityStrip` right below the main sport row.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Complete backward compatibility: `PeriodType.YEAR` and `PeriodType.DAY` rendering remains completely unaffected.
  2. Performance & Smooth Scrolling: Daily aggregations computed asynchronously during repository enrichment off the main thread (`Dispatchers.Default`); zero database I/O overhead.
  3. Strict localization parity (9 languages) for all formatted labels and accessibility semantics.
* **Side-Effect Audit (Rule 68)**:
  * `PeriodsViewModel.kt`: Updated `filterPeriodSummary` to filter `dailySportContributions`.
  * `PeriodSummaryCard.kt`: Direct caller of `DailyActivityStrip` inside `SportStatsRow`.
* **Risk Rating**: **LOW** — Pure UI component addition and existing in-memory repository enrichment pipeline; zero database schema or background service risks.
