# Stage 3: Implementation Plan - ATT-8201: Specify Missing SYSREQ Capabilities & Synchronize SYS.3 System Architecture

**Ticket**: [ATT-8201](https://rainerblind.atlassian.net/browse/ATT-8201) (`3 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8201` (base `2499dcddb5847052105850065dbb94681e20c736`)  
**Requirement Mapping**: `REQ-PRO-131` (`REQ-PRO-131.1`–`REQ-PRO-131.5`, `[Planned]`) | **Test Mapping**: `TST-PRO-131` (`TST-PRO-131.1`–`TST-PRO-131.5`, `[Planned]`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
`docs/system/` currently defines only `12` `SYSREQ-*` IDs (`SYSREQ-TRK-001..006`, `SYSREQ-DEX-001..006`), omits `9` implemented hardware/RF/navigation/biometric/recovery/cloud capabilities, leaves `docs/system/system_architecture.md` (`v1.1.0`) out of sync with `system_context.md` and `TrackerService.java:2475-2488`, cites a wrong Varia BLE UUID in `sys_verification_protocol.md:66`, conflicts on battery ceilings (`<= 8.0%/h` vs `< 5.0%/h` in `docs/architecture.md:311` / `REQ-NFR-006`), retains `13` obsolete IDs in `GRANDFATHERED_SYSREQS`, and lacks a `SYS.2/SYS.3` impact gate in Stage 1/2 skills.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-131` (refining `REQ-PRO-053..056`, `REQ-PRO-069`, `REQ-PRO-076`, `REQ-NFR-006`)
* **Test Mapping**: `TST-PRO-131` (`TST-PRO-131.1`–`TST-PRO-131.5`)

## 3. System Invariants & Preserved Behavior
1. **Zero `app/src/main/` & `ANT-Android-SDKs` Edits**: Pure `docs/`, `.agents/skills/`, `tools/`, and `app/src/test/` contract changes (`Localization Parity: N/A - no UI strings`).
2. **Bidirectional `SYS.2 <-> SYS.3` & `100%` `SYSREQ->REQ` Parity**: `declaredSysreqs == allocatedSysreqs == 21`; every `SYSREQ-*` covered by `SYSTEM_REFINED_PREFIXES` `REQ-*`s (`0` dangling IDs).
3. **Shard Cap & Header Governance (`REQ-PRO-120`, `<= 4,000` lines)**: Edit `- **System Requirement(s)**:` lines in-place in `req_data_migration_export_part1.md` (`3,991` lines) & `req_tracking_cockpit_part1.md` (`3,924` lines) without net line growth; bump `Version` & keep `Status: Reviewed` (`Pending PO Sign-Off`).
4. **`REQ-NFR-006` Honesty & Zero New `src/main` Source Readers**: Keep `REQ-NFR-006` `On-Device Manual` `[Implemented]` (`NonFunctionalRequirementsContractTest`); `0` new `src/main` file reads (`SourceTextReadingAuditContractTest`).
5. **Skill & Tool Parity**: Preserve `"Documentation Code-Grounding"` in `stage1-analysis/SKILL.md` (`AspiceSweAuditContractTest.kt:223`) and `HoechstmassCoreFrameworkContractTest` parity.

## 4. Proposed Architectural Changes
* **SYS.2 (`docs/system/sysreq_*.md`, `docs/system_requirements.md`, `system_software_traceability.md`)**: Add `SYSREQ-TRK-007..013` and `SYSREQ-DEX-007..008` (`21` total), bump versions (`Status: Reviewed`), update index & traceability matrices, and re-link corresponding `REQ-*` entries in `req_tracking_cockpit_part1/2.md`, `req_maps_navigation.md`, and `req_data_migration_export_part1/2.md`.
* **SYS.3 & Context (`docs/system/system_context.md`, `docs/system/system_architecture.md` -> `v1.2.0`)**: Add Smartphone IMU (`TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE`, `TYPE_LINEAR_ACCELERATION`, `TYPE_GRAVITY`, `TYPE_ROTATION_VECTOR`, `TYPE_GAME_ROTATION_VECTOR`), Polar PFTP/PMD (`0xFEEE`/`fb005c80`), ANT+ `0x28`/`0x23`, BLE `0x180A`/`6a4e3202`/`6a4e3203`/`6a4e2f00`, all `6` missing context elements (`Barometer`/`Proximity`, `Google Maps SDK v2`, `Open-Meteo`, `Firebase`, `Play Auth/Review`, `PebbleKit`), bidirectional `SYS-IF-01`/`02` (`<-->`), §4.2 synchronous 30s Polar PFTP backfill on `mDbExecutor`, and `21`-row §5 allocation.
* **SYS.5 & Battery Reconciliation (`docs/system/sys_verification_protocol.md`, `docs/architecture.md:311`, `REQ-NFR-006`)**: Fix `:66` UUID to `6a4e3200-667b-11e3-949a-0800200c9a66`; reconcile power targets explicitly (`< 5.0%/h` screen-off background per `REQ-NFR-006` / `architecture.md:311`, `<= 8.0%/h` mixed `E5` in `sys_verification_protocol.md:99,195,266`, `<= 12.0%/h` screen-on cockpit).
* **Governance, Skills & Contract Tests**: Remove `GRANDFATHERED_SYSREQS` (`tools/verify_requirement_governance.py:4159-4173`, `L4471,5042,5070`) + add offline negative test in `tools/test_verify_requirement_governance.py`; add `SYS.2/SYS.3` impact gate to `.agents/skills/stage1-analysis/SKILL.md` & `.agents/skills/stage2-req-test-spec/SKILL.md`; update `System*ContractTest.kt`.
* **UI Consistency**: `No UI changes.`

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`SYS.2` Specifications & `REQ-*` Links)**: Author `SYSREQ-TRK-007..013` (`sysreq_live_tracking.md`) and `SYSREQ-DEX-007..008` (`sysreq_data_exchange.md`), update `docs/system_requirements.md`, `docs/system/system_software_traceability.md`, and in-place `- **System Requirement(s)**:` links across `req_tracking_cockpit_part{1,2}.md`, `req_maps_navigation.md`, `req_data_migration_export_part{1,2}.md` (`TST-PRO-131.1`).
2. **Step 2 (`SYS.3` & Context Sync)**: Update `docs/system/system_context.md` (IMU, Polar PFTP/PMD, ANT+ `0x28`/`0x23`) and `docs/system/system_architecture.md` (`v1.2.0`, bidirectional `SYS-IF-01/02`, §4.2 Polar backfill, 6 context elements + IMU, `21` allocations) (`TST-PRO-131.2`).
3. **Step 3 (`SYS.5` UUID & Battery Reconciliation)**: Fix Varia UUID in `sys_verification_protocol.md:66` (`6a4e3200-667b-11e3-949a-0800200c9a66`), align battery mode tiers across `sys_verification_protocol.md:99,195,266`, `docs/architecture.md:311`, and `REQ-NFR-006` (`req_process_stability_part1.md`, keeping `On-Device Manual` `[Implemented]`) (`TST-PRO-131.3`).
4. **Step 4 (`GRANDFATHERED_SYSREQS` Purge & Skills Gate)**: Remove `GRANDFATHERED_SYSREQS` (`verify_requirement_governance.py`) + add offline phantom-rejection test (`test_verify_requirement_governance.py`); add `SYS.2/SYS.3` impact gate to `.agents/skills/stage1-analysis/SKILL.md` & `stage2-req-test-spec/SKILL.md` (preserving `"Documentation Code-Grounding"`) (`TST-PRO-131.4`, `TST-PRO-131.5`).
5. **Step 5 (Contract Tests & Living-Doc Promotion)**: Update `SystemContextContractTest.kt` (allow authentic IMU `Sensor.TYPE_*`), `SystemArchitectureDesignContractTest.kt` (`21` `SYSREQ-*`), `SystemLiveTrackingRequirementsContractTest.kt`, `SystemDataExchangeRequirementsContractTest.kt`, `SystemRequirementsSpecificationContractTest.kt`, `SystemVerificationProtocolContractTest.kt`; promote `REQ-PRO-131` / `TST-PRO-131` in `_part2.md` & sync indexes.

## 6. Verification & Rollback Plan
* **Batched Gradle Command (Stage 4/5 under `Rule 7.5`)**:
  `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemDataExchangeRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemRequirementsSpecificationContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemVerificationProtocolContractTest" --tests "com.atrainingtracker.trainingtracker.build.NonFunctionalRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --max-workers=1 --rerun-tasks`
* **Python & Governance Commands**:
  `python3 -m unittest tools/test_verify_requirement_governance.py` | `python3 -m unittest tools/test_jira_accounts.py` | `python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py` | `python3 tools/verify_requirement_governance.py --repo-root .` | `python3 tools/shard_living_docs.py --check-size --strict`
* **Rollback**: `feature/ATT-8201` branch isolation allows clean revert without touching `sprint/2026-11-18_01_Früh` or `develop`.
