# Stage 5: Walkthrough & Verification - ATT-8158: Measured NFR Verification & Evidence

**Ticket**: [ATT-8158](https://rainerblind.atlassian.net/browse/ATT-8158) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `2026-11-17_01_Früh` | **Branch**: `feature/ATT-8158-fix` | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-113` (`REQ-PRO-113.1..3`), `REQ-NFR-001`..`REQ-NFR-018`
**Test Mapping**: `TST-PRO-113` (`TST-PRO-113.1..3`), `TST-NFR-001`..`TST-NFR-018`
**UI Consistency**: N/A - no UI or strings modified (`ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`)

---

## 1. Executive Summary & Stage 6 Bounce Remediation
- Decoupled synchronous SQLite extrema writes from `TrackerService.sampleAndWriteToDb()` via `WorkoutRepository.updateExtremaValueInMemory()`, keeping reactive `_allWorkouts` updates synchronous while batching SQLite extrema persistence on `mDbExecutor` (`recordLiveSample`), cutting `REQ-NFR-003` `p99` to `31.802–38.950 ms` across consecutive runs.
- Upgraded `MeasuredNfrBenchmarkTest.kt`: `testNfr001` drives `SpeedAndLocationDevice_ATTFused.onSensorChanged` with synthetic `SensorEvent.timestamp` vs `41.55 ms` arrival jitter; `testNfr005` exercises `LiveWorkoutSession.recordStreamPoint` + total heap ceiling; `testNfr007` exercises `BANALService` / `MySensorManager` -> `ProxySensor`; `testNfr009` drives full `TrackerService` lifecycle (`onCreate` -> `START_NORMAL` -> `PAUSED` -> `ACTION_STOP` -> `onDestroy`) with `REQ-NFR-009` reconciled; `testNfr010` writes via `TrackerService.mDbExecutor` + `WorkoutRepository.recordLiveSample`.
- Hardened atomic per-NFR (`REQ-NFR-*.json`) + `FileLock`-synchronized `nfr_measurements.json` persistence, `@AfterClass` completeness verification, `NonFunctionalRequirementsContractTest.kt` completeness assertions, and `tools/generate_test_evidence.py` ledger parsing of `docs/engineering/test_evidence/nfr_manual_protocol_template.md`.

---

## 2. Group A Measured NFR Results (`app/build/reports/nfr/nfr_measurements.json`)

| REQ / TST | Component / Class | Metric | Measured | Budget | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `REQ-NFR-001` / `TST-NFR-001` | `SpeedAndLocationDevice_ATTFused`, `KinematicAltitudeSlopeEkf` | `ekf_cycle_p95_ms` | `0.6055 ms` (`dt_err=0.1866 ms`, jitter=`41.55 ms`) | `<= 2.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-002` / `TST-NFR-002` | `SpeedAndLocationDevice_GPS` | `stationary_drift_m_per_min` | `0.0000 m/min` (1800 ticks, `431.42 ms`) | `< 0.5 m/min` | **PASS** (`Verified`) |
| `REQ-NFR-003` / `TST-NFR-003` | `TrackerService` (`tracker`), `WorkoutRepository` | `tick_p99_ms` | `31.8023 ms` (`p50=13.035`, `p95=19.542`, 1000 ticks) | `< 50.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-005` / `TST-NFR-005` | `LiveWorkoutSession` (`recordStreamPoint`) | `retained_heap_delta_mb` | `0.0000 MB` (`total=149.758 MB`, `180` increments) | `< 40.0 MB` (`<= 150 MB`) | **PASS** (`Verified`) |
| `REQ-NFR-007` / `TST-NFR-007` | `BANALService`, `MySensorManager`, `ProxySensor` | `dispatch_p99_ms` | `0.0009 ms` (1000/1000 delivered, `banal_hr=149`) | `< 25.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-008` / `TST-NFR-008` | `BTLERadarDevice`, `ANTBikeRadarDevice` | `watchdog_failover_ms` | `3500.0000 ms` (`DISCONNECTED_ERROR`) | `<= 3500 ms` | **PASS** (`Verified`) |
| `REQ-NFR-009` / `TST-NFR-009` | `TrackerService` (`wakeLock` lifecycle) | `wakelock_release_latency_ms` | `175.1567 ms` (held tracking/paused, 0 leaks) | `0 leaks` | **PASS** (`Verified`) |
| `REQ-NFR-010` / `TST-NFR-010` | `TrackerService` (`mDbExecutor`), `WorkoutRepository` | `db_write_p99_ms` | `1.7371 ms` (`off_main_thread=true`, `all_inserted=true`) | `< 30.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-011` / `TST-NFR-011` | `WorkoutSamplesDatabaseManager` | `storage_growth_kb_per_h` | `260.0000 KB/h` (`73.96 B/sample`, 3600 rows) | `<= 360.0 KB/h` | **PASS** (`Verified`) |
| `REQ-NFR-013` / `TST-NFR-013` | `IncrementalTrackDecimator` | `polyline_update_p95_ms` | `0.0060 ms` (10,000-pt track, max `0.5201 ms`) | `< 30.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-014` / `TST-NFR-014` | `BANALServiceRepository` | `emissions_in_5s` | `5.0000 emissions` (5000 ms window) | `<= 5 / 5s` | **PASS** (`Verified`) |

---

## 3. Group B Honest Manual Boundary (`[MANUAL PROTOCOL — NOT EXECUTED IN JVM]`)
- `REQ-NFR-004, 006, 012, 015, 016, 017, 018` remain `[Implemented]` and `TST-NFR-004, 006, 012, 015, 016, 017, 018` remain `[Planned]` (`On-Device Manual` in `docs/engineering/test_evidence/nfr_manual_protocol_template.md`) parsed dynamically by `tools/generate_test_evidence.py`.

---

## 4. Verification Scope & Governance Results
- **Targeted Gradle Run 1** (`*MeasuredNfrBenchmarkTest`, `*NonFunctionalRequirementsContractTest`, `*ArchitectureConformanceContractTest`, `*TrackerService*`, `*WorkoutRepository*`, `*SpeedAndLocationDevice_ATTFusedTest`, `:app:compileDebugAndroidTestKotlin`): **220/220 PASS** (`0` failures, `0` skipped; `REQ-NFR-003` `tick_p99_ms=38.9501 ms`).
- **Targeted Gradle Run 2 (`--rerun-tasks` under `maxParallelForks = 2`)** (`*MeasuredNfrBenchmarkTest` 11/11, `*NonFunctionalRequirementsContractTest` 3/3, `*ArchitectureConformanceContractTest` 9/9): **23/23 PASS** (`0` failures, `0` skipped; `REQ-NFR-003` `tick_p99_ms=31.8023 ms`).
- **Python Evidence Unit Tests** (`python3 -m unittest discover -s tools -p "test_generate_test_evidence*.py" -v`): **8/8 OK**.
- **Governance & Spec Checks**: `verify_requirement_governance.py --sync-indexes`, `verify_requirement_governance.py` (`docs/requirements.md`: Total=1209, Verified=965, Implemented=243, Planned=1; `docs/tests.md`: Total=1216, Verified=994, Implemented=214, Planned=8), and `--check-specs --text-file docs/engineering/walkthroughs/ATT-8158_walkthrough.md` exit **0**.
