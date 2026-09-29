# Stage 2: Requirement & Test Specification - ATT-1454: [Cockpit] Smartphone Battery Level & Remaining Run Time Sensors (PHONE_BATTERY & BATTERY_REMAINING_TIME)

**Ticket**: [ATT-1454](https://atrainingtracker.atlassian.net/browse/ATT-1454)  
**Sub-task**: [ATT-1560](https://atrainingtracker.atlassian.net/browse/ATT-1560) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1454](https://atrainingtracker.atlassian.net/browse/ATT-1454) (*[Cockpit] Telemetry & Sensor Expansion*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-CON-015` (*Smartphone Battery Level and Estimated Remaining Duration Telemetry Sensors (`PHONE_BATTERY` & `BATTERY_REMAINING_TIME`)*)  
**Test Spec ID**: `TST-CON-006`  
**Branch**: `feature/ATT-1454`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-CON-015)

### 1.1 Problem Statement & Rationale
During endurance athletics (centuries, brevets, marathon running, multi-day bikepacking trips, and alpine trail runs), managing smartphone battery life is mission-critical for route navigation, athlete safety, continuous GPS track logging, and emergency communication.
Currently, aTrainingTracker provides telemetry tiles for external connected sensors (Heart Rate, Cycling Speed & Cadence, Cycling Power, Barometric Pressure, Temperature) and displays peripheral battery levels in device management dialogs (`REQ-CON-007`). However, there is no real-time telemetry tile available in the tracking cockpit to monitor the host smartphone's internal battery level (`PHONE_BATTERY`).
Furthermore, raw battery percentages can be deceptive: lithium-ion battery discharge rates fluctuate dramatically depending on display brightness, direct sunlight, cellular network searches in remote areas, ambient temperature, GPS receiver load, and ANT+/BLE peripheral polling. Athletes therefore need an intelligent, smoothed projection of remaining tracking duration (`BATTERY_REMAINING_TIME`) based on actual in-session power consumption.

### 1.2 Functional & Architectural Requirements
1. **Base Smartphone Battery Percentage (`PHONE_BATTERY`)**:
   - The system SHALL register an internal `BatteryDevice` (`MyDevice`) that listens to Android system battery state broadcasts (`Intent.ACTION_BATTERY_CHANGED`).
   - The sensor SHALL report instantaneous state of charge as an integer percentage in the range $0\dots 100\%$ with unit `%` (`IntegerFormatter`).
2. **Derived Remaining Battery Duration (`BATTERY_REMAINING_TIME`)**:
   - The system SHALL compute an internal battery drain rate ($\Delta\% / \Delta t$ in $\%/\text{h}$) strictly over active (non-paused) workout recording duration.
   - The projected remaining duration SHALL be derived via $\text{Remaining Time } [h] = \frac{\text{Current Battery } [\%]}{\text{Drain Rate } [\%/h]}$.
   - The sensor reading SHALL be formatted in hours and minutes (`H:MM h`, e.g. `8:45 h`) via a dedicated formatter (`BatteryRemainingTimeFormatter`).
3. **Graceful Fallback & Charging States**:
   - During the initial workout baseline stabilization period (first 5 minutes of active recording or prior to measuring $\ge 1\%$ battery consumption), the remaining time sensor SHALL display `--:--`.
   - When connected to external power (`BatteryManager.BATTERY_STATUS_CHARGING` or `BATTERY_STATUS_FULL`), the remaining time sensor SHALL display the localized string for charging (`Lädt` / `Charging`) or `--:--` and MUST NOT project false depletion times.
   - If computed drain rate is $\le 0$ without charging status, the sensor SHALL display `--:--`.
4. **Cockpit & Activity Integration**:
   - `PHONE_BATTERY` and `BATTERY_REMAINING_TIME` SHALL be integrated into `SensorType.java` and available for selection across all activity sport types in `ActivityType.java`.
   - 100% localization parity for sensor names, short names, units, and fallback strings SHALL be maintained across all 9 supported locales (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
5. **Efficiency & Lifecycle Invariants**:
   - Sensor evaluation SHALL execute strictly in-memory during 1Hz telemetry updates with zero wake-locks and zero background disk I/O.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Battery Percentage Display)**:
  * *Given* an active workout session with the host smartphone battery at 84%,
  * *When* the athlete views a cockpit tab configured with `PHONE_BATTERY`,
  * *Then* the tile SHALL render `84` with unit `%`.
* **Criterion 2 (Stabilization Fallback)**:
  * *Given* an active workout in its first 3 minutes of recording,
  * *When* the cockpit renders `BATTERY_REMAINING_TIME`,
  * *Then* the tile value SHALL render `--:--`.
* **Criterion 3 (Accurate Remaining Duration Projection)**:
  * *Given* an active workout where 2% battery has drained over 30 minutes of active recording (drain rate = 4.0%/h) and current battery is 80%,
  * *When* `BATTERY_REMAINING_TIME` is resolved,
  * *Then* the tile SHALL display `20:00 h`.
* **Criterion 4 (Charging State Handling)**:
  * *Given* the smartphone is connected to a power bank during tracking,
  * *When* `BATTERY_REMAINING_TIME` is rendered,
  * *Then* the tile SHALL display `Lädt` (in German) or `Charging` (in English).

### 1.4 System Invariants
1. Existing sensor pipelines, peripheral sensor battery alerts (`REQ-CON-007`), cockpit rendering layouts, and 1Hz dispatch threading MUST NOT be regressed.
2. Zero additional wake-locks, polling threads, or disk writes during 1Hz sensor ticks.
3. 9-language localization parity across all supported application locales.

---

## 2. Test Specification (TST-CON-006)

### Test Case 1: Battery Broadcast Parsing & Value Propagation (`TST-CON-006.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt`
* **Preconditions**: `BatteryDevice` initialized with mock/fake context.
* **Action**: Inject mock `Intent(ACTION_BATTERY_CHANGED)` with `EXTRA_LEVEL = 75` and `EXTRA_SCALE = 100`.
* **Expected Result**: `PHONE_BATTERY` sensor value updates to `75`, and `getStringValue()` returns `"75"`.

### Test Case 2: Stabilization Fallback Period (`TST-CON-006.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt`
* **Preconditions**: Tracking active with elapsed time < 300 seconds (5 minutes).
* **Action**: Request `BATTERY_REMAINING_TIME` value.
* **Expected Result**: Formatted string returns `"--:--"`.

### Test Case 3: Drain Rate & Remaining Duration Projection (`TST-CON-006.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt`
* **Preconditions**: Active workout tracking for 3600 seconds (1 hour); battery dropped from 85% to 80% (5%/h drain); current level = 80%.
* **Action**: Compute remaining time projection.
* **Expected Result**: Projected remaining time equals 16 hours (`16 * 3600` seconds); formatted string returns `"16:00 h"`.

### Test Case 4: External Charging State Protection (`TST-CON-006.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt`
* **Preconditions**: Host device reports `BatteryManager.BATTERY_STATUS_CHARGING`.
* **Action**: Evaluate `BATTERY_REMAINING_TIME`.
* **Expected Result**: Remaining time returns special charging code (`-1`), formatted string renders localized charging text or fallback without false depletion projections.

### Test Case 5: Zero / Negative Drain Resilience (`TST-CON-006.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt`
* **Preconditions**: Active workout tracking for 600 seconds with 0% battery drop while not charging.
* **Action**: Evaluate remaining time.
* **Expected Result**: Displays `"--:--"` gracefully without division by zero or infinite duration.

### Test Case 6: 9-Language Localization & Specifier Audit (`TST-CON-006.6`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence for all new string keys across all 9 locales:
  * `phone_battery`, `phone_battery_short`
  * `battery_remaining_time`, `battery_remaining_time_short`
  * `battery_charging`
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 7: Clean-Room Regression Suite (`TST-CON-006.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the entire project test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-006.1` | Unit | `BatteryDevice.onBatteryChanged` | `REQ-CON-015` | Specified |
| `TST-CON-006.2` | Unit | `BatteryDevice.getRemainingTimeSeconds` | `REQ-CON-015` | Specified |
| `TST-CON-006.3` | Unit | `BatteryDevice.getRemainingTimeSeconds` | `REQ-CON-015` | Specified |
| `TST-CON-006.4` | Unit | `BatteryDevice.onBatteryChanged` (charging) | `REQ-CON-015` | Specified |
| `TST-CON-006.5` | Unit | `BatteryDevice.getRemainingTimeSeconds` (zero drain) | `REQ-CON-015` | Specified |
| `TST-CON-006.6` | Localization | `TranslationParityTest` | `REQ-CON-015`, `REQ-UI-106` | Specified |
| `TST-CON-006.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
