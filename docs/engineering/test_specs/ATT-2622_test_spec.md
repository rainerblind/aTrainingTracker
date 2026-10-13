# Stage 2 Requirement & Test Specification: ATT-2622 - Filter file picker dialogs to matching workout file extensions for FIT, TCX, and GPX import

**Ticket**: [ATT-2622](https://atrainingtracker.atlassian.net/browse/ATT-2622)  
**Sub-task**: [ATT-2706](https://atrainingtracker.atlassian.net/browse/ATT-2706) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2622`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-UI-294)

### 1.1 Requirement Definition
The system SHALL configure format-specific MIME type filtering for Storage Access Framework (SAF) document pickers and enforce defensive extension validation on selected files in `ImportBackupTabsScreen.kt` (ATT-2622):

1. **Format-Specific MIME Arrays for Document Pickers (`ImportBackupTabsScreen.kt`)**:
   - When the athlete taps "Lokale FIT-Datei auswählen", `pickFitFilesLauncher.launch` SHALL be invoked with targeted FIT MIME types: `arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")`.
   - When the athlete taps "Lokale TCX-Datei auswählen", `pickLegacyFileLauncher.launch` SHALL be invoked with targeted TCX MIME types: `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")`.
   - When the athlete taps "Lokale GPX-Datei auswählen", `pickLegacyFileLauncher.launch` SHALL be invoked with targeted GPX MIME types: `arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")`.
   - Generic `arrayOf("*/*")` launches for workout imports SHALL be completely eliminated.

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

### 1.2 Acceptance Criteria (Given-When-Then)
- **AC-1 (FIT Picker)**:
  - *Given* an athlete tapping "Lokale FIT-Datei auswählen",
  - *When* the document picker opens,
  - *Then* `pickFitFilesLauncher` SHALL receive MIME types `application/vnd.ant.fit`, `application/fit`, and `application/octet-stream`.
- **AC-2 (TCX Picker)**:
  - *Given* an athlete tapping "Lokale TCX-Datei auswählen",
  - *When* the document picker opens,
  - *Then* `pickLegacyFileLauncher` SHALL receive MIME types `application/vnd.garmin.tcx+xml`, `application/xml`, `text/xml`, and `application/octet-stream`.
- **AC-3 (GPX Picker)**:
  - *Given* an athlete tapping "Lokale GPX-Datei auswählen",
  - *When* the document picker opens,
  - *Then* `pickLegacyFileLauncher` SHALL receive MIME types `application/gpx+xml`, `application/xml`, `text/xml`, and `application/octet-stream`.
- **AC-4 (Extension Rejection)**:
  - *Given* an athlete selecting a file whose display name ends with `.png` when TCX was requested,
  - *When* the result is returned,
  - *Then* the file SHALL be rejected and an error displayed indicating invalid format.
- **AC-5 (Localization Parity)**:
  - *Given* all 9 application locales,
  - *When* running translation parity tests,
  - *Then* `invalid_workout_file_format` SHALL exist and contain `%1$s` across all 9 `strings.xml` files.

---

## 2. Test Specification (TST-UI-254)

### 2.1 Test Suite Structure
1. **SAF MIME Launcher Contract Tests (`ImportFormatFilePickerContractTest.kt`)**:
   - Verify `ImportBackupTabsScreen` defines format-specific MIME constants.
   - Verify `onLocalImportClick("fit")` dispatches with `FIT_MIME_TYPES`.
   - Verify `onLocalImportClick("tcx")` dispatches with `TCX_MIME_TYPES`.
   - Verify `onLocalImportClick("gpx")` dispatches with `GPX_MIME_TYPES`.
   - Assert zero occurrences of `arrayOf("*/*")` in local workout file import triggers.

2. **Defensive Extension Validation Unit Tests (`ImportFormatExtensionValidatorTest.kt`)**:
   - Verify `isMatchingFormat("ride.fit", "fit") == true`.
   - Verify `isMatchingFormat("ride.FIT", "fit") == true`.
   - Verify `isMatchingFormat("ride.tcx", "fit") == false`.
   - Verify `isMatchingFormat("ride.gpx", "tcx") == false`.
   - Verify `isMatchingFormat("document.pdf", "gpx") == false`.
   - Verify `isMatchingFormat("opaque_id", "fit") == true` (no extension fallback).

3. **9-Language Localization Parity Audit (`ImportFormatFilePickerLocalizationTest.kt`)**:
   - Verify `invalid_workout_file_format` exists in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
   - Verify each contains valid format specifier `%1$s`.

4. **Clean-Room Full Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Verification |
|:---|:---|:---|
| **REQ-UI-294.1** (SAF MIME Filtering) | `ImportFormatFilePickerContractTest` | `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, `GPX_MIME_TYPES` verified in launcher calls |
| **REQ-UI-294.2** (Extension Validation) | `ImportFormatExtensionValidatorTest` | Validates format extension matching and opaque ID fallback |
| **REQ-UI-294.3** (Localization Parity) | `ImportFormatFilePickerLocalizationTest` | 9-locale parity for `invalid_workout_file_format` with `%1$s` |
| **REQ-UI-294.4** (Core Invariants) | `testDebugUnitTest` | 100% clean-room test pass rate |
