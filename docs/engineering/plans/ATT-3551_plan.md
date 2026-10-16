# Stage 3: Implementation Plan - ATT-3551

## Ticket Overview
- **Ticket ID**: ATT-3551
- **Subtask ID**: ATT-3762
- **Summary**: Remove Obsolete Runkeeper and TrainingPeaks Legacy Classes, Manifest Entries, and Unused Apache HttpClient Dependency
- **Requirement ID**: `REQ-EXP-020`
- **Test Specification ID**: `TST-EXP-017`
- **Active Branch**: `feature/ATT-3551`

---

## 1. SWE.2 Architecture & Design Decisions

### 1.1 Architectural Context & Strategy
This plan implements `REQ-EXP-020`, permanently excising dead legacy code for Runkeeper and TrainingPeaks, purging unused `org.apache.httpcomponents:*` dependencies from the Gradle build graph, removing `<uses-library android:name="org.apache.http.legacy" />` and dead activities from `AndroidManifest.xml`, and deleting dead elevation helpers in `MyHelper.java`.

### 1.2 Inventory of Target Operations

#### 1.2.1 Files Scheduled for Deletion (7 files)
1. `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/runkeeper/RunkeeperGetAccessTokenActivity.java`
2. `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/runkeeper/RunkeeperTestActivity.java`
3. `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/trainingpeaks/TrainingpeaksGetAccessTokenActivity.java`
4. `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/BaseGetAccessTokenActivity.java`
5. `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/RunkeeperUploader.java`
6. `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/TrainingPeaksUploader.java`
7. `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/RunkeeperFileWriter.java`

*Note*: Deleting these files leaves `onlinecommunities/strava` as the sole active online community package and leaves directory trees `onlinecommunities/runkeeper/` and `onlinecommunities/trainingpeaks/` completely removed.

#### 1.2.2 Build & Manifest Files to Modify (3 files)
1. `app/build.gradle`:
   - Delete lines 144–148:
     ```groovy
     implementation 'org.apache.httpcomponents:fluent-hc:4.5.14'
     implementation 'org.apache.httpcomponents:httpclient:4.5.14'
     implementation 'org.apache.httpcomponents:httpclient-cache:4.5.14'
     implementation 'org.apache.httpcomponents:httpcore:4.4.16'
     implementation 'org.apache.httpcomponents:httpmime:4.5.14'
     ```
2. `app/proguard-rules.pro`:
   - Delete line 3: `-dontwarn org.apache.http.**`
3. `app/src/main/AndroidManifest.xml`:
   - Delete line 74: `<uses-library android:name="org.apache.http.legacy" android:required="false" />`
   - Delete lines 162–165: `<activity android:name=".trainingtracker.onlinecommunities.runkeeper.RunkeeperGetAccessTokenActivity" ... />`
   - Delete lines 181–184: `<activity android:name=".trainingtracker.onlinecommunities.trainingpeaks.TrainingpeaksGetAccessTokenActivity" ... />`

#### 1.2.3 Living Production Classes to Cleanse (5 files)
1. `app/src/main/java/com/atrainingtracker/trainingtracker/MyHelper.java`:
   - Delete lines 31–38 (`import org.apache.http.*`).
   - Delete lines 52–85 (`public static double getAltitudeFromUSGS(...)`).
   - Delete lines 89–122 (`public static double getElevationFromGoogleMaps(...)`).
2. `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java`:
   - Delete commented-out `/* case RUNKEEPER: ... case TRAINING_PEAKS: ... */` blocks at lines 84–88 and 97–102.
3. `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/BaseFileWriter.java`:
   - Delete commented-out lines 121–131 (`} /* else if (exportInfo.getFileFormat() == FileFormat.RUNKEEPER) ... */`).
4. `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/FileFormat.java` & `ExportType.java`:
   - Clean commented-out `RUNKEEPER` and `TRAINING_PEAKS` entries in enums and arrays.
5. `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`:
   - Remove dead preference keys (`PREFERENCE_SCREEN_RUNKEEPER`, `SP_UPLOAD_TO_RUNKEEPER`, `SP_RUNKEEPER_TOKEN`, `SP_UPLOAD_TO_TRAINING_PEAKS`, `SP_TRAINING_PEAKS_ACCESS_TOKEN`, `SP_TRAINING_PEAKS_REFRESH_TOKEN`, `SP_UPLOAD_RUNKEEPER_*`, `SP_UPLOAD_TRAINING_PEAKS_*`).
   - Remove dead preference getters and setters (lines 1093–1186).
   - Remove commented-out switch cases in `exportFileFormat()` and `uploadToCommunity()`.

#### 1.2.4 New Test File
1. `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/LegacyHttpClientPurgeContractTest.kt`:
   - Implements contract assertions for `TST-EXP-017`.

---

## 2. Atomic Step Sequencing

### Step 1: Implement Static Contract Test (`LegacyHttpClientPurgeContractTest.kt`)
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/LegacyHttpClientPurgeContractTest.kt`
- **Actions**:
  - Author unit test methods checking:
    1. Zero occurrences of `org.apache.http` in `app/src/main/java/`.
    2. Zero `org.apache.httpcomponents` in `app/build.gradle`.
    3. Zero `org.apache.http.legacy` in `app/src/main/AndroidManifest.xml`.
    4. Absence of `RunkeeperGetAccessTokenActivity` and `TrainingpeaksGetAccessTokenActivity` from `AndroidManifest.xml`.
    5. Absence of the 7 dead files on disk.
    6. Presence of `SportTypeDatabaseManager.SportType.RUNKEEPER_NAME`.

### Step 2: Delete Dead Classes via `git rm`
- **Actions**:
  - `git rm app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/runkeeper/RunkeeperGetAccessTokenActivity.java`
  - `git rm app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/runkeeper/RunkeeperTestActivity.java`
  - `git rm app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/trainingpeaks/TrainingpeaksGetAccessTokenActivity.java`
  - `git rm app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/BaseGetAccessTokenActivity.java`
  - `git rm app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/RunkeeperUploader.java`
  - `git rm app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/TrainingPeaksUploader.java`
  - `git rm app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/RunkeeperFileWriter.java`

### Step 3: Remove Manifest Entries and Legacy Library
- **Target File**: `app/src/main/AndroidManifest.xml`
- **Actions**:
  - Remove `<uses-library android:name="org.apache.http.legacy" android:required="false" />`.
  - Remove `<activity>` declarations for `RunkeeperGetAccessTokenActivity` and `TrainingpeaksGetAccessTokenActivity`.

### Step 4: Remove Gradle Dependencies and Proguard Rules
- **Target Files**: `app/build.gradle`, `app/proguard-rules.pro`
- **Actions**:
  - Excise lines 144–148 in `app/build.gradle`.
  - Excise `-dontwarn org.apache.http.**` in `app/proguard-rules.pro`.

### Step 5: Cleanse `MyHelper.java`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/MyHelper.java`
- **Actions**:
  - Delete `getAltitudeFromUSGS` and `getElevationFromGoogleMaps`.
  - Delete `org.apache.http.*` imports.

### Step 6: Cleanse `ExportManager.java`, `TrainingApplication.java`, and Export Enums
- **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/BaseFileWriter.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/FileFormat.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportType.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
- **Actions**:
  - Remove dead commented-out cases, unused preferences, accessors, and mutators.

### Step 7: Targeted Unit Test Verification
- **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.LegacyHttpClientPurgeContractTest"
  ```
- **Validation**: All contract test cases pass 100%.

### Step 8: Full Clean-Room Regression Suite Execution
- **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Validation**: Full test suite passes with 0 failures across all modules.

---

## 3. Non-Negotiable Invariants & Rollback Safety

1. **SQLite Database Schema Stability**:
   - Column `SportType.RUNKEEPER_NAME = "runkeeperName"` in `SportTypeDatabaseManager.java` is part of the existing SQLite table schema. It MUST NOT be modified or dropped.
2. **Active Exporter Functionality**:
   - Strava, Dropbox, Google Drive, TCX, GPX, FIT, CSV, and GC file exports and community uploads MUST continue functioning with 100% fidelity.
3. **Zero Apache HttpClient Footprint**:
   - Post-cleanup, zero references to `org.apache.http.*` shall remain in `app/src/main/java/` or `app/src/main/AndroidManifest.xml`.
4. **Git Atomic Commits**:
   - Code and tests committed cleanly on branch `feature/ATT-3551`.

---

## 4. UI Consistency (Rule 23)
- **Status**: Not Applicable (N/A).
- **Justification**: This ticket is a non-visual architectural cleanup, dependency reduction, and dead code excision task. No user interfaces, Compose layouts, or theme tokens are created or modified.
