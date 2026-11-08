# Stage 5: Walkthrough & Verification - ATT-5874

**Ticket**: [ATT-5874](https://atrainingtracker.atlassian.net/browse/ATT-5874)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5874`  
**Requirement Mapping**: `REQ-UI-474` | **Test Mapping**: `TST-UI-474`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
This walkthrough validates all four Import & Backup UI polish, Strava tuning integration, and heading deduplication fixes:
1. **Strava Archive Pre-Import Tuning Integration (`REQ-UI-474.1`)**: In `ImportBackupTabsScreen.kt`, selecting a Strava `.zip` export stores the URI in `pendingStravaArchiveUri` and displays `PreImportTuningBottomSheet(showStravaUploadOption = false)`, enabling cluster tolerance tuning while suppressing redundant Strava re-upload.
2. **Authentic Cloud Brand Iconography & Merge Icon (`REQ-UI-474.2`)**: In `FormatImportCard`, Dropbox scan buttons render `R.drawable.dropbox_logo_blue` with `tint = Color.Unspecified`. In `RestoreTabContent.kt`, Dropbox restore renders `dropbox_logo_blue` and Google Drive restore renders `ic_google_drive` (`tint = Color.Unspecified`, `20.dp`). The incremental import button renders `Icons.Default.CallMerge` (replacing generic upward `Backup`).
3. **Heading Deduplication & Action Button Phrasing Parity (`REQ-UI-474.3`)**: In `BackupTabContent.kt`, the primary backup button binds `R.string.backup_now` ("Jetzt sichern" / "Backup Now"), deduplicating from the card header `create_backup`. The automated backup `Switch` is integrated directly into the card header row next to `automated_backups`, eliminating duplicate inner row text. In `RestoreTabContent.kt`, incremental import binds `R.string.merge_from_backup_file` across all 9 locales, deduplicating from `import_workouts`.
4. **Contract Verification (`REQ-UI-474.4`)**: `RestoreCardContractTest.kt` verifies brand drawables, `CallMerge`, `merge_from_backup_file`, `backup_now`, and Strava pre-import tuning contracts.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-474.1` | `TST-UI-474.4` | Strava Archive Pre-Import Tuning (`RestoreCardContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-474.2` | `TST-UI-474.2` | Authentic Brand Icons & CallMerge (`RestoreCardContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-474.3` | `TST-UI-474.1`, `TST-UI-474.3` | Heading Deduplication & 9-Locale Parity (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-UI-474.4` | `TST-UI-474.2` | Restore Card Layout & Brand Contract (`RestoreCardContractTest`) | **PASSED** | `Verified` |
| `REQ-BLD-001` | `TST-BLD-001` | Debug APK Build (`./gradlew assembleDebug`) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Living Docs Parity (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Executed Test Scope (Tier 1 Targeted)
* **Build & Targeted Unit Tests**:
  `./gradlew assembleDebug testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.ui.migration.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2`
  * **Result**: `BUILD SUCCESSFUL in 2m 7s` (50 actionable tasks, 0 failures).
* **Requirement Governance**:
  `python3 tools/verify_requirement_governance.py`
  * **Result**: `PASS: Living documentation master tables match all 9 domain shards with 100% parity.` (0 errors).

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Status**: Deferred to Sprint Ceremony 2 (no physical USB device attached to host; software emulator strictly prohibited per hardware safety policy).
* **APK Build Verification**: Confirmed via `./gradlew assembleDebug`.

---

## 5. Invariant & Governance Verification
1. **Archive Security**: Extension and path traversal validations (`.zip`, `.attbackup`) remain strictly enforced before tuning launch.
2. **100% 9-Language Parity**: Verified zero missing entries for `merge_from_backup_file` and `backup_now` across all 9 locales via `TranslationParityTest`.
3. **Living Documentation Synchronized**: `REQ-UI-474` and `TST-UI-474` updated to `Verified`.
4. **Target Release Integrity**: Target release strictly set to `V5.0.0` (Rule 66).
