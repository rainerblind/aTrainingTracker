# Stage 5: Walkthrough & Verification - ATT-8705: Post-Workout ERTS Odometry & Altitude Collapse

**Ticket**: [ATT-8705](https://rainerblind.atlassian.net/browse/ATT-8705) (`3 SP`, `severity-major`) | **Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-FIL-025` | **Test Mapping**: `TST-FIL-025` | **Branch**: `feature/ATT-8705` | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Resolved the post-workout ERTS odometry/altitude collapse (`ATT-8705`) across 4 production files (`UnicycleOdometryEkf.kt`, `ExtendedRtsOdometrySmoother.kt`, `ErtsPostWorkoutRecomputer.kt`, `LapsDatabaseManager.java`) and 3 test suites:
1. **Offline Heading Init & Running-Pace Anchoring**: `ExtendedRtsOdometrySmoother.resolveInitialHeadingRad()` initializes heading from the first valid GNSS movement chord (fallback moving bearing -> `PI / 2.0`), while GNSS displacement-chord & running-pace bearing updates call `UnicycleOdometryEkf.updateOfflineHeading()`. Live `MIN_DOPPLER_SPEED_MPS = 3.0` (`ATT-8576` / `REQ-FIL-023.1`) remains strictly preserved for `updateDopplerHeading()`.
2. **1-Hz Yaw-Rate Anti-Aliasing & Non-Negative Speed**: `computeFilteredYawRates()` (5-sample centered window, `±0.35 rad/s` clamp) + `gyroNoiseScale` (`25.0` when trusted GNSS is active) eliminate 1-Hz arm-swing drift; `x[2] >= 0.0` prevents negative velocity reversal and false `0.0 m/s` / altitude standstill clamping.
3. **Symmetric Sanity Bounds & `Laps.db` Sync**: `ErtsPostWorkoutRecomputer.isOdometrySane()` enforces pause-aware `0.85x..3.0x` bounds via `estimateRawReferenceDistanceMeters()`; `LapsDatabaseManager.syncLapDistancesAndSpeeds()` synchronizes `Laps.TABLE` distances & average speeds from smoothed `LAP_NR` segments (with proportional fallback).

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-025.1` | `TST-FIL-025.1` | Offline Unit (`ExtendedRtsOdometrySmootherTest`, `UnicycleOdometryEkfTest`) | **PASSED** (`7/7`, `15/15`) | `Verified` |
| `REQ-FIL-025.2` | `TST-FIL-025.2` | Offline Unit (`ExtendedRtsOdometrySmootherTest`, `ExtendedRtsAltitudeSlopeSmootherTest`) | **PASSED** (`7/7`, `2/2`) | `Verified` |
| `REQ-FIL-025.3` | `TST-FIL-025.3` | Robolectric Unit (`ErtsPostWorkoutRecomputerTest`) | **PASSED** (`7/7`) | `Verified` |
| `REQ-FIL-025.4` | `TST-FIL-025.4` | Robolectric/SQLite (`ErtsPostWorkoutRecomputerTest`, `LapsDatabaseManagerTest`) | **PASSED** (`7/7`, `10/10`) | `Verified` |
| `REQ-PRO-049.1` | `TST-FIL-025.5` | Tier 1 Targeted Regression (`246/246`, zero `src/main` reads) | **PASSED** (`246/246`) | `Verified` |

## 3. Automated Test Evidence
* **Tier 1 Targeted Build & Unit Regression** (verified on Stage 4 commit `11dd0749`; Stage 5 `.kt`/`.java` tree 100% identical to `11dd0749`, `Rule 7.5` zero redundant re-run; `N/A - no UI strings`, no `strings*.xml` or `androidTest/` edits):
```text
python3 tools/parallel_sprint_lock.py ./gradlew :app:assembleDebug :app:testDebugUnitTest \
  --tests "com.atrainingtracker.banalservice.odometry.*" \
  --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" \
  --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" \
  --tests "com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest" \
  --tests "com.atrainingtracker.trainingtracker.database.LapsDatabaseManagerTest" \
  --tests "com.atrainingtracker.trainingtracker.tracker.TrackerService*" \
  --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummaries*" --rerun-tasks
-> BUILD SUCCESSFUL in 10m 39s (55 actionable tasks: 55 executed)
-> TOTAL: 246 tests, 0 failures, 0 errors, 0 skipped
   (odometry.*: 54/54, ATTFused: 29/29, ErtsPostWorkoutRecomputerTest: 7/7,
    LapsDatabaseManagerTest: 10/10, WorkoutSummaries*: 45/45, TrackerService*: 101/101)
```
* **Governance Verification**: `python3 tools/verify_requirement_governance.py --sync-indexes`, `--text-file docs/engineering/walkthroughs/ATT-8705_walkthrough.md --check-doc-headers --check-commit-hygiene --check-conflict-markers`, and full `verify_requirement_governance.py` exited `0`.

## 4. Hardware / Physical Verification (Pixel 10)
* Non-UI backend odometry/database fix; APK deployment skipped in Stage 5 per `stage5-verification/SKILL.md` (`:app:assembleDebug` compilation + 246/246 Tier-1 unit/Robolectric tests verified; real-world outdoor verification deferred to PO physical device acceptance).
* **Visual Consistency**: No UI changes (`N/A - no UI strings`).

## 5. Invariant & Governance Verification
1. **Live EKF & Column Invariants**: `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` (`ATT-8576` / `REQ-FIL-023.1`), `REQ-FIL-017.3` device-column immutability, `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` default, and DB schema versions preserved.
2. **Living Documentation Synchronized**: `REQ-FIL-025` and `TST-FIL-025` promoted to `[Verified]` (`v1.0.10`) and synced into master indexes.
3. **Human Decision Gate**: Parent `ATT-8705` advances only to `Final Review (Agent)` / `Final Review (Human)` (never `Erledigt`).
