# Stage 2: Requirement & Test Specification - ATT-5195: Periods (Months & Weeks): embed daily activity intensity / mini-bar strip inside each sport's SportStatsRow (Option A)

**Ticket**: [ATT-5195](https://rainerblind.atlassian.net/browse/ATT-5195)  
**Sub-task**: [ATT-5240](https://rainerblind.atlassian.net/browse/ATT-5240) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PER-027` (*Embedded Daily Activity Intensity / Mini-Bar Strip for Months & Weeks in SportStatsRow*)  
**Test Spec ID**: `TST-PER-035`  
**Branch**: `feature/ATT-5195`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-PER-027)

### 1.1 Problem Statement & Rationale
`YearlyContributionGrid` embeds a 53-week activity grid inside `SportStatsRow` for `PeriodType.YEAR` (ATT-5076). However, for Months and Weeks, athletes see only aggregated summary totals. Embedding a compact horizontal mini-bar strip inside `SportStatsRow` for Months (days 1..28/29/30/31) and Weeks (7 days, Mon..Sun) provides visual activity distribution parity across all aggregate periods.

### 1.2 Functional & Architectural Requirements
1. **Temporal Scope & Placement (`REQ-PER-027.1`, `REQ-PER-027.7`)**: Embedded inside each sport's `SportStatsRow` below summary totals and above sub-sports/longest workout for `PeriodType.MONTH` and `PeriodType.WEEK`. `PeriodType.YEAR` and `PeriodType.DAY` remain unaffected.
2. **In-Memory Hydration (`REQ-PER-027.2`)**: `PeriodsRepository.enrich()` aggregates daily per-sport activities into `dailySportContributions: Map<BSportType, DailySportContribution>` without altering `PeriodSummaries.db` (v27) schema. `PeriodsViewModel.filterPeriodSummary()` filters contributions by `selectedSports`.
3. **Mini-Bar Layouts (`REQ-PER-027.3`)**: Week renders 7 columns (Mon..Sun) with localized day initials. Month renders 28..31 compact non-scrolling columns with milestone day labels (1, 10, 20, last day).
4. **Metric Slaving & Proportional Scaling (`REQ-PER-027.4`, `REQ-PER-027.5`)**: Heights scale proportionally ($H_{\text{bar}} = \text{clamp}(V_{\text{day}} / V_{\text{max}}, 0.15, 1.0) \times H_{\text{max}}$) slaved to active `PeriodBarMetric` (`DURATION`, `DISTANCE`, `ASCENT`), colored with `TTColor.BikeSportBarColor`, `RunSportBarColor`, `OtherSportBarColor`. Rest days render subtle muted baseline tracks.
5. **Interactive Drill-Down (`REQ-PER-027.6`)**: Tapping a day bar toggles an interactive summary banner with canonical formatting (`TimeFormatter`, `DistanceFormatter`, `AltitudeFormatter`), triggering `onDayCellClick` drill-down on active days.
6. **Localization Parity (`REQ-PER-027.8`)**: 100% 9-language parity across added string resources (`workout_periods__daily_activity_strip`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Month Embedding)**: *Given* a Month card, *When* rendered, *Then* each sport's `SportStatsRow` renders a 28..31-day activity strip colored in that sport's semantic palette.
* **Criterion 2 (Week Embedding)**: *Given* a Week card, *When* rendered, *Then* each sport's `SportStatsRow` renders a 7-day (Mon..Sun) activity strip.
* **Criterion 3 (Metric Slaving & Drill-Down)**: *Given* an active metric switch or day bar tap, *Then* bar heights reactively update and tapping displays the detail banner with navigation to that day's workouts.

---

## 2. Test Specification (TST-PER-035)

* **TST-PER-035.1 (Repository Hydration)**: In `PeriodsRepositoryTest.kt`, verify `enrich()` populates `dailySportContributions` for `WEEK` and `MONTH` without altering `YEAR` or `DAY`.
* **TST-PER-035.2 (Matrix Mapping)**: In `DailyActivityStripTest.kt`, verify 7-column Week and 28..31-column Month day mappings.
* **TST-PER-035.3 (Proportional Scaling & Palette)**: In `DailyActivityStripTest.kt`, verify bar heights scale proportionally ($0.15 \le \text{ratio} \le 1.0$) and match sport colors.
* **TST-PER-035.4 (Sport Filter Decoupling)**: In `PeriodsViewModelTest.kt`, verify `filterPeriodSummary()` filters `dailySportContributions` symmetrically.
* **TST-PER-035.5 (Interactive Selection & Drill-Down)**: In `DailyActivityStripTest.kt`, verify day bar selection banner and `onDayCellClick` invocation.
* **TST-PER-035.6 (SportStatsRow Placement)**: In `PeriodSummaryCardTest.kt`, verify seamless embedding between totals row and sub-sports divider.
* **TST-PER-035.7 (9-Language Parity)**: Verify `workout_periods__daily_activity_strip` across all 9 locales.
* **TST-PER-035.8 (Targeted Regression)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-035.1]` | Unit | `PeriodsRepository.enrich()` | `REQ-PER-027.2` | Specified |
| `[TST-PER-035.2]` | Unit | `DailyActivityStrip` mapping | `REQ-PER-027.3` | Specified |
| `[TST-PER-035.3]` | Unit | `DailyActivityStrip` scaling | `REQ-PER-027.4`, `REQ-PER-027.5` | Specified |
| `[TST-PER-035.4]` | Unit | `PeriodsViewModel.filterPeriodSummary` | `REQ-PER-027.2` | Specified |
| `[TST-PER-035.5]` | Unit | `DailyActivityStrip` interactions | `REQ-PER-027.6` | Specified |
| `[TST-PER-035.6]` | Unit | `SportStatsRow` embedding | `REQ-PER-027.1`, `REQ-PER-027.7` | Specified |
| `[TST-PER-035.7]` | Localization | `TranslationParityTest` | `REQ-PER-027.8` | Specified |
| `[TST-PER-035.8]` | Regression | Tier 1 Period List Suite | `REQ-PRO-001` | Specified |
