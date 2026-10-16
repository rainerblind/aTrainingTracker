# Stage 2: Requirement & Test Specification - ATT-3551

## Ticket Overview
- **Ticket ID**: ATT-3551
- **Subtask ID**: ATT-3761
- **Summary**: Remove Obsolete Runkeeper and TrainingPeaks Legacy Classes, Manifest Entries, and Unused Apache HttpClient Dependency
- **Requirement ID**: `REQ-EXP-020`
- **Test Specification ID**: `TST-EXP-017`
- **Active Branch**: `feature/ATT-3551`

---

## 1. Formal Requirement Specification (`REQ-EXP-020`)

### 1.1 Requirement Title
**Decommission Obsolete Runkeeper and TrainingPeaks Legacy Exporters, Activities, and Unused Apache HttpClient Dependencies**

### 1.2 Description
The system SHALL decommission obsolete, unmaintained Runkeeper and TrainingPeaks integrations, delete dead token extraction activities, uploaders, and file writers, remove unused manifest elements and legacy Apache HttpClient dependencies, and eliminate dead elevation helpers in `MyHelper.java`, enforcing Rule 18 (Dependency Cleanliness), Rule 23 (Dead Code Removal), and Rule 36 (Single Source of Truth & DRY) (ATT-3551):

1. **Decommission Dead Runkeeper & TrainingPeaks Classes & Activities**:
   - The system SHALL permanently excise the following obsolete source files from the codebase:
     - `onlinecommunities/runkeeper/RunkeeperGetAccessTokenActivity.java`
     - `onlinecommunities/runkeeper/RunkeeperTestActivity.java`
     - `onlinecommunities/trainingpeaks/TrainingpeaksGetAccessTokenActivity.java`
     - `onlinecommunities/BaseGetAccessTokenActivity.java`
     - `exporter/uploader/RunkeeperUploader.java`
     - `exporter/uploader/TrainingPeaksUploader.java`
     - `exporter/writer/RunkeeperFileWriter.java`
   - The system SHALL remove `<activity>` declarations for `RunkeeperGetAccessTokenActivity` and `TrainingpeaksGetAccessTokenActivity` from `app/src/main/AndroidManifest.xml`.

2. **Decommission Unused Apache HttpClient Dependencies & Legacy Platform Library**:
   - The system SHALL remove `<uses-library android:name="org.apache.http.legacy" android:required="false" />` from `app/src/main/AndroidManifest.xml`.
   - The system SHALL remove all 5 `org.apache.httpcomponents:*` dependencies (`fluent-hc`, `httpclient`, `httpclient-cache`, `httpcore`, `httpmime`) from `app/build.gradle`.
   - The system SHALL remove `-dontwarn org.apache.http.**` from `app/proguard-rules.pro`.

3. **Purge Dead Elevation Helpers and Imports in `MyHelper.java`**:
   - The system SHALL remove dead, uncalled methods `getAltitudeFromUSGS(double, double)` and `getElevationFromGoogleMaps(double, double)` from `MyHelper.java`.
   - The system SHALL remove all unused `org.apache.http.*` imports from `MyHelper.java`.

4. **Cleanse Lingering References in Living Production Classes**:
   - `ExportManager.java`: The system SHALL remove dead commented-out cases for `RUNKEEPER` and `TRAINING_PEAKS` in `getExporter(ExportInfo)`.
   - `BaseFileWriter.java`: The system SHALL remove dead commented-out selective setting blocks for `RUNKEEPER` and `TRAINING_PEAKS`.
   - `FileFormat.java`: The system SHALL remove dead commented-out enum entries and arrays for `RUNKEEPER` and `TRAINING_PEAKS`.
   - `ExportType.java`: The system SHALL remove dead commented-out entries for `FileFormat.RUNKEEPER` and `FileFormat.TRAINING_PEAKS`.
   - `TrainingApplication.java`: The system SHALL remove dead preference constants (`PREFERENCE_SCREEN_RUNKEEPER`, `SP_UPLOAD_TO_RUNKEEPER`, `SP_RUNKEEPER_TOKEN`, `SP_UPLOAD_TO_TRAINING_PEAKS`, `SP_TRAINING_PEAKS_ACCESS_TOKEN`, `SP_TRAINING_PEAKS_REFRESH_TOKEN`, `SP_UPLOAD_RUNKEEPER_*`, `SP_UPLOAD_TRAINING_PEAKS_*`), dead preference accessors/mutators (lines 1093–1186), and dead commented switch cases in `exportFileFormat()` and `uploadToCommunity()`.

5. **Preservation of System Invariants**:
   - *Database Schema Invariant*: Column `SportType.RUNKEEPER_NAME = "runkeeperName"` in `SportTypeDatabaseManager.java` is part of the persistent SQLite schema and SHALL NOT be dropped or modified in this ticket.
   - *Active Exporter Invariant*: All supported exporters (Strava, Dropbox, Google Drive, TCX, GPX, FIT, CSV, GC) MUST retain 100% operational integrity.
   - *Zero Apache Invariant*: Production source code under `app/src/main/` SHALL contain strictly 0 occurrences of `org.apache.http`.
   - *Clean-Room Pass Invariant*: The full unit test suite (`./gradlew testDebugUnitTest`) MUST pass with 100% success.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-EXP-020`) targeting dead legacy code excision and dependency pruning across `app/build.gradle`, `AndroidManifest.xml`, `MyHelper.java`, `ExportManager.java`, and `TrainingApplication.java`.
2. **Historical Origin & Commit Trace**: Early git history (2011–2015). Runkeeper and TrainingPeaks were early upload integrations using Apache HttpClient. In Android 6.0 (API 23), Google removed Apache HttpClient from the platform, prompting the addition of `org.apache.httpcomponents:*` dependencies and `org.apache.http.legacy`. As `aTrainingTracker` adopted OkHttp3 and Coroutines for Strava and modern services, the legacy uploaders were commented out rather than deleted.
3. **Root Reason for Existing Formulation**: The code and dependencies were left dormant to avoid build breakage when uploaders were deactivated.
4. **Preservation of Core Invariants**: Retaining SQLite database schema column `SportType.RUNKEEPER_NAME` guarantees zero migration breakage for existing databases. All active exporters remain completely intact.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Zero Apache HttpClient References in Main Source**:
  - *Given* the production source files under `app/src/main/java/`,
  - *When* scanned for `org.apache.http`,
  - *Then* strictly 0 occurrences or imports are found.

- **Scenario 2: Absence of Apache Dependencies and Manifest Entries**:
  - *Given* `app/build.gradle` and `app/src/main/AndroidManifest.xml`,
  - *When* evaluated for legacy HTTP client entries,
  - *Then* `org.apache.httpcomponents` dependencies and `<uses-library android:name="org.apache.http.legacy"` are completely absent.

- **Scenario 3: Permanent Excision of Dead Activities and Classes**:
  - *Given* the file system under `app/src/main/java/`,
  - *When* checking the locations of `RunkeeperGetAccessTokenActivity.java`, `RunkeeperTestActivity.java`, `TrainingpeaksGetAccessTokenActivity.java`, `BaseGetAccessTokenActivity.java`, `RunkeeperUploader.java`, `TrainingPeaksUploader.java`, and `RunkeeperFileWriter.java`,
  - *Then* none of these files exist on disk.

- **Scenario 4: Manifest Activity Pruning**:
  - *Given* `app/src/main/AndroidManifest.xml`,
  - *When* inspected for `<activity>` declarations,
  - *Then* neither `RunkeeperGetAccessTokenActivity` nor `TrainingpeaksGetAccessTokenActivity` is present.

- **Scenario 5: Database Schema Invariant Preservation**:
  - *Given* `SportTypeDatabaseManager.java`,
  - *When* inspecting the SQLite table schema definition,
  - *Then* `SportType.RUNKEEPER_NAME` remains present and functional to guarantee schema backward compatibility.

- **Scenario 6: Clean-Room Regression Pass**:
  - *Given* the full project test suite,
  - *When* executing `./gradlew testDebugUnitTest`,
  - *Then* all tests pass with 0 errors.

---

## 4. Test Specification (`TST-EXP-017`)

### 4.1 Test Scope & Traceability
- **Test ID**: `TST-EXP-017`
- **Traced Requirement**: `REQ-EXP-020`
- **Test File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/LegacyHttpClientPurgeContractTest.kt`

### 4.2 Test Cases
1. `verifyNoApacheHttpImportsInMainSource()`:
   - Scans all `.java` and `.kt` source files under `app/src/main/java/`.
   - Asserts that no file contains `org.apache.http`.
2. `verifyNoApacheHttpInDependencies()`:
   - Reads `app/build.gradle`.
   - Asserts that `org.apache.httpcomponents` is not present in dependency declarations.
3. `verifyNoApacheHttpInManifest()`:
   - Reads `app/src/main/AndroidManifest.xml`.
   - Asserts that `org.apache.http.legacy` is absent.
4. `verifyDeadActivitiesRemovedFromManifest()`:
   - Reads `app/src/main/AndroidManifest.xml`.
   - Asserts that `RunkeeperGetAccessTokenActivity` and `TrainingpeaksGetAccessTokenActivity` are absent.
5. `verifyDeadClassesRemovedFromDisk()`:
   - Checks that all 7 targeted dead class files do not exist.
6. `verifySportTypeRunkeeperDbColumnPreserved()`:
   - Asserts that `SportTypeDatabaseManager.SportType.RUNKEEPER_NAME` equals `"runkeeperName"`.
