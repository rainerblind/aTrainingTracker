# Stage 3: Implementation Plan - ATT-4863: [Bug] OutOfMemoryError at Parcel.obtain: eliminate remaining 1-Hz live-tracking allocation and recomposition hotspots

**Ticket**: [ATT-4863](https://rainerblind.atlassian.net/browse/ATT-4863)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.44`  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-TRK-042`  
**Test Mapping**: `TST-TRK-042`  
**Branch**: `feature/ATT-4863`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background

Heap exhaustion (512 MB limit) on multi-hour rides produces fatal `OutOfMemoryError` on `Parcel.obtain` on incoming Binder threads. Root causes:
1. `_currentPathPoints` and `_currentTrack` perform $O(N^2)$ array list copies on 1-Hz ticks even when stationary/null.
2. `LiveTrackLayer` passes unbounded lists to `XRayPolyline`, serializing tens of thousands of points twice per second across Binder via `Polyline.setPoints`.
3. `SensorGridScreen.kt` mutates a `MutableState` inside `remember` during composition and computes elevation profiles even when hidden.
4. `TrackingViewModel.kt` rebuilds `baseFields` twice per second per tab in `combine`.
5. `DeviceTelemetry` defeats `StateFlow` structural equality with `version = System.nanoTime()`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-042` (*1-Hz Live-Tracking Allocation Hotspot Elimination, Recomposition Decoupling & Bounded Polyline Serialization*)
  * `REQ-TRK-042.1`: Stationary `PathPoint` deduplication (`BANALServiceRepository.kt`).
  * `REQ-TRK-042.2`: Bounded live track decimation (`MapLayers.kt`).
  * `REQ-TRK-042.3`: Non-state index holder & hidden profile gating (`SensorGridScreen.kt`).
  * `REQ-TRK-042.4`: Memoized base sensor fields (`TrackingViewModel.kt`).
  * `REQ-TRK-042.5`: Value equality for `DeviceTelemetry` (`BANALServiceRepository.kt`).
* **Test Mapping**: `TST-TRK-042` (`TST-TRK-042.1`, `TST-TRK-042.2`, `TST-TRK-042.3`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Degradation of Live Traces**: Full resolution preserved for the active riding head (last 200 points).
2. **Database Integrity**: Raw telemetry written to SQLite `WorkoutSamples.db` remains 100% untouched.
3. **Thread Safety**: Repository state updates remain safely encapsulated on `Dispatchers.Main`.
4. **Human Gate Invariance**: Terminal completion of parent ticket remains reserved for human in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `BANALServiceRepository`
* In `DeviceTelemetry`: drop `version = System.nanoTime()`.
* In `startObservingBANALService()`: track `_lastAppendedDistance`. Only append to `_currentPathPoints` if `_currentLocation.value != null` and (`locationChanged || currentDistance != _lastAppendedDistance`).

### Component 2: `MapLayers` (`LiveTrackLayer`)
* Implement `decimateLiveTrack(path: List<LatLng>): List<LatLng>`. If `path.size > 300`, take `tail = path.takeLast(200)` and `head = path.dropLast(200)`. Run `PolyUtil.simplify(head, 3.0)` and join with `tail`.

### Component 3: `SensorGridScreen`
* Introduce `class SegmentIndexHolder(var index: Int? = null)`.
* In `remember`: if `!state.showElevationProfile`, return empty profile without calling `TrackingElevationProfileResolver.resolve(...)`.

### Component 4: `TrackingViewModel`
* Memoize `baseFields` so that 1-Hz sensor emissions only invoke `applySensorData(...)`.

### UI Consistency (Rule 23)
* No visual design or UI token changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `BANALServiceRepository.kt` Gating & Structural Equality
* Remove `version` from `DeviceTelemetry`. Guard `_currentPathPoints` appending.

### Step 2: `MapLayers.kt` Bounded Live Track Decimation
* Add `decimateLiveTrack` helper with 3m tolerance for history $>200$ points.

### Step 3: `SensorGridScreen.kt` State Mutation Elimination & Profile Gating
* Use `SegmentIndexHolder` and short-circuit `resolve` when hidden.

### Step 4: `TrackingViewModel.kt` Base Field Memoization
* Precompute and cache `baseFields` on config/activity changes.

### Step 5: Unit Tests
* `BANALServiceRepositoryMemoryTest.kt` (`TST-TRK-042.1`)
* `LiveTrackDecimationTest.kt` (`TST-TRK-042.2`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4, followed by clean-room regression in Stage 5.
* **Rollback**: Branch reset on `feature/ATT-4863`.
