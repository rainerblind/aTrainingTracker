# Stage 2: Requirement & Test Specification - ATT-4850: Eliminate Stationary GPS Spider-Map Jitter, Phantom Speed Overrides, and Creeping Distance

**Ticket**: [ATT-4850](https://rainerblind.atlassian.net/browse/ATT-4850)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-048` (`REQ-TRK-048.1`..`REQ-TRK-048.5`)  
**Test Mapping**: `TST-TRK-048` (`TST-TRK-048.1`..`TST-TRK-048.5`)  
**Branch**: `feature/ATT-4850`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
When athletes stop at traffic lights, cafés, or rest indoors, GNSS multipath reflections cause horizontal fixes to wander $1\text{--}4\text{ m}$. Overriding genuine GNSS Doppler zero speed with geometric displacement creates fake $5\text{--}14\text{ km/h}$ speed readings, breaks Auto-Pause, accumulates creeping distance, spins compass bearing, and records dense spiderweb track artifacts in `BANALServiceRepository`. This specification establishes Doppler zero preservation, stationary anchor pinning, and accuracy-scaled deadband gating.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-048.1` | `TST-TRK-048.1` | `SpeedAndLocationDeviceStationaryJitterTest` | Preserve `0.0 m/s` when `location.hasSpeed() && location.getSpeed() <= SPEED_ZERO_THRESHOLD`. |
| `REQ-TRK-048.2` | `TST-TRK-048.2` | `SpeedAndLocationDeviceStationaryJitterTest` | Anchor coordinate pinning and distance gating within $R = \max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$. |
| `REQ-TRK-048.3` | `TST-TRK-048.3` | `SpeedAndLocationDeviceStationaryJitterTest` | Stationary bearing lock and stabilized anchor coordinate emission. |
| `REQ-TRK-048.4` | `TST-TRK-048.4` | `SpeedAndLocationDeviceStationaryJitterTest` | Provider without Doppler velocity (`!hasSpeed()`) geometric fallback beyond deadband. |
| `REQ-TRK-048.5` | `TST-TRK-048.5` | `BANALServiceRepositoryTest` + Regression Suite | Spider-map track suppression, watchdog speed decay (`REQ-TRK-013`), and teleportation gating (`REQ-TRK-041.1`). |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-TRK-048.1: Stationary GPS Jitter Zero Speed & Zero Distance Increment
* **Given** a sequence of synthetic 1-Hz GPS fixes with `hasSpeed() == true`, `getSpeed() == 0.0f`, `accuracy = 5.0 m`, and jittering coordinates ($\Delta d \in [1.5\text{ m}, 3.0\text{ m}]$),
* **When** processed by `SpeedAndLocationDevice.onNewLocation()`,
* **Then** published `SPEED_mps` remains `0.0`, `DISTANCE_m` increment is `0.0`, `BEARING` is locked, and published coordinates equal the anchored location.

### TST-TRK-048.2: Anchor Pinning & Smooth Transition to Motion
* **Given** a stationary athlete pinned at anchor $(lat_0, lon_0)$,
* **When** subsequent location fixes advance steadily with displacement $> \max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$ and non-zero speed,
* **Then** the anchor unpins immediately, `SPEED_mps` updates, distance accumulation resumes, and `BEARING` updates along the direction of motion.

### TST-TRK-048.3: Provider Without Doppler Velocity Geometric Fallback
* **Given** location fixes with `hasSpeed() == false`,
* **When** jittering within the deadband ($R \le \max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$), speed remains `0.0` and distance accumulation is suppressed;
* **When** sustained displacement exceeds $R$, geometric speed `delta_distance / dt` and distance accumulation execute accurately.

### TST-TRK-048.4: Spider-Map Breadcrumb Suppression
* **Given** `BANALServiceRepository` observing location updates during a stationary pause,
* **When** incoming location updates remain within the stationary deadband radius,
* **Then** `_currentTrack` suppresses appending redundant jitter breadcrumbs.

---

## 4. Invariants & Non-Functional Verification
1. **Speed Decay Watchdog Parity (`REQ-TRK-013`)**: Watchdog timer still decays stale speed to `0.0` if location fixes cease.
2. **Teleportation Spike Rejection (`REQ-TRK-041.1`)**: Guard against speeds $> 70.0\text{ m/s}$ ($252\text{ km/h}$) remains intact.
3. **High-Speed Responsiveness**: Zero lag introduced during rapid acceleration, high-speed descents, or sharp cornering.
4. **Clean-Room Test Pass Rate**: 100% pass across targeted unit tests and full Tier 1 regression suite without `--rerun-tasks`.
