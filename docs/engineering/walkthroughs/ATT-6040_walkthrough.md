# Stage 5: Walkthrough & Verification - ATT-6040: Sealed UI State Models for ImportBackupTabsScreen & MapScreenWithTrack

**Ticket**: [ATT-6040](https://issue-tracker/ATT-6040) | **Sub-task**: Pending creation (`[Test]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Requirement Mapping**: `REQ-UI-2790` (`REQ-UI-2790.1`–`REQ-UI-2790.3`) | **Test Mapping**: `TST-UI-2790` (`TST-UI-2790.1`–`TST-UI-2790.3`)  
**Branch**: `feature/ATT-6040` (verified code SHA `cd809943fc7641917e919b2976b739a9d7e7f072`)  
**Author**: Lane 2 Author (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Executive Summary & Verification Overview
* **Implemented Changes (`cd809943`)**:
  * `ImportBackupTabsScreen.kt` (**`1,513 -> 1,400` LoC**, **`-113` LoC**): Consolidated `4` restore `AlertDialog`s via `sealed interface PendingRestoreSource`, `3` cloud info `AlertDialog`s via `sealed interface CloudInfoDialog` (preserving `isDropboxConnected`/`isGoogleDriveConnected` `remember` key refreshes), and `5` active `PreImportTuningBottomSheet` flows via `sealed interface PendingImportSource` (`StravaArchive` `showStravaUploadOption = false`, `LegacyFiles` single/multi dispatch, SAF pre-picker format holders retained); removed dead `pendingSingleLegacyUri`; ratcheted `ArchitectureConformanceContractTest.MAX_MONOLITH_FILE_LOC_BASELINE` (`16 -> 15` entries).
  * `MapScreenWithTrack.kt` (**`886` LoC**): Replaced `5` mutually exclusive `rememberSaveable` IDs and `8` manual reset blocks with `sealed interface MapPeekSelection` + lossless `MapPeekSelection.Saver`, added `isSheetBacked` (`this !is Climb`) keeping modal `ClimbDetailSheet` (`0.dp` sheet), unified `sheetPeekHeight` & `sheetContent` priority dispatch, consolidated `measuredHeaderHeight` (`remember(peekSelection)`), and preserved `Location` expansion lock + `editingLocation`/`previewRadius` coexistence.
  * `ImportBackupTabsSealedStateComposeTest.kt` (`4/4`) & `MapPeekSelectionSaverAndUiComposeTest.kt` (`3/3`): Added behavioral Robolectric `createComposeRule()`, `StateRestorationTester`, and `MapPeekSelection.Saver` round-trip tests (`0` `src/main` file reads, `0` reflection-only UI stubs, `0` `strings*.xml` edits).
* **Rule 7.5 Doc-Only Fast Path**: Zero files under `app/` modified since Gate 4 `PASS` (`ATT-9485` @ `cd809943`); Gate 4's independently verified **`886/886 PASS` across `172` suites (`0` new warnings)** is reused directly without redundant Gradle execution.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-2790.1` | `TST-UI-2790.1`, `TST-UI-2790.3` | Robolectric `createComposeRule()` & `ArchitectureConformanceContractTest` | **PASSED** (`4/4` compose, `11/11` arch; `1,400 <= 1,500` LoC) | `Verified` |
| `REQ-UI-2790.2` | `TST-UI-2790.2` | `MapPeekSelection.Saver`, `StateRestorationTester` & `createComposeRule()` | **PASSED** (`3/3`; 5 variants + `null`, zero overlap) | `Verified` |
| `REQ-UI-2790.3` | `TST-UI-2790.3` | Tier-1 Contract & Safety Linters (Gate 4 `ATT-9485` @ `cd809943`) | **PASSED** (`886/886` across `172` suites, `0` new warnings) | `Verified` |

---

## 3. Automated Test Evidence (Gate 4 `ATT-9485` @ `cd809943`, Reused per `Rule 7.5`)
```text
Gate 4 Independent Verification (@ cd809943): 886/886 PASS across 172 suites (0 failures, 0 errors, 0 skipped, 0 new warnings)
New Behavioral Compose & Saver Suites:
  - ImportBackupTabsSealedStateComposeTest: 4/4 PASS (PendingRestoreSource, CloudInfoDialog, PendingImportSource, Strava upload suppression)
  - MapPeekSelectionSaverAndUiComposeTest: 3/3 PASS (MapPeekSelection.Saver round-trip, StateRestorationTester, Location lock)
Contract & Audit Suites (included in 886/886): ArchitectureConformanceContractTest (11/11), InterfaceSignatureDriftContractTest,
  DeadCodeEliminationContractTest, SourceTextReadingAuditContractTest, UiReflectionOnlyTestAuditContractTest,
  ImportBackupTabsUiContractTest, BackupCloudProviderUnificationTest, RestoreCardContractTest, ImportFormat*Test,
  MapScreenWithTrackTest, FavoriteLocationSheetContractTest, GeneralMapLayersContractTest, HardcodedStringsAndUnitsContractTest
Safety Linters: lint_cursor_safety, lint_lifecycle_safety, lint_safe_enum -> 3/3 PASS
```
*Live `emulator-5554` runtime verification & `Rule 8.5` visual check occur in Stage 6 Cold Review (`Rule 3.3`).*

---

## 4. Living Documentation & Branch Integrity
* **Living Documentation**: `REQ-UI-2790` (`docs/requirements/req_ui_design_system_part5.md` `v1.0.16`) & `TST-UI-2790` (`docs/tests/tst_ui_design_system_part3.md` `v1.0.17`) promoted to `[Verified]`; master indexes synchronized (`--sync-indexes`); `--check-living-doc-layout --check-verified-evidence` `exit 0`.
* **Branch State (`Rule 3.3`)**: `feature/ATT-6040` preserved **intact** for Stage 6 Cold Review (NOT merged onto `sprint/2026-11-21_01_Früh`; `git diff --name-only cd809943..HEAD` contains `0` files under `app/`).
