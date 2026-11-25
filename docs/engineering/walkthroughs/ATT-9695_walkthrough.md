# Stage 5 Walkthrough — ATT-9695: Propagate Resolved Walking `SportType` to `SpeedAndLocationDevice_ATTFused`

- **Ticket**: `ATT-9695` (`2 SP`, Bug, `tier-a`, `severity-major`, Epic `ATT-166`) | **Gate 3**: `ATT-9697` (`PASS`)
- **Branch**: `feature/ATT-9695` (branched off `sprint/2026-11-24_03_Nacht` @ `e677878d`)
- **Traceability**: `REQ-FIL-046` (`.1`–`.3`) `[Verified]` <-> `TST-FIL-046` (`.1`–`.3`) `[Verified]` (`N/A - 0 string changes`)

## 1. Summary of Changes & Gate 3 Watchpoints Verified

| File | Final LoC / Cap | Summary of Changes |
|---|---:|---|
| `app/src/main/java/com/atrainingtracker/banalservice/BANALService.java` | `1,121 / 1,500` | Added volatile `mResolvedBSportType` (`BIKE`/`RUN` else `UNKNOWN`, cleared in `clearInferredSportTelemetry()`), `setResolvedBSportType()` on `BANALService` / `BANALServiceComm` / static `updateActiveResolvedBSportType()`, and `getEffectiveBSportType()` / `getEffectiveActiveBSportType()` (`Tier 1 User (incl. OTHER) > Tier 1.5 Multisport > Tier 2 Hardware > Tier 3 Kinematic > Tier 4 Resolved > UNKNOWN`, **Watchpoint 2** null-instance `cDeviceManager` fallback). Left `getBSportType()` and `getAuthoritativeBSportType()` untouched. |
| `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java` | `3,193 / 3,220` | Added `propagateResolvedBSportType(@Nullable BSportType)` and invoked it in `createNewWorkout()`, `onSearchingFinished()`, and `applyResumedState()` with `0` extra SQLite queries and `0` `SPORT_TYPE_CHANGED_BY_USER_INTENT` broadcasts. |
| `app/src/main/java/com/atrainingtracker/banalservice/odometry/SmartphoneImuCadenceEstimator.kt` | `1,120 / 1,500` | Added `UNKNOWN_WALK_CONFIRM_DWELL_MS = 25_000L` (calibrated on real Pixel 10 `2026-11-22_081402` `18–20 s` pre-ride driveway bike-pushing), monotonic `unknownWalkStartTsMs` dwell state, lock-free `@Volatile isRunningOrWalkingActive`, strict `CYCLING`/`OTHER` `0`-stride suppression, and `STATIONARY` sport-contradiction suppression. |
| `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicSportTypeDiscriminator.kt` | `537 / 1,500` | Added `MIN_BIKE_CARRIAGE_VOTE_SPEED_MPS = 1.8` speed floor on `BIKE_*` carriage votes (`+1.2 * dt`), eliminating walking-speed (`1.1–1.56 m/s`) `BIKE_FRONT_POCKET` false latches discovered during empirical `2026-11-23_171814` Pixel 10 replay while preserving `>= 2.0 m/s` MTB climbs. |
| `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt` | `1,489 / 1,500` | **Watchpoint 1**: Added `@Volatile mLastObservedSportHint`, routed `resolveActiveBSportType()` to `effectiveBSportType`, mapped user-locked `UNKNOWN` to `SportContextHint.OTHER`, published `mCachedCarriageMode = mode` strictly before comparing `hint != mLastObservedSportHint`, synced `mLastObservedSportHint` inside `handleSportTypeChanged()`, added `!isStridesAllowed(hint)` `unregisterStridesSensor()` in `else if (modeChanged)`, and cleared `BANALService.updateActiveResolvedBSportType(UNKNOWN)` in `resetOriginAndEkf()`'s non-`BANALService` branch. |
| `OfflineReplayModels.kt`, `OfflineTelemetryReplayHarness.kt` | — | **Watchpoint 3**: Added `phoneTotalStrides: Int = 0` and production-parity `Tier 3 lastInferredSport > Tier 4 STATE resolvedSport` precedence with non-retroactive stride gating. |
| `banal_service.md` (`v1.0.12`), `tracker_service.md` (`v1.0.14`), Living Docs | — | **Watchpoint 4**: Synced SWE.3 detailed design docs, promoted `REQ-FIL-046` (`v1.0.61`) & `TST-FIL-046` (`v1.0.65`) to `[Verified]` with 6 verbatim method citations, and monotonically bumped `docs/requirements.md` & `docs/tests.md` (`v1.0.188`) with Change History rows. |

## 2. Verification Evidence

### A. Targeted Unit, Robolectric, Replay & Contract Suites (`190/190` PASS, `0` Failures, `0` Errors, `0` New Warnings)
- **`BANALServiceSportInferenceTest`** (`3/3` PASS) — `tier4ResolvedBSportType_precedenceIsolationResetAndFallback_tstFil046_1`
- **`TrackerServiceStateMachineCharacterisationTest`** (`9/9` PASS) — `testOnSearchingFinished_propagatesResolvedWalkingBSportTypeToBanalService_TST_FIL_046_1`
- **`KinematicSportTypeDiscriminatorTest`** (`7/7` PASS) — `walkingSpeedBikePocketFlicker_neverLatchesBike_whileCyclingSpeedStillLatches_tstFil046_1`
- **`SmartphoneImuCadenceEstimatorTest`** (`13/13` PASS) — `walkingTransition_unknownDwellConfirmationAndCyclingOtherStationaryGuards_tstFil046_2`
- **`SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest`** (`4/4` PASS) — `tier4ResolvedWalk_activatesCadenceAndStridesAndRespectsOtherAndBikeGuards_tstFil046_3`
- **`OfflineTelemetryReplayHarnessTest`** (`12/12` PASS) — `replay_phoneOnlyWalkAndCyclingBakeryStop_emitsWalkCadenceStridesAndSuppressesBakeryWalk_tstFil046_3`
- **Regression & Contract Suites**: `SpeedAndLocationDevice_ATTFusedTest` (`42/42`), `BANALServiceMultisportTransitionTest` (`4/4`), `PhoneCarriageClassifierTest` (`21/21`), `OfflineReplayCliTest` (`4/4`), `TrackerServiceFinalizationTest` (`36/36`), `TrackerServiceResumeTest` (`18/18`), `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`), `InterfaceSignatureDriftContractTest` (`3/3`).
- *(Pre-existing `ATT-9681` `TuningPreferencesDataStoreRoundTripTest` DataStore init livelock excluded per Sprint 479 Coordinator guidance.)*

### B. Real Pixel 10 Field Sidecar Replay Verification
Executed inside `OfflineTelemetryReplayHarnessTest` against `/home/rainer/AndroidStudioProjects/aTrainingTracker_raw_telemetry/`:
- **`2026-11-23_093732.phone_raw.csv.gz`** (phone-only walk): `> 80%` active walking cadence coverage (`~45–60 strides/min`) and `>= 500` cumulative phone strides (vs `0%` / `0` strides pre-fix).
- **`2026-11-23_171814.phone_raw.csv.gz`** (phone-only walk with walking-speed `BIKE_FRONT_POCKET` flicker): `> 80%` active walking cadence coverage and `>= 500` cumulative phone strides after adding `MIN_BIKE_CARRIAGE_VOTE_SPEED_MPS = 1.8`.
- **`2026-11-22_081402.phone_raw.csv.gz`** (cycling ride with `18–20 s` driveway bike-pushing + mid-ride bakery stop): strictly **`0`** walking strides across the entire ride (`UNKNOWN_WALK_CONFIRM_DWELL_MS = 25_000L` + `CYCLING` suppression).

### C. Live `emulator-5554` Smoke & `Rule 8.5` Visual Inspection
- Executed atomically via `python3 tools/agent_emulator.py exec --ensure -- bash -c '...'`: `:app:installDebug` (`BUILD SUCCESSFUL`), cold launch (`MainActivityStandard -> MainActivityWithNavigation`, PID `25264`), `logcat -b crash` **`0` crashes**.
- Captured and inspected `docs/engineering/test_evidence/tickets/ATT-9695/screenshots/att9695_emulator_smoke.png` via `read_file`: all tabs, sensor status bar icons, `Start Tracking` CTA, sport-type selector (`run`/`bike`/`other`), and `Select Route` card render cleanly with crisp contrast and zero clipping or error dialogs.

### D. Governance & Evidence Bundle
- `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-11-24_03_Nacht`: **EXIT 0** (22/22 governance checks PASS).
- SHA-256 evidence bundle generated and committed at `docs/engineering/test_evidence/tickets/ATT-9695/`.
