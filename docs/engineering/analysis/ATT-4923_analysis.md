# Stage 1 Analysis: ATT-4923 - Live tracking: make track append, live-track decimation and sensor-field combine O(1) per tick (ATT-4863 follow-up)

**Ticket**: [ATT-4923](https://rainerblind.atlassian.net/browse/ATT-4923)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4923`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation

Follow-up to `ATT-4863` (`V4.9.41`, commit `06195f2a`). While `ATT-4863` stopped live-tracking allocations on stationary ticks, three 1-Hz allocation and CPU hotspots remain active while moving:
1. `_currentTrack` and `_currentPathPoints` in `BANALServiceRepository.kt` copy the entire $N$-element list on every moving tick via `+`, causing $>100\text{M}$ reference copies on multi-hour sessions.
2. `LiveTrackLayer` in `MapLayers.kt` invokes `PolyUtil.simplify` over the entire historical head on every new point, scaling $O(N \log N)$ and causing historical vertices to shift across ticks.
3. `loadSensorFieldStates()` in `TrackingViewModel.kt` combines `currentPathPoints` with `allFilteredSensorData`, firing twice per second per tab even when `showElevationProfile == false`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  1. *Hotspot 1 (O(N) Full-List Copy per Moving Tick)*: Confirmed in `BANALServiceRepository.kt:446, 472`. `Iterable.plus` allocates a new `ArrayList(size + 1)` copying all $N$ references. At $N = 14,400$ (4h), this churns $>200\text{M}$ references across both flows.
  2. *Hotspot 2 (Full Re-decimation Every Second)*: Confirmed in `MapLayers.kt:263-270, 281`. `decimateLiveTrack` runs `PolyUtil.simplify` on `head = path.dropLast(200)` every second. At 15,000 points, Douglas-Peucker runs over 14,800 points each tick. Shifting the head/tail boundary by 1 point alters Douglas-Peucker chord baselines, causing vertex flutter.
  3. *Hotspot 3 (Sensor-Field Combine 2-Hz Churn)*: Confirmed in `TrackingViewModel.kt:504-510`. Both `allFilteredSensorData` and `currentPathPoints` emit at 1 Hz independently, triggering `loadSensorFieldStates()` twice per second per tab and re-running `applySensorData(...)` even when `showElevationProfile == false`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Replace full-list copies in `BANALServiceRepository.kt` with an amortized $O(1)$ immutable snapshot list (`SnapshotChunkedList`), maintaining `List<T>` contract and $O(1)$ `StateFlow` inequality checks.
  * Implement incremental chunked decimation in `MapLayers.kt` via a plain holder (`IncrementalTrackDecimator`) adhering to Rule 66 (no Compose `MutableState` inside `remember`), freezing historical chunks permanently.
  * Decouple `currentPathPoints` from `loadSensorFieldStates()` combine, isolating it to a dedicated flow gated by `showElevationProfile`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying database schemas (`WorkoutSamples.db`, `Routes.db`, `Climbs.db`).
  * Changing external polyline rendering styling or Google Maps Compose camera controller logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-TRK-042` (*1-Hz Live-Tracking Memory & Recomposition Optimization*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-4863` (commit `06195f2a`).
* **Root Reason for Existing Formulation**: `ATT-4863` prioritized immediate OOM mitigation by gating stationary points and memoizing base fields, deferring data structure refactoring to this follow-up.
* **Preservation of Core Invariants**: Visual polyline smoothness, full 200-point tail fidelity, `List<T>` consumer API contracts, `clearBreadcrumbs()` reset behavior (`BANALServiceRepositoryTest`), and 100% test pass rate are preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **`BANALServiceRepository.kt`**: Implement `SnapshotChunkedList<T>` (chunk size $C = 256$, `RandomAccess`). Appends only allocate a new tail chunk ($\le 256$ elements) and chunk pointer array. `AbstractList.equals` checks `size != other.size` in $O(1)$, ensuring clean `StateFlow` emissions.
2. **`MapLayers.kt`**: Create `class IncrementalTrackDecimator` (plain holder inside `remember`). Freezes chunks of $C = 200$ points once via `PolyUtil.simplify` when tail exceeds $M + C$ ($200 + 200 = 400$). Renders `frozenSimplifiedHead + unsimplifiedTail`.
3. **`TrackingViewModel.kt`**: Remove `currentPathPoints` from `loadSensorFieldStates()` combine. Collect `currentPathPoints` via `flatMapLatest` strictly when `viewInfo.showElevationProfile == true` to update `_uiState.pathPoints`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Thread-safe snapshot reads across coroutines/UI without `ConcurrentModificationException`.
  2. Continuous polyline topology with zero vertex jumps across chunk boundaries.
  3. Single-tick combine rate ($\le 1\text{ Hz}$) for sensor field telemetry.
* **Risk Rating**: **LOW** (Targeted data structure and reactive pipeline decoupling with zero database or protocol changes).
