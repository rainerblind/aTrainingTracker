# Stage 1 Analysis: ATT-6279 - [Feature] ATT Fused (Höhe und Steigung): Configurable 2-State/3-State kinematic altitude & slope fusion sensor with paired-source UI guards

**Ticket**: [ATT-6279](https://rainerblind.atlassian.net/browse/ATT-6279)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-6279`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Currently, altitude and slope tracking relies on isolated single-source drivers: `AltitudeFromPressureDevice` (fast and smooth, but subject to thermal heating drift in jersey pockets and synoptic weather changes), `AltitudeFromPositionDevice` (DEM; absolute elevation, but coarse 1m discrete grid resolution causing staircase jumps), and `VerticalSpeedAndSlopeDevice` (moving-window linear regression VAM/slope lagging by 15s during punchy gradient changes and oscillating at low/standstill speeds). Athletes lack a unified kinematic terrain-slope fusion engine ($dh = g \cdot ds$) that couples vertical displacement to horizontal ground progress. ATT-6279 introduces an observability-aware 2-State / 3-State Kalman filter exposed as a pairable `Protocol.SMARTPHONE` virtual sensor (*ATT Fused (Höhe und Steigung)* / *ATT Fused (Altitude & Slope)*) with paired-source UI guards in `EditDeviceDialog.kt`.

---

## 2. Root Cause Analysis & Architectural Gap Analysis
* **Independent Peer Review & Verification**:
  - Live inspection of `AltitudeFromPressureDevice.java` shows `mAltitudeFilter` (`BarometricAltitudeFilter`) applies a 3-stage pipeline (7s median + 1.2 m/s slew clamp + 0.18 EMA). Ingesting the post-EMA output into a secondary Kalman filter introduces cascaded double-EMA phase lag. ATT Fused must ingest the median-filtered and slew-rate clamped raw barometric altitude without the EMA stage.
  - Inspection of `VerticalSpeedAndSlopeDevice.java` shows it computes slope and VAM strictly via $N=15$ linear regression, which has no spatial coupling ($dh/ds$) to speed or distance, resulting in severe noise at low walking speeds ($< 1.2\text{ m/s}$).
  - `DeviceType.java` and `DevicesDatabaseManager.java` lack `ALTITUDE_AND_SLOPE_ATT_FUSED` and schema v12 migration.
  - `MySensorManager.java` lacks top-priority routing for `ALTITUDE` and priority arrays for `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT` overriding `VerticalSpeedAndSlopeDevice` when ATT Fused is active.
  - `EditDeviceDialog.kt` lacks the configuration section with pairing guards for Barometer, DEM, GPS altitude, and speed coupling.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (Kinematic Filter, Virtual Driver & UI Guards — ATT-6279, 5 SP)**:
  - Implement `KinematicAltitudeSlopeEkf`: 2-State mode $[h_k, g_k]^T$ (Baro + Speed) with standstill clamping ($v=0$) and 3-State mode $[h_k, g_k, b_{baro,k}]^T$ (Baro + DEM/GPS + Speed) making barometric thermal/weather drift observable.
  - Expose `BarometricAltitudeFilter.filterSlewOnly()` to avoid cascaded double-EMA phase lag.
  - Register `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED` under `Protocol.SMARTPHONE` with `Devices.db` v12 migration (`PAIRED = 0` default).
  - Implement `AltitudeAndSlopeDevice_ATTFused` driving the EKF and emitting `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`.
  - Integrate into `MySensorManager.java` (`ALTITUDE_PRIORITY` rank 0; dedicated priority lists for `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`).
  - Implement `EditDeviceDialog.kt` educational card and 4 source toggles guarded by paired/hardware state with 9-language localization parity.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Post-workout forward-backward ERTS smoothing pass execution (deferred to ATT-7750).
  - Open-Meteo DEM API client modifications (decoupled via `AltitudeFromPositionDevice` / `ElevationCache.db`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new requirement `REQ-FIL-016` (*Kinematic Terrain-Slope Altitude Fusion Engine & Pairable ATT Fused (Höhe und Steigung) Virtual Device*), extending `REQ-FIL-012` (*Stabilized VAM Metric*), `REQ-SEN-025` (*DEM Elevation*), and `REQ-SEN-026` (*Barometric Elevation*). Targets `KinematicAltitudeSlopeEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `MySensorManager.java`, `DevicesDatabaseManager.java`, and `EditDeviceDialog.kt`.
* **Historical Origin & Commit Trace**: `ATT-1621` / `ATT-1738` (VAM linear regression), `ATT-4859` (DEM altitude device), `ATT-6265` (causal barometric outlier filtering).
* **Root Reason for Existing Formulation**: Prior designs treated barometric elevation, DEM elevation, and vertical speed as decoupled silos without kinematic terrain-slope progression ($dh = g \cdot ds$).
* **Preservation of Core Invariants**: When `ALTITUDE_AND_SLOPE_ATT_FUSED` is unpaired (default), `VerticalSpeedAndSlopeDevice`, `AltitudeFromPressureDevice`, and `MySensorManager` operate 100% identically to baseline. Independent device-attributed sample columns in `WorkoutSamples.db` are preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-FIL-016` (extending `REQ-FIL-012`, `REQ-SEN-025`, `REQ-SEN-026`).
* **Affected Architecture Elements**: New class `KinematicAltitudeSlopeEkf.kt` in `banalservice.odometry`, driver `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, `DeviceType.java`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `EditDeviceDialog.kt`, `strings_devices.xml` (9 locales).
* **Verification Impact & Test Surface**: Unit test suites `KinematicAltitudeSlopeEkfTest.kt`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`, `DevicesDatabaseManagerMigrationV12Test.kt`, `AltitudeSlopePriorityTest.kt`, `EditDeviceDialogAltitudeGuardsTest.kt`. Clean-room test suite verification.
* **Risk Assessment & Invariants**: **LOW-MEDIUM** risk. Virtual sensor is opt-in (`PAIRED = 0`); existing users experience zero behavioral change until explicitly activated in sensor settings.

---

## 6. Architectural Strategy & High-Level Solution
1. **Kinematic Fusion Filter (`KinematicAltitudeSlopeEkf`)**:
   - Discrete transition: $h_k = h_{k-1} + g_{k-1} \cdot \Delta s_k$, with horizontal distance $\Delta s_k = v_k \cdot \Delta t_k$.
   - Process noise: $Q_g = q_g \cdot \Delta s_k$. At standstill ($\Delta s_k = 0$), $Q_g = 0$ and altitude is clamped.
   - Observability switching: In 2-State mode ($[h_k, g_k]^T$), updates via raw slew-clamped barometer $z_{baro} = h_k$. In 3-State mode ($[h_k, g_k, b_{baro,k}]^T$), $z_{baro} = h_k + b_{baro,k}$ and absolute DEM/GPS anchors update $z_{ref} = h_k$, isolating thermal/weather drift into $b_{baro}$ without DEM grid steps.
2. **Device Driver (`AltitudeAndSlopeDevice_ATTFused.kt`)**: Extends `MyDevice`. Subscribes to barometric, DEM, GPS altitude, and speed sources via `MySensorManager`. Emits `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`.
3. **Database Migration (`Devices.db` v12)**: Increment `DevicesDbHelper.DB_VERSION` to 12. Add idempotent `migrateAddAltitudeAndSlopeAttFused()` inserting row with `PAIRED = 0`.
4. **Priority Routing**: Insert at rank 0 of `ALTITUDE_PRIORITY`. Define explicit priority arrays for `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT` prioritizing ATT Fused over `VERTICAL_SPEED_AND_SLOPE`.
5. **UI Source Guards (`EditDeviceDialog.kt`)**: Add educational card and 4 source switches guarded by paired/hardware states.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default unpaired state (`PAIRED = 0`): zero battery or CPU overhead when inactive.
  2. Single-sensor fallback: unpairing ATT Fused instantly restores `VerticalSpeedAndSlopeDevice` with zero residual callbacks.
  3. 9-language localization parity across all new UI strings.
  4. 100% existing unit tests pass without regression.
* **Risk Rating**: **LOW-MEDIUM** (Advanced estimation mathematics encapsulated in modular opt-in virtual device).
