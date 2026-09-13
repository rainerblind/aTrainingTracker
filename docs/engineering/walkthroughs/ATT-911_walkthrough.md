# Implementation Walkthrough: TCX-Import - Make Strava Upload Optional (ATT-911)

## 1. Overview & Objectives
* **Parent Ticket**: [ATT-911](https://jira.blind-it.com/browse/ATT-911) (`[Feature] TCX-Import: Make Strava upload optional`)
* **Associated Requirements & Tests**:
  * Requirements: `REQ-MIG-028` ([docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L348))
  * Test Specifications: `TST-MIG-025` ([docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L137))
  * Fix Version: `V4.9.36`
  * Git Branch: `feature/ATT-911`

This feature grants athletes explicit control over whether historical TCX workouts (imported individually or in bulk via Dropbox cloud recovery) are automatically uploaded to Strava. It eliminates duplicate activities on Strava, prevents user feeds from being flooded with historical sessions, and protects athletes against Strava API rate-limit exhaustion (HTTP 429).

---

## 2. Changes Implemented

### 2.1 Configuration Layer
* **[TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)**:
  * Added `public static final String SP_IMPORT_TCX_UPLOAD_TO_STRAVA = "import_tcx_upload_to_strava";`
  * Added `uploadImportedWorkoutsToStrava(): Boolean` defaulting safely to `false` (with defensive null checks for uninitialized / test environments).
  * Added `setUploadImportedWorkoutsToStrava(boolean value)` to persist athlete choice across app sessions.
  * Added defensive null checks to `getStravaAccessToken()` ensuring complete robustness in headless unit testing environments.

### 2.2 TCX Import Engine Layer
* **[LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)**:
  * Parameterized `importFromTcx`:
    `suspend fun importFromTcx(context: Context, tcxFile: File, listener: ProgressListener? = null, uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()): Boolean`
  * In `summaryValues`:
    ```kotlin
    if (uploadToStrava && TrainingApplication.uploadToCommunity(FileFormat.STRAVA)) {
        put(WorkoutSummaries.UPLOAD_TO_STRAVA, 1)
    } else {
        put(WorkoutSummaries.UPLOAD_TO_STRAVA, 0)
    }
    ```
  * Parameterized `bulkRecoverFromDropbox`:
    `suspend fun bulkRecoverFromDropbox(context: Context, format: String, listener: ProgressListener? = null, uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()): RecoveryResult`
    Forwarding `uploadToStrava` to each individual `importFromTcx(...)` call.
  * Leveraged existing downstream guard in `schedulePostImportCommunityUpload`:
    When `getUploadToStravaStatus(...) == 0`, Strava upload is cleanly bypassed.

### 2.3 ViewModel Layer
* **[BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)**:
  * Added observable state: `var uploadToStravaOnImport by mutableStateOf(TrainingApplication.uploadImportedWorkoutsToStrava())`
  * Added mutator: `fun updateUploadToStravaOnImport(enabled: Boolean)`
  * Passed `uploadToStravaOnImport` into `importLegacyFile()` and `bulkRecoverLegacyData()`.

### 2.4 UI Presentation Layer
* **[ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)**:
  * In `PreImportTuningBottomSheet`:
    When Strava is connected (`TrainingApplication.uploadToStrava() == true`), rendered a divider and an interactive Material 3 switch row bound to `viewModel.uploadToStravaOnImport`.
    When Strava is not connected, the switch is cleanly omitted to avoid presenting irrelevant settings to athletes.

### 2.5 Multi-Locale Localization
* Added `import_upload_to_strava_label` and `import_upload_to_strava_summary` across all 9 supported locales:
  * `values`: "Upload to Strava" / "Automatically upload imported workouts to Strava."
  * `values-de`: "Zu Strava hochladen" / "Importierte Trainings automatisch zu Strava hochladen."
  * `values-es`: "Subir a Strava" / "Subir automáticamente los entrenamientos importados a Strava."
  * `values-fr`: "Télécharger vers Strava" / "Télécharger automatiquement les entraînements importés vers Strava."
  * `values-it`: "Carica su Strava" / "Carica automaticamente gli allenamenti importati su Strava."
  * `values-ja`: "Stravaにアップロード" / "インポートしたワークアウトを自動的にStravaにアップロードします。"
  * `values-nl`: "Uploaden naar Strava" / "Geïmporteerde trainingen automatisch uploaden naar Strava."
  * `values-pl`: "Prześlij do Strava" / "Automatycznie przesyłaj zaimportowane treningi do Strava."
  * `values-pt`: "Carregar para o Strava" / "Carregar automaticamente treinos importados para o Strava."

---

## 3. Verification & Validation Results

### 3.1 Automated Test Execution

1. **`TranslationParityTest`**:
   * Verified 100% translation coverage across all 9 languages with zero missing resource keys and valid format specifiers.
2. **`PreImportTuningBottomSheetTest`**:
   * Verified that `import_upload_to_strava_label` and `import_upload_to_strava_summary` exist, are non-null, and non-empty in all 9 locale directories.
3. **`TcxImportCommunityUploadTest`**:
   * `testImportSchedulesStravaUploadWhenEnabled`: Verified Strava upload enqueued when enabled.
   * `testImportSkipsUploadWhenCommunityDisabled`: Verified no export scheduled when Strava disabled.
   * `testImportRobustnessAgainstSchedulingExceptions`: Verified exception safety.
   * `testImportSkipsStravaUploadWhenExplicitlyOptedOut`: Verified that `UPLOAD_TO_STRAVA == 0` causes `schedulePostImportCommunityUpload` to cleanly bypass Strava upload.
   * `testImportUploadsToStravaWhenExplicitlyOptedIn`: Verified that `UPLOAD_TO_STRAVA == 1` causes `schedulePostImportCommunityUpload` to trigger Strava export.
4. **Clean-Room Regression (`./gradlew testDebugUnitTest`)**:
   * Executed 352 unit tests across all test suites.
   * Result: **100% passed (0 failures, 0 errors)**.

---

## 4. System Invariants Preserved
* [x] **Live Tracking Isolation**: Live sessions recorded via `TrackerService` continue to trigger automatic upload based on global community settings; only imported sessions are governed by the import switch.
* [x] **Manual Upload Sovereignty**: Workouts imported with Strava upload disabled can still be manually uploaded at any future time via Workout Summary or Edit Workout.
* [x] **Database Schema Stability**: No database migrations required; leverages the existing `WorkoutSummaries.UPLOAD_TO_STRAVA` column.
* [x] **Strava Rate Limit Preservation**: By defaulting to `false`, bulk imports will no longer saturate Strava API quotas.
