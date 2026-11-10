# Stage 5: Walkthrough & Verification - ATT-6181: Define Resource and Timing Budgets in Architecture

**Ticket**: [ATT-6181](https://rainerblind.atlassian.net/browse/ATT-6181)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6181`  
**Requirement Mapping**: `REQ-PRO-052` (`REQ-PRO-052.1`–`REQ-PRO-052.2`) | **Test Mapping**: `TST-PRO-052` (`TST-PRO-052.1`–`TST-PRO-052.3`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview
Formally resolved ASPICE 4.0 pre-assessment finding C-07 (SWE.2.BP4) by establishing explicit quantitative resource and timing budgets in `docs/architecture.md` Section 4.9 and enforcing compliance via automated contract testing:
* **TrackerService Budget**: CPU < 5% single-core on reference mid-tier device, heap memory < 40 MB, tick latency < 50 ms per 1000 ms periodic tick, background battery drain < 5%/h with GPS/sensors active and screen off (`REQ-PRO-052.1`).
* **Sensor Layer Budget**: Dispatch latency < 25 ms event-to-subscriber in `BANALService` / `ProxySensor`, 3.5 s reconnection timeout window, and wake-lock acquisition strictly bounded to active tracking states (`REQ-PRO-052.1`).
* **SQLite Persistence Budget**: Sample insertion latency < 30 ms on `Dispatchers.IO` in `WorkoutSamplesDatabaseManager`, zero main-thread database I/O, disk storage growth ceiling ~360 KB/h at 1-Hz sampling (`REQ-PRO-052.1`).
* **Cockpit UI & Map Budget**: UI frame rendering < 16.6 ms per frame (60 FPS) in `SensorGridScreen` / `ATrainingTrackerMap`, polyline render update latency < 30 ms, conflated 1-Hz UI telemetry StateFlow emission (`REQ-PRO-052.1`).
* **Contract Testing Enforcement**: Extended `ArchitectureConformanceContractTest.kt` with `testArchitectureDoc_definesResourceAndTimingBudgets` verifying Section 4.9 presence, coverage of all 4 critical subsystem domains, and quantitative numeric thresholds (`REQ-PRO-052.2`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-052.1` | `TST-PRO-052.1` | `ArchitectureConformanceContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-052.2` | `TST-PRO-052.2` | `ArchitectureConformanceContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-052` | `TST-PRO-052.3` | Targeted Suite `build.*` (Tier 1) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope
* **Executed Scope**: `Tier 1 targeted: com.atrainingtracker.trainingtracker.build.*`
* **Command**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 \
    ./gradlew testDebugUnitTest --max-workers=2 \
    --tests "com.atrainingtracker.trainingtracker.build.*"
  ```
* **Results**:
  - `testDebugUnitTest`: 100% PASS across all contract tests in `com.atrainingtracker.trainingtracker.build.*` (0 failures, 0 regressions).
  - `ArchitectureConformanceContractTest`: `testArchitectureDoc_definesResourceAndTimingBudgets` verified Section 4.9 structure, domain coverage, and numeric thresholds.
* **Lock Coordination**: Run under `/tmp/att_build_merge.lock` via `parallel_sprint_lock.py` to prevent resource contention.

---

## 4. Key Implementation Highlights
* **`docs/architecture.md` §4.9**: Documented SWE.2.BP4 resource and timing budgets covering Tracking Core, Sensor Ingestion, Database Persistence, and Cockpit UI/Map Rendering, tracing candidates to ATT-6139.
* **`ArchitectureConformanceContractTest.kt`**: Enforced structural and numeric threshold verification for Section 4.9 and its 4 core subsystem domains.

---

## 5. Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production source code in `app/src/main/`.
2. **Preserved Architecture Invariants**: `Dispatchers.Main` vs `Dispatchers.IO` isolation, 1-Hz sampling loop, and 3.5 s sensor failover remain intact.
3. **Living Doc Parity**: `REQ-PRO-052` and `TST-PRO-052` updated to `[Verified]` with 100% master index parity.
