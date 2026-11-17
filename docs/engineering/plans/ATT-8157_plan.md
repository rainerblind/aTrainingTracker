# Stage 3 Implementation Plan - ATT-8157: Simulator Self-Test Scoping & Release Gate Honesty

**Ticket**: [ATT-8157](https://rainerblind.atlassian.net/browse/ATT-8157) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Release**: `V5.0.0` | **Sprint**: `2026-11-17_01_Früh` | **SP**: 2 SP
**Mapping**: `REQ-PRO-112` (`REQ-PRO-112.1..3`), `REQ-PRO-068` (`REQ-PRO-068.1..4`), `REQ-PRO-072` (`REQ-PRO-072.1..4`), `REQ-PRO-074` (`REQ-PRO-074.4`) | `TST-PRO-112`, `TST-PRO-068`, `TST-PRO-072`, `TST-PRO-074`
**Branch**: `feature/ATT-8157` | **Author**: AI Agent 1 | **Date**: 2026-11-17

---

## 1. Architectural Boundary & Scoping (SWE.2 / `ATT-5431`)
Decouples developer Python sensor simulators from Android app/release gating:
- **Developer Simulator Self-Tests**: `tools/run_simulator_self_tests.py` (`SimulatorSelfTestRunner`) + `tools/test_simulator_self_tests.py` validate `tools/ble_hr_simulator.py`, `tools/ble_power_simulator.py`, `tools/ble_radar_simulator.py`, `tools/fake_gps.py`, `tools/replay_workout.py` contracts only (zero Android APK/emulator/constant-arithmetic claims).
- **Automated App Integration**: Covered by Robolectric Level 1 `INT-SWE-01..08` (`app/src/test/java/com/atrainingtracker/trainingtracker/integration/`) + CI `AppSmokeAndroidTest.kt`.
- **Release Gate (`SYS.5`)**: `tools/generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, and `.github/workflows/android-release.yml` gate releases on unit/`INT-SWE-01..08`, coverage, `sys_verification_record.md` (`SYS.5` hardware-in-the-loop), and `verification_summary.md` — never `system_integration_results.json`.

---

## 2. Atomic Implementation Steps

* **Step 1 (`REQ-PRO-112.1`, `REQ-PRO-068.2..3` — Simulator Self-Test Rename & Purity)**:
  - Rename `tools/run_system_integration_tests.py` -> `tools/run_simulator_self_tests.py` (`SimulatorSelfTestRunner`, keeping `--output`) and `tools/test_system_integration_tests.py` -> `tools/test_simulator_self_tests.py`.
  - Exercise real simulator builders/parsers (`ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py`, `fake_gps.py`); remove `MockService()`, hardcoded `backoffs` loop, `(10 - 65530) & 0xFFFF`, `oauth_response` / `int("15")`, and `SYS.4` Android claims.
* **Step 2 (`REQ-PRO-112.2`, `REQ-PRO-074.4` — Release Summary Generator Honesty)**:
  - Update `collect_sys4_summary()` and markdown summary rendering in `tools/generate_release_verification_summary.py`: missing `system_integration_results.json` yields **0 blocking deviations**; optional legacy `system_integration_results.json` renders strictly as non-blocking legacy/simulator telemetry.
  - Update `tools/test_generate_release_verification_summary.py` (`test_collect_sys4_summary_pass`, `test_collect_sys4_summary_failing`, `test_collect_sys4_summary_missing`) to verify non-blocking behavior and `SYS.5` gating.
* **Step 3 (`REQ-PRO-112.2..3`, `REQ-PRO-072.1..3` — Skill, Architecture & Release Docs)**:
  - Update `.agents/skills/release-manager/SKILL.md` (remove blocking `system_integration_results.json` gate and broken `--output-json` flag), `.github/workflows/android-release.yml` (step label honest to `SYS.5`), `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, `docs/architecture.md` (§6.2, §6.4), and `docs/system/sys_integration_tests.md` (explicit `INT-SWE-01..08` / `AppSmokeAndroidTest.kt` / manual `SYS.5` boundaries + `tools/run_simulator_self_tests.py`).
  - Annotate `docs/releases/V5.0.0/verification_summary.md` and `docs/releases/V5.0.0/system_integration_results.json` as non-gating legacy simulator self-tests (zero fabricated logs).
* **Step 4 (`TST-PRO-112.1..2`, `TST-PRO-068`, `TST-PRO-072` — JUnit Contract Guardrails)**:
  - Update `SystemIntegrationTestContractTest.kt` and `ReleaseManagerSystemEvidenceContractTest.kt` to enforce `tools/run_simulator_self_tests.py`, `tools/test_simulator_self_tests.py`, gap disclosures, `SYS.5` gating, and absence of blocking `system_integration_results.json` / `--output-json` mandates.
* **Step 5 (`TST-PRO-112.3` — Living Docs & Governance Sync)**:
  - In Stage 5 after passing tests, promote `REQ-PRO-068`, `REQ-PRO-072`, `REQ-PRO-074`, `REQ-PRO-112`, `TST-PRO-112` to `[Verified]` in `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `docs/requirements.md`, and `docs/tests.md`.

---

## 3. Invariant Protection & Code Lock (`Rule 4`)
* **Code Lock**: Zero edits to `tools/`, `.agents/`, `.github/`, `app/src/test/`, `docs/architecture.md`, `docs/system/`, or `docs/releases/` in Stage 3 (plan file only).
* **Production & Submodule Safety**: Zero edits to `app/src/main/`; `ANT-Android-SDKs` stays untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **UI Consistency**: N/A (`0` UI/Compose/strings changes).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Updates `REQ-PRO-068`, `REQ-PRO-072`, `REQ-PRO-074.4` and introduces `REQ-PRO-112` targeting `tools/run_system_integration_tests.py` (`tools/run_simulator_self_tests.py`), `tools/generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/architecture.md`, `docs/system/sys_integration_tests.md`, `docs/releases/`, and build contract tests.
* **Historical Origin & Commit Trace**: `REQ-PRO-068` (`ATT-6166`), `REQ-PRO-072` (`ATT-6169`), `REQ-PRO-074` (`ATT-6138`) originated in Sprint `2026-46.4`; rescoped under `ATT-8157` (`REQ-PRO-112`) after `ATT-8155`/`ATT-8156`.
* **Root Reason for Existing Formulation**: Prior rules gated releases on pure-Python simulator checks (`SystemIntegrationRunner`) with zero Android execution and a nonexistent `--output-json` flag in `SKILL.md`.
* **Preservation of Core Invariants**: Retains Python simulator self-tests, `SYS-IF-01..05` traceability, `SYS.5` (`sys_verification_record.md`) + `verification_summary.md` release gates, and untouched `app/src/main/` & `ANT-Android-SDKs`.

---

## 4. Verification Commands (Stage 4 / Stage 5)
```bash
python3 -m unittest tools/test_simulator_self_tests.py tools/test_generate_release_verification_summary.py
python3 tools/run_simulator_self_tests.py --output /tmp/sim_self_test.json
python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.build.SystemIntegrationTestContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ReleaseManagerSystemEvidenceContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --max-workers=2
python3 tools/verify_requirement_governance.py --check-specs
python3 tools/verify_requirement_governance.py --text-file docs/engineering/plans/ATT-8157_plan.md
```
