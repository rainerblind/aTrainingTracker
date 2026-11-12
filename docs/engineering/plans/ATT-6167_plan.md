# Stage 3: Implementation Plan - ATT-6167: System Verification Protocol

**Ticket**: [ATT-6167](https://rainerblind.atlassian.net/browse/ATT-6167)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `subagent-Lane-2-Agent---ATT-6167-self-028e4c80`  
**Requirement Mapping**: `REQ-PRO-069` (*System Verification Protocol & Real Sensor Hardware Reference Matrix Specification*)  
**Test Spec ID**: `TST-PRO-069`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE 4.0 pre-assessment finding S-07 (SYS.5 BP1–BP7) identified that on-device testing in aTrainingTracker has historically relied on ad-hoc developer walkthroughs without release-level system verification against system requirements. A standardized protocol (`docs/system/sys_verification_protocol.md`) defining hardware reference matrices, environmental scenarios, verification test cases, objective pass/fail criteria, and release sign-off records is required to verify the product against system requirements in its target operational environment.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-069` (*System Verification Protocol & Real Sensor Hardware Reference Matrix Specification*)
* **Test Mapping**: `TST-PRO-069` (*System Verification Protocol Contract Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-069.1` -> `TST-PRO-069.1`, `TST-PRO-069.2` (`docs/system/sys_verification_protocol.md` hardware matrix)
  - `REQ-PRO-069.2` -> `TST-PRO-069.2` (`docs/system/sys_verification_protocol.md` environmental conditions)
  - `REQ-PRO-069.3` -> `TST-PRO-069.3` (`docs/system/sys_verification_protocol.md` test cases `SYS-VRF-001`..`008`)
  - `REQ-PRO-069.4` -> `TST-PRO-069.4` (pass/fail thresholds, release record template, `docs/architecture.md` Section 6.3)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production code in `app/src/main/` or database schemas.
2. **Audit Rule Compliance (ATT-6186)**: Contract tests MUST NOT read production source files in `app/src/main/`.
3. **SYSREQ Traceability 100%**: Every verification case maps bidirectionally to system requirements (`SYSREQ-TRK-*`, `SYSREQ-DEX-*`).
4. **Release Governance**: System verification protocol serves as release gating standard under `docs/releases/<tag>/sys_verification_record.md`.

---

## 4. Proposed Architectural Changes
* **System Verification Protocol Specification (`docs/system/sys_verification_protocol.md`)**:
  - Document hardware reference matrix (Pixel reference devices, Polar H10 / Garmin HRM-Pro, Favero Assioma / Stages / Wahoo KICKR, Garmin Varia RTL515, Barometer/GNSS).
  - Document 5 environmental matrices: (E1) Urban Canyon, (E2) Rural Road, (E3) Alpine Climb, (E4) Indoor Smart Trainer, (E5) Extended Battery Longevity.
  - Document 8 formal test cases (`SYS-VRF-001` through `SYS-VRF-008`) covering `SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`.
  - Document objective pass/fail criteria (FIT/TCX integrity, Strava/Dropbox sync, RF reconnection, $\le 1\%$ sample drop, $\le 8\%$/hr battery drain).
  - Provide release sign-off record template under `docs/releases/<tag>/sys_verification_record.md`.
* **JUnit Contract Test (`SystemVerificationProtocolContractTest.kt`)**:
  - Implement contract assertions in `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemVerificationProtocolContractTest.kt` validating file existence, metadata headers, hardware matrix, environmental matrix, test case IDs, SYSREQ bidirectional coverage, and release template.
* **Continuous SWE.2 Architecture Maintenance (`docs/architecture.md`)**:
  - Update `docs/architecture.md` Section 6 with §6.3 linking to ASPICE SYS.5 system verification.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Author `docs/system/sys_verification_protocol.md` (REQ-PRO-069.1–REQ-PRO-069.4)
* Draft complete protocol with hardware matrix, environmental scenarios, test cases `SYS-VRF-001`..`008`, pass/fail criteria, and release sign-off template.

### Step 2: Implement `SystemVerificationProtocolContractTest.kt` (TST-PRO-069.1–TST-PRO-069.4)
* Create JUnit test class in `app/src/test/java/com/atrainingtracker/trainingtracker/build/` verifying protocol integrity, coverage, and governance constraints.

### Step 3: Update `docs/architecture.md` Section 6.3 (ATT-5431 / SYS.5)
* Document §6.3 System Verification Protocol on Real Hardware & Sensors linking to `sys_verification_protocol.md`.

### Step 4: Execute Targeted Verification & Governance Parity
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemVerificationProtocolContractTest"` under sprint lock.
* Run `python3 tools/verify_requirement_governance.py --enforce-unique`.

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` + `verify_requirement_governance.py`.
* **Rollback Plan**: Git worktree branch isolation ensures immediate clean revert if needed.
