# Stage 2: Requirement & Test Specification - ATT-5193: YearlyContributionGrid: fix vertical clipping of day-of-week labels (e.g. 'F') and add 'S' label for Sunday

**Ticket**: [ATT-5193](https://rainerblind.atlassian.net/browse/ATT-5193)  
**Sub-task**: [ATT-5245](https://rainerblind.atlassian.net/browse/ATT-5245) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PER-025` (`REQ-PER-025.3`)  
**Test Mapping**: `TST-PER-031` (`TST-PER-031.10`)  
**Branch**: `feature/ATT-5193`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-PER-025.3)

### 1.1 Problem Statement & Rationale
In `YearlyContributionGrid.kt`, the left day-of-week label column displays single-letter abbreviations inside `Box(Modifier.height(11.dp))`. Because `Text` defaults to `lineHeight = 16.sp` with platform font padding, tall glyphs like `F` (Friday) suffer vertical clipping at the bottom. Additionally, only Mon, Wed, and Fri are labeled, omitting Sunday and leaving the bottom of the grid unanchored.

### 1.2 Functional & Architectural Requirements
1. **Micro-Typography Bounding (`REQ-PER-025.3`)**: Day-of-week labels in `YearlyContributionGrid.kt` SHALL apply `PlatformTextStyle(includeFontPadding = false)`, explicit `lineHeight = 9.sp`, and `wrapContentHeight(unbounded = true)` per Design Guidelines Section 5.15, guaranteeing unclipped glyph rendering inside 11 dp cell rows.
2. **Sunday Anchor Cadence (`REQ-PER-025.3`)**: Day-of-week labels SHALL display for Monday (0), Wednesday (2), Friday (4), and Sunday (6, `DayOfWeek.SUNDAY`) via pure helper `shouldShowDayOfWeekLabel(rowIdx)`, establishing a consistent 2-day cadence (`[M, W, F, S]`).
3. **Pure Function Separation**: Cadence evaluation logic SHALL be extracted into `shouldShowDayOfWeekLabel(rowIdx: Int): Boolean` for 100% headless JVM testing without Android mocks (Coding Standards Section 5.4).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PER-025.3` (*Matrix Dimensions & Calendar Mapping*). Targets `YearlyContributionGrid.kt`, `YearlyContributionGridTest.kt`, and `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4849` (Sprint 2026-44.2); embedded into `SportStatsRow` in `ATT-5076` (Sprint 2026-45.1). Followed GitHub's desktop 3-label convention until mobile real-device inspection revealed clipping on tall glyphs and bottom truncation.
* **Root Reason for Existing Formulation**: Starting with Mon/Wed/Fri was adopted from desktop web grids; default Compose text styling caused line box overflow within 11 dp cell boundaries.
* **Preservation of Core Invariants**: 7-row matrix dimensions ($7 \times 53$), 11 dp cell height, quartile shading, and localized `TextStyle.NARROW` day initial resolution remain 100% preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Unclipped Glyphs)**: *Given* `YearlyContributionGrid` rendered on any density screen, *When* day labels are drawn, *Then* tall glyphs (e.g. `F`) SHALL render with full vertical clearance without bottom clipping.
* **Criterion 2 (Sunday Label)**: *Given* the day-of-week column, *When* rows 0..6 are rendered, *Then* row 6 (Sunday) SHALL display its localized narrow initial (`S` / `So`).

---

## 2. Test Specification (TST-PER-031.10)

* **TST-PER-031.10 (Cadence Unit Test)**: In `YearlyContributionGridTest.kt`, verify `shouldShowDayOfWeekLabel(rowIdx)` returns `true` for rows 0, 2, 4, 6 and `false` for rows 1, 3, 5.
* **TST-PER-031.7 (Targeted Regression)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-031.10]` | Unit | `shouldShowDayOfWeekLabel` | `REQ-PER-025.3` | Specified |
| `[TST-PER-031.7]` | Regression | Tier 1 Period List Suite | `REQ-PRO-001` | Specified |
