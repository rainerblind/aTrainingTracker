# Stage 2: Requirement & Test Specification - ATT-2623: Structure Import tab into format-specific blocks (FIT, TCX, GPX) each offering Local, Dropbox, and Google Drive options

**Ticket**: [ATT-2623](https://atrainingtracker.atlassian.net/browse/ATT-2623)  
**Sub-task**: [ATT-2701](https://atrainingtracker.atlassian.net/browse/ATT-2701) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.3`  
**Branch**: `feature/ATT-2623`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-UI-293`)

### REQ-UI-293: Format-Specific Import Tab Architecture with Uniform Source Triad (FIT, TCX, GPX)

The system SHALL structure the Import tab in `ImportBackupTabsScreen.kt` into three dedicated, format-specific cards (FIT, TCX, GPX) providing complete source parity via a uniform action triad (ATT-2623):

1. **Format-Based Card Partitioning (`ImportBackupTabsScreen.kt`)**:
   - `ImportTabContent` SHALL render three distinct, cohesive cards for the supported file formats:
     - **FIT Card**: Garmin, Wahoo, Hammerhead, and smartwatch binary activity files (`.fit`).
     - **TCX Card**: Training Center XML activity files (`.tcx`).
     - **GPX Card**: GPS Exchange Format tracks and activities (`.gpx`).
   - The legacy generic "Workout Import" / "Legacy Recovery" card grouping TCX and GPX together SHALL be eliminated.

2. **Uniform Action Triad per Format**:
   - Each of the three format cards SHALL provide an identical, intuitive action triad composed of three `OutlinedButton` options:
     - **Button 1 (Local File Picker)**:
       - For FIT: launches multi-document picker (`ActivityResultContracts.OpenMultipleDocuments()`), supporting single or batch `.fit` file ingestion (`REQ-DAT-019`).
       - For TCX: launches document picker (`ActivityResultContracts.OpenDocument()`), prompting for a single `.tcx` file.
       - For GPX: launches document picker (`ActivityResultContracts.OpenDocument()`), prompting for a single `.gpx` file.
     - **Button 2 (Dropbox Scan)**:
       - Displays clear format context (`import_fit_dropbox_button`, `import_tcx_dropbox_button`, `import_gpx_dropbox_button`).
       - When Dropbox is connected (`isDropboxConnected == true`), opens pre-import cluster tuning sheet (`PreImportTuningBottomSheet`), confirming which dispatches `viewModel.bulkRecoverLegacyData(context, format)` with format `"fit"`, `"tcx"`, or `"gpx"`.
       - When Dropbox is disconnected (`isDropboxConnected == false`), renders in deactivated styling, and clicking presents the Dropbox connection prompt dialog (`showDropboxDisconnectedDialog`).
     - **Button 3 (Google Drive Scan)**:
       - Displays clear format context (`import_fit_gdrive_button`, `import_tcx_gdrive_button`, `import_gpx_gdrive_button`).
       - When Google Drive is connected (`isGoogleDriveConnected == true`), opens pre-import cluster tuning sheet (`PreImportTuningBottomSheet`), confirming which dispatches `viewModel.bulkRecoverGoogleDriveData(context, format)` with format `"fit"`, `"tcx"`, or `"gpx"`.
       - When Google Drive is disconnected (`isGoogleDriveConnected == false`), renders in deactivated styling, and clicking presents the Google Drive connection prompt dialog (`showGoogleDriveDisconnectedDialog`).

3. **Format-Scoped Execution & State Tracking**:
   - `ImportBackupTabsScreen` SHALL track pending format states for cloud recovery (`pendingDropboxFormat`, `pendingGoogleDriveFormat`) and single-file local legacy import (`pendingSingleLegacyFormat`), eliminating hardcoded `"all"` parameters and scoping folder scans and file extension filters directly to the requested format.

4. **100% 9-Language Localization Parity**:
   - All new format headers, descriptions, and button labels SHALL maintain 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

5. **Preservation of Core Invariants**:
   - Pre-import cluster parameter tuning sheet (`REQ-MIG-012`, `REQ-UI-150`) remains mandatory before cloud scanning commences.
   - Interactive post-import workout navigation (`REQ-MIG-032`) via `StateOverlaySection` remains unchanged.
   - Unlinked cloud dialog alerts (`REQ-MIG-023`, `REQ-MIG-034`) remain strictly enforced.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-293`) extending `REQ-MIG-011` (*Tabbed Import & Backup UI*), `REQ-DAT-019` (*External FIT Workout Importer*), `REQ-MIG-034` (*Google Drive Bulk Recovery*), and `REQ-MIG-035` (*Dropbox Bulk Recovery for FIT*).
2. **Historical Origin & Commit Trace**: Sprint 2026-41.1 human review of `ATT-2341` and `ATT-2346` identified fragmented and asymmetrical layout in `ImportBackupTabsScreen.kt`.
3. **Root Reason for Existing Formulation**: Evolutionary accretion: TCX and GPX were historically the only supported legacy migration formats, sharing a single "Legacy Recovery" card. When FIT was introduced later, it was placed in a separate card above to avoid disturbing legacy recovery.
4. **Preservation of Core Invariants**: Cloud authentication guards, cluster tuning sheet presentation, post-import navigation, multi-document picking for FIT, and database deduplication remain 100% preserved.

---

### Acceptance Criteria (Given-When-Then)

* **Scenario 1: Format-Based Card Partitioning**
  - **Given** an athlete navigating to the Import tab,
  - **When** the screen renders,
  - **Then** `ImportTabContent` SHALL display three format cards: FIT, TCX, and GPX, and no generic combined legacy recovery card.

* **Scenario 2: Uniform Action Triad Presentation**
  - **Given** any format card (FIT, TCX, GPX),
  - **When** inspecting the actions,
  - **Then** the card SHALL display three buttons: Local File Picker, Scan Dropbox, and Scan Google Drive.

* **Scenario 3: Format-Scoped Dropbox Recovery Trigger**
  - **Given** an athlete tapping "Dropbox scannen (FIT)",
  - **When** confirming cluster parameters in `PreImportTuningBottomSheet`,
  - **Then** `viewModel.bulkRecoverLegacyData(context, "fit")` SHALL be dispatched.

* **Scenario 4: Format-Scoped Google Drive Recovery Trigger**
  - **Given** an athlete tapping "Google Drive scannen (TCX)",
  - **When** confirming cluster parameters in `PreImportTuningBottomSheet`,
  - **Then** `viewModel.bulkRecoverGoogleDriveData(context, "tcx")` SHALL be dispatched.

* **Scenario 5: Unauthenticated Cloud Service Feedback**
  - **Given** a disconnected cloud service (Dropbox or Google Drive),
  - **When** the athlete taps the corresponding scan button,
  - **Then** the action button SHALL display deactivated styling and tapping it SHALL present the disconnected status dialog.

---

## 2. Test Specification (`TST-UI-253`)

### TST-UI-253: Format-Specific Import Tab Architecture and Format-Scoped Recovery Verification

1. **9-Language Localization Parity Tests (`ImportFormatLocalizationTest.kt`)**:
   - Verify all format titles, descriptions, and button labels exist, are non-empty, and contain no malformed format tokens across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
   - Required String Keys:
     - `import_fit_title`, `import_fit_description`, `import_fit_button`, `import_fit_dropbox_button`, `import_fit_gdrive_button`
     - `import_tcx_title`, `import_tcx_description`, `import_tcx_button`, `import_tcx_dropbox_button`, `import_tcx_gdrive_button`
     - `import_gpx_title`, `import_gpx_description`, `import_gpx_button`, `import_gpx_dropbox_button`, `import_gpx_gdrive_button`

2. **ViewModel Format Scoping Unit Tests (`BackupRestoreViewModelFormatScopingTest.kt`)**:
   - Verify `bulkRecoverLegacyData` passes `"fit"`, `"tcx"`, and `"gpx"` correctly to `LegacyImportEngine.bulkRecoverFromDropbox`.
   - Verify `bulkRecoverGoogleDriveData` passes `"fit"`, `"tcx"`, and `"gpx"` correctly to `LegacyImportEngine.bulkRecoverFromGoogleDrive`.
   - Verify `importLegacyFile` with format `"tcx"` and `"gpx"` appropriately resolves file extension and dispatches to corresponding parser.

3. **UI Contract & Architecture Tests (`ImportFormatCardsContractTest.kt`)**:
   - Verify `ImportTabContent` renders three distinct format blocks (FIT, TCX, GPX).
   - Verify each block contains the uniform action triad (Local, Dropbox, Google Drive).
   - Verify disconnected cloud styling and dialog dispatch.

4. **Full Clean-Room Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Verification Method | Target Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-293` (Cl. 1, 2) | `TST-UI-253` (Cl. 3) | Contract / Composable structure test (`ImportFormatCardsContractTest`) | Specified |
| `REQ-UI-293` (Cl. 3) | `TST-UI-253` (Cl. 2) | Mockk ViewModel format dispatch test (`BackupRestoreViewModelFormatScopingTest`) | Specified |
| `REQ-UI-293` (Cl. 4) | `TST-UI-253` (Cl. 1) | XML parsing & assertion across 9 locales (`ImportFormatLocalizationTest`) | Specified |
| `REQ-UI-293` (Cl. 5) | `TST-UI-253` (Cl. 4) | Full clean-room regression (`./gradlew testDebugUnitTest`) | Specified |

---

## 4. Review Gate 2 Readiness

* Formal requirement `REQ-UI-293` added to `docs/requirements.md` and verified with `tools/verify_requirement_governance.py`.
* Test specification `TST-UI-253` added to `docs/tests.md`.
* 9-language localization audit planned with 12 distinct string keys across all 9 locales.
* Ready for Gate 2 Audit submission.
