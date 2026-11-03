# Stage 1 Analysis: ATT-5073 - Specialized Altitude Devices: rename 'Luftdruck' to 'Höhe aus Luftdruck' (all 9 languages) and explain how they work (pros & cons) in device details

**Ticket**: [ATT-5073](https://rainerblind.atlassian.net/browse/ATT-5073)  
**Sub-task**: [[ATT-5164]](https://rainerblind.atlassian.net/browse/ATT-5164) (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5073`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
Following the introduction of `ALTITUDE_FROM_POSITION` ("Topografische Höhe (DEM)") in ATT-4859:
1. The smartphone barometric altimeter (`ALTITUDE_FROM_PRESSURE`) remains named simply "Luftdruck" ("Air Pressure") via `R.string.devices_altitude_from_pressure`, creating a naming asymmetry and confusing athletes about what the sensor produces.
2. In the device details bottom sheet (`EditDeviceDialog.kt`), specialized altitude devices (`ALTITUDE_FROM_PRESSURE` and `ALTITUDE_FROM_POSITION`) display no explanation of how they operate or their respective advantages and disadvantages (e.g., barometer smooth relative ascent vs. weather drift; DEM weather immunity vs. grid resolution).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Asymmetric Naming Call-Sites**:
  * `strings_devices.xml` defines `devices_altitude_from_pressure` as "Air Pressure" / "Luftdruck", reflecting raw Android sensor terminology (`Sensor.TYPE_PRESSURE`) rather than its functional role as an altitude provider.
  * In `DevicesDatabaseManager.java:1314`, `AltitudeFromPressureDevice.java:106`, and `DeviceManager.java:327`, default device names are hardcoded to `devices_altitude_from_pressure`.
  * Existing installed rows in `Devices.db` retain unmodified legacy names ("Luftdruck", "Air Pressure") and require an idempotent migration without overriding custom user renames.
* **Missing Educational Context in `EditDeviceDialog.kt`**:
  * `EditDeviceDialog.kt` only renders `EquipmentSection`, `CalibrationSection` (speed/cadence/power), and `PowerMeterSection`.
  * Specialized altitude devices have no dedicated educational card explaining their measurement principles (barometer + reference calibration vs. GPS + DEM grid matching) and trade-offs.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Verified: `strings_devices.xml` across all 9 locales defines raw pressure labels instead of altitude provider labels.
  * Verified: `Devices.db` (`DEVICES` table) stores default device names at initialization; existing users will not see updated names without a database migration.
  * Verified: `EditDeviceDialog.kt` has no conditional branch for `DeviceType.ALTITUDE_FROM_PRESSURE` or `DeviceType.ALTITUDE_FROM_POSITION`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Rename `R.string.devices_altitude_from_pressure` across all 9 supported application locales to "Altitude from Pressure" / "Höhe aus Luftdruck" (and language equivalents).
  2. Implement an idempotent migration in `DevicesDatabaseManager` updating legacy default names in `Devices.db` for `ALTITUDE_FROM_PRESSURE` while strictly preserving custom user renames.
  3. Add an explanatory Material 3 info card in `EditDeviceDialog.kt` for `ALTITUDE_FROM_PRESSURE` and `ALTITUDE_FROM_POSITION` detailing "How it works" and "Pros & Cons" with 100% 9-language translation parity.
* **Out-of-Scope Non-Goals**:
  1. Modifying the underlying barometric formula, Kalman filter, or DEM interpolation algorithms (`AltitudeFromPressureDevice`, `ElevationService`).
  2. Altering geofence calibration logic or *Lieblingsorte* schemas (`KnownLocations.db`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-SEN-025` (*Pairable Position-Based Elevation Sensor*) and `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs & Selection Flows*).
* **Historical Origin & Commit Trace**: `ATT-4859` (Sprint 2026-44.2) established `REQ-SEN-025` for DEM altitude. `ATT-501` (Sprint 2026-40.5) established `REQ-UI-149` for bottom sheet dialogs.
* **Root Reason for Existing Formulation**: "Luftdruck" was inherited from the initial ANT+/BLE sensor stack. `EditDeviceDialog.kt` prioritized hardware calibration (wheel circumference), omitting virtual/smartphone sensor explanations.
* **Preservation of Core Invariants**: Custom device renames preserved; sensor data delivery, calibration dispatch, and 100% test pass rate across all 9 locales remain invariant.

---

## 5. Architectural Strategy & High-Level Solution
* **9-Language String Modernization**: Update `devices_altitude_from_pressure` in all 9 `strings_devices.xml` files. Add new localized strings for the altitude explanation cards (`device_altitude_explanation_how_it_works_pressure`, `device_altitude_explanation_pros_cons_pressure`, etc.).
* **Idempotent DB Migration**: In `DevicesDatabaseManager`, execute a query targeting `DEVICE_TYPE = 'ALTITUDE_FROM_PRESSURE'` where `NAME` matches any known legacy default name, updating to the new string resource.
* **Compose Educational Card**: In `EditDeviceDialog.kt`, render an `OutlinedCard` when `data.deviceType` is an altitude sensor, presenting structured "How it works" and "Pros & Cons" sections styled with standard M3 tokens (`surfaceVariant`, `onSurfaceVariant`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero loss of custom device renames in `Devices.db`.
  2. Full 100% translation parity across all 9 supported locales (`TranslationParityTest`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Localized to UI strings, Compose dialog presentation, and safe idempotent SQLite name migration. Zero impact on sensor fusion mathematics or background tracking stability.
