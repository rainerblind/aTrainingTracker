# Stage 1 Analysis: ATT-6040 - Sealed UI State Models for ImportBackupTabsScreen & MapScreenWithTrack

**Ticket**: [ATT-6040](https://issue-tracker/ATT-6040) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` (`Rule 8.3`, active unreleased version) | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Branch**: `feature/ATT-6040` (from `sprint/2026-11-21_01_Früh` @ `0db0b557`) | **Author**: Lane 2 Author (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Problem Statement & Motivation
* **Current Behavior**: Both `ImportBackupTabsScreen.kt` (`1,513` LoC) and `MapScreenWithTrack.kt` (`865` LoC) manage mutually exclusive UI states via scattered boolean/nullable `remember` / `rememberSaveable` variables and duplicated dialog/sheet branches, allowing impossible overlapping states and a latent priority mismatch between `sheetPeekHeight` and `sheetContent`.
* **Expected Behavior**: Type-safe `sealed interface` models (`PendingRestoreSource`, `CloudInfoDialog`, `PendingImportSource` in `ImportBackupTabsScreen.kt`; `MapPeekSelection` with `Saver` in `MapScreenWithTrack.kt`) eliminate overlapping states, duplicate dialog/sheet boilerplate, dead `pendingSingleLegacyUri`, and priority mismatches with `0` visual, behavioral, or string changes.

---

## 2. Independent Peer Review & Live Codebase Audit (`0db0b557`)
* **`ImportBackupTabsScreen.kt` (`1,513` LoC) Verified**:
  * **18 state `remember` vars (`:126–146`)**: `4` restore flags + `restoreUri` (`:126–130`), `showMappingDialog` (`:131`, driven by `LaunchedEffect(uiState)`), `9` pre-import/format vars (`:134–142`), `3` cloud info flags (`:143–145`), and `resumeTrigger` (`:146`). Neither `BackupRestoreViewModel` nor `MapFragmentWithTrackViewModel` holds dialog/peek state.
  * **4 identical Restore `AlertDialog`s (`:456–558`)** $\rightarrow$ unify via `PendingRestoreSource` (`LocalFile(uri)`, `AutomaticLocalBackup`, `Dropbox`, `GoogleDrive`).
  * **3 Cloud Info `AlertDialog`s (`:560–603`)** $\rightarrow$ unify via `CloudInfoDialog` (`DropboxDisconnected`, `GoogleDriveDisconnected`, `NoCloudConnected`); **must update `remember` keys** on `isDropboxConnected` (`:169`) & `isGoogleDriveConnected` (`:172`) to reference the sealed state!
  * **6 `PreImportTuningBottomSheet` blocks (`:630–700`)**: `pendingSingleLegacyUri` (`:136, :641–650`) is **confirmed dead code** (never assigned non-null) $\rightarrow$ remove; remaining `5` (`DropboxBulk(format)`, `LegacyFiles(uris, format)`, `FitFiles(uris)`, `StravaArchive(uri)` with `showStravaUploadOption = false`, `Directory(uri, format)`) unify via `PendingImportSource`. Keep pre-picker SAF launcher format holders (`pendingSingleLegacyFormat`, `pendingDirectoryFormat`) intact for async `rememberLauncherForActivityResult` callbacks (`:180–232`).
* **`MapScreenWithTrack.kt` (`865` LoC) Verified**:
  * **5 mutually exclusive `rememberSaveable` IDs (`:131–135`)**: `selectedSegmentId`, `selectedRouteId`, `selectedClusterId`, **`selectedClimbId`**, and `selectedLocationId` (initialized from `targetLocationId`), manually cleared across **8** blocks (`:144–150`, `:320–326`, `:330–372`, `:539–545`).
  * **Important Nuance (`Climb`)**: `Segment`, `Route`, `Cluster`, `Location` render inside `BottomSheetScaffold`, whereas `Climb` renders modal `ClimbDetailSheet` (`:527–536`) with `BottomSheetScaffold` hidden (`0.dp`). Modeling `MapPeekSelection` with all `5` variants (`Segment`, `Route`, `Cluster`, `Location`, `Climb`) guarantees 5-way exclusion while preserving `isSheetBacked` (`!= Climb`) behavior!
  * **Latent Priority Bug Confirmed**: `sheetPeekHeight` (`:223–244`, segment > route > location > cluster) $\neq$ `sheetContent` (`:249–306`, cluster > location > segment > route) $\rightarrow$ single exhaustive `when (peekSelection)` fixes this by construction.
  * **Rotation & Header Height**: `rememberSaveable(stateSaver = MapPeekSelection.Saver)` preserves selection across rotation (`targetLocationId` `LaunchedEffect` overrides when `> 0`); single `measuredHeaderHeight` (`remember(peekSelection)`) replaces `3` `measured*HeaderHeight` states; `editingLocation`/`previewRadius` (`:136–137`) stay separate (coexist with `Location`).
* **Contract & LoC Audit**: `ImportBackupTabsScreen.kt` sits at exact ceiling `1,513/1,513` in `ArchitectureConformanceContractTest.kt:133` — shrinking it `<= 1,500` LoC requires removing it from `MAX_MONOLITH_FILE_LOC_BASELINE` (`:518–520`). `InterfaceSignatureDriftContractTest` and `DeadCodeEliminationContractTest` (`0` `@Preview`s currently in either file) impose no blockers; callers (`ATrainingTrackerApp.kt:403,639`, `MapFragmentWithTrack.kt:92`, `BackupRestoreFragment.kt:42`) keep unchanged signatures.

---

## 3. Scope Grounding & Non-Goals (`3 SP`)
* **In-Scope**: Refactor `ImportBackupTabsScreen.kt` (`PendingRestoreSource`, `CloudInfoDialog`, `PendingImportSource`, remove `pendingSingleLegacyUri`) and `MapScreenWithTrack.kt` (`MapPeekSelection` + `Saver`, unified `measuredHeaderHeight`); ratchet `ArchitectureConformanceContractTest` if `ImportBackupTabsScreen.kt <= 1,500` LoC; add behavioral Compose UI + Saver unit tests.
* **Out-of-Scope**: `LegacyImportEngine` / `WorkoutParser` / cloud bulk recovery (`ATT-8135`), `showMappingDialog` / `activeInteraction` ViewModel ownership, `values*/strings*.xml` edits, or visual/layout changes.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
* **Original Requirement IDs & Target**: `REQ-MIG-036.13` (`req_data_migration_export_part1.md:2224`, disconnected dialogs), `REQ-UI-221` (dynamic `measured*HeaderHeight` re-align), `REQ-UI-238` (favorite location peek lock), `REQ-UI-293`/`REQ-UI-362` (format cards/pre-import sheet), `REQ-UI-322` (layers & cluster sheet), `REQ-MAP-059` (`MapScreenWithTrack` insets/nav).
* **Root Reason & Invariants Preserved**: Existing REQs protect behavioral contracts (no destructive restore when cloud disconnected, locked location sheet `confirmValueChange`, `showStravaUploadOption = false` on Strava archive, `PreImportTuningBottomSheet` public symbol). Existing REQs remain unmodified; net-new `REQ-UI-2790` / `TST-UI-2790` go into roomy shards `req_ui_design_system_part5.md` (`1,880/4,000`) & `tst_ui_design_system_part3.md` (`928/4,000`) — avoiding `>99.5%` full shards (`req_ui_design_system_part2.md`, `tst_ui_design_system_part2.md`, `tst_tracking_cockpit.md`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-UI-2790` (`docs/requirements/req_ui_design_system_part5.md`) & `TST-UI-2790` (`docs/tests/tst_ui_design_system_part3.md`); preserves `REQ-MIG-036.13`, `REQ-UI-221`, `REQ-UI-238`, `REQ-UI-293`, `REQ-UI-322`, `REQ-UI-362`, `REQ-MAP-059`. `SYS.2`/`SYS.3`: None (pure UI state-modeling refactor). Localization: `N/A - 0 string changes`.
* **Affected Architecture Elements**: `migration/ImportBackupTabsScreen.kt`, `ui/map/MapScreenWithTrack.kt`, `build/ArchitectureConformanceContractTest.kt` (monolith ratchet if `<= 1,500` LoC). Zero `docs/architecture.md` subsystem changes (`Rule 8.8`).
* **Verification Impact & Test Surface**: Existing `ImportBackupTabsUiContractTest`, `BackupCloudProviderUnificationTest`, `RestoreCardContractTest`, `ImportFormatCardsContractTest`, `ImportFormatFilePickerContractTest`, `ImportBackupMapResilienceTest`, `AppDialogActionsIntegrityTest`, `MapScreenWithTrackTest`, `FavoriteLocationSheetContractTest`, `GeneralMapLayersContractTest`, `MapDetailLayoutNavigationBarsPaddingTest`, `HardcodedStringsAndUnitsContractTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, plus new behavioral `createComposeRule()` dialog/peek-selection tests & `MapPeekSelection.Saver` round-trip tests (`0` `src/main` reads, `REQ-PRO-132`).
* **Risk Assessment & Invariants**: **LOW–MEDIUM** (`3 SP`) — rotation state loss mitigated by explicit `Saver` unit tests; SAF picker format loss mitigated by keeping launcher pre-picker format; `isDropboxConnected`/`isGoogleDriveConnected` `remember` keys updated; zero overlapping dialogs or orphaned peek states.

---

## 6. System Invariants & Risk Rating
* **Core Invariants**: Identical UX/dialog/sheet appearance and callbacks; `PreImportTuningBottomSheet`, `ImportBackupTabsScreen`, and `MapScreenWithTrack` signatures intact; location peek cannot expand (`SheetValue.Expanded` rejected, `sheetSwipeEnabled = false`); `Climb` keeps modal `ClimbDetailSheet`; `0` `strings*.xml` edits; `0` Compose snapshot writes in composition (`Rule 66`).
* **Risk Rating**: **LOW–MEDIUM** — localized UI state refactor across 2 screens with strong existing + new Compose behavioral test coverage.
