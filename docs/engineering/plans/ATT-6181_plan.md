# Stage 3: Implementation Plan - ATT-6181: Define resource and timing budgets in architecture

**Ticket**: [ATT-6181](https://rainerblind.atlassian.net/browse/ATT-6181)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-PRO-052` (`REQ-PRO-052.1`, `REQ-PRO-052.2`)  
**Test Mapping**: `TST-PRO-052` (`TST-PRO-052.1`, `TST-PRO-052.2`, `TST-PRO-052.3`)  
**Branch**: `feature/ATT-6181`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE pre-assessment finding C-07 (SWE.2 BP4) identified that `docs/architecture.md` specifies only 1 Hz sampling and a 3.5 s sensor failover timeout without explicit CPU, memory, battery, disk, or timing budgets for runtime-critical components. This plan operationalizes Section 4.9 in `docs/architecture.md` defining quantitative resource bounds across 4 critical domains and establishes automated contract test verification in `ArchitectureConformanceContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-052` (*Architectural Resource and Timing Budgets Specification*)
* **Test Mapping**: `TST-PRO-052` (*Architectural Resource and Timing Budgets Contract Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-052.1` -> `TST-PRO-052.1`, `TST-PRO-052.2` (Architecture §4.9 structure & numeric bounds)
  - `REQ-PRO-052.2` -> `TST-PRO-052.2`, `TST-PRO-052.3` (`ArchitectureConformanceContractTest` suite execution)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production classes in `app/src/main/` or database schemas.
2. **Dispatcher Separation**: Execution thread partitions (`Dispatchers.Main`, `Dispatchers.IO`, `dbDispatcher`) remain strictly preserved.
3. **Sampling Invariants**: The 1-Hz sampling cycle in `TrackerService` and 3.5 s sensor arbitration failover continue unmodified.
4. **Clean-Room Test Pass Rate**: 100% pass rate across targeted and contract test suites.

---

## 4. Proposed Architectural Changes
* **Architecture Specification (`docs/architecture.md`)**:
  - Add Section 4.9 (*Resource and Timing Budgets*, SWE.2.BP4).
  - Define quantitative matrix covering `TrackerService` (CPU < 5%, Heap < 40 MB, Tick < 50 ms, Battery < 5%/h), Sensor Ingestion / `BANALService` (Latency < 25 ms, Timeout 3.5 s, Partial wake-lock bounded), SQLite Persistence / `WorkoutSamplesDatabaseManager` (Write < 30 ms on `Dispatchers.IO`, Disk ~360 KB/h @ 1 Hz), and Cockpit UI/Map (Frame time < 16.6 ms, Polyline render < 30 ms, Conflated 1-Hz StateFlow).
  - Explicitly link architecture budgets to candidate NFRs (ATT-6139).
* **Architecture Conformance Contract Test (`ArchitectureConformanceContractTest.kt`)**:
  - Add `testArchitectureDoc_definesResourceAndTimingBudgets()` asserting Section 4.9 exists, covers all 4 domains, and defines numeric bounds.
* **UI Consistency**: No UI changes (architecture specification & test suite only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Document Resource & Timing Budgets in `docs/architecture.md` (REQ-PRO-052.1)
* Add Section 4.9 (*Resource and Timing Budgets*, SWE.2.BP4) detailing the quantitative budget table.
* Ensure all 4 critical subsystem domains and performance bounds are articulated and traced to NFRs.

### Step 2: Extend `ArchitectureConformanceContractTest.kt` (REQ-PRO-052.2)
* Add `testArchitectureDoc_definesResourceAndTimingBudgets()` asserting presence of Section 4.9, 4 subsystem domains, and numeric thresholds.
* Verify contract test cleanly executes via:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`

### Step 3: Living Doc Verification & Parity Check (TST-PRO-052.3)
* Run `python3 tools/verify_requirement_governance.py` to ensure 100% living documentation parity.

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`
* **Rollback Plan**: Branch isolation on `feature/ATT-6181` ensures clean revert without impacting `sprint/2026-46.1`.
