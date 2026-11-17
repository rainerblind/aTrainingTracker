# Stage 3: Implementation Plan - ATT-8437: Replace hardcoded live repo count assertions in test_scenario_68/70 with relational invariants

**Ticket**: [ATT-8437](https://rainerblind.atlassian.net/browse/ATT-8437)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-102` (`REQ-PRO-102.3`), `REQ-PRO-103` (`REQ-PRO-103.2`)  
**Test Mapping**: `TST-PRO-102` (`TST-PRO-102.3`), `TST-PRO-103` (`TST-PRO-103.2`)  
**Branch**: `feature/ATT-8437` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Problem Description & Background
In `tools/test_verify_requirement_governance.py`, `test_scenario_68` and `test_scenario_70` originally tested live repository state using brittle snapshot counts (`assertEqual(..., 363)`). Adding legitimate new requirements (currently 366 System-Refinement REQs) broke these tests despite 100% classification compliance. While commit `4011bd9c` applied an ad-hoc `assertGreaterEqual(..., 363)`, it did not enforce the necessary relational identity invariants between total, covered, and classified counts.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-102.3` $\rightarrow$ `TST-PRO-102.3`**: In `test_scenario_68`, replace scalar equality with forward parity (`covered_count == sysreq_count == 12`), lower-bound floors (`swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`), and zero pollution from `REQ-DEX-*` or `REQ-PRO-*`.
* **`REQ-PRO-103.2` $\rightarrow$ `TST-PRO-103.2`**: In `test_scenario_70`, assert mathematical identity invariants: `reverse_classified_count == swe_refinement_total_count`, `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`, `unclassified_swe_refinement_reqs == []`, `swe_refinement_total_count >= 362`, and `reverse_classified_pct == 100.0`.
* **`REQ-PRO-103.1` $\rightarrow$ `TST-PRO-103.1`**: Retain synthetic fixture `test_scenario_69` verifying that unclassified requirements fail loudly under `fail_on_unclassified_swe=True`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Code Edits**: Zero modifications to `app/src/main/` or `tools/verify_requirement_governance.py`.
2. **Regression Floor Preservation**: Enforce `swe_refinement_total_count >= 362` and `swe_refinement_covered_count >= 346` to guarantee requirement counts never silently drop.
3. **100% Classification Enforcement**: Mathematical identity between classified count and total refinement count ensures zero unclassified requirements escape.
4. **Submodule Integrity**: `ANT-Android-SDKs` preserved at commit `0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`TST-PRO-102.3` / `tools/test_verify_requirement_governance.py`)**:
  - Update `test_scenario_68_tst_pro_102_3_bidirectional_sys_coverage_live_repo`:
    - Assert `report["covered_count"] == report["sysreq_count"]` and `report["sysreq_count"] == 12`.
    - Assert `report["swe_refinement_total_count"] >= 362`.
    - Assert `report["swe_refinement_covered_count"] >= 346`.
    - Assert `report["swe_refinement_coverage_pct"] >= 95.0`.
* **Step 2 (`TST-PRO-103.2` / `tools/test_verify_requirement_governance.py`)**:
  - Update `test_scenario_70_tst_pro_103_2_live_reverse_sys_trace_100pct`:
    - Assert `report["unclassified_swe_refinement_reqs"] == []`.
    - Assert `report["reverse_classified_count"] == report["swe_refinement_total_count"]`.
    - Assert `report["swe_refinement_covered_count"] + report["swe_derived_sw_only_count"] == report["swe_refinement_total_count"]`.
    - Assert `report["swe_refinement_total_count"] >= 362`.
    - Assert `report["reverse_classified_pct"] == 100.0`.
* **Step 3 (`Verification`)**:
  - Run `python3 -m unittest tools/test_verify_requirement_governance.py` (82/82 passing).
  - Run `python3 tools/verify_requirement_governance.py` (code 0).

---

## 5. Verification & Rollback Plan
* **Verification**: Verify all 82 tests pass in `test_verify_requirement_governance.py` and requirement governance passes cleanly.
* **Rollback**: `git checkout feature/ATT-8437 -- tools/test_verify_requirement_governance.py` cleanly reverts changes.
