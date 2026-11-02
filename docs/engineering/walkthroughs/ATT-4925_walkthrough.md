# Stage 5: Walkthrough & Verification - ATT-4925: Speed Source Gating for Start in Auto-Paused & Sensor Removal

**Ticket**: [ATT-4925](https://rainerblind.atlassian.net/browse/ATT-4925)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-044` (`REQ-TRK-044.3`, `REQ-TRK-044.4`)  
**Test Mapping**: `TST-TRK-044` (`TST-TRK-044.1`, `TST-TRK-044.2`, `TST-TRK-044.3`)  
**Branch**: `feature/ATT-4925`  
**Author**: AI Agent 2 (`subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview
Resolved the edge case introduced in `ATT-4840` where a workout started with *Start in Auto-Paused* remained paused indefinitely when no speed source was available:
- `AutoPauseManager.shouldStartInAutoPause` now gates on `hasSpeedSource: Boolean` (default `false`), ensuring indoor workouts without speed sensors initialize in `TRACKING`.
- In `AutoPauseManager.evaluateTick`: mid-workout speed source loss (`!hasSpeedSource`) while in `AUTO_PAUSED` transitions to `SENSOR_LOST` with `triggerResume = true`, restoring workout recording while keeping corrupt speed and tunnel protections intact when `hasSpeedSource == true`.
- Wired `hasSpeedSource` at both startup decision points in `TrainingApplication.startTracking()` and `TrackerService.onServiceConnected()`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-044.3` | `TST-TRK-044.1` | Unit: `AutoPauseManagerTest.shouldStartInAutoPause contract` | **PASSED** | `Verified` |
| `REQ-TRK-044.4` | `TST-TRK-044.2` | Unit: `TrainingApplicationTrackingTest` (HR-only start in `TRACKING` vs GPS start in `AUTO_PAUSED`) | **PASSED** | `Verified` |
| `REQ-TRK-044.4` | `TST-TRK-044.3` | Unit: `AutoPauseManagerTest` (mid-workout removal `AUTO_PAUSED -> SENSOR_LOST`) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Script: `python3 tools/verify_requirement_governance.py` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope
Executed incremental targeted unit test suite covering modified packages:
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.autopause.*" \
    --tests "com.atrainingtracker.trainingtracker.TrainingApplicationTrackingTest" \
    --tests "com.atrainingtracker.trainingtracker.tracker.*" \
    --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 17s (2 executed, 30 up-to-date)
BUILD SUCCESSFUL in 27s (1 executed, 31 up-to-date)
```
- Total targeted tests: 100% pass across all tests in `autopause.*`, `TrainingApplicationTrackingTest`, `tracker.*`, and `build.*`.
- Requirement governance: `PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.`

---

## 4. Hardware / Physical Verification (Pixel 10)
- **Scope**: Core domain engine state machine and service lifecycle logic; zero Compose UI, layout XML, or navigation routes touched.
- **Verification**: Verified via deterministic JVM unit tests and `./gradlew processDebugResources` compilation verification. On-device UI screenshot verification omitted per Rule 60 non-UI standard.

---

## 5. Invariant & Governance Verification
1. **GPS Fix Pending**: Workouts with GPS enabled awaiting initial satellite fix (`hasSpeedSource == true`, `speed == null`) still start in `AUTO_PAUSED`.
2. **Tunnel & Corrupt Packet Defense**: Stale packets in motion (`REQ-TRK-037`) or NaN glitches while `hasSpeedSource == true` remain paused.
3. **Manual Pause Sovereignty**: Explicit user pause lock retains absolute precedence over auto-resume.
4. **Living Documentation Synchronized**: `REQ-TRK-044` and `TST-TRK-044` updated to `Verified`.
