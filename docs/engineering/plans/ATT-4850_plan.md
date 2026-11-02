# Stage 3: Implementation Plan - ATT-4850: Eliminate Stationary GPS Spider-Map Jitter

**Ticket**: [ATT-4850](https://rainerblind.atlassian.net/browse/ATT-4850)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-048`  
**Test Mapping**: `TST-TRK-048`  
**Branch**: `feature/ATT-4850`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
When athletes stop, GPS multipath jitter (1–4 m/s) overrides true GNSS Doppler zero speed with fake 5–14 km/h geometric speed, accumulates creeping distance, spins bearing, wakes Auto-Pause, and records dense spiderweb track artifacts in `BANALServiceRepository`. We implement Doppler zero velocity preservation, stationary anchor pinning with accuracy-scaled deadband, and adaptive 2D position smoothing.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-048.1**: Respect GNSS Doppler zero speed (`location.hasSpeed() && location.getSpeed() <= SPEED_ZERO_THRESHOLD`).
* **REQ-TRK-048.2**: Stationary anchor pinning and distance gating within deadband $R = \max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$.
* **REQ-TRK-048.3**: Stationary bearing lock and stabilized anchor coordinate emission.
* **REQ-TRK-048.4**: Provider without Doppler fallback (`!hasSpeed()`) beyond deadband.
* **REQ-TRK-048.5**: Spider-map breadcrumb suppression in `BANALServiceRepository.kt`.

---

## 3. System Invariants & Invariant Protections
1. **Speed Decay Watchdog (`REQ-TRK-013`)**: Inactivity watchdog timeout (10s) and exponential decay remain active if fixes halt.
2. **Teleportation Spike Gating (`REQ-TRK-041.1`)**: Guard against speeds $> 70.0\text{ m/s}$ ($252\text{ km/h}$) remains strictly enforced.
3. **High-Speed Cornering**: Adaptive smoothing weight $\alpha = 1.0$ for speed $\ge 3.0\text{ m/s}$, introducing zero corner-cutting lag.
4. **Clean-Room Test Pass Rate**: 100% pass across targeted unit tests without `--rerun-tasks`.
5. **UI Consistency (Rule 23)**: No UI changes (backend sensor and repository pipeline only).

---

## 4. Proposed Architectural Changes
* **SpeedAndLocationDevice.java**:
  * Track `mStationaryAnchor: Location?`, `mIsStationaryPinned: boolean`, `mFilteredLat/Lon: double`.
  * Calculate dynamic accuracy deadband $R = \max(3.0\text{ m},\, 0.6 \cdot \text{location.getAccuracy()})$.
  * When `hasSpeed && speed <= SPEED_ZERO_THRESHOLD`: pin anchor, clamp speed to `0.0 m/s`, suppress distance accumulation, lock bearing, and emit pinned anchor coordinates.
  * When stationary and displacement $> R$ with true movement: unpin anchor and resume normal tracking.
  * For `!hasSpeed()`: compute geometric speed `delta_distance / dt` only when displacement $> R$.
  * Apply adaptive 2D smoothing ($\alpha = \text{clamp}(speed / 3.0, 0.2, 1.0)$) to published coordinates.
* **BANALServiceRepository.kt**:
  * Guard `_currentTrack` appends when stationary and displacement from last point $\le 3.0\text{ m}$.

---

## 5. Step-by-Step Implementation Sequence
* **Step 1: Stationary Pinning & Doppler Zero Logic in SpeedAndLocationDevice.java**:
  * Add anchor state fields, deadband calculation, Doppler zero clamping, distance suppression, and bearing lock.
* **Step 2: Adaptive 2D Smoothing & Non-Doppler Fallback in SpeedAndLocationDevice.java**:
  * Implement adaptive $\alpha$-filter for low-speed navigation and steady non-Doppler geometric fallback.
* **Step 3: Repository Spiderweb Suppression in BANALServiceRepository.kt**:
  * Add stationary displacement guard to `_currentTrack` / `_currentPathPoints` loop.
* **Step 4: Unit Verification & Regression Testing (`TST-TRK-048.1`..`5`)**:
  * Create `SpeedAndLocationDeviceStationaryJitterTest.kt`.
  * Update `BANALServiceRepositoryTest.kt` for stationary track suppression.
  * Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted package unit tests and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Isolated branch `feature/ATT-4850` allows instantaneous revert without risk to `sprint/2026-44.2`.
