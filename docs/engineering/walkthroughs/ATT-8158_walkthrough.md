# Stage 5: Walkthrough & Verification - ATT-8158: Measured NFR Verification & Evidence

**Ticket**: [ATT-8158](https://rainerblind.atlassian.net/browse/ATT-8158) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `2026-11-17_01_Früh` | **Branch**: `feature/ATT-8158-fix` | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-113` (`REQ-PRO-113.1..3`), `REQ-NFR-001`..`REQ-NFR-018`
**Test Mapping**: `TST-PRO-113` (`TST-PRO-113.1..3`), `TST-NFR-001`..`TST-NFR-018`
**UI Consistency**: N/A - no UI or strings modified (`app/src/main/` untouched; `ANT-Android-SDKs` at `0e8cea714a231fb9565170d4ed01eafa868f9798`)

---

## 1. Executive Summary
- Replaced markdown string-presence checks in `NonFunctionalRequirementsContractTest.kt` with authentic measured JVM/Robolectric benchmarks (`MeasuredNfrBenchmarkTest.kt`) and explicit `On-Device Manual` governance (`docs/engineering/test_evidence/nfr_manual_protocol_template.md`).
- Updated `tools/generate_test_evidence.py` (`tools/test_generate_test_evidence.py`) to ingest `app/build/reports/nfr/nfr_measurements.json` and Group B manual protocol status into `docs/engineering/test_evidence/<target>.{md,json}`.
- Promoted `REQ-PRO-113` (`REQ-PRO-113.1..3`), `TST-PRO-113`, and the 11 Group A measured NFRs/TSTs (`001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014`) to `[Verified]`; kept all 7 Group B hardware/Vitals NFRs/TSTs (`004, 006, 012, 015, 016, 017, 018`) at `[Implemented]` / `[Planned]`.

---

## 2. Group A Measured NFR Results (`app/build/reports/nfr/nfr_measurements.json`)

| REQ / TST | Component / Class | Metric | Measured | Budget | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `REQ-NFR-001` / `TST-NFR-001` | `UnicycleOdometryEkf`, `KinematicAltitudeSlopeEkf` | `ekf_cycle_p95_ms` | `0.0262 ms` (1000 ticks) | `<= 2.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-002` / `TST-NFR-002` | `SpeedAndLocationDevice_GPS` | `stationary_drift_m_per_min` | `0.0000 m/min` (1800 ticks) | `< 0.5 m/min` | **PASS** (`Verified`) |
| `REQ-NFR-003` / `TST-NFR-003` | `TrackerService` (`tracker`) | `tick_p99_ms` | `49.5308 ms` (p50=`16.235`, p95=`33.432`, 1000 ticks) | `< 50.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-005` / `TST-NFR-005` | `LiveWorkoutSession` | `retained_heap_delta_mb` | `0.0000 MB` (3600 ticks, total `149.631 MB`) | `< 40.0 MB` | **PASS** (`Verified`) |
| `REQ-NFR-007` / `TST-NFR-007` | `MySensor`, `ProxySensor` | `dispatch_p99_ms` | `0.0010 ms` (1000/1000 delivered) | `< 25.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-008` / `TST-NFR-008` | `BTLERadarDevice`, `ANTBikeRadarDevice` | `watchdog_failover_ms` | `3500.0000 ms` (`DISCONNECTED_ERROR`) | `<= 3500 ms` | **PASS** (`Verified`) |
| `REQ-NFR-009` / `TST-NFR-009` | `TrackerService` (`wakeLock`) | `wakelock_release_latency_ms` | `2.8831 ms` (`released=true`, 0 leaks) | `0 leaks` | **PASS** (`Verified`) |
| `REQ-NFR-010` / `TST-NFR-010` | `WorkoutSamplesDatabaseManager` | `db_write_p99_ms` | `0.1948 ms` (`off_main_thread=true`, 1000 writes) | `< 30.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-011` / `TST-NFR-011` | `WorkoutSamplesDatabaseManager` | `storage_growth_kb_per_h` | `260.0000 KB/h` (`73.96 B/sample`, 3600 rows) | `<= 360.0 KB/h` | **PASS** (`Verified`) |
| `REQ-NFR-013` / `TST-NFR-013` | `IncrementalTrackDecimator` | `polyline_update_p95_ms` | `0.0070 ms` (10,000-pt track, max `0.4500 ms`) | `< 30.0 ms` | **PASS** (`Verified`) |
| `REQ-NFR-014` / `TST-NFR-014` | `BANALServiceRepository` | `emissions_in_5s` | `5.0000 emissions` (5000 ms window) | `<= 5 / 5s` | **PASS** (`Verified`) |

---

## 3. Group B Honest Manual Boundary (`[MANUAL PROTOCOL — NOT EXECUTED IN JVM]`)
- `REQ-NFR-004, 006, 012, 015, 016, 017, 018` remain `[Implemented]` and `TST-NFR-004, 006, 012, 015, 016, 017, 018` remain `[Planned]` (`On-Device Manual` in `docs/engineering/test_evidence/nfr_manual_protocol_template.md`) pending attested Pixel / Play Vitals execution.

---

## 4. Verification Scope & Governance Results
- **Tier 1 Targeted Gradle Verification** (`./gradlew :app:testDebugUnitTest --tests "*MeasuredNfrBenchmarkTest" --tests "*NonFunctionalRequirementsContractTest" --tests "*ArchitectureConformanceContractTest" :app:compileDebugAndroidTestKotlin`): **23/23 PASS** (`MeasuredNfrBenchmarkTest` 11/11, `NonFunctionalRequirementsContractTest` 3/3, `ArchitectureConformanceContractTest` 9/9; 0 failures, 0 skipped; `BUILD SUCCESSFUL in 5m 56s`), `:app:compileDebugAndroidTestKotlin` **PASS**.
- **Python Evidence Unit Tests** (`python3 -m unittest discover -s tools -p "test_generate_test_evidence*.py" -v`): **8/8 OK**.
- **Governance & Spec Checks**: `verify_requirement_governance.py --sync-indexes`, `verify_requirement_governance.py` (`docs/requirements.md`: Total=1209, Verified=965, Implemented=243, Planned=1; `docs/tests.md`: Total=1216, Verified=994, Implemented=214, Planned=8), and `--check-specs --text-file docs/engineering/walkthroughs/ATT-8158_walkthrough.md` exit **0**.
