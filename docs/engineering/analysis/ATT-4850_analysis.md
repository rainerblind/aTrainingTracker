# Stage 1 Analysis: ATT-4850 - Eliminate Stationary GPS Spider-Map Jitter, Phantom Speed Overrides, and Creeping Distance

**Ticket**: [ATT-4850](https://rainerblind.atlassian.net/browse/ATT-4850)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4850`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
When athletes stop (traffic lights, cafés, or indoor pauses), GNSS multipath reflections cause horizontal fixes to wander $1\text{--}4\text{ m}$ randomly. Currently, `SpeedAndLocationDevice` overrides genuine GNSS Doppler zero speed (`location.hasSpeed() && location.getSpeed() == 0.0f`) with geometric speed `delta_distance / dt` whenever `delta_distance > 0.1 m`, generating fake $5\text{--}14\text{ km/h}$ speed spikes, waking workouts from Auto-Pause, accumulating kilometres of creeping distance, spinning compass bearing, and recording dense "spider-web" map breadcrumbs in `BANALServiceRepository`.

---

## 2. Architectural Gap Analysis & Forensic Investigation
* **Root Cause 1: Disregard of GNSS Doppler Zero Velocity (`SpeedAndLocationDevice.java:322-332`)**:
  * GNSS carrier Doppler shift accurately detects zero velocity ($\pm 0.05\text{ m/s}$). Overriding `speed == 0.0` with `delta_distance / dt` on small multipath jitter steps ($> 0.1\text{ m}$) corrupts stationary states.
* **Root Cause 2: Unconditional Distance Accumulation (`SpeedAndLocationDevice.java:308-309`)**:
  * `mDistanceSensor.increment(delta_distance)` adds displacement on every 1-Hz fix without testing against the horizontal accuracy noise floor ($0.6 \cdot \text{accuracy}$) or stationary status, causing distance creep ($>3\text{ km/h}$ while sitting still).
* **Root Cause 3: Unpinned Coordinates & Bearing Whirl (`SpeedAndLocationDevice.java:288-320`)**:
  * Raw coordinates are published without anchor pinning; bearing recalculates whenever `delta_distance > 0.5 m`, spinning the map orientation uncontrollably.
* **Root Cause 4: Spider-Map Breadcrumb Recording (`BANALServiceRepository.kt:371-403`)**:
  * Appends coordinates to `_currentTrack` whenever `_currentLocation.value != newLocation`, drawing spiderwebs during stops.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Objectives**:
  1. Doppler Zero Preservation: Preserve `0.0 m/s` when `location.hasSpeed() && location.getSpeed() <= SPEED_ZERO_THRESHOLD`. Only compute geometric speed if `!location.hasSpeed()` and displacement exceeds deadband.
  2. Stationary Anchor Pinning & Accuracy Deadband: When stationary, pin `(LATITUDE, LONGITUDE)` to an anchor. Suppress distance accumulation, bearing updates, and breadcrumbs until displacement exceeds deadband radius $R = \max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$.
  3. Adaptive 2D Smoothing: Apply low-speed position filter weighted by accuracy and speed to keep low-speed tracks smooth without high-speed cornering lag.
* **Out-of-Scope Non-Goals**:
  1. Double integration of raw IMU accelerations in Java (ruled out by Epic ATT-166 as drift-prone).
  2. Modifying ANT+/BLE speed sensor pipelines (wheel sensors already take precedence via `MySensorManager`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: Net-new `REQ-TRK-048` (Stationary Jitter Suppression, Doppler Zero Preservation & Anchor Pinning).
* **Chesterton's Fence**:
  * `REQ-TRK-013` (Speed Decay Watchdog): Preserved; watchdog continues to decay stale speed if fixes cease.
  * `REQ-TRK-041.1` (Teleportation Spike Gating $> 70\text{ m/s}$): Preserved; spike rejection remains active.
  * `REQ-TRK-042.1` (PathPoint advancing logic): Preserved; benefits directly from anchored coordinates.

---

## 5. Architectural Strategy & High-Level Solution
* **SpeedAndLocationDevice.java**:
  * Introduce `mStationaryAnchor: Location?` and `mIsStationaryPinned: Boolean`.
  * If `location.hasSpeed()` and `location.getSpeed() <= SPEED_ZERO_THRESHOLD`: clamp speed to `0.0 m/s`, set/keep anchor, suppress `mDistanceSensor.increment`, lock `mBearingSensor`, publish anchor coordinates.
  * If displacement from anchor exceeds $\max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$: unpin anchor, resume distance accumulation, and update bearing.
  * For providers without Doppler (`!location.hasSpeed()`): compute `delta_distance / dt` only when displacement exceeds deadband.
* **BANALServiceRepository.kt**:
  * Inherits stabilized anchored coordinates naturally; add stationary guard to prevent spiderweb line segments.

---

## 6. System Invariants & Risk Assessment
* **Invariants**: Zero distance creep while stationary; valid non-Doppler geometric fallback; zero regression on decay watchdog or teleportation gating; 100% clean-room test pass rate.
* **Risk Rating**: **MEDIUM** — Core location and distance processing. Mitigated by comprehensive synthetic unit test suite covering stationary jitter, provider without Doppler, transition from stationary to movement, and high-speed cornering.
