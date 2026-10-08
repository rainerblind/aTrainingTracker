# Stage 3: Implementation Plan - ATT-2740: Filter file picker strictly by file extension for FIT, TCX, and GPX import

**Ticket**: [ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740)  
**Sub-task**: [ATT-2809](https://atrainingtracker.atlassian.net/browse/ATT-2809) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-294` (*Workout File Picker MIME Filtering and Defensive Format-Extension Validation (FIT, TCX, GPX)*)  
**Test Mapping**: `TST-UI-254` (*Workout File Picker MIME Filtering and Format-Extension Validation Verification*)  
**Branch**: `improvement/ATT-2740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

In `ImportBackupTabsScreen.kt`, launching the file picker from any of the three format cards (FIT, TCX, GPX) currently invokes `pickFitFilesLauncher` or `pickLegacyFileLauncher` with MIME type arrays that include `application/octet-stream`. In Android's Storage Access Framework (SAF), `application/octet-stream` is treated as a generic wildcard for unclassified binary data. This caused the document picker on physical test devices (Pixel 10) to display and permit selection of arbitrary non-workout files (photos, audio, videos, APKs, PDFs, zips), prompting the athlete's observation during the Sprint 2026-41.3 review.

This implementation plan defines the exact steps to eliminate `application/octet-stream` from `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES`, update contract assertions in `ImportFormatFilePickerContractTest.kt`, and verify end-to-end format isolation.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-294` (*Workout File Picker MIME Filtering and Defensive Format-Extension Validation (FIT, TCX, GPX)*)
* **Test Mapping**: `TST-UI-254` (*Workout File Picker MIME Filtering and Format-Extension Validation Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Defensive Post-Selection Validation**: `ImportFileValidator.isMatchingFormat` and `resolveDisplayName` continue to validate file extensions upon selection and display localized Toast `invalid_workout_file_format` if an invalid file is returned.
3. **Multi-Document vs Single-Document Invariant**: FIT retains `ActivityResultContracts.OpenMultipleDocuments()`; TCX and GPX retain `ActivityResultContracts.OpenDocument()`.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ImportBackupTabsScreen.kt`
* Modify `FIT_MIME_TYPES`:
  ```kotlin
  val FIT_MIME_TYPES = arrayOf("application/vnd.ant.fit", "application/fit")
  ```
* Modify `TCX_MIME_TYPES`:
  ```kotlin
  val TCX_MIME_TYPES = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")
  ```
* Modify `GPX_MIME_TYPES`:
  ```kotlin
  val GPX_MIME_TYPES = arrayOf("application/gpx+xml", "application/xml", "text/xml")
  ```

### Component 2: `ImportFormatFilePickerContractTest.kt`
* Update contract test assertions to check that:
  - `FIT_MIME_TYPES` matches `arrayOf("application/vnd.ant.fit", "application/fit")`.
  - `TCX_MIME_TYPES` matches `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")`.
  - `GPX_MIME_TYPES` matches `arrayOf("application/gpx+xml", "application/xml", "text/xml")`.
  - Add explicit assertions verifying that none of the arrays contain `"application/octet-stream"`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `ImportBackupTabsScreen.kt` format cards (FIT, TCX, GPX).
* **Reused components**: `OutlinedButton`, `ElevatedCard`, `ImportFileValidator`.
* **Theme tokens**: No visual layout or styling changes; purely restricts intent extras passed to the system SAF file picker.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update MIME Type Array Constants in `ImportBackupTabsScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* **Changes**: Remove `"application/octet-stream"` from `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES`.

### Step 2: Update Contract Assertions in `ImportFormatFilePickerContractTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatFilePickerContractTest.kt`
* **Changes**: Update expected array signatures and add checks ensuring `"application/octet-stream"` is not present.

### Step 3: Run Targeted Unit & Contract Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.ImportFormat*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted test execution: `ImportFormatFilePickerContractTest`, `ImportFormatExtensionValidatorTest`, `ImportFormatCardsContractTest`, `ImportFormatLocalizationTest`.
  - Clean-room regression suite: `./gradlew testDebugUnitTest` (all tests passing).
* **Rollback**: Branch isolation allows full revert via `git reset --hard` or deleting `improvement/ATT-2740`.
