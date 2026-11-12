# Stage 1 Analysis: ATT-6185 - [Verbesserung] Add integration tests for TrackerService, sensor layer and database

**Ticket**: [ATT-6185](https://rainerblind.atlassian.net/browse/ATT-6185)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-1-Agent---ATT-6185-self-1e7c93c0`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

Prior to this ticket, testing of the core tracking pipeline relied predominantly on isolated unit tests (e.g. `TrackerServiceWorkoutStartTest`, `TrackerServiceResumeTest`, `TrackerServiceLapTest`, `TrackerServiceDecoupledSummaryTest`) that heavily mock out SQLite persistence (`DatabaseManager`, `WorkoutRepository`) or Android framework services.
ASPICE Assessment finding C-11 explicitly identified the absence of integrated software testing across the core tracking chain (`SWE.5 BP`).
The goal of ATT-6185 is to establish $\ge 5$ robust integration tests verifying the integrated chain:
`Simulated Sensor Layer -> TrackerService -> LiveWorkoutSession -> WorkoutRepository -> SQLite Persistence (summaries, samples, rr_intervals, laps)`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * An audit of `TrackerService.java` reveals the core tracking loop is orchestrated via `sampleAndWriteToDb(BANALServiceComm)` and `maybeFlushLiveRrQueue()`.
  * `TrackerService.createNewWorkout()` generates a new workout record in `workout_summaries` and initializes the dynamic sample table `workout_samples_<baseFileName>`.
  * Multi-sensor telemetry from `banalService.getAllSensorData()` is mapped into `samplingValues` and persisted via `WorkoutRepository.recordLiveSample()`.
  * Dynamic statistics (min, max, average) are accumulated in `LiveWorkoutSession` and written to extrema tables and workout summary columns.
  * During paused state (`TrainingApplication.isPaused()`), `sampleAndWriteToDb` accumulates `TIME_TOTAL_s` but omits sample table insertions, preserving `TIME_ACTIVE_s`.
  * `AutoPauseManager.evaluateTick(...)` detects speed drops below threshold and transitions from `TRACKING` to `AUTO_PAUSED`, issuing pause broadcast; motion recovery above threshold triggers resume broadcast.
  * `TrackerService.endWorkout()` closes the active lap in `laps`, flushes remaining RR intervals synchronously via `flushLiveRrQueueSync()`, computes final metrics, and sets `FINISHED = 1`.
  * Running these paths together against authentic SQLite tables via Robolectric and `RealSqliteTestHelper` provides robust end-to-end integration verification without flaky network/radio hardware dependencies.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement $\ge 5$ robust integration tests in `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt` using Robolectric (`@RunWith(RobolectricTestRunner::class)`) and real SQLite databases.
  * Scenario 1: Start tracking $\rightarrow$ receive simulated sensor samples (HR, Speed/Cadence, Power, GPS) $\rightarrow$ verify SQLite tables (`workout_summaries`, dynamic samples table, `workout_rr_intervals`) are populated with matching values.
  * Scenario 2: Pause tracking $\rightarrow$ verify sample ingestion halts, no new rows are written to the samples table, and `TIME_ACTIVE_s` stops accumulating while `TIME_TOTAL_s` increments.
  * Scenario 3: Resume tracking $\rightarrow$ verify active time accumulation resumes and new sample rows continue to be written to SQLite.
  * Scenario 4: Auto-pause trigger $\rightarrow$ verify transition to `AUTO_PAUSED` upon low speed and auto-resume to `TRACKING` upon renewed motion.
  * Scenario 5: Stop and finish tracking $\rightarrow$ verify summary metrics (distance, avg/max HR, active time, ascent), lap persistence, and `FINISHED = 1` flag in SQLite.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying production tracking algorithms or changing database schemas.
  * Real Bluetooth/ANT+ radio hardware communication (simulated sensor providers in memory are used).
  * UI layer Compose rendering tests (handled by dedicated UI test suites).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Requirement Status**: Net-new requirement only (`REQ-INT-001`). No existing requirements modified.
* **Target**: Formalizes ASPICE SWE.5 integration test coverage for the core tracking pipeline.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-INT-001` (Core Tracking Chain Integration Verification) in `docs/requirements/requirement_specifications.md` and test specifications `TST-INT-001` through `TST-INT-005` in `docs/tests/test_specifications.md`.
* **Affected Architecture Elements**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`, test helpers, and engineering deliverables.
* **Verification Impact & Test Surface**: Verified via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.integration.*"` and `python3 tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Only test code and documentation are created. In-memory/temporary SQLite databases are cleanly closed and unlinked in `@After`.

---

## 6. Architectural Strategy & High-Level Solution

* Create package `com.atrainingtracker.trainingtracker.integration` under `app/src/test/java`.
* Author `TrackerServiceIntegrationTest.kt`:
  * Configured with `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [34])`.
  * Set up a testable `TrackerService` instance with synchronous `mDbExecutor` to ensure atomic deterministic database operations.
  * Provide a mock/stub `BANALServiceComm` delivering simulated `SensorData` across all primary sensor channels (`HEART_RATE_bpm`, `SPEED_mps`, `CADENCE_rpm`, `POWER_W`, `DISTANCE_m`, `ALTITUDE`, `TIME_ACTIVE`, `TIME_TOTAL`).
  * Initialize real SQLite databases via `WorkoutRepository.getInstance(context)`.
  * Verify all 5 lifecycle scenarios by asserting real rows in the SQLite tables using standard SQLite queries.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero modification to production code or database schemas.
  2. Complete isolation: each integration test initializes its own database state and cleans up on teardown.
  3. All assertions verify actual SQLite table rows and column values.
* **Risk Rating**: **LOW** (Net-new test suite adding verification coverage for SWE.5).
