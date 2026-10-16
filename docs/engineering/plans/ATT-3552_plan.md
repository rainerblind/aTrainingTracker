# Stage 3: Implementation Plan - ATT-3552: Decompose LegacyImportEngine into Dedicated Format Parsers for TCX, GPX, and FIT

**Ticket**: [ATT-3552](https://rainerblind.atlassian.net/browse/ATT-3552)  
**Sub-task**: [ATT-3767](https://rainerblind.atlassian.net/browse/ATT-3767) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.5`  
**Active Sprint**: `Sprint 2026-42.5`  
**Requirement Mapping**: `REQ-MIG-038` (*Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade*), complementing `REQ-MIG-030`, `REQ-MIG-036`, and `REQ-MIG-037`  
**Test Mapping**: `TST-MIG-035` (*Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade Verification*)  
**Branch**: `feature/ATT-3552`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

`LegacyImportEngine.kt` is currently a 1,971-line monolithic class responsible for:
1. Orchestrating background workout imports and deduplication checks.
2. Parsing Garmin TCX XML files directly into `ParsedLap`, `ContentValues`, and `LatLng` lists.
3. Parsing GPX XML files directly into `ParsedLap`, `ContentValues`, and `LatLng` lists.
4. Parsing Garmin FIT binary files via the FIT SDK directly into `ParsedLap`, `ContentValues`, and `LatLng` lists.
5. Persisting parsed workouts into SQLite (`WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`).
6. Cloud synchronization recovery flows (Dropbox, Google Drive).

Because format parsing and database persistence are tightly coupled inside `LegacyImportEngine.kt`:
- **Code Duplication & Maintenance Burden**: `importFromTcxResult`, `importFromGpxResult`, and `importFromFitResult` each contain ~120 lines of redundant post-parsing persistence orchestration (sample table creation, transaction batching, summary insertion, bounds derivation, lap persistence, cluster evaluation, stats recalculation).
- **Single Responsibility Violation (Rule 23)**: A single engine orchestrates cloud synchronization, binary FIT decoding, streaming XML parsing, SQLite transactions, and route clustering.
- **Architectural Divergence**: Format parsing cannot be isolated, inspected, or tested independently without spinning up database mocks or complex integration harnesses.

ATT-3552 addresses this by extracting the format-specific parsing algorithms into dedicated parser classes under `migration/parsers/`, unifying the post-parsing database persistence pipeline, and preserving 100% backward compatibility of `LegacyImportEngine`'s public and internal APIs.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-038` (*Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade*)
* **Test Mapping**: `TST-MIG-035` (*Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade Verification*)
* **Living Documentation**:
  - `docs/requirements.md` (`REQ-MIG-038` specified)
  - `docs/tests.md` (`TST-MIG-035` specified)

---

## 3. System Invariants & Preserved Behavior

1. **Facade API Backward Compatibility**:
   - `LegacyImportEngine` must maintain exact signatures and behavior for:
     - `importFromTcx`, `importFromTcxResult`, `importFromTcxInternal`
     - `importFromGpx`, `importFromGpxResult`, `importFromGpxInternal`
     - `importFromFit`, `importFromFitResult`, `importFromFitInternal`
     - `bulkRecoverFromDropbox(...)`, `bulkRecoverFromGoogleDrive(...)`, `isWorkoutExisting(...)`
2. **Deduplication Invariant (`REQ-MIG-031`)**:
   - Workouts within a 3-minute same-sport temporal window must be detected as duplicates and skipped without inserting duplicate database records.
3. **Database Mapping Symmetry (Rule 20)**:
   - Sample tables must be created with `SensorType.values().toList()` columns (`ATT-602`).
   - Summary table rows must be inserted with `EQUIPMENT_ID = -1L`, `FINISHED = 1`, and consistent upload flags.
4. **Cluster Candidate Invariant (`REQ-MIG-025`)**:
   - `listener.onNewClusterCandidate` must be triggered post-import when valid trackpoints exist.
5. **Pure JVM Parsing Support**:
   - Streaming XML and FIT decoding must execute cleanly on standard JVM unit test environments.
6. **Clean-Room Full Suite Regression Invariant**:
   - 100% test pass rate across all existing unit test suites (`LegacyImportEngineFitTest`, `LegacyImportEngineGpxTest`, `TcxHighFidelityImportTest`, `LegacyImportEngineDeduplicationTest`, `LegacyImportEngineResultTest`, etc.) and `./gradlew testDebugUnitTest`.

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 Component Architecture & Package Structure

New package: `com.atrainingtracker.trainingtracker.migration.parsers`
1. **`ParsedWorkoutData.kt`**: Immutable in-memory DTO holding extracted workout data:
   - Metadata: `workoutName: String?`, `workoutNotes: String?`, `sportName: String?`, `bSportType: BSportType`, `resolvedSportId: Long`, `firstTime: String?`, `lastTime: String?`
   - Geospatial & Elevation Extrema: `points: List<LatLng>`, `altitudes: List<Double>`, `distances: List<Double>`, `minAltVal: Double`, `minAltPos: LatLng?`, `maxAltVal: Double`, `maxAltPos: LatLng?`
   - Telemetry & Laps: `parsedLaps: List<LegacyImportEngine.ParsedLap>`, `bufferedSamples: List<ContentValues>`, `foundSensors: Set<SensorType>`
2. **`TcxWorkoutParser.kt`**:
   - Extracts TCX XML parsing logic from `LegacyImportEngine.kt`.
   - Delegates XML trackpoint ingestion to `TcxTrackpointParser.parse()`.
   - Formats timestamps, derives min/max altitudes, maps sensor presence (`foundSensors`), and resolves sport type via `SportTypeDatabaseManager`.
   - Signature: `fun parse(file: File, context: Context): Result<ParsedWorkoutData>`.
3. **`GpxWorkoutParser.kt`**:
   - Extracts GPX XML parsing logic from `LegacyImportEngine.kt`.
   - Delegates XML trackpoint ingestion to `GpxTrackpointParser.parse()`.
   - Computes cumulative distances, segments, and telemetry samples.
   - Signature: `fun parse(file: File, context: Context): Result<ParsedWorkoutData>`.
4. **`FitWorkoutParser.kt`**:
   - Extracts Garmin FIT binary parsing from `LegacyImportEngine.kt`.
   - Uses `Decode.checkFileIntegrity` and `MesgBroadcaster` with `FileIdMesgListener`, `SessionMesgListener`, `LapMesgListener`, and `RecordMesgListener`.
   - Maps FIT sports and sub-sports, session statistics, and record telemetry.
   - Signature: `fun parse(file: File, context: Context): Result<ParsedWorkoutData>`.

### 4.2 LegacyImportEngine Decomposition & Persistence Unification

1. **`persistParsedWorkout(...)`**:
   - Consolidate common persistence logic into private helper:
     ```kotlin
     private fun persistParsedWorkout(
         context: Context,
         baseFileName: String,
         data: ParsedWorkoutData,
         source: String,
         uploadToStrava: Boolean,
         listener: ProgressListener?
     ): ImportResult
     ```
   - Encapsulates:
     - Mutex lock & duplicate verification (`isWorkoutExisting`).
     - Sample table creation and transactional bulk insert of `data.bufferedSamples`.
     - `WorkoutSummariesDatabaseManager` insertion.
     - `LapsDatabaseManager` batch insertion.
     - Cluster candidate notification via `listener?.onNewClusterCandidate`.
     - Non-blocking stats recalculation (`recalculateStats`).
     - Community upload scheduling (`schedulePostImportCommunityUpload`).
2. **Delegation in `importFrom*Result`**:
   - `importFromTcxResult`: Parse via `TcxWorkoutParser.parse(tcxFile, context)`, handle errors, delegate to `persistParsedWorkout(...)`.
   - `importFromGpxResult`: Parse via `GpxWorkoutParser.parse(gpxFile, context)`, handle errors, delegate to `persistParsedWorkout(...)`.
   - `importFromFitResult`: Parse via `FitWorkoutParser.parse(fitFile, context)`, handle errors, delegate to `persistParsedWorkout(...)`.
3. **De-cluttering**:
   - Remove redundant private parsing methods and duplicate persistence blocks from `LegacyImportEngine.kt`, shrinking the class from ~1,971 lines to < 1,000 lines.

---

## 5. Detailed Atomic Implementation Steps

### Step 1: Create `ParsedWorkoutData.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/ParsedWorkoutData.kt`
- Define data class with all required properties for parsed workout representation.

### Step 2: Implement `TcxWorkoutParser.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/TcxWorkoutParser.kt`
- Extract TCX parsing logic from `LegacyImportEngine.kt`.
- Map XML tags using `TcxTrackpointParser.parse(inputStream)`.
- Derive telemetry `ContentValues`, `ParsedLap`s, and geospatial bounds.

### Step 3: Implement `GpxWorkoutParser.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/GpxWorkoutParser.kt`
- Extract GPX parsing logic from `LegacyImportEngine.kt`.
- Map XML tags using `GpxTrackpointParser.parse(inputStream)`.
- Derive telemetry `ContentValues`, geodesic cumulative distances, and laps.

### Step 4: Implement `FitWorkoutParser.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/FitWorkoutParser.kt`
- Extract Garmin FIT binary SDK decoding from `LegacyImportEngine.kt`.
- Handle `Decode.checkFileIntegrity` and message listeners for session, laps, and records.

### Step 5: Refactor `LegacyImportEngine.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
- Implement `persistParsedWorkout(...)`.
- Update `importFromTcxResult`, `importFromGpxResult`, and `importFromFitResult` to delegate to dedicated parsers and `persistParsedWorkout`.
- Remove obsolete private format parsing functions.

### Step 6: Create Dedicated Parser Unit Tests
- Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/parsers/TcxWorkoutParserTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/parsers/GpxWorkoutParserTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/migration/parsers/FitWorkoutParserTest.kt`
- Verify isolated parser parsing with mock/sample input streams.

### Step 7: Clean-Room Full Suite Regression Execution
- Command: `./gradlew testDebugUnitTest`
- Ensure 100% test pass rate across all modules.

---

## 6. Review & Gate Sign-off Plan

- Stage 3 deliverable saved to `docs/engineering/plans/ATT-3552_plan.md`.
- Jira subtask `ATT-3767` updated with plan content.
- Subtask moved to `in_review`.
- Gate 3 audit executed via `tools/review_agent.py audit ATT-3767 --model gemini-3.1-flash-lite`.
- Upon passing, subtask transitioned to `Erledigt`.
