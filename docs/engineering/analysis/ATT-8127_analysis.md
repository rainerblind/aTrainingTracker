# Stage 1 Analysis: ATT-8127 - FIT Epoch/APP_ID Scoping, HRV Timestamp Allocations, GPX MaxSpeed/XML & HealthConnect Calories

**Ticket**: [ATT-8127](https://issue-tracker/ATT-8127) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`, `severity-major`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_02_Spät` | **Branch**: `feature/ATT-8127` (`7736e969`)  
**Author**: Lane 2 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Problem Statement & Forensic Root Cause Analysis
1. **`FitWorkoutParser.kt` Lap Matching & Unguarded Developer Fields (`:178-199`, `:326-360`, `:363-375`, `:435-463`)**:
   * **O(N·L) `SimpleDateFormat.parse` Loop**: Formats `lapMesg.startTime.date` to `String` (`:179`), then inside `rawRecords.forEach` (`:363-375`) re-parses `parsedLaps[i].startTime` backwards via `timeFormat.parse(it)` on every 1-Hz trackpoint (up to `14,400 × 20 = 288,000` parses per 4-hour multi-lap FIT file).
   * **Unscoped Developer Fields**: Matches `RecordMesg` radar fields solely by `fieldName == ... || num == 0|1|2|4|5` (`:326-360`) AND `SessionMesg` metadata fields by `fieldName == ... || num == 0..7` (`:435-463`) without checking `DeveloperDataIdMesg.applicationId` / `developerDataIndex`. Foreign Connect-IQ apps (Stryd, Moxy, Core Body Temp) reusing field numbers `0..7` corrupt radar telemetry and session metadata.
2. **`HrvPostWorkoutRecomputer.kt` 1-Hz `SimpleDateFormat` Allocations (`:162-168`, `:452-471`)**:
   * `parseTimestampMs(timeStr)` allocates a new `List<String>` and `1..4` `SimpleDateFormat` + `TimeZone` instances per sample row (`<= 57,600` allocations per 4-hour workout).
3. **`GpxWorkoutParser.kt` Peak Speed Bug (`:204-212`) & `GPXFileWriter.java` Unescaped XML / Dead Lap Query (`:83-96`, `:128-150`, `:205-213`)**:
   * `GpxWorkoutParser.kt:209` writes average lap speed (`parsedLap.distanceMeters / duration`) into `parsedLap.maxSpeed` instead of tracking peak speed (`maxOf`).
   * `GPXFileWriter.java:87,95` writes `creator` and `<name>` without XML escaping (`&`, `<`, `>`, `"`, `'`), ignores `workoutName`/`description`, and runs a dead `LapsDatabaseManager` SQL query per lap (`:128-150`) overwriting unused `totalTime`/`totalDistance` via dead `myGet()` (`:205-213`).
4. **`HealthConnectRecordMapper.kt` Legacy String-Scraping Fallback (`:384-393`)**:
   * While `ATT-6711` added numeric `workoutData.calories` (`:385`), the fallback (`:386-391`) still parses localized `extremaRows` display strings via `str?.toDoubleOrNull()`, turning German `"1.250"` into `1.25` kcal (1,000× error).

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)
Verified directly against live `sprint/2026-11-22_02_Spät` (`7736e969`):
* **Line-Number & Scope Drift Identified vs. Jira Hypothesis**:
  1. **FIT DevFields (2 colliding scopes, 2 App IDs, 0 HRV DevFields)**: HRV uses native `HrvMesg` (`:134-145`), NOT DeveloperFields. The two real unguarded DeveloperField sites are: (a) `RecordMesg` Bike Radar (`:326-360`, MyBikeTraffic UUID `6a4e3200-667b-11e3-949a-0800200c9a66` at `FitFileWriter.kt:387`), and (b) **`SessionMesg` App Metadata (`:435-463`, `FitFileWriter.APP_METADATA_DEVELOPER_DATA_ID` at `FitFileWriter.kt:124-127`)** — an additional blind spot missed by the ticket description! Both must be scoped via `DeveloperDataIdMesgListener` + `devField.appId`.
  2. **`HrvPostWorkoutRecomputer.kt`**: Confirmed live at `:452-471` (shifted from `:373-392` by `ATT-8706`/`ATT-9160`).
  3. **`GpxWorkoutParser.kt`**: `ATT-6045` (`6748d691`) migrated timestamps to `WorkoutTimestampParser` (`java.time`), shifting the `maxSpeed` bug to `:209` (`parsedLap.distanceMeters / duration` — still unfixed!). Both explicit `<speed>` (`pt.speedMps`) and fallback per-segment GPS speed (`segDistM / dtSec`, `0..100 m/s`, per-lap bounded so cross-lap pauses don't skew speed) must track `maxOf`.
  4. **`GPXFileWriter.java`**: Currently writes `<name>` + `startTime` (no `workoutName` or `<desc>` emitted yet) and unescaped `creator` (`:87`). Emit XML-escaped `creator`, `<name>` (`workoutName` if non-blank else `startTime`), and optional `<desc>` (GPX 1.1 XSD order) reusing `TCXFileWriter.escapeXml` (`coding_standards.md` §1.1), and remove dead Lap SQL (`:128-150`), unused `LapsDatabaseManager`/`BANALService` imports, and dead `myGet()` (`:205-213`).
  5. **`HealthConnectRecordMapper.kt`**: `ATT-6711` (`8abca7ef`) already added `workoutData.calories` (`:385`), and `WorkoutDataMapper` maps `WorkoutSummaries.CALORIES` symmetrically on both single-row and batch cursor paths (`:495-497,556,764`). Removing the dead/buggy `extremaRows` string-scraping fallback (`:386-391`) eliminates the `de_DE` `"1.250" -> 1.25` corruption completely (`coding_standards.md` §1.3).
  6. **Contract & LoC Audit**: All 6 touched files are well below `1,500` LoC (`ArchitectureConformanceContractTest`); `GPXFileWriter.java` stays `.java` (`JavaCoreMigrationStrategyContractTest` `<= 120`); `HrvPostWorkoutRecomputer.kt` retains its existing `ui.*` imports in `BASELINE_ALLOWED_DATABASE_UPWARD_UI_IMPORTS`; `DetailedDesignParityContractTest` is unaffected (none of the 4 detailed-design files touched).

## 3. Scope Grounding & Requirement Archaeology (Chesterton's Fence)
* **In-Scope (`2 SP`, `tier-a`)**: `FitWorkoutParser.kt`, `FitFileWriter.kt` (shared `RADAR_DEVELOPER_DATA_APP_ID` constant), `HrvPostWorkoutRecomputer.kt`, `WorkoutTimestampParser.kt` (epoch helper), `GpxWorkoutParser.kt`, `GPXFileWriter.java`, `HealthConnectRecordMapper.kt`, and targeted unit/contract tests.
* **Out-of-Scope**: `ATT-6045` (already merged), migrating `GPXFileWriter.java` to Kotlin (`ADR-001`), DB schema migrations (`0` version bumps), UI/strings (`0` strings).
* **Chesterton's Fence Audit**: Net-new `REQ-MIG-058` refining `REQ-MIG-038`, `REQ-MIG-053`, `REQ-EXP-022`, `REQ-PER-034.3`, and `REQ-SET-101.2` without relaxing any existing invariant; preserves `FitWorkoutParser` local-time DB string contract (`REQ-MIG-038`), `FitFileWriter` lossless round-trip (`REQ-MIG-053`), `GPXFileWriter` cropped-lap `<trkseg>` state machine (`ATT-4013`), and `HealthConnectRecordMapper` typed calories + route deduplication (`ATT-7668`).

## Change Impact Analysis
* **Affected Requirements**: Net-new `REQ-MIG-058` (`REQ-MIG-058.1`–`.5`) in `docs/requirements/req_data_migration_export_part2.md` tracing to `SYSREQ-DEX-001`, `SYSREQ-DEX-002`; `TST-MIG-058` in `docs/tests/tst_data_migration_export.md`. `SYS.2`/`SYS.3`: No external API/sensor/schema change (internal parser/exporter/mapper correctness). Localization: `N/A - 0 string changes`.
* **Affected Architecture Elements**: Migration parsers (`FitWorkoutParser`, `GpxWorkoutParser`, `WorkoutTimestampParser`), file writers (`FitFileWriter`, `GPXFileWriter`), HRV post-workout recomputer (`HrvPostWorkoutRecomputer`), and Health Connect mapper (`HealthConnectRecordMapper`). Zero new subsystems/services (`Rule 8.8`).
* **Verification Impact & Test Surface**: `FitWorkoutParserTest`, `FitLosslessRoundTripTest`, `FitFileWriterRadarTest`, `LegacyImportEngineFitHrvTest`, `HrvPostWorkoutRecomputerTest`, `GpxWorkoutParserTest`, `GPXFileWriterTest`, `FileExportersIntegrationTest`, `HealthConnectRecordMapperTest`, `PowerPhysiologyCalculatorTest`, `ArchitectureConformanceContractTest`, `JavaCoreMigrationStrategyContractTest`.
* **Risk Assessment & Invariants**: **MEDIUM-HIGH (`tier-a`)** — touches FIT/GPX import-export fidelity and Health Connect telemetry; mitigated by zero schema changes, strict `ByteArray.contentEquals` App-ID matching preserving `FitLosslessRoundTripTest`, XML DOM parsing verification, and locale-explicit `Locale.GERMANY` tests.
