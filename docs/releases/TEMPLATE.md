# Release Qualification Dossier: Release <TAG> (ASPICE SUP.8, SYS.4, SYS.5)

**Release Tag**: `<TAG>` (e.g. `V5.0.0__263` / `V5.0.0`)  
**Target Version Name**: `<VERSION_NAME>`  
**Target Version Code**: `<VERSION_CODE>`  
**Release Date**: `YYYY-MM-DD`  
**Release Manager**: `<NAME>`  
**Overall Release Verdict**: **[ PASS / REVISION / FAIL ]**  

---

## 1. Executive Summary & Verification Gating Checklist
In accordance with **REQ-PRO-072** and `.agents/skills/release-manager/SKILL.md`, release qualification requires mandatory system-level evidence before release candidate tag creation and distribution.

| Gate | Category | Evidence Artifact | Required Threshold | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **Gate 1** | Unit & Component Verification | Gradle unit test reports | 100% test pass rate, 0 failures | [ PASS / FAIL ] |
| **Gate 2** | Code Signing & Integrity | `apksigner verify` & AAB signatures | v2 scheme = true, signer = aTrainingTracker | [ PASS / FAIL ] |
| **Gate 3** | System Integration Verification (SYS.4) | `docs/releases/<TAG>/system_integration_results.json` | 6/6 tests PASS, 0 failures | [ PASS / FAIL ] |
| **Gate 4** | System Verification Protocol (SYS.5) | `docs/releases/<TAG>/sys_verification_record.md` | 8/8 tests PASS, formal QA sign-off | [ PASS / FAIL ] |

---

## 2. SYS.4 System Integration Verification Summary
* **Artifact Path**: `docs/releases/<TAG>/system_integration_results.json`
* **Test Runner**: `python3 tools/run_system_integration_tests.py --headless`
* **Execution Timestamp**: `<TIMESTAMP>`
* **Git Commit**: `<GIT_COMMIT>`
* **Results Breakdown**:
  - `SYS-INT-001` (BLE HRM Ingestion & Backoff): [ PASS / FAIL ]
  - `SYS-INT-002` (BLE Cycling Power & Cadence): [ PASS / FAIL ]
  - `SYS-INT-003` (ANT+ Cadence & Profile Dispatch): [ PASS / FAIL ]
  - `SYS-INT-004` (GNSS Multipath & Barometric Fusion): [ PASS / FAIL ]
  - `SYS-INT-005` (Radar Alerting & Threat Assessment): [ PASS / FAIL ]
  - `SYS-INT-006` (FIT/TCX Storage Deduplication): [ PASS / FAIL ]
* **Overall SYS.4 Status**: [ PASS / FAIL ]

---

## 3. SYS.5 System Verification Protocol Summary
* **Artifact Path**: `docs/releases/<TAG>/sys_verification_record.md`
* **Governing Specification**: `docs/system/sys_verification_protocol.md`
* **Execution Date**: `YYYY-MM-DD`
* **Hardware Setup**: Primary Phone: `<MODEL>`, HRM: `<MODEL>`, Power Meter: `<MODEL>`, Radar: `<MODEL>`
* **Pass/Fail Criteria Verification**:
  - Sample loss rate: `<X.XX>%` (Threshold: $\le 1.0\%$) -> [ PASS / FAIL ]
  - RF reconnection latency: `<X.X> s` (Threshold: $\le 10.0\text{ s}$) -> [ PASS / FAIL ]
  - Battery drain rate: `<X.X>%/hr` (Threshold: $\le 8.0\%/\text{hr}$) -> [ PASS / FAIL ]
  - FIT/TCX schema & CRC integrity: [ VALID / INVALID ] -> [ PASS / FAIL ]
* **Overall SYS.5 Status**: [ PASS / FAIL ]

---

## 4. Release Decision & Formal Sign-Off
Release candidate tag creation and Play Store distribution are approved only when all 4 gates pass without deviations.

* **Release Manager**: ___________________________ Date: ____________
* **System Verification Lead**: ___________________ Date: ____________
* **Quality Assurance Gatekeeper**: _______________ Date: ____________
