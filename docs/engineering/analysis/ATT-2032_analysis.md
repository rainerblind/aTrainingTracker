# Stage 1 Analysis: ATT-2032 - Unify X-Axis Domain & Time-vs-Distance Calculation Across Telemetry Components

**Ticket**: [ATT-2032](https://rainerblind.atlassian.net/browse/ATT-2032)  
**Sub-task**: [ATT-2242](https://rainerblind.atlassian.net/browse/ATT-2242) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Branch**: `feature/ATT-2032`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

In the Aftermath inspection module (`MapDetailLayout.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`), visual telemetry charts can be rendered along either the **Spatial Domain** (Distance in meters/miles) or the **Temporal Domain** (Elapsed Time in seconds) according to athlete preferences (`TuningConfig.elevationXAxisDomain` and `tuningConfig.telemetryXAxisDomain`, REQ-UI-233) or activity characteristics (Trackless stationary sessions, REQ-UI-235).

However, the evaluation of whether a chart should effectively render in the Time Domain versus Distance Domain, as well as the calculation of the maximum horizontal domain span (`totalSpan`), has become fragmented across three distinct composable files with diverging edge-case rules:
1. **Inconsistent Time-Domain Checking**:
   - `ElevationProfile.kt`:
     ```kotlin
     val isTimeDomain = xAxisDomain == ProfileXAxisDomain.TIME && cachedData.totalTimeSec > 0
     ```
     Correctly validates that the track contains valid positive timestamps (`totalTimeSec > 0`).
   - `TelemetryMetricGraph.kt`:
     ```kotlin
     val isTrackless = remember(pathPoints) {
         (pathPoints.lastOrNull()?.distance ?: 0.0) == 0.0 && (pathPoints.lastOrNull()?.timeSec ?: 0L) > 0L
     }
     val isTimeDomain = xAxisDomain == ProfileXAxisDomain.TIME || isTrackless
     ```
     Fails to verify whether timestamps actually exist when `xAxisDomain == ProfileXAxisDomain.TIME`. For GPS tracks lacking timestamps or corrupt samples where `timeSec == 0`, `isTimeDomain` evaluates to `true`, resulting in a zero-span or 00:00 tick canvas collapse.
   - `MapDetailLayout.kt`:
     ```kotlin
     val isTrackless = (activeScrubPath?.lastOrNull()?.distance ?: 0.0) == 0.0 && (activeScrubPath?.lastOrNull()?.timeSec ?: 0) > 0
     val activeTelemetryDomain = if (isTrackless) ProfileXAxisDomain.TIME else tuningConfig.telemetryXAxisDomain
     val isElevationTimeDomain = (tuningConfig.elevationXAxisDomain == ProfileXAxisDomain.TIME || isTrackless) && (activeScrubPath?.lastOrNull()?.timeSec ?: 0) > 0
     val elevationTotalSpan = if (isElevationTimeDomain) (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble() else (activeScrubPath?.lastOrNull()?.distance ?: 0.0)
     val telemetryTotalSpan = if (activeTelemetryDomain == ProfileXAxisDomain.TIME) (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble() else (activeScrubPath?.lastOrNull()?.distance ?: 0.0)
     val totalSpan = if (isElevationTimeDomain || isTrackless) (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble() else (activeScrubPath?.lastOrNull()?.distance ?: 0.0)
     ```
     `telemetryTotalSpan` fails to guard `(activeScrubPath?.lastOrNull()?.timeSec ?: 0) > 0`, leading to a subtle divergence between elevation span and telemetry span when corrupt or non-timestamped tracks are displayed. Furthermore, `totalSpan` ignores `telemetryTotalSpan` when `showElevationProfile == false`.

2. **Diverging Span Calculation & Coercion**:
   - `ElevationProfile.kt` uses uncoerced `cachedData.totalTimeSec.toDouble()` vs `cachedData.totalDist`.
   - `TelemetryMetricGraph.kt` coerces values with `.coerceAtLeast(1.0)`.
   - `MapDetailLayout.kt` uses uncoerced raw values with `.toDouble()`.

3. **Sub-optimal Scrubbing Lookups**:
   - `TelemetryMetricGraph.kt` continues to perform linear $O(N)$ searches (`minByOrNull { abs(it.distance - currentDistance) }`) for cursor positioning, despite the existence of $O(\log N)$ binary search (`TelemetryMetricUtils.findNearestPoint`).

---

## 2. Root Cause Analysis (Forensic Investigation)

The root cause of these discrepancies is evolutionary feature layering across four successive sprints without a single unifying domain mathematical abstraction:
1. **Sprint 2026-40.5 (`ATT-1391` / `REQ-UI-201`)**: Introduced configurable `ProfileXAxisDomain` on `ElevationProfile.kt` with adaptive time ticks in `ElevationProfileZoomMath.kt`.
2. **Sprint 2026-40.6 (`ATT-1740` / `REQ-UI-206`)**: Introduced continuous telemetry charts (`TelemetryMetricGraph.kt`), duplicating domain checking logic and introducing a `.coerceAtLeast(1.0)` fallback.
3. **Sprint 2026-40.10 (`ATT-1986` / `REQ-UI-233`)**: Decoupled `elevationXAxisDomain` and `telemetryXAxisDomain` into independent DataStore settings, adding cross-domain synchronization in `MapDetailLayout.kt`.
4. **Sprint 2026-40.11 (`ATT-2006` / `REQ-UI-235`)**: Introduced trackless indoor workouts (where `distance == 0.0` and `timeSec > 0`), forcing time-domain rendering in `MapDetailLayout.kt` and `TelemetryMetricGraph.kt`, but with duplicated, non-centralized boolean expressions.

Because each sprint incrementally patched local `isTimeDomain` and `isTrackless` flags, subtle edge-case disparities emerged:
- If a route track contains zero timestamps (`timeSec == 0`) and the athlete selects Time Domain in tuning settings:
  - `ElevationProfile` falls back to Distance Domain because `totalTimeSec <= 0`.
  - `TelemetryMetricGraph` remains in Time Domain because it only checks `xAxisDomain == ProfileXAxisDomain.TIME`, rendering a flat collapsed graph spanning `[0.0, 1.0]` seconds with invalid `0:00` ticks.
  - `MapDetailLayout` computes `telemetryTotalSpan = 0.0` while `elevationTotalSpan = totalDistance`, causing viewport sync mismatch between the global toolbar and stacked graphs.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Create a canonical domain evaluation engine (`ProfileDomainMath`) in `com.atrainingtracker.trainingtracker.ui.map`:
    - `fun isTracklessWorkout(points: List<PathPoint>?): Boolean`
    - `fun isTracklessWorkout(totalDistance: Double, totalTimeSec: Long): Boolean`
    - `fun isEffectiveTimeDomain(domain: ProfileXAxisDomain, points: List<PathPoint>?): Boolean`
    - `fun isEffectiveTimeDomain(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Boolean`
    - `fun calculateTotalSpan(domain: ProfileXAxisDomain, points: List<PathPoint>?): Double`
    - `fun calculateTotalSpan(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Double`
  * Refactor `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` to consume `ProfileDomainMath` exclusively.
  * Guarantee corrupt / zero-timestamp track safety: fall back gracefully to Distance domain if elapsed time is zero.
  * Optimize remaining $O(N)$ linear scans in `TelemetryMetricGraph.kt` to use `TelemetryMetricUtils.findNearestPoint`.
  * Comprehensive unit test suite covering `ProfileDomainMathTest.kt` across edge cases (empty list, null, zero duration, zero distance, stationary trackless, normal track, corrupt timestamps).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to DataStore preferences schema (`TuningPreferencesDataStore.kt`).
  * No alteration of visual styling, colors, paddings, or typography of charts.
  * No changes to database storage (`WorkoutSamples.db`) or sensor telemetry ingestion pipelines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Archaeology of Prior Requirements:
1. **REQ-UI-201** (`ATT-1391`, Sprint 2026-40.5):
   - Defined `ProfileXAxisDomain { DISTANCE, TIME }` and adaptive time step generation (`ElevationProfileZoomMath.calculateAdaptiveTimeStep`).
2. **REQ-UI-206** (`ATT-1740`, Sprint 2026-40.6):
   - Mandated pixel-perfect alignment and synchronized scrubbing across stacked telemetry graphs matching the active horizontal axis domain.
3. **REQ-UI-233** (`ATT-1986`, Sprint 2026-40.10):
   - Decoupled `elevationXAxisDomain` and `telemetryXAxisDomain` while preserving cross-domain scrubbing synchronization.
4. **REQ-UI-235** (`ATT-2006`, Sprint 2026-40.11):
   - Handled trackless workouts (`distance == 0.0 && timeSec > 0`) requiring time-domain representation and collapsing map viewports.

### Net-New Requirement Formulation:
* **Requirement**: `REQ-UI-261` (*Canonical Horizontal Domain Evaluation & Total Span Engine (`ProfileDomainMath`)*).
* **Rationale**: Replaces duplicated and diverging domain branching with a single mathematical utility. Preserves all invariants of `REQ-UI-201`, `REQ-UI-206`, `REQ-UI-233`, and `REQ-UI-235` while eliminating zero-timestamp rendering bugs and $O(N)$ scrubbing bottlenecks.

---

## 5. Architectural Strategy & High-Level Solution

### Component Design (`ProfileDomainMath.kt`):
```kotlin
package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain

/**
 * Pure mathematical utility encapsulating canonical domain evaluation (Distance vs. Time)
 * and horizontal span calculations across all Aftermath telemetry visualizers.
 * (REQ-UI-261)
 */
object ProfileDomainMath {

    /**
     * Determines whether a workout is stationary/trackless (no GPS distance but valid elapsed duration).
     */
    fun isTracklessWorkout(points: List<PathPoint>?): Boolean {
        val last = points?.lastOrNull() ?: return false
        return isTracklessWorkout(totalDistance = last.distance, totalTimeSec = last.timeSec)
    }

    fun isTracklessWorkout(totalDistance: Double, totalTimeSec: Long): Boolean {
        return totalDistance <= 0.0 && totalTimeSec > 0L
    }

    /**
     * Determines whether the effective horizontal domain should be TIME.
     * Enforces that stationary workouts always resolve to TIME, and configured TIME domain
     * safely falls back to DISTANCE if the track has zero elapsed duration (corrupt/missing timestamps).
     */
    fun isEffectiveTimeDomain(domain: ProfileXAxisDomain, points: List<PathPoint>?): Boolean {
        val last = points?.lastOrNull() ?: return false
        return isEffectiveTimeDomain(domain, totalDistance = last.distance, totalTimeSec = last.timeSec)
    }

    fun isEffectiveTimeDomain(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Boolean {
        if (isTracklessWorkout(totalDistance, totalTimeSec)) return true
        return domain == ProfileXAxisDomain.TIME && totalTimeSec > 0L
    }

    /**
     * Calculates the total horizontal span in effective domain units (meters or seconds).
     */
    fun calculateTotalSpan(domain: ProfileXAxisDomain, points: List<PathPoint>?): Double {
        val last = points?.lastOrNull() ?: return 0.0
        return calculateTotalSpan(domain, totalDistance = last.distance, totalTimeSec = last.timeSec)
    }

    fun calculateTotalSpan(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Double {
        val isTime = isEffectiveTimeDomain(domain, totalDistance, totalTimeSec)
        return if (isTime) {
            totalTimeSec.toDouble().coerceAtLeast(0.0)
        } else {
            totalDistance.coerceAtLeast(0.0)
        }
    }
}
```

### Component Consumption:
1. **`MapDetailLayout.kt`**:
   - Replace manual boolean logic for `isTrackless`, `isElevationTimeDomain`, `activeTelemetryDomain`, `elevationTotalSpan`, `telemetryTotalSpan`, and `totalSpan` with direct calls to `ProfileDomainMath`.
   - Ensure `totalSpan` cleanly switches based on whether `showElevationProfile` is true (using `elevationTotalSpan`) or false (using `telemetryTotalSpan`).
2. **`ElevationProfile.kt`**:
   - Replace `val isTimeDomain = xAxisDomain == ProfileXAxisDomain.TIME && cachedData.totalTimeSec > 0` with `ProfileDomainMath.isEffectiveTimeDomain(xAxisDomain, totalDistance = cachedData.totalDist, totalTimeSec = cachedData.totalTimeSec)`.
   - Replace `totalSpan` computation with `ProfileDomainMath.calculateTotalSpan(...)`.
3. **`TelemetryMetricGraph.kt`**:
   - Replace duplicated `isTrackless` and `isTimeDomain` with `ProfileDomainMath.isTracklessWorkout(pathPoints)` and `ProfileDomainMath.isEffectiveTimeDomain(xAxisDomain, pathPoints)`.
   - Replace `totalSpan` computation with `ProfileDomainMath.calculateTotalSpan(xAxisDomain, pathPoints)`.
   - Replace linear `pathPoints.minByOrNull { abs(it.distance - currentDistance) }` with `TelemetryMetricUtils.findNearestPoint`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Regression**: All 1550 unit tests must continue to pass without failure.
  2. **Domain Parity**: Time-domain ticks and distance-domain ticks must remain identical for valid tracks.
  3. **Trackless Stability**: Stationary sessions must continue collapsing the map and displaying continuous time-domain telemetry.
  4. **Human Decision Gate**: Parent ticket `ATT-2032` must NOT be moved to `Erledigt` autonomously.
* **Risk Rating**: **LOW**
  - Pure mathematical domain logic extraction.
  - Zero database schema or UI layout hierarchy alterations.
  - High testability via deterministic unit tests.
