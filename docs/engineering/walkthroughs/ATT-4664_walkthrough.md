# Stage 5: Walkthrough & Verification - ATT-4664: Unify Restore Backup card button labels, icons, and disconnected visibility

**Ticket**: [ATT-4664](https://rainerblind.atlassian.net/browse/ATT-4664)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4664`  
**Requirement Mapping**: `REQ-MIG-036` (`REQ-MIG-036.13a`–`d`)  
**Test Mapping**: `TST-MIG-045` (`TST-MIG-045.1`–`4`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Executive Summary & Verification Overview
Harmonized the four restore buttons in `RestoreTabContent.kt` across all 9 application locales to the standardized `"Restore from ..."` pattern (`"Aus ... wiederherstellen"` in German), eliminating obsolete technical migration wording. Corrected the Dropbox restore button icon from `Icons.Default.CloudUpload` to `Icons.Default.CloudDownload`. Removed the conditional `if (isGoogleDriveConnected)` gating in `RestoreTabContent.kt`, rendering Google Drive restore unconditionally with `googleDriveRestoreColors` and `googleDriveRestoreBorder` (dimmed text and border when disconnected) to establish visual symmetry with Dropbox. In `ImportBackupTabsScreen.kt`, wired `showGoogleDriveDisconnectedDialog` with `R.string.google_drive_disconnected_status`, safely presenting the explanatory dialog without triggering destructive full restore confirmation when disconnected. Implemented comprehensive contract tests in `RestoreCardContractTest.kt` and refined `ImportFormatCardsContractTest.kt`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MIG-036.13a` | `TST-MIG-045.1` | `RestoreCardContractTest`, `TranslationParityTest`: 9-locale parity & "Restore from ..." pattern | **PASSED** | `Verified` |
| `REQ-MIG-036.13b` | `TST-MIG-045.2` | `RestoreCardContractTest`: Dropbox button uses `Icons.Default.CloudDownload` | **PASSED** | `Verified` |
| `REQ-MIG-036.13c` | `TST-MIG-045.2` | `RestoreCardContractTest`: Google Drive button rendered unconditionally with dimmed styling | **PASSED** | `Verified` |
| `REQ-MIG-036.13d` | `TST-MIG-045.3` | `RestoreCardContractTest`: Disconnected dialog triggered safely without confirmation dialog | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-MIG-045.4` | Full targeted migration suite (277 tests) & `assembleDebug` packaging validation | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Test Execution
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 52s
277 tests completed, 0 failed (100% pass rate)
```

### Build & Packaging Integrity (`./gradlew assembleDebug`)
```text
./gradlew assembleDebug
BUILD SUCCESSFUL in 20s
43 actionable tasks: 4 executed, 39 up-to-date
DEX, manifest, and debug APK packaging compiled cleanly with 0 errors.
```

### Requirement Governance Audit (`REQ-PRO-022`)
```text
python3 tools/verify_requirement_governance.py
Modified/Altered existing requirement(s) detected: REQ-MIG-036.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
Exit code: 0
```

---

## 4. Hardware / Physical Verification (Pixel 10) & UI Consistency (Rule 23)
Checked attached devices via `adb devices`: zero devices attached. Visual UI adheres to Material 3 tokens, `ButtonDefaults.outlinedButtonBorder`, standard 8.dp/16.dp spacing scale, and existing localized string resources without introducing one-off tokens.

---

## 5. Invariant & Governance Verification
1. **Destructive Guard Preserved**: Full restore execution remains strictly protected behind confirmation dialogs (`MigrationEngine.performFullRestore` guarded).
2. **Translation Parity**: 100% string and semantic parity verified across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
3. **Living Documentation Synchronized**: `REQ-MIG-036` in `docs/requirements.md` and `TST-MIG-045` in `docs/tests.md` updated to `Verified`.
4. **Subtask Governance**: Stage 5 subtask submitted for Gate 5 review; parent ticket ready for `Final Review (Human)`. Zero intermediate commits on `feature/ATT-4664` (Core Rule 8).
