# Stage 1 Analysis: ATT-43 - Add photos taken during a workout

**Ticket**: [ATT-43](https://rainerblind.atlassian.net/browse/ATT-43)  
**Parent Epic**: None (*Standalone Feature*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Branch**: `feature/ATT-43`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Athletes capture photos during outdoor workouts (scenic viewpoints, summit landmarks, group selfies) and export archives from Strava containing workout media. Currently, `aTrainingTracker` lacks photo attachment, storage, and visualization capabilities: workouts cannot store media references, photos are omitted during Strava bulk ZIP import ([ATT-4957]), and photos cannot be previewed in workout details, displayed as markers on the workout map, or shared via [ATT-30]'s Photo Track Overlay.

---

## 2. Root Cause Analysis & Forensic Codebase Audit
* **SQLite Schema & Migration Gap**: `WorkoutSummariesDatabaseManager.java` (`DB_VERSION = 26`) lacks a `workout_photos` table. Incrementing to version 27 requires symmetrical `onCreate` / `onUpgrade` definitions (Rule 2.3), fixing `DatabaseSchemaRegistry.kt` (which hardcodes obsolete literal `25` instead of referencing `DB_VERSION` directly per Rule 2.2), and adding cascading photo deletion in `WorkoutSummariesDatabaseManager.deleteWorkout` and `WorkoutDeletionHelper.java`.
* **EXIF Metadata & Track Interpolation Gap**: EXIF capture timestamps (`TAG_DATETIME_ORIGINAL`) and GPS coordinates must be extracted (`android.media.ExifInterface`). When GPS coordinates are absent, coordinates must be interpolated by querying adjacent timestamped trackpoints in `WorkoutSamples.db` at `taken_at_ms` when within the workout's time window.
* **Strava Bulk Export Media Synergy ([ATT-4957])**: `StravaCsvParser.kt` parses `activities.csv` but currently ignores the bilingual `Medien` / `Media` column (pipe-separated paths) and `media.csv` (captions).
* **UI & Map Presentation Gap**: `WorkoutDetailTabbedContent.kt` and `WorkoutSummary.kt` lack a horizontal photo strip. `WorkoutRepository.getWorkoutMarkers()` lacks photo pin markers for `TrackOnMapScreen.kt` and `ATrainingTrackerMap.kt`.
* **Independent Peer Review & Verification**:
  * Audited real Strava export `/home/rainer/Downloads/export_387065/`: `activities.csv` formats media as pipe-separated entries (e.g. `media/6e431192-e73c-4de7-8874-6bd5d7954976.jpg|media/43a9112f-5111-460b-ad2b-f3c765bba0cf.jpg`); `media.csv` has `Medien-Dateiname,Medienunterschrift` headers; Strava strips EXIF from exported images, proving the absolute necessity of falling back to workout start time and track interpolation.
  * Audited `WorkoutShareBottomSheet.kt` ([ATT-30]): Already declares `attachedPhotos: List<Uri>`, allowing seamless forwarding into `PhotoTrackOverlayComposer`.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Guardrail
* **In-Scope Goals**:
  * DB & Storage: `workout_photos` table (`_id`, `workout_id`, `file_path`, `taken_at_ms`, `latitude`, `longitude`, `caption`) in `WorkoutSummaries.db` (v27) + app-private storage (`filesDir/workout_photos/<workout_id>/`).
  * Ingestion: System Photo Picker (`PickMultipleVisualMedia`) in Workout Details / Edit Workout; Strava ZIP archive photo & caption extraction in `StravaBulkExportImporter`.
  * Metadata & Geo-coding: EXIF extraction (`TAG_DATETIME_ORIGINAL`, lat/lon) with fallback GPS track interpolation from `WorkoutSamples.db`.
  * UI & Map: Horizontal photo strip in Workout Details / Summary; `AppModalBottomSheet` photo preview with [ATT-30] overlay share and delete action; map photo pins in `TrackOnMapScreen` / `ATrainingTrackerMap`.
  * Deletion: Cascade deletion of DB rows and disk files on single photo or full workout deletion.
  * Localization: 9-language string parity (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Out-of-Scope Non-Goals**: In-app camera hardware capture; photo cropping/filtering; cloud photo sync.
* **Complexity Audit**: 3 SP (Standard multi-component feature spanning DB, importer, UI, and map layers).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-new requirements**: `REQ-DAT-024` (Workout Photo Storage & EXIF/GPS Track Interpolation) and `REQ-UI-450` (Workout Photo Strip, Viewer, and Map Pins).
* **Extensions**: Extends `REQ-MIG-048` (Strava Bulk Export Ingestion) and `REQ-UI-447` (Photo Track Overlay Share).
* **Core Invariants Preserved**: Zero regression on existing workout summaries; lossless workout deletion; asynchronous disk/bitmap IO; strict Zip-Slip path sanitization.

---

## 5. Architectural Strategy & High-Level Solution
* **Persistence & Schema v27**: Add `workout_photos` table and `idx_workout_photos_workout_id` to `WorkoutSummariesDatabaseManager.java`. Update `DatabaseSchemaRegistry.kt` to reference `DB_VERSION` directly.
* **Photo Manager (`WorkoutPhotoManager`)**: Handles copying selected/imported images to `filesDir/workout_photos/<id>/`, parsing EXIF, querying `WorkoutSamples.db` for GPS track interpolation, and purging on deletion.
* **Strava Import**: `StravaCsvParser` parses `Medien`/`Media` and `media.csv`; `StravaBulkExportImporter` unzips photos, links captions, and persists photos.
* **Presentation**: `WorkoutPhotoStrip` in `WorkoutSummary` / `WorkoutDetails`; `WorkoutPhotoViewerSheet` (`AppModalBottomSheet`); `LocationMarker` photo pins in `WorkoutRepository.getWorkoutMarkers()`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Symmetrical SQLite evolution: `onCreate` and `onUpgrade` yield identical schemas; verified by migration contract test.
  2. Complete cleanup: Deleting a workout removes its SQLite photo rows and physical files from disk.
  3. Memory safety: Downsampled thumbnail decoding via `PhotoTrackOverlayComposer.decodeSampledBitmap` avoids OOM.
* **Risk Rating**: **MEDIUM** (SQLite schema migration and file IO; fully decoupled from foreground tracking service).
