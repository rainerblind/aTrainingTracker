# Stage 2: Requirement & Test Specification - ATT-7566: Define strategy for Java core services (ADR and migration plan)

**Ticket**: [ATT-7566](https://issue-tracker/ATT-7566)  
**Sub-task**: `[Req & Test Spec]` (To be created via submit-stage)  
**Parent Epic**: [ATT-431](https://issue-tracker/ATT-431) (*Clean Code*)  
**Target Release**: `5.1.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-ARC-005` (*Java Core Services Architecture Decision Record & Prioritized Migration Strategy*)  
**Test Spec ID**: `TST-ARC-005`  
**Branch**: `feat/ATT-7566`  
**Author**: Epic Pro Agent (`agent1`)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (`REQ-ARC-005`)

### 1.1 Problem Statement & Rationale
* ASPICE evaluation finding F9 identifies that core robustness/telemetry services (`TrackerService.java`, `BANALService.java`, peripheral drivers, database managers) remain in Java (149 files, 45,167 LOC).
* Java platform types (`T!`) bypass Kotlin compile-time null safety (SWE.3), while divergent concurrency models and lack of Detekt/KtLint coverage increase defect risks.
* An authoritative Architecture Decision Record (ADR-001) is required to document the complete inventory, migrate vs. keep determinations, dependency-ordered migration tiers, characterisation test prerequisites, and 1–3 SP atomic child tickets.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. `REQ-ARC-005.1`: An authoritative ADR (`docs/architecture/adr_001_java_core_services_migration.md`) SHALL catalog all 149 Java classes across 12 subsystems, documenting LOC, commit churn, test coverage, and defect risks.
2. `REQ-ARC-005.2`: The ADR SHALL record an explicit Migrate/Keep/Retire decision with rationale for every core subsystem.
3. `REQ-ARC-005.3`: Migration sequencing SHALL follow ASPICE SWE.5 integration seams across 7 ordered tiers (Tiers 0–6).
4. `REQ-ARC-005.4`: Every migration ticket SHALL mandate pre-existing characterisation tests, zero functional changes, and nullability shielding.
5. `REQ-ARC-005.5`: All migration tasks SHALL be partitioned into atomic child tickets of 1–3 Story Points under Epic `ATT-431`.
6. `REQ-ARC-005.6`: Contract test `JavaCoreMigrationStrategyContractTest.kt` SHALL verify ADR-001 existence, structure, and child ticket sizing.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* the Java inventory, *When* ADR-001 is reviewed, *Then* it specifies a migrate/keep/retire decision per core class with technical rationale and risk tier.
* **Criterion 2**: *Given* ADR-001, *When* child tickets are filed/planned, *Then* all tickets are sized $\le 3$ SP under Epic `ATT-431`.
* **Criterion 3**: *Given* `JavaCoreMigrationStrategyContractTest`, *When* executed via Gradle, *Then* all contract assertions pass with zero failures.

### 1.5 System Invariants
* Zero production runtime code modified in `app/src/main/` in this ticket.
* Characterisation testing mandatory before migrating any Java class.
* 100% clean-room test pass rate across the unit test suite.

---

## 2. Test Specification (`TST-ARC-005`)

### Test Case 1: Strategy Contract & Structural Completeness (`TST-ARC-005.1`, `TST-ARC-005.2`, `TST-ARC-005.3`)
* **Scope**: Automated Unit Contract Test (`JavaCoreMigrationStrategyContractTest.kt`).
* **Target File**: `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt`.
* **Preconditions**: ADR-001 written to `docs/architecture/adr_001_java_core_services_migration.md`.
* **Action**: Execute reflection/file contract inspecting ADR sections, core class decisions, and child ticket SP sizing.
* **Expected Result**: ADR exists; all 10 top Java anchors covered; all child tickets $\le 3$ SP; 0 failures.

### Test Case 2: Fast Targeted Regression (`TST-ARC-005.4`)
* **Scope**: Targeted Unit Test Suite.
* **Command**: `./gradlew testDebugUnitTest --tests com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest`.
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-ARC-005.1` | Quantitative Java Inventory | `TST-ARC-005.1` | Unit Contract Test |
| `REQ-ARC-005.2` | Core Class Migrate/Keep Decisions | `TST-ARC-005.2` | Unit Contract Test |
| `REQ-ARC-005.3` | Bottom-Up Migration Tiers (0–6) | `TST-ARC-005.1` | Review & Contract Test |
| `REQ-ARC-005.4` | Characterisation Test Safeguards | `TST-ARC-005.1` | Review & Contract Test |
| `REQ-ARC-005.5` | 1–3 SP Child Ticket Sizing | `TST-ARC-005.3` | Unit Contract Test |
| `REQ-ARC-005.6` | Contract Test Automation | `TST-ARC-005.4` | Fast Targeted Regression |
