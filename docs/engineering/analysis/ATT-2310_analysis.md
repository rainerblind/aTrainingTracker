# Stage 1 Analysis: ATT-2310 - Include FIT Format in Detailed Export Report, Export Status Tracking and Workout Header Export Menu

**Ticket**: [ATT-2310](https://rainerblind.atlassian.net/browse/ATT-2310)  
**Sub-task**: [ATT-2354](https://rainerblind.atlassian.net/browse/ATT-2354) (`[Analysis]`)  
**Parent Epic**: [ATT-1117](https://rainerblind.atlassian.net/browse/ATT-1117) (*FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2310`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During Sprint Review on physical device (Pixel 10) evaluating the newly introduced full-telemetry FIT workout exporter (ATT-1827), completed workout exports succeeded (the `.fit` binary file was successfully written and synchronized to Dropbox). However, inspecting the detailed export status bottom sheet (`SD-Karte`) in the Workout Details view revealed two prominent gaps:
1. **Defect 1: FIT Format Omitted from Detailed Export Status Dialog**:
   - In the `ExportDetailsDialog` (`ExportStatusGroup.kt`), the format breakdown lists `CSV`, `GC`, `TCX`, `GPX`, and `Strava`.
   - `FIT` is completely absent from the detailed export status report, leaving athletes unable to inspect the export status ('Erfolgreich', 'Nicht erwünscht', error messages) of the FIT file format.
2. **Defect 2: FIT Omitted from Workout Header Export Dropdown Menu**:
   - In `WorkoutHeader.kt`, the 3-dots overflow export menu offers direct on-demand export only to `TCX`, `GPX`, `CSV`, and `GC`.
   - `FIT` is missing from the list of export options, forcing athletes to navigate away or rely solely on automatic background export.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic investigation into the export database management layer, UI data providers, and header presentation components revealed the exact mechanisms causing these defects:

### 2.1 Missing Database Upsert Mechanism in `ExportStatusDatabaseManager`
In `ExportStatusDatabaseManager.java` (lines 117–141):
```java
public void updateExportStatus(ContentValues contentValues, String fileBaseName, ExportType exportType, FileFormat fileFormat) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    if (db == null) return;
    db.update(ExportStatusDbHelper.TABLE,
            contentValues,
            WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
            new String[]{fileBaseName, exportType.name(), fileFormat.name()});
}
```
* **No Pre-existing Rows for FIT in Older or Imported Workouts**:
  - `ExportManager.newWorkout(fileBaseName)` initializes rows for all `FileFormat.values()` and `ExportType.values()`. However, for workouts recorded prior to `FileFormat.FIT` being added, or for workouts imported without initializing all export rows, no row exists with `FORMAT = 'FIT'`.
  - When on-demand export or background export is triggered (`exportWorkoutTo(workoutId, FileFormat.FIT)` or `ExportWorker.updateStatus`), `ExportStatusDatabaseManager.updateExportStatus` executes a SQL `UPDATE`.
  - Because no row exists with `FORMAT = 'FIT'`, `db.update(...)` updates 0 rows and returns without inserting the row.
  - Furthermore, when `updateExportStatus` is called with `exportType == null` (e.g. `ExportManager.java` line 290 when WorkManager is unavailable), calling `exportType.name()` results in a `NullPointerException`.

### 2.2 Lack of Missing-Format Fallback Synthesis in `ExportStatusDataProvider` & `ExportStatusRepository`
In `ExportStatusDataProvider.kt` (lines 34–36):
```kotlin
val allRows = ExportStatusDatabaseManager.getInstance(context).getExportRows(fileBaseName)
val rows = allRows.filter { it.type == exportType }
```
* `ExportStatusDataProvider` (and `ExportStatusRepository`) directly iterates over SQLite rows retrieved from `ExportStatusDatabaseManager.getExportRows(fileBaseName)`.
* If a workout has existing export rows for an `ExportType` (e.g. `FILE`), but lacks a row for `FileFormat.FIT`, `rows` only contains the pre-existing formats (`CSV`, `GC`, `TCX`, `GPX`, `STRAVA`).
* Neither `ExportStatusDataProvider` nor `ExportStatusRepository` verifies that all formats defined in `exportType.exportToFileFormats` are present. Consequently, `FIT` is never rendered in `ExportDetailsDialog`.

### 2.3 Hardcoded Format List in `WorkoutHeader.kt`
In `WorkoutHeader.kt` (lines 382–387):
```kotlin
val standardFormats = listOf(
    FileFormat.TCX to R.string.tcxWrite,
    FileFormat.GPX to R.string.gpxWrite,
    FileFormat.CSV to R.string.csvWrite,
    FileFormat.GC to R.string.jsonWrite
)
```
* `WorkoutHeader.kt` hardcodes a static list of 4 export options, completely omitting `FileFormat.FIT`.
* String resource `fitWrite` is not yet defined in `res/values/strings.xml` or localized across the 9 supported languages.

---

## 3. Chesterton's Fence Archaeology & Git Trace

### 3.1 Historical Evolution of Export Status & FIT Exporter
1. **ATT-149 / REQ-UI-149** (commit `a9bc34d1`):
   - Modernized `ExportDetailsDialog` as an `AppModalBottomSheet` to display per-service and per-format status lines (`ExportStatusGroup.kt`).
2. **ATT-1827** (Sprint `2026-40.13`, commit `cf1120ab`):
   - Introduced `FitFileWriter.java` and full-telemetry FIT export capabilities via the Garmin FIT SDK.
   - Added `FileFormat.FIT` to `FileFormat.java`, `ExportType.DROPBOX`, and `ExportType.GOOGLE_DRIVE`.
   - `WorkoutHeader.kt` and `ExportStatusDatabaseManager` upsert handling were omitted from the scope of ATT-1827.
3. **ATT-602 / REQ-EXT-008** (`LegacyImportEngine.kt`):
   - Added defensive check `if (existingRows.isEmpty()) exportManager.newWorkout(baseFileName)`. For workouts with existing rows, new formats were not backfilled.

### 3.2 Invariants to Protect
* **Database Stability**: Existing rows in `ExportStatus.db` (`ExportManager` table) must not be corrupted or duplicated.
* **Localization Parity**: All newly introduced user-facing export strings must be localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **WorkManager Pipeline**: The asynchronous `ExportWorker` and `ExportManager.startFullExportProcess` pipeline must remain intact.

---

## 4. Proposed Architectural & Code Remediation

### 4.1 Database Layer: Safe Upsert in `ExportStatusDatabaseManager.java`
* Modify `updateExportStatus(ContentValues contentValues, String fileBaseName, ExportType exportType, FileFormat fileFormat)`:
  - If `db.update(...) == 0`:
    - Perform an `insert` into `ExportStatusDbHelper.TABLE` with `fileBaseName`, `TYPE = exportType.name()`, `FORMAT = fileFormat.name()`, `EXPORT_STATUS = contentValues.getAsString(EXPORT_STATUS)` (defaulting to `UNWANTED`), and `ANSWER = contentValues.getAsString(ANSWER)`.
  - If `exportType == null`:
    - Update all rows matching `FILE_BASE_NAME = ? AND FORMAT = ?`.
    - If 0 rows updated, insert rows for all `ExportType`s that include `fileFormat` in `getExportToFileFormats()`.
* Update `updateExportStatus(ContentValues contentValues, ExportInfo exportInfo)`:
  - Forward directly to `updateExportStatus(contentValues, exportInfo.getFileBaseName(), exportInfo.getExportType(), exportInfo.getFileFormat())`.

### 4.2 UI Provider Layer: Synthesis of Missing Supported Formats
* In `ExportStatusDataProvider.kt` and `ExportStatusRepository.kt`:
  - When generating `ExportStatusGroupData` for an `ExportType`, if existing rows are present for that type, compare the existing format set with `exportType.exportToFileFormats`.
  - For any format defined in `exportType.exportToFileFormats` that is absent from the database (such as `FileFormat.FIT` on legacy workouts), synthesize an `ExportRow` with `status = ExportStatus.UNWANTED` and `answer = null`.
  - Order the rendered `ExportDetail` items consistently according to `exportType.exportToFileFormats` index.

### 4.3 UI Presentation Layer: Include FIT in `WorkoutHeader.kt`
* In `WorkoutHeader.kt`, expand `standardFormats`:
  ```kotlin
  val standardFormats = listOf(
      FileFormat.TCX to R.string.tcxWrite,
      FileFormat.GPX to R.string.gpxWrite,
      FileFormat.FIT to R.string.fitWrite,
      FileFormat.CSV to R.string.csvWrite,
      FileFormat.GC to R.string.jsonWrite
  )
  ```
* Tapping `FIT` invokes `onExport(FileFormat.FIT)`, which triggers `WorkoutSummariesViewModel.onExportWorkoutTo(id, FileFormat.FIT)` -> `ExportManager.exportWorkoutTo(id, FileFormat.FIT)`.

### 4.4 9-Language Localization Parity
* Define string resource `fitWrite` across all 9 supported language directories:
  - `values/strings.xml`: `<string name="fitWrite">export to FIT</string>`
  - `values-de/strings.xml`: `<string name="fitWrite">export nach FIT</string>`
  - `values-es/strings.xml`: `<string name="fitWrite">exportar a FIT</string>`
  - `values-fr/strings.xml`: `<string name="fitWrite">exporter en FIT</string>`
  - `values-it/strings.xml`: `<string name="fitWrite">esporta in FIT</string>`
  - `values-ja/strings.xml`: `<string name="fitWrite">FIT出力</string>`
  - `values-nl/strings.xml`: `<string name="fitWrite">naar FIT exporteren</string>`
  - `values-pl/strings.xml`: `<string name="fitWrite">eksportuj do FIT</string>`
  - `values-pt/strings.xml`: `<string name="fitWrite">exportar para FIT</string>`

---

## 5. Scope Bounding & Invariants

### 5.1 In-Scope
* Database upsert in `ExportStatusDatabaseManager` for missing status rows.
* Dynamic synthesis and rendering of `FileFormat.FIT` in `ExportDetailsDialog` via `ExportStatusDataProvider` and `ExportStatusRepository`.
* Adding `FileFormat.FIT` to `WorkoutHeader.kt` 3-dots export menu.
* 9-language localization for `fitWrite`.
* Unit test coverage for database upsert, provider synthesis, and header contract.

### 5.2 Out-of-Scope
* Modifying the FIT binary serialization encoding in `FitFileWriter.java` (already verified in ATT-1827).
* Changing cloud upload workers for Dropbox or Google Drive.
* Modifying legacy TCX/GPX/CSV/GC exporters.

---

## 6. Verification & Test Strategy

1. **Unit Tests**:
   - `ExportStatusDatabaseManagerTest`: verify that calling `updateExportStatus` on a fileBaseName/format combination with no prior database row performs a safe INSERT and persists the status.
   - `ExportStatusDataProviderTest`: verify that `createGroupData` includes `FIT` with `UNWANTED` when given legacy rows containing only CSV, GC, TCX, GPX.
   - `WorkoutHeaderVisualContractTest`: verify `WorkoutHeader.kt` contains `FileFormat.FIT` in `standardFormats`.
2. **Clean-Room Regression**:
   - Execute full suite `./gradlew testDebugUnitTest`.
   - Validate debug APK assembly `./gradlew assembleDebug`.
