# Release Qualification Dossier: Release <TAG> (ASPICE SUP.8, SWE.5, SYS.5)

**Release Tag**: `<TAG>` (e.g. `V5.0.0__263` / `V5.0.0`)  
**Target Version Name**: `<VERSION_NAME>`  
**Target Version Code**: `<VERSION_CODE>`  
**Release Date**: `YYYY-MM-DD`  
**Release Manager**: `<NAME>`  
**Overall Release Verdict**: **[ PASS / REVISION / FAIL ]**  

---

## 1. Executive Summary & Verification Gating Checklist
In accordance with **REQ-PRO-072**, **REQ-PRO-112**, and `.agents/skills/release-manager/SKILL.md`, release qualification requires mandatory unit/software-integration evidence and physical SYS.5 verification evidence before release candidate tag creation and distribution.

| Gate | Category | Evidence Artifact | Required Threshold | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **Gate 1** | Unit & Software Integration (`INT-SWE-01..08`, `AppSmokeAndroidTest`) | Gradle unit/integration & CI smoke reports | 100% test pass rate, 0 failures | [ PASS / FAIL ] |
| **Gate 2** | Code Signing & Integrity | `apksigner verify` & AAB signatures | v2 scheme = true, signer = aTrainingTracker | [ PASS / FAIL ] |
| **Gate 3** | Release Verification Summary (SWE.6) | `docs/releases/<TAG>/verification_summary.md` | `PASS (READY FOR RELEASE)`, 0 deviations | [ PASS / FAIL ] |
| **Gate 4** | System Verification Protocol (SYS.5) | `docs/releases/<TAG>/sys_verification_record.md` | 8/8 tests PASS, formal QA + PO sign-off | [ PASS / FAIL ] |

---

## 2. Software Integration & Simulator Tooling Notes (Non-Gating Simulators)
* **Automated Android Integration**: Verified via `./gradlew :app:testDebugUnitTest` (`INT-SWE-01..08`) and connected CI `AppSmokeAndroidTest.kt`.
* **Developer Simulator Self-Tests (Optional / Non-Gating)**: `python3 tools/run_simulator_self_tests.py` tests Python simulator tools only and does **not** certify Android APK execution (`REQ-PRO-112`).

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

## 4. Human PO Physical Field Verification Attestation (REQ-PRO-098)
In accordance with ASPICE 4.0 Finding K1 and REQ-PRO-098, physical field verification records SHALL derive exclusively from authentic workouts recorded on the reference Google Pixel 10. Autonomous AI agents are strictly forbidden from signing physical execution or synthesizing hardware data.

* **Attesting Product Owner**: ___________________________ Date: ____________
* **Reference Hardware**: Google Pixel 10 (Serial: `<PIXEL_10_SERIAL>`)
* **Sprint / Build Commit SHA**: `<GIT_COMMIT_SHA>`
* **Attested Workout / FIT Session IDs**: `<WORKOUT_IDS>`
* **PO Attestation Verdict**: [ PASS / REVISION / FAIL ]

---

## 5. Release Decision & Formal Sign-Off
Release candidate tag creation and Play Store distribution are approved only when all gates pass and the Product Owner has formally attested to physical field verification.

* **Release Manager**: ___________________________ Date: ____________
* **System Verification Lead**: ___________________ Date: ____________
* **Quality Assurance Gatekeeper**: _______________ Date: ____________
