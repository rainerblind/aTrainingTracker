# Stage 1 Analysis: ATT-8158 - Measured NFR Verification

**Ticket**: [ATT-8158](https://issue-tracker/ATT-8158) | **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
**Branch**: `feature/ATT-8158` | **Sprint**: `sprint/2026-11-17_01_Früh` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit Finding `M8` (`SWE.2 BP3`, `SWE.6 BP3`): `NonFunctionalRequirementsContractTest.kt` (L54-123) marks all 18 `REQ-NFR-001`..`018` and `TST-NFR-001`..`018` as `[Verified]` by checking only that threshold substrings appear in `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, and `docs/architecture.md` §4.9. Zero runtime timing, heap, storage, or concurrency measurements exist, and `tools/generate_test_evidence.py` emits only JUnit pass/fail and Kover metrics without measured NFR values.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**: Audited live codebase against ticket hypothesis:
  * **Confirmed**: `NonFunctionalRequirementsContractTest.kt` L59-123 performs pure `content.contains(...)` checks; `tools/generate_test_evidence.py` and `tools/jira_util.py` (`export-test-evidence`) do not ingest quantitative NFR benchmarks.
  * **Blind Spots Found**: (1) All 18 `REQ-NFR-*` / `TST-NFR-*` claim `Verification Method: Unit` and `[Verified]`, including physical-hardware/Play-Vitals metrics impossible in JVM. (2) Component drift: EKF lives in `UnicycleOdometryEkf.kt` / `SpeedAndLocationDevice_ATTFused.kt` / `KinematicAltitudeSlopeEkf.kt` (not `TrackerService.java`); stationary pinning in `SpeedAndLocationDevice.java`; sample writes via `LiveWorkoutSession.java` (`addSample`) and `WorkoutSamplesDatabaseManager.java`; app class is `TrainingApplication.java` (not `.kt`). (3) `tools/verify_requirement_governance.py` already supports `On-Device Manual` and flags unbacked `[Verified]` specs.
* **Catalog of All 18 `REQ-NFR-*` Budgets**:
  * **(A) Machine-Measurable in Robolectric JVM / Micro-Benchmark (11)**:
    * `REQ-NFR-001` (EKF delta-T `<= 2.0 ms`: `UnicycleOdometryEkf.kt`, `KinematicAltitudeSlopeEkf.kt`), `REQ-NFR-002` (Stationary drift `< 0.5 m/min`, unpin `<= 1.0 s`: `SpeedAndLocationDevice.java`), `REQ-NFR-003` (1-Hz tick `< 50 ms`: `TrackerService.java` `mScheduler`), `REQ-NFR-005` (Retained heap delta `< 40 MB`: `TrackerService.java`, `LiveWorkoutSession.java`), `REQ-NFR-007` (Sensor dispatch `< 25 ms`: `ProxySensor.java`, `BANALService.java`), `REQ-NFR-008` (Failover/watchdog `<= 3.5 s`: `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `MyBTLEDevice.java`), `REQ-NFR-009` (WakeLock `TrackingLock` lifecycle: `TrackerService.java`), `REQ-NFR-010` (SQLite sample write `< 30 ms` off main thread: `WorkoutSamplesDatabaseManager.java`, `TrackerService.java` `mDbExecutor`), `REQ-NFR-011` (SQLite growth `<= 360 KB/h`: `WorkoutSamplesDatabaseManager.java`), `REQ-NFR-013` (Map polyline update `< 30 ms`: `ATrainingTrackerMap.kt`), `REQ-NFR-014` (1-Hz UI emission throttling: `BANALServiceRepository.kt`, `TrackingTabsViewModel.kt`).
  * **(B) Physical/Device-Only Manual Attested Protocols (7)**:
    * `REQ-NFR-004` (CPU `< 5%`), `REQ-NFR-006` (Battery `< 5%/h` bg / `< 12%/h` fg), `REQ-NFR-012` (Compose `< 16.6 ms` / 60 FPS), `REQ-NFR-015` (Cold start TTID `<= 1200 ms`, TTFD `<= 1500 ms`), `REQ-NFR-016` (Open-sky GNSS `<= 5.0 m` p95 vs RTK), `REQ-NFR-017` (Play Vitals crash-free `>= 99.5%`), `REQ-NFR-018` (Play Vitals ANR `<= 0.05%`). Must use `On-Device Manual` (`[Implemented]` until attested).

## 3. Scope Grounding & Non-Goals
* **In-Scope**: Replace string-only checks in `NonFunctionalRequirementsContractTest.kt` with honest verification: JVM/Robolectric measured tests for Group A (11 NFRs) emitting structured metrics into `tools/generate_test_evidence.py`, and reclassify Group B (7 NFRs) as `On-Device Manual` with attested measurement protocols in `docs/requirements/req_process_stability.md` / `docs/tests/tst_process_stability.md`.
* **Out-of-Scope**: Refactoring production tracking/EKF/SQLite algorithms, running physical-device AndroidX Macrobenchmark/Battery Historian sessions, or modifying `ANT-Android-SDKs`.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-NFR-001`..`018` and `TST-NFR-001`..`018` (`[Verified]`, `Unit`) in `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`, linked from `docs/architecture.md` §4.9 (`REQ-PRO-052`).
* **Historical Origin & Commit Trace**: `fb45b2bd` (`ATT-6181`), `04e67a33` (`ATT-6139`), `0777baa6` (`ATT-6735`).
* **Root Reason for Existing Formulation**: Established quantitative §4.9 targets and bidirectional `REQ-NFR`/`TST-NFR` links, but used `NonFunctionalRequirementsContractTest.kt` doc-string assertions as a placeholder for execution.
* **Preservation of Core Invariants**: Preserve all 18 numeric budgets, `ArchitectureConformanceContractTest.kt` §4.9 traceability links, and `TrackerService.java` threading/WakeLock invariants while separating JVM-measured from `On-Device Manual` verification.

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-NFR-001`..`018` (honest `Verification Method`, component paths, Group B status `[Implemented]` until attested) and `REQ-PRO-100` (NFR metric ingestion in evidence).
* **Affected Architecture Elements**: `NonFunctionalRequirementsContractTest.kt`, new/extended JVM/Robolectric NFR measurement tests (alongside `TrackerServiceIntegrationTest.kt`, `HardwareHalIngestionIntegrationTest.kt`, `PersistenceSqliteIntegrationTest.kt`), `tools/generate_test_evidence.py`, `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`.
* **Verification Impact & Test Surface**: `TST-NFR-001`..`018`, `TST-PRO-100`, `tools/test_generate_test_evidence.py`, `tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Timing tests in CI/Robolectric can flake under host load; mitigate using warmup iterations, deterministic shadow/synthetic clocks where applicable, and p95/p99 or generous-yet-meaningful assertions alongside exact recorded measurements.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Keep all 18 documented budgets unchanged; preserve `ArchitectureConformanceContractTest.kt` §4.9 checks; zero changes to `ANT-Android-SDKs` or production behavior; no fabricated `[Verified]` claims for unexecuted device metrics.
* **Risk Rating**: **MEDIUM** — touches 18 NFR specs, contract tests, and evidence tooling; requires careful JVM benchmark stability without weakening budget honesty.
