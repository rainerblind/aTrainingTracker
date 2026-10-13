# Stage 1 Analysis: ATT-2873 - Optimize in-ride fork route detection performance via spatial bounding-box rejection and GPS throttling

**Ticket**: [ATT-2873](https://rainerblind.atlassian.net/browse/ATT-2873)  
**Sub-task**: [ATT-2921](https://rainerblind.atlassian.net/browse/ATT-2921) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Branch**: `improvement/ATT-2873`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

In ticket `ATT-1955` (`REQ-MAP-031`), in-ride fork-in-the-road route detection was introduced to alert athletes at upcoming trail and road splits when riding along a common outbound departure corridor. While computational overhead is negligible for small route libraries (< 10 routes), an architectural efficiency audit revealed key scalability bottlenecks in `ForkNavigationRepository.kt` and `ForkRouteMatcher.kt`:

1. **Absence of Spatial Bounding-Box Rejection**: In `ForkRouteMatcher.findCandidateRoutes()`, the matcher executes `projectOntoPolyline()` across every coordinate segment for *every* stored route in `routesRepository.allRoutes`. For an athlete with 100+ routes (500k+ polyline points), the engine performs expensive trigonometric haversine and vector projection calculations against distant, geographically irrelevant routes located in other cities or countries.
2. **Unthrottled 1 Hz Execution**: `ForkNavigationRepository.onLocationChanged()` triggers a full candidate evaluation loop on every single 1 Hz GPS update, even when the athlete is stationary or moving slowly along a straight corridor without any active divergence alerts.
3. **Absence of Sport Type Pre-Filtering**: Routes intended for different sport disciplines (e.g. Trail Run vs. Road Bike) are evaluated indiscriminately, wasting CPU cycles on incompatible routes.

The goal of **ATT-2873** is to eliminate unnecessary CPU wake-locks, conserve mobile battery during long endurance workouts, and achieve sub-millisecond route candidate filtering through $O(1)$ spatial bounding-box rejection, sport-type pre-filtering, and intelligent GPS evaluation throttling, while maintaining 100% responsiveness when approaching an active divergence alert.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Root Cause 1: Brute-Force $O(N \times M)$ Polyline Projection in `ForkRouteMatcher`
* **Location**: `ForkRouteMatcher.kt` (lines 124–130):
  ```kotlin
  for (route in allRoutes) {
      if (route.path.size < 2) continue
      val proj = projectOntoPolyline(currentPos, route.path)
      if (proj.crossTrackDistanceMeters > MAX_CORRIDOR_TOLERANCE_METERS) {
          continue
      }
      ...
  }
  ```
* **Mechanism**: Every route in the database is evaluated regardless of proximity. `projectOntoPolyline()` iterates through every vertex pair $(P_i, P_{i+1})$, calculating mid-latitude radians, trigonometric cosines, dot products, clamped segment ratios, and haversine distances. If a user has 100 stored routes across multiple regions, thousands of floating-point operations run at 1 Hz, heating the device and draining battery.

### Root Cause 2: Omission of $O(1)$ Bounding-Box Checks
* **Location**: `RoutesDatabaseManager.kt` (`RouteSummary`):
  ```kotlin
  val minLat: Double? = null,
  val minLng: Double? = null,
  val maxLat: Double? = null,
  val maxLng: Double? = null
  ```
* **Mechanism**: `RouteSummary` already contains the min/max latitude and longitude boundaries for persisted routes. When absent, a bounding box can be trivially computed from `route.path`. Testing whether `currentPos` lies within `[minLat - margin, maxLat + margin]` and `[minLng - margin, maxLng + margin]` requires 4 simple double comparisons ($O(1)$) and immediately rejects over 95% of routes without touching polyline vertices.

### Root Cause 3: Unthrottled 1 Hz Search Loop during Quiescent Tracking
* **Location**: `ForkNavigationRepository.kt` (lines 67–85):
  `onLocationChanged()` runs full candidate discovery on every GPS update.
* **Mechanism**: When no divergence alert is active (`_forkDecisionState.value == null`), route candidates do not change significantly every second unless the athlete moves into a new area. Candidate discovery can be safely throttled to every 3–5 seconds (or upon a distance displacement $\ge 20\text{ m}$). However, when a fork alert *is* active (`_forkDecisionState.value != null`), full 1 Hz frequency must be preserved to update countdown distances accurately and trigger autonomous route snapping.

### Root Cause 4: Missing Sport Type Filtering
* **Location**: `ForkRouteMatcher.kt` does not accept or filter by `BSportType`.
* **Mechanism**: Running workouts evaluate cycling routes and vice-versa, violating the domain principle established in `RouteProximityRanker.matchesSport()`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add bounding-box pre-filtering with a generous ~100m corridor margin in `ForkRouteMatcher.findCandidateRoutes()`, instantly discarding out-of-bounds routes.
  2. Add `activeSportType: BSportType? = null` parameter to `ForkRouteMatcher.findCandidateRoutes()` utilizing `RouteProximityRanker.matchesSport()`.
  3. Implement location evaluation throttling in `ForkNavigationRepository`: throttle candidate route searches to a 3–5 second interval (or distance delta $\ge 20\text{ m}$) when no alert is active, while retaining 1 Hz responsiveness when tracking towards an active divergence point.
  4. Provide `banalRepository.bSportType.value` to `ForkNavigationRepository` for sport-aware candidate matching.
  5. Expand unit tests in `ForkRouteMatcherTest.kt` and `ForkNavigationRepositoryTest.kt` validating spatial rejection, sport filtering, throttling, and latency invariants.
  6. Maintain 100% unit test pass rate across the test suite.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying the UI layout of `ForkDecisionCard` or its placement in `SensorGridScreen.kt` (assigned to `ATT-2874`).
  2. Modifying `RouteDivergenceDetector` divergence angle or distance threshold calculations.
  3. Modifying database schemas or table columns in `Routes.db`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Refines and amends `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clauses 1 & 4).
* **Historical Origin & Commit Trace**: Ticket `ATT-1955` (Sprint 2026-40.16).
* **Root Reason for Existing Formulation**: `ForkRouteMatcher` was initially authored as an MVP focusing on core geometrical matching algorithms (perpendicular projections, corridor heading alignment). Route collections were small during testing, so global linear iteration did not cause noticeable lag.
* **Preservation of Core Invariants**: The bounding box margin ($\sim 100\text{ m}$) is strictly wider than the corridor tolerance (`MAX_CORRIDOR_TOLERANCE_METERS = 50.0\text{ m}$), mathematically ensuring zero false dismissals. Divergence alert distances (< 300m) and auto-binding latency remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

### A. $O(1)$ Bounding-Box Pre-Filtering in `ForkRouteMatcher`
```kotlin
const val BOUNDING_BOX_CORRIDOR_MARGIN_METERS = 100.0

private fun isWithinBoundingBox(
    point: LatLng,
    route: RouteWithPath,
    marginMeters: Double = BOUNDING_BOX_CORRIDOR_MARGIN_METERS
): Boolean {
    val minLat = route.summary.minLat ?: route.path.minOfOrNull { it.latLng.latitude } ?: return true
    val maxLat = route.summary.maxLat ?: route.path.maxOfOrNull { it.latLng.latitude } ?: return true
    val minLng = route.summary.minLng ?: route.path.minOfOrNull { it.latLng.longitude } ?: return true
    val maxLng = route.summary.maxLng ?: route.path.maxOfOrNull { it.latLng.longitude } ?: return true

    val deltaLat = marginMeters / 111320.0
    val cosLat = kotlin.math.cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01)
    val deltaLng = marginMeters / (111320.0 * cosLat)

    return point.latitude in (minLat - deltaLat)..(maxLat + deltaLat) &&
           point.longitude in (minLng - deltaLng)..(maxLng + deltaLng)
}
```

### B. Sport Type Pre-Filtering
Before evaluating geometry:
```kotlin
if (activeSportType != null && !RouteProximityRanker.matchesSport(route.summary.bSportType, activeSportType)) {
    continue
}
```

### C. Quiescent Candidate Search Throttling in `ForkNavigationRepository`
```kotlin
private var lastSearchTimeMs: Long = 0L
private var lastSearchPos: LatLng? = null

const val QUIESCENT_THROTTLE_INTERVAL_MS = 3000L
const val QUIESCENT_THROTTLE_DISTANCE_METERS = 20.0
```
* If `_forkDecisionState.value == null`:
  Check elapsed time and displacement from `lastSearchPos`. If elapsed time $< 3000\text{ ms}$ and displacement $< 20\text{ m}$, skip searching.
* If `_forkDecisionState.value != null`:
  Execute immediately at 1 Hz to maintain high-frequency alert distance updates and auto-binding.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Auto-binding latency: when an athlete passes a divergence point down a chosen branch, auto-binding occurs at $\ge 50\text{m}$ past divergence.
  2. Zero false negatives: bounding-box margin ($\ge 100\text{m}$) strictly encompasses the $50\text{m}$ corridor tolerance.
  3. Zero regression in existing unit tests (`ForkRouteMatcherTest`, `ForkNavigationRepositoryTest`).
* **Risk Rating**: **LOW**. The changes are purely additive optimizations with mathematically bounded margins and fallbacks.
