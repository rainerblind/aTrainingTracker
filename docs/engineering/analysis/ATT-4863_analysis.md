# Stage 1 Analysis: ATT-4863 - [Bug] OutOfMemoryError at Parcel.obtain: eliminate remaining 1-Hz live-tracking allocation and recomposition hotspots

**Ticket**: [ATT-4863](https://rainerblind.atlassian.net/browse/ATT-4863)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.1`  
**Branch**: `feature/ATT-4863`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation

Production Crashlytics issue `16e0317dd5b3aa5a53b9248f50f05822` in release `4.9.38.5 (269)` reported a fatal `java.lang.OutOfMemoryError` on `Parcel.obtain` on an incoming Binder thread during active multi-hour tracking. Heap reached 100% of its 512 MB limit (with only 8 bytes free). While `ATT-3690` eliminated static route re-allocations in `V4.9.40`, four critical 1-Hz allocation and recomposition churn hotspots remain active during live tracking.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  1. *Hotspot 1 (Unbounded Polyline Binder IPC & O(N^2) PathPoint Copying)*: Confirmed in `BANALServiceRepository.kt:351, 372` and `MapLayers.kt:258-273`. `_currentPathPoints` appends unconditionally every second (even when stationary/null), copying $>100\text{M}$ references on long rides. `LiveTrackLayer` passes the unsimplified growing track to `XRayPolyline`, serializing tens of thousands of points twice every second across Binder via `Polyline.setPoints`.
  2. *Hotspot 2 (MutableState Mutation During Composition)*: Confirmed in `SensorGridScreen.kt:364-382`. `lastRouteSegmentIndex` is a `mutableStateOf` written inside `remember` during composition, causing immediate snapshot invalidation and recomposition churn. `TrackingElevationProfileResolver.resolve` executes every second even when `showElevationProfile == false`.
  3. *Hotspot 3 (2-Hz Sensor Grid Re-allocation)*: Confirmed in `TrackingViewModel.kt:486-578`. Combining `allFilteredSensorData` and `currentPathPoints` causes `combine` to fire twice per second per tab, recreating `baseFields` (`configs.map { ... }`) from scratch each time.
  4. *Hotspot 4 (Unconditional Telemetry Allocation)*: Confirmed in `BANALServiceRepository.kt:78, 332-340`. `DeviceTelemetry` contains `version = System.nanoTime()`, defeating `StateFlow` structural equality and forcing downstream subscribers to recompute on every tick.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Bound/downsample historical live track polyline in `LiveTrackLayer` via `PolyUtil.simplify` while keeping recent tail at full fidelity.
  * Guard `_currentPathPoints` in `BANALServiceRepository.kt` to only append when location is non-null and location or distance has advanced.
  * Replace `mutableStateOf` with a non-state holder in `SensorGridScreen.kt` and guard elevation profile resolution behind `showElevationProfile`.
  * Memoize `baseFields` in `TrackingViewModel.kt` so 1-Hz telemetry updates only execute `applySensorData`.
  * Remove synthetic `version = System.nanoTime()` from `DeviceTelemetry` to restore data class value equality.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Redesigning Google Maps SDK architecture or replacing Google Maps Compose with custom Canvas.
  * Modifying historical workout recording databases (`WorkoutSamples.db`, `WorkoutSummaries.db`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-042` (*1-Hz Live-Tracking Memory & Recomposition Optimization*).
* **Historical Origin & Commit Trace**: `ATT-3690` (`V4.9.40`) addressed static route memoization; this ticket addresses live 1-Hz streaming paths.
* **Root Reason for Existing Formulation**: `version = System.nanoTime()` was added in early refactorings under the assumption that `StateFlow` required unique timestamps. Unbounded `_currentPathPoints` and dual polyline updates were implemented before multi-hour heap profiles were captured.
* **Preservation of Core Invariants**: 1-Hz live telemetry updates, visual track sharpness, map follow-me behavior, and 100% test pass rate are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. `BANALServiceRepository.kt`: Gate `_currentPathPoints` to only append on valid movement; remove `version` from `DeviceTelemetry`.
2. `MapLayers.kt`: In `LiveTrackLayer`, keep the last 300 points at full 1-Hz resolution and simplify older history with `PolyUtil.simplify(head, 3.0)`.
3. `SensorGridScreen.kt`: Use `class SegmentIndexHolder(var index: Int? = null)` and short-circuit `resolve(...)` when `!state.showElevationProfile`.
4. `TrackingViewModel.kt`: Memoize `baseFields` on config/activity changes, decoupled from 1-Hz sensor ticks.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero visual degradation of live GPS tracks or navigation chevrons.
  2. Complete fidelity of raw database samples in `WorkoutSamples.db`.
  3. Single-threaded database serialization on `mDbExecutor`.
* **Risk Rating**: **LOW** (Targeted UI/repository memory decoupling with zero database schema or protocol changes).
