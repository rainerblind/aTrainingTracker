# Stage 2 Requirement & Test Specification — ATT-9349

- **Ticket**: `ATT-9349` (`2 SP`, Feature, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Risk Rating**: `MEDIUM`
- **Reserved IDs**: `REQ-EXP-046` (`[Planned]`), `TST-EXP-046` (`[Planned]`)
- **System Traceability (`SYS.2`/`SYS.3`)**: Refines `SYSREQ-TRK-010` and `SYSREQ-DEX-001` (no `SYS.2`/`SYS.3` hardware/protocol changes; `docs/architecture.md` SWE.2 note & `docs/system/` links in Stage 4/5).
- **Living-Doc Shards**: `docs/requirements/req_data_migration_export_part2.md` (`v1.0.14`), `docs/tests/tst_data_migration_export.md` (`v1.0.15`).
- **Localization Parity**: `N/A` — pure-JVM offline replay developer tool with zero UI or string resource additions.

## 1. Requirements Summary (`REQ-EXP-046` — Priority: `Medium`, Type: `Functional`, Verification: `Unit / Integration`)

| Sub-ID | Normative Contract (SHALL / MUST) |
|---|---|
| `REQ-EXP-046.1` | Convert `PolarRawMetadata.kt` and `PolarRawTelemetryReader.kt` to 100% pure JVM (zero `android.*` and zero `org.json.*` imports) while preserving exact JSON keys (`schemaVersion`, `appVersion`, `sensorModel`, `firmwareVersion`, `streamMode`, `accRateHz`, `gyroRateHz`, `sportId`, optional `poolLength_m`), JSON escaping, malformed-JSON fallback, small-chunk `GZIPInputStream` EOF resilience, and strict non-newline trailing-row drop (mirroring `ATT-9348`). |
| `REQ-EXP-046.2` | Implement `OfflineTelemetryReplayHarness` strictly under `app/src/test/java/com/atrainingtracker/banalservice/replay/` (`0` bytes added to production APK) ingesting `.phone_raw.csv.gz` (`ATT-9348`) and/or `.polar_raw.csv.gz` (H10 & Verity Sense via `sensorModel`), aligning timestamps deterministically onto relative workout `tRelMs` with stable `(tRelMs, sourceOrder, seqIndex)` tie-breaking, and feeding exact recorded samples through all 9 production pure-Kotlin estimators (`PhoneCarriageClassifier`, `QuaternionGyroProjector`, `SmartphoneImuCadenceEstimator`, `KinematicSportTypeDiscriminator`, `UnicycleOdometryEkf`, `KinematicAltitudeSlopeEkf`, `ExtendedRtsOdometrySmoother`, `ExtendedRtsAltitudeSlopeSmoother`, `PolarLiveRunningCadenceEstimator`, `PolarH10RunningDynamicsEstimator`). |
| `REQ-EXP-046.3` | Emit deterministic `OfflineReplayScorecard` (carriage mode `%` & transition count, Phone/H10/Verity cadence stats + pairwise MAE & signed bias, H10 VO/GCT means, live EKF + ERTS total distance, ascent/descent, min/max slope, cold-start time-to-motion latency), `OfflineReplayDiff` (before-vs-after deltas), optional dot-decimal 1-Hz `--csv` output, and pure-JVM JSON `--truth` ground-truth regression assertions (`GroundTruthCheckResult`). |
| `REQ-EXP-046.4` | Register lazy Gradle `JavaExec` task `:app:replayRawTelemetry` (`tasks.register("replayRawTelemetry", JavaExec)`) in `app/build.gradle` executing `OfflineReplayCliKt` against debug + unit-test compiled classes (`-PphoneRaw`, `-PpolarRaw`, `-Ppolar2Raw`, `-PcsvOut`, `-Ptruth`, `-PjsonOut`) without busting Gradle's `Rule 8.6` build cache or requiring Android SDK runtime mocks. |

## 2. Traceability Matrix & Verification Plan (`TST-EXP-046`)

| Test ID | Target Test Class(es) / Command | Requirement(s) | Key Verification Procedure |
|---|---|---|---|
| `TST-EXP-046.1` | `PolarRawTelemetryReaderTest.kt`, `OfflineTelemetryReplayHarnessTest.kt` | `REQ-EXP-046.1` | Plain-JVM round-trip of `PolarRawMetadata` (escaped strings, `poolLength_m`, malformed fallback) & `PolarRawTelemetryReader.readFile()` small-chunk truncated-EOF recovery + non-newline trailing-row drop without Robolectric/Android mocks. |
| `TST-EXP-046.2` | `OfflineTelemetryReplayHarnessTest.kt` | `REQ-EXP-046.2` | Synthetic `.phone_raw.csv.gz` (all 8 channels) + `.polar_raw.csv.gz` (H10 + Verity Sense) replayed phone-only, Polar-only, and paired twice -> bit-exact identical scorecards across all production estimators. |
| `TST-EXP-046.3` | `OfflineTelemetryReplayHarnessTest.kt` | `REQ-EXP-046.3` | `OfflineReplayDiff.compare()` exact deltas; dot-decimal 1-Hz CSV export; pure-JVM JSON `OfflineReplayGroundTruth` pass on compliant run and explicit violation list on failed bounds. |
| `TST-EXP-046.4` | `OfflineReplayCliTest.kt`, `./gradlew :app:replayRawTelemetry`, `ArchitectureConformanceContractTest` | `REQ-EXP-046.4`, `REQ-EXP-046.1` | CLI arg parsing, scorecard/JSON/CSV emission, non-zero exit on ground-truth failure; live Gradle `:app:replayRawTelemetry` execution on synthetic sidecar exits `0`; monolith ceilings & `PolarRawTelemetryRecorderTest` regression PASS. |

## 3. Acceptance Criteria (Given-When-Then)

- **Given** a recorded `.phone_raw.csv.gz` and/or `.polar_raw.csv.gz` workout archive, **When** replayed via `OfflineTelemetryReplayHarness` or `./gradlew :app:replayRawTelemetry`, **Then** the harness replays deterministically on pure JVM in seconds and outputs identical scorecards (carriage mode %, transition count, cadence stats + pairwise MAE/bias, H10 VO/GCT, EKF + ERTS distance/ascent/descent/min-max slope, cold-start latency).
- **Given** baseline and candidate scorecards from parameter adjustments, **When** compared via `OfflineReplayDiff`, **Then** exact before-vs-after metric deltas are computed offline alongside optional 1-Hz CSV export.
- **Given** a JSON ground-truth sidecar, **When** checked via `OfflineReplayGroundTruth`, **Then** compliant replays pass with `0` violations and out-of-tolerance replays fail with explicit violation details.
- **Given** a `.polar_raw.csv.gz` archive with escaped metadata or a truncated non-newline trailing line, **When** read by `PolarRawTelemetryReader.readFile()` on standalone plain JVM, **Then** parsing succeeds with zero `android.util.Log` or `org.json` runtime exceptions.

## 4. System Invariants & Chesterton's Fence

- Extends `REQ-SET-117` & `REQ-EXP-044` without relaxing existing Polar sidecar format/API contracts; `0` bytes added to production APK; `0` edits to `SpeedAndLocationDevice_ATTFused.kt`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, `TrainingApplication.java`, or UI strings; `ANT-Android-SDKs` stays `160000`.
