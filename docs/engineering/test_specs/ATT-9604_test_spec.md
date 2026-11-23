# Stage 2 Requirement & Test Specification — ATT-9604

- **Ticket**: `ATT-9604` (`1 SP`, `Bug`, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Risk Rating**: `HIGH`
- **Reserved IDs**: `REQ-EXP-047` (`[Planned]`), `TST-EXP-047` (`[Planned]`)
- **System Traceability (`SYS.2`/`SYS.3`)**: `Derived (SW-only)` — internal sidecar deduplication & provider channel tagging extending `REQ-EXP-045` / `REQ-EXP-046` without external hardware, HAL, SQLite, or cloud API changes.
- **Living-Doc Shards**: `docs/requirements/req_data_migration_export_part2.md` (`v1.0.38`), `docs/tests/tst_data_migration_export.md` (`v1.0.39`).
- **Localization Parity**: `N/A` — pure telemetry/replay bugfix with zero UI or `strings*.xml` edits.

## 1. Requirements Summary (`REQ-EXP-047` — Priority: `Medium`, Type: `Functional`, Verification: `Unit`)

| Sub-ID | Normative Contract (SHALL / MUST) |
|---|---|
| `REQ-EXP-047.1` | Extend `PhoneRawChannel` with `GNSS_FUSED` alongside `GNSS` (preserving exact 9-column `CSV_HEADER`, `v0..v5` layout, and `schemaVersion = 1`). `SpeedAndLocationDevice.onNewLocation()` SHALL pass `GNSS_FUSED` for `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` and `GNSS` otherwise; `SpeedAndLocationDevice_ATTFused.onLocationChanged()` SHALL pass `GNSS` (`+0` net LoC); `SmartphoneRawTelemetryRecorder.recordLocation(location, epochMs, channel = resolveLocationChannel(location))` SHALL stay `@JvmOverloads`-compatible, mapping `"fused"` (case-insensitive) to `GNSS_FUSED` and all other/null providers to `GNSS`. |
| `REQ-EXP-047.2` | `SmartphoneRawTelemetryRecorder` SHALL deduplicate GNSS fixes per channel (`GNSS` and `GNSS_FUSED` independently) using an instance-scoped, thread-safe, zero-allocation 4-slot primitive ring buffer matching `location.elapsedRealtimeNanos` when `> 0L` (or `(elapsedNs, epochMs)` when `<= 0L`), suppressing duplicate rows before `Channel.trySend` without incrementing `droppedFrameCount`, so concurrent `GPS` + `ATTFused` callbacks (including interleaved `GoogleFused` updates) emit exactly **1** `GNSS` row per fix while keeping single-device pairings intact. |
| `REQ-EXP-047.3` | `PhoneRawTelemetryRow.fromCsvLine()`, `PhoneRawSidecarRecovery`, and `PhoneRawTelemetryReader` SHALL parse/salvage both `GNSS` and `GNSS_FUSED` rows alongside unmodified `v1` sidecars. `ReplayConfig` SHALL add `gnssSource: ReplayGnssSource = ReplayGnssSource.AUTO` (`AUTO`: prefer `GNSS`, fallback to `GNSS_FUSED` only when zero `GNSS` rows exist; `GPS`: `GNSS` only; `FUSED`: `GNSS_FUSED` only); `OfflineTelemetryReplayHarness` SHALL filter to the active channel and skip exact consecutive duplicate GNSS rows in legacy v1 files; `OfflineReplayCli` / `:app:replayRawTelemetry` SHALL support optional `--gnss-provider <auto\|gps\|fused>` / `-PgnssProvider=...`. |

## 2. Traceability Matrix & Verification Plan (`TST-EXP-047`)

| Test ID | Target Test Class(es) / Command | Requirement(s) | Key Verification Procedure |
|---|---|---|---|
| `TST-EXP-047.1` | `PhoneRawTelemetryReaderTest.kt`, `PhoneRawSidecarRecoveryTest.kt`, `SmartphoneRawTelemetryRecorderTest.kt` | `REQ-EXP-047.1` | 9-channel round-trip (`GNSS` + `GNSS_FUSED`) & non-finite rejection; `.tmp` crash salvage of `GNSS_FUSED`; `recordLocation` provider resolution (`"fused"`/`"FUSED"` -> `GNSS_FUSED`, `"gps"`/`null` -> `GNSS`); `SpeedAndLocationDevice_GoogleFused` -> `GNSS_FUSED` even with null provider, `GPS`/`ATTFused` -> `GNSS`. |
| `TST-EXP-047.2` | `SmartphoneRawTelemetryRecorderTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt` | `REQ-EXP-047.2` | Concurrent `SpeedAndLocationDevice_GPS` + `SpeedAndLocationDevice_ATTFused` identical GPS fix delivery (with interleaved `GoogleFused` updates & `time == 0L`) emits exactly 1 `GNSS` + 1 `GNSS_FUSED` row, `droppedFrameCount == 0`; fallback `elapsedRealtimeNanos == 0L` deduplicates `(elapsedNs, epochMs)` while preserving distinct `epochMs`; fresh session resets dedup buffer. |
| `TST-EXP-047.3` | `OfflineTelemetryReplayHarnessTest.kt`, `OfflineReplayCliTest.kt`, `ArchitectureConformanceContractTest.kt`, `DataStoreHotPathContractTest.kt` | `REQ-EXP-047.3` | Mixed `GNSS` + divergent `GNSS_FUSED` (`+20 m` alt) replayed under `AUTO`/`GPS`/`FUSED`; `GNSS_FUSED`-only `AUTO` fallback; legacy v1 archive with duplicate `GNSS` rows parses cleanly & deduplicates; CLI `--gnss-provider` valid/invalid exit codes; monolith LoC (`ATTFused <= 1,500`) & DataStore hot-path contracts PASS. |

## 3. Acceptance Criteria (Given-When-Then)

- **Given** `SpeedAndLocationDevice_GPS` and `SpeedAndLocationDevice_ATTFused` are both active during a workout, **When** a 1-Hz GPS `Location` arrives, **Then** exactly **1** `GNSS` row (not 2 identical rows) is written for that `elapsedRealtimeNs` with `droppedFrameCount == 0`.
- **Given** raw GPS and Google Fused providers both emit updates during a workout, **When** `SmartphoneRawTelemetryRecorder` writes the sidecar, **Then** raw GPS fixes use channel `GNSS` and Google Fused fixes use channel `GNSS_FUSED` with zero cross-channel mixing.
- **Given** both existing `v1` sidecars (`2026-11-22_081402.phone_raw.csv.gz`) and newly recorded sidecars, **When** `:app:replayRawTelemetry` / `OfflineTelemetryReplayHarness` parses and replays them, **Then** both parse cleanly without errors, default `AUTO` replay isolates raw `GNSS` (suppressing legacy v1 duplicates), and `--gnss-provider gps|fused` selects the requested provider deterministically.

## 4. Chesterton's Fence & System Invariants

- **Original Requirement ID & Target**: `REQ-EXP-045` (`ATT-9348`) & `REQ-EXP-046` (`ATT-9349`), `docs/requirements/req_data_migration_export_part2.md`.
- **Historical Origin & Commit Trace**: `b0bf0bc1` (`ATT-9348`), `d889e9ca` (`ATT-9349`).
- **Root Reason for Existing Formulation**: Dual capture hooks (`SpeedAndLocationDevice.java:579` and `SpeedAndLocationDevice_ATTFused.kt:504`) ensured pre-EKF raw GPS recording in `ATTFused` alongside `GPS`-only / `GoogleFused`-only setups.
- **Preservation of Core Invariants**: Both hooks stay active so all device-pairing subsets work; exact 9-column `CSV_HEADER` & `schemaVersion = 1` unchanged; `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC (`+0` net LoC); `0` allocations when disabled; `0` SQLite/UI/strings changes; `ANT-Android-SDKs` intact (`160000`).
