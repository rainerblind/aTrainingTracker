# Backlog Ticket Specification: ATT-3491

**Key:** ATT-3491  
**Summary:** Recursive directory workout file discovery and bulk import via Storage Access Framework  
**Type:** Improvement (`Verbesserung`)  
**Status:** Open / Backlog (`Zu erledigen`)  
**Epic:** ATT-162 (Cloud integration)  
**Related Tickets:** [ATT-3489](https://rainerblind.atlassian.net/browse/ATT-3489) (Remove Google Drive scan option across import cards), [ATT-3315](https://rainerblind.atlassian.net/browse/ATT-3315)

---

### Problem Statement & Value Proposition
With the removal of the direct Google Drive REST API scan (which was constrained by Google's sensitive scope verification policy for `drive.readonly`), athletes migrating from Garmin, Wahoo, Polar, or desktop archives need an easy, automated way to bulk-import hundreds of historical workout files (.fit, .tcx, .gpx) organized in folder hierarchies.

Individual file selection via `ACTION_OPEN_DOCUMENT` requires athletes to manually pick files one by one or multiselect from a single flat directory. When archives are organized in nested directories (e.g. `Workouts/2024/05/`, `TCX/Rennrad/`), manual selection is tedious.

By leveraging the Android Storage Access Framework (SAF) Directory Picker (`Intent.ACTION_OPEN_DOCUMENT_TREE` / `OpenDocumentTree`), athletes can select an entire root directory once. The app can then recursively traverse all subdirectories, discover all matching workout files, and bulk-import them with automated duplicate detection.

Crucially, this approach operates completely under native Android platform permissions:
1. **Zero OAuth Sensitive Scope Burden**: Bypasses Google Cloud Console sensitive scope reviews, video submissions, and privacy policy audits.
2. **Universal Cloud & Storage Agnostic**: Seamlessly scans Google Drive, Dropbox, Nextcloud, local device storage, USB-OTG, and SD cards through Android's native DocumentProvider architecture.
3. **Athlete Privacy & Least Privilege**: Grants temporary or persistable access strictly to the selected directory tree.

---

### User Story
* **As an**: endurance athlete with nested workout archives (.fit, .tcx, .gpx) on Google Drive, local storage, or SD card
* **I want**: to select a root directory via the system directory picker and have all subdirectories scanned recursively for workouts
* **So that**: my entire historical training catalog is automatically imported in bulk without tedious manual file selection and without third-party cloud API permission barriers.

---

### Scope & Technical Architecture

1. **Directory Picker Integration (`OpenDocumentTree`)**:
   - Introduce "Ordner scannen / importieren" (Scan folder) action on each format card (or a unified directory import card) in `ImportBackupTabsScreen.kt`.
   - Launch `ActivityResultContracts.OpenDocumentTree()` to allow the athlete to pick any local or cloud-backed directory.
   - Optionally persist directory read permissions via `contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`.

2. **Recursive Traversal Engine (`DocumentFile`)**:
   - Implement a background coroutine worker / engine that recursively traverses the selected `DocumentFile.fromTreeUri(context, treeUri)`.
   - Traversal guards: Enforce cycle detection, depth limits (e.g. max depth 6), and cancellation support.
   - File filtering: Match extensions contextually based on the active format (e.g. `.fit`, `.tcx`, `.gpx`) or scan all supported formats when triggered from a bulk action.

3. **Batch Ingestion Pipeline & Deduplication**:
   - Stream discovered `DocumentFile` content URIs via `contentResolver.openInputStream(uri)` directly to existing canonical parsers (`importFromFitInternal`, `importFromTcxInternal`, `importFromGpxInternal`).
   - Perform atomic duplicate checks (via `isWorkoutExisting` on base filename, timestamp, and trackpoint count) to skip already imported activities.
   - Post-import reconciliation: Trigger `WorkoutRepository.loadAllWorkouts()`, cluster refreshes, and period stats updates upon completion.

4. **User Experience & Progress HUD**:
   - Display a non-blocking Material 3 progress dialog or bottom banner showing:
     - Discovered file count
     - Current import progress ("Importiere Datei 14 von 85...")
     - Final summary digest ("72 importiert, 13 Duplikate übersprungen, 0 Fehler").

5. **Localization Parity**:
   - Provide complete 9-language translations (EN, DE, ES, FR, IT, JA, NL, PL, PT) for folder scan button labels, progress messages, and summary dialogs.

---

### Acceptance Criteria (Given-When-Then)

* **Criterion 1: Recursive Traversal Across Nested Folders**:
  - *Given* an athlete with a folder structure containing nested subdirectories (e.g. `Root/2024/Jan/` and `Root/2024/Feb/`) with `.fit` and `.tcx` files
  - *When* selecting `Root` via the directory picker and starting a scan
  - *Then* the app recursively traverses all subfolders and identifies all matching workout files.
* **Criterion 2: Cloud Provider Interoperability via SAF**:
  - *Given* an athlete browsing Google Drive in the Android SAF picker
  - *When* selecting a Google Drive folder containing workout files
  - *Then* the app streams and imports the files through the SAF content resolver without prompting for Google Drive OAuth scopes.
* **Criterion 3: Automated Duplicate Skipping**:
  - *Given* a directory containing 10 workouts, 4 of which already exist in `WorkoutSummaries`
  - *When* the directory import executes
  - *Then* the 6 new workouts are imported, the 4 existing workouts are skipped, and the summary displays "6 importiert, 4 Duplikate übersprungen".
* **Criterion 4: Graceful Error & Permission Handling**:
  - *Given* the athlete cancels the directory picker or selects an empty folder
  - *When* evaluated
  - *Then* the app gracefully dismisses without errors or crashes and displays an informative snackbar if zero compatible files were found.

---

### Parent Epic & Placement
* **Target Epic**: `ATT-162` (Cloud integration)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
