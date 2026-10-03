# Stage 5 Verification Walkthrough: ATT-1454 Smartphone Battery Level & Remaining Run Time Sensors

**Ticket**: [ATT-1454](https://atrainingtracker.atlassian.net/browse/ATT-1454)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) / Cockpit Telemetry  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Requirement Mapping**: `REQ-CON-015` (*Smartphone Battery Level and Estimated Remaining Duration Telemetry Sensors (`PHONE_BATTERY` & `BATTERY_REMAINING_TIME`)*)  
**Test Mapping**: `TST-CON-006`  
**Branch**: `feature/ATT-1454`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Executive Summary

During long endurance outdoor activities (e.g. 5–10 hour cycling or trail running sessions with continuous GPS tracking, ANT+/BLE peripheral logging, and active display), athletes risk unexpected device shutdown if the smartphone's internal battery depletes unnoticed. Prior to ATT-1454, the BANAL telemetry cockpit only exposed peripheral sensor battery statuses (e.g. chest heart rate straps, power meters via `REQ-CON-007`), leaving host phone battery state invisible on custom cockpit dashboard tabs.

Under **ATT-1454**, we engineered two first-class cockpit telemetry sensors:
1. `PHONE_BATTERY` (`SensorType.PHONE_BATTERY`): Reports instantaneous smartphone battery state of charge ($0\dots 100\%$) backed by Android system battery state broadcasts (`Intent.ACTION_BATTERY_CHANGED`).
2. `BATTERY_REMAINING_TIME` (`SensorType.BATTERY_REMAINING_TIME`): Derives operating endurance by continuously tracking battery drain ($\Delta\% / \Delta t$) strictly during active workout recording (ignoring pause periods) across a 30-minute rolling window, projecting remaining duration in hours and minutes (`H:MM h`, e.g. `16:00 h`).

Comprehensive unit tests and clean-room regression tests pass with a 100% pass rate. 9-language localization parity (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) is fully established.

---

## 2. Requirements & Traceability Mapping

| Artifact / Requirement | Implementation Details | Status |
| :--- | :--- | :--- |
| **REQ-CON-015 (Point 1)** | `BatteryDevice.java` registers receiver for `Intent.ACTION_BATTERY_CHANGED` and emits 0-100% integer via `PHONE_BATTERY` sensor with `%` unit. | **Verified** |
| **REQ-CON-015 (Point 2)** | `BatteryDevice.java` tracks active workout seconds on 1Hz tick, computes drain rate $\Delta\% / \Delta t$, and calculates remaining seconds ($H = \text{level} / \text{drainRate}$). Formatted via `BatteryRemainingTimeFormatter.java` as `H:MM h`. | **Verified** |
| **REQ-CON-015 (Point 3)** | Graceful stabilization fallback (`--:--`) during first 5 minutes (<300s) or prior to $\ge 1\%$ drop; charging detection (`-1` -> `Lädt` / `Charging`); $\le 0$ drain rate fallback (`--:--`). | **Verified** |
| **REQ-CON-015 (Point 4)** | Mapped into `SensorType.java` and added to all default sport profiles in `ActivityType.java`. 100% localization parity across 9 languages. | **Verified** |
| **REQ-CON-015 (Point 5)** | Pure in-memory calculation on 1Hz tick, zero wakelocks, zero disk I/O, zero network calls. | **Verified** |
| **TST-CON-006** | Unit tests in `BatteryRemainingTimeFormatterTest.kt` (4 tests) and `BatteryDeviceTest.kt` (6 tests). | **Verified** |
| **Clean-Room Regression** | Entire test suite (`./gradlew testDebugUnitTest`). | **Passed (100%)** |

---

## 3. Modified Components & Architectural Changes

1. **`BatteryRemainingTimeFormatter.java` (`SWE.2`)**:
   - Implements `MyFormatter<Number>`. Formats duration in seconds into `"%d:%02d h"` (e.g. `16:00 h`).
   - Handles special status codes: `CHARGING_STATUS_CODE = -1` (resolves localized `R.string.battery_charging` string) and `STABILIZING_STATUS_CODE = -2` (returns `"--:--"`).
2. **`BatteryDevice.java` (`SWE.2`)**:
   - Extends `MyDevice` (`DeviceType.BATTERY`).
   - Listens to Android system `Intent.ACTION_BATTERY_CHANGED` sticky broadcasts and BANAL 1Hz `NEW_TIME_EVENT_INTENT`.
   - Tracks active recording seconds (`mActiveRecordingSeconds`) strictly during active, non-paused tracking.
   - Maintains rolling sample history `(activeSeconds, batteryPercent)` pruned to a 30-minute window while guaranteeing at least 2 baseline samples are retained.
   - Clears sample history and resets tracking seconds on workout accumulator reset (`onAccumulatorsReset()`).
3. **`DeviceType.java` & `DeviceManager.java` (`SWE.2`)**:
   - Registered `DeviceType.BATTERY`.
   - Instantiated and registered `BatteryDevice` in `DeviceManager` device registry.
4. **`SensorType.java` & `ActivityType.java` (`SWE.2`)**:
   - Added `PHONE_BATTERY` and `BATTERY_REMAINING_TIME` to `SensorType` with icon `ic_battery_full`.
   - Appended both sensors to default sensor configurations in `ActivityType` for all sports.
5. **Localization & Vector Assets**:
   - Created `ic_battery_full.xml`.
   - Added `phone_battery`, `phone_battery_short`, `battery_remaining_time`, `battery_remaining_time_short`, and `battery_charging` to strings.xml for `en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`.
6. **Unit Tests (`SWE.4`)**:
   - `BatteryRemainingTimeFormatterTest.kt`: 4 unit tests.
   - `BatteryDeviceTest.kt`: 6 unit tests with MockK for Context and SensorManager.

---

## 4. Verification Evidence & Test Execution

### Targeted Unit Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.BatteryDeviceTest" --tests "com.atrainingtracker.banalservice.sensor.formater.BatteryRemainingTimeFormatterTest"
```
**Result**: BUILD SUCCESSFUL. 10/10 tests passed (0 failures, 0 errors, 0 skipped).

### Clean-Room Regression Test Suite
```bash
./gradlew testDebugUnitTest
```
**Result**: Clean-room regression executed without errors.

---

## 5. Chesterton's Fence & Invariant Compliance

- **Peripheral Battery Alert Isolation**: Peripheral battery alerts (`REQ-CON-007`) listen strictly to BLE/ANT+ peripheral device battery characteristics; internal `BatteryDevice` does not emit peripheral alert broadcasts.
- **Background Energy Conservation**: Battery status is received via system event broadcasts; remaining time calculation runs synchronously inside existing 1Hz timer callback with $O(1)$ deque pruning operations. Zero threads, zero wake locks, zero battery drain overhead introduced.
