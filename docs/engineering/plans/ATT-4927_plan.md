# Stage 3: Implementation Plan - ATT-4927: Sport-Dependent Bar Support, Empty Tile Fallback & Upgrade Parity

**Ticket**: [ATT-4927](https://rainerblind.atlassian.net/browse/ATT-4927)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-046` (`REQ-TRK-046.1`..`REQ-TRK-046.6`)  
**Test Mapping**: `TST-TRK-046` (`TST-TRK-046.1`..`TST-TRK-046.6`)  
**Branch**: `feature/ATT-4927`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Sprint Review 2026-44.1 of [ATT-4841] identified that `isBarSupportedSensorType()` evaluated sensor type independently of layout sport type, allowing invalid bar configurations (e.g. HR in Generic, Power in Run). Bar-only tiles rendered completely blank when no bar was active, and `v15->v16` forced `ShowBar = 1` for all HR/Power fields, ignoring the legacy global `showGauge = false` default. `ATT-4927` unifies sport-dependent bar gating, implements empty tile fallback in `SensorFieldView`, bumps `DB_VERSION` to 18, and repairs existing rows.

---

## 2. Traceability & Requirements Mapping
* **`REQ-TRK-046.1` / `TST-TRK-046.1`**: `TrackingViewsDatabaseManager.DB_VERSION = 18`. Migration v18 re-applies legacy `showGauge` from `SettingsDataStoreJavaHelper.getZoneDisplayOptions` for HR/Power in supported sports, sets Bike Balance/W'Bal to 1, sets unsupported to 0, and enforces `ShowValue = 1` when `ShowBar == 0`. `DatabaseSchemaRegistry` dynamically references `DB_VERSION`.
* **`REQ-TRK-046.2` / `TST-TRK-046.3`**: Canonical `isBarSupported(SensorType, BSportType)`: HR in Run/Bike; Power/Balance/W'Bal in Bike; others false. `EditSensorFieldDialog` hides toggles when unsupported and saves `showValue = true, showBar = false`.
* **`REQ-TRK-046.3` / `TST-TRK-046.4`**: `SensorFieldView` renders value row if `fieldState.showValue || !hasActiveBar`, preventing empty tiles.
* **`REQ-TRK-046.6` / `TST-TRK-046.4`**: `TrackingViewModel` restricts balance and W'Bal bars strictly to `BSportType.BIKE`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Empty Tiles**: Cockpit cards never render blank under any sport or telemetry state.
2. **Upgrade Parity**: Users with previous global `showGauge = false` retain `ShowBar = 0` on upgrade.
3. **Mutual Exclusion**: Athletes cannot deselect both Value and Bar simultaneously when bars are supported.
4. **Code Lock (Rule 4)**: Production source code remains locked until Gate 3 passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **`TrackingViewsDatabaseManager.java`**:
  * Bump `DB_VERSION` from 17 to 18.
  * Define `isBarSupported(SensorType sensorType, BSportType sportType)` and `isBarSupported(SensorType, ActivityType)`.
  * In `addDefaultTab`, evaluate `isBarSupported(rowData.sensorType, activityType)`.
  * Add `oldVersion < 18` migration joining `ROWS_TABLE` with `VIEWS_TABLE` (`ActivityType`), carrying over legacy `showGauge` and sport rules.
* **`DatabaseSchemaRegistry.kt`**:
  * Reference `TrackingViewsDatabaseManager.TrackingViewsDbHelper.DB_VERSION` for `"TrackingViews.db"` and `"FlexibleGenericView.db"`.
* **`EditSensorFieldViewModel.kt` & `EditSensorFieldDialog.kt`**:
  * `EditSensorFieldUiState.isBarSupported` checks `isBarSupported(selectedSensorType, activityType.sportType)`.
  * In `onSensorTypeSelected`, set `showBar = isBarSupported` and `showValue = true`.
  * In `EditSensorFieldDialog`, hide element toggles when `!uiState.isBarSupported`.
* **`TrackingViewModel.kt`**:
  * Restrict `pedalBalanceState` and `wPrimeBatteryState` to `sportType == BSportType.BIKE`.
* **`SensorFieldView.kt`**:
  * `hasActiveBar = fieldState.showBar && (fieldState.zoneGaugeState != null || fieldState.pedalBalanceState != null || fieldState.wPrimeBatteryState != null)`.
  * Render numeric value when `fieldState.showValue || !hasActiveBar`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`TrackingViewsDatabaseManager.java`)**: Add `isBarSupported` methods, bump `DB_VERSION = 18`, and implement `oldVersion < 18` migration.
2. **Step 2 (`DatabaseSchemaRegistry.kt`)**: Map `"TrackingViews.db"` and `"FlexibleGenericView.db"` to `DB_VERSION`.
3. **Step 3 (`EditSensorFieldViewModel.kt` & `EditSensorFieldDialog.kt`)**: Wire sport-dependent `isBarSupported` and toggle hiding.
4. **Step 4 (`TrackingViewModel.kt` & `SensorFieldView.kt`)**: Gate bike bars by sport type and add empty tile fallback.
5. **Step 5 (Unit Tests)**: Update `TrackingViewsDatabaseManagerTest`, `EditSensorFieldViewModelTest`, and `SensorFieldZoneDisplayTest`.
6. **Step 6 (Verification)**: Run targeted test suite and `verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Revert changes on `feature/ATT-4927` via git.
