# Stage 2: Requirement & Test Specification - ATT-6566: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.aftermath

**Ticket**: [ATT-6566](https://rainerblind.atlassian.net/browse/ATT-6566)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`)  
**Test Spec ID**: `TST-PRO-049` (`TST-PRO-049.1`, `TST-PRO-049.3`, `TST-PRO-049.5`)  
**Branch**: `feature/ATT-6566`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-049)

### 1.1 Problem Statement & Rationale
Under ASPICE pre-assessment finding C-12 and `docs/coding_standards.md` §5.4, unit tests must not read production source code files (`File.readText()`) to assert on Compose tokens, internal parameters, or string literals. An exhaustive audit identified exactly 30 legacy test suites in `com.atrainingtracker.trainingtracker.ui.aftermath` (10 in root, 1 in `crop`, 2 in `editworkout`, 2 in `header`, 1 in `periodlist`, 2 in `photos`, 1 in `splits`, 6 in `workoutlist`, 5 in `zones`) reading `src/main` files, creating brittle test suites that verify zero runtime behavior and fail on innocent formatting changes. All 30 tests must be phased out, converted to behavioral assertions, JVM reflection, and `LocalizationTestCache`, and ratcheted out of `app/source-reading-baseline.txt`.

### 1.2 Functional & Architectural Requirements
1. All 30 legacy source-reading tests in `com.atrainingtracker.trainingtracker.ui.aftermath` SHALL be refactored to eliminate all `readText()`, `readLines()`, `useLines()`, and direct `src/main` file reads (`REQ-PRO-049.1`):
   - XML string checks SHALL use `LocalizationTestCache.getStrings(locale)` across all 9 supported application locales.
   - Component, composable, and parameter presence checks SHALL use JVM reflection (`Class.forName`, `declaredMethods`, `parameterTypes`) rather than raw source regex grep.
   - Repository operations, enums, formatters, and mathematical aggregations SHALL use direct behavioral assertions (`WorkoutRepositoryCropTest`, `WorkoutRepositoryDeleteTest`, `LapSplitVisualizerTest`, `GradeZoneDistributionCardTest`).
2. `app/source-reading-baseline.txt` SHALL remove all 30 refactored test paths, ratcheting legacy debt ceiling down from 211 to 181 (`REQ-PRO-049.3`).
3. `SourceTextReadingAuditContractTest.kt` SHALL update its ratcheting assertion to `baseline.size <= 181` and `assertEquals(181, baseline.size)` (`REQ-PRO-049.3`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), refining `REQ-PRO-034`. Targets `SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`, and 30 unit test classes in `com.atrainingtracker.trainingtracker.ui.aftermath`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding C-12 (2026-11-08), ticket ATT-6186 (`c91e62b6`), ATT-6731 (`88ce1d51`), ATT-6569 (`bed1157d`), ATT-6568 (`17300170`), and ATT-6567 (`3b5a5b41`).
* **Root Reason for Existing Formulation**: Tests were initially authored to verify UI parameters and tokens by grepping `.kt` source text. This violated §5.4 and generated maintenance debt.
* **Preservation of Core Invariants**: 100% of functional assertions (9-language localization parity, workout crop/delete contracts, tab ordering, telemetry histogram rendering, and zone distribution logic) are preserved or strengthened. Legitimate governance guards remain protected in `LEGITIMATE_GOVERNANCE_GUARDS`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* the 30 target test files in `com.atrainingtracker.trainingtracker.ui.aftermath`,
* *When* scanned by `SourceTextReadingAuditContractTest`,
* *Then* zero tests SHALL invoke `readText()`, `readLines()`, `readString()`, `useLines()`, or `bufferedReader()` targeting `src/main`.
* *Given* `app/source-reading-baseline.txt`,
* *When* evaluated by `SourceTextReadingAuditContractTest`,
* *Then* `baseline.size` SHALL be exactly 181, and `(baseline - detectedReaders).isEmpty()` SHALL pass.
* *Given* `SourceTextReadingAuditContractTest.kt` and all 30 refactored test classes in `ui.aftermath`,
* *When* executed via `./gradlew testDebugUnitTest`,
* *Then* all tests SHALL pass with 100% success.

---

## 2. Test Specification (TST-PRO-049)

### Test Case 1: Legacy Baseline Downward Ratchet Test (`TST-PRO-049.3`)
* **Scope**: Contract Test (`SourceTextReadingAuditContractTest.testBaseline_containsOnlyExpectedLegacyCount`)
* **Assertion**: Verifies `baseline.size <= 181` and `assertEquals(181, baseline.size)` with zero stale baseline entries (`staleBaselineEntries.isEmpty()`).

### Test Case 2: Source Text Reading Prohibition Scan (`TST-PRO-049.1`)
* **Scope**: Contract Test (`SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders`)
* **Assertion**: Confirms no unallow-listed test reads production source code targeting `app/src/main/`.

### Test Case 3: Targeted Behavioral Test Suite Execution (`TST-PRO-049.5`)
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* **Expected Result**: 100% pass rate across all 30 refactored tests and the audit contract test.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-049.1` | Audit | `testSourceReadingAudit_noUnallowlistedSourceReaders` | `REQ-PRO-049.1` | Specified |
| `TST-PRO-049.2` | Governance | `testGovernanceGuards_allExplicitlyAllowed` | `REQ-PRO-049.2` | Specified |
| `TST-PRO-049.3` | Ratchet | `testBaseline_containsOnlyExpectedLegacyCount` | `REQ-PRO-049.3` | Specified |
| `TST-PRO-049.4` | Tripwire | `testRatchetingTripwire_detectsUnallowlistedReader` | `REQ-PRO-049.3` | Specified |
| `TST-PRO-049.5` | Unit Suite | `com.atrainingtracker.trainingtracker.ui.aftermath.*` | `REQ-PRO-049.1` | Specified |
