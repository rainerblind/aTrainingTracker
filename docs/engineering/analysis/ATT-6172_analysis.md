# Stage 1 Analysis: ATT-6172 - LiveSegmentsRepository: Nicht-atomares Read-Modify-Write & In-Place-Mutation beheben

**Ticket**: [ATT-6172](https://rainerblind.atlassian.net/browse/ATT-6172)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6172`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
In `LiveSegmentsRepository.kt`, `_liveSegments` (`MutableStateFlow<List<LiveSegment>>`) is updated concurrently from two independent coroutines running on `Dispatchers.Default`:
1. **Lost Updates via Non-Atomic Read-Modify-Write**:
   - `reconcileSegments` (L133–149) reads `_liveSegments.value`, builds an updated list, and assigns `_liveSegments.value = updatedList`.
   - `updateLiveSegments` (L199–202) executes upon every GPS location update, doing `_liveSegments.value = _liveSegments.value.map { … }`.
   - Concurrent execution causes classic **Lost Updates**: an active status transition (e.g. crossing start gate $\rightarrow$ `ON_SEGMENT`) is overwritten by a parallel segment database reload, or newly synchronized segments are dropped by a simultaneous GPS update.
2. **In-Place Mutation in `LiveSegmentMath`**: `LiveSegmentMath` declares 6 mutable `var` fields (`start_cross_loc`, `end_cross_loc`, `startTime_ms`, `startDistance`, `indexOfDistance`, `lastSegmentIndex`). In-place mutation invalidates CAS retry idempotency in `StateFlow.update { … }` and breaks functional immutability.
3. **Unreachable Dead Code**: Line 126 chains a redundant, unreachable `.collect()` onto `banalRepository.currentLocation.collect { … }`.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**:
  - `LiveSegmentsRepository.kt:149,202`: Non-atomic assignment to `_liveSegments.value` instead of atomic compare-and-set via `_liveSegments.update { … }`.
  - `LiveSegmentsRepository.kt:74,79-82,85`: Mutable `var` fields in `LiveSegmentMath` mutated in-place during GPS tracking loops (L271-273, L320-321, L351).
  - `LiveSegmentsRepository.kt:126`: Typo chaining `.collect()` after a terminal `.collect { }` lambda.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - Audited `calculateInitialLiveSegmentState` (L156–192): Confirmed it is pure and side-effect free, making it safe to execute inside CAS retry loops.
  - Audited multi-threaded race conditions: Confirmed that under `Dispatchers.Default`, background DB reconciliation and high-frequency GPS location updates execute concurrently across distinct worker threads.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals (2 SP)**:
  - Replace non-atomic `.value = ...` in `reconcileSegments` and `updateLiveSegments` with atomic `_liveSegments.update { … }`.
  - Make `LiveSegmentMath` strictly immutable (`val` properties only) and replace in-place mutations with `.copy(math = ...)`.
  - Remove dead `.collect()` call at line 126.
  - Author concurrent stress tests simulating parallel GPS updates and segment reconciliation without lost updates.
* **Out-of-Scope Non-Goals**:
  - `ATT-6037`: Elevation graph optimization and general ViewModel cleanup.
  - Modifying `PolylineProgressTracker` gate crossing or orthogonal projection algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: Living doc shard `docs/requirements/req_maps_navigation.md` and `docs/tests/tst_maps_navigation.md`.
* **Proposed Requirements**: Introduces `REQ-MAP-065` (*LiveSegmentsRepository Concurrency Safety & Immutability*) and `TST-MAP-067`, refining `REQ-GEO-001` (*Polyline Engine & Live Segments*).
* **Historical Origin**: Sprint 2026-41 (`ATT-2582`), where Kotlin coroutines were initially integrated for segment tracking.
* **Root Reason for Existing Formulation**: Early design assumed sequential execution, overlooking multi-threaded races on `Dispatchers.Default`.
* **Preservation of Core Invariants**: Gate crossing detection, bearing tolerance (45°), along-track distance calculation, PR delta computation, and existing UI StateFlow contracts remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
* In `LiveSegmentsRepository.kt`:
  - Convert all `var` fields in `LiveSegmentMath` to `val`.
  - Extract pure helper `processLiveSegmentUpdate(liveSegment, ...): LiveSegment` returning a new immutable copy with updated math and live data.
  - In `updateLiveSegments`: `_liveSegments.update { list -> list.map { processLiveSegmentUpdate(it, ...) } }`.
  - In `reconcileSegments`: `_liveSegments.update { current -> val map = current.associateBy { it.staticData.summary.stravaId }; newStaticData.mapNotNull { map[it.summary.stravaId] ?: calculateInitialLiveSegmentState(it) } }`.
  - Clean up `.collect()` on location flow listener.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero lost updates; idempotent CAS retries; 100% pass on existing `LiveSegmentsRepositoryTest`.
* **Risk Rating**: **LOW** (Isolated internal state management in `LiveSegmentsRepository.kt`; no public API or database schema changes).
