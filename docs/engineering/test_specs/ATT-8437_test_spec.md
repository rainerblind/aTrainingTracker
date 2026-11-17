# Stage 2: Requirement & Test Specification - ATT-8437: Replace hardcoded live repo count assertions in test_scenario_68/70 with relational invariants

**Ticket**: [ATT-8437](https://rainerblind.atlassian.net/browse/ATT-8437)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-102` (`REQ-PRO-102.3`), `REQ-PRO-103` (`REQ-PRO-103.2`)  
**Test Spec ID**: `TST-PRO-102` (`TST-PRO-102.3`), `TST-PRO-103` (`TST-PRO-103.2`)  
**Branch**: `feature/ATT-8437` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Requirement Specification (REQ-PRO-102 & REQ-PRO-103)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Status: `[Verified]`
* **REQ-PRO-102.3 (Bidirectional Forward & Reverse Coverage Reporting — [Amended] ATT-8437)**: `verify_system_software_traceability()` and `--report-sys-coverage` SHALL report both forward (`12/12` = `100.0%`) and reverse `System-Refinement` coverage. In live repository regression testing (`test_scenario_68`), assertions SHALL evaluate relational invariants rather than brittle point-in-time snapshot equality counts: exact forward coverage parity (`covered_count == sysreq_count == 12`), lower-bound floors (`swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`), and zero pollution from `REQ-DEX-*` or `REQ-PRO-*`.
* **REQ-PRO-103.2 (100% System-Refinement Coverage & Structural Invariant Verification — [Amended] ATT-8437)**: All `SYSTEM_REFINED_PREFIXES` software requirements SHALL define `- **System Requirement(s)**:` (`SYSREQ-*` or `Derived (SW-only)`), achieving `100.0%` forward and reverse classification. In live repository testing (`test_scenario_70`), assertions SHALL enforce structural invariants rather than brittle snapshot counts: `reverse_classified_count == swe_refinement_total_count`, `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`, `unclassified_swe_refinement_reqs == []`, `swe_refinement_total_count >= 362`, and `reverse_classified_pct == 100.0`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-102.3` / `TST-PRO-102.3` and `REQ-PRO-103.2` / `TST-PRO-103.2`. Targets `tools/test_verify_requirement_governance.py` (`test_scenario_68`, `test_scenario_70`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-8146` (`5a263429`) and `ATT-8147` (`5fb97345`) on Sprint 2026-47.1 to remediate Finding M2.
* **Root Reason for Existing Formulation**: Snapshot equality counts (`== 362` or `== 363`) were written to verify the instantaneous count of requirements during ticket execution, but break whenever new requirements are added in subsequent sprints.
* **Preservation of Core Invariants**: The lower bound `swe_refinement_total_count >= 362` preserves regression baseline coverage; mathematical identities (`reverse_classified_count == swe_refinement_total_count` and `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`) and `unclassified_swe_refinement_reqs == []` strictly enforce 100% classification; synthetic fixtures (`test_scenario_69`) continue to prove that any unclassified requirement fails loudly.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Live Forward Coverage Invariants in test_scenario_68)**: *Given* the live repository, *When* `test_scenario_68` executes, *Then* it asserts `covered_count == sysreq_count == 12`, `swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`, and zero `REQ-DEX-*` / `REQ-PRO-*` pollution in `sys_to_swe_map`.
* **Criterion 2 (Live Reverse Classification Invariants in test_scenario_70)**: *Given* the live repository, *When* `test_scenario_70` executes with `fail_on_unclassified_swe=True`, *Then* it asserts `unclassified_swe_refinement_reqs == []`, `reverse_classified_count == swe_refinement_total_count`, `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`, `swe_refinement_total_count >= 362`, and `reverse_classified_pct == 100.0`.
* **Criterion 3 (Synthetic Mismatch Failure Proof)**: *Given* synthetic fixture `test_scenario_69`, *When* an unclassified requirement is injected, *Then* `verify_system_software_traceability(fail_on_unclassified_swe=True)` returns `ok == False` and identifies the exact unclassified requirement in `unclassified_swe_refinement_reqs`.
* **Criterion 4 (Full Governance Test Suite Green)**: *Given* `tools/test_verify_requirement_governance.py`, *When* executed via `python3 -m unittest`, *Then* all 82 tests pass with 100% success rate.

---

## 4. Test Specification (TST-PRO-102.3 & TST-PRO-103.2)
* **TST-PRO-102.3 (Bidirectional Coverage & Live CLI Invariant Verification Test)**: Verify in `tools/test_verify_requirement_governance.py` (`test_scenario_68`) that live repository traceability satisfies structural invariants (`covered_count == sysreq_count == 12`, `swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`, zero `REQ-DEX-*` / `REQ-PRO-*` in `sys_to_swe_map`).
* **TST-PRO-103.2 (Live Repository 100% Bidirectional Classification & Invariant Test)**: Verify in `tools/test_verify_requirement_governance.py` (`test_scenario_70`) that live reverse classification satisfies structural invariants (`reverse_classified_count == swe_refinement_total_count`, `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`, `unclassified_swe_refinement_reqs == []`, `swe_refinement_total_count >= 362`, `reverse_classified_pct == 100.0`).

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-102.3` | Unit/Live | `tools/test_verify_requirement_governance.py` (`test_scenario_68`) | `REQ-PRO-102.3` | Specified |
| `TST-PRO-103.2` | Unit/Live | `tools/test_verify_requirement_governance.py` (`test_scenario_70`) | `REQ-PRO-103.2` | Specified |
| `TST-PRO-103.1` | Unit/Synthetic | `tools/test_verify_requirement_governance.py` (`test_scenario_69`) | `REQ-PRO-103.1` | Specified |
