# Stage 5 Walkthrough - ATT-3552: Decompose LegacyImportEngine into Dedicated Format Parsers for TCX, GPX, and FIT

**Ticket**: [ATT-3552](https://rainerblind.atlassian.net/browse/ATT-3552)  
**Stage 5 Subtask**: [ATT-3769](https://rainerblind.atlassian.net/browse/ATT-3769) (`[Verification]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.5`  
**Active Sprint**: `Sprint 2026-42.5`  
**Requirement Mapping**: `REQ-MIG-038` (`Verified`)  
**Test Mapping**: `TST-MIG-035` (`Verified`)  
**Branch**: `feature/ATT-3552`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Implementation Details

Prior to ATT-3552, `LegacyImportEngine.kt` was a 1,972-line monolithic god-class that combined:
1. File format parsing for Garmin TCX (XML), GPX (XML), and Garmin FIT (binary).
2. Dynamic SQLite table creation (`createNewTable`) and bulk sample ingestion across variable sensor column sets.
3. Workout deduplication checks (`isWorkoutExisting`), summary insertion (`summaryDb`), and lap persistence (`LapsDatabaseManager`).
4. Route cluster candidate discovery (`listener.onNewClusterCandidate`), stats recalculation (`recalculateStats`), and background cloud upload scheduling (`schedulePostImportCommunityUpload`).
5. Cloud sync and recovery pipelines (Dropbox and Google Drive bulk recovery).

This violated:
- **Rule 23 (Code Cleanliness & Dead Code Hygiene)**: 1,972 LOC monolith with tangled parsing and persistence routines.
- **Rule 36 (Single Source of Truth & DRY)**: Nearly identical ~120-line database persistence blocks copy-pasted across `importFromTcxResult`, `importFromGpxResult`, and `importFromFitResult`.
- **Single Responsibility Principle (SRP)**: Any format specification change required modifying the central orchestration and persistence engine.

ATT-3552 decomposed `LegacyImportEngine.kt` into dedicated, modular parsers under `com.atrainingtracker.trainingtracker.migration.parsers`, unified the post-parsing database persistence pipeline into a single reusable helper `persistParsedWorkout(...)`, and preserved 100% backward compatibility of all public and internal APIs.

### Key Architectural Changes & Deliverables

1. **Structured In-Memory Domain Model (`ParsedWorkoutData.kt`)**:
   - Authored immutable data structures:
     - `ParsedWorkoutData`: encapsulates workout metadata (`workoutName`, `workoutNotes`, `sportName`, `bSportType`, `resolvedSportId`, `firstTime`, `lastTime`), bounds/extrema (`points`, `altitudes`, `distances`, `minAltVal`, `minAltPos`, `maxAltVal`, `maxAltPos`), laps (`parsedLaps`), telemetry samples (`bufferedSamples`), and active sensor types (`foundSensors`).
     - `ParsedLap`: encapsulates per-lap telemetry and timing (`startTime`, `totalTimeSeconds`, `distanceMeters`, `maxSpeedMps`, `calories`, `avgHeartRate`, `name`, `notes`).

2. **Dedicated Modular Format Parsers (`migration/parsers/`)**:
   - `TcxWorkoutParser`: Extracts Garmin TCX XML workout history, delegating trackpoint pull parsing to `TcxTrackpointParser`, resolving sport IDs via `SportTypeDatabaseManager`, computing altitude extrema, and extracting lap splits.
   - `GpxWorkoutParser`: Extracts GPX XML track history, delegating trackpoint pull parsing to `GpxTrackpointParser`, calculating geodesic cumulative distances, resolving sport IDs, and populating telemetry samples.
   - `FitWorkoutParser`: Decodes Garmin FIT binary files via `Decode.checkFileIntegrity` and `MesgBroadcaster`, handling session, lap, and record messages with sport type resolution and sensor column extraction.

3. **Unified Persistence Pipeline in `LegacyImportEngine.kt`**:
   - Extracted `persistParsedWorkout(context, baseFileName, parsedWorkout, source, uploadToStrava, listener): ImportResult`.
   - Unified mutex-locked deduplication (`importMutex`), dynamic table generation (`createNewTable`), transactional sample batch insertion, summary metadata record insertion, lap persistence, cluster candidate dispatching, background stats recalculation, and Strava community upload scheduling.
   - Reduced `LegacyImportEngine.kt` from 1,972 down to 1,283 lines (net reduction of 689 LOC, -35%).

4. **100% Backward Compatibility Preserved**:
   - All public and internal facade entry points (`importFromTcx`, `importFromTcxResult`, `importFromTcxInternal`, `importFromGpx`, `importFromGpxResult`, `importFromGpxInternal`, `importFromFit`, `importFromFitResult`, `importFromFitInternal`, `bulkRecoverFromDropbox`, `bulkRecoverFromGoogleDrive`) retained identical signatures, return contracts, and concurrency characteristics.

5. **Dedicated Unit Test Suites**:
   - Authored `TcxWorkoutParserTest.kt`, `GpxWorkoutParserTest.kt`, and `FitWorkoutParserTest.kt` validating format parsing, telemetry extraction, and sport type mapping in isolation.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-038` (*Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-MIG-035` (*Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Rule 20 (Database Mapping Symmetry)**: All columns, sample types, and database table contracts remain strictly identical across legacy and modular paths.
2. **Rule 23 (Code Cleanliness & Dead Code Hygiene)**: Removed unused XML/FIT imports from `LegacyImportEngine.kt`, eliminated compiler warnings (unnecessary safe calls and assertions).
3. **Rule 36 (Single Source of Truth & DRY)**: Single unified `persistParsedWorkout` pipeline eliminated duplicate persistence routines across TCX, GPX, and FIT.
4. **Concurrency Safety**: `importMutex` protects all database writes and deduplication checks against races.
5. **Deduplication Invariant**: 3-minute same-sport deduplication window strictly verified (`DUPLICATE_SKIPPED`).
6. **Safety Linters**: `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, and `lint_safe_enum.py` passed 100%.
7. **Clean-Room Regression Free**: Full regression test suite passed 100% on pure JVM.

---

## 4. Test Verification Results

### Unit & Regression Test Suites

| Test Suite | Description | Result |
| :--- | :--- | :--- |
| `TcxWorkoutParserTest` | Dedicated unit tests for TCX XML parser, trackpoints, laps, sport mapping, altitude extrema | **PASS** |
| `GpxWorkoutParserTest` | Dedicated unit tests for GPX XML parser, waypoints, laps, geodesic distances, sport mapping | **PASS** |
| `FitWorkoutParserTest` | Dedicated unit tests for FIT binary parser, session/lap/record decoding, sport mapping | **PASS** |
| `LegacyImportEngineResultTest` | Tests unified persistence pipeline, deduplication, and result contracts | **PASS** |
| `LegacyImportEngineFitTest` | Verifies end-to-end FIT file import through `LegacyImportEngine` facade | **PASS** |
| `LegacyImportEngineGpxTest` | Verifies end-to-end GPX file import through `LegacyImportEngine` facade | **PASS** |
| `TcxHighFidelityImportTest` | Verifies end-to-end TCX file import through `LegacyImportEngine` facade | **PASS** |
| `LegacyImportEngineDeduplicationTest` | Verifies 3-minute same-sport deduplication logic | **PASS** |
| `LegacyImportEngineDropboxTest` | Verifies cloud recovery pipeline from Dropbox | **PASS** |
| `LegacyImportEngineGoogleDriveTest` | Verifies cloud recovery pipeline from Google Drive | **PASS** |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules, build duration 4m 49s).

### Linter Verification Run
* Commands: `python3 tools/lint_cursor_safety.py`, `python3 tools/lint_lifecycle_safety.py`, `python3 tools/lint_safe_enum.py`
* Result: **100% PASS** (zero cursor leaks, zero unguarded lifecycle calls, zero unsafe enum deserializations).
