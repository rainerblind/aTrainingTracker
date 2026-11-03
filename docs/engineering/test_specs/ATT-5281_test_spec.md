# Stage 2: Requirement & Test Specification - ATT-5281: Unconditional Histogram Background Track Continuity

**Ticket**: [[ATT-5281]](https://rainerblind.atlassian.net/browse/ATT-5281)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Requirement Mapping**: `REQ-UI-443` (`REQ-UI-443.2`, `REQ-UI-443.3`)  
**Test Mapping**: `TST-UI-443` (`TST-UI-443.2`, `TST-UI-443.3`, `TST-UI-443.4`)  
**Branch**: `feature/ATT-5281`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-443)

### 1.1 Problem Statement & Rationale
In `TelemetryMetricGraph.kt` (*Leistung* chart in *Histogramm* mode), power samples across high-wattage sprint ranges (e.g. 375 W .. 574 W) land in non-contiguous 10 W bins while intermediate bins have `durationSec == 0L`. Because the `alpha = 0.15f` container track was gated behind `if (bin.durationSec > 0L)`, white surface horizontal gaps appeared between active bins, disrupting the continuous Zone 7 background band. The `alpha = 0.15f` background track must be drawn unconditionally for every bin in `linearBins` spanning `[dataMin, dataMax]`, while solid foreground bars remain gated on `bin.durationSec > 0L`.

### 1.2 Functional & Architectural Requirements
1. *Portrait Unconditional Background Track (`REQ-UI-443.2`)*: In `TelemetryMetricGraph.kt` portrait mode (`zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM`), the canvas drawing routine SHALL render the `bin.color.copy(alpha = 0.15f)` background container track unconditionally for every bin in `histogram.linearBins` across `[dataMin, dataMax]`, while the solid foreground bar (`barWidthPx > 0f`) remains gated on `bin.durationSec > 0L`.
2. *Landscape Unconditional Background Track (`REQ-UI-443.3`)*: In `TelemetryMetricGraph.kt` landscape mode (`AlignedMarginalZoneDistribution`), the canvas drawing routine SHALL render the `bin.color.copy(alpha = 0.15f)` background container track unconditionally for every bin in `histogram.linearBins` across `[dataMin, dataMax]`, while the solid foreground bar and text percentage overlays remain gated on `bin.durationSec > 0L`.
3. *Preservation of Invariants (`REQ-UI-443.4`)*: Standalone `TelemetryHistogramChart.kt` sprint-tail folding, 1:1 vertical Y-axis watt mapping, multi-chart plot alignment (`startPadding = 50.dp`), and 72%/28% landscape split SHALL remain 100% preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Continuous Zone 7 Track)**: *Given* high-wattage power bins with `durationSec == 0L` across `[dataMin, dataMax]`, *When* viewing `TelemetryMetricGraph` marginal histogram in portrait or landscape, *Then* the `alpha = 0.15f` background container track is drawn unconditionally across all `linearBins` without white horizontal gaps.
* **Criterion 2 (Foreground Gating)**: *Given* zero-duration intermediate bins, *When* rendered, *Then* solid foreground frequency bars and text overlays remain suppressed.

---

## 2. Test Specification (TST-UI-443)

### Test Case 1: Landscape Aligned Marginal Track Continuity Contract (`TST-UI-443.3`)
* **Scope**: Contract Test (`TelemetryMetricGraphLandscapeContractTest.kt`)
* **Procedure**: Assert `TelemetryMetricGraph.kt` does not suppress background container tracks for empty `linearBins`.

### Test Case 2: Portrait Marginal Strip Track Continuity Contract (`TST-UI-443.2`)
* **Scope**: Contract Test (`TelemetryMetricGraphMarginalBarsContractTest.kt`)
* **Procedure**: Assert portrait marginal histogram iterates `linearBins` and renders continuous background tracks across `[dataMin, dataMax]`.

### Test Case 3: Clean-Room Targeted Regression Suite (`TST-UI-443.4`)
* **Scope**: Targeted Package Suite (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`)
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-443.2` | Contract | `TelemetryMetricGraph.kt` (Portrait) | `REQ-UI-443.2` | Specified |
| `TST-UI-443.3` | Contract | `TelemetryMetricGraph.kt` (Landscape) | `REQ-UI-443.3` | Specified |
| `TST-UI-443.4` | Regression | Map & Zones Unit Test Suite | `REQ-PRO-001` | Specified |
