# Stage 2: Requirement & Test Specification - ATT-8127: FIT Epoch/APP_ID Scoping, HRV Timestamp Parsing, GPX MaxSpeed/XML & HealthConnect Numeric Calories

**Ticket**: [ATT-8127](https://issue-tracker/ATT-8127) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_02_Spät` | **Branch**: `feature/ATT-8127`  
**Requirement Mapping**: `REQ-MIG-058` (`REQ-MIG-058.1`–`REQ-MIG-058.5`, `SYSREQ-DEX-001`, `SYSREQ-DEX-002`) | **Test Spec ID**: `TST-MIG-058` (`TST-MIG-058.1`–`TST-MIG-058.5`)  
**Author**: Lane 2 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Requirement Specification (`REQ-MIG-058`)

### 1.1 Attributes
- **System Requirement(s)**: `SYSREQ-DEX-001`, `SYSREQ-DEX-002` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration | **Localization Parity**: `N/A - 0 string changes`

### 1.2 Normative Clauses
1. **Epoch-Based FIT Lap Matching & `APP_ID`-Scoped Developer Fields (`REQ-MIG-058.1`)**:
   - `FitWorkoutParser.parse()` SHALL store lap start boundaries as primitive `Long` epoch milliseconds (`rawLaps[i].startTime?.date?.time`) and assign `LAP_NR` per record via numeric `Long` binary search (or `LongArray` reverse scan fallback if laps are unsorted) with **zero** `SimpleDateFormat.parse` calls inside the record loop.
   - `FitFileWriter` SHALL expose `RADAR_DEVELOPER_DATA_APP_ID` (16-byte MyBikeTraffic UUID `6a4e3200-667b-11e3-949a-0800200c9a66`) alongside `APP_METADATA_DEVELOPER_DATA_ID`. `FitWorkoutParser` SHALL register `DeveloperDataIdMesgListener`, resolve each `DeveloperField`'s 16-byte `applicationId`, and interpret `RecordMesg` radar fields (`0, 1, 2, 4, 5`) strictly when `applicationId == FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID` and `SessionMesg` metadata fields (`0..7`) strictly when `applicationId == FitFileWriter.APP_METADATA_DEVELOPER_DATA_ID`. Foreign Connect-IQ developer fields MUST be ignored.
2. **Allocation-Free `java.time` Timestamps in `HrvPostWorkoutRecomputer` (`REQ-MIG-058.2`)**:
   - `HrvPostWorkoutRecomputer.parseTimestampMs(timeStr)` SHALL replace per-sample `SimpleDateFormat` / `TimeZone` allocations with static immutable UTC `java.time.format.DateTimeFormatter` instances (`"yyyy-MM-dd HH:mm:ss"`, `"yyyy-MM-dd'T'HH:mm:ss"`, `"yyyy-MM-dd'T'HH:mm:ss'Z'"`, `"yyyy-MM-dd_HHmmss"`, plus ISO offset/fractional fallback) with zero `SimpleDateFormat` usage.
3. **True Peak `maxSpeed` in `GpxWorkoutParser` (`REQ-MIG-058.3`)**:
   - `GpxWorkoutParser.parse()` SHALL populate `ParsedLap.maxSpeed` with the true peak speed (`maxOf`) within each lap — preferring explicit valid trackpoint `<speed>` (`pt.speedMps in 0.0..100.0`) and falling back to peak valid per-segment GPS speed (`segDistM / dtSec in 0.0..100.0`, computed strictly within the lap so cross-lap pauses cannot distort speed), returning `null` if no valid speed sample exists — NEVER writing average lap speed (`distanceMeters / duration`).
4. **XML-Escaped GPX Export & Dead Lap Query Removal (`REQ-MIG-058.4`)**:
   - `GPXFileWriter.doExport()` SHALL XML-escape `creator`, `<name>` (`workoutName` if non-blank else `startTime`), and optional `<desc>` (when `description` is non-blank, in valid GPX 1.1 XSD sequence `<name>`, `<desc>`, `<trkseg>*`) reusing `TCXFileWriter.escapeXml` (`&`, `<`, `>`, `"`, `'`).
   - `GPXFileWriter` SHALL remove the dead `LapsDatabaseManager` SQL query loop (`:128-150`) and dead `myGet()` helper (`:205-213`), executing zero `LapsDatabaseManager` queries during GPX export.
5. **Locale-Independent Numeric Calories in `HealthConnectRecordMapper` (`REQ-MIG-058.5`)**:
   - `HealthConnectRecordMapper.extractCalories(workoutData)` SHALL read strictly the numeric `workoutData.calories` primitive (`> 0 -> toDouble()`, otherwise `0.0`) and MUST NOT parse formatted UI display strings from `workoutData.extremaRows`, preventing German thousands separators (`"1.250"`) from being parsed as `1.25` kcal under `Locale.GERMANY` (`de_DE`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (`TST-MIG-058.1`)**: *Given* a multi-lap FIT file containing both own `FitFileWriter` radar/metadata developer fields AND foreign Connect-IQ developer fields (`num = 0..7` with foreign `applicationId`), *When* `FitWorkoutParser.parse` executes, *Then* lap assignment matches exact numeric epoch boundaries without `SimpleDateFormat.parse`, foreign fields are ignored (zero ghost radar/metadata), and own `FitFileWriter` fields still round-trip losslessly.
* **AC2 (`TST-MIG-058.2`)**: *Given* 1-Hz sample timestamps across all 4 supported patterns under a DST-observing timezone (`Europe/Berlin`), *When* `HrvPostWorkoutRecomputer.parseTimestampMs` and `recomputeWorkout` execute, *Then* UTC epoch milliseconds match deterministically with zero `SimpleDateFormat` allocations.
* **AC3 (`TST-MIG-058.3`)**: *Given* a GPX file with varying speeds (explicit `<speed>` tags AND GPS-only trackpoints with non-uniform segment speeds), *When* `GpxWorkoutParser.parse` runs, *Then* `ParsedLap.maxSpeed` equals the true peak speed (strictly greater than lap average speed).
* **AC4 (`TST-MIG-058.4`)**: *Given* a workout with `&`, `<`, `>`, `"`, `'` in `workoutName` and `description` across multiple laps, *When* exported via `GPXFileWriter`, *Then* the GPX parses cleanly via DOM XML parser preserving unescaped text values, and `LapsDatabaseManager` is invoked `0` times.
* **AC5 (`TST-MIG-058.5`)**: *Given* `Locale.GERMANY` (`de_DE`) and a workout with `calories = 1250` (and/or `calories = null` with localized `extremaRows` `"1.250"` / `"1.250 kcal"`), *When* `HealthConnectRecordMapper.extractRecords` runs, *Then* `1250` produces exact `1250.0` kcal and `calories = null` emits zero `TotalCaloriesBurnedRecord` (never `1.25` kcal).

---

## 2. Test Specification (`TST-MIG-058`)

* **`TST-MIG-058.1` (`FitWorkoutParserTest.kt`, `FitLosslessRoundTripTest.kt`)**: Encode (a) multi-lap FIT with 3+ laps verifying exact per-record `LAP_NR` boundary assignment (including before-first-lap, exact-boundary, mid-lap, and unsorted-lap fallback); (b) FIT with foreign Connect-IQ `DeveloperDataIdMesg` + `RecordMesg` fields `num = 0..5` + `SessionMesg` fields `num = 0..7`, asserting `foundSensors` contains NO `RADAR_*` and metadata fields remain `null`/`false`; (c) verify existing `FitLosslessRoundTripTest` still passes for own `RADAR_DEVELOPER_DATA_APP_ID` and `APP_METADATA_DEVELOPER_DATA_ID`.
* **`TST-MIG-058.2` (`HrvPostWorkoutRecomputerTest.kt`)**: Verify `parseTimestampMs` across `"yyyy-MM-dd HH:mm:ss"`, `"yyyy-MM-dd'T'HH:mm:ss"`, `"yyyy-MM-dd'T'HH:mm:ss'Z'"`, `"yyyy-MM-dd_HHmmss"`, blank/malformed `null`, and DST spring-forward (`2026-03-29 02:30:00`) under `Europe/Berlin`, plus full `recomputeWorkout` parity.
* **`TST-MIG-058.3` (`GpxWorkoutParserTest.kt`)**: Verify `ParsedLap.maxSpeed` equals peak `<speed>` (e.g. `12.5` vs avg `6.0`) when `<speed>` is present, equals peak segment speed (`segDistM / dtSec`, e.g. `10.0` vs avg `7.5`) when `<speed>` is absent, and isolates lap-boundary pauses.
* **`TST-MIG-058.4` (`GPXFileWriterTest.kt`, `FileExportersIntegrationTest.kt`)**: Export workout with `"Ride & Climb <Fast> \"A\" 'B'"` name and `"Notes <1> & 2"` description across 3 laps; parse via `DocumentBuilderFactory`, assert exact DOM `<name>` & `<desc>` text content, assert `verify(exactly = 0) { LapsDatabaseManager.getInstance(any()) }`, and verify blank `workoutName` falls back to `startTime` with zero `<desc>` tag.
* **`TST-MIG-058.5` (`HealthConnectRecordMapperTest.kt`)**: Under `Locale.GERMANY`, assert (a) `calories = 1250` + German `"1.250"` extrema string yields `1250.0` kcal; (b) `calories = null` + German `"1.250"` / `"1.250 kcal"` extrema row yields zero `TotalCaloriesBurnedRecord` (never `1.25` kcal).

---

## 3. Traceability Matrix

| Requirement ID | Normative Scope | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-058.1` | FIT epoch lap matching + `APP_ID` scoping (Radar & Metadata) | `TST-MIG-058.1` | Unit + Round-Trip Test |
| `REQ-MIG-058.2` | `HrvPostWorkoutRecomputer` static `java.time` UTC parsers | `TST-MIG-058.2` | Unit + Robolectric Test |
| `REQ-MIG-058.3` | `GpxWorkoutParser` true peak `maxSpeed` (`<speed>` & GPS segment) | `TST-MIG-058.3` | Unit Test |
| `REQ-MIG-058.4` | `GPXFileWriter` XML escaping (`creator`/`<name>`/`<desc>`) + dead Lap SQL removal | `TST-MIG-058.4` | Unit + Integration Test |
| `REQ-MIG-058.5` | `HealthConnectRecordMapper` numeric-only calories (`de_DE` safe) | `TST-MIG-058.5` | Robolectric Unit Test |
