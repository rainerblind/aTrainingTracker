# Stage 1 Analysis: ATT-4802 - Speed toggles periodically between plausible value and near-zero on 7-8s location update intervals

**Ticket**: [ATT-4802](https://rainerblind.atlassian.net/browse/ATT-4802)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-43.10`  
**Branch**: `feature/ATT-4802`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-24  

---

## 1. Problem Statement & Motivation
During workout recording (e.g. workout "Eier holen #2", `2026-10-23_055921`), recorded speed (`SPEED_mps`) exhibits a periodic 7–8 second sawtooth/comb pattern oscillating between plausible cycling speeds (10–21 km/h) and near-zero (0–4 km/h). When location updates arrive on a 7–8s cadence (e.g., degraded GNSS, Google Fused on Wi-Fi/cell, or battery-saver batching), the hardcoded 5-second inactivity watchdog triggers premature exponential decay before the next fix arrives, and new fixes are smoothed against the decayed artifact.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Causal Chain**:
  1. *Premature Watchdog Expiry*: In `SpeedAndLocationDevice.java`, `INACTIVITY_TIMEOUT_MS = 5000L` posts `mInactivityRunnable` (halving speed every 1.0s via `DECAY_FACTOR = 0.5`). On 7–8s fix intervals, 2–3 decay ticks fire before each fix arrives ($4.54 \rightarrow 2.27 \rightarrow 1.14 \rightarrow 0.39\text{ m/s}$).
  2. *Contaminated Speed Averaging*: In `onNewLocation()`, line 316 executes `mSpeed = (mSpeed + speed) / 2`. Because `mSpeed` was artificially halved by the watchdog, averaging drags the fresh fix down by ~50%, preventing recovery to true speed.
  3. *Provider Cadence & Tooling Trigger*: `SpeedAndLocationDevice_GoogleFused` lacked `setFastestInterval(SAMPLING_TIME)`, allowing OS throttling. Lingering ADB mock providers from `tools/replay_workout.py` (due to an EOF race in `step()` and unsynchronized `_worker_loop`) blocked hardware GPS on test devices.
* **Independent Peer Review & Verification**:
  - Live code inspection confirms `INACTIVITY_TIMEOUT_MS = 5000L` (`SpeedAndLocationDevice.java:38`) and unconditional `(mSpeed + speed) / 2` averaging (`line 316`).
  - Sample trace in `WorkoutSamples.db` matches exact decay timing ($t = 58, 63, 64, 65, 70, 71, 72, 73\text{ s}$).
  - Blind spot check: Existing `SpeedAndLocationDeviceDecayTest.kt` only covered 1.0s continuous fixes and full silence; it lacked coverage for multi-second ($6\text{--}8\text{ s}$) update cadences with active motion.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Decouple watchdog decay state from active filter: track `mIsDecaying` so fresh fixes do not average with decayed artifacts (`mSpeed = speed` on recovery or when $\Delta t \ge 3000\text{ ms}$).
  - Increase/adapt watchdog inactivity timeout to $\ge 10.0\text{ s}$ (`10000L`) to tolerate multi-second location cadences.
  - Harden `SpeedAndLocationDevice_GoogleFused` with `setFastestInterval(SAMPLING_TIME)`.
  - Harden `tools/replay_workout.py`: suppress EOF update and synchronize `release_providers()`.
  - Add unit tests in `SpeedAndLocationDeviceDecayTest.kt` simulating 7–8s fix cadences.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No 3D strapdown IMU double-integration in Java (vibration rectification bias trap).
  - No handlebar-accelerometer cycling cadence inference.
  - No database schema alterations in `WorkoutSamples.db` or `Devices.db`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-013` (GPS Speed Inactivity Watchdog, Gradual Speed Decay & Pace Nullification Architecture [`TST-TRK-005`]).
* **Historical Origin & Commit Trace**: Introduced in commit `de8f92c4` (integrated in `9ae99c6c`, `sprint/2026-41.3`, ticket `ATT-2616`).
* **Root Reason for Existing Formulation**: A 5.0s timeout was chosen because 5 missing 1-Hz GPS fixes reliably indicated an athlete stop or tunnel entry, preventing speed/pace from freezing indefinitely at 25 km/h.
* **Preservation of Core Invariants**: Complete stops still decay speed to 0.0 m/s, pace is nullified without division-by-zero, and distance does not accumulate during decay. Extending the timeout to 10.0s and bypassing averaging after decay preserves stopped-state decay while eliminating false-positive comb oscillations.

---

## 5. Architectural Strategy & High-Level Solution
* **`SpeedAndLocationDevice.java`**: Set `INACTIVITY_TIMEOUT_MS = 10000L`. Track `mIsDecaying` flag. In `onNewLocation()`, if `mIsDecaying`, `mPrevLocation == null`, or $\Delta t \ge 3000\text{ ms}$, assign `mSpeed = speed` directly; else smooth via `(mSpeed + speed) / 2`.
* **`SpeedAndLocationDevice_GoogleFused.java`**: Add `mLocationRequest.setFastestInterval(SAMPLING_TIME)` (1000 ms).
* **`tools/replay_workout.py`**: Guard `_worker_loop` with `threading.Lock` against `release_providers()`; skip `on_update` at `reached_eof`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. True stops decay speed to 0.0 m/s and nullify pace without division-by-zero (`REQ-TRK-013`).
  2. Zero phantom distance accumulated during watchdog decay.
  3. Real hardware GNSS fixes and bearing/distance calculations remain uncompromised.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Localized sensor ingestion and test script fixes; zero schema changes; verified by targeted unit tests.
