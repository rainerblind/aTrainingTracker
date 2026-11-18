# Stage 2: Requirement & Test Specification - ATT-8201

**DOC-ID**: `DOC-TST-ATT-8201` | **Ticket**: [ATT-8201](https://rainerblind.atlassian.net/browse/ATT-8201) (`3 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8201`  
**Requirement Mapping**: `REQ-PRO-131` (`[Planned]`, refining `REQ-PRO-053..056`, `REQ-PRO-069`, `REQ-PRO-076`, `REQ-NFR-006`) | **Test Spec ID**: `TST-PRO-131` (`[Planned]`)  
**Localization Parity**: `N/A - no UI strings` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-131` `[Planned]`)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **Implementation**: `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system_requirements.md`, `docs/system/system_context.md`, `docs/system/system_architecture.md`, `docs/system/sys_verification_protocol.md`, `docs/system/system_software_traceability.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `.agents/skills/stage1-analysis/SKILL.md`, `.agents/skills/stage2-req-test-spec/SKILL.md`, `SystemContextContractTest.kt`, `SystemArchitectureDesignContractTest.kt`
* **REQ-PRO-131.1 (Net-New `SYSREQ-*` Capabilities — Staged for Stage 4 Execution)**: Author `9` net-new black-box `SYSREQ-*` specs (`SYSREQ-TRK-007..013`, `SYSREQ-DEX-007..008`) in `docs/system/sysreq_*.md` + `docs/system_requirements.md` + `docs/system/system_software_traceability.md`, linked bidirectionally to existing `REQ-*` shards (deferred to Stage 4 after Gate 3 so Stage 2 preserves `SystemArchitectureDesignContractTest.kt:177` 12-ID parity and `verify_requirement_governance.py` 100% coverage):
  | Planned `SYSREQ` ID | Capability & Grounded Code/Protocol Scope | Primary Linked `REQ-*` |
  | :--- | :--- | :--- |
  | `SYSREQ-TRK-007` | Bidirectional BLE (`6a4e3202`/`3203`/`3204`, RearVue `6a4e2f00`/`2f11`/`2f14`) & ANT+ (`0x28`/`0x23`) Radar/Light Actuation & Smart Braking (`RadarLightController.kt`, `SmartBrakeLightDetector.kt`, `ANTBikeRadarDevice.kt`, `ANTBikeLightDevice.kt`) | `REQ-CON-035`, `REQ-RAD-005` |
  | `SYSREQ-TRK-008` | Barometric Pressure (`TYPE_PRESSURE`), EKF Altitude/Slope Fusion, Live Climbs & Segments (`KinematicAltitudeSlopeEkf.kt`, `ClimbDetector.kt`, `LiveClimbsRepository.kt`, `LiveClimbSheet.kt`, `LiveSegmentsRepository.kt`) | `REQ-CLM-008..011`, `REQ-LIV-001` |
  | `SYSREQ-TRK-009` | Google Maps GPX Follow-Me & Turn-by-Turn Audio Navigation (`GpxRouteImporter.kt`, `TurnByTurnNavigationEngine.kt`, `RouteDivergenceDetector.kt`, `NavigationAudioAlertManager.kt`) | `REQ-NAV-043`, `REQ-NAV-056`, `REQ-MAP-003` |
  | `SYSREQ-TRK-010` | Smartphone IMU Kinematics (`TYPE_GYROSCOPE`, `TYPE_ROTATION_VECTOR`, `TYPE_GAME_ROTATION_VECTOR`, `TYPE_LINEAR_ACCELERATION`, `TYPE_GRAVITY`, `TYPE_ACCELEROMETER`), Fused Odometry & iTPMS (`SpeedAndLocationDevice_ATTFused.kt`, `QuaternionGyroProjector.kt`, `IndirectTpmsEstimator.kt`, `DualWheelTpmsEstimator.kt`) | `REQ-TRK-050`, `REQ-SEN-028..031` |
  | `SYSREQ-TRK-011` | Pool & Open-Water Swimming Telemetry & Lap Adjustment (`SwimWorkoutAdjustCard.kt`, `SwimmingSection.kt`, Polar/watch pool-length & lap backfill) | `REQ-SET-107`, `REQ-SET-110`, `REQ-SET-111` |
  | `SYSREQ-TRK-012` | Live HRV / RR Streaming & DFA Alpha-1 Threshold Estimation (`DfaCalculator.kt`, `HRVMetricsDevice.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvRampTestScreen.kt`, `HrvThresholdEstimator.kt`) | `REQ-TRK-060`, `REQ-TRK-061`, `REQ-HRV-038..044` |
  | `SYSREQ-TRK-013` | Three-Tier Process-Death Session Recovery (`TrackerService.java`, `StartOrResumeDialog.kt`, `MANUAL_PAUSED` -> `AUTO_PAUSED` -> `TRACKING`) | `REQ-TRK-018`, `REQ-TRK-078`, `REQ-STB-003` |
  | `SYSREQ-DEX-007` | Polar BLE SDK `8.6.0` PFTP (`0000feee-...`) / PMD (`fb005c80-...`) Offline Recording, Clock Sync & 30s (`BACKFILL_TIMEOUT_MS`) Backfill (`PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `TrackerService.java`) | `REQ-SET-104..106`, `REQ-SET-108..118` |
  | `SYSREQ-DEX-008` | External Elevation Model (`api.open-meteo.com/v1/elevation`, `ElevationService.kt`, `ElevationCacheDatabaseManager.kt`) & Offline-First Fallback | `REQ-GEO-001`, `REQ-GEO-002` |
* **REQ-PRO-131.2 (`SYS.3` & `system_context.md` Synchronization)**: `docs/system/system_architecture.md` (`v1.1.0` -> `v1.2.0`) and `docs/system/system_context.md` SHALL synchronize all external elements (Smartphone IMU sensors, Barometer/Proximity, Google Maps SDK v2, Open-Meteo, Firebase, Play Auth/Review, PebbleKit), make `SYS-IF-01/02` bidirectional (`<-->`), add 30s Polar PFTP backfill on `mDbExecutor` to §4.2, and allocate 100% of all `21` `SYSREQ-*` IDs (`declaredSysreqs == allocatedSysreqs`).
* **REQ-PRO-131.3 (`SYS.5` Protocol Fix, Battery Budget Reconciliation & `GRANDFATHERED_SYSREQS` Purge)**: `docs/system/sys_verification_protocol.md:66` SHALL fix Varia UUID to `6a4e3200-667b-11e3-949a-0800200c9a66`; lines `99, 195, 266` SHALL reconcile battery budgets (`< 5.0%/h` screen-off per `REQ-NFR-006`, `<= 8.0%/h` mixed `E5` endurance, `<= 12.0%/h` screen-on cockpit, preserving `REQ-NFR-006` `On-Device Manual` `[Implemented]`); `tools/verify_requirement_governance.py:4159-4173` SHALL empty/remove `GRANDFATHERED_SYSREQS` (`0` phantom citations) with offline tests in `tools/test_verify_requirement_governance.py`.
* **REQ-PRO-131.4 (`SYS.2`/`SYS.3` Skill Impact Gate)**: `.agents/skills/stage1-analysis/SKILL.md` and `.agents/skills/stage2-req-test-spec/SKILL.md` SHALL add a mandatory `SYS.2/SYS.3` impact check (preserving `"Documentation Code-Grounding"` for `AspiceSweAuditContractTest.kt:223` and `HoechstmassCoreFrameworkContractTest` parity).
* **REQ-PRO-131.5 (Contract Parity & Invariants)**: Update `SystemContextContractTest.kt` (allow authentic IMU `Sensor.TYPE_*` constants), `SystemArchitectureDesignContractTest.kt` (`21` `SYSREQ-*` count + bidirectional `SYS-IF-01/02`), `SystemLiveTrackingRequirementsContractTest.kt`, `SystemDataExchangeRequirementsContractTest.kt`, `SystemRequirementsSpecificationContractTest.kt`, and `SystemVerificationProtocolContractTest.kt`. Zero `app/src/main/` edits; `Localization Parity: N/A - no UI strings`; all shards `<= 4,000` lines (`_part2.md`).
* **Given-When-Then Acceptance Criteria**:
  * *Given* `docs/system/` (`21` `SYSREQ-*`), `system_context.md`, `system_architecture.md` (`v1.2.0`), and `sys_verification_protocol.md`, *When* all `System*ContractTest` and `NonFunctionalRequirementsContractTest` suites execute, *Then* 100% pass with `declaredSysreqs == allocatedSysreqs == 21`, authentic IMU/Polar/Radar/DEM grounding, `6a4e3200-667b-11e3-949a-0800200c9a66`, reconciled battery budgets (`REQ-NFR-006` `[Implemented]`), and `100%` `SYSREQ->REQ` coverage.
  * *Given* `tools/verify_requirement_governance.py` (`GRANDFATHERED_SYSREQS` removed/empty) and updated Stage 1/2 `SKILL.md` files, *When* `test_verify_requirement_governance.py`, `verify_requirement_governance.py`, `AspiceSweAuditContractTest`, and `HoechstmassCoreFrameworkContractTest` run, *Then* phantom `SYSREQ-PER/ACQ/ENV-*` references fail closed and all governance/contract gates exit `0`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-053..056`, `REQ-PRO-069`, `REQ-PRO-076`, `REQ-NFR-006` (`req_process_stability_part1.md`); `GRANDFATHERED_SYSREQS` (`verify_requirement_governance.py:4159-4173`); `SystemContextContractTest.kt:126-127,240,249`; `SystemArchitectureDesignContractTest.kt:177`.
* **Historical Origin & Commit Trace**: `52b49a02` (`ATT-6163` `GRANDFATHERED_SYSREQS`), `1660a7de` (`ATT-6741` purged 13 orphan IDs & locked `12` `SYSREQ-*`), `ATT-6738` (locked `SystemContextContractTest` to `TYPE_PRESSURE`/`TYPE_PROXIMITY`), `ATT-8158` (`REQ-NFR-006` `On-Device Manual`).
* **Root Reason for Existing Formulation**: Blocked phantom `SYSREQ-*` links and hallucinated Android sensors (`TYPE_STEP_COUNTER`) before IMU/Polar/Radar-Light specs were authored.
* **Preservation of Core Invariants**: Preserves `declared_ids == allocated_ids` 1:1 parity, `REQ-NFR-006` `On-Device Manual` `[Implemented]`, `<= 8.0%/h` mixed `E5` threshold in `SystemVerificationProtocolContractTest.kt:174`, `<= 4,000`-line shard cap (`_part2.md`), `REQ-PRO-120` `Status: Reviewed` headers, `"Documentation Code-Grounding"` (`AspiceSweAuditContractTest.kt:223`), `HoechstmassCoreFrameworkContractTest` parity, and `Localization Parity: N/A - no UI strings`.

---

## 2. Test Specification (`TST-PRO-131` `[Planned]`)
* **`TST-PRO-131.1` (`SystemLiveTrackingRequirementsContractTest`, `SystemDataExchangeRequirementsContractTest`, `SystemRequirementsSpecificationContractTest`)**: Verify all `21` `SYSREQ-*` (`SYSREQ-TRK-001..013`, `SYSREQ-DEX-001..008`) exist with mandatory attributes, authentic disk files, and `100%` `SYSREQ->REQ` traceability.
* **`TST-PRO-131.2` (`SystemContextContractTest`, `SystemArchitectureDesignContractTest`)**: Verify IMU sensors, Polar PFTP/PMD, bidirectional `SYS-IF-01/02`, §4.2 30s backfill on `mDbExecutor`, all 6 context elements, and `declaredSysreqs == allocatedSysreqs == 21`.
* **`TST-PRO-131.3` (`SystemVerificationProtocolContractTest`, `NonFunctionalRequirementsContractTest`)**: Verify Varia UUID `6a4e3200-667b-11e3-949a-0800200c9a66`, reconciled battery budget (`< 5.0%/h` screen-off / `<= 8.0%/h` mixed / `<= 12.0%/h` screen-on), and `REQ-NFR-006` `On-Device Manual` `[Implemented]`.
* **`TST-PRO-131.4` (`tools/test_verify_requirement_governance.py` & `verify_requirement_governance.py`)**: Offline `tempfile` unit test proving `GRANDFATHERED_SYSREQS` removal rejects `SYSREQ-PER/ACQ/ENV-*` and full governance + `--check-size --strict` (`<= 4,000` lines) exits `0`.
* **`TST-PRO-131.5` (`AspiceSweAuditContractTest`, `HoechstmassCoreFrameworkContractTest`)**: Verify Stage 1/2 `SKILL.md` `SYS.2/SYS.3` impact check preserves `"Documentation Code-Grounding"` and Höchstmaß framework parity (`Localization Parity: N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-131.1` | Gradle Contract | `sysreq_*.md`, `docs/system_requirements.md`, `system_software_traceability.md` | `REQ-PRO-131.1`, `REQ-PRO-054/055/076` | Planned |
| `TST-PRO-131.2` | Gradle Contract | `system_context.md`, `system_architecture.md`, `SystemContextContractTest`, `SystemArchitectureDesignContractTest` | `REQ-PRO-131.2`, `REQ-PRO-053/056` | Planned |
| `TST-PRO-131.3` | Gradle Contract | `sys_verification_protocol.md`, `docs/architecture.md` §4.9, `REQ-NFR-006` | `REQ-PRO-131.3`, `REQ-PRO-069`, `REQ-NFR-006` | Planned |
| `TST-PRO-131.4` | Python Unit + Gov | `verify_requirement_governance.py`, `test_verify_requirement_governance.py` | `REQ-PRO-131.3`, `REQ-PRO-131.5` | Planned |
| `TST-PRO-131.5` | Skill Audit | `stage1-analysis/SKILL.md`, `stage2-req-test-spec/SKILL.md`, `AspiceSweAuditContractTest` | `REQ-PRO-131.4` | Planned |
