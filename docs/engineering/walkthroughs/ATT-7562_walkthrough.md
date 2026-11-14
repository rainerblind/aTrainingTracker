# Stage 5 Walkthrough: ATT-7562

**Ticket:** ATT-7562 (`[Verbesserung] Backfill missing attributes of 137 legacy requirements and make the check strict`)  
**Parent Epic:** ATT-232 (*Process*) | **Sprint:** 2026-46.6 | **Target Release:** `V5.0.0`  
**Requirement Mapping:** `REQ-PRO-094` (`REQ-PRO-094.1`–`REQ-PRO-094.4`) | **Test Mapping:** `TST-PRO-094`  
**Role:** `agent1` (Subagent Flash) | **Branch:** `feat/ATT-7562`  

---

## 1. Executive Summary & Verification Overview

ATT-7562 eliminates ASPICE finding F5 (legacy requirement attribute omissions and non-fatal warning tolerance under SWE.1 BP2 / BP3):
1. **Legacy Requirement Attribute Backfill (`REQ-PRO-094.1`)**: Backfilled valid `Priority`, `Type`, and `Verification Method` attributes across all 115 legacy requirements in `docs/requirements/req_maps_navigation.md` (5 requirements: `REQ-MAP-065`..`067`, `REQ-CLM-008`..`009`) and `docs/requirements/req_tracking_cockpit.md` (110 requirements: `REQ-CON-001`..`009`, `REQ-FIL-001`..`006`, `REQ-TRK-001`..`003`, `REQ-SEN-027`, `REQ-TRK-053`..`058`, `REQ-TRK-064`..`075`, etc.).
2. **Strict Attribute Enforcement by Default (`REQ-PRO-094.2`)**: Updated `verify_requirement_attributes` and `verify_governance` in `tools/verify_requirement_governance.py` to enforce attribute validity by default (`enforce_attributes=True`), treating any missing or invalid attribute as a fatal exit code 1 error.
3. **Zero Warning Requirement (`REQ-PRO-094.3`)**: Executed `tools/verify_requirement_governance.py`, confirming zero legacy attribute warnings and certifying `PASS: 100% of living requirements define valid Priority, Type, and Verification Method attributes.`
4. **Regression & Default Enforcement Test Suite (`REQ-PRO-094.4`)**: Added `test_scenario_57_req_pro_094_strict_attribute_enforcement_default` in `tools/test_verify_requirement_governance.py` asserting that omission of required attributes fails verification unconditionally without flags, while maintaining full regression test pass rate (61/61 tests).

---

## 2. Requirement & Test Verification Matrix

| Requirement ID | Test Spec ID | Verification Method | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-094.1` | `TST-PRO-094` | Static Inspection & Audit (`find_missing_requirement_attributes`) | **PASSED** | `Verified` |
| `REQ-PRO-094.2` | `TST-PRO-094` | Unit & Verifier Test (`tools/test_verify_requirement_governance.py`) | **PASSED** | `Verified` |
| `REQ-PRO-094.3` | `TST-PRO-094` | Full Governance Run (`python3 tools/verify_requirement_governance.py`) | **PASSED** | `Verified` |
| `REQ-PRO-094.4` | `TST-PRO-094` | Negative & Default Mutation Tests (`test_scenario_57`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit & Governance Test Scope
- **Requirement Attributes Static Audit**:
  - `python3 -c "from tools.verify_requirement_governance import find_missing_requirement_attributes; assert len(find_missing_requirement_attributes()) == 0"`: **0 defective requirements detected across all shards**.
- **Governance Verifier Execution**:
  - `python3 tools/verify_requirement_governance.py`: **100% parity across living doc tables, zero warnings, 100% attribute compliance certified**.
- **Full Requirement Governance Unit Test Suite**:
  - `python3 -m unittest tools/test_verify_requirement_governance.py`: **61/61 tests passed in 11.3s** (0 failures, 0 errors), including `test_scenario_57_req_pro_094_strict_attribute_enforcement_default`.

---

## 4. Hardware & Device Verification Scope
- Non-UI process tooling and living documentation ticket.
- Zero modifications to Android UI composables or runtime application code in `app/src/main/`. On-device deployment skipped per Rule 7.6.

---

## 5. Invariant & Governance Verification
1. **Zero Runtime Regressions**: No modifications to application code; all existing unit tests in `test_verify_requirement_governance.py` passed.
2. **Document Triad Parity**: Living documentation synchronized (`REQ-PRO-094` and `TST-PRO-094` updated to `Verified`, master indices recalculated with 100% parity).
3. **No Stage 4 Markdown Artifacts**: Stage 4 submitted directly via Jira CLI.
4. **Target Release Alignment**: Verified against active release `V5.0.0` from `tools/jira_util.py versions`.
