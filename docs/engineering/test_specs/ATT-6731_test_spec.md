# Stage 2: Requirement & Test Specification - ATT-6731: Move TranslationParityTest and repo governance guards from source-reading baseline to allow-list and ratchet ceiling

**Ticket**: [ATT-6731](https://rainerblind.atlassian.net/browse/ATT-6731)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (*Source-Text Reading Test Prohibition & Audit Governance*)  
**Test Spec ID**: `TST-PRO-049` (*Source-Text Reading Test Audit Suite*)  
**Branch**: `feature/ATT-6731`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-049)

### 1.1 Problem Statement & Rationale
During Sprint 2026-46.1 review of ATT-6186, essential repository-wide governance guards (e.g., `TranslationParityTest`, `LocalizationTestCache`, `ThemeColorCentralizationAuditTest`, `RepositoryEngineCleanlinessContractTest`, `RepositoryUiCleanlinessContractTest`, `GlobalDeleteContextMenuAuditTest`, `EditIconConsistencyTest`, `AspiceSweAuditContractTest`) were misclassified into the legacy source-reading phase-out debt baseline (`app/source-reading-baseline.txt`). Furthermore, exact size equality (`assertEquals(280, baseline.size)`) lacked stale-entry tripwires (`(baseline - detectedReaders).isEmpty()`), and reader detection missed stream calls (`useLines`, `bufferedReader`).

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-049.1** (*Scanner Expansion*): `SourceTextReadingAuditContractTest` SHALL scan `app/src/test/java/` detecting `readText`, `readLines`, `readString`, `useLines`, and `bufferedReader` targeting `app/src/main/`.
* **REQ-PRO-049.2** (*Governance Guard Allow-List*): The audit test SHALL maintain an explicit allow-list (`LEGITIMATE_GOVERNANCE_GUARDS`) of exactly 22 legitimate system policy guards, verifying each file ends with `Test.kt` or `Cache.kt` and exists on disk.
* **REQ-PRO-049.3** (*Ratcheted Ceiling & Stale Detection*): The audit test SHALL enforce `(baseline - detectedReaders).isEmpty()` (zero stale entries) and `baseline.size <= 276`, permanently protecting promoted guards and ratcheting legacy debt downward.
* **REQ-PRO-049.4** (*Inventory & Batch Sizing*): Maintain structured baseline inventory in living documentation and record remaining inventory for ATT-6569 (118 entries) recommending `ui.components` (17 entries) split per `REQ-PRO-029`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Promote Governance Guards)**:
  * *Given* `app/source-reading-baseline.txt` and `LEGITIMATE_GOVERNANCE_GUARDS`,
  * *When* audited per `docs/coding_standards.md` §5.4,
  * *Then* 8 repository-wide policy scanners are registered in `LEGITIMATE_GOVERNANCE_GUARDS` (total 22) and removed from the phase-out baseline.
* **Criterion 2 (Ratcheted Baseline & Stale Detection)**:
  * *Given* detected test readers and `app/source-reading-baseline.txt`,
  * *When* `testBaseline_containsOnlyExpectedLegacyCount()` runs,
  * *Then* `(baseline - detectedReaders).isEmpty()` SHALL hold and `baseline.size <= 276`.
* **Criterion 3 (Stream Scanner Coverage)**:
  * *Given* tests invoking `useLines` or `bufferedReader` targeting `src/main`,
  * *When* `scanTestSourceFiles()` executes,
  * *Then* they SHALL be detected and verified against `LEGITIMATE_GOVERNANCE_GUARDS + baseline`.

### 1.4 System Invariants
Zero runtime code changes; zero regression in existing test suite; monotonic ratcheting downward of baseline legacy debt.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), refining `REQ-PRO-034`. Targets `SourceTextReadingAuditContractTest.kt` and `app/source-reading-baseline.txt`.
2. *Historical Origin & Commit Trace*: Independent ASPICE pre-assessment finding C-12 (2026-11-08), ticket ATT-6186 (`c91e62b6`), and refined in ATT-6731.
3. *Root Reason for Existing Formulation*: Baseline originally captured all tests accessing `src/main` without discriminating single-file UI grep tests from repository-wide architectural scanners, setting an exact count (280) before ratcheting mechanisms were standardized.
4. *Preservation of Core Invariants*: Promoting governance guards guarantees that essential system invariants (Rule 34, 9-locale parity, database decoupling, theme centralization) are permanently preserved and never deleted by downstream cleanup tickets. Enforcing `(baseline - detectedReaders).isEmpty()` prevents stale debt accumulation.

---

## 2. Test Specification (TST-PRO-049)

### Test Case 1: `testSourceReadingAudit_noUnallowlistedSourceReaders` (`TST-PRO-049.1`)
* **Scope**: Architectural Audit Unit Test (`SourceTextReadingAuditContractTest.kt`)
* **Preconditions**: Expanded scanner checking `readText`, `readLines`, `readString`, `useLines`, `bufferedReader` against `src/main`.
* **Action**: Scan `app/src/test/java`, filter against `LEGITIMATE_GOVERNANCE_GUARDS + baseline`.
* **Expected Result**: `unallowedReaders.isEmpty()` is `true`.

### Test Case 2: `testGovernanceGuards_allExplicitlyAllowed` (`TST-PRO-049.2`)
* **Scope**: Governance Guard Integrity Unit Test (`SourceTextReadingAuditContractTest.kt`)
* **Preconditions**: `LEGITIMATE_GOVERNANCE_GUARDS` contains approved set.
* **Action**: Assert `LEGITIMATE_GOVERNANCE_GUARDS.size == 22`, verify each ends with `Test.kt` or `Cache.kt`, and verify each file exists on disk.
* **Expected Result**: All assertions pass.

### Test Case 3: `testBaseline_containsOnlyExpectedLegacyCount` (`TST-PRO-049.3`)
* **Scope**: Ratcheting & Stale-Entry Detection Unit Test (`SourceTextReadingAuditContractTest.kt`)
* **Preconditions**: `app/source-reading-baseline.txt` with promoted guards removed and 2 recent debt tests recorded.
* **Action**: Assert `(baseline - detectedReaders).isEmpty()` and `baseline.size <= 276`.
* **Expected Result**: Zero stale entries in baseline and size within ceiling.

### Test Case 4: `testRatchetingTripwire_detectsUnallowlistedReader` (`TST-PRO-049.4`)
* **Scope**: Tripwire Unit Test (`SourceTextReadingAuditContractTest.kt`)
* **Preconditions**: Synthetic unallow-listed reader `NewSourceReadingTest.kt`.
* **Action**: Evaluate set difference `mockDetected - allowed`.
* **Expected Result**: Non-empty violation set detected.

### Test Case 5: Tier 1 Targeted Test Suite Execution (`TST-PRO-049.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-049.1` | Audit | `SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders` | `REQ-PRO-049.1`, `REQ-PRO-049.3` | Specified |
| `TST-PRO-049.2` | Governance | `SourceTextReadingAuditContractTest.testGovernanceGuards_allExplicitlyAllowed` | `REQ-PRO-049.2` | Specified |
| `TST-PRO-049.3` | Ratchet | `SourceTextReadingAuditContractTest.testBaseline_containsOnlyExpectedLegacyCount` | `REQ-PRO-049.3` | Specified |
| `TST-PRO-049.4` | Tripwire | `SourceTextReadingAuditContractTest.testRatchetingTripwire_detectsUnallowlistedReader` | `REQ-PRO-049.3` | Specified |
| `TST-PRO-049.5` | Targeted Regression | `./gradlew testDebugUnitTest --tests "...SourceTextReadingAuditContractTest"` | `REQ-PRO-049.1`..`REQ-PRO-049.4` | Specified |
