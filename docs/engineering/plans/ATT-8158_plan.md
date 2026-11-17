# Stage 3: Implementation Plan - ATT-8158: Measured NFR Verification & Evidence

**Ticket**: [ATT-8158](https://rainerblind.atlassian.net/browse/ATT-8158) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `sprint/2026-11-17_01_Früh` | **Branch**: `feature/ATT-8158` | **Author**: AI Agent 1 | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-113` (`REQ-PRO-113.1..3`), `REQ-NFR-001`..`REQ-NFR-018` | **Test Mapping**: `TST-PRO-113`, `TST-NFR-001`..`TST-NFR-018`

---

## 1. Problem Description & Background
`NonFunctionalRequirementsContractTest.kt` (L54-123) previously checked only markdown threshold strings, falsely certifying all 18 `REQ-NFR-*` as `[Verified]`. Stage 4 replaces string-presence assertions with real JVM/Robolectric measurements for 11 Group A NFRs, honest `On-Device Manual` protocols for 7 Group B hardware/Vitals NFRs, and NFR metric ingestion in `tools/generate_test_evidence.py`.

## 2. Traceability & Requirements Mapping
* **Group A (11 JVM/Robolectric Measured)**: `REQ-NFR-001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014` -> `TST-NFR-001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014` & `TST-PRO-113.1`.
* **Group B (7 On-Device Manual)**: `REQ-NFR-004, 006, 012, 015, 016, 017, 018` -> `TST-NFR-004, 006, 012, 015, 016, 017, 018` & `TST-PRO-113.2`.
* **Evidence & Contract**: `REQ-PRO-113.3`, `REQ-PRO-100` -> `TST-PRO-113.3`, `TST-PRO-100`, `TST-PRO-052`.

## 3. System Invariants & Preserved Behavior
1. **Surgical Production Isolation & Submodule Lock**: `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`) remains untouched; `app/src/main/` is untouched except decoupling synchronous SQLite extrema writes from `TrackerService.sampleAndWriteToDb()` onto `mDbExecutor` via `WorkoutRepository.updateExtremaValueInMemory()` (`REQ-NFR-003`, `REQ-NFR-010`). All 18 §4.9 numeric budgets and `ArchitectureConformanceContractTest.kt` traceability remain preserved.
2. **Honest Evidence Boundary**: Group B NFRs stay `[Implemented]` / `[Planned]` (`On-Device Manual`) — zero fabricated hardware measurements or `[Verified]` claims in JVM tests.
3. **UI Consistency**: N/A (zero UI/Compose layout or `strings*.xml` modifications).

## 4. Proposed Architectural Changes
* **JVM/Robolectric NFR Benchmark Suite** (planned `MeasuredNfrBenchmarkTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/`): Executes JIT warmup + measured runs on real production components (`UnicycleOdometryEkf.kt`, `KinematicAltitudeSlopeEkf.kt`, `SpeedAndLocationDevice.java`, `TrackerService.java`, `LiveWorkoutSession.java`, `BANALService.java`, `ProxySensor.java`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `WorkoutSamplesDatabaseManager.java`, `MapLayers.kt`, `BANALServiceRepository.kt`), writing numeric results to `app/build/reports/nfr/nfr_measurements.json` and JUnit stdout.
* **Manual Protocol Template & Evidence Pipeline**: Add Group B attested protocol template (`[MANUAL PROTOCOL — NOT EXECUTED IN JVM]`) under `docs/engineering/test_evidence/` and extend `tools/generate_test_evidence.py` to ingest `app/build/reports/nfr/nfr_measurements.json` + manual protocol status into `docs/engineering/test_evidence/<target>.{md,json}`.
* **Contract Guardrail**: Refactor `NonFunctionalRequirementsContractTest.kt` to enforce Group A benchmark backing & Group B `On-Device Manual` non-`[Verified]` honesty instead of raw doc-string presence.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Group A Measured Benchmarks)**: Author planned `MeasuredNfrBenchmarkTest.kt` measuring real production execution after warmup for all 11 Group A NFRs (`001` EKF cycle/delta-T `<= 2.0 ms`; `002` stationary drift `< 0.5 m/min` & unpin `<= 1.0 s`; `003` `mScheduler` 1-Hz tick `p99 < 50 ms`; `005` `LiveWorkoutSession` post-GC heap delta `< 40 MB`; `007` `ProxySensor`/`BANALService` dispatch `p99 < 25 ms`; `008` `BTLERadarDevice` 3.5 s watchdog failover; `009` `ShadowPowerManager` `TrackingLock` lifecycle; `010` `WorkoutSamplesDatabaseManager` off-main-thread WAL write `p99 < 30 ms`; `011` 3,600-row WAL checkpoint `.db` delta `<= 360 KB/h`; `013` `IncrementalTrackDecimator` 10k-pt update `< 30 ms`; `014` `BANALServiceRepository.allFilteredSensorData` `TestScope` 1-Hz emission), emitting `app/build/reports/nfr/nfr_measurements.json`.
2. **Step 2 (Group B Manual Protocol Template)**: Create attested manual protocol template under `docs/engineering/test_evidence/` covering `REQ-NFR-004, 006, 012, 015, 016, 017, 018` with `[MANUAL PROTOCOL — NOT EXECUTED IN JVM]`, device, build, date, operator, measured value, and verdict fields.
3. **Step 3 (Contract Test Refactor)**: Update `NonFunctionalRequirementsContractTest.kt` (removing L54-123 doc-string presence checks) to verify Group A measured benchmark artifacts/tags and Group B `On-Device Manual` non-`[Verified]` honesty.
4. **Step 4 (Evidence Generator & Unit Tests)**: Extend `tools/generate_test_evidence.py` and `tools/test_generate_test_evidence.py` to ingest `app/build/reports/nfr/nfr_measurements.json` and render Group A measured metrics + Group B manual protocol status in `docs/engineering/test_evidence/<target>.{md,json}`.
5. **Step 5 (Verification & Status Sync)**: Run targeted Gradle benchmarks/contracts, `python3 -m unittest tools/test_generate_test_evidence.py`, and `python3 tools/verify_requirement_governance.py`; promote Group A `REQ-NFR-*` / `TST-NFR-*` and `REQ-PRO-113` / `TST-PRO-113` to `[Verified]` while keeping Group B `[Implemented]` / `[Planned]`.

## 6. Verification & Rollback Plan
* **Verification**: Targeted `./gradlew testDebugUnitTest --tests ...MeasuredNfrBenchmarkTest --tests ...NonFunctionalRequirementsContractTest --tests ...ArchitectureConformanceContractTest` (under `tools/parallel_sprint_lock.py`), `python3 -m unittest tools/test_generate_test_evidence.py`, and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Isolated on `feature/ATT-8158` (`git checkout --` / `git reset` cleanly restores prior state).

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-NFR-001`..`REQ-NFR-018` (`TST-NFR-001`..`018`) in `docs/requirements/req_process_stability.md` / `docs/tests/tst_process_stability.md`, plus `NonFunctionalRequirementsContractTest.kt` and `tools/generate_test_evidence.py`.
2. **Historical Origin & Commit Trace**: `fb45b2bd` (`ATT-6181`), `04e67a33` (`ATT-6139`), `0777baa6` (`ATT-6735`); updated under `ATT-8158`.
3. **Root Reason for Existing Formulation**: Established quantitative §4.9 targets, but `NonFunctionalRequirementsContractTest.kt` checked only doc-string presence and marked unexecuted device NFRs `[Verified]` under `Unit`.
4. **Preservation of Core Invariants**: Preserves all 18 numeric budgets, `ArchitectureConformanceContractTest.kt` §4.9 traceability, atomic `mDbExecutor` sample/extrema persistence, and `ANT-Android-SDKs` (`0e8cea71`).
