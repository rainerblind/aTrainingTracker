# Release Evidence Repository & Gating Governance (ASPICE SUP.8, SWE.5, SWE.6, SYS.5)

## Overview
This directory governs the release evidence dossiers for all production and release-candidate builds of aTrainingTracker.
In compliance with **ASPICE CL2 SUP.8** (Configuration & Release Management), **SWE.5/SWE.6** (Software Integration & Qualification), and **SYS.5** (System Verification), release decisions and tag creation are strictly gated on verified software/integration tests (`INT-SWE-01..08`, `AppSmokeAndroidTest`) and hardware-in-the-loop system verification records.

Each release candidate tag (`<tag>`, e.g., `V5.0.0` or `V4.9.38__262`) MUST have a corresponding dossier directory:
```
docs/releases/<tag>/
├── sys_verification_record.md        # SYS.5: On-device hardware-in-the-loop verification sign-off record
├── verification_summary.md           # SWE.6 BP5 / REQ-PRO-074: Release verification summary per tag
└── release_dossier.md                 # SUP.8: Release qualification summary and audit sign-off (optional)
```

*(Note per `REQ-PRO-112` / `ATT-8157`: `system_integration_results.json` from Python sensor simulators is **retired as a blocking release gate**. Developer simulator self-tests are run via `python3 tools/run_simulator_self_tests.py --output <path>` and never represent Android app execution.)*

---

## Required Release Evidence Artifacts

### 1. Automated Software Integration & Simulator Tooling Scope (`REQ-PRO-112`)
* **App Integration Gate**: Executed via `./gradlew :app:testDebugUnitTest` (`INT-SWE-01..08` in `app/src/test/.../integration/`) and CI `AppSmokeAndroidTest.kt`.
* **Developer Simulator Self-Tests (Non-Gating)**:
  ```bash
  python3 tools/run_simulator_self_tests.py --output build/reports/simulator_self_tests.json
  ```
  Verifies Python simulator modules (`ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py`, `fake_gps.py`, `replay_workout.py`) only; **not** a release-gating artifact.

### 2. SYS.5 System Verification Protocol Sign-Off Record
* **File**: `docs/releases/<tag>/sys_verification_record.md`
* **Governing Specification**: `docs/system/sys_verification_protocol.md` (`REQ-PRO-069`, `TST-PRO-069`)
* **Generation**: Copy template from `docs/system/sys_verification_protocol.md` Section 6 to `docs/releases/<tag>/sys_verification_record.md`.
* **Required Sign-Off Criteria**:
  - `Overall Verdict`: `PASS` (or `FREIGABE`)
  - All 8 formal test cases (`SYS-VRF-001` through `SYS-VRF-008`) executed on physical hardware and marked `PASS`
  - 100% bidirectional coverage of `SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`
  - Hardware & Environmental logs populated with real device and sensor serial numbers
  - Data sample loss $\le 1.0\%$
  - RF reconnection latency $\le 10.0\text{ s}$
  - Battery drain rate $\le 8.0\%\text{ per hour}$
  - FIT/TCX export schema validity 100% valid with zero CRC errors
  - Formal signatures, Human PO Physical Field Verification Attestation (`REQ-PRO-098`), and dates from System Verification Engineer and Quality Gatekeeper.

### 3. SWE.6 BP5 Release Verification Summary per Tag
* **File**: `docs/releases/<tag>/verification_summary.md`
* **Governing Specification**: `REQ-PRO-074` / `TST-PRO-074` (Finding A-11, updated per `ATT-8157`)
* **Generation Command**:
  ```bash
  python3 tools/generate_release_verification_summary.py --tag <tag>
  ```
* **Required Pass/Fail Criteria**:
  - `Overall Verification Status`: `PASS (READY FOR RELEASE)`
  - Zero open deviations listed under `Deviations Requiring Human Approval`
  - Complete aggregation of unit/integration test counts/archive, bidirectional traceability matrix, and SYS.5 on-device protocol sign-off.

---

## Blocking Enforcement Invariant

> [!CAUTION]
> **MANDATORY RELEASE GATE (REQ-PRO-072 / REQ-PRO-112 / REQ-STP-008)**:
> In `.agents/skills/release-manager/SKILL.md`, creating a release candidate tag (`V<versionName>__<versionCode>`), finishing a GitFlow release, or uploading artifacts to the Google Play Store is **STRICTLY BLOCKED** if:
> 1. `docs/releases/<tag>/sys_verification_record.md` is missing, incomplete, or lacks formal QA + PO sign-off.
> 2. `docs/releases/<tag>/verification_summary.md` is missing or reports unresolved deviations / failing unit-integration tests.
>
> Any release attempted without these verified records will be rejected by QA and flagged as an ASPICE SUP.8 compliance violation.

---

## Historical Pre-ASPICE Release Backfill & Legacy V5.0.0 Governance (`REQ-PRO-112`)

Prior to Sprint 2026-44 and the formal adoption of ASPICE Level 2 process governance (SYS.5, SUP.8), releases preceding `V5.0.0` (versionCode 273) were qualified under legacy regression testing protocols without maintaining structured Markdown dossiers in `docs/releases/<tag>/`.

In compliance with ASPICE audit standards and non-retroactive data integrity:
1. Historical pre-ASPICE release builds are permanently cataloged in git tag history and Jira release milestones.
2. Physical hardware logs are never retroactively fabricated (`REQ-PRO-098`).
3. Formal release dossiers containing `sys_verification_record.md` and `verification_summary.md` are mandatory beginning with production release `V5.0.0` (code `273`) and all subsequent release candidates. The existing `docs/releases/V5.0.0/system_integration_results.json` file is preserved strictly as a **non-gating legacy simulator self-test artifact** (not Android app integration).
