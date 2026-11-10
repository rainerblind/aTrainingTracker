# Stage 3 Implementation Plan: ATT-6172 - LiveSegmentsRepository Concurrency Safety & Immutability

**Ticket**: [ATT-6172](https://rainerblind.atlassian.net/browse/ATT-6172)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-MAP-065` (`REQ-MAP-065.1`..`REQ-MAP-065.4`)  
**Test Spec Mapping**: `TST-MAP-067` (`TST-MAP-067.1`..`TST-MAP-067.4`)  
**Branch**: `feature/ATT-6172`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Architecture Boundaries**:
  - `LiveSegmentsRepository.kt`:
    1. **Immutable Math Domain Model**: Convert all mutable `var` properties in `LiveSegmentMath` (`start_cross_loc`, `end_cross_loc`, `startTime_ms`, `startDistance`, `indexOfDistance`, `lastSegmentIndex`) to read-only `val` properties (`REQ-MAP-065.2`).
    2. **Pure State Transformation**: Refactor `updateLiveSegments` to execute pure segment evaluations returning new immutable `LiveSegmentMath.copy(...)` and `LiveSegment.copy(...)` instances without mutating existing references (`REQ-MAP-065.2`).
    3. **Atomic State Updates**: Replace non-atomic `.value = ...` in `updateLiveSegments` and `reconcileSegments` with `_liveSegments.update { ... }`. In `reconcileSegments`, match existing active segments by `stravaId` and lazily initialize new segments via pure, side-effect-free `calculateInitialLiveSegmentState` inside the CAS block (`REQ-MAP-065.1`).
    4. **Dead Code Cleanup**: Remove the unreachable redundant trailing `.collect()` call on `banalRepository.currentLocation.collect { ... }` at line 126 (`REQ-MAP-065.3`).
  - **Testing Strategy**:
    - `LiveSegmentsRepositoryConcurrencyTest.kt`: Multi-coroutine stress test on `Dispatchers.Default` asserting zero lost updates during concurrent location updates and segment database reconciliations (`TST-MAP-067.1`).
    - `LiveSegmentsRepositoryImmutabilityTest.kt`: Reflection audit asserting 100% `val` properties in `LiveSegmentMath` and reference isolation (`TST-MAP-067.2`).
    - `LiveSegmentsRepositoryTest.kt`: Full regression pass verifying gate crossings and along-track progress (`TST-MAP-067.4`).
* **Invariants**: Public StateFlow signature `val liveSegments: StateFlow<List<LiveSegment>>` unchanged; start/end gate crossing physics preserved; zero database schema modifications.

---

## 2. Atomic Implementation Steps

| Step | Component | Target File | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Domain Model | `LiveSegmentsRepository.kt` | Convert all 6 `var` fields in `LiveSegmentMath` to `val`. | `REQ-MAP-065.2`<br>`TST-MAP-067.2` |
| **2** | Flow Listener | `LiveSegmentsRepository.kt` | Remove dead trailing `.collect()` call at line 126. | `REQ-MAP-065.3`<br>`TST-MAP-067.3` |
| **3** | Core Service | `LiveSegmentsRepository.kt` | Replace in-place math mutations in `updateLiveSegments` with `.copy()`. Wrap in `_liveSegments.update { ... }`. | `REQ-MAP-065.1-2`<br>`TST-MAP-067.1-2` |
| **4** | Core Service | `LiveSegmentsRepository.kt` | Convert `reconcileSegments` to atomic `_liveSegments.update { ... }` with side-effect-free initial state seeding. | `REQ-MAP-065.1`<br>`TST-MAP-067.1` |
| **5** | Concurrency Tests | `LiveSegmentsRepositoryConcurrencyTest.kt` | Author multi-coroutine stress tests asserting no lost segment state under race conditions. | `REQ-MAP-065.1`<br>`TST-MAP-067.1` |
| **6** | Immutability Tests| `LiveSegmentsRepositoryImmutabilityTest.kt` | Author reflection test asserting immutability of `LiveSegmentMath`. | `REQ-MAP-065.2`<br>`TST-MAP-067.2` |
| **7** | Verification | Test Suite | Run targeted tests (`com.atrainingtracker.trainingtracker.segments.*`). | `REQ-PRO-001`<br>`TST-MAP-067.4` |

---

## 3. System Invariants & Rollback Safety (Rule 4)
* **Code Lock**: Strict enforcement of Rule 4; no production code modified until Gate 3 is marked `Erledigt`.
* **Invariants**: public StateFlow emission order; zero lock-inversions; 100% clean-room test pass rate.
* **Risk Rating**: **LOW** (Internal concurrency and immutability hardening in single repository; no breaking API or schema changes).
