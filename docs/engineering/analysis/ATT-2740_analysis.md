# Stage 1 Analysis: ATT-2740 - SAF File Picker MIME Handling and Format Validation for FIT, TCX, and GPX Import (Rework)

**Ticket**: [ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740)  
**Sub-task**: [ATT-2875](https://atrainingtracker.atlassian.net/browse/ATT-2875) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.5`)  
**Active Sprint**: `2026-41.5`  
**Branch**: `bugfix/ATT-2740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During Sprint Review 2026-41.4 on a physical Google Pixel 10, the human reviewer evaluated ticket `ATT-2740` and observed two critical defects:
1. *"When I want to import a TCX file, there are still all file types shown (similar for GPX and FIT)."*
2. *"Even worse, when clicking on a TCX file, nothing happens (same for GPX and FIT). Please fix this. I will move the ticket back to 'zu Erledigen'."*

Consequently, `ATT-2740` was rejected (n.i.O.) and bounced back to `Analysis` for thorough technical investigation.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 The Android Storage Access Framework (SAF) MIME Invariant
Android's `Intent.ACTION_OPEN_DOCUMENT` and `ActivityResultContracts.OpenDocument` operate strictly on **MIME types**, passed via `Intent.EXTRA_MIME_TYPES`.
Crucially:
- **No Intent Extra for File Extensions**: Android's Storage Access Framework provides **no API** to filter files by file extension (e.g. `*.fit`, `*.tcx`, `*.gpx`).
- **Android `MimeTypeMap` Has No Entry for Workout Formats**: In AOSP's platform MIME mapping table (`MimeMap` / `MimeTypeMap`), custom extensions `.fit`, `.tcx`, and specialized `.gpx` have **no registered platform MIME types**.
- **System DocumentsProvider Classification**: When Android's internal providers (`ExternalStorageProvider`, `DownloadProvider`) inspect files on disk or in the `Download/` folder, any file whose extension is not in `MimeTypeMap` is assigned the default fallback: `application/octet-stream`.

### 2.2 Why Clicking a File Did Nothing (Defect 2)
In the previous attempt (commit `424a17d5`), `application/octet-stream` was stripped from the filter arrays:
```kotlin
val FIT_MIME_TYPES = arrayOf("application/vnd.ant.fit", "application/fit")
val TCX_MIME_TYPES = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")
val GPX_MIME_TYPES = arrayOf("application/gpx+xml", "application/xml", "text/xml")
```
When launched on a real device:
1. Every `.fit` file on the filesystem is classified by Android as `application/octet-stream`. Because `application/octet-stream` was missing from `FIT_MIME_TYPES`, DocumentsUI treated all `.fit` files as non-matching and disabled/greyed them out. On-device screencap verified that the picker displayed *"Keine Elemente"* or greyed-out items.
2. Similarly, `.tcx` files downloaded to the device are assigned `application/octet-stream`. DocumentsUI evaluated `TCX_MIME_TYPES` (`application/vnd.garmin.tcx+xml`, `application/xml`, `text/xml`), found no match, and disabled `.tcx` files.
3. When the user tapped on a disabled `.tcx`, `.fit`, or `.gpx` file, DocumentsUI ignored the tap. **Nothing happened.**

### 2.3 Product Management Alignment (Human Decision: Option A)
Following Gate 1 audit challenge regarding SAF limitations vs. initial acceptance criteria, the issue was escalated to the Human Product Owner. 
The Human PO decided on **Option A**:
- Retain the native Android Storage Access Framework document picker with `application/octet-stream` enabled so `.fit`, `.tcx`, and `.gpx` files are clickable and importable across all devices and Android OS versions.
- Enforce strict defense-in-depth file extension validation in `ImportFileValidator` upon selection.
- Update acceptance criteria on parent ticket `ATT-2740` to reflect this platform constraint.

---

## 3. User Scope Grounding & Revised Acceptance Criteria

### 3.1 In-Scope Goals
1. **Restore Selectability for Workout Files**: Include `application/octet-stream` in `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES` so that `.fit`, `.tcx`, and `.gpx` files are recognized as valid and clickable in the system document picker.
2. **Defensive Post-Selection Extension Validation**: Ensure `ImportFileValidator.isMatchingFormat` strictly checks the resolved file extension (`.fit`, `.tcx`, `.gpx`).
3. **Graceful User Guidance on Incompatible Selection**: If an athlete mistakenly selects a non-workout file (e.g. `.pdf`, `.zip`, `.jpg`) from the picker, cleanly intercept it, do NOT invoke the parser or bottom sheet, and display the localized `invalid_workout_file_format` toast.
4. **Contract & Regression Tests**: Update `ImportFormatFilePickerContractTest.kt` to enforce the inclusion of `application/octet-stream` alongside specific MIME types, guaranteeing selectability.

### 3.2 Revised Acceptance Criteria (Given-When-Then)
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

## 4. Architectural Strategy & High-Level Solution

### 4.1 MIME Definitions in `ImportBackupTabsScreen.kt`
```kotlin
// Format-specific MIME type definitions (ATT-2622, ATT-2740 / REQ-UI-294)
// application/octet-stream is mandatory so Android DocumentsUI permits selection of .fit, .tcx, and .gpx files
val FIT_MIME_TYPES = arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")
val TCX_MIME_TYPES = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")
val GPX_MIME_TYPES = arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")
```

### 4.2 Verification Strategy
1. **Contract Test Alignment**: `ImportFormatFilePickerContractTest.kt` asserts that `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES` include `application/octet-stream`.
2. **Post-Selection Validation**: `ImportFormatExtensionValidatorTest.kt` asserts strict extension checking.
3. **Clean-Room Regression**: Full test suite passes 100%.
4. **Physical Device Verification**: Confirm on Pixel 10 that `.tcx`, `.gpx`, and `.fit` files can be clicked and launch the import flow.
