# Stage 5 Walkthrough - ATT-8019: Raw Polar IMU/RR Streaming GZIP Sidecars with Rolling Retention

**Ticket**: [ATT-8019](https://rainerblind.atlassian.net/browse/ATT-8019) (`3 SP`) | **Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-47.2`  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8019-heal` | **Healed Commit**: `d01d2668`  
**Requirements**: `REQ-SET-117` (`REQ-SET-117.1`..`REQ-SET-117.4`) | **Test Spec**: `TST-SET-119` (`TST-SET-119.1`..`TST-SET-119.5`)  
**Author**: AI Agent 1 (`Lane 3 Flash`, Role Rotation `Rule 7.1`) | **UI Changes**: M3 Settings Segmented Selector

---

## 1. Implementation Summary
- **Buffered Streaming GZIP Sidecars (`PolarRawTelemetryRecorder.kt`, `REQ-SET-117.1`, `117.2`)**:
  - Persists high-frequency Polar PMD live (`ATT-8016`) and offline sync (`ATT-7918`) telemetry into `files/raw_sensor/<fileBaseName>.polar_raw.csv.gz` outside `WorkoutSamples.db`.
  - Non-blocking `Channel<RawTelemetryRow>(capacity = 8192)` drained on `Dispatchers.IO` ensures BLE GATT and PMD callbacks never stall or drop frames due to disk I/O.
  - Self-describing `# metadata: {...}` JSON header followed by monotonic CSV columns (`sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms`).
  - Empty or aborted sessions discard the file, leaving zero orphan files on disk.
- **Deterministic Replay Parser (`PolarRawTelemetryReader.kt`, `REQ-SET-117.2`)**:
  - Pure-Kotlin reader validating metadata integrity and streaming reconstructed frames with exact timestamp fidelity and corrupted archive recovery.
- **Cascade Deletion & Rolling Retention (`PolarRawTelemetryPruner.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `REQ-SET-117.3`)**:
  - `PolarRawTelemetryPruner.deleteSidecar()` hooked into `WorkoutSummariesDatabaseManager.deleteWorkout()` and `WorkoutRepository.deleteWorkoutCascade()` (`REQ-DAT-042`).
  - Off-thread background pruner enforcing `RawSensorRetentionPolicy` (`DAYS_30` [default], `KEEP_ALL`, `DAYS_7`, `OFF`) without deleting workout summary database records.
- **M3 Settings UI, 9-Locale Parity & Visual Inspection (`SensorsGpsFilterSection.kt`, `Rule 8.5`, `REQ-SET-117.4`)**:
  - Added `TuningSegmentedButtonRow` for `RawSensorRetentionPolicy` across 9 locales (`strings_devices.xml`) without emojis (`Rule 38`).
  - Generated and verified `docs/engineering/walkthroughs/img/ATT-8019_raw_telemetry_settings.png` via `read_file` across orientation, color mapping, typography, and touch ergonomics.
  - Updated SWE.2 architecture specification in `docs/architecture.md`.

---

## 2. Stage 6 Bounce 1/3 Remediation (`d01d2668`)
- **Lifecycle Registration Race Condition (`PolarOnlineStreamingBridge.kt`)**: Added `activeBridges` registry (`attachActiveRecorder`/`detachActiveRecorder`). Guarantees bridges connected before workout start (e.g. on Dashboard/Cockpit) attach to the recorder upon session start without dropping initial frames.
- **Clock-Domain Mixing & Drift Remediation (`PolarRawTelemetryRecorder.kt`)**: Separated hardware uptime clocks (`firstAccNs`, `firstGyroNs`, `resolveHwElapsedMs`) from host wall-clock epoch timestamps (`firstHrNs`, `firstRrNs`, `resolveHostElapsedMs`), eliminating multi-year timestamp distortion.
- **Targeted Healed Verification**: `PolarRawTelemetryRecorderTest` (5/5 PASS), `PolarOnlineStreamingBridgeTest` (6/6 PASS), `PolarRawTelemetryPrunerTest` (4/4 PASS), `PolarRawTelemetryReaderTest` (3/3 PASS), and `TuningPreferencesDataStoreRawSensorRetentionTest` (3/3 PASS) — total 21/21 tests passed cleanly.

---

## 3. Verification Evidence & Traceability Matrix (`Rule 7.5` — Tier 1 Targeted Suite)
- **Governance**: `verify_requirement_governance.py` passed with 0 code orphans (2357 files scanned) and 100% living doc parity.

| Spec ID | Scope | Evidence (`app/src/test/`) | Status |
| :--- | :--- | :--- | :--- |
| `TST-SET-119.1` | Buffered GZIP sidecar writer, pre-connected bridges & clock isolation | `PolarRawTelemetryRecorderTest` | Verified |
| `TST-SET-119.2` | Self-describing header & replay parser | `PolarRawTelemetryReaderTest` | Verified |
| `TST-SET-119.3` | Cascade deletion & rolling retention pruner | `PolarRawTelemetryPrunerTest` | Verified |
| `TST-SET-119.4` | DataStore persistence & 9-locale strings | `TuningPreferencesDataStoreRawSensorRetentionTest`, `TranslationParityTest` | Verified |
| `TST-SET-119.5` | Compose M3 Settings selector & Rule 8.5 PNG | `SensorsGpsFilterScreenshotTest`, `ATT-8019_raw_telemetry_settings.png` | Verified |
