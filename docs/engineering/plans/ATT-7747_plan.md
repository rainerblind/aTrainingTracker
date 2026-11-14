# Stage 3: Implementation Plan - ATT-7747: Support lossless FIT round-trip for workout metadata, HRV protocol tags, and Bike Radar developer fields

**Ticket**: [ATT-7747](https://rainerblind.atlassian.net/browse/ATT-7747)  
**Sub-task**: [ATT-7823](https://rainerblind.atlassian.net/browse/ATT-7823) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-MIG-053`  
**Test Mapping**: `TST-MIG-051`  
**Branch**: `feat/ATT-7747`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
Athletes exporting recorded activities from their phone to `.fit` and importing them onto a secondary device or tablet experience metadata truncation: workout title, notes, equipment assignment, commute/trainer/race flags, and HRV test protocol tags (`method`/`goal`) are dropped because they are not encoded into the FIT file and `FitWorkoutParser` hardcodes nulls. Additionally, while `FitFileWriter` writes Bike Radar developer fields on `RecordMesg`, `FitWorkoutParser` does not read developer fields on import, dropping radar vehicle counts, passing speeds, and threat telemetry.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-MIG-053` (*Lossless FIT Round-Trip for Workout Metadata, HRV Protocol Tags & Bike Radar Developer Fields*)
* **Test Mapping**: `TST-MIG-051` (*Lossless FIT Workout Metadata, HRV Protocol & Bike Radar Round-Trip Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Garmin FIT SDK Specification**: All developer fields must conform strictly to FIT SDK V2.0 Developer Data ID & Field Description specifications.
2. **Backward Compatibility**: Third-party FIT files (Garmin, Wahoo, Zwift) lacking developer fields MUST continue to parse cleanly without null-pointer exceptions or missing field errors.
3. **Zero Telemetry Loss**: All 1-Hz GPS, sensor, HRV RR intervals, and Bike Radar developer fields must round-trip with 100% mathematical fidelity.
4. **Code Lock Until Gate 3**: No production code modifications prior to Gate 3 PASS verification (`jira_util.py check-gate`).

---

## 4. Proposed Architectural Changes

### Component 1: `ParsedWorkoutData.kt` DTO Enhancement
- Add missing workout attributes to `ParsedWorkoutData`:
  - `val method: String? = null`
  - `val isTrainer: Boolean = false`
  - `val isRace: Boolean = false`
  - `val equipmentName: String? = null`

### Component 2: `FitFileWriter.java` Session Metadata Developer Fields & Metrics
- Define `APP_METADATA_DEVELOPER_DATA_ID = { 0x54, 0x72, 0x61, 0x69, 0x6e, 0x69, 0x6e, 0x67, 0x54, 0x72, 0x61, 0x63, 0x6b, 0x65, 0x72, 0x01 }` ("TrainingTracker1").
- Register and write Developer Data ID and Field Description messages for `SessionMesg`:
  - Field 0: `workout_name` (String, FIT_BASE_TYPE_STRING)
  - Field 1: `notes` (String, FIT_BASE_TYPE_STRING)
  - Field 2: `equipment_name` (String, FIT_BASE_TYPE_STRING)
  - Field 3: `method` (String, FIT_BASE_TYPE_STRING)
  - Field 4: `goal` (String, FIT_BASE_TYPE_STRING)
  - Field 5: `is_commute` (Uint8, FIT_BASE_TYPE_UINT8)
  - Field 6: `is_trainer` (Uint8, FIT_BASE_TYPE_UINT8)
  - Field 7: `is_race` (Uint8, FIT_BASE_TYPE_UINT8)
- Populate standard FIT session metrics from `WorkoutExportHeader`:
  - `sessionMesg.setNormalizedPower((float) np)`
  - `sessionMesg.setIntensityFactor((float) ifVal)`
  - `sessionMesg.setTrainingStressScore((float) tss)`
  - `sessionMesg.setTotalWork((long) (workKj * 1000.0))`

### Component 3: `FitWorkoutParser.kt` Developer Field Extraction
- On `SessionMesg`:
  - Inspect `mesg.developerFields`. Match field names (`workout_name`, `notes`, `equipment_name`, `method`, `goal`, `is_commute`, `is_trainer`, `is_race`) or developer field numbers (0–7) under `APP_METADATA_DEVELOPER_DATA_ID`.
  - Extract and populate `workoutName`, `workoutNotes`, `equipmentName`, `method`, `goal`, `isCommute`, `isTrainer`, and `isRace`.
- On `RecordMesg`:
  - Inspect `mesg.developerFields` matching `radar_current`, `radar_total`, `passing_speed`, `passing_distance`, `large_vehicle_count` (or radar developer field numbers 0, 1, 2, 4, 5).
  - Write extracted values into `bufferedSamples` ContentValues:
    - 0 -> `SensorType.RADAR_THREAT_COUNT.dbColumnName`
    - 1 -> `SensorType.RADAR_VEHICLE_COUNT.dbColumnName`
    - 2 -> `SensorType.RADAR_APPROACH_SPEED_mps.dbColumnName`
    - 4 -> `SensorType.RADAR_PASSING_DISTANCE_m.dbColumnName`
    - 5 -> `SensorType.RADAR_LARGE_VEHICLE_COUNT.dbColumnName`
  - Register the corresponding `SensorType` in `foundSensors`.

### Component 4: `LegacyImportEngine.kt` Persistence & Equipment Resolution
- In `importFromFitResult`:
  - Populate `WorkoutSummaryUpdateData` with `workoutName`, `description`, `commute`, `trainer`, `race`, `method`, `goal`.
  - If `parsedData.equipmentName` is present and `equipmentId <= 0`:
    - Query `EquipmentDatabaseManager.getEquipmentIdByName(context, parsedData.equipmentName)` to link the existing equipment record.
  - Ensure `foundSensors` containing `SensorType.RADAR_*` are persisted and radar extrema computed.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: DTO Field Extension
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/ParsedWorkoutData.kt`
* Changes: Add `method`, `isTrainer`, `isRace`, `equipmentName`.

### Step 2: FIT File Writer Session Developer Fields
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java`
* Changes: Add `APP_METADATA_DEVELOPER_DATA_ID`, developer field descriptions on `SessionMesg`, and standard session power/stress setters.

### Step 3: FIT Workout Parser Developer Field Decoding
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/FitWorkoutParser.kt`
* Changes: Decode `SessionMesg` metadata developer fields and `RecordMesg` Bike Radar developer fields into `bufferedSamples` and `foundSensors`.

### Step 4: Import Engine Equipment Resolution & Persistence
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* Changes: Link `equipmentName` to equipment database, persist metadata and flags in `WorkoutSummaryUpdateData`.

### Step 5: Unit & Round-Trip Test Suite
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/FitLosslessRoundTripTest.kt`
* Changes: Verify `TST-MIG-051` test cases (metadata round-trip, power metrics, radar developer fields, equipment matching, and backward compatibility).
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.FitLosslessRoundTripTest"`

---

## 6. Verification & Invariants Check
* Full regression run across `migration.*` and `exporter.*`:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.exporter.*"`
* Living doc verification: `python3 tools/verify_requirement_governance.py --sync-indexes`
