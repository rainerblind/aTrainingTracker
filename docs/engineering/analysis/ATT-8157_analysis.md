# ATT-8157 Stage 1 Analysis — System Integration Test Honesty & Release Gate Scope

## 1. Problem Domain & Root Cause (`Rule 8.7` Verified)
- **Root Cause (`M6` Evidence Integrity)**: `tools/run_system_integration_tests.py` (`SystemIntegrationRunner`, `SYS-INT-001`..`006`) executes only pure-Python simulator/constant-arithmetic checks with **zero Android APK, ADB, or emulator execution**:
  - `SYS-INT-001` (L143-153) verifies a local `backoffs` loop equals `[1000, 2000, 4000, 8000, 16000]`.
  - `SYS-INT-002` (L168-209) exercises a local `MockService()`; `SYS-INT-003` (L211-274) calls `ble_radar_simulator`; `SYS-INT-004` (L276-304) asserts hardcoded `device_types` and `(10 - 65530) & 0xFFFF == 16`.
  - `SYS-INT-005` (L306-352) checks 5 hardcoded Munich tuples; `SYS-INT-006` (L354-394) checks a hardcoded `oauth_response` dict and `int("15")`.
- **Gate Misrepresentation**: `.agents/skills/release-manager/SKILL.md`, `tools/generate_release_verification_summary.py` (`collect_sys4_summary()`), `docs/architecture.md` (§6.2, §6.4), `docs/system/sys_integration_tests.md`, and `docs/releases/V5.0.0/verification_summary.md` + `system_integration_results.json` treat this script as a mandatory blocking SYS.4 release gate (`REQ-PRO-072`), creating false confidence that end-to-end app/sensor integration ran.

## 2. Option Tradeoff & Architecture Decision (ADR)
| Option | Scope & Feasibility (2 SP) | Verdict |
|---|---|---|
| **A: Wire `run_system_integration_tests.py` to Real App** | Drive APK on emulator via `tools/replay_workout.py` + `tools/ble_*_simulator.py` + `tools/fake_gps.py` and assert FIT/TCX/SQLite outputs. Standard headless emulators (`.github/workflows/android-branch-build.yml` `api-level: 34`) lack BlueZ virtual BLE GATT loopback & ANT+ radio IPC (`com.dsi.ant.plugins.antplus`); fragile, >2 SP. | **Reject** |
| **B: Remove Fake SYS.4 Release Gate & Rescope (Recommended)** | Rename/rescope `tools/run_system_integration_tests.py` -> `tools/run_simulator_self_tests.py` (and `test_system_integration_tests.py` -> `test_simulator_self_tests.py`); remove `system_integration_results.json` as a blocking release gate; keep real app integration under `INT-SWE-01..08` (`ATT-8155`/`ATT-8156`) + CI `AppSmokeAndroidTest.kt` + hardware-in-the-loop `SYS.5`. | **Adopt** |

- **Documented Gap (Option B)**: Automated Python simulators (`ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py`, `fake_gps.py`, `replay_workout.py`) are developer tools only. Physical-sensor system verification remains manual hardware-in-the-loop under `SYS.5` (`docs/releases/*/verification_summary.md`), while software-layer boundaries (`INT-SWE-01..08`) and Android smoke (`AppSmokeAndroidTest.kt`) provide automated coverage.

## 3. Independent Peer Review & Verification
- **CLI Flag Mismatch Confirmed**: `.agents/skills/release-manager/SKILL.md` (L197) invokes `tools/run_system_integration_tests.py --headless --output-json ...`, whereas the script only defines `--output` (meaning the documented release command could not even execute as written).
- **Historical Artifact Check**: `docs/releases/V5.0.0/system_integration_results.json` is already committed and referenced by `docs/releases/V5.0.0/verification_summary.md`; Option B must annotate/reclassify V5.0.0 or document legacy scope cleanly without falsifying history.
- **Side-Effect & Cross-Caller Impact Analysis (`Rule 68`)**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemIntegrationTestContractTest.kt` (`REQ-PRO-068` / `TST-PRO-068`) asserts existence of `docs/system/sys_integration_tests.md`, `SYS-INT-001..006`, `tools/run_system_integration_tests.py`, and `system_integration_results.json`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseManagerSystemEvidenceContractTest.kt` (`REQ-PRO-072` / `TST-PRO-072`) asserts `SKILL.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, and `docs/architecture.md` §6.4 mandate `run_system_integration_tests.py` + `system_integration_results.json`.
  - `tools/generate_release_verification_summary.py` (`collect_sys4_summary()`) + `tools/test_generate_release_verification_summary.py` enforce `system_integration_results.json` with `>= 6` cases; removing the gate requires synchronized updates here + `docs/architecture.md` §6.2/§6.4, `docs/system/sys_integration_tests.md`, `docs/releases/{README,TEMPLATE}.md`.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Modifies `REQ-PRO-068` (`TST-PRO-068`), `REQ-PRO-072` (`TST-PRO-072`), and `REQ-PRO-074.4` (`TST-PRO-074.4`) in `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`.
- **Historical Origin & Commit Trace**: `REQ-PRO-068` (`ATT-6166`) and `REQ-PRO-072` (`ATT-6169`) originated in Sprint `2026-46.4` seeking automated SYS.4 system integration tests and blocking release-tag evidence gates.
- **Root Reason for Existing Formulation**: The simulator suite never executed Android code, turning `REQ-PRO-068`/`REQ-PRO-072` into constant-checking gates (`M6`); rescoping/retiring them preserves honest release governance alongside `INT-SWE-01..08` + `AppSmokeAndroidTest.kt`.
- **Preservation of Core Invariants**: Prevents silent loss of tool tests (retained as `tools/run_simulator_self_tests.py`) and avoids unverified `SYS.5` claims by explicitly marking physical-sensor verification as hardware-in-the-loop.

## 5. Scope Bounding (2 SP) & Change Impact Analysis (`REQ-PRO-064`)
- **UI/UX Impact**: None (`0` UI changes).
- **Schema & Persistence Impact**: None (`0` SQLite/Room changes; removes `system_integration_results.json` release-gate artifact requirement).
- **Backward Compatibility Impact**: Removes `tools/run_system_integration_tests.py` blocking gate from `release-manager/SKILL.md` and `generate_release_verification_summary.py`; renames script to `tools/run_simulator_self_tests.py` and updates `V5.0.0` evidence note honestly.
- **Documentation & Governance Sync**: Stage 2–4 will update `REQ-PRO-068`/`REQ-PRO-072`, `TST-PRO-068`/`TST-PRO-072`, `SystemIntegrationTestContractTest.kt`, `ReleaseManagerSystemEvidenceContractTest.kt`, `docs/architecture.md`, `docs/system/sys_integration_tests.md`, `docs/releases/*`, `.agents/skills/release-manager/SKILL.md`, and `tools/generate_release_verification_summary.py`.
