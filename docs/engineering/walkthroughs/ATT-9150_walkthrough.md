# Stage 5: Walkthrough & Verification - ATT-9150: Preserve Explicit `UPLOAD_TO_STRAVA = 0` Opt-Out in `applyInferredIdentity`

**DOC-ID**: `DOC-WLK-ATT-9150` | **Ticket**: [ATT-9150](https://rainerblind.atlassian.net/browse/ATT-9150) (`1 SP`, Medium, `severity-major`) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-20_01_Früh` | **Branch**: `feature/ATT-9150` | **Author**: `--as agent1`  
**Requirement Mapping**: `REQ-MIG-054` (`[Verified]`, `SYSREQ-DEX-004`) | **Test Mapping**: `TST-MIG-052` (`[Verified]`)

---

## 1. Executive Summary & Bug Fix Verification (`REQ-PRO-121.2`)
- **Pre-Fix Defect Behavior**: `WorkoutSummariesDatabaseManager.applyInferredIdentity()` (`lines 335-338`) checked `Integer currentUpload = getInt(workoutId, WorkoutSummaries.UPLOAD_TO_STRAVA); if (currentUpload == null || currentUpload != 1)`. On a first TCX import with `uploadToStrava = false` (`UPLOAD_TO_STRAVA = 0`) when `RouteClusters.db` was empty, `LegacyImportEngine` called `WorkoutClusterRepository.refreshClusters()`, triggering `migrateHistory()` -> `assignClusterToWorkout()` -> `applyInferredIdentity()` and overwriting `0` to `1`. Additionally, `getInt()` coerced SQL `NULL` to `0` (`cursor.isNull` unchecked).
- **Post-Fix Verified Behavior (`aa9a407c`)**: Replaced the `getInt()` / `!= 1` guard with a parameterized `try (Cursor c = ...)` query (`-1` net lines -> `WorkoutSummariesDatabaseManager.java = 3,147 / 3,148` LoC) returning safely when `!c.moveToFirst()` and updating `UPLOAD_TO_STRAVA` **only** when `c.isNull(0) || c.getInt(0) == -1` (`DEFAULT -1`). Removed the cluster-seed workaround from `TcxImportStravaToggleTest.kt` (`assertTrue(WorkoutClusterDatabaseManager.getInstance(context).getAllClusters().isEmpty())`), proving empty-DB TCX opt-out preservation (`UPLOAD_TO_STRAVA == 0`, zero Strava export jobs), plus 5 deterministic unit tests covering `-1` -> `1`/`0`, SQL `NULL` -> `1`, explicit `0` & `1` preservation, and non-existent `workoutId` safe no-op.
- **Rule 7.5 Zero-Redundant-Gradle Compliance**: Zero `app/` edits in Stage 5 (`git diff aa9a407c..HEAD -- app/` is empty); reuses Gate 4 (`ATT-9281`) verified SHA `aa9a407c` (`46/46` PASS, `0` compiler warnings).

## 2. Suite-by-Suite Automated Test Evidence (Gate 4 SHA `aa9a407c`)
Executed `:app:assembleDebug` (GREEN) + `7` locked incremental `:app:testDebugUnitTest --rerun --tests ...` suites (**46/46 PASS, 0 failures, 0 errors, 0 skipped**, Tier 1 targeted + architecture/audit contracts):

| Test Class (`app/src/test/`) | Passed | Linked IDs | Scope / Notes |
| :--- | :---: | :--- | :--- |
| `TcxImportStravaToggleTest` | `4/4` | `TST-MIG-025`, `TST-MIG-052` | Empty `RouteClusters.db` TCX opt-out `0` preservation (cluster-seed workaround removed) |
| `WorkoutSummariesDatabaseManagerApplyIdentityTest` | `5/5` | `REQ-MIG-054`, `TST-MIG-052` | `-1` (`1`/`0`), SQL `NULL`, explicit `0`/`1` preservation, missing ID no-op |
| `WorkoutClusterEngineLearningTest` | `9/9` | `REQ-SET-*` | Cluster engine learning regression check |
| `WorkoutRepositorySaveClusteringTest` | `5/5` | `REQ-SET-*` | Repository clustering save regression check |
| `ArchitectureConformanceContractTest` | `11/11` | `REQ-PRO-129` | `WorkoutSummariesDatabaseManager.java` = `3,147 <= 3,148` LoC limit |
| `DeadCodeEliminationContractTest` | `6/6` | `REQ-PRO-102` | Dead code & layout contract |
| `SourceTextReadingAuditContractTest` | `6/6` | `REQ-PRO-133` | `0` `app/src/main/` file reads added (`baseline == 105`) |

## 3. Linters, Living Docs & Governance Verification
- **Safety Linters**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` all passed (`0` violations).
- **Living Docs (`REQ-PRO-120.2`)**: Promoted `REQ-MIG-054` (`docs/requirements/req_data_migration_export_part2.md` `v1.0.3 -> v1.0.4`) and `TST-MIG-052` (`docs/tests/tst_data_migration_export.md` `v1.0.4 -> v1.0.5`) to `[Verified]`; synced `docs/requirements.md` and `docs/tests.md` via `--sync-indexes`.
- **Governance & Branch Preservation (`REQ-PRO-125`)**: Programmatic verbatim citation check (`100%` match in `app/src/test/`) + both `verify_requirement_governance.py` checks exited `0`; `feature/ATT-9150` preserved intact for Stage 6 Cold Review; `Localization Parity: N/A` (`0` XML string edits).

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-MIG-054` (`TST-MIG-052`) hardening `REQ-MIG-028` (`TST-MIG-025`) & `REQ-SET-029`/`030` in `WorkoutSummariesDatabaseManager.applyInferredIdentity()` (`lines 334-343`).
- **Historical Origin & Commit Trace**: `applyInferredIdentity()` introduced in `e0392e39` (`SCRUM-200`) and guarded in `b58ca19b` (`ATT-1116`, `currentUpload != 1`).
- **Root Reason for Existing Formulation**: `ATT-1116` protected opt-in `1` from downgrade without distinguishing explicit opt-out `0` from undecided DDL sentinel `-1` (`line 2554`) or SQL `NULL` (coerced to `0` by `getInt()`).
- **Preservation of Core Invariants**: Live workouts (`DEFAULT -1`) and SQL `NULL` rows continue receiving `identity.getUploadToStrava()` (`1` or `0` per `REQ-EXP-006`); explicit `1` opt-ins stay protected; `getInt()` unchanged for all other callers; `WorkoutSummariesDatabaseManager.java` reduced `3,148 -> 3,147` lines.
