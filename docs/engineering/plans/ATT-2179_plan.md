# Stage 3 Implementation Plan: ATT-2179 - [Bug] TrackingViewsRepository.fetchSensorFieldConfigs NullPointerException

**Ticket**: [ATT-2179](https://rainerblind.atlassian.net/browse/ATT-2179)  
**Sub-task**: [ATT-2182](https://rainerblind.atlassian.net/browse/ATT-2182) (`[Impl-Plan]`)  
**Parent (Epic)**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashes*)  
**Target Release**: `V4.9.39`  
**Branch**: `bugfix/ATT-2179`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architecture & Technical Strategy (SWE.2)

To fulfill `REQ-UI-253` and eliminate startup crashes on fresh installations or database inconsistencies, we implement a comprehensive two-tier defense:

1. **Defensive Runtime Deserialization Layer (`TrackingViewsRepository.kt`)**:
   - Provide an inline generic helper `safeValueOf(name: String?, default: T): T` that explicitly checks `name == null` (returning `default`) and wraps `java.lang.Enum.valueOf(T::class.java, name)` in a try/catch block handling `IllegalArgumentException`.
   - Apply `safeValueOf` across `fetchSensorFieldConfigs` and `fetchSingleSensorFieldConfig` for:
     - `ViewSize`: fallback `ViewSize.NORMAL`
     - `SensorType`: fallback `SensorType.TIME_ACTIVE`
     - `FilterType`: fallback `FilterType.INSTANTANEOUS`
2. **Defensive Seeding & Migration Layer (`TrackingViewsDatabaseManager.java`)**:
   - In `TrackingViewsDbHelper.addDefaultTab(...)`: Insert `values.put(VIEW_SIZE, rowData.viewSize.name())` inside the row insertion loop, ensuring that `ROWS_TABLE` rows are always populated with non-null `VIEW_SIZE` values upon initial database creation.
   - Upgrade database version from `10` to `11`:
     - In `onUpgrade(oldVersion < 11)`: Execute `UPDATE LayoutRowsTable SET ViewSize = 'NORMAL' WHERE ViewSize IS NULL;`, repairing any existing databases from versions 8–10 that missed `VIEW_SIZE` values.
   - In `getAllFilterData`: Apply defensive try/catch parsing for `SensorType` and `FilterType`.

---

## 2. Step-by-Step Atomic Implementation Steps

### Step 1: Implement Defensive Enum Parsing in `TrackingViewsRepository.kt`
- Add private helper method:
  ```kotlin
  private inline fun <reified T : Enum<T>> safeValueOf(name: String?, default: T): T {
      if (name == null) return default
      return try {
          java.lang.Enum.valueOf(T::class.java, name)
      } catch (e: IllegalArgumentException) {
          default
      }
  }
  ```
- In `fetchSensorFieldConfigs`:
  - Replace `ViewSize.valueOf(sizeString)` with `safeValueOf(c.getString(viewSizeIndex), ViewSize.NORMAL)`.
  - Replace `SensorType.valueOf(...)` with `safeValueOf(c.getString(sensorTypeIndex), SensorType.TIME_ACTIVE)`.
  - Replace `FilterType.valueOf(...)` with `safeValueOf(c.getString(filterTypeIndex), FilterType.INSTANTANEOUS)`.
- In `fetchSingleSensorFieldConfig`:
  - Replace `ViewSize.valueOf(...)` with `safeValueOf(sizeString, ViewSize.NORMAL)`.
  - Replace `SensorType.valueOf(...)` with `safeValueOf(sensorTypeString, SensorType.TIME_ACTIVE)`.
  - Replace `FilterType.valueOf(...)` with `safeValueOf(filterTypeString, FilterType.INSTANTANEOUS)`.

### Step 2: Fix Default Row Seeding & Version 11 Migration in `TrackingViewsDatabaseManager.java`
- In `TrackingViewsDbHelper.addDefaultTab(...)`:
  - Add `values.put(VIEW_SIZE, rowData.viewSize.name());` into the insertion loop.
- In `TrackingViewsDbHelper`:
  - Increment `DB_VERSION` from `10` to `11`.
  - In `onUpgrade`, add check for `oldVersion < 11`:
    ```java
    if (oldVersion < 11) {
        Log.i(TAG, "Upgrading database to version 11: repairing null VIEW_SIZE values");
        try {
            db.execSQL("UPDATE " + ROWS_TABLE + " SET " + VIEW_SIZE + " = 'NORMAL' WHERE " + VIEW_SIZE + " IS NULL;");
        } catch (Exception e) {
            Log.e(TAG, "Failed to repair null VIEW_SIZE", e);
        }
    }
    ```
- In `getAllFilterData`:
  - Parse `SensorType` and `FilterType` with null-checks and `try/catch (IllegalArgumentException e)`.

### Step 3: Implement Unit Tests in `TrackingViewsRepositoryTest.kt` & `TrackingViewsDatabaseManagerTest.kt`
- Create `TrackingViewsRepositoryTest.kt`:
  - Test `fetchSensorFieldConfigs_handlesNullEnumValuesWithoutCrashing`: assert `ViewSize.NORMAL`, `SensorType.TIME_ACTIVE`, `FilterType.INSTANTANEOUS` when cursor returns null.
  - Test `fetchSensorFieldConfigs_handlesInvalidEnumValuesGracefully`: assert defaults when cursor returns invalid strings.
  - Test `fetchSingleSensorFieldConfig_handlesNullEnumValues`: assert single-field fetch resilience.
- In `TrackingViewsDatabaseManagerTest.kt`:
  - Add test verifying `addDefaultTab` puts `VIEW_SIZE` in `ContentValues`.
  - Add test verifying upgrade to version 11 triggers repair SQL.

### Step 4: Verification & Clean-Room Regression Run
- Run targeted test suites:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest"`
- Run full suite:
  - `./gradlew testDebugUnitTest`

---

## 3. Invariants & Rollback Safety

- **Zero Breaking Schema Changes**: Column names, data types, and primary/foreign keys in SQLite remain strictly identical.
- **Custom Tile Stability**: Athlete-configured tile sizes and layouts are completely preserved.
- **Fail-Safe Startup Guarantee**: `TrackingViewsRepository` will never crash application initialization due to corrupt or uninitialized SQLite records.
