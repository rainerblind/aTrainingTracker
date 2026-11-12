# Stage 3: Implementation Plan - ATT-6185: [Verbesserung] Add integration tests for TrackerService, sensor layer and database

**Ticket**: [ATT-6185](https://rainerblind.atlassian.net/browse/ATT-6185)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-TRK-075`  
**Test Mapping**: `TST-TRK-075`  
**Branch**: `subagent-Lane-1-Agent---ATT-6185-self-1e7c93c0`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Unit tests for `TrackerService` historically mocked out SQLite databases and framework components, leaving the core tracking chain unverified across multi-component boundaries. Under ASPICE SWE.5 (Remediation ID C-11), this plan defines the construction of an end-to-end integration test suite verifying the integrated behavior of `TrackerService`, simulated sensors, `LiveWorkoutSession`, `WorkoutRepository`, and authentic SQLite databases.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-075` (*Core Tracking Pipeline Integration Verification*)
* **Test Mapping**: `TST-TRK-075` (`TST-TRK-075.1` through `TST-TRK-075.6`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Code / Schema Alterations**: No modification to production schemas or runtime logic in `TrackerService.java`.
2. **Authentic SQLite Persistence**: Tests run against real SQLite databases using Robolectric and `RealSqliteTestHelper`.
3. **Database Isolation**: Databases are dynamically created, cleaned, and deleted per test to prevent leakage.
4. **Code Lock Enforced**: Production code modifications remain strictly blocked until Gate 3 is marked `Erledigt`.

---

## 4. Proposed Architectural Changes

### Component 1: `TrackerServiceIntegrationTest` (`app/src/test/.../integration/`)
* **Package**: `com.atrainingtracker.trainingtracker.integration`
* **Test Runner**: `@RunWith(RobolectricTestRunner::class)` with `@Config(sdk = [34])`.
* **Harness Elements**:
  * Authentic SQLite database backing via `ApplicationProvider.getApplicationContext()` and `WorkoutRepository.getInstance(context)`.
  * `TestableTrackerService` subclass injecting a synchronous direct executor for `mDbExecutor` to eliminate concurrency races.
  * Stubbed/mocked `BANALServiceComm` delivering simulated `SensorData` across HR, Speed, Cadence, Power, Distance, Altitude, GPS, and RR intervals.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create Integration Test Harness & Setup
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
* **Action**: Configure test harness, initialize Robolectric context, wire `TrainingApplication` context/preferences, set up authentic SQLite databases, and implement `TestableTrackerService`.

### Step 2: Implement Test Scenarios 1–3
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
* **Action**:
  * Scenario 1: `startTracking_ingestsMultiSensorSamplesAndPopulatesSqlite()` (`TST-TRK-075.1`).
  * Scenario 2: `pauseTracking_suspendsSampleWritesAndFreezesActiveTime()` (`TST-TRK-075.2`).
  * Scenario 3: `resumeTracking_resumesSampleIngestionAndActiveTimeAccumulation()` (`TST-TRK-075.3`).

### Step 3: Implement Test Scenarios 4–5
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
* **Action**:
  * Scenario 4: `autoPause_transitionsStateOnSpeedDropAndResumesOnMotion()` (`TST-TRK-075.4`).
  * Scenario 5: `stopTracking_persistsLapsAndFinalSummaryMetricsInSqlite()` (`TST-TRK-075.5`).

### Step 4: Verification Run
* **Command**: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.integration.*"`
* **Expected Result**: 5 passing integration tests in `< 30s`.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted integration test suite under `parallel_sprint_lock.py`, followed by governance check.
* **Rollback**: Branch isolation allows trivial discard via `git checkout -- .`.
