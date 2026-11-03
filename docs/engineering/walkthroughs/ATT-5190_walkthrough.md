# Stage 5: Walkthrough & Verification - ATT-5190: Power TelemetryMetricGraph marginal histogram: render unfolded 10 W bins instead of giant black Sprint-Tail overflowBin

**Ticket**: [[ATT-5190]](https://rainerblind.atlassian.net/browse/ATT-5190)  
**Parent Epic**: Standalone  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-443` (`REQ-UI-443.1`, `REQ-UI-443.2`, `REQ-UI-443.3`, `REQ-UI-443.4`)  
**Test Mapping**: `TST-UI-443` (`TST-UI-443.1`, `TST-UI-443.2`, `TST-UI-443.3`, `TST-UI-443.4`)  
**Branch**: `feature/ATT-5190`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Executive Summary & Verification Overview
* **Issue Addressed**: When `ZoneCardDisplayMode.HISTOGRAM` is active for Cycling Power, a massive solid black vertical block (`TTColor.Zone7`) rendered in the right marginal histogram across `[tailCutoff, dataMax]`. Standalone column histograms on the *Zones* tab (`TelemetryHistogramChart.kt`) fold high-power spikes into an `overflowBin`, but marginal histograms share a continuous 1:1 vertical Y-axis with the time-series curve. The single overflow bin was stretched across 150–200 W, obscuring high-wattage zone inspection.
* **Delivered Fix**:
  1. `ZoneDistributionModels.kt`: Added `TelemetryHistogramData.linearBins: List<TelemetryHistogramBin>` unrolling the continuous sequence of 10 W frequency bins spanning `[dataMin, dataMax]` without `overflowBin` or `returnBin` when sprint-tail folding is active.
  2. `TelemetryMetricGraph.kt`: Switched portrait inline marginal strip (L482-L510) and landscape `AlignedMarginalZoneDistribution` (L941-L975) to iterate over `histogram.linearBins`, normalize `maxBinPct` across `linearBins`, and suppress container tracks (`alpha = 0.15f`) for 0-duration bins (`bin.durationSec == 0L`).
  3. Tests: Implemented unit tests in `TelemetryHistogramCalculatorTest.kt` (`TST-UI-443.1`), and contract tests in `TelemetryMetricGraphMarginalBarsContractTest.kt` (`TST-UI-443.2`) and `TelemetryMetricGraphLandscapeContractTest.kt` (`TST-UI-443.3`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-443.1` | `[TST-UI-443.1]` | Unit Test (`TelemetryHistogramCalculatorTest`) | **PASSED** | `Verified` |
| `REQ-UI-443.2` | `[TST-UI-443.2]` | Contract Test (`TelemetryMetricGraphMarginalBarsContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-443.3` | `[TST-UI-443.3]` | Contract Test (`TelemetryMetricGraphLandscapeContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-443.4` | `[TST-UI-443.4]` | Tier 1 Targeted (`aftermath.zones.*`, `map.*`, `build.*`, `ThemeAudit`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Explicitly Executed)
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.*" \
  --tests "com.atrainingtracker.trainingtracker.build.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
BUILD SUCCESSFUL in 13s (32 actionable tasks: 2 executed, 30 up-to-date) - 473 tests passed (100%)
```

### Requirement Governance Audit (`verify_requirement_governance.py`)
```text
python3 tools/verify_requirement_governance.py
Net-new requirement(s) detected: REQ-UI-443. Bypassing archaeology check cleanly.
PASS: Requirement governance clean (0 errors).
```

---

## 4. Hardware / Physical Verification
* Verified via automated Compose Canvas behavioral mathematical models and reflection contract tests. No physical ADB device attached (`adb devices` reported empty list).

---

## 5. Invariant & Governance Verification
1. **Standalone Sprint-Tail Invariant**: Standalone `TelemetryHistogramChart.kt` folding, interactive overflow bar, and drill-down navigation remain 100% intact.
2. **Multi-Chart Plot Alignment**: 50dp start padding and landscape 72%/28% dual-pane split remain byte-for-byte preserved.
3. **Living Documentation Synchronized**: `REQ-UI-443` and `TST-UI-443` updated to `Verified`.
