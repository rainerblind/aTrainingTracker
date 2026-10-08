# Stage 2: Requirement & Test Specification - ATT-2740: Filter file picker strictly by file extension for FIT, TCX, and GPX import

**Ticket**: [ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740)  
**Sub-task**: [ATT-2808](https://atrainingtracker.atlassian.net/browse/ATT-2808) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-294` (*Workout File Picker MIME Filtering and Defensive Format-Extension Validation (FIT, TCX, GPX)*)  
**Test Spec ID**: `TST-UI-254` (*Workout File Picker MIME Filtering and Format-Extension Validation Verification*)  
**Branch**: `improvement/ATT-2740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-UI-294`)

### 1.1 Problem Statement & Rationale
During Sprint 2026-41.3 review on a physical Google Pixel 10 (ATT-2622), launching the file picker from any format card (FIT, TCX, GPX) on `ImportBackupTabsScreen.kt` continued to display all non-workout files (images, audio, videos, zips, APKs, PDFs, etc.) as selectable. This occurred because `application/octet-stream` was included in `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES`. In Android's Storage Access Framework (SAF), `application/octet-stream` serves as a generic wildcard for binary data, causing `DocumentsProvider` to treat almost all files as selectable and defeating format-targeted filtering.

### 1.2 Functional & Architectural Requirements
The system SHALL configure strict format-specific MIME type filtering for Storage Access Framework (SAF) document pickers and enforce defensive extension validation on selected files in `ImportBackupTabsScreen.kt`, eliminating generic binary wildcards that cause non-workout files to appear selectable (ATT-2622, ATT-2740):
1. **Strict Format-Specific MIME Arrays (`ImportBackupTabsScreen.kt`)**:
   - When tapping "Lokale FIT-Datei(en) wählen", `pickFitFilesLauncher.launch` SHALL be invoked with targeted FIT MIME types excluding generic binary wildcards: `arrayOf("application/vnd.ant.fit", "application/fit")`.
   - When tapping "Lokale TCX-Datei wählen", `pickLegacyFileLauncher.launch` SHALL be invoked with targeted TCX XML MIME types excluding generic binary wildcards: `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")`.
   - When tapping "Lokale GPX-Datei wählen", `pickLegacyFileLauncher.launch` SHALL be invoked with targeted GPX XML MIME types excluding generic binary wildcards: `arrayOf("application/gpx+xml", "application/xml", "text/xml")`.
   - Generic `arrayOf("*/*")` and generic `application/octet-stream` launches for workout imports SHALL be completely eliminated.
2. **Defensive Extension & Format Validation**:
   - Upon URI selection, the application SHALL inspect the file's display name (`OpenableColumns.DISPLAY_NAME`).
   - If the display name contains an explicit file extension, it SHALL match the requested format (case-insensitive: `.fit` for FIT, `.tcx` for TCX, `.gpx` for GPX).
   - If the selected file's extension conflicts with the requested format (e.g. user selects a `.pdf` or `.png`), the file SHALL be rejected before stream ingestion, displaying a clear localized error status (`invalid_workout_file_format`).
   - If the display name does not specify an extension (e.g. content provider opaque ID), the file SHALL proceed to content/header validation in `LegacyImportEngine` without premature rejection.
3. **100% 9-Language Localization Parity**:
   - String resource `invalid_workout_file_format` ("Ungültiges Dateiformat. Bitte wählen Sie eine %1$s-Datei aus." / "Invalid file format. Please select a %1$s file.") SHALL maintain 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
4. **Preserved Invariants**:
   - Multi-document selection for FIT (`OpenMultipleDocuments`) and single-document selection for TCX/GPX (`OpenDocument`) remain preserved.
   - Full restore archive picker (`.attbackup`) and incremental DB import pickers remain untouched.

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Refines Clause 1 of `REQ-UI-294` (*Workout File Picker MIME Filtering and Defensive Format-Extension Validation (FIT, TCX, GPX)*), targeting `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-2622`, Sprint `2026-41.3`, commit `c75b8e97`.
3. **Root Reason for Existing Formulation**: In ATT-2622, `application/octet-stream` was included as a fallback under the assumption that some Android storage providers might fail to resolve specialized MIME types. However, this fallback caused Android's Storage Access Framework to treat all generic binary files (images, audio, videos, zips, APKs, PDFs) as selectable, defeating format-specific picker filtering as observed on a physical Pixel 10 during the Sprint 2026-41.3 review.
4. **Preservation of Core Invariants**: Defensive post-selection file extension validation via `ImportFileValidator` (Clause 2), localized error toast `invalid_workout_file_format` across all 9 locales (Clause 3), multi-document selection for FIT, single-document selection for TCX/GPX, and full regression test pass rate remain strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (FIT file picker filtering)**:
  * *Given* the athlete taps "Lokale FIT-Datei(en) wählen" on the FIT card
  * *When* the system document picker opens
  * *Then* `pickFitFilesLauncher` SHALL receive MIME types `arrayOf("application/vnd.ant.fit", "application/fit")` without `application/octet-stream`.
* **Criterion 2 (TCX file picker filtering)**:
  * *Given* the athlete taps "Lokale TCX-Datei wählen" on the TCX card
  * *When* the system document picker opens
  * *Then* `pickLegacyFileLauncher` SHALL receive MIME types `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")` without `application/octet-stream`.
* **Criterion 3 (GPX file picker filtering)**:
  * *Given* the athlete taps "Lokale GPX-Datei wählen" on the GPX card
  * *When* the system document picker opens
  * *Then* `pickLegacyFileLauncher` SHALL receive MIME types `arrayOf("application/gpx+xml", "application/xml", "text/xml")` without `application/octet-stream`.
* **Criterion 4 (Defensive Post-Selection Rejection)**:
  * *Given* an athlete selecting a file whose display name ends with `.png` when TCX was requested
  * *When* the result is returned
  * *Then* the file SHALL be rejected and an error toast displayed indicating invalid format.
* **Criterion 5 (Localization Parity)**:
  * *Given* all 9 application locales
  * *When* running translation parity tests
  * *Then* `invalid_workout_file_format` SHALL exist and contain `%1$s` in all 9 `strings.xml` files.

---

## 2. Test Specification (`TST-UI-254`)

### Test Case 1: SAF MIME Launcher Contract Tests (`TST-UI-254.1`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatFilePickerContractTest.kt`
* **Preconditions**: `ImportBackupTabsScreen.kt` declares `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, `GPX_MIME_TYPES`.
* **Action**: Assert exact array contents for all three constants and assert that `application/octet-stream` is absent from all three.
* **Expected Result**:
  - `FIT_MIME_TYPES` equals `arrayOf("application/vnd.ant.fit", "application/fit")`.
  - `TCX_MIME_TYPES` equals `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")`.
  - `GPX_MIME_TYPES` equals `arrayOf("application/gpx+xml", "application/xml", "text/xml")`.
  - None contain `"application/octet-stream"`.

### Test Case 2: Defensive Extension Validation Unit Tests (`TST-UI-254.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatExtensionValidatorTest.kt`
* **Preconditions**: `ImportFileValidator.isMatchingFormat(fileName, format)`
* **Action**: Execute validation against uppercase/lowercase valid extensions, invalid formats, non-workout files, null, empty, and opaque IDs.
* **Expected Result**:
  - `.fit` files pass for FIT and fail for TCX/GPX.
  - `.tcx` files pass for TCX and fail for FIT/GPX.
  - `.gpx` files pass for GPX and fail for FIT/TCX.
  - Unsupported extensions (`.pdf`, `.png`, `.txt`, `.zip`) fail.
  - Opaque URIs / null / empty pass to stream parser.

### Test Case 3: 9-Language Localization Parity Audit (`TST-UI-254.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatFilePickerLocalizationTest.kt`
* **Action**: Validate `invalid_workout_file_format` across all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% presence, non-empty, matching `%1$s` specifier across all 9 locales.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-254.4`)
* **Scope**: Full Clean-Room Regression Test
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-254.1` | Unit / Contract | `ImportFormatFilePickerContractTest` | `REQ-UI-294` Clause 1 | Specified |
| `TST-UI-254.2` | Unit | `ImportFormatExtensionValidatorTest` | `REQ-UI-294` Clause 2 | Specified |
| `TST-UI-254.3` | Localization | `ImportFormatFilePickerLocalizationTest` | `REQ-UI-294` Clause 3 | Specified |
| `TST-UI-254.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
