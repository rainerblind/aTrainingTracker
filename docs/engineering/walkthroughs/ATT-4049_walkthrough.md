# Stage 5 Walkthrough: ATT-4049 - Support selecting and importing multiple GPX route files at once

**Ticket**: [ATT-4049](https://atrainingtracker.atlassian.net/browse/ATT-4049)  
**Sub-task**: [ATT-4280](https://atrainingtracker.atlassian.net/browse/ATT-4280) (`[Test]`)  
**Parent Epic**: [ATT-1144](https://atrainingtracker.atlassian.net/browse/ATT-1144) (*Mapping & Waypoint Management*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-MAP-053` (*Multi-File GPX Route Batch Import & Single-Route Edit Preservation*)  
**Test Spec ID**: `TST-MAP-055`  
**Branch**: `feature/ATT-4049`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Verification Outcome

Ticket `ATT-4049` introduces seamless batch importing of multiple GPX route files from the routes screen and via Android system sharing (`ACTION_SEND_MULTIPLE`), while strictly preserving the existing single-route interactive editing workflow (`GpxImportActivity` and `EditRouteScreen`).

### Verification Outcome:
1. **Multi-File Picker Contract (`REQ-MAP-053.1`)**:
   - `RoutesScreen.kt` replaces `ActivityResultContracts.GetContent()` with `ActivityResultContracts.GetMultipleContents()`.
   - The launcher invokes `launch("*/*")` allowing multi-selection across file managers.
2. **Single vs. Multi-File Selection Dispatching (`REQ-MAP-053.2`)**:
   - When the user selects exactly 1 URI (`uris.size == 1`), the existing single-route workflow is triggered via `GpxImportActivity`, opening `EditRouteScreen` for manual naming, sport type selection, and elevation enrichment.
   - When the user selects multiple URIs (`uris.size > 1`), `RoutesViewModel.importRoutesBatch(uris)` executes directly in the background.
3. **Robust Batch Execution & Fault Tolerance (`REQ-MAP-053.3`)**:
   - `RoutesViewModel.kt` imports each route sequentially using `GpxRouteImporter` and inserts successfully parsed routes into `RoutesRepository`.
   - Any corrupt or invalid file is caught, logged, and incremented in `failedCount` without aborting remaining imports.
4. **Non-Dismissible Modal Progress Dialog & Summary Feedback (`REQ-MAP-053.4`)**:
   - While batch import is active, `RoutesScreen.kt` displays an M3 `Dialog` showing `CircularProgressIndicator` and localized `routes_import_progress` ("Importing routes (%1$d of %2$d)…").
   - Upon completion, an M3 `Snackbar` displays localized results: `routes_import_success` ("%1$d routes imported successfully."), `routes_import_partial` ("%1$d routes imported, %2$d failed."), or `routes_import_all_failed` ("Failed to import routes.").
5. **External Multi-File Share Support (`REQ-MAP-053.5`)**:
   - `AndroidManifest.xml` declares `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intent filters on `GpxImportActivity`.
   - `GpxImportActivity.kt` and `GpxImportViewModel.kt` parse multiple streams, executing batch import and displaying progress/Toast.
6. **9-Language Localization Parity (`REQ-MAP-053.6`)**:
   - Strings `routes_import_progress`, `routes_import_success`, `routes_import_partial`, and `routes_import_all_failed` are translated across EN, DE, ES, FR, IT, JA, NL, PL, and PT with 100% placeholder format consistency.

---

## 2. Requirement & Test Traceability Matrix

| Requirement Clause | Test Spec ID | Scope | Target File | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-MAP-053.1` (Multi-Picker Contract) | `TST-MAP-055_1`, `TST-MAP-055_4` | Compose Contract | `RoutesScreen.kt` | Unit & Activity Flow | **PASSED** | `Verified` |
| `REQ-MAP-053.2` (Single vs Batch Flow) | `TST-MAP-055_1` | Contract / Flow | `RoutesScreen.kt`, `GpxImportActivity.kt` | Unit & Activity Flow | **PASSED** | `Verified` |
| `REQ-MAP-053.3` (Batch Import Execution) | `TST-MAP-055_1`, `TST-MAP-055_2`, `TST-MAP-055_3`, `TST-MAP-055_4`, `TST-MAP-055_5` | Unit / ViewModel | `RoutesViewModel.kt`, `RoutesViewModelTest.kt` | JUnit / Coroutine Unit Tests | **PASSED** | `Verified` |
| `REQ-MAP-053.4` (Progress Dialog & Snackbar) | `TST-MAP-055_1`, `TST-MAP-055_2` | UI State Mapping | `RoutesScreen.kt`, `RoutesViewModel.kt` | Unit & Flow State Inspection | **PASSED** | `Verified` |
| `REQ-MAP-053.5` (External Multi-Share Support) | `TST-MAP-055_6` | Unit / Intent | `AndroidManifest.xml`, `GpxImportViewModelTest.kt` | Unit / Intent Filter Verification | **PASSED** | `Verified` |
| `REQ-MAP-053.6` (9-Language Parity) | `TST-MAP-055_7` | Localization | `res/values*/strings.xml`, `TranslationParityTest.kt` | Automated String Parity Test | **PASSED** | `Verified` |
| Full Regression Suite | `TST-MAP-055_8` | Full Regression | Entire Codebase | `./gradlew testDebugUnitTest` | **PASSED** | `Verified` |

---

## 3. Test Execution Verification

### 3.1 Targeted Test Suites
- **Routes & Importer Tests**:
  * Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`
  * Result: 100% pass rate across all route tests including `RoutesViewModelTest` (5 tests) and `GpxImportViewModelTest` (6 tests).
- **Localization Parity Test**:
  * Command: `./gradlew testDebugUnitTest --tests "*TranslationParityTest*"`
  * Result: 100% pass rate across all 9 languages without formatting mismatches.

### 3.2 Clean-Room Full Regression Suite
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL** with 100% pass rate across all modules and tests in 10m 8s.

---

## 4. Modified & Created Files

* [AndroidManifest.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/AndroidManifest.xml):
  * Added `<intent-filter>` for `ACTION_SEND` and `ACTION_SEND_MULTIPLE` to `GpxImportActivity`.
* [RoutesScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt):
  * Replaced `ActivityResultContracts.GetContent()` with `ActivityResultContracts.GetMultipleContents()`.
  * Added single vs. batch URI dispatching.
  * Added modal progress `Dialog` displaying `routes_import_progress`.
  * Added `Snackbar` displaying localized result summary.
* [RoutesViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt):
  * Added `BatchImportProgress` and `BatchImportResult` data models.
  * Added `batchImportProgress` and `batchImportResult` StateFlows.
  * Added `importRoutesBatch(uris)` and `clearBatchImportResult()`.
  * Injected `GpxRouteImporter` via `@JvmOverloads` constructor.
* [RoutesFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesFragment.kt):
  * Removed dead orphaned `gpxPickerLauncher` and unused imports.
* [GpxImportActivity.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt):
  * Extracted streams for `ACTION_SEND_MULTIPLE` vs single URI.
  * Added `BatchProgress` UI and `BatchSuccess` Toast + `finish()`.
* [GpxImportViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModel.kt):
  * Added `ImportState.BatchProgress` and `ImportState.BatchSuccess`.
  * Added `handleBatchUris(uris)`.
* Localization files:
  * [values/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml)
  * [values-de/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-de/strings.xml)
  * [values-es/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-es/strings.xml)
  * [values-fr/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-fr/strings.xml)
  * [values-it/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-it/strings.xml)
  * [values-ja/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-ja/strings.xml)
  * [values-nl/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-nl/strings.xml)
  * [values-pl/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-pl/strings.xml)
  * [values-pt/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-pt/strings.xml)
* Test files:
  * [RoutesViewModelTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModelTest.kt): Added unit tests for batch import contracts (`TST-MAP-055_1` through `TST-MAP-055_5`).
  * [GpxImportViewModelTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt): Added batch handling unit tests (`TST-MAP-055_6`).
* Living documentation:
  * [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) (`REQ-MAP-053` -> `Verified`)
  * [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) (`TST-MAP-055` -> `Verified`)
