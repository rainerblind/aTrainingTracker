# Stage 5: Walkthrough & Verification - ATT-8576: att_fused Cold GNSS Jump Gating, Standstill Slope Clearing & Low-Speed Slope Damping

**Ticket**: `ATT-8576` (`2 SP`, Epic `ATT-166`) | **Target Release**: `V5.0.0`  
**Branch**: `feature/ATT-8576` (rebased atop `sprint/2026-11-17_02_Spät` @ `d7dec44702aee8e9eed20946cc954a153e4be441`)  
**Requirement Mapping**: `REQ-FIL-023` (`REQ-FIL-023.1`..`REQ-FIL-023.3`) | **Test Mapping**: `TST-FIL-023` (`TST-FIL-023.1`..`TST-FIL-023.4`)  
**UI Consistency**: `N/A - no UI strings` (`strings*.xml` and `androidTest/` untouched; `REQ-PRO-049.1` behavioral tests only)

---

## 1. Executive Summary & Implemented Changes
1. **`UnicycleOdometryEkf.kt` & `SpeedAndLocationDevice_ATTFused.kt` (`REQ-FIL-023.1`)**:
   - Added `applyZeroVelocityUpdate()` (ZUPT clamping `x[2] = 0.0`, keeping `p[2][2] >= ZUPT_SPEED_VARIANCE = 2.0` for start acceleration responsiveness, decoupling `p[*][2]` cross-covariances) and stationary jump gating (`max(8.0, 0.75 * acc)`) + moving Huber outlier weighting (`GNSS_HUBER_GAMMA = 3.0`) in `updateGnssPosition()`.
   - Deferred `refLat`/`refLon` anchoring and `onNewLocation()` emission until first `<= 20.0 m` GNSS accuracy fix; ingested speed/ZUPT before `updateGnssPosition()` under `synchronized(ekf)`; preserved `location.accuracy = max(acc, sqrt(fusedPosVar))` so `SpeedAndLocationDevice.onNewLocation()` (`REQ-TRK-056`) does not shrink `escapeRadius`; reset origin/EKF in `onAccumulatorsReset()` and `shutDown()`.
2. **`KinematicAltitudeSlopeEkf.kt` & `AltitudeAndSlopeDevice_ATTFused.kt` (`REQ-FIL-023.2`, `REQ-FIL-023.3`)**:
   - Added standstill exponential slope decay (`STANDSTILL_SLOPE_GRACE_SEC = 2.0`, `tau = 2.5 s`) while preserving `dh = 0`, `Q_g = 0`, and `REQ-FIL-022` `baroBias`; added quadratic low-speed slope damping below `LOW_SPEED_SLOPE_THRESHOLD_MPS = 1.2 m/s`, `MAX_SLOPE_VARIANCE = 0.015`, and `±35%` `MAX_PHYSICAL_SLOPE` clamping.
   - Passed `speedMps` to `ekf.updateBarometer()` and deterministic `nowMs` to `publishAltitudeAndMetrics()`, publishing decaying/`0%` slope during sustained standstill (`STANDSTILL_SLOPE_CLEAR_DELAY_MS = 10_000L`) instead of freezing pre-stop slope.

---

## 2. Requirement & Test Verification Matrix
* **Tier 1 Clean-Room Post-Rebase Execution** (`python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests ... :app:assembleDebug`): **102/102 PASSED** across **11 suites** (`0` failures, `0` errors, `0` skipped; `:app:compileDebugUnitTestKotlin` & `:app:assembleDebug` `BUILD SUCCESSFUL` in `3m 52s`). Tier 2 full-repo suite deferred to Sprint Integration Gate; `TranslationParityTest` & `androidTest` skipped (`strings*.xml` & `androidTest/` untouched).

| Requirement | Test Spec | Verification Class & Observed Scope | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-023.1` | `TST-FIL-023.1` | `SpeedAndLocationDevice_ATTFusedTest` (`8/8`), `UnicycleOdometryEkfTest` (`10/10`) | **PASSED** (`18/18`) | `Verified` |
| `REQ-FIL-023.2`, `.3` | `TST-FIL-023.2`, `.3` | `AltitudeAndSlopeDevice_ATTFusedTest` (`17/17`), `KinematicAltitudeSlopeEkfTest` (`12/12`) | **PASSED** (`29/29`) | `Verified` |
| `REQ-TRK-056`, `REQ-FIL-009`, `REQ-FIL-012`, `REQ-FIL-017` | `TST-FIL-023.4` | `SpeedAndLocationDeviceStationaryJitterTest` (`15/15`), `...IndoorAndRestCouplingTest` (`10/10`), `...DisplacementTest` (`4/4`), `VerticalSpeedAndSlopeDeviceTest` (`16/16`), `OdometrySimulationTest` (`3/3`), `ExtendedRtsOdometrySmootherTest` (`5/5`), `ExtendedRtsAltitudeSlopeSmootherTest` (`2/2`) | **PASSED** (`55/55`) | `Verified` |

---

## 3. Invariant & Governance Verification
* **Living Docs**: `REQ-FIL-023` (`docs/requirements/req_tracking_cockpit.md`) and `TST-FIL-023` (`docs/tests/tst_tracking_cockpit.md`) promoted to `[Verified]`; master indexes synchronized (`docs/requirements.md`: `1222` total, `978` Verified; `docs/tests.md`: `1229` total, `1007` Verified).
* **Governance & Submodule**: `python3 tools/verify_requirement_governance.py` and `--text-file docs/engineering/walkthroughs/ATT-8576_walkthrough.md` exit `0`; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
