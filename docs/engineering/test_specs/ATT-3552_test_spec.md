# Stage 2: Requirement & Test Specification - ATT-3552

## Ticket Overview
- **Ticket ID**: ATT-3552
- **Subtask ID**: ATT-3766
- **Summary**: Decompose LegacyImportEngine into Dedicated Format Parsers for TCX, GPX, and FIT
- **Requirement ID**: `REQ-MIG-038`
- **Test Specification ID**: `TST-MIG-035`
- **Active Branch**: `feature/ATT-3552`

---

## 1. Formal Requirement Specification (`REQ-MIG-038`)

### 1.1 Requirement Title
**Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade**

### 1.2 Description
The system SHALL decompose the monolithic `LegacyImportEngine.kt` by extracting format-specific parsing algorithms into dedicated, single-responsibility parser components (`TcxWorkoutParser`, `GpxWorkoutParser`, `FitWorkoutParser`) under `migration/parsers/`, unifying the post-parsing database persistence pipeline, and preserving 100% backward compatibility of `LegacyImportEngine`'s public and internal APIs, enforcing Rule 20 (Database Mapping Symmetry), Rule 23 (Code Cleanliness), and Rule 36 (Single Source of Truth & DRY) (ATT-3552, complementing `REQ-MIG-030`, `REQ-MIG-036`, and `REQ-MIG-037`):

1. **Structured In-Memory Parsed Workout Model (`ParsedWorkoutData`)**:
   - The system SHALL define `ParsedWorkoutData` in `com.atrainingtracker.trainingtracker.migration.parsers` encapsulating:
     - Metadata: `workoutName: String?`, `workoutNotes: String?`, `sportName: String?`, `bSportType: BSportType`, `resolvedSportId: Long`, `firstTime: String?`, `lastTime: String?`
     - Geospatial & Elevation Extrema: `points: List<LatLng>`, `altitudes: List<Double>`, `distances: List<Double>`, `minAltVal: Double`, `minAltPos: LatLng?`, `maxAltVal: Double`, `maxAltPos: LatLng?`
     - Telemetry & Laps: `parsedLaps: List<ParsedLap>`, `bufferedSamples: List<ContentValues>`, `foundSensors: Set<SensorType>`

2. **Dedicated Modular Format Parsers (`com.atrainingtracker.trainingtracker.migration.parsers`)**:
   - `TcxWorkoutParser`: The system SHALL extract TCX XML parsing logic into `TcxWorkoutParser.parse(file: File, context: Context): Result<ParsedWorkoutData>`, delegating XML trackpoint ingestion to `TcxTrackpointParser.parse()`, mapping ISO-8601 timestamps, extracting laps, and resolving sport types via `SportTypeDatabaseManager`.
   - `GpxWorkoutParser`: The system SHALL extract GPX XML parsing logic into `GpxWorkoutParser.parse(file: File, context: Context): Result<ParsedWorkoutData>`, delegating trackpoint ingestion to `GpxTrackpointParser.parse()`, extracting laps/segments, and computing cumulative geodesic distances.
   - `FitWorkoutParser`: The system SHALL extract Garmin FIT binary parsing into `FitWorkoutParser.parse(file: File, context: Context): Result<ParsedWorkoutData>`, verifying file integrity via `Decode.checkFileIntegrity`, dispatching messages via `MesgBroadcaster`, and mapping session, lap, and record messages to telemetry samples.

3. **Unified Orchestration & Persistence Pipeline in `LegacyImportEngine`**:
   - In `LegacyImportEngine.kt`, the system SHALL consolidate redundant database persistence logic across `importFromTcxResult`, `importFromGpxResult`, and `importFromFitResult` into a single helper method `persistParsedWorkout(context, baseFileName, parsedWorkout, source, uploadToStrava, listener): ImportResult`.
   - `persistParsedWorkout` SHALL enforce:
     - Mutex-guarded deduplication via `importMutex.withLock` and `isWorkoutExisting`.
     - Sample table creation via `samplesDbManager.createNewTable(baseFileName, SensorType.values().toList())` and transactional bulk insert of `bufferedSamples`.
     - Summary table insertion in `summaryDb` with `EQUIPMENT_ID = -1L`, `FINISHED = 1`, and upload flags.
     - Lap insertion in `LapsDatabaseManager`.
     - Cumulative distance and bounds enrichment.
     - Route cluster candidate discovery via `listener.onNewClusterCandidate`.
     - Non-blocking stats recalculation via `recalculateStats`.
     - Strava upload scheduling via `schedulePostImportCommunityUpload`.

4. **100% Facade API Backward Compatibility**:
   - `LegacyImportEngine` SHALL preserve all public and internal function signatures:
     - `importFromTcx(context: Context, tcxFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportStatus`
     - `importFromTcxResult(context: Context, tcxFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportResult`
     - `importFromTcxInternal(context: Context, tcxFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportStatus`
     - `importFromGpx(context: Context, gpxFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportStatus`
     - `importFromGpxResult(context: Context, gpxFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportResult`
     - `importFromGpxInternal(context: Context, gpxFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportStatus`
     - `importFromFit(context: Context, fitFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportStatus`
     - `importFromFitResult(context: Context, fitFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportResult`
     - `importFromFitInternal(context: Context, fitFile: File, listener: ProgressListener?, uploadToStrava: Boolean): ImportStatus`
     - `bulkRecoverFromDropbox(...)` and `bulkRecoverFromGoogleDrive(...)`

5. **Preservation of System Invariants**:
   - *Deduplication Invariant*: 3-minute same-sport deduplication window (`REQ-MIG-031`) remains strictly preserved.
   - *Table Structure Invariant*: Imported sample tables must be initialized with `SensorType.values().toList()` (`ATT-602`).
   - *Cluster Candidate Invariant*: Clusters must be evaluated and emitted via `listener.onNewClusterCandidate` (`REQ-MIG-025`).
   - *Clean-Room Regression Invariant*: 100% full clean-room unit test pass rate (`./gradlew testDebugUnitTest`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new architectural requirement (`REQ-MIG-038`), refactoring and modularizing the internal implementation of `REQ-MIG-030` (GPX), `REQ-MIG-036` (FIT), and `REQ-MIG-037` (Streaming Trackpoint XML).
2. **Historical Origin & Commit Trace**: Sprints 2026-40.12 through 2026-42.3 incrementally grew `LegacyImportEngine.kt` to 1,971 lines. `routes/parsers/TrackpointModels.kt` was added in `ATT-3362`.
3. **Root Reason for Existing Formulation**: Rapid iterative additions placed format parsing directly inside the orchestration engine.
4. **Preservation of Core Invariants**: Concurrency locking (`importMutex`), cloud recovery pipelines, dynamic sample table columns, deduplication rules, and 100% unit test pass rate strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Modular Parser File Structure**:
  - *Given* package `com.atrainingtracker.trainingtracker.migration.parsers`,
  - *When* inspected,
  - *Then* `ParsedWorkoutData.kt`, `TcxWorkoutParser.kt`, `GpxWorkoutParser.kt`, and `FitWorkoutParser.kt` exist as dedicated, focused classes.

- **Scenario 2: Facade API Integrity**:
  - *Given* existing callers and unit tests interacting with `LegacyImportEngine`,
  - *When* invoking `importFromTcx`, `importFromGpx`, `importFromFit`, or their `*Result` / `*Internal` variants,
  - *Then* the method signatures, return types, and execution semantics are 100% identical.

- **Scenario 3: Parser Extraction Fidelity**:
  - *Given* valid TCX, GPX, and FIT files,
  - *When* parsed via `TcxWorkoutParser.parse()`, `GpxWorkoutParser.parse()`, and `FitWorkoutParser.parse()`,
  - *Then* they return `Result.success(ParsedWorkoutData)` containing correct sport types, laps, trackpoints, and sensor telemetry.

- **Scenario 4: Unified Persistence & Deduplication**:
  - *Given* an already-imported workout file,
  - *When* imported again through `LegacyImportEngine`,
  - *Then* `ImportStatus.DUPLICATE_SKIPPED` is returned and no duplicate SQLite rows or sample tables are created.

- **Scenario 5: Clean-Room Regression Pass**:
  - *Given* all existing migration and import tests,
  - *When* executing `./gradlew testDebugUnitTest`,
  - *Then* 100% of unit tests pass without regression.

---

## 4. Test Specification (`TST-MIG-035`)

### 4.1 Test Scope & Traceability
- **Test ID**: `TST-MIG-035`
- **Traced Requirement**: `REQ-MIG-038`
- **Test Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/parsers/TcxWorkoutParserTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/parsers/GpxWorkoutParserTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/parsers/FitWorkoutParserTest.kt`
  - Existing suite: `LegacyImportEngineFitTest.kt`, `LegacyImportEngineGpxTest.kt`, `TcxHighFidelityImportTest.kt`, `LegacyImportEngineDeduplicationTest.kt`, `LegacyImportEngineResultTest.kt`

### 4.2 Test Cases
1. `testTcxWorkoutParserExtractsMetadataAndTelemetry`:
   - Verifies `TcxWorkoutParser.parse()` correctly parses sample TCX data into `ParsedWorkoutData`.
2. `testGpxWorkoutParserExtractsTrackpointsAndDistance`:
   - Verifies `GpxWorkoutParser.parse()` correctly parses sample GPX data into `ParsedWorkoutData`.
3. `testFitWorkoutParserExtractsRecordsAndLaps`:
   - Verifies `FitWorkoutParser.parse()` correctly parses sample FIT data into `ParsedWorkoutData`.
4. `testLegacyImportEngineDelegatesCleanly`:
   - Verifies all existing 10+ import test suites continue passing with 100% success.
