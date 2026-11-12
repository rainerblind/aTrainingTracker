# Stage 2: Requirement & Test Specification - ATT-6167: System Verification Protocol

**Ticket**: [ATT-6167](https://rainerblind.atlassian.net/browse/ATT-6167)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `subagent-Lane-2-Agent---ATT-6167-self-028e4c80`  
**Requirement Mapping**: `REQ-PRO-069` (*System Verification Protocol & Real Sensor Hardware Reference Matrix Specification*)  
**Test Spec ID**: `TST-PRO-069`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-069)

### 1.1 Problem Statement & Rationale
ASPICE 4.0 pre-assessment finding S-07 (SYS.5 BP1–BP7) identified that on-device checks in aTrainingTracker are currently ad-hoc developer walkthroughs without release-level system verification against system requirements. A formal system verification protocol (`docs/system/sys_verification_protocol.md`) executed on real devices and sensors per release is required to verify the product against system requirements in its target operational environment.

### 1.2 Functional & Technical Requirements
* **REQ-PRO-069.1** (*Hardware Reference Matrix*): `docs/system/sys_verification_protocol.md` SHALL define a reference matrix covering mobile devices (Google Pixel reference family, Android 14/15), BLE Heart Rate monitors (Polar H10, Garmin HRM-Pro), BLE/ANT+ Power meters (Favero Assioma Duo, Stages, Wahoo KICKR), Garmin Varia Cycling Radar (RTL515), and Barometric Altimeter / Multi-GNSS.
* **REQ-PRO-069.2** (*Environmental Operating Conditions Matrix*): Protocol SHALL specify 5 quantified operational environments: (E1) Urban Canyon, (E2) Rural Road / High Speed, (E3) Alpine Climb & Barometric Variation, (E4) Indoor Smart Trainer, and (E5) Extended Endurance & Battery Longevity.
* **REQ-PRO-069.3** (*Structured Verification Cases*): Protocol SHALL define formal verification test cases (`SYS-VRF-001` through `SYS-VRF-008`) covering all system requirements (`SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`).
* **REQ-PRO-069.4** (*Data Integrity, Reconnection & Release Sign-Off Record*): Protocol SHALL define objective pass/fail criteria (FIT/TCX integrity, Strava/Dropbox sync, RF reconnection within backoff window, sample loss $\le 1\%$, battery drain $\le 8\%$/hr) and provide a release sign-off record template under `docs/releases/<tag>/sys_verification_record.md`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Hardware & Environment Matrix)**: *Given* `docs/system/sys_verification_protocol.md`, *When* inspected, *Then* it specifies Google Pixel reference devices, BLE HRM, BLE/ANT+ Power meters, Garmin Varia Radar, Barometer/GNSS, and the 5 environmental scenarios.
* **Criterion 2 (SYSREQ Bidirectional Traceability)**: *Given* the verification test cases (`SYS-VRF-001`..`008`), *When* mapped against system requirements, *Then* 100% of `SYSREQ-TRK-*` and `SYSREQ-DEX-*` requirements are verified.
* **Criterion 3 (Contract Test & Release Template)**: *Given* `SystemVerificationProtocolContractTest.kt`, *When* executed via Gradle, *Then* it passes with 100% success and confirms release template and document structure.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-069` (*System Verification Protocol and Hardware Reference Specification*), fulfilling ASPICE SYS.5 under Parent Epic ATT-6142; renumbering legacy duplicate line 10 heading `REQ-UI-518` to unique `REQ-UI-521` (*Weekly Period Date Range*) to preserve unique ID invariant.
* **Historical Origin & Commit Trace**: Pre-assessment remediation finding S-07 (SYS.5); commit `c66efd53` (ATT-6123) and `517bcc4b` (ATT-7312) in sprint 2026-46.4.
* **Root Reason for Existing Formulation**: ATT-6123 renumbered line 10 to 518 concurrently with ATT-7312 adding REQ-UI-518, creating a duplicate ID collision across branches.
* **Preservation of Core Invariants**: Zero runtime code modifications in `app/src/main/`; pure governance, system documentation, and build contract tests; 100% test pass rate and unique ID parity.

---

## 2. Test Specification (TST-PRO-069)

### Test Case 1: Protocol Structural Integrity & Metadata Header (`TST-PRO-069.1`)
* **Scope**: Contract Test (`SystemVerificationProtocolContractTest.kt`)
* **Assertion**: Verify `docs/system/sys_verification_protocol.md` exists and contains required metadata header referencing ASPICE SYS.5, REQ-PRO-069, TST-PRO-069, and Mermaid workflow diagrams.

### Test Case 2: Hardware Reference & Environmental Conditions Matrices (`TST-PRO-069.2`)
* **Scope**: Contract Test (`SystemVerificationProtocolContractTest.kt`)
* **Assertion**: Verify specification of Google Pixel reference devices, BLE HRM, Cycling Power, Garmin Varia Radar, Barometer/GNSS, and 5 environmental test matrices.

### Test Case 3: System Verification Cases & SYSREQ Traceability (`TST-PRO-069.3`)
* **Scope**: Contract Test (`SystemVerificationProtocolContractTest.kt`)
* **Assertion**: Verify formal test cases (`SYS-VRF-001` through `SYS-VRF-008`) trace bidirectionally to `SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`.

### Test Case 4: Objective Pass/Fail Criteria & Release Record Template (`TST-PRO-069.4`)
* **Scope**: Contract Test (`SystemVerificationProtocolContractTest.kt`)
* **Assertion**: Verify pass/fail thresholds for FIT/TCX integrity, Strava/Dropbox sync, RF reconnection, sample loss $\le 1\%$, battery drain $\le 8\%$/hr, and release sign-off record template.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-069.1` | Contract | `docs/system/sys_verification_protocol.md`, `SystemVerificationProtocolContractTest.kt` | `REQ-PRO-069.1` | Planned |
| `TST-PRO-069.2` | Contract | `docs/system/sys_verification_protocol.md`, `SystemVerificationProtocolContractTest.kt` | `REQ-PRO-069.1`, `REQ-PRO-069.2` | Planned |
| `TST-PRO-069.3` | Contract | `docs/system/sys_verification_protocol.md`, `SystemVerificationProtocolContractTest.kt` | `REQ-PRO-069.3` | Planned |
| `TST-PRO-069.4` | Contract | `docs/system/sys_verification_protocol.md`, `SystemVerificationProtocolContractTest.kt`, `docs/architecture.md` | `REQ-PRO-069.4` | Planned |
