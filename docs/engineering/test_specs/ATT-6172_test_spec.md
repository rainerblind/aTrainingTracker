# Stage 2: Requirement & Test Specification - ATT-6172: LiveSegmentsRepository Concurrency Safety & Immutability

**Ticket**: [ATT-6172](https://rainerblind.atlassian.net/browse/ATT-6172) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6172`  
**Requirement Mapping**: `REQ-MAP-065` (`REQ-MAP-065.1`..`REQ-MAP-065.4`) | **Test Spec ID**: `TST-MAP-067` (`TST-MAP-067.1`..`TST-MAP-067.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-MAP-065`)

### 1.1 Problem Statement & Rationale
In `LiveSegmentsRepository.kt`, `_liveSegments` (`MutableStateFlow<List<LiveSegment>>`) is updated concurrently from `reconcileSegments` (database reloads) and `updateLiveSegments` (GPS location updates) on `Dispatchers.Default`. Both methods use non-atomic read-modify-write assignments (`_liveSegments.value = ...`), causing lost updates during concurrent execution. In addition, `LiveSegmentMath` declares 6 mutable `var` properties, and in-place mutations violate CAS retry idempotency. Furthermore, line 126 contains an unreachable `.collect()` call. The system SHALL modernize `LiveSegmentsRepository` with atomic `update { ... }` operations, immutable `LiveSegmentMath` data structures, pure state transformations, and dead code cleanup.

### 1.2 Functional & Architectural Requirements
* **REQ-MAP-065.1** (*Atomic StateFlow Update & Lost Update Elimination*): In `LiveSegmentsRepository.kt`, all state mutations to `_liveSegments` in `reconcileSegments` and `updateLiveSegments` SHALL be executed atomically using `_liveSegments.update { ... }`. Direct non-atomic assignments (`_liveSegments.value = ...`) SHALL be strictly prohibited.
* **REQ-MAP-065.2** (*LiveSegmentMath Immutability & Functional Transformation*): `LiveSegmentMath` SHALL be strictly immutable with all properties declared as `val`. State transformations across location updates SHALL be pure functions returning new immutable `LiveSegmentMath` and `LiveSegment` copies without mutating existing objects, guaranteeing idempotent CAS retries.
* **REQ-MAP-065.3** (*Dead Coroutine Collector Elimination*): The redundant chained `.collect()` at line 126 on `banalRepository.currentLocation.collect { ... }` SHALL be removed.
* **REQ-MAP-065.4** (*Preservation of Live Segment Tracking Invariants*): Gate crossing detection, bearing thresholds (45°), along-track distance calculation, PR delta computation, and public `liveSegments` StateFlow contracts SHALL remain 100% functionally identical.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Atomic Reconcile and Update Under Concurrency)**: *Given* high-frequency GPS updates and asynchronous segment database reloads running concurrently across multiple threads on `Dispatchers.Default`, *When* updating `_liveSegments`, *Then* all segment state transitions (e.g. crossing start gate $\rightarrow$ `ON_SEGMENT`) and newly synchronized segments are preserved without lost updates.
* **AC-2 (LiveSegmentMath Immutability)**: *Given* an active live segment, *When* location updates process tracking math, *Then* existing `LiveSegmentMath` instances are never mutated in-place; a new immutable copy is emitted.
* **AC-3 (Clean Location Collector)**: *Given* repository initialization, *When* location flow collection is launched, *Then* it executes cleanly as a single terminal collector without trailing dead calls.
* **AC-4 (Clean-Room Test Parity)**: *Given* existing segment tracking test cases, *When* tests run, *Then* 100% pass rate is maintained with zero behavioral regressions.

---

## 2. Test Specification & Traceability Matrix (`TST-MAP-067`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-065.1` | `TST-MAP-067.1` | `LiveSegmentsRepositoryConcurrencyTest.kt` | Concurrent multi-threaded stress test simulating parallel location updates and segment reconciliation without lost updates |
| `REQ-MAP-065.2` | `TST-MAP-067.2` | `LiveSegmentsRepositoryImmutabilityTest.kt` | Immutability audit asserting all `LiveSegmentMath` fields are `val` and in-place mutation is eliminated |
| `REQ-MAP-065.3` | `TST-MAP-067.3` | `LiveSegmentsRepositoryContractTest.kt` | Static / code inspection verifying elimination of trailing `.collect()` on location listener |
| `REQ-MAP-065.4` | `TST-MAP-067.4` | `LiveSegmentsRepositoryTest.kt` | Full regression suite verifying gate crossing, progress tracking, and PR calculation |
