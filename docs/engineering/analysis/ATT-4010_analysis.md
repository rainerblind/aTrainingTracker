# Stage 1 Analysis: ATT-4010 - insertSensorFieldAt shifts sensor fields in all cockpit tabs

**Ticket**: [ATT-4010](https://rainerblind.atlassian.net/browse/ATT-4010)  
**Sub-task**: [ATT-4216](https://rainerblind.atlassian.net/browse/ATT-4216) (`[Analysis]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Branch**: `feature/ATT-4010`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

During the whole-app architecture and code quality audit conducted on 2026-10-18 (`feature/ATT-3921` @ `0234bfc0`), an issue was identified in `TrackingViewsDatabaseManager.java:insertSensorFieldAt`:
- The `UPDATE` statements shifting rows and columns when inserting a new sensor field or new row historically lacked `WHERE VIEW_ID = ?` predicates (unlike `moveSensorField` and `normalizeGrid`).
- Consequently, inserting a new field in Tab A would shift rows and columns globally across **all** configured cockpit tabs (Tab B, Tab C, etc.), resulting in cross-tab coordinate distortion, grid misalignment, and orphaned gaps.
- In addition, `insertSensorFieldAt` performs coordinate shifting and row insertion without database transaction boundaries (`beginTransaction()`), leaving the database vulnerable to corruption if an interruption or process kill occurs between the `UPDATE` and `insert()` calls.
- Furthermore, `insertSensorFieldAt` does not invoke `normalizeGrid(db, tabViewId)`, failing to guarantee contiguous $1..N$ coordinate indices and divider synchronization after insertion.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `TrackingViewsDatabaseManager.java` and git commit history reveals:

1. **Historical Origin (`85a59b8d` & `3042007b`)**:
   - `insertSensorFieldAt` was authored in March 2026 with global SQL updates:
     ```java
     // Legacy flaw:
     sql = "UPDATE " + TrackingViewsDbHelper.ROWS_TABLE +
           " SET " + TrackingViewsDbHelper.ROW_NR + "=" + TrackingViewsDbHelper.ROW_NR + "+1" +
           " WHERE " + TrackingViewsDbHelper.ROW_NR + " >= " + rowNr;
     ```
   - Because `TrackingViewsDbHelper.ROWS_TABLE` stores sensor fields for all cockpit tabs in a single flat table discriminated by `VIEW_ID`, this statement shifted rows in **every** tab in the database.

2. **Partial Mitigation in ATT-4055 (`6199b2a4`)**:
   - On 2026-10-18, commit `6199b2a4` (`ATT-4055`: horizontal section dividers) added `WHERE VIEW_ID = tabViewId` to both `UPDATE` branches and to `SECTION_DIVIDERS_TABLE`.
   - However, the following critical defects remain in `insertSensorFieldAt`:
     - **Lack of Atomic Transaction**: The method executes `getDatabase().execSQL(sql)`, `getDatabase().execSQL(shiftDividersSql)`, and `getDatabase().insert(...)` as discrete autocommit statements. Any failure, constraint check, or crash mid-execution permanently shifts existing rows without inserting the new sensor field.
     - **Omission of `normalizeGrid(db, tabViewId)`**: Both `moveSensorField` and `deleteSensorField` strictly execute `normalizeGrid(db, tabViewId)` inside their transactions to ensure gapless $1..N$ numbering. `insertSensorFieldAt` currently omits normalization.
     - **String Concatenation instead of Parameterized Queries**: Values `tabViewId`, `rowNr`, and `colNr` are formatted via raw string concatenation rather than SQLite compiled bind arguments (`?`).
     - **Missing Multi-Tab Isolation Test Suite**: There are no tests in the codebase verifying that mutating one tab leaves other cockpit tabs completely unaffected under real SQLite storage.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Input Validation**: Validate that `tabViewId > 0` and `rowNr >= 1` before performing any database updates; return early if parameters are invalid.
  * **Single-Transaction Boundary**: Wrap the entire operation in a single `beginTransaction() ... try { ... setTransactionSuccessful() } finally { endTransaction() }` block. Ensure `normalizeGrid(SQLiteDatabase db, long tabViewId)` (which does not start a new transaction) is invoked directly inside this block to prevent nested transaction exceptions.
  * **Deterministic Sequence for Row and Divider Shifts**:
    - When `colNr == -1` (new row insertion at `rowNr`):
      1. Shift `ROWS_TABLE`: `ROW_NR = ROW_NR + 1 WHERE VIEW_ID = ? AND ROW_NR >= ?`
      2. Shift `SECTION_DIVIDERS_TABLE`: `ROW_NR = ROW_NR + 1 WHERE VIEW_ID = ? AND ROW_NR >= ?`
      3. Insert new row at `(rowNr, 1)`.
    - When inserting into an existing row at `colNr`:
      1. Shift `ROWS_TABLE`: `COL_NR = COL_NR + 1 WHERE VIEW_ID = ? AND ROW_NR = ? AND COL_NR >= ?`
      2. Insert new field at `(rowNr, colNr)`.
    - In both cases, invoke `normalizeGrid(db, tabViewId)` before committing to guarantee contiguous $1..N$ indices and synchronize section dividers (`REQ-UI-411.2`).
  * **Parameterized Queries**: Replace raw string-interpolated SQL statements with parameterized queries using `Object[]` bind arguments (`?`).
  * **Real SQLite Multi-Tab & Rollback Verification**: Implement an authentic multi-tab test in `TrackingViewsRealSqliteMigrationTest.kt` verifying that:
    1. Inserting fields/rows in Tab 1 leaves Tab 2 completely untouched (`ROWS_TABLE` and `SECTION_DIVIDERS_TABLE`).
    2. Simulated rollback leaves both tables in Tab 1 and Tab 2 completely intact with zero corruption or orphan shifts.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Refactoring the Compose Cockpit UI or `TrackingViewsRepository` data flows.
  * Modifying the schema of `TrackingViews.db` (`ROWS_TABLE`, `VIEWS_TABLE`, or `SECTION_DIVIDERS_TABLE`).
  * Altering default sensor layouts or sport-type view mappings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-200` (*Cockpit Tile Reordering and Coordinate Integrity*), targeting `TrackingViewsDatabaseManager.java`.
* **Historical Origin & Commit Trace**: Commit `85a59b8d` (March 2026, initial sensor field insertion), commit `3042007b` (March 2026, `colNr = 1` fix), and commit `6199b2a4` (ATT-4055, section divider shift).
* **Root Reason for Existing Formulation**: `insertSensorFieldAt` was implemented as a simple helper before complex multi-tab layouts and grid normalization were formalized in `REQ-UI-200`.
* **Preservation of Core Invariants**: Section divider shift logic (`REQ-UI-411.2`), contiguous coordinate invariants (`REQ-UI-200`), and zero-cross-tab side effects must be strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

```
+-----------------------------------------------------------------------------+
|               TrackingViewsDatabaseManager.insertSensorFieldAt              |
|                                                                             |
|  1. Input validation: if (tabViewId <= 0 || rowNr < 1) return;              |
|  2. db.beginTransaction()                                                   |
|  3. If colNr == -1 (New Row):                                               |
|     - Shift ROWS_TABLE: ROW_NR = ROW_NR + 1 WHERE VIEW_ID = ? AND ROW >= ?   |
|       (bindArgs: [tabViewId, rowNr])                                        |
|     - Shift SECTION_DIVIDERS: ROW_NR = ROW_NR + 1 WHERE VIEW_ID = ? ...     |
|       (bindArgs: [tabViewId, rowNr])                                        |
|     - colNr = 1                                                             |
|  4. Else (New Col in Existing Row):                                         |
|     - Shift ROWS_TABLE: COL_NR = COL_NR + 1 WHERE VIEW_ID = ? AND ROW = ?    |
|                         AND COL >= ?                                        |
|       (bindArgs: [tabViewId, rowNr, colNr])                                 |
|  5. db.insert(ROWS_TABLE, null, values)                                     |
|  6. normalizeGrid(db, tabViewId)  // Non-nested, uses active db connection   |
|  7. db.setTransactionSuccessful()                                           |
|  8. db.endTransaction() in finally block                                    |
+-----------------------------------------------------------------------------+
```

### Detailed Design Specifications:
1. **Transaction Strategy & Nested Transaction Prevention**:
   - `insertSensorFieldAt` initiates `db.beginTransaction()`.
   - It directly invokes the private overload `normalizeGrid(SQLiteDatabase db, long tabViewId)`, which operates directly on the provided `db` reference without opening a nested transaction (avoiding `IllegalStateException`).
2. **Divider & Row Synchronization Order**:
   - For new rows (`colNr == -1`), row coordinate shifting in `ROWS_TABLE` is executed first, followed immediately by `SECTION_DIVIDERS_TABLE` row coordinate shifting.
   - The new row is inserted, and `normalizeGrid(db, tabViewId)` guarantees that any section dividers assigned to `rowNr` remain aligned and any orphaned dividers (e.g. at row index $\le 1$) are safely pruned per `REQ-UI-411.2`.
3. **Parameterized DML**:
   - `db.execSQL(sql, new Object[]{tabViewId, rowNr})` ensures type-safe parameterized execution.
4. **Real SQLite Multi-Tab & Rollback Test Harness**:
   - Author `testInsertSensorFieldAt_strictlyIsolatesTabViewAndRollsBackOnError` in `TrackingViewsRealSqliteMigrationTest.kt` verifying multi-tab isolation across both `ROWS_TABLE` and `SECTION_DIVIDERS_TABLE`, and validating dirty-state rollback resilience.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Multi-tab isolation: modifying one tab's sensor grid must have zero effect on any other tab.
  2. Contiguous grid coordinates: all row and column numbers remain contiguous $1..N$ with zero gaps.
  3. Single atomic commit per ticket (Rule 44).
  4. 100% clean-room unit test pass rate.
* **Risk Rating**: **LOW**
  - Self-contained database logic in `TrackingViewsDatabaseManager.java`.
  - Leverages existing, thoroughly-tested `normalizeGrid(db, tabViewId)` overload.
  - Zero UI or networking changes.
  - Fully verifiable via unit and Robolectric SQLite tests.
