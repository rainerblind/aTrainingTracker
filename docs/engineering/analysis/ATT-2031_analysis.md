# Stage 1 Analysis: ATT-2031 - Centralize Zone Threshold Resolution (DRY) and Optimize Scrubbing Point Search to O(log N)

**Ticket**: [ATT-2031](https://rainerblind.atlassian.net/browse/ATT-2031)  
**Sub-task**: [ATT-2237](https://rainerblind.atlassian.net/browse/ATT-2237) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Branch**: `feature/ATT-2031`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

During post-workout telemetry analysis in Aftermath components (`MapDetailLayout.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`), athletes inspect heart rate, cycling power, and speed/pace metrics along an interactive scrubbing timeline.

Two architectural deficiencies and performance bottlenecks currently degrade code maintainability and touch interaction fluidity:

1. **Duplicate Zone Threshold Resolution (DRY Violation)**:
   A ~15-line boilerplate query retrieving zone thresholds from `SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1..4)` is copy-pasted across multiple locations:
   * `ElevationProfile.kt` (lines 785–815)
   * `TelemetryMetricGraph.kt` (lines 280–305)
   * `MapDetailLayout.kt` (lines 145–175)
   Any future change to zone fallback handling or data store keys requires synchronized edits across 3 separate files.
2. **Linear Search During Interactive Scrubbing ($O(N)$ vs $O(\log N)$)**:
   In `MapDetailLayout.kt` (lines 394–400 and 441–447) and in `TelemetryMetricGraph.kt` (lines 921–925), finding the active trackpoint closest to `selectedDistance` executes `pathPoints.minByOrNull { abs(...) }`.
   On long workout recordings with $> 2,000$ points, this performs an $O(N)$ linear scan on the Main / UI thread for every touch move event and during Canvas drawing frames, resulting in UI micro-stutters and frame drops during scrub gesture dragging.

---

## 2. Root Cause Analysis (Forensic Investigation)

### A. Redundant Zone Loading Boilerplate
In `MapDetailLayout.kt`, `ElevationProfile.kt`, and `TelemetryMetricGraph.kt`, the following block is repeatedly duplicated:
```kotlin
val zoneType = if (bSportType == BSportType.BIKE) {
    SettingsDataStore.ZoneType.HR_BIKE
} else {
    SettingsDataStore.ZoneType.HR_RUN
}
val z1 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
val z2 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
val z3 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
val z4 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
if (z1 > 0 && z2 > 0 && z3 > 0 && z4 > 0) {
    HeartRateZoneThresholds(z1, z2, z3, z4)
} else null
```
And similarly for power zones:
```kotlin
val zoneType = SettingsDataStore.ZoneType.PWR_BIKE
val z1 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
...
```
`TelemetryZoneMath.kt` is the designated domain math engine for training zones in Aftermath, yet callers currently assemble these data classes via ad-hoc queries rather than through standard factory methods.

### B. $O(N)$ Linear Search on Monotonic Data
`PathPoint` entries in `pathPoints` are recorded chronologically during tracking and ordered monotonically by both `timeSec` and accumulated `distance`:
```kotlin
// Distance domain:
point[i].distance <= point[i+1].distance
// Time domain (trackless):
point[i].timeSec <= point[i+1].timeSec
```
Using `minByOrNull { abs(it.distance - selectedDistance) }` treats the list as unsorted, inspecting every single element ($O(N)$ comparisons per touch event). For 3,000 points and a 60fps drag gesture, this burns millions of unnecessary CPU cycles per second on the UI thread.
Because the array is sorted and indexed, a binary search (`binarySearch`) can locate the nearest neighbor in $O(\log N)$ comparisons (e.g. $\le 12$ comparisons for 4,096 points), reducing point lookup computation by over 99%.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add factory methods `loadHeartRateThresholds(context: Context, bSportType: BSportType): HeartRateZoneThresholds?` and `loadPowerThresholds(context: Context): PowerZoneThresholds?` to `TelemetryZoneMath.kt`.
  2. Replace duplicated threshold extraction logic in `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` with calls to `TelemetryZoneMath`.
  3. Implement an optimized, allocation-free binary search lookup helper `TelemetryMetricUtils.findNearestPoint(points: List<PathPoint>, targetValue: Double, isTimeDomain: Boolean): PathPoint?`.
  4. Replace `minByOrNull` in `MapDetailLayout.kt` and `TelemetryMetricGraph.kt` with `TelemetryMetricUtils.findNearestPoint`.
  5. Add unit tests in `TelemetryZoneMathTest.kt` and `TelemetryMetricUtilsTest.kt` verifying threshold loading and binary search edge cases (empty list, single element, exact match, left/right tie-breaking, out-of-bounds lower/upper).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying `SettingsDataStore` schema or data persistence.
  * Changing graph visual styles, line widths, curve smoothing, or axis labels.
  * Refactoring gesture detection or zoom pan connection architecture.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement (`REQ-PER-007`), refining performance and maintainability under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**: `ATT-1839` (commit `53b708cf` - introduced zone bands in `TelemetryMetricGraph.kt`) and `ATT-1391` (commit `e7a6a273` - introduced Aftermath scrubbing).
* **Root Reason for Existing Formulation**: When zone thresholds and multi-metric graphs were incrementally introduced across sprints, threshold loading code was copied into each component to maintain independence. `minByOrNull` was a quick idiomatic Kotlin prototype for nearest-point lookup that became a performance bottleneck as trackpoint densities grew.
* **Preservation of Core Invariants**: Component behavior, visual outputs, zone calculations, and telemetry synchronization are 100% preserved. The binary search returns the exact same nearest point as `minByOrNull`.

---

## 5. Architectural Strategy & High-Level Solution

```
┌────────────────────────────────────────────────────────┐
│                   MapDetailLayout.kt                   │
│  - Top-level remember(bSportType, context) threshold   │
│  - O(log N) findNearestPoint for active HR/Power point │
└───────────────────────┬────────────────────────────────┘
                        │
       ┌────────────────┴───────────────┐
       ▼                                ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│   ElevationProfile.kt    │  │  TelemetryMetricGraph.kt │
│  - Uses centralized      │  │  - Uses centralized      │
│    TelemetryZoneMath     │  │    TelemetryZoneMath     │
│    threshold loading     │  │  - O(log N) Canvas       │
│                          │  │    cursor highlight      │
└──────────────┬───────────┘  └─────────────┬────────────┘
               │                            │
               ▼                            ▼
┌────────────────────────────────────────────────────────┐
│                  com.atrainingtracker.ui.map           │
│                                                        │
│  TelemetryZoneMath.kt:                                 │
│  - loadHeartRateThresholds(context, bSportType)        │
│  - loadPowerThresholds(context)                        │
│                                                        │
│  TelemetryMetricUtils.kt:                              │
│  - findNearestPoint(points, targetValue, isTimeDomain) │
└────────────────────────────────────────────────────────┘
```

1. **`TelemetryZoneMath.kt`**:
   Expose pure factory methods wrapping `SettingsDataStoreJavaHelper.getZoneMax` with safe `runCatching` handling and positive-value validation.
2. **`TelemetryMetricUtils.kt`**:
   Implement `findNearestPoint` utilizing `List.binarySearch`. If exact match is found, return point. If between elements, evaluate absolute difference between `insertionPoint - 1` and `insertionPoint` to select the closest neighbor.
3. **`MapDetailLayout.kt` & `TelemetryMetricGraph.kt`**:
   Replace ad-hoc queries and `minByOrNull` calls with the centralized helpers.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Exact numerical parity: `findNearestPoint` must return the identical nearest `PathPoint` as `minByOrNull`.
  2. Zone thresholds must fall back to `null` if any upper threshold $\le 0$ or unconfigured.
  3. Zero regressions across 1530+ existing unit tests.
  4. Parent ticket Human Decision Gate remains strictly reserved for the human user.
* **Risk Rating**: **LOW**
  * Pure algorithmic optimization and DRY refactoring within local presentation modules.
  * Zero database migration, zero API surface disruption, and zero network dependency.
