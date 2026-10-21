# Stage 1 Analysis: ATT-4447 - [Verbesserung] [Low] Remove Torque (Moment) from default Bike Power tab so Pedal Power Balance spans full row width

**Ticket**: [ATT-4447](https://rainerblind.atlassian.net/browse/ATT-4447)  
**Sub-task**: [ATT-4514](https://rainerblind.atlassian.net/browse/ATT-4514) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Branch**: `feature/ATT-4447`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation

In **aTrainingTracker**, the dedicated default **Power** (*Leistung*) Cockpit tab for `ActivityType.BIKE_POWER` was introduced in `ATT-3921` (`REQ-UI-409.2`) and seeded via `TrackingViewsDatabaseManager.TrackingViewsDbHelper.getDefaultPowerRowDataList()`.

In the current implementation, Row 6 of this default Power tab places two sensor cards side by side in two columns:
* **Column 1**: `SensorType.PEDAL_POWER_BALANCE` (`ViewSize.SMALL`, row 6, col 1)
* **Column 2**: `SensorType.TORQUE` (`ViewSize.SMALL`, row 6, col 2)

### Defect Mechanism & Visual Inspection
During live evaluation of the cycling cockpit on device (`bike_power_balance_torque.png`), two significant layout defects were identified on Row 6:
1. **Vertical Height Mismatch (Stepped Card Border)**:
   In `SensorFieldView.kt`, `SensorType.PEDAL_POWER_BALANCE` renders an interactive horizontal bilateral graphic bar (`PedalPowerBalanceBar` via `REQ-UI-377`) beneath its numeric percentage value. Conversely, `SensorType.TORQUE` renders only a numeric value and label without any graphical bar. Because `SensorGridScreen.kt` wraps row tiles in a Compose `Row(modifier = Modifier.height(IntrinsicSize.Min))`, both card boxes expand vertically, but the content inside `TORQUE` lacks the lower graphic element, producing an unbalanced card appearance and stepped visual baseline.
2. **Cramped Bilateral Balance Visualizer**:
   The `PedalPowerBalanceBar` visually depicts bilateral pedal symmetry ($0\% \dots 100\%$ with a central $50/50$ tick mark). When confined to a half-width card column ($\sim 160\text{--}180\,\text{dp}$ on typical smartphone screens), the horizontal bar is severely compressed. Subtle shifts in pedal balance (e.g. $49/51$ vs. $48/52$) are difficult for the cyclist to perceive at a glance while riding at speed.
3. **Cockpit Information Hierarchy**:
   `SensorType.TORQUE` (instantaneous torque in Newton-meters, $\text{N}\cdot\text{m}$) is a secondary engineering diagnostic metric rarely monitored on the primary power screen compared to power, cadence, heart rate, and balance. Occupying prime real estate next to pedal balance degrades the readability of the balance bar.

### Expected Behavior
`SensorType.TORQUE` is removed from `getDefaultPowerRowDataList()`. Row 6 contains solely `SensorType.PEDAL_POWER_BALANCE` (`ViewSize.SMALL`, row 6, col 1). By virtue of `SensorGridScreen.kt`'s `Modifier.weight(1f)` per tile, `PEDAL_POWER_BALANCE` automatically spans the full width of the row, matching the expansive presentation of Row 2 (`POWER`, `ViewSize.HUGE`) and Row 5 (`W_PRIME_BAL`, `ViewSize.LARGE`), giving the bilateral balance bar full horizontal breadth from Left to Right and eliminating the mismatched card height.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Codebase Audit of `TrackingViewsDatabaseManager.java`
Inspection of `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java` reveals lines 1395–1416:
```java
@NonNull
public List<RowData> getDefaultPowerRowDataList() {
    List<RowData> rowDataList = new LinkedList<>();
    // Row 1: TIME_ACTIVE (SMALL, col 1), HR (SMALL, col 2), CADENCE (SMALL, col 3)
    rowDataList.add(new RowData(SensorType.TIME_ACTIVE, ViewSize.SMALL, 1, 1));
    rowDataList.add(new RowData(SensorType.HR, ViewSize.SMALL, 1, 2));
    rowDataList.add(new RowData(SensorType.CADENCE, ViewSize.SMALL, 1, 3));
    // Row 2: POWER (HUGE, col 1)
    rowDataList.add(new RowData(SensorType.POWER, ViewSize.HUGE, 2, 1));
    // Row 3: POWER_TO_WEIGHT (LARGE, col 1), NORMALIZED_POWER (LARGE, col 2)
    rowDataList.add(new RowData(SensorType.POWER_TO_WEIGHT, ViewSize.LARGE, 3, 1));
    rowDataList.add(new RowData(SensorType.NORMALIZED_POWER, ViewSize.LARGE, 3, 2));
    // Row 4: INTENSITY_FACTOR (NORMAL, col 1), TSS (NORMAL, col 2), WORK_KJ (NORMAL, col 3)
    rowDataList.add(new RowData(SensorType.INTENSITY_FACTOR, ViewSize.NORMAL, 4, 1));
    rowDataList.add(new RowData(SensorType.TSS, ViewSize.NORMAL, 4, 2));
    rowDataList.add(new RowData(SensorType.WORK_KJ, ViewSize.NORMAL, 4, 3));
    // Row 5: W_PRIME_BAL (LARGE, col 1)
    rowDataList.add(new RowData(SensorType.W_PRIME_BAL, ViewSize.LARGE, 5, 1));
    // Row 6: PEDAL_POWER_BALANCE (SMALL, col 1), TORQUE (SMALL, col 2)
    rowDataList.add(new RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1));
    rowDataList.add(new RowData(SensorType.TORQUE, ViewSize.SMALL, 6, 2));
    return rowDataList;
}
```

### 2.2 Layout Expansion Mechanism in `SensorGridScreen.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt` (lines 718–732):
```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.height(IntrinsicSize.Min),
    horizontalArrangement = if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Start
) {
    fieldsInThisRow.forEach { fieldState ->
        Box(modifier = Modifier.weight(1f)) {
            SensorFieldView(...)
        }
    }
}
```
* When `fieldsInThisRow` has two elements (`PEDAL_POWER_BALANCE` at `col 1`, `TORQUE` at `col 2`), each receives `weight(1f)`, splitting the row width $50\% / 50\%$.
* When `fieldsInThisRow` has only one element (`PEDAL_POWER_BALANCE` at `col 1`), that single child receives `weight(1f)` across the entire row, automatically expanding to $100\%$ width.
* In `SensorFieldView.kt` (lines 317–324):
  ```kotlin
  if (fieldState.pedalBalanceState != null) {
      Spacer(modifier = Modifier.height(4.dp))
      PedalPowerBalanceBar(
          state = fieldState.pedalBalanceState,
          modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 4.dp)
      )
  }
  ```
  The `PedalPowerBalanceBar` fills the available width of the card. In a full-width card, it spans the entire screen, resolving both the height mismatch (since it is the only card on that row) and the horizontal cramping.

### 2.3 Database Migration Evaluation
* `DB_VERSION` in `TrackingViewsDatabaseManager.java` is currently `15`.
* Calling `getDefaultPowerRowDataList()` occurs in:
  1. `onCreate(SQLiteDatabase db)` (line 1006): Seeds default views on clean app installs.
  2. `ensurePowerTabExistsForBikePower(SQLiteDatabase db)` (line 1065): Non-destructively inserts the default Power tab if the athlete does not already have one.
  3. User action: Resetting or adding a new default tab (`Insert After -> Default -> Power`).
* **Chesterton's Fence & Scope Bounding**: We **MUST NOT** introduce an SQLite migration (`onUpgrade`) that mutates existing user databases. Existing athletes may have customized their layouts, swapped tiles, or intentionally positioned Torque. Modifying existing database rows would destroy user preferences. Only the default seed list `getDefaultPowerRowDataList()` is modified.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Remove `new RowData(SensorType.TORQUE, ViewSize.SMALL, 6, 2)` from `getDefaultPowerRowDataList()` in `TrackingViewsDatabaseManager.java`.
  2. Ensure Row 6 of the default Power tab contains exclusively `new RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1)`, spanning full row width.
  3. Update unit test expectations in `BikePowerCockpitDefaultsAndGatingTest.kt` (`testBikePowerDefaultTabs_standardPowerMapAndLapsLayouts` and `testPackVisibleRowsForTuning_isViewOnlyAndRestoresOriginalRowNrColNrOnToggleRoundTrip`).
  4. Synchronize living requirements documentation in `docs/requirements.md` (`REQ-UI-409.2`) and `docs/tests.md` (`TST-UI-372`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. **No SQLite Schema Bump or Destructive Migration**: Do not increment `DB_VERSION` (retaining `DB_VERSION = 15`) and do not alter existing user-customized tabs in SQLite.
  2. **Sensor Catalog Retention**: Do not remove `SensorType.TORQUE` from `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, ...)`. Torque remains available in the catalog for any user wishing to add it manually in `ScreenMode.CONFIGURATION`.
  3. **No Changes to Other Tabs or Sports**: Do not touch `Standard`, `Map`, or `Laps` tabs, nor any other `ActivityType`.
  4. **No Modification to Pedal Balance Component**: Do not alter `PedalPowerBalanceBar.kt`, `PedalPowerBalanceMath.kt`, or `SensorFieldView.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  Refines `REQ-UI-409` (specifically clause `REQ-UI-409.2`: *4-Tab Default BIKE_POWER Cockpit Architecture & First-Run Seeding*).
  Targets `TrackingViewsDatabaseManager.java` and `BikePowerCockpitDefaultsAndGatingTest.kt`.

* **Historical Origin & Commit Trace**:
  Introduced in commit `a6289096` (`feat(ATT-3921): update BIKE_POWER default cockpit tabs, add W_PRIME_BAL, and gate physiology tiles (REQ-UI-409, TST-UI-372)`), authored by Rainer Blind on Sun Oct 18 2026.

* **Root Reason for Existing Formulation (Why was this fence built?)**:
  During Sprint 2026-43.1 (`ATT-3921`), the dedicated **Power** tab was created to showcase all newly supported cycling power metrics. Both `PEDAL_POWER_BALANCE` and `TORQUE` were categorized under `SensorCategory.POWER_AND_PEDALING`. Because they were considered secondary mechanical pedaling metrics, they were grouped together on Row 6 as two `SMALL` tiles side by side (`col 1` and `col 2`) to fit within a 6-row layout. The visual consequence—namely that `PedalPowerBalanceBar` adds significant vertical height and requires horizontal space that `TORQUE` does not—was not apparent until tested with real hardware graphics.

* **Preservation of Core Invariants (Why is it safe to alter now?)**:
  Removing `TORQUE` from the *default* seed layout preserves 100% of the underlying telemetry pipelines, ANT+ / BLE power meter drivers, and sensor catalog capabilities. `SensorType.TORQUE` remains fully selectable in `ScreenMode.CONFIGURATION`. The 4-tab cycling architecture (`Standard`, `Power`, `Map`, `Laps`), dynamic physiology gating (`REQ-UI-409.3`), and existing user database rows remain completely intact.

---

## 5. Architectural Strategy & High-Level Solution

1. **`TrackingViewsDatabaseManager.java`**:
   - In `getDefaultPowerRowDataList()`:
     - Remove `rowDataList.add(new RowData(SensorType.TORQUE, ViewSize.SMALL, 6, 2));`.
     - Update inline comment to: `// Row 6: PEDAL_POWER_BALANCE (SMALL, col 1) — spans full width for balance bar`.
     - Row count remains 6 rows, but total fields in the default Power tab drops from 12 to 11.

2. **`BikePowerCockpitDefaultsAndGatingTest.kt`**:
   - In `testBikePowerDefaultTabs_standardPowerMapAndLapsLayouts()`:
     - Update `expectedPower` to end with `RowData(SensorType.PEDAL_POWER_BALANCE, ViewSize.SMALL, 6, 1)`.
   - In `testPackVisibleRowsForTuning_isViewOnlyAndRestoresOriginalRowNrColNrOnToggleRoundTrip()`:
     - Remove field 12 (`TORQUE`) from the `powerTabFields` fixture.
     - Update assertions for packed rows (total size becomes 11; Row 6 mapping becomes `listOf(SensorType.PEDAL_POWER_BALANCE)`).

3. **`docs/requirements.md` & `docs/tests.md`**:
   - Update `REQ-UI-409.2` description of Tab 2: `Row 6: PEDAL_POWER_BALANCE (SMALL, col 1)`.
   - Update `TST-UI-372.2` description to match.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`./gradlew testDebugUnitTest`).
  2. Zero data loss or layout corruption for existing athletes (no schema bump, no destructive migration).
  3. `SensorType.TORQUE` remains present in `ActivityType.BIKE_POWER` sensor catalog (`REQ-UI-409.1`).
  4. Parent ticket Human Decision Gate remains strictly enforced (`Rule 3`).

* **Risk Rating**: **LOW**
  - **Technical Justification**: The modification is strictly localized to a single collection insertion in a static factory method (`getDefaultPowerRowDataList()`) and its associated unit test expectations. No database schema changes, concurrency pathways, or Android lifecycle mechanisms are touched.
