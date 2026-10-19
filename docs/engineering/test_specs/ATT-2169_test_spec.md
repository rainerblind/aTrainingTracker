# Stage 2: Requirement & Test Specification - ATT-2169: [Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity

**Ticket**: [ATT-2169](https://rainerblind.atlassian.net/browse/ATT-2169)  
**Sub-task**: [ATT-4102](https://rainerblind.atlassian.net/browse/ATT-4102) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-027` (*GoldenCheetah JSON File Export Format and Data Integrity Verification*)  
**Test Spec ID**: `TST-DAT-022`  
**Branch**: `feature/ATT-2169`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (REQ-DAT-027)

### 1.1 Problem Statement & Rationale
GoldenCheetah JSON export (`GCFileWriter.java`) allows athletes to analyze workouts in GoldenCheetah desktop software. The existing implementation formatted numeric metrics into string literals using device-default locale (`String.format(Locale.getDefault(), ...)`), causing GoldenCheetah's Bison parser (`JsonRideFile.y`) to fail on decimal commas in non-US locales and reject string-typed metrics. Furthermore, GPS validation checked `LATITUDE` twice leaving `LONGITUDE` unvalidated, longitude was read as `Float` losing coordinate precision, workout laps/intervals were not exported to the `"INTERVALS"` block, and file/cursor resources were not safely guarded against leaks.

### 1.2 Functional & Architectural Requirements
The system SHALL modernize and harden `GCFileWriter.java` conforming to the GoldenCheetah Ride JSON specification:
1. **Numeric Telemetry Primitives (Locale-Independent)**:
   - All sample telemetry metrics in GoldenCheetah JSON export (`KM`, `KPH`, `WATTS`, `CAD`, `NM`, `ALT`, `LRBALANCE`, `LTE`, `RTE`, `LPS`, `RPS`, `LAT`, `LON`) SHALL be serialized into the JSON `SAMPLES` array as raw JSON numeric literals (`Double` or `Int`).
   - String formatting with `Locale.getDefault()` and decimal commas (e.g. `"12,345"`) SHALL be completely eliminated.
   - `SECS` and `HR` SHALL be serialized as JSON integers.
2. **GPS Sensor Validation and Double Precision**:
   - Telemetry validation for GPS samples SHALL validate both `SensorType.LATITUDE.name()` and `SensorType.LONGITUDE.name()` via `dataValid(...)`.
   - Longitude coordinate extraction SHALL use `cursor.getDouble(...)`, matching latitude and preserving full 64-bit IEEE 754 precision.
3. **Workout Laps / INTERVALS Block**:
   - When recorded laps exist for the workout (`LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)`), `GCFileWriter` SHALL serialize an `"INTERVALS"` JSON array nested under `"RIDE"`.
   - Each interval item SHALL contain `NAME` (string, e.g. `"Lap 1"`), `START` (elapsed seconds integer), and `STOP` (elapsed seconds integer).
   - If no laps exist, the `"INTERVALS"` block MAY be omitted or empty, preserving valid GoldenCheetah schema structure.
4. **Metadata Tags Alignment**:
   - The `"TAGS"` block SHALL populate:
     - `"Sport"`: mapped via `SportTypeDatabaseManager.getInstance(mContext).getGcName(sportTypeId)`.
     - `"Workout Code"`: `(goal + " " + method).trim()`.
     - `"Workout Title"`: `workoutName` (when present and non-empty).
     - `"Notes"`: `description` (when present and non-empty).
     - `"Device"`: `TrainingApplication.getAppName()`.
     - `"Data"`: sensor capabilities string (`data`).
5. **Thread-Safe, UTC-Anchored Start Time**:
   - `STARTTIME` conversion SHALL execute using thread-safe, method-local or synchronized date formatters configured with `TimeZone.getTimeZone("UTC")` and `Locale.US`, generating `"yyyy/MM/dd HH:mm:ss UTC"`.
6. **Resource Safety**:
   - `BufferedWriter` and SQLite `Cursor` SHALL be managed within `try-with-resources` blocks to ensure unconditional cleanup upon normal exit or exceptional failure.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Valid Numeric JSON & Locale Independence)**:
  * *Given* a workout recorded on a device operating in German locale (`Locale.GERMANY`)
  * *When* exported to GoldenCheetah JSON via `GCFileWriter`
  * *Then* the output parses cleanly as standard JSON, telemetry values (`KM`, `KPH`, `WATTS`, `CAD`, `ALT`, `LAT`, `LON`) are JSON numbers (not strings), and no decimal commas appear anywhere in the document.
* **Criterion 2 (GPS Validation and Precision)**:
  * *Given* sample data with GPS coordinates
  * *When* exported to GoldenCheetah JSON
  * *Then* both `LATITUDE` and `LONGITUDE` are checked for validity, and longitude retains identical double-precision fidelity as stored in SQLite.
* **Criterion 3 (Intervals / Laps Export)**:
  * *Given* a workout with 2 or more recorded laps in `LapsDatabaseManager`
  * *When* exported to GoldenCheetah JSON
  * *Then* the JSON contains an `"INTERVALS"` array with exact start/stop seconds and lap titles matching the recorded laps.
* **Criterion 4 (Metadata and Tags Integrity)**:
  * *Given* a workout with title, description, and sport type
  * *When* exported to GoldenCheetah JSON
  * *Then* the `"TAGS"` object contains `"Sport"`, `"Workout Title"`, `"Notes"`, `"Workout Code"`, and `"Device"`.
* **Criterion 5 (Resource and Concurrency Safety)**:
  * *Given* an export triggered concurrently or interrupted by an error
  * *When* executing `doExport`
  * *Then* date formatting operates without thread collision or timezone drift, and file writer/cursor streams are safely closed.

### 1.4 System Invariants
1. GoldenCheetah export file extension remains `.json`.
2. Existing file exporters (`FitFileWriter`, `TCXFileWriter`, `GPXFileWriter`) remain untouched.
3. 100% test pass rate across the full clean-room unit test suite (`./gradlew testDebugUnitTest`).

---

## 2. Test Specification (TST-DAT-022)

### Test Case 1: `testGCFileWriter_validNumericJson_and_localeIndependence` (`TST-DAT-022.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriterTest.kt`
* **Preconditions**: Mock database cursor returning sample rows with KM, KPH, WATTS, HR, CAD, ALT. Device default locale set to `Locale.GERMANY`.
* **Action**: Execute `GCFileWriter.doExport(exportInfo)`.
* **Expected Result**: Output file is valid JSON (`JSONObject(content)` succeeds). `SAMPLES` array entries contain numeric types (`sample.get("KM") is Number`, `sample.get("WATTS") is Number`). No decimal commas (`","`) present in numeric values.

### Test Case 2: `testGCFileWriter_gpsValidation_and_doublePrecision` (`TST-DAT-022.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriterTest.kt`
* **Preconditions**: Mock cursor with double latitude `48.137154` and double longitude `11.576124`.
* **Action**: Execute `GCFileWriter.doExport(exportInfo)`.
* **Expected Result**: Sample contains `LAT = 48.137154` and `LON = 11.576124` with full double precision (not truncated float `11.576124f`). Invalid longitude causes coordinate emission to be skipped.

### Test Case 3: `testGCFileWriter_intervalsExport_fromRecordedLaps` (`TST-DAT-022.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriterTest.kt`
* **Preconditions**: Mock `LapsDatabaseManager` returning 2 laps (Lap 1: duration 300s, Lap 2: duration 600s).
* **Action**: Execute `GCFileWriter.doExport(exportInfo)`.
* **Expected Result**: JSON contains `"INTERVALS"` array with 2 entries. Entry 0 has `START = 0`, `STOP = 300`, `NAME = "Lap 1"`. Entry 1 has `START = 300`, `STOP = 900`, `NAME = "Lap 2"`.

### Test Case 4: `testGCFileWriter_tagsMetadataAlignment` (`TST-DAT-022.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriterTest.kt`
* **Preconditions**: Mock workout summary with `workoutName = "Morning Ride"`, `description = "Felt great"`, `sportTypeId = 1L` (Bike).
* **Action**: Execute `GCFileWriter.doExport(exportInfo)`.
* **Expected Result**: `"TAGS"` contains `"Sport": "Bike"`, `"Workout Title": "Morning Ride"`, `"Notes": "Felt great"`, `"Device": "aTrainingTracker"`.

### Test Case 5: `testGCFileWriter_resourceSafetyOnException` (`TST-DAT-022.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriterTest.kt`
* **Preconditions**: Mock cursor that throws `IllegalStateException` during traversal.
* **Action**: Execute `GCFileWriter.doExport(exportInfo)` wrapped in try-catch.
* **Expected Result**: Cursor `close()` is invoked deterministically.

### Test Case 6: 9-Language Localization Audit (`TST-DAT-022.6`)
* **Scope**: Localization Parity Test
* **Command**: `./gradlew testDebugUnitTest --tests "*Translation*"`
* **Expected Result**: 100% parity across all 9 supported languages.

### Test Case 7: Clean-Room Regression Suite (`TST-DAT-022.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-022.1` | Unit | `GCFileWriter.doExport` (numeric / locale) | `REQ-DAT-027` (Sec 1.2.1) | Specified |
| `TST-DAT-022.2` | Unit | `GCFileWriter.doExport` (GPS fidelity) | `REQ-DAT-027` (Sec 1.2.2) | Specified |
| `TST-DAT-022.3` | Unit | `GCFileWriter.doExport` (Intervals) | `REQ-DAT-027` (Sec 1.2.3) | Specified |
| `TST-DAT-022.4` | Unit | `GCFileWriter.doExport` (Tags) | `REQ-DAT-027` (Sec 1.2.4) | Specified |
| `TST-DAT-022.5` | Unit | `GCFileWriter.doExport` (Resource safety) | `REQ-DAT-027` (Sec 1.2.6) | Specified |
| `TST-DAT-022.6` | Localization | `TranslationParityTest` | `REQ-UI-106`, `REQ-DAT-027` | Specified |
| `TST-DAT-022.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
