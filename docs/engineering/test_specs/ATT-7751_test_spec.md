# Stage 2: Requirement & Test Specification - ATT-7751: [Feature] ATT Fused (Position und Geschwindigkeit)

**Ticket**: [ATT-7751](https://rainerblind.atlassian.net/browse/ATT-7751)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7751`  
**Requirement Mapping**: `REQ-SEN-028` (*Pairable Virtual Sensor ATT Fused with Priority Routing & UI Guards*)  
**Test Spec ID**: `TST-SEN-028`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-SEN-028)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. **Virtual Device Declaration & Schema Migration (`REQ-SEN-028.1`)**:
   - `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` SHALL be declared under `Protocol.SMARTPHONE`, providing `LATITUDE`, `LONGITUDE`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `LINE_DISTANCE_m`, `BEARING`, and `ACCURACY`.
   - `DevicesDbHelper.DB_VERSION` SHALL be incremented to 11. Idempotent migration `migrateAddSpeedAndLocationAttFused()` SHALL insert the device row with `PAIRED = 0` (unpaired by default).
   - Display names SHALL be localized across all 9 supported languages.
2. **MySensorManager Priority Hierarchy Integration (`REQ-SEN-028.2`)**:
   - `SPEED_AND_LOCATION_ATT_FUSED` SHALL be placed at index 0 of `LOCATION_PRIORITY` and index 0 of `SPEED_PRIORITY` when active/paired, smoothly falling back to hardware/GNSS priority when unpaired.
3. **Driver Lifecycle & 1-Hz Yaw-Rate Persistence (`REQ-SEN-028.3`)**:
   - `SpeedAndLocationDevice_ATTFused` SHALL extend `SpeedAndLocationDevice`, driving `UnicycleOdometryEkf`, `QuaternionGyroProjector`, and `WheelCircumferenceCalibrator`.
   - `DeviceManager` SHALL manage driver lifecycle upon pairing state changes.
   - 1-Hz world yaw-rate $\omega_{ENU,z}$ SHALL be emitted as `SensorType.WORLD_YAW_RATE_radps` and persisted into `WorkoutSamples.db` for post-workout ERTS replay (ATT-7750).
4. **EditDeviceDialog Educational Card & Source Guards (`REQ-SEN-028.4`)**:
   - Dialog SHALL present an educational "How It Works" card and 4 source toggles guarded by pairing/hardware states (Location, Wheel Speed, Gyro Turn, Auto Calibration).
   - Zero text clipping, wrapping, or ellipsizing across German and English UI (Rule 84).

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* the sensor list, *When* viewed on fresh install or DB upgrade, *Then* ATT Fused is listed with default `PAIRED = 0` across 9 languages.
- *Given* ATT Fused is paired, *When* workout tracks, *Then* `MySensorManager` prioritizes fused values above GPS/Google Fused and records separate columns in `WorkoutSamples.db`.
- *Given* `EditDeviceDialog` for ATT Fused, *When* gyro hardware or speed sensors are absent, *Then* toggles are greyed out with clear hints.
- *Given* ATT Fused is unpaired, *When* tracking runs, *Then* routing cleanly reverts to standard priority with zero gyro drain.

---

## 2. Test Specification (TST-SEN-028)

### Test Case 1: Schema Migration & DeviceType Registration (`TST-SEN-028.1`)
- **Scope**: Database & Model Unit Test (`DevicesDatabaseManagerMigrationTest.kt`)
- **Action**: Upgrade in-memory `Devices.db` from v10 to v11; assert row inserted with `PAIRED = 0`. Verify `DatabaseSchemaRegistry` parity.

### Test Case 2: Priority Routing & Dynamic Fallback (`TST-SEN-028.2`)
- **Scope**: Sensor Routing Unit Test (`MySensorManagerPriorityTest.kt`)
- **Action**: Register ATT Fused; assert `bestSensor` selects it for speed/distance/location. Unpair ATT Fused; assert clean fallback.

### Test Case 3: Driver Lifecycle & IMU Ingestion (`TST-SEN-028.3`)
- **Scope**: Device Driver Unit Test (`SpeedAndLocationDevice_ATTFusedTest.kt`)
- **Action**: Feed GNSS fixes and wheel revolutions; assert EKF update and 1-Hz `WORLD_YAW_RATE_radps` emissions. Verify clean shutdown.

### Test Case 4: Side-by-Side WorkoutSamples Persistence (`TST-SEN-028.4`)
- **Scope**: Database Integration Unit Test (`WorkoutSamplesPersistenceTest.kt`)
- **Action**: Simulate concurrent Google Fused + ATT Fused session; verify `WorkoutSamples.db` records device-attributed columns.

### Test Case 5: EditDeviceDialog Educational Card & Guards (`TST-SEN-028.5`)
- **Scope**: UI Logic Unit Test (`EditDeviceDialogGuardsTest.kt`)
- **Action**: Test guarded switch states with missing sensors/hardware; verify localized helper hints.

### Test Case 6: 9-Language Localization Parity (`TST-SEN-028.6`)
- **Scope**: Localization Audit (`TranslationParityTest.kt`)
- **Action**: Verify `devices_location_ATT_fused`, explanation strings, and hints across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 7: Targeted Regression Suite (`TST-SEN-028.7`)
- **Action**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.odometry.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-028.1` | Unit (DB) | `DevicesDatabaseManager.java`, `DeviceType.java` | `REQ-SEN-028.1` | Specified |
| `TST-SEN-028.2` | Unit (Routing) | `MySensorManager.java` | `REQ-SEN-028.2` | Specified |
| `TST-SEN-028.3` | Unit (Driver) | `SpeedAndLocationDevice_ATTFused.kt` | `REQ-SEN-028.3` | Specified |
| `TST-SEN-028.4` | Unit (DB) | `TrackerService.java`, `SensorType.java` | `REQ-SEN-028.3` | Specified |
| `TST-SEN-028.5` | Unit (UI) | `EditDeviceDialog.kt` | `REQ-SEN-028.4` | Specified |
| `TST-SEN-028.6` | Localization | `strings_devices.xml` (9 locales) | `REQ-SEN-028.1`, `REQ-SEN-028.4` | Specified |
| `TST-SEN-028.7` | Regression | `com.atrainingtracker.banalservice.*` | `REQ-PRO-001` | Specified |
