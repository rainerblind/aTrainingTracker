# Stage 3: Implementation Plan - ATT-6279: [Feature] ATT Fused (Höhe und Steigung)

**Ticket**: [ATT-6279](https://rainerblind.atlassian.net/browse/ATT-6279)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-6279`  
**Requirement Mapping**: `REQ-FIL-016` (*Kinematic Altitude & Slope Fusion Engine & Virtual Sensor Device*)  
**Test Mapping**: `TST-FIL-016`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Existing elevation and slope tracking operates in disconnected silos: `AltitudeFromPressureDevice` is responsive but drifts with pocket temperature and weather; `AltitudeFromPositionDevice` (DEM) provides absolute elevation but exhibits 1m discrete staircase jumps; `VerticalSpeedAndSlopeDevice` uses a 15-second moving regression window that lags during gradient changes and oscillates when stationary. ATT-6279 introduces a kinematic terrain-slope fusion engine ($dh = g \cdot ds$) with observability-aware 2-State $[h_k, g_k]^T$ and 3-State $[h_k, g_k, b_{baro,k}]^T$ filtering and standstill clamping ($v=0$). It is exposed as an opt-in pairable `Protocol.SMARTPHONE` virtual sensor (*ATT Fused (Höhe und Steigung)*) with UI source configuration guards in `EditDeviceDialog.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-FIL-016` (`REQ-FIL-016.1`..`REQ-FIL-016.5`)
* **Test Mapping**: `TST-FIL-016` (`TST-FIL-016.1`..`TST-FIL-016.6`)

---

## 3. System Invariants & Preserved Behavior
1. **Unpaired by Default (`PAIRED = 0`)**: Fresh installs and DB v12 upgrades leave ATT Fused disabled until explicitly activated; zero CPU/battery drain when inactive.
2. **Clean Fallback**: Deactivating ATT Fused cleanly restores `VerticalSpeedAndSlopeDevice` and standard altitude priority.
3. **Double-EMA Prevention**: Ingests slew-clamped barometric altitude from `BarometricAltitudeFilter` without EMA smoothing to avoid cascaded filter lag.
4. **Single-Thread Dispatcher Affinity**: Sensor updates adhere strictly to `MyDevice` thread dispatching conventions.
5. **Localization Parity**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 4. Proposed Architectural Changes
* **Fusion Core (`KinematicAltitudeSlopeEkf.kt`)**: Implemented in `banalservice.odometry`. 2-State mode $[h_k, g_k]^T$ (Baro + Speed) with standstill clamping ($v=0 \implies Q_g = 0, dh = 0$). 3-State mode $[h_k, g_k, b_{baro,k}]^T$ (Baro + DEM/GPS + Speed) making barometric drift continuously observable.
* **Barometer Filter Update (`BarometricAltitudeFilter.kt`)**: Expose `filterSlewOnly(rawAltitude, timestampMs)` providing median-filtered and slew-clamped altitude without EMA.
* **Driver (`AltitudeAndSlopeDevice_ATTFused.kt`)**: Extends `MyDevice`. Subscribes to barometric, DEM, GPS altitude, and speed sources. Emits `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT`.
* **Database Migration (`Devices.db` v12)**: Add `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED`. Upgrade `DevicesDbHelper.DB_VERSION` to 12 with idempotent `migrateAddAltitudeAndSlopeAttFused()`.
* **Routing & Lifecycle (`DeviceManager.java` & `MySensorManager.java`)**: Manage driver start/stop on pairing changes. Insert at index 0 of `ALTITUDE_PRIORITY` and establish priority arrays for `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT` overriding `VERTICAL_SPEED_AND_SLOPE`.
* **UI & Configuration (`EditDeviceDialog.kt`)**: Add educational card and 4 source switches guarded by paired/hardware states (Barometer, DEM, GPS altitude, Speed coupling).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Fusion Math Engine & Slew-Only Filter Ingestion
* Files: `KinematicAltitudeSlopeEkf.kt`, `BarometricAltitudeFilter.kt`.
* Changes: Implement 2-state/3-state EKF kinematics ($dh = g \cdot ds$), distance-scaled process noise $Q_g \sim q_g \cdot \Delta s_k$, standstill clamping ($v=0$), Huber innovation weighting. Expose `filterSlewOnly()` in `BarometricAltitudeFilter.kt`.

### Step 2: DeviceType Registration & `Devices.db` v12 Migration
* Files: `DeviceType.java`, `DevicesDatabaseManager.java`, `values*/strings_devices.xml`, `values*/strings.xml`.
* Changes: Declare `ALTITUDE_AND_SLOPE_ATT_FUSED` under `Protocol.SMARTPHONE`. Bump `DevicesDbHelper.DB_VERSION` to 12 with idempotent `migrateAddAltitudeAndSlopeAttFused()`. Localize strings across all 9 languages.

### Step 3: Priority Routing & `DeviceManager` Lifecycle Management
* Files: `MySensorManager.java`, `DeviceManager.java`.
* Changes: Insert at index 0 of `ALTITUDE_PRIORITY`. Define explicit priority arrays for `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`. Instantiate, start, and shut down `AltitudeAndSlopeDevice_ATTFused` on pairing events in `DeviceManager`.

### Step 4: Implement Device Driver `AltitudeAndSlopeDevice_ATTFused.kt`
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`.
* Changes: Extend `MyDevice`. Coordinate `KinematicAltitudeSlopeEkf`. Ingest slew-clamped barometric altitude, DEM, GPS altitude, and speed/distance. Emit fused altitude, slope %, vertical speed m/h, ascent, and descent.

### Step 5: `EditDeviceDialog.kt` Educational Card & Paired-Source Guards
* Files: `EditDeviceDialog.kt`, `AttFusedAltitudePreferences.kt`.
* Changes: Add educational "How It Works" card and 4 source toggles with pairing and hardware availability guards. Localize helper hints.

### Step 6: Targeted Tests & Living Documentation Sync
* Files: `KinematicAltitudeSlopeEkfTest.kt`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`, `DevicesDatabaseManagerMigrationV12Test.kt`, `AltitudeSlopePriorityTest.kt`, `EditDeviceDialogAltitudeGuardsTest.kt`, `docs/architecture.md`.
* Changes: Author unit tests. Update `docs/architecture.md` (SWE.2). Update living docs status to `[Implemented]`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.odometry.*"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in Stage 4; 100% clean-room test suite pass in Stage 5.
* **Rollback**: Isolated working branch `feat/ATT-6279` allows clean rollback to `sprint/2026-46.6` if any invariant fails.
