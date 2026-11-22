# Stage 5 Walkthrough: ATT-8127 - FIT Epoch/APP_ID Scoping, HRV Timestamps, GPX MaxSpeed/XML & HealthConnect Calories

**Ticket**: [ATT-8127](https://issue-tracker/ATT-8127) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`, `severity-major`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_02_Spät` | **Branch**: `feature/ATT-8127`  
**Requirement / Test**: `REQ-MIG-058` (`SYSREQ-DEX-001`, `SYSREQ-DEX-002`) / `TST-MIG-058` (`[Verified]`)  
**Evidence Bundle**: `docs/engineering/test_evidence/tickets/ATT-8127/manifest.json` | **Gate 3**: `ATT-9644` (`PASS`)  
**Author**: Lane 2 Author (`agent1`) | **Date**: 2026-11-22 (Bounce 1/5 healed)

---

## 1. Summary of Implemented Changes
1. **`FitFileWriter.kt` & `FitWorkoutParser.kt` (`REQ-MIG-058.1`)**:
   * Replaced per-record `SimpleDateFormat.parse` lap matching (`:363-375`) with precomputed `LongArray` epoch bounds (`Long.MIN_VALUE` sentinel for null `startTime`), rightmost-matching upper-bound binary search for sorted non-null laps, and primitive `LongArray` reverse-scan fallback (`0` parses/record).
   * Exposed `FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID` (`6a4e3200-667b-11e3-949a-0800200c9a66`), fixed Garmin FIT SDK `BASE_TYPE_BYTE` signed-byte clamping via `writeApplicationIdUnsigned()` (`0..255`), and added `LEGACY_RADAR_DEVELOPER_DATA_APP_ID` for pre-`ATT-8127` backward compatibility.
   * Registered `DeveloperDataIdMesgListener` (clearing stale entries via `devDataAppIds.remove(devIndex)` on invalid `applicationId`) and guarded BOTH `RecordMesg` radar fields and `SessionMesg` metadata fields via `matchesAppId` preferring per-`MESG_DEF`-bound `devField.appId` over the mutable file-wide `devDataAppIds[devIndex]` fallback (preventing chained/redefined-index spoofing).
2. **`HrvPostWorkoutRecomputer.kt` (`REQ-MIG-058.2`)**:
   * Replaced per-sample `SimpleDateFormat`/`TimeZone` allocations in `parseTimestampMs()` with static immutable UTC `java.time.format.DateTimeFormatter` instances and trimmed char-at-10 dispatch supporting space/fractional, ISO local/UTC/offset, and compact formats.
3. **`WorkoutTimestampParser.kt`, `GpxWorkoutParser.kt` & `GPXFileWriter.java` (`REQ-MIG-058.3`, `.4`)**:
   * Added `WorkoutTimestampParser.parseIsoToEpochMillis()` preserving exact sub-second millisecond precision (>1 Hz GPX) and replaced `GpxWorkoutParser`'s average-speed `maxSpeed` bug with true intra-lap peak speed (`maxOf` on explicit `<speed>` `0..100 m/s` or intra-lap GPS segment speed `dtMs > 0L` / `0..100 m/s` with non-regressing `prevLap*` and distance reuse).
   * Updated `GPXFileWriter.java` to XML-escape `creator`, `<name>` (`workoutName` if non-blank else `startTime`), and optional `<desc>` via `TCXFileWriter.escapeXml` (GPX 1.1 XSD order), and deleted the dead `LapsDatabaseManager` SQL loop, unused imports, and dead `myGet()`.
4. **`HealthConnectRecordMapper.kt` (`REQ-MIG-058.5`)**:
   * Removed legacy `extremaRows` formatted-string scraping in `extractCalories()`; now returns strictly `workoutData.calories?.takeIf { it > 0 }?.toDouble() ?: 0.0` (immune to German `"1.250" -> 1.25` kcal corruption).

## 2. Verification Summary (Tier 1 Targeted Incremental Suite + Live Emulator Smoke)
* **Test Scope**: Tier-1 targeted incremental `./gradlew :app:testDebugUnitTest --rerun` across **14 suites**: **106/106 PASS (`0` fail, `0` err, `0` skip, `0` new compiler warnings)**:
  * `FitWorkoutParserTest` (`7/7` — incl. mixed foreign+own, legacy signed-byte radar, and redefined `developerDataIndex` tests), `FitLosslessRoundTripTest` (`6/6`), `FitFileWriterRadarTest` (`2/2`), `FitFileWriterTest` (`16/16`), `LegacyImportEngineFitHrvTest` (`3/3`)
  * `HrvPostWorkoutRecomputerTest` (`14/14`)
  * `GpxWorkoutParserTest` (`6/6` — incl. 2 Hz sub-second trackpoints), `WorkoutTimestampParserTest` (`4/4`), `GPXFileWriterTest` (`5/5`), `FileExportersIntegrationTest` (`3/3`)
  * `HealthConnectRecordMapperTest` (`10/10`), `PowerPhysiologyCalculatorTest` (`18/18`), `ArchitectureConformanceContractTest` (`11/11`), `JavaCoreMigrationStrategyContractTest.testClassInventoryAndDecisionCoverage_TST_ARC_005_2` (`1/1`)
  * *(Pre-existing sprint-branch failures in `auto_gate.py`'s 24-suite sweep, strictly outside `ATT-8127`: `JavaCoreMigrationStrategyContractTest.testKeepVsMigrateConsistencyAndJavaCountReconciliation_TST_PRO_129_1` & `AspiceSweAuditContractTest.testWorkProductHeadersAndGovernanceReconciliation_contract` from `ATT-9501` PO approval of `ADR-001`, and `RepositoryEngineCleanlinessContractTest.testTST_DAT_038_2_phaseBEngines_zeroProhibitedDatabaseImports` on untouched `WorkoutClusterEngine.kt`.)*
* **Live Emulator Verification (`Rule 7.6`, `Rule 8.5`, `Rule 9.3`)**: `check-install-required` reported **`INSTALL_REQUIRED=true`** (runtime packages `HealthConnectRecordMapper.kt` & `HrvPostWorkoutRecomputer.kt`). Executed locked `agent_emulator.py exec --ensure` on `emulator-5554` (`Pixel_8_Android_15`): `installDebug` + `am start -W MainActivityWithNavigation` (`LaunchState: COLD`, `TotalTime: 10547 ms`) + `logcat -d -b crash` **CLEAN (`0` crashes/ANRs)** + visual `read_file` inspection of `docs/engineering/test_evidence/tickets/ATT-8127/screenshots/att8127_emulator_smoke.png` confirming rendered cockpit UI.
* **Governance & Evidence**: `verify_requirement_governance.py --base-ref sprint/2026-11-22_02_Spät` (`EXIT 0`); SHA-256 evidence bundle at `docs/engineering/test_evidence/tickets/ATT-8127/manifest.json`; `0` `strings*.xml` edits (`Localization Parity: N/A`); `ANT-Android-SDKs` intact at `160000`.

## 3. Acceptance Criteria Traceability

| AC | Requirement | Verified Evidence | Verdict |
| :--- | :--- | :--- | :---: |
| **AC1** | `REQ-MIG-058.1` | `FitWorkoutParserTest` (`7/7`), `FitLosslessRoundTripTest` (`6/6`), `FitFileWriterRadarTest` (`2/2`) | **MET** |
| **AC2** | `REQ-MIG-058.2` | `HrvPostWorkoutRecomputerTest` (`14/14`) incl. UTC formats, trim/fractional, DST `Europe/Berlin` | **MET** |
| **AC3** | `REQ-MIG-058.3` | `GpxWorkoutParserTest` (`6/6`) & `WorkoutTimestampParserTest` (`4/4`) incl. 2 Hz sub-second precision | **MET** |
| **AC4** | `REQ-MIG-058.4` | `GPXFileWriterTest` (`5/5`), `FileExportersIntegrationTest` (`3/3`), `verify(exactly=0)` on `LapsDatabaseManager` | **MET** |
| **AC5** | `REQ-MIG-058.5` | `HealthConnectRecordMapperTest` (`10/10`) under `Locale.GERMANY` + live `emulator-5554` smoke (`0` crashes) | **MET** |
