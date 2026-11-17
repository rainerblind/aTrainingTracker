# Stage 2: Requirement & Test Specification - ATT-8158: Measured NFR Verification & Evidence

**Ticket**: [ATT-8158](https://rainerblind.atlassian.net/browse/ATT-8158) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `2026-11-17_01_Früh` | **SP**: 3 SP | **Status**: Planned | **Author**: AI Agent 1 | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-113` (`REQ-PRO-113.1..3`), `REQ-NFR-001`..`REQ-NFR-018`
**Test Spec Mapping**: `TST-PRO-113` (`TST-PRO-113.1..3`), `TST-NFR-001`..`TST-NFR-018`

---

## 1. Requirement Specification (`REQ-PRO-113`, `REQ-NFR-001..018`)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit` (`001, 002, 008, 013, 014`), `Integration` (`003, 005, 007, 009, 010, 011`), `On-Device Manual` (`004, 006, 012, 015, 016, 017, 018`)
* **REQ-PRO-113.1 (`Measured JVM/Robolectric NFR Benchmarks`)**: Group A NFRs (`001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014`) SHALL be verified by measured JVM/Robolectric benchmarks (planned `MeasuredNfrBenchmarkTest.kt`) executing JIT warmup, asserting §4.9 budgets via `System.nanoTime`, `Runtime` post-GC heap delta, `ShadowPowerManager`, SQLite `insertOrThrow` / file byte growth, `IncrementalTrackDecimator`, and `TestScope`, and emitting structured metrics to `app/build/reports/nfr/nfr_measurements.json`.
* **REQ-PRO-113.2 (`Attested Manual Protocol for Hardware NFRs`)**: Group B hardware/battery/Vitals NFRs (`004, 006, 012, 015, 016, 017, 018`) MUST use `Verification Method: On-Device Manual` (`REQ-NFR-*` `[Implemented]`, `TST-NFR-*` `[Planned]`) with explicit Pixel/Vitals attestation fields (device, build, date, operator, measured metric, verdict) and MUST NEVER be marked `[Verified]` by JVM unit tests.
* **REQ-PRO-113.3 (`Evidence Ingestion & Contract Verification`)**: `tools/generate_test_evidence.py` SHALL ingest `app/build/reports/nfr/nfr_measurements.json` and manual protocol status into `docs/engineering/test_evidence/sprint_<ID>.{md,json}`, and `NonFunctionalRequirementsContractTest.kt` SHALL verify measured benchmark & manual protocol artifacts instead of raw doc-string presence.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Modifies `REQ-NFR-001`..`REQ-NFR-018` (`TST-NFR-001`..`018`) and adds `REQ-PRO-113` (`TST-PRO-113`) in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`, targeting `NonFunctionalRequirementsContractTest.kt` and `tools/generate_test_evidence.py`.
* **Historical Origin & Commit Trace**: `fb45b2bd` (`ATT-6181`), `04e67a33` (`ATT-6139`), `0777baa6` (`ATT-6735`); updated in Sprint `2026-11-17_01_Früh` via `ATT-8158` after `ATT-8157` (`1750ab64`).
* **Root Reason for Existing Formulation**: `NonFunctionalRequirementsContractTest.kt` (`L54-123`) asserted only markdown substring presence, marked unexecuted hardware/Vitals NFRs `[Verified]` under `Unit`, and drifted from real symbols (`UnicycleOdometryEkf.kt`, `KinematicAltitudeSlopeEkf.kt`, `SpeedAndLocationDevice.java`, `LiveWorkoutSession.java`, `MapLayers.kt`, `BANALServiceRepository.kt`, `TrainingApplication.java`).
* **Preservation of Core Invariants**: Retains all 18 numeric §4.9 budgets, `ArchitectureConformanceContractTest.kt` §4.9 checks, zero `app/src/main/` changes, and `ANT-Android-SDKs` untouched (`0e8cea71`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **AC-1 (`TST-PRO-113.1` — Measured Group A Benchmarks)**: *Given* the 11 measurable NFR tests (`001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014`), *When* executed after warmup under `./gradlew testDebugUnitTest`, *Then* measured p95/p99 latency, drift, heap delta, storage KB/h, WakeLock release, and 1-Hz throttling pass §4.9 budgets and write `app/build/reports/nfr/nfr_measurements.json`.
* **AC-2 (`TST-PRO-113.2` — Honest Group B Manual Protocols)**: *Given* `REQ-NFR-004, 006, 012, 015, 016, 017, 018` and `TST-NFR-004, 006, 012, 015, 016, 017, 018`, *When* inspected before a physical Pixel/Vitals run, *Then* `Verification Method` is `On-Device Manual`, `TST-NFR-*` remains `[Planned]`, and no unit test fabricates `[Verified]`.
* **AC-3 (`TST-PRO-113.3` — Evidence & Contract Enforcement)**: *Given* `tools/generate_test_evidence.py` and updated `NonFunctionalRequirementsContractTest.kt`, *When* run alongside `tools/test_generate_test_evidence.py` and `tools/verify_requirement_governance.py`, *Then* NFR measurements and manual status render in `docs/engineering/test_evidence/sprint_<ID>.{md,json}` and all checks pass (`exit 0`).

---

## 4. Test Specifications & Traceability Matrix (`9-Locale Parity`: N/A — zero `strings*.xml` edits)

| Test ID(s) | Scope / Method | Target Class / Symbol & Measurement | REQ Link | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-NFR-001` | JVM Unit | `UnicycleOdometryEkf.kt`, `KinematicAltitudeSlopeEkf.kt` (warmup + delta-T `<= 2.0 ms`) | `REQ-NFR-001` | Planned |
| `TST-NFR-002` | Robolectric Unit | `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_ATTFused.kt` (`< 0.5 m/min`, unpin `<= 1.0 s`) | `REQ-NFR-002` | Planned |
| `TST-NFR-003` | Robolectric Integration | `TrackerService.java` `mScheduler` (`p99 < 50 ms` on 1000 ms tick) | `REQ-NFR-003` | Planned |
| `TST-NFR-005` | Robolectric Integration | `TrackerService.java`, `LiveWorkoutSession.java` (post-GC heap delta `< 40 MB`) | `REQ-NFR-005` | Planned |
| `TST-NFR-007` | Robolectric Integration | `BANALService.java`, `ProxySensor.java` (`p99 < 25 ms` dispatch) | `REQ-NFR-007` | Planned |
| `TST-NFR-008` | Robolectric Unit | `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `MyBTLEDevice.java` (`<= 3.5 s` failover) | `REQ-NFR-008` | Planned |
| `TST-NFR-009` | Robolectric Integration | `TrackerService.java` `ShadowPowerManager` `TrackingLock` lifecycle (`0` leaks) | `REQ-NFR-009` | Planned |
| `TST-NFR-010` | Robolectric Integration | `WorkoutSamplesDatabaseManager.java`, `TrackerService.java` `mDbExecutor` (`< 30 ms` off-main) | `REQ-NFR-010` | Planned |
| `TST-NFR-011` | Robolectric Integration | `WorkoutSamplesDatabaseManager.java` WAL checkpoint `.db` delta (`<= 360 KB/h`) | `REQ-NFR-011` | Planned |
| `TST-NFR-013` | JVM Unit | `MapLayers.kt` `IncrementalTrackDecimator` / `decimateLiveTrack` (`< 30 ms` / 10k pts) | `REQ-NFR-013` | Planned |
| `TST-NFR-014` | JVM Unit (`TestScope`) | `BANALServiceRepository.kt` `allFilteredSensorData`, `TrackingTabsViewModel.kt` (`<= 1.0 Hz`) | `REQ-NFR-014` | Planned |
| `TST-NFR-004, 006, 012, 015, 016, 017, 018` | `On-Device Manual` | Attested Pixel/Vitals protocols (`simpleperf`, `batterystats`, `gfxinfo`, `am start -W`, RTK, Play Vitals) | `REQ-NFR-004,006,012,015..018` | Planned |
| `TST-PRO-113` | Integration / Review | `NonFunctionalRequirementsContractTest.kt`, `tools/generate_test_evidence.py`, `tools/test_generate_test_evidence.py` | `REQ-PRO-113` | Planned |
