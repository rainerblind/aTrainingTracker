# Stage 2: Requirement & Test Specification - ATT-2740: SAF File Picker MIME Handling and Format Validation for FIT, TCX, and GPX Import (Rework)

**Ticket**: [ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740)  
**Sub-task**: [ATT-2876](https://atrainingtracker.atlassian.net/browse/ATT-2876) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.5`)  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-294` (*Workout File Picker MIME Filtering, Selectability, and Defensive Format-Extension Validation (FIT, TCX, GPX)*)  
**Test Spec ID**: `TST-UI-254` (*Workout File Picker Selectability and Format-Extension Validation Verification*)  
**Branch**: `bugfix/ATT-2740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (`REQ-UI-294`)

### 1.1 Problem Statement & Rationale
During Sprint Review 2026-41.4 on a physical Google Pixel 10, clicking on a TCX, FIT, or GPX file in the system file picker produced no reaction ("nothing happens").
Investigation revealed that Android's Storage Access Framework (SAF) does not support filtering by file extension, and Android has no registered platform MIME types for `.fit` or `.tcx`. Consequently, Android classifies them as `application/octet-stream`. Stripping `application/octet-stream` in the previous attempt caused Android's DocumentsUI to consider all `.fit` and `.tcx` files as non-matching, disabling them and ignoring user taps.
Under the approved Option A Product Decision, selectability of `.fit`, `.tcx`, and `.gpx` files in the native system document picker is restored by including `application/octet-stream` alongside format-specific MIME signatures, while strict defense-in-depth file extension enforcement is provided by `ImportFileValidator` upon selection.

### 1.2 Functional & Architectural Requirements
The system SHALL configure Storage Access Framework (SAF) document picker MIME arrays to guarantee file selectability and enforce defensive extension validation on selected files in `ImportBackupTabsScreen.kt` (ATT-2622, ATT-2740):
1. **Guaranteed Selectability MIME Arrays (`ImportBackupTabsScreen.kt`)**:
   - `FIT_MIME_TYPES` SHALL contain `arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")`.
   - `TCX_MIME_TYPES` SHALL contain `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")`.
   - `GPX_MIME_TYPES` SHALL contain `arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")`.
2. **Defensive Extension & Format Validation**:
   - Upon URI selection, the application SHALL inspect the file's display name (`OpenableColumns.DISPLAY_NAME`) via `ImportFileValidator.resolveDisplayName`.
   - If the display name contains an explicit file extension, it SHALL match the requested format (case-insensitive: `.fit` for FIT, `.tcx` for TCX, `.gpx` for GPX).
   - If the selected file's extension conflicts with the requested format (e.g. user selects a `.pdf` or `.png`), the file SHALL be rejected before stream ingestion, displaying a clear localized error status (`invalid_workout_file_format`).
   - If the display name does not specify an extension (e.g. content provider opaque ID), the file SHALL proceed to content/header validation in `LegacyImportEngine` without premature rejection.
3. **100% 9-Language Localization Parity**:
   - String resource `invalid_workout_file_format` SHALL maintain 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
4. **Preserved Invariants**:
   - Multi-document selection for FIT (`OpenMultipleDocuments`) and single-document selection for TCX/GPX (`OpenDocument`) remain preserved.
   - Pre-import tuning bottom sheet (`PreImportTuningBottomSheet`) is launched upon selecting a valid single TCX or GPX file.

### 1.3 Acceptance Criteria (Given-When-Then) - Option A
* **Criterion 1 (Selectability & Import Execution)**:
  * *Given* the athlete taps 'Lokale Datei wählen' on the FIT, TCX, or GPX card
  * *When* the system document picker opens and the athlete selects a valid workout file matching the card format (.fit, .tcx, or .gpx)
  * *Then* the file is selectable, clickable, and the corresponding import workflow (pre-import tuning bottom sheet or multi-FIT import) is launched without failure.
* **Criterion 2 (Defensive Non-Matching Rejection)**:
  * *Given* the athlete selects an incompatible file (e.g. .pdf, .jpg, .zip, or wrong workout format)
  * *When* the selection callback returns to the application
  * *Then* the import is blocked by `ImportFileValidator`, no corrupted parsing is attempted, and a clear localized toast notification (`invalid_workout_file_format`) informs the user of the expected format.
* **Criterion 3 (Contract & Localization Parity)**:
  * *Given* unit and contract test execution
  * *Then* `ImportFormatFilePickerContractTest` and `ImportFormatExtensionValidatorTest` pass 100% and string resources maintain complete parity across all 9 locales.

---

## 2. Test Specification (`TST-UI-254`)

### 2.1 Test Architecture & Coverage Matrix

| Test ID | Level | Target Component | Verifies |
| :--- | :--- | :--- | :--- |
| `TST-UI-254.1` | Unit (Contract) | `ImportFormatFilePickerContractTest` | MIME arrays include `application/octet-stream` alongside specific signatures |
| `TST-UI-254.2` | Unit (Logic) | `ImportFormatExtensionValidatorTest` | Extension validation matches `.fit`, `.tcx`, `.gpx` case-insensitively and rejects non-matching |
| `TST-UI-254.3` | Unit (I18N) | `ImportFormatLocalizationTest` | 100% string parity across all 9 locales for `invalid_workout_file_format` |
| `TST-UI-254.4` | System | Clean-room `./gradlew testDebugUnitTest` | Zero regressions across complete unit test suite |

### 2.2 Test Cases & Assertions

```kotlin
@Test
fun testFitMimeTypes_containsTargetedMimeSignaturesAndOctetStream() {
    val expected = arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")
    assertArrayEquals(expected, FIT_MIME_TYPES)
}

@Test
fun testTcxMimeTypes_containsTargetedMimeSignaturesAndOctetStream() {
    val expected = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")
    assertArrayEquals(expected, TCX_MIME_TYPES)
}

@Test
fun testGpxMimeTypes_containsTargetedMimeSignaturesAndOctetStream() {
    val expected = arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")
    assertArrayEquals(expected, GPX_MIME_TYPES)
}
```
