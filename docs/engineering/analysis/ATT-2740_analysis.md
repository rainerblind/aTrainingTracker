# Stage 1 Analysis: ATT-2740 - Filter file picker strictly by file extension for FIT, TCX, and GPX import

**Ticket**: [ATT-2740](https://atrainingtracker.atlassian.net/browse/ATT-2740)  
**Sub-task**: [ATT-2807](https://atrainingtracker.atlassian.net/browse/ATT-2807) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Branch**: `improvement/ATT-2740`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-41.3 human review on a physical Google Pixel 10 (Sprint Review of ticket ATT-2622), an athlete observed:
> *"this does not work properly. I still got all shown. Can't we filter with respect to the file ending?"*

When launching the file picker from any of the three format cards (FIT, TCX, GPX) on `ImportBackupTabsScreen.kt`, the Android Storage Access Framework (SAF) document picker continues to display and enable all non-workout files (images, audio, videos, zips, APKs, PDFs, etc.) as selectable, rather than filtering to files matching the chosen format.

### Expected Behavior
- When tapping "Lokale FIT-Datei(en) wählen", the document picker filters to FIT-specific types and does not allow arbitrary media/binary files to be selected.
- When tapping "Lokale TCX-Datei wählen", the document picker filters to XML-based TCX documents.
- When tapping "Lokale GPX-Datei wählen", the document picker filters to XML-based GPX documents.
- In all cases, files that do not match the expected workout format are filtered out in the picker and defensively rejected upon selection.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 The Presence of `application/octet-stream` in MIME Arrays
In `ImportBackupTabsScreen.kt` (lines 85–87):
```kotlin
val FIT_MIME_TYPES = arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")
val TCX_MIME_TYPES = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")
val GPX_MIME_TYPES = arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")
```
Under Android's Storage Access Framework:
1. `ActivityResultContracts.OpenDocument` and `OpenMultipleDocuments` pass the provided array into `Intent.EXTRA_MIME_TYPES`.
2. In Android, `application/octet-stream` is the universal fallback MIME type for arbitrary binary data. Android's internal `DocumentsProvider` implementations (`ExternalStorageProvider`, `DownloadProvider`, `MediaDocumentsProvider`, Google Drive) treat `application/octet-stream` as a wildcard for all unclassified binary files.
3. Because `application/octet-stream` was included as a fallback in `TCX_MIME_TYPES`, `GPX_MIME_TYPES`, and `FIT_MIME_TYPES`, Android's DocumentsUI considered virtually *every* file on the device (photos, videos, downloads, PDFs, APKs) as matching the filter criteria.

### 2.2 Android SAF Extension Filtering Limitations & MIME Resolution
A technical investigation of Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT`) reveals:
1. **No Intent Extra for Extension Filtering**: The Android OS framework does not support filtering by file extension (e.g., `*.fit`, `*.tcx`, `*.gpx`) at the document picker intent level. SAF is architected strictly around MIME types.
2. **Immutable System `MimeTypeMap`**: Android's `MimeTypeMap` is a system-level singleton that cannot be dynamically registered or extended by client applications at runtime.
3. **MIME Classification for XML Formats (TCX & GPX)**:
   - TCX and GPX are XML-based formats. Android maps `.tcx` and `.gpx` to `application/xml` or `text/xml`, as well as vendor types `application/vnd.garmin.tcx+xml` and `application/gpx+xml`.
   - By eliminating `application/octet-stream`, all non-XML binary files (images, audio, videos, APKs, PDFs, zips) are immediately filtered out by the system document picker.
4. **MIME Classification for FIT**:
   - FIT is a binary format whose vendor-specific MIME types are `application/vnd.ant.fit` and `application/fit`.
   - Eliminating `application/octet-stream` from `FIT_MIME_TYPES` restricts the picker strictly to FIT MIME types.
5. **Defense-in-Depth Post-Selection Validation**:
   - `ImportFileValidator.isMatchingFormat` is already integrated in `ImportBackupTabsScreen.kt` to inspect the selected URI's display name (`OpenableColumns.DISPLAY_NAME`) and reject non-matching extensions before any parsing begins.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Goals
1. **Eliminate `application/octet-stream`**: Remove `application/octet-stream` from `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES` in `ImportBackupTabsScreen.kt`.
2. **Configure Strict Targeted MIME Types**:
   - `FIT_MIME_TYPES = arrayOf("application/vnd.ant.fit", "application/fit")`
   - `TCX_MIME_TYPES = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")`
   - `GPX_MIME_TYPES = arrayOf("application/gpx+xml", "application/xml", "text/xml")`
3. **Update Contract & Unit Tests**: Update `ImportFormatFilePickerContractTest.kt` to assert the strict MIME type arrays without `application/octet-stream`.
4. **Living Documentation Refinement**: Update `REQ-UI-294` in `docs/requirements.md` and `TST-UI-254` in `docs/tests.md` to reflect the strict MIME type specifications and pass Chesterton's Fence archaeology.

### 3.2 Out-of-Scope Non-Goals (Scope Bounding)
1. Modifying full backup archive (`.attbackup`) or database restore file pickers.
2. Modifying Dropbox or Google Drive cloud recovery scanning algorithms.
3. Modifying `LegacyImportEngine` parsers or FIT decoder libraries.
4. Modifying route GPX import in `GpxImportActivity`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-294` (*Workout File Picker MIME Filtering and Defensive Format-Extension Validation (FIT, TCX, GPX)*), targeting Clause 1 (`FIT_MIME_TYPES`, `TCX_MIME_TYPES`, `GPX_MIME_TYPES`).
* **Historical Origin & Commit Trace**: Ticket `ATT-2622`, Sprint `2026-41.3`, commit `c75b8e97`.
* **Root Reason for Existing Formulation**: In `ATT-2622`, `application/octet-stream` was added as a defensive fallback under the assumption that some Android storage providers might fail to resolve specialized MIME types. However, this fallback caused Android's Storage Access Framework to treat all generic binary files (images, audio, videos, zips, APKs, PDFs) as selectable, defeating the purpose of format-specific picker filtering as observed on a physical Pixel 10 during the Sprint 2026-41.3 review.
* **Preservation of Core Invariants**: Defensive post-selection file extension validation via `ImportFileValidator` (`REQ-UI-294` Clause 2), localized error toast `invalid_workout_file_format` across all 9 locales (`REQ-UI-294` Clause 3), multi-document selection for FIT, single-document selection for TCX/GPX, and full regression test pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Array Definitions Update in `ImportBackupTabsScreen.kt`
```kotlin
// Format-specific MIME type definitions (ATT-2740 / REQ-UI-294)
val FIT_MIME_TYPES = arrayOf("application/vnd.ant.fit", "application/fit")
val TCX_MIME_TYPES = arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml")
val GPX_MIME_TYPES = arrayOf("application/gpx+xml", "application/xml", "text/xml")
```

### 5.2 Verification Strategy
1. **Contract Test Alignment**: Update `ImportFormatFilePickerContractTest.kt` to verify that `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES` strictly match the updated arrays and do NOT contain `application/octet-stream`.
2. **Defensive Validation Verification**: Confirm that `ImportFileValidatorTest` and `ImportFormatExtensionValidatorTest` continue to pass 100%.
3. **Full Clean-Room Regression**: Execute `./gradlew testDebugUnitTest` to guarantee zero regressions.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Multi-document picking for FIT (`ActivityResultContracts.OpenMultipleDocuments()`) and single-document picking for TCX and GPX (`ActivityResultContracts.OpenDocument()`) are preserved.
  2. Post-selection extension validation and localized error notifications (`invalid_workout_file_format`) across all 9 locales remain intact.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Self-contained constant adjustments in `ImportBackupTabsScreen.kt` with zero database or concurrency impacts.
