# Stage 3: Implementation Plan - ATT-6743: Enforce Rule 81 via Deterministic Python Tripwires & Sync REQ-PRO-041.1 Concurrency=3

**Ticket**: [ATT-6743](https://rainerblind.atlassian.net/browse/ATT-6743)  
**Sub-task**: [ATT-6788](https://rainerblind.atlassian.net/browse/ATT-6788) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-041` (*Parallel Sprint Execution Protocol for Disjoint Epics*)  
**Test Mapping**: `TST-PRO-041` | **Branch**: `feature/ATT-6743`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Prose rules (`Rule 81`) cannot prevent defect escapes (unmounted composable islands, leaky repository SQLite getters, ungrounded architecture symbols/SYSREQ mismatches) under multi-agent context load without deterministic tripwires. This plan constructs the 3 deterministic Python tripwires in `tools/verify_requirement_governance.py`, backed by unit tests in `tools/test_verify_requirement_governance.py`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-041` (`REQ-PRO-041.1`, `REQ-PRO-041.2`, `REQ-PRO-041.5`)
* **Test Mapping**: `TST-PRO-041` (`TST-PRO-041.1`..`TST-PRO-041.6`)

---

## 3. System Invariants & Preserved Behavior
1. **Python 3 Standard Library Invariant**: Zero external pip dependencies; imports restricted to standard library modules (`os`, `re`, `subprocess`, `sys`, `unittest`).
2. **Deterministic Exit Codes**: Exit code `0` on compliant branch diffs; exit code `1` with actionable diagnostics on violations.
3. **Clean Working Tree Backward Compatibility**: Clean sprint branch passes all tripwires cleanly without false positives.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Zero-Caller Composable Island Check (`verify_composable_islands`)
* Location: `tools/verify_requirement_governance.py`
* Implementation: Parses git diff of modified/added `app/src/main/**/*.kt` files. Extracts added non-`@Preview`, non-`private` `@Composable` functions. Greps `app/src/main/` to verify at least one non-preview production call site `<Name>(` exists outside the declaration.

### Component 2: Repository Encapsulation Guard (`verify_repository_encapsulation`)
* Location: `tools/verify_requirement_governance.py`
* Implementation: Scans modified/added lines in `*Repository.kt` and `*Repository.java` in `app/src/main/`. Flags any non-private method/property exposing `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, or `Cursor`.

### Component 3: Architecture & System Spec Grounding (`verify_architecture_and_system_specs`)
* Location: `tools/verify_requirement_governance.py`
* Implementation: When diff touches `docs/architecture/` or `docs/system/`, verifies backticked `*.{kt,java,xml}` files exist in the repository, and compares `sysreq_*.md` declared IDs vs `system_architecture.md` allocated IDs for exact set equality.

### UI Consistency (Rule 23)
* No UI changes (pure developer tooling and living documentation synchronization).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Composable Island Check Implementation
* File: `tools/verify_requirement_governance.py` (`verify_composable_islands`)

### Step 2: Repository Encapsulation Guard Implementation
* File: `tools/verify_requirement_governance.py` (`verify_repository_encapsulation`)

### Step 3: Architecture & System Spec Code-Grounding & SYSREQ Guard
* File: `tools/verify_requirement_governance.py` (`verify_architecture_and_system_specs`)

### Step 4: Pipeline Integration & CLI Wiring
* File: `tools/verify_requirement_governance.py` (wire tripwires into `verify_governance()`)

### Step 5: Unit Test Suite Expansion
* File: `tools/test_verify_requirement_governance.py` (scenarios 6, 7, 8)

### Step 6: Clean-Room Test Execution
* Commands: `python3 tools/test_verify_requirement_governance.py` & `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `python3 tools/test_verify_requirement_governance.py` ensuring 100% pass rate. Verify clean tree exit code 0.
* **Rollback**: Branch isolation (`feature/ATT-6743`) allows full reset (`git reset --hard sprint/2026-46.2`) without side effects.
