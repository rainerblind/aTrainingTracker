# Stage 2: Requirement & Test Specification - ATT-1818: Fix pace decoding, clamp implausible scrubbing values, and format Y-axis pace labels as mm:ss

**Ticket**: [ATT-1818](https://rainerblind.atlassian.net/browse/ATT-1818)  
**Sub-task**: [ATT-1881](https://rainerblind.atlassian.net/browse/ATT-1881) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-219` (*Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting*)  
**Test Spec ID**: `TST-UI-173`  
**Branch**: `feature/ATT-1818`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-219)

### 1.1 Problem Statement & Rationale

In `aTrainingTracker`, inspecting running activities in Aftermath (`TrackOnMapScreen`, `MapDetailLayout.kt`, and `WorkoutSummary.kt`) revealed severe defects in Pace presentation (`screenshot_pace_scrubbing_and_xaxis.png`):
1. **Inverted Speed-to-Pace Passing**: `ElevationProfile.kt` and `TelemetryMetricGraph.kt` passed speed in $m/s$ directly into `PaceFormatter`, which expects pace in seconds per meter ($s/m$). For a runner at 4.167 m/s (15.0 km/h, 4:00 min/km), this inverted conversion calculated:
   $$4.1667 \times 1000 / 60 = 69.444 \rightarrow \mathbf{69:27 \text{ min/km}}$$
2. **Missing Stopped-Speed Clamping**: Pauses and near-zero speeds ($speedMps < 0.55 \text{ m/s}$ / $< 2.0 \text{ km/h}$) produced division-by-zero or extreme pace values ($> 30.0 \text{ min/km}$), blowing up the vertical dynamic range of the Tempo graph to `1.0 .. 33.0` and squeezing the actual running curve into a tiny band at the top.
3. **Unusable Decimal Y-Axis Labels**: Y-axis labels in `TelemetryMetricGraph` printed raw decimal floats (`1.0`, `33.0`, `5.5`) rather than athletic pace notation in minutes and seconds (`mm:ss`, such as `4:00`, `6:00`).

The objective of `REQ-UI-219` is to enforce robust pace decoding across all scrubbers and formatters, clamp/filter stopped speeds below $0.55 \text{ m/s}$, format Y-axis pace labels in `mm:ss`, and ensure the Pace curve effectively utilizes the vertical plot area.

---

### 1.2 Functional & Architectural Requirements

1. **Scrubbing Pace Decoding & Stopped Clamping (`ElevationProfile.kt`)**:
   - In `ElevationProfile.kt` within `ScrubbingInfoCard` (for `bSportType == BSportType.RUN`):
     - When `point.speedMps == null || point.speedMps < 0.55`: the component SHALL invoke `paceFormatter.format_with_units(null)`, displaying `"-- min/km"` (or `"-- min/mile"` in Imperial), eliminating division-by-zero, infinity, and implausible walking paces.
     - When `point.speedMps >= 0.55`: the component SHALL convert speed to seconds per meter ($s/m = 1.0 / point.speedMps$) and invoke `paceFormatter.format_with_units(1.0 / point.speedMps)`.
     - For non-running sports (`bSportType != BSportType.RUN`), speed SHALL continue formatting via `speedFormatter.format_with_units(point.speedMps)` in `km/h` or `mph`.

2. **Telemetry Metric Graph Value Formatting (`TelemetryMetricUtils.formatValue`)**:
   - In `TelemetryMetricUtils.formatValue` for `TelemetryMetricType.PACE`:
     - The function SHALL convert `value` (which is stored in minutes per unit) to seconds per meter ($s/m$):
       $$\text{spm} = \frac{\text{value} \times 60.0}{\text{metersPerUnit}}$$
       where $\text{metersPerUnit} = 1000.0$ for `MyUnits.METRIC` and $\text{metersPerUnit} = \text{BANALService.METER_PER_MILE}$ for `MyUnits.IMPERIAL`.
     - The computed $\text{spm}$ SHALL be passed to `paceFormatter.format_with_units(spm)`.
     - When `value == null`, it SHALL return `"--"`.

3. **Athletic Pace Y-Axis Formatting (`formatPaceMinutes` / `mm:ss`)**:
   - `TelemetryMetricUtils` SHALL provide a public helper:
     ```kotlin
     fun formatPaceMinutes(paceMinutes: Double): String {
         val totalSec = (paceMinutes * 60.0).roundToInt().coerceAtLeast(0)
         val min = totalSec / 60
         val sec = totalSec % 60
         return String.format(Locale.US, "%d:%02d", min, sec)
     }
     ```
   - In `TelemetryMetricGraph.kt`, when `metricType == TelemetryMetricType.PACE`:
     - The top Y-axis label (`maxLabel`, corresponding to `dataMin`, the fastest pace) SHALL be rendered as `TelemetryMetricUtils.formatPaceMinutes(dataMin)`.
     - The bottom Y-axis label (`minLabel`, corresponding to `dataMax`, the slowest pace) SHALL be rendered as `TelemetryMetricUtils.formatPaceMinutes(dataMax)`.
     - Raw float formatting (`String.format(Locale.US, "%.1f", ...)`) SHALL NOT be used for Pace.

4. **Dynamic Range Bounding & Stopped Filtering (`TelemetryMetricGraph.kt`)**:
   - In `TelemetryMetricUtils.extractMetricValue` for `TelemetryMetricType.PACE`:
     - Point speeds below $0.55 \text{ m/s}$ ($< 1.98 \text{ km/h}$, slower than $30:00 \text{ min/km}$) SHALL return `null` (treated as stopped/paused).
     - Running paces SHALL be bounded within realistic athletic limits: coerced to `[1.5, 20.0]`.
   - In `TelemetryMetricGraph.kt` dynamic range calculation:
     - For `TelemetryMetricType.PACE`:
       `dataMin` (fastest) and `dataMax` (slowest) SHALL be derived from active running points with a minimum spread of at least 1.0 min/km:
       $$\text{dataMin} = (\min \times 0.95).coerceAtLeast(1.5)$$
       $$\text{dataMax} = (\max \times 1.05).coerceAtMost(20.0)$$
       $$\text{dataMax} = \max(\text{dataMax}, \text{dataMin} + 1.0)$$
     - This ensures that pause samples do not blow up the vertical scale to 33.0 min/km and the active running curve utilizes the full plot height.

5. **Preservation of Core Invariants**:
   - Inversion logic in `TelemetryMetricGraph` (faster pace plotted higher near `topPaddingPx`, slower pace plotted lower near `topPaddingPx + chartHeightPx`) remains strictly preserved.
   - Synchronized scrubbing via `selectedDistance` and global horizontal zoom alignment across stacked charts remain 100% intact.
   - Non-running sports (cycling, hiking) remain unaffected and continue using `SpeedFormatter`.
   - 100% 9-language localization parity is maintained.

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

1. **Original Requirement ID & Target**:
   - Extends and refines `REQ-UI-206` (*Continuous Telemetry Metric Graphs with Section Headings & Synchronized Multi-Chart Scrubbing Architecture*) under Epic `ATT-111`.
   - Net-new requirement: **`REQ-UI-219`** (*Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting*).
2. **Historical Origin & Commit Trace**:
   - Commit `061a4b35` (`ATT-1740`) introduced `TelemetryMetricGraph.kt` and initial pace conversion.
   - Ticket `ATT-1391` introduced multi-metric scrubbing in `ElevationProfile.kt`.
3. **Root Reason for Existing Formulation**:
   - In `ElevationProfile.kt`, `speedFormatter.format_with_units(point.speedMps)` was adapted for running by simply substituting `paceFormatter.format_with_units(point.speedMps)`. The developer overlooked that while `SpeedFormatter` expects $m/s$, `PaceFormatter` expects $s/m$ ($1/speed$).
   - In `TelemetryMetricGraph.kt`, Y-axis labels were implemented quickly using `String.format(Locale.US, "%.1f", ...)`.
4. **Preservation of Core Invariants**:
   - `PaceFormatter.java` contract (accepting $s/m$) is preserved verbatim.
   - Imperial units (`min/mile`) and metric units (`min/km`) are strictly preserved.
   - Synchronized scrubbing and horizontal padding alignment across charts remain 100% preserved.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (ElevationProfile Scrubbing Pace Decoding)**:
  * *Given* an athlete inspecting a completed running workout (`bSportType == BSportType.RUN`) in `ElevationProfile`,
  * *When* scrubbing at a point where speed is 4.167 m/s (15 km/h),
  * *Then* the scrubbing card SHALL display `"4:00 min/km"` (in Metric) or `"6:26 min/mile"` (in Imperial), and SHALL NOT display `"69:27 min/km"`.

* **Criterion 2 (ElevationProfile Stopped-Speed Clamping)**:
  * *Given* an athlete scrubbing at a point where the runner stopped or paused ($speedMps < 0.55 \text{ m/s}$ or null),
  * *When* the scrubbing card renders,
  * *Then* the pace text SHALL render as `"-- min/km"` (or `"-- min/mile"`), and SHALL NOT display infinity, division errors, or giant pace values.

* **Criterion 3 (TelemetryMetricUtils formatValue Correctness)**:
  * *Given* `TelemetryMetricUtils.formatValue` called with `metricType = PACE` and `value = 4.0` (min/km),
  * *When* evaluated under Metric units,
  * *Then* the returned string SHALL equal `"4:00 min/km"`.
  * *Given* `TelemetryMetricUtils.formatValue` called with `metricType = PACE` and `value = 8.5` (min/mile),
  * *When* evaluated under Imperial units,
  * *Then* the returned string SHALL equal `"8:30 min/mile"`.

* **Criterion 4 (Y-Axis Pace Labels in mm:ss Format)**:
  * *Given* `TelemetryMetricGraph` rendering a running workout (`metricType == PACE`),
  * *When* inspecting the Y-axis labels on the left of the chart,
  * *Then* the top label (`maxLabel`) and bottom label (`minLabel`) SHALL be formatted in athletic `mm:ss` format (e.g. `"4:00"` and `"6:00"`), and SHALL NOT contain raw decimal floats (`"1.0"`, `"33.0"`).

* **Criterion 5 (Dynamic Range Bounding on Pace Graph)**:
  * *Given* a running workout with occasional stops where speed dropped below 0.55 m/s,
  * *When* the Pace curve is plotted in `TelemetryMetricGraph`,
  * *Then* stopped points SHALL NOT expand `dataMax` to 33.0, and the running pace curve SHALL utilize the vertical height of the chart dynamically.

---

## 2. Test Specification (TST-UI-173)

### 2.1 Scope & Test Categories

| Test ID | Class / File | Description | Target |
| :--- | :--- | :--- | :--- |
| **TST-UI-173.1** | `TelemetryMetricGraphTest.kt` | `testFormatValue_paceFormattingMetricAndImperial` | Validates `formatValue` for Pace in Metric (`min/km`) and Imperial (`min/mile`) |
| **TST-UI-173.2** | `TelemetryMetricGraphTest.kt` | `testFormatPaceMinutes_standardAndEdgeCases` | Validates `formatPaceMinutes` converts decimal minutes to `mm:ss` |
| **TST-UI-173.3** | `TelemetryMetricGraphTest.kt` | `testExtractMetricValue_paceStoppedThreshold` | Validates speeds $< 0.55 \text{ m/s}$ return `null` and running speeds calculate correct pace |
| **TST-UI-173.4** | `TelemetryMetricGraphTest.kt` | `testTelemetryMetricGraph_paceYAxisLabelsAreMmSs` | Validates `TelemetryMetricGraph.kt` renders Y-axis labels via `formatPaceMinutes` |
| **TST-UI-173.5** | `ElevationProfileScrubbingTest.kt` | `testScrubbingPace_decodingAndStoppedThreshold` | Validates `ElevationProfile` scrubbing passes $1.0/speed$ and clamps stopped speeds to `"-- min/km"` |
| **TST-UI-173.6** | Full Test Suite | Clean-Room Regression (`./gradlew testDebugUnitTest`) | Validates 0 regressions across all 720+ tests |

---

### 2.2 Traceability Matrix

| Requirement | Test ID | Verification Level | Target Artifacts | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-219` (Item 1: Scrubbing Pace Decoding & Stopped Clamping) | `TST-UI-173.5` | Unit / UI Contract | `ElevationProfile.kt` | Planned |
| `REQ-UI-219` (Item 2: formatValue Correctness) | `TST-UI-173.1` | Unit Test | `TelemetryMetricGraph.kt` | Planned |
| `REQ-UI-219` (Item 3: Athletic mm:ss Pace Labels) | `TST-UI-173.2`, `TST-UI-173.4` | Unit / Static Contract | `TelemetryMetricGraph.kt` | Planned |
| `REQ-UI-219` (Item 4: Dynamic Range & Stopped Filtering) | `TST-UI-173.3` | Unit Test | `TelemetryMetricGraph.kt` | Planned |
| `REQ-UI-219` (Item 5: Invariants & Regression) | `TST-UI-173.6` | Clean-Room Suite | All Modules | Planned |

---

## 3. Localization Verification

* `REQ-UI-219` introduces zero new string resource keys.
* Units (`min/km`, `min/mile`, `km/h`, `mph`) are managed via `PaceFormatter` and `SpeedFormatter`, which are verified across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* Localization parity score remains at **100%**.
