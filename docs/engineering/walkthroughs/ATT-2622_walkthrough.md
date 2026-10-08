# Stage 5 Walkthrough: ATT-2622 - Filter file picker dialogs to matching workout file extensions for FIT, TCX, and GPX import

**Ticket**: [ATT-2622](https://atrainingtracker.atlassian.net/browse/ATT-2622)  
**Sub-task**: [ATT-2709](https://atrainingtracker.atlassian.net/browse/ATT-2709) (`[Test]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2622`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Executive Summary
ATT-2622 addresses the unrestricted file picker dialogs in the newly restructured Import tab (`ATT-2623`). Prior to this change, picking local files launched system document pickers with generic wildcard MIME types (`arrayOf("*/*")`), showing all files (including images, audio, PDFs) and providing zero post-selection format validation before attempting stream parsing.

Under `REQ-UI-294`:
1. **Format-Specific MIME Arrays**: Document picker launchers now provide targeted MIME types:
   - FIT: `arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")`
   - TCX: `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")`
   - GPX: `arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")`
2. **Defensive Extension Validation (`ImportFileValidator.kt`)**: Selected URIs have their display name verified against the expected format before ingestion. If an explicit mismatch occurs, an informative localized toast/message is displayed (`invalid_workout_file_format`). If the URI has no extension (e.g. opaque content provider ID), it safely passes through to content-level stream inspection.
3. **100% 9-Language Localization Parity**: `invalid_workout_file_format` added across all 9 application locales.
4. **Clean-Room Verification**: Full regression suite passed 100% with zero regressions.

---

## 2. Changes Summary

| Component | Target File | Key Changes |
|:---|:---|:---|
| **MIME Constants & Screen Wiring** | `ImportBackupTabsScreen.kt` | Added `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, `GPX_MIME_TYPES`; updated `onLocalImportClick` to launch format pickers; wired post-selection validation in `pickLegacyFileLauncher` and `pickFitFilesLauncher`. |
| **Validation Engine** | `ImportFileValidator.kt` | Created utility object with `isMatchingFormat` and `resolveDisplayName` helpers. |
| **Localization** | `strings.xml` (9 locales) | Added `invalid_workout_file_format` across EN, DE, ES, FR, IT, JA, NL, PL, PT. |
| **Contract & Parity Tests** | `ImportFormat*Test.kt` | Created `ImportFormatExtensionValidatorTest`, `ImportFormatFilePickerContractTest`, and `ImportFormatFilePickerLocalizationTest`. |
| **Living Documentation** | `requirements.md`, `tests.md` | Promoted `REQ-UI-294` and `TST-UI-254` to `Verified`. |

---

## 3. Test & Verification Results

### 3.1 Targeted Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.ImportFormat*"
```
- Result: **BUILD SUCCESSFUL** in 58s.
- 100% of targeted contract, extension validation, and localization tests passed.

### 3.2 Full Regression Suite
```bash
./gradlew testDebugUnitTest
```
- Result: **BUILD SUCCESSFUL** in 9m 21s.
- Total tasks: 32 actionable, 12 executed, 20 up-to-date.
- Zero failures, zero regressions across entire project test suite.

---

## 4. UI Consistency & Invariants
- **Consistency**: Toast and feedback strings use standard Material / Android messaging patterns. Pickers integrate seamlessly into `ImportTabContent`.
- **Invariants Preserved**:
  - Multi-document FIT selection preserved (`OpenMultipleDocuments`).
  - Pre-import cluster parameter tuning sheet preserved (`PreImportTuningBottomSheet`).
  - Cloud recovery format scoping preserved.
  - Zero regression on database restore or incremental import.

---

## 5. Gate Review Sign-Off Recommendation
- **Gate 1 (Analysis)**: Passed (`ATT-2705`).
- **Gate 2 (Test Spec)**: Passed (`ATT-2706`).
- **Gate 3 (Plan)**: Passed (`ATT-2707`).
- **Gate 4 (Implementation)**: Passed (`ATT-2708`).
- **Gate 5 (Verification)**: Ready for Gate 5 review (`ATT-2709`).
