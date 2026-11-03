# Stage 3: Implementation Plan - ATT-4923: Live tracking: make track append, live-track decimation and sensor-field combine O(1) per tick (ATT-4863 follow-up)

**Ticket**: [ATT-4923](https://rainerblind.atlassian.net/browse/ATT-4923) | **Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*) | **Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-042` (`REQ-TRK-042.1`, `REQ-TRK-042.2`, `REQ-TRK-042.4`)  
**Test Mapping**: `TST-TRK-042` (`TST-TRK-042.1`, `TST-TRK-042.2`, `TST-TRK-042.3`, `TST-TRK-042.4`)  
**Branch**: `feature/ATT-4923` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-02  

---

## 1. Problem Description & Background

Follow-up to `ATT-4863`. Live-tracking allocations remain active during movement:
1. `BANALServiceRepository.kt`: `_currentTrack` and `_currentPathPoints` copy full lists on each moving tick via `+` ($O(N)$ copies, $>100\text{M}$ references on long rides).
2. `MapLayers.kt`: `LiveTrackLayer` re-simplifies the entire historical head via `PolyUtil.simplify` every second ($O(N \log N)$ CPU churn and shifting vertex chords).
3. `TrackingViewModel.kt`: `loadSensorFieldStates()` combines `currentPathPoints`, triggering sensor-field recalculation twice per second even when elevation is hidden.

---

## 2. Traceability & Requirements Mapping

* **`REQ-TRK-042.1` / `TST-TRK-042.1`**: Amortized $O(1)$ append snapshot list (`SnapshotChunkedList`) in `BANALServiceRepository.kt`.
* **`REQ-TRK-042.2` / `TST-TRK-042.2`**: Incremental chunked live-track decimation (`IncrementalTrackDecimator`) in `MapLayers.kt`.
* **`REQ-TRK-042.4` / `TST-TRK-042.3`**: Sensor-field combine decoupling from path points in `TrackingViewModel.kt`.
* **`REQ-PRO-001` / `TST-TRK-042.4`**: Clean-room regression verification across repository, map, and tracking suites.

---

## 3. System Invariants & Preserved Behavior

1. **API Fidelity & Immutability**: `SnapshotChunkedList` implements `List<T>` and `RandomAccess`. Snapshots are immutable for thread-safe reads from Compose/UI.
2. **Polyline Topology & Sharpness**: 200-point full-fidelity tail preserved; frozen historical chunks share boundary endpoints with zero gaps.
3. **Telemetry Rate Limit**: Sensor grid field updates emit strictly $\le 1$ Hz on sensor ticks.
4. **Rule 66 Invariant**: No Compose `MutableState` written inside `remember` or composition.
5. **Human Decision Gate**: Parent ticket advances to `Final Review (Human)`, never completed by agents.

---

## 4. Proposed Architectural Changes (SWE.2)

* **`SnapshotChunkedList<E>` & `BANALServiceRepository.kt`**: Implement `SnapshotChunkedList<E>` with chunk size $C = 256$ (`frozenChunks: List<List<E>>`, `activeTail: List<E>`). Appends copy at most $\le 256$ items. `equals(other)` checks `size != other.size` in $O(1)$ for `StateFlow` emissions. Used in `_currentTrack` and `_currentPathPoints`. `clearBreadcrumbs()` resets both to empty.
* **`IncrementalTrackDecimator` & `MapLayers.kt`**: Plain holder (no Compose `MutableState`). In `LiveTrackLayer`: `val decimator = remember { IncrementalTrackDecimator() }` and `remember(path.size) { decimator.decimate(path) }`. When unsimplified tail exceeds $M + C$ ($200 + 200 = 400$), simplifies the oldest chunk once into `frozenSimplifiedHead`.
* **`TrackingViewModel.kt`**: Remove `currentPathPoints` from `loadSensorFieldStates()` combine. Collect `currentPathPoints` via dedicated coroutine with `distinctUntilChanged().flatMapLatest` strictly when `viewInfo?.showElevationProfile == true`.
* **UI Consistency (Rule 23)**: Purely internal performance and reactive pipeline refactoring. Zero visual UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: `SnapshotChunkedList.kt`**: Create `com.atrainingtracker.trainingtracker.util.SnapshotChunkedList<E>` implementing `List<E>`, `RandomAccess` with $O(1)$ amortized append.
* **Step 2: `BANALServiceRepository.kt` Integration**: Replace `+` list copies for `_currentTrack` and `_currentPathPoints` with `SnapshotChunkedList` appends. Preserve `clearBreadcrumbs()` reset contract.
* **Step 3: `IncrementalTrackDecimator` in `MapLayers.kt`**: Create `IncrementalTrackDecimator` and wire into `LiveTrackLayer` via plain `remember`.
* **Step 4: `TrackingViewModel.kt` Combine Decoupling**: Remove `currentPathPoints` from sensor-field combine. Isolate `pathPoints` updates to dedicated `showElevationProfile` flow.
* **Step 5: Unit Tests**:
  - `BANALServiceRepositoryMemoryTest.kt`: synthetic 15,000-point track benchmark verifying $O(1)$ bounded append.
  - `LiveTrackDecimationTest.kt`: chunk freezing, 200-point tail preservation, and historical vertex stability.
  - `TrackingViewModelMapStateTest.kt`: assert combine does not fire on path point emissions when profile is hidden.

---

## 6. Verification & Rollback Plan

* **Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.*"`
* **Rollback**: `git checkout feature/ATT-4923 && git clean -fd`.
