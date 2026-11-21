# Stage 3: Implementation Plan - ATT-6040: Sealed UI State Models for ImportBackupTabsScreen & MapScreenWithTrack

**Ticket**: [ATT-6040](https://issue-tracker/ATT-6040) | **Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Requirement Mapping**: `REQ-UI-2790` (`REQ-UI-2790.1`–`REQ-UI-2790.3`) | **Test Mapping**: `TST-UI-2790` (`TST-UI-2790.1`–`TST-UI-2790.3`)  
**Branch**: `feature/ATT-6040` | **Author**: Lane 2 Author (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Problem Description & Traceability
* **Problem**: `ImportBackupTabsScreen.kt` (`1,513` LoC) manages exclusive dialogs/sheets via `18` loose `remember` vars (`4` restore `AlertDialog`s, `3` cloud info `AlertDialog`s, `5` active + `1` dead `PreImportTuningBottomSheet` blocks), while `MapScreenWithTrack.kt` (`865` LoC) manually resets `5` `rememberSaveable` IDs across `8` sites with a priority mismatch between `sheetPeekHeight` and `sheetContent`.
* **1:1 Traceability**:
  * `REQ-UI-2790.1` $\leftrightarrow$ `TST-UI-2790.1`, `TST-UI-2790.3` (Steps 1, 3, 4)
  * `REQ-UI-2790.2` $\leftrightarrow$ `TST-UI-2790.2` (Steps 2, 3, 4)
  * `REQ-UI-2790.3` $\leftrightarrow$ `TST-UI-2790.3` (Steps 1–4)

---

## 2. System Invariants, UI Consistency & SWE.2/SWE.3 Scope
1. **UI Consistency (`docs/design_guidelines.md`)**: Pure state-modeling refactor (`0` visual/layout/string edits; `Localization Parity: N/A`). Preserves §5.3 `AlertDialog` styling (`surface`, `tonalElevation = 0.dp`, `error` confirm tint on destructive restore) and `AppModalBottomSheet` (`PreImportTuningBottomSheet`, `ClimbDetailSheet`), `BottomSheetScaffold` peek/swipe/expansion lock on `Location`, and Material 3 Light/Dark/AMOLED tokens.
2. **Contract Guards**: Preserve public signatures (`ImportBackupTabsScreen`, `PreImportTuningBottomSheet`, `MapScreenWithTrack`), `showMappingDialog` (`MappingRequired`), `activeInteraction` (`ClusterNamingDialog`), `editingLocation`/`previewRadius` coexistence with `Location`, and `0` Compose snapshot writes during composition (`Rule 66`).
3. **SWE.2 / SWE.3 Scope (`Rule 8.8`, `REQ-PRO-114`)**: Zero updates required to `docs/architecture.md` or `docs/architecture/detailed_design/` (internal UI state modeling inside `2` existing Compose screens; no new subsystem, service, DB, or prefix family).

---

## 3. Proposed Architectural Changes
### Component 1: `migration/ImportBackupTabsScreen.kt` & `build/ArchitectureConformanceContractTest.kt`
* **`PendingRestoreSource`** (`sealed interface` — `LocalFile(val uri: Uri)`, `AutomaticLocalBackup`, `Dropbox`, `GoogleDrive`): replaces `showRestoreConfirm`, `showDropboxRestoreConfirm`, `showAutomaticBackupRestoreConfirm`, `showGoogleDriveRestoreConfirm`, and `restoreUri`; single `AlertDialog` renders `restore_warning_title`/`restore_warning_message` and dispatches the matching `viewModel.restore*`/`performFullRestore` call.
* **`CloudInfoDialog`** (`sealed interface` — `DropboxDisconnected`, `GoogleDriveDisconnected`, `NoCloudConnected` carrying `@StringRes titleRes` & `messageRes`): replaces `3` boolean flags with one `AlertDialog`. Update `isDropboxConnected` & `isGoogleDriveConnected` `remember` keys to observe `cloudInfoDialog` alongside `uiState` & `resumeTrigger`.
* **`PendingImportSource`** (`sealed interface` — `DropboxBulk(val format: String)`, `LegacyFiles(val uris: List<Uri>, val format: String)`, `FitFiles(val uris: List<Uri>)`, `StravaArchive(val uri: Uri)`, `Directory(val uri: Uri, val format: String)`): replaces `showTuningDialogForBulk`, `pendingDropboxFormat`, `pendingLegacyUris`, `pendingFitUris`, `pendingStravaArchiveUri`, and `pendingDirectoryUri` with one `PreImportTuningBottomSheet` call (`showStravaUploadOption = source !is PendingImportSource.StravaArchive`, `LegacyFiles` dispatches `importLegacyFile` when `uris.size == 1` else `importWorkoutFiles`). Retain pre-picker SAF launcher format holders (`pendingSingleLegacyFormat`, `pendingDirectoryFormat`) for async `rememberLauncherForActivityResult` callbacks; delete dead `pendingSingleLegacyUri` + its unreachable block.
* **LoC Ratchet**: When `ImportBackupTabsScreen.kt <= 1,500` LoC, remove it from `MAX_MONOLITH_FILE_LOC_BASELINE` in `ArchitectureConformanceContractTest.kt`.

### Component 2: `ui/map/MapScreenWithTrack.kt`
* **`MapPeekSelection`** (`sealed interface` — `Segment(val id: Long)`, `Route(val id: Long)`, `Cluster(val id: Long)`, `Location(val id: Long)`, `Climb(val id: Long)` with `val isSheetBacked: Boolean get() = this !is Climb`): replaces `5` `rememberSaveable` IDs and `8` manual reset blocks via `rememberSaveable(stateSaver = MapPeekSelection.Saver)` using a lossless 2-element `List<Any>` (`[tag, id]`) `Saver` (`null` -> `null`).
* **Unified Dispatch & Measurement**: `LaunchedEffect(targetLocationId)` sets `MapPeekSelection.Location(targetLocationId)` when `> 0`; single `measuredHeaderHeight` (`remember(peekSelection)`) replaces `3` `measured*HeaderHeight` states; `LaunchedEffect(peekSelection)` & `LaunchedEffect(peekSelection, measuredHeaderHeight)` drive `partialExpand()`/`hide()` using `isSheetBacked`; single exhaustive `when (peekSelection)` drives both `sheetPeekHeight` and `sheetContent` (eliminating priority mismatch), while `MapPeekSelection.Climb` renders modal `ClimbDetailSheet` with `BottomSheetScaffold` at `0.dp`; `Location` keeps `confirmValueChange` expansion lock and `sheetSwipeEnabled = false`; `BackHandler(enabled = peekSelection != null)` resets `peekSelection = null`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Pre-Check (`Rule 4.1`)**: Confirm `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` exits `0`.
2. **Step 1 (`REQ-UI-2790.1`, `.3`)**: Refactor `ImportBackupTabsScreen.kt` with `PendingRestoreSource`, `CloudInfoDialog`, and `PendingImportSource`; remove dead `pendingSingleLegacyUri`; ratchet `ArchitectureConformanceContractTest.kt` once `<= 1,500` LoC.
3. **Step 2 (`REQ-UI-2790.2`, `.3`)**: Refactor `MapScreenWithTrack.kt` with `MapPeekSelection` + `Saver`, `isSheetBacked`, single `measuredHeaderHeight`, and unified `sheetPeekHeight`/`sheetContent`.
4. **Step 3 (`TST-UI-2790.1`, `.2`)**: Author `ImportBackupTabsSealedStateComposeTest.kt` and `MapPeekSelectionSaverAndUiComposeTest.kt` (`createComposeRule()`, `StateRestorationTester`, `Saver` round-trip, `0` `src/main` reads, `0` reflection-only stubs).
5. **Step 4 (`TST-UI-2790.3`)**: Run Tier-1 targeted tests + `verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Tier-1 Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActionsIntegrityTest" --tests "com.atrainingtracker.trainingtracker.localization.HardcodedStringsAndUnitsContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"`
* **Rollback**: Isolated on `feature/ATT-6040` until Stage 6 `PASS`.
