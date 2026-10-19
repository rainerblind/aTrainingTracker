# Stage 2: Requirement & Test Specification - ATT-4010: insertSensorFieldAt shifts sensor fields in all cockpit tabs

**Ticket**: [ATT-4010](https://rainerblind.atlassian.net/browse/ATT-4010)  
**Sub-task**: [ATT-4217](https://rainerblind.atlassian.net/browse/ATT-4217) (`[Req & Test Spec]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-413` (*Cockpit Sensor Field Insertion Transaction Atomicity, Multi-Tab Isolation, and Grid Normalization*)  
**Test Spec ID**: `TST-UI-376` (*Cockpit Sensor Field Insertion Atomicity, Tab Isolation, Divider Synchronization, and Rollback Verification Suite*)  
**Branch**: `feature/ATT-4010`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-UI-413)

### 1.1 Problem Statement & Rationale
`TrackingViewsDatabaseManager.insertSensorFieldAt` is responsible for inserting new sensor metric tiles into cockpit tabs and opening space by shifting existing rows down or columns right. Historically, these shift statements lacked `WHERE VIEW_ID = ?` predicates, causing global coordinate distortion across all cockpit tabs. While `VIEW_ID = tabViewId` string filters were recently added in commit `6199b2a4` (`ATT-4055`), the operation remains non-transactional (vulnerable to torn state and corrupted grids on crash or failure), omits post-insertion `normalizeGrid` calls (allowing gap creation), and relies on raw string-interpolated SQL statements instead of compiled parameterized queries.

### 1.2 Functional & Architectural Requirements

1. **Input & Coordinate Validation (`REQ-UI-413.1`)**:
   - `insertSensorFieldAt` SHALL validate that `tabViewId > 0` and `rowNr >= 1`.
   - If either parameter is non-positive or invalid, the method SHALL log an error and return immediately without executing any database mutations.
   - For out-of-bounds row insertion where `rowNr > currentMaxRow + 1`, the insertion SHALL be accepted, placing the new field at `rowNr`, and subsequently compacted to contiguous coordinate `currentMaxRow + 1` by post-insertion grid normalization (`REQ-UI-413.5`), guaranteeing that gaps in row coordinates are never permanently persisted.

2. **Single-Transaction Atomicity (`REQ-UI-413.2`)**:
   - All coordinate shifts, divider adjustments, and record insertions in `insertSensorFieldAt` SHALL execute within a single atomic SQLite transaction (`db.beginTransaction() ... try { ... db.setTransactionSuccessful(); } finally { db.endTransaction(); }`).
   - If an error, constraint violation, or crash occurs at any point during execution, the transaction SHALL be rolled back completely, guaranteeing that `ROWS_TABLE` and `SECTION_DIVIDERS_TABLE` remain in their exact pre-call state.

3. **Multi-Tab Isolation & Parameterized DML (`REQ-UI-413.3`)**:
   - All `UPDATE` statements shifting row numbers or column numbers SHALL strictly filter by `WHERE VIEW_ID = ?` using parameterized `Object[]` bind arguments matching `tabViewId`.
   - Shifting rows or columns in Tab A SHALL NEVER affect, renumber, or mutate any row or divider record in Tab B, Tab C, or any other configured cockpit tab.

4. **Deterministic Sequence & Divider Synchronization (`REQ-UI-413.4`)**:
   - When inserting a new row (`colNr == -1`), `ROWS_TABLE` rows $\ge \text{rowNr}$ SHALL be shifted down by 1 first, followed by shifting `SECTION_DIVIDERS_TABLE` records $\ge \text{rowNr}$ down by 1 (`UPDATE SectionDividersTable SET RowNr = RowNr + 1 WHERE ViewId = ? AND RowNr >= ?`).
   - If a section divider's row coordinate is shifted, it SHALL retain its relative position between rows. Subsequent grid normalization SHALL ensure that any divider residing at `RowNr <= 1` or orphaned by layout changes is safely purged, while valid dividers remain bound to their row boundaries ($\ge 2$).
   - The new sensor field SHALL be inserted at `(rowNr, 1)`.
   - When inserting into an existing row (`colNr \ge 1`), columns $\ge \text{colNr}$ at `rowNr` SHALL be shifted right by 1, and the new field inserted at `(rowNr, colNr)`.

5. **Post-Insertion Grid Normalization (`REQ-UI-413.5`)**:
   - Before committing the transaction, `insertSensorFieldAt` SHALL invoke the internal transaction-participating overload `normalizeGrid(SQLiteDatabase db, long tabViewId)`.
   - This ensures all sensor fields in `tabViewId` occupy contiguous $1..N$ coordinates, healing any gapped states (e.g. rows 1, 3, 5 -> 1, 2, 3), and that all section dividers remain synchronized without creating nested transaction deadlocks.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement `REQ-UI-413`, extending `REQ-UI-200` (*Sensor Grid Configuration & Drag-and-Drop / Pick & Place*) and `REQ-UI-411.2` (*Section Divider Row Shift Invariants*). Targets `TrackingViewsDatabaseManager.java`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-4010`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). `insertSensorFieldAt` was authored in March 2026 (commit `5a8d102e`) when the application supported only a single global layout view. Commit `6199b2a4` (`ATT-4055`) introduced horizontal section dividers and added `WHERE VIEW_ID = tabViewId` string filters to mitigate cross-tab leakage, but left the method non-transactional and un-normalized.
3. **Root Reason for Existing Formulation**: The original logic operated directly on SQLite without transaction wrapping because initial single-tab layouts were simple and rarely edited concurrently. String interpolation was used for simplicity before security/safety linting was applied across the DAL. Grid normalization was omitted because callers were expected to supply contiguous indices.
4. **Preservation of Core Invariants**: Multi-tab isolation across independent cockpits, contiguous $1..N$ coordinates with zero gaps, section divider alignment between row boundaries ($\ge 2$), passive `onOpen()` behavior, and 100% clean-room test suite pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Multi-Tab Isolation)**:
  * *Given* two distinct cockpit tabs (Tab 1 and Tab 2), each containing sensor fields and section dividers,
  * *When* `insertSensorFieldAt` is called on Tab 1 with `colNr = -1` (new row),
  * *Then* rows and dividers in Tab 1 are shifted down and the new field is inserted,
  * *And* all rows and section dividers in Tab 2 remain 100% unchanged in coordinate position, order, and count.

* **Criterion 2 (Transaction Rollback & Integrity)**:
  * *Given* a cockpit tab with existing sensor fields and section dividers,
  * *When* an insertion fails mid-operation (e.g. invalid constraints or failure before `setTransactionSuccessful`),
  * *Then* the transaction rolls back completely,
  * *And* zero rows or dividers in `ROWS_TABLE` or `SECTION_DIVIDERS_TABLE` remain shifted or orphaned.

* **Criterion 3 (Contiguous Grid Normalization & Gapped Healing)**:
  * *Given* a cockpit tab with gapped sensor fields (e.g. rows 1, 3, 5),
  * *When* a new sensor field is inserted,
  * *Then* the resulting sensor grid for that tab contains strictly contiguous $1..N$ row and column coordinates (1, 2, 3, 4) with zero gaps, and section dividers re-map accurately.

* **Criterion 4 (Input & Boundary Validation)**:
  * *Given* invalid parameters (`tabViewId <= 0` or `rowNr < 1`),
  * *When* `insertSensorFieldAt` is invoked,
  * *Then* the method returns immediately and zero database writes are dispatched.
  * *Given* an out-of-bounds row index (`rowNr = 999`),
  * *When* `insertSensorFieldAt` is invoked,
  * *Then* the field is inserted and compacted cleanly to `currentMaxRow + 1` by `normalizeGrid`.

### 1.5 System Invariants

1. **Passive `onOpen()`**: `onOpen()` in database helpers remains strictly passive with zero mutations.
2. **Contiguous Coordinates**: Sensor grid coordinates strictly adhere to $1..N$ row and column numbering.
3. **Divider Alignment (`REQ-UI-411.2`)**: Section dividers remain positioned at valid row boundaries ($\ge 2$).
4. **Single Atomic Commit per Ticket (Rule 44)**.
5. **100% Clean-Room Test Pass Rate**.

---

## 2. Test Specification (TST-UI-376)

### Test Case 1: Multi-Tab Real SQLite Isolation Test (`TST-UI-376.1`)
* **Scope**: Integration Test (Real SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsRealSqliteMigrationTest.kt`
* **Preconditions**: Real SQLite database initialized with Tab 1 (`viewId = 1`) and Tab 2 (`viewId = 2`), each populated with multiple rows in `ROWS_TABLE` and records in `SECTION_DIVIDERS_TABLE`.
* **Action**: Invoke `insertSensorFieldAt(tabViewId = 1, rowNr = 1, colNr = -1, ...)` (new row in Tab 1).
* **Expected Result**:
  - Tab 1 existing rows and dividers shift down by 1.
  - Tab 2 existing rows and dividers retain their exact original `ROW_NR` and `COL_NR` values.

### Test Case 2: Transaction Rollback Resilience Test (`TST-UI-376.2`)
* **Scope**: Integration Test (Real SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsRealSqliteMigrationTest.kt`
* **Preconditions**: Real SQLite database with Tab 1 having 3 sensor rows and 1 section divider.
* **Action**: Simulate a failure/exception mid-transaction during `insertSensorFieldAt`.
* **Expected Result**:
  - Entire transaction rolls back.
  - No rows or dividers in Tab 1 remain shifted.
  - Table state matches pre-execution snapshot exactly.

### Test Case 3: Contiguous Coordinate Normalization & Gapped Grid Healing Test (`TST-UI-376.3`)
* **Scope**: Integration Test (Real SQLite) & Unit Test
* **Target Files**: `TrackingViewsRealSqliteMigrationTest.kt`, `TrackingViewsDatabaseManagerTest.kt`
* **Preconditions**: Tab 1 initialized with fragmented row coordinates (e.g. rows 1, 3, 5) and an existing section divider at row 3.
* **Action**: Invoke `insertSensorFieldAt` for new column and new row.
* **Expected Result**: `normalizeGrid(db, tabViewId)` is invoked on the active transaction database connection, asserting rows compact contiguously to 1, 2, 3 and section dividers re-map accurately.

### Test Case 4: Parameter, Boundary & Out-of-Bounds Row Validation Test (`TST-UI-376.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
* **Preconditions**: Mock database attached.
* **Action**:
  - Invoke `insertSensorFieldAt` with `tabViewId = 0`, `tabViewId = -1`, and `rowNr = 0`.
  - Invoke `insertSensorFieldAt` with out-of-bounds row `rowNr = 999`.
* **Expected Result**:
  - Non-positive parameters return immediately with zero database transactions (`mockDb.beginTransaction()` invoked 0 times).
  - Out-of-bounds `rowNr = 999` is processed and compacted by `normalizeGrid`.

### Test Case 5: 9-Language Localization & Specifier Audit (`TST-UI-376.5`)
* **Scope**: Localization Parity Test
* **Goal**: Confirm zero string resources modified or added.
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-376.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all project unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-376.1` | Integration (Real SQLite) | `TrackingViewsDatabaseManager.insertSensorFieldAt` | `REQ-UI-413.3`, `REQ-UI-413.4` | Specified |
| `TST-UI-376.2` | Integration (Real SQLite) | `TrackingViewsDatabaseManager.insertSensorFieldAt` | `REQ-UI-413.2` | Specified |
| `TST-UI-376.3` | Integration / Unit | `TrackingViewsDatabaseManager.insertSensorFieldAt` | `REQ-UI-413.5` | Specified |
| `TST-UI-376.4` | Unit (MockK) | `TrackingViewsDatabaseManager.insertSensorFieldAt` | `REQ-UI-413.1` | Specified |
| `TST-UI-376.5` | Localization | Git diff audit of `res/values*/strings.xml` | `REQ-UI-413` | Specified |
| `TST-UI-376.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
