# Stage 2: Requirement & Test Specification - ATT-5280: DailyActivityStrip Mini-Bars Rounded Top and Sharp Bottom Corners

**Ticket**: [[ATT-5280]](https://rainerblind.atlassian.net/browse/ATT-5280)  
**Sub-task**: [[ATT-5302]](https://rainerblind.atlassian.net/browse/ATT-5302) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Requirement Mapping**: `REQ-PER-027.4`  
**Test Spec ID**: `TST-PER-035.3`, `TST-PER-035.8`  
**Branch**: `feature/ATT-5280`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-PER-027.4)

### 1.1 Problem Statement & Rationale
In `DailyActivityStrip.kt` (Periods -> Months / Weeks), daily mini-bars inside `SportStatsRow` appear inverted with rounded bottom corners and sharp top corners. Bottom-anchored vertical bar charts require rounded top corners (`topStart = 2.dp..3.dp, topEnd = 2.dp..3.dp`) and flat, sharp bottom corners (`bottomStart = 0.dp, bottomEnd = 0.dp`) resting flush on the baseline per `docs/design_guidelines.md` §2.7 and Rule 69.

### 1.2 Functional & Architectural Requirements
1. *Outer Container Clip Removal (`REQ-PER-027.4a`)*: The outer container `Box` in `MonthDayBar` (`L374-375`) and `WeekDayColumn` (`L320-322`) SHALL NOT apply `.clip(RoundedCornerShape(...))`, ensuring inner bar bottom corners and selection borders are not clipped at the baseline.
2. *Month Mini-Bar Geometry (`REQ-PER-027.4b`)*: In `MonthDayBar`, active filled bars SHALL apply `RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 0.dp, bottomEnd = 0.dp)`.
3. *Week Mini-Bar Geometry (`REQ-PER-027.4c`)*: In `WeekDayColumn`, active filled bars SHALL apply `RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 0.dp, bottomEnd = 0.dp)`.
4. *Empty Baseline Track Invariance (`REQ-PER-027.4d`)*: Rest-day baseline tracks (fixed 2.dp height, `RoundedCornerShape(1.dp)`) SHALL remain invariant.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Corner Geometry Parity)**: *Given* an active daily mini-bar rendered in `DailyActivityStrip` (`MonthDayBar` or `WeekDayColumn`), *When* drawn on-screen, *Then* the bar renders with rounded top corners and flat sharp bottom corners resting flush on the baseline.
* **Criterion 2 (Selection & Rest Track Invariance)**: *Given* a day cell with `isSelected == true` or zero activity, *When* rendered, *Then* selection border and rest-day baseline track remain fully visible and positioned without clipping.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-PER-027.4` (*Metric Slaving & Proportional Height Scaling / Mini-Bar Corner Geometry*). Targets `DailyActivityStrip.kt` (`MonthDayBar`, `WeekDayColumn`).
2. *Historical Origin & Commit Trace*: Added in `ATT-5195` (commit `ca6a892f`, Sprint 2026-45.2). Amending ticket: `ATT-5280` (Sprint 2026-45.3).
3. *Root Reason for Existing Formulation*: In `ATT-5195`, outer container `Box` applied `.clip(RoundedCornerShape(2.dp / 3.dp))` for cell slot encapsulation, inadvertently clipping bottom corners of bottom-anchored inner bars while inner bar top corners had smaller radii, creating an inverted rounded-bottom visual defect.
4. *Preservation of Core Invariants*: Click targets, selection borders (`RoundedCornerShape(2.dp / 3.dp)`), empty rest-day baseline tracks (fixed 2.dp height), date mappings, metric calculations, and repository flows are 100% preserved.

---

## 2. Test Specification (TST-PER-035)

### Test Case 1: Mini-Bar Structural Contract & Shape Assertion (`TST-PER-035.3`)
* **Scope**: Contract Test (`DailyActivityStripTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/DailyActivityStripTest.kt`
* **Action**: Programmatic contract test verifying `DailyActivityStrip.kt` does not apply outer Box corner clipping on bar hosts and verifies corner shape invariants per Rule 5.4.
* **Expected Result**: Assertions pass without source-text grep on UI literals.

### Test Case 2: Clean-Room PeriodList Regression Suite (`TST-PER-035.8`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* **Expected Result**: 100% pass rate across period list unit tests and theme centralization audit.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-035.3` | Contract | `DailyActivityStrip.kt` | `REQ-PER-027.4` | Specified |
| `TST-PER-035.8` | Regression | PeriodList & Theme Tests | `REQ-PRO-001` | Specified |
