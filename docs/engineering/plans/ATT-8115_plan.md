# Stage 3: Implementation Plan - ATT-8115: ADR-001 & Contract Test PO Scope Alignment

**Ticket**: [ATT-8115](https://rainerblind.atlassian.net/browse/ATT-8115)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-ARC-005` (*Java Core Services Architecture Decision Record & Prioritized Migration Strategy*)  
**Test Mapping**: `TST-ARC-005` (*Java Core Services Architecture Decision Record & Migration Strategy Verification Suite*)  
**Branch**: `feature/ATT-8115`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`)  
**Date**: 2026-11-16  

---

## 1. Problem Description & Background
On 2026-11-14, the Product Owner bounded active Kotlin migration to leaf formatters (`ATT-7903`), pure filters (`ATT-7905`), and streaming file writers (`ATT-7906`), leaving remaining core subsystems in Java (`Keep`). ADR-001 (§3, §4, §5, §7) and `JavaCoreMigrationStrategyContractTest.kt` must be updated to align decision matrices and child ticket contracts with this boundary, reducing registered child tickets from 8 to 3.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-005` (`REQ-ARC-005.1`..`REQ-ARC-005.6`)
* **Test Mapping**: `TST-ARC-005` (`TST-ARC-005.1`..`TST-ARC-005.4`)

---

## 3. System Invariants & Preserved Behavior
1. **Production Code Immutability**: Zero edits to production code (`app/src/main/` 100% untouched).
2. **Submodule Immutability**: `ANT-Android-SDKs` preserved cleanly at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
3. **Ratchet Integrity**: Java class count ratchet assertion (`javaFiles.size <= 149`) strictly preserved.
4. **Contract Test Pass Rate**: 100% pass rate under `./gradlew testDebugUnitTest --tests "*JavaCoreMigrationStrategyContractTest*"`.

---

## 4. Proposed Architectural Changes
* **`docs/architecture/adr_001_java_core_services_migration.md`**: Update §3.1/3.2/4 tables to reflect `Keep` for tracking and peripheral drivers; update §5 and §7 to cite `ATT-7903`, `ATT-7905`, `ATT-7906` as the only active migration tickets while archiving cancelled tickets.
* **`docs/architecture.md` §5.3**: Update Java Core Services Migration Strategy summary to codify PO scope decision.
* **`JavaCoreMigrationStrategyContractTest.kt`**: Reduce `REGISTERED_CHILD_TICKETS` to 3 items and assert `size == 3`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Align ADR-001 Document (`REQ-ARC-005.1`..`REQ-ARC-005.5`)
* **File**: `docs/architecture/adr_001_java_core_services_migration.md`
* **Changes**:
  - §3.1: Set strategy to `Keep` for Subsystems 1–5, 10, 11 (citing PO decision to prevent telemetry regression).
  - §3.2: Update top anchors (`TrackerService`, `TrainingApplication`, `DeviceManager`, `BTLERadarDevice`, `MyBTLEDevice`) to `Keep`.
  - §4: Update table decisions for Subsystems 1–5, 10, 11 classes to `Keep`.
  - §7: Reduce registered child ticket table to `ATT-7903`, `ATT-7905`, `ATT-7906`, noting status of cancelled child tickets.

### Step 2: Update System Architecture Documentation (`REQ-ARC-005.6`)
* **File**: `docs/architecture.md`
* **Changes**: Update §5.3 text with PO decision boundary and reference to 3 active leaf migration tickets.

### Step 3: Update Strategy Contract Test (`TST-ARC-005.3`)
* **File**: `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt`
* **Changes**:
  - Update `REGISTERED_CHILD_TICKETS` to list `ATT-7903` (2 SP, Tier 1), `ATT-7905` (2 SP, Tier 1), `ATT-7906` (3 SP, Tier 2).
  - Update `testChildTicketSizingAndTraceability_TST_ARC_005_3()` assertion from `8` to `3`.
  - Preserve `javaFiles.size <= 149` ratchet check in `testClassInventoryAndDecisionCoverage_TST_ARC_005_2()`.

### Step 4: Targeted Unit Tests & Governance Verification
* **Targeted Tests**: `./gradlew testDebugUnitTest --tests "*JavaCoreMigrationStrategyContractTest*"`
* **Governance**: `python3 tools/verify_requirement_governance.py --check-code-orphans --check-req-description --verify-sys-trace --check-reverse-sys-trace`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "*JavaCoreMigrationStrategyContractTest*"` ensuring 4/4 passing tests.
* **Rollback**: Clean git commit isolation allows immediate reset to base ref (`FETCH_HEAD`) if issues occur.
