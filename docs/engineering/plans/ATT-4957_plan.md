# Stage 3: Implementation Plan - ATT-4957: Import Strava bulk export ZIP archives

**Ticket**: [ATT-4957](https://rainerblind.atlassian.net/browse/ATT-4957)  
**Sub-task**: [ATT-5434](https://rainerblind.atlassian.net/browse/ATT-5434) (`[Impl-Plan]`)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-4957`  
**Requirement Mapping**: `REQ-MIG-048` (`REQ-MIG-048.1`..`REQ-MIG-048.5`)  
**Test Mapping**: `TST-MIG-046` (`TST-MIG-046.1`..`TST-MIG-046.7`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Strava export archives (`export_<id>.zip`) contain metadata (`activities.csv`), gear (`bikes.csv`, `shoes.csv`), and compressed activity streams (`activities/*.fit.gz`, `*.tcx.gz`, `*.gpx.gz`). Current import engines only handle uncompressed standalone files. Furthermore, exported TCX streams frequently contain illegal leading spaces before `<?xml`, crashing `XmlPullParser`. This plan details streaming ZIP/GZIP ingestion, RFC 4180 bilingual CSV parsing, XML sanitization, gear/upload database reconciliation, and UI integration.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-MIG-048` (`REQ-MIG-048.1`..`REQ-MIG-048.5`).
* **Test Mapping**: `TST-MIG-046` (`TST-MIG-046.1`..`TST-MIG-046.7`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Multi-Gigabyte Staging**: Streaming archive/GZIP processing; no full unzipping to disk.
2. **Strict Security**: All archive entry paths sanitized against Zip-Slip (`REQ-MIG-040`, CWE-22).
3. **Deduplication**: Existing workouts are safely skipped (`DUPLICATE_SKIPPED`) without overwrite.
4. **Re-Upload Prevention**: Imported activities set `UPLOAD_TO_STRAVA = 0` and register in `StravaUploadDbHelper`.
5. **Existing Imports Intact**: Standalone FIT, TCX, and GPX file/folder imports remain 100% unregressed.

---

## 4. Proposed Architectural Changes

### Component 1: Bilingual RFC 4180 CSV Parser (`StravaCsvParser.kt`)
* Implement streaming parser reading `activities.csv`, `bikes.csv`, and `shoes.csv`.
* Handle multiline quoted fields, escaped quotes (`""`), commas, and alias maps for German (`Aktivitäts-ID`, `Dateiname`, `Name der Aktivität`, `Ausrüstung`) and English (`Activity ID`, `Filename`, `Activity Name`, `Gear`).
### Component 2: Tolerant XML Stream Sanitizer (`TrackpointModels.kt`)
* Enhance `stripBom()` to `stripBomAndLeadingWhitespace()`, skipping BOM and leading ASCII whitespace (` `, `\t`, `\r`, `\n`) before `<?xml`, curing `XmlPullParserException`.
### Component 3: Streaming Archive Importer Engine (`StravaBulkExportImporter.kt`)
* Ingests ZIP archive via `ZipFile` streaming; validates entry paths against Zip-Slip.
* Pre-loads gear from `bikes.csv`/`shoes.csv` into `EquipmentDbHelper` (`addOrUpdateStravaGear`).
* Maps each row in `activities.csv` to its decompressed stream (`GZIPInputStream`), passes to `TcxWorkoutParser`, `GpxWorkoutParser`, or `FitWorkoutParser`, enriches metadata (`exportName`, `description`, `goal`, `commute`, `sportId`, `equipmentId`), persists via `LegacyImportEngine.persistParsedWorkout()`, and registers Strava `Activity ID` in `StravaUploadDbHelper`.
### Component 4: UI & File Validator (`ImportBackupTabsScreen.kt`, `ImportFileValidator.kt`)
* Add `FormatImportCard` for Strava Bulk Export ZIP in `ImportTabContent`.
* Support `.zip` in `ImportFileValidator.isMatchingFormat()`.
* Add localized string resources across all 9 application locales.

### UI Consistency (Rule 23)
* **Reference component**: `FormatImportCard` in `ImportBackupTabsScreen.kt`.
* **Reused components**: `FormatImportCard`, `ElevatedCard`, `Button`, `LinearProgressIndicator`.
* **Theme tokens**: `MaterialTheme.colorScheme.surfaceVariant`, spacing: `16.dp`, shape: `RoundedCornerShape(12.dp)`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: CSV Parser & XML Stream Sanitizer (`REQ-MIG-048.2`, `REQ-MIG-048.3`)
* Files: `migration/StravaCsvParser.kt`, `routes/parsers/TrackpointModels.kt`.
* Tests: `StravaActivitiesCsvParserTest.kt`, `TcxLeadingWhitespaceSanitizationTest.kt`.

### Step 2: Stream Parser Adapter Overloads (`REQ-MIG-048.1`)
* Files: `migration/parsers/TcxWorkoutParser.kt`, `migration/parsers/GpxWorkoutParser.kt`.
* Changes: Add `InputStream` parsing overloads enabling direct stream consumption without temporary disk files.

### Step 3: Strava Bulk Export Importer Engine (`REQ-MIG-048.1`, `REQ-MIG-048.4`, `REQ-MIG-048.5`)
* Files: `migration/StravaBulkExportImporter.kt`, `migration/LegacyImportEngine.kt`.
* Tests: `StravaZipImportEngineTest.kt` (testing GZIP decompression, gear sync, Strava ID registration, deduplication).

### Step 4: UI Card, File Validator & 9-Locale Parity (`REQ-UI-106`, `REQ-MIG-048.1`)
* Files: `ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`, `ImportFileValidator.kt`, `values*/strings.xml`.
* Tests: `TranslationParityTest.kt`, `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `TST-MIG-046.1`..`TST-MIG-046.7` unit tests and verify 9-locale translation parity.
* **Rollback**: Clean branch isolation on `feature/ATT-4957`; atomic squashed commit in Stage 5.
