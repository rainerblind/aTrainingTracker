# Stage 2: Requirement & Test Specification - ATT-6174

**Ticket**: `ATT-6174` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Requirement ID**: `REQ-MIG-056` (`[Planned]`) | **Test Spec ID**: `TST-MIG-056` (`[Planned]`)  
**Living Docs Shards**: `docs/requirements/req_data_migration_export_part2.md` (`v1.0.20`), `docs/tests/tst_data_migration_export.md` (`v1.0.22`), `docs/requirements.md`, `docs/tests.md`  
**Localization Parity**: N/A - 0 string changes (`0` edits to `values*/strings*.xml`)  
**Branch**: `feature/ATT-6174` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Requirement Specification (`REQ-MIG-056`)
* **Attributes**: **Priority**: `Low` | **Type**: `Functional` | **Verification Method**: `Unit` | **System Requirement(s)**: `Derived (SW-only)` — internal local cache-path sanitization parity for Dropbox bulk recovery without external sensor, REST API, or backup schema changes.
* **Normative Contract (`REQ-MIG-056.1`–`.2`)**:
  1. `LegacyImportEngine.bulkRecoverFromDropbox` SHALL sanitize every remote `entry.name` via `SecureZipExtractor.sanitizeDriveFileName(entry.name, workerDir)` before calling `downloadFileWithRetry`. Upon `SecurityException`, it SHALL log the error, delete the empty `workerDir`, increment `failedCount`, and skip downloading/importing so zero files are written outside `workerDir`.
  2. `SecureZipExtractor.sanitizeDriveFileName(fileName, workerDir)` SHALL retain its exact name and signature (preserving `BackupImportSecurityTest` and `bulkRecoverFromGoogleDrive`) while additionally rejecting null bytes (`\u0000`) and single-dot paths (`"."` / `targetPath == workerPath`) with `SecurityException`, alongside existing rejection of `/`, `\`, `..` (incl. `ride..fit`), and blank strings.

---

## 2. Given-When-Then Acceptance Criteria & Invariants
| ID | Given | When | Then |
|---|---|---|---|
| `AC-1` | Dropbox entry with `../../evil.tcx`, `sub/evil.tcx`, `sub\evil.tcx`, or `ride..fit` | `bulkRecoverFromDropbox` runs | `SecurityException` is caught, `failedCount` increments, violation is logged, empty `workerDir` is removed, `downloadFileWithRetry` is not called, and zero files escape `workerDir`. |
| `AC-2` | Input `"evil\u0000.tcx"` or `"."` to `sanitizeDriveFileName` | Sanitization runs | `SecurityException` is thrown deterministically instead of `InvalidPathException` or returning `workerDir`. |
| `AC-3` | Valid filename (no separators, `..`, `\u0000`, `"."`, or blank) | `bulkRecoverFromDropbox` / `sanitizeDriveFileName` runs | Resolves strictly inside `workerDir` and imports normally; `BackupImportSecurityTest` and `RepositoryEngineCleanlinessContractTest` pass (`0` string edits). |

* **System Invariants**: `sanitizeDriveFileName` signature unchanged; `bulkRecoverFromGoogleDrive` parity preserved; `0` DB manager calls in `LegacyImportEngine.kt`; `0` `strings*.xml` edits.

---

## 3. Verification Test Suite (`TST-MIG-056`) & Traceability Matrix
| Test ID | Target File / Suite | Verification Scope | Linked REQ |
|---|---|---|---|
| `TST-MIG-056.1` | `LegacyImportEngineCloudRecoveryTest.kt` | Mocked `DbxClientV2` Dropbox traversal/separator/`..` rejection (`failedCount`, `workerDir` cleanup, zero `download` calls) + valid entry import | `REQ-MIG-056.1` |
| `TST-MIG-056.2` | `BackupImportSecurityTest.kt` | `SecureZipExtractor.sanitizeDriveFileName` `\u0000` & `"."` `SecurityException` + existing 5 traversal/valid assertions | `REQ-MIG-056.2` |
| `TST-MIG-056.3` | `RepositoryEngineCleanlinessContractTest.kt`, `ArchitectureConformanceContractTest.kt` | Zero DB manager calls in `LegacyImportEngine.kt`, architecture contracts, `0` string edits (`Localization Parity: N/A`) | `REQ-MIG-056.1`, `.2` |
