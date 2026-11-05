# Stage 1 Analysis: ATT-4957 - Import Strava bulk export ZIP archives (activities.csv metadata + .fit/.tcx/.gpx.gz streams + gear)

**Ticket**: [ATT-4957](https://rainerblind.atlassian.net/browse/ATT-4957)  
**Sub-task**: [ATT-5432](https://rainerblind.atlassian.net/browse/ATT-5432) (`[Analysis]`)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-4957`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Athletes can download complete data archives from Strava (`export_<id>.zip`). Currently, `aTrainingTracker`'s `LegacyImportEngine` and `ImportBackupTabsScreen` only support standalone, uncompressed FIT, TCX, and GPX files. Stream files lack rich user metadata (custom workout names, descriptions, goals, commute flags, assigned equipment), which reside exclusively in `activities.csv`. Furthermore, Strava packages recording files inside `activities/` as `.gz` archives (`.fit.gz`, `.tcx.gz`, `.gpx.gz`), and exported TCX streams frequently contain illegal leading whitespace before `<?xml`, breaking Android's `XmlPullParser`. A dedicated Strava bulk ZIP import pipeline is required to ingest archives, parse bilingual CSV metadata, decompress streams on-the-fly, sanitize XML, synchronize equipment, and register Strava IDs to prevent re-upload loops.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Archive & Decompression Gap**: `LegacyImportEngine` operates on raw files and lacks ZIP entry streaming (`ZipFile`/`ZipInputStream`) and transparent GZIP decompression (`GZIPInputStream`) for `.fit.gz`, `.tcx.gz`, and `.gpx.gz`.
* **XML Declaration Whitespace Bug**: In real export sample `/home/rainer/Downloads/export_387065/activities/3131550008.tcx.gz`, decompressed data begins with 10 spaces (`b'          <?xml'`). `XmlPullParser` strictly enforces `<?xml` at document start, throwing `XmlPullParserException: Unexpected token`. `TrackpointModels.kt:stripBom()` only strips UTF-8 BOM (`0xEF, 0xBB, 0xBF`), failing on leading ASCII whitespace.
* **Metadata Disconnect**: `activities.csv` contains authoritative metadata (`Activity Name`, `Description`, `Private Note`, `Commute`, `Activity Type`, `Activity Gear`, `Activity ID`). Currently, imports assign generic fallback names, default sport types, and unlinked gear (`EQUIPMENT_ID = -1L`).
* **Bilingual & Multi-Line CSV Variance**: Strava exports localized headers (German sample has `Aktivitäts-ID`, `Dateiname`, `Name der Aktivität`; English has `Activity ID`, `Filename`, `Activity Name`). Descriptions contain embedded newlines, commas, and escaped quotes (`""`).
* **Independent Peer Review & Verification**:
  * Audited real Strava export `/home/rainer/Downloads/export_387065/`: 3,214 activities (`.tcx.gz`, `.gpx.gz`, `.gpx`), German `activities.csv`, `bikes.csv`, and `shoes.csv`. Confirmed 10-space leading whitespace in `3131550008.tcx.gz`.
  * Audited `EquipmentDbHelper.java`: `addOrUpdateStravaGear()` and `getIdFromStravaId()` / `getEquipmentId()` allow seamless gear matching/creation, populating `WorkoutSummaries.EQUIPMENT_ID`.
  * Audited `StravaUploadDbHelper.java`: Calling `updateAll(fileBaseName, activityId, activityId, "Uploaded", ...)` registers Strava IDs locally, preventing duplicate re-uploads when `uploadImportedWorkoutsToStrava()` is enabled.
  * Audited disk/memory efficiency: In-place streaming decompression via `ZipFile` avoids extracting 3,200+ files (~1+ GB) to internal storage.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * UI: Strava Archive import card in `ImportTabContent` (`ImportBackupTabsScreen.kt`) with ZIP file picker launcher.
  * Engine: `StravaZipImportEngine` (or `LegacyImportEngine.importFromStravaZip`) orchestrating archive ingestion.
  * CSV Parser: Lightweight RFC 4180 streaming CSV parser supporting German/English header aliases and multi-line quoted fields.
  * Decompression & Sanitization: `GZIPInputStream` stream decoding and `stripBomAndLeadingWhitespace` in `TrackpointModels.kt`.
  * Metadata Enrichment: Ingest `exportName`, `description`, `goal`, `commute`, `sportId`, and `equipmentId` into `WorkoutSummaries.db`.
  * Deduplication & Strava Link: Honor `isWorkoutExisting()`, mark `UPLOAD_TO_STRAVA = 0`, and register Strava ID in `StravaUploadDbHelper`.
  * Progress: Emit incremental `ProgressListener` callbacks for UI updates.
* **Out-of-Scope Non-Goals**:
  * Direct Strava Web API bulk downloads (Strava requires asynchronous user request via browser).
  * Media import from `media/` directory into local photo storage (covered by ATT-43).
  * Overwriting pre-existing workouts on timestamp collision (preserve skip on duplicate invariant).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-MIG-048` (Strava Bulk Export ZIP Archive Ingestion Pipeline). Extends `REQ-MIG-030` (Legacy GPX), `REQ-MIG-031` (Deduplication), `REQ-MIG-037` (Streaming XML Parsing), `REQ-MIG-038` (ParsedWorkoutData), and `REQ-MIG-040` (Secure Zip Extraction).
* **Historical Origin & Commit Trace**: `ATT-314` (Deduplication), `ATT-3982` (`REQ-MIG-040`), `ATT-4008` (TCX/GPX stream parsers).
* **Root Reason for Existing Formulation**: Initial import architecture was scoped to individual workout files and uncompressed directory scans.
* **Preservation of Core Invariants**: Retains mutex-guarded deduplication, transactional compensatory cleanup on failure, Zip-Slip path sanitization, and thread safety.

---

## 5. Architectural Strategy & High-Level Solution
* **Ingestion Pipeline**: Author `StravaZipImportEngine` reading `export_*.zip`. Extract `bikes.csv` and `shoes.csv` to seed/match `EquipmentDbHelper`. Stream `activities.csv` into memory map indexed by relative filename.
* **Stream Sanitizer**: Update `stripBom()` in `TrackpointModels.kt` to strip leading ASCII whitespace (` `, `\t`, `\r`, `\n`) before `<?xml`.
* **Activity Processing**: Iterate through CSV activity rows with associated stream files. Open ZIP entry, wrap in `GZIPInputStream` if `.gz`, pipe to `TcxWorkoutParser`, `GpxWorkoutParser`, or `FitWorkoutParser`, apply enriched metadata, and persist via `persistParsedWorkout()`. Register Strava `Activity ID` in `StravaUploadDbHelper`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression on standalone FIT, TCX, and GPX file/folder import pipelines.
  2. Strict memory & disk footprint: Streaming decompression per workout; no batch multi-gigabyte unzipping.
  3. Security: All ZIP entries validated against Zip-Slip path traversal (CWE-22).
  4. Deduplication: Pre-existing workouts are safely skipped (`DUPLICATE_SKIPPED`) without data loss.
  5. Strava Safety: Imported workouts marked `UPLOAD_TO_STRAVA = 0` to prevent duplicate cloud sync loops.
* **Risk Rating**: **MEDIUM** (New multi-format archive import pipeline and cross-db metadata enrichment; isolated from active recording).
