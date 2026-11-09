# Stage 1 Analysis: ATT-6256 - SpeedAndLocationDevice (Google Fused): Prevent speed toggling between 0.0 km/h and plausible speed when provider reports zero/damped Doppler speed during active movement

**Ticket**: [ATT-6256](https://rainerblind.atlassian.net/browse/ATT-6256)  
**Sub-task**: [ATT-6312](https://rainerblind.atlassian.net/browse/ATT-6312) (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6256`  
**Requirement Mapping**: `REQ-TRK-056` (extending `REQ-TRK-048`)  
**Test Mapping**: `TST-TRK-056`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-09  

---

## 1. Problem Statement & Motivation
When tracking with Google Fused (`SpeedAndLocationDevice_GoogleFused`)—especially during mock/replay runs or when IMU sensor fusion damps Doppler speed—speed (`SPEED_mps`) oscillates at 1 Hz between 0.0 km/h and plausible speed (~25–30 km/h), or remains stuck at ~0.5 km/h while distance steadily accumulates ~8 m/s. Direct GPS (`SpeedAndLocationDevice_GPS`) does not toggle because GPS chips report undamped Doppler speed. Expected behavior: when coordinates advance beyond the GPS noise/accuracy floor (`rawDelta > escapeRadius`), active movement must be recognized smoothly without toggling to 0.0 km/h, and geometric fallback speed must be used when Doppler speed is zero or damped.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
In `SpeedAndLocationDevice.java` (`onNewLocation()`, L318–408):
1. **Unconditional Doppler Pinning (L373)**: When unpinned (`!mIsStationaryPinned`), `isDopplerStationary = hasSpeed && (rawSpeed <= 0.1)` pins the anchor immediately (`mIsStationaryPinned = true`, `delta_distance = 0.0`, `speed = 0.0`) without checking if `rawDelta` exceeds `escapeRadius = Math.max(3.0, 1.5 * accuracy)`. Even with an 8 m displacement, the device is forced into stationary pinning.
2. **1-Hz Oscillation Cycle**: Next second ($t+1$), `distFromAnchor > escapeRadius` unpins the device (L337), computing geometric speed `calcSpeed` (~8 m/s) and advancing distance. At $t+2$, because the device is now unpinned, L373 immediately re-pins to the anchor on the next zero/damped fix. This creates a perpetual 1-Hz toggling between pinned (0 km/h) and unpinned (~29 km/h).
3. **Damped Speed Sub-Case (L397)**: If Google Fused reports `0.10 < rawSpeed <= 0.25 m/s` (e.g. 0.15 m/s), `isDopplerStationary` is false, but L397 assigns `speed = rawSpeed` (0.5 km/h) unconditionally, ignoring geometric displacement `calcSpeed` (~8 m/s).
* **Independent Peer Review & Verification**:
  * Live code audit confirms L373 lacks a `rawDelta > escapeRadius` motion guard.
  * Verified `testBuggyProvider_stuckZeroSpeed_unpinsWhenDisplacementExceedsEscapeRadius` in `SpeedAndLocationDeviceStationaryJitterTest.kt` only verified the single unpinning transition, missing the subsequent re-pinning cycle at $t+2$.
  * Also verified that anchor unpinning (L349, L359) should evaluate elapsed time from `mStationaryAnchor` (`location.getTime() - mStationaryAnchor.getTime()`) rather than `mPrevLocation` when computing unpinning speed across multi-epoch rests.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Guard stationary pinning in `SpeedAndLocationDevice.onNewLocation()`: do not pin if `rawDelta > escapeRadius` with plausible movement speed.
  * Fall back to geometric speed (`calcSpeed`) when `rawDelta > escapeRadius` and provider reports zero or implausibly damped speed (`rawSpeed <= 0.1` or `rawSpeed < 0.2 * calcSpeed`).
  * Preserve instant Doppler clamping and stationary anchor pinning when true standstill occurs (`rawDelta <= escapeRadius`).
  * Add multi-step regression tests in `SpeedAndLocationDeviceStationaryJitterTest.kt`.
* **Out-of-Scope Non-Goals**:
  * No changes to `SpeedAndLocationDevice_GoogleFused` registration or API client.
  * No changes to `TrackerService` database persistence, UI, or `MySensorManager` priority rankings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-056` (*Google Fused Doppler Velocity Plausibility & Movement Pinning Prevention*), refining `REQ-TRK-048` (*Stationary GPS Jitter Suppression, Doppler Zero Preservation & Anchor Pinning*).
* **Historical Origin & Commit Trace**: `REQ-TRK-048` was introduced in Sprint 2026-44.2 (`ATT-4850`). It prioritized Doppler zero to suppress GPS multipath jitter (1–4 m wander at traffic lights).
* **Root Reason for Existing Formulation**: Standalone GNSS Doppler velocity reliably indicates stationary state. However, Google Fused fuses phone IMUs, which can report zero or damped speed when held steady or replayed, despite valid coordinate progression.
* **Preservation of Core Invariants**: Standstill at red lights (`rawDelta <= escapeRadius`) still immediately pins (`speed = 0.0`, distance suppressed, bearing locked). Teleportation spike limit $\le 70\text{ m/s}$ (`REQ-TRK-041.1`) remains strictly enforced.

---

## 5. Architectural Strategy, Solution & Side-Effect Audit
1. **Movement-Aware Pinning Gating**: In `SpeedAndLocationDevice.onNewLocation()`, only pin anchor on `isDopplerStationary` if `rawDelta <= escapeRadius` (or `mPrevLocation == null`). If `rawDelta > escapeRadius`, treat as valid movement.
2. **Damped/Zero Speed Geometric Fallback**: When unpinned and `rawDelta > escapeRadius`, if `!hasSpeed || rawSpeed <= SPEED_ZERO_THRESHOLD || (rawSpeed < 0.2 * calcSpeed && calcSpeed > 1.0)`, assign `speed = calcSpeed` (subject to 70 m/s spike guard).
3. **Side-Effect Audit**:
   - `AutoPauseManager`: Eliminates false pause/resume toggling during Google Fused movement.
   - `Cockpit Tiles & Map`: Eliminates 1-Hz speed flicker and ensures smooth pace/speed telemetry.
   - `mDistanceSensor`: Smooth distance accumulation without skipped seconds.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Instant Doppler zero velocity clamping and distance suppression when stationary (`rawDelta <= escapeRadius`).
  2. Teleportation spike guard ($\le 70\text{ m/s}$) remains intact.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM** (Core speed/location dispatch in `SpeedAndLocationDevice.java`; mitigated by comprehensive existing and net-new unit tests in `SpeedAndLocationDeviceStationaryJitterTest.kt`).
