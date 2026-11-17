# Stage 5: Walkthrough & Verification - ATT-8575: 3-State EKF Barometer Offset Anchoring & Ascent Guard

**Ticket**: `ATT-8575` (`2 SP`, Epic `ATT-166`) | **Target Release**: `V5.0.0`  
**Branch**: `feature/ATT-8575` (rebased onto `sprint/2026-11-17_01_Früh` @ `243e0fbc34545c97d4545774cd3420a1047a347e`)  
**Requirement Mapping**: `REQ-FIL-022` (`REQ-FIL-022.1`..`REQ-FIL-022.3`) | **Test Mapping**: `TST-FIL-022` (`TST-FIL-022.1`..`TST-FIL-022.4`)  
**UI Consistency**: `N/A - no UI strings` (`strings*.xml` and `androidTest/` untouched)

---

## 1. Executive Summary & Implemented Changes
1. **`KinematicAltitudeSlopeEkf.kt` (`app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt`)**:
   - Added `DEFAULT_UNANCHORED_ALT_VAR = 400.0` and `@Volatile` `hasReferenceAnchor` / `hasBarometerSample` flags (reset cleanly in `reset()` and `setMode()`).
   - **Baro-before-Ref (`REQ-FIL-022.1`)**: In 3-state mode (`is3StateMode == true`), `updateBarometer()` initializes unanchored barometer startup with `p00 = DEFAULT_UNANCHORED_ALT_VAR` (`400.0`) rather than `0.25`. On the first `updateReferenceAltitude(refAltM, varRef)`, even at standstill, `baroBias += (altitude - refAltM)`, `altitude = refAltM`, `p00 = varRef`, cross-covariances (`p01`, `p10`, `p02`, `p20`) zero out, and `true` is returned.
   - **Ref-before-Baro & 2-State Guard (`REQ-FIL-022.2`)**: When `updateReferenceAltitude()` initializes first and the first `updateBarometer(baroAltM)` arrives in 3-state mode, `baroBias = baroAltM - altitude` is seeded directly without Huber-clamping pull. In 2-state mode (`!is3StateMode`), `p00 = varBaro` (`0.25`) and `ekf.p00 < 10.0` remain preserved.
2. **`AltitudeAndSlopeDevice_ATTFused.kt` (`app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`)**:
   - Added `resetAscentDescentAnchor()` (`mAscentDescentAnchorAltitude = Double.NaN; mLastAltitude = Double.NaN`), invoked whenever `ekf.updateReferenceAltitude(...)` returns `true` in `handleReferenceAltitude()` or `checkReferenceAltitudeFromSensors()` prior to `publishAltitudeAndMetrics()` (`REQ-FIL-022.3`).
3. **Unit Tests (`KinematicAltitudeSlopeEkfTest.kt`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)**:
   - Added `testThreeStateBaroBeforeRef_anchorsAndAbsorbs130mWeatherOffsetAtStandstill` (`TST-FIL-022.1`), `testThreeStateRefBeforeBaro_andTwoStateGuard_preservesP00AndClearsFlags` (`TST-FIL-022.2`), and `testCalibrationJumpAscentDescentGuard_zeroBogusDescentAndRealClimbAfterward_tstFil022_3` (`TST-FIL-022.3`).

---

## 2. Requirement & Test Verification Matrix
* **Tier 1 Clean-Room Post-Rebase Execution** (`python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.KinematicAltitudeSlopeEkfTest" --tests "*.AltitudeAndSlopeDevice_ATTFusedTest" --tests "*.MeasuredNfrBenchmarkTest"`): **34/34 PASSED** (`0` failures, `0` errors, `0` skipped; `BUILD SUCCESSFUL`). Tier 2 full-repo suite deferred to Sprint Integration Gate; `TranslationParityTest` & `androidTest` skipped (`strings*.xml` & `androidTest/` untouched).

| Requirement | Test Spec | Verification Class & Measured Scope | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-022.1`, `REQ-FIL-022.2` | `TST-FIL-022.1`, `TST-FIL-022.2` | `KinematicAltitudeSlopeEkfTest` (8 tests: Baro-before-Ref & Ref-before-Baro `+130.4 m`, 2-state `p00 < 10.0`, reset/mode, concurrency) | **PASSED** (`8/8`) | `Verified` |
| `REQ-FIL-022.3` | `TST-FIL-022.3` | `AltitudeAndSlopeDevice_ATTFusedTest` (15 tests: `0.0 m` bogus descent guard on `-130.4 m` jump + `+25 m` real climb, hysteresis, DEM precedence) | **PASSED** (`15/15`) | `Verified` |
| `REQ-FIL-022.1`..`.3` | `TST-FIL-022.4` | `MeasuredNfrBenchmarkTest` (11 tests: EKF timing & delta-t determinism) | **PASSED** (`11/11`) | `Verified` |

---

## 3. Invariant & Governance Verification
* **Living Docs**: `REQ-FIL-022` (`docs/requirements/req_tracking_cockpit.md`) and `TST-FIL-022` (`docs/tests/tst_tracking_cockpit.md`) promoted to `[Verified]`; master indexes synchronized (`docs/requirements.md`: `1213` total, `969` Verified; `docs/tests.md`: `1220` total, `998` Verified).
* **Governance & Submodule**: `python3 tools/verify_requirement_governance.py` and `--text-file docs/engineering/walkthroughs/ATT-8575_walkthrough.md` exit `0`; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
