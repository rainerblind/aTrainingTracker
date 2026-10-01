# Stage 5: Walkthrough & Verification - ATT-1818: Fix pace decoding, clamp implausible scrubbing values, and format Y-axis pace labels as mm:ss

**Ticket**: [ATT-1818](https://rainerblind.atlassian.net/browse/ATT-1818)  
**Sub-task**: [ATT-1884](https://rainerblind.atlassian.net/browse/ATT-1884) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-219` (*Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting*)  
**Test Mapping**: `TST-UI-173`  
**Branch**: `feature/ATT-1818`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Executive Summary & Verification Overview

Ticket ATT-1818 resolves critical defects in Pace telemetry presentation across Aftermath workout inspection screens (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `ElevationProfile.kt`, and `TelemetryMetricGraph.kt`):

1. **Resolution of the Inverted Speed-to-Pace Flaw**:
   - `PaceFormatter.java` expects pace in seconds per meter ($s/m$): $\text{pace (min/km)} = s/m \times 1000 / 60$.
   - Previously, `ElevationProfile.kt` and `TelemetryMetricGraph.kt` passed speed in $m/s$ directly into `PaceFormatter`. For a runner at 4.167 m/s (15.0 km/h, 4:00 min/km), this calculated:
     $$4.1667 \times 1000 / 60 = 69.444 \rightarrow \mathbf{69:27 \text{ min/km}}$$
   - Now, `ElevationProfile.kt` passes $s/m = 1.0 / point.speedMps$, displaying `"4:00 min/km"` (in Metric) or `"6:26 min/mile"` (in Imperial).
2. **Stopped / Paused Speed Clamping & Thresholding**:
   - Speeds below $0.55 \text{ m/s}$ ($< 2.0 \text{ km/h}$, slower than $30:00 \text{ min/km}$) represent stopped or paused steps.
   - In `ElevationProfile.kt` scrubbing, speeds $< 0.55 \text{ m/s}$ or null invoke `paceFormatter.format_with_units(null)`, displaying `"-- min/km"` (or `"-- min/mile"`), preventing division-by-zero, infinity, and absurd numbers.
   - In `TelemetryMetricGraph.kt`, `extractMetricValue` filters speeds $< 0.55 \text{ m/s}$ to `null` and bounds running pace to realistic limits: `[1.5, 20.0]`.
3. **Athletic `mm:ss` Y-Axis Label Formatting**:
   - Added public helper `TelemetryMetricUtils.formatPaceMinutes(paceMinutes: Double): String` returning `String.format(Locale.US, "%d:%02d", min, sec)`.
   - `TelemetryMetricGraph` now formats Y-axis labels using `formatPaceMinutes(dataMin)` and `formatPaceMinutes(dataMax)` (e.g. `"4:00"` and `"6:00"`), completely eliminating the raw decimal floats (`"1.0"`, `"33.0"`).
4. **Dynamic Range Bounding on Pace Graphs**:
   - In `TelemetryMetricGraph`, `(dataMin, dataMax)` for `PACE` is derived from active running points with a minimum spread of at least 1.0 min/km (`dataMin = (min * 0.95).coerceAtLeast(1.5)`, `dataMax = (max * 1.05).coerceAtMost(20.0)`).
   - Momentary pause points no longer blow up `dataMax` to 33.0 min/km; the running curve dynamically utilizes the full vertical plot area.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-219` (item 1) | `[TST-UI-173.5]` | Unit Test (`ElevationProfileScrubbingTest.testScrubbingPace_decodingAndStoppedThreshold`) | **PASSED** | `Verified` |
| `REQ-UI-219` (item 2) | `[TST-UI-173.1]` | Unit Test (`TelemetryMetricGraphTest.testFormatValue_paceFormattingMetricAndImperial`) | **PASSED** | `Verified` |
| `REQ-UI-219` (item 3) | `[TST-UI-173.2]`, `[TST-UI-173.4]` | Unit Test (`TelemetryMetricGraphTest.testFormatPaceMinutes_standardAndEdgeCases`, `testTelemetryMetricGraph_paceYAxisLabelsAreMmSs`) | **PASSED** | `Verified` |
| `REQ-UI-219` (item 4) | `[TST-UI-173.3]` | Unit Test (`TelemetryMetricGraphTest.testExtractMetricValue_paceStoppedThreshold`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-173.6]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
```text
./gradlew testDebugUnitTest --tests "*TelemetryMetricGraphTest*" --tests "*ElevationProfileScrubbingTest*"
BUILD SUCCESSFUL in 12s
32 actionable tasks: 5 executed, 27 up-to-date
```
- `TelemetryMetricGraphTest.testFormatValue_paceFormattingMetricAndImperial`: PASSED
- `TelemetryMetricGraphTest.testFormatPaceMinutes_standardAndEdgeCases`: PASSED
- `TelemetryMetricGraphTest.testExtractMetricValue_paceStoppedThreshold`: PASSED
- `TelemetryMetricGraphTest.testTelemetryMetricGraph_paceYAxisLabelsAreMmSs`: PASSED
- `TelemetryMetricGraphTest.testExtractMetricValue_speedAndPace`: PASSED
- `ElevationProfileScrubbingTest.testScrubbingPace_decodingAndStoppedThreshold`: PASSED
- `ElevationProfileScrubbingTest.testResolvePointByDistance`: PASSED
- `ElevationProfileScrubbingTest.testResolvePointByTime`: PASSED
- `ElevationProfileScrubbingTest.testTelemetryGracefulNullHandling`: PASSED

### Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
- Total test suite: 100% pass rate, 0 failures, 0 regressions across all modules.

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Running Workout Scrubbing Verification**:
  1. Open a recorded running workout (e.g. *'Mit Jonas durch den Wald'*) in detailed inspection.
  2. Scrub to any point along the track.
  3. Verify that active running points display realistic pace values (e.g. `"4:30 min/km"`, `"5:15 min/km"`), not `"69:27 min/km"`.
  4. Scrub across a pause/stop: verify that pace renders cleanly as `"-- min/km"` without crashing or displaying giant numbers.
* **Tempo Graph Y-Axis Labels**:
  1. Inspect the Tempo graph Y-axis on the left.
  2. Verify that top and bottom labels render as standard pace notation (e.g. `"4:00"` and `"6:00"`), not raw decimal numbers (`"1.0"`, `"33.0"`).
  3. Verify that the running curve is vertically centered and dynamically scaled rather than compressed into a thin line at the top.
* **Cycling & Other Sports**:
  1. Open a cycling workout.
  2. Verify that speed continues displaying in `km/h` (Metric) or `mph` (Imperial) via `SpeedFormatter`.

---

## 5. Invariant & Governance Verification

1. **Formatter Contract Intact**: `PaceFormatter.java` contract (accepting seconds per meter $s/m$) is preserved verbatim.
2. **Sport Profile Independence**: Cycling, hiking, and other sports are completely unaffected and continue using `SpeedFormatter`.
3. **Graph Geometry Invariants**: Shared 50dp start and 25dp end paddings, global horizontal zoom alignment (`REQ-UI-215`), and synchronized multi-metric scrubbing remain 100% intact.
4. **Clean-Room Regression**: Full suite pass with 0 regressions.
