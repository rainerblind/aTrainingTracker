# Stage 5 Walkthrough - ATT-8157: Simulator Self-Test Scoping & Release Gate Honesty

**Ticket**: [ATT-8157](https://rainerblind.atlassian.net/browse/ATT-8157) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*Finding M6*)  
**Branch**: `feature/ATT-8157` | **Stage 4 Baseline**: `5226b6afa528c9030edfa494fe2790682e67ab7a`  
**Requirements Promoted `[Verified]`**: `REQ-PRO-112` (`REQ-PRO-112.1..3`), `REQ-PRO-068`, `REQ-PRO-072`, `REQ-PRO-074`  
**Tests Promoted `[Verified]`**: `TST-PRO-112`, `TST-PRO-068`, `TST-PRO-072`, `TST-PRO-074`  

---

## 1. Delivered Changes (`app/src/main/` & `ANT-Android-SDKs` Untouched)
- **Simulator Self-Test Rename & Purity (`REQ-PRO-112.1`, `REQ-PRO-068`)**: Renamed `tools/run_system_integration_tests.py` -> `tools/run_simulator_self_tests.py` (`SimulatorSelfTestRunner`, `--output`, `executes_android_app=false`, `release_gate=false`, executing `SIM-HR-001`, `SIM-PWR-001`, `SIM-RDR-001`, `SIM-GPS-001`, `SIM-RPL-001` against `ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py`, `fake_gps.py`, `replay_workout.py`) and `tools/test_system_integration_tests.py` -> `tools/test_simulator_self_tests.py` (`TestSimulatorSelfTestRunner`). Removed misleading constant-arithmetic stubs and fake SYS.4 release-gate claims.
- **Release Gate Honesty (`REQ-PRO-112.2`, `REQ-PRO-072`, `REQ-PRO-074.4`)**: Updated `collect_sys4_summary()` in `tools/generate_release_verification_summary.py` and `tools/test_generate_release_verification_summary.py` so missing `system_integration_results.json` yields **0 blocking deviations** and optional legacy files render strictly as `[ INFO / SIMULATOR-ONLY ]`. Updated `.agents/skills/release-manager/SKILL.md` (removed blocking `system_integration_results.json` gate and broken `--output-json` CLI flag), `.github/workflows/android-release.yml`, `docs/releases/README.md`, and `docs/releases/TEMPLATE.md`.
- **Gap Disclosure, V5.0.0 Reclassification & Contract Tests (`REQ-PRO-112.3`)**: Updated `docs/architecture.md` (§6.2, §6.4), `docs/system/sys_integration_tests.md`, `docs/releases/V5.0.0/verification_summary.md`, and `docs/releases/V5.0.0/system_integration_results.json` to classify Python simulators as developer-only, automated Android integration under `INT-SWE-01..08` + CI `AppSmokeAndroidTest.kt`, and physical-sensor checks as hardware-in-the-loop `SYS.5`. Updated `SystemIntegrationTestContractTest.kt` (`6/6`) and `ReleaseManagerSystemEvidenceContractTest.kt` (`4/4`).

## 2. Verification Results (Observed on `feature/ATT-8157`)
- **Python `unittest` (`109/109 PASSED`, `OK`, `51.031s`)**:
  - `python3 -m unittest tools/test_simulator_self_tests.py tools/test_generate_release_verification_summary.py tools/test_verify_requirement_governance.py` (`109/109 PASSED`; includes `5/5` `SimulatorSelfTestRunner` cases).
- **Gradle Serialized Contract & AndroidTest Compile (`21/21 PASSED`, `BUILD SUCCESSFUL`)**:
  - `SystemIntegrationTestContractTest` (`6/6`), `ReleaseManagerSystemEvidenceContractTest` (`4/4`), `AspiceSweAuditContractTest` (`11/11`) + `:app:compileDebugAndroidTestKotlin` PASS.
- **Governance & Living Documentation**:
  - `python3 tools/verify_requirement_governance.py --check-specs` PASS (exit `0`).
  - `python3 tools/verify_requirement_governance.py --sync-indexes`: `docs/requirements.md` (`1206` total, `969` Verified) & `docs/tests.md` (`1213` total, `998` Verified), exit `0`.
- **Integrity Guardrails**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-068` (`TST-PRO-068`), `REQ-PRO-072` (`TST-PRO-072`), `REQ-PRO-074.4` (`TST-PRO-074.4`), and net-new `REQ-PRO-112` (`TST-PRO-112`) targeting `tools/run_simulator_self_tests.py`, `tools/test_simulator_self_tests.py`, `tools/generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `.github/workflows/android-release.yml`, `docs/architecture.md`, `docs/system/sys_integration_tests.md`, `docs/releases/`, `SystemIntegrationTestContractTest.kt`, and `ReleaseManagerSystemEvidenceContractTest.kt`.
* **Historical Origin & Commit Trace**: `REQ-PRO-068` (`ATT-6166`), `REQ-PRO-072` (`ATT-6169`), and `REQ-PRO-074` (`ATT-6138`) originated in Sprint `2026-46.4`; updated via `ATT-8157` (`REQ-PRO-112`) under Epic `ATT-8138` after `ATT-8155` & `ATT-8156`.
* **Root Reason for Existing Formulation**: Prior specs wired pure-Python simulator checks into a blocking `system_integration_results.json` gate without executing Android/ADB/emulator code while `SKILL.md` passed an invalid `--output-json` CLI flag.
* **Preservation of Core Invariants**: Retains Python simulator self-testing, external interface traceability (`SYS-IF-01..05`, `SYSREQ-*`), `SYS.5` hardware sign-off (`sys_verification_record.md`) & `verification_summary.md` blocking gates, zero `app/src/main/` changes, and `ANT-Android-SDKs` (`0e8cea71`).
