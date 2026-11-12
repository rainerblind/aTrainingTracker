# Stage 1 Analysis: ATT-6167 - Define system verification protocol with real sensors on device

**Ticket**: [ATT-6167](https://rainerblind.atlassian.net/browse/ATT-6167)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-6167-self-028e4c80`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In the independent ASPICE 4.0 pre-assessment of 2026-11-08, finding S-07 (ASPICE SYS.5: System Verification, Base Practices BP1 through BP7) identified that on-device testing in aTrainingTracker has historically relied on ad-hoc developer walkthroughs and informal field checks. No release-level system verification protocol exists that:
1. Systematically validates product capabilities against system requirements (`SYSREQ-TRK-*` and `SYSREQ-DEX-*`) on target hardware in real-world operating environments.
2. Defines a formal hardware reference matrix (smartphones, BLE HRM straps, BLE/ANT+ power meters, Garmin Varia cycling radars, barometric altimeters/GNSS).
3. Defines objective pass/fail criteria and quantifiable environmental operating conditions (urban canyon, rural high-speed, steep climbs, indoor stationary trainers, long-duration battery stress).
4. Provides a reproducible sign-off record template for release qualification under `docs/releases/<tag>/sys_verification_record.md`.

Without a standardized system verification protocol (`docs/system/sys_verification_protocol.md`) and automated contract tests validating its completeness, release qualification cannot satisfy ASPICE CL2 SYS.5.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification (ATT-5431)
1. **Ad-Hoc Manual Walkthroughs vs. Normative System Verification (SYS.5 Gap)**:
   - Walkthroughs in `docs/engineering/walkthroughs/` verify atomic ticket changes, but do not provide an end-to-end, multi-sensor verification protocol against formal system requirements (`SYSREQ-TRK-001..006`, `SYSREQ-DEX-001..006`) across realistic environmental conditions.
2. **Hardware & Operating Environment Heterogeneity**:
   - Modern mobile endurance sports apps interface with heterogeneous physical RF peripherals (BLE 4.x/5.x GATT, ANT+ 2.4 GHz IPC), varying GNSS constellations (GPS, GLONASS, Galileo, BeiDou), barometric altimeters, and diverse Android OS power management policies (Doze mode, battery saver, aggressive vendor background killers).
   - A formal hardware reference matrix (e.g. Google Pixel reference devices, Polar H10 / Garmin HRM-Pro, Favero Assioma / Stages cycling power, Garmin Varia RTL515 radar) is necessary to ensure consistent, reproducible verification across releases.
3. **Audit of Existing System Baseline**:
   - `docs/system/system_context.md`: Defines system elements `SYS-EL-01` (App Core), `SYS-EL-02` (BLE Sensors), `SYS-EL-03` (ANT+ Sensors), `SYS-EL-04` (GNSS / Barometer), `SYS-EL-05` (Cloud REST Ecosystem).
   - `docs/system/sysreq_live_tracking.md`: Defines 6 system requirements for live tracking (`SYSREQ-TRK-001` through `SYSREQ-TRK-006`).
   - `docs/system/sysreq_data_exchange.md`: Defines 6 system requirements for data exchange (`SYSREQ-DEX-001` through `SYSREQ-DEX-006`).
   - `docs/system/sys_integration_tests.md`: Covers virtual/simulator system integration (ASPICE SYS.4). SYS.5 completes the V-model by specifying real-hardware verification in the target operational environment.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Author `docs/system/sys_verification_protocol.md`:
     - **Hardware Reference Matrix**: Google Pixel reference devices (Android 14/15), BLE HRM (Polar H10, Garmin HRM-Pro), BLE/ANT+ Power Meters (Favero Assioma, Stages, Wahoo KICKR), Garmin Varia Radar (RTL515), Barometric Altimeter & GNSS sensors.
     - **Environmental Operating Conditions**: 5 core scenarios: (E1) Urban Canyon, (E2) Rural Road / High Speed, (E3) Alpine Climb & Barometric Variation, (E4) Indoor Smart Trainer, (E5) Extended Endurance & Battery Longevity.
     - **Structured System Verification Cases**: Formal test cases (`SYS-VRF-001` through `SYS-VRF-008`) systematically covering all `SYSREQ-TRK-*` and `SYSREQ-DEX-*` requirements with explicit setup, execution steps, expected telemetry, and pass/fail thresholds.
     - **Objective Pass/Fail Criteria & Data Integrity**: Explicit checks for FIT/TCX export validity, Strava/Dropbox sync, RF reconnection within backoff window, sample loss $\le 1\%$, battery drain rate $\le 8\%$/hr.
     - **Release Sign-Off Record Template**: Standardized template for `docs/releases/<tag>/sys_verification_record.md`.
  2. Implement verification contract test `SystemVerificationProtocolContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/` verifying document existence, section headers, test case IDs, hardware matrix, environment matrix, SYSREQ bidirectional tracing, and release template structure.
  3. Update `docs/architecture.md` Section 6 to document ASPICE SYS.5 system verification and trace to `sys_verification_protocol.md`.
  4. Register living documentation entries `REQ-PRO-069` and `TST-PRO-069` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md` with 100% master index parity.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No changes to Android production runtime code in `app/src/main/`.
  - No physical execution of hardware field tests during automated CI run (protocol governs release qualification).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-069` (*System Verification Protocol and Hardware Reference Specification*), fulfilling ASPICE SYS.5 under Parent Epic ATT-6142. Targets `docs/system/sys_verification_protocol.md`, `docs/architecture.md`, and `SystemVerificationProtocolContractTest.kt`.
* **Historical Origin & Commit Trace**: Pre-assessment remediation finding S-07 (SYS.5) following S-06 (ATT-6166, SYS.4 integration tests) and S-05 (ATT-6164, SYS.3 architecture).
* **Root Reason for Existing Formulation**: Previously, on-device checks were conducted informally without a traceable matrix, leaving a critical compliance gap in ASPICE SYS.5.
* **Preservation of Core Invariants**: Document-only and contract-test scope. Zero disruption to existing tracking, sensor connectivity, export engines, or database layers.

---

## 5. Change-Request Impact Analysis (REQ-PRO-064 / ASPICE SUP.10)
1. **Affected Requirements**: Net-new requirement `REQ-PRO-069` (`REQ-PRO-069.1`–`REQ-PRO-069.4`) in `docs/requirements/req_process_stability.md` and `TST-PRO-069` (`TST-PRO-069.1`–`TST-PRO-069.4`) in `docs/tests/tst_process_stability.md`.
2. **Affected Architecture Elements**: New specification `docs/system/sys_verification_protocol.md`; new contract test `SystemVerificationProtocolContractTest.kt`; updated `docs/architecture.md` Section 6.
3. **Verification Impact & Test Surface**:
   - `SystemVerificationProtocolContractTest.kt` (JUnit contract test in `app/src/test/.../build/`).
   - `python3 tools/verify_requirement_governance.py` (master index sync and ID parity).
4. **Risk Assessment & Invariants**:
   - **Risk Rating**: **LOW** (3 SP, documentation and build contract test; zero changes to `app/src/main/`).
   - **System Invariants**: Invariant preservation across all sensor pipelines and database schemas; zero runtime regression risk.
