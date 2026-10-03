# Stage 2 Requirement & Test Specification: ATT-2179 - [Bug] TrackingViewsRepository.fetchSensorFieldConfigs NullPointerException

**Ticket**: [ATT-2179](https://rainerblind.atlassian.net/browse/ATT-2179)  
**Sub-task**: [ATT-2181](https://rainerblind.atlassian.net/browse/ATT-2181) (`[Req & Test Spec]`)  
**Parent (Epic)**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashes*)  
**Target Release**: `V4.9.39`  
**Branch**: `bugfix/ATT-2179`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Formal Requirements

### REQ-UI-253: Tracking Views Null-Safe Enum Deserialization, Fresh-Install Seeding & Database Repair Migration
The system SHALL ensure fault-tolerant deserialization of tracking view configurations and robust database initialization/migration (ATT-2179):
1. *Defensive Enum Deserialization (`TrackingViewsRepository.kt`)*:
   - When reading sensor field configurations from SQLite (`ROWS_TABLE`), the system SHALL guard against null, empty, or invalid enum values.
   - `ViewSize` SHALL default to `ViewSize.NORMAL` if the column value is null or unparseable.
   - `SensorType` SHALL default to `SensorType.TIME_ACTIVE` if the column value is null or unparseable.
   - `FilterType` SHALL default to `FilterType.INSTANTANEOUS` if the column value is null or unparseable.
   - The deserialization logic MUST catch `NullPointerException` and `IllegalArgumentException` (e.g., via explicit null check and try/catch), ensuring `Enum.valueOf(null)` is never executed and application startup never crashes on fresh installs or corrupted rows.
2. *Default Cockpit View Seeding (`TrackingViewsDatabaseManager.java`)*:
   - In `addDefaultTab(...)`, the system SHALL persist `values.put(VIEW_SIZE, rowData.viewSize.name())` for each seeded sensor field row into `ROWS_TABLE`, guaranteeing that fresh installations populate rows with non-null `VIEW_SIZE` values matching the template.
3. *Database Upgrade & Repair Migration (Version 11)*:
   - `TrackingViewsDbHelper.DB_VERSION` SHALL be incremented from `10` to `11`.
   - In `onUpgrade` for `oldVersion < 11`, the system SHALL execute `UPDATE LayoutRowsTable SET ViewSize = 'NORMAL' WHERE ViewSize IS NULL;`, repairing any pre-existing databases created on previous versions where `VIEW_SIZE` was null.
4. *Defensive Filter Parsing in `getAllFilterData`*:
   - In `TrackingViewsDatabaseManager.getAllFilterData`, `SensorType` and `FilterType` SHALL be parsed defensively with null checks and fallback to `SensorType.TIME_ACTIVE` and `FilterType.INSTANTANEOUS`.
5. *Preserved Invariants*:
   - Existing valid user cockpit customizations, font sizing hierarchies (`REQ-UI-181`), pick & place reordering (`REQ-UI-200`), and SQLite schema structure MUST NOT be broken.

---

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-181` (*Ultra-Large Cockpit Typography Extensions*) Clause 4, and `REQ-PRO-005` (*Database Schema Integrity*).
2. *Historical Origin & Commit Trace*: `REQ-UI-181` was introduced in `ATT-1431` (Sprint 2026-40.3). `REQ-PRO-005` is an architectural invariant.
3. *Root Reason for Existing Formulation*: `REQ-UI-181` explicitly specified fallback to `ViewSize.NORMAL`. However, the implementation caught only `IllegalArgumentException`, while in Java/Kotlin `Enum.valueOf(null)` throws `NullPointerException`. Concurrently, `addDefaultTab` omitted `VIEW_SIZE` during `onCreate` seeding.
4. *Preservation of Core Invariants*: Full typography scales, tile coordinate systems, filter configs, and SQLite table schema remain 100% strictly intact.

---

### Acceptance Criteria (Given-When-Then)

* **AC-1 (Null Enum Fallback)**:
  * *Given* a SQLite cursor row where `VIEW_SIZE`, `SENSOR_TYPE`, and/or `FILTER_TYPE` are `null`,
  * *When* `TrackingViewsRepository.fetchSensorFieldConfigs` or `fetchSingleSensorFieldConfig` deserializes the row,
  * *Then* it SHALL NOT throw `NullPointerException`, and SHALL resolve defaults: `ViewSize.NORMAL`, `SensorType.TIME_ACTIVE`, and `FilterType.INSTANTANEOUS`.

* **AC-2 (Corrupted/Invalid Enum Fallback)**:
  * *Given* a SQLite cursor row where `VIEW_SIZE = "INVALID_SIZE"`, `SENSOR_TYPE = "UNKNOWN"`, and `FILTER_TYPE = "GARBAGE"`,
  * *When* deserializing the row,
  * *Then* it SHALL NOT throw `IllegalArgumentException`, and SHALL resolve defaults: `ViewSize.NORMAL`, `SensorType.TIME_ACTIVE`, and `FilterType.INSTANTANEOUS`.

* **AC-3 (Fresh-Install Seeding Parity)**:
  * *Given* a clean application install triggering `TrackingViewsDbHelper.onCreate`,
  * *When* default tabs and sensor field rows are inserted via `addDefaultTab`,
  * *Then* each inserted row in `ROWS_TABLE` SHALL have `VIEW_SIZE` populated with `rowData.viewSize.name()`, matching the template definition.

* **AC-4 (Version 11 Migration Repair)**:
  * *Given* a database upgrading from version 10 (or lower) containing rows with `VIEW_SIZE IS NULL`,
  * *When* `TrackingViewsDbHelper.onUpgrade` executes with `newVersion = 11`,
  * *Then* an `UPDATE` query SHALL execute setting `VIEW_SIZE = 'NORMAL'` for all rows where `VIEW_SIZE IS NULL`.

---

## 2. Test Specification

### TST-UI-212: Tracking Views Null-Safe Deserialization & DB Seeding Verification

* **Component**: `TrackingViewsRepository.kt`, `TrackingViewsDatabaseManager.java`
* **Test Type**: Unit Test & Regression Suite
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`

#### Test Procedure & Cases:
1. **`fetchSensorFieldConfigs_handlesNullEnumValuesWithoutCrashing`**:
   - Mock SQLite cursor returning nulls for `VIEW_SIZE`, `SENSOR_TYPE`, `FILTER_TYPE`.
   - Invoke `repository.getSensorFieldConfigsForView(1L).first()`.
   - Assert zero exception thrown, `viewSize == ViewSize.NORMAL`, `sensorType == SensorType.TIME_ACTIVE`, `filterType == FilterType.INSTANTANEOUS`.
2. **`fetchSensorFieldConfigs_handlesInvalidEnumValuesGracefully`**:
   - Mock SQLite cursor returning unknown string constants.
   - Assert graceful fallback to defaults without throwing `IllegalArgumentException`.
3. **`fetchSingleSensorFieldConfig_handlesNullEnumValues`**:
   - Verify single field fetch with null enums resolves to defaults.
4. **`addDefaultTab_insertsViewSize`**:
   - Verify `TrackingViewsDbHelper.addDefaultTab` writes `VIEW_SIZE` to `ContentValues`.
5. **`onUpgrade_migratesToVersion11_repairsNullViewSize`**:
   - Verify upgrade from 10 to 11 executes repair SQL for null `VIEW_SIZE`.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Test Implementation Status |
| :--- | :--- | :--- | :--- |
| **REQ-UI-253** | Null-safe enum parsing in `TrackingViewsRepository` | `TST-UI-212` | Specified |
| **REQ-UI-253** | Fresh-install `VIEW_SIZE` seeding in `TrackingViewsDatabaseManager` | `TST-UI-212` | Specified |
| **REQ-UI-253** | DB Version 11 upgrade repair migration | `TST-UI-212` | Specified |
| **REQ-UI-253** | Defensive enum parsing in `getAllFilterData` | `TST-UI-212` | Specified |
