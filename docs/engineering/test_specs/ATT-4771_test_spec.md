# Stage 2: Requirement & Test Specification - ATT-4771: Full Distance Unit Parity in PeriodBarGraph

**Ticket**: [ATT-4771](https://rainerblind.atlassian.net/browse/ATT-4771)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4771`  
**Requirement Mapping**: `REQ-PER-023` (refining `REQ-PER-018.2`)  
**Test Spec ID**: `TST-PER-029` (`TST-PER-029.1` - `TST-PER-029.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-PER-023`)

### 1.1 Problem Statement & Rationale
`PeriodBarGraph.kt` currently hardcodes distance labels to metric units with shorthand `"k"` suffixes (e.g. `"8.5k"`, `"42k"`). This violates Rule 36 and ignores the athlete's unit preference (`MyUnits.IMPERIAL`), showing raw metric truncated with `"k"` instead of `"mi"`. Furthermore, `"k"` is non-standard SI nomenclature for distance (`"km"`). Day bars (`18.dp` width, `4.dp` spacing) require geometry expansion to prevent label collisions when rendering 2-character unit suffixes.

### 1.2 Functional & Architectural Requirements
1. **Metric Distance Formatting (`REQ-PER-023.1`)**: When `unit == MyUnits.METRIC`, values $< 1000\text{ m}$ SHALL format as `"${m}m"`, values $< 10\text{ km}$ SHALL format with 1 decimal place as `String.format(Locale.US, "%.1fkm", km)`, and values $\ge 10\text{ km}$ SHALL format as integer `"${km.roundToInt()}km"`.
2. **Imperial Distance Formatting (`REQ-PER-023.2`)**: When `unit == MyUnits.IMPERIAL`, values $< 0.1\text{ mi}$ SHALL format as `"<0.1mi"`, values $< 10\text{ mi}$ SHALL format with 1 decimal place as `String.format(Locale.US, "%.1fmi", mi)`, and values $\ge 10\text{ mi}$ SHALL format as integer `"${mi.roundToInt()}mi"` (using `BANALService.METER_PER_MILE = 1609.344`).
3. **Ascent Unit Parity (`REQ-PER-023.3`)**: In `ASCENT` mode, Metric SHALL format as `"${m}m"`, and Imperial SHALL format as `"${ft}ft"` (using `BANALService.METER_PER_FOOT = 0.3048`).
4. **Non-Overlap Geometry Expansion (`REQ-PER-023.4`)**: Expand `barWidth` for `DAY` from `18.dp` to `24.dp`, `WEEK` from `28.dp` to `32.dp`, and increase `LazyRow` spacing from `4.dp` to `6.dp` ($\ge 30\text{ dp}$ pitch), preventing adjacent label collision.
5. **System Invariants (`REQ-PER-023.5`)**: Duration formatting in `REQ-PER-018.2` remains untouched; zero SQLite schema alterations; 100% clean-room test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PER-023` (*PeriodBarGraph Full Distance & Imperial Unit Parity*), refining `REQ-PER-018.2` (*Sub-Hour Duration & Value Formatting*). Targets `PeriodBarGraph.kt` and `PeriodBarGraphTest.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-43.9 Review (`ATT-4478`), where initial `PeriodBarGraph` was introduced with tight `18.dp` Day bars and shorthand `"k"` suffixes.
* **Root Reason for Existing Formulation**: `"k"` was used as a single-character shorthand to avoid label truncation on narrow `18.dp` Day bars, while Imperial unit conversion was omitted in the initial MVP commit.
* **Preservation of Core Invariants**: Expanding Day bars to `24.dp` and pitch to `30.dp` provides ample horizontal clearance for `"km"` and `"mi"` labels without collision, while maintaining unit system parity (Rule 36) and zero SQLite schema alterations.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Metric Distance)**: *Given* metric units, *When* formatting distance, *Then* values $< 1000\text{ m}$ render as `"${m}m"`, $< 10\text{ km}$ as `"%.1fkm"`, and $\ge 10\text{ km}$ as `"${km}km"`.
* **Criterion 2 (Imperial Distance)**: *Given* imperial units, *When* formatting distance, *Then* values $< 0.1\text{ mi}$ render as `"<0.1mi"`, $< 10\text{ mi}$ as `"%.1fmi"`, and $\ge 10\text{ mi}$ as `"${mi}mi"`.
* **Criterion 3 (Imperial Ascent)**: *Given* imperial units, *When* formatting ascent, *Then* elevation renders as integer feet with `"ft"`.
* **Criterion 4 (Geometry Non-Overlap)**: *Given* Day bar graph with consecutive active days, *When* rendered, *Then* labels up to 6 characters (`"26.2mi"`, `"125km"`) do not overlap.

---

## 2. Test Specification (`TST-PER-029`)

### Test Cases
* `TST-PER-029.1` (**Metric Distance Formatting Test**): Verify boundary values for Metric (`500m`, `999m`, `1.0km`, `8.5km`, `9.9km`, `10km`, `42km`).
* `TST-PER-029.2` (**Imperial Distance Formatting Test**): Verify boundary values for Imperial (`<0.1mi`, `1.0mi`, `5.3mi`, `10mi`, `26mi`).
* `TST-PER-029.3` (**Ascent Unit Parity Test**): Verify metric ascent (`250m`) and imperial ascent (`820ft` for 250m).
* `TST-PER-029.4` (**Application Preference Fallback Test**): Verify default `unit` queries `TrainingApplication.getUnit()` and respects `TrainingApplication.setUnitForTesting`.
* `TST-PER-029.5` (**Geometry & Layout Integrity Test**): Verify `barWidth` for `DAY` is `24.dp`, `WEEK` is `32.dp`, and spacing is `6.dp`.
* `TST-PER-029.6` (**Clean-Room Regression Execution**): Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` with 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-029.1` | Unit | `PeriodBarGraphKt.formatMetricValue` (Metric) | `REQ-PER-023.1` | Planned |
| `TST-PER-029.2` | Unit | `PeriodBarGraphKt.formatMetricValue` (Imperial) | `REQ-PER-023.2` | Planned |
| `TST-PER-029.3` | Unit | `PeriodBarGraphKt.formatMetricValue` (Ascent) | `REQ-PER-023.3` | Planned |
| `TST-PER-029.4` | Unit | `PeriodBarGraphKt.formatMetricValue` (Default Unit) | `REQ-PER-023.1`, `REQ-PER-023.2` | Planned |
| `TST-PER-029.5` | UI / Unit | `PeriodBarGraph.kt` (Geometry layout) | `REQ-PER-023.4` | Planned |
| `TST-PER-029.6` | Regression | Full Period List Test Suite | `REQ-PER-023.5` | Planned |
