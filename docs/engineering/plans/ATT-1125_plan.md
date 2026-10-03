# Implementation Plan - ATT-1125: Developer Infrastructure: Android framework test stubbing helpers and sandbox consistency

## 1. Problem Description & Background
In unit tests interacting with Android SQLite databases (e.g. `EquipmentDbHelperRetiredTest`, `WorkoutClusterStatsTest`, `RoutesDatabaseManagerTTLTest`), developers frequently write 40-60 lines of repetitive MockK answer blocks to stub Android framework `android.database.Cursor` instances. These mocks manually emulate `getColumnIndex`, `moveToNext`, `getInt`, `getString`, `isNull`, and row position pointers. This introduces maintenance friction, cognitive load, and subtle discrepancies in mock behaviour across test files.

Furthermore, developer tooling in `tools/jira_util.py` does not automatically attach created sub-tasks to the active sprint or inherit parent fixVersions during sub-task creation, necessitating extra manual steps. Lastly, executing build and test commands in restrictive IDE sandboxes without understanding Gradle Daemon caches (`~/.gradle`, `~/.android`) leads to sandbox permission violations and build failures unless documented clearly.

This ticket introduces:
1. `MockCursorFactory` in `app/src/test/java/com/atrainingtracker/testutil/MockCursorFactory.kt`: A lightweight, reusable test fixture factory to construct high-fidelity mock `Cursor` instances from tabular column and row definitions.
2. Tooling enhancements in `tools/jira_util.py`: Support for `--add-to-sprint` and `--fixversion` auto-inheritance flags for sub-tasks and issues.
3. Living protocol updates in `docs/project_protocol.md` and `.cursorrules` explicitly documenting sandbox filesystem boundaries and Gradle Daemon constraints.

## 2. Traceability & Requirements Mapping
* **REQ-PRO-023**: Android Framework Test Stubbing Helpers & Developer Tooling Consistency
  * *Functional*: `MockCursorFactory` creates mock `Cursor` matching provided column/row data with accurate iteration, indexed/named column resolution, typed getters (`getInt`, `getLong`, `getFloat`, `getDouble`, `getString`, `getBlob`), and `isNull` handling.
  * *Tooling*: `tools/jira_util.py` supports `--add-to-sprint` and `--fixversion` propagation.
  * *Sandbox Documentation*: `docs/project_protocol.md` and `.cursorrules` explicitly detail daemon caching directories and when `BypassSandbox: true` is necessary.
  * *Invariants*: Pure Python standard library for tools, zero changes to `app/src/main/` production code or assets.
* **TST-PRO-016**: Android Test Fixtures & Jira Metadata Auto-Inheritance Verification
  * Verified by `MockCursorFactoryTest.kt`, refactoring demonstration in `EquipmentDbHelperRetiredTest.kt`, unit tests in `tools/test_jira_accounts.py`, and documentation checks.

## 3. System Invariants & Preserved Behavior
1. **Zero APK Runtime Code & Asset Isolation**: Zero modifications to production source files (`app/src/main/...`), assets, resources, or production dependencies.
2. **Zero External Dependencies for Tooling**: All additions to `tools/` use strictly Python 3 standard library modules (`urllib`, `json`, `sys`, `os`, `unittest`).
3. **Inviolable Human Decision Gate**: Gating tools provide deterministic verification, but AI agents are strictly prohibited from moving tickets or sub-tasks to `Erledigt`.
4. **Existing Test Invariance**: Refactoring test files to use `MockCursorFactory` must preserve all existing test assertions, verifying identical behavior with cleaner code.

## 4. Proposed Architectural Changes

### Component 1: Android Test Fixture Factory (`app/src/test/java/com/atrainingtracker/testutil/MockCursorFactory.kt`)
* **Package**: `com.atrainingtracker.testutil` (test scope only)
* **API Specification**:
  ```kotlin
  object MockCursorFactory {
      fun create(
          columns: List<String>,
          rows: List<List<Any?>> = emptyList()
      ): Cursor
  }
  ```
* **Behaviors Emulated via MockK**:
  * `cursor.count`: Returns `rows.size`.
  * `cursor.columnCount`: Returns `columns.size`.
  * `cursor.columnNames`: Returns `columns.toTypedArray()`.
  * `cursor.getColumnNames()`: Returns `columns.toTypedArray()`.
  * `cursor.getColumnIndex(name)`: Case-insensitive (or exact match with fallback) lookup returning 0-based index or -1 if not found.
  * `cursor.getColumnIndexOrThrow(name)`: Returns index or throws `IllegalArgumentException` if not found.
  * `cursor.getColumnName(index)`: Returns column name at index.
  * Position tracking: Internal mutable pointer `position` initialized to -1.
  * `cursor.position`: Returns current row index.
  * `cursor.moveToFirst()`: Sets `position = 0` if `rows.isNotEmpty()`, returns `rows.isNotEmpty()`.
  * `cursor.moveToNext()`: Increments `position`; returns true if `position < rows.size`, else false.
  * `cursor.moveToPrevious()`, `cursor.moveToPosition(pos)`: Updates position with bounds checks.
  * `cursor.isBeforeFirst`: Returns `position < 0`.
  * `cursor.isAfterLast`: Returns `position >= rows.size`.
  * `cursor.isFirst`: Returns `rows.isNotEmpty() && position == 0`.
  * `cursor.isLast`: Returns `rows.isNotEmpty() && position == rows.size - 1`.
  * `cursor.isNull(columnIndex)`: Returns true if current row cell is null.
  * Typed Getters:
    * `getString(col)`: Converts cell to String (or null).
    * `getInt(col)`: Converts cell via `(cell as? Number)?.toInt() ?: cell.toString().toInt()`.
    * `getLong(col)`: Converts cell via `(cell as? Number)?.toLong() ?: cell.toString().toLong()`.
    * `getDouble(col)`: Converts cell via `(cell as? Number)?.toDouble() ?: cell.toString().toDouble()`.
    * `getFloat(col)`: Converts cell via `(cell as? Number)?.toFloat() ?: cell.toString().toFloat()`.
    * `getShort(col)`: Converts cell via `(cell as? Number)?.toShort() ?: cell.toString().toShort()`.
    * `getBlob(col)`: Casts cell to `ByteArray`.
  * `cursor.close()`: Returns `Unit` without error; tracks closed state if needed.

### Component 2: Unit Test Suite (`app/src/test/java/com/atrainingtracker/testutil/MockCursorFactoryTest.kt`)
* Verifies empty cursor (`count == 0`, `moveToFirst() == false`, `isAfterLast == true`).
* Verifies column lookup (`getColumnIndex` and `getColumnIndexOrThrow` with valid and invalid names).
* Verifies multi-row traversal (`moveToNext`, `position`, `isFirst`, `isLast`, boundary conditions).
* Verifies heterogeneous data type extractions (`getInt`, `getLong`, `getFloat`, `getDouble`, `getString`, `getBlob`).
* Verifies `isNull` accuracy on null vs non-null cells.
* Verifies index out of bounds handling.

### Component 3: Integration Demonstration in Existing Test (`app/src/test/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelperRetiredTest.kt`)
* Refactor `testIsEquipmentRetired_returnsTrueWhenColumnIsOne`, `testIsEquipmentRetired_returnsFalseWhenColumnIsZero`, and `testGetEquipmentItems_activeOnly_appendsRetiredClause` to replace manual `mockk<Cursor>` mocks with `MockCursorFactory.create(...)`.
* Verifies test cleanliness and exact assertion fidelity.

### Component 4: Tooling Enhancements in `tools/jira_util.py`
* Extend `create_subtask`:
  * Add parameters `add_to_sprint: bool = False`, `fix_version: Optional[str] = None`.
  * When `add_to_sprint` is true, automatically invoke `add_to_active_sprint(new_key, role=role)` upon creation.
  * When `fix_version` is provided (or when parent has a `fixVersion` and inheritance is requested), attach `fixVersions` to issue creation payload or call `set_fix_version`.
* Extend `create_issue`:
  * Add optional `--add-to-sprint` and `--fixversion` flag handling.
* CLI Argument Parsing:
  * Parse optional flags `--add-to-sprint` and `--fixversion=<VERSION>` across `create-subtask` and `create-issue` commands.
* Unit Test Additions in `tools/test_jira_accounts.py`:
  * Add unit tests verifying `create_subtask` and `create_issue` payload construction with `fixVersions` and automatic sprint association call.

### Component 5: Living Protocol & Sandbox Boundaries Documentation
* Update `docs/project_protocol.md`:
  * Add section on *JVM Unit Test Fixtures & Android Framework Stubbing Guidelines* (encouraging `MockCursorFactory`).
  * Add section on *Sandbox Boundaries & Execution Isolation Constraints*:
    * Document that Gradle Daemons require access to `~/.gradle` and Android SDK / metrics caching requires `~/.android`.
    * Commands invoking `./gradlew` must run with elevated permissions (`BypassSandbox: true`) because the restricted sandbox denies write access outside the project directory.
    * REST API commands requiring external network access to Jira/Gemini must run with `BypassSandbox: true`.
* Update `.cursorrules`:
  * Add guidelines on `MockCursorFactory` usage for Android database testing.
  * Add clear reminders regarding Gradle sandbox access requirements.

## 5. Verification Plan
### Automated Tests
1. Run `tools/test_jira_accounts.py`:
   ```bash
   python3 tools/test_jira_accounts.py
   ```
2. Run unit tests for `MockCursorFactory` and `EquipmentDbHelperRetiredTest`:
   ```bash
   ./gradlew testDebugUnitTest --tests com.atrainingtracker.testutil.MockCursorFactoryTest --tests com.atrainingtracker.trainingtracker.database.EquipmentDbHelperRetiredTest
   ```
3. Run full clean-room Android unit test regression suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### Manual / Integration Verification
1. Inspect git diff against `develop` to verify zero alterations to `app/src/main/`.
2. Test CLI syntax for `jira_util.py create-subtask` and `create-issue` with `--help` or unit mocks.
