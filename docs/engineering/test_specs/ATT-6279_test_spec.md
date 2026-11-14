# Stage 2: Requirement & Test Specification - ATT-6279: [Feature] ATT Fused (Höhe und Steigung)

**Ticket**: [ATT-6279](https://rainerblind.atlassian.net/browse/ATT-6279)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-6279`  
**Requirement Mapping**: `REQ-FIL-016` (*Kinematic Altitude & Slope Fusion Engine & Virtual Sensor Device*)  
**Test Spec ID**: `TST-FIL-016`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-FIL-016)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. **Kinematic EKF & Observability Modes (`REQ-FIL-016.1`)**:
   - The system SHALL maintain elevation $h$ and terrain slope grade $g$ coupled to horizontal displacement $\Delta s_k = v_k \cdot \Delta t_k$ via $h_k = h_{k-1} + g_{k-1} \cdot \Delta s_k$.
   - Unanchored mode (Barometer + Speed) SHALL operate in 2-State mode $[h_k, g_k]^T$.
   - Anchored mode (DEM or GPS altitude enabled) SHALL operate in 3-State mode $[h_k, g_k, b_{baro,k}]^T$, isolating barometric thermal and synoptic weather drift into $b_{baro}$ while preserving continuous smooth elevation without 1m DEM discrete grid staircase artifacts.
2. **Standstill Clamping & Slew-Only Barometer Ingestion (`REQ-FIL-016.2`)**:
   - At standstill ($v=0, \Delta s=0$), slope process noise SHALL be zero ($Q_g = 0$) and ground altitude clamped ($dh = 0$), preventing stationary weather drift from accumulating false ascent/descent.
   - `BarometricAltitudeFilter.kt` SHALL expose slew-clamped altitude to prevent cascaded double-EMA phase lag.
3. **Pairable Virtual Sensor & `Devices.db` Schema v12 Migration (`REQ-FIL-016.3`)**:
   - `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED` SHALL be declared under `Protocol.SMARTPHONE`, providing `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT`.
   - `DevicesDbHelper.DB_VERSION` SHALL be incremented to 12 with idempotent `migrateAddAltitudeAndSlopeAttFused()` inserting the device row with default `PAIRED = 0`.
   - Display name strings SHALL be localized across all 9 supported languages.
4. **MySensorManager Priority Hierarchy Integration (`REQ-FIL-016.4`)**:
   - `ALTITUDE_AND_SLOPE_ATT_FUSED` SHALL be placed at index 0 of `ALTITUDE_PRIORITY`.
   - Priority arrays for `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT` SHALL prioritize `ALTITUDE_AND_SLOPE_ATT_FUSED` at index 0 over `VERTICAL_SPEED_AND_SLOPE`.
5. **EditDeviceDialog Educational Card & Source Guards (`REQ-FIL-016.5`)**:
   - Dialog SHALL present an educational "How It Works" card and 4 source toggles guarded by pairing/hardware states (Barometer, DEM, GPS altitude, Speed coupling).
   - Zero text clipping, wrapping, or ellipsizing across German and English UI (Rule 84).

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* the sensor list, *When* viewed on fresh install or DB upgrade, *Then* ATT Fused (Höhe und Steigung) is listed with default `PAIRED = 0` across 9 languages.
- *Given* 2-State mode (Baro + Speed), *When* athlete stops ($v=0$), *Then* altitude changes are clamped and false climb accumulation is zero.
- *Given* 3-State mode (Baro + DEM + Speed), *When* DEM updates arrive, *Then* thermal drift is absorbed into $b_{baro}$ and elevation remains smooth without DEM staircase jumps.
- *Given* `EditDeviceDialog` for ATT Fused, *When* pressure sensor or DEM is not paired, *Then* toggles are greyed out with clear localized hints.

---

## 2. Test Specification (TST-FIL-016)

### Test Case 1: 2-State & 3-State Kinematic Simulation (`TST-FIL-016.1`)
- **Scope**: Mathematical Fusion Unit Test (`KinematicAltitudeSlopeEkfTest.kt`)
- **Action**: Test constant slope tracking ($+8\%$ at $5\text{ m/s}$), standstill drift clamping ($v=0$), and 3-State thermal drift absorption ($+0.2\text{ m/min}$) anchored by DEM.

### Test Case 2: Schema Version 12 Migration & DeviceType Registration (`TST-FIL-016.2`)
- **Scope**: Database & Model Unit Test (`DevicesDatabaseManagerMigrationV12Test.kt`)
- **Action**: Upgrade in-memory `Devices.db` from v11 to v12; assert row inserted with `PAIRED = 0`. Verify `DatabaseSchemaRegistry` parity.

### Test Case 3: MySensorManager Priority Routing & Fallback (`TST-FIL-016.3`)
- **Scope**: Sensor Routing Unit Test (`AltitudeSlopePriorityTest.kt`)
- **Action**: Register ATT Fused; assert `bestSensor` selects it for `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`. Unpair ATT Fused; assert clean fallback.

### Test Case 4: Driver Lifecycle & Metrics Ingestion (`TST-FIL-016.4`)
- **Scope**: Device Driver Unit Test (`AltitudeAndSlopeDevice_ATTFusedTest.kt`)
- **Action**: Feed simulated pressure, DEM, and speed samples; verify EKF prediction/update and emitted sensor telemetry. Verify clean shutdown.

### Test Case 5: EditDeviceDialog Educational Card & Source Guards (`TST-FIL-016.5`)
- **Scope**: UI Logic Unit Test (`EditDeviceDialogAltitudeGuardsTest.kt`)
- **Action**: Verify educational card rendering, disabled switch states with localized hints, and 9-language localization parity.

### Test Case 6: Targeted Regression Suite (`TST-FIL-016.6`)
- **Action**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.odometry.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-016.1` | Unit (Math) | `KinematicAltitudeSlopeEkf.kt` | `REQ-FIL-016.1`, `REQ-FIL-016.2` | Specified |
| `TST-FIL-016.2` | Unit (DB) | `DevicesDatabaseManager.java`, `DeviceType.java` | `REQ-FIL-016.3` | Specified |
| `TST-FIL-016.3` | Unit (Routing) | `MySensorManager.java` | `REQ-FIL-016.4` | Specified |
| `TST-FIL-016.4` | Unit (Driver) | `AltitudeAndSlopeDevice_ATTFused.kt` | `REQ-FIL-016.1`, `REQ-FIL-016.4` | Specified |
| `TST-FIL-016.5` | Unit (UI) | `EditDeviceDialog.kt`, `strings_devices.xml` | `REQ-FIL-016.5` | Specified |
| `TST-FIL-016.6` | Regression | `com.atrainingtracker.banalservice.*` | `REQ-PRO-001` | Specified |
