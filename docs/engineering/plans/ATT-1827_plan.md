# Stage 3: Implementation Plan - ATT-1827: Full-Telemetry FIT Workout Exporter via Garmin FIT SDK

**Ticket**: [ATT-1827](https://rainerblind.atlassian.net/browse/ATT-1827)  
**Sub-task**: [ATT-2255](https://rainerblind.atlassian.net/browse/ATT-2255) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1117](https://rainerblind.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-DAT-018` (*Full-Telemetry FIT Workout Exporter via Garmin FIT SDK (`FitFileWriter`)*)  
**Test Mapping**: `TST-DAT-013` (*Full-Telemetry FIT Workout Exporter via Garmin FIT SDK Verification*)  
**Branch**: `feature/ATT-1827`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

aTrainingTracker currently exports workout telemetry exclusively into XML/text-based formats (TCX, GPX, CSV, GC JSON). While functional, text formats suffer from significant storage bloat (~8x–15x larger than binary FIT files) and lack native, first-class compatibility with modern endurance analysis platforms (Garmin Connect, Intervals.icu, TrainingPeaks, Golden Cheetah, WKO5).

The objective of `ATT-1827` is to implement a high-fidelity binary FIT activity exporter utilizing the official Garmin FIT SDK (`com.garmin:fit:21.141.0`). The exporter will encode complete workout telemetry—including `FileIdMesg`, `ActivityMesg`, `SessionMesg`, per-interval `LapMesg`, and second-by-second `RecordMesg` trackpoints—with full support for indoor/missing GPS sessions, automated CRC verification, and seamless integration into existing preference and cloud upload pipelines.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-018` (*Full-Telemetry FIT Workout Exporter via Garmin FIT SDK (`FitFileWriter`)*)
* **Test Mapping**: `TST-DAT-013` (*Full-Telemetry FIT Workout Exporter via Garmin FIT SDK Verification*)
  - `TST-DAT-013.1`: Binary FIT File Structure & Header Unit Tests (14-byte header, CRC integrity, clean decode via `com.garmin.fit.Decode`).
  - `TST-DAT-013.2`: FIT Message Hierarchy & Telemetry Accuracy Unit Tests (`FileIdMesg`, `ActivityMesg`, `SessionMesg`, `LapMesg`, `RecordMesg`, semicircles GPS conversion).
  - `TST-DAT-013.3`: Indoor Workout & Missing GPS Fallback Unit Tests (omission of `positionLat` and `positionLong` while preserving sensor streams).
  - `TST-DAT-013.4`: Export Subsystem & Preferences Integration Unit Tests (`ExportManager`, `FileFormat`, `TrainingApplication`, `ExportSettingsDialog`).
  - `TST-DAT-013.5`: 9-Language Localization Audit (`FIT` string resource).
  - `TST-DAT-013.6`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing text/XML exporters (`TCXFileWriter`, `GPXFileWriter`, `CSVFileWriter`, `GCFileWriter`, `RunkeeperFileWriter`) retain 100% identical serialization behavior.
2. **Standard Class Naming Convention**: Adhering to the existing exporter architecture, the exporter class SHALL be named `FitFileWriter` in package `com.atrainingtracker.trainingtracker.exporter.writer` extending `BaseFileWriter`.
3. **FIT Semicircles GPS Invariant**: GPS coordinates are converted via $\text{round}(\text{deg} \times 2^{31} / 180.0)$. For indoor workouts or samples lacking valid GPS fixes, coordinate fields SHALL be omitted per the Garmin FIT specification.
4. **Thread Safety & Background Dispatch**: All file writing and encoding operations execute on background dispatchers via WorkManager (`ExportWorker`).
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Build Dependencies & String Resources
- **`app/build.gradle`**: Ensure `com.garmin:fit:21.141.0` dependency is resolved via Maven Central.
- **`app/src/main/res/values/strings.xml`**: Define `<string name="FIT" translatable="false">FIT</string>`.

### Component 2: Exporter Registration & Domain Models
- **`FileFormat.java`**:
  - Add enum constant `FIT("FIT", ".fit", R.string.FIT)`.
  - Include `FIT` in `STANDARD_FILE_FORMATS = new FileFormat[]{CSV, GC, TCX, GPX, FIT}`.
- **`ExportType.java`**:
  - Add `FileFormat.FIT` to `DROPBOX` exportToFileFormats:
    `DROPBOX(R.string.Dropbox, new FileFormat[]{FileFormat.CSV, FileFormat.GC, FileFormat.GPX, FileFormat.TCX, FileFormat.FIT})`.
- **`ExportManager.java`**:
  - Register `case FIT -> new FitFileWriter(context)` in `getExporter(Context, ExportInfo)`.

### Component 3: Preferences & Export Settings UI
- **`TrainingApplication.java`**:
  - Define `public static final String SP_EXPORT_FIT = "export_fit";`
  - Add `public static boolean exportToFIT() { return cSharedPreferences.getBoolean(SP_EXPORT_FIT, false); }`
  - Add `public static void setExportToFIT(boolean value) { cSharedPreferences.edit().putBoolean(SP_EXPORT_FIT, value).apply(); }`
  - Update `exportToFile(@NonNull FileFormat fileFormat)` to handle `case FIT -> exportToFIT();`.
- **`ExportSettingsDialog.kt`**:
  - Initialize `var exportFit by remember { mutableStateOf(TrainingApplication.exportToFIT()) }`.
  - Render `ExportOptionToggle(label = "FIT", isChecked = exportFit, onCheckedChange = { exportFit = it })`.
  - In `onSave`, invoke `TrainingApplication.setExportToFIT(exportFit)`.

### Component 4: Core `FitFileWriter` Exporter
- Resides in `com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriter`.
- Extends `BaseFileWriter`.
- Implementation structure:
  - `doExport(ExportInfo exportInfo)`:
    1. Query workout summary via `getHeaderData(exportInfo)`.
    2. Resolve target file: `File file = new File(getBaseDirFile(mContext), exportInfo.getShortPath());`. Ensure parent directory exists.
    3. Instantiate `FileEncoder encoder = new FileEncoder(file, Fit.ProtocolVersion.V2_0);`.
    4. Write `FileIdMesg`:
       - `setType(File.ACTIVITY)`
       - `setManufacturer(Manufacturer.DEVELOPMENT)`
       - `setProduct(1)`
       - `setSerialNumber(1L)`
       - `setTimeCreated(new DateTime(startTimeParsed))`
    5. Write `ActivityMesg`:
       - `setTimestamp(new DateTime(endTimeParsed))`
       - `setTotalTimerTime((float) totalTimeSeconds)`
       - `setNumSessions(1)`
       - `setType(Activity.MANUAL)`
    6. Write `SessionMesg`:
       - `setStartTime(new DateTime(startTimeParsed))`
       - `setTimestamp(new DateTime(endTimeParsed))`
       - `setTotalElapsedTime((float) totalTimeSeconds)`
       - `setTotalTimerTime((float) totalTimeSeconds)`
       - `setTotalDistance((float) totalDistanceMeters)`
       - `setSport(mapSportType(sportTypeId))`
       - `setSubSport(SubSport.GENERIC)`
       - `setTotalCalories(calories)`
       - Set summary statistics from `extrema_values` / `WorkoutSummaries` (`avgSpeed`, `maxSpeed`, `avgHeartRate`, `maxHeartRate`, `avgCadence`, `maxCadence`, `avgPower`, `maxPower`, `totalAscent`, `totalDescent`).
       - `setNumLaps(laps.size())`
    7. Query `LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)` and write `LapMesg` entries.
    8. Query `WorkoutSamplesDatabaseManager` cursor for `exportInfo.getFileBaseName()`.
    9. Loop cursor and write sequential `RecordMesg` entries:
       - `setTimestamp(new DateTime(sampleTimeParsed))`
       - If coordinates are valid/finite, convert to semicircles and set `setPositionLat` / `setPositionLong`. If indoor or coordinates are NaN/null, omit coordinate fields.
       - Set `setAltitude`, `setDistance`, `setSpeed`, `setHeartRate`, `setCadence`, `setPower`, `setTemperature` when respective sensor data is available.
    10. Close `encoder.close()`. This finalizes the FIT header and calculates the 2-byte file CRC.
    11. Return `new ExportResult(true, false, null)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Resource, Model & Preference Updates
* Files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/FileFormat.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportType.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialog.kt`
* Changes: Add `FIT` string, enum definitions, Dropbox support, preference getters/setters, and Compose toggle.

### Step 2: Implement `FitFileWriter.java`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java`
* Changes:
  - Implement `FitFileWriter` with Garmin FIT message encoding hierarchy, semicircles conversion, indoor omission, and CRC calculation.
  - Wire `FitFileWriter` in `ExportManager.getExporter()`.

### Step 3: Author Automated Unit Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriterTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/ExportManagerFitTest.kt`
* Changes:
  - Verify binary `.fit` file generation and round-trip decoding via `com.garmin.fit.Decode` and `MesgBroadcaster`.
  - Verify telemetry accuracy, semicircles coordinates, and indoor coordinate omission.
  - Verify `ExportManager` and `TrainingApplication` preference binding.

### Step 4: Targeted Unit Testing & Localization Verification
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.*"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests (`FitFileWriterTest`, `ExportManagerFitTest`) validate binary FIT compliance, message structure, and round-trip decode.
  - Translation parity audit validates strings across 9 locales.
  - Clean-room regression suite (`./gradlew testDebugUnitTest`) validates zero regressions across all modules.
* **Rollback**:
  - Branch isolation on `feature/ATT-1827` ensures changes can be cleanly reset or reverted via `git checkout sprint/2026-40.14` without touching mainline branches.
