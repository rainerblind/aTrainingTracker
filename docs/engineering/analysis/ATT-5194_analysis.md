# Stage 1 Analysis: ATT-5194 - YearlyContributionGrid: auto-scroll by default so the current month (or end of year) is shown on the very right

**Ticket**: [ATT-5194](https://rainerblind.atlassian.net/browse/ATT-5194)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5194`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Statement & Motivation

`YearlyContributionGrid` renders a 53-week horizontal matrix ($\approx 689\text{ dp}$ content width) inside a `horizontalScroll(rememberScrollState())` container.
Currently, `rememberScrollState()` defaults to `0`, positioning the viewport at January on the far left.
In the second half of the calendar year (e.g., November), athletes must manually scroll across ~35–45 weeks to inspect their recent workouts.
The expected behavior is:
1. When viewing the **current calendar year**, automatically scroll so that the **current week / current month** is aligned at the right edge of the visible viewport.
2. When viewing **past calendar years**, automatically scroll to the end of the year (`scrollState.maxValue`).
3. Ensure the athlete retains unrestricted manual scrolling left (to past months) and right (into remaining future weeks of the current year).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * **Code Verification**: In `YearlyContributionGrid.kt:268`, `val scrollState = rememberScrollState()` does not specify an initial offset and lacks an auto-scroll effect.
  * **Coordinate Geometry**: Each column pitch is $13\text{ dp}$ ($11\text{ dp}$ cell + $2\text{ dp}$ spacing). Total content width is $\text{totalCols} \times 13\text{ dp}$. On typical viewports ($\approx 250\text{--}320\text{ dp}$ available grid width), only $\approx 19\text{--}24$ weeks fit on screen.
  * **Offset Derivation**: The visible viewport width is $W_{\text{viewport}} = W_{\text{content}} - \text{scrollState.maxValue}$. To align the right edge of target column $C_{\text{target}}$ with the right edge of the viewport:
    $$S_{\text{target}} = \text{scrollState.maxValue} - [(\text{totalCols} - 1 - C_{\text{target}}) \times \text{colPitchPx}]$$
    clamped to $[0, \text{scrollState.maxValue}]$.
  * **Target Column Alignment Trade-off**:
    * *Option A (Current Week Column $C_{\text{today}}$)*: Aligns today's active week to the right edge. All visible cells represent past days with training history; future empty weeks are positioned off-screen to the right.
    * *Option B (End of Current Month $C_{\text{monthEnd}}$)*: Aligns the last day of the current month to the right edge.
    * *Recommendation*: Option A ($C_{\text{today}}$) maximizes visible workout history while fully honoring the freedom to scroll right into future weeks.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement initial auto-scroll in `YearlyContributionGrid.kt` via `LaunchedEffect` once `scrollState.maxValue` is measured.
  * Align current week / month column at the right edge for the current year, and scroll to `maxValue` for past years.
  * Retain smooth bidirectional manual scrolling without jumpy snap-backs on recomposition.
  * Extract pure calculation helpers (`calculateInitialScrollOffset`, `calculateTargetColumn`) for 100% headless JVM unit testing in `YearlyContributionGridTest.kt`.
  * Update living documentation (`REQ-PER-025`, `TST-PER-031`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter grid dimensions ($7 \times 53$), cell sizing ($11\text{ dp}$), or quartile color ramps.
  * Do not alter day tap drill-down behavior or `PeriodSummaryCard.kt` embedding.
  * Do not modify `PeriodBarGraph.kt` (which uses a separate `LazyListState`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-PER-025` (*GitHub-Style Yearly Activity Contribution Grids*, `ATT-4849`, `ATT-5076`). Targets `YearlyContributionGrid.kt`, `YearlyContributionGridTest.kt`, and `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: `ATT-4849` (commit `285d2470`, Sprint 2026-44.2) introduced the grid; `ATT-5076` embedded it inside `SportStatsRow`. Initial scroll state was kept at default `0` pending user feedback.
* **Root Reason for Existing Formulation**: Starting at offset 0 was the default Compose behavior; auto-scrolling was deferred until in-sprint testing highlighted the inconvenience for late-year inspections.
* **Preservation of Core Invariants**: $7 \times 53$ matrix mapping, quartile calculations, day-tap drill-down, and bidirectional scrolling freedom are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Pure Calculation Engine (SWE.2 / Rule 36)**:
   * `calculateTargetColumn(year: Int, today: LocalDate, startOffset: Int, totalCols: Int): Int`
   * `calculateInitialScrollOffset(maxValue: Int, totalCols: Int, targetCol: Int, colPitchPx: Float): Int`
2. **Recomposition-Resilient Initial Scroll**:
   * Track `var hasAutoScrolled by remember(year) { mutableStateOf(false) }`.
   * In `LaunchedEffect(scrollState.maxValue, year)`: when `!hasAutoScrolled && scrollState.maxValue in 1 until Int.MAX_VALUE`, compute target offset and call `scrollState.scrollTo(targetOffset)`, then set `hasAutoScrolled = true`.
3. **Unit Test Coverage**:
   * Add unit tests in `YearlyContributionGridTest.kt` verifying offset calculations across past years, early year, mid year, late year, and leap years.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Manual scrolling left and right remains completely unrestricted.
  2. Zero recomposition snap-backs when tapping cells or interacting with detail dialogs.
  3. Clean headless JVM testability with zero Android framework mocking.
* **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
  * Single caller in production: `PeriodSummaryCard.kt:403`.
  * Compose `@Preview`s in `YearlyContributionGrid.kt` remain functional.
* **Risk Rating**: **LOW** — Self-contained Compose UI scroll adjustment; zero data model or database changes.
