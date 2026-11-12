# Stage 2: Requirement & Test Specification - ATT-6564: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.map

**Ticket**: [ATT-6564](https://rainerblind.atlassian.net/browse/ATT-6564)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-TST-007` (`REQ-TST-007.1`..`REQ-TST-007.3`), refining `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`) & `REQ-TST-002`  
**Test Spec ID**: `TST-TST-007` (`TST-TST-007.1`..`TST-TST-007.3`), validating `TST-PRO-049`  
**Branch**: `feature/ATT-6564`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-TST-007 / REQ-PRO-049)

### 1.1 Problem Statement & Rationale
Under ASPICE pre-assessment finding C-12 and `docs/coding_standards.md` §5.4, unit tests must not read production source code files (`File.readText()`) to assert on Compose tokens, internal parameters, or string literals. An exhaustive audit identified exactly 45 legacy test classes in `com.atrainingtracker.trainingtracker.ui.map` (3 `DarkMap*`, 11 `ElevationProfile*`, 9 `MapDetailLayout*`, 8 `TelemetryMetricGraph*`, and 14 Map & Sheet component tests) reading `src/main` files. All 45 tests must be phased out, converted to behavioral assertions, JVM reflection, and `LocalizationTestCache`, and ratcheted out of `app/source-reading-baseline.txt`.

### 1.2 Functional & Architectural Requirements
1. **Source-Text Reading Elimination (`REQ-TST-007.1`)**: All 45 legacy source-reading tests in `com.atrainingtracker.trainingtracker.ui.map` SHALL be refactored to eliminate all `readText()`, `readLines()`, `useLines()`, and direct `src/main` file reads:
   - XML string checks SHALL use `LocalizationTestCache.getStrings(locale)` across all 9 supported application locales.
   - Component, composable, and parameter presence checks SHALL use JVM reflection (`Class.forName`, `declaredMethods`, `parameterTypes`) rather than raw source regex grep.
   - Map detail layout slots, dynamic viewports, elevation profile geometry, scrub synchronization, and gestures SHALL use semantic state models and behavioral assertions.
2. **Baseline Ratchet & Ceiling Enforcement (`REQ-TST-007.2`)**: `app/source-reading-baseline.txt` SHALL remove all 45 refactored test paths, ratcheting legacy debt ceiling down from 139 to 94 (`baseline.size <= 94`).
3. **Audit Contract Tripwire (`REQ-TST-007.3`)**: `SourceTextReadingAuditContractTest.kt` SHALL update its ratcheting assertion to `baseline.size <= 94` and `assertEquals(94, baseline.size)` with `staleBaselineEntries.isEmpty()`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TST-007` (`REQ-TST-007.1`–`REQ-TST-007.3`) refining `REQ-PRO-049` and `REQ-TST-002`. Targets `SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`, and 45 test classes in `ui.map`.
* **Historical Origin & Commit Trace**: ASPICE pre-assessment finding C-12; initial ratchet in ATT-6186 (`c91e62b6`), baseline stabilization in ATT-6731 (`88ce1d51`), prior batches in ATT-6569 (`bed1157d`), ATT-6568 (`17300170`), ATT-6567 (`3b5a5b41`), ATT-6566 (`ef50449a`), and ATT-6565 (`1298a643`).
* **Root Reason for Existing Formulation**: Tests grepped `.kt` files to assert map layout slots, padding, and UI tokens. This froze non-functional formatting and violated §5.4.
* **Preservation of Core Invariants**: 100% of functional assertions (9-language string parity, map camera behaviors, elevation geometry, metric graphs) are preserved or strengthened. Legitimate governance guards remain protected in `LEGITIMATE_GOVERNANCE_GUARDS`.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* the 45 target test files in `com.atrainingtracker.trainingtracker.ui.map`,
* *When* scanned by `SourceTextReadingAuditContractTest`,
* *Then* zero tests SHALL invoke `readText()`, `readLines()`, `readString()`, `useLines()`, or `bufferedReader()` targeting `src/main`.
* *Given* `app/source-reading-baseline.txt`,
* *When* evaluated by `SourceTextReadingAuditContractTest`,
* *Then* `baseline.size` SHALL be exactly 94, and `(baseline - detectedReaders).isEmpty()` SHALL pass.
* *Given* `SourceTextReadingAuditContractTest.kt` and all 45 refactored test classes in `ui.map`,
* *When* executed via `./gradlew testDebugUnitTest`,
* *Then* all tests SHALL pass with 100% success.

---

## 2. Test Specification (TST-TST-007 / TST-PRO-049)

### Test Case 1: Legacy Baseline Downward Ratchet Test (`TST-TST-007.1`)
* **Scope**: Contract Test (`SourceTextReadingAuditContractTest.testBaseline_containsOnlyExpectedLegacyCount`)
* **Assertion**: Verifies `baseline.size <= 94` and `assertEquals(94, baseline.size)` with zero stale baseline entries (`staleBaselineEntries.isEmpty()`).

### Test Case 2: Source Text Reading Prohibition Scan (`TST-TST-007.2`)
* **Scope**: Contract Test (`SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders`)
* **Assertion**: Confirms no unallow-listed test reads production source code targeting `app/src/main/`.

### Test Case 3: Targeted Behavioral Test Suite Execution (`TST-TST-007.3`)
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* **Expected Result**: 100% pass rate across all 45 refactored tests and the audit contract test.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TST-007.1` | Ratchet | `testBaseline_containsOnlyExpectedLegacyCount` | `REQ-TST-007.2`, `REQ-PRO-049.3` | Specified |
| `TST-TST-007.2` | Audit | `testSourceReadingAudit_noUnallowlistedSourceReaders` | `REQ-TST-007.1`, `REQ-PRO-049.1` | Specified |
| `TST-TST-007.3` | Unit Suite | `com.atrainingtracker.trainingtracker.ui.map.*` | `REQ-TST-007.1`, `REQ-PRO-049.4` | Specified |
