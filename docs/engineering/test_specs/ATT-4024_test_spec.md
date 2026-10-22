# Stage 2: Requirement & Test Specification - ATT-4024: Close test coverage gaps and fix flaky test hygiene

**Ticket**: [ATT-4024](https://rainerblind.atlassian.net/browse/ATT-4024)  
**Sub-task**: [ATT-4618](https://rainerblind.atlassian.net/browse/ATT-4618) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4024`  
**Requirement Mapping**: `REQ-TST-001` (*Deterministic JVM Test State Isolation, Production Catch Workaround Excision, Strict Assertions, and Exporter Coverage*)  
**Test Spec ID**: `TST-TST-001`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-TST-001`)

### 1.1 Problem Statement & Rationale
JVM test executions mutate global `Locale.setDefault` and `TimeZone.setDefault` without `@After` restoration, contaminating subsequent tests. Battery tests rely on Kotlin bare `assert` and tautological `|| isNotEmpty()` conditions, masking failures. Over 24 production methods swallow `Throwable` purely to tolerate unmocked Android dependencies in unit tests. Critical exporters `CSVFileWriter` and `DropboxUploader` possess zero unit tests.

### 1.2 Functional & Architectural Requirements
1. **JVM Global State Isolation (`REQ-TST-001.1`)**: Unit tests modifying `Locale.setDefault` or `TimeZone.setDefault` (`IntensityFactorFormatterTest`, `BatteryRemainingTimeFormatterTest`, `BatteryDeviceTest`, `AdvancedTuningAccordionTest`, `PeriodsAggregationTest`) SHALL capture baseline state and restore it in `@After`.
2. **Strict Deterministic Assertions (`REQ-TST-001.2`)**: Replace bare `assert` and tautological `|| isNotEmpty()` checks in `BatteryRemainingTimeFormatterTest` and `BatteryDeviceTest` with explicit `assertEquals("Charging", ...)`.
3. **Production Catch Workaround Excision (`REQ-TST-001.3`)**: Excise artificial `catch (Throwable)` blocks in `TrainingApplication.java`, `TrackerService.java`, `WorkoutClusterEngine.kt`, and `AutoPauseManager.kt`; unit test callers SHALL supply proper MockK / Robolectric mocks.
4. **Exporter Unit Test Coverage (`REQ-TST-001.4`)**: Implement unit test suites `CSVFileWriterTest.kt` and `DropboxUploaderTest.kt` verifying header formatting, sample serialization, decimal precision, token validation, and error/cancellation handling.
5. **System Invariants (`REQ-TST-001.5`)**: Zero alteration to production runtime behavior, schemas, or calculation formulas; 100% clean-room test pass rate under `./gradlew testDebugUnitTest`.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TST-001` under Epic `ATT-2466` (*Developer Testing Tools*), upholding `docs/coding_standards.md` §5.1.
* **Historical Origin & Commit Trace**: Independent whole-app code review (`0234bfc0`, `feature/ATT-3921`) and follow-ups in `ATT-3980`, `ATT-3984`, and `ATT-4006`.
* **Root Reason for Existing Formulation**: Developers inserted inline `catch (Throwable ignored)` as quick fixes when unit tests ran against naked Android classes without `Log`, `Context`, or `Handler` mocks. `Locale.setDefault` was added without `@After` cleanup.
* **Preservation of Core Invariants**: 100% clean-room test pass rate, deterministic execution across all host locales/timezones, and zero alteration of runtime production behavior.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Deterministic JVM State)**: *Given* unit tests mutating Locale/TimeZone, *When* executed in any order, *Then* global state is fully restored and no cross-test pollution occurs.
* **Criterion 2 (Strict Assertions)**: *Given* battery formatters and device models, *When* tested, *Then* assertions strictly verify deterministic expected values via `assertEquals`.
* **Criterion 3 (Excision of Workarounds)**: *Given* production methods with test-only catch blocks, *When* refactored, *Then* catch blocks are excised and callers provide proper mocks.
* **Criterion 4 (Exporter Coverage)**: *Given* `CSVFileWriter` and `DropboxUploader`, *When* unit tested, *Then* full coverage is achieved for formatting, upload contracts, and error paths.

### 1.5 System Invariants
Zero test contamination; no production catches purely for unmocked test runners; 100% clean-room test pass rate; low-risk test isolation.

---

## 2. Test Specification (`TST-TST-001`)

### Test Cases
* `TST-TST-001.1` (**Locale / TimeZone Teardown**): Unit tests in `IntensityFactorFormatterTest`, `BatteryRemainingTimeFormatterTest`, `BatteryDeviceTest`, `AdvancedTuningAccordionTest`, and `PeriodsAggregationTest` restore baseline Locale/TimeZone in `@After`.
* `TST-TST-001.2` (**Strict Battery Assertions**): `BatteryRemainingTimeFormatterTest` and `BatteryDeviceTest` strictly assert `assertEquals("Charging", ...)` without tautological `|| isNotEmpty()`.
* `TST-TST-001.3` (**CSVFileWriter Coverage**): `CSVFileWriterTest.kt` verifies header format, sample comma-separation, coordinate precision, and stream flush.
* `TST-TST-001.4` (**DropboxUploader Coverage**): `DropboxUploaderTest.kt` verifies token handling, upload execution, error classification, and cancellation.
* `TST-TST-001.5` (**Production Catch Excision & Mocks**): Unit tests calling `TrainingApplication`, `TrackerService`, `WorkoutClusterEngine`, and `AutoPauseManager` mock dependencies with catch workarounds excised.
* `TST-TST-001.6` (**Localization Parity**): `TranslationParityTest` verifies 100% parity across all 9 locales (0 new strings).
* `TST-TST-001.7` (**Clean-Room Regression**): `./gradlew testDebugUnitTest` achieves 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TST-001.1` | Unit | `IntensityFactorFormatterTest`, `BatteryRemainingTimeFormatterTest`, etc. | `REQ-TST-001.1` | In Progress (ATT-4024) |
| `TST-TST-001.2` | Unit | `BatteryRemainingTimeFormatterTest`, `BatteryDeviceTest` | `REQ-TST-001.2` | In Progress (ATT-4024) |
| `TST-TST-001.3` | Unit | `CSVFileWriterTest.kt` (`CSVFileWriter.java`) | `REQ-TST-001.4` | In Progress (ATT-4024) |
| `TST-TST-001.4` | Unit | `DropboxUploaderTest.kt` (`DropboxUploader.java`) | `REQ-TST-001.4` | In Progress (ATT-4024) |
| `TST-TST-001.5` | Unit | `TrainingApplication`, `TrackerService`, `WorkoutClusterEngine` | `REQ-TST-001.3` | In Progress (ATT-4024) |
| `TST-TST-001.6` | Localization | `TranslationParityTest` | `REQ-TST-001`, `REQ-UI-106` | In Progress (ATT-4024) |
| `TST-TST-001.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-TST-001.5`, `REQ-PRO-001` | In Progress (ATT-4024) |
