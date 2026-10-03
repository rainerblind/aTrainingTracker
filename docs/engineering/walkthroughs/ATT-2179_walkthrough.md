# Stage 5 Walkthrough: ATT-2179 - TrackingViewsRepository.fetchSensorFieldConfigs Null-Safe Deserialization & DB Seeding

**Ticket**: [ATT-2179](https://rainerblind.atlassian.net/browse/ATT-2179)  
**Sub-task**: [ATT-2184](https://rainerblind.atlassian.net/browse/ATT-2184) (`[Test]`)  
**Target Release**: `V4.9.39`  
**Branch**: `bugfix/ATT-2179`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Executive Summary & Root Cause Resolution

During Google Play pre-launch and automated Robo testing on fresh installs, a fatal `NullPointerException` crashed the application on cold startup within `TrackingViewsRepository.fetchSensorFieldConfigs`.

### Root Cause Analysis
1. **Omitted Database Seeding**: `TrackingViewsDatabaseManager.addDefaultTab(...)` omitted inserting `VIEW_SIZE` (`values.put(VIEW_SIZE, rowData.viewSize.name())`) when populating default rows on clean database creation (`onCreate`). Consequently, `VIEW_SIZE` was `NULL` for every default row in `LayoutRowsTable`.
2. **NPE on Enum Deserialization**: In Kotlin/Java, `Enum.valueOf(null)` throws `NullPointerException`, *not* `IllegalArgumentException`. `TrackingViewsRepository.kt` invoked `ViewSize.valueOf(sizeString)` inside a `try ... catch (e: IllegalArgumentException)` block. Because `NullPointerException` bypassed the catch block, the app crashed unhandled on startup.

### Delivered Architectural Fix
1. **Repository Defensive Parsing (`TrackingViewsRepository.kt`)**:
   - Introduced generic helper `safeValueOf(name: String?, default: T): T` that explicitly checks for `name == null` and catches `IllegalArgumentException`.
   - Applied `safeValueOf` across `fetchSensorFieldConfigs` and `fetchSingleSensorFieldConfig` for `ViewSize`, `SensorType`, and `FilterType`.
2. **Database Seeding & Migration (`TrackingViewsDatabaseManager.java`)**:
   - In `addDefaultTab(...)`: Explicitly inserted `values.put(VIEW_SIZE, rowData.viewSize.name())`.
   - Incremented database schema `DB_VERSION` from `10` to `11`.
   - In `onUpgrade(...)`: Added migration for `oldVersion < 11` executing `UPDATE LayoutRowsTable SET ViewSize = 'NORMAL' WHERE ViewSize IS NULL;`.
   - In `getAllFilterData(...)`: Added defensive try/catch handling for `FilterType` parsing.

---

## 2. Test Execution & Verification Matrix

| Test Suite / Target | Description | Result |
| :--- | :--- | :--- |
| **`TrackingViewsRepositoryTest`** | Verified `fetchSensorFieldConfigs` and `fetchSingleSensorFieldConfig` handle null `VIEW_SIZE`, `SENSOR_TYPE`, and `FILTER_TYPE` cursor values without throwing `NullPointerException`, resolving to defaults (`NORMAL`, `TIME_ACTIVE`, `INSTANTANEOUS`), and handling invalid enum strings without throwing `IllegalArgumentException`. | **PASS (100%)** |
| **`TrackingViewsDatabaseManagerTest`** | Verified `addDefaultTab` seeds `VIEW_SIZE` in `ContentValues`, schema version 11 upgrade executes repair SQL for null `VIEW_SIZE` rows, and `getAllFilterData` safely handles corrupted enum strings. | **PASS (100%)** |
| **Full Clean-Room Regression Suite** | Executed `./gradlew testDebugUnitTest` across all 1,436 unit tests. | **PASS (100%)** |

---

## 3. Invariants & Backward Compatibility Verification

- **Crash Immunity**: No `NullPointerException` or `IllegalArgumentException` can escape deserialization of `ViewSize`, `SensorType`, or `FilterType` from database cursors. Corrupted or null values gracefully resolve to canonical defaults.
- **Fresh Install Parity**: Fresh database creation seeds valid non-null `VIEW_SIZE` values for all default tabs and rows.
- **Legacy Database Migration**: Existing databases upgraded to schema version 11 repair any legacy null rows, setting `VIEW_SIZE = 'NORMAL'` seamlessly.
