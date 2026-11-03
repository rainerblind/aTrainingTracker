# Stage 2: Requirement & Test Specification - ATT-5076: Embed Yearly Activity Grid inside SportStatsRow

**Ticket**: [ATT-5076](https://rainerblind.atlassian.net/browse/ATT-5076)  
**Parent Epic**: Standalone (`ATT-5076`)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-PER-025` (*GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years)*)  
**Test Spec ID**: `TST-PER-031`  
**Branch**: `feature/ATT-5076`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-PER-025)

### 1.1 Problem Statement & Rationale
Currently, in `PeriodSummaryCard.kt` (`PeriodType.YEAR`), `YearlyContributionGrid` is rendered in a separate trailing section at the bottom of the card, repeating the sport icon and name with redundant card-in-card borders. Embedding each sport's contribution grid directly into its corresponding `SportStatsRow` container creates a unified, uncluttered visual hierarchy.

### 1.2 Functional & Architectural Requirements
1. `YearlyContributionGrid.kt` SHALL support embedded rendering via `showCard: Boolean = true` and `showSportHeader: Boolean = true`. When `false`, the outer `Card` container and duplicate sport icon/title header SHALL be omitted, retaining the intensity legend (`REQ-PER-025.8`).
2. `PeriodSummaryCard.kt` SHALL embed each sport's `YearlyContributionGrid` directly inside `SportStatsRow` for `PeriodType.YEAR`, positioned immediately below the main sport header row and above sub-sports details and longest workout.
3. The trailing standalone *"Yearly Activity"* section title and loop SHALL be removed from `PeriodSummaryCard.kt`.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Modifies `REQ-PER-025` (`REQ-PER-025.8`) and `TST-PER-031` (`TST-PER-031.8`), targeting `PeriodSummaryCard.kt` and `YearlyContributionGrid.kt`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-44.2 via commit `285d2470` (`ATT-4849`).
* `Root Reason for Existing Formulation`: In ATT-4849, contribution grids were initially placed at the bottom to avoid altering `SportStatsRow` before gathering user feedback on visual balance.
* `Preservation of Core Invariants`: $7 \times 53$ calendar day mapping, quartile intensity calculation, interactive day-tap drill-down, and non-year period layouts remain 100% preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Embedded Placement & Seamless Layout)**:
  * *Given* a Year period summary (`PeriodType.YEAR`),
  * *When* viewing any sport's `SportStatsRow`,
  * *Then* that sport's $7 \times 53$ contribution grid is embedded directly below the main summary header row and above any sub-sports breakdown, without outer card borders or duplicate sport icons.
* **Criterion 2 (Non-Year Periods Unaffected)**:
  * *Given* a Week or Month period summary,
  * *When* viewing `SportStatsRow`,
  * *Then* no contribution grid is rendered.

---

## 2. Test Specification (TST-PER-031)

### Test Case 1: Embedded Placement & Parameter Contracts (`TST-PER-031.8`)
* **Scope**: Unit / Composable Contract Test (`PeriodSummaryCardTest.kt`, `YearlyContributionGridTest.kt`)
* **Preconditions**: Year period summary with positive workout stats.
* **Action**: Render `YearlyContributionGrid` with `showCard = false, showSportHeader = false`.
* **Expected Result**: Outer card container is bypassed, sport header is omitted, intensity legend remains visible.

### Test Case 2: 9-Language Localization Audit (`TST-PER-031.6`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Goal**: Verify 100% string parity across all 9 locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.

### Test Case 3: Tier 1 Targeted Test Suite
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Goal**: 100% pass on period list UI, theme centralization, and build audit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-031.8` | Composable | `YearlyContributionGrid`, `SportStatsRow` | `REQ-PER-025.8` | Specified |
| `TST-PER-031.6` | Localization | `TranslationParityTest` | `REQ-PER-025.7` | Specified |
| Tier 1 Suite | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
