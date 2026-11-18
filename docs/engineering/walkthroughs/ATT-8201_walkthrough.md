# Stage 5: Walkthrough & Verification - ATT-8201: Specify Missing SYSREQ Capabilities & Synchronize SYS.3

**Ticket**: [ATT-8201](https://rainerblind.atlassian.net/browse/ATT-8201) (`3 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh`  
**Requirement Mapping**: `REQ-PRO-131` (`REQ-PRO-131.1`–`REQ-PRO-131.5`, `[Verified]`) | **Test Mapping**: `TST-PRO-131` (`TST-PRO-131.1`–`TST-PRO-131.5`, `[Verified]`)  
**Branch**: `feature/ATT-8201` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Closed ASPICE 4.0 Gap #4 across `SYS.2`, `SYS.3`, `SYS.5`, governance tooling, Stage 1/2 skills, and Kotlin contract tests with **zero `app/src/main/` runtime edits**:
* **SYS.2 (`REQ-PRO-131.1`)**: Added `SYSREQ-TRK-007..013` (`docs/system/sysreq_live_tracking.md` `v1.2.1`) and `SYSREQ-DEX-007..008` (`docs/system/sysreq_data_exchange.md` `v1.2.1`), synced `docs/system_requirements.md` (`v1.1.1`, `21` `SYSREQ-*` IDs) & `docs/system/system_software_traceability.md` (`v1.2.1`, including exact `REQ-*` links across `SYSREQ-TRK-001..013`/`SYSREQ-DEX-001..008` and current sharded `req_*_part*.md` filenames), and linked refined `REQ-*` entries in-place across 6 shards (`req_tracking_cockpit_part1.md` bumped to `v1.0.21`).
* **SYS.3 & Context (`REQ-PRO-131.2`)**: Synchronized `docs/system/system_context.md` (`v1.3.1`) and `docs/system/system_architecture.md` (`v1.2.1`) with Smartphone IMU `Sensor.TYPE_*` channels + ambient `Sensor.TYPE_LIGHT`, Polar BLE SDK `8.6.0` PFTP/PMD distinguishing H10 `30_000L` (`30s`) vs. Verity Sense `45_000L` (`45s`) `BACKFILL_TIMEOUT_MS`, Varia RearVue `6a4e2f00`, ANT+ `0x28`/`0x23`, 6 missing context elements, bidirectional `SYS-IF-01/02`, §4.2 `endWorkout()` sequence (`markWorkoutFinishedBlocking()` `FINISHED=1` before Polar backfill, followed by HRV/ERTS recompute & `ExportManager.workoutFinished()`), and `21`-row §5 allocation (`declared == allocated`).
* **SYS.5, Governance & Skills (`REQ-PRO-131.3`, `REQ-PRO-131.4`, `REQ-PRO-131.5`)**: Fixed Varia BLE UUID (`6a4e3200-667b-11e3-949a-0800200c9a66`), updated `docs/system/sys_verification_protocol.md` (`v1.2.1`) consistently to `10` test cases (`SYS-VRF-001..010`), reconciled battery budgets across `SYS.5` / `docs/architecture.md` (`v1.0.4`) / `REQ-NFR-006` (`< 5.0%/h` screen-off, `<= 8.0%/h` mixed `E5`, `<= 12.0%/h` screen-on, preserving `REQ-NFR-006` `On-Device Manual` `[Implemented]`), removed `GRANDFATHERED_SYSREQS` (`tools/verify_requirement_governance.py` + `TestGrandfatheredSysreqsRemoved`), added `SYS.2/SYS.3` impact gate to Stage 1/2 `SKILL.md`s (+ Höchstmaß parity), and updated 6 `System*ContractTest.kt` suites (`0` new `src/main` reads).
* **Cold Stage 6 Bounce 1/3 Remediation**: Fixed Polar Verity Sense `45_000L` (`45s`) backfill timeout distinction, added `Sensor.TYPE_LIGHT` (`SmartRadarLightEngine.kt`) to SYS docs & `SystemContextContractTest.kt` (`allowedSensors`), eliminated `8` vs `10` / `SYS-VRF-001..008` internal contradictions in `sys_verification_protocol.md`, synced all `SYSREQ-TRK-001..006`/`SYSREQ-DEX-001..007` & §3.1 shard names in `system_software_traceability.md`, corrected §4.2 `endWorkout()` sequence (removing nonexistent `autoExport()`), and fixed `req_tracking_cockpit_part1.md` version (`1.0.21`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-131.1` | `TST-PRO-131.1` | `SystemLiveTrackingRequirementsContractTest` (`4`), `SystemDataExchangeRequirementsContractTest` (`4`), `SystemRequirementsSpecificationContractTest` (`5`) + `verify_requirement_governance.py` | **PASSED** (`13/13`, `21/21` SYSREQs `100%`) | `Verified` |
| `REQ-PRO-131.2` | `TST-PRO-131.2` | `SystemContextContractTest` (`6`), `SystemArchitectureDesignContractTest` (`9`) | **PASSED** (`15/15`, `declared == allocated == 21`) | `Verified` |
| `REQ-PRO-131.3` | `TST-PRO-131.3` | `SystemVerificationProtocolContractTest` (`7`), `NonFunctionalRequirementsContractTest` (`3`) | **PASSED** (`10/10`, `REQ-NFR-006` `[Implemented]`) | `Verified` |
| `REQ-PRO-131.3` / `.5` | `TST-PRO-131.4` | `test_verify_requirement_governance.py` (`TestGrandfatheredSysreqsRemoved`) & `verify_requirement_governance.py` | **PASSED** (`TST-PRO-131` `True` via `29` cases) | `Verified` |
| `REQ-PRO-131.4` / `.5` | `TST-PRO-131.5` | `AspiceSweAuditContractTest` (`13`), `HoechstmassCoreFrameworkContractTest` (`7`), `ArchitectureConformanceContractTest` (`11`), `SourceTextReadingAuditContractTest` (`4`), `DetailedDesignParityContractTest` (`3`), `InterfaceSignatureDriftContractTest` (`3`), `test_hoechstmass_tools.py` | **PASSED** (`41/41` Gradle + `37/37` Python) | `Verified` |

---

## 3. Automated Test Evidence

### Batched Gradle Contract Suites (`parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --max-workers=1 --rerun-tasks ...`)
* **Result**: `BUILD SUCCESSFUL in 12m 34s` (`38 actionable tasks: 38 executed`) — **`13` suites / `79` tests: `79` PASS, `0` FAIL, `0` ERR, `0` SKIP** (XML timestamps `2026-11-18T13:56:13.872Z`..`13:57:29.367Z`):
  * `ArchitectureConformanceContractTest`: `11/11` | `AspiceSweAuditContractTest`: `13/13` | `DetailedDesignParityContractTest`: `3/3` | `HoechstmassCoreFrameworkContractTest`: `7/7` | `InterfaceSignatureDriftContractTest`: `3/3` | `NonFunctionalRequirementsContractTest`: `3/3` | `SourceTextReadingAuditContractTest`: `4/4` | `SystemArchitectureDesignContractTest`: `9/9` | `SystemContextContractTest`: `6/6` | `SystemDataExchangeRequirementsContractTest`: `4/4` | `SystemLiveTrackingRequirementsContractTest`: `4/4` | `SystemRequirementsSpecificationContractTest`: `5/5` | `SystemVerificationProtocolContractTest`: `7/7`.

### Python Unit Tests & Governance Verification
* `python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py`: **`37/37` OK** (`2.322s`).
* `python3 -m unittest tools/test_verify_requirement_governance.py`: **`104/105` PASS** (`76.412s`; `TestGrandfatheredSysreqsRemoved` & all `SYSREQ` scenarios PASS; sole fail is pre-existing unrelated `test_scenario_64_tst_pro_101_2_3_4_live_repo_ratchet_zero` on `TST-TRK-087` at `docs/tests/tst_tracking_cockpit.md:3411`).
* `python3 tools/verify_requirement_governance.py --repo-root .`: **exit `0`** — `21/21 SYSREQs` `100%` covered (`369/385 [95.84%]` `SYSREQ`-linked, `16 Derived (SW-only)`, `385/385 [100.0%]` classified, `0` dangling); `61` governed headers PASS; `28/28` shards `<= 4,000` lines.

---

## 4. Hardware / Physical Verification (`Rule 7.6`)
Pure documentation, governance tooling, skill, and contract-test synchronization (`0` `app/src/main/` runtime bytecode edits; `Localization Parity: N/A - no UI strings`). No physical device installation performed (`Rule 7.6`).

---

## 5. Invariant & Governance Verification
1. **Zero Runtime Regressions**: `13/13` contract suites (`79/79` PASS) and `21/21` `SYSREQ` 1:1 parity (`declared == allocated`) verified.
2. **Living Docs Synchronized**: `REQ-PRO-131` & `TST-PRO-131` promoted to `[Verified]`; master indexes (`docs/requirements.md` `v1.0.38`, `docs/tests.md` `v1.0.38`) synced with `100%` parity (`Total=1249/1256`).
3. **Branch Hygiene (`REQ-PRO-125`)**: All changes remain committed cleanly on `feature/ATT-8201` pending Stage 6 Cold Final Review (`Final Review (Agent)`).
