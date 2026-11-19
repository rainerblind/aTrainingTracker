# Stage 2: Requirement & Test Specification - ATT-8917: Multisport Transition Detection, Retroactive Anchoring & Automatic Named Laps

**Ticket**: `ATT-8917` (`3 SP`, `Feature`) | **Parent Epic**: `ATT-5908` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_01_Früh` | **Requirement Mapping**: `REQ-FIL-031` (`[Verified]`; `SYSREQ-TRK-003`, `SYSREQ-TRK-006`, `SYSREQ-TRK-010`) | **Test Spec ID**: `TST-FIL-031` (`[Verified]`)  
**Branch**: `feature/ATT-8917` | **Sub-task**: `[Req & Test Spec]` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-FIL-031`)

### 1.1 Problem Statement & Rationale
* In triathlon, duathlon, and cycling-running brick workouts, manual lap/sport presses in `T1`/`T2` are error-prone and frequently missed. Fusing the 1-Hz `PhoneCarriageMode` stream (`REQ-FIL-028`), permission-free 50-Hz accelerometer footstrike / kinematic inference (`REQ-FIL-030`), and active non-zero hardware sensor telemetry enables hands-free `BIKE <-> TRANSITION <-> RUN` detection, retroactive boundary anchoring (`t_exit_sport1`, `t_enter_sport2`), automatic named laps (`Bike`, `Transition`, `Run`) in `Laps.db`, and live cockpit sport switching without false triggers on single-sport stops.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit (`./gradlew testDebugUnitTest`) | **Localization**: 3 keys x 9 locales (`values*/strings_cockpit.xml`)

### 1.3 Functional & Architectural Requirements
1. **Pure-Kotlin Multisport Transition State Machine (`REQ-FIL-031.1`)**: `MultisportTransitionDetector` (`banalservice/odometry/`) SHALL run at 1 Hz with zero Android UI/DB coupling, fusing `PhoneCarriageMode`, `KinematicSportTypeDiscriminator` (including permission-free 50-Hz `ACC` footstrike inference so `PhoneCarriageClassifier.kt:398` `cyclingPrior += 2.5` cannot mask running in `T2` while a parked bike sensor stays connected without `TYPE_STEP_DETECTOR`), and active non-zero hardware telemetry (`BIKE_POWER`, `BIKE_SPEED_AND_CADENCE`/`BIKE_SPEED`/`BIKE_CADENCE`, `RUN_SPEED`):
   - Maintain a bounded 1-Hz anchor ring buffer `(timestampMs, activeTimeS, totalTimeS, totalDistanceM)` recording `t_exit_sport1` upon leaving confirmed `sport1` (`BIKE` or `RUN`) into `WALKING_TRANSITION`/`STATIONARY` (or direct opposite sport) and `t_enter_sport2` upon first sustained opposite-sport evidence.
   - Gate the `15 s` (`15_000 ms`) continuous confirmation window strictly on active unpaused tracking (`isTracking && !isPaused && !isAutoPaused`); transitions MUST NEVER confirm while `!isTracking`, `PAUSED`, or `AUTO_PAUSED`.
   - Upon `15 s` continuous confirmation of `sport2` (`RUN` after `BIKE`, or `BIKE` after `RUN`), emit a confirmed transition event carrying anchored `(fromSport, toSport, t_exit_sport1, t_enter_sport2, t_confirm)` with both wall-clock epoch ms and `(activeTimeS, totalTimeS, totalDistanceM)` snapshots.
   - **Same-Sport Stop Immunity & Glitch Rejection**: Returning to `sport1` after any `WALKING_TRANSITION`/`STATIONARY` stop (e.g., `160 s` bakery stop `BIKE -> WALK/STAT -> BIKE`) or transient `< 15 s` opposite-sport noise MUST reset pending candidates cleanly and emit `0` transitions.
2. **Retroactive Accumulator Reset Contract & `BANALService` Precedence (`REQ-FIL-031.2`)**:
   - `MyAccumulatorSensor.resetTo(N targetValue)` SHALL acquire `synchronized (this)`, compute `mInitialValue = sub(targetValue, mValue)`, leave `mLastAbsoluteValue` untouched (preserving cumulative ANT+/BLE `accumulate()` continuity), snapshot `active = mActivated`, and notify `mSensorListeners` **outside the lock** with `targetValue` when `active` is true, with ZERO mutation of `MySensorManager.mInitialValues`.
   - `BANALService` SHALL expose dedicated `resetAccumulatorSensorsTo(SensorType, Integer|Double)` helpers and maintain volatile confirmed multisport `BSportType` (`mMultisportBSportType`), strictly subordinate to Tier 1 user lock (`mHaveUserSelectedSportType == true` MUST block auto-multisport), while overriding Tier 2 `BSportType.CONFLICT` or silent/parked bike sensors when `!mHaveUserSelectedSportType`.
   - On confirmed transition, `BANALService` SHALL update `mMultisportBSportType`, reset `mActivityTypeMax` to the new leg's `ActivityType` (ensuring `TrackingTabsScreen` switches `BIKE <-> RUN` tabs/units even when `!changeSportWhenDeviceGetsLost()`), invoke `SpeedAndLocationDevice_ATTFused.handleSportTypeChanged()` (`STRIDES` gating) without broadcasting `SPORT_TYPE_CHANGED_BY_USER_INTENT`, and broadcast package-scoped `BANALService.MULTISPORT_TRANSITION_INTENT` (never raw `LAP_SUMMARY` popups). `RESET_ACCUMULATORS_INTENT`, `onAccumulatorsReset()`, `shutDown()`, and `onDestroy()` MUST reset all detector/multisport state cleanly.
3. **Retroactive Named Laps, Auto-Pause `T2` Policy, Manual-Lap Clamping & `WorkoutSamples.db` Sync (`REQ-FIL-031.3`)**:
   - `MultisportLapCoordinator` (`trainingtracker/tracker/`) SHALL track `currentLapStartAnchor` (updated on workout start/resume and every non-pause `BANALService.LAP_SUMMARY`), clamping `effectiveExit = max(t_exit, currentLapStart)` and `effectiveEnter = max(t_enter, effectiveExit)`.
   - **Auto-Pause inside `T2` & `REQ-TRK-002` Pre-Increment Evaluation**: `Transition` lap eligibility SHALL evaluate wall-clock `(effectiveEnterMs - effectiveExitMs) >= 10_000L`. Persisted `Laps.TIME_TOTAL_s` for Leg 1 and `Transition` SHALL use active unpaused delta `activeTimeS(end) - activeTimeS(start)` (consistent with `WorkoutSummaries.TIME_ACTIVE_s`). `hasLeg1` and `hasTransition` MUST be evaluated against `REQ-TRK-002` (`lapTime > 0 || lapDistance > 0.0`) *before* advancing `LAP_NR` (`+2` if both persist, `+1` if only one persists, `+0` if neither) so 100% auto-paused stationary transitions (`0 s` active, `0.0 m`) are skipped cleanly without `1 -> 3` `LAP_NR` gaps or orphaned sample tags.
   - Persist closed legs via `WorkoutRepository.saveLap(..., timeStart, duration, distance, avgSpeed, name, null)` with `Laps.TIME_START` formatted in UTC `"yyyy-MM-dd HH:mm:ss"` (`Locale.US`, `ZoneOffset.UTC`, matching `FitFileWriter.parseUtcTimestamp()`) and localized `Laps.NAME` (`lap_name_bike`, `lap_name_transition`, `lap_name_run`; English fallback `"Bike"`, `"Transition"`, `"Run"`), then advance `LAP_NR` cleanly and re-seed `TIME_LAP` / `DISTANCE_m_LAP` via `resetAccumulatorSensorsTo` to `(now - effectiveEnter)` deltas while storing active-leg `timeStart` + `name` for `TrackerService.createNewLap()` at workout stop.
   - Update `WorkoutSamples.db` `LAP_NR` asynchronously on `mDbExecutor` (chained after `mTableInitializationFuture`) with **`0` edits** to `WorkoutSamplesDatabaseManager.java` (`1,533 / 1,533` LoC): check `existsColumnInTable` / `PRAGMA table_info` for `LAP_NR` (adding `INTEGER DEFAULT 1` if absent) before parameterized `UPDATE` bounded by `TIME_TOTAL` (or UTC `time`) for persisted `Transition` and new-leg windows.
4. **9-Locale Localization Parity (`REQ-FIL-031.4`)**: Add `lap_name_bike`, `lap_name_transition`, and `lap_name_run` across all 9 `values*/strings_cockpit.xml` locales (zero `&#10;`, exact key parity verified by `TranslationParityTest`).
5. **Monolith LoC Ceilings & `SWE.2`/`SWE.3`/`SYS.2`/`SYS.3` Parity (`REQ-FIL-031.5`)**: Maintain `TrackerService.java <= 3,540` (`+0`), `DeviceManager.java <= 1,707` (`0` edits), `WorkoutSamplesDatabaseManager.java <= 1,533` (`0` edits), `WorkoutRepository.kt <= 4,931` (`+0`), `SpeedAndLocationDevice_ATTFused.kt <= 1,455` (`< 1,500`), `BANALService.java <= 1,075`, zero DB schema changes (`Laps.db` v3 unchanged), `0` new `src/main` test reads (`REQ-PRO-049.1`), and Stage 4–5 sync of `docs/architecture.md`, `docs/architecture/detailed_design/banal_service.md`, `docs/system/sysreq_live_tracking.md`, `docs/system/system_architecture.md`, and `docs/system/system_software_traceability.md`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-FIL-031` extending `REQ-FIL-028`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-TRK-002`, and `REQ-FIL-025.4`.
* **Historical Origin & Commit Trace**: `ATT-896` (`REQ-TRK-002`), `fee3e805` (`ATT-8890`), `a3a5942a` (`ATT-8895`), `410240a8` (`ATT-8891`).
* **Root Reason for Existing Formulation**: `ATT-896` prevented empty `0s/0m` splits; `ATT-8891` locked Tier 1/2 sovereignty; `mActivityTypeMax` guarded transient sensor drops; `MyAccumulatorSensor.reset()` kept `mValue` monotonic.
* **Preservation of Core Invariants**: Tier 1 user lock stays 100% sovereign; `REQ-TRK-002` & contiguous `1..N` `LAP_NR` preserved; existing `MyAccumulatorSensor.reset()`/`setInitialValue()` unchanged; `PhoneCarriageClassifier` never gets circular kinematic hints; `WorkoutSummaries.SPEED_AVERAGE_mps` unchanged.

---

## 3. Acceptance Criteria (Given-When-Then), 9-Locale Parity & Invariants
* **AC1 (Bike-to-Run Brick / `T2` Retroactive Anchoring)**: *Given* active tracking cycling (`BIKE_*`) until `t = 1800 s`, `T2` (`WALKING_TRANSITION`/`STATIONARY`) `1800..1850 s`, and `RUN_*` from `1850 s`, *When* `RUN_*` confirms at `t = 1865 s`, *Then* `Bike` closes at `1800 s`, `Transition` persists `1800..1850 s` (UTC `TIME_START`), `Run` anchors at `1850 s` with `TIME_LAP = 15 s` & `DISTANCE_m_LAP = d(1865)-d(1850)` via `resetTo`, `WorkoutSamples.db` `LAP_NR` updates cleanly, and `BSportType` switches to `RUN` (`ActivityType.RUN_SPEED_AND_CADENCE`).
* **AC2 (Same-Sport Bakery Stop & Short Blip Immunity)**: *Given* cycling with a `160 s` bakery stop (`WALKING_TRANSITION`/`STATIONARY`) followed by `BIKE_*`, or `< 15 s` opposite-sport noise, *When* riding resumes, *Then* `0` transitions/laps emit and `BSportType` stays `BIKE`.
* **AC3 (Auto-Pause in `T2`, Direct `< 10 s` Switch & Manual-Lap Interleaving)**: *Given* (a) partial/full auto-pause inside `T2`, (b) direct `BIKE -> RUN` (`< 10 s` gap), or (c) manual lap pressed between `t_exit` and `t_confirm`, *When* transition confirms, *Then* `Transition` uses active unpaused duration (skipped cleanly if `0s & 0m` or `< 10 s` wall-clock), boundaries clamp to `currentLapStartAnchor`, and `LAP_NR` remains strictly contiguous.
* **AC4 (Tier 1 Lock, Parked Bike Sensor + 50-Hz Accel Fallback & Reset)**: *Given* (a) `mHaveUserSelectedSportType == true`, (b) parked silent `0 W` bike sensor in `T2` with `TYPE_STEP_DETECTOR` absent (50-Hz `ACC` footstrikes active) or `CONFLICT`, or (c) `RESET_ACCUMULATORS_INTENT`/`onDestroy()`, *Then* (a) emits `0` transitions, (b) confirms `RUN` & resets `mActivityTypeMax`, and (c) clears all detector/multisport state cleanly.

| Key (`values*/strings_cockpit.xml`) | EN | DE | ES | FR | IT | JA | NL | PL | PT |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `lap_name_bike` | Bike | Rad | Ciclismo | Vélo | Bici | バイク | Fietsen | Rower | Ciclismo |
| `lap_name_transition` | Transition | Wechsel | Transición | Transition | Transizione | トランジション | Wissel | Strefa zmian | Transição |
| `lap_name_run` | Run | Laufen | Carrera | Course | Corsa | ラン | Hardlopen | Bieg | Corrida |

---

## 4. Test Specification (`TST-FIL-031`) & Traceability Matrix
* **`TST-FIL-031.1` (`MultisportTransitionDetectorTest`)**: `1800/1850/1865 s` `BIKE -> T2 -> RUN` brick, `RUN -> T1 -> BIKE -> T2 -> RUN` duathlon, `< 10 s` direct switch, `160 s` bakery stop immunity, `< 15 s` blip rejection, pause/auto-pause confirmation gating, parked silent bike sensor + 50-Hz `ACC` footstrike fusion without `TYPE_STEP_DETECTOR`, reset.
* **`TST-FIL-031.2` (`MyAccumulatorSensorResetToTest`, `BANALServiceMultisportTransitionTest`)**: `resetTo(N)` exact `mInitialValue = target - mValue`, preserved `mLastAbsoluteValue` across subsequent `accumulate()`, outside-lock listener dispatch, zero `MySensorManager` initial-value map mutation; `BANALService` Tier 1 lock immunity, `CONFLICT`/silent-bike override, `mActivityTypeMax` reset when `!changeSportWhenDeviceGetsLost()`, `MULTISPORT_TRANSITION_INTENT` (no `SPORT_TYPE_CHANGED_BY_USER_INTENT`/`LAP_SUMMARY`), `RESET_ACCUMULATORS_INTENT`/`onDestroy` cleanup.
* **`TST-FIL-031.3` (`MultisportLapCoordinatorTest`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest`, `TrackerServiceFinalizationTest`)**: Named `Laps.db` rows (`Bike`/`Transition`/`Run`) with UTC `"yyyy-MM-dd HH:mm:ss"` `TIME_START` parseable by `FitFileWriter`, partial & 100% auto-paused `T2` contiguous `LAP_NR` pre-increment check, manual-lap interleaving clamping, live `TIME_LAP`/`DISTANCE_m_LAP` `resetTo` delta verification, async `WorkoutSamples.db` `LAP_NR` column guard + time-range update, final-leg anchored naming on stop.
* **`TST-FIL-031.4` (`TranslationParityTest`)**: 9-locale parity for `lap_name_bike`, `lap_name_transition`, `lap_name_run` in `values*/strings_cockpit.xml`.
* **`TST-FIL-031.5` (Contracts & Governance)**: `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SystemArchitectureDesignContractTest`, `SourceTextReadingAuditContractTest`, `verify_requirement_governance.py` (`exit 0`).

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-FIL-031.1` (`SYSREQ-TRK-010`) | Multisport state machine, pause gating, bakery stop immunity, parked-sensor/50-Hz `ACC` fusion | `TST-FIL-031.1` | Unit (`MultisportTransitionDetectorTest`) |
| `REQ-FIL-031.2` (`SYSREQ-TRK-003`/`006`) | `MyAccumulatorSensor.resetTo`, `BANALService` precedence, `mActivityTypeMax` reset, lifecycle | `TST-FIL-031.2` | Unit (`MyAccumulatorSensorResetToTest`, `BANALServiceMultisportTransitionTest`) |
| `REQ-FIL-031.3` (`SYSREQ-TRK-003`) | `MultisportLapCoordinator`: retroactive named laps, UTC `TIME_START`, auto-pause `T2`, clamping, `WorkoutSamples` `LAP_NR` | `TST-FIL-031.3` | Unit (`MultisportLapCoordinatorTest`, `TrackerServiceFinalizationTest`) |
| `REQ-FIL-031.4` (`SYSREQ-TRK-006`) | 9-locale localization parity for `lap_name_bike`/`transition`/`run` | `TST-FIL-031.4` | Unit (`TranslationParityTest`) |
| `REQ-FIL-031.5` (`REQ-PRO-131.4`, `Rule 8.8`) | Zero-growth monolith ceilings (`0` edits `DeviceManager`/`WorkoutSamplesDatabaseManager`) & `SWE.2`/`SYS.2`/`SYS.3` parity | `TST-FIL-031.5` | Contract & Governance Check |
