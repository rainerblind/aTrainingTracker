# Stage 1 Analysis: ATT-6174 — Sanitize Remote Dropbox Filenames in `LegacyImportEngine`

**Ticket**: `ATT-6174` (`1 SP`, `Bug`, `Low`, `1 SP` confirmed — no split per `REQ-PRO-029`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469) | **Branch**: `feature/ATT-6174` (`b92c2484`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## Context & Problem Statement
* **Current Behavior**: In `migration/LegacyImportEngine.kt`, `bulkRecoverFromGoogleDrive` (`:450-457`) sanitizes each remote filename via `SecureZipExtractor.sanitizeDriveFileName(entry.name, workerDir)` and catches `SecurityException`, whereas `bulkRecoverFromDropbox` (`:229-230`) builds `val tempFile = File(workerDir, entry.name)` with zero validation before `downloadFileWithRetry` writes to `FileOutputStream(tempFile)`.
* **Expected Behavior**: `bulkRecoverFromDropbox` validates every remote `entry.name` via `SecureZipExtractor.sanitizeDriveFileName(entry.name, workerDir)`, catches `SecurityException`, logs the violation, cleans up `workerDir`, increments `failedCount`, and skips downloading — guaranteeing zero file writes outside `workerDir`.

---

## Root Cause & Forensics
### Independent Peer Review & Verification (Live `sprint/2026-11-21_02_Spät` @ `b92c2484`)
Audited `LegacyImportEngine.kt`, `SecureZipExtractor.kt`, `BackupRestoreViewModel.kt`, `BackupImportSecurityTest.kt`, `LegacyImportEngineCloudRecoveryTest.kt`, and `ArchitectureConformanceContractTest.kt`:

| Hypothesis / Check | Verdict | Live Code Evidence (`b92c2484`) |
| :--- | :---: | :--- |
| **1. Dropbox unsanitized path vs GDrive** | **CONFIRMED** | `LegacyImportEngine.kt:230` still uses raw `File(workerDir, entry.name)` (whereas `:452` uses `SecureZipExtractor.sanitizeDriveFileName`); `ATT-6040` (`2b63fc73`) only refactored Compose UI state and left `LegacyImportEngine` untouched. |
| **2. `SecureZipExtractor.sanitizeDriveFileName` gaps** | **HARDENING NEEDED** | `SecureZipExtractor.kt:222-236` rejects `/`, `\`, `..` (incl. `ride..fit`), and blank names, and checks `targetPath.startsWith(workerPath)`. However: (a) null bytes (`\u0000`) throw `InvalidPathException` instead of `SecurityException`, bypassing `catch (SecurityException)`; (b) `"."` normalizes to `workerPath` (`targetPath.startsWith(workerPath)` is `true`!). Rejecting `\u0000` and `targetPath == workerPath` closes both gaps. |
| **3. Empty `workerDir` cleanup & sibling paths** | **CONFIRMED** | Creating `workerDir` (`:229`) before `sanitizeDriveFileName` leaves an empty `job_$current` folder on `SecurityException` unless `workerDir.delete()` runs in `catch`. Only `bulkRecoverFromDropbox` & `bulkRecoverFromGoogleDrive` exist in `LegacyImportEngine.kt` (`BackupRestoreViewModel.kt:809` local `Uri` `DISPLAY_NAME` is out-of-scope). |
| **4. Testability & `BackupImportSecurityTest` parity** | **CONFIRMED** | `LegacyImportEngineCloudRecoveryTest.kt:150-245` already mocks `DbxClientV2` end-to-end; keeping `sanitizeDriveFileName` name intact preserves all 5 calls in `BackupImportSecurityTest.kt:226-257`. |
| **5. `LegacyImportEngine.kt` LoC ceiling** | **SAFE (No Cap)** | `LegacyImportEngine.kt` is `1,452` LoC (`SecureZipExtractor.kt` `237` LoC) with zero monolith LoC ceiling in `ArchitectureConformanceContractTest.kt`. |

---

## Requirements Archaeology (Chesterton's Fence)
* **Original Requirement ID & Target**: `REQ-MIG-016` / `REQ-MIG-034` (`docs/requirements/req_data_migration_export_part1.md:2075`, Google Drive & Dropbox cloud recovery); `REQ-MIG-040` / `TST-MIG-037.6` (`SecureZipExtractor.kt` Zip Slip & Drive filename sanitization in `BackupImportSecurityTest.kt:217-260`).
* **Historical Origin & Commit Trace**: `REQ-MIG-034` (`ATT-2346`) introduced Google Drive bulk recovery using `SecureZipExtractor.sanitizeDriveFileName`; `REQ-MIG-040` (`ATT-3982`) hardened archive/Drive extraction against path traversal.
* **Root Reason for Existing Formulation**: Dropbox bulk recovery predated `SecureZipExtractor.sanitizeDriveFileName` and assumed Dropbox `Metadata.name` contains only a benign leaf filename.
* **Preservation of Core Invariants**: (1) Retain `sanitizeDriveFileName(fileName, workerDir)` signature so `BackupImportSecurityTest.kt:226-257` and `bulkRecoverFromGoogleDrive` remain 100% compatible; (2) preserve `..` rejection semantics (note: filenames containing literal `..` such as `ride..fit` are rejected identically to Google Drive); (3) valid `.fit`/`.tcx`/`.gpx` filenames without separators/`..`/`\u0000` import unchanged; (4) `RepositoryEngineCleanlinessContractTest` (`0` DB manager calls in `LegacyImportEngine.kt`) preserved.

---

## Proposed Scope & Invariants
* **In-Scope**: (1) Update `LegacyImportEngine.bulkRecoverFromDropbox` (`:229-231`) to sanitize `entry.name` via `SecureZipExtractor.sanitizeDriveFileName(entry.name, workerDir)`, catching `SecurityException`, logging, cleaning up `workerDir`, incrementing `failedCount`, and skipping `downloadFileWithRetry`; (2) harden `SecureZipExtractor.sanitizeDriveFileName` (`:222-236`) to reject `\u0000` and `targetPath == workerPath` (`"."`) with `SecurityException`; (3) add end-to-end Dropbox traversal test in `LegacyImportEngineCloudRecoveryTest.kt` + `\u0000`/`"."` cases in `BackupImportSecurityTest.kt`.
* **Out-of-Scope**: `executeBulkCloudRecovery` unification (`ATT-6040`), `BackupRestoreViewModel.kt:809` local `Uri` temp naming, renaming `sanitizeDriveFileName`, or `strings*.xml` edits (`0` strings).

---

## Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-MIG-*` / `TST-MIG-*` in roomy **`docs/requirements/req_data_migration_export_part2.md`** (`301 / 4,000` LoC, `+3,699` headroom) and **`docs/tests/tst_data_migration_export.md`** (`2,437 / 4,000` LoC, `+1,563` headroom) — avoiding near-cap `req_data_migration_export_part1.md` (`3,994/4,000`). **SYS.2/SYS.3 Impact (`REQ-PRO-131.4`)**: `None` (`Derived (SW-only)` — internal local cache-path sanitization for Dropbox recovery; zero external sensor, REST API, or backup schema changes). `Localization Parity: N/A - 0 string edits`.
* **Affected Architecture Elements**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt` (`bulkRecoverFromDropbox`), `app/src/main/java/com/atrainingtracker/trainingtracker/migration/SecureZipExtractor.kt` (`sanitizeDriveFileName`). No `docs/architecture.md` subsystem changes.
* **Verification Impact & Test Surface**: Add mocked `DbxClientV2` traversal test (`../evil.tcx`, `sub/evil.tcx`, `sub\evil.tcx`) in `LegacyImportEngineCloudRecoveryTest.kt`, add `\u0000` & `"."` cases to `BackupImportSecurityTest.kt`, and run `RepositoryEngineCleanlinessContractTest` + `ArchitectureConformanceContractTest`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`) — identical defense-in-depth parity with `bulkRecoverFromGoogleDrive`; zero impact on valid filenames or `BackupImportSecurityTest` compatibility.
