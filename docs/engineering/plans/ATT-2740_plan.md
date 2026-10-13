# Stage 3: Implementation Plan - ATT-2740: Restore octet-stream MIME handling and format validation (Rework Option A)

**Ticket**: [ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740)  
**Sub-task**: [ATT-2877](https://atrainingtracker.atlassian.net/browse/ATT-2877) (`[Impl Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.5`)  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-294` (*Workout File Picker MIME Filtering, Selectability, and Defensive Format-Extension Validation (FIT, TCX, GPX)*)  
**Test Mapping**: `TST-UI-254` (*Workout File Picker Selectability and Format-Extension Validation Verification*)  
**Branch**: `bugfix/ATT-2740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Root Cause Analysis

During Ceremony 2 review of ATT-2740 on a physical Google Pixel 10 (Android 16 DP / API 35+), the athlete observed that when tapping "Lokale TCX-Datei wählen", all files were still displayed in the SAF system picker, and tapping on a `.tcx` (or `.fit` / `.gpx`) file resulted in no response (unclickable / greyed out).

**Root Cause**:
Android's Storage Access Framework (`DocumentsUI`) relies on registered system MIME types to determine which files are selectable. Standard Android OS installations do not include native MIME type associations for `.fit` (`application/vnd.ant.fit`) or `.tcx` (`application/vnd.garmin.tcx+xml`). Storage providers (local download managers, Google Drive, SD card providers) frequently index these files as generic binary data (`application/octet-stream`).
When ATT-2740 stripped `application/octet-stream` from the MIME type filter array, SAF concluded that none of the files matched the requested MIME types, disabling them or ignoring click events.

**Solution (Option A - Selectability First)**:
Restore `application/octet-stream` in `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES` so that files on storage providers lacking specialized MIME mapping remain selectable and clickable. Rely on our proven defense-in-depth validator (`ImportFileValidator`) to enforce that the selected file actually has the expected extension (`.fit`, `.tcx`, `.gpx`), displaying a clear localized error toast (`invalid_workout_file_format`) if an unsupported or mismatched file is picked.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-294` (*Workout File Picker MIME Filtering, Selectability, and Defensive Format-Extension Validation (FIT, TCX, GPX)*)
* **Test Mapping**: `TST-UI-254` (*Workout File Picker Selectability and Format-Extension Validation Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Guaranteed Selectability**: Tapping a `.fit`, `.tcx`, or `.gpx` file in the SAF picker MUST successfully return the URI to the app and start the import process.
2. **Defensive Post-Selection Validation**: `ImportFileValidator.isMatchingFormat` and `resolveDisplayName` MUST continue to inspect the selected file's display name and reject invalid extensions (e.g. `.pdf`, `.png`, wrong format) before passing to the parser.
3. **Multi-Document vs Single-Document Invariant**: FIT retains `ActivityResultContracts.OpenMultipleDocuments()`; TCX and GPX retain `ActivityResultContracts.OpenDocument()`.
4. **9-Language Localization Parity**: `invalid_workout_file_format` remains localized across all 9 locales.
5. **Zero Unintended Regressions**: Full clean-room test suite (`./gradlew testDebugUnitTest`) must pass with 100%.

---

## 4. Proposed Architectural Changes

### Component 1: `ImportBackupTabsScreen.kt`
* Update `FIT_MIME_TYPES`:
  ```kotlin
  val FIT_MIME_TYPES = arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")
  ```
* Update `TCX_MIME_TYPES`:
  ```kotlin
  val TCX_MIME_TYPES = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")
  ```
* Update `GPX_MIME_TYPES`:
  ```kotlin
  val GPX_MIME_TYPES = arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")
  ```

### Component 2: `ImportFormatFilePickerContractTest.kt`
* Update contract test assertions to verify that:
  - `FIT_MIME_TYPES` contains `"application/vnd.ant.fit"`, `"application/fit"`, and `"application/octet-stream"`.
  - `TCX_MIME_TYPES` contains `"application/vnd.garmin.tcx+xml"`, `"application/xml"`, `"text/xml"`, and `"application/octet-stream"`.
  - `GPX_MIME_TYPES` contains `"application/gpx+xml"`, `"application/xml"`, `"text/xml"`, and `"application/octet-stream"`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `ImportBackupTabsScreen.kt` format cards.
* **Reused components**: No UI element changes; purely internal MIME filter array configuration and callback execution.
* **Theme tokens**: Untouched.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update MIME Arrays in `ImportBackupTabsScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* Add `"application/octet-stream"` to `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES`.

### Step 2: Update Contract Assertions in `ImportFormatFilePickerContractTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatFilePickerContractTest.kt`
* Assert that `"application/octet-stream"` is present in all three MIME arrays to guarantee file selectability across Android storage providers.

### Step 3: Run Targeted Unit & Contract Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.ImportFormat*"`

### Step 4: Full Clean-Room Regression Suite
* **Command**: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run all migration unit and contract tests: `ImportFormatFilePickerContractTest`, `ImportFormatExtensionValidatorTest`, `ImportFormatCardsContractTest`, `ImportFormatLocalizationTest`.
  - Execute full suite `./gradlew testDebugUnitTest` asserting 0 failures.
* **Rollback**:
  - `git checkout develop` or revert changes via `git reset --hard`.
