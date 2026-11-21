# Stage 5 Verification & Walkthrough: ATT-6174 — Dropbox Filename Sanitization & `SecureZipExtractor` Hardening

**Ticket**: `ATT-6174` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Living Docs**: `REQ-MIG-056` (`[Verified]`), `TST-MIG-056` (`[Verified]`) | **SWE.2 Architecture**: `docs/architecture.md` unchanged (`Rule 8.8`)  
**Branch**: `feature/ATT-6174` (preserved intact for Stage 6 Cold Review per `Rule 3.3`; verified code SHA `993be8d5`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Delivered Changes (`993be8d5`)
* **`SecureZipExtractor.sanitizeDriveFileName` Hardening (`REQ-MIG-056.2`, `SecureZipExtractor.kt:222-236`)**:
  * Preserved public name and signature `sanitizeDriveFileName(fileName, workerDir)` (`BackupImportSecurityTest` and `bulkRecoverFromGoogleDrive` compatible), adding explicit `SecurityException` checks for null bytes (`'\u0000' in fileName`) before `Path.resolve` and single-dot / directory identity (`safeName == "." || targetPath == workerPath`).
* **Unified `LegacyImportEngine.sanitizeRemoteTempFile` (`REQ-MIG-056.1`, `LegacyImportEngine.kt`)**:
  * Wired both `bulkRecoverFromDropbox` (`:229-230`) and `bulkRecoverFromGoogleDrive` (`:450-451`) through shared `sanitizeRemoteTempFile(entry.name, workerDir, failedCount)`, logging security violations, deleting empty `workerDir`, incrementing `failedCount`, skipping `downloadFileWithRetry`, and continuing sibling entries (`LegacyImportEngine.kt` `1,452 -> 1,465 <= 1,500` LoC; `SecureZipExtractor.kt` `237` LoC).
* **Unit Regression Coverage (`TST-MIG-056.1`–`.3`)**:
  * Added `testBulkRecoverFromDropbox_rejectsTraversalAndUnsafeNamesWhileRecoveringValidSibling_TST_MIG_056` (`LegacyImportEngineCloudRecoveryTest.kt`) and `\u0000` / `"."` assertions in `testSanitizeDriveFileName_rejectsTraversalAndSeparators` (`BackupImportSecurityTest.kt`), both annotated with `// Verifies REQ-MIG-056 / TST-MIG-056`.
  * **Bug-Fix Verification Evidence (`REQ-PRO-121.2`)**: Pre-fix `bulkRecoverFromDropbox` passed `entry.name` directly into `File(workerDir, entry.name)`; post-fix mocked `DbxClientV2` test asserts `../../evil_1.tcx`, `sub/evil_2.gpx`, `sub\evil_3.fit`, `ride..fit`, and `bad\u0000.fit` fail safely (`failedCount == 5`, `0` downloads, empty `workerDir` deleted, `0` files outside `workerDir`) while `valid_ride.tcx` imports (`importedCount == 1`).
* **Localization & Submodule Hygiene**: `0` `values*/strings*.xml` edits (`Localization Parity: N/A`); `ANT-Android-SDKs` untouched at `160000 0e8cea71`.

## 2. Verification Evidence (`Rule 7.5` Doc-Only Fast Path — Zero `app/` Edits Since `993be8d5`)
| Check / Suite | Scope | Result | Evidence / Reference |
|---|---|---|---|
| **Gate 4 Tier-1 Targeted (`ATT-9555`)** | `BackupImportSecurityTest` (`14`), `LegacyImportEngineCloudRecoveryTest` (`4`), `ArchitectureConformanceContractTest` (`11`), `RepositoryEngineCleanlinessContractTest.testLegacyImportEngine_zeroDatabaseManagerInstantiation` (`1`) | **30/30 PASS** (`0` fail, `0` error, `0` skip) | `@ 993be8d5` (reused per `Rule 7.5`) |
| **Python Safety Linters** | `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py` | **4/4 PASS** (`exit 0`) | `@ 993be8d5` |
| **Living Docs & Traceability** | `verify_requirement_governance.py --sync-indexes --check-verified-evidence --check-living-doc-layout` | **PASS** (`exit 0`, `0` unbacked) | `REQ-MIG-056` & `TST-MIG-056` `[Verified]` |
| **Live Emulator (`emulator-5554`)** | Deferred to independent Stage 6 Cold Review (`Rule 3.3`) | Pending Stage 6 | `feature/ATT-6174` preserved intact |

## 3. Branch & Handover State
* `feature/ATT-6174` remains intact and unmerged (`0` `app/` edits since `993be8d5`), ready for Gate 5 audit and Stage 6 Cold Review (`Rule 3.3` / `Rule 8.2`).
