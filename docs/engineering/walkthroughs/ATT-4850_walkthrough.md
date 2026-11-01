# Stage 5: Walkthrough & Verification - ATT-4850: Eliminate Stationary GPS Spider-Map Jitter

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

## 1. Executive Summary & Verification Overview

ATT-4850 eliminates stationary GPS spider-map jitter, phantom speed overrides, creeping distance, and erratic bearing spinning when athletes stop at traffic lights, cafés, or rest indoors. In `SpeedAndLocationDevice.java`, true GNSS Doppler zero speed (`speed <= SPEED_ZERO_THRESHOLD`) is strictly preserved without geometric displacement overrides (`delta_distance / dt`), stationary anchor pinning is established with an accuracy-scaled deadband ($R = \max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$), bearing is locked to prevent jitter spinning, and coordinates are stabilized via adaptive 2D low-pass filtering. In `BANALServiceRepository.kt`, stationary track and path point appends within deadband radius are suppressed, preventing spiderweb map breadcrumbs.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-048.1` | `TST-TRK-048.1` | `SpeedAndLocationDeviceStationaryJitterTest` (`testStationaryGpsJitter...`) | **PASSED** | `Verified` |
| `REQ-TRK-048.2` | `TST-TRK-048.2` | `SpeedAndLocationDeviceStationaryJitterTest` (`testStationaryAnchor_unpins...`) | **PASSED** | `Verified` |
| `REQ-TRK-048.3` | `TST-TRK-048.3` | `SpeedAndLocationDeviceStationaryJitterTest` (`testStoppingAtRedLight...`) | **PASSED** | `Verified` |
| `REQ-TRK-048.4` | `TST-TRK-048.4` | `SpeedAndLocationDeviceStationaryJitterTest` (`testProviderWithoutDoppler...`) | **PASSED** | `Verified` |
| `REQ-TRK-048.5` | `TST-TRK-048.5` | `BANALServiceRepositoryMemoryTest` (`testTrackBreadcrumbs...`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.banalservice.*` (including `SpeedAndLocationDeviceStationaryJitterTest`, `SpeedAndLocationDeviceDisplacementTest`, `SpeedAndLocationDeviceDecayTest`, `SpeedAndLocationDeviceResilienceTest`)
  - `com.atrainingtracker.trainingtracker.repositories.*` (including `BANALServiceRepositoryMemoryTest`, `BANALServiceRepositoryTrackingModeTest`)
  - `com.atrainingtracker.trainingtracker.tracker.*` (including `MaxLineDistanceIntegrityTest`, `TrackerServiceWorkoutStartTest`)
  - `com.atrainingtracker.trainingtracker.build.*`
* **Execution**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: **BUILD SUCCESSFUL in 1m 11s** (568 tests, 0 failures, 0 errors, 100% clean-room pass).
* **Resource Compilation**: `./gradlew processDebugResources` -> **BUILD SUCCESSFUL in 1s**.
* **Governance**: `python3 tools/verify_requirement_governance.py` -> Clean net-new pass (`REQ-TRK-048`).

---

## 4. Invariant & Governance Verification

1. **Speed Decay Watchdog Parity (`REQ-TRK-013`)**: Inactivity timeout (10s) and exponential decay tick verified intact; watchdog decays stale speed to 0.0 if fixes halt.
2. **Teleportation Spike Rejection (`REQ-TRK-041.1`)**: Guard against speeds $> 70.0\text{ m/s}$ ($252\text{ km/h}$) strictly preserved and tested.
3. **High-Speed Responsiveness**: Adaptive smoothing weight $\alpha = 1.0$ at speed $\ge 3.0\text{ m/s}$ ensures zero corner-cutting lag during cycling or running.
4. **Auto-Pause Stability**: Eliminating phantom 5–14 km/h speed overrides ensures Auto-Pause triggers promptly and remains stable while stopped.
5. **No Intermediate Commits**: Feature branch `feature/ATT-4850` remains uncommitted until atomic squash merge in Stage 5.
6. **Lösungsversion Governance (Core Rule 8)**: Sub-tasks have no `fixVersion` assigned; Target Release `V4.9.41` referenced consistently across deliverables.
