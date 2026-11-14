# Stage 1 Analysis: ATT-7566 - Define strategy for Java core services (ADR and migration plan)

**Ticket**: [ATT-7566](https://issue-tracker/ATT-7566)  
**Sub-task**: `[Analysis]` (To be created via submit-stage)  
**Parent Epic**: [ATT-431](https://issue-tracker/ATT-431) (*Clean Code*)  
**Target Release**: `5.1.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7566`  
**Author**: Epic Pro Agent (`agent1`)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
* **ASPICE Evaluation Finding F9**: The most safety- and robustness-critical core of `aTrainingTracker` (`TrackerService.java`, `BANALService.java`, sensor managers, and database layers) comprises 149 Java files (45,167 LOC), whereas newer subsystems (Compose UI, ViewModels, Repositories, Math Calculators) are Kotlin (654 files).
* **Engineering Problem**: Kotlin treats unannotated Java return types as Platform Types (`T!`), bypassing compile-time null safety (SWE.3) and leading to runtime `NullPointerException` risks. Concurrency models diverge between Java synchronized/Handler models and Kotlin coroutine `StateFlow` primitives.
* **Objective**: Define an authoritative Architecture Decision Record (ADR-001) with an inventory, migrate/keep/retire determinations per core class, quality safeguards (characterisation tests first), and atomic child tickets (1–3 SP). No production code is modified in this ticket.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Hypothesis Evaluation**: The ticket hypothesizes that Java core services present heightened migration risks due to high complexity, threading coupling, and lack of nullability guarantees.
* **Independent Codebase Verification**:
  * Quantitative Audit: 149 Java files (45,167 LOC) vs 654 Kotlin files in `app/src/main/java`.
  * Top Java anchors by LOC and git churn:
    1. `TrackerService.java` (3,307 LOC, 128 commits, tested)
    2. `WorkoutSummariesDatabaseManager.java` (2,945 LOC, 108 commits, tested)
    3. `TrackingViewsDatabaseManager.java` (2,421 LOC, 67 commits, tested)
    4. `TrainingApplication.java` (2,125 LOC, 174 commits, tested)
    5. `DevicesDatabaseManager.java` (1,522 LOC, 39 commits, tested)
    6. `DeviceManager.java` (1,352 LOC, 53 commits, tested)
    7. `BTLERadarDevice.java` (1,224 LOC, 20 commits, tested)
    8. `MyBTLEDevice.java` (1,130 LOC, 24 commits, tested)
    9. `WorkoutSamplesDatabaseManager.java` (954 LOC, 31 commits, tested)
    10. `BANALService.java` (864 LOC, 54 commits, tested)
  * Test Coverage Findings: 60/149 Java files (34,659 LOC, 76.7% of code volume) have unit tests. However, critical state machines in `TrackerService` and device drivers lack exhaustive characterisation test harnesses for end-to-end telemetry streams.
  * Dead / Obsolete Code: Pebble smartwatch integration (7 files, 1,739 LOC, 0 tests) represents dead hardware ecosystem code that should be retired/frozen rather than migrated. 3rd-party `OpenCSV` (3 files, 493 LOC) should be kept or replaced by library dependencies.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Comprehensive Java class inventory categorized by subsystem, LOC, test coverage, and defect churn.
  * Authoring Architecture Decision Record `docs/architecture/adr_001_java_core_services_migration.md` defining migrate vs. keep decisions with rationale.
  * Defining bottom-up migration sequencing (Tiers 0–6) adhering to ASPICE SWE.5 integration seams.
  * Establishing mandatory migration safeguards (characterisation test golden masters, nullability barriers, strict refactoring invariance).
  * Structuring backlog child tickets of 1–3 SP under Epic `ATT-431` (*Clean Code*).
  * Updating living documentation (`docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `docs/architecture.md`).
* **Out-of-Scope Non-Goals**:
  * Direct migration or refactoring of Java source code in this ticket (strictly forbidden by ticket scope).
  * Modifying existing runtime interfaces or database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-ARC-005` (*Java Core Services Architecture Decision Record & Migration Strategy*), extending `REQ-ARC-001` through `REQ-ARC-004` under Epic `ATT-431`.
* **Historical Origin & Commit Trace**: Follows ASPICE evaluation finding F9 and Epic `ATT-431` code cleanliness initiatives.
* **Root Reason for Existing Formulation**: Java classes were created during early Android API levels (pre-Kotlin). Preserving them while Kotlin was introduced avoided breaking functional tracking stability.
* **Preservation of Core Invariants**: Retains full backward compatibility and zero runtime disruption by adopting an asynchronous, test-prefixed migration strategy.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new requirement `REQ-ARC-005` in `docs/requirements/req_process_stability.md`; mapped to test specification `TST-ARC-005` in `docs/tests/tst_process_stability.md`.
* **Affected Architecture Elements**: `docs/architecture.md` (System Architecture §2 allocation), `docs/architecture/adr_001_java_core_services_migration.md` (ADR-001 deliverable).
* **Verification Impact & Test Surface**: Verification via contract testing (`JavaCoreMigrationStrategyContractTest.kt`) confirming ADR-001 structural completeness, class inventory coverage, and child ticket mapping. Zero regression on production APK.
* **Risk Assessment & Invariants**: Low risk (documentation & strategy only). Preserves all existing code, interfaces, and system invariants.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero production code modifications in `app/src/main/` within this ticket.
  2. Characterisation testing mandate before any subsequent Java class migration.
  3. Every migration child ticket must remain $\le 3$ SP to prevent monolithic regressions.
* **Risk Rating**: `LOW` (Strategy and architectural governance deliverable; zero runtime impact).
