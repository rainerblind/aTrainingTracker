# Stage 1: Problem Domain & Root Cause Analysis (RCA) - ATT-3551

## Ticket Overview
- **Ticket ID**: ATT-3551
- **Subtask ID**: ATT-3760
- **Summary**: Remove Obsolete Runkeeper and TrainingPeaks Legacy Classes, Manifest Entries, and Unused Apache HttpClient Dependency
- **Type**: Improvement (Verbesserung)
- **Component**: Clean Code / Architecture / Dependency Optimization (Epic ATT-431)
- **Sprint**: 2026-42.5
- **Active Branch**: `feature/ATT-3551`

---

## 1. Problem Domain & Forensic Root Cause Analysis

### 1.1 Technical Background & Context
In early Android versions (API < 23), Apache HttpClient was bundled as part of the Android framework (`org.apache.http.*`). In Android 6.0 (API 23, Marshmallow), Google removed Apache HttpClient from the bootclasspath, requiring applications that still depended on it to link against the legacy platform wrapper `<uses-library android:name="org.apache.http.legacy" />` and bundle external `org.apache.httpcomponents:*` artifacts.

In `aTrainingTracker`, modern network operations (including Strava OAuth, Strava activity upload, segment sync, route sync, Dropbox API v2, Google Drive REST, and map tile fetching) have long been migrated to `OkHttp3` (`okhttp3.OkHttpClient`), Retrofit, or Android `HttpURLConnection`. 

However, forensic inspection reveals that the application still carries legacy dead code from abandoned **Runkeeper** (HealthGraph) and **TrainingPeaks** integrations:
1. The integrations have long been commented out in `ExportManager.java` (lines 84–88, 97–102) and `TrainingApplication.java` (lines 1246–1250, 1257–1261).
2. The concrete uploaders (`RunkeeperUploader.java`, `TrainingPeaksUploader.java`) and token extraction activities (`RunkeeperGetAccessTokenActivity.java`, `TrainingpeaksGetAccessTokenActivity.java`) still directly imported and instantiated `org.apache.http.impl.client.DefaultHttpClient`, `HttpPost`, `HttpResponse`, and `EntityUtils`.
3. In `MyHelper.java`, two uncalled, obsolete helper methods (`getAltitudeFromUSGS` and `getElevationFromGoogleMaps`) also retained dependencies on `org.apache.http.*`.
4. To sustain these dead classes, `app/build.gradle` bundles 5 obsolete Apache dependencies (`fluent-hc:4.5.14`, `httpclient:4.5.14`, `httpclient-cache:4.5.14`, `httpcore:4.4.16`, `httpmime:4.5.14`), `AndroidManifest.xml` declares `<uses-library android:name="org.apache.http.legacy" android:required="false" />`, and `app/proguard-rules.pro` carries `-dontwarn org.apache.http.**`.

Retaining this dead weight inflates the APK footprint, pollutes the build graph with vulnerable/obsolete dependencies, leaves zombie `<activity>` tags exposed in `AndroidManifest.xml`, and introduces cognitive drag during maintenance.

### 1.2 Forensic Audit of Dead Code & Target Artifacts

#### 1.2.1 Dead Activities and Classes Scheduled for Deletion
- `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/runkeeper/RunkeeperGetAccessTokenActivity.java`: Obsolete OAuth 1.0/2.0 activity for Runkeeper; zero callers.
- `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/runkeeper/RunkeeperTestActivity.java`: Unregistered debug activity solely launching `RunkeeperGetAccessTokenActivity`; zero callers.
- `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/trainingpeaks/TrainingpeaksGetAccessTokenActivity.java`: Obsolete OAuth activity for TrainingPeaks; zero callers.
- `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/BaseGetAccessTokenActivity.java`: Deprecated base class solely extended by the two obsolete activities above; zero remaining callers once those activities are removed.
- `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/RunkeeperUploader.java`: Unused uploader commented out in `ExportManager`; directly imports `org.apache.http.*`.
- `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/TrainingPeaksUploader.java`: Unused uploader commented out in `ExportManager`; directly imports `org.apache.http.*`.
- `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/RunkeeperFileWriter.java`: Unused file writer commented out in `ExportManager`; zero callers across production and tests.

*Note on package cleanups*: Deleting these files completely empties `com.atrainingtracker.trainingtracker.onlinecommunities.runkeeper` and `com.atrainingtracker.trainingtracker.onlinecommunities.trainingpeaks`.

#### 1.2.2 Dead Manifest Entries
- `app/src/main/AndroidManifest.xml` line 74:
  ```xml
  <uses-library android:name="org.apache.http.legacy" android:required="false" />
  ```
- `app/src/main/AndroidManifest.xml` lines 162–165:
  ```xml
  <activity
      android:name=".trainingtracker.onlinecommunities.runkeeper.RunkeeperGetAccessTokenActivity"
      android:label="@string/RunkeeperGetAccessTokenActivityLabel"
      android:theme="@style/Theme.aTrainingTracker" />
  ```
- `app/src/main/AndroidManifest.xml` lines 181–184:
  ```xml
  <activity
      android:name=".trainingtracker.onlinecommunities.trainingpeaks.TrainingpeaksGetAccessTokenActivity"
      android:label="@string/TrainingPeaksGetAccessTokenActivityLabel"
      android:theme="@style/Theme.aTrainingTracker" />
  ```

#### 1.2.3 Dead Dependencies and Proguard Rules
- `app/build.gradle` lines 144–148:
  ```groovy
  implementation 'org.apache.httpcomponents:fluent-hc:4.5.14'
  implementation 'org.apache.httpcomponents:httpclient:4.5.14'
  implementation 'org.apache.httpcomponents:httpclient-cache:4.5.14'
  implementation 'org.apache.httpcomponents:httpcore:4.4.16'
  implementation 'org.apache.httpcomponents:httpmime:4.5.14'
  ```
- `app/proguard-rules.pro` line 3:
  ```proguard
  -dontwarn org.apache.http.**
  ```

#### 1.2.4 Dead Elevation Methods in `MyHelper.java`
Lines 52–122 in `MyHelper.java` define:
- `public static double getAltitudeFromUSGS(double longitude, double latitude)`
- `public static double getElevationFromGoogleMaps(double longitude, double latitude)`

Forensic verification (`git grep` across the entire project):
- Neither method is called anywhere in the codebase.
- Both rely on obsolete HTTP URLs (`http://gisdata.usgs.gov/...`, `http://maps.googleapis.com/...`) and instantiate `DefaultHttpClient`.
- Lines 31–38 contain `org.apache.http.*` imports.
- Removing these two dead methods removes all remaining references to `org.apache.http.*` in non-deleted code.

#### 1.2.5 Commented-out and Dead Code in Active Classes
- `ExportManager.java`:
  - Lines 84–88: Commented-out `/* case RUNKEEPER: ... case TRAINING_PEAKS: ... */`.
  - Lines 97–102: Commented-out `/* case RUNKEEPER: ... case TRAINING_PEAKS: ... */`.
- `TrainingApplication.java`:
  - Constants (lines 154–156, 159–161, 215–224): `PREFERENCE_SCREEN_RUNKEEPER`, `SP_UPLOAD_TO_RUNKEEPER`, `SP_RUNKEEPER_TOKEN`, `SP_UPLOAD_TO_TRAINING_PEAKS`, `SP_TRAINING_PEAKS_ACCESS_TOKEN`, `SP_TRAINING_PEAKS_REFRESH_TOKEN`, `SP_UPLOAD_RUNKEEPER_*`, `SP_UPLOAD_TRAINING_PEAKS_*`.
  - Methods (lines 1093–1186): Getters and setters for Runkeeper and TrainingPeaks tokens/preferences; zero external callers.
  - Switch cases (lines 1246–1249, 1257–1260): Commented-out cases in `exportFileFormat()` and `uploadToCommunity()`.
- `BaseFileWriter.java`:
  - Lines 121–131: Commented-out filtering logic for `RUNKEEPER` and `TRAINING_PEAKS`.
- `FileFormat.java`:
  - Lines 33–35, 38: Commented-out `RUNKEEPER` and `TRAINING_PEAKS` entries.
- `ExportType.java`:
  - Line 30: Commented-out `/* FileFormat.RUNKEEPER, FileFormat.TRAINING_PEAKS */`.

---

## 2. Architectural Invariants & Chesterton's Fence Analysis

### 2.1 Chesterton's Fence Archaeology
- **Original Requirement / Origin**: Early Android versions (2011–2015) before Android 6.0 Marshmallow. Runkeeper (HealthGraph API) and TrainingPeaks were early upload targets. When Google deprecated Apache HttpClient in API 23, the legacy library wrapper and Maven dependencies were added to prevent runtime class-not-found exceptions.
- **Why Was the Code Retained?**: When modern exporters (Strava, Dropbox, Google Drive) transitioned to OkHttp3, the Runkeeper and TrainingPeaks integrations were simply commented out in `ExportManager.java` rather than systematically excised. The dependencies in `build.gradle` and `<uses-library>` in `AndroidManifest.xml` remained in place to allow the dead classes to continue compiling.
- **Why Is It Safe to Remove Now?**:
  1. Neither Runkeeper nor TrainingPeaks is functional, exposed in the UI, or supported by active API keys.
  2. Active networking is 100% migrated to OkHttp3 and Kotlin Coroutines.
  3. All callers in `MyHelper.java` are dead code.
  4. No external third-party library in the project classpath relies on `org.apache.httpcomponents`.

### 2.2 Preservation of Core Invariants
1. **SQLite Database Schema Stability**:
   - In `SportTypeDatabaseManager.java`, column `SportType.RUNKEEPER_NAME = "runkeeperName"` is part of the existing SQLite table schema.
   - *Invariant*: Modifying or dropping SQLite table columns risks schema corruption for existing user databases. `SportType.RUNKEEPER_NAME` MUST NOT be altered or removed in this ticket.
2. **Active Exporter Functionality**:
   - Strava, Dropbox, Google Drive, TCX, GPX, FIT, CSV, and GC file exports and community uploads MUST continue functioning with 100% fidelity.
3. **Zero Apache HttpClient Footprint**:
   - Post-cleanup, zero references to `org.apache.http.*` shall remain in `app/src/main/java/` or `app/src/main/AndroidManifest.xml`.
   - Build and test pipelines must succeed without `org.apache.httpcomponents:*` dependencies.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Deliverables
1. **File Deletions**:
   - `onlinecommunities/runkeeper/RunkeeperGetAccessTokenActivity.java`
   - `onlinecommunities/runkeeper/RunkeeperTestActivity.java`
   - `onlinecommunities/trainingpeaks/TrainingpeaksGetAccessTokenActivity.java`
   - `onlinecommunities/BaseGetAccessTokenActivity.java`
   - `exporter/uploader/RunkeeperUploader.java`
   - `exporter/uploader/TrainingPeaksUploader.java`
   - `exporter/writer/RunkeeperFileWriter.java`
2. **Manifest Cleanups**:
   - Remove `RunkeeperGetAccessTokenActivity` and `TrainingpeaksGetAccessTokenActivity` `<activity>` declarations.
   - Remove `<uses-library android:name="org.apache.http.legacy" android:required="false" />`.
3. **Gradle & Proguard Cleanups**:
   - Remove `org.apache.httpcomponents:*` dependencies (lines 144–148) from `app/build.gradle`.
   - Remove `-dontwarn org.apache.http.**` from `app/proguard-rules.pro`.
4. **Living Code Refactorings**:
   - Delete uncalled `getAltitudeFromUSGS` and `getElevationFromGoogleMaps` methods and Apache imports from `MyHelper.java`.
   - Remove commented-out blocks in `ExportManager.java`, `TrainingApplication.java`, `BaseFileWriter.java`, `FileFormat.java`, and `ExportType.java`.
   - Remove dead preference keys and dead getter/setter methods in `TrainingApplication.java`.
5. **Contract Verification**:
   - Implement unit/contract test `LegacyHttpClientPurgeContractTest.kt` verifying:
     - 0 references to `org.apache.http` in production source code.
     - Complete absence of deleted legacy classes from the classpath.
     - Clean absence of legacy `<uses-library>` and dead activities in `AndroidManifest.xml`.

### 3.2 Out-of-Scope (To Prevent Scope Creep)
- **SQLite Database Schema Modifications**: Do NOT drop `SportType.RUNKEEPER_NAME` column in SQLite database (`SportTypeDatabaseManager.java`).
- **Translation / String Resource Purge**: Existing unused strings in `strings.xml` / `arrays.xml` will not be aggressively purged in this ticket to avoid churn across all 9 localization files unless directly tied to manifest activity labels.
- **BaseOAuthCallbackActivity.java**: Retained as part of general OAuth infrastructure.

---

## 4. Verification & Testing Strategy

1. **Static Contract Test (`LegacyHttpClientPurgeContractTest.kt`)**:
   - Scan `app/src/main/java/` to assert 0 occurrences of `import org.apache.http`.
   - Assert `app/src/main/AndroidManifest.xml` does not contain `org.apache.http.legacy`, `RunkeeperGetAccessTokenActivity`, or `TrainingpeaksGetAccessTokenActivity`.
   - Assert `app/build.gradle` does not contain `org.apache.httpcomponents`.
2. **Full Clean-Room Unit Test Execution**:
   - `./gradlew testDebugUnitTest` must pass 100% cleanly without errors.
3. **Compilation & Assembly Verification**:
   - `./gradlew assembleDebug` must compile cleanly without missing symbols or unresolved dependencies.
