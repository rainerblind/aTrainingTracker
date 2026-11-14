# Stage 3: Implementation Plan - ATT-7566: Define strategy for Java core services (ADR and migration plan)

**Ticket**: [ATT-7566](https://issue-tracker/ATT-7566)  
**Sub-task**: `[Impl-Plan]` (To be created via submit-stage)  
**Parent Epic**: [ATT-431](https://issue-tracker/ATT-431) (*Clean Code*)  
**Target Release**: `5.1.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-ARC-005` (*Java Core Services Architecture Decision Record & Prioritized Migration Strategy*)  
**Test Mapping**: `TST-ARC-005` (*Java Core Services Strategy & Child Ticket Traceability Suite*)  
**Branch**: `feat/ATT-7566`  
**Author**: Epic Pro Agent (`agent1`)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
ASPICE evaluation finding F9 identified that while the newer application layers (Compose UI, ViewModels, Repositories, Math Engines) are 100% Kotlin (654 files), the safety- and robustness-critical core (`TrackerService.java`, `BANALService.java`, sensor managers, database helpers) remains in Java (149 files, 45,167 LOC). Unannotated Java return types introduce platform types (`T!`), breaking Kotlin compile-time null safety (SWE.3) and creating `NullPointerException` risks. This plan details the software construction steps for ADR-001, architectural documentation updates, contract testing, and atomic child ticket generation.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-005` (`REQ-ARC-005.1` to `REQ-ARC-005.6`) in `docs/requirements/req_process_stability.md`.
* **Test Mapping**: `TST-ARC-005` (`TST-ARC-005.1` to `TST-ARC-005.4`) in `docs/tests/tst_process_stability.md`.
* **Traceability Invariant**: 100% bidirectional coverage verified via `verify_requirement_governance.py`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Code Edits**: Zero modifications to production source code in `app/src/main/` under this ticket.
2. **Characterisation Testing Gate**: No Java core file will be migrated in future child tickets without pre-existing characterisation tests capturing 100% of current public contract behavior.
3. **Atomic Ticket Bounding**: All migration child tickets must remain strictly between 1 and 3 Story Points (no monolithic migrations).
4. **Clean-Room Test Pass Rate**: 100% unit test pass rate maintained under `./gradlew testDebugUnitTest`.

---

## 4. Proposed Architectural Changes
### Component 1: Architecture Decision Record ADR-001 (`docs/architecture/adr_001_java_core_services_migration.md`)
* Document context, options evaluated (Big-Bang rewrite vs. Status Quo vs. Phased Risk-Tiered Migration), and decision outcome.
* Class-by-class inventory of 149 Java files categorized into 12 subsystems with LOC, commit churn, test status, and Migrate/Keep/Retire determinations.
* Detailed 7-tier bottom-up migration sequencing (Tiers 0–6) aligned with ASPICE SWE.5 integration seams.
* Mandatory quality safeguards: Golden Master characterisation tests, nullability barriers (`@NonNull`/`@Nullable`), refactoring invariance.
* 8 atomic child tickets (1–3 SP each) filed under Epic `ATT-431`.

### Component 2: System Architecture Update (`docs/architecture.md`)
* Update §2 (Requirement Allocation to Architecture) to allocate `REQ-ARC-005` to Engineering Process & Stability.
* Add §5.3 referencing ADR-001 and the Java Core Migration Strategy.

### Component 3: Automated Strategy Contract Test (`JavaCoreMigrationStrategyContractTest.kt`)
* Unit contract test in `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt`.
* Asserts ADR-001 file existence, mandatory section presence, coverage of all 10 top Java anchors, and child ticket SP sizing ($\le 3$ SP).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Check Gate 3 Prerequisite)**:
   * Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.
2. **Step 2 (Author ADR-001)**:
   * Create `docs/architecture/adr_001_java_core_services_migration.md` with complete inventory, decisions, roadmap, safeguards, and child tickets.
3. **Step 3 (Update System Architecture)**:
   * Update `docs/architecture.md` §2 table and §5 to link ADR-001.
4. **Step 4 (Implement Contract Test)**:
   * Create `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt` implementing `TST-ARC-005.1` through `TST-ARC-005.3`.
5. **Step 5 (File Backlog Child Tickets via Jira CLI)**:
   * File atomic child tickets (1–3 SP each) under Epic `ATT-431` via `tools/jira_util.py create-issue` (or document filed keys in ADR).
6. **Step 6 (Targeted Unit Verification)**:
   * Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted contract test `JavaCoreMigrationStrategyContractTest`, governance sync (`verify_requirement_governance.py`), and full test suite regression (`./gradlew testDebugUnitTest`).
* **Rollback**: Feature branch isolation (`feat/ATT-7566`); revert commits if contract assertions fail.
