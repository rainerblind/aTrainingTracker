# Stage 2: Requirement & Test Specification - ATT-8891: Kinematic `BSportType` Inference & Moving-Speed Sport Identity

**Ticket**: `ATT-8891` (`3 SP`, `Feature`) | **Parent Epic**: `ATT-5908` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_01_Früh` | **Requirement Mapping**: `REQ-FIL-030` (`[Verified]`; `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`) | **Test Spec ID**: `TST-FIL-030` (`[Verified]`)  
**Branch**: `feature/ATT-8891` | **Sub-task**: `[Req & Test Spec]` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-FIL-030`)

### 1.1 Problem Statement & Rationale
* In sensorless unselected workouts (`mHaveUserSelectedSportType == false`, `DeviceManager.getSportType() == BSportType.UNKNOWN`), `BANALService.getBSportType()` stays `UNKNOWN` live and finalization classifies solely by diluted session `averageSpeed`, misclassifying multi-stop errand bike rides (`Metzger` `1.35 m/s` avg / `p90 = 6.34 m/s`, `Norma #9`, `Zum Bäcker #57`) as walking/running while leaving running features (`STRIDES`, pace) inactive.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit (`./gradlew testDebugUnitTest`) | **Localization**: `N/A - no UI strings`

### 1.3 Functional & Architectural Requirements
1. **Pure-Kotlin Kinematic `BSportType` Discriminator & Hardware Degradation (`REQ-FIL-030.1`)**: `KinematicSportTypeDiscriminator` (`banalservice/odometry/`) SHALL fuse 4 physical cues with zero Android UI/DB coupling:
   - *Coasting / Freewheeling*: $v > 3.0\text{ m/s}$ for $\ge 3.0\text{ s}$ with near-zero step rate ($f_{\text{step}} < 0.5\text{ Hz}$ or absent) and low angular/footstrike oscillation (de-biased gyro RMS $< 0.25\text{ rad/s}$ when gyro active, low `2.4–3.6 Hz` impact energy) $\to$ strong `BIKE` evidence.
   - *Step-Length & Permission-Free Footstrike Impact Coherence*: active `TYPE_STEP_DETECTOR` ($f_{\text{step}} \in [2.0, 3.6]\text{ Hz}$) OR, when unavailable (`ACTIVITY_RECOGNITION` denied, absent hardware, OEM screen-off throttling), permission-free `50-Hz` `TYPE_ACCELEROMETER` `2.4–3.6 Hz` vertical/magnitude impact periodicity reusing `ImuCadenceDsp` with $L_{\text{step}} = v / f_{\text{step}} \in [0.60, 1.75]\text{ m}$ $\to$ strong `RUN` evidence within `<= 30 s` (including `5.6 m/s` fast intervals); $f_{\text{step}} \approx 0$ or apparent distance/cycle $> 3.5\text{ m}$ at $v > 5.0\text{ m/s}$ $\to$ `BIKE`.
   - *Carriage Mode Coupling*: `BIKE_*` modes vote `BIKE`; `RUN_*` modes vote `RUN`; `STATIONARY`/`WALKING_TRANSITION`/`UNKNOWN` remain neutral.
   - *Stop-Filtered Moving Speed Distribution (`MovingSpeedSummary`)*: track moving samples ($v > 0.8\text{ m/s}$) into `movingMeanMps`, `p50Mps`, `p90Mps`, `movingSampleCount`, `coastingSeconds`; prioritize kinematic evidence over speed cutoffs so `5.6 m/s` runners classify `RUN` and `2.0 m/s` uphill MTB cyclists avoid false `RUN` latching. Bounded evidence + hysteresis MUST permit `RUN <-> BIKE` recovery and degrade cleanly without barometer, gyro, rotation vector, or `ACTIVITY_RECOGNITION`.
2. **Split Sport-Context Hint & `SpeedAndLocationDevice_ATTFused` Wiring (`REQ-FIL-030.2`)**:
   - `PhoneCarriageClassifier.setSportContext(...)` MUST read strictly `BANALService.getAuthoritativeBSportType()` (Tier 1+2 only) to prevent circular latching.
   - Downstream `SmartphoneImuCadenceEstimator` and `applyCadenceAndStrideSensors()` MUST read full `BANALService.getBSportType()` (Tier 1>2>3) so inferred `RUN` activates `STRIDES`, retains stride counting across `WALKING_TRANSITION` breaks, and suppresses false cycling RPM on carriage flicker.
   - Inferred transitions SHALL call internal `handleSportTypeChanged()`, MUST NOT broadcast `SPORT_TYPE_CHANGED_BY_USER_INTENT`, and SHALL reset cleanly on `onAccumulatorsReset()` / `shutDown()`.
3. **`BANALService` 4-Tier Precedence, Unified `getSportTypeId` & Lifecycle Hygiene (`REQ-FIL-030.3`)**:
   - Enforce volatile, zero-SQLite precedence: (1) `mHaveUserSelectedSportType` (preserving explicit `WALK`/`OTHER` whose `BSportType == UNKNOWN`) $>$ (2) `DeviceManager.getSportType()` (`!= UNKNOWN`) $>$ (3) high-confidence `mInferredBSportType` $>$ (4) `UNKNOWN`.
   - Route both `getSportTypeId()` and `getSportTypeId(double avgSpd)` through `getBSportType()` when `!mHaveUserSelectedSportType`.
   - Register a `RESET_ACCUMULATORS_INTENT` receiver in `BANALService` (unregistered in `onDestroy()`) clearing `mInferredBSportType` and `mMovingSpeedSummary` while **strictly preserving** `mHaveUserSelectedSportType`, `mUserSelectedSportTypeId`, and `mUserSelectedBSportType` (`onDestroy()` clears all).
4. **Moving-Speed Discovery, Sparse-Sample Floor & Finalization (`REQ-FIL-030.4`)**:
   - Preserve existing `EquipmentAndSportTypeDiscoveryManager` 2/3-arg signatures and add moving-speed-aware resolution: enforce `MIN_MOVING_SAMPLES_FOR_IDENTITY = 10` moving seconds below which resolution falls back strictly to `sessionAvgSpeed` (protecting indoor/treadmill/short walks); when `>= 10`, use `movingMeanMps` for `UNKNOWN`/`RUN` (so `p90` never pushes a fast `4.3–5.6 m/s` `UNKNOWN` runner into `MTB`/`BIKE`) and representative cycling cruising speed (`max(movingMeanMps, 0.5 * (p50Mps + p90Mps))`) under `BSportType.BIKE` so errand rides (`Metzger`, `Norma #9`, `Zum Bäcker #57`) resolve cleanly to `BIKE` (`Endurance` / linked bike gear).
   - Add 1-Hz moving-speed fallback in `LiveWorkoutSession` for GPS-only sessions lacking `ATT_FUSED`; preserve Rule 20 DTO compatibility on `BanalFinalizationSnapshot` (retaining 4-arg/6-arg `capture` overloads); strictly preserve `mDistanceTotal_m / mTimeActive_s` for `WorkoutSummaries.SPEED_AVERAGE_mps` and `SensorType.SPEED_mps` `AVG` extrema (`SCRUM-108` / `REQ-TRK-030.3`) with zero new main-thread SQLite queries.
5. **Monolith LoC Bounds & `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` Parity (`REQ-FIL-030.5`)**: Maintain `TrackerService.java <= 3,556` (`0` net growth), `DeviceManager.java <= 1,707` (`0` edits), `SpeedAndLocationDevice_ATTFused.kt < 1,500`, `BANALService.java <= 1,035`, zero DB schema changes, `N/A - no UI strings`, `0` new `src/main` test reads (`REQ-PRO-049.1`), and Stage 4–5 doc sync (`architecture.md`, `banal_service.md`, `sysreq_live_tracking.md`, `system_architecture.md`, `system_software_traceability.md`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-FIL-030` extending `REQ-SEN-028`, `REQ-FIL-028`, `REQ-FIL-029`, `REQ-SET-030`, `REQ-SET-064`, and `REQ-TRK-030`.
* **Historical Origin & Commit Trace**: `SCRUM-108` / `ATT-3986` (`REQ-TRK-030.3`), `ATT-773` (`REQ-SET-064`), `fee3e805` (`ATT-8890`), `a3a5942a` (`ATT-8895`).
* **Root Reason for Existing Formulation**: Sensorless sessions previously lacked 50-Hz carriage/footstrike DSP, so sport inference deferred to session-wide `distance / activeTime` while `SCRUM-108` locked `SPEED_mps` `AVG` extrema to `distance / activeTime`.
* **Preservation of Core Invariants**: Tier 1 user selection (incl. `WALK`/`OTHER`) and Tier 2 external hardware sensors stay 100% sovereign; `SPEED_mps` `AVG` extrema stays `distance / activeTime`; `PhoneCarriageClassifier` never gets circular hints; Rule 20 DTO and discovery APIs stay backward-compatible.

---

## 3. Acceptance Criteria (Given-When-Then) & System Invariants
* **AC1 (Multi-Stop Errand Bike Rides)**: *Given* sensorless unselected rides (`Metzger` `1.35 m/s` avg / `p90 = 6.34 m/s` / coasting, `Norma #9`, `Zum Bäcker #57`), *When* live inference and finalization run, *Then* `BANALService` infers `BIKE`, finalization resolves cycling `SportType`/`Equipment`, and `SPEED_mps` `AVG` extrema remains `mDistanceTotal_m / mTimeActive_s`.
* **AC2 (Sensorless Running Across Budget & Permission-Restricted Phones)**: *Given* `Kurze Runde #52` (`1.9–2.6 m/s`) and `5.6 m/s` fast intervals with `0 s` coasting and missing barometer/gyro + denied `ACTIVITY_RECOGNITION`, *When* 50-Hz `ACC` + GNSS speed stream, *Then* `BSportType.RUN` activates within `<= 30 s`, `STRIDES` stays active across `WALKING_TRANSITION`, and finalization resolves `RUN`.
* **AC3 (Slow Uphill MTB & `RUN -> BIKE` Recovery)**: *Given* a `2.0 m/s` uphill MTB climb or an initial jog followed by `>= 3 s` coasting (`v > 3.0 m/s`), *When* evaluated, *Then* uphill MTB avoids false `RUN` lock and `RUN -> BIKE` recovers without circular carriage latching.
* **AC4 (Tier 1/2 Precedence & `RESET_ACCUMULATORS_INTENT`)**: *Given* explicit user sport (incl. `WALK`/`OTHER`) or active `BIKE_POWER`/`RUN_SPEED` sensor, *When* discriminator runs and `RESET_ACCUMULATORS_INTENT` fires, *Then* Tier 1/2 is never overridden, user selection survives reset, and Tier 3 resets cleanly.
* **AC5 (Sparse Moving-Sample Floor)**: *Given* an indoor/treadmill session or short walk with `< 10` moving seconds, *When* `EquipmentAndSportTypeDiscoveryManager` resolves identity, *Then* it falls back strictly to `sessionAvgSpeed`.
* **System Invariants**: `User (incl. WALK/OTHER) > Hardware > Kinematic > speed`; split authoritative vs. full sport hint; `SPEED_mps` `AVG = distance/time`; zero IMU/Main-thread SQLite; `TrackerService <= 3,556`, `DeviceManager <= 1,707`, `SpeedAndLocationDevice_ATTFused < 1,500`; `N/A - no UI strings`; `0` new `src/main` test reads.

---

## 4. Test Specification (`TST-FIL-030`)
* **`TST-FIL-030.1` (`KinematicSportTypeDiscriminatorTest`)**: Errand rides (`2176`/`2173`/`2191`), `Kurze Runde #52` & `5.6 m/s` intervals (full & degraded no-baro/no-gyro/no-`ACTIVITY_RECOGNITION` via 50-Hz `ACC` `ImuCadenceDsp`), `2.0 m/s` uphill MTB, `RUN -> BIKE` recovery, coasting gate, stop-filtered `MovingSpeedSummary`.
* **`TST-FIL-030.2` (`SpeedAndLocationDevice_ATTFusedTest`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest`)**: Split `getAuthoritativeBSportType()` -> `PhoneCarriageClassifier` vs. full `getBSportType()` -> `SmartphoneImuCadenceEstimator`/`STRIDES` (across `WALKING_TRANSITION`), zero `SPORT_TYPE_CHANGED_BY_USER_INTENT` on inference, `onAccumulatorsReset`/`shutDown` reset.
* **`TST-FIL-030.3` (`BANALServiceSportInferenceTest`, `BANALServiceLifecycleTest`)**: 4-tier precedence (incl. explicit `WALK`/`OTHER`), unified `getSportTypeId()` / `getSportTypeId(avgSpd)`, `RESET_ACCUMULATORS_INTENT` preserving user selection while clearing Tier 3 + moving summary, `onDestroy()` full clear, zero hot-path SQLite.
* **`TST-FIL-030.4` (`EquipmentAndSportTypeDiscoveryManagerMovingSpeedTest`, `TrackerServiceFinalizationTest`)**: `>= 10` moving-sample floor fallback, `movingMeanMps` protecting `UNKNOWN` fast `4.3–5.6 m/s` runners vs. `BIKE` cruising speed resolving errand rides to `Endurance`/bike gear, backward-compatible signatures & `BanalFinalizationSnapshot` overloads, `LiveWorkoutSession` 1-Hz fallback, `SPEED_mps` `AVG` extrema invariant.
* **`TST-FIL-030.5` (Contracts & Governance)**: `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SystemArchitectureDesignContractTest`, `SourceTextReadingAuditContractTest`, `verify_requirement_governance.py` (`exit 0`).

---

## 5. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-FIL-030.1` (`SYSREQ-TRK-010`) | Kinematic discriminator (coasting, step/`50-Hz ACC` impact, carriage, moving speed, degradation) | `TST-FIL-030.1` | Unit (`KinematicSportTypeDiscriminatorTest`) |
| `REQ-FIL-030.2` (`SYSREQ-TRK-003`/`010`) | Anti-circular split sport context hint & `ATTFused` `STRIDES`/reset wiring | `TST-FIL-030.2` | Unit (`SpeedAndLocationDevice_ATTFusedTest`) |
| `REQ-FIL-030.3` (`SYSREQ-TRK-003`) | `BANALService` 4-tier precedence, unified `getSportTypeId`, `RESET_ACCUMULATORS_INTENT` hygiene | `TST-FIL-030.3` | Unit (`BANALServiceSportInferenceTest`) |
| `REQ-FIL-030.4` (`SYSREQ-TRK-004`) | Moving-speed discovery (`>= 10` floor, sport-aware metric), `LiveWorkoutSession` & `TrackerService` finalization | `TST-FIL-030.4` | Unit (`EquipmentAndSportTypeDiscoveryManagerMovingSpeedTest`, `TrackerServiceFinalizationTest`) |
| `REQ-FIL-030.5` (`REQ-PRO-131.4`, `Rule 8.8`) | Monolith LoC bounds, `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` parity & `0` `src/main` test reads | `TST-FIL-030.5` | Contract & Governance Check |
