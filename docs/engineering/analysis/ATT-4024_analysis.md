# Stage 1 Analysis: ATT-4024 - Close test coverage gaps and fix flaky test hygiene

**Ticket**: [ATT-4024](https://rainerblind.atlassian.net/browse/ATT-4024)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4024`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-22  

---

## 1. Problem Domain & Root Cause Analysis

Forensic audit of review findings confirms test hygiene deficiencies and specific coverage gaps, while clarifying already-resolved components:
1. **Global JVM State Leaks (Flaky Test Hygiene)**: `Locale.setDefault(Locale.US)` is invoked without an `@After` restore in `IntensityFactorFormatterTest.kt:32`, `BatteryRemainingTimeFormatterTest.kt:52`, `BatteryDeviceTest.kt:53`, and `AdvancedTuningAccordionTest.kt:371`, leaking mutated locale into subsequent test workers. `PeriodsAggregationTest.kt:42` and period cache tests rely on unpinned `ZoneId.systemDefault()`, creating timezone-dependent epoch discrepancies.
2. **Weak / Always-True Assertions**: `BatteryRemainingTimeFormatterTest.kt:47` and `BatteryDeviceTest.kt:124` assert `result == "Charging" || result.isNotEmpty()`, which unconditionally passes for any non-empty string (masking regressions), and relies on Kotlin's unasserted bare `assert`.
3. **Test-Only Catch Workarounds in Production Code**: 24+ catch blocks swallow `Throwable` purely for unmocked JVM tests rather than handling real runtime failures: `TrainingApplication.java:1266,1292` (`setManualPauseLock`, `setAutoPaused`), `WorkoutClusterEngine.kt:281` (`ensureStartingLocationAnchor`), `AutoPauseManager.kt:141,157` (unmocked `Log.w`), and `TrackerService.java:938,1045,1065,1081,1144,2289` (`performStopForeground`, `runOnMainThread`, `handleTrackingAborted`, `notifyTrackingStarted`, `sampleAndWriteToDb`).
4. **Coverage Audit Resolution**: `GPXFileWriter` (`GPXFileWriterTest.kt`), `GCFileWriter` (`GCFileWriterTest.kt`), `ExportWorker` (`ExportWorkerTest.kt`), `ImportEngine`, and `MigrationEngine` already possess dedicated unit test suites from prior tickets. True coverage gaps exist in `CSVFileWriter.java` (0 tests) and `DropboxUploader.java` (0 tests).

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Defect & Planned Remediation |
| :--- | :--- | :--- |
| **Locale / TimeZone Hygiene** | `IntensityFactorFormatterTest.kt`, `BatteryRemainingTimeFormatterTest.kt`, `BatteryDeviceTest.kt`, `AdvancedTuningAccordionTest.kt`, `PeriodsAggregationTest.kt` | Pin `Locale` / `TimeZone` with guaranteed restore in `@After` or test lifecycle rules. |
| **Assertion Hardening** | `BatteryRemainingTimeFormatterTest.kt`, `BatteryDeviceTest.kt` | Replace weak `|| isNotEmpty()` assertions with strict deterministic `assertEquals("Charging", ...)`. |
| **Exporter Test Coverage** | `CSVFileWriter.java`, `DropboxUploader.java` | Add unit test suites (`CSVFileWriterTest.kt`, `DropboxUploaderTest.kt`) covering formatting and upload contracts. |
| **Production Test Workarounds** | `TrainingApplication.java`, `WorkoutClusterEngine.kt`, `AutoPauseManager.kt`, `TrackerService.java` | Provide proper MockK / Robolectric scaffolding in test fixtures, allowing removal of artificial test-only catch blocks in production. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Enforce strict `Locale` and `TimeZone` restoration across all cited unit tests to prevent JVM state contamination.
  2. Replace weak assertions (`|| isNotEmpty()`) with strict `assertEquals` checks in battery test suites.
  3. Create comprehensive unit tests for untested exporters: `CSVFileWriterTest.kt` and `DropboxUploaderTest.kt`.
  4. Equip unit tests calling `TrainingApplication`, `TrackerService`, `WorkoutClusterEngine`, and `AutoPauseManager` with proper MockK/Robolectric mocks to excise test-only catch workarounds from production code.
* **Out-of-Scope Non-Goals**:
  1. No rewriting of already well-tested exporters/importers (`GPXFileWriter`, `GCFileWriter`, `ExportWorker`, `ImportEngine`, `MigrationEngine`).
  2. No refactoring or simulation harness creation for legacy ANT+ hardware drivers or Pebble smartwatch subsystems.
  3. No changes to database schemas, calculation formulas, or user-facing presentation strings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Net-new requirement `REQ-TST-001` (*Deterministic JVM Test State Isolation, Production Catch Workaround Excision, and Exporter Test Coverage*) under Epic `ATT-2466` (*Developer Testing Tools*), upholding `docs/coding_standards.md` §5.1.
* **Historical Origin & Commit Trace**: Independent whole-app code review (`0234bfc0`, `feature/ATT-3921`) and follow-ups in `ATT-3980`, `ATT-3984`, and `ATT-4006`.
* **Root Reason for Existing Formulation**: Developers inserted inline `catch (Throwable ignored)` in production methods as quick fixes when unit tests ran against naked Android classes without `Log`, `Context`, or `Handler` mocks. `Locale.setDefault` was added to formatters without `@After` cleanup.
* **Preservation of Core Invariants**: 100% clean-room test pass rate across 3,660+ unit tests, deterministic execution in any locale/timezone, and zero alteration of runtime production behavior.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero test contamination: tests modifying `Locale.setDefault` or `TimeZone.setDefault` MUST restore baseline state in `@After`.
  2. Production code MUST NOT catch `Throwable` purely to tolerate unmocked test runners; test callers must mock dependencies.
  3. Full backward compatibility and zero regressions across all file exporters and tracking services.
* **Risk Rating**: **LOW**
  - Justification: Scoped strictly to unit test hygiene, assertion tightening, new exporter test suites, and excising test-only catch workarounds backed by proper mocking. Zero risk to production schemas or user-facing features.
