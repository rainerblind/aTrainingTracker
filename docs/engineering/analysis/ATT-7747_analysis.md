# Stage 1 Analysis: ATT-7747 - Support lossless FIT round-trip for workout metadata, HRV protocol tags, and Bike Radar developer fields

**Ticket**: [ATT-7747](https://rainerblind.atlassian.net/browse/ATT-7747)  
**Sub-task**: [ATT-7821](https://rainerblind.atlassian.net/browse/ATT-7821) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7747`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Athletes exporting recorded activities from a smartphone to `.fit` files and importing them onto a secondary device (e.g. tablet for review, or replacement phone) experience data truncation across two key dimensions:
1. **Workout Metadata & Protocol Tags**: While `FitFileWriter.java` writes telemetry samples and `SessionMesg` metrics, it does not persist `workoutName`, user notes/description (`description`), assigned gear name (`equipmentName`), workout category flags (`commute`, `trainer`, `race`), or structured HRV test protocol tags (`method`, `goal`). Furthermore, `FitWorkoutParser.kt` unconditionally instantiates `workoutName = null, workoutNotes = null`.
2. **Bike Radar FIT Developer Fields Missing on Import**: `FitFileWriter.java` already defines and writes 6 FIT V2.0 Developer Fields on `RecordMesg` (`radar_current`, `radar_total`, `passing_speed`, `passing_speed_abs`, `passing_distance`, `large_vehicle_count`), but `FitWorkoutParser.kt` completely ignores `developerFields` on `RecordMesg`. Consequently, importing a `.fit` file recorded with bike radar drops all vehicle count, approach speed, passing distance, and threat telemetry.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Gap Analysis**:
  1. `FitFileWriter.java`:
     - Does not write FIT Developer Fields for session-level workout metadata onto `SessionMesg`.
     - Standard fields `sessionMesg.setNormalizedPower()`, `sessionMesg.setTrainingStressScore()`, `sessionMesg.setIntensityFactor()`, and `sessionMesg.setTotalWork()` are not populated from `WorkoutExportHeader` / `WorkoutRepository.getExtremaValue`, even though `FitWorkoutParser.kt` already parses them.
     - Does not write Developer Data ID / Field Descriptions for session metadata (`workout_name`, `workout_notes`, `equipment_name`, `method`, `goal`, `is_commute`, `is_trainer`, `is_race`).
  2. `FitWorkoutParser.kt`:
     - Explicitly ignores workout name/notes: `val workoutName: String? = null`, `val workoutNotes: String? = null`.
     - In `onMesg(mesg: Mesg)` for `RecordMesg`: does not inspect `mesg.developerFields`. As a result, radar telemetry fields written by `FitFileWriter.java` are discarded.
     - `ParsedWorkoutData.kt` lacks `method`, `isTrainer`, `isRace`, and `equipmentName` fields needed to carry imported metadata to `LegacyImportEngine.kt`.
  3. `LegacyImportEngine.kt`:
     - `importFromFitResult(...)` creates `WorkoutSummaryUpdateData`, but defaults or ignores `method`, `goal`, `isTrainer`, `isRace`, and does not look up `equipmentName` to map to existing equipment IDs.
     - Does not register `SensorType.RADAR_*` in `foundSensors` or compute radar extrema when importing FIT files.
- **Independent Peer Review & Verification**:
  - Audited `FitFileWriterRadarTest.kt`: Confirmed developer field definitions in `FitFileWriter.java` use `RADAR_DEVELOPER_DATA_ID` (16 bytes UUID) and field numbers 0 (`radar_current`), 1 (`radar_total`), 2 (`passing_speed`), 3 (`passing_speed_abs`), 4 (`passing_distance`), 5 (`large_vehicle_count`).
  - Audited `FitWorkoutParser.kt:292-310`: `mesg.developerFields` is accessible on any FIT `Mesg` (both `SessionMesg` and `RecordMesg`).
  - Audited `EquipmentDatabaseManager.kt`: `getEquipmentIdByName(name)` allows matching `equipmentName` to an existing equipment record, preserving gear assignment on import.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. **Session Metadata Developer Fields**: Define a FIT Developer Data ID for Application Metadata (`APP_METADATA_DEVELOPER_DATA_ID`) in `FitFileWriter.java` and write developer fields on `SessionMesg`:
     - Field 0: `workout_name` (String)
     - Field 1: `notes` / `description` (String)
     - Field 2: `equipment_name` (String)
     - Field 3: `method` (String)
     - Field 4: `goal` (String)
     - Field 5: `is_commute` (Uint8: 0/1)
     - Field 6: `is_trainer` (Uint8: 0/1)
     - Field 7: `is_race` (Uint8: 0/1)
  2. **Standard FIT Power/Work Metrics**: In `FitFileWriter.java`, populate `sessionMesg.setNormalizedPower()`, `sessionMesg.setTrainingStressScore()`, `sessionMesg.setIntensityFactor()`, and `sessionMesg.setTotalWork()` from `WorkoutExportHeader` or workout extrema.
  3. **Lossless Parsing in `FitWorkoutParser.kt`**:
     - Extract `SessionMesg` developer fields into `ParsedWorkoutData` (`workoutName`, `workoutNotes`, `equipmentName`, `method`, `goal`, `isCommute`, `isTrainer`, `isRace`).
     - Extract `RecordMesg` developer fields for radar metrics:
       - Field 0 (`radar_current`) -> `SensorType.RADAR_THREAT_COUNT`
       - Field 1 (`radar_total`) -> `SensorType.RADAR_VEHICLE_COUNT`
       - Field 2 (`passing_speed`) -> `SensorType.RADAR_APPROACH_SPEED_mps`
       - Field 4 (`passing_distance`) -> `SensorType.RADAR_PASSING_DISTANCE_m`
       - Field 5 (`large_vehicle_count`) -> `SensorType.RADAR_LARGE_VEHICLE_COUNT`
  4. **Persistence in `LegacyImportEngine.kt`**:
     - Update `importFromFitResult` to persist `workoutName`, `workoutNotes`, resolve `equipmentName` via `EquipmentDatabaseManager`, persist flags (`isCommute`, `isTrainer`, `isRace`), `method`, and `goal`.
     - Register radar sensors in `foundSensors` and calculate radar extrema.
* **Out-of-Scope Non-Goals**:
  - Auto-import background scheduling / UI (covered by `ATT-7748`).
  - Modifying non-FIT formats (GPX / TCX) which have distinct schema constraints.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-MIG-053` extending `REQ-MIG-038` (*FIT Import Telemetry*), `REQ-DAT-042` (*Workout Metadata Persistence*), and `REQ-EXP-042` (*FIT Export Radar Developer Fields*).
* **Historical Origin & Commit Trace**:
  - `FitFileWriterRadarTest.kt` added developer fields for radar export in Sprint 2026-42.
  - `FitWorkoutParser.kt` was initially implemented for raw GPS/sensor telemetry from third-party head units (Garmin/Wahoo), so developer fields and app-specific metadata were omitted as non-standard.
* **Root Reason for Existing Formulation**: Third-party FIT files do not follow uniform developer field conventions. The parser only extracted standard Garmin profile fields.
* **Preservation of Core Invariants**:
  - Parsing third-party FIT files (Garmin, Wahoo, Zwift) without developer fields MUST continue to work without null-pointer exceptions or default value errors.
  - Developer field reading is strictly additive: only recognized developer data IDs / field names are mapped.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-MIG-053` (`REQ-MIG-053.1`–`REQ-MIG-053.4`).
* **Affected Architecture Elements**:
  - `FitFileWriter.java`
  - `FitWorkoutParser.kt`
  - `ParsedWorkoutData.kt`
  - `LegacyImportEngine.kt`
* **Verification Impact & Test Surface**: Net-new `TST-MIG-051` (`FitLosslessRoundTripTest`, verifying round-trip of metadata, flags, HRV tags, and radar fields).
* **Risk Assessment & Invariants**: Low risk. Isolated to FIT export/import pipeline. Third-party FIT parsing backward compatibility preserved.

---

## 6. Architectural Strategy & High-Level Solution
- **FitFileWriter Developer Fields**:
  - Define `APP_METADATA_DEVELOPER_DATA_ID = { 0x54, 0x72, 0x61, 0x69, 0x6e, 0x69, 0x6e, 0x67, 0x54, 0x72, 0x61, 0x63, 0x6b, 0x65, 0x72, 0x01 }` ("TrainingTracker1").
  - Register `FieldDescriptionMesg` definitions for `workout_name` (string), `notes` (string), `equipment_name` (string), `method` (string), `goal` (string), `is_commute` (uint8), `is_trainer` (uint8), `is_race` (uint8).
  - Write standard FIT power/stress metrics to `SessionMesg` (`normalizedPower`, `trainingStressScore`, `intensityFactor`, `totalWork`).
- **FitWorkoutParser Extraction**:
  - In `onMesg(mesg: Mesg)` for `SessionMesg`: inspect developer fields matching app metadata and populate `ParsedWorkoutData`.
  - In `onMesg(mesg: Mesg)` for `RecordMesg`: inspect developer fields matching radar field numbers or field names and write them into `bufferedSamples` ContentValues and `foundSensors`.
- **LegacyImportEngine Persistence**:
  - Store metadata, flags, and HRV tags in `WorkoutSummaryUpdateData` and persist in database.
  - Map `equipmentName` to `equipmentId` via `EquipmentDatabaseManager`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. FIT Standard Compliance: All developer fields must conform strictly to Garmin FIT SDK V2.0 Developer Data ID & Field Description specifications.
  2. Backward Compatibility: Parsing FIT files lacking developer fields must succeed with clean null/default fallbacks.
  3. Lossless Round-Trip: Exporting an activity with metadata and radar telemetry to `.fit` and re-importing it produces an identical activity profile.
* **Risk Rating**: **LOW** (strictly additive schema and parser enhancements with zero live-recording impact).
