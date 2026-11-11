# Stage 2: Requirement & Test Specification - ATT-6569: Phase out source-text-reading tests in banalservice.ui and migration packages

**Ticket**: [ATT-6569](https://rainerblind.atlassian.net/browse/ATT-6569)  
**Sub-task**: `[Req & Test Spec]` [ATT-6971](https://rainerblind.atlassian.net/browse/ATT-6971)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`)  
**Test Spec ID**: `TST-PRO-049` (`TST-PRO-049.1`, `TST-PRO-049.3`, `TST-PRO-049.5`)  
**Branch**: `feature/ATT-6569`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-049)

### 1.1 Problem Statement & Rationale
Under ASPICE finding C-12 and `docs/coding_standards.md` §5.4, unit tests must not read production source code (`File.readText()`) to grep for tokens or formatting. 24 legacy tests across `banalservice.ui` (14) and `migration` (10) currently read `src/main` files, creating fragile tests that verify zero runtime behavior. These 24 tests must be phased out, converted to behavioral/reflection assertions and `LocalizationTestCache`, and ratcheted out of `app/source-reading-baseline.txt`.

### 1.2 Functional & Architectural Requirements
1. All 14 legacy source-reading tests in `banalservice.ui` SHALL be refactored to eliminate all `readText()` and direct `src/main` reads (`REQ-PRO-049.1`):
   - XML string checks SHALL use `LocalizationTestCache.getStrings(locale)`.
   - Structural and parameter checks SHALL use programmatic reflection (`Class.forName`, `declaredMethods`, `declaredFields`) or Robolectric/unit state models.
2. All 10 legacy source-reading tests in `migration` SHALL be refactored to eliminate all `readText()` and direct `src/main` reads (`REQ-PRO-049.1`).
3. `app/source-reading-baseline.txt` SHALL remove all 24 refactored test paths, ratcheting legacy debt ceiling from 275 down to 251 (`REQ-PRO-049.3`).
4. `SourceTextReadingAuditContractTest.kt` SHALL update its ratcheting assertion to `baseline.size <= 251` and `assertEquals(251, baseline.size)` (`REQ-PRO-049.3`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), refining `REQ-PRO-034`. Targets `SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`, and 24 unit test classes across `banalservice.ui` and `migration`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding C-12 (2026-11-08), ticket ATT-6186 (`c91e62b6`), and ATT-6731 (`88ce1d51`).
* **Root Reason for Existing Formulation**: Tests were initially written to verify that UI components had certain parameters or strings by reading source text. This violated §5.4 and generated maintenance churn.
* **Preservation of Core Invariants**: 100% of functional assertions (9-language localization parity, parameter validation, permission simulation, backup resilience) are preserved or strengthened. Legitimate governance guards remain protected in `LEGITIMATE_GOVERNANCE_GUARDS`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* the 24 target test files in `banalservice.ui` and `migration`,
* *When* scanned by `SourceTextReadingAuditContractTest`,
* *Then* zero tests SHALL invoke `readText()`, `readLines()`, `readString()`, `useLines()`, or `bufferedReader()` targeting `src/main`.
* *Given* `app/source-reading-baseline.txt`,
* *When* evaluated by `SourceTextReadingAuditContractTest`,
* *Then* `baseline.size` SHALL be exactly 251, and `(baseline - detectedReaders).isEmpty()` SHALL pass.
* *Given* `SourceTextReadingAuditContractTest.kt` and all 24 refactored tests,
* *When* executed via `./gradlew testDebugUnitTest`,
* *Then* all tests SHALL pass with 100% success.

---

## 2. Test Specification (TST-PRO-049)

### Test Case 1: Legacy Baseline Downward Ratchet Test (`TST-PRO-049.3`)
* **Scope**: Contract Test (`SourceTextReadingAuditContractTest.testBaseline_containsOnlyExpectedLegacyCount`)
* **Assertion**: Verifies `baseline.size <= 251` and `assertEquals(251, baseline.size)` with zero stale baseline entries (`staleBaselineEntries.isEmpty()`).

### Test Case 2: Source Text Reading Prohibition Scan (`TST-PRO-049.1`)
* **Scope**: Contract Test (`SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders`)
* **Assertion**: Confirms no unallow-listed test reads production source code targeting `app/src/main/`.

### Test Case 3: Targeted Behavioral Test Suite Execution (`TST-PRO-049.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* **Expected Result**: 100% pass rate across all 24 refactored tests and the audit contract test.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-049.1` | Audit | `testSourceReadingAudit_noUnallowlistedSourceReaders` | `REQ-PRO-049.1` | Specified |
| `TST-PRO-049.2` | Governance | `testGovernanceGuards_allExplicitlyAllowed` | `REQ-PRO-049.2` | Specified |
| `TST-PRO-049.3` | Ratchet | `testBaseline_containsOnlyExpectedLegacyCount` | `REQ-PRO-049.3` | Specified |
| `TST-PRO-049.4` | Tripwire | `testRatchetingTripwire_detectsUnallowlistedReader` | `REQ-PRO-049.3` | Specified |
| `TST-PRO-049.5` | Targeted Regression | 24 refactored test suites in `banalservice.ui` & `migration` | `REQ-PRO-049.4` | Specified |
