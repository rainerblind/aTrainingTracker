# Stage 3: Implementation Plan - ATT-8127: FIT Epoch/APP_ID Scoping, HRV Timestamp Parsing, GPX MaxSpeed/XML & HealthConnect Numeric Calories

**Ticket**: [ATT-8127](https://issue-tracker/ATT-8127) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_02_Spät` | **Branch**: `feature/ATT-8127`  
**Requirement Mapping**: `REQ-MIG-058` (`REQ-MIG-058.1`–`REQ-MIG-058.5`, `SYSREQ-DEX-001`, `SYSREQ-DEX-002`) | **Test Mapping**: `TST-MIG-058` (`TST-MIG-058.1`–`TST-MIG-058.5`)  
**Author**: Lane 2 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Architectural Design (SWE.2)
* **FIT Developer-Field Scoping & Epoch Lap Lookup (`FitFileWriter.kt`, `FitWorkoutParser.kt`)**:
  * Promote `FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID: ByteArray` (`@JvmField`, 16-byte UUID `6a4e3200-667b-11e3-949a-0800200c9a66`) alongside existing `FitFileWriter.APP_METADATA_DEVELOPER_DATA_ID` and reuse it when encoding `devDataIdMesg` (`FitFileWriter.kt:387`).
  * In `FitWorkoutParser.parse()`, register `DeveloperDataIdMesgListener` capturing `developerDataIndex: Short -> ByteArray` (from `mesg.getApplicationId(0..num-1)`). Resolve each `DeveloperField`'s `applicationId` (from map or `devField.appId` fallback) and guard BOTH `RecordMesg` radar fields (`RADAR_DEVELOPER_DATA_APP_ID`) and `SessionMesg` metadata fields (`APP_METADATA_DEVELOPER_DATA_ID`) via `ByteArray.contentEquals`.
  * Precompute `lapStartEpochs = LongArray(rawLaps.size) { rawLaps[it].startTime?.date?.time ?: Long.MIN_VALUE }` once; determine `sampleLapNr` per record via `O(log L)` upper-bound binary search when sorted (or `O(L)` primitive `LongArray` reverse scan if unsorted) — **0 `SimpleDateFormat.parse` calls** while preserving `timeFormat.format(date)` local-time output (`REQ-MIG-038`).
* **Allocation-Free `HrvPostWorkoutRecomputer.kt` Timestamp Parsing**:
  * Replace per-sample `SimpleDateFormat` loop (`:452-471`) with 4 static immutable UTC `DateTimeFormatter`s + `ZoneOffset.UTC` and O(1) char-at-10 dispatch (`' '`, `'T'`, `'_'`) in `internal fun parseTimestampMs(timeStr: String?): Long?`. Remove `java.text.SimpleDateFormat` and `java.util.TimeZone` imports; keep existing `ui.*` imports untouched so `ArchitectureConformanceContractTest` baseline stays valid.
* **GPX Peak `maxSpeed` (`WorkoutTimestampParser.kt`, `GpxWorkoutParser.kt`) & Clean XML Export (`GPXFileWriter.java`)**:
  * Add `internal fun parseDbTimeToEpochMillis(dbTime: String): Long?` to `WorkoutTimestampParser.kt`.
  * In `GpxWorkoutParser.kt`, track per-lap `lapMaxExplicitSpeed` (`pt.speedMps in 0.0..100.0`) and `lapMaxSegmentSpeed` (`segDistM / dtSec in 0.0..100.0` using per-lap `prevLapLat`/`prevLapLng`/`prevLapEpochMs`), setting `parsedLap.maxSpeed = lapMaxExplicitSpeed ?: lapMaxSegmentSpeed`.
  * In `GPXFileWriter.java`, reuse `TCXFileWriter.escapeXml` (`coding_standards.md` §1.1) on `creator`, `<name>` (`workoutName` if non-blank else `startTime`), and optional `<desc>` (GPX 1.1 XSD order); delete dead `LapsDatabaseManager` SQL loop (`:128-150`), unused `LapsDatabaseManager`/`BANALService` imports, and dead `myGet()` (`:205-213`).
* **Health Connect Numeric Calories (`HealthConnectRecordMapper.kt`)**:
  * Simplify `extractCalories(workoutData)` to `workoutData.calories?.takeIf { it > 0 }?.toDouble() ?: 0.0` — zero UI-string parsing (`coding_standards.md` §1.3).
* **SWE.2 / Detailed Design / Contract Parity**: Zero new subsystems/services (`Rule 8.8`), zero DB schema bumps, zero UI/strings edits, `GPXFileWriter.java` stays `.java` (`JavaCoreMigrationStrategyContractTest`), all 6 files `< 1,500` LoC (`ArchitectureConformanceContractTest`), `DetailedDesignParityContractTest` unaffected.

## 2. Non-Negotiable System Invariants
1. **FIT Round-Trip & Local-Time Parity (`REQ-MIG-038`, `REQ-MIG-053`, `REQ-EXP-042`)**: Own `FitFileWriter` radar and metadata developer fields continue round-tripping identically; `FitWorkoutParser` keeps local-time `"yyyy-MM-dd HH:mm:ss"` formatting and native `HrvMesg` / `PowerPhysiologyCalculatorTest` source invariants (`foundSensors.add(SensorType.POWER)`, `sessionNormalizedPowerWatts = sessionNp`).
2. **GPX XML Well-Formedness & Cropped-Lap State Machine (`REQ-EXP-022`, `ATT-4013`)**: Zero orphan `</trkseg>` tags on cropped lap 0 or zero-sample workouts; valid GPX 1.1 XSD element order (`<name>`, `<desc>`, `<trkseg>*`).
3. **Health Connect Precision & Route Safety (`REQ-SET-101`, `ATT-7668`)**: Exact numeric kcal under all locales; route deduplication and end-time boundary behavior untouched.

## 3. Atomic Step-by-Step Construction Plan (Stage 4 — Strictly Post-Gate-3)
1. **Step 1 — Gate 3 Check (`Rule 4.1`)**: Execute `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` before touching any file under `app/src/`.
2. **Step 2 — FIT Epoch Lap Matching & `APP_ID` Scoping (`REQ-MIG-058.1`)**: Expose `FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID`; wire `DeveloperDataIdMesgListener`, `matchesAppId` guards on `RecordMesg` & `SessionMesg`, and `LongArray` binary-search/reverse-scan lap lookup in `FitWorkoutParser.kt`.
3. **Step 3 — `HrvPostWorkoutRecomputer.kt` `java.time` Refactor (`REQ-MIG-058.2`)**: Replace `SimpleDateFormat` in `parseTimestampMs` with static UTC `DateTimeFormatter`s and O(1) separator dispatch.
4. **Step 4 — GPX `maxSpeed` & `GPXFileWriter.java` XML/Dead-SQL Fix (`REQ-MIG-058.3`, `.4`)**: Add `WorkoutTimestampParser.parseDbTimeToEpochMillis`, update `GpxWorkoutParser.kt` lap peak speed tracking, and update `GPXFileWriter.java` (XML escaping via `TCXFileWriter.escapeXml` + optional `<desc>` + remove dead Lap SQL & `myGet`).
5. **Step 5 — `HealthConnectRecordMapper.kt` Numeric Calories (`REQ-MIG-058.5`)**: Remove `extremaRows` string-scraping fallback in `extractCalories`.
6. **Step 6 — Unit & Contract Tests (`TST-MIG-058.1`–`.5`)**: Add targeted tests in `FitWorkoutParserTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `GpxWorkoutParserTest.kt`, `GPXFileWriterTest.kt`, and `HealthConnectRecordMapperTest.kt`.
7. **Step 7 — Verification & Governance**: Run targeted Tier-1 Gradle suites (`--rerun`), safety linters, `verify_requirement_governance.py`, and `auto_gate.py --write-bundle`.

## 4. Targeted Verification & Rollback Plan
* **Verification Command (`--rerun`, never `--rerun-tasks`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.migration.parsers.FitWorkoutParserTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.FitLosslessRoundTripTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.LegacyImportEngineFitHrvTest" \
    --tests "com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriterRadarTest" \
    --tests "com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriterTest" \
    --tests "com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputerTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.parsers.GpxWorkoutParserTest" \
    --tests "com.atrainingtracker.trainingtracker.exporter.writer.GPXFileWriterTest" \
    --tests "com.atrainingtracker.trainingtracker.integration.FileExportersIntegrationTest" \
    --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectRecordMapperTest" \
    --tests "com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculatorTest" \
    --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
    --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"
  ```
* **Rollback**: Isolated on `feature/ATT-8127`; cleanly revertible prior to Stage 6 merge.
