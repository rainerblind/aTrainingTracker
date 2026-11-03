# Stage 2: Requirement & Test Specification - ATT-5194: YearlyContributionGrid: auto-scroll by default so the current month (or end of year) is shown on the very right

**Ticket**: [ATT-5194](https://rainerblind.atlassian.net/browse/ATT-5194)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PER-025` (`REQ-PER-025.9`)  
**Test Spec ID**: `TST-PER-031` (`TST-PER-031.9`)  
**Branch**: `feature/ATT-5194`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-PER-025.9)

### 1.1 Problem Statement & Rationale
`YearlyContributionGrid` renders a 53-week horizontal matrix (~689 dp content width). Currently, `rememberScrollState()` defaults to 0 (January on the far left), forcing athletes in the second half of the year (e.g. November) to manually scroll through ~35–45 weeks of past data.

### 1.2 Functional & Architectural Requirements
1. **Initial Viewport Auto-Scroll Alignment (`REQ-PER-025.9`)**: Upon initial composition or year selection of `YearlyContributionGrid`, the horizontal scroll state SHALL automatically position the viewport:
   - For the current calendar year (`year == LocalDate.now().year`): the viewport SHALL align the current week column at the far right edge of the visible content area (`scrollState.maxValue - (totalCols - 1 - targetCol) * colPitchPx`, clamped to `[0, scrollState.maxValue]`), ensuring historical activity remains visible while future weeks sit off-screen to the right.
   - For past calendar years (`year < LocalDate.now().year`): the viewport SHALL scroll to the end of the year (`scrollState.maxValue`).
2. **Unrestricted Bidirectional Scrolling**: Athletes SHALL retain unrestricted manual scrolling freedom left and right without snap-backs on recomposition or cell interaction.
3. **Pure Function Separation**: Calculation logic SHALL be isolated into pure helper functions (`calculateTargetColumn`, `calculateInitialScrollOffset`) for 100% headless JVM testing without Android framework mocks.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PER-025` (*GitHub-Style Yearly Activity Contribution Grids per BSportType in Periods (Years)*), adding `REQ-PER-025.9`. Targets `YearlyContributionGrid.kt`, `YearlyContributionGridTest.kt`, and `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: Sprint 2026-44.2 via commit `285d2470` (`ATT-4849`) and Sprint 2026-45.1 via commit `4f74d924` (`ATT-5076`).
* **Root Reason for Existing Formulation**: `rememberScrollState()` defaulted to offset 0 (standard Compose behavior); auto-scrolling was deferred until late-year testing revealed the inconvenience.
* **Preservation of Core Invariants**: $7 \times 53$ matrix mapping, quartile calculations, day-tap drill-down, embedded `SportStatsRow` placement, and bidirectional manual scrolling freedom are 100% preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Current Year Viewport Alignment)**: *Given* an athlete viewing `YearlyContributionGrid` for the current year, *When* the grid measures its layout, *Then* it SHALL automatically scroll so the current week column aligns at the right edge of the viewport.
* **Criterion 2 (Past Year Viewport Alignment)**: *Given* an athlete viewing `YearlyContributionGrid` for a past year, *When* the grid measures its layout, *Then* it SHALL automatically scroll to the end of the year (`scrollState.maxValue`).
* **Criterion 3 (Scrolling Freedom & Recomposition Hygiene)**: *Given* the initial scroll has completed, *When* the athlete scrolls manually or taps cells, *Then* no unexpected scroll snap-backs SHALL occur.

---

## 2. Test Specification (TST-PER-031.9)

* **TST-PER-031.9 (Pure Calculation Unit Tests)**: In `YearlyContributionGridTest.kt`, verify `calculateTargetColumn` and `calculateInitialScrollOffset` across past years, early year, mid year, late year, and leap years, confirming offset clamping and exact edge alignment.
* **TST-PER-031.7 (Targeted Regression)**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-031.9]` | Unit | `YearlyContributionGridTest` | `REQ-PER-025.9` | Specified |
| `[TST-PER-031.7]` | Regression | Tier 1 Period List Suite | `REQ-PRO-001` | Specified |
