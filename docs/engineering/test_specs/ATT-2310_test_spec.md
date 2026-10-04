# Stage 2: Requirement & Test Specification - ATT-2310: Include FIT Format in Detailed Export Report, Export Status Tracking and Workout Header Export Menu

**Ticket**: [ATT-2310](https://rainerblind.atlassian.net/browse/ATT-2310)  
**Sub-task**: [ATT-2355](https://rainerblind.atlassian.net/browse/ATT-2355) (`[Test-Spec]`)  
**Parent Epic**: [ATT-1117](https://rainerblind.atlassian.net/browse/ATT-1117) (*FIT File Format Support (Export & Import)*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-EXP-018`  
**Test Mapping**: `TST-EXP-015`  
**Branch**: `feature/ATT-2310`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (`REQ-EXP-018`)

### 1.1 Formal Specification
| Requirement ID | Module | Title | Target Description |
| :--- | :--- | :--- | :--- |
| **REQ-EXP-018** | **Export / UI** | **FIT Format Support in Detailed Export Report, Safe Database Upsert & Workout Header Export Menu Integration.** | The system SHALL include the FIT binary format in the detailed export status report, ensure database persistence via safe upsert in `ExportStatusDatabaseManager`, and provide direct on-demand FIT export in the workout header overflow menu (ATT-2310):<br>1. *Safe Database Upsert (`ExportStatusDatabaseManager.java`)*: When `updateExportStatus(contentValues, fileBaseName, exportType, fileFormat)` is called: (a) If `exportType != null`, the system SHALL execute `db.update(...)`. If `0` rows are affected, the system SHALL insert a new row into `ExportStatusDbHelper.TABLE` with `fileBaseName`, `TYPE = exportType.name()`, `FORMAT = fileFormat.name()`, `EXPORT_STATUS = contentValues.getAsString(EXPORT_STATUS)` (defaulting to `UNWANTED`), and `ANSWER = contentValues.getAsString(ANSWER)`. (b) If `exportType == null`, the system SHALL update all rows matching `FILE_BASE_NAME = ? AND FORMAT = ?`. If `0` rows are affected, the system SHALL insert rows for all `ExportType`s that support `fileFormat`. (c) `updateExportStatus(contentValues, exportInfo)` SHALL delegate cleanly to `updateExportStatus(contentValues, exportInfo.getFileBaseName(), exportInfo.getExportType(), exportInfo.getFileFormat())`.<br>2. *Supported Format Synthesis in UI Data Providers (`ExportStatusDataProvider.kt` & `ExportStatusRepository.kt`)*: In `createGroupData(...)`, when generating details for an `ExportType` that has existing export activity: (a) If any format defined in `exportType.exportToFileFormats` (such as `FileFormat.FIT` on legacy workouts) is absent from the database, the system SHALL synthesize an `ExportRow` with `status = ExportStatus.UNWANTED` and `answer = null`; (b) The rendered `ExportDetail` items in `ExportDetailsDialog` SHALL be ordered consistently according to `exportType.exportToFileFormats`.<br>3. *FIT Export Option in Workout Header Dropdown Menu (`WorkoutHeader.kt`)*: `standardFormats` in `WorkoutHeader.kt` SHALL include `FileFormat.FIT to R.string.fitWrite` alongside `TCX`, `GPX`, `CSV`, and `GC`. Selecting FIT SHALL invoke `onExport(FileFormat.FIT)`, triggering on-demand FIT export via `WorkoutSummariesViewModel` and `ExportManager`.<br>4. *100% 9-Language Localization Parity*: String resource `fitWrite` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).<br>5. *Preservation of System Invariants*: Asynchronous WorkManager workers (`ExportWorker`), cloud export options (Dropbox, Google Drive), existing table schemas, and 100% clean-room test suite pass rate MUST NOT be compromised. |

---

### 1.2 Given-When-Then Acceptance Criteria

#### Scenario 1: Viewing Detailed Export Status Bottom Sheet for Legacy Workout
* **Given** a workout recorded or imported prior to FIT format support (database rows exist only for `CSV`, `GC`, `TCX`, `GPX`, `STRAVA`),
* **When** the athlete opens the detailed export status bottom sheet (`ExportDetailsDialog`),
* **Then** `FIT` SHALL be rendered in the list with localized status `@string/unwanted` ("Nicht erwünscht").

#### Scenario 2: Upserting Export Status on On-Demand FIT Export
* **Given** a workout with no existing database row for `(fileBaseName, FILE, FIT)`,
* **When** on-demand FIT export is triggered,
* **Then** `ExportStatusDatabaseManager.updateExportStatus` SHALL perform an `insert` into `ExportStatusDbHelper.TABLE`, persisting status `WAITING` and subsequent `FINISHED_SUCCESS` with answer message.

#### Scenario 3: Exporting Workout to FIT via Workout Header 3-Dots Menu
* **Given** an athlete viewing a completed workout summary card in `WorkoutDetailsScreen` / `WorkoutSummariesListFragment`,
* **When** tapping the 3-dots overflow menu in `WorkoutHeader`,
* **Then** an option labeled with localized `fitWrite` (e.g. "export nach FIT" in German, "export to FIT" in English) SHALL be visible, and tapping it SHALL trigger export to `FileFormat.FIT`.

#### Scenario 4: Global Export Error Handling with Null ExportType
* **Given** a WorkManager scheduling failure where `updateExportStatus` is called with `exportType == null`,
* **When** executed,
* **Then** the system SHALL NOT throw `NullPointerException` and SHALL safely update or insert failure rows for supported export types.

---

## 2. Test Specification (`TST-EXP-015`)

### 2.1 Test Cases Breakdown

| Test Case ID | Class / Unit Under Test | Description & Validation Target | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TST-EXP-015.1` | `ExportStatusDatabaseManagerTest` | Verify `updateExportStatus` performs a safe `insert` (upsert) when row for `(fileBaseName, exportType, fileFormat)` does not exist. | Row is inserted with correct `fileBaseName`, `TYPE`, `FORMAT`, `EXPORT_STATUS`, and `ANSWER`. |
| `TST-EXP-015.2` | `ExportStatusDatabaseManagerTest` | Verify `updateExportStatus` safely handles `exportType == null` without throwing `NullPointerException`. | Existing rows for format are updated; missing rows are inserted. |
| `TST-EXP-015.3` | `ExportStatusDataProviderTest` | Verify `createGroupData` synthesizes `ExportRow` for `FileFormat.FIT` with status `UNWANTED` when given rows missing `FIT`. | `details` contains `FIT` formatted with `@string/unwanted` and ordered per `exportToFileFormats`. |
| `TST-EXP-015.4` | `ExportStatusDataProviderTest` | Verify `createGroupData` uses actual database status for `FIT` when a row for `FIT` is present (e.g. `FINISHED_SUCCESS`). | `details` reflects `FIT: Erfolgreich` / `FIT: Success`. |
| `TST-EXP-015.5` | `WorkoutHeaderExportMenuContractTest` | Verify `WorkoutHeader.kt` includes `FileFormat.FIT to R.string.fitWrite` in `standardFormats`. | Contract test passes with zero regressions. |
| `TST-EXP-015.6` | 9-Language Localization Audit | Audit string resource `fitWrite` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`. | All 9 locales contain non-empty, matching string values. |
| `TST-EXP-015.7` | Full Suite Clean-Room Regression | Run `./gradlew testDebugUnitTest`. | 100% pass rate with zero failures. |

---

## 3. Localization Parity Matrix (9 Locales for `fitWrite`)

| Language | Directory | Key | Translated String |
| :--- | :--- | :--- | :--- |
| **English** | `values/strings.xml` | `fitWrite` | `export to FIT` |
| **German** | `values-de/strings.xml` | `fitWrite` | `export nach FIT` |
| **Spanish** | `values-es/strings.xml` | `fitWrite` | `exportar a FIT` |
| **French** | `values-fr/strings.xml` | `fitWrite` | `exporter en FIT` |
| **Italian** | `values-it/strings.xml` | `fitWrite` | `esporta in FIT` |
| **Japanese** | `values-ja/strings.xml` | `fitWrite` | `FIT出力` |
| **Dutch** | `values-nl/strings.xml` | `fitWrite` | `naar FIT exporteren` |
| **Polish** | `values-pl/strings.xml` | `fitWrite` | `eksportuj do FIT` |
| **Portuguese** | `values-pt/strings.xml` | `fitWrite` | `exportar para FIT` |

---

## 4. Traceability Matrix

| Requirement Clause | Test Specification | Verification Method |
| :--- | :--- | :--- |
| `REQ-EXP-018` Clause 1 (Database Safe Upsert) | `TST-EXP-015.1`, `TST-EXP-015.2` | Automated Unit Test (`ExportStatusDatabaseManagerTest`) |
| `REQ-EXP-018` Clause 2 (UI Provider Missing Format Synthesis) | `TST-EXP-015.3`, `TST-EXP-015.4` | Automated Unit Test (`ExportStatusDataProviderTest`) |
| `REQ-EXP-018` Clause 3 (WorkoutHeader Export Menu) | `TST-EXP-015.5` | Architectural Visual Contract Test (`WorkoutHeaderExportMenuContractTest`) |
| `REQ-EXP-018` Clause 4 (9-Language Parity) | `TST-EXP-015.6` | Static Resource Validation across 9 locales |
| `REQ-EXP-018` Clause 5 (Full System Integrity) | `TST-EXP-015.7` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) |
