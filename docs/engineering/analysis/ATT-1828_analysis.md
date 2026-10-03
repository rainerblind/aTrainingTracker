# Stage 1 Analysis: ATT-1828 - FIT Workout Importer with Duplicate Detection and Sport Mapping

**Ticket**: [ATT-1828](https://rainerblind.atlassian.net/browse/ATT-1828)  
**Sub-task**: [ATT-2258](https://rainerblind.atlassian.net/browse/ATT-2258) (`[Analysis]`)  
**Parent Epic**: [ATT-1117](https://rainerblind.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1828`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

Athletes frequently record endurance training activities across dedicated head units (e.g. Garmin Edge bike computers, Wahoo ELEMNT, Hammerhead Karoo, Bryton Rider), sports watches (Garmin Forerunner/Fenix, Suunto, Polar), or virtual training applications (Zwift, Rouvy, TrainerRoad). The universal binary standard across all these hardware and software vendors is the Garmin Flexible and Interoperable Data Transfer (FIT) format.

While aTrainingTracker now features a native full-telemetry FIT exporter (`FitFileWriter`, [ATT-1827](https://rainerblind.atlassian.net/browse/ATT-1827)), its workout ingestion pipeline has historically been limited to:
1. **TCX files** (Garmin Training Center XML via `LegacyImportEngine.importFromTcx`)
2. **GPX files** (GPS Exchange Format via `LegacyImportEngine.importFromGpx`)
3. **Database backup archives** (`ImportEngine.kt`)

Currently, athletes who possess libraries of historical `.fit` workout files or who want to import external activities into aTrainingTracker cannot do so. Attempting to convert `.fit` files to `.tcx` or `.gpx` via third-party web tools is tedious, loses sensor channels (e.g. power balance, cadence, high-resolution altitude, ambient temperature), and alters file timestamps.

Implementing a native **FIT Workout Importer** using the official Garmin FIT SDK (`com.garmin:fit`) allows users to import single or batch `.fit` files directly into aTrainingTracker. The importer must support:
- Comprehensive message decoding (`FileIdMesg`, `SessionMesg`, `LapMesg`, `RecordMesg`)
- Robust coordinate conversion from Garmin semicircles back to decimal degrees
- Intelligent vendor sport mapping (`Sport` / `SubSport` to internal `BSportType` / `SportType`)
- Mutex-guarded multi-dimensional duplicate detection (filename, start timestamp, epoch proximity window)
- Single and batch document picker integration in `ImportBackupTabsScreen.kt` with live progress and completion summary
- Provenance attribution (`WorkoutSummaries.SOURCE = WorkoutSource.FIT.name`)

---

## 2. Forensic Root Cause & Gap Analysis

### 2.1 Existing Ingestion Pipeline Architecture (`LegacyImportEngine.kt`)
The workout ingestion pipeline in `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt` provides proven, robust ingestion mechanics:
- **`importMutex`**: A coroutine `Mutex` preventing concurrent SQLite table creation and summary insertions.
- **`isWorkoutExisting(summaryDb, fileBaseName, timeStart, bSportType)`**: Multi-dimensional deduplication checking:
  1. Exact `FILE_BASE_NAME` match in `workout_summaries`.
  2. Exact `TIME_START` timestamp match.
  3. Proximity window: `ABS(strftime('%s', TIME_START) - strftime('%s', ?)) <= 180` seconds matching the same sport type (`B_SPORT`), preventing duplicate imports of renamed files (REQ-MIG-031).
- **`WorkoutSamplesDatabaseManager`**: Dynamic table creation per workout (`createNewTable(baseFileName, SensorType.values().toList())`) and batch transaction insertion of sample `ContentValues`.
- **`WorkoutSummariesDatabaseManager`**: Insertion of workout metadata (`FILE_BASE_NAME`, `WORKOUT_NAME`, `TIME_START`, `SPORT_ID`, `B_SPORT`, `FINISHED = 1`, `SOURCE`, `UPLOAD_TO_STRAVA`).
- **`recalculateStats(...)`**: Synchronous post-processing calculating polyline bounding boxes, min/max elevations, distance, time, and cluster candidate generation via `WorkoutClusterEngine`.
- **Post-Import Notification**: Live reactive refresh via `WorkoutRepository.getInstance().loadAllWorkouts()`, `PeriodsRepository.getInstance().syncPeriodsIfDiscrepancy()`, and `WorkoutClusterRepository.getInstance().refreshClusters()`.

### 2.2 Garmin FIT SDK Decoding Pipeline
The Garmin FIT SDK (`com.garmin:fit:21.141.0`) provides an event-driven `Decode` parser and `MesgBroadcaster`:
```java
Decode decode = new Decode();
MesgBroadcaster broadcaster = new MesgBroadcaster(decode);

broadcaster.addListener((FileIdMesgListener) mesg -> {
    // Validate mesg.getType() == File.ACTIVITY (or allow vendor variations)
});

broadcaster.addListener((SessionMesgListener) mesg -> {
    // Sport, SubSport, start time, total elapsed time, total timer time,
    // total distance, total calories, avg/max HR, cadence, power, speed, ascent, descent
});

broadcaster.addListener((LapMesgListener) mesg -> {
    // Lap number, start time, total time, distance, max speed, calories, avg/max HR
});

broadcaster.addListener((RecordMesgListener) mesg -> {
    // Semicircles latitude & longitude, altitude, distance, speed, HR, cadence, power, temperature
});

decode.read(inputStream, broadcaster, broadcaster);
```
`Decode.checkIntegrity(inputStream)` validates the 14-byte header and 2-byte CRC before reading.

### 2.3 Semicircles Coordinate Conversion
Garmin FIT stores coordinates as 32-bit signed integers (semicircles), where:
$$\text{semicircles} = \text{round}\left(\text{degrees} \times \frac{2^{31}}{180.0}\right)$$
To recover decimal degrees for SQLite and Google Maps polyline decoding:
$$\text{degrees} = \text{semicircles} \times \left(\frac{180.0}{2147483648.0}\right)$$
When a record has no GPS fix (e.g. indoor turbo trainer workouts or tunnel dropouts), `mesg.getPositionLat()` and `mesg.getPositionLong()` return `null` or `Fit.BASE_TYPE_SINT32_INVALID` (`0x7FFFFFFF`). These must be safely handled without creating invalid coordinates.

### 2.4 Sport Type & Subsport Mapping
Garmin FIT defines broad sport types via `com.garmin.fit.Sport` and `com.garmin.fit.SubSport`:
- `Sport.CYCLING` $\rightarrow$ `BSportType.BIKE`
- `Sport.RUNNING`, `Sport.WALKING` $\rightarrow$ `BSportType.RUN`
- `Sport.SWIMMING` $\rightarrow$ `BSportType.OTHER` (or dedicated swimming profile if available)
- `Sport.FITNESS_EQUIPMENT` $\rightarrow$ If `subSport == SubSport.INDOOR_CYCLING`, map to `BSportType.BIKE`; if `INDOOR_RUNNING`, map to `BSportType.RUN`; else `BSportType.OTHER`
- `Sport.GENERIC` / other $\rightarrow$ Fallback to `BSportType.UNKNOWN` / `BSportType.OTHER`
The sport ID is resolved using `SportTypeDatabaseManager.getSportTypeId(bSportType)`.

### 2.5 Multi-Vendor File Compatibility
Different device manufacturers format FIT files with subtle variations:
- Wahoo ELEMNT files may omit `ActivityMesg` and rely purely on `SessionMesg`.
- Hammerhead Karoo files may write developer data fields for radar or temperature.
- Garmin devices write detailed lap definitions and developer data fields.
The importer must be robust against missing optional messages, extracting timing from the earliest `RecordMesg` if `SessionMesg` or `ActivityMesg` timestamps are absent.

### 2.6 UI Integration: Single & Batch File Picker
In `ImportBackupTabsScreen.kt`:
- Add a new "FIT-Dateien importieren" card or action button within `ImportTabContent`.
- Use `rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments())` allowing athletes to select one or multiple `.fit` files simultaneously from device storage or Google Drive / Downloads.
- Delegate to `BackupRestoreViewModel.importFitFiles(context, uris)`.
- During import, display active progress (`UiState.Loading("Importiere FIT-Dateien (X von Y)...")`).
- On completion, emit a structured `UiState.Success` or show an AlertDialog summary: e.g. *"X Workouts erfolgreich importiert, Y Duplikate übersprungen, Z Fehler"*.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Objectives
1. **Garmin FIT SDK Ingestion Engine**:
   - Implement `LegacyImportEngine.importFromFit(context, fitFile, listener, uploadToStrava)` and `importFromFitInternal(...)`.
   - Parse `FileIdMesg`, `SessionMesg`, `LapMesg`, `RecordMesg` using `Decode` and `MesgBroadcaster`.
   - Scale semicircles coordinates to decimal degrees with indoor null safety.
   - Populate `WorkoutSamplesDatabaseManager` with sensor streams (`LATITUDE`, `LONGITUDE`, `ALTITUDE`, `SPEED`, `DISTANCE`, `HEART_RATE`, `CADENCE`, `POWER`, `TEMPERATURE`).
   - Populate `LapsDatabaseManager` with parsed lap intervals.
   - Insert `WorkoutSummaries` row with `SOURCE = WorkoutSource.FIT.name`.
   - Execute `recalculateStats(...)` for polylines, extrema, and clustering.
2. **Duplicate Detection & Multi-Dimensional Prevention**:
   - Utilize existing `isWorkoutExisting(...)` check inside `importMutex.withLock`.
   - Skip duplicate workouts returning `ImportStatus.DUPLICATE_SKIPPED` without throwing errors or halting batch imports.
3. **ViewModel & Batch Ingestion**:
   - Add `BackupRestoreViewModel.importFitFiles(context, uris)` supporting single and multi-file URI collections.
   - Copy streamed content to temporary cache files, parse, clean up temporary files, and count imported, skipped, and failed files.
   - Trigger reactive repository refreshes (`WorkoutRepository`, `PeriodsRepository`, `WorkoutClusterRepository`).
4. **UI Presentation in `ImportBackupTabsScreen.kt`**:
   - Add a dedicated "FIT-Dateien importieren" button / card in `ImportTabContent`.
   - Wire multi-file document picker (`ActivityResultContracts.OpenMultipleDocuments()`).
   - Display progress indicator and completion summary.
5. **Localization Parity**:
   - Add all new strings (`import_fit_files_title`, `import_fit_files_description`, `import_fit_button`, `fit_import_summary`, etc.) across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 3.2 Out-of-Scope Objectives
- Modifying existing TCX, GPX, or database backup restore logic (preserved strictly as Chesterton's fences).
- Exporting FIT files (already fully implemented in ATT-1827).
- Live real-time streaming of FIT messages over ANT+ or Bluetooth (BANALService scope).
- External cloud API synchronization for FIT files (handled via Dropbox/Google Drive file sync).

---

## 4. Chesterton's Fence Requirement Archaeology (REQ-PRO-022)

1. **Original Requirement ID & Target**:
   - Net-new requirement: **`REQ-DAT-019`** (*FIT Workout Importer with Duplicate Detection and Sport Mapping*).
   - Complements `REQ-DAT-017` (*Workout Origin Source Provenance Attribute*), `REQ-DAT-018` (*Full-Telemetry FIT Exporter*), and `REQ-MIG-031` (*Multi-Dimensional Workout Deduplication*).
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-1828`, target release `V4.9.39`, active sprint `2026-40.14`.
   - Fulfills Child Feature 2 of Epic `ATT-1117` (*[Epic] FIT File Format Support (Export & Import)*).
3. **Root Reason for Existing Formulation**:
   - Historically, only TCX and GPX were supported by `LegacyImportEngine` because Garmin XML formats were simple text files parseable with Android's built-in `XmlPullParser`. Binary FIT required integrating the official Garmin FIT SDK dependency (`com.garmin:fit`), which was integrated in `ATT-1827`.
4. **Preservation of Core Invariants**:
   - `LegacyImportEngine.importFromTcx` and `importFromGpx` retain 100% identical behavior.
   - Database schema V24 with `WorkoutSummaries.SOURCE` is preserved without further schema version bumps.
   - Multi-dimensional deduplication window (180s epoch threshold) remains strictly intact.
   - Clean-room test suite 100% pass rate.

---

## 5. Proposed Requirement Formulation (`REQ-DAT-019`)

```markdown
| **REQ-DAT-019** | **External FIT Workout Importer with Duplicate Detection & Sport Mapping.** | The system SHALL import external binary Garmin FIT activity files (`.fit`) into the application's local workout database utilizing the official Garmin FIT SDK (`com.garmin:fit`):<br>1. *FIT Stream Decoding (`LegacyImportEngine.importFromFit`)*:<br>• The system SHALL utilize `com.garmin.fit.Decode` and `com.garmin.fit.MesgBroadcaster` to parse `.fit` input streams with CRC and header integrity checks.<br>• `RecordMesg`: The system SHALL extract second-by-second trackpoint records, converting semicircles to decimal degrees ($\text{degrees} = \text{semicircles} \times (180.0 / 2^{31})$), with null-safe omission for indoor records lacking GPS fix. The system SHALL buffer and insert samples for `LATITUDE`, `LONGITUDE`, `ALTITUDE`, `SPEED`, `DISTANCE`, `HEART_RATE`, `CADENCE`, `POWER`, and `TEMPERATURE` into `WorkoutSamplesDatabaseManager`.<br>• `LapMesg`: The system SHALL extract lap intervals and persist them into `LapsDatabaseManager`.<br>• `SessionMesg` / `ActivityMesg`: The system SHALL extract workout timing (`TIME_START`, elapsed time, timer time), total distance, calories, and averages/extrema for HR, speed, cadence, and power.<br>2. *Intelligent Sport Mapping*:<br>• The system SHALL map FIT `Sport` and `SubSport` enums to `BSportType` (`Sport.CYCLING` -> `BIKE`, `Sport.RUNNING` / `WALKING` -> `RUN`, `Sport.SWIMMING` / other -> `OTHER` / `UNKNOWN`) and resolve `SPORT_ID` via `SportTypeDatabaseManager`.<br>3. *Multi-Dimensional Duplicate Detection & Mutex Confinement*:<br>• Within `importMutex.withLock`, the system SHALL evaluate `isWorkoutExisting(summaryDb, fileBaseName, timeStart, bSportType)`. If a duplicate exists (exact file name, exact start time, or $\le 180$s epoch proximity with matching sport), the importer SHALL return `ImportStatus.DUPLICATE_SKIPPED` without throwing errors or interrupting batch imports.<br>4. *Provenance Attribution*:<br>• The imported workout summary SHALL be stored with `WorkoutSummaries.SOURCE = WorkoutSource.FIT.name`.<br>5. *Batch & Single UI Ingestion (`BackupRestoreViewModel`, `ImportBackupTabsScreen`)*:<br>• The system SHALL support single and multiple file selection (`ActivityResultContracts.OpenMultipleDocuments()`) for `.fit` files.<br>• `BackupRestoreViewModel.importFitFiles` SHALL process selected files sequentially, display progress, trigger post-import repository reconciliation, and present an import summary (imported count, skipped duplicates count, failed count).<br>6. *9-Language Localization Parity*:<br>• All user-facing strings SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). | Enable athletes to import historical and external workouts from Garmin, Wahoo, Hammerhead, and third-party devices with full sensor telemetry, duplicate prevention, and sport mapping. | `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `strings.xml` (9 locales) | `TST-DAT-014` | In Bearbeitung |
```

---

## 6. Risk Analysis & Mitigation Strategies

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Corrupted or Incomplete FIT Files** | Import crashes or partial SQLite records | Medium | Enforce `Decode.checkIntegrity()`, wrap file decoding in `try-catch`, and run sample insertion inside SQLite transactions with atomic rollback on failure. |
| **Indoor Workouts Missing GPS Fix** | Crashes or invalid $(0,0)$ coordinates on map | Medium | Check `mesg.getPositionLat() != null && mesg.getPositionLong() != null`. If null or invalid (`Fit.BASE_TYPE_SINT32_INVALID`), skip map point insertion while preserving HR, cadence, and power in SQLite. |
| **Duplicate Workout Bloat from Batch Import** | Cluttered workout history and corrupted stats | High | Check `isWorkoutExisting(...)` inside `importMutex.withLock` before creating tables or summary entries. Skip duplicate files cleanly and count as `DUPLICATE_SKIPPED`. |
| **Large Batch Import Memory Pressure** | `OutOfMemoryError` or UI freeze during 50+ file import | Low | Stream each file sequentially from cacheDir, copy from URI one-by-one, delete temp files immediately after processing, and run on `Dispatchers.IO`. |
| **Non-Standard Vendor Messages** | Parser fails on unknown third-party developer data | Low | Use standard `MesgBroadcaster` which safely ignores unrecognized developer fields and focuses on core Profile messages. |

---

## 7. Next Steps & Stage 2 Transition
1. Post this Stage 1 Analysis to subtask `ATT-2258`.
2. Transition `ATT-2258` to `In Überprüfung`.
3. Execute automated Gate 1 audit (`python3 tools/review_agent.py audit ATT-2258`).
4. Upon Gate 1 approval, register `REQ-DAT-019` in `docs/requirements.md` and commit.
5. Advance to Stage 2 (`[Req & Test Spec]`).
