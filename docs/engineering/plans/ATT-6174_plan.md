# Stage 3: Implementation Plan - ATT-6174: Dropbox Filename Sanitization & `SecureZipExtractor` Hardening

**Ticket**: `ATT-6174` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Requirement**: `REQ-MIG-056` (`REQ-MIG-056.1`–`.2`) | **Test Spec**: `TST-MIG-056` (`TST-MIG-056.1`–`.3`)  
**Branch**: `feature/ATT-6174` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. SWE.2 Architectural Decomposition, Threading & Governance
* **Layering & Threading**: Sanitization executes synchronously per entry inside the `Dispatchers.IO` worker coroutines of `LegacyImportEngine.bulkRecoverFromDropbox` (`:215-270`) and `bulkRecoverFromGoogleDrive` (`:436-498`) prior to `downloadFileWithRetry` / `FileOutputStream` writes. No database managers or shared mutable state are accessed (`RepositoryEngineCleanlinessContractTest` preserved).
* **SWE.2 / SWE.3 & UI Consistency**:
  * `docs/architecture.md` (`Rule 8.8`): **None** — internal path-sanitization parity inside existing `LegacyImportEngine` and `SecureZipExtractor`; zero new subsystems, repositories, DB managers, or prefix families.
  * `docs/architecture/detailed_design/` (`REQ-PRO-114`): **None** — zero `TrackerService`, `BANALService`, or `MyBTLEDevice` state changes.
  * `UI Consistency` (`docs/design_guidelines.md`): **N/A** — zero UI/Compose or `values*/strings*.xml` edits (`Localization Parity: N/A`).

---

## 2. Atomic Construction Steps (Stage 4 — Post-Gate 3 Only)
| Step | Action & Target Files | Mapped Req / Test | Verification / Guardrail |
|---|---|---|---|
| **1** | **Harden `SecureZipExtractor.sanitizeDriveFileName` (`SecureZipExtractor.kt:222-236`)**: Preserve exact public name and signature `sanitizeDriveFileName(fileName: String, workerDir: File): File` (for `BackupImportSecurityTest.kt:226-257` & `bulkRecoverFromGoogleDrive`). Explicitly reject null bytes (`'\u0000' in fileName`) before `Path.resolve` (preventing `InvalidPathException` bypass) and reject single-dot / directory identity (`safeName == "." || targetPath == workerPath`) with `SecurityException`, alongside existing `/`, `\`, `..`, and blank guards. | `REQ-MIG-056.2`, `TST-MIG-056.2` | `BackupImportSecurityTest.kt:226-257` remains 100% compatible; `"evil\u0000.tcx"` and `"."` throw `SecurityException`. |
| **2** | **Dropbox Sanitization & `workerDir` Cleanup (`LegacyImportEngine.kt:229-231`, `:450-457`)**: In `bulkRecoverFromDropbox`, replace `val tempFile = File(workerDir, entry.name)` (`:230`) with `try { SecureZipExtractor.sanitizeDriveFileName(entry.name, workerDir) } catch (e: SecurityException) { Log.e(TAG, ...); workerDir.delete(); failedCount.incrementAndGet(); continue }`, skipping `downloadFileWithRetry` and continuing sibling entries cleanly. Also add `workerDir.delete()` inside `bulkRecoverFromGoogleDrive`'s `SecurityException` catch (`:453-456`) for full cleanup parity. | `REQ-MIG-056.1`, `TST-MIG-056.1` | `LegacyImportEngine.kt` stays `<= 1,500` LoC (`1,452 -> ~1,465 <= 1,500` per `ArchitectureConformanceContractTest.kt:116`); `0` DB manager calls (`RepositoryEngineCleanlinessContractTest`). |
| **3** | **Unit Tests & Tier-1 Verification (`Rule 7.5`)**: (a) Add mocked `DbxClientV2` Dropbox traversal test in `LegacyImportEngineCloudRecoveryTest.kt` (`../../evil.tcx`, `sub/evil.tcx`, `sub\evil.tcx`, `ride..fit`, `bad\u0000.fit`, `.` rejected -> `failedCount == 6`, `0` downloads & `0` files outside `workerDir`, empty `workerDir` cleaned, valid sibling recovered) with `// Verifies REQ-MIG-056 / TST-MIG-056`; (b) add `\u0000` & `"."` `SecurityException` tests in `BackupImportSecurityTest.kt` with `// Verifies REQ-MIG-056 / TST-MIG-056`; (c) run incremental `--rerun` Gradle command (`-Dkotlin.daemon.jvm.options=-Xmx3072m`, NEVER `--rerun-tasks`): `./gradlew :app:testDebugUnitTest --rerun -Dkotlin.daemon.jvm.options=-Xmx3072m --tests "com.atrainingtracker.trainingtracker.migration.LegacyImportEngineCloudRecoveryTest" --tests "com.atrainingtracker.trainingtracker.migration.BackupImportSecurityTest" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryEngineCleanlinessContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` + Python linters + `verify_requirement_governance.py`. | `TST-MIG-056.1`, `.2`, `.3` | `100%` targeted unit & contract pass rate (`0` skipped/failed), `LegacyImportEngine.kt <= 1,500` LoC, `0` linter/governance errors. |

---

## 3. System Invariants & Rollback Safety
* **Invariants**: `SecureZipExtractor.sanitizeDriveFileName(fileName, workerDir)` signature unchanged; `bulkRecoverFromGoogleDrive` parity preserved; `LegacyImportEngine.kt <= 1,500` LoC; `0` DB manager instantiation in `LegacyImportEngine.kt`; `0` `strings*.xml` edits.
* **Code Lock (`Rule 4.1`) & Rollback**: Zero `app/` edits until `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `0` (`GATE_PASSED`). Isolated on `feature/ATT-6174`.
