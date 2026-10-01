# Stage 3: Implementation Plan - ATT-1818: Fix pace decoding, clamp implausible scrubbing values, and format Y-axis pace labels as mm:ss

**Ticket**: [ATT-1818](https://rainerblind.atlassian.net/browse/ATT-1818)  
**Sub-task**: [ATT-1882](https://rainerblind.atlassian.net/browse/ATT-1882) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-219` (*Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting*)  
**Test Mapping**: `TST-UI-173`  
**Branch**: `feature/ATT-1818`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In `aTrainingTracker`, inspecting running activities in Aftermath (`TrackOnMapScreen`, `MapDetailLayout.kt`, and `WorkoutSummary.kt`) revealed severe defects in Pace presentation (`screenshot_pace_scrubbing_and_xaxis.png`):
1. **Inverted Speed-to-Pace Passing**: `ElevationProfile.kt` and `TelemetryMetricGraph.kt` passed speed in $m/s$ directly into `PaceFormatter`, which expects pace in seconds per meter ($s/m$). For a runner at 4.167 m/s (15.0 km/h, 4:00 min/km), this inverted conversion calculated:
   $$4.1667 \times 1000 / 60 = 69.444 \rightarrow \mathbf{69:27 \text{ min/km}}$$
2. **Missing Stopped-Speed Clamping**: Pauses and near-zero speeds ($speedMps < 0.55 \text{ m/s}$ / $< 2.0 \text{ km/h}$) produced division-by-zero or extreme pace values ($> 30.0 \text{ min/km}$), blowing up the vertical dynamic range of the Tempo graph to `1.0 .. 33.0` and squeezing the actual running curve into a tiny band at the top.
3. **Unusable Decimal Y-Axis Labels**: Y-axis labels in `TelemetryMetricGraph` printed raw decimal floats (`1.0`, `33.0`, `5.5`) rather than athletic pace notation in minutes and seconds (`mm:ss`, such as `4:00`, `6:00`).

This implementation plan details the atomic steps to resolve these defects cleanly, preserve all system invariants, and verify behavior with automated tests.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-219` (*Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting*)
* **Test Mapping**: `TST-UI-173` (*Aftermath Pace Telemetry Decoding, Stopped Speed Clamping & Y-Axis Label Formatting Verification*)
* **Governing Requirements**:
  - `REQ-UI-206`: *Aftermath: Continuous Telemetry Metric Graphs with Section Headings & Synchronized Multi-Chart Scrubbing Architecture* (telemetry graphing invariants preserved).
  - `REQ-UI-215`: *Aftermath: Global Synchronized Horizontal Zoom & Panning Architecture Across Stacked Telemetry Graphs* (zoom & pan coordination preserved).
  - `REQ-PRO-001`: *Clean-Room Full Suite Regression Execution* (full test suite pass).

---

## 3. System Invariants & Preserved Behavior

1. **Formatter Contract Invariant**: `PaceFormatter.java` contract (accepting seconds per meter $s/m$) is preserved verbatim without breaking downstream callers.
2. **Sport Profile Independence Invariant**: Non-running sports (cycling, hiking, inline skating) continue formatting speed via `SpeedFormatter` in `km/h` or `mph`.
3. **Chart Layout & Synchronization Invariant**: Shared horizontal margins (`start = 50.dp, end = 25.dp, bottom = 24.dp`), synchronized multi-chart scrubbing via `selectedDistance`, and global horizontal zoom alignment remain 100% strictly preserved.
4. **Subtask & Gate Governance**: Subtasks transition directly to `Erledigt` upon passing review audit via `freigabe`. Parent ticket `ATT-1818` must be transitioned to `Final Review (Human)` assigned to `rainer`, preserving the human gate mandate.

---

## 4. Proposed Architectural Changes

### Component 1: `ElevationProfile.kt` (Scrubbing Pace Decoding & Stopped Clamping)
* In `ScrubbingInfoCard` (lines 914–925):
  ```kotlin
  val speedStr = if (bSportType == BSportType.RUN) {
      val spd = point.speedMps
      if (spd == null || spd < 0.55) {
          paceFormatter.format_with_units(null)
      } else {
          paceFormatter.format_with_units(1.0 / spd)
      }
  } else {
      speedFormatter.format_with_units(point.speedMps)
  }
  ```
  - For speeds $\ge 0.55 \text{ m/s}$, passes $s/m = 1.0 / spd$, calculating true pace (e.g. 4:00 min/km for 15 km/h).
  - For speeds $< 0.55 \text{ m/s}$ (or null), displays `"-- min/km"` (or `"-- min/mile"` in Imperial), eliminating division-by-zero and absurd numbers.

### Component 2: `TelemetryMetricGraph.kt` (Metric Utilities & Dynamic Range Bounding)
* In `TelemetryMetricUtils`:
  - Add public helper:
    ```kotlin
    fun formatPaceMinutes(paceMinutes: Double): String {
        val totalSec = (paceMinutes * 60.0).roundToInt().coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%d:%02d", min, sec)
    }
    ```
  - Update `extractMetricValue` for `PACE`:
    - Filter points with $speedMps < 0.55 \text{ m/s}$ to `null` (treated as stopped/paused).
    - Bound pace values to realistic athletic limits: `.coerceIn(1.5, 20.0)`.
  - Update `formatValue` for `PACE`:
    - Correctly calculate seconds per meter:
      ```kotlin
      val secPerUnit = value * 60.0
      val spm = if (unit == MyUnits.METRIC) {
          secPerUnit / 1000.0
      } else {
          secPerUnit / BANALService.METER_PER_MILE
      }
      paceFormatter.format_with_units(spm)
      ```
* In `TelemetryMetricGraph` composable:
  - Dynamic range for `PACE`:
    ```kotlin
    TelemetryMetricType.PACE -> {
        val paceMin = (min * 0.95).coerceAtLeast(1.5)
        val paceMax = (max * 1.05).coerceAtMost(20.0)
        paceMin to paceMax.coerceAtLeast(paceMin + 1.0)
    }
    ```
  - Y-axis label rendering:
    ```kotlin
    val maxLabel = if (metricType == TelemetryMetricType.PACE) {
        TelemetryMetricUtils.formatPaceMinutes(dataMin)
    } else {
        "${dataMax.toInt()}"
    }
    val minLabel = if (metricType == TelemetryMetricType.PACE) {
        TelemetryMetricUtils.formatPaceMinutes(dataMax)
    } else {
        "${dataMin.toInt()}"
    }
    ```

### Component 3: Unit Tests & Verification
* In `TelemetryMetricGraphTest.kt`:
  - Test `formatValue` for `PACE` in Metric and Imperial.
  - Test `formatPaceMinutes` across standard and boundary values.
  - Test `extractMetricValue` stopped speed threshold ($< 0.55 \text{ m/s}$) returning `null`.
  - Test `TelemetryMetricGraph.kt` contract: uses `formatPaceMinutes` for pace labels.
* In `ElevationProfileScrubbingTest.kt`:
  - Test running pace scrubbing: $4.167 \text{ m/s} \rightarrow \text{"4:00 min/km"}$.
  - Test stopped speed scrubbing: $0.2 \text{ m/s} \rightarrow \text{"-- min/km"}$.
  - Test cycling speed scrubbing: $4.167 \text{ m/s} \rightarrow \text{"15.0 km/h"}$.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `TelemetryMetricGraph.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Changes**:
  1. Add `formatPaceMinutes(paceMinutes: Double): String` to `TelemetryMetricUtils`.
  2. Fix `formatValue` for `PACE` to pass $s/m = (value \times 60.0) / metersPerUnit$.
  3. Update `extractMetricValue` for `PACE` to filter $speedMps < 0.55$ and coerce in `[1.5, 20.0]`.
  4. Update `(dataMin, dataMax)` calculation for `PACE` to avoid 33.0 expansion.
  5. Update `maxLabel` and `minLabel` in `TelemetryMetricGraph` to use `formatPaceMinutes`.

### Step 2: Update `ElevationProfile.kt` Scrubbing Pace Passing
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* **Changes**:
  1. In `ScrubbingInfoCard`, when `bSportType == BSportType.RUN`:
     - If `point.speedMps == null || point.speedMps < 0.55`: call `paceFormatter.format_with_units(null)`.
     - Else: call `paceFormatter.format_with_units(1.0 / point.speedMps)`.

### Step 3: Implement & Update Unit Tests in `TelemetryMetricGraphTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* **Changes**:
  1. Update `testExtractMetricValue_speedAndPace` to reflect new 0.55 m/s threshold.
  2. Add `testFormatValue_paceFormattingMetricAndImperial`.
  3. Add `testFormatPaceMinutes_standardAndEdgeCases`.
  4. Add `testExtractMetricValue_paceStoppedThreshold`.
  5. Add `testTelemetryMetricGraph_paceYAxisLabelsAreMmSs`.

### Step 4: Implement & Update Unit Tests in `ElevationProfileScrubbingTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileScrubbingTest.kt`
* **Changes**:
  1. Add tests verifying running pace calculation ($1.0 / speed$) and stopped clamping ($< 0.55 \text{ m/s}$ -> `"-- min/km"`).

### Step 5: Execute Targeted Tests
* Run `./gradlew testDebugUnitTest --tests "*TelemetryMetric*" --tests "*ElevationProfile*"`

### Step 6: Execute Full Clean-Room Regression Suite
* Run `./gradlew testDebugUnitTest` across all modules.

---

## 6. Verification & Test Plan

| Step | Verification Procedure | Command / Method | Expected Result |
| :--- | :--- | :--- | :--- |
| 1 | Unit tests for `formatValue` & `formatPaceMinutes` | `./gradlew testDebugUnitTest --tests "*TelemetryMetricGraphTest*"` | 100% PASS |
| 2 | Unit tests for scrubbing pace & stopped clamping | `./gradlew testDebugUnitTest --tests "*ElevationProfileScrubbingTest*"` | 100% PASS |
| 3 | Full Clean-Room Regression | `./gradlew testDebugUnitTest` | 100% PASS (0 regressions) |
