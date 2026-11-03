# Stage 1 Analysis: ATT-5280 - DailyActivityStrip Mini-Bars Rounded Top and Sharp Bottom Corners

**Ticket**: [ATT-5280](https://rainerblind.atlassian.net/browse/ATT-5280)  
**Sub-task**: [ATT-5301](https://rainerblind.atlassian.net/browse/ATT-5301) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Branch**: `feature/ATT-5280`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
During Sprint 2026-45.2 on-device review of `ATT-5195` (`DailyActivityStrip.kt` in Periods -> Months / Weeks):
- The daily mini-bars inside `SportStatsRow` appear visually inverted with rounded bottom corners and sharp top corners.
- Standard bottom-anchored vertical bar charts (such as `PeriodBarGraph.kt` at the top of the period card) require rounded top corners (`topStart = 2.dp..3.dp, topEnd = 2.dp..3.dp`) and flat, sharp bottom corners (`bottomStart = 0.dp, bottomEnd = 0.dp`) resting flush against the baseline.
- `docs/design_guidelines.md` §2.7 (Rule 69) explicitly codifies this requirement: bottom-anchored bars must have rounded top corners and sharp bottom corners, without outer container clipping at the bottom.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  - `MonthDayBar` (`DailyActivityStrip.kt:372-393`): The outer 26.dp container `Box(contentAlignment = Alignment.BottomCenter)` applies `.clip(RoundedCornerShape(2.dp))` to all 4 corners. The inner active bar aligns to the bottom, so the outer container clips its bottom corners with a 2.dp radius. The inner bar applies `.clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))`. Because 1.dp is visually sharper than the 2.dp rounded bottom, the bar visually inverts standard bar chart styling.
  - `WeekDayColumn` (`DailyActivityStrip.kt:318-338`): Similarly, the outer 28.dp container `Box` applies `.clip(RoundedCornerShape(3.dp))` to all 4 corners, clipping bottom corners to 3.dp, while the inner bar applies `.clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))`.
* **Independent Peer Review & Verification**:
  - Verified live sprint codebase in `DailyActivityStrip.kt`: outer container clipping at lines 322 and 375 directly clips inner bar bottom corners.
  - Verified `PeriodBarGraph.kt:480-486`: Reference vertical bars use `.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))` with no outer container clipping on the bar host.
  - Verified selection borders in `MonthDayBar` (line 379) and `WeekDayColumn` (line 325): Selection borders use `RoundedCornerShape(2.dp / 3.dp)`; removing `.clip()` from the outer container Box leaves the selection border and click target fully intact.
  - Verified rest-day tracks (lines 340-347 and 394-401): Empty tracks render at a fixed 2.dp height with `RoundedCornerShape(1.dp)`, functioning as subtle horizontal baseline ticks that remain unchanged.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Remove outer `.clip(RoundedCornerShape(...))` modifier from the container `Box` in `MonthDayBar` and `WeekDayColumn` in `DailyActivityStrip.kt`.
  - In `MonthDayBar`: update inner active bar clip to `RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 0.dp, bottomEnd = 0.dp)`.
  - In `WeekDayColumn`: update inner active bar clip to `RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 0.dp, bottomEnd = 0.dp)`.
  - Add regression unit test in `DailyActivityStripTest.kt` verifying calculation and layout stability without UI source-text grep.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No changes to `PeriodBarGraph.kt` or `YearlyContributionGrid.kt`.
  - No changes to metric calculation logic (`calculateDailyBarFraction`, `extractDailyMetricValue`).
  - No changes to `PeriodsRepository.kt` or `PeriodSummaries.db` schema.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-027.4` (Metric Slaving & Proportional Height Scaling in `DailyActivityStrip.kt`).
* **Historical Origin & Commit Trace**: Added in `ATT-5195` (commit `ca6a892f`, Sprint 2026-45.2).
* **Root Reason for Existing Formulation**: Outer container `Box` was given `.clip(RoundedCornerShape(2.dp / 3.dp))` for visual encapsulation of the cell slot, unintentionally clipping the bottom corners of bottom-anchored inner bars.
* **Preservation of Core Invariants**: Date sequences, selection drill-down, sport color mapping, empty track baselines, and touch accessibility contracts are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Outer Container Clip Removal (`DailyActivityStrip.kt`)**: Remove `.clip(RoundedCornerShape(2.dp))` from `MonthDayBar` (line 375) and `.clip(RoundedCornerShape(3.dp))` from `WeekDayColumn` (line 322).
2. **Inner Bar Shape Refinement (`DailyActivityStrip.kt`)**:
   - `MonthDayBar`: Inner active bar gets `.clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 0.dp, bottomEnd = 0.dp))`.
   - `WeekDayColumn`: Inner active bar gets `.clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 0.dp, bottomEnd = 0.dp))`.
3. **Living Documentation & Invariants**: Update `REQ-PER-027.4` and `TST-PER-035.3` in `req_aftermath_periods.md` / `tst_aftermath_periods.md` to reflect top corner rounding and sharp bottom baseline geometry per `docs/design_guidelines.md` §2.7 (Rule 69).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero SQLite schema changes; zero background service or telemetry processing modifications.
  2. Day selection state (`isSelected`), selection border, and click listeners remain fully functional.
  3. Strict compliance with `docs/coding_standards.md` §5.4 (zero UI literal source-text grep in tests).
* **Risk Rating**: **LOW**. Localized Compose UI shape modifier refinement in a single presentation component.
