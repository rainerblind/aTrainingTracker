# Stage 2: Requirement & Test Specification - ATT-4802: Speed toggles periodically between plausible value and near-zero on 7-8s location update intervals

**Ticket**: [ATT-4802](https://rainerblind.atlassian.net/browse/ATT-4802)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4802`  
**Requirement Mapping**: `REQ-TRK-013` (GPS Speed Inactivity Watchdog, Gradual Speed Decay & Pace Nullification Architecture)  
**Test Spec ID**: `TST-TRK-005` (GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-24  

---

## 1. Requirement Specification (`REQ-TRK-013`)

### 1.1 Problem Statement & Rationale
When location updates arrive on a 7–8s cadence (e.g., degraded GNSS, Google Fused on Wi-Fi/cell, or battery-saver batching), `SpeedAndLocationDevice`'s hardcoded 5-second watchdog (`INACTIVITY_TIMEOUT_MS = 5000L`) fires prematurely, halving `mSpeed` toward zero before the next valid fix arrives. Upon arrival, `mSpeed = (mSpeed + speed) / 2` averages incoming velocity with the decayed artifact, causing periodic sawtooth drops (10–21 km/h down to 0–4 km/h). Increasing the watchdog timeout to 10.0s, decoupling decay state from speed smoothing, and hardening provider update intervals prevents false-positive speed collapse while preserving stopped-state decay.

### 1.2 Functional & Architectural Requirements
1. **Watchdog Timeout Adaptation (`REQ-TRK-013.1`)**: In `SpeedAndLocationDevice.java`, `INACTIVITY_TIMEOUT_MS` SHALL be increased to 10.0 seconds (`10000L`). When 10s elapse without a valid fix, `mIsDecaying = true` SHALL be set and exponential decay triggered.
2. **Decoupled Speed Recovery & Smoothing (`REQ-TRK-013.4`)**: In `SpeedAndLocationDevice.onNewLocation()`, upon receiving a valid fix, if `mIsDecaying` was true, `mPrevLocation == null`, or $\Delta t \ge 3000\text{ ms}$, the system SHALL assign `mSpeed = speed` directly and reset `mIsDecaying = false`. Standard smoothing `(mSpeed + speed) / 2` SHALL strictly apply only to continuous fixes within nominal intervals ($\Delta t < 3000\text{ ms}$) without prior decay.
3. **Google Fused Interval Hardening (`REQ-TRK-013.5`)**: In `SpeedAndLocationDevice_GoogleFused.java`, `mLocationRequest.setFastestInterval(SAMPLING_TIME)` (1000 ms) SHALL be explicitly set alongside `setInterval(SAMPLING_TIME)`.
4. **Replay Tooling Provider Synchronization (`REQ-TRK-013.5`)**: In `tools/replay_workout.py`, `ReplayEngine.step()` SHALL suppress dispatching new location updates upon reaching EOF, and mock location provider cleanup in `MockGpsManager.release_providers()` SHALL be thread-safe with worker loop processing to prevent test providers from lingering.
5. **Preserved Invariants**: Distance accumulator sensors (`mDistanceSensor`, `mLapDistanceSensor`) MUST NOT accumulate phantom distance; true stops decay speed to 0.0 m/s; pace nullifies cleanly without division-by-zero.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-013` (GPS Speed Inactivity Watchdog, Gradual Speed Decay & Pace Nullification Architecture [`TST-TRK-005`]).
* **Historical Origin & Commit Trace**: Introduced in `ATT-2616` (commit `de8f92c4`, sprint `sprint/2026-41.3`); amended in `ATT-4802` (`sprint/2026-43.10`).
* **Root Reason for Existing Formulation**: A 5.0-second timeout was originally selected assuming continuous 1-Hz GPS updates, where 5 missing samples reliably signaled an athlete stop or tunnel entry, preventing speed from remaining frozen indefinitely.
* **Preservation of Core Invariants**: Stationary athletes still decay to 0.0 m/s and nullify pace without division-by-zero; distance accumulators never accumulate phantom distance; genuine GPS fixes take precedence. Extending timeout to 10.0s and bypassing smoothing after decay avoids false-positive speed collapse on 7-8s multi-second location cadences.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (10s Watchdog & Cadence Immunity)**: *Given* an athlete tracking speed via GPS, *When* location fixes arrive at 7–8 second intervals with active displacement ($\Delta d > 0$), *Then* the watchdog SHALL NOT trigger speed decay and speed SHALL remain stable.
* **Criterion 2 (Decoupled Speed Recovery)**: *Given* an inactive decaying speed state (`mIsDecaying = true`), *When* a new valid location fix is received, *Then* `mSpeed` SHALL be set directly to the fix's speed without averaging against the decayed watchdog value.
* **Criterion 3 (True Stop Decay & Pace Nullification)**: *Given* an athlete stopping at a signal, *When* 10.0s elapse without a valid fix, *Then* speed SHALL decay exponentially every 1.0s to 0.0 m/s and `mPaceSensor` SHALL emit `null` without division-by-zero.
* **Criterion 4 (Google Fused & Replay Tooling)**: *Given* `SpeedAndLocationDevice_GoogleFused`, *When* instantiated, *Then* `fastestInterval` is configured to 1000 ms. *Given* `replay_workout.py`, *When* replay reaches EOF, *Then* lingering mock providers are cleanly removed.

---

## 2. Test Specification (`TST-TRK-005`)

### Test Cases
* `TST-TRK-005.1` (**Continuous Fix Stability & Inactivity Timeout Unit Test**): Verify continuous 1-Hz fixes keep speed stable; verify 10.0s inactivity timeout triggers progressive 50% decay per second until clamped to 0.0 m/s.
* `TST-TRK-005.2` (**Multi-Second 7–8s Cadence Immunity Test**): Emit valid fixes at 7.0–8.0s intervals with displacement; verify watchdog never fires premature decay ticks and speed never collapses toward zero.
* `TST-TRK-005.3` (**Decoupled Speed Recovery Test**): Following watchdog decay ticks, emit a fresh valid fix; verify speed recovers directly to incoming speed without dragging down by decayed artifact.
* `TST-TRK-005.4` (**Pace Nullification & Distance Invariant Test**): Verify pace sensor emits `null` when speed $\le 0.1\text{ m/s}$ (no `Infinity`/`NaN`); verify zero phantom distance accumulated during decay.
* `TST-TRK-005.5` (**Clean-Room Regression Execution**): Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*"` and verify 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-005.1` | Unit | `SpeedAndLocationDevice.onInactivityTimeout`, `triggerDecayTick` | `REQ-TRK-013.1` | Planned |
| `TST-TRK-005.2` | Unit | `SpeedAndLocationDevice.onNewLocation`, `resetWatchdog` | `REQ-TRK-013.1` | Planned |
| `TST-TRK-005.3` | Unit | `SpeedAndLocationDevice.onNewLocation` (decay bypass) | `REQ-TRK-013.4` | Planned |
| `TST-TRK-005.4` | Unit | `SpeedAndLocationDevice` pace nullification & distance | `REQ-TRK-013.3` | Planned |
| `TST-TRK-005.5` | Regression | Full `banalservice.devices` test suite | `REQ-TRK-013` | Planned |
