# Stage 1 Analysis: ATT-6181 - Define resource and timing budgets in architecture

**Ticket**: [ATT-6181](https://rainerblind.atlassian.net/browse/ATT-6181)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6181`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 pre-assessment finding C-07 (SWE.2 BP4) identified that `docs/architecture.md` specifies only a 1 Hz sampling rate and a 3.5 s failover timeout without defining explicit CPU, memory, battery, disk, or timing budgets for runtime-critical components. Without documented budgets, system resource utilization is accidental rather than engineered, risking background thread contention, UI jank, and battery drain during multi-hour endurance sessions. Defining quantified budgets traces architecture to non-functional requirements (NFRs).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Architectural Specification Gap**:
   - `docs/architecture.md` §3.2 documents dynamic sequence flows for 1-Hz accumulation and a 3.5 s sensor timeout, but omits resource consumption ceilings and latency budgets.
   - No structured budget matrix maps runtime subsystems (`TrackerService`, `BANALService`, `WorkoutSamplesDatabaseManager`, Compose/Map rendering) to measurable performance metrics.
2. **Independent Peer Review & Verification**:
   - Audited live `docs/architecture.md`: Confirmed zero mentions of CPU, heap memory, disk growth, or frame timing budgets.
   - Audited `TrackerService.java:966`: Verified `scheduleAtFixedRate(tracker, 0, 1, TimeUnit.SECONDS)` operates on a 1000 ms periodic cycle requiring tick execution latency < 50 ms.
   - Audited `WorkoutSamplesDatabaseManager.java`: Verified unbuffered per-second SQLite insertions require write latency < 30 ms on `Dispatchers.IO` and consume ~360 KB/h disk bandwidth.
   - Audited `SensorGridScreen.kt` & `ATrainingTrackerMap.kt`: Confirmed 60 FPS Compose rendering requires frame time < 16.6 ms and conflated 1 Hz UI state emission.
   - Audited dependency ATT-6139 (A-12, backlog): Traced architecture budgets to target NFR categories (battery %/h, cold start, peak RAM, frame drop rate).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Add §4.9 (*Resource and Timing Budgets*, SWE.2 BP4) to `docs/architecture.md` detailing CPU, memory, disk, battery, and latency budgets for `TrackerService`, sensor layer (`BANALService`/`ProxySensor`), database writes (`WorkoutSamplesDatabaseManager`), and UI/Map rendering.
  - Formulate living requirements `REQ-PRO-052` and test spec `TST-PRO-052` in `req_process_stability.md` and `tst_process_stability.md`.
  - Add contract tests in `ArchitectureConformanceContractTest.kt` asserting that `docs/architecture.md` defines and maintains required resource budgets.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Performing physical hardware benchmark runs or profiler captures (covered under downstream NFR testing).
  - Modifying application runtime code in `app/src/main/` or database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-052` (*Architectural Resource and Timing Budgets*), refining `REQ-PRO-043` (*SWE.2 Architecture Conformance*) and tracing to candidate NFRs (ATT-6139).
* **Historical Origin & Commit Trace**: ASPICE pre-assessment finding C-07 (2026-11-08) and Parent Epic ATT-6142.
* **Root Reason for Existing Formulation**: Project historically focused on functional unit tests and feature delivery, leaving performance bounds unquantified in architecture docs.
* **Preservation of Core Invariants**: Existing threading models (`Dispatchers.Main`/`Dispatchers.IO`), 1-Hz sampling loops, 3.5 s sensor failover, and 100% unit test pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* `docs/architecture.md`: Introduce Section 4.9 containing a comprehensive budget specification table:
  - `TrackerService`: CPU < 5% single-core, Heap < 40 MB, Tick Latency < 50 ms, Battery < 5%/h (screen off).
  - Sensor Layer (`BANALService`): Event latency < 25 ms, Failover timeout 3.5 s, Partial wake-lock bounded to active tracking.
  - SQLite Persistence (`WorkoutSamplesDatabaseManager`): Write latency < 30 ms, Disk growth ~360 KB/h @ 1 Hz.
  - Cockpit UI & Map: Frame time < 16.6 ms (60 FPS), Polyline update latency < 30 ms, Conflated 1 Hz state emission.
* `ArchitectureConformanceContractTest.kt`: Add `testArchitectureDoc_definesResourceAndTimingBudgets()` contract test.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime code alterations; 100% unit test pass rate; living doc parity maintained.
* **Risk Rating**: **LOW** (2 SP, documentation and contract testing enhancement with zero production runtime code impact).
