# Stage 3: Implementation Plan - ATT-4024: Close test coverage gaps and fix flaky test hygiene

**Ticket**: [ATT-4024](https://rainerblind.atlassian.net/browse/ATT-4024)  
**Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6`  
**Requirement Mapping**: `REQ-TST-001` (`REQ-TST-001.1`–`REQ-TST-001.5`)  
**Test Mapping**: `TST-TST-001` (`TST-TST-001.1`–`TST-TST-001.7`)  
**Branch**: `feature/ATT-4024`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background

Unit tests mutate global `Locale` and `TimeZone` without restoration, contaminating the Gradle JVM test daemon. Battery tests contain tautological assertions (`|| isNotEmpty()`). Over 24 production methods catch `Throwable` solely to tolerate unmocked JVM unit tests in `TrainingApplication`, `TrackerService`, `WorkoutClusterEngine`, and `AutoPauseManager`. `CSVFileWriter` and `DropboxUploader` lack unit test coverage.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TST-001` (*Deterministic JVM Test State Isolation, Production Catch Workaround Excision, Strict Assertions, and Exporter Coverage*).
* **Test Mapping**: `TST-TST-001` (`TST-TST-001.1`–`TST-TST-001.7` in `CSVFileWriterTest.kt`, `DropboxUploaderTest.kt`, formatter tests, and full test suite).

---

## 3. System Invariants & Preserved Behavior

1. **Deterministic JVM State**: Tests mutating `Locale.setDefault` or `TimeZone.setDefault` MUST restore baseline state in `@After`.
2. **Production Code Cleanliness**: Production classes MUST NOT swallow `Throwable` purely for test runners; test callers MUST mock dependencies.
3. **Strict Assertions**: Assertions must verify exact deterministic outputs (`assertEquals`), eliminating bare `assert` and `|| isNotEmpty()`.
4. **Zero Regressions**: 100% clean-room test pass rate across all 3,660+ unit tests without altering runtime production behavior.

---

## 4. Proposed Architectural Changes

### Component 1: Test Suite Lifecycle & JVM State Restoration (`REQ-TST-001.1`, `REQ-TST-001.2`)
* **State Teardown**: In `IntensityFactorFormatterTest.kt`, `BatteryRemainingTimeFormatterTest.kt`, `BatteryDeviceTest.kt`, `AdvancedTuningAccordionTest.kt`, and `PeriodsAggregationTest.kt`, store original `Locale`/`TimeZone` in `@Before` and restore in `@After`.
* **Strict Assertions**: Replace `|| isNotEmpty()` in battery tests with exact `assertEquals("Charging", ...)`.

### Component 2: Production Catch Workaround Excision & Test Mocking (`REQ-TST-001.3`)
* **Excision**: Remove test-only `catch (Throwable ignored)` blocks in `TrainingApplication.java` (`setManualPauseLock`, `setAutoPaused`), `WorkoutClusterEngine.kt` (`ensureStartingLocationAnchor`), `AutoPauseManager.kt` (`DefaultAutoPauseAudioNotifier`), and `TrackerService.java` (`performStopForeground`, `runOnMainThread`, `handleTrackingAborted`, `notifyTrackingStarted`, `sampleAndWriteToDb`).
* **Test Scaffolding**: Ensure test classes calling these methods mock `PreferenceManager`, `Log`, `KnownLocationsDatabaseManager`, `Handler`, and `Context` as required.

### Component 3: New Exporter Unit Test Suites (`REQ-TST-001.4`)
* **`CSVFileWriterTest.kt`**: Verify CSV header creation, column escaping, coordinate precision, sample rows, and stream flushing.
* **`DropboxUploaderTest.kt`**: Verify token retrieval, file upload dispatch, error/retry classification, and cancellation handling.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: Restore Global JVM State & Harden Assertions (`REQ-TST-001.1`, `REQ-TST-001.2`)**: Add `@Before`/`@After` `Locale` and `TimeZone` restoration across cited tests; replace weak `|| isNotEmpty()` checks with strict `assertEquals`.
* **Step 2: Excise Test-Only Catch Workarounds & Scaffold Mocks (`REQ-TST-001.3`)**: Remove artificial catch blocks in `TrainingApplication.java`, `WorkoutClusterEngine.kt`, `AutoPauseManager.kt`, and `TrackerService.java`; add required mocks in test suites.
* **Step 3: Implement `CSVFileWriterTest.kt` (`REQ-TST-001.4`)**: Author comprehensive unit tests covering CSV headers, data row mapping, and stream lifecycle.
* **Step 4: Implement `DropboxUploaderTest.kt` (`REQ-TST-001.4`)**: Author comprehensive unit tests covering OAuth token validation, upload execution, and error handling.
* **Step 5: Targeted Unit Test Verification (`TST-TST-001.1`–`TST-TST-001.6`)**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.writer.CSVFileWriterTest" --tests "com.atrainingtracker.trainingtracker.exporter.uploader.DropboxUploaderTest" --tests "com.atrainingtracker.banalservice.sensor.formater.*" --tests "com.atrainingtracker.banalservice.devices.BatteryDeviceTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit tests during Stage 4 construction; execute full `./gradlew testDebugUnitTest` and `TranslationParityTest` in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-4024` allows clean atomic discard via `git checkout -- .` if unexpected regressions arise.
