# Stage 3: Implementation Plan - ATT-5280: DailyActivityStrip Mini-Bars Rounded Top and Sharp Bottom Corners

**Ticket**: [ATT-5280](https://rainerblind.atlassian.net/browse/ATT-5280)  
**Sub-task**: [ATT-5303](https://rainerblind.atlassian.net/browse/ATT-5303) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Requirement Mapping**: `REQ-PER-027.4`  
**Test Mapping**: `TST-PER-035.3`, `TST-PER-035.8`  
**Branch**: `feature/ATT-5280`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In `DailyActivityStrip.kt` (Periods -> Months / Weeks), daily mini-bars inside `SportStatsRow` appear visually inverted with rounded bottom corners and sharp top corners. The root cause is that the outer container `Box(contentAlignment = Alignment.BottomCenter)` applies `.clip(RoundedCornerShape(2.dp / 3.dp))`, clipping the bottom corners of bottom-anchored inner bars, while inner bars have smaller/sharp top radii. Per `docs/design_guidelines.md` §2.7 and Rule 69, vertical bar charts anchored to a bottom baseline must have rounded top corners (`topStart = 2.dp..3.dp, topEnd = 2.dp..3.dp`) and flat, sharp bottom corners (`bottomStart = 0.dp, bottomEnd = 0.dp`) resting flush on the baseline without outer container bottom-corner clipping.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PER-027.4` (Bar Shape Geometry & Height Scaling)** $\leftrightarrow$ `TST-PER-035.3`: Remove outer Box clipping and set rounded top / sharp bottom corner shapes in `MonthDayBar` and `WeekDayColumn`.
* **`REQ-PRO-001` (Core Regression Gate)** $\leftrightarrow$ `TST-PER-035.8`: PeriodList unit test suite and theme centralization audit.

---

## 3. System Invariants & Preserved Behavior
1. **Selection Border & Click Targets**: Outer container `Box` retains `isSelected` border (`RoundedCornerShape(2.dp / 3.dp)`) and click/tap handlers.
2. **Empty Baseline Tracks**: Empty rest tracks (fixed 2.dp height, `RoundedCornerShape(1.dp)`) remain untouched.
3. **Data Model & Calculation Invariance**: `calculateDailyBarFraction`, `extractDailyMetricValue`, and `generateDateSequence` remain unchanged.
4. **Test Integrity (Rule 5.4)**: Zero UI literal source-text grep in unit/contract tests.

---

## 4. Proposed Architectural Changes
* **UI Consistency (Rule 23)**:
  * Reference screen / component: `PeriodBarGraph.kt` (vertical bar chart with `topStart = 4.dp, topEnd = 4.dp` and no container bottom clip).
  * Reused components: `MonthDayBar`, `WeekDayColumn`, `DailyActivityStrip`.
  * Theme tokens: `RoundedCornerShape`, `MaterialTheme.colorScheme.primary`, `emptyTrackColor`, `sportColor`.
  * New one-off styles & justification: None; aligns mini-bars directly with Rule 69 and `PeriodBarGraph.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `MonthDayBar` Outer Clip Removal & Corner Geometry (`DailyActivityStrip.kt`, `TST-PER-035.3`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/DailyActivityStrip.kt`
* Remove `.clip(RoundedCornerShape(2.dp))` from outer container `Box` (L375).
* Update inner active bar shape to `RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 0.dp, bottomEnd = 0.dp)` (L390).

### Step 2: `WeekDayColumn` Outer Clip Removal & Corner Geometry (`DailyActivityStrip.kt`, `TST-PER-035.3`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/DailyActivityStrip.kt`
* Remove `.clip(RoundedCornerShape(3.dp))` from outer container `Box` (L322).
* Update inner active bar shape to `RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 0.dp, bottomEnd = 0.dp)` (L336).

### Step 3: Targeted Unit & Regression Verification (`TST-PER-035.3`, `TST-PER-035.8`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/DailyActivityStripTest.kt`
* Add regression test verifying structural shape invariants without source-text grep.
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Clean-room test suite pass rate, governance script verification, and walkthrough documentation.
* **Rollback**: Clean branch isolation on `feature/ATT-5280`; zero schema or persistent state migrations.
