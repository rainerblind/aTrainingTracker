# Stage 5 Walkthrough — ATT-9694: Walking-Speed `BIKE` Gating & Bounded User `SportTypes` Speed Priors

- **Ticket**: `ATT-9694` (`3 SP`, Bug, `tier-a`, `severity-major`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Gate 3**: `ATT-9700` (`PASS`)
- **Traceability**: `REQ-FIL-047` (`.1`–`.3`) `[Verified]` <-> `TST-FIL-047` (`.1`–`.3`) `[Verified]` (`SYSREQ-TRK-003`, `SYSREQ-TRK-006`, `SYSREQ-TRK-010`)
- **Evidence Bundle**: [`docs/engineering/test_evidence/tickets/ATT-9694/`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/test_evidence/tickets/ATT-9694/evidence_summary.json) | **UI/Schema/Strings**: `0` UI/resource/DB schema changes (`N/A - 0 string changes`)

## 1. Summary of Changes & Gate 3 Watchpoints

| File | LoC / Cap | Summary |
|---|---|---|
| `SportSpeedPriors.kt` (new, pure-JVM) | `151 / 1,500` | Immutable `SportSpeedBand` + `SportSpeedPriors` clamping `maxWalkSpeedMps` (`[1.5, 2.5]`, default `2.0`), `minBikeSpeedMps` (`[1.8, 3.5]`, default `2.5`), `maxRunSpeedMps` (`[3.5, 6.5]`, default `5.5`); ignores non-finite/`0.0`/inverted rows and excludes explicit `"run"/"Run"/"Running"` metadata from walk matching. |
| `SportTypeDatabaseManager.java` | `970 / 1,500` | Added try-with-resources `getActiveSportTypeSpeedBands()` (`IS_PLACEHOLDER = 0`, zero raw `Cursor` leaks). |
| `SportSpeedPriorsLoader.kt` (new) | `110 / 1,500` | Placed in `banalservice.devices` (Watchpoint 2); daemon background executor (`SDK_INT > 0`), synchronous plain-JVM no-op (`SDK_INT == 0`, `Coding Standards §5.5`), and deterministic `executorOverride` seam (Watchpoint 3). |
| `SpeedAndLocationDevice_ATTFused.kt` | `1,495 / 1,500` | Annotated `mIsShuttingDown` `@Volatile` (Watchpoint 1); wired `refreshSportSpeedPriorsAsync()` on `init` & `onAccumulatorsReset()` via `SportSpeedPriorsLoader.loadAsyncInto(...)` keeping LoC `<= 1,500` (Watchpoint 2). |
| `PhoneCarriageClassifier.kt` | `1,388 / 1,500` | Wired `SportSpeedPriors` (retained on `reset()`); added trailing `3.0 s` `sustainedGnssSpeedMps` with `unambiguousMotionExit` bypass (`<= 1.0 s` `STATIONARY` fast-exit preserved) and `1.2 s` interleaved high-speed hold; suppressed non-wheel `BIKE_*` below `bikeLowSpeedFloorMps` and gated `WALKING_TRANSITION` short-circuit on `confirmedNoFootstrikes \|\| effectiveSpeed <= priors.maxWalkSpeedMps` (so `2.01–3.49 m/s` auto-detect runners under fresh-install `minBike=3.5` resolve to `RUN_*` while preserving `2.0 m/s` uphill MTB, `1.8 m/s` established cycling climbs, and `runCyclingMaskSpeedMps >= 5.5`). |
| `KinematicSportTypeDiscriminator.kt` | `555 / 1,500` | Wired bounded `SportSpeedPriors` (`bikeCarriageVoteFloorMps` + `highBikeSpeedMps` in `[5.0, 6.5]`, retained on `reset()`). |
| `MultisportTransitionDetector.kt` | `549 / 1,500` | Added symmetric sliding-window `evaluateBikeWindow()` across Phase 1 & Phase 2 (`hasActiveBikeHardware` bypass, `windowAvgSpeedMps >= minBikeSpeedMps`, `windowDistM >= minBikeSpeedMps * spanSec`, Phase 2 `enterAnchor` head recovery preserving `exitAnchor` and duathlon carryover). |
| `OfflineTelemetryReplayHarness.kt` & `OfflineReplayModels.kt` | test-only | Wired `SportSpeedPriors` & `MultisportTransitionDetector` into replay scorecard (`multisportEstablishedSport`, `multisportTransitionCount`). |

## 2. Targeted Verification Results (`141/141 PASS`, `0` Failures, `0` New Warnings)

Executed via `tools/parallel_sprint_lock.py` (`./gradlew :app:testDebugUnitTest --rerun --tests ...`):
- **New & Modified Core Suites**: `SportSpeedPriorsTest` `3/3`, `SportSpeedPriorsLoaderTest` `2/2`, `PhoneCarriageClassifierTest` `23/23` (incl. Stage 6 Bounce 1/5 fresh-install `minBike=3.5` `3.0 m/s` auto-detect runner regression test), `KinematicSportTypeDiscriminatorTest` `8/8`, `MultisportTransitionDetectorTest` `12/12`, `OfflineTelemetryReplayHarnessTest` `13/13`.
- **Integration, DB & Contract Suites**: `SpeedAndLocationDevice_ATTFusedTest` `42/42`, `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` `4/4`, `BANALServiceMultisportTransitionTest` `4/4`, `SportTypeDatabaseManagerStravaTest` `4/4`, `SportTypeRealSqliteMigrationTest` `3/3`, `OfflineReplayCliTest` `4/4`, `ArchitectureConformanceContractTest` `11/11`, `TryWithResourcesCursorAuditTest` `5/5`, `InterfaceSignatureDriftContractTest` `3/3`.

## 3. Offline Replay Validation (`./gradlew :app:replayRawTelemetry`, Pixel 10 Archive)

| Archived Session | Activity | `BIKE_*` Carriage % | `inferredSport` | `multisportEstablishedSport` | Transitions | Phone Cadence / Strides |
|---|---|---:|---|---|---:|---|
| `2026-11-23_093732` | 1.4 km walk | `0.0%` (`99.9%` walk) | `RUN` | **`UNKNOWN`** (`0` `BIKE` locks) | **`0`** | `54.9 spm`, `832` strides |
| `2026-11-23_171814` | 1.4 km walk | `0.0%` (`94.5%` walk, down from `0.59%`) | **`RUN`** (fixed from `BIKE`) | **`UNKNOWN`** (`0` `BIKE` locks) | **`0`** | `52.7 spm`, `861` strides |
| `2026-11-23_182416` | `Essen gehen` (walk + 2h04m dinner + walk) | `0.0%` (`76.2%` stat, `23.8%` walk) | `BIKE` (exit GNSS spike) | **`UNKNOWN`** (`0` `BIKE` locks) | **`0`** (0 phantom splits) | `54.1 spm`, `2,077` strides |
| `2026-11-22_081402` | Cycling errand + bakery stop | `55.0%` (`49.0%` pocket, `6.0%` jersey) | **`BIKE`** | **`BIKE`** | **`0`** | `67.9 spm`, **`0` walk strides** |

- Non-UI odometry/classifier ticket (`0` Compose/Activity/Manifest/resource edits); live emulator UI smoke not applicable (`check-install-required` deferred to Stage 6 if runtime service check requested). Governance `verify_requirement_governance.py`: EXIT `0`.
- **Rule 1.3 Documented Deviation (`auto_gate.py` 87-suite dependent batch)**: `85/87` suites passed on the first batch run; `EditWorkoutClusteringTest` failed in the batch due to cross-test `DataStoreImpl` `UncaughtExceptionsBeforeTest` leakage and passed **`6/6` in isolation** (included in the `86`-suite `PASS` bundle). The single remaining suite (`MeasuredNfrBenchmarkTest.testNfr014_cockpitTelemetryThrottling_measuredMaxOneHzOverFiveSeconds` + `@AfterClass classMethod`) is a pre-existing out-of-scope baseline failure on `sprint/2026-11-25_01_Früh` (commit `959c15d0` / `ATT-8126`, already tracked as Bug **`ATT-9639`**) and is excluded per Coordinator `Rule 1.3` direction.
