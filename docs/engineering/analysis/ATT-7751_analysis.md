# Stage 1 Analysis: ATT-7751 - [Feature] ATT Fused (Position und Geschwindigkeit): Pairable Smartphone sensor device, priority routing & EditDeviceDialog source guards

**Ticket**: [ATT-7751](https://rainerblind.atlassian.net/browse/ATT-7751)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7751`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
In ATT-131, the mathematical odometry fusion core (`UnicycleOdometryEkf`, `QuaternionGyroProjector`, `WheelCircumferenceCalibrator`) was completed in `com.atrainingtracker.banalservice.odometry`. However, it remains an uncoupled algorithm without an Android device driver, lifecycle management, or UI configuration. Athletes currently choose only between raw GPS and Google Fused. External ANT+/BLE wheel speed revolutions and smartphone IMU gyroscopes are not fused into real-time position, speed, or distance. ATT-7751 bridges this gap by exposing the fusion engine as an opt-in, pairable `Protocol.SMARTPHONE` virtual sensor (*ATT Fused (Position und Geschwindigkeit)* / *ATT Fused (Location & Speed)*), integrating it into `MySensorManager` priority routing, persisting 1-Hz world yaw-rate $\omega_{ENU,z}$ to `WorkoutSamples.db` for post-workout ERTS replay (ATT-7750), and providing educational UI and hardware/pairing source guards in `EditDeviceDialog.kt`.

---

## 2. Root Cause Analysis & Architectural Gap Analysis
* **Independent Peer Review & Verification**:
  - `DeviceType.java` currently defines `SPEED_AND_LOCATION_GPS` and `SPEED_AND_LOCATION_GOOGLE_FUSED`, but lacks `SPEED_AND_LOCATION_ATT_FUSED` and its sensor mappings (`LATITUDE`, `LONGITUDE`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `LINE_DISTANCE_m`, `BEARING`, `ACCURACY`).
  - `DevicesDatabaseManager.java` (DB_VERSION 10) lacks schema migration 11 to seed the new device row with default `PAIRED = 0` (opt-in).
  - `MySensorManager.java` priority arrays omit ATT Fused: `LOCATION_PRIORITY` must place `SPEED_AND_LOCATION_ATT_FUSED` at rank 0, and `SPEED_PRIORITY` must integrate it so fused speed/distance is prioritized when active while cleanly falling back to hardware/GPS sources when unpaired.
  - `DeviceManager.java` only instantiates GPS and Google Fused drivers; it must instantiate, start, and shut down `SpeedAndLocationDevice_ATTFused`.
  - `WorkoutSamples.db` and `TrackerService.java` require 1-Hz world yaw-rate persistence (`SensorType.WORLD_YAW_RATE_radps`) to enable offline backward smoothing in ATT-7750.
  - `EditDeviceDialog.kt` lacks educational explanation and source switches with pairing/hardware state guards (GPS/Fused availability, wheel speed availability, gyroscope hardware presence).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (Device Driver, Routing & UI Guards — ATT-7751, 3 SP)**:
  - Register `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` under `Protocol.SMARTPHONE` with full sensor mappings and 9-language localization parity.
  - Upgrade `DevicesDatabaseManager` schema to DB_VERSION 11 with idempotent migration inserting the device row with `PAIRED = 0`.
  - Implement `SpeedAndLocationDevice_ATTFused` driving `UnicycleOdometryEkf`, `QuaternionGyroProjector`, and `WheelCircumferenceCalibrator`.
  - Integrate `SPEED_AND_LOCATION_ATT_FUSED` into `MySensorManager` (`LOCATION_PRIORITY` rank 0, `SPEED_PRIORITY` rank 0 when active).
  - Add `SensorType.WORLD_YAW_RATE_radps` to log 1-Hz $\omega_{ENU,z}$ in `WorkoutSamples.db`.
  - Implement `EditDeviceDialog.kt` educational card and 4 source toggles guarded by hardware/pairing state.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Offline post-workout ERTS smoothing pass execution (deferred to companion ticket ATT-7750).
  - Dual-wheel FRONT/REAR matrix configuration UI (deferred to ATT-7752).
  - Dual-wheel iTPMS puncture detection (deferred to ATT-7753).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new requirement `REQ-SEN-028` (*Pairable Virtual Sensor ATT Fused (Position und Geschwindigkeit) with Priority Routing and Input Source Guards*), extending `REQ-TRK-024` (*Sensor Priority Hierarchy*) and `REQ-FIL-007`..`010` (*Odometry EKF*). Targets `DeviceType.java`, `SpeedAndLocationDevice_ATTFused.java`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `EditDeviceDialog.kt`, and `TrackerService.java`.
* **Historical Origin & Commit Trace**: `ATT-3961` / `ATT-5491` / `ATT-5492` (sensor priority hierarchy and network excision in `Devices.db` v10) and `ATT-131` (`bc4a4109`, 5-state unicycle EKF core).
* **Root Reason for Existing Formulation**: Prior architecture assumed only single-source location (GPS or Google Fused). Virtual sensor fusion was not previously modularized as an activatable device.
* **Preservation of Core Invariants**: When `SPEED_AND_LOCATION_ATT_FUSED` is unpaired (default), `MySensorManager` priority routing, background battery consumption, and `Devices.db` operate 100% identically to baseline. Independent device-attributed sample columns in `WorkoutSamples.db` remain preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SEN-028` (extending `REQ-TRK-024`, `REQ-FIL-007`..`010`).
* **Affected Architecture Elements**: `DeviceType.java`, `SpeedAndLocationDevice_ATTFused.kt`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `SensorType.java`, `TrackerService.java`, `EditDeviceDialog.kt`, `strings_devices.xml` (9 locales).
* **Verification Impact & Test Surface**: Unit tests `SpeedAndLocationDevice_ATTFusedTest.kt`, `DevicesDatabaseManagerMigrationTest.kt`, `MySensorManagerPriorityTest.kt`, `EditDeviceDialogGuardsTest.kt`. Clean-room test suite verification.
* **Risk Assessment & Invariants**: **LOW-MEDIUM** risk. The virtual sensor is opt-in (`PAIRED = 0`); existing users experience zero behavioral change until explicitly activated in sensor settings.

---

## 6. Architectural Strategy & High-Level Solution
1. **Device Driver (`SpeedAndLocationDevice_ATTFused`)**: Extends `SpeedAndLocationDevice`. Subscribes to `Sensor.TYPE_GYROSCOPE` and `Sensor.TYPE_GAME_ROTATION_VECTOR` via `SensorManager`. Ingests GNSS fixes and wheel revolutions via `MySensorManager` listeners. Feeds inputs into `UnicycleOdometryEkf` and broadcasts fused `LATITUDE`, `LONGITUDE`, `SPEED_mps`, `BEARING`, `DISTANCE_m`, and `WORLD_YAW_RATE_radps`.
2. **Database Migration (`Devices.db` v11)**: Increment `DevicesDbHelper.DB_VERSION` to 11. Add idempotent `migrateAddSpeedAndLocationAttFused(db)` inserting `SPEED_AND_LOCATION_ATT_FUSED` with `PAIRED = 0`.
3. **Priority Routing**: Insert `SPEED_AND_LOCATION_ATT_FUSED` at index 0 in `LOCATION_PRIORITY` and index 0 in `SPEED_PRIORITY` when active.
4. **UI Source Guards (`EditDeviceDialog.kt`)**: Add educational card and 4 toggles: Location source (guarded by GPS/Fused paired state), Wheel sensors (guarded by external speed sensor paired state), Gyro turn support (guarded by hardware sensor presence), and Auto wheel calibration (guarded by wheel sensor paired state).

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default unpaired state (`PAIRED = 0`): zero battery drain when inactive.
  2. Thread safety: single-threaded sensor update dispatching conforming to `SpeedAndLocationDevice`.
  3. 9-language localization parity across all new UI strings.
  4. 100% existing unit tests pass without regression.
* **Risk Rating**: **LOW-MEDIUM** (Standard feature lifecycle, opt-in device prevents regression on existing installations).
