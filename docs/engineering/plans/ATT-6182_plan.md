# Stage 3: Implementation Plan - ATT-6182: Add Automated Architecture Conformance Tests

**Ticket**: [ATT-6182](https://rainerblind.atlassian.net/browse/ATT-6182)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6182`  
**Requirement Mapping**: `REQ-PRO-050` (`REQ-PRO-050.1`..`REQ-PRO-050.4`)  
**Test Mapping**: `TST-PRO-050` (`TST-PRO-050.1`..`TST-PRO-050.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE pre-assessment finding C-08 flagged that layering rules in `docs/architecture.md` lack automated CI test enforcement, allowing architectural drift (such as direct UI-to-DatabaseManager bindings) to accumulate unnoticed. This plan establishes `ArchitectureConformanceContractTest` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/` to enforce UI-to-database isolation and domain purity via a ratcheting allow-list, registered under `coding_standards.md` §5.4.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-050` (*Automated Architecture Conformance Testing*)
  * `REQ-PRO-050.1`: UI layer database isolation banning direct `*DatabaseManager`/`*DbHelper` usage in Compose UI.
  * `REQ-PRO-050.2`: Pure domain calculation layer isolation banning Android UI and database persistence classes.
  * `REQ-PRO-050.3`: Ratcheting baseline allow-list ensuring existing callers pass while new violations fail.
  * `REQ-PRO-050.4`: Registration in `SourceTextReadingAuditContractTest`'s `LEGITIMATE_GOVERNANCE_GUARDS`.
* **Test Mapping**: `TST-PRO-050` (`TST-PRO-050.1`..`TST-PRO-050.4`)
  * `TST-PRO-050.1`: UI layer database isolation test asserting 0 unallow-listed direct database references.
  * `TST-PRO-050.2`: Domain calculation purity test asserting zero UI or database imports.
  * `TST-PRO-050.3`: Ratcheting tripwire failure test on synthetic layer violation.
  * `TST-PRO-050.4`: Clean-room execution of `ArchitectureConformanceContractTest` and `SourceTextReadingAuditContractTest`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero False Positives**: All existing baseline callers pass without test failures.
2. **Quality Gate Tripwire**: Any net-new architectural layering violation deterministically fails unit tests.
3. **Architecture Document Isolation**: `docs/architecture.md` is strictly read-only in this ticket to prevent conflicts with concurrent `ATT-3556`.

---

## 4. Proposed Architectural Changes
* **Contract Test (`app/src/test/java/.../build/ArchitectureConformanceContractTest.kt`)**:
  * Implements `scanUiLayerForDatabaseReferences()` detecting direct database bindings.
  * Implements `scanDomainLayerForUiCoupling()` verifying domain calculation engine purity.
  * Maintains `BASELINE_ALLOWED_UI_DB_CALLERS` set capturing legacy baseline debt.
  * Includes negative tripwire test verifying unallow-listed violations trigger assertion failures.
* **Governance Registration (`SourceTextReadingAuditContractTest.kt`)**:
  * Adds `com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt` to `LEGITIMATE_GOVERNANCE_GUARDS` and updates guard count assertion to 14.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Author Architecture Conformance Test)**: Create `ArchitectureConformanceContractTest.kt` in `build/` with UI and domain layer scanning logic (`REQ-PRO-050.1`..`REQ-PRO-050.3`, `TST-PRO-050.1`..`TST-PRO-050.3`).
2. **Step 2 (Register Governance Guard)**: Register test in `SourceTextReadingAuditContractTest.kt` under `LEGITIMATE_GOVERNANCE_GUARDS` (`REQ-PRO-050.4`, `TST-PRO-050.4`).
3. **Step 3 (Targeted Verification)**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% PASS (`TST-PRO-050.4`).
4. **Step 4 (Living Docs Synchronization)**: Update `req_process_stability.md` and `tst_process_stability.md` to `[Implemented]`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`.
* **Rollback Plan**: Changes are isolated to `feature/ATT-6182`; revert via `git checkout -- .` and `git clean -fd`.
