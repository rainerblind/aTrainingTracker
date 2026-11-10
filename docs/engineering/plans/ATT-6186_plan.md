# Stage 3: Implementation Plan - ATT-6186: Inventory and Phase Out Source-Text-Reading Tests

**Ticket**: [ATT-6186](https://rainerblind.atlassian.net/browse/ATT-6186)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6186`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`..`REQ-PRO-049.4`)  
**Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`..`TST-PRO-049.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE pre-assessment finding C-12 flagged ~290 test files reading production source text (`.readText()`, `Files.readString()`) to grep for literals/tokens instead of verifying behavior (`coding_standards.md` §5.4). This plan defines the implementation of `SourceTextReadingAuditContractTest` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/`, maintaining an allow-list for legitimate architectural governance guards (§5.4) and a ratcheted baseline for legacy tests, alongside creating per-package backlog follow-up tickets.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-049` (*Source-Text Reading Test Prohibition & Audit Governance*)
  * `REQ-PRO-049.1`: Automated scan of `app/src/test/java` for source-reading invocations.
  * `REQ-PRO-049.2`: Allow-list for legitimate system policy & governance guards under §5.4.
  * `REQ-PRO-049.3`: Ratcheting baseline set isolating 280 legacy debt tests.
  * `REQ-PRO-049.4`: Domain inventory and per-package backlog follow-up tickets.
* **Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`..`TST-PRO-049.4`)
  * `TST-PRO-049.1`: Audit scanner execution verifying 0 unallow-listed violations against baseline.
  * `TST-PRO-049.2`: Governance guard allow-list verification (§5.4 guards).
  * `TST-PRO-049.3`: Ratchet tripwire failure test on synthetic unallow-listed source reader.
  * `TST-PRO-049.4`: Clean-room targeted execution of `SourceTextReadingAuditContractTest`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero False Positives**: Legitimate governance contract tests (§5.4) and existing baseline tests pass 100%.
2. **Quality Gate Tripwire**: Any newly authored test reading `app/src/main/` without allow-list entry fails CI.
3. **Sprint Scope Protection**: Backlog follow-up tickets are created in the Product Backlog, not the active sprint.

---

## 4. Proposed Architectural Changes
* **Contract Test (`app/src/test/java/.../build/SourceTextReadingAuditContractTest.kt`)**:
  * Implements `scanTestSourceFiles()` detecting `.readText(`, `readLines(`, `Files.readString(`.
  * Maintains `LEGITIMATE_GOVERNANCE_GUARDS` set (e.g. `TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`, `ReleaseCiAndLintConfigContractTest`).
  * Maintains `BASELINE_LEGACY_SOURCE_READERS` set (the 280 baselined tests).
  * Asserts all non-exempted source readers are present in `BASELINE_LEGACY_SOURCE_READERS`.
* **Per-Package Backlog Tickets**:
  * File follow-up issues for top clusters (`ui.map`, `ui.tracking`, `ui.aftermath`, `ui.settings`, `ui.routes`, `banalservice.ui`, `migration`, etc.) under Epic `ATT-6142`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Author Contract Test)**: Create `SourceTextReadingAuditContractTest.kt` in `build/` package with scanner and allow-lists (`REQ-PRO-049.1`..`REQ-PRO-049.3`, `TST-PRO-049.1`..`TST-PRO-049.3`).
2. **Step 2 (Create Backlog Follow-Up Tickets)**: File per-package tickets in Jira backlog under Epic `ATT-6142` without adding to active sprint (`REQ-PRO-049.4`).
3. **Step 3 (Targeted Verification)**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"` asserting 100% PASS (`TST-PRO-049.4`).
4. **Step 4 (Living Docs Synchronization)**: Update `req_process_stability.md` and `tst_process_stability.md` to `[Implemented]`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`.
* **Rollback Plan**: Changes are isolated to `feature/ATT-6186`; revert via `git checkout -- .` and `git clean -fd`.
