# Stage 2: Requirement & Test Specification: ATT-3993 (Revision 2)

**Ticket**: [ATT-3993](https://atrainingtracker.atlassian.net/browse/ATT-3993)  
**Subtask**: `ATT-4177` (`[Specification]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-044` (*Non-Destructive SportType Schema Upgrade, Custom Sport Preservation, Post-v2 Data Healing Reconciliation, Placeholder Flagging & Strict Downgrade Guard*)  
**Test Case Mapping**: `TST-MIG-041` (`TST-MIG-041.1` – `TST-MIG-041.6`)  
**Branch**: `feature/ATT-3993`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. System Requirements & Functional Specifications

### 1.1 Technical Constraints (MUST / MUST NOT)
1. **Elimination of Destructive Schema Upgrades**:
   - `SportTypeDbHelper.onUpgrade()` MUST NOT execute `db.execSQL("drop table if exists " + SportType.TABLE)` or recreate the table via `onCreate(db)`.
   - All schema upgrades MUST execute non-destructively through incremental, idempotent `ALTER TABLE` and targeted `UPDATE` operations.
2. **Stable Primary Key Preservation**:
   - The primary keys (`_id`) of default sport types (1..5) and user-defined custom sport types (`_id >= 6`) MUST remain strictly immutable across all schema migrations.
3. **Transactional Atomic Migration & Sequence Atomicity**:
   - All statements within `onUpgrade()` MUST execute inside a single atomic SQLite transaction (`db.beginTransaction()`) and commit via `db.setTransactionSuccessful()`.
   - In `reconcileOrphanSportTypes`, the insertion of resurrected placeholders AND the synchronization of `sqlite_sequence` MUST execute within the same atomic transaction, ensuring zero window where placeholders exist with uncommitted sequence.
4. **Strict Downgrade Rejection**:
   - `SportTypeDbHelper.onDowngrade()` MUST NOT execute destructive drops or table recreations. It MUST throw a strict `SQLiteException` refusing to downgrade.
5. **UI Isolation of Placeholders**:
   - The system MUST NOT expose resurrected placeholder sport types (`is_placeholder == 1`) in active workout creation or tracking sport selection pickers.
6. **Auto-Increment Collision Immunity**:
   - The system MUST ensure `sqlite_sequence` for `SportTypes` is synchronized to at least the highest sport ID referenced in `WorkoutSummaries`, preventing SQLite `AUTOINCREMENT` from issuing duplicate IDs.

### 1.2 Functional Behavior (SHALL)
1. **Schema Version 3 Evolution (`REQ-MIG-044.1`)**:
   - `SportTypeDbHelper.DB_VERSION` SHALL be bumped to `3`.
   - When migrating from version 1 (`oldVersion < 2`), `onUpgrade` SHALL update `WALK`'s `baseSportType` to `RUN`:
     ```sql
     UPDATE SportTypes SET baseSportType = 'RUN'
     WHERE (_id = 4 OR UIName IN ('Walk', 'Gehen')) AND baseSportType = 'UNKNOWN';
     ```
   - When migrating to version 3 (`oldVersion < 3`), `onUpgrade` SHALL add the column `is_placeholder INTEGER DEFAULT 0` via `ALTER TABLE SportTypes ADD COLUMN is_placeholder INTEGER DEFAULT 0;` (guarded by column existence inspection).
   - `onUpgrade` SHALL verify that canonical default sport types 1..5 (`OTHER`, `RUN`, `BIKE`, `WALK`, `MTB`) exist, inserting any missing canonical entries without altering user-defined custom sport types.
2. **Post-v2 Corrupted State Data Healing (`REQ-MIG-044.2`)**:
   - `SportTypeDatabaseManager` SHALL provide a reconciliation routine (`reconcileOrphanSportTypes`) executing on database startup or migration to v3.
   - It SHALL scan `WorkoutSummaries.db` (`WorkoutSummaries.SPORT_ID`), `Equipment.db` (`SportTypeEquipmentLinkManager.COLUMN_SPORT_ID`), and `WorkoutClusters.db` (`WorkoutClusterContract.COLUMN_PROBABLE_SPORT_ID`) to collect all referenced `sportId`s.
   - For every referenced ID $X$ ($X > 5$) missing from `SportTypes`, it SHALL insert a placeholder row with explicit primary key `_id = X`, `UIName = "Restored Sport " + X` (or localized string resource), `baseSportType = "UNKNOWN"`, and `is_placeholder = 1`.
3. **Transactional Sequence Atomicity (`REQ-MIG-044.3`)**:
   - The system SHALL execute the `sqlite_sequence` update within the same atomic transaction as the placeholder insertions in `reconcileOrphanSportTypes` or `onUpgrade`.
   - It SHALL calculate `maxId = MAX(MAX(SportTypes._id), MAX(WorkoutSummaries.sportId), 5)` and execute:
     ```sql
     INSERT OR REPLACE INTO sqlite_sequence (name, seq)
     VALUES ('SportTypes', MAX(COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'SportTypes'), 0), :maxId));
     ```
   - This guarantees that placeholder creation and sequence synchronization commit atomically, preventing ID collisions on subsequent user sport creations.
4. **UI Isolation & User Re-Configuration (`REQ-MIG-044.4`)**:
   - In `SportTypeSelectorContract` / tracking start picker, sport types with `is_placeholder == 1` SHALL be filtered out of active selection lists.
   - In historical workout cards, workouts referencing healed sports SHALL display the restored sport name safely without crashing.
   - In sport settings, placeholder entries SHALL display with badge "Restored (Unconfigured)". When the user edits and saves the sport, `is_placeholder` SHALL be cleared to `0`.
5. **Strict Downgrade Prohibition (`REQ-MIG-044.5`)**:
   - `SportTypeDbHelper.onDowngrade()` SHALL throw an explicit `SQLiteException` refusing to downgrade.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
1. **Original Requirement ID & Target**:
   - `REQ-MIG-044` (New), targeting `SportTypeDatabaseManager.java`, `SportTypeDbHelper`, and `SportTypeSelectorContract.kt`.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-3993`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`.
   - Commit `7339ebc5` (Feb 8, 2026) bumped `DB_VERSION` from 1 to 2 to change `WALK`'s `baseSportType` to `RUN`, leaving `drop table if exists` stubbed in `onUpgrade`.
3. **Root Reason for Existing Formulation**:
   - Historical `onUpgrade` implementations in early development scaffolded a quick `DROP TABLE IF EXISTS` pattern with comment `// TODO: alter table instead of deleting!`. When `DB_VERSION` was incremented to 2, `onUpgrade` was never updated to incremental migration, causing user custom sport types to be destroyed on upgrade.
4. **Preservation of Core Invariants**:
   - Non-destructive schema evolution, primary key immutability for custom sport types (`_id >= 6`), transactional sequence atomicity, `WALK` base sport type as `RUN`, default canonical sport types 1..5, active picker isolation, and 100% clean-room test pass rate.

---

## 2. Acceptance Criteria (Given-When-Then)

### Acceptance Criterion 1: Clean v1 -> v3 Migration
- **Given** a SportType database at version 1 containing user-created sport types (`_id = 6`, `_id = 7`),
- **When** `SportTypeDbHelper.onUpgrade(db, 1, 3)` executes,
- **Then** user-created sport types and their IDs are fully preserved, `WALK`'s `baseSportType` equals `RUN`, and column `is_placeholder` is present with value `0`.

### Acceptance Criterion 2: Corrupted Post-v2 State Healing
- **Given** a post-v2 database where custom sport types were dropped and `WorkoutSummaries` references `sportId = 6` and `sportId = 8`,
- **When** `reconcileOrphanSportTypes()` runs,
- **Then** placeholder rows are created with `_id = 6` and `_id = 8`, `is_placeholder = 1`, and workouts resolve to these entities instead of falling back to `OTHER`.

### Acceptance Criterion 3: Transactional Sequence Atomicity & Collision Immunity
- **Given** a post-v2 database undergoing reconciliation with orphan ID 8,
- **When** `reconcileOrphanSportTypes()` executes,
- **Then** the placeholder insertion and `sqlite_sequence` update commit atomically within the same transaction, setting `seq >= 8`, and subsequent custom sport creation receives `_id >= 9`.

### Acceptance Criterion 4: Active Picker Isolation
- **Given** a database containing a resurrected placeholder sport type with `is_placeholder = 1`,
- **When** querying available sport types for a new workout in `SportTypeSelectorContract`,
- **Then** the placeholder sport type is filtered out of the active selection list.

### Acceptance Criterion 5: Strict Downgrade Rejection
- **Given** a database at version 3,
- **When** `onDowngrade(db, 3, 2)` is invoked,
- **Then** `SQLiteException` is thrown, the table is not dropped, and local data remains 100% intact.

---

## 3. Test Specification (`TST-MIG-041`)

### 3.1 `TST-MIG-041.1`: Clean v1 -> v3 Upgrade & Invariant Preservation
- **Target**: `SportTypeDbHelper.onUpgrade`
- **Methodology**:
  1. Construct an SQLite in-memory or mock database with Schema v1 (`CREATE_TABLE` without `is_placeholder`).
  2. Insert canonical default sport types 1..5 (`OTHER`, `RUN`, `BIKE`, `WALK` with `baseSportType = 'UNKNOWN'`, `MTB`).
  3. Insert custom sport types `_id = 6` ("Inline Skating") and `_id = 7` ("Rowing").
  4. Invoke `helper.onUpgrade(db, 1, 3)`.
  5. Assert:
     - Table `SportTypes` exists and was NOT dropped.
     - Row 4 (`WALK`) has `baseSportType == "RUN"`.
     - Row 6 exists with `UIName == "Inline Skating"`.
     - Row 7 exists with `UIName == "Rowing"`.
     - Column `is_placeholder` exists and defaults to `0` for all existing rows.

### 3.2 `TST-MIG-041.2`: Corrupted Post-v2 State Healing & Placeholder Flagging
- **Target**: `SportTypeReconciliationHelper` / `SportTypeDatabaseManager.reconcileOrphanSportTypes`
- **Methodology**:
  1. Construct a database in Schema v3 containing only canonical default sports 1..5.
  2. Simulate `WorkoutSummaries.db` containing workouts with `sportId = 6` and `sportId = 8`.
  3. Run `reconcileOrphanSportTypes(context, sportDb, workoutSummariesDb)`.
  4. Assert:
     - Row with `_id = 6` exists in `SportTypes` with `is_placeholder = 1`.
     - Row with `_id = 8` exists in `SportTypes` with `is_placeholder = 1`.
     - `sportTypeManager.getUIName(6)` returns the restored placeholder name ("Restored Sport 6").
     - `sportTypeManager.getBSportType(6)` returns `BSportType.UNKNOWN`.

### 3.3 `TST-MIG-041.3`: Atomic Transactionality & Sequence Protection
- **Target**: `SportTypeReconciliationHelper` & `sqlite_sequence`
- **Methodology**:
  1. Set up a database undergoing data healing with orphan ID 8.
  2. Verify that placeholder insertion and `sqlite_sequence` update commit atomically within the same transaction.
  3. Assert `seq >= 8` in `sqlite_sequence`.
  4. Insert a new custom sport type using `setSportType(-1, "Climbing", ...)`.
  5. Assert that the returned ID is $\ge 9$, verifying zero collision with historical workouts.

### 3.4 `TST-MIG-041.4`: UI Isolation & Active Picker Filtering
- **Target**: `SportTypeSelectorContract` / `SportTypeDatabaseManager.getSportTypesIdList`
- **Methodology**:
  1. Populate `SportTypes` table with standard sport types (`is_placeholder = 0`) and a placeholder sport type `_id = 6` (`is_placeholder = 1`).
  2. Query active sport selection list via `getSportTypesIdList(BSportType.UNKNOWN, ...)`.
  3. Assert that ID 6 is excluded from the list of selectable active sports.
  4. Query `getString(6, SportType.UI_NAME)` asserting that historical workout rendering resolves "Restored Sport 6" successfully.

### 3.5 `TST-MIG-041.5`: Strict Downgrade Guard Rejection
- **Target**: `SportTypeDbHelper.onDowngrade`
- **Methodology**:
  1. Initialize database at Schema v3.
  2. Invoke `helper.onDowngrade(db, 3, 2)` inside an `assertThrows(SQLiteException.class, ...)`.
  3. Assert that `SQLiteException` is thrown.
  4. Verify that table `SportTypes` still exists and all rows are intact.

### 3.6 `TST-MIG-041.6`: Clean-Room Full Suite Regression Execution
- **Target**: Entire unit test suite
- **Methodology**:
  1. Execute `./gradlew testDebugUnitTest`.
  2. Assert 100% pass rate with zero test failures or regressions across all modules.

---

## 4. 9-Language Localization Parity Audit

The feature adds 2 new string resources to identify restored sport types and badge unconfigured placeholders in settings:
1. `sport_type_restored_placeholder`: Template for restored placeholder sport types (e.g. "Restored Sport %1$d").
2. `sport_type_placeholder_badge`: Badge displayed in settings for unconfigured placeholders.

### Localization Parity Matrix
| Locale Code | Language | `sport_type_restored_placeholder` | `sport_type_placeholder_badge` | Format Specifiers Match? | Literal `\n` Verified? |
|---|---|---|---|:---:|:---:|
| `values` | English (Default) | `Restored Sport %1$d` | `Restored (Unconfigured)` | Yes (`%1$d`) | N/A |
| `values-de` | German | `Wiederhergestellte Sportart %1$d` | `Wiederhergestellt (Nicht konfiguriert)` | Yes (`%1$d`) | N/A |
| `values-es` | Spanish | `Deporte restaurado %1$d` | `Restaurado (Sin configurar)` | Yes (`%1$d`) | N/A |
| `values-fr` | French | `Sport restauré %1$d` | `Restauré (Non configuré)` | Yes (`%1$d`) | N/A |
| `values-it` | Italian | `Sport ripristinato %1$d` | `Ripristinato (Non configurato)` | Yes (`%1$d`) | N/A |
| `values-ja` | Japanese | `復元されたスポーツ %1$d` | `復元済み（未設定）` | Yes (`%1$d`) | N/A |
| `values-nl` | Dutch | `Herstelde sport %1$d` | `Hersteld (Niet geconfigureerd)` | Yes (`%1$d`) | N/A |
| `values-pl` | Polish | `Przywrócony sport %1$d` | `Przywrócony (Nieskonfigurowany)` | Yes (`%1$d`) | N/A |
| `values-pt` | Portuguese | `Desporto restaurado %1$d` | `Restaurado (Não configurado)` | Yes (`%1$d`) | N/A |

---

## 5. Traceability Matrix

| Requirement Sub-Clause | Description | Test Case ID | Test Implementation |
|---|---|---|---|
| `REQ-MIG-044.1` | Non-Destructive Schema Upgrade & Version 3 Evolution | `TST-MIG-041.1` | `SportTypeDatabaseUpgradeTest.kt:testCleanV1ToV3UpgradePreservesCustomSports` |
| `REQ-MIG-044.2` | Post-v2 Corrupted State Data Healing & Orphan Resurrection | `TST-MIG-041.2` | `SportTypeDatabaseUpgradeTest.kt:testCorruptedPostV2StateHealingResurrectsPlaceholders` |
| `REQ-MIG-044.3` | Transactional Sequence Atomicity & Collision Immunity | `TST-MIG-041.3` | `SportTypeDatabaseUpgradeTest.kt:testSequenceProtectionPreventsIdCollision` |
| `REQ-MIG-044.4` | UI Isolation of Placeholders from Active Workout Pickers | `TST-MIG-041.4` | `SportTypeDatabaseUpgradeTest.kt:testActivePickerFiltersPlaceholders` |
| `REQ-MIG-044.5` | Strict Downgrade Prohibition | `TST-MIG-041.5` | `SportTypeDatabaseUpgradeTest.kt:testDowngradeThrowsSQLiteExceptionWithoutDataLoss` |
| All | Clean-Room Full Suite Regression | `TST-MIG-041.6` | `./gradlew testDebugUnitTest` |
