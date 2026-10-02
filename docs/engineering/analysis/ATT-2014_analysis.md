# Stage 1 Analysis: ATT-2014 - Configurable Minimum Pace Ceiling in Expert Settings (Default 3:00 min/km)

**Ticket**: [ATT-2014](https://rainerblind.atlassian.net/browse/ATT-2014)  
**Sub-task**: [ATT-2102](https://rainerblind.atlassian.net/browse/ATT-2102) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Branch**: `feature/ATT-2014`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

In the Aftermath workout inspection screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapScreen.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), and [TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt)), running workouts (`bSportType == BSportType.RUN`) render a continuous running pace curve ("Tempo").

Currently, [TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt) hardcodes a static pace clamp:
```kotlin
(secPerUnit / 60.0).coerceIn(1.5, 20.0) // 1.5 min = 1:30 min/km = 40 km/h!
```
and:
```kotlin
val paceMin = (min * 0.95).coerceAtLeast(1.5)
val paceMax = (max * 1.05).coerceAtMost(20.0)
paceMin to paceMax.coerceAtLeast(paceMin + 1.0)
```

When transient GPS velocity spikes occur (common upon GPS acquisition, bridge/tree canopy crossings, or pause resumption), raw instantaneous velocity briefly spikes to high speeds. With the hardcoded `1.5` bound, the pace graph scales its top Y-axis label to **1:30** (1:30 min/km = 40 km/h):
1. **Athletic Invariance**: A 1:30 min/km pace is physiologically impossible for human distance runners and should never appear on a running analysis chart.
2. **Dynamic Range Compression**: With an upper bound of 1:30 and a lower bound of 20:00, the vertical range spans 18.5 min/km. The athlete's actual running pace curve (typically 4:30 – 6:30 min/km) is squashed into an unreadable, flat horizontal line in the middle of the graph.

The goal of ATT-2014 is to introduce a configurable "Minimum Pace Ceiling" setting in **Expert Settings** (Section 4: Aftermath & Analyse) defaulting to **3:00 min/km** (and corresponding Imperial conversion ~4:50 min/mi), which parameterizes `TelemetryMetricGraph` data clamping and Y-axis bounding so that implausible spikes faster than the ceiling are filtered out.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Origin of Hardcoded Bounds**:
   - In Sprint 2026-40.7 (`ATT-1818` / `REQ-UI-219`), pace formatting was upgraded to athletic `mm:ss` syntax. To protect against division-by-zero ($0 \text{ m/s}$) and infinite values, hardcoded clamping `coerceIn(1.5, 20.0)` and `(min * 0.95).coerceAtLeast(1.5)` were introduced.
   - The choice of `1.5` (1:30 min/km) was an arbitrary mathematical guard against infinity rather than an athletic boundary.
2. **Current Scaling Pipeline in `TelemetryMetricGraph.kt`**:
   - `extractMetricValue`: converts speed in m/s to decimal minutes per kilometer (`secPerKm / 60.0`), then applies `.coerceIn(1.5, 20.0)`.
   - `calculateBounds`: calculates `paceMin = (min * 0.95).coerceAtLeast(1.5)`. Because pace is plotted inverted (faster near top, slower near bottom), `paceMin` becomes the top Y-axis label rendered via `formatPaceMinutes(bounds.first)`.
   - Any sample with speed $\ge 11.1\text{ m/s}$ (e.g. initial GPS lock glitch) results in `paceMin = 1.5`, forcing the top Y-axis label to "1:30".
3. **Architecture of Expert Settings**:
   - Expert Settings are centrally managed by `TuningPreferencesDataStore.kt` using Jetpack DataStore, exposing an immutable `TuningConfig` flow.
   - Section 4 of `AdvancedTuningDialog.kt` (`AftermathAnalysisSection`) already configures Aftermath preferences (`elevationXAxisDomain` and `telemetryXAxisDomain`).
   - `MapDetailLayout.kt` already collects `val tuningConfig by tuningDataStore.tuningConfigFlow.collectAsState()`, making the active tuning configuration readily available to pass to child graph composables.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Net-new requirement (`REQ-UI-243`), refining and parameterizing `REQ-UI-219` (*Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting*) and `REQ-UI-206` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. **Historical Origin & Commit Trace**:
   - Commit `7a8c4d21` (`ATT-1818`, Sprint 2026-40.7) introduced `REQ-UI-219` with a hardcoded `1.5` min/km pace bound.
3. **Root Reason for Existing Formulation**:
   - In `ATT-1818`, the author clamped pace to `[1.5, 20.0]` (1:30 min/km) to prevent division-by-zero on extreme GPS spikes. However, 1:30 min/km is an athletically impossible running speed that squashes the readable dynamic range of typical distance running curves (4:30 – 6:30 min/km) and displays an unsightly `1:30` label on the top Y-axis.
4. **Preservation of Core Invariants**:
   - Inverted pace axis plotting (faster pace higher, slower pace lower).
   - `mm:ss` label formatting (`formatPaceMinutes`).
   - Stopped/pause filtering (<0.55 m/s).
   - Metric vs. Imperial unit conversion parity (`MyUnits.METRIC` vs `MyUnits.IMPERIAL`).
   - Synchronized multi-chart scrubbing (`selectedDistance`).
   - 100% 9-language translation parity.

---

## 4. Scope Bounding & Proposed Architecture

### 4.1 In-Scope Components
1. **Preferences Layer (`TuningPreferencesDataStore.kt`)**:
   - Constants in `TuningPreferencesDefaults`:
     - `DEFAULT_PACE_CEILING_MIN_KM = 3.0f` (3:00 min/km)
     - `MIN_PACE_CEILING_MIN_KM = 2.0f` (2:00 min/km)
     - `MAX_PACE_CEILING_MIN_KM = 6.0f` (6:00 min/km)
   - Add `val paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM` to `TuningConfig`.
   - Add `KEY_PACE_CEILING_MIN_KM` in `TuningPreferencesDataStore`, read and persist with `.coerceIn(MIN_PACE_CEILING_MIN_KM, MAX_PACE_CEILING_MIN_KM)`, and include in `ALL_KEYS` for factory reset.
2. **Settings UI (`AdvancedTuningDialog.kt`)**:
   - In `AftermathAnalysisSection` (Section 4: Aftermath & Analyse):
     - Add `TuningSliderItem` for the Minimum Pace Ceiling slider:
       - Range: `2.0f..6.0f` with `steps = 15` (16 steps of 0.25 min / 15 seconds: 2:00, 2:15, 2:30, 2:45, 3:00, 3:15, ... 6:00).
       - Value text: formatted with active units (`3:00 min/km` in Metric, `4:50 min/mi` in Imperial).
       - Default text: formatted default (`Default: 3:00 min/km` / `Default: 4:50 min/mi`).
3. **Graph Component (`TelemetryMetricGraph.kt`)**:
   - Add parameter `paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM`.
   - In `extractMetricValue` and `calculateBounds`:
     - Compute unit-adjusted ceiling:
       ```kotlin
       val effectiveCeiling = if (unit == MyUnits.METRIC) {
           paceCeilingMinKm.toDouble()
       } else {
           paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)
       }
       ```
     - Data clamping: `(secPerUnit / 60.0).coerceIn(effectiveCeiling, 20.0)`.
     - Bounds clamping: `val paceMin = (min * 0.95).coerceAtLeast(effectiveCeiling)`.
4. **Layout Integration (`MapDetailLayout.kt`)**:
   - Forward `paceCeilingMinKm = tuningConfig.paceCeilingMinKm` to `TelemetryMetricGraph` when `metricType == TelemetryMetricType.PACE`.
5. **Localization (All 9 Locales)**:
   - Define `tuning_pace_ceiling_title` and `tuning_pace_ceiling_desc` in `values*/strings.xml`.

### 4.2 Out-of-Scope (Non-Goals)
- No SQLite database schema changes (persisted purely in Jetpack DataStore).
- No changes to Speed formatting for non-running sports (cycling, hiking).
- No changes to Elevation Profile or Zoom Math.

---

## 5. Living Documentation & Traceability Matrix

| Artifact | Identifier | Title / Scope | Target Deliverable |
| :--- | :--- | :--- | :--- |
| **Requirement** | `REQ-UI-243` | Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default (3:00 min/km) | `docs/requirements.md` |
| **Test Specification** | `TST-UI-202` | Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings Verification | `docs/tests.md` |

---

## 6. Review Gate Audit Readiness

- Problem domain completely analyzed and root cause confirmed in source code.
- Chesterton's Fence archaeology populated per `REQ-PRO-022`.
- Invariants, bounds, and unit conversions rigorously calculated.
- Ready for subtask description synchronization, `in_review` transition, and Gate 1 independent audit.
