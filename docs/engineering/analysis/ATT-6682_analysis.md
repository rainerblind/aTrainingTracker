# Stage 1 Analysis: ATT-6682 - SpeedAndLocationDevice: Require entry hysteresis and ignore sub-second duplicate callbacks before stationary pinning

**Ticket**: [ATT-6682](https://rainerblind.atlassian.net/browse/ATT-6682)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-6682`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During on-device simulation testing on the Pixel 10 (`66020DLCR002FL`) following sprint integration of `ATT-6256`, the speed telemetry emitted by `GoogleFusedSpeedAndLocationDevice` still dropped to `0.0 km/h` regularly while moving.

Forensic analysis of the live tracking engine reveals that despite the escape radius gating introduced in `ATT-6256` (`REQ-TRK-056.1`), active movement is frequently interrupted by spurious stationary pinning, zeroing published speed and freezing distance progression.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  1. **Sub-second Duplicate Callbacks (`rawDelta == 0.0`, `dt_ms < 250 ms`)**:
     * When Google Play Services `FusedLocationProviderClient` emits secondary/duplicate location callbacks (typically 50–150 ms after the primary 1 Hz fix) with identical coordinates (`rawDelta == 0.0`) and `speed == 0.0f` (due to resting phone IMU sensors):
     * In `SpeedAndLocationDevice.onNewLocation()` (lines 372–378), because `rawDelta == 0.0 <= escapeRadius` and `isDopplerStationary == true`, the device immediately enters stationary pinning (`mIsStationaryPinned = true`, `speed = 0.0`), overwriting legitimate moving speed (e.g. 25–30 km/h) with `0.0 km/h`.
  2. **Single-Sample Pinning Without Entry Hysteresis & Asymmetric Threshold Leakage**:
     * In `SpeedAndLocationDevice.java:372`:
       `if (isDopplerStationary && (mPrevLocation == null || rawDelta <= escapeRadius))`
     * `escapeRadius = Math.max(3.0, 1.5 * location.getAccuracy())`. At 2.5 m accuracy, `escapeRadius = 3.75 m` (13.5 km/h); at 4.0 m accuracy, `escapeRadius = 6.0 m` (21.6 km/h).
     * When climbing a hill or cycling at moderate speeds ($\le 13.5\text{--}21.6\text{ km/h}$), 1-second coordinate displacement is naturally $\le \text{escapeRadius}$.
     * A single spurious zero-speed fix from Google Fused immediately transitions the unpinned moving device (`!mIsStationaryPinned`) into pinned state on that single tick.
     * The exit threshold (`distFromAnchor > escapeRadius`) was conflated with the entry threshold (`rawDelta <= deadbandRadius`), and no multi-tick entry hysteresis existed to filter transient zero-velocity noise.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Filter out duplicate and sub-interval location callbacks in `SpeedAndLocationDevice.onNewLocation()`: ignore fixes where `dt_ms < 250` or `(rawDelta == 0.0 && dt_ms < 900)`.
  2. Separate the entry threshold for stationary pinning (`rawDelta <= deadbandRadius`) from the exit threshold (`distFromAnchor > escapeRadius`).
  3. Introduce entry hysteresis: require 3 consecutive updates (`STATIONARY_PINNING_HYSTERESIS_TICKS = 3`) satisfying `isDopplerStationary && rawDelta <= deadbandRadius` (or non-Doppler stationary condition) before transitioning from moving (`!mIsStationaryPinned`) to pinned (`mIsStationaryPinned = true`).
  4. Preserve immediate initial pinning when `mPrevLocation == null` to prevent startup GPS spiderwebs.
  5. Expand `SpeedAndLocationDeviceStationaryJitterTest.kt` with comprehensive unit tests for sub-second duplicate rejection, 3-tick entry hysteresis, hill climbing resilience, and true standstill verification.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to database schemas or ViewModel state flows.
  * No changes to `AltitudeFromPressureDevice` or topographic elevation models.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-TRK-074` (*Duplicate Location Callback Filtering & Stationary Pinning Entry Hysteresis*), amending `REQ-TRK-048.1` and `REQ-TRK-056.1` in `SpeedAndLocationDevice.java`.
* **Historical Origin & Commit Trace**:
  * `REQ-TRK-048` (`ATT-4850`, Sprint 2026-44.2): Prioritized Doppler zero velocity to eliminate multipath jitter at red lights.
  * `REQ-TRK-056` (`ATT-6256`, Sprint 2026-45.13): Added escape radius check to prevent pinning during high-speed Google Fused movement.
* **Root Reason for Existing Formulation**: `REQ-TRK-056.1` assumed `rawDelta > escapeRadius` would protect all movement, but overlooked sub-second duplicates and low-speed climbing where $v < 15\text{ km/h}$.
* **Preservation of Core Invariants**:
  * Initial fix (`mPrevLocation == null`) pins immediately to protect the starting line.
  * Genuine standstill (3 consecutive ticks $\le \text{deadbandRadius}$) pins cleanly, clamps speed to 0.0, locks bearing, and suppresses distance increments (`REQ-TRK-048.1`..`REQ-TRK-048.3`).
  * Teleportation spike protection ($\le 70\text{ m/s}$, `REQ-TRK-041.1`) preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Sub-interval & Duplicate Callback Guard**:
   In `SpeedAndLocationDevice.onNewLocation()`:
   ```java
   if (mPrevLocation != null) {
       long dt_ms = location.getTime() - mPrevLocation.getTime();
       double rawDelta = mPrevLocation.distanceTo(location);
       if (dt_ms < 250 || (rawDelta == 0.0 && dt_ms < 900)) {
           return;
       }
   }
   ```
2. **Entry Hysteresis State Machine**:
   * State variable: `private int mStationaryHysteresisCount = 0;`
   * Constant: `public static final int STATIONARY_PINNING_HYSTERESIS_TICKS = 3;`
   * When `mIsStationaryPinned`: `mStationaryHysteresisCount = 0`. If `shouldUnpin`, unpin and reset count.
   * When `!mIsStationaryPinned`:
     * If `mPrevLocation == null && (isDopplerStationary || !hasSpeed)`: pin immediately.
     * Else if candidate stationary: `(isDopplerStationary && rawDelta <= deadbandRadius) || (!hasSpeed && rawDelta <= deadbandRadius && mSpeed <= SPEED_ZERO_THRESHOLD)`:
       * Increment `mStationaryHysteresisCount++`.
       * If `mStationaryHysteresisCount >= STATIONARY_PINNING_HYSTERESIS_TICKS`: pin anchor (`mIsStationaryPinned = true`, `mStationaryAnchor = location`, `delta_distance = 0.0`, `speed = 0.0`).
       * Else (`count < 3`): treat as movement (`delta_distance = rawDelta`, compute `calcSpeed`, fall back to geometric speed).
     * Else (`rawDelta > deadbandRadius` or `rawSpeed > SPEED_ZERO_THRESHOLD`):
       * `mStationaryHysteresisCount = 0` (reset streak). Treat as legitimate movement.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing stationary jitter suppression (`REQ-TRK-048`).
  2. Initial startup fix always pins anchor to suppress start-line wandering.
  3. Thread safety preserved on sensor dispatchers.
  4. Parent ticket human decision gate strictly enforced.
* **Risk Rating**: **MEDIUM** (core sensor tracking velocity calculation; mitigated by 100% unit test coverage in `SpeedAndLocationDeviceStationaryJitterTest.kt`).
