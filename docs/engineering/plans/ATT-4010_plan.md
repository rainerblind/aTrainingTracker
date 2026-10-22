# Stage 3: Implementation Plan - ATT-4010: insertSensorFieldAt shifts sensor fields in all cockpit tabs

**Ticket**: [ATT-4010](https://rainerblind.atlassian.net/browse/ATT-4010)  
**Sub-task**: [ATT-4218](https://rainerblind.atlassian.net/browse/ATT-4218) (`[Plan]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-413` (*Cockpit Sensor Field Insertion Transaction Atomicity, Multi-Tab Isolation, and Grid Normalization*)  
**Test Spec ID**: `TST-UI-376` (*Cockpit Sensor Field Insertion Atomicity, Tab Isolation, Divider Synchronization, and Rollback Verification Suite*)  
**Branch**: `feature/ATT-4010`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Root Cause Analysis

In `TrackingViewsDatabaseManager.insertSensorFieldAt`, a new sensor field is inserted into a cockpit tab and existing rows (or columns) are shifted to open space. Historically, the SQL shift statements lacked `WHERE VIEW_ID = ?` predicates, causing global coordinate distortion across all cockpit tabs.

While commit `6199b2a4` (`ATT-4055`) introduced horizontal section dividers and added `WHERE VIEW_ID = tabViewId` string filters to mitigate basic cross-tab leakage, comprehensive inspection and Gate 1 / Gate 2 review identified remaining critical flaws:
1. **Non-Transactional Execution**: The method lacks transaction boundaries (`beginTransaction() ... try { ... setTransactionSuccessful(); } finally { endTransaction(); }`). If an interruption, SQLite constraint error, or process crash occurs during row shifting or insertion, the database is left in a torn, corrupted state with shifted rows but no inserted tile.
2. **Missing Grid Normalization**: Unlike `deleteSensorField` and `moveSensorField`, `insertSensorFieldAt` omits invoking `normalizeGrid(db, tabViewId)`. Inserting out-of-order or out-of-bounds coordinates (e.g. `rowNr > currentMaxRow + 1`) creates permanent coordinate gaps.
3. **Raw String SQL Formatting**: Shift statements use raw string concatenation (`" WHERE " + VIEW_ID + "=" + tabViewId + ...`) instead of compiled parameterized queries with `Object[]` bind arguments.
4. **Missing Parameter Validation**: The method performs zero validation on `tabViewId` or `rowNr`, allowing non-positive IDs (`tabViewId <= 0` or `rowNr < 1`) to trigger invalid database writes.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-413` (*Cockpit Sensor Field Insertion Transaction Atomicity, Multi-Tab Isolation, and Grid Normalization*)
  * `REQ-UI-413.1`: Input & boundary validation (`tabViewId > 0` and `rowNr >= 1`), with out-of-bounds rows (`rowNr > currentMaxRow + 1`) compacted cleanly to `currentMaxRow + 1` by grid normalization.
  * `REQ-UI-413.2`: Single-transaction atomicity (`beginTransaction()` ... `setTransactionSuccessful()` ... `endTransaction()`) guaranteeing complete rollback on failure.
  * `REQ-UI-413.3`: Multi-tab isolation and parameterized DML (`WHERE VIEW_ID = ?` with `Object[]` bind arguments).
  * `REQ-UI-413.4`: Deterministic shift sequence and divider synchronization (`ROWS_TABLE` rows $\ge \text{rowNr}$ shifted down first, then `SECTION_DIVIDERS_TABLE` records $\ge \text{rowNr}$ shifted down, new field inserted, and dividers remapped).
  * `REQ-UI-413.5`: Post-insertion grid normalization invoking internal transaction-participating overload `normalizeGrid(SQLiteDatabase db, long tabViewId)`.
* **Test Mapping**: `TST-UI-376` (*Cockpit Sensor Field Insertion Atomicity, Tab Isolation, Divider Synchronization, and Rollback Verification Suite*)
  * `TST-UI-376.1`: `testInsertSensorFieldAt_strictlyIsolatesTabView` in `TrackingViewsRealSqliteMigrationTest.kt` (verifying Tab 2 records remain 100% untouched when inserting in Tab 1).
  * `TST-UI-376.2`: `testInsertSensorFieldAt_rollsBackEntirelyOnError` in `TrackingViewsRealSqliteMigrationTest.kt` (verifying atomic rollback on mid-operation failure).
  * `TST-UI-376.3`: `testInsertSensorFieldAt_normalizesGappedGridAndDividers` in `TrackingViewsRealSqliteMigrationTest.kt` and `TrackingViewsDatabaseManagerTest.kt` (verifying gapped coordinate healing and section divider remapping).
  * `TST-UI-376.4`: `testInsertSensorFieldAt_parameterAndBoundaryValidation` in `TrackingViewsDatabaseManagerTest.kt` (verifying rejection of non-positive IDs and handling of out-of-bounds row insertion).
  * `TST-UI-376.5`: Localization audit (zero string modifications in `res/values*/strings.xml`).
  * `TST-UI-376.6`: Full clean-room regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Passive `onOpen()` Invariant (`REQ-CON-017 / ATT-3990`)**: `onOpen()` in database helpers remains strictly passive with zero mutations and zero thread allocations.
2. **Contiguous Coordinates**: Sensor grid coordinates strictly adhere to $1..N$ row and column numbering without coordinate gaps.
3. **Divider Alignment (`REQ-UI-411.2`)**: Section dividers remain positioned at valid row boundaries ($\ge 2$).
4. **Nested Transaction Prevention**: `insertSensorFieldAt` must call the private overload `normalizeGrid(SQLiteDatabase db, long tabViewId)` which participates in the open transaction without calling `db.beginTransaction()`, preventing `IllegalStateException: Cannot perform this operation because there is an already open transaction`.
5. **Single Atomic Commit per Ticket (Rule 44)**: Changes remain uncommitted until Stage 5 clean-room verification is signed off.

---

## 4. Proposed Architectural Changes

```
+---------------------------------------------------------------------------------+
|              TrackingViewsDatabaseManager.insertSensorFieldAt(...)              |
+---------------------------------------------------------------------------------+
|  1. Validate Parameters:                                                        |
|     if (tabViewId <= 0 || rowNr < 1) {                                          |
|         Log.w(TAG, "Invalid parameters for insertSensorFieldAt: ...");          |
|         return;                                                                 |
|     }                                                                           |
|                                                                                 |
|  2. Atomic Transaction:                                                         |
|     SQLiteDatabase db = getDatabase();                                          |
|     db.beginTransaction();                                                      |
|     try {                                                                       |
|         if (colNr == -1) {                                                      |
|             // Shift ROWS_TABLE rows >= rowNr down by 1 (parameterized)         |
|             db.execSQL("UPDATE " + ROWS_TABLE +                                 |
|                 " SET " + ROW_NR + " = " + ROW_NR + " + 1 " +                   |
|                 " WHERE " + VIEW_ID + " = ? AND " + ROW_NR + " >= ?",           |
|                 new Object[]{tabViewId, rowNr});                                |
|                                                                                 |
|             // Shift SECTION_DIVIDERS_TABLE rows >= rowNr down by 1             |
|             db.execSQL("UPDATE " + SECTION_DIVIDERS_TABLE +                     |
|                 " SET " + ROW_NR + " = " + ROW_NR + " + 1 " +                   |
|                 " WHERE " + VIEW_ID + " = ? AND " + ROW_NR + " >= ?",           |
|                 new Object[]{tabViewId, rowNr});                                |
|             colNr = 1;                                                          |
|         } else {                                                                |
|             // Shift ROWS_TABLE columns >= colNr right by 1 (parameterized)     |
|             db.execSQL("UPDATE " + ROWS_TABLE +                                 |
|                 " SET " + COL_NR + " = " + COL_NR + " + 1 " +                   |
|                 " WHERE " + VIEW_ID + " = ? AND " + ROW_NR + " = ? " +          |
|                 " AND " + COL_NR + " >= ?",                                     |
|                 new Object[]{tabViewId, rowNr, colNr});                         |
|         }                                                                       |
|                                                                                 |
|         // Insert new sensor field record                                       |
|         ContentValues values = new ContentValues();                             |
|         values.put(VIEW_ID, tabViewId);                                         |
|         values.put(ROW_NR, rowNr);                                              |
|         values.put(COL_NR, colNr);                                              |
|         values.put(SENSOR_TYPE, sensorType.name());                             |
|         values.put(VIEW_SIZE, viewSize.name());                                 |
|         if (sourceDeviceId != null && sourceDeviceId >= 0) {                    |
|             values.put(SOURCE_DEVICE_ID, sourceDeviceId);                        |
|         }                                                                       |
|         values.put(FILTER_TYPE, filterType.name());                             |
|         values.put(FILTER_CONSTANT, filterConstant);                            |
|         db.insertOrThrow(ROWS_TABLE, null, values);                             |
|                                                                                 |
|         // Normalize grid & synchronize dividers within transaction             |
|         normalizeGrid(db, tabViewId);                                           |
|                                                                                 |
|         db.setTransactionSuccessful();                                          |
|     } finally {                                                                 |
|         db.endTransaction();                                                    |
|     }                                                                           |
+---------------------------------------------------------------------------------+
```

### Component Details:
1. **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`.
2. **Input Validation**: Check `tabViewId <= 0 || rowNr < 1` and return immediately.
3. **Transaction Safety**: Enclose all DML statements in `db.beginTransaction() ... try { ... normalizeGrid(db, tabViewId); db.setTransactionSuccessful(); } finally { db.endTransaction(); }`.
4. **Parameterized DML**: Replace string-interpolated `db.execSQL(...)` with parameterized overloads using `new Object[]{...}` to guarantee multi-tab isolation and SQL injection immunity.
5. **Use `insertOrThrow`**: Replace `db.insert(...)` with `db.insertOrThrow(...)` to ensure SQLite constraint violations fail fast and trigger clean transaction rollback rather than silently failing.
6. **Internal `normalizeGrid`**: Invoke `normalizeGrid(db, tabViewId)` which is already implemented as a private helper that operates directly on the passed `db` connection without creating a nested transaction.

---

## 5. UI Consistency (Rule 23)

* **Scope**: UI Consistency: N/A.
* **Justification**: This change modifies database data access and transaction boundaries in `TrackingViewsDatabaseManager.java`. Zero UI composables, themes, typography styles, or layout resources are created or altered.

---

## 6. Atomic Implementation Steps

### Step 1: Refactor `insertSensorFieldAt` in `TrackingViewsDatabaseManager.java`
- Add input boundary check: `if (tabViewId <= 0 || rowNr < 1) { Log.w(TAG, ...); return; }`.
- Wrap coordinate shifting, divider shifting, field insertion, and normalization in `db.beginTransaction()`.
- Convert all `UPDATE` queries for `ROWS_TABLE` and `SECTION_DIVIDERS_TABLE` to use parameterized `Object[]` bind arguments.
- Replace `db.insert(...)` with `db.insertOrThrow(...)`.
- Call `normalizeGrid(db, tabViewId)` before `db.setTransactionSuccessful()`.
- Add `finally { db.endTransaction(); }`.

### Step 2: Author Real SQLite Integration Tests in `TrackingViewsRealSqliteMigrationTest.kt`
- `testInsertSensorFieldAt_strictlyIsolatesTabView`:
  - Initialize Tab 1 and Tab 2 with multiple rows and section dividers.
  - Call `insertSensorFieldAt` on Tab 1 with `colNr = -1` (new row).
  - Verify Tab 1 rows and dividers shift down by 1.
  - Verify Tab 2 rows and dividers retain exact original `ROW_NR` and `COL_NR` values.
- `testInsertSensorFieldAt_rollsBackEntirelyOnError`:
  - Simulate a mid-transaction failure (or trigger constraint error).
  - Verify that the entire transaction rolls back and Tab 1 remains identical to its pre-execution state.
- `testInsertSensorFieldAt_normalizesGappedGridAndDividers`:
  - Set up a tab with fragmented row coordinates (e.g. rows 1, 3, 5) and a divider at row 3.
  - Call `insertSensorFieldAt` to insert a new row.
  - Verify that all rows compact contiguously to 1, 2, 3, 4 and dividers re-map accurately.

### Step 3: Author Unit Tests in `TrackingViewsDatabaseManagerTest.kt`
- `testInsertSensorFieldAt_parameterValidation`:
  - Verify `tabViewId <= 0` or `rowNr < 1` returns immediately without invoking `db.beginTransaction()`.
- `testInsertSensorFieldAt_outOfBoundsRowHandledGracefully`:
  - Verify calling `insertSensorFieldAt` with `rowNr = 999` succeeds and normalizes the row coordinate to `currentMaxRow + 1`.

### Step 4: Targeted Verification & Clean-Room Regression
- Run targeted tests: `./gradlew testDebugUnitTest --tests "*TrackingViewsRealSqliteMigrationTest*" --tests "*TrackingViewsDatabaseManagerTest*"`
- Verify 100% pass rate.

---

## 7. Review and Gate Audit Preparation

- Update subtask `ATT-4218` description with this document.
- Transition `ATT-4218` to `In Überprüfung`.
- Run Gate 3 review audit via `python3 tools/review_agent.py audit ATT-4218`.
- Upon passing Gate 3, execute `python3 tools/jira_util.py move ATT-4218 freigabe` to advance parent `ATT-4010` to `Implementation`.
