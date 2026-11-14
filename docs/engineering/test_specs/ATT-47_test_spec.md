# Stage 2: Requirement & Test Specification - ATT-47: Indoor GPS suppression & external sensor / IMU rest-variance coupling for stationary coordinate pinning

**Ticket**: [ATT-47](https://rainerblind.atlassian.net/browse/ATT-47)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-TRK-077` (*Indoor GPS Suppression, External Speed Sensor Coupling & IMU Rest-Variance Stationary Pinning*)  
**Test Spec ID**: `TST-TRK-077`  
**Branch**: `feat/ATT-47`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-TRK-077)

### 1.1 Problem Statement & Rationale
GNSS Doppler velocity and spatial displacement in `SpeedAndLocationDevice.java` (`REQ-TRK-048`, `REQ-TRK-056`, `REQ-TRK-074`) evaluate satellite signals alone. During indoor trainer workouts and café/rest stops, multipath reflections create jumps $> 1.5 \times \text{accuracy}$, breaking stationary pinning, tangling spiderweb polylines, and causing false GPS distance creep. `SpeedAndLocationDevice` SHALL support indoor GPS suppression, couple stationary pinning to connected external speed sensors reporting $0.0\text{ m/s}$, and reinforce pinning via low-power IMU rest variance.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
- **REQ-TRK-077.1 (Indoor GPS Suppression)**: `SpeedAndLocationDevice` SHALL maintain `mIsIndoorMode` state (`setIndoorMode(boolean)`). When active, it SHALL pin coordinates to initial fix, block unpinning unconditionally (`shouldUnpin = false`), clamp GPS speed to $0.0\text{ m/s}$, and suppress GPS distance increments (`delta_distance = 0.0`), allowing external speed/distance to record unimpeded.
- **REQ-TRK-077.2 (External Speed Sensor Standstill Coupling)**: In `onNewLocation()`, when an active external speed sensor (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `BIKE_POWER`) is updated within $5\text{ s}$ and reports $\le 0.1\text{ m/s}$, the device SHALL block unpinning and force candidate stationary state, holding `mStationaryAnchor` regardless of GNSS jumps. Releases immediately upon motion ($> 0.1\text{ m/s}$) or stale data ($> 5\text{ s}$).
- **REQ-TRK-077.3 (Accelerometer Rest-Variance Gating)**: When hardware accelerometer (`Sensor.TYPE_ACCELEROMETER`) is present, maintain rolling variance $\sigma^2$ of norm $||a||$ over 1.5–2.0 s. When $\sigma^2 \le 0.05\text{ (m/s}^2)^2$, reinforce stationary pinning against GNSS multipath; release within $\le 1\text{ s}$ once physical motion resumes.
- **REQ-TRK-077.4 (Mock Replay & Neutral Fallback)**: When external sensors and accelerometer are absent, operate with neutral fallback preserving 100% equivalence with baseline `REQ-TRK-048`, `REQ-TRK-056`, and `REQ-TRK-074`.

### 1.4 Acceptance Criteria (Given-When-Then)
- *Given* `SpeedAndLocationDevice` in indoor mode (`mIsIndoorMode == true`), *When* GNSS multipath fixes arrive, *Then* coordinates remain pinned, GPS distance creep is zero, and external sensors supply speed/distance.
- *Given* connected wheel speed sensor reporting $0.0\text{ m/s}$ at a rest stop, *When* indoor GNSS jumps exceed escape radius, *Then* anchor pinning is held without accumulating false coordinates.
- *Given* phone resting motionless with accelerometer variance $\sigma^2 \le 0.05\text{ (m/s}^2)^2$, *When* noisy Doppler fixes arrive, *Then* pinning holds and releases within $\le 1\text{ s}$ when movement resumes.
- *Given* standalone mock GPS replay, *When* location fixes arrive, *Then* baseline unpinning works identically with zero regressions.

---

## 2. Test Specification (TST-TRK-077)

### Test Case 1: Indoor Mode Suppression (`TST-TRK-077.1`)
- **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`
- Assert `setIndoorMode(true)` pins to first location fix, clamps speed to $0.0$, suppresses distance accumulation on $30\text{ m}$ multipath jumps, and keeps published coordinates anchored.

### Test Case 2: External Sensor Zero-Coupling (`TST-TRK-077.2`)
- Assert active wheel sensor reporting $0.0\text{ m/s}$ blocks unpinning on multipath jumps $> \text{escapeRadius}$. Assert updating wheel speed to $5.0\text{ m/s}$ unpins immediately. Assert stale sensor ($> 5000\text{ ms}$) permits normal GPS unpinning.

### Test Case 3: Accelerometer Rest-Variance Gating (`TST-TRK-077.3`)
- Assert low-variance samples ($\sigma^2 \le 0.05$) maintain stationary pinning against Doppler spikes. Assert dynamic samples ($\sigma^2 \ge 0.5$) release the gate within $\le 1.0\text{ s}$.

### Test Case 4: Baseline Regression & Mock Compatibility (`TST-TRK-077.4`)
- Execute `SpeedAndLocationDeviceStationaryJitterTest.kt` with absent external sensors/IMU; assert 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-077.1` | Unit | `SpeedAndLocationDevice.setIndoorMode()` / `onNewLocation()` | `REQ-TRK-077.1` | Specified |
| `TST-TRK-077.2` | Unit | `SpeedAndLocationDevice.isExternalSpeedSensorStationary()` | `REQ-TRK-077.2` | Specified |
| `TST-TRK-077.3` | Unit | `SpeedAndLocationDevice.isAccelerometerAtRest()` | `REQ-TRK-077.3` | Specified |
| `TST-TRK-077.4` | Unit | `SpeedAndLocationDeviceStationaryJitterTest` | `REQ-TRK-077.4` | Specified |
| `TST-TRK-077.5` | Tier 1 Regr | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*"` | `REQ-PRO-001` | Specified |
