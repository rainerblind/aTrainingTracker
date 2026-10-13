# Stage 2: Requirement & Test Specification - ATT-3315: Enable import of externally copied workout files from Google Drive with file picker fallback

**Ticket**: [ATT-3315](https://rainerblind.atlassian.net/browse/ATT-3315)  
**Sub-task**: [ATT-3398](https://rainerblind.atlassian.net/browse/ATT-3398) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-MIG-034` (amended), `REQ-UI-293` (amended)  
**Test Mapping**: `TST-MIG-031`, `TST-UI-253`  
**Branch**: `feature/ATT-3315`  
**Author**: AI Agent 1 (Specification Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-MIG-034` (*Google Drive Historical Workout File Discovery & Bulk Recovery (.fit, .tcx, .gpx)*, `ATT-2346`), targeting `GoogleDriveClient.kt`, `LegacyImportEngine.kt`, and `BackupRestoreViewModel.kt`.
   - `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Source Triad (FIT, TCX, GPX)*, `ATT-2623`), targeting `ImportBackupTabsScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.1 (`ATT-2346`, commit `ec000c11`) introduced automated recursive discovery and bulk recovery from Google Drive using `GoogleDriveClient.listFilesRecursively`.
   - Sprint 2026-41.1 (`ATT-2623`, commit `d5a9d217`) structured the Import tab into three cards (FIT, TCX, GPX), each with a triad: Local File Picker, Dropbox Scan, and Google Drive Scan.
   - Sprint 2026-41.10 (`ATT-3168`) added format-specific subdirectories (`aTrainingTracker/FIT`, `aTrainingTracker/TCX`, `aTrainingTracker/GPX`).
3. **Root Reason for Existing Formulation**:
   - Least-Privilege OAuth: The application intentionally uses `https://www.googleapis.com/auth/drive.file` to protect user privacy and comply with Google Play Store data safety requirements.
   - Under `drive.file`, the app is restricted to files created by the application itself or explicitly opened by the user via Storage Access Framework (SAF).
   - In `ImportBackupTabsScreen.kt`, the local file picker for FIT used `ActivityResultContracts.OpenMultipleDocuments()`, whereas TCX and GPX were historically restricted to `ActivityResultContracts.OpenDocument()` (single file only).
   - Furthermore, `LegacyImportEngine.candidateFolderHierarchies` only scanned paths prefixed with `aTrainingTracker` (`listOf("aTrainingTracker", "TCX")`, `listOf("aTrainingTracker", "Workouts")`, `listOf("aTrainingTracker")`). Files placed directly in root `TCX/` or `FIT/` were never probed.
   - When Google Drive bulk recovery returned 0 files due to these permission or hierarchy constraints, `BackupRestoreViewModel` presented a generic "0 of 0 imported" message, giving the athlete zero indication why files were not discovered.
4. **Preservation of Core Invariants**:
   - Least-Privilege Security Scope: Maintain `https://www.googleapis.com/auth/drive.file` without escalating to broad, sensitive scopes (`drive` or `drive.readonly`).
   - Cluster Reconciliation & Deduplication: Ensure all imported files run through base filename deduplication (`REQ-MIG-031`) and trigger `WorkoutRepository`, `PeriodsRepository`, and `WorkoutClusterRepository` reconciliation.
   - Pre-Import Tuning: Preserve mandatory `PreImportTuningBottomSheet` presentation prior to starting imports.
   - 9-Language Localization Parity: Guarantee 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - Clean-Room Test Pass Rate: 100% test pass rate across all unit tests and layout contracts.

---

## 1. Requirements Specification

### 1.1 `REQ-MIG-034` (Amended): Google Drive Historical Workout File Discovery & Bulk Recovery (.fit, .tcx, .gpx)
The system SHALL provide automated discovery, download, and multi-format bulk importing of historical workout activity files (`.fit`, `.tcx`, `.gpx`) from Google Drive (ATT-2346, ATT-3315):
1. **Google Drive API Discovery (`GoogleDriveClient.kt`)**:
   - `GoogleDriveClient` SHALL provide recursive file discovery (`listFilesRecursively`) under specified folder hierarchies (`folderId`).
   - The query SHALL paginate through results using `nextPageToken` and filter entries strictly matching target extensions: `.fit`, `.tcx`, and `.gpx`.
   - `GoogleDriveClient` SHALL provide direct file download by Drive file ID (`downloadFileById(fileId, destinationFile)`).
   - Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` SHALL be strictly preserved.
2. **Top-Level & Nested Folder Hierarchy Scanning (`LegacyImportEngine.kt`)**:
   - `LegacyImportEngine.bulkRecoverFromGoogleDrive` SHALL scan candidate folder hierarchies including both nested `aTrainingTracker/` paths and top-level root paths:
     - For `fit`: `listOf("aTrainingTracker", "FIT")`, `listOf("FIT")`, `listOf("aTrainingTracker", "Workouts")`, `listOf("Workouts")`, `listOf("aTrainingTracker")`.
     - For `tcx`: `listOf("aTrainingTracker", "TCX")`, `listOf("TCX")`, `listOf("aTrainingTracker", "Workouts")`, `listOf("Workouts")`, `listOf("aTrainingTracker")`.
     - For `gpx`: `listOf("aTrainingTracker", "GPX")`, `listOf("GPX")`, `listOf("aTrainingTracker", "Workouts")`, `listOf("Workouts")`, `listOf("aTrainingTracker")`.
     - For `all`: all above candidates in prioritized order.
   - Discovered entries SHALL be deduplicated across scanned paths by base filename (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) per `REQ-MIG-031`.
   - The engine SHALL spawn 3 concurrent workers bounded by semaphore.
   - Before downloading, each entry SHALL be checked against `isWorkoutExisting(summaryDb, baseFileName)`. Existing workouts SHALL be skipped immediately without downloading (`REQ-MIG-015`).
   - Discovered files SHALL be routed by extension: `.fit` -> `importFromFitResult`, `.tcx` -> `importFromTcxResult`, `.gpx` -> `importFromGpxResult`.
3. **Zero-Result Diagnostic Feedback (`BackupRestoreViewModel.kt`)**:
   - When Google Drive recovery completes with `totalScanned == 0`, `BackupRestoreViewModel` SHALL display localized string `google_drive_scan_empty_diagnostic` instead of the generic "0 of 0 imported" status.
   - The diagnostic string SHALL inform the user that under least-privilege `drive.file` scope, externally copied files cannot be queried automatically via REST API and directs them to use the "Select files" button with the system document picker to import them directly from Google Drive.
4. **Post-Recovery Reconciliation**:
   - Upon successful import of one or more activities, the system SHALL asynchronously trigger: `WorkoutRepository.loadAllWorkouts()`, `PeriodsRepository.syncPeriodsIfDiscrepancy()`, `WorkoutClusterRepository.refreshClusters()`.

### 1.2 `REQ-UI-293` (Amended): Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad
The system SHALL structure the Import tab in `ImportBackupTabsScreen.kt` into three dedicated, format-specific cards (FIT, TCX, GPX) providing complete source parity via a uniform action triad with multi-document selection (ATT-2623, ATT-3315):
1. **Multi-Document Local Selection for All Formats**:
   - Each of the three format cards (FIT, TCX, GPX) SHALL launch `ActivityResultContracts.OpenMultipleDocuments()`, allowing single or batch selection of `.fit`, `.tcx`, or `.gpx` files.
   - The system file picker integrates Android Storage Access Framework (SAF), permitting athletes to browse their Google Drive account, local storage, Dropbox, or downloads and select multiple files simultaneously.
   - Selected URIs SHALL be validated via `ImportFileValidator.isMatchingFormat`. If any invalid files are selected, a warning Toast is displayed, and all valid files proceed to import.
2. **Unified Batch Execution (`BackupRestoreViewModel.kt`)**:
   - `BackupRestoreViewModel` SHALL expose `importWorkoutFiles(context: Context, uris: List<Uri>, format: String): Job`.
   - The method SHALL sequentially ingest each URI into a temporary cache file, dispatch to the format-specific engine (`importFromFitResult`, `importFromTcxResult`, `importFromGpxResult`), update progress, track imported/skipped/failed counts, clean up temp files, and trigger post-import reconciliation.
3. **Pre-Import Tuning Integration**:
   - Selecting local files SHALL present `PreImportTuningBottomSheet` before import starts, maintaining full UX parity with cloud scanning operations.

---

## 2. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Multi-Document Selection for TCX & GPX Cards**
  - *Given* an athlete navigating to the Import tab in `ImportBackupTabsScreen`,
  - *When* tapping "Dateien auswählen (TCX)" or "Dateien auswählen (GPX)",
  - *Then* the system launches `ActivityResultContracts.OpenMultipleDocuments()` with appropriate MIME types (`TCX_MIME_TYPES` / `GPX_MIME_TYPES`), allowing the athlete to select multiple files from Google Drive or local storage.

- **Scenario 2: Batch Import Execution & Clustering Reconciliation**
  - *Given* multiple `.tcx` or `.gpx` files selected from Google Drive via SAF,
  - *When* confirming cluster parameters in `PreImportTuningBottomSheet`,
  - *Then* `viewModel.importWorkoutFiles` imports each file sequentially, skips duplicates, updates loading progress, and refreshes `WorkoutRepository`, `PeriodsRepository`, and `WorkoutClusterRepository`.

- **Scenario 3: Top-Level Folder Discovery in Google Drive**
  - *Given* workout files residing in a top-level `TCX/` or `FIT/` directory in Google Drive,
  - *When* `bulkRecoverFromGoogleDrive` executes,
  - *Then* `candidateFolderHierarchies` probes `listOf("TCX")` and `listOf("FIT")` and discovers matching files.

- **Scenario 4: Actionable Zero-Result Diagnostic**
  - *Given* a Google Drive account where no app-accessible workout files are found (`totalScanned == 0`),
  - *When* `bulkRecoverGoogleDriveData` finishes,
  - *Then* `uiState` displays `google_drive_scan_empty_diagnostic` explaining the `drive.file` scope restriction and pointing the athlete to the multi-document local picker.

---

## 3. Test Specification

### 3.1 `TST-MIG-031` (Amended): Google Drive Historical Workout File Discovery, Top-Level Candidates & Zero-Result Diagnostic Verification
- **Unit Test Class**: `com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveBulkRecoveryContractTest`
- **Scope**:
  1. Verify `candidateFolderHierarchies` includes top-level folder candidates `listOf("TCX")`, `listOf("FIT")`, `listOf("GPX")`, `listOf("Workouts")`.
  2. Verify that when `bulkRecoverFromGoogleDrive` returns `totalScanned == 0`, `BackupRestoreViewModel` emits `google_drive_scan_empty_diagnostic`.

### 3.2 `TST-UI-253` (Amended): Format-Specific Import Cards Multi-Document Local Selection & Batch Ingestion Verification
- **Unit Test Class**: `com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModelBatchImportTest` & `ImportBackupTabsScreenContractTest`
- **Scope**:
  1. Verify `importWorkoutFiles` successfully ingests a batch of multiple URIs (FIT, TCX, GPX) with duplicate detection and error handling.
  2. Verify `ImportBackupTabsScreen.kt` configures `OpenMultipleDocuments()` across FIT, TCX, and GPX cards.
  3. Verify 100% 9-language translation parity for `google_drive_scan_empty_diagnostic`.

---

## 4. 9-Language Localization Plan

New string resource: `google_drive_scan_empty_diagnostic`
- **EN**: "No workout files found in Google Drive. Note: Due to Google Drive privacy permissions (drive.file), only files created by this app can be scanned automatically. For files copied from PC or Dropbox, please use the 'Select files' button and choose Google Drive in the file picker."
- **DE**: "Keine Trainingsdateien in Google Drive gefunden. Hinweis: Aufgrund der Google Drive Datenschutzrichtlinien (drive.file) können nur von dieser App erstellte Dateien automatisch gescannt werden. Für vom PC oder aus Dropbox kopierte Dateien nutzen Sie bitte den Button 'Dateien auswählen' und wählen Sie Google Drive im Dateimanager aus."
- **ES**: "No se encontraron archivos de entrenamiento en Google Drive. Nota: Debido a los permisos de privacidad de Google Drive (drive.file), solo los archivos creados por esta aplicación se pueden escanear automáticamente. Para archivos copiados desde PC o Dropbox, use el botón 'Seleccionar archivos' y elija Google Drive en el selector."
- **FR**: "Aucun fichier d'entraînement trouvé sur Google Drive. Remarque : En raison des autorisations de confidentialité Google Drive (drive.file), seuls les fichiers créés par cette application peuvent être analysés automatiquement. Pour les fichiers copiés depuis un PC ou Dropbox, veuillez utiliser le bouton 'Sélectionner des fichiers' et choisir Google Drive dans le sélecteur."
- **IT**: "Nessun file di allenamento trovato su Google Drive. Nota: a causa delle autorizzazioni di privacy di Google Drive (drive.file), solo i file creati da questa app possono essere scansionati automaticamente. Per i file copiati da PC o Dropbox, utilizza il pulsante 'Seleziona file' e scegli Google Drive nel selettore."
- **JA**: "Google ドライブにワークアウトファイルが見つかりませんでした。注意: Google ドライブのプライバシー権限 (drive.file) により、このアプリで作成されたファイルのみが自動スキャンされます。PC や Dropbox からコピーしたファイルについては、「ファイルを選択」ボタンを使用し、ファイル選択ツールで Google ドライブを選択してください。"
- **NL**: "Geen trainingsbestanden gevonden in Google Drive. Opmerking: Vanwege Google Drive privacyrechten (drive.file) kunnen alleen door deze app gemaakte bestanden automatisch worden gescand. Gebruik voor bestanden die vanaf een pc of Dropbox zijn gekopieerd de knop 'Bestanden selecteren' en kies Google Drive in de bestandsbeheerder."
- **PL**: "Nie znaleziono plików treningowych na Dysku Google. Uwaga: Ze względu na uprawnienia prywatności Dysku Google (drive.file) tylko pliki utworzone przez tę aplikację mogą być skanowane automatycznie. W przypadku plików skopiowanych z komputera lub Dropboksa użyj przycisku 'Wybierz pliki' i wybierz Dysk Google w menedżerze plików."
- **PT**: "Nenhum arquivo de treino encontrado no Google Drive. Observação: Devido às permissões de privacidade do Google Drive (drive.file), apenas arquivos criados por este aplicativo podem ser verificados automaticamente. Para arquivos copiados do PC ou Dropbox, use o botão 'Selecionar arquivos' e escolha o Google Drive no seletor."
