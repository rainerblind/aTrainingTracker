# Stage 2 Test Specification: ATT-1828 - FIT Workout Importer with Duplicate Detection and Sport Mapping

**Ticket**: [ATT-1828](https://rainerblind.atlassian.net/browse/ATT-1828)  
**Sub-task**: [ATT-2259](https://rainerblind.atlassian.net/browse/ATT-2259) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1117](https://rainerblind.atlassian.net/browse/ATT-1117) (*[Epic] FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1828`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Overview & Verification Strategy

This test specification defines the verification suite for `REQ-DAT-019` under ticket [ATT-1828](https://rainerblind.atlassian.net/browse/ATT-1828).

The objective is to validate that external Garmin FIT binary activity files (`.fit`) from bike computers, smartwatches, and virtual trainers can be imported with 100% sensor stream fidelity, accurate coordinate reconstruction, automatic sport type mapping, multi-dimensional duplicate prevention, and responsive UI batch progress feedback.

---

## 2. Requirement Traceability Matrix

| Requirement ID | Test Specification ID | Test Classes / Suites | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-019` (1: FIT Decoding & Samples) | `TST-DAT-014` (Group 1) | `LegacyImportEngineFitTest.kt` | Robolectric / JUnit Unit Test | Defined |
| `REQ-DAT-019` (1: Indoor Null-Safety) | `TST-DAT-014` (Group 2) | `LegacyImportEngineFitTest.kt` | Robolectric / JUnit Unit Test | Defined |
| `REQ-DAT-019` (2: Sport Mapping) | `TST-DAT-014` (Group 3) | `FitSportMappingTest.kt` | JUnit Unit Test | Defined |
| `REQ-DAT-019` (3: Deduplication) | `TST-DAT-014` (Group 4) | `LegacyImportEngineFitTest.kt` | Robolectric / SQLite Unit Test | Defined |
| `REQ-DAT-019` (4: Provenance Tagging) | `TST-DAT-014` (Group 1) | `LegacyImportEngineFitTest.kt` | Robolectric / SQLite Unit Test | Defined |
| `REQ-DAT-019` (5: Batch & UI Ingestion) | `TST-DAT-014` (Group 5) | `BackupRestoreViewModelFitTest.kt` | Robolectric / Coroutines Test | Defined |
| `REQ-DAT-019` (6: 9-Language Localization) | `TST-DAT-014` (Group 6) | `TranslationParityTest.kt` | JVM Unit Test | Defined |
| `REQ-ALL` (Regression Invariant) | `TST-DAT-014` (Group 7) | Full `./gradlew testDebugUnitTest` | Clean-Room CI Suite | Defined |

---

## 3. Concrete Test Cases (`TST-DAT-014`)

### Group 1: Binary FIT Decoding & Full Sensor Extraction
* **Test Class**: `com.atrainingtracker.trainingtracker.migration.LegacyImportEngineFitTest`
* **Test Case**: `testImportFromFit_createsWorkoutWithFullTelemetryAndSourceFit`
* **Preconditions**: In-memory or temporary SQLite test databases (`WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`).
* **Execution**:
  1. Generate a valid binary FIT file using `FileEncoder` containing `FileIdMesg`, `SessionMesg` (`Sport.CYCLING`), 2 `LapMesg` entries, and 10 `RecordMesg` trackpoints with GPS, barometric altitude, speed, distance, HR (145 bpm), cadence (85 rpm), power (220 W), and temperature ($21^\circ\text{C}$).
  2. Invoke `LegacyImportEngine.importFromFit(context, fitFile)`.
* **Assertions**:
  - Return value is `true` (`ImportStatus.SUCCESS`).
  - `WorkoutSummaries` row is created with matching `TIME_START`, `FINISHED = 1`, and `SOURCE == "FIT"`.
  - Dynamic table in `WorkoutSamplesDatabaseManager` exists with 10 sample rows.
  - Longitude and latitude match original decimal degrees within $10^{-6}$ precision ($\text{deg} = \text{semicircles} \times (180.0 / 2^{31})$).
  - Laps in `LapsDatabaseManager` contain 2 intervals matching lap timestamps and distances.

### Group 2: Indoor / Missing GPS Null-Safety
* **Test Class**: `com.atrainingtracker.trainingtracker.migration.LegacyImportEngineFitTest`
* **Test Case**: `testImportFromFit_indoorWorkoutWithoutGpsSucceeds`
* **Execution**:
  1. Generate a binary FIT file where `RecordMesg` trackpoints omit `positionLat` and `positionLong` (or set to invalid), but contain speed, cadence, HR, and power.
  2. Invoke `LegacyImportEngine.importFromFit(context, indoorFitFile)`.
* **Assertions**:
  - Import completes successfully with `ImportStatus.SUCCESS`.
  - Samples are recorded with null/NaN coordinates without throwing SQLite constraint errors.
  - Sensor streams (HR, cadence, power) are populated accurately in the samples table.

### Group 3: Sport & Subsport Mapping
* **Test Class**: `com.atrainingtracker.trainingtracker.migration.FitSportMappingTest`
* **Test Case**: `testFitSportMapping_resolvesCorrectBSportType`
* **Execution**:
  - `mapFitSport(Sport.CYCLING, SubSport.GENERIC)` $\rightarrow$ asserts `BSportType.BIKE`.
  - `mapFitSport(Sport.RUNNING, SubSport.GENERIC)` $\rightarrow$ asserts `BSportType.RUN`.
  - `mapFitSport(Sport.WALKING, SubSport.GENERIC)` $\rightarrow$ asserts `BSportType.RUN`.
  - `mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.INDOOR_CYCLING)` $\rightarrow$ asserts `BSportType.BIKE`.
  - `mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.INDOOR_RUNNING)` $\rightarrow$ asserts `BSportType.RUN`.
  - `mapFitSport(Sport.SWIMMING, SubSport.GENERIC)` $\rightarrow$ asserts `BSportType.OTHER`.
  - `mapFitSport(Sport.GENERIC, SubSport.GENERIC)` $\rightarrow$ asserts `BSportType.UNKNOWN`.

### Group 4: Multi-Dimensional Deduplication
* **Test Class**: `com.atrainingtracker.trainingtracker.migration.LegacyImportEngineFitTest`
* **Test Case**: `testImportFromFit_duplicateSkippedOnMatchingFilenameOrProximity`
* **Execution**:
  1. Import a FIT workout file $\rightarrow$ asserts `ImportStatus.SUCCESS`.
  2. Import the identical file again $\rightarrow$ asserts `ImportStatus.DUPLICATE_SKIPPED`.
  3. Rename the file to `different_name.fit` and re-import (same start timestamp) $\rightarrow$ asserts `ImportStatus.DUPLICATE_SKIPPED`.
  4. Create a file with start timestamp shifted by 60 seconds (within the 180s epoch threshold for the same sport) $\rightarrow$ asserts `ImportStatus.DUPLICATE_SKIPPED`.
  5. Create a file with start timestamp shifted by 600 seconds $\rightarrow$ asserts `ImportStatus.SUCCESS`.

### Group 5: Batch Processing & ViewModel Integration
* **Test Class**: `com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModelFitTest`
* **Test Case**: `testImportFitFiles_processesBatchAndSummarizesResults`
* **Execution**:
  1. Provide a batch of 4 mock URIs: 2 new valid workouts, 1 duplicate workout, 1 corrupt/empty file.
  2. Invoke `viewModel.importFitFiles(context, uris)`.
* **Assertions**:
  - ViewModel transitions through `UiState.Loading` with progress updates.
  - Final `UiState.Success` contains summary: 2 imported, 1 skipped duplicate, 1 failed.
  - Post-import repository reload is triggered.

### Group 6: 9-Language Localization Audit
* **Test Class**: `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`
* **Execution**:
  - Verify that all newly introduced string resources (`import_fit_files_title`, `import_fit_files_description`, `import_fit_button`, `fit_import_summary`, etc.) exist in:
    - `res/values/strings.xml` (EN)
    - `res/values-de/strings.xml` (DE)
    - `res/values-es/strings.xml` (ES)
    - `res/values-fr/strings.xml` (FR)
    - `res/values-it/strings.xml` (IT)
    - `res/values-ja/strings.xml` (JA)
    - `res/values-nl/strings.xml` (NL)
    - `res/values-pl/strings.xml` (PL)
    - `res/values-pt/strings.xml` (PT)
  - Verify identical format arguments (`%d`, `%s`) across all language variants.

### Group 7: Full Clean-Room Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Assertion**: 100% test pass rate with 0 regressions across all modules.

---

## 4. Gate 2 Verification Readiness
Upon approval of this Test Specification, subtask `ATT-2259` will transition to `Erledigt` via Gate 2 review, and software construction will proceed under Stage 3 Implementation Plan.
