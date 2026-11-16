# Stage 5 Walkthrough - ATT-8018: Polar H10 200 Hz Live Running Dynamics & FIT Export

**Ticket**: [ATT-8018](https://rainerblind.atlassian.net/browse/ATT-8018) (`3 SP`) | **Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-47.1`  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8018` | **Stage 4 Commit**: `4882ef17`  
**Requirements**: `REQ-SET-116` (`REQ-SET-116.1`..`REQ-SET-116.4`) | **Test Spec**: `TST-SET-118` (`TST-SET-118.1`..`TST-SET-118.5`)  
**Author**: AI Agent 1 (`Lane 3 Flash`, Role Rotation `Rule 7.1`) | **UI Changes**: None (Sensor/DSP/Export)

---

## 1. Implementation Summary
- **200 Hz Biomechanics DSP (`PolarH10RunningDynamicsEstimator.kt`, `REQ-SET-116.2`)**:
  - Continuous `~0.25 Hz` 3D low-pass gravity alignment projecting `200 Hz` (`5 ms`) sternum `ACC` onto world vertical, immune to chest strap tilt.
  - Per-step zero-mean detrended double integration computing peak-to-trough **Vertical Oscillation** (`5.0..13.0 cm`, `6`-step rolling smoothing, zero quadratic drift).
  - `5 ms`-resolution stance-peak foot-strike (`IC`) to toe-off (`TO`) **Ground Contact Time** (`140..400 ms`), suppressed to `null` during walking (`< 130 spm`) and cleared on standstill (`>= 2.5 s`) or `< 200 Hz` rates.
- **200 Hz H10 Bridge & Telemetry (`PolarOnlineStreamingBridge.kt`, `SensorType.java`, `ActivityType.java`, `DeviceType.java`, `REQ-SET-116.1`, `116.3`)**:
  - Activated `highRateRunningDynamicsActive = !isVeritySense && isRunSportActive()` (`200 Hz` H10 `ACC` in `BSportType.RUN`; Verity Sense `52 Hz` and `BIKE` unchanged).
  - Added `SensorType.VERTICAL_OSCILLATION` (`cm`, `units_cm`, `DOUBLE`) and `SensorType.GROUND_CONTACT_TIME` (`ms`, `units_time_ms`, `DOUBLE`) in `ActivityType.RUN_SPEED_AND_CADENCE`, `DeviceType.HRM`, and 9-locale `strings_devices.xml`.
- **Persistence, Aftermath & Native FIT Export (`TrackerService.java`, `WorkoutRepository.kt`, `WorkoutDataMapper.kt`, `FitFileWriter.kt`, `REQ-SET-116.3`, `116.4`)**:
  - Wired `TrackerService.IMPORTANT_SENSOR_TYPES`, `WorkoutRepository.calculateAndSaveExtrema()`/`streamSamples()`, `WorkoutSampleRecord`, and `WorkoutDataMapper.sensorsToCheck` with zero SQLite schema bumps.
  - Encoded FIT SDK `mm` (`(voCm * 10.0).toFloat()`) and `ms` (`Float`) on `RecordMesg`, `LapMesg`, and `SessionMesg` (`RunningDynamicsAccumulator`), and updated `docs/architecture.md` Section 3.4.

---

## 2. Verification Evidence (`Rule 7.5` — Reused Stage 4 Suite at Unchanged Code SHA `4882ef17`)
- **Tier 1 Targeted Unit, Contract, L10n & FIT Suite (`59/59 PASS`, `0 failures`, `0 errors`, `0 skipped`)**:
  - `PolarH10RunningDynamicsEstimatorTest`: tilted gravity VO (`5.0..13.0 cm`), `5 ms` GCT (`140..400 ms`), `< 130 spm` walking suppression, standstill reset (`// TST-SET-118.1`, `// TST-SET-118.2`).
  - `PolarOnlineStreamingBridgeTest`: H10 `200 Hz` `BSportType.RUN` negotiation vs Verity/Bike exclusion (`// TST-SET-118.2`).
  - `SensorCategoryAndSelectorContractTest`, `ActivityTypeTest`, `TranslationParityTest`: partition parity, `RUN_SPEED_AND_CADENCE`, 9-locale parity (`// TST-SET-118.3`).
  - `WorkoutRepositoryProjectionTest`: 1-Hz series & `AVG` extrema persistence without DB bump (`// TST-SET-118.4`).
  - `FitFileWriterTest`: decoded FIT binary round-trip verifying exact `mm` (`cm * 10f`) & `ms` across `RecordMesg`/`LapMesg`/`SessionMesg` (`// TST-SET-118.5`).

---

## 3. Traceability & Governance Matrix

| Spec ID | Scope | Evidence (`app/src/test/`) | Status |
| :--- | :--- | :--- | :--- |
| `TST-SET-118.1` | 200 Hz Tilted VO & 5 ms GCT | `PolarH10RunningDynamicsEstimatorTest` | Verified |
| `TST-SET-118.2` | `< 130 spm` Walk Guard & 200 Hz Bridge | `PolarH10RunningDynamicsEstimatorTest`, `PolarOnlineStreamingBridgeTest` | Verified |
| `TST-SET-118.3` | SensorType, ActivityType & 9-Locale Parity | `SensorCategoryAndSelectorContractTest`, `ActivityTypeTest`, `TranslationParityTest` | Verified |
| `TST-SET-118.4` | 1-Hz Samples & Aftermath Extrema | `WorkoutRepositoryProjectionTest` | Verified |
| `TST-SET-118.5` | FIT `Record`/`Lap`/`Session` `mm` & `ms` | `FitFileWriterTest` | Verified |
