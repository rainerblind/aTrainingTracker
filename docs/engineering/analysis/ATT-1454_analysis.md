# Stage 1 Analysis: ATT-1454 - [Feature] [Cockpit] Smartphone Battery Level & Remaining Run Time Sensors (PHONE_BATTERY & BATTERY_REMAINING_TIME)

**Ticket**: [ATT-1454](https://atrainingtracker.atlassian.net/browse/ATT-1454)  
**Sub-task**: [ATT-1559](https://atrainingtracker.atlassian.net/browse/ATT-1559) (`[Analysis]`)  
**Parent Epic**: [ATT-1454](https://atrainingtracker.atlassian.net/browse/ATT-1454) (*[Cockpit] Telemetry & Sensor Expansion*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Branch**: `feature/ATT-1454`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & Motivation

During long endurance athletic activities (e.g. centuries, multi-day bikepacking trips, alpine trail runs, and brevets), managing smartphone battery life is mission-critical for route navigation, athlete safety, continuous GPS track logging, and emergency communication.

Currently, aTrainingTracker provides live telemetry tiles for external connected sensors (Heart Rate, Cycling Speed & Cadence, Cycling Power, Barometric Pressure, Temperature) and displays peripheral battery levels in device management dialogs (`REQ-CON-007`). However, there is no real-time telemetry tile available in the tracking cockpit to monitor the host smartphone's internal battery level (`PHONE_BATTERY`).

Furthermore, raw battery percentages can be deceptive: lithium-ion battery discharge rates fluctuate dramatically depending on display brightness, direct sunlight, cellular network searches in remote areas, ambient temperature, GPS receiver load, and ANT+/BLE peripheral polling. Athletes therefore need an intelligent, smoothed projection of remaining tracking duration (`BATTERY_REMAINING_TIME`) based on actual in-session power consumption.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap Analysis)

An architectural audit of current telemetry and sensor processing in the application reveals four key gaps:

1. **Sensor Pipeline Missing Telemetry Types**:
   - `SensorType.java` defines existing telemetry enums (`SPEED_mps`, `HR`, `CADENCE`, `POWER`, `ALTITUDE`, `TIME_TOTAL`, `TIME_ACTIVE`, etc.). `PHONE_BATTERY` and `BATTERY_REMAINING_TIME` are absent from `SensorType`.
   - `ActivityType.java` declares the list of configurable sensors per activity profile (`RUN`, `BIKE`, `ALL`, etc.). Without entries in `SensorType`, athletes cannot place battery tiles on their cockpit pages.
2. **Device Hardware Representation**:
   - Internal smartphone sensors are represented in `DeviceManager.java`. `ClockDevice` encapsulates time and timer accumulation (`TIME_TOTAL`, `TIME_ACTIVE`, `LAP_NR`, `TIME_LAP`, `TIME_OF_DAY`).
   - A dedicated internal smartphone device abstraction, `BatteryDevice` (`MyDevice`), is required to encapsulate Android framework battery broadcasts (`Intent.ACTION_BATTERY_CHANGED`), track battery drain during active workout recording, and publish `PHONE_BATTERY` and `BATTERY_REMAINING_TIME`.
3. **Drain Rate Calculation & Noise Rejection Pipeline**:
   - Lithium-ion battery percentages report in 1% discrete steps. A single snapshot difference is noisy and unstable.
   - To provide reliable remaining duration projections, the system requires an internal rolling moving average or exponential moving average (EMA) filter:
     - Metric: Drain rate $\Delta\% / \Delta t$ (% per hour) tracked strictly during active (non-paused) workout recording.
     - Formula: $\text{Drain Rate } [\%/h] = \frac{\Delta \text{Battery } [\%]}{\Delta \text{Time } [h]}$
     - Projected Remaining Time: $\text{Remaining Time } [h] = \frac{\text{Current Battery } [\%]}{\text{Drain Rate } [\%/h]}$
     - Fallback / Graceful degradation:
       - Warmup stabilization period: During the first 5 minutes of active recording (or before sufficient battery drop occurs to establish a confident drain slope), display `--:--`.
       - Charging state: When the device is connected to external power (e.g. dynamo hub or power bank mounted on handlebars), `BatteryManager.BATTERY_STATUS_CHARGING` or `BATTERY_STATUS_FULL` is detected; the sensor renders "Lädt" / "Charging" or `--:--`.
       - Zero or negative drain: If battery does not drop or increases while not explicitly reported as charging, fallback to `--:--`.
4. **Presentation & Localization**:
   - `SensorValueResolver` / `SensorFieldState.kt` / `SensorFieldView.kt`: Must render battery level with `%` unit (`IntegerFormatter`) and remaining time in hours and minutes (e.g. `8:45 h` or localized).
   - Must achieve 100% localization parity across all 9 supported application locales (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement base sensor `PHONE_BATTERY` in `SensorType.java` reporting smartphone battery percentage (0–100%).
  * Implement derived sensor `BATTERY_REMAINING_TIME` in `SensorType.java` projecting hours and minutes until depletion based on active workout power consumption.
  * Implement `BatteryDevice.java` extending `MyDevice`, instantiated in `DeviceManager.java`.
  * Compute and maintain internal signal `BATTERY_DRAIN_RATE` (%/h) in memory to drive `BATTERY_REMAINING_TIME`.
  * Provide graceful fallback states: `--:--` during initial stabilization (< 5 min) and "Lädt" / "Charging" (or `--:--`) when connected to external charger.
  * Integrate into `ActivityType.java` and `DeviceType.java` so cockpit tabs can display both sensors.
  * Complete localization across all 9 supported locales for labels, units, and empty/charging states.
  * Unit test suite for battery reading, drain rate estimation, time formatting, and edge cases.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Non-Goal 1: Exposing `BATTERY_DRAIN_RATE` as an independent user-facing tile (retained strictly as an internal calculation signal).
  * Non-Goal 2: Modifying external peripheral sensor battery alerts (`REQ-CON-007` / `BatteryStatusHelper`).
  * Non-Goal 3: Modifying AMOLED battery saver mode display dimming rules (`ATT-1268`).
  * Non-Goal 4: Introducing background wake-locks, polling threads, or disk writes on 1Hz sensor ticks (strict adherence to zero overhead).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement only (REQ-CON-015, REQ-UI-174). No existing requirements modified.
* **Historical Origin & Commit Trace**: N/A (Net-new feature).
* **Root Reason for Existing Formulation**: N/A (Net-new feature).
* **Preservation of Core Invariants**: N/A (Net-new feature).

---

## 5. Architectural Strategy & High-Level Solution

1. **`BatteryDevice` Architecture**:
   - Implements `MyDevice` with `DeviceType.BATTERY` (or mapped internal device).
   - Registers a sticky broadcast receiver for `Intent.ACTION_BATTERY_CHANGED`.
   - Dispatches 1Hz updates to `mPhoneBatterySensor` and `mBatteryRemainingTimeSensor` synchronously with workout tracking.
2. **Drain Rate Calculation Engine**:
   - Maintains a timestamped history of active recording battery levels.
   - Calculates drain slope $\Delta\% / \Delta t$.
   - Projects remaining seconds: $\text{Remaining Seconds} = \frac{\text{Current Battery Level}}{\text{Drain Rate}} \times 3600$.
3. **Formatters**:
   - `PHONE_BATTERY`: `IntegerFormatter` with `R.string.units_percent`.
   - `BATTERY_REMAINING_TIME`: `BatteryRemainingTimeFormatter` supporting `-1` (charging), `null` / `-2` (stabilizing: `--:--`), and formatted `H:MM h` (e.g. `8:45 h`).
4. **Cockpit Integration**:
   - Added to `SensorType` enum with icon `R.drawable.ic_battery_full` (or dedicated vector).
   - Added to `ActivityType` default sensor options for all sport profiles.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing sensors and cockpit telemetry rendering.
  2. Zero wake-locks and zero background battery drain added by the sensor monitoring itself.
  3. Zero disk I/O on 1Hz sensor ticks (in-memory calculations only).
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - All operations are lightweight in-memory computations integrated into established `MyDevice` / `SensorType` architecture.
