# Stage 1 Analysis: ATT-5193 - YearlyContributionGrid: fix vertical clipping of day-of-week labels (e.g. 'F') and add 'S' label for Sunday

**Ticket**: [ATT-5193](https://rainerblind.atlassian.net/browse/ATT-5193)  
**Sub-task**: [ATT-5244](https://rainerblind.atlassian.net/browse/ATT-5244) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5193`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Statement & Motivation

In `YearlyContributionGrid.kt`, the left day-of-week label column displays abbreviated days aligned with the 7 rows of the $7 \times 53$ matrix.
Two visual flaws were observed during Sprint 2026-45.1 Review:
1. **Vertical Glyph Clipping**: The bottom of Friday's label (`F` / `Fr`) is clipped/truncated because each label is constrained within `Box(Modifier.height(cellSize = 11.dp))` while `Text` inherits `MaterialTheme.typography.labelSmall`'s default `lineHeight = 16.sp` with Android platform font padding enabled.
2. **Missing Sunday Anchor**: Only Monday (`idx == 0`), Wednesday (`idx == 2`), and Friday (`idx == 4`) are labeled. The 7th row (Sunday, `idx == 6`) has no label, leaving the bottom of the grid unanchored.
The objective of ATT-5193 is to eliminate glyph clipping via micro-typography line-height bounding (Design Guidelines Section 5.15) and render Sunday (`S`) to establish an even 2-day cadence (Mon, Wed, Fri, Sun).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause 1 (Font Metrics & Bounding)**: In `YearlyContributionGrid.kt:294`, `Text` inherits `labelSmall` default `lineHeight = 16.sp`. Inside `Box(Modifier.height(11.dp))`, the line box overflows the $11\text{ dp}$ container boundary, clipping tall glyphs like `F`. Default `PlatformTextStyle(includeFontPadding = true)` exacerbates vertical overflow.
* **Root Cause 2 (Hardcoded Label Cadence)**: In `YearlyContributionGrid.kt:284`, `val showLabel = idx == 0 || idx == 2 || idx == 4` omits row index 6 (`DayOfWeek.SUNDAY`).
* **Independent Hypothesis Verification**:
  * Setting `lineHeight = 9.sp`, `PlatformTextStyle(includeFontPadding = false)`, and `wrapContentHeight(unbounded = true)` eliminates glyph clipping while maintaining vertical alignment with 11.dp grid cells.
  * Expanding `showLabel` to `idx in (0, 2, 4, 6)` completes the 2-day cadence (Mon, Wed, Fri, Sun).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `PlatformTextStyle(includeFontPadding = false)` and explicit `lineHeight = 9.sp` to day-of-week labels in `YearlyContributionGrid.kt`.
  2. Add `wrapContentHeight(unbounded = true)` on the day label container to guarantee unclipped vertical rendering.
  3. Update `showLabel` condition to include Sunday (`idx == 6`), producing labels for Monday, Wednesday, Friday, and Sunday.
  4. Extract pure helper function `shouldShowDayOfWeekLabel(rowIdx: Int): Boolean` for 100% headless JVM testing in `YearlyContributionGridTest.kt`.
  5. Synchronize living documentation (`REQ-PER-025.3`, `TST-PER-031.10`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not alter grid cell dimensions ($11\text{ dp}$), cell spacing ($2\text{ dp}$), or column pitch ($13\text{ dp}$).
  * Do not alter month header labels, quartile intensity calculations, or auto-scroll mechanics.
  * Do not change `DailyActivityStrip.kt` (which uses dedicated Week and Month layouts).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-PER-025.3` (*Matrix Dimensions & Calendar Mapping*) in `docs/requirements/req_aftermath_periods.md`. Targets `YearlyContributionGrid.kt` and `YearlyContributionGridTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4849` (Sprint 2026-44.2). The 3-day label convention (Mon/Wed/Fri) followed standard desktop GitHub grids, but on mobile viewports the omitted Sunday made the bottom boundary hard to distinguish.
* **Root Reason for Existing Formulation**: Starting with Monday/Wednesday/Friday followed GitHub conventions; glyph clipping went unnoticed until real-device inspection in Sprint 2026-45.1.
* **Preservation of Core Invariants**: 7-row mapping, 11 dp cell spacing, and localized `TextStyle.NARROW` formatting remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Pure Cadence Helper (SWE.2 / Rule 36)**:
   * Define `fun shouldShowDayOfWeekLabel(rowIdx: Int): Boolean = rowIdx in listOf(0, 2, 4, 6)`.
2. **Micro-Typography Styling (Design Guidelines Section 5.15)**:
   * In `YearlyContributionGrid.kt`:
     ```kotlin
     Text(
         text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
         style = MaterialTheme.typography.labelSmall.copy(
             fontSize = 9.sp,
             lineHeight = 9.sp,
             platformStyle = PlatformTextStyle(includeFontPadding = false)
         ),
         color = MaterialTheme.colorScheme.onSurfaceVariant,
         modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
     )
     ```
3. **Unit Test Coverage**:
   * Add test `testDayOfWeekLabelCadence_showsMonWedFriSun` in `YearlyContributionGridTest.kt` asserting rows 0, 2, 4, 6 are shown and rows 1, 3, 5 are hidden.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero layout shift: Total column height remains strictly $7 \times 11\text{ dp} + 6 \times 2\text{ dp} = 89\text{ dp}$.
  2. Complete localization parity: `day.getDisplayName(TextStyle.NARROW, Locale.getDefault())` naturally handles all locales without static strings.
  3. No snapshot state mutations in composition (Section 1.10).
* **Side-Effect Audit (Rule 68)**:
  * Only caller is `YearlyContributionGrid.kt`. Zero impact on database, ViewModel, or other screens.
* **Risk Rating**: **LOW** — Self-contained Compose typography and cadence adjustment; zero behavioral or data model risk.
