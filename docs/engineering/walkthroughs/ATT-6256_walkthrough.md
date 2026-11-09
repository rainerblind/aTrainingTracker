# Stage 5 Walkthrough: ATT-6256 - Google Fused Doppler Velocity Plausibility & Stationary Pinning Prevention

## Overview
- **Ticket**: [ATT-6256](https://rainerblind.atlassian.net/browse/ATT-6256) (`[High] SpeedAndLocationDevice (Google Fused): Prevent speed toggling between 0.0 km/h and plausible speed`)
- **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)
- **Target Release**: `V5.0.0`
- **Fulfills Requirements**: `REQ-TRK-056` (`REQ-TRK-056.1`..`REQ-TRK-056.3`), `REQ-TRK-041.1`, `REQ-TRK-048`
- **Fulfills Tests**: `TST-TRK-056` (`TST-TRK-056.1`..`TST-TRK-056.5`)
- **Implementation Subtask**: ATT-6332

---

## Changes Implemented

### 1. Spatial Movement Gating & Damped Velocity Fallback (`SpeedAndLocationDevice.java`)
- **Movement-Aware Pinning Gating (`REQ-TRK-056.1`)**: Computed `escapeRadius = Math.max(3.0, 1.5 * location.getAccuracy())` early in `onNewLocation()`. When `isDopplerStationary` is reported (`hasSpeed && rawSpeed <= SPEED_ZERO_THRESHOLD`), the stationary anchor is only pinned if `mPrevLocation == null || rawDelta <= escapeRadius`. Active movement (`rawDelta > escapeRadius`) is never pinned.
- **Damped / Zero Doppler Velocity Fallback (`REQ-TRK-056.2`)**: When moving or unpinning, if the provider reports zero or damped Doppler speed (`!hasSpeed || rawSpeed <= SPEED_ZERO_THRESHOLD || (rawSpeed < 0.2 * calcSpeed && calcSpeed > 1.0)`), the device falls back to geometric velocity `calcSpeed = delta_distance / (dt_ms / 1000.0)`.
- **Standstill Invariant Preserved (`REQ-TRK-056.3`)**: True standstill within the accuracy noise floor (`rawDelta <= escapeRadius`) pins immediately, clamping speed to `0.0 m/s`, suppressing distance creep, and locking bearing (`REQ-TRK-048`).
- **Teleportation Protection (`REQ-TRK-041.1`)**: Multipath teleportation spikes exceeding `70.0 m/s` (252 km/h) are clamped to `delta_distance = 0.0`.

### 2. Contract Unit Tests (`SpeedAndLocationDeviceStationaryJitterTest.kt`)
- `TST-TRK-056.1`: Verified 8 m/s movement with `hasSpeed=true, speed=0.0f` prevents 0.0 km/h toggling and accumulates distance steadily.
- `TST-TRK-056.2`: Verified damped Doppler speed (0.15 m/s) falls back to geometric velocity (8.0 m/s).
- `TST-TRK-056.3`: Verified immediate pinning, speed clamping, distance suppression, and locked bearing upon standstill within escape radius.
- `TST-TRK-056.4`: Verified teleportation jumps (> 70 m/s) clamp displacement to zero.

---

## Verification & Test Results

### Tier 1 Targeted Unit Test Scope (`Rule 60`)
- **Scope**: Targeted unit tests covering modified device classes and core contract dependencies (`com.atrainingtracker.banalservice.devices.*` and `com.atrainingtracker.trainingtracker.build.*`).
- **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
- **Result**: `BUILD SUCCESSFUL` (100% PASS, 0 failures, 0 errors).

### Governance & Living Documentation
- **Living Document Sync**: `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py`
- **Result**: `PASS: Master index tables successfully synchronized with 100% parity` (Total living tests: 932, 932 Verified; Total living requirements: 930, 924 Verified, 6 Planned).
- **Physical Device Deployment**: Skipped in accordance with `Rule 60` / `stage5-verification/SKILL.md` (pure backend mathematical telemetry fix in `SpeedAndLocationDevice`, zero UI/layout/resource changes).
