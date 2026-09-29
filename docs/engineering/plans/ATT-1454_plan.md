# Stage 3: Implementation Plan - ATT-1454: [Cockpit] Smartphone Battery Level & Remaining Run Time Sensors (PHONE_BATTERY & BATTERY_REMAINING_TIME)

**Ticket**: [ATT-1454](https://atrainingtracker.atlassian.net/browse/ATT-1454)  
**Sub-task**: [ATT-1561](https://atrainingtracker.atlassian.net/browse/ATT-1561) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1454](https://atrainingtracker.atlassian.net/browse/ATT-1454) (*[Cockpit] Telemetry & Sensor Expansion*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-CON-015`  
**Test Mapping**: `TST-CON-006`  
**Branch**: `feature/ATT-1454`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

Athletes on long endurance workouts (centuries, brevets, multi-day bikepacking trips, and alpine trail runs) need real-time awareness of their smartphone's battery status and an intelligent prediction of remaining operating time under active GPS/recording load. Currently, aTrainingTracker only exposes external peripheral battery levels in device dialogs (`REQ-CON-007`) and provides no cockpit telemetry tiles for the host smartphone's internal battery level (`PHONE_BATTERY`) or estimated remaining time (`BATTERY_REMAINING_TIME`).

This plan details the software construction of:
1. `BatteryDevice` (`MyDevice`) to listen to Android `Intent.ACTION_BATTERY_CHANGED`.
2. Telemetry sensor enums `PHONE_BATTERY` and `BATTERY_REMAINING_TIME` in `SensorType.java`.
3. Dedicated `BatteryRemainingTimeFormatter.java` formatting hours and minutes (`H:MM h`) with graceful fallback (`--:--`, `Lädt` / `Charging`).
4. Internal rolling drain rate engine ($\Delta\% / \Delta t$) tracking battery drain strictly during active (non-paused) recording.
5. Integration into `DeviceType.java`, `DeviceManager.java`, and `ActivityType.java`.
6. Full 9-language localization parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-015` (*Smartphone Battery Level and Estimated Remaining Duration Telemetry Sensors (`PHONE_BATTERY` & `BATTERY_REMAINING_TIME`)*)
* **Test Mapping**: `TST-CON-006` (*Smartphone Battery Level & Remaining Duration Telemetry Unit & Localization Tests*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing sensor pipelines, peripheral sensor battery alerts (`REQ-CON-007`), cockpit rendering layouts, and 1Hz dispatch threading MUST NOT be regressed.
2. **Zero Wake-Locks & Zero Polling Threads**: Battery monitoring uses standard Android broadcast receivers with zero CPU wake-locks and zero background disk I/O.
3. **In-Memory Calculations Only**: Calculations are performed in-memory during 1Hz sensor ticks without database writes.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `BatteryRemainingTimeFormatter.java`
* **Package**: `com.atrainingtracker.banalservice.sensor.formater`
* **Role**: Implements `MyFormatter<Number>`.
* **Behavior**:
  * `value == null` or `value.intValue() == -2`: returns `"--:--"`.
  * `value.intValue() == -1`: returns localized string for charging (`Lädt` / `Charging`).
  * `value.intValue() >= 0`: formats remaining seconds into hours and minutes:
    $\text{hours} = \text{seconds} / 3600$, $\text{minutes} = (\text{seconds} \% 3600) / 60$, formatted as `"%d:%02d h"`.

### Component 2: `SensorType.java`
* **Role**: Defines `PHONE_BATTERY` and `BATTERY_REMAINING_TIME`:
  * `PHONE_BATTERY`: `SensorValueType.INTEGER`, `IntegerFormatter`, unit `R.string.units_percent`.
  * `BATTERY_REMAINING_TIME`: `SensorValueType.INTEGER`, `BatteryRemainingTimeFormatter`, unit `R.string.units_none`.
* **Icons**: Maps both to `R.drawable.ic_battery_full`.

### Component 3: `DeviceType.java` & `DeviceManager.java`
* **`DeviceType.java`**: Adds `BATTERY(SensorType.PHONE_BATTERY, BSportType.UNKNOWN, 1)` and includes it in `getDeviceTypeList()` for `PHONE_BATTERY` and `BATTERY_REMAINING_TIME`.
* **`DeviceManager.java`**: Instantiates `mBatteryDevice = new BatteryDevice(mContext, mSensorManager);` and includes it in `myDeviceList.add(mBatteryDevice);`.

### Component 4: `BatteryDevice.java`
* **Package**: `com.atrainingtracker.banalservice.devices`
* **Role**: Extends `MyDevice` (`DeviceType.BATTERY`).
* **Behavior**:
  * Registers `BroadcastReceiver` for `Intent.ACTION_BATTERY_CHANGED`.
  * Exposes `mPhoneBatterySensor` and `mBatteryRemainingTimeSensor`.
  * Maintains in-memory history of battery samples `(activeDurationSeconds, batteryPercent)` during active workout tracking.
  * Calculates drain rate: $\text{Drain Rate } [\%/h] = \frac{\Delta \text{Battery}}{\Delta \text{Active Time}} \times 3600$.
  * Fallbacks:
    * If charging: sets remaining time to `-1`.
    * If active recording time < 300s (5 min) or battery drop < 1%: sets remaining time to `-2` (`--:--`).
    * If drain rate $\le 0$: sets remaining time to `-2` (`--:--`).
    * Otherwise: sets remaining seconds to $\text{round}\left(\frac{\text{currentPercent}}{\text{drainRate}} \times 3600\right)$.
  * Dispatches updates on 1Hz time event broadcast (`NEW_TIME_EVENT_INTENT`).

### Component 5: `ActivityType.java`
* **Role**: Appends `PHONE_BATTERY` and `BATTERY_REMAINING_TIME` to default sensors list across all activity profiles (`RUN`, `BIKE`, `ALL`, etc.) so athletes can select and configure tiles in cockpit tabs.

### Component 6: 9-Language Localization
* **Files**: `app/src/main/res/values*/strings.xml` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* Keys: `phone_battery`, `phone_battery_short`, `battery_remaining_time`, `battery_remaining_time_short`, `battery_charging`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create Formatter and Vector Assets
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/sensor/formater/BatteryRemainingTimeFormatter.java`
  * `app/src/main/res/drawable/ic_battery_full.xml`

### Step 2: Add 9-Language String Resources
* Files:
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-de/strings.xml`
  * `app/src/main/res/values-es/strings.xml`
  * `app/src/main/res/values-fr/strings.xml`
  * `app/src/main/res/values-it/strings.xml`
  * `app/src/main/res/values-ja/strings.xml`
  * `app/src/main/res/values-nl/strings.xml`
  * `app/src/main/res/values-pl/strings.xml`
  * `app/src/main/res/values-pt/strings.xml`

### Step 3: Extend `SensorType.java` and `DeviceType.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceType.java`

### Step 4: Implement `BatteryDevice.java` and Wire into `DeviceManager.java` & `ActivityType.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/BatteryDevice.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java`
  * `app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java`

### Step 5: Unit Tests Construction
* Files:
  * `app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt`
  * `app/src/test/java/com/atrainingtracker/banalservice/sensor/formater/BatteryRemainingTimeFormatterTest.kt`
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.BatteryDeviceTest" --tests "com.atrainingtracker.banalservice.sensor.formater.BatteryRemainingTimeFormatterTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted unit tests verifying battery broadcast parsing, drain rate calculation, remaining duration projection, fallback dashes, charging detection, and formatting.
  2. Localization parity audit ensuring zero missing strings across all 9 locales.
  3. Full clean-room regression suite: `./gradlew testDebugUnitTest`.
* **Rollback**: Branch isolation (`feature/ATT-1454`) allows immediate checkout and revert without impacting `sprint/2026-40.3` or `develop`.
