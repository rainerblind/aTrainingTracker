# Stage 1 Analysis — ATT-9349: Pure-JVM Offline Simulation Replay Harness

- **Ticket**: `ATT-9349` (`2 SP`, Feature, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Base**: `sprint/2026-11-21_01_Früh` (`b0bf0bc1`)
- **Risk Rating**: `MEDIUM` (pure-JVM cleanup of `PolarRawMetadata`/`PolarRawTelemetryReader` + new test-scoped replay engine + Gradle `JavaExec` CLI task; `0` UI/DB/Service/monolith edits)
- **Complexity & Split Audit**: `2 SP` verified cohesive (pure-JVM Polar reader fix + chronological multi-sidecar replay harness + scorecard/CSV/ground-truth diff + Gradle CLI task); no split required.
- **Living-Doc Shard (`REQ-PRO-093.3`)**: `docs/requirements/req_data_migration_export_part2.md` (`234/4,000` LoC) & `docs/tests/tst_data_migration_export.md` (`2,387/4,000` LoC) -> reserve **`REQ-EXP-046` / `TST-EXP-046`** in Stage 2 (directly extends `REQ-EXP-044`/`REQ-EXP-045`; avoids full `tst_tracking_cockpit.md` at `3,992/4,000`).

## 1. Forensic Codebase Verification & Gap Analysis (Independent Peer Review)

| Component / Symbol | Verified Baseline (`b0bf0bc1`) | Forensic Finding / Gap |
|---|---|---|
| `PhoneRawTelemetryReader.kt` & 9 production estimators | `PhoneRawTelemetryReader`, `PhoneCarriageClassifier`, `SmartphoneImuCadenceEstimator`, `QuaternionGyroProjector`, `UnicycleOdometryEkf`, `KinematicAltitudeSlopeEkf`, `ExtendedRtsOdometrySmoother`, `ExtendedRtsAltitudeSlopeSmoother`, `PolarLiveRunningCadenceEstimator`, `PolarH10RunningDynamicsEstimator` all have **zero `android.*` / `org.json.*` imports**. | Ready for 100% pure-JVM execution directly against production Kotlin classes without mocks or Robolectric. |
| `PolarRawTelemetryReader.kt` (`108` LoC) & `PolarRawMetadata.kt` (`71` LoC) | Imports `android.util.Log`, `org.json.JSONObject`, and `org.json.JSONException`; wraps `GZIPInputStream` in `BufferedReader`. | **Critical Pure-JVM Trap**: Standalone `JavaExec` CLI throws `RuntimeException("Method ... not mocked")` when invoking `PolarRawTelemetryReader.readFile()` outside Robolectric! Converting `PolarRawMetadata` & `PolarRawTelemetryReader` to pure JVM (zero `android.util.Log` / `org.json`, mirroring `PhoneRawMetadata`/`PhoneRawTelemetryReader`) keeps 100% API compatibility while enabling native pure-JVM Polar sidecar replay. |
| Replay Harness & Gradle CLI (`app/src/test/...`, `app/build.gradle`) | No unified offline replay engine, chronological stream merger, scorecard/diff formatter, ground-truth verifier, or Gradle `JavaExec` CLI task exists. | Placing `OfflineTelemetryReplayHarness`, `OfflineReplayModels`, `OfflineReplayGroundTruth`, and `OfflineReplayCli` in `app/src/test/java/com/atrainingtracker/banalservice/replay/` wired to a Gradle `JavaExec` task (`:app:replayRawTelemetry`) adds **0 bytes to the production APK** and **0 edits** to monoliths (`SpeedAndLocationDevice_ATTFused.kt`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, `TrainingApplication.java`). |

## 2. Proposed Pure-JVM Replay Architecture

- **Chronological Multi-Sidecar Ingestion**: Reads `.phone_raw.csv.gz` (`PhoneRawTelemetryReader`, `CLOCK_BOOTTIME` `elapsedRealtimeNs`) and/or `.polar_raw.csv.gz` (`PolarRawTelemetryReader`, `elapsedMs` + `sensorModel` routing `H10` -> `Mode.H10_ACC` + `PolarH10RunningDynamicsEstimator`, `Verity` -> `Mode.VERITY_GYRO_ACC`), normalizing to relative workout time `tRelMs` and merging stably by `(tRelMs, sourceOrder, seqIndex)` for bit-exact determinism.
- **Production Pipeline Execution**: Feeds exact recorded samples into `PhoneCarriageClassifier`, `QuaternionGyroProjector`, `SmartphoneImuCadenceEstimator`, `UnicycleOdometryEkf(recordSnapshots = true)`, `KinematicAltitudeSlopeEkf`, post-workout `ExtendedRtsOdometrySmoother` & `ExtendedRtsAltitudeSlopeSmoother`, `PolarLiveRunningCadenceEstimator`, and `PolarH10RunningDynamicsEstimator`.
- **Quantitative Scorecard, Before/After Diff, CSV & Ground-Truth**:
  - `OfflineReplayScorecard`: carriage-mode `%` distribution & transition count; cadence stats (`mean`, `count`, pairwise `MAE` & signed bias across Phone vs Polar H10 vs Verity Sense) + H10 VO/GCT means; live EKF & ERTS total distance (m), ascent/descent (m), min/max slope (%), cold-start time-to-motion latency (s).
  - `OfflineReplayDiff`: deterministic before-vs-after delta comparison across two `OfflineReplayScorecard`s for parameter tuning.
  - Optional 1-Hz `--csv <path>` export and optional pure-JVM JSON `--truth <path>` ground-truth assertion (`expectedDominantMode`, `minDominantModePct`, `maxTransitions`, `expectedDistanceM` ± tol, `expectedAscentM` ± tol, `maxCadenceMaeSpm`) returning structured pass/fail violations.
- **Gradle CLI Entry Point (`:app:replayRawTelemetry`)**: `JavaExec` task running `OfflineReplayCliKt` over debug + unit-test compiled classes (`-PphoneRaw=...`, `-PpolarRaw=...`, optional `-Ppolar2Raw=...`, `-PcsvOut=...`, `-Ptruth=...`, `-PjsonOut=...`) executing in seconds with zero Android device or emulator.

## 3. Chesterton's Fence Archaeology

- **Original Requirement ID & Target**: `REQ-SET-117` (`ATT-8019`, Polar raw sidecar format & reader), `REQ-EXP-045` (`ATT-9348`, smartphone raw sidecar & pure-JVM `PhoneRawTelemetryReader`), `REQ-PRO-129.2` (monolith LoC ceilings), `REQ-PRO-049` (`SourceTextReadingAuditContractTest`).
- **Architectural Rationale**: `PolarRawMetadata`/`PolarRawTelemetryReader` were originally written under Android runtime where `org.json` and `android.util.Log` were available; `ATT-9348` purposefully kept `PhoneRawMetadata`/`PhoneRawTelemetryReader` pure JVM to unlock `ATT-9349`.
- **Invariant Preservation**: All existing `PolarRawMetadata` and `PolarRawTelemetryReader` public signatures, JSON keys (`schemaVersion`, `appVersion`, `sensorModel`, `firmwareVersion`, `streamMode`, `accRateHz`, `gyroRateHz`, `sportId`, `poolLength_m`), and `PolarRawTelemetryReaderTest` assertions stay 100% preserved; `0` production estimators are altered in behavior; `0` bytes added to release APK.
- **Refactor / Removal Policy**: Replace `org.json.JSONObject` and `android.util.Log` inside `PolarRawMetadata.kt` and `PolarRawTelemetryReader.kt` with pure-Kotlin equivalents without removing any public method or field.

## 4. System Invariants

1. **Zero Android Dependencies in Replay Path**: Replay engine, CLI, `PhoneRawTelemetryReader`, and `PolarRawTelemetryReader` execute on plain JVM (`JavaExec` / pure JUnit) with zero Robolectric, Android SDK stubs, or emulator.
2. **Bit-Exact Determinism**: Replaying the same archive(s) twice produces identical scorecards, transitions, cadence MAE/bias, distances, elevation metrics, and CSV output.
3. **Zero Monolith & Release APK Impact**: Replay harness lives in `app/src/test/`; `0` changes to `SpeedAndLocationDevice_ATTFused.kt`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, `TrainingApplication.java`, or production UI/strings.

## Change-Request Impact Analysis

- **Affected Requirements**: Adds net-new `REQ-EXP-046` (`TST-EXP-046`) in `req_data_migration_export_part2.md` & `tst_data_migration_export.md` refining `SYSREQ-TRK-010` and `SYSREQ-DEX-001` (no `SYS.2`/`SYS.3` hardware/protocol changes; downstream consumer of `REQ-EXP-045` and `REQ-SET-117`).
- **Affected Architecture Elements**: Pure-JVM cleanup in `PolarRawMetadata.kt` & `PolarRawTelemetryReader.kt`; new test-scoped replay package `app/src/test/java/com/atrainingtracker/banalservice/replay/` (`OfflineReplayModels.kt`, `OfflineTelemetryReplayHarness.kt`, `OfflineReplayGroundTruth.kt`, `OfflineReplayCli.kt`); new Gradle `JavaExec` task `replayRawTelemetry` in `app/build.gradle`; `docs/architecture.md` SWE.2 §3.3.4 note + `docs/system/` bidirectional links in Stage 4/5.
- **Verification Impact & Test Surface**: Pure-JVM unit tests verifying deterministic phone-only, Polar-only (H10 + Verity Sense), and paired multi-sidecar replay across all production estimators, scorecard & before-vs-after diff computation, 1-Hz CSV export, ground-truth pass/fail assertions, truncated-archive resilience, `PolarRawTelemetryReaderTest` regression, and live CLI invocation of `./gradlew :app:replayRawTelemetry` on synthetic sidecars.
- **Risk Assessment & Invariants**: `MEDIUM` risk (modifying `PolarRawMetadata.kt`/`PolarRawTelemetryReader.kt` and `app/build.gradle`). Mitigated by preserving exact public signatures/JSON keys, verifying `PolarRawTelemetryReaderTest` + `PolarRawTelemetryRecorderTest` + `ArchitectureConformanceContractTest`, zero release-APK bytecode impact, and end-to-end pure-JVM `JavaExec` CLI verification.
