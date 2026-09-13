# Implementation Plan: TCX-Import - Make Strava Upload Optional (ATT-911)

## 1. Executive Summary & Objective
The objective of **ATT-911** is to give athletes explicit control over whether historical workouts imported from TCX files (either via single file import or cloud bulk recovery from Dropbox) are automatically uploaded to Strava:
1. **Prevent Mass-Upload & Feed Pollution**: In `ATT-602`, imported workouts unconditionally triggered Strava upload if Strava was connected. For athletes importing historical or bulk datasets, this caused unwanted historical activity flooding on athlete feeds and duplicate session creation.
2. **Prevent Strava API Rate-Limit Exhaustion**: Bulk importing dozens or hundreds of TCX activities rapidly exhausted Strava's rate limits (100 requests per 15 minutes, 1,000 per day), causing subsequent live workouts to fail upload with HTTP 429 errors.
3. **Safe Default (Opt-In Agency)**: The setting defaults to `false` (`SP_IMPORT_TCX_UPLOAD_TO_STRAVA`), protecting user accounts by default while persisting explicit user changes across app sessions.
4. **Contextual UI Control**: Athletes configuring import parameters in the `PreImportTuningBottomSheet` can toggle Strava upload directly before starting import, visible conditionally only when Strava is connected.
5. **Preserve Downstream & Manual Invariants**: Sessions imported with Strava upload disabled remain fully eligible for manual upload via Workout Summary or Edit Workout, and live tracking upload pipelines remain completely isolated.

---

## 2. Traceability & Mapped Entities

| Artifact | Identifier | Details |
| :--- | :--- | :--- |
| **Requirement** | `REQ-MIG-028` | Optional Strava Upload on TCX Import (`docs/requirements.md`) |
| **Test Specification** | `TST-MIG-025` | TCX Import Optional Strava Upload & UI Switch Verification (`docs/tests.md`) |
| **Parent Ticket** | `ATT-911` | `[Feature] TCX-Import: Make Strava upload optional` (Epic: `ATT-529`) |
| **Analysis Sub-task** | `ATT-968` | `[Analysis] TCX-Import: Make Strava upload optional` (`Erledigt` - Gate 1 Approved) |
| **Test Sub-task** | `ATT-969` | `[Test-Spec] TCX-Import: Make Strava upload optional` (`Erledigt` - Gate 2 Approved) |
| **Plan Sub-task** | `ATT-970` | `[Impl-Plan] TCX-Import: Make Strava upload optional` (`In Bearbeitung` - Stage 3) |

---

## 3. Detailed Software Design & File Changes (SWE.2 / SWE.3)

### 3.1 Configuration Layer ([TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java))
* **Declare Preference Key**:
  ```java
  public static final String SP_IMPORT_TCX_UPLOAD_TO_STRAVA = "import_tcx_upload_to_strava";
  ```
* **Accessor & Mutator Methods**:
  ```java
  public static boolean uploadImportedWorkoutsToStrava() {
      return cSharedPreferences.getBoolean(SP_IMPORT_TCX_UPLOAD_TO_STRAVA, false);
  }

  public static void setUploadImportedWorkoutsToStrava(boolean value) {
      cSharedPreferences.edit().putBoolean(SP_IMPORT_TCX_UPLOAD_TO_STRAVA, value).apply();
  }
  ```
  *Default `false`*: Ensures historical batches do not auto-upload without user intent.

### 3.2 TCX Import Engine Layer ([LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt))
* **Parameterize `importFromTcx`**:
  ```kotlin
  suspend fun importFromTcx(
      context: Context,
      tcxFile: File,
      listener: ProgressListener? = null,
      uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
  ): Boolean
  ```
* **Populate `WorkoutSummaries.UPLOAD_TO_STRAVA`**:
  ```kotlin
  // Lines 618-621:
  if (uploadToStrava && TrainingApplication.uploadToCommunity(FileFormat.STRAVA)) {
      put(WorkoutSummaries.UPLOAD_TO_STRAVA, 1)
  } else {
      put(WorkoutSummaries.UPLOAD_TO_STRAVA, 0)
  }
  ```
* **Parameterize `bulkRecoverFromDropbox`**:
  ```kotlin
  suspend fun bulkRecoverFromDropbox(
      context: Context,
      format: String,
      listener: ProgressListener? = null,
      uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
  ): RecoveryResult
  ```
  Forward `uploadToStrava` to individual `importFromTcx` call:
  ```kotlin
  val success = when (format.lowercase()) {
      "tcx" -> importFromTcx(context, tempFile, listener, uploadToStrava)
      else -> false
  }
  ```
* **Existing Downstream Guard**:
  In `schedulePostImportCommunityUpload(...)` (lines 1076–1083), the engine already evaluates:
  ```kotlin
  val uploadToStrava = getUploadToStravaStatus(summaryDb, workoutId)
  if (uploadToStrava == 0) {
      if (TrainingApplication.getDebug(true)) {
          Log.d(TAG, "Skipping Strava upload for workout $workoutId: Explicitly opted out.")
      }
      continue
  }
  ```
  When `UPLOAD_TO_STRAVA` is set to `0`, `schedulePostImportCommunityUpload` cleanly skips Strava export without any changes needed to the scheduling function.

### 3.3 ViewModel State Layer ([BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt))
* **Add Observable State & Mutator**:
  ```kotlin
  var uploadToStravaOnImport by mutableStateOf(TrainingApplication.uploadImportedWorkoutsToStrava())
      private set

  fun updateUploadToStravaOnImport(enabled: Boolean) {
      uploadToStravaOnImport = enabled
      TrainingApplication.setUploadImportedWorkoutsToStrava(enabled)
  }
  ```
* **Forward State in Operations**:
  * In `importLegacyFile`: Pass `uploadToStrava = uploadToStravaOnImport` to `LegacyImportEngine.importFromTcx`.
  * In `bulkRecoverLegacyData`: Pass `uploadToStrava = uploadToStravaOnImport` to `LegacyImportEngine.bulkRecoverFromDropbox`.

### 3.4 UI Presentation Layer ([ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt))
* **In `PreImportTuningBottomSheet`**:
  Render the upload toggle inside the scrollable column beneath `ClusterTuningContent`, conditioned on Strava connection:
  ```kotlin
  if (TrainingApplication.uploadToStrava()) {
      HorizontalDivider(
          modifier = Modifier.padding(vertical = 4.dp),
          color = MaterialTheme.colorScheme.outlineVariant
      )
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .clickable {
                  viewModel.updateUploadToStravaOnImport(!viewModel.uploadToStravaOnImport)
              }
              .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
      ) {
          Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
              Text(
                  text = stringResource(R.string.import_upload_to_strava_label),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                  text = stringResource(R.string.import_upload_to_strava_summary),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
              )
          }
          Switch(
              checked = viewModel.uploadToStravaOnImport,
              onCheckedChange = { viewModel.updateUploadToStravaOnImport(it) }
          )
      }
  }
  ```

### 3.5 Localization (All 9 Locales)
Add `import_upload_to_strava_label` and `import_upload_to_strava_summary` across all 9 localized `strings.xml` files:
* `values/strings.xml`:
  * Label: `"Upload to Strava"`
  * Summary: `"Automatically upload imported workouts to Strava."`
* `values-de/strings.xml`:
  * Label: `"Zu Strava hochladen"`
  * Summary: `"Importierte Trainings automatisch zu Strava hochladen."`
* `values-es/strings.xml`:
  * Label: `"Subir a Strava"`
  * Summary: `"Subir automáticamente los entrenamientos importados a Strava."`
* `values-fr/strings.xml`:
  * Label: `"Télécharger vers Strava"`
  * Summary: `"Télécharger automatiquement les entraînements importés vers Strava."`
* `values-it/strings.xml`:
  * Label: `"Carica su Strava"`
  * Summary: `"Carica automaticamente gli allenamenti importati su Strava."`
* `values-ja/strings.xml`:
  * Label: `"Stravaにアップロード"`
  * Summary: `"インポートしたワークアウトを自動的にStravaにアップロードします。"`
* `values-nl/strings.xml`:
  * Label: `"Uploaden naar Strava"`
  * Summary: `"Geïmporteerde trainingen automatisch uploaden naar Strava."`
* `values-pl/strings.xml`:
  * Label: `"Prześlij do Strava"`
  * Summary: `"Automatycznie przesyłaj zaimportowane treningi do Strava."`
* `values-pt/strings.xml`:
  * Label: `"Carregar para o Strava"`
  * Summary: `"Carregar automaticamente treinos importados para o Strava."`

---

## 4. Verification Plan (SWE.4 / SWE.5)

### 4.1 Unit Tests ([TcxImportCommunityUploadTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/TcxImportCommunityUploadTest.kt))
* Add tests verifying:
  1. When Strava is enabled globally and `uploadToStravaStatus` is `0`, `schedulePostImportCommunityUpload` skips Strava export.
  2. When Strava is enabled globally and `uploadToStravaStatus` is `1`, `schedulePostImportCommunityUpload` enqueues Strava export.
  3. Verify `TrainingApplication.uploadImportedWorkoutsToStrava()` defaults to `false` and reflects mutations.

### 4.2 UI Resource Tests ([PreImportTuningBottomSheetTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/PreImportTuningBottomSheetTest.kt))
* Add `"import_upload_to_strava_label"` and `"import_upload_to_strava_summary"` to `requiredStringKeys` to enforce existence across all 9 locales.

### 4.3 Multi-Locale Parity Test ([TranslationParityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt))
* Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.localization.TranslationParityTest` to ensure zero missing keys or broken format specifiers across all supported languages.

### 4.4 Full Clean-Room Regression
* Run full test suite: `./gradlew testDebugUnitTest` to guarantee zero regressions.

---

## 5. System Invariants Checklist
* [x] Live tracking upload lifecycle (`TrackerService`, `LiveWorkoutSession`) remains completely uninfluenced.
* [x] Manual upload option in `WorkoutSummary` and `EditWorkoutScreen` remains 100% available for imported workouts.
* [x] Database schema is unaltered (uses existing `WorkoutSummaries.UPLOAD_TO_STRAVA` column).
* [x] Strava API quotas and rate limits are preserved against accidental mass-upload.
