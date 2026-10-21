# Stage 3: Implementation Plan - ATT-4447: [Verbesserung] [Low] Remove Torque (Moment) from default Bike Power tab so Pedal Power Balance spans full row width

**Ticket**: [ATT-4447](https://rainerblind.atlassian.net/browse/ATT-4447)  
**Sub-task**: [ATT-4516](https://rainerblind.atlassian.net/browse/ATT-4516) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-UI-409` (`REQ-UI-409.2`)  
**Test Mapping**: `TST-UI-372` (`TST-UI-372.1`, `TST-UI-372.2`, `TST-UI-372.3`, `TST-UI-372.4`, `TST-UI-372.6`)  
**Branch**: `feature/ATT-4447`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Description & Background

In **aTrainingTracker**, the default **Power** (*Leistung*) Cockpit tab for `ActivityType.BIKE_POWER` was introduced in `ATT-3921` (`REQ-UI-409.2`) and seeded via `TrackingViewsDatabaseManager.TrackingViewsDbHelper.getDefaultPowerRowDataList()`.

In the current implementation, Row 6 of this default Power tab places two sensor cards side by side in two columns:
* **Column 1**: `SensorType.PEDAL_POWER_BALANCE` (`ViewSize.SMALL`, row 6, col 1)
* **Column 2**: `SensorType.TORQUE` (`ViewSize.SMALL`, row 6, col 2)

During physical device evaluation (`bike_power_balance_torque.png`), this arrangement was found to suffer from two significant visual defects:
1. **Vertical Stepped Baseline**: `SensorFieldView.kt` renders an interactive horizontal bilateral bar (`PedalPowerBalanceBar`) beneath `PEDAL_POWER_BALANCE`'s numeric percentage. `TORQUE` renders only text without any graphic bar. Inside Compose's `Row(modifier = Modifier.height(IntrinsicSize.Min))`, the row expands to fit the pedal balance bar, leaving the torque card with empty bottom space and creating an uneven, stepped bottom border across the two cards.
2. **Cramped Bilateral Gauge**: The `PedalPowerBalanceBar` illustrates bilateral pedal symmetry ($0\% \dots 100\%$ with a central $50/50$ tick mark). Confined to half the screen width ($\sim 160\text{--}180\,\text{dp}$), subtle balance shifts ($49/51$ vs. $48/52$) are difficult to resolve during high-speed cycling.
3. **Information Hierarchy**: Instantaneous torque ($\text{N}\cdot\text{m}$) is a secondary engineering diagnostic metric rarely monitored on the primary power screen compared to power, cadence, heart rate, and balance.

Removing `TORQUE` from `getDefaultPowerRowDataList()` leaves `PEDAL_POWER_BALANCE` as the sole tile in Row 6, allowing it to automatically span 100% of the row width via `SensorGridScreen.kt`'s `weight(1f)` layout rule, matching the expansive visual presentation of Row 2 (`POWER`, `ViewSize.HUGE`) and Row 5 (`W_PRIME_BAL`, `ViewSize.LARGE`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-409` (*Default `BIKE_POWER` Cockpit Tabs (`Standard`, `Power`, `Map`, `Laps`), `W_PRIME_BAL` Sensor Catalog Inclusion, and Live Cockpit Gating by Physiology Model Toggles.*)
  * `REQ-UI-409.2`: Tab 2 Dedicated Power Tab specification — Row 6 updated to contain solely `PEDAL_POWER_BALANCE` (`SMALL`, col 1), omitting `TORQUE`. Total fields in default Power tab updated from 12 to 11 across 6 rows.
  * `REQ-UI-409.1`: Sensor Catalog Invariant — `ActivityType.BIKE_POWER` continues to include `SensorType.TORQUE` so athletes can still add Torque to any custom tab.
  * `REQ-UI-409.3`: Live-Cockpit Gating & Packing Invariant — `packVisibleRowsForTuning` handles the 11-field layout without coordinate mutation.
* **Test Mapping**: `TST-UI-372` (*Default `BIKE_POWER` Cockpit Tabs, `W_PRIME_BAL` Catalog Inclusion, Live Cockpit Gating & 9-Language Parity Verification Suite*)
  * `TST-UI-372.1`: Verifies `SensorType.TORQUE` remains present in `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)`.
  * `TST-UI-372.2`: Verifies `getDefaultPowerRowDataList()` produces the updated 11-field layout with Row 6 containing only `PEDAL_POWER_BALANCE` (`SMALL`, col 1).
  * `TST-UI-372.3`: Verifies `packVisibleRowsForTuning` packs Row 6 as `listOf(SensorType.PEDAL_POWER_BALANCE)` across default, both-off, and both-on configs without mutating `rowNr` or `colNr`.
  * `TST-UI-372.4`: Verifies `tab_power` string exists across all 9 locales.
  * `TST-UI-372.6`: Full clean-room test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Non-Destructive Database Preservation**: `DB_VERSION = 15` remains unchanged. No SQLite schema bump, `ALTER TABLE`, or destructive DML is executed on existing user databases. Existing athlete tracking tabs remain completely untouched.
2. **Sensor Catalog Retention**: `SensorType.TORQUE` remains registered in `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, Context)`, preserving the athlete's freedom to configure Torque manually in `ScreenMode.CONFIGURATION`.
3. **Telemetry Pipeline Integrity**: ANT+ and Bluetooth LE torque/power/cadence decoding and calculation pipelines remain 100% intact.
4. **Coordinate Immutability**: `packVisibleRowsForTuning` preserves the original `rowNr` and `colNr` coordinates of all fields across toggle round-trips.
5. **Code Lock Invariant (Rule 4)**: No production source code in `app/src/...` is modified until Gate 3 check-gate returns exit code 0 (`GATE_PASSED`).
6. **Parent Human Gate Invariance (Rule 3)**: Parent ticket `ATT-4447` ends strictly at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

```
+---------------------------------------------------------------------------------------------------+
|                                  SWE.2 Component Architecture                                     |
|                                                                                                   |
|  +----------------------------------------------+        +-------------------------------------+  |
|  |           Database / Defaults Layer          |        |        Live Cockpit UI Layer        |  |
|  |                                              |        |                                     |  |
|  |  TrackingViewsDatabaseManager.java           |        |  SensorGridScreen.kt                |  |
|  |  TrackingViewsDbHelper                       |        |  - Row(height = IntrinsicSize.Min)  |  |
|  |  + getDefaultPowerRowDataList(): List<RowData>| -----> |  - fieldsInThisRow.forEach {        |  |
|  |    * Row 1: TIME_ACTIVE, HR, CADENCE (col 1-3)|        |      Box(weight(1f)) { ... }        |  |
|  |    * Row 2: POWER (HUGE, col 1)               |        |    }                                |  |
|  |    * Row 3: W/kg, NP (col 1-2)                |        |    -> Single item receives 100%     |  |
|  |    * Row 4: IF, TSS, kJ (col 1-3)             |        |       width automatically!          |  |
|  |    * Row 5: W'bal (LARGE, col 1)              |        +------------------+------------------+  |
|  |    * Row 6: PEDAL_POWER_BALANCE (col 1 only!)|                           |                     |
|  |      [TORQUE REMOVED from default list]      |                           v                     |
|  +----------------------------------------------+        +-------------------------------------+  |
|                                                          |  SensorFieldView.kt                 |  |
|                                                          |  - Value + Unit centered            |  |
|                                                          |  - PedalPowerBalanceBar (full width)|  |
|                                                          +-------------------------------------+  |
+---------------------------------------------------------------------------------------------------+
```

### 4.1 UI Consistency (Rule 23 — mandatory for UI changes)
* **Reference screen / component**:
  - Existing full-width sensor tiles in `SensorGridScreen.kt`: Row 2 (`POWER`, `ViewSize.HUGE`), Row 5 (`W_PRIME_BAL`, `ViewSize.LARGE`), and standalone `SensorFieldView` cards.
* **Reused components**:
  - `SensorFieldView.kt` (`PedalPowerBalanceBar`)
  - Material 3 `Card`, Compose `Row`, `Box(modifier = Modifier.weight(1f))`
* **Theme tokens**:
  - Elevation: `CardDefaults.cardElevation(defaultElevation = 1.dp)`
  - Shapes: Standard cockpit card shapes (`MaterialTheme.shapes.medium` / `CockpitCardDefaults.shape`)
  - Typography: `LocalCockpitTypography.current`
  - Colors: Standard `MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`, and `PedalPowerBalanceBar` theme colors
* **New one-off styles & justification**: None. Employs standard Compose `weight(1f)` behavior where a single item in a row naturally occupies the entire row width.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Code Gate Check (Rule 4 — Code Lock Verification)
* Run the gate check before editing any production code in `app/src/...`:
  ```bash
  python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>
  ```
* Confirm exit code `0` (`GATE_PASSED`).

### Step 2: Remove `SensorType.TORQUE` from `getDefaultPowerRowDataList()` in `TrackingViewsDatabaseManager.java`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
* **Diff / Changes**:
  ```diff
  @@ -1410,6 +1410,5 @@
               // Row 5: W_PRIME_BAL (LARGE, col 1)
               rowDataList.add(new RowData(SensorType.W_PRIME_BAL, ViewSize.LARGE, 5, 1));
  -            // Row 6: PEDAL_POWER_BALANCE (SMALL, col 1), TORQUE (SMALL, col 2)
  +            // Row 6: PEDAL_POWER_BALANCE (SMALL, col 1) — spans full width for balance bar
               rowDataList.add(new RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1));
  -            rowDataList.add(new RowData(SensorType.TORQUE, ViewSize.SMALL, 6, 2));
               return rowDataList;
  ```

### Step 3: Update Unit Test Expectations in `BikePowerCockpitDefaultsAndGatingTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/BikePowerCockpitDefaultsAndGatingTest.kt`
* **Diff / Changes**:
  1. In `testBikePowerDefaultTabs_standardPowerMapAndLapsLayouts()` (line 164–166):
     ```diff
     @@ -164,3 +164,2 @@
                  RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1)
     -            RowData(SensorType.TORQUE, ViewSize.SMALL, 6, 2)
              )
     ```
  2. In `testPackVisibleRowsForTuning_isViewOnlyAndRestoresOriginalRowNrColNrOnToggleRoundTrip()`:
     - Remove fixture element `createField(12, 6, 2, SensorType.TORQUE, ViewSize.SMALL)` from `powerTabFields` (line 340).
     - Update assertion for `packedDefault[4]` (line 353):
       ```diff
       - assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE, SensorType.TORQUE), packedDefault[4].map { it.sensorType })
       + assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE), packedDefault[4].map { it.sensorType })
       ```
     - Update assertion for `packedBothOff[4]` (line 362):
       ```diff
       - assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE, SensorType.TORQUE), packedBothOff[4].map { it.sensorType })
       + assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE), packedBothOff[4].map { it.sensorType })
       ```
     - Update assertion for `packedBothOn` total count and row 6 (line 369, 373):
       ```diff
       - assertEquals(12, packedBothOn.flatten().size)
       + assertEquals(11, packedBothOn.flatten().size)
       ...
       - assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE, SensorType.TORQUE), packedBothOn[5].map { it.sensorType })
       + assertEquals(listOf(SensorType.PEDAL_POWER_BALANCE), packedBothOn[5].map { it.sensorType })
       ```
     - Update assertion for `packedConfigMode` total count (line 382):
       ```diff
       - assertEquals(12, packedConfigMode.flatten().size)
       + assertEquals(11, packedConfigMode.flatten().size)
       ```

### Step 4: Execute Targeted Unit Tests
* Execute targeted test suites:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.BikePowerCockpitDefaultsAndGatingTest" --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest"
  ```
* Assert 100% pass rate.

### Step 5: Verification & Governance Validation
* Verify requirement governance:
  ```bash
  python3 tools/verify_requirement_governance.py
  ```
* Confirm exit code `0`.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests in Stage 4 (`BikePowerCockpitDefaultsAndGatingTest`, `TrackingViewsDatabaseManagerTest`).
  - Requirement governance script (`tools/verify_requirement_governance.py`).
  - Full clean-room regression test suite in Stage 5 (`./gradlew testDebugUnitTest`).
* **Rollback**:
  - The feature branch is fully isolated (`feature/ATT-4447`). If any unexpected issue arises, the branch can be hard-reset or discarded cleanly via `git checkout sprint/2026-43.4 && git branch -D feature/ATT-4447` without impacting other sprint work.
