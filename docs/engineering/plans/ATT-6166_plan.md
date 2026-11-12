# Stage 3: Implementation Plan - ATT-6166: System Integration Verification

**Ticket**: [ATT-6166](https://rainerblind.atlassian.net/browse/ATT-6166)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `subagent-Lane-2-Agent---ATT-6166-self-a6b551bd`  
**Requirement Mapping**: `REQ-PRO-068` (*System Integration Verification & Sensor Simulators Specification*)  
**Test Spec ID**: `TST-PRO-068`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE 4.0 pre-assessment finding S-06 (SYS.4 BP1–BP7) identified that while wireless sensor and GPS simulators exist in the repository (`tools/ble_hr_simulator.py`, `tools/ble_power_simulator.py`, `tools/ble_radar_simulator.py`, `tools/fake_gps.py`, `tools/replay_workout.py`), no formal system integration test specification or repeatable verification procedure exists. Without `docs/system/sys_integration_tests.md` and a headless execution harness (`tools/run_system_integration_tests.py`), interactions between phone software (`SYS-EL-01`) and external system elements (`SYS-EL-02` BLE, `SYS-EL-03` ANT+, `SYS-EL-04` GNSS, `SYS-EL-05` Cloud) cannot be verified reproducibly in CI or audited against ASPICE SYS.4.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-068` (*System Integration Verification & Sensor Simulators Specification*)
* **Test Mapping**: `TST-PRO-068` (*System Integration Verification Contract Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-068.1` -> `TST-PRO-068.1`, `TST-PRO-068.2` (`docs/system/sys_integration_tests.md` specification)
  - `REQ-PRO-068.2` -> `TST-PRO-068.3` (`tools/run_system_integration_tests.py` execution)
  - `REQ-PRO-068.3` -> `TST-PRO-068.4` (`tools/test_system_integration_tests.py` unit suite)
  - `REQ-PRO-068.4` -> `TST-PRO-068.1`, `TST-PRO-068.2` (`SystemIntegrationTestContractTest.kt` contract test)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production code in `app/src/main/` or database schemas.
2. **Audit Rule Compliance (ATT-6186)**: Contract tests MUST NOT read production source files (`app/src/main/`).
3. **SYSREQ Traceability 100%**: Every integration test case maps to >= 1 system requirement (`SYSREQ-*`) and external system interface (`SYS-IF-*`).
4. **Standard Library Footprint**: Pure Python standard library for new integration verification scripts.
5. **Headless Execution Invariant**: Runner must execute cleanly in CI without requiring Bluetooth hardware or a display.

---

## 4. Proposed Architectural Changes
* **System Integration Test Specification (`docs/system/sys_integration_tests.md`)**:
  - Author formal SYS.4 integration specification defining test strategy, test environments (Headless CI vs Real Hardware/ADB), and 6 formal test cases (`SYS-INT-001` through `SYS-INT-006`).
  - Map cases to interfaces: `SYS-IF-01` (BLE HR `0x180D`, Power `0x1818`, Radar `6a4e3200`), `SYS-IF-02` (ANT+ IPC), `SYS-IF-03` (GNSS NMEA/Mock), and `SYS-IF-05` (Cloud Sync / Strava REST).
  - Document execution commands, timing tolerances, pass/fail thresholds, and release artifact logging under `docs/releases/<tag>/`.
* **Headless Integration Test Runner (`tools/run_system_integration_tests.py`)**:
  - Author automated runner executing headless simulation verifications across sensor payloads and mock responses, writing structured JSON logs.
* **Runner Unit Test Suite (`tools/test_system_integration_tests.py`)**:
  - Standard library unit tests verifying CLI flags, test handlers, and exit codes.
* **JUnit Contract Test (`SystemIntegrationTestContractTest.kt`)**:
  - Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemIntegrationTestContractTest.kt` asserting document presence, interface coverage, requirement parity, and command availability.
* **Continuous SWE.2 Architecture Maintenance (`docs/architecture.md`)**:
  - Update `docs/architecture.md` §4.10 referencing SYS.4 system integration verification.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Create `docs/system/sys_integration_tests.md` (REQ-PRO-068.1)
* Author formal specification documenting test cases `SYS-INT-001` through `SYS-INT-006`, interfaces, timing, and commands.

### Step 2: Implement `tools/run_system_integration_tests.py` (REQ-PRO-068.2)
* Author headless runner with CLI flags (`--headless`, `--output`, `--verbose`), payload generators, and JSON report writing.

### Step 3: Implement `tools/test_system_integration_tests.py` (REQ-PRO-068.3)
* Author Python unittest suite verifying runner flags, simulation dispatchers, and result schemas.

### Step 4: Implement `SystemIntegrationTestContractTest.kt` (REQ-PRO-068.4)
* Author JUnit contract test asserting specification integrity, interface coverage, and requirement mapping.

### Step 5: Update `docs/architecture.md` (ATT-5431)
* Update `docs/architecture.md` §4.10 referencing SYS.4 system integration verification.

### Step 6: Execute Targeted Verification & Governance Parity
* Run `python3 tools/run_system_integration_tests.py`.
* Run `python3 -m unittest tools/test_system_integration_tests.py`.
* Run `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemIntegrationTestContractTest"`.
* Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` + Python test suites.
* **Rollback Plan**: Git worktree branch isolation ensures immediate clean revert if needed.
