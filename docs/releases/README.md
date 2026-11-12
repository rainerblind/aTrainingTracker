# Release Evidence Repository & Gating Governance (ASPICE SUP.8, SYS.4, SYS.5)

## Overview
This directory governs the release evidence dossiers for all production and release-candidate builds of aTrainingTracker.
In compliance with **ASPICE CL2 SUP.8** (Configuration & Release Management), **SYS.4** (System Integration & Integration Verification), and **SYS.5** (System Verification), release decisions and tag creation are strictly gated on verified system-level evidence.

Each release candidate tag (`<tag>`, e.g., `V5.0.0` or `V4.9.38__262`) MUST have a corresponding dossier directory:
```
docs/releases/<tag>/
├── system_integration_results.json   # SYS.4: Automated system integration verification results
├── sys_verification_record.md        # SYS.5: On-device system verification protocol sign-off record
└── release_dossier.md                 # SUP.8: Release qualification summary and audit sign-off
```

---

## Required Release Evidence Artifacts

### 1. SYS.4 System Integration Verification Evidence
* **File**: `docs/releases/<tag>/system_integration_results.json`
* **Governing Specification**: `docs/system/sys_integration_tests.md` (`REQ-PRO-068`, `TST-PRO-068`)
* **Generation Command**:
  ```bash
  python3 tools/run_system_integration_tests.py --headless --output-json docs/releases/<tag>/system_integration_results.json
  ```
* **Required Pass/Fail Criteria**:
  - `status`: `"PASS"`
  - `exit_code`: `0`
  - `total_cases`: `6`
  - `passed`: `6`
  - `failed`: `0`
  - All interfaces (`SYS-IF-01` through `SYS-IF-05`) verified across BLE HRM (`SYS-INT-001`), BLE Power (`SYS-INT-002`), ANT+ Cadence (`SYS-INT-003`), Barometer/GNSS (`SYS-INT-004`), Radar Alerting (`SYS-INT-005`), and FIT/TCX Deduplication (`SYS-INT-006`).

### 2. SYS.5 System Verification Protocol Sign-Off Record
* **File**: `docs/releases/<tag>/sys_verification_record.md`
* **Governing Specification**: `docs/system/sys_verification_protocol.md` (`REQ-PRO-069`, `TST-PRO-069`)
* **Generation**: Copy template from `docs/system/sys_verification_protocol.md` Section 6 to `docs/releases/<tag>/sys_verification_record.md`.
* **Required Sign-Off Criteria**:
  - `Overall Verdict`: `PASS` (or `FREIGABE`)
  - All 8 formal test cases (`SYS-VRF-001` through `SYS-VRF-008`) executed and marked `PASS`
  - 100% bidirectional coverage of `SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`
  - Hardware & Environmental logs populated with real device and sensor serial numbers
  - Data sample loss $\le 1.0\%$
  - RF reconnection latency $\le 10.0\text{ s}$
  - Battery drain rate $\le 8.0\%\text{ per hour}$
  - FIT/TCX export schema validity 100% valid with zero CRC errors
  - Formal signatures and dates from System Verification Engineer and Process Owner / Quality Gatekeeper.

---

## Blocking Enforcement Invariant

> [!CAUTION]
> **MANDATORY RELEASE GATE (REQ-PRO-072 / REQ-STP-008)**:
> In `.agents/skills/release-manager/SKILL.md`, creating a release candidate tag (`V<versionName>__<versionCode>`), finishing a GitFlow release, or uploading artifacts to the Google Play Store is **STRICTLY BLOCKED** if:
> 1. `docs/releases/<tag>/system_integration_results.json` is missing or reports any test failure.
> 2. `docs/releases/<tag>/sys_verification_record.md` is missing, incomplete, or lacks formal sign-off.
>
> Any release attempted without these verified records will be rejected by QA and flagged as an ASPICE SUP.8 compliance violation.
