# Backlog Ticket Specification: ATT-3489

**Key:** ATT-3489  
**Summary:** Remove Google Drive scan option across all workout format import cards  
**Type:** Improvement (`Verbesserung`)  
**Status:** Open / Backlog (`Zu erledigen`)  
**Epic:** ATT-162 (Cloud integration)  
**Related Tickets:** [ATT-2346](https://rainerblind.atlassian.net/browse/ATT-2346), [ATT-3315](https://rainerblind.atlassian.net/browse/ATT-3315), [ATT-2504](https://rainerblind.atlassian.net/browse/ATT-2504)

---

### Problem Statement & Value Proposition
In `ImportBackupTabsScreen.kt`, the format import tab currently displays a "Google Drive scannen" button for each supported workout file format (FIT: `import_fit_gdrive_button`, TCX: `import_tcx_gdrive_button`, GPX: `import_gpx_gdrive_button`).

However, scanning Google Drive via the direct REST API for externally copied workout files (e.g. copied via desktop sync or web browser) requires the sensitive OAuth scope `https://www.googleapis.com/auth/drive.readonly`. Google strictly mandates an extensive, recurring verification process for sensitive scopes (including YouTube demo video submissions, detailed justification reviews, and potential CASA audits). Under the least-privilege `drive.file` scope, the REST API cannot discover files created outside of aTrainingTracker, leading to false expectations and confusing empty scan results for athletes.

Furthermore, athletes can already import workout files stored on Google Drive effortlessly without sensitive scopes by using the standard local file picker ("Dateien importieren"), which integrates natively with the Android Storage Access Framework (SAF) Google Drive cloud provider.

Removing the redundant "Google Drive scannen" button across all three format cards (FIT, TCX, GPX) cleans up the import interface, eliminates confusing permission limitations, and simplifies the codebase.

---

### User Story
* **As an**: endurance athlete importing historical workouts
* **I want**: a clean, uncluttered import interface without broken or restricted Google Drive scanning buttons
* **So that**: I can import my FIT, TCX, and GPX files reliably through the standard system file picker or Dropbox without encountering permission dead-ends.

---

### Scope & Technical Architecture

1. **Composable UI Cleanup (`ImportBackupTabsScreen.kt`)**:
   - Remove `googleDriveButtonText`, `isGoogleDriveConnected`, and `onGoogleDriveClick` parameters from `FormatImportCard` and `WorkoutFormatImportTabContent`.
   - Eliminate the third action button in FIT, TCX, and GPX cards, leaving only the Local File Import button and the Dropbox Scan button.

2. **ViewModel & Event Decoupling (`ImportBackupViewModel.kt`)**:
   - Remove format-specific Google Drive scan dispatchers (e.g. `scanGoogleDriveForWorkouts(format)`), while preserving Google Drive database backup/restore and automated export capabilities.

3. **String Resources Cleanup**:
   - Deprecate or remove unused string resources (`import_fit_gdrive_button`, `import_tcx_gdrive_button`, `import_gpx_gdrive_button`) across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero lint regressions.

4. **Contract & Modularity Tests**:
   - Update `ImportBackupTabsScreenTest.kt` to assert that format import cards display only local file import and Dropbox buttons.

---

### Acceptance Criteria (Given-When-Then)

* **Criterion 1: Elimination of Google Drive Buttons**:
  - *Given* an athlete opens the "Import & Backup" screen and views the format import cards
  - *When* inspecting the FIT, TCX, and GPX sections
  - *Then* each format card displays exclusively the "Dateien importieren" (Local SAF) and "Dropbox scannen" actions, with zero Google Drive scan buttons present.
* **Criterion 2: Functional Preservation of Other Cloud Operations**:
  - *Given* an athlete using Google Drive for automated exports or full database backups (`.attbackup`)
  - *When* viewing the Backup/Restore tab or automatic export settings
  - *Then* Google Drive connection, export, and backup/restore workflows remain 100% operational under `drive.file`.
* **Criterion 3: Localization & Translation Parity**:
  - *Given* the 9 supported application locales
  - *When* running translation parity checks
  - *Then* zero orphaned or missing string errors occur.

---

### Parent Epic & Placement
* **Target Epic**: `ATT-162` (Cloud integration)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
