# Stage 3: Implementation Plan - ATT-6158: Define system context and external system elements

**Ticket**: [ATT-6158](https://rainerblind.atlassian.net/browse/ATT-6158)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-PRO-053` (`REQ-PRO-053.1`, `REQ-PRO-053.2`)  
**Test Mapping**: `TST-PRO-053` (`TST-PRO-053.1`, `TST-PRO-053.2`, `TST-PRO-053.3`)  
**Branch**: `feature/ATT-6158`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE pre-assessment finding S-01 (SYS.2 BP1, SYS.3) identified that aTrainingTracker lacks a defined system level and system context boundary. This plan establishes `docs/system/system_context.md` detailing the authoritative system context diagram, 5 external element domains grounded in live source code, and operational assumptions, alongside an automated contract test suite in `SystemContextContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-053` (*System Context and External Interfaces Specification*)
* **Test Mapping**: `TST-PRO-053` (*System Context Specification and Code-Grounding Contract Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-053.1` -> `TST-PRO-053.1`, `TST-PRO-053.2` (`docs/system/system_context.md` structure & code grounding)
  - `REQ-PRO-053.2` -> `TST-PRO-053.2`, `TST-PRO-053.3` (`SystemContextContractTest` targeted suite execution)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production classes in `app/src/main/` or database schemas.
2. **Offline-First Telemetry Guarantee**: Local tracking and SQLite persistence remain functional without active network access.
3. **Decommissioned Service Demarcation**: Runkeeper and TrainingPeaks remain permanently decommissioned with DB columns preserved for schema stability.
4. **Clean-Room Verification**: 100% test pass rate across targeted unit/contract test suites.

---

## 4. Proposed Architectural Changes
* **System Specification (`docs/system/system_context.md`)**:
  - Create authoritative system context documentation under `docs/system/`.
  - Include Mermaid context diagram illustrating boundaries between Athlete, aTrainingTracker Application, Hardware Sensors, Android OS, and Cloud Platforms.
  - Detail 5 external element domains: Sensors & Peripherals, Platform Frameworks, Location & Maps, Cloud & Health, and Decommissioned Platforms.
  - Document operational assumptions: Android versions (minSdk 26, targetSdk 37), sensor reconnect timeouts, and asynchronous cloud sync.
* **System Context Contract Test (`SystemContextContractTest.kt`)**:
  - Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemContextContractTest.kt`.
  - Assert existence of `docs/system/system_context.md`, valid Mermaid syntax, 5-domain coverage, and source code grounding (BLE UUIDs, ANT+ packages, cloud dependencies, decommissioned tags).
* **UI Consistency**: No UI changes (system-level documentation & contract test only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Create `docs/system/system_context.md` (REQ-PRO-053.1)
* Author `docs/system/system_context.md` with Mermaid system context diagram and complete external element inventory across all 5 domains.
* Ground each external element in live source code (`BluetoothConstants.java`, `AndroidManifest.xml`, `app/build.gradle`).

### Step 2: Implement `SystemContextContractTest.kt` (REQ-PRO-053.2)
* Author `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemContextContractTest.kt`.
* Implement `testSystemContext_fileExistsAndContainsMermaidDiagram()`, `testSystemContext_coversAllFiveInterfaceDomains()`, and `testSystemContext_codeGroundingParity()`.
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"`

### Step 3: Living Doc Verification & Parity Check (TST-PRO-053.3)
* Run `python3 tools/verify_requirement_governance.py` to assert 100% living documentation parity.

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"`
* **Rollback Plan**: Branch isolation on `feature/ATT-6158` ensures clean revert with zero impact on `sprint/2026-46.1`.
