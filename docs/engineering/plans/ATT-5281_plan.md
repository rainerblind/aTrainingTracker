# Stage 3: Implementation Plan - ATT-5281: Unconditional Histogram Background Track Continuity

**Ticket**: [ATT-5281](https://rainerblind.atlassian.net/browse/ATT-5281)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Requirement Mapping**: `REQ-UI-443.2`, `REQ-UI-443.3`  
**Test Mapping**: `TST-UI-443.2`, `TST-UI-443.3`, `TST-UI-443.4`  
**Branch**: `feature/ATT-5281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In `TelemetryMetricGraph.kt` (*Leistung* chart in *Histogramm* mode), power samples across high-wattage sprint ranges (e.g. 375 W .. 574 W) land in non-contiguous 10 W bins while intermediate bins have `durationSec == 0L`. Because the `alpha = 0.15f` container track was gated behind `if (bin.durationSec > 0L)`, white surface horizontal gaps appeared between active bins, disrupting the continuous Zone 7 background band. The `alpha = 0.15f` background track must be drawn unconditionally for every bin in `linearBins` spanning `[dataMin, dataMax]`, while solid foreground bars remain gated on `bin.durationSec > 0L`.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-443.2` (Portrait Track Continuity)** $\leftrightarrow$ `TST-UI-443.2`: Unconditional background track rendering in portrait marginal strip.
* **`REQ-UI-443.3` (Landscape Track Continuity)** $\leftrightarrow$ `TST-UI-443.3`: Unconditional background track rendering in `AlignedMarginalZoneDistribution`.
* **`REQ-PRO-001` (Core Regression Gate)** $\leftrightarrow$ `TST-UI-443.4`: Tier 1 targeted unit & contract test suite.

---

## 3. System Invariants & Preserved Behavior
1. **Unconditional Track Continuity**: `alpha = 0.15f` container track is drawn for all `linearBins` across `[dataMin, dataMax]`.
2. **Foreground Gating Invariance**: Solid foreground frequency bars (`barWidthPx > 0f`) and text overlays remain gated on `durationSec > 0L`.
3. **Standalone Sprint-Tail Invariance**: Standalone `TelemetryHistogramChart.kt` folding and drill-down navigation remain 100% intact.
4. **UI Standards Compliance**: Contract tests adhere to Rule 5.4 (zero source-grep on UI literals).

---

## 4. Proposed Architectural Changes
* **UI Consistency (Rule 23)**:
  * Reference screens: `TelemetryMetricGraph.kt` main chart background zone bands and 5-zones display mode.
  * Reused components: `drawRect` (portrait), `drawRoundRect` with `CornerRadius(2.dp.toPx())` (landscape).
  * Theme tokens: `bin.color.copy(alpha = 0.15f)` (background container track), `bin.color` / `bin.color.copy(alpha = 0.85f)` (solid bar).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Portrait Mode Unconditional Background Track (`TelemetryMetricGraph.kt`)
* Files: `TelemetryMetricGraph.kt`
* Remove `if (bin.durationSec > 0L)` wrapper around `drawRect(color = bin.color.copy(alpha = 0.15f), ...)` (L495).
* Retain `if (barWidthPx > 0f)` condition for solid foreground frequency bars.

### Step 2: Landscape Mode Unconditional Background Track (`TelemetryMetricGraph.kt`)
* Files: `TelemetryMetricGraph.kt`
* Remove `if (bin.durationSec > 0L)` wrapper around `drawRoundRect(color = bin.color.copy(alpha = 0.15f), ...)` (L953).
* Retain `if (barWidthPx > 0f)` for solid bars and `rectHeight >= 12.dp.toPx() && bin.durationSec > 0L` for text overlays.

### Step 3: Contract Test Verification (`TelemetryMetricGraphLandscapeContractTest.kt`)
* Files: `TelemetryMetricGraphLandscapeContractTest.kt`
* Update contract test logic around L262–L267 to verify that `linearBins` unrolls contiguous bins across the data domain and that empty bins draw container tracks while foreground bars require `durationSec > 0L`, adhering to Rule 5.4.

### Step 4: Verification & Regression Tests (`TST-UI-443.4`)
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit & contract tests, followed by ASPICE governance verification.
* **Rollback**: Clean git revert of `feature/ATT-5281`; no persistent database schema migrations or state alterations.
