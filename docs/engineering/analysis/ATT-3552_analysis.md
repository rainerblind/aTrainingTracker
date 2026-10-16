# Stage 1: Problem Domain & Root Cause Analysis (RCA) - ATT-3552

## Ticket Overview
- **Ticket ID**: ATT-3552
- **Subtask ID**: ATT-3765
- **Summary**: Decompose LegacyImportEngine into Dedicated Format Parsers for TCX, GPX, and FIT
- **Type**: Improvement (Verbesserung)
- **Component**: Workout Import Architecture / Migration Subsystem (Epic ATT-431)
- **Sprint**: 2026-42.5
- **Active Branch**: `feature/ATT-3552`

---

## 1. Problem Domain & Forensic Root Cause Analysis

### 1.1 Technical Background & Context
`LegacyImportEngine.kt` is currently the single largest monolithic file in the codebase at **1,971 lines of code**. It conflates several unrelated architectural responsibilities into a single object:
1. **TCX XML Stream Ingestion & Telemetry Mapping**: Unpacks XML tags (`<Trackpoint>`, `<HeartRateBpm>`, `<Cadence>`, `<RunCadence>`, `<Watts>`, `<Speed>`, `<AltitudeMeters>`), converts lap timestamps, maps sport types, and constructs sample rows.
2. **GPX XML Stream Ingestion & Telemetry Mapping**: Unpacks GPX tracks (`<trk>`), routes (`<rte>`), Garmin TrackPointExtension (`gpxtpx`), waypoints, and calculates geodesic cumulative distances.
3. **Garmin FIT Binary Decoding & Message Dispatch**: Interfaces with the Garmin FIT SDK (`com.garmin.fit.*`), configures `Decode`, `MesgBroadcaster`, and registers listeners (`FileIdMesgListener`, `SessionMesgListener`, `LapMesgListener`, `RecordMesgListener`), converting semicircles to degrees, scaling altitudes, and mapping FIT sport IDs.
4. **Cloud Storage Orchestration**: Manages recursive folder traversal, pagination, and multi-tier network retries for Dropbox (`bulkRecoverFromDropbox`) and Google Drive (`bulkRecoverFromGoogleDrive`).
5. **Database Transaction Pipeline & Deduplication**: Manages SQLite transactions across `WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, and `LapsDatabaseManager`, multi-dimensional deduplication (`isWorkoutExisting`), route cluster candidate evaluation, and background stats calculation (`recalculateStats`).

### 1.2 Forensic Audit of Redundancy and Architectural Violations
- **Violation of Single Responsibility Principle (SRP)**: Any change or extension to a single file format (such as adding FIT `HrvMesg` or developer data fields) requires editing this 2,000-line monolith, increasing risk of merge conflicts and accidental regression in cloud sync or other formats.
- **Duplicated Persistence Pipelines (DRY Violation, Rule 36)**:
  - In `importFromTcxResult` (lines 641–768),
  - In `importFromGpxResult` (lines 980–1116),
  - In `importFromFitResult` (lines 1280–1455),
  the post-parsing database insertion logic is replicated across ~120 lines in each method:
  1. `samplesDbManager.createNewTable(baseFileName, SensorType.values().toList())` and batch inserting `bufferedSamples` inside a transaction.
  2. Summary row insertion into `summaryDb` with sport metadata, `EQUIPMENT_ID = -1L`, and `FINISHED = 1`.
  3. Lap row insertion into `LapsDatabaseManager`.
  4. Bounds and distance metrics extraction.
  5. Route cluster candidate inspection (`onNewClusterCandidate`).
  6. Background stats recalculation (`recalculateStats`).
  7. Post-import Strava upload queuing (`schedulePostImportCommunityUpload`).

Consolidating this persistence pipeline into a unified `persistParsedWorkout` method and moving the format-specific parsing into dedicated classes (`TcxWorkoutParser`, `GpxWorkoutParser`, `FitWorkoutParser`) under `migration/parsers/` eliminates ~250 lines of duplicate orchestration code and cleanly modularizes the import architecture.

---

## 2. Architectural Invariants & Chesterton's Fence Analysis

### 2.1 Chesterton's Fence Archaeology
- **Historical Origin & Evolution**:
  - `LegacyImportEngine` originated in Sprint 2026-40.12 to recover legacy workout backups from Dropbox (`ATT-357`).
  - Sprint 2026-41.2: Added GPX workout import support (`ATT-1827`, `REQ-MIG-030`).
  - Sprint 2026-42.1: Added FIT workout import support (`ATT-2310`, `REQ-MIG-036`).
  - Sprint 2026-42.3: Ticket `ATT-3362` (`REQ-MIG-037`) consolidated low-level XML trackpoint pull parsing into `routes/parsers/TcxTrackpointParser.kt` and `routes/parsers/GpxTrackpointParser.kt`.
- **Why Was the Monolith Retained?**: During rapid iterative sprints adding format capabilities, format-specific ingestion methods were appended directly onto `LegacyImportEngine` to quickly satisfy new user stories.
- **Why Is It Safe to Decompose Now?**:
  1. The underlying XML trackpoint parsing engines (`TcxTrackpointParser`, `GpxTrackpointParser`) and FIT SDK integrations are mature, proven, and backed by comprehensive unit tests.
  2. By preserving the exact public and internal signatures of `LegacyImportEngine` as an orchestration facade, all external consumers (`BackupRestoreViewModel`, `ImportBackupTabsScreen`) and existing unit tests (`LegacyImportEngineFitTest`, `LegacyImportEngineGpxTest`, `TcxHighFidelityImportTest`, etc.) remain 100% binary and behavioral compatible.

### 2.2 Preservation of Core Invariants
1. **Public API Backward Compatibility**:
   - `LegacyImportEngine.importFromTcx`, `importFromTcxResult`, `importFromTcxInternal`
   - `LegacyImportEngine.importFromGpx`, `importFromGpxResult`, `importFromGpxInternal`
   - `LegacyImportEngine.importFromFit`, `importFromFitResult`, `importFromFitInternal`
   - `LegacyImportEngine.bulkRecoverFromDropbox`, `bulkRecoverFromGoogleDrive`, `isWorkoutExisting`
   All must remain present with identical signatures, return types, and coroutine contexts.
2. **Concurrency & Deduplication Invariant**:
   - Multi-dimensional deduplication (`isWorkoutExisting`) and table insertion MUST remain guarded by `importMutex.withLock` to prevent database race conditions (`REQ-MIG-031`).
3. **Database Schema & Sample Table Creation Invariant**:
   - Sample tables for imported workouts must be created with `SensorType.values().toList()` to ensure LAP_NR and standard telemetry columns are present (`ATT-602`).
4. **Cluster Candidate Invariant**:
   - Clusters must be evaluated and emitted via `listener.onNewClusterCandidate` (`REQ-MIG-025`).
5. **Clean-Room Test Pass Invariant**:
   - 100% test pass rate across the entire test suite (`./gradlew testDebugUnitTest`).

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Deliverables
1. **New Format Parsers (`com.atrainingtracker.trainingtracker.migration.parsers`)**:
   - `ParsedWorkoutData.kt`: Data transfer object representing parsed workout metadata, telemetry points, laps, and sensor samples.
   - `TcxWorkoutParser.kt`: Encapsulates TCX parsing, lap conversion, and telemetry extraction.
   - `GpxWorkoutParser.kt`: Encapsulates GPX parsing, waypoint/track handling, and distance calculations.
   - `FitWorkoutParser.kt`: Encapsulates FIT binary SDK decoding, message listeners, and record conversion.
2. **Refactored `LegacyImportEngine.kt` Facade**:
   - Extracts duplicate post-parsing persistence into a unified `persistParsedWorkout` method.
   - Delegates format ingestion in `importFromTcxResult`, `importFromGpxResult`, and `importFromFitResult` to the dedicated parsers.
3. **Dedicated Parser Unit Tests**:
   - `TcxWorkoutParserTest.kt`
   - `GpxWorkoutParserTest.kt`
   - `FitWorkoutParserTest.kt`

### 3.2 Out-of-Scope (To Prevent Scope Creep)
- **Modifying Cloud Recovery Logic**: Do NOT alter `bulkRecoverFromDropbox` or `bulkRecoverFromGoogleDrive` network logic or folder pagination.
- **Introducing New FIT Mesg Types**: FIT `HrvMesg` support is tracked for a separate feature ticket; this ticket strictly performs structural decomposition of existing capabilities.
- **UI Screen Refactoring**: Do NOT modify `ImportBackupTabsScreen.kt` or `BackupRestoreFragment.kt`.

---

## 4. Verification & Testing Strategy

1. **Targeted Parser Unit Tests**:
   - Test `TcxWorkoutParser` with sample TCX files and assert proper extraction of telemetry, laps, and metadata.
   - Test `GpxWorkoutParser` with sample GPX files and assert trackpoints and distance calculations.
   - Test `FitWorkoutParser` with sample FIT files and assert decoding, laps, and records.
2. **Existing Regression Suite for Import & Migration**:
   - Execute all existing import tests:
     `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*"`
   - Assert 100% pass rate.
3. **Full Clean-Room Suite Verification**:
   - Execute `./gradlew testDebugUnitTest` across the entire project.
