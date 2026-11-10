# Stage 1 Analysis: ATT-6180 - Specify Software Interfaces in Architecture

**Ticket**: [ATT-6180](https://rainerblind.atlassian.net/browse/ATT-6180)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6180`  
**Requirement Mapping**: `REQ-PRO-044` | **Test Mapping**: `TST-PRO-044`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
While `docs/architecture.md` establishes high-level subsystem models, requirement allocations, and dynamic behavior (ASPICE SWE.2 BP1, BP3, BP4), it lacks formal Software Interface Specifications (ASPICE SWE.2 BP2). The 14 inter-component arrows in the architectural diagram lack defined operations, data schemas, threading rules, and error handling contracts. This ambiguity impedes systematic integration testing (SWE.4 / SYS.4) and violates ASPICE Level 2 compliance (Finding C-06).

---

## 2. Root Cause Analysis & Independent Verification (4-Augen-Prinzip)
* **Codebase Interface Grounding**:
  * `IF-SWE-01` (UI $\to$ ViewModel): Compose MVI via immutable UI state, unidirectional `StateFlow`, user action events (`Dispatchers.Main`).
  * `IF-SWE-02` (ViewModel $\to$ Repository): Asynchronous coroutines (`suspend fun`), cached `StateFlow` streams (`SupervisorJob` on `Dispatchers.IO`).
  * `IF-SWE-03` (TrackerService $\leftrightarrow$ BANALService): In-process local Binder `BANALServiceComm` (`getAllFilteredSensorData()`, `getBestSensorData()`) and broadcasts (`RESET_ACCUMULATORS_INTENT`, `REQUEST_PAUSE_TRACKING`). Volatile reference with null guards.
  * `IF-SWE-04` (Hardware $\to$ Services): BLE `BluetoothGattCallback`, ANT+ `AntPluginPcc`, and Android `LocationListener` / Google Play Fused Location Provider.
  * `IF-SWE-05` (Services/Repos $\to$ Domain Engines): Synchronous pure JVM math (`AutoPauseManager.evaluateTick`, `PowerPhysiologyCalculator`, `WorkoutClusterEngine`, `ClimbDetector`).
  * `IF-SWE-06` (TrackerService $\to$ SQLite Databases): Dedicated managers (`WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `LapsDatabaseManager`) with 1-Hz batch writing, SQLite WAL mode, and thread synchronization.
  * `IF-SWE-07` (Repository $\to$ Exporters): Binary FIT (`FitFileWriter`), XML TCX (`TCXFileWriter`), XML GPX (`GPXFileWriter`). File I/O on `Dispatchers.IO`.
  * `IF-SWE-08` (Repository $\to$ Cloud APIs & Health Connect): Retrofit/OkHttp REST endpoints (`StravaUploader`, `RunkeeperUploader`), Dropbox SDK (`DropboxUploader`), and `HealthConnectClient` (`HealthConnectManager`).
* **Contract Verification**: `AspiceSweAuditContractTest.kt` currently verifies layer headings and dynamic models; adding interface validation will ensure permanent adherence to SWE.2 BP2.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Author comprehensive interface specification `docs/architecture/interfaces.md` and integrate Section 5 into `docs/architecture.md`.
  * Detail all 8 interface families (`IF-SWE-01`..`IF-SWE-08`) covering provider, consumer, operations, data payloads, threading, and error behavior.
  * Extend `AspiceSweAuditContractTest.kt` to assert presence and completeness of all interface specifications.
  * Define `REQ-PRO-044` in `req_process_stability.md` and `TST-PRO-044` in `tst_process_stability.md`.
* **Out-of-Scope Non-Goals**: Modifying existing API contracts or Android production source code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-044` (*ASPICE SWE.2 Software Interface Specification*), refining `REQ-PRO-034` and `REQ-PRO-043`. Targets `docs/architecture.md`, `docs/architecture/interfaces.md`, and `AspiceSweAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Pre-assessment finding C-06 (2026-11-08) identified missing interface contracts.
* **Root Reason for Existing Formulation**: High-level block diagrams were established first; interface data contracts were left implicit in code.
* **Preservation of Core Invariants**: Existing SWE.2 base practices, layers, and living documentation structure remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
* Create `docs/architecture/interfaces.md` containing formal tables for `IF-SWE-01` through `IF-SWE-08`.
* Integrate summary interface matrix in `docs/architecture.md` (Section 5) referencing detailed interface specifications.
* Add contract assertions in `AspiceSweAuditContractTest.kt` verifying all interface IDs and their required fields.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime code modifications; 100% build test pass rate.
* **Risk Rating**: **LOW**. Documentation and build contract testing only.
