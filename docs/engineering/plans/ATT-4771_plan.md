# Stage 3: Implementation Plan - ATT-4771: Full Distance Unit Parity in PeriodBarGraph

**Ticket**: [ATT-4771](https://rainerblind.atlassian.net/browse/ATT-4771)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4771`  
**Requirement Mapping**: `REQ-PER-023` (refining `REQ-PER-018.2`) | **Test Mapping**: `TST-PER-029`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background
`PeriodBarGraph.kt` currently formats distance values with hardcoded metric `"k"` shorthand, ignoring athlete imperial settings (`MyUnits.IMPERIAL`) and violating Rule 36. Top label geometry on Day bars (`18.dp`, `4.dp` spacing) also risks label collision when rendering 2-character unit suffixes (`"km"`, `"mi"`).

---

## 2. Traceability & Requirements Mapping
* `REQ-PER-023.1` (Metric Distance: `m` / `km`) $\rightarrow$ `TST-PER-029.1`
* `REQ-PER-023.2` (Imperial Distance: `<0.1mi` / `mi`) $\rightarrow$ `TST-PER-029.2`
* `REQ-PER-023.3` (Ascent Unit Parity: `m` / `ft`) $\rightarrow$ `TST-PER-029.3`
* `REQ-PER-023.4` (Geometry & Non-Overlap: Day `24.dp`, Week `32.dp`, spacing `6.dp`) $\rightarrow$ `TST-PER-029.4`, `TST-PER-029.5`
* `REQ-PER-023.5` (Invariants: Duration untouched, zero DB changes, 100% tests) $\rightarrow$ `TST-PER-029.6`

---

## 3. System Invariants & Preserved Behavior
* **Rule 36 (Unit Parity)**: Unit conversions route via `BANALService.METER_PER_MILE` and `BANALService.METER_PER_FOOT`.
* **Zero Database Impact**: No changes to `PeriodSummaries.db` schema or repository queries.
* **Duration Formatting Untouched**: Existing `<1m`, `${m}m`, `${h}h` rules are fully preserved.
* **UI Consistency (Rule 23)**: Retains M3 `labelSmall` typography and semantic color tokens (`onSurface`, `TTAlpha.Medium`).

---

## 4. Proposed Architectural Changes

| Component | Responsibility / Proposed Change |
| :--- | :--- |
| `PeriodBarGraph.kt` | • Add `unit: MyUnits = TrainingApplication.getUnit()` parameter to `formatMetricValue`.<br>• Metric: $< 1000\text{m} \rightarrow "\${m}\text{m}"$, $< 10\text{km} \rightarrow "\%.1\text{fkm}"$, $\ge 10\text{km} \rightarrow "\${km}\text{km}"$.<br>• Imperial: $< 0.1\text{mi} \rightarrow "<0.1\text{mi}"$, $< 10\text{mi} \rightarrow "\%.1\text{fmi}"$, $\ge 10\text{mi} \rightarrow "\${mi}\text{mi}"$.<br>• Ascent: Metric $\rightarrow "\${m}\text{m}"$, Imperial $\rightarrow "\${ft}\text{ft}"$.<br>• Geometry: Day `barWidth = 24.dp`, Week `barWidth = 32.dp`, `spacing = 6.dp`.<br>• In `PeriodBarGraph`: query `TrainingApplication.getUnit()` and pass to `formatMetricValue`. |
| `PeriodBarGraphTest.kt` | Update existing tests and add comprehensive test methods covering `TST-PER-029.1` through `TST-PER-029.5`. |

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `formatMetricValue` & Bar Geometry in `PeriodBarGraph.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodBarGraph.kt`
* Add `unit: MyUnits` parameter with default, implement Metric / Imperial distance and ascent branches, update `barWidth` and `spacedBy(6.dp)`.

### Step 2: Update & Author Tests in `PeriodBarGraphTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodBarGraphTest.kt`
* Implement `TST-PER-029.1` (Metric), `TST-PER-029.2` (Imperial), `TST-PER-029.3` (Ascent), `TST-PER-029.4` (Default Unit & Test Override).

### Step 3: Execute Unit Tests & Requirement Audit
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* Run `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and theme audit with 100% pass rate.
* **Rollback**: Clean git branch revert on `feature/ATT-4771` without persistent side effects.
