# Stage 3: Implementation Plan - ATT-43: Add photos taken during a workout

**Ticket**: [ATT-43](https://rainerblind.atlassian.net/browse/ATT-43)  
**Parent Epic**: None (*Standalone Feature*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-43`  
**Requirement Mapping**: `REQ-DAT-038`, `REQ-UI-448`, `REQ-MIG-048.6`  
**Test Mapping**: `TST-DAT-034`, `TST-UI-448`, `TST-MIG-046.8`, `TST-LOC-043`, `TST-REG-043`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Athletes capture photos during outdoor workouts and export archives containing workout media from Strava. Currently, `aTrainingTracker` lacks photo persistence, ignores media during Strava bulk export import ([ATT-4957]), and lacks photo preview, map pins, and direct sharing via [ATT-30]. This plan defines the end-to-end implementation across SQLite schema v27, EXIF/GPS interpolation, Strava archive media ingestion, and Compose UI/Map layers.

---

## 2. Invariants & Existing User Upgrade Behavior (Rule 5.6)
* **What changes for an existing user after update**: SQLite upgrades from v26 to v27 creating empty table `workout_photos` and index. Existing workouts display an empty-state "Add Photos" action; all existing summaries, metrics, and tracks remain 100% unaltered.
* **Core Invariants**: Symmetrical `onCreate`/`onUpgrade` parity (Rule 2.3); `DatabaseSchemaRegistry` directly references `DB_VERSION` (Rule 2.2); cascading deletion cleans DB and disk files; memory-safe sampled decoding (`inSampleSize`); Zip-Slip protection; 9-language localization parity without inline emojis ([ATT-5400]).

---

## 3. Proposed Architectural Changes

### Component 1: SQLite Schema v27 & Cascading Purge (`REQ-DAT-038.1`, `REQ-DAT-038.3`)
* **`WorkoutSummariesDatabaseManager.java`**: Bump `DB_VERSION = 27`. In both `onCreate` and `onUpgrade(oldVersion < 27)`, create table `workout_photos` (`_id INTEGER PRIMARY KEY AUTOINCREMENT, workout_id INTEGER NOT NULL, file_path TEXT NOT NULL, taken_at_ms INTEGER, latitude REAL, longitude REAL, caption TEXT`) and `idx_workout_photos_workout_id`. Add photo methods (`insertPhoto`, `getPhotosForWorkout`, `deletePhoto`).
* **Cascade Deletion**: In `deleteWorkout(workoutId)` and `WorkoutDeletionHelper.deleteWorkout(workoutId)`, delete rows from `workout_photos` and delete folder `filesDir/workout_photos/<workoutId>/`.
* **`DatabaseSchemaRegistry.kt`**: Map `"WorkoutSummaries.db"` to `WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_VERSION` directly.

### Component 2: Photo Manager & EXIF/GPS Track Interpolation (`REQ-DAT-038.2`)
* **`WorkoutPhotoManager.kt`**: Coordinates copying URIs to `filesDir/workout_photos/<workout_id>/`, extracts EXIF `TAG_DATETIME_ORIGINAL` and GPS coordinates (`ExifInterface`). When GPS is absent, interpolates `(latitude, longitude)` from `WorkoutSamplesDatabaseManager` trackpoints matching `taken_at_ms` within `[startTime, endTime]` bounds.

### Component 3: Strava Bulk Export Media Ingestion (`REQ-MIG-048.6`)
* **`StravaCsvParser.kt`**: Parse pipe-separated `Medien` / `Media` in `activities.csv`; parse `media.csv` captions (`Medien-Dateiname,Medienunterschrift`).
* **`StravaBulkExportImporter.kt`**: Extract `media/*` files to `filesDir/workout_photos/<id>/` with Zip-Slip path sanitization, fall back to workout start time when EXIF is stripped, and persist records in `workout_photos`.

### Component 4: UI Strip, Viewer Sheet, ATT-30 Share & Map Pins (`REQ-UI-448.1`..`REQ-UI-448.4`)
* **Photo Strip**: Add `WorkoutPhotoStrip` in `WorkoutDetails.kt` / `WorkoutDetailTabbedContent.kt` with thumbnails (`RoundedCornerShape(8.dp)`) and `PickMultipleVisualMedia` launcher.
* **`WorkoutPhotoViewerSheet.kt`**: `AppModalBottomSheet` displaying full image preview, timestamp/caption, delete action, and direct handoff to `WorkoutShareBottomSheet` ([ATT-30]). Wire `attachedPhotos` into `WorkoutShareBottomSheet.kt`.
* **Map Pins**: In `WorkoutRepository.getWorkoutMarkers()`, generate photo `LocationMarker`s on `TrackOnMapScreen.kt` and `ATrainingTrackerMap.kt`.

### UI Consistency (Rule 23)
* **Reference**: `WorkoutDetails.kt`, `WorkoutShareBottomSheet.kt`, `TrackOnMapScreen.kt`. Reuses `AppModalBottomSheet`, `Card`, `OutlinedButton`, `Button`, `Icon`.
* **Theme Tokens**: `MaterialTheme.colorScheme`, `RoundedCornerShape(12.dp)` for sheet, `8.dp` for thumbnails, `16.dp` horizontal padding. No one-off styles.

---

## 4. Step-by-Step Implementation Sequence

1. **Step 1 (Schema v27 & Cascading Cleanup)**: Update `WorkoutSummariesDatabaseManager.java` (`DB_VERSION = 27`, `workout_photos` table/index in `onCreate`/`onUpgrade`, photo CRUD), `DatabaseSchemaRegistry.kt`, and `WorkoutDeletionHelper.java`.
2. **Step 2 (Photo Manager & EXIF/Interpolation)**: Implement `WorkoutPhotoManager.kt` with file copying, EXIF extraction, and `WorkoutSamples` trackpoint interpolation.
3. **Step 3 (Strava Media Ingestion)**: Update `StravaCsvParser.kt` and `StravaBulkExportImporter.kt` to ingest `Medien` and `media.csv` photos into `workout_photos`.
4. **Step 4 (UI Strip, Viewer Sheet & Map Pins)**: Build `WorkoutPhotoStrip`, `WorkoutPhotoViewerSheet.kt`, wire `attachedPhotos` into `WorkoutShareBottomSheet.kt`, and add photo pins in `WorkoutRepository.getWorkoutMarkers()`.
5. **Step 5 (Localization & Tests)**: Update 9 `strings.xml` locales; create `WorkoutSummariesMigrationV27Test.kt`, `WorkoutPhotoManagerTest.kt`, `StravaBulkExportMediaTest.kt`, and `WorkoutPhotoUiContractTest.kt`.

---

## 5. Verification & Rollback Plan
* **Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Rollback**: Clean branch isolation on `feature/ATT-43`; atomic squashed commit on `sprint/2026-45.6` in Stage 5.
