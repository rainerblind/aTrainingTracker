# Stage 5 Walkthrough — ATT-9349

- **Ticket**: `ATT-9349` — *Build Pure-JVM Offline Simulation Replay Harness for Recorded Smartphone and Polar Raw Sensor Sidecars* (`2 SP`, Feature, Epic `ATT-166`)
- **Target Release**: `V5.0.0` (`ACTIVE/UNRELEASED`)
- **Branch**: `feature/ATT-9349` (verified code SHA `d889e9ca`, preserved intact for Stage 6 Cold Review; `0` `app/` modifications in Stage 5 per `Rule 7.5`)
- **Requirements & Tests**: `REQ-EXP-046` (`[Verified]`) <-> `TST-EXP-046` (`[Verified]`) -> `SYSREQ-TRK-010`, `SYSREQ-DEX-001`

## 1. Delivered Changes (`REQ-EXP-046.1`–`.4`)
- **Pure-JVM Polar Sidecar Reader (`REQ-EXP-046.1`)**: Removed all `android.*` and `org.json.*` imports from `PolarRawMetadata.kt` and `PolarRawTelemetryReader.kt`; added pure-Kotlin JSON serialization/parsing (preserving exact keys, string escaping + `\uXXXX`, malformed-JSON default fallback) and small-chunk `GZIPInputStream` reads with truncated EOF recovery and strict non-newline trailing-row drop.
- **Test-Scoped Offline Replay Engine (`REQ-EXP-046.2`–`.3`)**: Added `app/src/test/java/com/atrainingtracker/banalservice/replay/` (`OfflineReplayModels.kt`, `OfflineReplayGroundTruth.kt`, `OfflineTelemetryReplayHarness.kt`, `OfflineReplayCli.kt` — `0` production APK bytes, `0` monolith edits) aligning `.phone_raw.csv.gz` and `.polar_raw.csv.gz` (H10/Verity routed via `sensorModel`, including `"sense"` + unscaled Verity `deg/s` gyro + H10 external cadence wiring parity) onto unified `tRelMs` with `(tRelMs, sourceOrder, seqIndex)` tie-breaking across all 9 production estimators + `QuaternionGyroProjector` + ERTS odometry/altitude smoothers, emitting deterministic scorecards, `OfflineReplayDiff`, `Locale.US` 1-Hz CSV, JSON reports, and ground-truth assertions.
- **Lazy Compilation-Only Gradle CLI (`REQ-EXP-046.4`)**: Registered `:app:replayRawTelemetry` (`JavaExec`) in `app/build.gradle` with compilation-only dependencies and execution-time `doFirst` classpath resolution (verified via `--dry-run`: `:app:testDebugUnitTest` excluded from task graph).
- **Architecture & System Traceability**: Updated `docs/architecture.md` (`v1.0.21`), `docs/system/sysreq_data_exchange.md` (`v1.2.5`), `docs/system/sysreq_live_tracking.md` (`v1.2.22`), and `docs/system/system_software_traceability.md` (`v1.2.22`).

## 2. Verification Matrix (Rule 7.5 Doc-Only Fast Path — Reused Gate 4 Verified SHA `d889e9ca`)

| Verification Item | Scope / Command | Result |
| :--- | :--- | :--- |
| **Tier-1 Pure-JVM Replay & Polar Suites** | `OfflineTelemetryReplayHarnessTest` (8), `OfflineReplayCliTest` (3), `PolarRawTelemetryReaderTest` (6, plain JVM without Robolectric), `PolarRawTelemetryRecorderTest` (9), `PhoneRawTelemetryReaderTest` (2) | **28/28 PASS** (`0` warnings) |
| **Tier-1 Architecture & SYSREQ Contracts** | `ArchitectureConformanceContractTest` (11), `SystemArchitectureDesignContractTest` (9), `SystemDataExchangeRequirementsContractTest` (4), `SystemLiveTrackingRequirementsContractTest` (4) | **28/28 PASS** (`0` warnings) |
| **Cumulative Tier-1 Total (Gate 4 `ATT-9476`)** | 9 JUnit suites @ `d889e9ca` (`--rerun -Dkotlin.daemon.jvm.options=-Xmx3072m`) | **56/56 PASS** (`0` fail, `0` err, `0` skip) |
| **Live `:app:replayRawTelemetry` CLI** | `--dry-run` (no `testDebugUnitTest`) + live synthetic `.phone_raw.csv.gz` + `.polar_raw.csv.gz` + `.truth.json` | **PASS** (`exit 0`, `9s`, 21,296 events, Phone vs H10 MAE `0.10 spm`) & **FAIL** (`exit 1`, `4s`, explicit violation) |
| **Static Safety Linters & Governance** | `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `verify_requirement_governance.py --sync-indexes --check-living-doc-layout --check-verified-evidence` | **ALL PASS** (`EXIT 0`) |
| **On-Device / Emulator Check** | Pure-JVM developer CLI tool (zero Android UI/resource/APK changes) | **N/A** (`0` release APK bytes, `Localization Parity: N/A`) |

## 3. Invariants & Handover
- **Zero Production Bloat / Monolith Guard**: `0` release APK bytes added; `SpeedAndLocationDevice_ATTFused.kt`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, and `TrainingApplication.java` untouched; `ANT-Android-SDKs` intact at `160000`.
- **Stage 6 Readiness**: `feature/ATT-9349` remains intact (NOT merged onto `sprint/2026-11-21_01_Früh`) awaiting Gate 5 sign-off and independent Stage 6 Cold Final Review.
