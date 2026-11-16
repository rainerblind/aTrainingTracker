# Stage 1 Analysis: ATT-8115 - ADR-001 & JavaCoreMigrationStrategyContractTest an PO-Entscheidung anpassen

**Ticket**: [ATT-8115](https://rainerblind.atlassian.net/browse/ATT-8115)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_02_Spät`  
**Branch**: `feature/ATT-8115`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`)  
**Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
On 2026-11-14, the Product Owner established an authoritative scope boundary for Epic `ATT-431` (ADR-001): exclusively leaf sensor formatters (`ATT-7903`), pure signal filters (`ATT-7905`), and streaming file writers (`ATT-7906`) are authorized for Kotlin migration. All other legacy Java subsystems (`TrackerService`, `BANALService`, BLE/ANT+ drivers, SQLite database managers, legacy Pebble) deliberately remain in Java (`Keep`) or encapsulated behind Kotlin repositories (`ATT-7909` / `REQ-ARC-009`) to safeguard runtime telemetry stability.
However, `docs/architecture/adr_001_java_core_services_migration.md` (§3, §4, §5, §7) and `JavaCoreMigrationStrategyContractTest.kt` still reflect the superseded 8-ticket roadmap (`ATT-7902`, `ATT-7907`..`ATT-7910`), expecting 8 registered child tickets instead of the 3 active migration tickets.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Gap Analysis**: `JavaCoreMigrationStrategyContractTest.kt` asserts `REGISTERED_CHILD_TICKETS.size == 8`. ADR-001 §3.1, §3.2, §4, §5, and §7 classify background core, BLE/ANT+ drivers, and Pebble as "Migrate" or active backlog tickets, directly contradicting the PO's decision comments on `ATT-7566`, `ATT-7907`, `ATT-7908`, and `ATT-7910` ("Wird nicht umgesetzt").
- **Independent Peer Review & Verification**:
  - Live audit of Jira tickets confirms `ATT-7907`, `ATT-7908`, and `ATT-7910` were formally closed as cancelled ("Wird nicht umgesetzt"), while `ATT-7902` was restricted to characterisation test scaffolding only (zero production Java edits).
  - Production code audit confirms `com.atrainingtracker.banalservice.filters` (`ATT-7905`) and `writer` (`ATT-7906`) are migrated, while `formater` (`ATT-7903`) is planned.
  - `javaFiles.size <= 149` is already in place in `testClassInventoryAndDecisionCoverage_TST_ARC_005_2()`, correctly serving as an upper-bound ratchet.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Update ADR-001 (`docs/architecture/adr_001_java_core_services_migration.md`) §3, §4, §5, §7 to align decision matrices and child tickets with the PO decision (only `ATT-7903`, `ATT-7905`, `ATT-7906` as `Migrate`; remainder as `Keep`).
  2. Update `JavaCoreMigrationStrategyContractTest.kt` to reduce `REGISTERED_CHILD_TICKETS` to the 3 authorized tickets (`ATT-7903`, `ATT-7905`, `ATT-7906`) and assert `size == 3`.
  3. Align `docs/architecture.md` §5.3, `docs/requirements/req_process_stability.md` (`REQ-ARC-005`), and `docs/tests/tst_process_stability.md` (`TST-ARC-005`).
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No modification to production application runtime code or database schemas.
  - No deletion or revival of closed child tickets in Jira.
  - No changes to other contract tests or architectural invariants.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-ARC-005` (ADR-001 & Contract Test Governance) in `docs/requirements/req_process_stability.md`.
- **Historical Origin & Commit Trace**: Created in Sprint `2026-46.6` under ticket `ATT-7566` to address ASPICE finding F9.
- **Root Reason for Existing Formulation**: Scoped an exhaustive 12-subsystem migration before real-world endurance risk and complexity trade-offs led the PO to bound migration to high-value leaf components.
- **Preservation of Core Invariants**: Aligning ADR-001 and the contract test to the PO decision reinforces Chesterton's fence—preventing dangerous unauthorized migrations of high-frequency background tracking engines.

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: `REQ-ARC-005` (refined), `TST-ARC-005` (updated).
- **Affected Architecture Elements**: `docs/architecture/adr_001_java_core_services_migration.md`, `JavaCoreMigrationStrategyContractTest.kt`, `docs/architecture.md`.
- **Verification Impact & Test Surface**: `./gradlew testDebugUnitTest --tests "*JavaCoreMigrationStrategyContractTest*"`, `python3 tools/verify_requirement_governance.py`.
- **Risk Assessment & Invariants**: LOW risk. 100% non-functional documentation and contract test alignment; zero runtime code changes.

---

## 6. Architectural Strategy & High-Level Solution
1. **ADR-001 Alignment**: Update §3.1/3.2/4 tables to mark subsystems 1-5, 8, 10, 11, 12 as `Keep` (or `Keep / Repository Modernization`), and §7 to cite `ATT-7903`, `ATT-7905`, `ATT-7906` as the only active migration tickets, archiving cancelled tickets (`ATT-7907`, `ATT-7908`, `ATT-7910`).
2. **Contract Test Update**: Set `REGISTERED_CHILD_TICKETS = listOf(ChildTicketContract("ATT-7903", 2, 1), ChildTicketContract("ATT-7905", 2, 1), ChildTicketContract("ATT-7906", 3, 2))` and assert `REGISTERED_CHILD_TICKETS.size == 3`.
3. **Living Docs Sync**: Update `REQ-ARC-005` / `TST-ARC-005` and synchronize indexes.

---

## 7. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Zero production runtime code changes (`app/src/main/` untouched).
  2. Contract test asserts `<= 149` Java files and exactly 3 authorized migration tickets.
  3. 100% pass rate in `./gradlew testDebugUnitTest --tests "*JavaCoreMigrationStrategyContractTest*"`.
- **Risk Rating**: **LOW** (Scope: 1 SP trivial doc & contract test alignment).
