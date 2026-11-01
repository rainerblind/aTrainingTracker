# Stage 2: Requirement & Test Specification - ATT-4863: [Bug] OutOfMemoryError at Parcel.obtain: eliminate remaining 1-Hz live-tracking allocation and recomposition hotspots

**Ticket**: [ATT-4863](https://rainerblind.atlassian.net/browse/ATT-4863)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.44`  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-TRK-042` (*1-Hz Live-Tracking Allocation Hotspot Elimination, Recomposition Decoupling & Bounded Polyline Serialization*)  
**Test Spec ID**: `TST-TRK-042`  
**Branch**: `feature/ATT-4863`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Requirement Specification (`REQ-TRK-042`)

### 1.1 Problem Statement & Rationale
During multi-hour workouts, live tracking can trigger `OutOfMemoryError` on `Parcel.obtain` due to heap exhaustion from: (1) unbounded dual polyline Binder IPC serialization and $O(N^2)$ path list copying, (2) `MutableState` mutation inside `remember` during composition in `SensorGridScreen`, (3) 2-Hz `baseFields` re-allocation per tab in `TrackingViewModel`, and (4) synthetic `System.nanoTime()` in `DeviceTelemetry` defeating `StateFlow` conflation.

### 1.2 Functional & Architectural Requirements
1. **Stationary PathPoint Deduplication (`REQ-TRK-042.1`)**: In `BANALServiceRepository.kt`, `_currentPathPoints` SHALL only append when `_currentLocation.value` is non-null AND either geographical position or cumulative distance has advanced.
2. **Bounded Live Track Decimation (`REQ-TRK-042.2`)**: In `MapLayers.kt` (`LiveTrackLayer`), when `path.size > 300`, older historical points SHALL be downsampled via `PolyUtil.simplify(head, 3.0)` while preserving the most recent 200 points unsimplified.
3. **Non-State Index Caching & Profile Gating (`REQ-TRK-042.3`)**: In `SensorGridScreen.kt`, route segment index SHALL be cached using `SegmentIndexHolder` (non-state), and `TrackingElevationProfileResolver.resolve(...)` SHALL be bypassed when `!state.showElevationProfile`.
4. **Base Field Memoization (`REQ-TRK-042.4`)**: In `TrackingViewModel.kt`, `baseFields` SHALL be memoized across 1-Hz sensor ticks and only recomputed on configuration, activity, or zone changes.
5. **Structural Telemetry Equality (`REQ-TRK-042.5`)**: `DeviceTelemetry` SHALL eliminate `version = System.nanoTime()`, restoring data class value equality.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Deduplication)**: Given stationary tracking for 30s, When 1-Hz loop ticks, Then `_currentPathPoints` does not append duplicate entries.
* **Criterion 2 (Decimation)**: Given a 3,000-point track, When `LiveTrackLayer` renders, Then `XRayPolyline` receives $\le 500$ points.
* **Criterion 3 (Profile Gating)**: Given a tab with `showElevationProfile == false`, When path updates, Then resolver is not executed.
* **Criterion 4 (Structural Equality)**: Given identical device telemetry, When updated, Then `_allActiveDevicesTelemetry` suppresses redundant emissions.

### 1.4 System Invariants
1. Raw `WorkoutSamples.db` records remain uncompressed and uncompromised.
2. Live track visual smoothness and recent turn accuracy are 100% preserved.
3. 100% test pass rate across all tracking packages.

---

## 2. Test Specification (`TST-TRK-042`)

### Test Case 1: Deduplication & Structural Equality (`TST-TRK-042.1`)
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositoryMemoryTest.kt`
* Verify: (a) Stationary ticks do not grow `currentPathPoints`, (b) `DeviceTelemetry` value equality without `System.nanoTime()`.

### Test Case 2: Live Track Decimation (`TST-TRK-042.2`)
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/LiveTrackDecimationTest.kt`
* Verify: Track with 3,000 collinear/curved points simplifies historical head while retaining last 200 points full-fidelity.

### Test Case 3: Base Fields Memoization & Clean-Room Regression (`TST-TRK-042.3`, `TST-TRK-042.4`)
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-042.1` | Unit | `BANALServiceRepository` path & telemetry | `REQ-TRK-042.1`, `REQ-TRK-042.5` | Specified |
| `TST-TRK-042.2` | Unit | `MapLayers.decimateLiveTrack` | `REQ-TRK-042.2` | Specified |
| `TST-TRK-042.3` | Regression | Full tracking & repository suites | `REQ-TRK-042.3`, `REQ-TRK-042.4` | Specified |
