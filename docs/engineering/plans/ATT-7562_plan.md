# Stage 3 Implementation Plan: ATT-7562

**Ticket:** ATT-7562 (`[Verbesserung] Backfill missing attributes of 137 legacy requirements and make the check strict`)  
**Parent Epic:** ATT-232 (*Process*) | **Sprint:** 2026-46.6 | **Target Release:** `V5.0.0` | **Story Points:** 3 SP  
**Role:** `agent1` (Subagent Pro) | **Branch:** `feat/ATT-7562` | **Status:** In Progress (Stage 3)

---

## 1. SWE.2 Architecture & Verification Surface
- **Living Requirement Shards (`docs/requirements/`)**:
  - `docs/requirements/req_maps_navigation.md`: Backfill 5 legacy requirements (`REQ-MAP-065`..`067`, `REQ-CLM-008`..`009`) with Priority, Type, and Verification Method.
  - `docs/requirements/req_tracking_cockpit.md`: Backfill 110 legacy requirements (`REQ-CON-001`..`009`, `REQ-FIL-001`..`006`, `REQ-TRK-001`..`003`, `REQ-SEN-027`, `REQ-TRK-053`..`058`, `REQ-TRK-064`..`075`, etc.) with Priority, Type, and Verification Method.
- **Verification Tooling (`tools/verify_requirement_governance.py`)**:
  - Update `verify_requirement_attributes` and `verify_governance` to enforce attributes strictly by default (`enforce_attributes=True`).
  - Eliminate legacy tolerance warning branch, ensuring zero warnings on clean runs.
- **Unit Test Suite (`tools/test_verify_requirement_governance.py`)**:
  - Update test cases to verify unconditional failure when any requirement lacks required attributes.

---

## 2. Step-by-Step Atomic Implementation Steps

| Step | Action & Target File | Description & Scope | Verification Command |
| :--- | :--- | :--- | :--- |
| **Step 1** | Backfill Maps Shard (`docs/requirements/req_maps_navigation.md`) | Add Priority, Type, Verification Method to 5 legacy requirements (`REQ-MAP-065`..`067`, `REQ-CLM-008`..`009`). | `python3 -c "from tools.verify_requirement_governance import find_missing_requirement_attributes; print([r['req_id'] for r in find_missing_requirement_attributes() if 'maps' in r['file']])"` |
| **Step 2** | Backfill Cockpit Shard (`docs/requirements/req_tracking_cockpit.md`) | Add Priority, Type, Verification Method to 110 legacy requirements in tracking cockpit shard. | `python3 -c "from tools.verify_requirement_governance import find_missing_requirement_attributes; assert len(find_missing_requirement_attributes()) == 0"` |
| **Step 3** | Strict Verification Default (`tools/verify_requirement_governance.py`) | Set `enforce_attributes=True` as default in `verify_requirement_attributes` and `verify_governance`; eliminate legacy tolerance. | `python3 tools/verify_requirement_governance.py` |
| **Step 4** | Update Test Assertions (`tools/test_verify_requirement_governance.py`) | Update unit tests to verify strict attribute enforcement and unconditional failure without tolerance. | `python3 tools/test_verify_requirement_governance.py` |
| **Step 5** | Full Governance & Parity Gate | Verify zero warnings, 100% attribute compliance, and master table parity. | `python3 tools/verify_requirement_governance.py && python3 tools/test_verify_requirement_governance.py` |

---

## 3. System Invariants & Rollback Safety
- **Zero Production Code Changes**: No modifications to `app/src/main/` Android source files.
- **100% Master Table Parity**: Living documentation master indices remain strictly synchronized.
- **Zero Flaky Tests**: All 57+ unit tests in `test_verify_requirement_governance.py` pass cleanly.
- **UI Consistency**: No UI changes (pure process governance and living documentation ticket).
