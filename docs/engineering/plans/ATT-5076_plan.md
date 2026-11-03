# Stage 3: Implementation Plan - ATT-5076: Embed Yearly Activity Grid inside SportStatsRow

**Ticket**: [ATT-5076](https://rainerblind.atlassian.net/browse/ATT-5076)  
**Sub-task**: [[ATT-5151]](https://rainerblind.atlassian.net/browse/ATT-5151) (`[Impl-Plan]`)  
**Parent Epic**: Standalone (`ATT-5076`)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-PER-025` (`REQ-PER-025.8`)  
**Test Mapping**: `TST-PER-031` (`TST-PER-031.8`)  
**Branch**: `feature/ATT-5076`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
In `PeriodSummaryCard.kt` (`PeriodType.YEAR`), `YearlyContributionGrid` is rendered in a separate trailing block at the bottom of the card, repeating the sport icon, name, and container card borders. Moving each sport's contribution grid directly inside that sport's `SportStatsRow` container below main summary totals and above sub-sport breakdowns creates a unified visual hierarchy without redundant headers or nested card borders.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-025.8` (*SportStatsRow Embedded Placement & Seamless Styling*)
* **Test Mapping**: `TST-PER-031.8` (*Embedded SportStatsRow Placement & Seamless Styling Test*)

---

## 3. System Invariants & Preserved Behavior
1. **Calendar & Math Invariants**: $7 \times 53$ matrix mapping and quartile intensity math remain untouched.
2. **Drill-Down Navigation**: Day-cell tap drill-down to workout list remains fully functional.
3. **Period Scope Isolation**: Non-year periods (`PeriodType.WEEK`, `PeriodType.MONTH`) remain unaffected.
4. **Code Lock Until Gate 3**: No production code changes occur before Gate 3 passes audit.

---

## 4. Proposed Architectural Changes

### Component 1: `YearlyContributionGrid.kt` (UI Layer)
* Add parameters `showCard: Boolean = true` and `showSportHeader: Boolean = true`.
* When `showCard = false`, omit outer `Card` and render content directly in `Column(modifier = modifier)`.
* When `showSportHeader = false`, omit the sport icon/name row and right-align the intensity legend.

### Component 2: `PeriodSummaryCard.kt` (UI Layer)
* Update `SportStatsRow` to accept `yearlyContribution: YearlySportContribution? = null`, `activeMetric: PeriodBarMetric = PeriodBarMetric.DURATION`, and `onDayCellClick: ((LocalDate, DailySportActivity?) -> Unit)? = null`.
* In `SportStatsRow`, if `yearlyContribution != null`, render `YearlyContributionGrid(..., showCard = false, showSportHeader = false)` immediately below the main sport summary header row and above `HorizontalDivider`.
* Pass the corresponding `yearlySportContributions[bSportType]` from `summary` when `summary.periodType == PeriodType.YEAR`.
* Delete the separate trailing `yearlySportContributions` loop and section header.

### UI Consistency (Rule 23)
* **Reference component**: `SportStatsRow` in `PeriodSummaryCard.kt`.
* **Reused components**: `YearlyContributionGrid`, `SportStatsRow`, `Surface`, `Row`, `Column`.
* **Theme tokens**: `MaterialTheme.colorScheme.outlineVariant`, `MaterialTheme.typography.labelSmall`, `8.dp`/`10.dp` spacing.
* **New one-off styles**: None. Eliminates card-in-card nesting per `docs/design_guidelines.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `YearlyContributionGrid.kt` Embedded Support
* Add `showCard: Boolean = true` and `showSportHeader: Boolean = true` parameters.
* Conditionally bypass outer `Card` and hide sport identity header while keeping the legend.

### Step 2: `PeriodSummaryCard.kt` SportStatsRow Integration
* Add `yearlyContribution`, `activeMetric`, `onDayCellClick` parameters to `SportStatsRow`.
* Render embedded `YearlyContributionGrid` below the main sport header row in `SportStatsRow`.
* Wire `summary.yearlySportContributions[bSportType]` in `PeriodSummaryCard`.
* Remove the standalone trailing yearly contribution section and header.

### Step 3: Test Verification
* Add embedded rendering contract assertions in `YearlyContributionGridTest.kt`.
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and `TranslationParityTest` in Stage 4; clean-room regression in Stage 5.
* **Rollback**: Branch `feature/ATT-5076` can be cleanly reverted via `git restore`.
