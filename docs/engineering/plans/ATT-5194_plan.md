# Stage 3: Implementation Plan - ATT-5194: YearlyContributionGrid: auto-scroll by default so the current month (or end of year) is shown on the very right

**Ticket**: [ATT-5194](https://rainerblind.atlassian.net/browse/ATT-5194)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PER-025` (`REQ-PER-025.9`)  
**Test Mapping**: `TST-PER-031` (`TST-PER-031.9`)  
**Branch**: `feature/ATT-5194`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Description & Background

The 53-week `YearlyContributionGrid` starts at scroll offset 0 (January), forcing athletes in late months (e.g. November) to manually scroll right to inspect current activity. This plan implements default initial auto-scrolling:
- Current year (`year == today.year`): aligns the current week column at the right edge of the viewport so recent activity is immediately visible while remaining future weeks sit off-screen to the right.
- Past years (`year < today.year`): scrolls to `scrollState.maxValue` (December at right edge).
- Freedom to scroll left and right is preserved with zero recomposition snap-backs.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PER-025.9` (*Initial Viewport Auto-Scroll Alignment for Current & Past Years*)
  * Current year aligns current week column at right edge; past year scrolls to `maxValue`.
  * Preserves bidirectional manual scroll freedom.
* **Test Mapping**: `TST-PER-031.9` in `YearlyContributionGridTest.kt` (pure functional tests, no UI grep per Section 5.4).

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-PER-025` (*GitHub-Style Yearly Activity Contribution Grids*, `ATT-4849`, `ATT-5076`). Targets `YearlyContributionGrid.kt`, `YearlyContributionGridTest.kt`, and `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: `ATT-4849` (commit `285d2470`, Sprint 2026-44.2) introduced the grid; `ATT-5076` embedded it inside `SportStatsRow`. Initial scroll state was kept at default `0` pending user feedback.
* **Root Reason for Existing Formulation**: Starting at offset 0 was the default Compose behavior; auto-scrolling was deferred until in-sprint testing highlighted the inconvenience for late-year inspections.
* **Preservation of Core Invariants**: $7 \times 53$ matrix mapping, quartile calculations, day-tap drill-down, and bidirectional scrolling freedom are 100% preserved.

---

## 3. System Invariants & Preserved Behavior

1. **Section 1.10 Compliance**: No Compose snapshot state writes during composition. `hasAutoScrolled` write is strictly inside `LaunchedEffect`.
2. **Section 5.4 Compliance**: No source code text-grepping in unit tests. Pure functions are tested via behavioral return values.
3. **Rule 4 Code Lock**: Zero production code edits before Gate 3 passes `check-gate`.
4. **Grid Fidelity**: $7 \times 53$ matrix mapping, cell sizing ($11\text{ dp}$), quartile colors, and cell tap drill-downs remain 100% unchanged.

---

## 4. Proposed Architectural Changes

### Component 1: Pure Scroll Calculation Functions (`YearlyContributionGrid.kt`)
* Extract pure mathematical helpers:
  * `calculateTargetColumn(year: Int, today: LocalDate, startOffset: Int, totalCols: Int): Int`: Returns `totalCols - 1` for past years, 0 for future years, and `((today.dayOfYear - 1 + startOffset) / 7).coerceIn(0, totalCols - 1)` for current year.
  * `calculateInitialScrollOffset(maxValue: Int, totalCols: Int, targetCol: Int, colPitchPx: Float): Int`: Returns `(maxValue - ((totalCols - 1 - targetCol) * colPitchPx).roundToInt()).coerceIn(0, maxValue)`.

### Component 2: Recomposition-Resilient Auto-Scroll (`YearlyContributionGrid.kt`)
* Add `var hasAutoScrolled by remember(year) { mutableStateOf(false) }`.
* In `LaunchedEffect(scrollState.maxValue, year)`:
  * When `!hasAutoScrolled && scrollState.maxValue in 1 until Int.MAX_VALUE`, compute target offset, call `scrollState.scrollTo(targetOffset)`, and set `hasAutoScrolled = true`.

### Component 3: Unit Test Suite (`YearlyContributionGridTest.kt`)
* Implement `TST-PER-031.9`: Test `calculateTargetColumn` and `calculateInitialScrollOffset` across past years, Jan 1 (start), mid-year, current date, Dec 31 (end), and leap years.

### UI Consistency (Rule 23)
* **Reference screen**: `PeriodSummaryCard.kt` (embedded yearly contribution grid).
* **Theme tokens**: Standard typography and spacing; no new colors or one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Pure Functions & LaunchedEffect in YearlyContributionGrid.kt
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/YearlyContributionGrid.kt`
* Changes: Add `calculateTargetColumn`, `calculateInitialScrollOffset`, and `LaunchedEffect(scrollState.maxValue, year)`.

### Step 2: Implement Unit Tests in YearlyContributionGridTest.kt
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/YearlyContributionGridTest.kt`
* Changes: Add `testInitialAutoScroll_targetColumnAndOffsetCalculation` validating edge cases and clamping.

### Step 3: Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.YearlyContributionGridTest" --max-workers=2`

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted `YearlyContributionGridTest` and `verify_requirement_governance.py`.
* **Rollback**: Clean git branch revert via `git checkout sprint/2026-45.2`.
