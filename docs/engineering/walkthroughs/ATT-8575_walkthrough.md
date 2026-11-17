# Stage 5: Walkthrough & Verification - ATT-8575: 3-State EKF Barometer Offset Anchoring & Ascent Guard

**Ticket**: `ATT-8575` (`2 SP`, Epic `ATT-166`) | **Target Release**: `V5.0.0`  
**Branch**: `feature/ATT-8575` (healed atop `sprint/2026-11-17_01_Früh` @ `23317f586646f449b9b655ccb31880d0ea2a39b6`)  
**Requirement Mapping**: `REQ-FIL-022` (`REQ-FIL-022.1`..`REQ-FIL-022.3`) | **Test Mapping**: `TST-FIL-022` (`TST-FIL-022.1`..`TST-FIL-022.4`)  
**UI Consistency**: `N/A - no UI strings` (`strings*.xml` and `androidTest/` untouched)

---

## 1. Executive Summary & Implemented Changes
1. **`KinematicAltitudeSlopeEkf.kt` (`app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt`)**:
   - Added `DEFAULT_UNANCHORED_ALT_VAR = 400.0` and `@Volatile` `hasReferenceAnchor` / `hasBarometerSample` flags (reset cleanly in `reset()` and `setMode()`). On `setMode(true)` when already `isInitialized`, restores `p00 = DEFAULT_UNANCHORED_ALT_VAR`, `p01 = 0.0`, `p10 = 0.0`, `baroBias = 0.0`, `p22 = 100.0`, and `hasReferenceAnchor = false`.
   - **Baro-before-Ref (`REQ-FIL-022.1`)**: In 3-state mode (`is3StateMode == true`), `updateBarometer()` initializes unanchored barometer startup with `p00 = DEFAULT_UNANCHORED_ALT_VAR` (`400.0`) rather than `0.25`. Standstill `updateBarometer()` is guarded by `is3StateMode && hasReferenceAnchor` and floors `p22 = max(1e-6, max(p00, (1.0 - kBias) * p22))` so standstill warmup never freezes `baroBias` against pending async DEM fixes. On the first `updateReferenceAltitude(refAltM, varRef)`, `baroBias += (altitude - refAltM)`, `altitude = refAltM`, `p00 = varRef`, `p22 = 100.0`, cross-covariances (`p01`, `p10`, `p02`, `p20`, `p12`, `p21`) zero out, and `true` is returned.
   - **Ref-before-Baro & 2-State Guard (`REQ-FIL-022.2`)**: When `updateReferenceAltitude()` initializes first and the first `updateBarometer(baroAltM)` arrives in 3-state mode, `baroBias = baroAltM - altitude` is seeded directly without Huber-clamping pull. In 2-state mode (`!is3StateMode`), `p00 = varBaro` (`0.25`) and `ekf.p00 < 10.0` remain preserved.
2. **`AltitudeAndSlopeDevice_ATTFused.kt` (`app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`)**:
   - Added `resetAscentDescentAnchor()` (`mAscentDescentAnchorAltitude = Double.NaN; mLastAltitude = Double.NaN`), invoked whenever `ekf.updateReferenceAltitude(...)` returns `true` in `handleReferenceAltitude()` or `checkReferenceAltitudeFromSensors()` prior to `publishAltitudeAndMetrics()` (`REQ-FIL-022.3`).
3. **Unit Tests (`KinematicAltitudeSlopeEkfTest.kt`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`)**:
   - Added `testThreeStateBaroBeforeRef_anchorsAndAbsorbs130mWeatherOffsetAtStandstill` (`TST-FIL-022.1`), `testThreeStateRefBeforeBaro_andTwoStateGuard_preservesP00AndClearsFlags` (20 moving + standstill updates, `TST-FIL-022.2`), `testThreeStateGpsBeforeDem_recoversFromCoarseGpsAnchorWithoutBiasFreeze`, `testTwoStateToThreeStateTransition_resetsUnanchoredP00AndReanchorsCleanly`, and `testCalibrationJumpAscentDescentGuard_zeroBogusDescentAndRealClimbAfterward_tstFil022_3` (`TST-FIL-022.3`).

---

## 2. Requirement & Test Verification Matrix
* **Tier 1 Clean-Room Post-Heal Execution** (`python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.KinematicAltitudeSlopeEkfTest" --tests "*.AltitudeAndSlopeDevice_ATTFusedTest" --tests "*.MeasuredNfrBenchmarkTest"`): **36/36 PASSED** (`0` failures, `0` errors, `0` skipped; `BUILD SUCCESSFUL`). Tier 2 full-repo suite deferred to Sprint Integration Gate; `TranslationParityTest` & `androidTest` skipped (`strings*.xml` & `androidTest/` untouched).

| Requirement | Test Spec | Verification Class & Measured Scope | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-022.1`, `REQ-FIL-022.2` | `TST-FIL-022.1`, `TST-FIL-022.2` | `KinematicAltitudeSlopeEkfTest` (10 tests: Baro/Ref `+130.4 m`, GPS->DEM recovery, 2->3 `setMode`, 2-state `p00 < 10.0`, concurrency) | **PASSED** (`10/10`) | `Verified` |
| `REQ-FIL-022.3` | `TST-FIL-022.3` | `AltitudeAndSlopeDevice_ATTFusedTest` (15 tests: `0.0 m` bogus descent guard on `-130.4 m` jump + `+25 m` real climb, hysteresis, DEM precedence) | **PASSED** (`15/15`) | `Verified` |
| `REQ-FIL-022.1`..`.3` | `TST-FIL-022.4` | `MeasuredNfrBenchmarkTest` (11 tests: EKF timing & delta-t determinism) | **PASSED** (`11/11`) | `Verified` |

---

## 3. Invariant & Governance Verification
* **Living Docs**: `REQ-FIL-022` (`docs/requirements/req_tracking_cockpit.md`) and `TST-FIL-022` (`docs/tests/tst_tracking_cockpit.md`) promoted to `[Verified]`; master indexes synchronized (`docs/requirements.md`: `1213` total, `969` Verified; `docs/tests.md`: `1220` total, `998` Verified).
* **Governance & Submodule**: `python3 tools/verify_requirement_governance.py` and `--text-file docs/engineering/walkthroughs/ATT-8575_walkthrough.md` exit `0`; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
