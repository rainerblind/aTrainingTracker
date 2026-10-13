# Stage 1 Analysis: ATT-1827 - Full-Telemetry FIT Workout Exporter via Garmin FIT SDK

**Ticket**: [ATT-1827](https://rainerblind.atlassian.net/browse/ATT-1827)  
**Sub-task**: [ATT-2253](https://rainerblind.atlassian.net/browse/ATT-2253) (`[Analysis]`)  
**Parent Epic**: [ATT-1117](https://rainerblind.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1827`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

aTrainingTracker currently provides workout telemetry export exclusively through text and XML-based formats:
- **TCX** (Garmin Training Center XML v2 via `TCXFileWriter`)
- **GPX** (GPS Exchange Format 1.1 via `GPXFileWriter`)
- **CSV** (Comma Separated Values via `CSVFileWriter`)
- **GC JSON** (Golden Cheetah JSON format via `GCFileWriter`)

While functional, text/XML formats present several severe architectural and practical limitations for endurance athletes:
1. **File Size Overhead**: Verbose XML tags incur an approximate 8x-15x file size multiplier compared to equivalent binary representations, increasing storage consumption and cloud synchronization latency (Dropbox / Google Drive).
2. **Analysis Platform Interoperability**: Modern desktop and web athletic analysis suites (Garmin Connect, Intervals.icu, TrainingPeaks, WKO5, Golden Cheetah) natively prefer Garmin's Flexible and Interoperable Data Transfer (FIT) binary protocol. Some third-party platforms reject or only partially parse extended sensor streams (power balance, cadence, high-frequency heart rate, barometric temperature) when supplied via XML extensions.
3. **Data Precision & Standard Protocol**: XML decimal string serialization risks precision truncation and regional comma/dot delimiter parse failures. The binary FIT protocol provides compact, bit-packed integer and fixed-point representations with standardized endianness and cyclical redundancy check (CRC) verification.

Implementing a native, full-telemetry FIT workout exporter using the official Garmin FIT SDK (`com.garmin:fit`) empowers athletes to export activities in the industry-standard binary format with 100% sensor fidelity, compact file size, and zero data loss.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap Analysis)

### 2.1 Current Exporter Pipeline Architecture & Class Naming Reconciliation
The export subsystem in `app/src/main/java/com/atrainingtracker/trainingtracker/exporter` adheres to a strict class naming convention:
- **Base Class**: `BaseFileWriter.java` (abstract base class under `exporter.writer`) manages file creation, export path resolution in app storage (`getBaseDirFile()`), progress intent broadcasting, and database query lifecycles (`getHeaderData()`, `getWorkoutExtrema()`, `getLaps()`, `getWorkoutDataCursor()`).
- **Concrete Writers**: All concrete implementations follow the `*FileWriter` naming pattern:
  - `TCXFileWriter.java`
  - `GPXFileWriter.java`
  - `CSVFileWriter.java`
  - `GCFileWriter.java`
  - `RunkeeperFileWriter.java`
- **Class Naming Reconciliation**: While the parent ticket text informally mentioned `FitFileExporter` extending `BaseExporter`, no `BaseExporter` class exists in the codebase. To preserve 100% structural architectural consistency with existing exporters, the new implementation SHALL be named **`FitFileWriter`** located in `com.atrainingtracker.trainingtracker.exporter.writer` and SHALL extend **`BaseFileWriter`**.

### 2.2 Architectural Gap: Binary Protocol vs Character Streams
`BaseFileWriter` was historically designed around text-based `BufferedWriter`:
```java
mBufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
```
Garmin FIT SDK operates on binary output streams or `File` handles via `com.garmin.fit.FileEncoder`:
```java
FileEncoder encoder = new FileEncoder(file, Fit.ProtocolVersion.V2_0);
```
To preserve the `BaseFileWriter` contracts (background thread execution, progress reporting, metadata querying) while supporting binary FIT output, `FitFileWriter` manages its own `FileEncoder` directly targeting the output `File` resolved via `new File(getBaseDirFile(mContext), mExportInfo.getShortPath())`. In `writeHeader()`, `mBufferedWriter` is safely closed or bypassed so that binary encoding proceeds cleanly without character encoding contamination.

### 2.3 Garmin FIT Protocol Specification & Message Hierarchy
A valid FIT activity file requires a strict sequential message protocol defined in the FIT Profile:
1. **`FileIdMesg`**:
   - `setType(File.ACTIVITY)`
   - `setManufacturer(Manufacturer.DEVELOPMENT)` (or official manufacturer ID)
   - `setProduct(1)`
   - `setSerialNumber(1L)`
   - `setTimeCreated(new DateTime(startTime))`
2. **`ActivityMesg`**:
   - `setTimestamp(new DateTime(endTime))`
   - `setTotalTimerTime(totalTimeSec)`
   - `setNumSessions(1)`
   - `setType(Activity.MANUAL)`
3. **`SessionMesg`**:
   - `setStartTime(new DateTime(startTime))`
   - `setTimestamp(new DateTime(endTime))`
   - `setTotalElapsedTime(elapsedTimeSec)`
   - `setTotalTimerTime(activeTimerTimeSec)`
   - `setTotalDistance(totalDistanceMeters)`
   - `setSport(mappedSport)` (e.g. `Sport.CYCLING`, `Sport.RUNNING`, `Sport.WALKING`, `Sport.SWIMMING`, `Sport.FITNESS_EQUIPMENT`, `Sport.GENERIC`)
   - `setSubSport(SubSport.GENERIC)`
   - `setAvgSpeed`, `setMaxSpeed` (m/s)
   - `setAvgHeartRate`, `setMaxHeartRate` (bpm)
   - `setAvgCadence`, `setMaxCadence` (rpm)
   - `setAvgPower`, `setMaxPower` (watts)
   - `setTotalAscent`, `setTotalDescent` (meters)
   - `setTotalCalories` (kcal)
   - `setNumLaps(lapCount)`
4. **`LapMesg`**:
   - Sequential lap entries matching `Laps.db`:
   - `setStartTime`, `setTimestamp`, `setTotalElapsedTime`, `setTotalTimerTime`, `setTotalDistance`
   - Lap averages and extrema (`avgSpeed`, `maxSpeed`, `avgHeartRate`, `maxHeartRate`, `avgCadence`, `maxCadence`, `avgPower`, `maxPower`, `totalAscent`, `totalDescent`)
5. **`RecordMesg`**:
   - Time-series trackpoints for every sampled second from `samplesTable`:
   - `setTimestamp(new DateTime(sampleTime))`
   - `setPositionLat` / `setPositionLong`: Coordinate conversion and missing/indoor handling (detailed in 2.4).
   - `setAltitude((float) altitudeMeters)`
   - `setDistance((float) distanceMeters)`
   - `setSpeed((float) speedMps)`
   - `setHeartRate((short) hrBpm)`
   - `setCadence((short) cadenceRpm)`
   - `setPower((int) powerWatts)`
   - `setTemperature((byte) tempCelsius)` (if available)

### 2.4 GPS Semicircles Conversion & Indoor / Missing Fix Fallback Behavior
The FIT protocol represents GPS coordinates as 32-bit signed integers in semicircles:
$$\text{semicircles} = \text{round}\left(\text{degrees} \times \frac{2^{31}}{180.0}\right)$$
Range: $-2^{31} \le \text{semicircles} \le 2^{31}-1$.
* **Valid GPS Coordinates**: When latitude and longitude are valid finite numbers within range ($-90 \le \text{lat} \le 90$, $-180 \le \text{lng} \le 180$), they are converted to semicircles and set via `setPositionLat()` and `setPositionLong()`.
* **Indoor Workouts & Missing GPS Fixes**: For stationary trainer workouts, indoor treadmill sessions, or trackpoint seconds where GPS lock was lost (represented by `Double.isNaN()`, null, or out-of-range sentinel values in `samplesTable`), `setPositionLat()` and `setPositionLong()` SHALL be **omitted** from `RecordMesg`. Per the official Garmin FIT SDK specification, omitting optional fields represents invalid/absent data (`Fit.BASE_TYPE_SINT32_INVALID`), allowing downstream analysis tools to recognize indoor activities and calculate cadence, heart rate, and power without falsifying geographic locations.

### 2.5 UI Settings State Binding & Preference Synchronization
In `ExportSettingsDialog.kt`, export preferences are managed reactively:
- The dialog initializes state:
  ```kotlin
  var exportFit by remember { mutableStateOf(TrainingApplication.exportToFIT()) }
  ```
- An `ExportOptionToggle` is rendered:
  ```kotlin
  ExportOptionToggle(
      label = "FIT",
      isChecked = exportFit,
      onCheckedChange = { exportFit = it }
  )
  ```
- On user confirmation (`AppDialogActions.SaveCancel.onSave`):
  ```kotlin
  TrainingApplication.setExportToFIT(exportFit)
  ```
- `TrainingApplication.java` persists the preference under `SP_EXPORT_FIT` in SharedPreferences and provides getter/setter:
  ```java
  public static final String SP_EXPORT_FIT = "SP_EXPORT_FIT";
  public static boolean exportToFIT() { return getAppPreferences().getBoolean(SP_EXPORT_FIT, false); }
  public static void setExportToFIT(boolean export) { getAppPreferences().edit().putBoolean(SP_EXPORT_FIT, export).apply(); }
  ```
- In `exportToFile(FileFormat format)`:
  ```java
  case FIT -> exportToFIT();
  ```

### 2.6 Dependency Verification & Repository Availability
- **Maven Dependency**: `com.garmin:fit:21.141.0`.
- **Repository**: Official Garmin FIT SDK is published on **Maven Central** (`mavenCentral()`), which is already configured in `settings.gradle` (`dependencyResolutionManagement.repositories`).
- **Build Verification**: Dependency resolution was verified with Gradle (`./gradlew testDebugUnitTest --version`), compiling and resolving without any errors or repository additions required.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add official Garmin FIT SDK dependency (`com.garmin:fit:21.141.0`) to `app/build.gradle`.
  2. Implement `FitFileWriter` in `com.atrainingtracker.trainingtracker.exporter.writer` extending `BaseFileWriter`, encoding complete `FileIdMesg`, `ActivityMesg`, `SessionMesg`, `LapMesg`, and `RecordMesg` hierarchies.
  3. Register `FileFormat.FIT` in `FileFormat.java` and include in `STANDARD_FILE_FORMATS`.
  4. Register `FitFileWriter` in `ExportManager.java` for local file generation and Dropbox upload support.
  5. Add preference handling in `TrainingApplication.java` (`SP_EXPORT_FIT`, `exportToFIT()`, `setExportToFIT()`).
  6. Add FIT export toggle to `ExportSettingsDialog.kt`.
  7. Add string resource `FIT` (`<string name="FIT" translatable="false">FIT</string>`).
  8. Comprehensive automated unit tests verifying FIT binary generation, CRC integrity, and round-trip decoding via Garmin FIT SDK `Decode`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. External FIT Workout Importer (Reserved strictly for child ticket `ATT-1828`).
  2. Garmin Developer Data Fields / custom FIT developer definitions (standard profile messages cover 100% of recorded sensors).
  3. Direct BLE upload to Garmin Connect API (export produces standard `.fit` file for user/cloud sharing).
  4. Refactoring existing TCX/GPX/CSV exporters.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement (`REQ-DAT-018`), extending the export framework (`REQ-DAT-001`, `REQ-DAT-012`, `REQ-DAT-013`) under Epic `ATT-1117` (*FIT File Format Support*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1827`, target release `V4.9.39`, active sprint `2026-40.14`.
* **Root Reason for Existing Formulation**: Prior exporters relied on XML/text because early Android iterations had limited memory and third-party binary libraries were scarce. As athletic analysis matured, FIT emerged as the ubiquitous standard.
* **Preservation of Core Invariants**:
  - Existing TCX, GPX, CSV, and GC JSON exporters retain 100% identical behavior.
  - Export background thread execution and non-blocking UI contracts are fully maintained.
  - SQLite database schemas (`WorkoutSummaries`, `Laps`, `samplesTable`) require zero schema changes for export.
  - 9-language localization parity is preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 FIT SDK Integration & Packaging
- Maven dependency: `com.garmin:fit:21.141.0` in `app/build.gradle` via Maven Central.
- Clean licensing and zero reflection overhead.

### 5.2 `FitFileWriter` Implementation Strategy
- Extends `BaseFileWriter`.
- Overrides `writeHeader()`, `writeStream()`, `writeFooter()`.
- In `writeHeader()`, creates output file via `new File(getBaseDirFile(mContext), mExportInfo.getShortPath())`, instantiates `FileEncoder(file, Fit.ProtocolVersion.V2_0)`, and writes `FileIdMesg`, `ActivityMesg`, and `SessionMesg`.
- In `writeStream()`, iterates `Cursor` points from `mSamplesTable`, translates latitude/longitude into semicircles (or omits them if indoor/invalid), and encodes `RecordMesg`. Also encodes `LapMesg` records from `LapsDatabaseManager`.
- In `writeFooter()`, calls `encoder.close()`, which automatically calculates and writes the file header size, data size, and 2-byte CRC.

### 5.3 Semicircles Coordinate Conversion Invariant
The FIT protocol represents GPS coordinates as 32-bit signed integers in semicircles:
$$\text{semicircles} = \text{round}\left(\text{degrees} \times \frac{2^{31}}{180.0}\right)$$
Range: $-2^{31} \le \text{semicircles} \le 2^{31}-1$.
This provides sub-millimeter precision worldwide while eliminating floating-point rounding ambiguities.

### 5.4 Sport Mapping
Map aTrainingTracker `SportType` to Garmin FIT `Sport`:
- Cycling / Mountain Biking / Gravel / Indoor Cycling $\to$ `Sport.CYCLING`
- Running / Trail Running / Treadmill $\to$ `Sport.RUNNING`
- Walking / Hiking $\to$ `Sport.WALKING`
- Swimming $\to$ `Sport.SWIMMING`
- Fitness / Strength $\to$ `Sport.FITNESS_EQUIPMENT`
- Other $\to$ `Sport.GENERIC`

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing exporters (`TCXFileWriter`, `GPXFileWriter`, `CSVFileWriter`, `GCFileWriter`).
  2. Byte-exact FIT CRC checksum validity: Decoded by official Garmin FIT SDK `Decode` without errors.
  3. Non-blocking UI: All file encoding executed asynchronously on export background dispatchers.
  4. Parent ticket Human Decision Gate remains strictly enforced (`Final Review (Human)` assigned to `human`).

* **Risk Rating**: **LOW**
  - Justification: The FIT exporter is an additive feature leveraging the official, battle-tested Garmin FIT SDK. It integrates into the existing, mature exporter pipeline without altering database schemas or active tracking services.
