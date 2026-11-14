# Stage 3: Implementation Plan - ATT-7751: [Feature] ATT Fused (Position und Geschwindigkeit)

**Ticket**: [ATT-7751](https://rainerblind.atlassian.net/browse/ATT-7751)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7751`  
**Requirement Mapping**: `REQ-SEN-028` (*Pairable Virtual Sensor ATT Fused with Priority Routing & UI Guards*)  
**Test Mapping**: `TST-SEN-028`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
ATT-131 implemented the core odometry fusion mathematics in `com.atrainingtracker.banalservice.odometry`. ATT-7751 integrates this fusion engine into the Android runtime as a pairable `Protocol.SMARTPHONE` virtual device (`DeviceType.SPEED_AND_LOCATION_ATT_FUSED`). It integrates into `MySensorManager` priority routing, persists 1-Hz world yaw-rate $\omega_{ENU,z}$ to `WorkoutSamples.db` for post-workout ERTS replay (ATT-7750), and exposes educational explanations and guarded source toggles in `EditDeviceDialog.kt` with 9-language localization parity.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SEN-028` (`REQ-SEN-028.1`..`REQ-SEN-028.4`)
* **Test Mapping**: `TST-SEN-028` (`TST-SEN-028.1`..`TST-SEN-028.7`)

---

## 3. System Invariants & Preserved Behavior
1. **Unpaired by Default (`PAIRED = 0`)**: Fresh installs and DB v11 upgrades leave ATT Fused disabled until explicitly activated by the user; zero gyro battery drain when inactive.
2. **Clean Fallback**: Deactivating ATT Fused cleanly restores standard GPS/Google Fused/wheel priority without residual callbacks.
3. **Multi-Source Persistence**: Concurrently paired GPS/Google Fused/wheel sensors continue to record separate device-attributed columns in `WorkoutSamples.db`.
4. **Dispatcher Safety**: Sensor updates adhere strictly to `SpeedAndLocationDevice` single-thread dispatching.
5. **Localization Parity**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 4. Proposed Architectural Changes
* **Database & Models (`Devices.db` v11)**: Add `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` and `SensorType.WORLD_YAW_RATE_radps`. Upgrade `DevicesDbHelper.DB_VERSION` to 11 with idempotent `migrateAddSpeedAndLocationAttFused()`.
* **Driver (`SpeedAndLocationDevice_ATTFused.kt`)**: Extends `SpeedAndLocationDevice`. Coordinates `UnicycleOdometryEkf`, `QuaternionGyroProjector`, and `WheelCircumferenceCalibrator`. Emits fused coordinates, speed, distance, bearing, and 1-Hz world yaw-rate $\omega_{ENU,z}$.
* **Routing & Lifecycle (`DeviceManager.java` & `MySensorManager.java`)**: Manage driver start/stop on pairing changes. Integrate `SPEED_AND_LOCATION_ATT_FUSED` at index 0 of `LOCATION_PRIORITY` and `SPEED_PRIORITY` when active.
* **UI & Configuration (`EditDeviceDialog.kt`)**: Add educational card and 4 source switches guarded by paired/hardware states (GNSS, Wheel Sensors, Gyro IMU, Auto Calibration).
* **UI Consistency**: Follows `docs/design_guidelines.md` and `AltitudeExplanationSection` pattern in `EditDeviceDialog.kt` using standard `Card`, `Switch`, and `Text`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Enum Models & `Devices.db` v11 Migration
* Files: `DeviceType.java`, `SensorType.java`, `DevicesDatabaseManager.java`, `values*/strings_devices.xml`, `values*/strings.xml`.
* Changes: Declare `SPEED_AND_LOCATION_ATT_FUSED` and `WORLD_YAW_RATE_radps`. Bump `DevicesDbHelper.DB_VERSION` to 11 with `migrateAddSpeedAndLocationAttFused()`. Localize strings across all 9 languages.

### Step 2: Priority Routing & `DeviceManager` Lifecycle Management
* Files: `MySensorManager.java`, `DeviceManager.java`.
* Changes: Add `SPEED_AND_LOCATION_ATT_FUSED` to `LOCATION_PRIORITY` (rank 0) and `SPEED_PRIORITY` (rank 0). Instantiate and manage `SpeedAndLocationDevice_ATTFused` on pairing events in `DeviceManager`.

### Step 3: Implement Device Driver `SpeedAndLocationDevice_ATTFused.kt`
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt`.
* Changes: Extend `SpeedAndLocationDevice`. Subscribe to IMU (`TYPE_GYROSCOPE`, `TYPE_GAME_ROTATION_VECTOR`) and ingest GNSS fixes and wheel revolutions. Feed into `UnicycleOdometryEkf` and emit fused location, speed, distance, and 1-Hz `WORLD_YAW_RATE_radps`.

### Step 4: Sample Persistence & Device Attribute Routing
* Files: `TrackerService.java`.
* Changes: Ensure `WORLD_YAW_RATE_radps` is captured and recorded every second in `WorkoutSamples.db`. Support `_att_fused` device name suffix.

### Step 5: `EditDeviceDialog.kt` Educational Card & Paired-Source Guards
* Files: `EditDeviceDialog.kt`, `AttFusedPreferences.kt`.
* Changes: Add educational "How It Works" card and 4 source toggles with pairing and hardware availability guards. Localize helper hints.

### Step 6: Targeted Tests & Living Documentation Sync
* Files: `SpeedAndLocationDevice_ATTFusedTest.kt`, `DevicesDatabaseManagerMigrationTest.kt`, `MySensorManagerPriorityTest.kt`, `EditDeviceDialogGuardsTest.kt`, `docs/architecture.md`.
* Changes: Author unit tests. Update `docs/architecture.md` (SWE.2). Update living docs status to `[Implemented]`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.odometry.*"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in Stage 4; 100% clean-room test suite pass in Stage 5.
* **Rollback**: Isolated working branch `feat/ATT-7751` allows clean rollback to `sprint/2026-46.6` if any invariant fails.
