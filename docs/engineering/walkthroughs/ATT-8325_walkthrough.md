# Stage 5: Walkthrough & Verification - ATT-8325: Data Migration & Export Verification (25 TSTs)

**Ticket**: [ATT-8325](https://rainerblind.atlassian.net/browse/ATT-8325)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8325`  
**Plan Reference**: [`ATT-8325_plan.md`](file:///home/rainer/.gemini/antigravity/worktrees/68bfcc3e-c3eb-4d35-bf40-75111f97ff53/subagent-Lane-A-Flash-Author-ATT-8138-R2-self-daec6b1c/docs/engineering/plans/ATT-8325_plan.md)  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19

---

## 1. Executive Summary & Verification Overview

Implemented and hardened executable Robolectric, Compose UI, and real SQLite verification covering all **25** Data Migration & Export `TST-*` specs from `ATT-8145` with **`0` edits to `app/src/main/`** and **`0` `src/main/` file reads** (`SourceTextReadingAuditContractTest`: `6/6 PASS`). Rebased cleanly onto `sprint/2026-11-19_02_Spät` (`2d7f1489`) with zero conflicts (preserving `ATT-8917`'s test in `TrackerServiceResumeTest.kt` alongside `TST-DAT-021`). Per **Rule 7.5**, Stage 5 reuses Gate 4's deterministic **`159/159 PASS`** across all 27 targeted suites.

---

## 2. Verification Matrix (25 TSTs Promoted `[Verified]`)

| TST ID(s) | REQ ID(s) | Test Suite(s) | Gate 4 Result (`--rerun`) | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-001` | `REQ-DAT-007` | `KnownLocationsDatabaseManagerTest` | **18/18 PASS** | `[Verified]` (REQ already Verified) |
| `TST-DAT-021` | `REQ-DAT-026` | `TrackerServiceResumeTest` | **17/17 PASS** | `[Verified]` / `REQ-DAT-026` `[Verified]` |
| `TST-DAT-032` | `REQ-DAT-036` | `SqliteSchemaParityContractTest` (6/6), `DatabaseMigrationIndexTest` (7/7), `*RealSqliteMigrationTest` (7 suites: 28/28) | **41/41 PASS** | `[Verified]` / `REQ-DAT-036` `[Verified]` |
| `TST-EXP-004` | `REQ-EXP-001` | `TCXFileWriterSchemaComplianceTest` | **2/2 PASS** | TST `[Verified]`; `REQ-EXP-001` held `[Implemented]` (`TST-REG-003`) |
| `TST-MIG-001`, `002` | `REQ-MIG-001,002,004,005,006` | `BackupMigrationCycleTest` (3/3), `LegacyImportEngineDeduplicationTest` (9/9) | **12/12 PASS** | `[Verified]` / `REQ-MIG-001,002,005,006` `[Verified]` |
| `TST-MIG-003`, `006`, `007` | `REQ-MIG-003,009,010` | `BackupWorkerTest` (12/12), `BackupWorkerDropboxGatingTest` (5/5), `DropboxBackupDefaultsTest` (2/2) | **19/19 PASS** | `[Verified]` / `REQ-MIG-003,009,010` `[Verified]` |
| `TST-MIG-004`, `005`, `008`, `016` | `REQ-MIG-007,008,011,020` | `ImportBackupTabsUiContractTest` | **4/4 PASS** | `[Verified]` / `REQ-MIG-007,008,011,020` `[Verified]` |
| `TST-MIG-010..012`, `015`, `018` | `REQ-MIG-013,015,016,019,021` | `LegacyImportEngineCloudRecoveryTest` | **3/3 PASS** | `[Verified]` / 5 REQs `[Verified]` |
| `TST-MIG-013`, `017` | `REQ-MIG-017`, `REQ-MIG-014` | `BackupRestoreInteractionQueueTest` | **1/1 PASS** | TST `[Verified]`; `REQ-MIG-017` `[Verified]`; `REQ-MIG-014` held `[Implemented]` (`TST-PRO-004`) |
| `TST-MIG-014` | `REQ-MIG-018` | `LegacyImportEngineSparseDataTest` | **1/1 PASS** | `[Verified]` / `REQ-MIG-018` `[Verified]` |
| `TST-MIG-019` | `REQ-MIG-022` | `LegacyImportEngineWorkoutNameTest` | **9/9 PASS** | `[Verified]` / `REQ-MIG-022` `[Verified]` |
| `TST-MIG-025` | `REQ-MIG-028` | `TcxImportStravaToggleTest` (4/4), `TranslationParityTest` (11/11) | **15/15 PASS** | `[Verified]` / `REQ-MIG-028` `[Verified]` |
| `TST-EXT-001`, `002` | `REQ-EXT-004,005` | `StravaAuthCustomTabsFlowTest` (3/3), `StravaOAuthCallbackActivityTest` (7/7) | **10/10 PASS** | `[Verified]` / `REQ-EXT-004,005` `[Verified]` |
| `REQ-PRO-133` | Contract / Governance | `SourceTextReadingAuditContractTest` (6/6), `verify_requirement_governance.py` | **6/6 PASS**, **EXIT 0** | `1,103` backed TSTs (`0` gaps) |

---

## 3. Automated Test & Governance Evidence (Gate 4 Reuse — Rule 7.5)

- **Targeted Unit Suites (`./gradlew :app:testDebugUnitTest --rerun` across 27 suites under `parallel_sprint_lock.py`)**: **159 tests, 0 failures, 0 errors, 0 skipped** (reused unchanged `app/` tree after clean conflict-free rebase onto `sprint/2026-11-19_02_Spät` `2d7f1489`).
- **Governance (`python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-19_02_Spät --check-verified-evidence`)**: `PASS: 100% of [Verified] TST specifications verified against test evidence (0 legacy unbacked TSTs tolerated in ratchet baseline, 1103 backed).`
- **Production Diff Invariant**: `git diff sprint/2026-11-19_02_Spät..HEAD -- app/src/main/` is empty (`0` lines).

---

## 4. Device Verification & Visual Consistency (`Rule 23`)

Pure test-construction ticket (`0` `app/src/main/` edits, `0` string edits). Compose behaviors (`ImportBackupTabsScreen`, `BackupTabContentLayout`, `RestoreTabContent`, `PreImportTuningBottomSheet`) are verified under Robolectric `createComposeRule()`. Emulator deployment is N/A.

---

## 5. Out-of-Scope Production Observations & Handover (`REQ-PRO-125`)

Two out-of-scope production defects uncovered during executable testing were kept untouched (`0` `src/main/` invariant) and are tracked separately:
1. **`ATT-8122`**: `DatabaseSchemaRegistry` max versions lag helper versions for `Laps.db` (`2` vs `3`), `Routes.db` (`10` vs `11`), and `ExportStatus.db` (`1` vs `2`).
2. **`ATT-9150`**: `WorkoutSummariesDatabaseManager.applyInferredIdentity` checks `currentUpload != 1`, causing empty-DB `migrateHistory()` on `refreshClusters()` to overwrite explicit `UPLOAD_TO_STRAVA = 0` back to `1`.
