# Stage 1 Analysis: ATT-6164 - Create system architectural design

**Ticket**: [ATT-6164](https://rainerblind.atlassian.net/browse/ATT-6164)  
**Sub-task**: [ATT-6662](https://rainerblind.atlassian.net/browse/ATT-6662) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6164`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 pre-assessment finding S-05 (SYS.3 BP1-BP6) identified that aTrainingTracker lacks a formal system architectural design. While software architecture (`docs/architecture.md`) details internal SWE.2 software components, the system-level architecture defining system elements, external/internal system interfaces, dynamic behavior, and the 100% allocation of system requirements (`SYSREQ-*`) across mobile hardware, peripheral sensors, Android OS services, and cloud endpoints is undocumented. Establishing `docs/system/system_architecture.md` satisfies ASPICE SYS.3.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Missing System Architectural Design (SYS.3 Gap)**:
   - Engineering documentation historically leaped directly from high-level user stories to software code and classes, omitting the ASPICE SYS.3 system architecture layer that bridges system requirements (SYS.2) to software architecture (SWE.2).
2. **Independent Peer Review & Verification (ATT-5431)**:
   - Audited System Requirements: Verified 24 system requirements across `SYSREQ-TRK-*` (live tracking, sensor profiles, GNSS, power), `SYSREQ-DEX-*` (export/import, cloud APIs, backup, security), and existing telemetry domains.
   - Audited System Elements: Identified 5 primary system elements:
     - `SYS-EL-01`: Android Smartphone Host Application (aTrainingTracker Core App: Services, ViewModels, Compose UI, SQLite DBs).
     - `SYS-EL-02`: BLE GATT Peripheral Sensors (HRM, CSC, Power, Radar, Battery).
     - `SYS-EL-03`: ANT+ Wireless Sensors & Radio IPC Services (HRM, Bike Power, Speed/Cadence).
     - `SYS-EL-04`: Android OS Platform Services (GNSS/Fused Location, PowerManager WakeLock, AudioTrack, Storage).
     - `SYS-EL-05`: External Cloud Platforms & OAuth2 REST Services (Strava, Dropbox, Google Drive, Health Connect).
   - Audited System Interfaces: Derived 6 distinct system interfaces (`SYS-IF-01`..`06`) detailing protocols (GATT, ANT+ IPC, LocationListener, FGS/WakeLock, HTTPS REST, SQLite/VFS), payload data schemas, and timing/rate contracts.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Author authoritative system architectural design document `docs/system/system_architecture.md`:
    (1) System elements inventory (`SYS-EL-01`..`SYS-EL-05`) with physical execution boundaries,
    (2) System interface specifications (`SYS-IF-01`..`SYS-IF-06`) defining protocol, data payload, and timing/rate bounds,
    (3) Dynamic behavior sequence diagrams (Mermaid `sequenceDiagram` for Live Telemetry and Cloud Sync/Backup),
    (4) 100% System Requirements Allocation Table mapping all `SYSREQ-*` to system elements.
  - Define living requirement `REQ-PRO-056` in `docs/requirements/req_process_stability.md` and test spec `TST-PRO-056` in `docs/tests/tst_process_stability.md`.
  - Author automated contract test `SystemArchitectureDesignContractTest.kt` under `app/src/test/.../build/` asserting structural completeness, interface properties, and 100% allocation coverage without reading production source files.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying Android production runtime code in `app/src/main/` or existing software architecture in `docs/architecture.md`.
  - Adding new hardware or cloud integrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-056` (*System Architectural Design Specification*), establishing ASPICE SYS.3 baseline under Parent Epic ATT-6142. Targets `docs/system/system_architecture.md` and `SystemArchitectureDesignContractTest.kt`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-05 (2026-11-08) and ticket ATT-6164.
* **Root Reason for Existing Formulation**: Project previously managed system behavior informally at the software code level without an explicit system architectural design linking SYS.2 to SWE.2.
* **Preservation of Core Invariants**: Zero runtime code changes; existing software boundaries, thread models, and database managers remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
* `docs/system/system_architecture.md`: Specify complete ASPICE SYS.3 system architecture:
  - System decomposition into 5 system elements (`SYS-EL-01` Host App, `SYS-EL-02` BLE, `SYS-EL-03` ANT+, `SYS-EL-04` OS, `SYS-EL-05` Cloud).
  - 6 System Interfaces (`SYS-IF-01` BLE GATT, `SYS-IF-02` ANT+ IPC, `SYS-IF-03` GNSS/Location, `SYS-IF-04` Power/FGS, `SYS-IF-05` Cloud REST, `SYS-IF-06` Storage/DB).
  - Dynamic sequence diagrams illustrating live 1-Hz tracking pipeline and post-workout cloud export/backup.
  - 100% Traceability Allocation Matrix mapping every `SYSREQ-*` to allocated system elements.
* `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`: Automated contract test enforcing document existence, element definitions, interface contracts, Mermaid syntax, and allocation coverage.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero modifications to production code in `app/src/main/`; 100% unit test pass rate; living documentation index parity; Parent ticket Human Decision Gate preserved.
* **Risk Rating**: **LOW** (3 SP, system-level architecture specification and contract testing baseline without runtime code impacts).
