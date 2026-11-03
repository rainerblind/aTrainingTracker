# Stage 1 Analysis: ATT-4771 — Full Distance Unit Parity in PeriodBarGraph

**Ticket**: [ATT-4771](https://rainerblind.atlassian.net/browse/ATT-4771)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4771`  
**Refining Requirement**: `REQ-PER-018.2` | **Proposed Spec ID**: `REQ-PER-023` / `TST-PER-029`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Root Cause Analysis
* **Root Cause**: In `PeriodBarGraph.kt` (`formatMetricValue`), distances are hardcoded to metric with abbreviated `"k"` suffixes (e.g., `"8.5k"`, `"42k"` for meters $\ge 1000$).
* **Deficiency**: Violates Rule 36 and athlete unit configuration (`MyUnits.METRIC` vs. `MyUnits.IMPERIAL`). Imperial users see raw metric values truncated with `"k"` rather than `"mi"`. In addition, `"k"` is non-standard SI nomenclature for distance (`"km"`).
* **Layout Constraint**: `PeriodBarGraph` previously assigned `18.dp` bar width with `4.dp` spacing for `PeriodType.DAY`. Two-character suffixes (`"km"`, `"mi"`) could cause visual label collisions between adjacent active day bars without geometry adjustments.

---

## 2. Chesterton's Fence Forensics
* **Original Motivation (ATT-4478)**: `"k"` was chosen as a single-character shorthand to fit inside tight `18.dp` day bar bounds in the initial `PeriodBarGraph` implementation.
* **Why Safe to Refactor**: Horizontal whitespace analysis reveals Day bars can safely expand to `24.dp` with `6.dp` spacing (30.dp pitch, fitting 13+ days on standard mobile viewports). Week (`32.dp`), Month (`48.dp`), and Year (`80.dp`) bars already possess ample horizontal clearance for `"km"` and `"mi"` labels without overlap.

---

## 3. Scope Bounding & Architectural Design

| Component | Responsibility / Proposed Change |
| :--- | :--- |
| `PeriodBarGraph.kt` | Update `formatMetricValue(value, metric, periodType, unit: MyUnits = TrainingApplication.getUnit())`:<br>• **Metric**: `< 1000m` $\rightarrow$ `"${m}m"`, `< 10km` $\rightarrow$ `String.format(Locale.US, "%.1fkm", km)`, $\ge 10km$ $\rightarrow$ `"${km.roundToInt()}km"`.<br>• **Imperial**: `< 0.1mi` $\rightarrow$ `"<0.1mi"`, `< 10mi` $\rightarrow$ `String.format(Locale.US, "%.1fmi", mi)`, $\ge 10mi$ $\rightarrow$ `"${mi.roundToInt()}mi"`.<br>• **Ascent**: Metric `${m}m`, Imperial `${ft}ft` via `METER_PER_FOOT`.<br>Adjust Day bar width from `18.dp` $\rightarrow$ `24.dp`, Week from `28.dp` $\rightarrow$ `32.dp`, and spacing to `6.dp`. |
| `PeriodBarGraphTest.kt` | Add test cases verifying metric (`km`, `m`) and imperial (`mi`, `<0.1mi`) formatting across boundary thresholds, testing both direct parameter and `TrainingApplication.setUnitForTesting`. |
| Living Docs | Update `req_aftermath_periods.md` (`REQ-PER-023`) and `tst_aftermath_periods.md` (`TST-PER-029`). |

---

## 4. Invariants & Governance Checklist
* **Rule 36 (DRY & Unit Parity)**: Canonical unit system (`MyUnits`) and conversion constants (`BANALService.METER_PER_MILE`, `BANALService.METER_PER_FOOT`) strictly adhered to.
* **Non-Overlap Invariant**: Centered labels with `24.dp` width + `6.dp` gap provide $\ge 30\text{dp}$ horizontal pitch, eliminating adjacent label overlap.
* **Zero Database Impact**: Zero changes to SQLite schema or DAO queries.
* **Clean-Room Test Pass Rate**: 100% pass across `./gradlew testDebugUnitTest` and `verify_requirement_governance.py`.
