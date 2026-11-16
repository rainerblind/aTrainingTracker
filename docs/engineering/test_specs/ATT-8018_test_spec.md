# Stage 2: Requirement & Test Specification - ATT-8018: Polar H10 200 Hz Running Dynamics & FIT Export

**Ticket**: [ATT-8018](https://issue-tracker/ATT-8018) (`3 SP`) | **Parent Epic**: `ATT-5910` | **Target Release**: `V5.0.0` | **Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-SET-116` *(skips `REQ-SET-115` allocated by `ATT-8108`)* | **Test Spec ID**: `TST-SET-118` | **Author**: `agent2` (`subagent_pro`)

---

## 1. Requirement Specification (`REQ-SET-116`)
- **Attributes**: Priority `High` | Type `Functional` | Method `Unit / Integration` | Status `Planned`
- **REQ-SET-116.1 (H10 200 Hz Lifecycle)**: `PolarOnlineStreamingBridge` SHALL set `highRateRunningDynamicsActive = true` (`H10_HIGH_RATE_ACC_HZ = 200 Hz`, fallback `100/50 Hz`) exclusively for Polar H10 during `BSportType.RUN`; Verity (`52 Hz`) & non-`RUN` stay unchanged; reset cleanly on disconnect/sport switch/`shutDown()`.
- **REQ-SET-116.2 (200 Hz Biomechanics & Guards)**: `PolarH10RunningDynamicsEstimator` SHALL estimate trunk vertical direction (`~0.25 Hz` gravity LPF), compute drift-free detrended per-step **Vertical Oscillation** (`5.0..13.0 cm`, 5–8 step rolling smoothing), detect `5 ms` **Ground Contact Time** (`140..400 ms`), suppress GCT during walking (`< 130 steps/min`), and reset/clear on standstill (`>= 2–3 s`).
- **REQ-SET-116.3 (Telemetry, Persistence & 9-Locale Strings)**: Expose `SensorType.VERTICAL_OSCILLATION` (`cm`, `units_cm`, `DOUBLE`) and `SensorType.GROUND_CONTACT_TIME` (`ms`, `units_time_ms`, `DOUBLE`) in `SPEED_PACE_AND_DISTANCE`, `ActivityType.RUN_SPEED_AND_CADENCE`, `DeviceType.HRM`, `TrackerService`, `WorkoutDataMapper`, and `WorkoutRepository` (zero DB schema bump), passing `SensorCategoryAndSelectorContractTest` and 9-locale `TranslationParityTest`.
- **REQ-SET-116.4 (FIT SDK Unit Scaling)**: `FitFileWriter.kt` SHALL encode VO in **millimeters (`mm`)** (`cm * 10f`) and GCT in **milliseconds (`ms`)** on `RecordMesg`, `LapMesg`, and `SessionMesg`.

### Acceptance Criteria & System Invariants
- *Given* `200 Hz` H10 `ACC` across tilted axes (`150–195 spm`) vs walking (`< 130 spm`), *Then* running emits drift-free VO (`5.0..13.0 cm`) & GCT (`140..400 ms`), while walking suppresses invalid GCT.
- *Given* a recorded H10 run, *When* exported via `FitFileWriter` and audited for i18n, *Then* `RecordMesg`/`LapMesg`/`SessionMesg` hold `mm` (`cm * 10f`) and `ms`, and all 9 locales define matching display/unit resources (`cm`, `ms`).
- **Invariants**: Net-new `REQ-SET-116`/`TST-SET-118` (zero collision with `ATT-8108`'s `REQ-SET-115`/`TST-SET-117`); zero SQLite schema bumps; `FitFileWriter` exact `cm * 10f -> mm`; walking `< 130 spm` emits no GCT; `ATT-8017` cadence & `CADENCE_PRIORITY` intact.

---

## 2. Test Specification (`TST-SET-118`)
1. `TST-SET-118.1` (`PolarH10RunningDynamicsEstimatorTest.kt`): Tilted `0.25 Hz` gravity alignment, detrended double-integration VO (`5.0..13.0 cm`, 5–8 step rolling stability), `5 ms` GCT (`140..400 ms`, `150–195 spm`), zero quadratic drift over sustained runs.
2. `TST-SET-118.2` (`PolarH10RunningDynamicsEstimatorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`): `< 130 spm` walking GCT suppression, standstill reset, H10-only `200 Hz` PMD negotiation in `BSportType.RUN` vs Verity/Bike exclusion, clean `shutDown()`.
3. `TST-SET-118.3` (`SensorCategoryAndSelectorContractTest.kt`, `ActivityTypeTest.kt`, `TranslationParityTest.kt`): Exhaustive `SensorCategory` count & partition parity, `ActivityType.RUN_SPEED_AND_CADENCE` inclusion, 9-language parity for `units_cm` & VO/GCT strings.
4. `TST-SET-118.4` (`WorkoutRepository*Test`, `WorkoutDataMapperTest`): `TrackerService`/`WorkoutRepository.streamSamples()`/`calculateAndSaveExtrema()` 1-Hz series & `AVG`/`MIN`/`MAX` persistence without DB migration.
5. `TST-SET-118.5` (`FitFileWriterTest.kt`): Decodes generated FIT file verifying exact `verticalOscillation` (`cm * 10f` `mm`) & `stanceTime` (`ms`) across `RecordMesg`, `LapMesg`, and `SessionMesg`.

---

## 3. Traceability Matrix
| Req ID | Description | Test Case ID | Verification |
| :--- | :--- | :--- | :--- |
| `REQ-SET-116.1` | H10-only 200 Hz `BSportType.RUN` PMD & reset | `TST-SET-118.2` | Unit / Integration |
| `REQ-SET-116.2` | Tilted 200 Hz DSP, bounds & `< 130 spm` guard | `TST-SET-118.1`, `TST-SET-118.2` | Unit |
| `REQ-SET-116.3` | SensorType, persistence & 9-locale parity | `TST-SET-118.3`, `TST-SET-118.4` | Unit / Contract |
| `REQ-SET-116.4` | FIT `RecordMesg`/`LapMesg`/`SessionMesg` `mm`/`ms` | `TST-SET-118.5` | Integration |
