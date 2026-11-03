# Stage 3: Implementation Plan - ATT-4861: User-Configurable Horizontal Width Ratios

**Ticket**: [ATT-4861](https://rainerblind.atlassian.net/browse/ATT-4861)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-UI-437` (`REQ-UI-437.1`..`REQ-UI-437.5`)  
**Test Mapping**: `TST-UI-437` (`TST-UI-437.1`..`TST-UI-437.5`)  
**Branch**: `feature/ATT-4861`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
In `SensorGridScreen.kt`, side-by-side cockpit sensor fields are hardcoded to `Modifier.weight(1f)`. This plan introduces user-configurable horizontal width ratios (e.g. `3/4 : 1/4`, `2/3 : 1/3`, `1/2 : 1/2`, `1/3 : 2/3`, `1/4 : 3/4`), SQLite migration v16 $\rightarrow$ v17 with default `1.0` backfill, Edit dialog presets, and Jetpack Compose proportional weight rendering.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-437.1` / `TST-UI-437.1`**: SQLite schema v17 (`ColWeight real DEFAULT 1.0`), upgrade backfill, and `DatabaseSchemaRegistry.kt` parity.
* **`REQ-UI-437.2` / `TST-UI-437.2`**: `SensorFieldConfig` / `SensorFieldState` DTO and cursor mapping symmetry (Rule 20).
* **`REQ-UI-437.3` / `TST-UI-437.3`**: Relative weight (1x, 2x, 3x) and 2-field row split presets in `EditSensorFieldDialog` / `EditSensorFieldViewModel`.
* **`REQ-UI-437.4` / `TST-UI-437.4`**: `Modifier.weight(effectiveWeight)` in `SensorGridScreen.kt` with non-zero clamp ($> 0f$).
* **`REQ-UI-437.5` / `TST-UI-437.5`**: 9-language localization parity across all supported locales.

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: Default `ColWeight = 1.0` guarantees existing 1:1 and 1:1:1 grid layouts remain visually identical.
2. **Compose Safety Invariant**: Layout engine guards `effectiveWeight = if (colWeight > 0f) colWeight else 1.0f` to prevent crashes.
3. **Single Source of Truth**: Database schema version 17 registered synchronously in `DatabaseSchemaRegistry.kt`.
4. **Thread Safety**: Database DDL/DML on `Dispatchers.IO`, UI updates on `Dispatchers.Main`.

---

## 4. Proposed Architectural Changes
* **Database (`TrackingViewsDatabaseManager.java`)**:
  * Increment `DB_VERSION = 17`. Define `COL_WEIGHT = "ColWeight"`.
  * Update `CREATE_LAYOUTS_TABLE_V17` and `onUpgrade` (v16 $\rightarrow$ v17) with column creation and `DEFAULT 1.0` backfill.
  * Overload `RowData` and `updateSensorView` to support `colWeight`, add `updateColWeight`.
* **Schema Registry & Repository (`DatabaseSchemaRegistry.kt`, `TrackingViewsRepository.kt`)**:
  * Set version 17 for `TrackingViews.db` and `FlexibleGenericView.db`.
  * Add `colWeight: Float = 1.0f` to `SensorFieldConfig` and map symmetrically in cursors.
* **UI & Configuration (`EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, `SensorGridScreen.kt`)**:
  * Expose relative weights and quick 2-field row split presets in `EditSensorFieldDialog`.
  * Replace hardcoded `Modifier.weight(1f)` with `Modifier.weight(effectiveWeight)` in `SensorGridContent` and config mode.

### UI Consistency (Rule 23)
* **Reference component**: `EditSensorFieldDialog.kt` Display Elements Section (ATT-4841).
* **Reused components**: Material 3 `FilterChip`, `Text`, `Row`, `Spacer`.
* **Theme tokens**: `MaterialTheme.typography.labelLarge`, `MaterialTheme.colorScheme.onSurfaceVariant`, `8.dp` / `16.dp` spacing.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Schema & DB)**: Update `TrackingViewsDatabaseManager.java` (`DB_VERSION = 17`, `ColWeight`, `onUpgrade`, `updateSensorView`) and `DatabaseSchemaRegistry.kt` (version 17).
2. **Step 2 (Repository & State)**: Add `colWeight` to `SensorFieldConfig`, `SensorFieldState`, and map symmetrically in `TrackingViewsRepository.kt` and `TrackingViewModel.kt`.
3. **Step 3 (Edit Dialog & ViewModel)**: Implement row split presets and individual weight selection in `EditSensorFieldViewModel.kt` and `EditSensorFieldDialog.kt`.
4. **Step 4 (Cockpit Compose Grid)**: Apply `Modifier.weight(effectiveWeight)` with non-zero clamp in `SensorGridScreen.kt` (lines 740 and 1058).
5. **Step 5 (Localization Parity)**: Add string resources for ratio section and presets across 9 locale `strings.xml` files.
6. **Step 6 (Targeted Unit Tests)**: Execute tests verifying migration, DTO symmetry, preset logic, layout weights, and localization.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsRealSqliteMigrationTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Atomic git branch isolation on `feature/ATT-4861` enables clean revert without affecting `sprint/2026-44.2`.
