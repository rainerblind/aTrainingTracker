# Stage 2: Requirement & Test Specification - ATT-9157: Calibrate PhoneCarriageClassifier for Front-Hip Running Belt, Easy-Pace Running, and Sport-Context Guardrails

**Ticket**: `ATT-9157` (`3 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9157` (base `e11a8123`, includes `ATT-9062`)  
**Requirement Mapping**: `REQ-FIL-035` (`REQ-FIL-035.1`–`.3`, refining `REQ-FIL-028.1`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-FIL-031`, `REQ-FIL-034`)  
**Test Spec ID**: `TST-FIL-035` (`TST-FIL-035.1`–`.3`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-FIL-035`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Implementation**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifier.kt` (`0` edits to `SpeedAndLocationDevice_ATTFused.kt` or any protected monolith)

### 1.2 Functional & Architectural Requirements
1. **Sport-Context Cycling Guardrail with Triathlon/Brick Override & Non-Sticky Eviction (`REQ-FIL-035.1`)**:
   - In `PhoneCarriageClassifier.evaluateRawCandidate()`, when `suppressCyclingForRun` holds — defined strictly as `sportHint == SportContextHint.RUNNING && !wheelActiveMoving && (wheelSpeed == null || wheelSpeed <= 0.5) && (effectiveSpeed == null || effectiveSpeed < 5.5)` — `PhoneCarriageClassifier` **SHALL** mask `BIKE_HANDLEBAR`, `BIKE_JERSEY_BACK`, and `BIKE_FRONT_POCKET` (excluding them from candidate selection and zero-IMU fallback) so cycling carriage modes occur `0%` of the time during normal running workouts.
   - When `suppressCyclingForRun` is `true` and `committedMode` is any `BIKE_*` mode, `committedScore` **SHALL** evaluate to `Double.NEGATIVE_INFINITY` so `SCORE_MARGIN` stickiness never retains a stale `BIKE_*` mode once `Sport = RUN` activates without cycling evidence.
   - As soon as a bike wheel speed sensor reports moving speed (`wheelActiveMoving || wheelSpeed > 0.5`) OR moving speed reaches cycling velocity (`effectiveSpeed >= 5.5 m/s`), `suppressCyclingForRun` **SHALL** evaluate to `false`, preserving triathlon/duathlon/brick `RUN -> BIKE` transition detection (`REQ-FIL-031`).
2. **Easy-Pace Running Walking-Gate Calibration & Front-Hip Running Belt Scoring (`REQ-FIL-035.2`)**:
   - `PhoneCarriageClassifier.AccelFootstrikeEval` **SHALL** expose `impactRmsMps2: Double?` (`2.1–4.2 Hz` bandpass RMS computed from the existing `ImuCadenceDsp` time-based `50 Hz` ring buffer).
   - `PhoneCarriageClassifier` **SHALL** compute `easyRunSuppressWalk` scoped strictly to `sportHint == SportContextHint.RUNNING && effectiveSpeed != null && effectiveSpeed >= 1.65` whenever: (a) vertical impact RMS indicates running (`impactRmsMps2 != null && impactRmsMps2 >= 1.8`), OR (b) detected running/easy-run step cadence `>= 2.15 Hz`, OR (c) `effectiveSpeed >= 1.75` and accelerometer does not report low-impact walking (`impactRmsMps2 == null || impactRmsMps2 >= 1.2`).
   - When `easyRunSuppressWalk` is `true`, **BOTH** `walkingStepBand` AND `walkingSpeedBand` short-circuits to `WALKING_TRANSITION` **SHALL** be bypassed, so easy-pace runs (`1.65–2.5 m/s`, `2.15–2.30 Hz` or batched/dropped step events) classify into `RUN_*`, while `effectiveSpeed == null`, slow obstacle climbs (`< 1.65 m/s`), low-impact walking (`impactRmsMps2 < 1.2`), and all non-`RUNNING` (`UNKNOWN`/`CYCLING`) walking/dismount flows remain unaffected.
   - `scoreRunWaistHip()`, `scoreRunBicepArm()`, and `scoreRunHandheld()` **SHALL** use smooth, simulation-verified running carry tiers yielding clean winning margins across all running placements:
     - `scoreRunWaistHip`: `swingSdDeg < 10.0 -> +2.6`, `< 15.5 -> +1.8`, `< 22.0 -> -0.5`, else `-2.0`; `yawResidualRms <= 0.48 -> +2.2`, `<= 0.68 -> +1.4`, `<= 0.95 -> -0.4`, else `-1.5`; `baroOscMps < 0.45 -> +1.2`; `stepFreq >= 2.15 -> +0.8`; `glanceCount == 0 -> +1.2`, `glanceCount >= 1 -> -1.5`.
     - `scoreRunBicepArm`: `swingSdDeg >= 15.5 -> +2.6`, `>= 11.5 -> +0.8`, else `-0.8` (preserving `17.0°` bicep armband separation).
     - `scoreRunHandheld`: `swingSdDeg >= 15.0 -> +1.8`, `>= 11.0 -> +0.4`; `yawResidualRms >= 0.55 -> +2.6`, `>= 0.42 -> +0.8`; `baroOscMps >= 0.45 -> +1.8`; `glanceCount >= 1 -> +2.8`, `glanceCount == 0 -> -1.4` (preserving `SpeedAndLocationDevice_ATTFusedTest:865` wrist-glance `RUN_HANDHELD` detection).
3. **Chatter-Resistant Margin & Asymmetric Running Dwell Hysteresis (`REQ-FIL-035.3`)**:
   - `PhoneCarriageClassifier.SCORE_MARGIN` **SHALL** be raised from `0.25` to `0.85` to eliminate 1-second score-noise ping-pong during steady running.
   - Transition dwell hysteresis **SHALL** be asymmetric by transition direction:
     - Verified wrist-glance `RUN_* -> RUN_HANDHELD` (when `isGlanceActive(nowMs)` is `true`): immediate (`0 ms`, unchanged).
     - `RUN_* -> WALKING_TRANSITION`: `mountHysteresisMs` (`4_000L` default) so brief 2-second canopy GPS speed dips do not ping-pong steady runs into walking.
     - Intra-run non-glance re-mount (`RUN_* -> RUN_*` when `!isGlanceActive(nowMs)`): `maxOf(mountHysteresisMs, 6_000L)` (`6.0 s` default) to eliminate mid-run waist/bicep/handheld chatter.
     - `WALKING_TRANSITION -> RUN_*`, `RUN_* -> STATIONARY`, `STATIONARY <-> *`, and `BIKE_* <-> WALKING_TRANSITION`: fast `dismountHysteresisMs` (`2_000L` default), preserving `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` Phase 2 `5.0 s` walk→run transitions and `ATT-9062` / `Zum Bäcker` responsiveness.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (`REQ-FIL-035.1` — Sport-Context Guardrail & Brick Override)**:
  - *Given* `sportHint == SportContextHint.RUNNING`, `wheelSpeed == null` (or `<= 0.5 m/s`), and `effectiveSpeed < 5.5 m/s` (including front-pocket-like IMU signals or stale/stale-BIKE committed mode), *When* `PhoneCarriageClassifier.classify()` runs, *Then* `BIKE_HANDLEBAR`, `BIKE_JERSEY_BACK`, and `BIKE_FRONT_POCKET` are masked (`0%` cycling output) and any prior `BIKE_*` `committedMode` is immediately non-sticky (`committedScore = -Inf`).
  - *Given* `sportHint == SportContextHint.RUNNING` followed by wheel speed `> 0.5 m/s` or sustained speed `>= 5.5 m/s` with cycling kinematics, *When* `PhoneCarriageClassifier.classify()` runs, *Then* the cycling mask lifts and `BIKE_*` classifies normally (`ATT-8917` triathlon/brick parity).
* **AC-2 (`REQ-FIL-035.2` — Easy-Pace Run, Front-Hip Belt & Carry-Mode Separation)**:
  - *Given* a `Sport = RUN` workout carried in a front-hip running belt (`1.8–2.5 m/s`, `2.15–2.30 Hz` or dropped step detector, `impactRms >= 1.8 m/s²`, `swingSdDeg = 10–15°`, `yawResidualRms = 0.38–0.65 rad/s`, `baroOscMps < 0.45`, `glanceCount == 0`), *When* `PhoneCarriageClassifier.classify()` evaluates, *Then* `easyRunSuppressWalk` bypasses both `walkingStepBand` and `walkingSpeedBand` and output classifies cleanly as `RUN_WAIST_HIP (5)` across mild, lively, bouncy, and low-yaw belt profiles.
  - *Given* a slow obstacle climb during the same run (`speed = 1.0–1.4 m/s`, `stepFreq = 1.5 Hz`, `impactRms < 1.2 m/s²`), *When* `classify()` evaluates past `mountHysteresisMs`, *Then* mode transitions to `WALKING_TRANSITION (1)`.
  - *Given* genuine `RUN_BICEP_ARM` (`swingSd >= 15.5°`, e.g. `17.0°`), `RUN_HANDHELD` (active glance and no-glance high swing/yaw/baro), and `SpeedAndLocationDevice_ATTFusedTest:865` wrist glance, *When* `classify()` evaluates, *Then* each mode still wins with positive margin (`9/9` carry-mode matrix).
* **AC-3 (`REQ-FIL-035.3` — Chatter Reduction & Asymmetric Dwell)**:
  - *Given* a steady front-hip belt run perturbed by 2–3 s canopy GPS speed dips (`1.3 m/s`) and transient 4 s handheld/bicep-like IMU bursts without a verified glance, *When* `PhoneCarriageClassifier.classify()` runs, *Then* `SCORE_MARGIN = 0.85`, `4.0 s` `RUN -> WALK` dwell, and `6.0 s` intra-run non-glance dwell hold `RUN_WAIST_HIP (5)` cleanly (`0` spurious chatter switches), while a genuine wrist glance still switches `RUN_WAIST_HIP -> RUN_HANDHELD` immediately (`0 ms`), `WALKING_TRANSITION -> RUN_*` switches in `2.0 s`, and `RUN_* -> STATIONARY` switches in `2.0 s`.

### 1.4 System Invariants & Localization
- `PhoneCarriageMode` enum codes (`-1..7`) & `CarriageFilterProfile` untouched; `SpeedAndLocationDevice_ATTFused.kt = 1,459 / 1,500` LoC (`0` edits); `PhoneCarriageClassifier.kt <= 1,500` LoC (`~1,135` target); `0` protected monolith edits; all `ATT-9062` (`REQ-FIL-034`) & `ATT-8890` (`REQ-FIL-028`) regression parity preserved; time-based windows only; `0` DB schema bumps; `0` new `src/main` test reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

---

## 2. Test Specification (`TST-FIL-035`)

* **`TST-FIL-035.1` (Sport-Context Cycling Guardrail, Non-Sticky Eviction & Brick Override Suite — `PhoneCarriageClassifierTest.kt`)**:
  - Verify `sportHint == RUNNING` with `wheelSpeed == null` (and `wheelSpeed = 0.4 m/s`), `effectiveSpeed = 2.0–4.8 m/s`, and cycling-like front-pocket IMU (`swingSd = 22°`, `yaw = 0.65`, `baro = 0.50`, `stepDetectorRegistered = true` zero steps) emits `0%` `BIKE_*` modes (never `BIKE_FRONT_POCKET`/`BIKE_JERSEY_BACK`/`BIKE_HANDLEBAR`).
  - Verify a previously committed `BIKE_FRONT_POCKET` mode is immediately non-sticky (`committedScore = -Inf`) when `sportHint` switches to `RUNNING` without wheel/`>= 5.5 m/s` cycling evidence.
  - Verify triathlon/brick override: once `wheelSpeed = 6.0 m/s` OR `gnssSpeed = 6.5 m/s` (`>= 5.5 m/s`) arrives during `sportHint == RUNNING` without footstrikes, `suppressCyclingForRun` lifts and `BIKE_*` classifies cleanly.
* **`TST-FIL-035.2` (Easy-Pace Run, Front-Hip Belt Calibration & 9-Profile Carry-Mode Separation Suite — `PhoneCarriageClassifierTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`)**:
  - Verify easy-pace front-hip running belt replay (`1.8–2.5 m/s`, `2.20 Hz` in `walkingStepBand` AND dropped step detector with `impactRms >= 1.8 m/s²`, `swingSd = 10–15°`, `yaw = 0.38–0.65 rad/s`, `baro < 0.45`, `glance == 0`) bypasses both `walkingStepBand` and `walkingSpeedBand` and classifies as `RUN_WAIST_HIP (5)` across mild, lively, bouncy, and low-yaw belt profiles.
  - Verify genuine obstacle/walking stretch during `Sport = RUN` (`speed = 1.2 m/s`, `stepFreq = 1.5 Hz`, `impactRms < 1.2 m/s²` for `>= 4.0 s`) still transitions to `WALKING_TRANSITION (1)`.
  - Verify separation of rear waist clip (`RUN_WAIST_HIP`), bicep armband (`RUN_BICEP_ARM`, `17.0°`), handheld with glance (`RUN_HANDHELD`), handheld without glance (high swing/yaw/baro), and `SpeedAndLocationDevice_ATTFusedTest:testHandheldGlanceGatingAndRunningChordHeading_tstFil028_3`.
* **`TST-FIL-035.3` (Chatter-Resistant Margin, Asymmetric Dwell & Regression/Contract Suite — `PhoneCarriageClassifierTest.kt`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest.kt`, Contracts)**:
  - Verify a steady front-hip belt run with 2.5 s canopy GPS speed dips (`1.2 m/s`) and 4.5 s non-glance IMU perturbations retains `RUN_WAIST_HIP (5)` with `0` spurious mode switches (`SCORE_MARGIN = 0.85`, `4.0 s` `RUN -> WALK` dwell, `6.0 s` intra-run non-glance dwell).
  - Verify verified wrist glance still switches `RUN_WAIST_HIP -> RUN_HANDHELD` in `0 ms`, `WALKING_TRANSITION -> RUN_*` switches within `2.0 s` (preserving `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` Phase 2 `5.0 s` walk→run), and `RUN_* -> STATIONARY` switches within `2.0 s`.
  - Verify full regression across `PhoneCarriageClassifierTest` (incl. `ATT-9062` `TST-FIL-034.1`–`.4` & `ATT-8890` `TST-FIL-028`), `SpeedAndLocationDevice_ATTFusedTest`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest`, `AltitudeAndSlopeDevice_ATTFusedTest`, `SmartphoneImuCadenceEstimatorTest`, `KinematicSportTypeDiscriminatorTest`, `MultisportTransitionDetectorTest`, `ArchitectureConformanceContractTest` (`SpeedAndLocationDevice_ATTFused.kt = 1,459 / 1,500`, `PhoneCarriageClassifier.kt <= 1,500`, `0` monolith edits), `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, and `SourceTextReadingAuditContractTest`.

---

## 3. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-035.1` | Sport-context cycling mask, non-sticky `BIKE_*` eviction, brick override | `PhoneCarriageClassifierTest` | `REQ-FIL-035.1` | Planned |
| `TST-FIL-035.2` | Easy-pace running `walkingStepBand`/`walkingSpeedBand` bypass, front-hip belt tiers, 9-profile separation | `PhoneCarriageClassifierTest`, `SpeedAndLocationDevice_ATTFusedTest` | `REQ-FIL-035.2` | Planned |
| `TST-FIL-035.3` | `SCORE_MARGIN = 0.85`, asymmetric `4 s`/`6 s`/`2 s`/`0 ms` dwell, regression & contracts | `PhoneCarriageClassifierTest`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest`, `ArchitectureConformanceContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest` | `REQ-FIL-035.3` | Planned |
