# Stage 1 Analysis: ATT-6731 - Move TranslationParityTest and repo governance guards from source-reading baseline to allow-list and ratchet ceiling

**Ticket**: [ATT-6731](https://rainerblind.atlassian.net/browse/ATT-6731)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6731`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-46.1 review of ATT-6186 (*Inventory and phase out source-text-reading tests*), three structural governance issues were identified in `SourceTextReadingAuditContractTest.kt` and `app/source-reading-baseline.txt`:
1. **Misclassified Governance Guards in Phase-Out Baseline**: Essential repository-wide policy scanners (`LocalizationTestCache.kt`, `ThemeColorCentralizationAuditTest.kt`, `RepositoryEngineCleanlinessContractTest.kt`, `RepositoryUiCleanlinessContractTest.kt`, `GlobalDeleteContextMenuAuditTest.kt`, `EditIconConsistencyTest.kt`) were miscategorized as legacy source-reading debt in `app/source-reading-baseline.txt` slated for deletion by cleanup tickets ATT-6564..ATT-6569, violating `docs/coding_standards.md` §5.4.
2. **Brittle Exact Equality vs. Ratcheted Ceiling & Stale-Entry Detection**: `testBaseline_containsOnlyExpectedLegacyCount()` asserts exact equality (`assertEquals(280, baseline.size)`). It fails to assert `(baseline - detectedReaders).isEmpty()`, allowing stale entries to linger when tests are removed.
3. **Scanner Loophole**: `scanTestSourceFiles()` checks `readText`, `readLines`, and `readString`, but misses stream readers `useLines` and `bufferedReader` referencing `src/main`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * Independently verified `app/src/test/java` across all 297 source-reading tests on `sprint/2026-46.2`.
  * Verified candidate guards against `docs/coding_standards.md` §5.4:
    * `LocalizationTestCache.kt` + `TranslationParityTest.kt`: 9-language XML localization parity & DOM cache (`REQ-PRO-025`, `REQ-LOC-001`).
    * `ThemeColorCentralizationAuditTest.kt`: Theme color token centralization outside `Color.kt` (`REQ-UI-381`).
    * `RepositoryEngineCleanlinessContractTest.kt` & `RepositoryUiCleanlinessContractTest.kt`: Decoupling Phase B engines and UI from direct SQLite access (`REQ-DAT-041`, `REQ-UI-507`).
    * `GlobalDeleteContextMenuAuditTest.kt` & `EditIconConsistencyTest.kt`: Repo-wide delete context menu standards (`REQ-UI-061`) and vector asset consistency (`TST-UI-134`).
    * `AspiceSweAuditContractTest.kt`: ASPICE SWE.1/2/4/5/6 interface & component code-grounding (`REQ-PRO-034`, `REQ-PRO-044`).
  * Verified stream reading call sites: `useLines` is used in `LegacyHttpClientPurgeContractTest`, `SpeedConversionContractTest`, `ClusterTuningContractTest`, `ThemeColorCentralizationAuditTest`; `bufferedReader` is used in `AspiceSweAuditContractTest`.
  * Quantified sets: 297 total detected readers. 22 legitimate governance guards (21 detected + `TranslationParityTest` DOM parser). 276 baseline debt entries (274 legacy + 2 recent UI layout tests `CardioPowerTrendsCardTest` and `SensorFieldViewGraphOnlyLayoutTest`). `(baseline - detectedReaders).isEmpty()` is strictly satisfied.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Expand `LEGITIMATE_GOVERNANCE_GUARDS` in `SourceTextReadingAuditContractTest.kt` from 14 to 22 entries (adding the 8 verified repository guards).
  * Remove the 6 misclassified governance guards from `app/source-reading-baseline.txt` and track remaining 276 legacy debt tests.
  * Extend `scanTestSourceFiles()` to detect `useLines` and `bufferedReader`.
  * In `testBaseline_containsOnlyExpectedLegacyCount()`, assert `(baseline - detectedReaders).isEmpty()` and ratchet ceiling to `baseline.size <= 276`.
  * Document exact remaining baseline inventory and ATT-6569 count (118 entries) for `REQ-PRO-029` split planning.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No behavioral test replacement of UI component tests (deferred to ATT-6564..ATT-6569).
  * No modification to production application runtime code.
  * No changes to Lint or Detekt configurations.

---

### 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.3`), refining `REQ-PRO-034`. Targets `SourceTextReadingAuditContractTest.kt` and `app/source-reading-baseline.txt`.
* **Historical Origin & Commit Trace**: Introduced in ATT-6186 (`c91e62b6`) following ASPICE C-12 pre-assessment finding on source-reading test debt.
* **Root Reason for Existing Formulation**: Baseline originally captured all tests accessing `src/main` without discriminating single-file UI grep tests from repository-wide architectural scanners, setting an exact count (280) before ratcheting mechanisms were standardized.
* **Preservation of Core Invariants**: Promoting governance guards to `LEGITIMATE_GOVERNANCE_GUARDS` guarantees that essential system invariants (Rule 34, 9-locale parity, database decoupling, theme centralization) are preserved and never deleted by downstream cleanup tickets. Enforcing `(baseline - detectedReaders).isEmpty()` prevents stale debt accumulation.

---

## 5. Architectural Strategy & High-Level Solution

1. **`SourceTextReadingAuditContractTest.kt`**:
   * Add 8 guards to `LEGITIMATE_GOVERNANCE_GUARDS` (total 22). Update `testGovernanceGuards_allExplicitlyAllowed` to verify 22 guards, support `.endsWith("Test.kt") || .endsWith("Cache.kt")`, and assert file existence on disk.
   * Extend `scanTestSourceFiles` to check `useLines` and `bufferedReader`.
   * Update `testBaseline_containsOnlyExpectedLegacyCount` to enforce `(baseline - detectedReaders).isEmpty()` and `baseline.size <= 276`.
2. **`app/source-reading-baseline.txt`**:
   * Remove the 6 promoted governance guards. Add the 2 recent sprint UI test debt entries, establishing the ratcheted ceiling at 276.
3. **ATT-6569 Inventory Documentation**:
   * Map remaining 276 entries: `ui.map` (45), `ui.tracking` (43), `ui.aftermath` (30), `ui.settings` (25), `ui.routes` (15), `ATT-6569` (118 entries across `ui.components` (17), `banalservice.ui` (14), `migration` (10), `ui.clusters` (9), etc.). Recommend splitting `ui.components` into a separate ticket for `REQ-PRO-029` compliance.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit test suite pass rate.
  2. Legitimate system governance contract tests are permanently immune to automated phase-out deletion.
  3. Legacy source-reading debt ceiling monotonically decreases via `(baseline - detectedReaders).isEmpty()`.
* **Risk Rating**: **LOW** (Changes restricted to test verification harness, baseline debt inventory, and allow-lists; zero production code touched).
