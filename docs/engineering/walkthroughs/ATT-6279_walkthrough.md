# ATT-6279 Walkthrough: Kinematic Terrain-Slope Altitude Fusion Engine & Pairable ATT Fused (Höhe und Steigung) Virtual Sensor

## Overview
- **Issue**: [ATT-6279](https://jira.example.com/browse/ATT-6279)
- **Epic**: [ATT-166](https://jira.example.com/browse/ATT-166) (Filtering to improve sensor values)
- **Stage**: Stage 5 (Verification, Clean-Room Regression & Walkthrough)
- **Branch**: `feat/ATT-6279`
- **Requirements**: `REQ-FIL-016` (`REQ-FIL-016.1`..`REQ-FIL-016.5`)
- **Tests**: `TST-FIL-016` (`TST-FIL-016.1`..`TST-FIL-016.6`)

## Problem & Architectural Solution
Prior to ATT-6279, barometric altitude, DEM elevation, and vertical speed/slope operated as decoupled subsystems. Standstill barometric drift caused phantom climbing accumulation and erratic slope swings, while standalone barometric filtering suffered from cascaded double-EMA phase lag when ingested by downstream estimators.

ATT-6279 introduces the **ATT Fused (Höhe und Steigung)** pairable virtual sensor powered by the **Kinematic Terrain-Slope EKF**:
1. **Coupled Kinematic Progression**:
   $$h_k = h_{k-1} + g_{k-1} \cdot \Delta s_k$$
   where terrain grade $g = \frac{dh}{ds}$ propagates directly with horizontal displacement $\Delta s_k = v_k \cdot \Delta t_k$.
2. **Observability-Aware Modes**:
   - **2-State Mode** $[h_k, g_k]^T$: When unanchored (Barometer + Speed only), updates elevation directly via barometric measurements while clamping altitude at standstill ($v < 0.5\text{ m/s} \implies dh = 0, Q_g = 0$).
   - **3-State Mode** $[h_k, g_k, b_{baro,k}]^T$: When anchored by DEM (`ALTITUDE_FROM_POSITION`) or GPS altitude, observes barometric synoptic weather and thermal drift into $b_{baro}$, eliminating 1m DEM discrete staircase artifacts while preserving continuous physical elevation.
3. **Double-EMA Prevention**:
   `BarometricAltitudeFilter.kt` exposes `filterSlewOnly(rawAltitude, timestampMs)` (omitting the causal EMA stage), providing slew-clamped, median-filtered barometric measurements directly to the EKF.
4. **Devices.db Version 12 Schema Migration**:
   `DB_VERSION = 12` registers `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED` under `Protocol.SMARTPHONE` with default `PAIRED = 0` (unpaired/opt-in) for zero unexpected battery drain.
5. **MySensorManager Priority Hierarchy**:
   When paired, `ALTITUDE_AND_SLOPE_ATT_FUSED` occupies index 0 in `ALTITUDE_PRIORITY` and `SLOPE_AND_VERTICAL_SPEED_PRIORITY` (`SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`), falling back cleanly to `VerticalSpeedAndSlopeDevice` on deactivation.
6. **Educational Card & Source Guards**:
   `EditDeviceDialog.kt` renders an educational "How It Works" card and 4 source toggles (Barometer, DEM, GPS altitude, Speed coupling) with hardware/pairing availability guards and 9-language localization parity.

---

## Changes Implemented

### 1. Fusion Core & Signal Processing
- [`KinematicAltitudeSlopeEkf.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt): 2-State / 3-State coupled kinematic model, Huber innovation down-weighting ($k=2.5$), standstill clamping ($v < 0.5\text{ m/s}$), and VAM derivation.
- [`BarometricAltitudeFilter.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/devices/BarometricAltitudeFilter.kt): Added `filterSlewOnly()` and `getSlewAltitude()` to prevent cascaded double-EMA phase lag.

### 2. Device Driver & Lifecycle Management
- [`AltitudeAndSlopeDevice_ATTFused.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt): Virtual device driver emitting `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT`.
- [`DeviceType.java`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceType.java): Added enum `ALTITUDE_AND_SLOPE_ATT_FUSED` under `Protocol.SMARTPHONE`.
- [`DeviceManager.java`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java): Lifecycle instantiation, preference listener routing, sensor registration, and getter.
- [`MySensorManager.java`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java): Priority routing arrays (`ALTITUDE_PRIORITY`, `SLOPE_AND_VERTICAL_SPEED_PRIORITY`), `getSensorForDeviceType()`.

### 3. Database & Preferences
- [`DevicesDatabaseManager.java`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java): Incremented `DB_VERSION` to 12, added `migrateAddAltitudeAndSlopeAttFused()`, `case 12` in `onCreate`, and `getAltitudeAndSlopeAttFusedDeviceId()`.
- [`DeviceDataRepository.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt): Exposed `getAltitudeAndSlopeAttFusedDeviceId()`.
- [`AttFusedAltitudePreferences.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/devices/AttFusedAltitudePreferences.kt): SharedPreferences keys, default values, and paired-source availability guards.

### 4. UI & Localization
- [`EditDeviceDialog.kt`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/subagent-ATT-166---Epic-Pro-Agent-self-ef5a707f/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt): `AttFusedAltitudeConfigSection` with educational card and 4 guarded toggles.
- `res/values*/strings_devices.xml` (all 9 languages: EN, DE, ES, FR, IT, JA, NL, PL, PT): Device name, educational description, and toggle hints.

### 5. Living Documentation & Architecture
- [`docs/architecture.md`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/docs/architecture.md): SWE.2 Component Model table updated with `AltitudeAndSlopeDevice_ATTFused`.
- [`docs/requirements/req_tracking_cockpit.md`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/docs/requirements/req_tracking_cockpit.md): Marked `REQ-FIL-016` as `[Verified]`.
- [`docs/tests/tst_tracking_cockpit.md`](file:///home/rainer/.gemini/antigravity/worktrees/02cffe02-c961-48a9-bc0a-30f7f1c75070/docs/tests/tst_tracking_cockpit.md): Marked `TST-FIL-016` as `[Verified]`.

---

## Verification Results

### Unit Test Suite
1. `KinematicAltitudeSlopeEkfTest.kt` (`TST-FIL-016.1`):
   - 2-State constant slope progression ($dh = g \cdot ds$): PASS
   - Standstill clamping ($v = 0 \implies dh = 0, Q_g = 0$): PASS
   - 3-State thermal drift decoupling ($b_{baro}$ absorption): PASS
   - Huber innovation down-weighting: PASS
   - Dynamic mode transition (2-State <-> 3-State): PASS
2. `DevicesDatabaseManagerMigrationV12Test.kt` (`TST-FIL-016.2`):
   - Enum mapping across all 5 sensor types: PASS
   - v11 -> v12 migration with default `PAIRED = 0`: PASS
   - Idempotency on repeated migrations: PASS
3. `AltitudeSlopePriorityTest.kt` (`TST-FIL-016.3`):
   - Priority rank 0 when paired for Altitude, Slope, Vertical Speed, Ascent, Descent: PASS
   - Clean fallback to `VerticalSpeedAndSlopeDevice` when unpaired: PASS
4. `AltitudeAndSlopeDevice_ATTFusedTest.kt` (`TST-FIL-016.4`):
   - Registration of all 5 sensors: PASS
   - Pressure measurement handling & altitude publishing: PASS
   - Climbing cycle with ascent accumulation and slope updates: PASS
   - Clean teardown in `shutDown()`: PASS
5. `EditDeviceDialogAltitudeGuardsTest.kt` (`TST-FIL-016.5`):
   - Educational card rendering: PASS
   - Source availability guards (barometer hardware, DEM paired, GPS paired, speed): PASS
   - 9-language translation parity: PASS

### Governance
- `python3 tools/verify_requirement_governance.py --sync-indexes`: PASS (Master index tables synchronized with 100% parity).
- `python3 tools/verify_requirement_governance.py`: PASS (Zero violations, 100% bidirectional traceability).
