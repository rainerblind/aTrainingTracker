# Stage 2: Requirement & Test Specification - ATT-4923: Live tracking: make track append, live-track decimation and sensor-field combine O(1) per tick (ATT-4863 follow-up)

**Ticket**: [ATT-4923](https://rainerblind.atlassian.net/browse/ATT-4923)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-042` (*1-Hz Live-Tracking Memory & Recomposition Optimization*)  
**Test Spec ID**: `TST-TRK-042`  
**Branch**: `feature/ATT-4923`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (`REQ-TRK-042`)

### 1.1 Problem Statement & Rationale
Follow-up to `ATT-4863`. Live-tracking allocations remain active during movement: (1) `_currentTrack` and `_currentPathPoints` copy the full list on each moving tick via `+`, (2) `LiveTrackLayer` re-simplifies the entire historical head every second via `PolyUtil.simplify`, causing $O(N \log N)$ CPU churn and vertex shifting, and (3) `TrackingViewModel.loadSensorFieldStates()` combines `currentPathPoints`, causing sensor fields to update twice per second.

### 1.2 Functional & Architectural Requirements
1. **Amortized O(1) Append Structure (`REQ-TRK-042.1`)**: In `BANALServiceRepository.kt`, `_currentTrack` and `_currentPathPoints` SHALL use an amortized $O(1)$ snapshot list (`SnapshotChunkedList<T>`), eliminating full-list copying upon each moving tick while maintaining `List<T>` random-access read fidelity.
2. **Incremental Chunked Live-Track Decimation (`REQ-TRK-042.2`)**: In `MapLayers.kt` (`LiveTrackLayer`), track decimation SHALL operate incrementally via `IncrementalTrackDecimator` (a plain holder adhering to Rule 66 without Compose `MutableState` mutation in composition). Completed historical chunks ($C = 200$) SHALL be simplified once and permanently frozen, bounding per-tick decimation to $O(1)$ and preventing coordinate flutter.
3. **Sensor-Field Combine Decoupling (`REQ-TRK-042.4`)**: In `TrackingViewModel.kt`, `currentPathPoints` SHALL be decoupled from `loadSensorFieldStates()` combine. Live path points SHALL be collected strictly in an isolated pipeline when `showElevationProfile == true`, ensuring sensor fields update at most once per sensor tick.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-042` (*1-Hz Live-Tracking Allocation Hotspot Elimination, Recomposition Decoupling & Bounded Polyline Serialization*).
* **Historical Origin & Commit Trace**: Ticket `ATT-4863`, commit `06195f2a`. Follows Crashlytics `16e0317dd5b3aa5a53b9248f50f05822`.
* **Root Reason for Existing Formulation**: `ATT-4863` eliminated stationary allocations and memoized base fields as an urgent OOM mitigation, deferring data structure changes to this follow-up.
* **Preservation of Core Invariants**: Visual polyline sharpness, 200-point unsimplified tail accuracy, continuous elevation profiles, and 100% test pass rate are preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Amortized O(1) Appends)**: Given a 15,000-point track, When appending a new point, Then time and memory allocation per append remain bounded ($O(1)$) rather than copying 15,000 references.
* **Criterion 2 (Incremental Decimation & Vertex Stability)**: Given a growing track exceeding 400 points, When new points arrive, Then already-frozen historical chunks are not re-simplified and their simplified vertices remain strictly identical across ticks.
* **Criterion 3 (Combine Decoupling)**: Given 1-Hz sensor emissions and independent 1-Hz GPS updates, When sensor fields are observed, Then `loadSensorFieldStates()` emits at most once per sensor tick.

---

## 2. Test Specification (`TST-TRK-042`)

### Test Case 1: SnapshotChunkedList Amortized O(1) & Contract Tests (`TST-TRK-042.1`)
* **Scope**: Unit & Allocation Benchmark (`app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositoryMemoryTest.kt`)
* **Assert**: `SnapshotChunkedList` implements `List<T>`, provides correct indexing/iteration/size, and appending 15,000 points completes in bounded time with zero full-list reallocation.

### Test Case 2: IncrementalTrackDecimator Vertex Stability & Chunking (`TST-TRK-042.2`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/LiveTrackDecimationTest.kt`)
* **Assert**: Historical chunks freeze at chunk boundaries, unsimplified tail preserves 200 points, and simplified vertices in the head remain strictly stable between tick $N$ and tick $N+1$.

### Test Case 3: Sensor Field Combine Decoupling (`TST-TRK-042.3`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelMapStateTest.kt`)
* **Assert**: Emitting on `currentPathPoints` does not trigger sensor field recomposition or recalculation in `loadSensorFieldStates()` when `showElevationProfile == false`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-042.1` | Unit | `BANALServiceRepository` (`SnapshotChunkedList`) | `REQ-TRK-042.1` | Specified |
| `TST-TRK-042.2` | Unit | `MapLayers` (`IncrementalTrackDecimator`) | `REQ-TRK-042.2` | Specified |
| `TST-TRK-042.3` | Unit | `TrackingViewModel` (`loadSensorFieldStates`) | `REQ-TRK-042.4` | Specified |
| `TST-TRK-042.4` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.*"` | `REQ-PRO-001` | Specified |
