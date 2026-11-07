# Stage 2: Requirement & Test Specification - ATT-5827: Eliminate 1s ScheduledThreadPoolExecutor timing race in TrackerServiceResumeTest

**Ticket**: [ATT-5827](https://rainerblind.atlassian.net/browse/ATT-5827)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-TRK-026` (*Resumed Workout Sample Table Resolution & Asynchronous BANAL Binding Barrier*)  
**Test Spec ID**: `TST-TRK-018`  
**Branch**: `feature/ATT-5827`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-TRK-026)

### 1.1 Problem Statement & Rationale
`TrackerService.onStartCommand()` schedules a periodic 1 Hz sampler (`mTrackerHandle = mScheduler.scheduleAtFixedRate(tracker, 0, 1, TimeUnit.SECONDS)`). In `TrackerServiceResumeTest.kt`, when `BANALService` connects asynchronously, `mBanalService` is populated. Under runner load, the background scheduler executes `sampleAndWriteToDb()` concurrently with the test's manual reflection call, causing sporadic duplicate transaction calls on `mockSamplesDb.setTransactionSuccessful()` (`2 matching calls found, exactly 1 expected`).

### 1.2 Functional & Architectural Requirements
1. `TrackerService` SHALL encapsulate `mTrackerHandle` cancellation into a `@VisibleForTesting void cancelTrackerHandle()` method to eliminate inline duplication across teardown and test isolation paths.
2. Unit tests simulating deferred BANAL binding in `TrackerServiceResumeTest.kt` SHALL invoke `service.cancelTrackerHandle()` immediately following `onStartCommand()` to deterministically stop background sampling ticks.
3. Transaction verification in `TrackerServiceResumeTest.kt` SHALL maintain strict `verify(exactly = 1)` assertions on both `mockSamplesDb` and `mockDb`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Deterministic Single Transaction)**:
  * *Given* `TrackerServiceResumeTest` executing `testDeferredResumeServiceRecreation_...`,
  * *When* `onStartCommand()` is followed by `cancelTrackerHandle()`, `onServiceConnected()`, and manual `sampleAndWriteToDb()`,
  * *Then* `mockSamplesDb.setTransactionSuccessful()` and `mockDb.setTransactionSuccessful()` are invoked exactly once under all load conditions.
* **Criterion 2 (Thread Leak Prevention)**:
  * *Given* `testDeferredResumeByUser_...` executing `onStartCommand()`,
  * *When* `cancelTrackerHandle()` is invoked before `onServiceConnected()`,
  * *Then* background scheduler ticks are halted with zero thread leakage.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-026.3` / `TST-TRK-018` (Vector 2a/2b)
* **Historical Origin & Commit Trace**: `ATT-3980` (`docs/requirements/req_tracking_cockpit.md:1282`)
* **Root Reason for Existing Formulation**: `onStartCommand()` was called to simulate deferred service startup; under fast runs ($<1\text{ s}$), background ticks did not fire before assertion.
* **Preservation of Core Invariants**: Production 1 Hz sampling during active tracking and scheduler lifecycle in `TrackerService` remain 100% untouched.

---

## 2. Test Specification (TST-TRK-018)

### Test Case 1: `testDeferredResumeServiceRecreation_whenBanalServiceConnectsLater_restoresTableNameAndWritesSamplesToResumedTable` (`TST-TRK-018.2a`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`)
* **Action**: Cancel `mTrackerHandle` post-`onStartCommand()`, connect `BANALService`, call `sampleAndWriteToDb()`.
* **Expected Result**: Table restored to `"\"2026-06-15_120000\""`; `verify(exactly = 1)` passes deterministically.

### Test Case 2: `testDeferredResumeByUser_whenBanalServiceConnectsLater_recreatesValuesBeforeCreateNewLapAndSearchingFinished` (`TST-TRK-018.2b`)
* **Scope**: Unit Test (`TrackerServiceResumeTest.kt`)
* **Action**: Cancel `mTrackerHandle` post-`onStartCommand()`, connect `BANALService`.
* **Expected Result**: Event order verified, zero background scheduler thread leakage.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-TRK-018.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*"`
* **Expected Result**: 100% pass across all `TrackerService` unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-018.2a` | Unit | `TrackerService.cancelTrackerHandle()` / `sampleAndWriteToDb()` | `REQ-TRK-026.3` | Specified |
| `TST-TRK-018.2b` | Unit | `TrackerService.cancelTrackerHandle()` / `onServiceConnected()` | `REQ-TRK-026.3` | Specified |
| `TST-TRK-018.3` | Regression | Full `com.atrainingtracker.trainingtracker.tracker.*` package | `REQ-PRO-001` | Specified |
