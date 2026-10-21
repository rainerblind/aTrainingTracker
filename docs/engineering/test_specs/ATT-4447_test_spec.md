# Stage 2: Requirement & Test Specification - ATT-4447: [Verbesserung] [Low] Remove Torque (Moment) from default Bike Power tab so Pedal Power Balance spans full row width

**Ticket**: [ATT-4447](https://rainerblind.atlassian.net/browse/ATT-4447)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-UI-409` (*Default `BIKE_POWER` Cockpit Tabs (`Standard`, `Power`, `Map`, `Laps`), `W_PRIME_BAL` Sensor Catalog Inclusion, and Live Cockpit Gating by Physiology Model Toggles.*)  
**Test Spec ID**: `TST-UI-372`  
**Branch**: `feature/ATT-4447`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-21  

---

## 1. Requirement Specification (`REQ-UI-409.2`)

### 1.1 Problem Statement & Rationale
In **aTrainingTracker**, the dedicated default **Power** (*Leistung*) Cockpit tab for `ActivityType.BIKE_POWER` was introduced in `ATT-3921` (`REQ-UI-409.2`) and seeded via `TrackingViewsDatabaseManager.TrackingViewsDbHelper.getDefaultPowerRowDataList()`.

In the current implementation, Row 6 of this default Power tab places two sensor cards side by side in two columns:
* **Column 1**: `SensorType.PEDAL_POWER_BALANCE` (`ViewSize.SMALL`, row 6, col 1)
* **Column 2**: `SensorType.TORQUE` (`ViewSize.SMALL`, row 6, col 2)

During live evaluation on physical devices (`bike_power_balance_torque.png`), this side-by-side arrangement produces two layout defects:
1. **Vertical Height Mismatch (Stepped Card Baseline)**: `SensorType.PEDAL_POWER_BALANCE` renders an interactive horizontal bilateral graphic bar (`PedalPowerBalanceBar`) beneath its numeric percentage value. Conversely, `SensorType.TORQUE` renders only numeric text without a graphic element. Because `SensorGridScreen.kt` wraps row tiles in a Compose `Row(modifier = Modifier.height(IntrinsicSize.Min))`, both card boxes expand vertically, but `TORQUE` lacks bottom content, producing an uneven, stepped appearance.
2. **Cramped Bilateral Balance Visualizer**: Confining `PedalPowerBalanceBar` to half the screen width ($\sim 160\text{--}180\,\text{dp}$) cramps the bilateral symmetry bar ($0\% \dots 100\%$), making slight balance shifts ($49/51$ vs. $48/52$) difficult to read at speed.
3. **Cockpit Information Hierarchy**: `SensorType.TORQUE` is a secondary diagnostic metric rarely monitored on the primary cycling power cockpit screen compared to power, cadence, heart rate, and balance.

Removing `TORQUE` from the default seed list allows `PEDAL_POWER_BALANCE` to be the sole occupant of Row 6, automatically spanning 100% width via Compose `weight(1f)` and providing full horizontal breadth for the balance bar while eliminating the stepped card border.

### 1.2 Functional & Architectural Requirements
1. **Default Power Tab Sensor Row 6 Specification (`REQ-UI-409.2`)**:
   - `TrackingViewsDatabaseManager.TrackingViewsDbHelper.getDefaultPowerRowDataList()` SHALL produce a 6-row layout containing exactly 11 `RowData` items.
   - Row 6 SHALL contain solely `new RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1)`.
   - `SensorType.TORQUE` SHALL NOT be included in `getDefaultPowerRowDataList()`.
2. **Sensor Catalog Retention (`REQ-UI-409.1`)**:
   - `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, Context)` SHALL continue to include `SensorType.TORQUE`, ensuring athletes can still manually add Torque to any tab in `ScreenMode.CONFIGURATION`.
3. **Full-Width Compose Row Expansion**:
   - In `SensorGridScreen.kt`, the single tile in Row 6 SHALL receive `Modifier.weight(1f)`, automatically expanding `PEDAL_POWER_BALANCE` and `PedalPowerBalanceBar` to span 100% of the available horizontal row width.
4. **Database Non-Destruction & Scope Bounding**:
   - `DB_VERSION` MUST remain `15`.
   - The system MUST NOT execute any destructive migration or database table update modifying existing user-customized tabs in `TrackingViews.db`.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  Refines `REQ-UI-409` (specifically clause `REQ-UI-409.2`: *4-Tab Default BIKE_POWER Cockpit Architecture & First-Run Seeding*).
  Targets `TrackingViewsDatabaseManager.java` and `BikePowerCockpitDefaultsAndGatingTest.kt`.
* **Historical Origin & Commit Trace**:
  Introduced in commit `a6289096` (`feat(ATT-3921): update BIKE_POWER default cockpit tabs, add W_PRIME_BAL, and gate physiology tiles (REQ-UI-409, TST-UI-372)`), authored by Rainer Blind on Sun Oct 18 2026.
* **Root Reason for Existing Formulation**:
  During Sprint 2026-43.1 (`ATT-3921`), the dedicated **Power** tab was created to showcase all newly supported cycling power metrics. Both `PEDAL_POWER_BALANCE` and `TORQUE` were categorized under `SensorCategory.POWER_AND_PEDALING`. Because they were considered secondary mechanical pedaling metrics, they were grouped together on Row 6 as two `SMALL` tiles side by side (`col 1` and `col 2`) to fit within a 6-row layout. The visual consequence—namely that `PedalPowerBalanceBar` adds significant vertical height and requires horizontal space that `TORQUE` does not—was not apparent until tested with real hardware graphics.
* **Preservation of Core Invariants**:
  Removing `TORQUE` from the *default* seed layout preserves 100% of the underlying telemetry pipelines, ANT+ / BLE power meter drivers, and sensor catalog capabilities. `SensorType.TORQUE` remains fully selectable in `ScreenMode.CONFIGURATION`. The 4-tab cycling architecture (`Standard`, `Power`, `Map`, `Laps`), dynamic physiology gating (`REQ-UI-409.3`), and existing user database rows remain completely intact.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Default Power Tab Seeding)**:
  * *Given* a clean install database creation or a call to `getDefaultPowerRowDataList()`,
  * *When* `getDefaultPowerRowDataList()` is evaluated,
  * *Then* it returns exactly 11 `RowData` items across 6 rows, where Row 6 contains solely `RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1)` and does not contain `SensorType.TORQUE`.
* **Criterion 2 (Sensor Catalog Invariant)**:
  * *Given* `ActivityType.BIKE_POWER`,
  * *When* `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)` is queried,
  * *Then* `SensorType.TORQUE` is present in the returned array alongside `POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `W_PRIME_BAL`, `WORK_KJ`, and `PEDAL_POWER_BALANCE`.
* **Criterion 3 (Tuning Packing Invariant)**:
  * *Given* the 11 default Power tab fields in `ScreenMode.TRACKING`,
  * *When* `packVisibleRowsForTuning` is evaluated with `enableCogganPowerMetrics = true` and `enableCriticalPowerModel = false`,
  * *Then* packed Row 6 (index 4) contains solely `listOf(SensorType.PEDAL_POWER_BALANCE)`.
* **Criterion 4 (No Schema Mutation)**:
  * *Given* an existing user database at `DB_VERSION = 15`,
  * *When* the app initializes,
  * *Then* `DB_VERSION` remains 15 and no destructive DDL/DML modifies existing athlete views.

### 1.5 System Invariants
* `DB_VERSION = 15` remains unchanged.
* Zero data loss or layout corruption for existing athletes.
* `SensorType.TORQUE` remains selectable in the `BIKE_POWER` sensor catalog.
* 100% clean-room test suite pass rate (`./gradlew testDebugUnitTest`).

---

## 2. Test Specification (`TST-UI-372`)

### Test Case 1: `testBikePowerDefaultTabs_standardPowerMapAndLapsLayouts` (`TST-UI-372.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/BikePowerCockpitDefaultsAndGatingTest.kt`
* **Preconditions**: Mock database and context initialized for `TrackingViewsDbHelper`.
* **Action**: Evaluate `dbHelper.defaultPowerRowDataList` and `dbHelper.onCreate(mockDb)`.
* **Expected Result**:
  * `expectedPower` contains exactly 11 items.
  * Row 6 of `expectedPower` terminates with `RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1)`.
  * `expectedPower` contains zero occurrences of `SensorType.TORQUE`.
  * `assertEquals("Power tab layout must match REQ-UI-409.2", expectedPower, powerRows)` passes.
  * Verification of `addDefaultTab` passes with updated `expectedPower`.

### Test Case 2: `testPackVisibleRowsForTuning_isViewOnlyAndRestoresOriginalRowNrColNrOnToggleRoundTrip` (`TST-UI-372.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/BikePowerCockpitDefaultsAndGatingTest.kt`
* **Preconditions**: `powerTabFields` fixture containing 11 fields (field 12 `TORQUE` removed, field 11 `PEDAL_POWER_BALANCE` at row 6, col 1).
* **Action**: Execute `packVisibleRowsForTuning` across:
  1. Default config (`enableCogganPowerMetrics = true, enableCriticalPowerModel = false`)
  2. Both models OFF (`enableCogganPowerMetrics = false, enableCriticalPowerModel = false`)
  3. Both models ON (`enableCogganPowerMetrics = true, enableCriticalPowerModel = true`)
* **Expected Result**:
  * `packedDefault[4].map { it.sensorType }` equals `listOf(SensorType.PEDAL_POWER_BALANCE)`.
  * `packedBothOff[4].map { it.sensorType }` equals `listOf(SensorType.PEDAL_POWER_BALANCE)`.
  * `packedBothOn.flatten().size` equals 11.
  * `packedBothOn[5].map { it.sensorType }` equals `listOf(SensorType.PEDAL_POWER_BALANCE)`.

### Test Case 3: `testBikePowerSensorCatalog_containsWPrimeBalAndAllPowerMetrics` (`TST-UI-372.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/BikePowerCockpitDefaultsAndGatingTest.kt`
* **Preconditions**: `ActivityType.BIKE_POWER`.
* **Action**: Evaluate `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)`.
* **Expected Result**:
  * `assertTrue(bikePowerSensors.contains(SensorType.TORQUE))` passes.
  * `assertTrue(bikePowerSensors.contains(SensorType.PEDAL_POWER_BALANCE))` passes.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-UI-372.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/BikePowerCockpitDefaultsAndGatingTest.kt`
* **Goal**: Verify string presence of `tab_power` across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-372.6` / `REQ-PRO-001`)
* **Scope**: Clean-room regression execution
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full project test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-372.1` | Unit | `ActivityType.getSensorTypeArray` | `REQ-UI-409.1` | Planned (ATT-4447) |
| `TST-UI-372.2` | Unit | `TrackingViewsDbHelper.getDefaultPowerRowDataList` | `REQ-UI-409.2` | Planned (ATT-4447) |
| `TST-UI-372.3` | Unit | `packVisibleRowsForTuning` | `REQ-UI-409.3` | Planned (ATT-4447) |
| `TST-UI-372.4` | Localization | `TranslationParityTest` (`tab_power`) | `REQ-UI-409.4`, `REQ-UI-106` | Planned (ATT-4447) |
| `TST-UI-372.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Planned (ATT-4447) |
