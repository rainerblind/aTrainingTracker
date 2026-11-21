# Stage 2: Requirement & Test Specification - ATT-6040: Sealed UI State Models for ImportBackupTabsScreen & MapScreenWithTrack

**Ticket**: [ATT-6040](https://issue-tracker/ATT-6040) | **Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Requirement Mapping**: `REQ-UI-2790` (`REQ-UI-2790.1`–`REQ-UI-2790.3`) | **Test Spec ID**: `TST-UI-2790` (`TST-UI-2790.1`–`TST-UI-2790.3`)  
**Branch**: `feature/ATT-6040` | **Author**: Lane 2 Author (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Requirement Specification (`REQ-UI-2790`)

### 1.1 Attributes
* **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
* **Localization Parity**: `N/A - 0 string changes` (`0` edits to `res/values*/strings*.xml`)

### 1.2 Functional & Architectural Requirements
1. **Sealed Dialog & Pre-Import States in `ImportBackupTabsScreen.kt` (`REQ-UI-2790.1`)**:
   - Consolidate the `4` destructive restore confirmation `AlertDialog`s into `sealed interface PendingRestoreSource` (`LocalFile(uri)`, `AutomaticLocalBackup`, `Dropbox`, `GoogleDrive`) rendered by a single parameterized `AlertDialog`.
   - Consolidate the `3` cloud informational `AlertDialog`s into `sealed interface CloudInfoDialog` (`DropboxDisconnected`, `GoogleDriveDisconnected`, `NoCloudConnected`) rendered by a single parameterized `AlertDialog`, updating `isDropboxConnected` and `isGoogleDriveConnected` `remember` keys to observe that state.
   - Consolidate the `5` active `PreImportTuningBottomSheet` blocks into `sealed interface PendingImportSource` (`DropboxBulk(format)`, `LegacyFiles(uris, format)`, `FitFiles(uris)`, `StravaArchive(uri)`, `Directory(uri, format)`), preserving `showStravaUploadOption = false` on `StravaArchive`, single-vs-multi dispatch on `LegacyFiles`, and pre-picker SAF launcher format holders (`pendingSingleLegacyFormat`, `pendingDirectoryFormat`).
   - Remove dead `pendingSingleLegacyUri` state and its unreachable sheet branch; once `ImportBackupTabsScreen.kt <= 1,500` LoC, remove it from `ArchitectureConformanceContractTest.MAX_MONOLITH_FILE_LOC_BASELINE`.
2. **Sealed `MapPeekSelection` with `Saver` in `MapScreenWithTrack.kt` (`REQ-UI-2790.2`)**:
   - Replace the `5` mutually exclusive `rememberSaveable` IDs (`Segment`, `Route`, `Cluster`, `Location`, `Climb`) and `8` manual reset blocks with `sealed interface MapPeekSelection` stored via `rememberSaveable(stateSaver = MapPeekSelection.Saver)`.
   - `MapPeekSelection.Saver` SHALL round-trip `null` and all `5` variants across rotation/process death while `LaunchedEffect(targetLocationId)` keeps overriding when `targetLocationId != null && targetLocationId > 0`.
   - Keep `Climb` modal (`ClimbDetailSheet` with `BottomSheetScaffold` hidden at `0.dp`), dispatch `sheetPeekHeight` and `sheetContent` from a single exhaustive `when (peekSelection)` eliminating the priority mismatch, replace `3` `measured*HeaderHeight` states with `measuredHeaderHeight` (`remember(peekSelection)`), and preserve `editingLocation`/`previewRadius` coexistence and `Location` expansion lock.
3. **Preserved Contracts & Invariants (`REQ-UI-2790.3`)**:
   - Preserve public signatures (`ImportBackupTabsScreen`, `PreImportTuningBottomSheet`, `MapScreenWithTrack`), `showMappingDialog`, `activeInteraction` (`ClusterNamingDialog`), `0` `strings*.xml` edits, and `0` visual/behavioral regressions.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (`TST-UI-2790.1`)**: *Given* `ImportBackupTabsScreen`, *When* any restore confirmation, cloud info notice, or pre-import tuning sheet opens, *Then* one parameterized dialog/sheet renders from `PendingRestoreSource`/`CloudInfoDialog`/`PendingImportSource`, `StravaArchive` hides Strava upload, `pendingSingleLegacyUri` is absent, connection `remember` keys refresh, and `ImportBackupTabsScreen.kt <= 1,500` LoC.
* **Criterion 2 (`TST-UI-2790.2`)**: *Given* `MapScreenWithTrack`, *When* `Segment`, `Route`, `Cluster`, `Location`, or `Climb` is selected or the device rotates, *Then* `MapPeekSelection.Saver` preserves state, `sheetPeekHeight` matches `sheetContent`, `Climb` renders modal `ClimbDetailSheet`, `Location` blocks `SheetValue.Expanded`, and `measuredHeaderHeight` resets cleanly per selection.
* **Criterion 3 (`TST-UI-2790.3`)**: *Given* new Robolectric `createComposeRule()` & `Saver` tests plus existing migration/map/build suites, *When* executed, *Then* 100% pass with `0` reflection-only UI stubs and `0` `src/main` reads in new tests.

---

## 2. Test Specification (`TST-UI-2790`)

### Test Case 1: `ImportBackupTabsScreen` Sealed Dialog & Pre-Import Behavioral Compose Suite (`TST-UI-2790.1`)
* **Scope**: Robolectric `createComposeRule()` test (`app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsSealedStateComposeTest.kt`)
* **Action**: Compose `ImportBackupTabsScreen` (and `PreImportTuningBottomSheet` / state transitions); exercise all `4` `PendingRestoreSource` restore confirms (Cancel vs Confirm), all `3` `CloudInfoDialog` notices, and `PendingImportSource` (`StravaArchive` hiding `import_upload_to_strava_label` vs active option on other sources).
* **Expected Result**: Single active dialog/sheet at a time, exact ViewModel dispatch on confirm, zero calls on cancel, `isDropboxConnected`/`isGoogleDriveConnected` refresh preserved.

### Test Case 2: `MapPeekSelection.Saver` & `MapScreenWithTrack` Mutual Exclusion Suite (`TST-UI-2790.2`)
* **Scope**: Unit & Robolectric `createComposeRule()` test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapPeekSelectionSaverAndUiComposeTest.kt`)
* **Action**: Test `MapPeekSelection.Saver` `save`/`restore` for `null` and all `5` variants + `StateRestorationTester` rotation survival; verify `sheetPeekHeight` vs `sheetContent` parity across sheet-backed selections, modal `ClimbDetailSheet` without sheet peek, `Location` expansion lock, and `measuredHeaderHeight` reset on selection change.
* **Expected Result**: Lossless state restoration, 5-way mutual exclusion, zero priority mismatch, zero `src/main` file reads.

### Test Case 3: Targeted Contract Regression & LoC Ratchet (`TST-UI-2790.3`)
* **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActionsIntegrityTest" --tests "com.atrainingtracker.trainingtracker.localization.HardcodedStringsAndUnitsContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"`
* **Expected Result**: 100% pass; `ImportBackupTabsScreen.kt <= 1,500` LoC ratcheted out of `MAX_MONOLITH_FILE_LOC_BASELINE`; `0` `strings*.xml` edits.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-2790.1` | Sealed `PendingRestoreSource`, `CloudInfoDialog`, `PendingImportSource`, dead code & LoC ratchet | `TST-UI-2790.1`, `TST-UI-2790.3` | Compose UI & Contract Test |
| `REQ-UI-2790.2` | Sealed `MapPeekSelection` + `Saver`, unified peek/content priority, `measuredHeaderHeight` | `TST-UI-2790.2` | Unit & Compose UI Test |
| `REQ-UI-2790.3` | Preserved signatures, `0` string edits, `0` regressions | `TST-UI-2790.3` | Contract & Regression Suite |
