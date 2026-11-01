# Stage 1 Analysis: ATT-4849 — GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years)

**Ticket**: [ATT-4849](https://rainerblind.atlassian.net/browse/ATT-4849)  
**Sub-task**: [ATT-4974](https://rainerblind.atlassian.net/browse/ATT-4974) (`[Analysis]`)  
**Parent Epic**: Standalone (`ATT-4849`)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4849`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
In `Periods` (`PeriodType.YEAR`), each Year card summarizes an entire 365/366-day season, but displays only high-level scalar sums (`totalDistance`, `totalDurationSec`, `totalAscent`) and a spatial overview map. Athletes cannot see seasonal consistency, volume distributions, rest streaks, or training blocks across the 12 months. A GitHub-style $7 \times 53$ (Mon–Sun rows $\times$ ISO weeks 1..53 columns, with Jan..Dec column header labels) Yearly Activity Contribution Grid provides an instant, compact seasonal heatmap without disrupting the existing card layout.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification (ATT-3534)
* **Forensic Gap Analysis**:
  * `PeriodSummary` for `PeriodType.YEAR` aggregates only macroscopic scalar sums per sport, lacking daily resolution.
  * In `PeriodsRepository.kt:971-1057`, `enrich()` already receives `groupWorkouts` containing all `WorkoutData` instances for that calendar year on `Dispatchers.Default`. Daily aggregation can be computed in-memory without database queries.
* **Independent Hypothesis Verification (ATT-3534)**:
  * *Master Selectors Slaving*: In `PeriodBarGraph.kt:336`, `selectedMetric` is currently local `rememberSaveable` state. Hoisting `selectedMetric: StateFlow<PeriodBarMetric>` to `PeriodsViewModel` makes it the single master authority for both `PeriodBarGraph` and yearly contribution grids.
  * *Zero Database Schema Impact*: Storing daily contribution maps in `PeriodSummary` in-memory avoids bumping `PeriodSummaries.db` (v27) schema, preserving fast startup and zero-risk DB stability.
  * *Multi-Sport Separation & Quartile Calibration*: 10 km running and 10 km cycling differ fundamentally in physiological strain. Rendering dedicated $7 \times 53$ grids per active `BSportType` enabled in `selectedSports` allows sport-specific `TTColor` ramps (Bike Blue, Run Coral, Other Teal) and sport-calibrated positive quartiles (Level 1..4; Level 0 = empty).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Scope grids strictly to `PeriodType.YEAR` inside `PeriodsTabsScreen` / `PeriodSummaryCard`.
  2. Hoist `selectedMetric` to `PeriodsViewModel` and slave both `PeriodBarGraph` and yearly grids to it (`Distance`, `Time`, `Ascent/Elevation`).
  3. Render one compact $7 \times 53$ grid per active `BSportType` enabled in `selectedSports` that has $\ge 1$ workout in that calendar year, following Rule 57 canonical order `[Bike, Run, Other]`.
  4. Implement sport semantic `TTColor` ramps: Level 0 (empty background) and Levels 1..4 (quartiles of positive day volume).
  5. Interactive tap drill-down: tapping a day cell highlights the date, displays formatted daily totals via Rule 36 canonical formatters (`DistanceFormatter`, `TimeFormatter`, `AltitudeFormatter`), and allows navigating to the day's workouts in `WorkoutSummariesListFragment`.
* **Out-of-Scope Non-Goals**:
  1. No changes to `PeriodSummaries.db` (v27) or `WorkoutSummaries.db` SQLite schemas.
  2. No contribution grids in Day, Week, or Month period views.
  3. No modification of raw sensor tracks or live cockpit recording engines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: Net-new requirement `REQ-PER-025` (*GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years)*) refining `REQ-PER-018` (PeriodBarGraph) and `REQ-PER-022` (Sport Filtering). Verified by `TST-PER-031`.
* **Chesterton's Fence**: `PeriodSummaryCard` was designed as a compact analytical summary. Adding the contribution grid preserves existing sport rows, mini-maps, and fast-scrolling performance by performing lightweight daily aggregation in background coroutines.

---

## 5. Architectural Strategy & High-Level Solution
* **Data Model (`PeriodData.kt`)**: Add `YearlySportContribution` domain models to `PeriodSummary` containing `dailyActivities: Map<LocalDate, DailySportActivity>` and precomputed quartile thresholds for the active metrics.
* **Repository & ViewModel (`PeriodsRepository.kt`, `PeriodsViewModel.kt`)**:
  * In `PeriodsRepository.enrich()`, aggregate `groupWorkouts` into daily sport buckets for `PeriodType.YEAR` on `Dispatchers.Default`.
  * In `PeriodsViewModel`, expose `selectedMetric: StateFlow<PeriodBarMetric>` and `selectMetric(metric)`. Filter `YearlySportContribution` reactively via `selectedSports`.
* **UI Components (`YearlyContributionGrid.kt`, `PeriodSummaryCard.kt`, `PeriodsTabsScreen.kt`)**:
  * Build `YearlyContributionGrid`: $7 \times 53$ matrix (Mon..Sun rows, ISO weeks 1..53 columns, Jan..Dec month headers), sport `TTColor` ramp, interactive day cell selection.
  * Integrate into `PeriodSummaryCard` when `periodType == PeriodType.YEAR`.
  * Connect day tap drill-down to `onStartWorkoutList(dayStartS, dayEndS, sport, workoutId)`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero SQLite migrations; 60 fps `LazyColumn` scrolling; Rule 36 unit/formatter parity; Rule 57 canonical sport ordering; non-empty sport filter guard; 100% clean-room test pass rate.
* **Risk Rating**: **LOW** — Pure presentation and in-memory projection layer; zero database schema or background recording service impact.
