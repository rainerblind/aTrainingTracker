# Stage 1 Analysis: ATT-5076 - Periods (Years): Embed Yearly Activity Contribution Grid inside each sport's SportStatsRow box below main totals

**Ticket**: [ATT-5076](https://rainerblind.atlassian.net/browse/ATT-5076)  
**Sub-task**: [[ATT-5149]](https://rainerblind.atlassian.net/browse/ATT-5149) (`[Analysis]`)  
**Parent Epic**: Standalone (`ATT-5076`)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5076`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
In `PeriodSummaryCard.kt` (`PeriodType.YEAR`), the GitHub-style $7 \times 53$ activity contribution grids (`YearlyContributionGrid`) are currently rendered in a standalone section at the bottom of the card below all `SportStatsRow` boxes. This duplicates the sport icon, sport name, and container card styling under a separate *"Yearly Activity"* header. Moving each sport's contribution grid directly inside that sport's `SportStatsRow` container creates a unified, cohesive card layout and eliminates visual redundancy.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Root Cause**:
  - `PeriodSummaryCard.kt:224-253` isolated `YearlyContributionGrid` rendering into a trailing loop below `SportStatsRow` with a separate section title (`workout_periods__yearly_activity_grid`).
  - `YearlyContributionGrid.kt:168-202` hardcoded an outer `Card` container (`surfaceContainerLow`) and internal sport icon/title header, causing card-in-card nesting and redundant sport identification when placed inside `SportStatsRow`.
- **Independent Peer Review & Verification**:
  - Audited `PeriodSummaryCard.kt:208-253`: confirmed separate loops for `SportStatsRow` and `yearlySportContributions`.
  - Audited `SportStatsRow:370-426`: confirmed that placing `YearlyContributionGrid` right below the main summary header (line 415) and above `HorizontalDivider` (line 421) perfectly satisfies Placement 1 in the Acceptance Criteria.
  - Audited `YearlyContributionGrid.kt`: verified adding `showCard: Boolean = true` and `showSportHeader: Boolean = true` allows embedded rendering without outer card borders or duplicate sport headers.
  - Audited string resources: `workout_periods__yearly_activity_grid` is used only by the trailing section title in `PeriodSummaryCard.kt` and its localization test.
  - Audited tests: `YearlyContributionGridTest.kt` (calendar math & quartiles) and `PeriodsViewModelTest.kt` (sport filtering) remain unaffected.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Embed `YearlyContributionGrid` into `SportStatsRow` in `PeriodSummaryCard.kt` when `summary.periodType == PeriodType.YEAR`.
  2. Place the grid below the main sport header row and above the sub-sports breakdown (`CompactMetricRow`) and longest workout highlight.
  3. Support embedded mode in `YearlyContributionGrid.kt` by omitting the outer `Card` container and duplicate sport header.
  4. Remove the separate *"Yearly Activity"* section header and trailing grid list from `PeriodSummaryCard.kt`.
  5. Verify unit tests and 9-language translation parity.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying calendar matrix mapping ($7 \times 53$), quartile levels, or day cell sizing in `YearlyContributionGrid.kt`.
  - Altering `PeriodSummaries.db` schema (v27) or ViewModel calculation logic.
  - Changing day-cell tap drill-down navigation.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Modifies `REQ-PER-025` (*GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years)*) and `TST-PER-031` in `docs/requirements/req_aftermath_periods.md` and `docs/tests/tst_aftermath_periods.md`. Targets `PeriodSummaryCard.kt`, `YearlyContributionGrid.kt`.
- **Historical Origin & Commit Trace**: Introduced in Sprint 2026-44.2 via commit `285d2470` (`ATT-4849`).
- **Root Reason for Existing Formulation**: In ATT-4849, contribution grids were placed at the bottom of the card to avoid altering `SportStatsRow` before gathering user feedback on visual balance.
- **Preservation of Core Invariants**:
  - $7 \times 53$ calendar layout, quartile shading, and day-cell tap drill-down remain 100% preserved.
  - Non-year periods (`PeriodType.WEEK`, `PeriodType.MONTH`) remain unchanged.
  - Card visual hierarchy aligns with Material 3 guidelines (eliminating nested card borders).

---

## 5. Architectural Strategy & High-Level Solution
- **`YearlyContributionGrid.kt`**: Add `showCard: Boolean = true` and `showSportHeader: Boolean = true` parameters. When `showCard = false`, omit outer `Card`. When `showSportHeader = false`, omit sport icon/name and right-align the legend.
- **`PeriodSummaryCard.kt`**: Pass `yearlyContribution`, `activeMetric`, and `onDayClick` to `SportStatsRow`. Inside `SportStatsRow`, render `YearlyContributionGrid(..., showCard = false, showSportHeader = false)` between the header row and `HorizontalDivider`. Remove trailing section from `PeriodSummaryCard`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Zero change to day-cell aggregation, quartile calculation, or drill-down navigation.
  2. Non-year periods (`WEEK`, `MONTH`) display unchanged.
  3. 100% pass on targeted unit tests and living document governance.
- **Risk Rating**: **LOW**. Localized Jetpack Compose layout refactoring in `PeriodSummaryCard.kt` and `YearlyContributionGrid.kt`; zero impact on database, background services, or data models.
