# Stage 2: Requirement & Test Specification - ATT-7747: Support lossless FIT round-trip for workout metadata, HRV protocol tags, and Bike Radar developer fields

**Ticket**: [ATT-7747](https://rainerblind.atlassian.net/browse/ATT-7747)  
**Sub-task**: [ATT-7822](https://rainerblind.atlassian.net/browse/ATT-7822) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-MIG-053` (*Lossless FIT Round-Trip for Workout Metadata, HRV Protocol Tags & Bike Radar Developer Fields*)  
**Test Spec ID**: `TST-MIG-051`  
**Branch**: `feat/ATT-7747`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-MIG-053)

### 1.1 Problem Statement & Rationale
When workouts recorded on a smartphone are exported to `.fit` and imported onto a secondary device (e.g. tablet or replacement phone), workout title, notes, gear assignment, commute/trainer/race flags, HRV protocol tags, and Bike Radar metrics must round-trip with 100% fidelity without loss or corruption.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **FIT Session Metadata Developer Fields (`FitFileWriter.java`)**: The system SHALL define `APP_METADATA_DEVELOPER_DATA_ID` and register/write developer fields onto `SessionMesg`: `workout_name` (string), `notes` (string), `equipment_name` (string), `method` (string), `goal` (string), `is_commute` (uint8), `is_trainer` (uint8), and `is_race` (uint8).
2. **Standard FIT Session Metrics Serialization (`FitFileWriter.java`)**: The system SHALL populate `sessionMesg.setNormalizedPower()`, `sessionMesg.setTrainingStressScore()`, `sessionMesg.setIntensityFactor()`, and `sessionMesg.setTotalWork()` from workout summary data when present.
3. **Session Metadata Developer Field Parsing (`FitWorkoutParser.kt`)**: The parser SHALL decode `workoutName`, `workoutNotes`, `equipmentName`, `method`, `goal`, `isCommute`, `isTrainer`, and `isRace` from `SessionMesg` developer fields into `ParsedWorkoutData`.
4. **Bike Radar Developer Field Ingestion (`FitWorkoutParser.kt`)**: The parser SHALL decode Garmin FIT V2.0 Developer Fields from `RecordMesg` into `bufferedSamples` and `foundSensors` for `radar_current` (0 -> `SensorType.RADAR_THREAT_COUNT`), `radar_total` (1 -> `SensorType.RADAR_VEHICLE_COUNT`), `passing_speed` (2 -> `SensorType.RADAR_APPROACH_SPEED_mps`), `passing_distance` (4 -> `SensorType.RADAR_PASSING_DISTANCE_m`), and `large_vehicle_count` (5 -> `SensorType.RADAR_LARGE_VEHICLE_COUNT`).
5. **Persistence & Equipment Resolution (`LegacyImportEngine.kt`)**: The system SHALL persist `workoutName`, `workoutNotes`, match `equipmentName` against `EquipmentDatabaseManager` to resolve `equipmentId`, persist `isCommute`, `isTrainer`, `isRace`, `method`, and `goal`, register radar sensors in `foundSensors`, and calculate radar extrema.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Metadata Round-Trip)**:
  * *Given* a workout recorded with title, description, assigned gear, commute/trainer/race flags, and HRV protocol tags,
  * *When* exported to `.fit` via `FitFileWriter` and parsed via `FitWorkoutParser`,
  * *Then* `ParsedWorkoutData` contains exact matching metadata, flags, and HRV tags.
* **Criterion 2 (Bike Radar Developer Fields)**:
  * *Given* a workout with Bike Radar developer telemetry,
  * *When* exported to `.fit` and parsed by `FitWorkoutParser`,
  * *Then* `bufferedSamples` contains all radar sample values and `foundSensors` contains the corresponding radar `SensorType` entries.
* **Criterion 3 (Third-Party FIT Backward Compatibility)**:
  * *Given* a third-party `.fit` file lacking developer fields,
  * *When* parsed by `FitWorkoutParser`,
  * *Then* the parser completes successfully without exceptions and populates standard telemetry with null metadata fallbacks.

### 1.5 System Invariants
- FIT SDK V2.0 Developer Data ID & Field Description specification compliance.
- Third-party FIT files without developer fields parse cleanly with zero regression.
- Thread-safe parsing and deterministic data extraction.

---

## 2. Test Specification (TST-MIG-051)

### Test Case 1: FIT Session Metadata Round-Trip (`TST-MIG-051.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/FitLosslessRoundTripTest.kt`
* **Preconditions**: Workout with title "Morning Ride", notes "Felt good", equipment "Canyon Aeroad", commute=true, trainer=false, race=false, method="Ramp", goal="FTP".
* **Action**: Export via `FitFileWriter` to byte array, parse via `FitWorkoutParser`.
* **Expected Result**: Assert `ParsedWorkoutData` matches all metadata values exactly.

### Test Case 2: Standard FIT Session Metrics Serialization (`TST-MIG-051.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/FitLosslessRoundTripTest.kt`
* **Preconditions**: Workout with NP=250W, IF=0.85, TSS=65, Work=750kJ.
* **Action**: Export via `FitFileWriter`, parse via `FitWorkoutParser`.
* **Expected Result**: Assert `sessionNormalizedPowerWatts`, `sessionIntensityFactor`, `sessionTss`, `sessionWorkKj` match exported metrics.

### Test Case 3: Bike Radar Developer Fields Ingestion (`TST-MIG-051.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/FitLosslessRoundTripTest.kt`
* **Preconditions**: Telemetry samples with radar threat count, vehicle count, passing speed, passing distance, and large vehicle count.
* **Action**: Export via `FitFileWriter`, parse via `FitWorkoutParser`.
* **Expected Result**: Assert `bufferedSamples` contains radar columns and `foundSensors` contains all radar sensor types.

### Test Case 4: Equipment Resolution & Persistence Contract (`TST-MIG-051.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/FitLosslessRoundTripTest.kt`
* **Preconditions**: Mock `EquipmentDatabaseManager` with gear "Canyon Aeroad" (id 42).
* **Action**: Simulate `LegacyImportEngine` processing of `ParsedWorkoutData`.
* **Expected Result**: Equipment name is resolved to id 42, and all flags are persisted.

### Test Case 5: Third-Party FIT Backward Compatibility (`TST-MIG-051.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/FitLosslessRoundTripTest.kt`
* **Preconditions**: Third-party FIT file without developer fields.
* **Action**: Parse via `FitWorkoutParser`.
* **Expected Result**: Parsing succeeds without exceptions; `workoutName` and `workoutNotes` are null; radar sensors are not added.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MIG-051.1` | Unit | `FitFileWriter` + `FitWorkoutParser` Metadata Round-Trip | `REQ-MIG-053.1`, `053.3` | Specified |
| `TST-MIG-051.2` | Unit | `FitFileWriter` + `FitWorkoutParser` Power/Stress Metrics | `REQ-MIG-053.2`, `053.3` | Specified |
| `TST-MIG-051.3` | Unit | `FitWorkoutParser` Bike Radar Developer Field Decoding | `REQ-MIG-053.4` | Specified |
| `TST-MIG-051.4` | Unit | `LegacyImportEngine` Equipment Matching & Persistence | `REQ-MIG-053.5` | Specified |
| `TST-MIG-051.5` | Unit | `FitWorkoutParser` Third-Party Backward Compatibility | `REQ-MIG-053.3`, `053.4` | Specified |
