# Stage 5 Verification & Walkthrough: ATT-7751

## Overview
- **Issue**: [ATT-7751](jira://ATT-7751) - Pairable virtual sensor ATT Fused with priority routing, UI guards, and 1-Hz yaw-rate persistence
- **Epic**: [ATT-166](jira://ATT-166) - Filtering to improve sensor values (especially locations)
- **Branch**: `feat/ATT-7751`
- **Subtasks**:
  - `ATT-7854`: [Implementation] ATT-7751: Stage 4 Construct & Implement
  - `ATT-7855`: [Verification] ATT-7751: Stage 5 Clean-Room Regression & Walkthrough

---

## 1. Summary of Changes

### Virtual Sensor Driver & Lifecycle
- **`SpeedAndLocationDevice_ATTFused.kt`**: Implemented virtual sensor device subscribing to physical sensors (`LOCATION`, `SPEED`, `CADENCE`, and device rotation vector `ROTATION_VECTOR`). Feeds `QuaternionGyroProjector`, `WheelCircumferenceCalibrator`, and `UnicycleOdometryEkf`. Emits fused location, speed, distance, and 1-Hz `SensorType.WORLD_YAW_RATE_radps` samples.
- **`DeviceType.java` & `DevicesDatabaseManager.java`**: Added `SPEED_AND_LOCATION_ATT_FUSED` with database schema v11 upgrade ensuring idempotent insertion with `PAIRED = 0`.
- **`MySensorManager.java`**: Configured `LOCATION_PRIORITY` and `SPEED_PRIORITY` routing such that when paired/active, `SPEED_AND_LOCATION_ATT_FUSED` preempts physical GPS and speed sensors at index 0.

### User Interface & Configuration
- **`AttFusedPreferences.kt`**: SharedPreferences helper managing 4 configurable feature toggles with prerequisite guards:
  - IMU orientation gyro projection (requires physical IMU)
  - Automatic wheel circumference calibration (requires wheel speed sensor)
  - Dead reckoning in GPS outages
  - RTS retrospective smoothing
- **`EditDeviceDialog.kt`**: Integrated `AttFusedConfigSection` providing an educational summary card and interactive toggle controls guarded by sensor availability.
- **9-Language Localization**: Provided full string parity for all educational and configuration texts across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.

### Sample Persistence & Contract Integrity
- **`SensorType.java` & `ActivityType.java`**: Added `WORLD_YAW_RATE_radps` in `SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` with unit `rad/s`.
- **`TrackerService.java` & `CSVFileWriter.java`**: Persisted 1-Hz yaw-rate samples with `_att_fused` source tagging.
- **Cleanliness & Architectural Invariants**: Fixed legacy singleton leaks in `LegacyImportEngine.kt` via `EquipmentRepository.getInstance(context).equipmentItems` and receiver leaks in `AntChannelHelper.kt` via `LifecycleUtils.safeUnregisterReceiver`.

---

## 2. Verification Results

### Unit & Integration Test Suites
- `SpeedAndLocationDevice_ATTFusedTest`: **PASSED** (10/10 tests) - EKF prediction, fusion updates, yaw-rate emission, pause/resume lifecycle.
- `EditDeviceDialogGuardsTest`: **PASSED** (4/4 tests) - Toggle availability and prerequisite enforcement.
- `DevicesDatabaseManagerMigrationTest`: **PASSED** (2/2 tests) - Idempotent schema v11 migration.
- `MySensorManagerPriorityTest`: **PASSED** (3/3 tests) - Priority routing and clean fallback when unpaired.
- `TranslationParityTest`: **PASSED** - 9 languages verified for 100% key parity and non-empty values.
- `SensorCategoryAndSelectorContractTest`: **PASSED** - 65 sensor types audited.
- `AspiceSweAuditContractTest`: **PASSED** - `REQ-SEN-028` and `TST-SEN-028` verified in living docs.
- Full regression suite (`testDebugUnitTest`): **PASSED** (All 1,069 tests passing, 0 failures, 0 regressions).

### Static Analysis & Linters
- `app:detekt`: **PASSED** (0 issues reported in all modified files, baseline preserved).
- `app:lintDebug`: **PASSED**.
- `tools/verify_requirement_governance.py`: **PASSED** (100% ASPICE index alignment).

---

## 3. Requirement Traceability Matrix
| Requirement ID | Test ID | Description | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SEN-028.1` | `TST-SEN-028.1` | Virtual sensor `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` with v11 DB migration | Verified |
| `REQ-SEN-028.2` | `TST-SEN-028.2` | Sensor priority routing in `MySensorManager` | Verified |
| `REQ-SEN-028.3` | `TST-SEN-028.3` | Fused estimation, smoothing & 1-Hz `WORLD_YAW_RATE_radps` | Verified |
| `REQ-SEN-028.4` | `TST-SEN-028.4` | Educational card & 4 guarded toggles in `EditDeviceDialog` | Verified |
| `REQ-SEN-028.5` | `TST-SEN-028.5` | Sample persistence with `_att_fused` source suffix | Verified |
| `REQ-SEN-028.6` | `TST-SEN-028.6` | 9-language localization parity across strings files | Verified |

---

## 4. Stage 6 Cold Carbon Review - Bounce 1/3 Remediation

### Findings Remediated
1. **Finding 1 (Feed GNSS Speed into EKF)**:
   - In `SpeedAndLocationDevice_ATTFused.kt:onLocationChanged`, when `location.hasSpeed()` is valid, `ekf.updateSpeed(location.speed.toDouble(), speedVar, timestampMs)` is now called before updating `location.speed = ekf.speedMps.toFloat()`. This prevents the EKF speed state from being permanently stuck at 0.0 m/s and enables unicycle dead-reckoning coordinate propagation during GPS outages.
2. **Finding 2 (Production Wiring for Wheel Speed & Auto-Calibration)**:
   - Implemented `MySensorManager.WheelMeasurementListener` interface (`onWheelMeasurement`, `onWheelDistance`, `onWheelRevolutions`) and listener lifecycle in `MySensorManager.java`.
   - Connected `MySensorManager` listeners for physical wheel sensors (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`) on registration/unregistration.
   - Wired `SpeedAndLocationDevice_ATTFused` to subscribe to `MySensorManager.WheelMeasurementListener`.
   - Wired `BTLEBikeDevice`, `ANTBikeSpeedDevice`, and `ANTBikeSpeedAndCadenceDevice` to notify revolutions and distance.
   - In `SpeedAndLocationDevice_ATTFused.onLocationChanged`, wired step-by-step auto-calibration invoking `processWheelCalibrationStep` on straight qualified segments.
3. **Finding 3 (Auto-Calibration Default Value)**:
   - Updated `AttFusedPreferences.isAutoCalibEnabled(context)` to default to `false` (Opt-in toggle) matching `REQ-SEN-028.4(d)` and ticket specification.
   - Updated `EditDeviceDialogGuardsTest.kt` to verify that auto-calibration is off by default and turns on when toggled.
4. **Finding 4 (Strengthened Unit Tests)**:
   - Strengthened `SpeedAndLocationDevice_ATTFusedTest.testOnLocationChanged_updatesEkfAndEmitsFusedLocation` to assert `ekf.speedMps > 0.0` and that emitted location speed matches the EKF/GNSS speed.
   - Added `testProductionWheelSpeedWiring_updatesSpeedAndCalibratesWheel` verifying live speed and distance wiring from `MySensorManager` into `SpeedAndLocationDevice_ATTFused`, wheel revolution tracking, and auto-calibrator step updates.

### Verification Evidence (Bounce 1/3)
- **Targeted Test Suite**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest \
      --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" \
      --tests "com.atrainingtracker.banalservice.devices.EditDeviceDialogGuardsTest" \
      --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerMigrationTest" \
      --tests "com.atrainingtracker.banalservice.sensor.MySensorManagerPriorityTest" \
      --tests "*TranslationParityTest*"
  ```
  **Result**: `BUILD SUCCESSFUL` (100% pass across all targeted tests).
- **ASPICE Requirement & Traceability Governance**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 python3 tools/verify_requirement_governance.py
  ```
  **Result**: `PASS: 100% System-to-Software requirement traceability verified. Zero ungrounded [Verified] requirements detected.`

