# Stage 1 Analysis: ATT-1818 - Fix pace decoding, clamp implausible scrubbing values, and format Y-axis pace labels as mm:ss

**Ticket**: [ATT-1818](https://rainerblind.atlassian.net/browse/ATT-1818)  
**Sub-task**: [ATT-1880](https://rainerblind.atlassian.net/browse/ATT-1880) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1818`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During Sprint 2026-40.6 and 2026-40.7 review on Pixel 10 inspecting real running workouts (specifically *'Mit Jonas durch den Wald'*, a 17.82 km trail run), two major defects were discovered in the visual presentation of Pace telemetry (`screenshot_pace_scrubbing_and_xaxis.png`):

1. **Implausible Scrubbing Pace Display (`69:27 min/km`)**:
   At km 10.21, the runner is actively jogging downhill at an aerobic heart rate of 145 bpm and a brisk speed of 4.167 m/s (15.0 km/h). Instead of displaying the true running pace of `4:00 min/km`, the multi-metric scrubber overlay in `ElevationProfile` displays an absurd, implausible pace of **`69:27 min/km`**.
2. **Useless Decimal Y-Axis Labels (`1.0` and `33.0`) and Squeezed Graph Range**:
   In the continuous Tempo (Pace) telemetry graph (`TelemetryMetricGraph`), the Y-axis tick labels render raw floating-point numbers (`1.0` at the top and `33.0` at the bottom) rather than standard athletic pace notation (`mm:ss`, such as `4:00` or `6:00`). Furthermore, because momentary stops or walking steps coerce to 30.0 min/km and expand the vertical dynamic range from 1.0 to 33.0 min/km, the runner's entire 17.82 km running curve (hovering between 4:30 and 5:30 min/km) is squeezed into a tiny, unreadable band occupying only ~15% of the chart height at the very top.

The objective of ATT-1818 is to fix the underlying unit conversion bug, clamp or filter non-running stopped/paused speeds, format Y-axis pace labels in human-readable `mm:ss` format, and ensure the running pace curve utilizes the vertical dynamic range effectively.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Photographic & Visual Evidence (`screenshot_pace_scrubbing_and_xaxis.png`)

Inspection of the captured on-device screenshot reveals the following:
* **Scrubber Card at km 10.21**:
  - Distance & Elevation: `10,21 km · 433 m`
  - Heart Rate: `145 bpm`
  - Pace: `69:27 min/km` (highlighted in primary blue).
* **Tempo Graph**:
  - Top Y-axis label: `1.0`
  - Bottom Y-axis label: `33.0`
  - Curve: A flat blue curve running horizontally near the top at ~5:00 min/km, with sharp downward spikes plummeting down to the 33.0 baseline whenever speed dropped.

### 2.2 The Mathematical Origin of `69:27 min/km`: Inverted Unit Flaw

Why did a 15.0 km/h runner get `69:27 min/km`?
Let's trace the contract of `PaceFormatter.java`:
```java
public class PaceFormatter implements MyFormatter<Number> {
    @Override
    public String format(Number paceN) {
        ...
        double pace = paceN.doubleValue();
        switch (TrainingApplication.getUnit()) {
            case METRIC:
                pace = pace * 1000 / 60; // expects seconds per meter (s/m)!
                break;
            case IMPERIAL:
                pace = pace * BANALService.METER_PER_MILE / 60;
                break;
        }
        int min = (int) Math.floor(pace);
        int sec = (int) Math.floor((pace - min) * 60);
        return min + ":" + (sec <= 9 ? "0" + sec : sec);
    }
}
```
`PaceFormatter.format` expects an input in **seconds per meter** ($s/m$):
$$\text{pace (min/km)} = \text{pace } (s/m) \times 1000 / 60$$

Now inspect `ElevationProfile.kt` (lines 914–918):
```kotlin
val speedStr = if (bSportType == BSportType.RUN) {
    paceFormatter.format_with_units(point.speedMps) // BUG! Passing speed (m/s) instead of pace (s/m)!
} else {
    speedFormatter.format_with_units(point.speedMps)
}
```
In `point.speedMps`, speed is stored in **meters per second** ($m/s$).
At km 10.21:
$$\text{speed} = 4.1667 \text{ m/s} = 15.0 \text{ km/h}$$
True pace:
$$\text{pace} = \frac{1}{\text{speed}} = \frac{1}{4.1667} = 0.240 \text{ s/m} = 240 \text{ s/km} = 4.0 \text{ min/km} \rightarrow \mathbf{4:00 \text{ min/km}}$$
However, `ElevationProfile.kt` passed `point.speedMps` ($4.1667$) directly to `paceFormatter`!
`PaceFormatter` calculated:
$$\text{pace} = 4.1667 \times \frac{1000}{60} = 69.444 \text{ min/km}$$
$$\text{minutes} = \lfloor 69.444 \rfloor = 69$$
$$\text{seconds} = \lfloor (69.444 - 69) \times 60 \rfloor = \lfloor 26.66 \rfloor \approx 27$$
$$\mathbf{69:27 \text{ min/km}}!$$

The exact string `69:27 min/km` shown in the screenshot was caused by passing speed ($m/s$) directly to a formatter expecting pace ($s/m$)!

The identical inversion bug existed in `TelemetryMetricGraph.kt` (lines 148–156):
```kotlin
TelemetryMetricType.PACE -> {
    val secPerUnit = value * 60.0
    val mps = if (unit == MyUnits.METRIC) {
        1000.0 / secPerUnit
    } else {
        BANALService.METER_PER_MILE / secPerUnit
    }
    paceFormatter.format_with_units(mps) // BUG! Converted min/km to m/s, then passed m/s to paceFormatter!
}
```
Here, `value` was already pace in min/km (e.g. 4.0). The code converted it to `mps = 4.167 m/s` and passed `mps` to `paceFormatter`, which multiplied it by $1000/60$, yielding $69:27$ again!
In contrast, correct usages elsewhere in the codebase (e.g., `WorkoutClusterHeatmapScreen.kt:901` and `WorkoutLapsTest.kt:165`) correctly pass `1.0 / speedMps`.

### 2.3 Stopped / Pause Speeds and Implausible Clamping

When a runner pauses, waits at a stoplight, or stops to tie their shoes, GPS speed drops to $0.0 \text{ m/s}$ or near-zero noise ($0.05 \text{ m/s}$).
1. In `ElevationProfile.kt`:
   - If `point.speedMps == 0.0`, `1.0 / 0.0` is `Double.POSITIVE_INFINITY`.
   - Without a stopped-speed threshold, near-zero speed ($0.1 \text{ m/s}$) results in $10 \text{ s/m} = 166 \text{ min/km}$.
   - A realistic minimum running speed threshold is required: speeds below $0.55 \text{ m/s}$ ($< 2.0 \text{ km/h}$, equivalent to slower than $30:00 \text{ min/km}$) represent stopped/walking pauses. For such points, the scrubber should format as `"-- min/km"` (or `"--"`), signaling that running pace is undefined when stopped.
2. In `TelemetryMetricGraph.kt`:
   - Line 115–123 currently performs:
     ```kotlin
     point.speedMps?.takeIf { it > 0.1 }?.let { mps ->
         val secPerKm = 1000.0 / mps
         ...
         (secPerUnit / 60.0).coerceIn(1.0, 30.0)
     }
     ```
   - Any pause where $mps = 0.11 \text{ m/s}$ calculates $151 \text{ min/km}$ and gets clamped to `30.0`.
   - When calculating `dataMax = (max * 1.1).coerceAtLeast(5.0)`, `dataMax` becomes `33.0`!
   - These stopped samples severely distort the vertical dynamic range of the chart.

### 2.4 Y-Axis Decimal Labeling Flaw

In `TelemetryMetricGraph.kt` (lines 343–352):
```kotlin
val maxLabel = if (metricType == TelemetryMetricType.PACE) {
    String.format(Locale.US, "%.1f", dataMin) // Prints "1.0"
} else {
    "${dataMax.toInt()}"
}
val minLabel = if (metricType == TelemetryMetricType.PACE) {
    String.format(Locale.US, "%.1f", dataMax) // Prints "33.0"
} else {
    "${dataMin.toInt()}"
}
```
1. Athletic convention never expresses pace as a decimal float (`1.0` or `33.0` or `5.5`). Pace is universally formatted in minutes and seconds per unit: `mm:ss` (e.g. `4:00`, `5:30`).
2. Y-axis tick labels for Pace must format `dataMin` (fastest, top) and `dataMax` (slowest, bottom) as `mm:ss`.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Goals
1. **Fix Inverted Speed-to-Pace Passing in `ElevationProfile.kt`**:
   - For `bSportType == BSportType.RUN`:
     - If `point.speedMps == null || point.speedMps < 0.55`: format as `paceFormatter.format_with_units(null)` (`"-- min/km"`).
     - Otherwise: clamp speed to realistic running bounds and pass `1.0 / point.speedMps` to `paceFormatter.format_with_units(...)`.
2. **Fix `TelemetryMetricUtils.formatValue` for `PACE`**:
   - Convert `value` (minutes per unit) directly to seconds-per-meter:
     $$spm = \frac{\text{value} \times 60.0}{\text{metersPerUnit}}$$
     and pass $spm$ into `paceFormatter.format_with_units(spm)`.
3. **Format Y-Axis Pace Labels as `mm:ss`**:
   - Provide a clean helper `TelemetryMetricUtils.formatPaceMinutes(paceMinutes: Double): String` returning `String.format(Locale.US, "%d:%02d", min, sec)`.
   - Use this helper for `maxLabel` and `minLabel` in `TelemetryMetricGraph.kt` when `metricType == TelemetryMetricType.PACE`.
4. **Calibrate Pace Dynamic Range in `TelemetryMetricGraph.kt`**:
   - Exclude stopped/near-zero samples ($speedMps < 0.55 \text{ m/s}$ / $> 20.0 \text{ min/km}$) from dominating the chart bounds, ensuring that running pace variations (e.g. 4:00 to 6:30 min/km) occupy the visual canvas effectively.
5. **Add Comprehensive Unit Tests**:
   - Test pace decoding in `ElevationProfile` scrubbing.
   - Test `formatValue` and `formatPaceMinutes` in `TelemetryMetricGraphTest.kt`.
   - Test stopped speed thresholding and clamping.

### 3.2 Out-of-Scope Non-Goals (Scope Bounding)
1. **X-Axis Milestone Overlapping**:
   - Overlapping X-axis milestone ticks visible in the screenshot (`0 km1 km2 km...`) are explicitly tracked in separate Sprint 2026-40.7 backlog ticket **`ATT-1819`** (*[Bug] Fix unreadable x-Axis milestone collisions and text overlapping on HR and Pace graphs*). ATT-1818 strictly bounds its scope to Pace decoding, stopped clamping, and Y-axis labels.
2. **Heart Rate or Power Metric Logic**:
   - Heart rate and cycling power calculations and graphs are unaffected and remain untouched.
3. **Pace Formatter Internal Architecture**:
   - `PaceFormatter.java` contract (accepting $s/m$) is a stable legacy component used across the application. We do NOT alter its signature or core logic; we fix the callers that violate its contract.

---

## 4. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Extends and refines `REQ-UI-206` (*Continuous Telemetry Metric Graphs with Section Headings & Synchronized Multi-Chart Scrubbing Architecture*) under Epic `ATT-111`.
   - New Requirement: **`REQ-UI-219`** (*Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting*).
2. **Historical Origin & Commit Trace**:
   - `ATT-1740` (commit `061a4b35`) introduced `TelemetryMetricGraph.kt` and initial pace conversion logic.
   - `ATT-1391` introduced multi-metric scrubbing in `ElevationProfile.kt`.
3. **Root Reason for Existing Formulation**:
   - In `ElevationProfile.kt`, `speedFormatter.format_with_units(point.speedMps)` was adapted for running by simply switching the formatter instance to `paceFormatter.format_with_units(point.speedMps)`. The developer overlooked that while `SpeedFormatter` expects $m/s$, `PaceFormatter` expects $s/m$ ($1/speed$).
   - In `TelemetryMetricGraph.kt`, Y-axis labels were implemented quickly using `String.format(Locale.US, "%.1f", ...)`.
4. **Preservation of Core Invariants**:
   - Cycling and hiking speeds continue formatting via `SpeedFormatter` in `km/h` or `mph`.
   - Imperial units (`min/mile`) remain 100% supported via `TrainingApplication.getUnit()`.
   - Synchronized scrubbing via `selectedDistance` and global horizontal zoom alignment remain intact.

---

## 5. Architectural Impact Analysis

* **`com.atrainingtracker.trainingtracker.ui.map.ElevationProfile.kt`**:
  - In `ScrubbingInfoCard` (lines 914–925):
    Replace:
    ```kotlin
    val speedStr = if (bSportType == BSportType.RUN) {
        paceFormatter.format_with_units(point.speedMps)
    } else {
        speedFormatter.format_with_units(point.speedMps)
    }
    ```
    With:
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
* **`com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraph.kt`**:
  - In `TelemetryMetricUtils.formatValue`:
    Correct $s/m$ calculation before invoking `paceFormatter.format_with_units(spm)`.
  - Add `TelemetryMetricUtils.formatPaceMinutes(paceMinutes: Double): String`.
  - In `TelemetryMetricGraph`:
    - Use `formatPaceMinutes(dataMin)` and `formatPaceMinutes(dataMax)` for Y-axis labels.
    - Bound `(dataMin, dataMax)` for `PACE` such that stopped samples ($< 0.55 \text{ m/s}$) do not blow up the vertical scale to 33.0.
* **Localization Impact**:
  - Zero new strings required; standard `PaceFormatter` and numeric formatters reuse existing unit strings and symbols.

---

## 6. Verification & Test Strategy

1. **Targeted Unit Tests (`TelemetryMetricGraphTest.kt`)**:
   - `testFormatValue_paceFormattingMetricAndImperial`: Verify that a 4.0 min/km pace formats as `"4:00 min/km"` (metric) and `"4:00 min/mile"` (imperial), not `69:27`.
   - `testFormatPaceMinutes_standardAndEdgeCases`: Verify `formatPaceMinutes(4.0)` -> `"4:00"`, `formatPaceMinutes(4.5)` -> `"4:30"`, `formatPaceMinutes(6.25)` -> `"6:15"`.
   - `testExtractMetricValue_paceStoppedSpeedThreshold`: Verify that speeds $< 0.55 \text{ m/s}$ return `null` or clamped values, avoiding division-by-zero or giant paces.
2. **Targeted Unit Tests (`ElevationProfileLayoutTest.kt` / `ElevationProfileScrubbingTest.kt`)**:
   - Verify that running workouts pass `1.0 / speedMps` and format stopped speeds as `"-- min/km"`.
3. **Clean-Room Regression**:
   - `./gradlew testDebugUnitTest` across the entire test suite.

---

## 7. Next Steps

1. Transition subtask `ATT-1880` to `Review` and run Gate 1 review agent audit (`tools/review_agent.py audit ATT-1880`).
2. Proceed to Stage 2 (Requirement & Test Specification): define `REQ-UI-219` and `TST-UI-173`.
