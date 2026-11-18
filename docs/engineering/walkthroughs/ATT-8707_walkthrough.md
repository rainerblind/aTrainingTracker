# Stage 5: Walkthrough & Verification - ATT-8707: VerticalSpeedAndSlopeDevice +96.4 m Phantom Descent Fix

**Ticket**: [ATT-8707](https://rainerblind.atlassian.net/browse/ATT-8707) (`2 SP`) | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8707`  
**Requirement Mapping**: `REQ-FIL-024` (`REQ-FIL-024.1`–`REQ-FIL-024.4`) | **Test Mapping**: `TST-FIL-024`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17

---

## 1. Executive Summary & Verification Overview
* Fixed the `+96.37 m` startup phantom `DESCENT` and `-203%` `SLOPE` artifact in `VerticalSpeedAndSlopeDevice.java` via three defenses: (1) `resolveActiveAltitudeSensor()` + `DeviceType.ALTITUDE_FROM_PRESSURE` broadcast guard in `mAltitudeCorrectionReceiver` (with wrapped ring-buffer shift); (2) synchronized `onAccumulatorsReset()` + `calculateMetrics()` clearing stale pre-workout baselines on `RESET_ACCUMULATORS_INTENT`; and (3) non-barometric same-device step discontinuity re-seeding (`ALTITUDE_STEP_RESEED_THRESHOLD_METERS = 15.0`, excluding `ALTITUDE_FROM_PRESSURE` to prevent calibration double-shift races) plus `Double.isFinite` guards on `delta`, `rawAlt`, and filtered altitudes.
* Non-UI backend sensor fix (`N/A - no UI strings`, zero SQLite schema changes); verified via Tier 1 targeted Gradle build + unit test suites (`260/260 passed`, `0` skipped, `0` failed, `0` errors) and governance checks (`exit 0`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-024.1`, `REQ-FIL-024.4` | `TST-FIL-024.1` | Unit (`VerticalSpeedAndSlopeDeviceTest`, non-baro ignore + active-baro no-double-shift) | **PASSED** (`23/23` in class) | `Verified` |
| `REQ-FIL-024.2` | `TST-FIL-024.2` | Unit (`VerticalSpeedAndSlopeDeviceTest`, synchronized `onAccumulatorsReset()`) | **PASSED** (`23/23` in class) | `Verified` |
| `REQ-FIL-024.3`, `REQ-FIL-024.4` | `TST-FIL-024.3` | Unit (`VerticalSpeedAndSlopeDeviceTest`, non-baro step reseed + `Double.isFinite` + climb/descent) | **PASSED** (`23/23` in class) | `Verified` |
| `REQ-PRO-001`, `REQ-PRO-049.1` | `TST-FIL-024.4` | Tier 1 Targeted Regression (`assembleDebug` + 33 suites, zero `src/main` reads) | **PASSED** (`260/260`) | `Verified` |

---

## 3. Automated Test Evidence
* **Tier 1 Scope**: `python3 tools/parallel_sprint_lock.py ./gradlew :app:assembleDebug :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.VerticalSpeedAndSlopeDeviceTest" --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" --tests "com.atrainingtracker.banalservice.odometry.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --max-workers=2`
```text
BUILD SUCCESSFUL in 2m 56s (55 actionable tasks: 19 executed, 36 up-to-date)
- VerticalSpeedAndSlopeDeviceTest: 23/23 passed (0 skipped, 0 failed, 0 errors)
- AltitudeAndSlopeDevice_ATTFusedTest: 17/17 passed (0 skipped, 0 failed, 0 errors)
- banalservice.odometry.* (7 suites): 51/51 passed (0 skipped, 0 failed, 0 errors)
- trainingtracker.tracker.* (24 suites): 169/169 passed (0 skipped, 0 failed, 0 errors)
TOTAL (33 suites): 260/260 passed, skipped=0, failures=0, errors=0
```

---

## 4. Device / UI Verification
* Pure backend sensor-filtering fix in `VerticalSpeedAndSlopeDevice.java`; zero UI/Compose/resource/XML changes (`No UI changes`). Deferred physical field verification not required (`Unit`-verified per `REQ-FIL-024`).

---

## 5. Invariant & Governance Verification
1. **Core Invariants Preserved**: `REQ-FIL-012` `N=15` VAM regression, accuracy/low-speed gating, `[-3000, 3000] m/h` clamp, `TST-FIL-004.5` active-baro shift parity, and steady-state climb/descent accumulation intact.
2. **Living Documentation Synchronized**: `REQ-FIL-024` and `TST-FIL-024` `[Verified]` in `docs/requirements/req_tracking_cockpit.md` (`v1.0.7`) and `docs/tests/tst_tracking_cockpit.md` (`v1.0.7`), with `docs/requirements.md` and `docs/tests.md` synced via `--sync-indexes`.
3. **Human Decision Gate**: Parent `ATT-8707` advances to `Final Review (Agent)` after Gate 5 and ends at `Final Review (Human)`.
