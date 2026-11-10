# Stage 3: Implementation Plan - ATT-6256: Google Fused Doppler Velocity Plausibility & Movement Pinning Prevention

**Ticket**: [ATT-6256](https://rainerblind.atlassian.net/browse/ATT-6256)  
**Sub-task**: [ATT-6315](https://rainerblind.atlassian.net/browse/ATT-6315) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6256`  
**Requirement Mapping**: `REQ-TRK-056` (`REQ-TRK-056.1`..`REQ-TRK-056.3`) | **Test Mapping**: `TST-TRK-056` (`TST-TRK-056.1`..`TST-TRK-056.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-09  

---

## 1. Problem Description & Objective
When tracking with Google Fused (`SpeedAndLocationDevice_GoogleFused`), Android fuses GNSS coordinates with device IMUs. In mock/replay runs or during smooth movement on a steady bike mount, the provider emits Doppler speed near or at zero (`rawSpeed <= 0.1 m/s` or `0.10 < rawSpeed <= 0.25 m/s`) despite true displacement exceeding 7–10 m/s. Prior logic in `SpeedAndLocationDevice.onNewLocation()` unconditionally pinned the stationary anchor on Doppler zero speed without checking displacement, causing an oscillation loop toggling between 0.0 km/h and ~25–30 km/h every second, or sticking at 0.5 km/h. This plan implements spatial displacement gating before anchor pinning and provides geometric velocity fallback for zero/damped Doppler speed during active movement.

---

## 2. Requirements & Living Documentation Mapping
* **`REQ-TRK-056.1` (Movement-Aware Stationary Pinning Gating)**: In `SpeedAndLocationDevice.onNewLocation()`, when `isDopplerStationary` is true, establish stationary pinning ONLY IF `mPrevLocation == null` or `rawDelta <= escapeRadius` where `escapeRadius = Math.max(3.0, 1.5 * location.getAccuracy())`. When `rawDelta > escapeRadius`, treat as valid movement.
* **`REQ-TRK-056.2` (Damped/Zero Speed Geometric Velocity Fallback)**: When unpinned or when unpinning from an anchor (`rawDelta > escapeRadius`), if `!hasSpeed || rawSpeed <= SPEED_ZERO_THRESHOLD || (rawSpeed < 0.2 * calcSpeed && calcSpeed > 1.0)`, assign `speed = calcSpeed = delta_distance / (dt_ms / 1000.0)` (gated by $\le 70.0\text{ m/s}$ teleportation guard in `REQ-TRK-041.1`).
* **`REQ-TRK-056.3` (Standstill & Jitter Pinning Preservation)**: When genuine standstill occurs (`rawDelta <= escapeRadius`), immediately clamp speed to 0.0 m/s, pin anchor coordinates, suppress distance creep, and lock bearing (`REQ-TRK-048.1`..`REQ-TRK-048.3`).
* **Test Mapping**: `TST-TRK-056.1` (Zero Doppler Movement), `TST-TRK-056.2` (Damped Doppler Fallback), `TST-TRK-056.3` (Standstill Jitter), `TST-TRK-056.4` (Teleportation Guard), `TST-TRK-056.5` (Targeted Suite).

---

## 3. System Invariants & UI Consistency
1. **Standstill Invariant**: True standstill at red lights (`rawDelta <= escapeRadius`) pins immediately with zero distance creep and locked bearing (`REQ-TRK-048`).
2. **Teleportation Spike Protection**: Large multipath jumps exceeding $70.0\text{ m/s}$ (252 km/h) are clamped with `delta_distance = 0.0` (`REQ-TRK-041.1`).
3. **UI Consistency**: Zero UI modifications (`docs/design_guidelines.md` §5). Pure backend telemetry precision fix in `SpeedAndLocationDevice`.

---

## 4. Architectural Strategy & Component Design
* **`SpeedAndLocationDevice.java` (`onNewLocation()`)**:
  * Define `escapeRadius = Math.max(3.0, 1.5 * location.getAccuracy())` early so it is accessible in both pinned and unpinned branches.
  * In the unpinned branch (`!mIsStationaryPinned`):
    * Change `if (isDopplerStationary)` to `if (isDopplerStationary && (mPrevLocation == null || rawDelta <= escapeRadius))`.
    * In the legitimate movement `else` block: when calculating `speed`, if `!hasSpeed || rawSpeed <= SPEED_ZERO_THRESHOLD || (rawSpeed < 0.2 * calcSpeed && calcSpeed > 1.0)`, assign `speed = calcSpeed`.
  * In the anchor unpinning branch (`mIsStationaryPinned && shouldUnpin`):
    * Compute elapsed time from anchor: `long dtAnchor_ms = (mStationaryAnchor != null && location.getTime() > mStationaryAnchor.getTime()) ? (location.getTime() - mStationaryAnchor.getTime()) : dt_ms;` for accurate anchor velocity calculation.
    * Fall back to `calcSpeed` if `!hasSpeed || rawSpeed <= SPEED_ZERO_THRESHOLD || (rawSpeed < 0.2 * calcSpeed && calcSpeed > 1.0)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification
* Verify Gate 3 sign-off before modifying production source code: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.

### Step 2: Implement Movement-Aware Pinning & Damped Speed Fallback in `SpeedAndLocationDevice.java`
* Update `onNewLocation(Location)` in `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java`:
  * Add spatial displacement guard (`rawDelta <= escapeRadius`) to `isDopplerStationary` anchor pinning.
  * Implement geometric fallback speed for zero or damped Doppler speed when `rawDelta > escapeRadius`.
  * Use anchor-relative elapsed time `dtAnchor_ms` when computing unpinning speed.

### Step 3: Implement Unit Tests in `SpeedAndLocationDeviceStationaryJitterTest.kt`
* Add `testGoogleFusedZeroSpeedDuringMovement_preventsTogglingAndCalculatesVelocity()` verifying consecutive fixes advancing at 8 m/s with `speed = 0.0f` maintain unpinned state, smooth non-zero velocity, and steady distance.
* Add `testGoogleFusedDampedSpeedDuringMovement_fallsBackToGeometricSpeed()` verifying fixes with `speed = 0.15f` substitute `calcSpeed`.
* Add `testStandstillJitterWithinEscapeRadius_pinsImmediatelyAndLocksBearing()` asserting standstill invariants (`REQ-TRK-048`).

### Step 4: Targeted Verification & Governance Parity
* Run targeted unit test suite: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice*"`.
* Run living documentation governance check: `python3 tools/parallel_sprint_lock.py python3 tools/verify_requirement_governance.py`.
