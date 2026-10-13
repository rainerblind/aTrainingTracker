# Stage 3 Implementation Plan: ATT-2622 - Filter file picker dialogs to matching workout file extensions for FIT, TCX, and GPX import

**Ticket**: [ATT-2622](https://atrainingtracker.atlassian.net/browse/ATT-2622)  
**Sub-task**: [ATT-2707](https://atrainingtracker.atlassian.net/browse/ATT-2707) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2622`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Architecture & Design (SWE.2)

### 1.1 Structural Decomposition
The implementation touches the UI Presentation layer in `ImportBackupTabsScreen.kt` and introduces a pure validation utility for file format compatibility:
1. **MIME Type Constant Definitions (`ImportBackupTabsScreen.kt`)**:
   - `FIT_MIME_TYPES`: `arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")`
   - `TCX_MIME_TYPES`: `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")`
   - `GPX_MIME_TYPES`: `arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")`
2. **Targeted Picker Dispatch (`ImportBackupTabsScreen.kt`)**:
   - In `onLocalImportClick`:
     - `"fit"` -> `pickFitFilesLauncher.launch(FIT_MIME_TYPES)`
     - `"tcx"` -> `pendingSingleLegacyFormat = "tcx"; pickLegacyFileLauncher.launch(TCX_MIME_TYPES)`
     - `"gpx"` -> `pendingSingleLegacyFormat = "gpx"; pickLegacyFileLauncher.launch(GPX_MIME_TYPES)`
3. **Defensive Post-Selection Validation (`ImportFileValidator.kt` or inline in `ImportBackupTabsScreen.kt`)**:
   - Pure function `isMatchingFormat(fileName: String?, expectedFormat: String): Boolean`:
     - If `fileName` is null or blank, return `true` (pass through to stream parser).
     - Extract extension via `fileName.substringAfterLast('.', "")`.
     - If extension is empty, return `true`.
     - Compare extension case-insensitively with `expectedFormat.lowercase()`.
4. **Error Handling & Feedback**:
   - When selected URI fails validation:
     - Clear pending URI.
     - Set `viewModel.setTransientError(...)` or show localized `invalid_workout_file_format` toast/error state.

### 1.2 UI Consistency (Rule 23)
- **Reference Screen**: `ImportBackupTabsScreen.kt` (Import tab).
- **Design Tokens**: Standard Material 3 design tokens (`MaterialTheme.colorScheme.error`, `MaterialTheme.typography.bodyMedium`).
- **No Novel One-Off Styles**: Reuses existing `BackupRestoreViewModel.UiState.Error` and standard Android string resource formatting.

---

## 2. Atomic Step Sequencing

### Step 1: Add Localized String Resource across All 9 Locales
- **Files**: `app/src/main/res/values*/strings.xml`
- **String ID**: `invalid_workout_file_format`
  - English: `"Invalid file format. Please select a %1$s file."`
  - German: `"Ungültiges Dateiformat. Bitte wählen Sie eine %1$s-Datei aus."`
  - Spanish: `"Formato de archivo no válido. Por favor seleccione un archivo %1$s."`
  - French: `"Format de fichier non valide. Veuillez sélectionner un fichier %1$s."`
  - Italian: `"Formato file non valido. Seleziona un file %1$s."`
  - Japanese: `"無効なファイル形式です。%1$s ファイルを選択してください。"`
  - Dutch: `"Ongeldig bestandsformaat. Selecteer een %1$s-bestand."`
  - Polish: `"Nieprawidłowy format pliku. Wybierz plik %1$s."`
  - Portuguese: `"Formato de arquivo inválido. Selecione um arquivo %1$s."`
- **Verification**: `ImportFormatFilePickerLocalizationTest`

### Step 2: Implement Extension Validation Utility
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportFileValidator.kt`
- **Functions**:
  - `fun isMatchingFormat(fileName: String?, expectedFormat: String): Boolean`
  - `fun resolveDisplayName(context: Context, uri: Uri): String?`
- **Verification**: `ImportFormatExtensionValidatorTest`

### Step 3: Wire MIME Filtering & Defensive Validation into `ImportBackupTabsScreen.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
- Update `onLocalImportClick` to supply `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, `GPX_MIME_TYPES`.
- In `pickFitFilesLauncher` callback: filter URIs; if any non-matching extension found, filter or report error.
- In `pickLegacyFileLauncher` callback: validate single legacy URI extension against `pendingSingleLegacyFormat`. If mismatch, show error message instead of proceeding.
- **Verification**: `ImportFormatFilePickerContractTest`

### Step 4: Run Targeted Tests & Full Clean-Room Regression Suite
- Run:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.ImportFormat*"`
  `./gradlew testDebugUnitTest`

---

## 3. Invariants & Rollback Safety
1. **Multi-Document Ingestion**: FIT multi-document selection (`OpenMultipleDocuments`) remains fully functional.
2. **Single-Document Legacy Selection**: TCX and GPX single-document selection (`OpenDocument`) remains intact.
3. **Cluster Tuning Dialog**: Mandatory pre-import tuning dialog on cloud recovery remains untouched.
4. **Full Restore Protection**: Database restore (`pickFullRestoreLauncher`) and incremental import (`pickImportLauncher`) remain unaffected.
5. **No Regressions**: 100% full-suite test pass rate.
