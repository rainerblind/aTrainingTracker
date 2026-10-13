# Stage 1 Analysis: ATT-2622 - Filter file picker dialogs to matching workout file extensions for FIT, TCX, and GPX import

**Ticket**: [ATT-2622](https://atrainingtracker.atlassian.net/browse/ATT-2622)  
**Sub-task**: [ATT-2705](https://atrainingtracker.atlassian.net/browse/ATT-2705) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2622`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 physical device review (Pixel 10) for ticket `ATT-2338` and human observation on `ATT-2623`, launching local file import for FIT, TCX, or GPX revealed an ergonomic deficiency in the system document picker:
* When tapping "Lokale FIT-Datei auswählen", the Android Storage Access Framework (SAF) document picker displays all files across the filesystem—including unrelated images, camera photos, PDFs, text documents, downloads, and archives.
* The same behavior occurs when selecting "Lokale TCX-Datei auswählen" or "Lokale GPX-Datei auswählen".
* Athletes are forced to manually filter through crowded folders to locate their workout files, introducing cognitive friction and risking the accidental selection of invalid document types.

### Objective
Configure targeted MIME type filtering on the `ActivityResultContracts.OpenDocument` and `ActivityResultContracts.OpenMultipleDocuments` launchers in `ImportBackupTabsScreen.kt`, and implement defensive post-selection file extension validation so that:
1. The system file picker highlights and filters relevant workout file types matching the requested format.
2. Incompatible or non-matching file extensions selected accidentally are cleanly intercepted and reported.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Unfiltered SAF Contracts in `ImportBackupTabsScreen.kt`
In `ImportBackupTabsScreen.kt` (lines 226–234):
```kotlin
onLocalImportClick = { format ->
    when (format) {
        "fit" -> pickFitFilesLauncher.launch(arrayOf("*/*"))
        else -> {
            pendingSingleLegacyFormat = format
            pickLegacyFileLauncher.launch(arrayOf("*/*"))
        }
    }
}
```
Both `pickFitFilesLauncher` and `pickLegacyFileLauncher` invoke `launch(arrayOf("*/*"))`.
Under Android's Storage Access Framework:
* `ActivityResultContracts.OpenDocument` and `OpenMultipleDocuments` translate the input array into `Intent.EXTRA_MIME_TYPES`.
* Specifying `*/*` signals to the Android DocumentsUI provider that every document regardless of MIME type is valid and selectable.

### 2.2 Format-Specific MIME Resolution on Android
Android devices do not register a single universal MIME type for specialized sports activity files, requiring a robust candidate array:
1. **FIT (`.fit`)**:
   - Garmin/Wahoo/ANT standard: `application/vnd.ant.fit`, `application/fit`
   - Android standard fallback for unknown binary formats: `application/octet-stream`
2. **TCX (`.tcx`)**:
   - Garmin XML standard: `application/vnd.garmin.tcx+xml`
   - XML standards: `application/xml`, `text/xml`
   - Fallback: `application/octet-stream`
3. **GPX (`.gpx`)**:
   - GPS Exchange XML standard: `application/gpx+xml`
   - XML standards: `application/xml`, `text/xml`
   - Fallback: `application/octet-stream`

### 2.3 Post-Selection Extension Validation
While Android SAF relies on MIME types rather than file extensions for document picker display filtering, user file providers and cloud drivers sometimes return files under generic MIME categories.
To provide defense-in-depth:
* `ImportBackupTabsScreen.kt` should resolve the file display name via `OpenableColumns.DISPLAY_NAME`.
* If a display name with an extension is present, it must be validated against the expected format (`.fit`, `.tcx`, or `.gpx`).
* If an invalid extension is selected (e.g. `.pdf` or `.png`), the operation is rejected before parsing commences, displaying a clear localized error status (`invalid_workout_file_format`).

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope
* Define centralized format MIME definitions for FIT, TCX, and GPX.
* Update `onLocalImportClick` in `ImportBackupTabsScreen.kt` to launch `pickFitFilesLauncher` and `pickLegacyFileLauncher` with format-specific MIME arrays instead of `arrayOf("*/*")`.
* Add defensive post-selection file extension validation.
* Maintain 100% 9-language localization parity for any new error messages.
* Add unit and contract tests verifying format-specific MIME launches and extension validation.

### 3.2 Out-of-Scope
* Modifying backup archive (`.attbackup`) or full restore picker mechanics.
* Modifying `LegacyImportEngine` stream decoders or SDK parsers.
* Modifying Route GPX importer in `GpxImportActivity`.

---

## 4. Chesterton's Fence & Requirement Archaeology

### 4.1 Requirement Trace
* Refines `REQ-UI-293` (*Format-Specific Import Tab Architecture with Uniform Source Triad*) Clause 2 (Button 1: Local File Picker).
* Extends `REQ-DAT-019` (*External FIT Workout Importer with Duplicate Detection & Sport Mapping*).

### 4.2 Four Mandatory Archaeology Fields
1. **Original Requirement ID & Target**: `REQ-UI-293` Clause 2 (ATT-2623) specified local file picker actions for FIT, TCX, and GPX, but launched with generic `*/*` MIME types.
2. **Historical Origin & Commit Trace**: Commit `5c7441c5` on `feature/ATT-2623` established the 3 format cards and wired `pickFitFilesLauncher` and `pickLegacyFileLauncher` with `arrayOf("*/*")`.
3. **Root Reason for Existing Formulation**: Generic `*/*` was used as a rapid initial implementation to guarantee all files were visible during development, without optimizing SAF document filtering.
4. **Preservation of Core Invariants**: Multi-document batch picking for FIT (`OpenMultipleDocuments`), single-document picking for TCX and GPX (`OpenDocument`), cluster tuning confirmations, and full-suite test pass rates remain strictly preserved.

---

## 5. Architectural Gap Analysis & Proposed SWE.2 Design

```mermaid
graph TD
    User([Athlete]) -->|Taps Local Import Button| FormatCard[FormatImportCard: FIT / TCX / GPX]
    FormatCard -->|onLocalImportClick(format)| MIMEHelper[Resolve Format MIME Types]
    MIMEHelper -->|FIT: ant.fit, fit, octet-stream| FitLauncher[pickFitFilesLauncher.launch]
    MIMEHelper -->|TCX: tcx+xml, xml, text/xml, octet-stream| TcxLauncher[pickLegacyFileLauncher.launch]
    MIMEHelper -->|GPX: gpx+xml, xml, text/xml, octet-stream| GpxLauncher[pickLegacyFileLauncher.launch]
    FitLauncher -->|URIs Returned| Validator[Validate Extension & File Integrity]
    TcxLauncher -->|URI Returned| Validator
    GpxLauncher -->|URI Returned| Validator
    Validator -->|Valid| Engine[Dispatch to Import Engine]
    Validator -->|Invalid Extension| ErrorFeedback[Show Format Mismatch Error]
```

---

## 6. Verification & Test Strategy
1. **SAF MIME Launcher Contract Tests**:
   - Verify `pickFitFilesLauncher` receives FIT MIME types containing `application/vnd.ant.fit`, `application/fit`, and `application/octet-stream`.
   - Verify `pickLegacyFileLauncher` for TCX receives TCX MIME types containing `application/vnd.garmin.tcx+xml` and `application/xml`.
   - Verify `pickLegacyFileLauncher` for GPX receives GPX MIME types containing `application/gpx+xml` and `application/xml`.
2. **Extension Validation Tests**:
   - Verify selecting a non-matching file extension triggers rejection without parsing.
3. **Full Clean-Room Regression**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.
