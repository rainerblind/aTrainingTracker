# Stage 3: Implementation Plan - ATT-6164: Create system architectural design

**Ticket**: [ATT-6164](https://rainerblind.atlassian.net/browse/ATT-6164)  
**Sub-task**: [ATT-6665](https://rainerblind.atlassian.net/browse/ATT-6665) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6164`  
**Requirement Mapping**: `REQ-PRO-056` (*System Architectural Design & SYSREQ Allocation*)  
**Test Spec ID**: `TST-PRO-056`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE 4.0 pre-assessment finding S-05 (SYS.3 BP1-BP6) identified that aTrainingTracker lacks a formal system architectural design specification. While SWE.2 software architecture (`docs/architecture.md`) details internal app classes, system-level architecture defining system elements (`SYS-EL-01`..`05`), system interfaces (`SYS-IF-01`..`06`), dynamic interactions, and 100% allocation of all 24 system requirements (`SYSREQ-*`) across mobile hardware, sensors, OS platform services, and cloud platforms is missing. Creating `docs/system/system_architecture.md` and verifying it via contract testing closes this ASPICE CL2 gap.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-056` (*System Architectural Design & SYSREQ Allocation*)
* **Test Mapping**: `TST-PRO-056` (*System Architecture Design Contract Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-056.1` -> `TST-PRO-056.1`, `TST-PRO-056.2`, `TST-PRO-056.3` (`docs/system/system_architecture.md` specification)
  - `REQ-PRO-056.2` -> `TST-PRO-056.3`, `TST-PRO-056.4` (`SystemArchitectureDesignContractTest.kt` execution)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production code in `app/src/main/` or database schemas.
2. **Audit Rule Compliance (ATT-6186)**: Contract tests MUST NOT read production source files (`app/src/main/`).
3. **SYSREQ Traceability 100%**: Every one of the 24 `SYSREQ-*` system requirements maps to >= 1 system element and interface.
4. **Clean-Room Verification**: 100% pass rate in targeted test suite under lock and 100% living doc parity.

---

## 4. Proposed Architectural Changes
* **System Architecture Specification (`docs/system/system_architecture.md`)**:
  - Author formal SYS.3 system architecture document under `docs/system/`.
  - Define 5 System Elements: `SYS-EL-01` (Smartphone Host App), `SYS-EL-02` (BLE GATT Sensors), `SYS-EL-03` (ANT+ Radio & Sensors), `SYS-EL-04` (Android OS Platform Services), `SYS-EL-05` (Cloud Platforms).
  - Define 6 System Interfaces: `SYS-IF-01` (BLE GATT), `SYS-IF-02` (ANT+ IPC), `SYS-IF-03` (GNSS/Sensors), `SYS-IF-04` (Power/Lifecycle), `SYS-IF-05` (Cloud REST/Sync), `SYS-IF-06` (Local Storage/VFS).
  - Define Dynamic Behavior: 2 Mermaid `sequenceDiagram` models (1-Hz Live Telemetry Loop; Cloud Sync & Backup).
  - Allocation Matrix: Complete 100% mapping of all 24 `SYSREQ-*` requirements to `SYS-EL-*` and `SYS-IF-*`.
* **System Architecture Contract Test (`SystemArchitectureDesignContractTest.kt`)**:
  - Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`.
  - Assert presence of `system_architecture.md`, definition of all 5 elements and 6 interfaces, valid Mermaid blocks, and 100% SYSREQ allocation matrix without reading production source files.
* **UI Consistency**: No UI changes (architecture specification and contract testing only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Create `docs/system/system_architecture.md` (REQ-PRO-056.1)
* Author `docs/system/system_architecture.md` detailing elements `SYS-EL-01`..`05`, interfaces `SYS-IF-01`..`06`, dynamic sequences, and the 24-requirement SYSREQ allocation matrix.

### Step 2: Implement `SystemArchitectureDesignContractTest.kt` (REQ-PRO-056.2)
* Author `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`.
* Implement assertions for file existence, elements, interfaces, sequence diagrams, and 100% SYSREQ allocation coverage.
* Run: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDemoDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest"`

### Step 3: Living Doc Verification & Parity Check (TST-PRO-056.4)
* Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-46.1` to confirm 100% living documentation parity.

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew testDemoDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Rollback Plan**: Branch isolation on `feature/ATT-6164` ensures clean revert with zero impact on `sprint/2026-46.1`.
