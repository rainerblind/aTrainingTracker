# Stage 2: Requirement & Test Specification - ATT-1827: Full-Telemetry FIT Workout Exporter via Garmin FIT SDK

**Ticket**: [ATT-1827](https://rainerblind.atlassian.net/browse/ATT-1827)  
**Sub-task**: [ATT-2254](https://rainerblind.atlassian.net/browse/ATT-2254) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1117](https://rainerblind.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-DAT-018` (*Full-Telemetry FIT Workout Exporter via Garmin FIT SDK (`FitFileWriter`)*)  
**Test Spec ID**: `TST-DAT-013`  
**Branch**: `feature/ATT-1827`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-DAT-018)

### 1.1 Problem Statement & Rationale
Athletes need compact, standardized, binary workout export files conforming to the Garmin FIT protocol. FIT files preserve high-frequency time-series telemetry (GPS coordinates, elevation, speed, heart rate, cadence, power/wattage, ambient temperature) with sub-millimeter precision, compact bit-packed encoding, and automated CRC verification, ensuring universal compatibility with third-party endurance analysis platforms (Garmin Connect, Intervals.icu, TrainingPeaks, Golden Cheetah, WKO5).

### 1.2 Functional & Architectural Requirements
1. **FIT Protocol & SDK Integration**:
   - The application SHALL depend on `com.garmin:fit:21.141.0` via Maven Central.
   - The exporter SHALL utilize `com.garmin.fit.FileEncoder` with protocol `Fit.ProtocolVersion.V2_0` to encode binary `.fit` activity files.
2. **FIT Message Pipeline Hierarchy (`FitFileWriter`)**:
   - Class `FitFileWriter` SHALL reside in package `com.atrainingtracker.trainingtracker.exporter.writer` and extend `BaseFileWriter`.
   - `FileIdMesg`: Set `type = File.ACTIVITY`, `manufacturer = Manufacturer.DEVELOPMENT`, `product = 1`, `serialNumber = 1L`, and `timeCreated` matching the workout start time.
   - `ActivityMesg`: Set `timestamp` matching workout end time, `totalTimerTime` matching elapsed duration in seconds, `numSessions = 1`, and `type = Activity.MANUAL`.
   - `SessionMesg`: Set `startTime`, `timestamp` (end time), `totalElapsedTime`, `totalTimerTime`, `totalDistance` in meters, `sport` mapped from `SportType` (`Sport.CYCLING`, `Sport.RUNNING`, `Sport.WALKING`, `Sport.SWIMMING`, `Sport.FITNESS_EQUIPMENT`, or `Sport.GENERIC`), `subSport = SubSport.GENERIC`, `totalCalories`, and session summary statistics (`avgSpeed`, `maxSpeed`, `avgHeartRate`, `maxHeartRate`, `avgCadence`, `maxCadence`, `avgPower`, `maxPower`, `totalAscent`, `totalDescent`) extracted from `WorkoutData` and `extrema_values`.
   - `LapMesg`: For workouts with laps in `Laps.db`, the system SHALL encode a `LapMesg` for each lap with `startTime`, `timestamp`, `totalElapsedTime`, `totalTimerTime`, `totalDistance`, lap averages, and extrema.
   - `RecordMesg`: The system SHALL iterate all recorded sample points (`samplesTable`), encoding sequential `RecordMesg` entries containing: `timestamp`, `positionLat` and `positionLong` (semicircles: $\text{round}(\text{deg} \times 2^{31} / 180.0)$), `altitude` (meters), `distance` (cumulative meters), `speed` (m/s), `heartRate` (bpm), `cadence` (rpm), `power` (watts), and `temperature` (Celsius).
   - **Indoor / Missing GPS Handling**: If coordinates are null, NaN, or out-of-range, `positionLat` and `positionLong` SHALL be omitted from `RecordMesg`, leaving sensor metrics intact.
3. **Export Architecture Integration**:
   - `FileFormat.java` SHALL define `FIT("FIT", ".fit", R.string.FIT)` and include `FIT` in `STANDARD_FILE_FORMATS`.
   - `ExportManager.java` SHALL register `case FIT -> new FitFileWriter(context)` in `getExporter(FileFormat)` and add `FileFormat.FIT` to `ExportType.DROPBOX` supported formats.
   - `TrainingApplication.java` SHALL define `SP_EXPORT_FIT = "SP_EXPORT_FIT"`, `exportToFIT()`, and `setExportToFIT(boolean)`.
   - `ExportSettingsDialog.kt` SHALL provide an `ExportOptionToggle` for `FIT` bound to `TrainingApplication.exportToFIT()` and `setExportToFIT()`.
4. **Binary Stream Handling**:
   - `FitFileWriter` SHALL write binary data directly to `new File(getBaseDirFile(mContext), mExportInfo.getShortPath())`.
5. **Localization Parity**:
   - String `FIT` SHALL be defined with `translatable="false"` in `res/values/strings.xml`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (FIT File Generation & Decoding)**:
  * *Given* a recorded workout session with GPS coordinates, heart rate, cadence, and power samples,
  * *When* `FitFileWriter.writeFile()` executes,
  * *Then* a binary `.fit` file is written to storage, containing a valid 14-byte header and valid 2-byte CRC, and Garmin FIT SDK `Decode` decodes the file with 0 errors.
* **Criterion 2 (Message Telemetry & Semicircles Precision)**:
  * *Given* the decoded `.fit` file,
  * *When* inspecting `RecordMesg` trackpoints,
  * *Then* `positionLat` and `positionLong` equal $\text{round}(\text{deg} \times 2^{31} / 180.0)$, and altitude, speed, heart rate, cadence, and power match the source database records.
* **Criterion 3 (Indoor / Missing Fix Handling)**:
  * *Given* a workout recorded without GPS fixes (indoor session),
  * *When* exported via `FitFileWriter`,
  * *Then* `positionLat` and `positionLong` fields are absent from `RecordMesg`, while heart rate, cadence, and power records are fully preserved.
* **Criterion 4 (Session & Lap Summaries)**:
  * *Given* a workout with interval laps recorded in `Laps.db`,
  * *When* exported to FIT,
  * *Then* `SessionMesg` and individual `LapMesg` entries reflect the exact start times, durations, distances, and averages.
* **Criterion 5 (UI Toggle & Preference Binding)**:
  * *Given* `ExportSettingsDialog` is displayed,
  * *When* the athlete toggles "FIT" and taps Save,
  * *Then* `TrainingApplication.exportToFIT()` returns the updated boolean state.

### 1.4 System Invariants
- Existing exporters (`TCXFileWriter`, `GPXFileWriter`, `CSVFileWriter`, `GCFileWriter`) retain 100% identical behavior.
- Non-blocking execution: File encoding executes on background dispatchers.
- Zero database schema modifications required for export.

---

## 2. Test Specification (TST-DAT-013)

### Test Case 1: `testFitFileWriter_generatesValidBinaryFitFile_andDecodesCleanly` (`TST-DAT-013.1`)
* **Scope**: Unit Test & Round-trip FIT SDK Decode Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriterTest.kt`
* **Preconditions**: Mock SQLite database with workout header, session extrema, and sample stream.
* **Action**: Execute `FitFileWriter(context).writeFile()`.
* **Expected Result**: File is created, has `.fit` extension, starts with valid 14-byte FIT header, and `Decode().read(inputStream, listener)` decodes all messages without throwing `FitRuntimeException`.

### Test Case 2: `testFitFileWriter_messageHierarchyAndTelemetryFidelity` (`TST-DAT-013.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriterTest.kt`
* **Preconditions**: Workout with known GPS coordinates (52.5200, 13.4050), HR (155), power (240W), cadence (90 rpm), and 2 laps.
* **Action**: Decode the generated FIT file and collect all messages via `MesgBroadcaster`.
* **Expected Result**:
  - `FileIdMesg`: `getType() == File.ACTIVITY`.
  - `ActivityMesg`: `getNumSessions() == 1`.
  - `SessionMesg`: `getSport() == Sport.CYCLING`, `getAvgPower() == 240`, `getAvgHeartRate() == 155`.
  - `LapMesg`: exactly 2 lap messages with matching metrics.
  - `RecordMesg`: `getPositionLat() == Math.round(52.5200 * (2^31 / 180.0))`.

### Test Case 3: `testFitFileWriter_indoorWorkoutOmitsCoordinates` (`TST-DAT-013.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriterTest.kt`
* **Preconditions**: Trackpoints with NaN or null coordinates (indoor workout).
* **Action**: Encode and decode the activity file.
* **Expected Result**: `RecordMesg.getPositionLat()` and `RecordMesg.getPositionLong()` return `null` or `Fit.BASE_TYPE_SINT32_INVALID`, while `RecordMesg.getPower()` and `RecordMesg.getHeartRate()` are valid.

### Test Case 4: `testExportSubsystemAndPreferences_integration` (`TST-DAT-013.4`)
* **Scope**: Subsystem & Settings Integration Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/ExportManagerFitTest.kt`
* **Preconditions**: Application context initialized.
* **Action**:
  - Check `ExportManager.getExporter(FileFormat.FIT)`.
  - Verify `FileFormat.FIT` in `STANDARD_FILE_FORMATS`.
  - Test `TrainingApplication.setExportToFIT(true)`.
* **Expected Result**:
  - `getExporter` returns instance of `FitFileWriter`.
  - `STANDARD_FILE_FORMATS` contains `FileFormat.FIT`.
  - `TrainingApplication.exportToFIT()` evaluates to `true`.

### Test Case 5: 9-Language Localization Audit (`TST-DAT-013.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Preconditions**: All `strings.xml` resource files.
* **Action**: Verify `R.string.FIT` is defined and resolves cleanly across all locales.
* **Expected Result**: 100% parity, 0 missing strings.

### Test Case 6: Clean-Room Full Suite Regression (`TST-DAT-013.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-DAT-013.1]` | Unit / SDK Decode | `FitFileWriter.writeFile` | `REQ-DAT-018` (Item 1, 4) | Specified |
| `[TST-DAT-013.2]` | Unit / Telemetry | `FitFileWriter.writeStream`, `writeHeader` | `REQ-DAT-018` (Item 2) | Specified |
| `[TST-DAT-013.3]` | Unit / Indoor Fallback | `FitFileWriter.writeRecordMesg` | `REQ-DAT-018` (Item 2) | Specified |
| `[TST-DAT-013.4]` | Integration | `ExportManager.getExporter`, `TrainingApplication` | `REQ-DAT-018` (Item 3) | Specified |
| `[TST-DAT-013.5]` | Localization | `TranslationParityTest` | `REQ-DAT-018` (Item 5) | Specified |
| `[TST-DAT-013.6]` | Regression | `./gradlew testDebugUnitTest` | Zero Regression Invariant | Specified |
