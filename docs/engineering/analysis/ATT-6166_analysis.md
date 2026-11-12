# Stage 1 Analysis: ATT-6166 - Define system integration verification with sensor simulators

**Ticket**: [ATT-6166](https://rainerblind.atlassian.net/browse/ATT-6166)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-6166-self-a6b551bd`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 pre-assessment finding S-06 (SYS.4 BP1–BP7) identified that while wireless sensor and GPS simulators exist in the repository (`tools/ble_hr_simulator.py`, `tools/ble_power_simulator.py`, `tools/ble_radar_simulator.py`, `tools/fake_gps.py`, `tools/replay_workout.py`), no formal system integration test specification or automated verification procedure exists. Without a documented system integration test specification (`docs/system/sys_integration_tests.md`) and a headless execution harness (`tools/run_system_integration_tests.py`), interactions between the host Android application (`SYS-EL-01`) and external system elements (`SYS-EL-02` BLE, `SYS-EL-03` ANT+, `SYS-EL-04` GNSS, `SYS-EL-05` Cloud) cannot be verified reproducibly or audited against ASPICE SYS.4.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Missing System Integration Strategy (SYS.4 Gap)**:
   - Sensor simulators were developed organically for ad-hoc debugging and manual verification, lacking a formal test specification mapping test cases to system interfaces (`SYS-IF-01` through `SYS-IF-05`) and system requirements (`SYSREQ-TRK-*`, `SYSREQ-DEX-*`).
2. **Independent Peer Review & Verification (ATT-5431)**:
   - Audited Existing Simulators:
     - `tools/ble_hr_simulator.py`: Implements BLE Heart Rate Service (`0x180D`, Measurement `0x2A37`), RR interval synthesis, DFA $\alpha_1$, and artifact injection.
     - `tools/ble_power_simulator.py`: Implements BLE Cycling Power Service (`0x1818`, Measurement `0x2A63`) and pedal balance asymmetry tiers.
     - `tools/ble_radar_simulator.py`: Implements Garmin Varia BLE custom radar service (`6a4e3200`, characteristic `6a4e3204`), vehicle approach kinematics, and light modes.
     - `tools/fake_gps.py`: External ADB GPX replay injecting mock GNSS fixes (`LocationManager.GPS_PROVIDER`).
     - `tools/replay_workout.py`: Synchronized multi-sensor replay (BLE Power, BLE HR, mock GPS) with headless CLI support.
   - Audited System Interfaces & Requirements:
     - Verified system context in `docs/system/system_context.md` and interfaces `SYS-IF-01` (BLE GATT), `SYS-IF-02` (ANT+ IPC), `SYS-IF-03` (GNSS), `SYS-IF-04` (OS Platform), `SYS-IF-05` (Cloud REST).
     - Verified 12 system requirements in `docs/system/sysreq_live_tracking.md` (`SYSREQ-TRK-001`..`006`) and `docs/system/sysreq_data_exchange.md` (`SYSREQ-DEX-001`..`006`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Author formal system integration test specification `docs/system/sys_integration_tests.md`:
    (1) Scope, objectives, and test harness architecture for headless CI and hardware-in-the-loop environments.
    (2) Formal test cases (`SYS-INT-001` through `SYS-INT-006`) covering all external system interfaces: BLE HR, BLE Power, BLE Radar, ANT+ Radio IPC, GNSS Positioning, and Cloud Synchronization.
    (3) Full bidirectional traceability to system requirements (`SYSREQ-*`) and system context elements (`SYS-EL-*`, `SYS-IF-*`).
    (4) Concrete automated execution commands, timing tolerances, pass/fail criteria, and release artifact logging under `docs/releases/<tag>/`.
  - Author automated runner script `tools/run_system_integration_tests.py` capable of executing headless system integration verification in CI without physical hardware, producing structured JSON test logs.
  - Author Python unit test suite `tools/test_system_integration_tests.py` and JUnit contract test `SystemIntegrationTestContractTest.kt` enforcing document structural integrity, interface coverage, and command validity.
  - Register normative requirement `REQ-PRO-068` and test spec `TST-PRO-068` in living documentation.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying Android production runtime code in `app/src/main/`.
  - Modifying existing simulator physics or D-Bus GATT server internals.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-068` (*System Integration Verification and Sensor Simulators Specification*), fulfilling ASPICE SYS.4 under Parent Epic ATT-6142. Targets `docs/system/sys_integration_tests.md`, `tools/run_system_integration_tests.py`, and `SystemIntegrationTestContractTest.kt`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-06 (2026-11-08) and ticket ATT-6166.
* **Root Reason for Existing Formulation**: Project previously had no formal system-level integration test specification connecting simulator tools to system requirements.
* **Preservation of Core Invariants**: Zero runtime code changes; existing simulators, unit tests, and governance tooling remain intact and backward compatible.

---

## 5. Change-Request Impact Analysis (REQ-PRO-064 / ASPICE SUP.10)
1. **Affected Requirements**: Net-new requirement `REQ-PRO-068` (`REQ-PRO-068.1`–`REQ-PRO-068.4`) in `docs/requirements/req_process_stability.md` and `TST-PRO-068` (`TST-PRO-068.1`–`TST-PRO-068.4`) in `docs/tests/tst_process_stability.md`.
2. **Affected Architecture Elements**: New specification `docs/system/sys_integration_tests.md`; new script `tools/run_system_integration_tests.py`; new tests `tools/test_system_integration_tests.py` and `SystemIntegrationTestContractTest.kt`; updated `docs/architecture.md` §4.10.
3. **Verification Impact & Test Surface**:
   - `python3 tools/run_system_integration_tests.py` (executes cleanly in headless CI).
   - `python3 tools/test_system_integration_tests.py` (unit test suite for runner and test case definitions).
   - `SystemIntegrationTestContractTest.kt` (JUnit contract test in `app/src/test/.../build/`).
   - `python3 tools/verify_requirement_governance.py` (master table and ID parity).
4. **Risk Assessment & Invariants**:
   - **Risk Rating**: **LOW** (3 SP, governance and system integration verification tooling; 0 files modified in `app/src/main/`).
   - **System Invariants**: Zero production runtime code changes; standard library footprint for Python tooling; 100% test pass rate.
