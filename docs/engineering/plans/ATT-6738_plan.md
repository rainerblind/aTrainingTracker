# Stage 3: Implementation Plan - ATT-6738: Fix hallucinated sensors, FGS types, and omitted cloud services in system_context.md

**Ticket**: [ATT-6738](https://rainerblind.atlassian.net/browse/ATT-6738)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-053`  
**Test Mapping**: `TST-PRO-053`  
**Branch**: `feature/ATT-6738`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

`docs/system/system_context.md` serves as the authoritative ASPICE SYS.2/SYS.3 boundary document for aTrainingTracker. An automated audit identified hallucinated sensor constants (`TYPE_STEP_COUNTER`, `TYPE_ACCELEROMETER`), omitted hardware proximity sensor (`Sensor.TYPE_PROXIMITY`), incorrect foreground service type (`CONNECTED_DEVICE` instead of `location|health`), inaccurate Android 16 label for targetSdk 37 (Android 17), stale `.java` file extensions for Kotlin uploaders, missing reference to `SpeedAndLocationDevice_GoogleFused.java`, and omissions of active external services (Open-Meteo, Firebase, Google Play Review/Auth, BLE `0x180A`, Varia V1/Light Control UUIDs, Health Connect HRV/RHR read).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-053` (*System Context and External Interfaces Specification*)
* **Test Mapping**: `TST-PRO-053` (*System Context Specification and Code-Grounding Contract Test Suite*)
  * `TST-PRO-053.1`: System context structural & Mermaid diagram verification
  * `TST-PRO-053.2`: External elements & live code grounding parity test
  * `TST-PRO-053.3`: Dynamic referenced code file & symbol existence verification
  * `TST-PRO-053.4`: Targeted clean-room unit test execution

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Runtime Code Changes**: No modifications to runtime application bytecode in `app/src/main/`.
2. **ATT-6186 Source-Text Reading Audit Compliance**: Contract tests verify structure, JVM constants, and filesystem existence without reading production source files.
3. **100% Test Pass Rate**: Existing unit and contract tests continue to pass with zero regressions.
4. **Subtask & Gate Invariance**: Stage subtasks transition to `Erledigt` via gate verdicts; parent ticket completion reserved for human in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: System Context Specification (`docs/system/system_context.md`)
- Update Mermaid diagram to reflect `Sensor.TYPE_PRESSURE` and `Sensor.TYPE_PROXIMITY`, `location|health` FGS types, API 37 (Android 17), Open-Meteo Elevation API, Firebase, Google Play Auth/Review, BLE `0x180A`, Varia V1/Light Control UUIDs, and Health Connect HRV/RHR read.
- Align Section 3 inventory (3.1 Sensors, 3.2 Platform, 3.3 Location, 3.4 Cloud) to authentic production code.
- Ground fused location provider with `SpeedAndLocationDevice_GoogleFused.java` (`GoogleApiClient` / `LocationServices.FusedLocationApi`).

### Component 2: System Context Contract Test (`SystemContextContractTest.kt`)
- Harden with dynamic regex parsing of backticked code files asserting `File.exists()` on disk.
- Dynamically verify `Sensor.TYPE_*` and `FOREGROUND_SERVICE_TYPE_*` platform symbols.
- Assert grounded BLE, Cloud, Platform, and Location constants.

### Component 3: Living Requirements & Test Shards
- Synchronize `docs/requirements/req_process_stability.md`, `docs/requirements.md`, `docs/tests/tst_process_stability.md`, and `docs/tests.md`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `docs/system/system_context.md`
* Update Mermaid diagram and Sections 3.1 through 3.4 with code-grounded sensors, FGS types, API 37 label, Kotlin uploaders, Open-Meteo, Firebase, Google Play, BLE/Varia UUIDs, and Health Connect HRV/RHR read capabilities.

### Step 2: Synchronize Living Documentation Shards
* Update `docs/requirements/req_process_stability.md`, `docs/requirements.md`, `docs/tests/tst_process_stability.md`, and `docs/tests.md` with refined `REQ-PRO-053` and `TST-PRO-053`.

### Step 3: Harden `SystemContextContractTest.kt`
* Implement `testSystemContext_allReferencedCodeFilesExistOnDisk` and `testSystemContext_referencedPlatformSymbolsGrounded`.
* Enhance `testSystemContext_codeGroundingParity` with assertions for new grounded constants.

### Step 4: Verification & Governance
* Run `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"`.
* Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted contract test suite execution under lock, followed by full living doc governance audit.
* **Rollback**: Clean git branch revert on `feature/ATT-6738` with zero impact on other branches.
