# Stage 2: Requirement & Test Specification - ATT-2186: Add Origin Source Attribute (Tracked, TCX, GPX, FIT) to Workouts

**Ticket**: [[ATT-2186]](https://atrainingtracker.atlassian.net/browse/ATT-2186)  
**Sub-task**: [[ATT-2188]](https://atrainingtracker.atlassian.net/browse/ATT-2188) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-281]](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-DAT-017` (*Workout Origin Source Provenance Attribute*)  
**Test Spec ID**: `TST-DAT-012` (*Workout Origin Source Provenance Verification*)  
**Branch**: `feature/ATT-2186`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-DAT-017)

### 1.1 Problem Statement & Rationale
In `aTrainingTracker`, workout sessions are recorded directly through sensor tracking (`TrackerService`) or imported via third-party files (`LegacyImportEngine` for TCX and GPX, and Garmin FIT SDK for upcoming FIT imports). Currently, `WorkoutSummaries.TABLE` stores extensive session statistics and classifications (sport, equipment, commute, trainer, race), but **lacks an explicit provenance attribute recording how the session entered the database**. 

Once imported or recorded, athletes cannot tell whether a workout was recorded live on their phone or imported from an external source, nor can downstream components (such as export filters or cloud synchronizers) differentiate between live recordings and external imports. Adding a dedicated `WorkoutSource` provenance attribute provides complete data transparency, traceability, and robust platform identity.

### 1.2 Functional & Architectural Requirements
The system SHALL store, maintain, and display the origin source of every workout session across the application lifecycle:

1. **Domain Model Definition (`WorkoutSource`)**:
   * The system SHALL define an enum `com.atrainingtracker.trainingtracker.database.WorkoutSource` with values:
     - `TRACKED`: Live sensor tracking on the Android device.
     - `TCX`: Imported Garmin Training Center XML file.
     - `GPX`: Imported GPS Exchange Format workout file.
     - `FIT`: Imported Garmin Flexible and Interoperable Data Transfer binary file.
   * `WorkoutSource` SHALL provide a robust parser `fromString(value: String?)` defaulting safely to `TRACKED` for null, blank, or unrecognized values.

2. **Database Schema Evolution (`WorkoutSummaries.db` v24)**:
   * In `WorkoutSummariesDatabaseManager.java`, the schema version SHALL be incremented: `DB_VERSION = 24`.
   * `WorkoutSummaries.TABLE` SHALL define constant `WorkoutSummaries.SOURCE = "source"`.
   * The `CREATE_TABLE` SQL statement SHALL include: `+ WorkoutSummaries.SOURCE + " text DEFAULT 'TRACKED',"`
   * In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`:
     - For `oldVersion < 24`, the system SHALL safely add the column:
       ```java
       addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE, "text", "'TRACKED'");
       ```
     - All existing historical records in SQLite SHALL safely evaluate to `'TRACKED'` with zero data loss.
   * In `updateWorkoutData(WorkoutData workoutData)`:
     - The system SHALL persist `values.put(WorkoutSummaries.SOURCE, workoutData.getSource().name());` to preserve source provenance across user edits.

3. **Ingestion Pipeline Provenance Tagging**:
   * **Live Tracking (`TrackerService.createNewWorkout`)**: The system SHALL explicitly persist `WorkoutSummaries.SOURCE = "TRACKED"`.
   * **TCX Import (`LegacyImportEngine.importTcxStream`)**: The system SHALL explicitly persist `WorkoutSummaries.SOURCE = "TCX"`.
   * **GPX Import (`LegacyImportEngine.importGpxWorkoutStream`)**: The system SHALL explicitly persist `WorkoutSummaries.SOURCE = "GPX"`.
   * **FIT Import (`FitFileImporter`)**: The system SHALL explicitly persist `WorkoutSummaries.SOURCE = "FIT"`.
   * **Backup Migration (`ImportEngine`)**: Database backup imports SHALL copy the `SOURCE` column dynamically when present, defaulting to `'TRACKED'` for legacy archives.

4. **Domain Mapping & In-Memory State**:
   * `WorkoutData.kt` SHALL expose `val source: WorkoutSource = WorkoutSource.TRACKED`.
   * `WorkoutHeaderData.kt` SHALL expose `val source: WorkoutSource = WorkoutSource.TRACKED`.
   * `WorkoutData.headerData` computed getter SHALL pass `source = source`.
   * `WorkoutDataMapper.fromCursor` SHALL extract the `SOURCE` column index safely with fallback to `WorkoutSource.TRACKED`.
   * `WorkoutRepository.saveWorkout` SHALL preserve `source = current.source` in the in-memory state copy.

5. **UI Presentation in WorkoutHeader**:
   * `WorkoutHeader.kt` SHALL render an origin badge for non-tracked workouts (`data.source != WorkoutSource.TRACKED`):
     - Rendered in Row A (sport metadata row) alongside sport name, equipment, and race badge.
     - Rendered as a subtle Material 3 `Surface` badge using tonal container styling (`colorScheme.surfaceVariant` / `onSurfaceVariant`).
     - Display localized origin label (e.g. "TCX", "GPX", "FIT").
     - Single-source implementation automatically enriches both `WorkoutSummary.kt` (list cards) and `TrackOnMapScreen.kt` (detailed full-screen view).

6. **100% 9-Language Localization Parity**:
   * User-facing origin strings (`workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, `workout_source_fit`) SHALL be defined with 100% parity across all 9 supported locales: English (EN), German (DE), Spanish (ES), French (FR), Italian (IT), Japanese (JA), Dutch (NL), Polish (PL), Portuguese (PT).

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Live Recording Provenance)**:
  * *Given* a workout recorded live on device via `TrackerService`,
  * *When* the workout is completed and committed to SQLite,
  * *Then* `WorkoutSummaries.SOURCE` is persisted as `'TRACKED'`.

* **Criterion 2 (File Import Provenance - TCX & GPX)**:
  * *Given* an external TCX or GPX activity file imported via `LegacyImportEngine`,
  * *When* the import transaction finishes,
  * *Then* `WorkoutSummaries.SOURCE` is persisted as `'TCX'` or `'GPX'` respectively.

* **Criterion 3 (Safe Schema Migration & Historical Default)**:
  * *Given* an existing v23 database with historical workouts,
  * *When* the database is upgraded to v24 via `onUpgrade`,
  * *Then* the `source` column is created, all historical workouts evaluate to `WorkoutSource.TRACKED`, and zero data is lost.

* **Criterion 4 (Domain Mapping & Repository State)**:
  * *Given* a cursor row with `source = 'TCX'`,
  * *When* mapped by `WorkoutDataMapper.fromCursor`,
  * *Then* `WorkoutData.source` and `WorkoutHeaderData.source` evaluate to `WorkoutSource.TCX`.

* **Criterion 5 (UI Header Origin Badge Visibility)**:
  * *Given* a workout with `source = WorkoutSource.TCX`,
  * *When* displayed in `WorkoutSummary` list card or `TrackOnMapScreen` details,
  * *Then* the header renders the distinct origin badge (`TCX`).
  * *Given* a workout with `source = WorkoutSource.TRACKED`,
  * *When* displayed in `WorkoutSummary` or `TrackOnMapScreen`,
  * *Then* the header renders cleanly without an unnecessary import badge.

### 1.4 System Invariants
1. **Zero Data Loss**: Existing historical workouts, extrema, cluster IDs, and spatial bounds are 100% preserved during v24 migration.
2. **Immutable Provenance**: The origin source attribute represents historical provenance and is not modified during normal field edits in `EditWorkoutScreen`.
3. **Single-Source UI Consistency**: `WorkoutHeader` is the single source of truth for header rendering across list cards and detail views.

---

## 2. Test Specification (TST-DAT-012)

### Test Case 1: `WorkoutSummariesDatabaseSourceMigrationTest` (`TST-DAT-012.1`)
* **Scope**: Unit Test (Robolectric / SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseSourceMigrationTest.kt`
* **Preconditions**: In-memory SQLite database initialized at schema version 23 containing historical workout records without a `source` column.
* **Action**: Execute `dbHelper.onUpgrade(db, 23, 24)`.
* **Expected Result**:
  * Column `source` exists in `WorkoutSummaries`.
  * All pre-existing historical workout records return `source == "TRACKED"`.
  * Inserting a new row with `source = "TCX"` persists and queries successfully.

### Test Case 2: `WorkoutDataSourceMappingTest` (`TST-DAT-012.2`)
* **Scope**: Unit Test (JVM)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataSourceMappingTest.kt`
* **Preconditions**: Mock Cursor configured with various `source` values (`"TRACKED"`, `"TCX"`, `"GPX"`, `"FIT"`, `null`, `"INVALID"`).
* **Action**: Invoke `WorkoutDataMapper.fromCursor(cursor)`.
* **Expected Result**:
  * Correct `WorkoutSource` enum mapping for all valid strings.
  * Fallback to `WorkoutSource.TRACKED` for null and unrecognized values.
  * Computed `workoutData.headerData.source` strictly matches `workoutData.source`.

### Test Case 3: `WorkoutIngestionSourceTaggingTest` (`TST-DAT-012.3`)
* **Scope**: Unit Test (Robolectric)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/WorkoutIngestionSourceTaggingTest.kt`
* **Preconditions**: Test TCX and GPX workout streams.
* **Action**: Parse and commit workouts via `LegacyImportEngine`.
* **Expected Result**:
  * TCX import records `source = 'TCX'` in SQLite.
  * GPX import records `source = 'GPX'` in SQLite.

### Test Case 4: `WorkoutHeaderSourceBadgeVisualContractTest` (`TST-DAT-012.4`)
* **Scope**: UI Visual Contract Test (Compose Test Rule)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderSourceBadgeVisualContractTest.kt`
* **Preconditions**: `WorkoutHeaderData` instances with `source = TRACKED`, `TCX`, `GPX`, `FIT`.
* **Action**: Render `WorkoutHeader` composable.
* **Expected Result**:
  * For `TRACKED`: Import badge is absent.
  * For `TCX`, `GPX`, `FIT`: Corresponding origin badge is displayed with correct text and container styling.

### Test Case 5: 9-Language Localization & Specifier Audit (`TST-DAT-012.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify string keys `workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, `workout_source_fit` exist across:
  * `values/` (EN)
  * `values-de/` (DE)
  * `values-es/` (ES)
  * `values-fr/` (FR)
  * `values-it/` (IT)
  * `values-ja/` (JA)
  * `values-nl/` (NL)
  * `values-pl/` (PL)
  * `values-pt/` (PT)
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 6: Clean-Room Regression Suite (`TST-DAT-012.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test success rate across the entire test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-012.1` | SQLite / Unit | `WorkoutSummariesDbHelper.onUpgrade(23, 24)` | `REQ-DAT-017` (items 2, 3) | Specified |
| `TST-DAT-012.2` | Unit | `WorkoutDataMapper.fromCursor`, `WorkoutSource.fromString` | `REQ-DAT-017` (items 1, 4) | Specified |
| `TST-DAT-012.3` | Integration | `LegacyImportEngine.importTcxStream`, `importGpxWorkoutStream` | `REQ-DAT-017` (item 3) | Specified |
| `TST-DAT-012.4` | UI Contract | `WorkoutHeader` | `REQ-DAT-017` (item 5) | Specified |
| `TST-DAT-012.5` | Localization | `TranslationParityTest` | `REQ-DAT-017` (item 6) | Specified |
| `TST-DAT-012.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
