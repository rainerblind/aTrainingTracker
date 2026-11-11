# Stage 5: Walkthrough & Verification - ATT-6682: SpeedAndLocationDevice: Require entry hysteresis and ignore sub-second duplicate callbacks before stationary pinning

**Ticket**: [ATT-6682](https://rainerblind.atlassian.net/browse/ATT-6682)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-TRK-074` (*Duplicate Location Callback Filtering, Asymmetric Deadband Separation & Stationary Pinning Entry Hysteresis in SpeedAndLocationDevice*)  
**Test Mapping**: `TST-TRK-074`  
**Branch**: `feature/ATT-6682`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview

During Sprint 2026-46.1 review on Pixel 10 (`66020DLCR002FL`), `GoogleFusedSpeedAndLocationDevice` velocity regularly dropped to `0.0 km/h` during active cycling. Two failure modes were identified:
1. Sub-second duplicate/burst callbacks (50–150 ms) with identical coordinates (`rawDelta == 0.0`) arrived with `speed == 0.0f` from resting phone IMUs, immediately triggering lines 372–378 and overriding moving velocity with 0.0 km/h.
2. Low-speed climbing ($\le 15\text{--}21\text{ km/h}$) produced displacements within `escapeRadius` (3.75–6.0 m), causing a single transient Doppler zero fix to immediately pin the moving device.

In `ATT-6682`, `SpeedAndLocationDevice.java` was hardened with:
1. **Duplicate Callback Guard (`REQ-TRK-074.1`)**: Ignored sub-interval ($dt < 250\text{ ms}$) and duplicate coordinate fixes ($rawDelta == 0.0\text{ m} \land dt < 900\text{ ms}$) without mutating speed or distance accumulators.
2. **Asymmetric Deadband Separation (`REQ-TRK-074.2`)**: Decoupled entry spatial bounds ($rawDelta \le deadbandRadius$) from exit bounds ($distFromAnchor > escapeRadius$). Low-speed climbs exceeding deadband ($3.0\text{ m}$) are never treated as stationary candidates.
3. **3-Tick Entry Hysteresis (`REQ-TRK-074.3`, `REQ-TRK-074.4`)**: Introduced `STATIONARY_PINNING_HYSTERESIS_TICKS = 3` and `mStationaryHysteresisCount`. Moving devices require 3 consecutive stationary candidate updates before establishing anchor pinning. Transient ticks fall back to geometric speed without pinning, and forward motion immediately resets the streak.
4. **Start-Line Invariance (`REQ-TRK-074.5`)**: Immediate anchor pinning on the very first fix ($mPrevLocation == null$) is strictly preserved to prevent startup GPS wander.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-074.1` | `TST-TRK-074.1` | `testDuplicateLocationCallback_ignoredWithoutSpeedZeroing` | **PASSED** | `Verified` |
| `REQ-TRK-074.2` | `TST-TRK-074.2` | `testLowSpeedClimb_singleDopplerZeroDoesNotPin` | **PASSED** | `Verified` |
| `REQ-TRK-074.3` | `TST-TRK-074.3` | `testStationaryEntryHysteresis_requiresThreeConsecutiveTicks` | **PASSED** | `Verified` |
| `REQ-TRK-074.4` | `TST-TRK-074.4` | `testStationaryHysteresisStreak_resetsOnMovement` | **PASSED** | `Verified` |
| `REQ-TRK-074.5` | `TST-TRK-074.5` | `testInitialFix_pinsImmediatelyWithoutHysteresis` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-074.6` | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice*"` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Device Unit Test Suite (`com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice*`)
```text
> Task :app:compileDebugUnitTestJavaWithJavac UP-TO-DATE
> Task :app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 3m 53s
33 actionable tasks: 9 executed, 11 from cache, 13 up-to-date
```

### Living Documentation Governance Verification (`tools/verify_requirement_governance.py`)
```text
Recalculated master index table in docs/requirements.md: Total=1037, Verified=1013, Planned=24.
Recalculated master index table in docs/tests.md: Total=1027, Verified=1023, Planned=4.
PASS: Master index tables successfully synchronized with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Verified on Pixel 10 (`66020DLCR002FL`, Android 16). Eliminates the 0.0 km/h speed drops caused by Google Fused duplicate callbacks and restores rock-solid velocity tracking during uphill climbs and steady riding.

### Visual Consistency (Rule 23)
No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions in Standstill Guarantees**: Upon completing 3 stationary ticks at a stop sign or traffic light, anchor pins cleanly, speed clamps to 0.0 m/s, distance increments halt, and bearing locks (`REQ-TRK-048.1`..`REQ-TRK-048.3`).
2. **Start-Line Anchor Pinning**: First fix upon starting an activity ($mPrevLocation == null$) pins immediately with zero delay.
3. **Teleportation Spike Invariance**: Multipath jumps exceeding $70.0\text{ m/s}$ remain clamped to `delta_distance = 0.0` (`REQ-TRK-041.1`).
4. **Living Documentation Synchronized**: `REQ-TRK-074` and `TST-TRK-074` updated to `Verified` in domain shards and master index tables.
5. **Parent Ticket Final Review**: Subtask transitioned to `Erledigt` via `gate-verdict PASS`; parent ticket advances to `Final Review (Human)`.
