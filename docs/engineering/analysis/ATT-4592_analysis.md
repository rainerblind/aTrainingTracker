# Stage 1 Analysis: ATT-4592 - [Bug] Backup restore and incremental import fail on Android with SecurityException due to /data/user/0 -> /data/data symlink mismatch in SecureZipExtractor

**Ticket**: [ATT-4592](https://rainerblind.atlassian.net/browse/ATT-4592)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4592`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

On Android devices, `context.applicationInfo.dataDir`, `context.filesDir`, and `context.cacheDir` reside under `/data/user/0/<packageName>`, where `/data/user/0` is an OS-level symlink pointing to `/data/data`.
1. **Symptom**: All full backup restores (Local, Automatic, Dropbox, Google Drive) and incremental imports fail immediately with `SecurityException: Parent real path escapes destination boundary: databases/WorkoutSummaries.db`.
2. **Path Resolution Divergence**: In `SecureZipExtractor.extractBackupArchive()`:
   - `destPath = destDir.toPath().toAbsolutePath().normalize()` retains `/data/user/0/<pkg>/...`.
   - `realDestPath = destPath.toRealPath()` resolves symlinks without `NOFOLLOW_LINKS`, canonicalizing to `/data/data/<pkg>/...`.
   - Archive entries are resolved via `entryPath = destPath.resolve(entryName).normalize()`, retaining `/data/user/0/...`.
3. **NOFOLLOW_LINKS Mismatch**: Lines 121, 129, and 188 invoke `toRealPath(LinkOption.NOFOLLOW_LINKS)` on directory/parent/file paths. Under Java NIO on Linux/Android, `NOFOLLOW_LINKS` prevents resolving symlinks in the path prefix, returning `/data/user/0/...`. Because `/data/user/0/...` does not start with `/data/data/...`, the boundary assertion fails and extraction is aborted.
4. **Root Cause**: Archive entries and ancestor symlink checks are evaluated against `destPath` instead of canonical `realDestPath`.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Secure Extractor** | `SecureZipExtractor.kt` | Canonicalize extraction root to `realDestPath = destPath.toRealPath()`; resolve `entryPath` against `realDestPath`; terminate ancestor symlink walk at `realDestPath`; validate `toRealPath(NOFOLLOW_LINKS)` relative to `realDestPath`. |
| **Full Restore** | `MigrationEngine.kt` | Caller of `SecureZipExtractor.extractBackupArchive()` for full database/preference restore; unaffected signature. |
| **Incremental Import** | `ImportEngine.kt` | Caller of `SecureZipExtractor.extractBackupArchive()` for backup analysis and workout merging; unaffected signature. |
| **Security Tests** | `BackupImportSecurityTest.kt` | Add test case simulating Android `/data/user/0 -> /data/data` symlink parent prefix, verifying clean extraction while ensuring archive-internal symlink attacks remain blocked. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Base all archive entry resolution, prefix checks, ancestor symlink loops, and real-path assertions in `SecureZipExtractor.kt` on canonical `realDestPath`.
  2. Ensure parent symlinks above `realDestPath` (such as `/data/user/0 -> /data/data`) are outside the symlink rejection boundary.
  3. Maintain strict rejection of any symbolic links inside the archive or within the destination tree.
  4. Add unit test coverage in `BackupImportSecurityTest.kt` with a symlinked parent directory simulating Android storage architecture.
* **Out-of-Scope Non-Goals**:
  1. No changes to `MigrationEngine.performFullRestore()` rollback snapshots or connection teardown (`REQ-MIG-041`).
  2. No changes to `ImportEngine.kt` database attachment or table deduplication logic (`REQ-MIG-042`, `REQ-MIG-043`).
  3. No relaxation of Zip-Slip or Zip-Bomb size/count thresholds.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-MIG-040` (*Zip-Slip Path Traversal Protection, Symlink Rejection, Top-Level Whitelist & Bounded Stream Extraction in Backup Operations*).
* **Historical Origin & Commit Trace**: Commit `b87593805db6` (`ATT-3982`, Sprint `2026-42.10`, author: Rainer Blind).
* **Root Reason for Existing Formulation**: `ATT-3982` introduced `SecureZipExtractor` to harden extraction against CWE-22 and symlink TOCTOU attacks. It resolved `realDestPath` via `toRealPath()`, but resolved `entryPath` from `destPath`, assuming `toRealPath(NOFOLLOW_LINKS)` would canonicalize parent segments. Host JVM unit tests in `/tmp` had no parent symlinks, masking the Android `/data/user/0` divergence.
* **Preservation of Core Invariants**: Normalized boundary assertions, strict rejection of symlinks inside extraction directory, top-level whitelist (`databases/`, `shared_prefs/`, `datastore/`), 10,000-entry / 1 GB streaming limits, and atomic staging moves are 100% preserved.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. All extraction operations inside `SecureZipExtractor` MUST resolve against `realDestPath`.
  2. Any symlink located at or beneath `realDestPath` MUST throw `SecurityException` and purge staging.
  3. Pre-existing Android OS symlinks above `realDestPath` MUST NOT cause valid backup archives to fail extraction.
  4. Atomic move and temporary staging file mechanics MUST remain invariant.
* **Risk Rating**: **LOW** — Targeted canonical path correction within `SecureZipExtractor.kt`; zero database schema, UI, or service lifecycle risk; eliminates a critical Android runtime blocker.
