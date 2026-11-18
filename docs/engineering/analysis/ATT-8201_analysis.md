# Stage 1 Analysis: ATT-8201 - Specify Missing SYSREQ Capabilities and Synchronize SYS.3 System Architecture

**Ticket**: [ATT-8201](https://rainerblind.atlassian.net/browse/ATT-8201)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_01_Früh`  
**Branch**: `feature/ATT-8201` (base `521d63795ac4eeb3f947e76a19d371207df444d8`) | **Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
Only `12` `SYSREQ-*` IDs (`SYSREQ-TRK-001..006`, `SYSREQ-DEX-001..006`) exist across `docs/system/sysreq_*.md` (`1,247` total `REQ-*`s; `368/384` `SYSTEM_REFINED_PREFIXES` linked, `16` `Derived (SW-only)`, while `SET`/`HRV`/`STB`/`NFR` sit in `SWE_DERIVED_PREFIXES`). Major hardware, RF actuation, navigation, biometric, and recovery capabilities lack `SYS.2` specs, `docs/system/system_architecture.md` (`SYS.3`, `v1.1.0`) is out of sync with `system_context.md` and `SWE.2`, `sys_verification_protocol.md:66` cites a wrong Varia BLE UUID, battery budgets conflict (`<= 8.0%/h` vs `< 5.0%/h`), and `13` obsolete IDs remain in `GRANDFATHERED_SYSREQS`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification (Live `521d6379`)**:
  * **Missing `SYS.2` (`SYSREQ-*`) Capabilities**:
    1. *Polar BLE SDK `8.6.0` (`app/build.gradle:244`)*: PFTP (`0000feee-...`) / PMD (`fb005c80-...`), flash recording, clock sync, 30s (`BACKFILL_TIMEOUT_MS = 30_000L`) post-workout backfill (`PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`), live IMU streaming (`REQ-SET-104..106, 108..118`).
    2. *Barometer, DEM, EKF, Climbs & Segments*: `Sensor.TYPE_PRESSURE`, Open-Meteo DEM (`api.open-meteo.com/v1/elevation`, `ElevationService.kt`), `KinematicAltitudeSlopeEkf.kt`, `ClimbDetector.kt`, `LiveClimbsRepository.kt`, `LiveSegmentSheet.kt` (`REQ-CLM-008..011`).
    3. *Google Maps GPX Follow-Me & Audio Navigation*: `GpxRouteImporter.kt`, `TurnByTurnNavigationEngine.kt`, `RouteDivergenceDetector.kt`, `NavigationAudioAlertManager.kt` (`REQ-NAV-043, 056`, `REQ-MAP-*`).
    4. *Smartphone IMU, Brake-Light & iTPMS*: `SpeedAndLocationDevice_ATTFused.kt` (`TYPE_GYROSCOPE`, `TYPE_ROTATION_VECTOR`), `QuaternionGyroProjector.kt` (`TYPE_GAME_ROTATION_VECTOR`), `SmartBrakeLightDetector.kt` (`TYPE_LINEAR_ACCELERATION`, `TYPE_GRAVITY`), `SpeedAndLocationDevice.java` (`TYPE_ACCELEROMETER`), `IndirectTpmsEstimator.kt`, `DualWheelTpmsEstimator.kt` (`REQ-CON-035`, `REQ-TRK-050`, `REQ-SEN-028..031`).
    5. *Bidirectional BLE & ANT+ Radar / Tail-Light Control*: BLE `6a4e3202` (`CHARACTERISTIC_RADAR_LIGHT_CONTROL`), `6a4e3203` (V1), `6a4e3204` (V2), RearVue `6a4e2f00`/`6a4e2f11`/`6a4e2f14` (`BluetoothConstants.java:58-74`); ANT+ `0x28` Radar (`ANTBikeRadarDevice.kt:79`) & `0x23` Bike Lights (`ANTBikeLightDevice.kt:48,68`).
    6. *Swimming*: `SwimWorkoutAdjustCard.kt`, `SwimmingSection.kt`, pool-length/lap backfill (`REQ-SET-107, 110, 111`).
    7. *HRV / DFA alpha-1*: `DfaCalculator.kt`, `HRVMetricsDevice.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvRampTestScreen.kt` (`REQ-TRK-060, 061, 067, 068, 072, 085`, `REQ-HRV-038..044`).
    8. *3-Tier Process-Death Recovery*: `TrackerService.java:1336-1355` (`MANUAL_PAUSED` -> `AUTO_PAUSED` -> `TRACKING`), `StartOrResumeDialog.kt`, `KillReason` (`REQ-TRK-018, 078, 080`, `REQ-STB-003, 017`).
  * **`SYS.3` (`system_architecture.md`) & `system_context.md` Drifts**: §4.2 omits synchronous Polar stop & 30s PFTP backfill on `mDbExecutor` (`TrackerService.java:2475-2488`, shifted from `2361-2363`); `SYS-IF-01` & `SYS-IF-02` are unidirectional (`-->`), omitting `0x180A`, `6a4e3202`/`6a4e3203`/`6a4e2f00`, Polar PFTP/PMD, and ANT+ `0x28`/`0x23`; `6` `system_context.md` elements (`Barometer`/`Proximity`, `Google Maps SDK v2`, `Open-Meteo`, `Firebase`, `Play Auth/Review`, `PebbleKit`) + Smartphone IMU are missing in `system_architecture.md` (and IMU is absent from `system_context.md`).
  * **Protocol & Budget Drifts**: `sys_verification_protocol.md:66` (shifted from `64`) cites hallucinated `6a4e3200-6628-eecd-2140-d68103ba32e3` vs `6a4e3200-667b-11e3-949a-0800200c9a66` (`BluetoothConstants.java:58`); `sys_verification_protocol.md:99,195,266` (`<= 8.0%/h`) conflicts with `docs/architecture.md:311` (shifted from `282`) & `REQ-NFR-006` (`< 5.0%/h` screen-off, `<= 12.0%/h` screen-on).
  * **`GRANDFATHERED_SYSREQS` (`verify_requirement_governance.py:4159-4173`)**: All `13` IDs (`SYSREQ-PER-001..005`, `SYSREQ-ACQ-001..005`, `SYSREQ-ENV-001..003`) have `0` remaining citations in `docs/requirements/*.md`, `docs/system/*.md`, and `tools/test_verify_requirement_governance.py` (purged from `docs/system/` in `ATT-6741`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: Author missing `SYSREQ-*` specs; synchronize `system_context.md`, `system_architecture.md`, `system_software_traceability.md`, and `docs/system_requirements.md`; fix Varia UUID in `sys_verification_protocol.md:66` & reconcile battery budget (`<= 5.0%/h` background / `<= 8.0%/h` mixed or unified budget across `REQ-NFR-006`, `architecture.md:311`, `sys_verification_protocol.md`); empty/remove `GRANDFATHERED_SYSREQS` in `verify_requirement_governance.py`; add `SYS.2/SYS.3` impact check to `stage1-analysis` & `stage2-req-test-spec` skills; update affected contract tests (`SystemContextContractTest`, `SystemArchitectureDesignContractTest`).
* **Out-of-Scope Non-Goals**: Any `app/src/main/` production code edits; physical device execution (`Rule 7.6`); reclassifying pure UI/process REQs (`UI`/`PRO`/`REV`/`STP`) out of `SWE_DERIVED_PREFIXES`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-053..056`, `REQ-PRO-069`, `REQ-PRO-076`, `REQ-NFR-006` (`req_process_stability_part1.md`); `GRANDFATHERED_SYSREQS` (`verify_requirement_governance.py:4159`); `SystemContextContractTest.kt:126-127,240`; `SystemArchitectureDesignContractTest.kt:177`.
* **Historical Origin & Commit Trace**: `52b49a02` (`ATT-6163`, added `GRANDFATHERED_SYSREQS` for 13 `ATT-6164` allocation IDs), `1660a7de` (`ATT-6741`, purged 13 orphan IDs & locked `SystemArchitectureDesignContractTest` to `12` IDs), `ATT-6738` (locked `SystemContextContractTest` to `TYPE_PRESSURE`/`TYPE_PROXIMITY` before IMU features existed), `ATT-8158` (`REQ-NFR-006` `On-Device Manual`).
* **Root Reason for Existing Formulation**: Prevented phantom `SYSREQ-*` links and hallucinated Android sensors (`TYPE_STEP_COUNTER`) prior to IMU/Polar/Radar-Light implementations.
* **Preservation of Core Invariants**: Keep `declared_ids == allocated_ids` 1:1 parity, `On-Device Manual` status on `REQ-NFR-006` (`NonFunctionalRequirementsContractTest`), `<= 4,000`-line shard cap (`req_process_stability_part1.md` at `3,979` lines & `req_data_migration_export_part1.md` at `3,991` lines -> new `REQ-PRO-*`/`TST-PRO-*` in `_part2.md`), `REQ-PRO-120` headers (`Status: Reviewed`), and `AspiceSweAuditContractTest`/`HoechstmassCoreFrameworkContractTest` skill markers.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `SYSREQ-*` capabilities across `docs/system/sysreq_*.md` + `docs/system_requirements.md`; net-new `REQ-PRO-*` (`req_process_stability_part2.md`) for `SYS.2/SYS.3` skill check; `REQ-NFR-006` & `REQ-PRO-053..056`/`069` reconciliation; re-linking affected `REQ-CON`/`TRK`/`SEN`/`NAV`/`CLM`/`SET`/`HRV`/`STB` entries.
* **Affected Architecture Elements**: `docs/system/system_context.md`, `system_architecture.md`, `sys_verification_protocol.md`, `system_software_traceability.md`, `docs/architecture.md:311`, `tools/verify_requirement_governance.py`, `.agents/skills/stage1-analysis/SKILL.md`, `.agents/skills/stage2-req-test-spec/SKILL.md`.
* **Verification Impact & Test Surface**: `SystemContextContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SystemDataExchangeRequirementsContractTest.kt`, `SystemVerificationProtocolContractTest.kt`, `SystemRequirementsSpecificationContractTest.kt`, `AspiceSweAuditContractTest.kt`, `NonFunctionalRequirementsContractTest.kt`, `tools/test_verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **MEDIUM** (`3 SP`, doc/governance/contract-test scope only, zero `app/src/main/` edits). Must update `SystemContextContractTest` & `SystemArchitectureDesignContractTest` in sync with `docs/system/` and keep shards `<= 4,000` lines.

---

## 6. Architectural Strategy & High-Level Solution
* Partition new `SYSREQ-*` specs cleanly across `docs/system/sysreq_live_tracking.md`, `sysreq_data_exchange.md`, and `sysreq_navigation_analytics.md` (or subsystem sections), allocate 100% in `system_architecture.md` (`v1.2.0`) + `docs/system_requirements.md`, make `SYS-IF-01/02` bidirectional, add Polar backfill in §4.2, and sync all 6 missing elements + IMU.
* Fix `sys_verification_protocol.md:66` UUID (`6a4e3200-667b-11e3-949a-0800200c9a66`), unify battery budget (`< 5.0%/h` screen-off vs `<= 8.0%/h` mixed endurance) across `SYS.5`/`SWE.2`/`REQ-NFR-006`, remove `GRANDFATHERED_SYSREQS`, and add `SYS.2/SYS.3` impact check to Stage 1 & Stage 2 skills.
