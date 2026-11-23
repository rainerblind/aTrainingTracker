# Stage 5 Verification & Walkthrough — ATT-9604

- **Ticket**: `ATT-9604` (`1 SP`, `Bug`, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0`
- **Branch**: `feature/ATT-9604` (preserved intact for Stage 6 Cold Review; `sprint/2026-11-22_03_Nacht` untouched)
- **Gate 3 Sign-Off**: `ATT-9649` (`Erledigt` / `GATE_PASSED`, all 5 Gate 3 guardrails implemented)
- **Traceability**: `REQ-EXP-047` (`REQ-EXP-047.1`–`.3`, `[Verified]`) <-> `TST-EXP-047` (`TST-EXP-047.1`–`.3`, `[Verified]`) | `Derived (SW-only)`

## 1. Implemented Changes

| Component | Change Summary | Guardrail / Invariant |
|---|---|---|
| `PhoneRawTelemetryRow.kt` | Added `PhoneRawChannel.GNSS_FUSED` with identical 6-finite-field validation (`v0..v5`) and `parseChannel("GNSS_FUSED")`. | Exact 9-column `CSV_HEADER` & `schemaVersion = 1` unchanged (`PhoneRawSidecarRecovery` & `PhoneRawTelemetryReader` v1-compatible). |
| `SmartphoneRawTelemetryRecorder.kt` | Added instance-scoped synchronized 4-slot primitive `LocationDedupRingBuffer` per GNSS channel (`GNSS`, `GNSS_FUSED`) with `filledCount` guard (Gate 3 #1), `resolveLocationChannel(location)`, and `@JvmOverloads` `channel` parameter on `recordLocation` & `recordGnss` (Gate 3 #2). | Zero allocations per fix; deduplication occurs before `Channel.trySend` (`droppedFrameCount == 0`); resets automatically per session; `0` overhead when disabled. |
| `SpeedAndLocationDevice.java` & `SpeedAndLocationDevice_ATTFused.kt` | Tagged `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` -> `GNSS_FUSED` and `GPS` -> `GNSS` in `SpeedAndLocationDevice.java:578-585`; tagged pre-EKF raw GPS fix as `GNSS` in `SpeedAndLocationDevice_ATTFused.kt:504`. | `SpeedAndLocationDevice_ATTFused.kt` `1,495/1,500` LoC (`+0` net LoC, Gate 3 #5); `SpeedAndLocationDevice.java` `841/1,500` LoC; all device-pairing subsets preserved. |
| `OfflineReplayModels.kt`, `OfflineTelemetryReplayHarness.kt`, `OfflineReplayCli.kt`, `app/build.gradle` | Added `ReplayGnssSource { AUTO, GPS, FUSED }` (`AUTO` prefers `GNSS`, falls back to `GNSS_FUSED` on fused-only archives); tracked last accepted GNSS row strictly inside GNSS branch across interleaved IMU rows (Gate 3 #3) keeping `totalEventsProcessed == phoneEvents + polarEvents`; wired `--gnss-provider` / `-PgnssProvider` + `ReplayConfig(gnssSource)` + updated `usageError` (Gate 3 #4). | `0` bytes added to production APK; legacy `v1` sidecars (`2026-11-22_081402.phone_raw.csv.gz`) parse cleanly and deduplicate repeated GNSS rows. |
| `docs/architecture.md` (`v1.0.25`) | Updated §3.3.4 to document all 9 channels (`GNSS` + `GNSS_FUSED`) and per-channel GNSS deduplication (`Rule 8.7`). | Zero SQLite, UI, or `strings*.xml` edits (`Localization Parity: N/A`); `ANT-Android-SDKs` untouched (`160000`). |

## 2. Verification Evidence (`TST-EXP-047.1`–`.3`)

- **Targeted Tier-1 Unit & Contract Suites (`88/88` PASS, `0` fail, `0` err, `0` skip, `0` new compiler warnings)**:
  - `PhoneRawTelemetryReaderTest`: `3/3` PASS (`roundTripAllNineChannelsIncludingGnssFusedAndRejectsNonFinite_TST_EXP_047_1`)
  - `PhoneRawSidecarRecoveryTest`: `4/4` PASS (`recoverAbandonedTmpToFinal_salvagesGnssAndGnssFusedRowsAndDropsTruncatedTail_TST_EXP_047_1`)
  - `SmartphoneRawTelemetryRecorderTest`: `11/11` PASS (`recordLocation_deduplicatesPerChannelAcrossInterleavedProvidersAndResetsPerSession_TST_EXP_047_2`)
  - `SpeedAndLocationDevice_ATTFusedTest`: `37/37` PASS (`concurrentGpsAndAttFused_deduplicatesIdenticalGpsFixAcrossInterleavedFusedUpdate_TST_EXP_047_2`)
  - `SpeedAndLocationDeviceGoogleFusedTest`: `7/7` PASS (`testOnLocationChanged_tagsGoogleFusedFixAsGnssFusedEvenWithNullProvider_TST_EXP_047_1`)
  - `OfflineTelemetryReplayHarnessTest`: `11/11` PASS (`replay_mixedGnssAndDivergentGnssFused_..._TST_EXP_047_3`, `replay_gnssFusedOnlySidecar_..._TST_EXP_047_3`, `replay_legacyV1DuplicateGnssRowsWithInterleavedImu_..._TST_EXP_047_3`)
  - `OfflineReplayCliTest`: `4/4` PASS (`cli_gnssProviderFlag_selectsAutoGpsFusedAndRejectsInvalidValue_TST_EXP_047_3`)
  - `ArchitectureConformanceContractTest`: `11/11` PASS (`SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC verified)
- **Live Pure-JVM CLI Smoke (`:app:replayRawTelemetry`)**: `./gradlew :app:replayRawTelemetry -PphoneRaw=... -PgnssProvider=gps` exited `0` (`BUILD SUCCESSFUL in 4s`), isolating raw GPS (`dist=29.2 m`) from divergent `GNSS_FUSED`.
- **Machine Evidence Bundle (`Rule 8.4` / `ATT-9544`)**: Generated via `tools/auto_gate.py --ticket ATT-9604 --tier tier-a --base-ref sprint/2026-11-22_03_Nacht --write-bundle --advance-jira` at `docs/engineering/test_evidence/tickets/ATT-9604/` (`evidence_summary.json`, `manifest.json`, 20 JUnit XMLs: `18/20` suites `184/184` in-scope PASS, `static_safety_linters` PASS, `deliverable_lint` PASS, `conflict_markers` PASS).
- **Documented Pre-Existing Baseline Deviations (`auto_gate.py`)**: Two unrelated pre-existing baseline failures on `sprint/2026-11-22_03_Nacht` (untouched by `ATT-9604`, tracked externally) caused `dependent_unit_tests` to exit non-zero: (1) `DataStoreHotPathContractTest.testHotPathFiles_containZeroRunBlockingCalls` on `SettingsDataStoreJavaHelper.kt:381` (tracked in **`ATT-9578`**), and (2) `MeasuredNfrBenchmarkTest.testNfr014_cockpitTelemetryThrottling_measuredMaxOneHzOverFiveSeconds` + `@AfterClass` on `BANALServiceRepository` 1-Hz UI polling (tracked in **`ATT-9639`**; `NFR-001`..`NFR-013` all PASS).
- **Governance & Index Parity**: `verify_requirement_governance.py --sync-indexes` and `--base-ref sprint/2026-11-22_03_Nacht` PASS (`exit 0`).
