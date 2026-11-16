# Stage 2: Requirement & Test Specification - ATT-8115: ADR-001 & Contract Test PO Scope Alignment

**Ticket**: [ATT-8115](https://rainerblind.atlassian.net/browse/ATT-8115)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-ARC-005` (*Java Core Services Architecture Decision Record & Prioritized Migration Strategy*)  
**Test Spec ID**: `TST-ARC-005` (*Java Core Services Architecture Decision Record & Migration Strategy Verification Suite*)  
**Branch**: `feature/ATT-8115`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`)  
**Date**: 2026-11-16  

---

## 1. Requirement Specification (`REQ-ARC-005`)

### 1.1 Problem Statement & Rationale
On 2026-11-14, the Product Owner bounded Epic `ATT-431` active Kotlin migration to leaf formatters (`ATT-7903`), pure filters (`ATT-7905`), and file exporters (`ATT-7906`). Remaining subsystems (`TrackerService`, `BANALService`, BLE/ANT+ drivers, SQLite database managers, legacy Pebble) remain in Java (`Keep`) to preserve high-frequency telemetry stability. `REQ-ARC-005` is refined to align ADR-001 and `JavaCoreMigrationStrategyContractTest.kt` with this decision.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. `docs/architecture/adr_001_java_core_services_migration.md` SHALL record `Migrate` only for leaf formatters, filters, and exporters; all other subsystems SHALL be classified as `Keep` or `Keep / Repository Modernization` (`REQ-ARC-005.2`).
2. `REGISTERED_CHILD_TICKETS` in `JavaCoreMigrationStrategyContractTest.kt` SHALL be reduced to exactly 3 child tickets (`ATT-7903`, `ATT-7905`, `ATT-7906`), each sized between 1 and 3 SP under Epic `ATT-431` (`REQ-ARC-005.5`).
3. Contract test assertions SHALL enforce `REGISTERED_CHILD_TICKETS.size == 3` and Java file count `<= 149` (`REQ-ARC-005.6`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Child Ticket Reduction)**:
  * *Given* `REGISTERED_CHILD_TICKETS` in `JavaCoreMigrationStrategyContractTest`,
  * *When* `testChildTicketSizingAndTraceability_TST_ARC_005_3()` runs,
  * *Then* it asserts exactly 3 registered child tickets (`ATT-7903`, `ATT-7905`, `ATT-7906`), all sized 1..3 SP.
* **Criterion 2 (ADR Alignment)**:
  * *Given* `docs/architecture/adr_001_java_core_services_migration.md` (§3, §4, §5, §7),
  * *When* inspected,
  * *Then* Subsystems 1–5, 8, 10–12 are classified as `Keep` and only the 3 authorized tickets are active.
* **Criterion 3 (Ratchet File Count)**:
  * *Given* `testClassInventoryAndDecisionCoverage_TST_ARC_005_2()`,
  * *When* scanning main Java files,
  * *Then* `javaFiles.size <= 149` passes.

### 1.5 System Invariants
- Zero modification to production runtime code (`app/src/main/` untouched).
- 100% test pass rate under `./gradlew testDebugUnitTest --tests "*JavaCoreMigrationStrategyContractTest*"`.
- `ANT-Android-SDKs` submodule untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 2. Test Specification (`TST-ARC-005`)

### Test Case 1: Child Ticket Sizing & Traceability (`TST-ARC-005.3`)
* **Scope**: Unit Test / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt`
* **Preconditions**: `REGISTERED_CHILD_TICKETS` contains `ATT-7903` (2 SP, Tier 1), `ATT-7905` (2 SP, Tier 1), and `ATT-7906` (3 SP, Tier 2).
* **Action**: Execute `testChildTicketSizingAndTraceability_TST_ARC_005_3()`.
* **Expected Result**: `assertEquals(3, REGISTERED_CHILD_TICKETS.size)` passes; all 3 tickets verified in ADR text.

### Test Case 2: Class Inventory & Decision Coverage (`TST-ARC-005.2`)
* **Scope**: Unit Test / Contract Test
* **Target File**: `JavaCoreMigrationStrategyContractTest.kt`
* **Action**: Execute `testClassInventoryAndDecisionCoverage_TST_ARC_005_2()`.
* **Expected Result**: All 10 anchors present, 12 subsystems present, and `javaFiles.size <= 149` passes.

### Test Case 3: ADR Integrity & Architecture Doc (`TST-ARC-005.1`, `TST-ARC-005.4`)
* **Scope**: Unit Test / Contract Test
* **Target File**: `JavaCoreMigrationStrategyContractTest.kt`
* **Action**: Execute `testAdrExistenceAndStructuralIntegrity_TST_ARC_005_1()` and `testArchitectureDocIntegration_TST_ARC_005_4()`.
* **Expected Result**: ADR structure valid; `docs/architecture.md` §5.3 integration passes.

### Test Case 4: Targeted Contract Suite Execution (`TST-ARC-005.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "*JavaCoreMigrationStrategyContractTest*"`
* **Expected Result**: 4/4 tests pass with 0 failures, 0 errors, 0 skipped.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-ARC-005.1` | Contract | `testAdrExistenceAndStructuralIntegrity` | `REQ-ARC-005.1` | Specified |
| `TST-ARC-005.2` | Contract | `testClassInventoryAndDecisionCoverage` | `REQ-ARC-005.2`, `REQ-ARC-005.6` | Specified |
| `TST-ARC-005.3` | Contract | `testChildTicketSizingAndTraceability` | `REQ-ARC-005.3`, `REQ-ARC-005.5` | Specified |
| `TST-ARC-005.4` | Contract | `testArchitectureDocIntegration` | `REQ-ARC-005.6` | Specified |
