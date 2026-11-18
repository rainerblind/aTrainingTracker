# Stage 5: Walkthrough & Verification (Bounce 1/3) - ATT-8880: Fix Post-Workout ERTS Batch Timestamp Quantization, Indoor Stop Drift, Jeans-Pocket Gyro/Baro Artifacts, Lap Distance Sync, and Stream Subsampling

**Ticket**: [ATT-8880](https://rainerblind.atlassian.net/browse/ATT-8880) (`3 SP`, `severity-major`) | **Sub-task**: `[Test - Bounce 1]` | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät` | **Branch**: `feature/ATT-8880` (`8a976a35`)  
**Requirement Mapping**: `REQ-FIL-027` (`REQ-FIL-027.1`..`.5`) | **Test Mapping**: `TST-FIL-027` (`TST-FIL-027.1`..`.5`), `TST-FIL-017.3`, `TST-FIL-019.3` | **Date**: 2026-11-18

---

## 1. Executive Summary & Bounce 1/3 Remediation Overview
* **Core ERTS & Gyro/Baro Fixes (`REQ-FIL-027.1`–`.5`)**: Reconstructs monotonic `timestampMs` via `TIME_TOTAL` before `BarometricAltitudeFilter().filterSlewOnly()` (9-locale barometer columns) and ERTS; adds rolling `1.0 s` time-weighted mean `mWorldYawRateSensor` with sample-1 emission (`0.1 rad/s`) and clean `resetOriginAndEkf()`/`shutDown()` reset in `SpeedAndLocationDevice_ATTFused.kt`; pins stationary indoor epochs (`speed <= 0.05 && (wheelSpeed == null || wheelSpeed <= 0.05)`) via ZUPT + backward RTS `[0..1][2]` decoupling; syncs `DISTANCE_m_LAP` with `Laps.db`; simplifies `mapPolyline` via `PolyUtil.simplify(..., 10.0)` and subsamples streams at `ENCODING_STEP_SIZE = 20`; enforces `2.0 m` ascent/descent hysteresis.
* **Stage 6 Bounce 1/3 Remediation (`8a976a35`)**:
  1. **Wheel-Speed Column & `NaN` Sanitization**: Added `isWheelSpeedColumn()` in `ErtsPostWorkoutRecomputer.kt` (`startsWith("SPEED_mps (")` with `Rad`/`Wheel` tokens, excluding `RADAR`/`YAW`/`CADENCE`/`ATT`/`GPS`/`Google`) so `RADAR_THREAT_COUNT` and `WORLD_YAW_RATE_radps` never match; added `.isFinite()` sanitization across `ErtsPostWorkoutRecomputer`, `ExtendedRtsOdometrySmoother` (`isStationarySample`, `sanitizeNonNegativeFinite`), and `UnicycleOdometryEkf` (`initialSpeed`, `updateSpeed`).
  2. **`baseEpochMs` Re-Anchoring & Batch-Stall Guard**: `ErtsPostWorkoutRecomputer.readWorkoutSamples()` now re-anchors `baseEpochMs`/`firstTimeTotalSec` on leading `TIME_TOTAL = NULL` transitions and repeated `wallJumpPastTimeTotal` pauses, comparing `parsedTs` against `effectiveReconstructed` so `>= 6s` SQLite batch-flush stalls never falsely trigger.
  3. **Rebase & Living Docs**: Rebased cleanly onto `sprint/2026-11-18_02_Spät` (`1beb3841`) with `git merge-tree` exit `0`.
* **Scope & Device Boundary (`Rule 7.6`, `stage5-verification` §2)**: Non-UI backend/algorithm ticket — skipped emulator APK install and `TranslationParityTest` (`N/A - no UI strings`). Raw DB columns and `WorkoutRepository.kt` (`5,048 <= 5,117` LoC) remain untouched.

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Suite (`8a976a35`) | Result |
| :--- | :--- | :--- | :--- |
| `REQ-FIL-027.1`, `.4`, `.5` | `TST-FIL-027.1`, `.4`, `.5`, `TST-FIL-017.3`, `TST-FIL-019.3` | Unit (`ErtsPostWorkoutRecomputerTest`) | **PASSED** (`12/12`) |
| `REQ-FIL-027.2` | `TST-FIL-027.2` | Unit (`SpeedAndLocationDevice_ATTFusedTest`) | **PASSED** (`14/14`) |
| `REQ-FIL-027.3` | `TST-FIL-027.3` | Unit (`ExtendedRtsOdometrySmootherTest`, `UnicycleOdometryEkfTest`) | **PASSED** (`9/9` + `16/16`) |
| `REQ-FIL-027.5` | `TST-FIL-027.5` | Unit & Contract (`ExtendedRtsAltitudeSlopeSmootherTest`, `BarometricAltitudeFilterTest`, `SourceTextReadingAuditContractTest`) | **PASSED** (`3/3` + `4/4` + `4/4`) |

## 3. Automated Test & Governance Evidence (Tier 1 Targeted Scope on `8a976a35`)
* **Stage 4 Author Tier 1 Run (6 suites, `./gradlew :app:testDebugUnitTest`)**: **`58/58` PASSED** (`0` failures, `0` errors, `0` skipped — `ErtsPostWorkoutRecomputerTest` `12/12`, `SpeedAndLocationDevice_ATTFusedTest` `14/14`, `ExtendedRtsOdometrySmootherTest` `9/9`, `ExtendedRtsAltitudeSlopeSmootherTest` `3/3`, `UnicycleOdometryEkfTest` `16/16`, `BarometricAltitudeFilterTest` `4/4`).
* **Gate 4 Bounce 1 Reviewer Run (7 suites, `./gradlew :app:testDebugUnitTest`)**: **`62/62` PASSED** (`0` failures, `0` errors, `0` skipped — above 6 suites plus `SourceTextReadingAuditContractTest` `4/4`, `0` new `src/main` reads). Reused on unchanged `8a976a35` per `Rule 7.5`; full-repo contract/lint/detekt suites defer to Tier 2 Sprint Integration Gate.
* **Governance Verification**: `python3 tools/verify_requirement_governance.py --repo-root .` exited `0` (`REQ-FIL-027` & `TST-FIL-027` `[Verified]`).
