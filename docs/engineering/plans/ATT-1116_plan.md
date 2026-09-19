# Implementation Plan: GPX File Import (ATT-1116)

* **Parent Issue**: [ATT-1116](https://rainerblind.atlassian.net/browse/ATT-1116) (*[Feature] Import GPX files*)
* **Sub-Task**: [ATT-1154](https://rainerblind.atlassian.net/browse/ATT-1154) (*Stage 3: Technical Implementation Plan*)
* **Target Version**: `V4.9.37`
* **Requirement**: `REQ-MIG-030` (*High-Fidelity GPX Workout Import & Full Telemetry Ingestion Parity*)
* **Test Specification**: `TST-MIG-027`
* **Branch**: `feature/ATT-1116`

---

## 1. Objective & Background

The application currently features a robust, reactive import pipeline for TCX workout files (`LegacyImportEngine.importFromTcx()`), allowing athletes to restore historical activities with high-fidelity telemetry, multi-lap segmentation, automatic route clustering, period aggregations, and optional Strava synchronization.

The objective of **ATT-1116** is to provide identical first-class workout import capability for **GPX files** (GPX 1.0/1.1), supporting both single-file selection via the Android document picker and cloud bulk recovery from Dropbox.

---

## 2. Technical Architecture & Parsing Strategy

### 2.1 Streaming XML Ingestion (`XmlPullParser`)
To prevent memory spikes and maintain consistency with `importFromTcx()`, GPX parsing in `LegacyImportEngine.importFromGpx()` will use Android's native `XmlPullParser`:
- **Tags Parsed**:
  - `<metadata>`: `<time>` (fallback start timestamp), `<name>` (workout name fallback), `<desc>` (description/notes).
  - `<trk>`: `<name>` (activity title), `<desc>` (notes/comments), `<type>` (activity / sport type, e.g. "running", "cycling", "ride", "hike").
  - `<trkseg>`: Track segment boundaries mapping directly to laps (`ParsedLap`), maintaining interval splits and indexing each sample with `SensorType.LAP_NR`.
  - `<trkpt lat="..." lon="...">`:
    - `<ele>`: Elevation in meters (`SensorType.ALTITUDE`).
    - `<time>`: ISO 8601 UTC timestamp parsed to `"yyyy-MM-dd HH:mm:ss"`.
    - `<speed>`: Instantaneous speed in m/s (`SensorType.SPEED_mps`).
    - `<extensions>` / Garmin TrackPointExtension (`gpxtpx:TrackPointExtension`):
      - `<gpxtpx:hr>` or `<hr>` or `<heartrate>` -> `SensorType.HR`
      - `<gpxtpx:cad>` or `<cad>` or `<cadence>` -> `SensorType.CADENCE`
      - `<gpxtpx:speed>` -> `SensorType.SPEED_mps`
      - `<gpxtpx:atemp>` or `<atemp>` -> `SensorType.TEMPERATURE_c`
      - `<power>` or `<watts>` -> `SensorType.POWER`
  - Fallback `<rte>` / `<rtept>`: Supported for legacy GPS export tools that write route points rather than tracks.

### 2.2 Geodesic Distance Calculation
Unlike TCX, GPX track points standardly omit cumulative distance. The engine will calculate geodesic distance between consecutive GPS fixes:
$$\Delta d_i = \text{distanceBetween}(lat_{i-1}, lon_{i-1}, lat_i, lon_i)$$
$$d_i = d_{i-1} + \Delta d_i$$
This accumulates `SensorType.DISTANCE_m` into `bufferedSamples` and populates the distance stream for analytics and laps.

### 2.3 Sport Type & Activity Detection
The engine will map `<type>` strings or activity keywords in `<trk><name>`:
- "running", "run", "jogging" -> `BSportType.RUN`
- "cycling", "biking", "bike", "ride" -> `BSportType.BIKE`
- "hiking", "walking" -> `BSportType.RUN` / `BSportType.UNKNOWN`
- Fallback to `BSportType.UNKNOWN`, triggering the existing multi-sport candidate discovery and speed heuristics (`EquipmentAndSportTypeDiscoveryManager.getCandidateBSportTypes`).

### 2.4 End-to-End Pipeline Reuse (`recalculateStats`)
Once parsed into samples, points, and laps, `importFromGpx` delegates to `recalculateStats()`:
- Computes total distance, active duration, ascent, descent, and average speed.
- Populates `LapsDatabaseManager` with accurate segment splits.
- Encodes Google Maps polyline string and derives apex (max line distance coordinate).
- Evaluates candidate route clusters via `suggestCluster()`.
- Updates `WorkoutRepository` and triggers self-healing period sync in `PeriodsRepository`.
- Conditionally enqueues Strava background upload if enabled.

---

## 3. ViewModel & UI Integration

### 3.1 `BackupRestoreViewModel.kt`
- In `importLegacyFile(context, uri, format)`:
  - Determine file extension from `displayName` (or fallback to `format`).
  - If extension is `"gpx"`, invoke `LegacyImportEngine.importFromGpx(context, tempFile, listener, uploadToStrava)`.
  - If extension is `"tcx"`, invoke `LegacyImportEngine.importFromTcx(...)`.

### 3.2 Dropbox Bulk Scan
- In `LegacyImportEngine.bulkRecoverFromDropbox(context, format, ...)`:
  - Add scan paths for GPX: `/GPX` and `/apps/Workouts/GPX`.
  - Download and dispatch `.gpx` files to `importFromGpx` and `.tcx` files to `importFromTcx`.

### 3.3 UI Labels (`ImportBackupTabsScreen.kt`)
- Update `R.string.import_single_file` to "Import File (TCX / GPX)" / "Datei importieren (TCX / GPX)".
- Update `R.string.legacy_recovery_description` to mention TCX & GPX support.

---

## 4. Implementation Steps

1. **`LegacyImportEngine.kt`**:
   - Implement `importFromGpx(context, gpxFile, listener, uploadToStrava)`.
   - Update `bulkRecoverFromDropbox()` to support GPX paths and dispatch based on file extension.
2. **`BackupRestoreViewModel.kt`**:
   - Enhance `importLegacyFile()` to detect `.gpx` extension and route to `importFromGpx()`.
3. **`ImportBackupTabsScreen.kt` & String Resources**:
   - Update string resources to clearly reflect TCX and GPX support across languages.
4. **Unit Tests (`LegacyImportEngineGpxTest.kt`)**:
   - Test standard GPX parsing (coordinates, elevation, timestamps).
   - Test multi-segment GPX (`<trkseg>` to `LapsDatabaseManager` mapping).
   - Test Garmin TPX telemetry (HR, cadence, speed, temp, power).
   - Test deduplication (`isWorkoutExisting`).
   - Test cluster suggestion and periods aggregation integration.

---

## 5. Verification Plan

- Run dedicated GPX import unit test suite:
  `./gradlew testDebugUnitTest --tests "*Gpx*"`
- Run full regression suite:
  `./gradlew testDebugUnitTest`
