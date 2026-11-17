# Stage 5: Walkthrough & Verification - ATT-8576: att_fused Cold GNSS Jump Gating, Standstill Slope Clearing & Low-Speed Slope Damping

**Ticket**: `ATT-8576` (`2 SP`, Epic `ATT-166`) | **Target Release**: `V5.0.0`  
**Branch**: `feature/ATT-8576` (rebased atop `sprint/2026-11-17_02_Spät` @ `2de2a41c9b7d4571428b9b6b6f858aed67ec2de7`)  
**Requirement Mapping**: `REQ-FIL-023` (`REQ-FIL-023.1`..`REQ-FIL-023.3`) | **Test Mapping**: `TST-FIL-023` (`TST-FIL-023.1`..`TST-FIL-023.4`)  
**UI Consistency**: `N/A - no UI strings` (`strings*.xml` and `androidTest/` untouched; `REQ-PRO-049.1` behavioral tests only)

---

## 1. Executive Summary & Implemented Changes (Incl. Stage 6 Bounce 1/3 & 2/3 Heals)
1. **`UnicycleOdometryEkf.kt` & `SpeedAndLocationDevice_ATTFused.kt` (`REQ-FIL-023.1`)**:
   - Added `applyZeroVelocityUpdate()` (`x[2] = 0.0`, `p[2][2] >= ZUPT_SPEED_VARIANCE = 2.0`, decoupled velocity cross-covariances) and `reanchorPosition(0.0, 0.0)` inside `synchronized(ekf)` on first `<= 20.0 m` anchor, resetting position variance (`initialPosVar`) and zeroing all position cross-covariances via `resetPositionCovariance()` so `p[3][3]`/`p[4][4]` do not collapse.
   - Added temporally & spatially adjacent stationary cluster recovery (`dt <= 5000 ms`, `>= 3` fixes or `>= 2` fixes after `>= 15 s` outage / `<= 0.5x` accuracy tightening on both fixes) with one-shot `consumeStationaryClusterSnapped()`, unconditionally syncing `mStationaryAnchor`, `mIsStationaryPinned = true`, `mStationaryHysteresisCount = 0`, and filtered coordinates on genuine snaps while never dragging `mStationaryAnchor` on normal within-gate fixes (`REQ-TRK-048.2`) and clearing moving-outlier streaks on snap.
   - Added post-outage moving cluster recovery (`>= 15 s` gap + `>= 2` agreeing moving fixes, or `>= 3` agreeing fixes) inflating `p[0][0]`/`p[1][1]` while keeping single-epoch 120 m spikes Huber-damped; marked wheel state `@Volatile`, read `ekf.speedMps` under `synchronized(ekf)`, used `abs(dt) <= 5000L` HAL clock-skew tolerance, and preserved `location.accuracy >= acc`.
2. **`KinematicAltitudeSlopeEkf.kt` & `AltitudeAndSlopeDevice_ATTFused.kt` (`REQ-FIL-023.2`, `REQ-FIL-023.3`)**:
   - Added standstill exponential slope decay (`STANDSTILL_SLOPE_GRACE_SEC = 2.0`, `tau = 2.5 s`, symmetric `p01`/`p12` decay, `dh = 0`, `Q_g = 0`, preserved `REQ-FIL-022` `baroBias`), quadratic low-speed damping below `1.2 m/s`, `MAX_SLOPE_VARIANCE = 0.015`, and `±35%` grade bounds; passed `speedMps` and deterministic `nowMs` (`handleReferenceAltitude` + `publishAltitudeAndMetrics`) to clear `SLOPE` to `0%` on sustained stops.

---

## 2. Requirement & Test Verification Matrix
* **Tier 1 Clean-Room Post-Final-Edit Execution** (`python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:assembleDebug :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice*" --tests "com.atrainingtracker.banalservice.devices.VerticalSpeedAndSlopeDeviceTest" --tests "com.atrainingtracker.banalservice.odometry.*"`): **154/154 PASSED** across **16 suites** (`0` failures, `0` errors, `0` skipped; `:app:assembleDebug` + `:app:testDebugUnitTest` `BUILD SUCCESSFUL`). Tier 2 full-repo suite deferred to Sprint Integration Gate.

| Requirement | Test Spec | Verification Class & Observed Scope | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-023.1` | `TST-FIL-023.1` | `SpeedAndLocationDevice_ATTFusedTest` (`12/12`), `UnicycleOdometryEkfTest` (`14/14`) | **PASSED** (`26/26`) | `Verified` |
| `REQ-FIL-023.2`, `.3` | `TST-FIL-023.2`, `.3` | `AltitudeAndSlopeDevice_ATTFusedTest` (`17/17`), `KinematicAltitudeSlopeEkfTest` (`13/13`) | **PASSED** (`30/30`) | `Verified` |
| `REQ-TRK-056`, `REQ-FIL-009`, `REQ-FIL-012`, `REQ-FIL-017` | `TST-FIL-023.4` | `SpeedAndLocationDevice*{Decay,Displacement,GoogleFused,IndoorAndRestCoupling,Resilience,StationaryJitter}` (`58/58`), `VerticalSpeedAndSlopeDeviceTest` (`16/16`), `OdometrySimulationTest` (`3/3`), `ExtendedRts*SmootherTest` (`7/7`), `QuaternionGyroProjectorTest` (`8/8`), `WheelCircumferenceCalibratorTest` (`6/6`) | **PASSED** (`98/98`) | `Verified` |

---

## 3. Invariant & Governance Verification
* **Living Docs & Headers**: `REQ-FIL-023` (`docs/requirements/req_tracking_cockpit.md`) and `TST-FIL-023` (`docs/tests/tst_tracking_cockpit.md`) remain `[Verified]` (`docs/requirements.md`: `1223` total, `979` Verified; `docs/tests.md`: `1230` total, `1008` Verified).
* **Governance & Submodule**: `verify_requirement_governance.py --check-doc-headers` (`50/50` governed docs), `--text-file docs/engineering/walkthroughs/ATT-8576_walkthrough.md`, and full `verify_requirement_governance.py` exit `0`; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
