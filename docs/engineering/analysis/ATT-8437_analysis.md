# Stage 1 Analysis: ATT-8437 - Replace hardcoded live repo count assertions in test_scenario_68/70 with relational invariants

**Ticket**: [ATT-8437](https://rainerblind.atlassian.net/browse/ATT-8437)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht`  
**Branch**: `feature/ATT-8437` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
Follow-up defect from Sprint 2026-47.1/47.2 audit of `ATT-8146` (`REQ-PRO-102`) and `ATT-8147` (`REQ-PRO-103`):
1. **Brittle Snapshot Assertions**: In `tools/test_verify_requirement_governance.py`, `test_scenario_68` and `test_scenario_70` originally asserted exact scalar equality against the live repository (`assertEqual(report["swe_refinement_total_count"], 363)` and `assertEqual(report["reverse_classified_count"], 363)`).
2. **False-Positive CI Breakages**: Whenever standard sprint work legitimately introduces a new System-Refinement requirement (e.g. `REQ-CON-044`, `REQ-CON-045`), the total increases (e.g. from 363 to 366), causing `AssertionError` despite 100% classification parity (`unclassified_swe_refinement_reqs == []`).
3. **Ad-Hoc Incomplete Workaround**: An intermediate commit (`4011bd9c`) replaced `assertEqual` with `assertGreaterEqual(..., 363)`, but omitted the necessary relational invariants (`reverse_classified_count == swe_refinement_total_count`, `unclassified_swe_refinement_reqs == []`, `swe_refinement_total_count >= 362`).

---

## 2. Root Cause Analysis & Independent Peer Review
* **RCA 1 (Point-in-Time Snapshot Testing)**: Initial tests created under `ATT-8146` and `ATT-8147` validated live repository results against instantaneous counts (355 in 47.1, then 362, then 363) rather than verifying relational invariants of the ASPICE traceability model.
* **RCA 2 (Missing Invariant Verification)**: The true invariant of ASPICE SWE.1/SYS.2 reverse traceability is that *every* requirement in `SYSTEM_REFINED_PREFIXES` must be classified (`reverse_classified_count == swe_refinement_total_count` and `unclassified_swe_refinement_reqs == []`), not that the total count of requirements remains permanently frozen.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `tools/test_verify_requirement_governance.py` L2627–2639 (`test_scenario_68`) and L2680–2687 (`test_scenario_70`): Confirmed current assertions use loose `assertGreaterEqual(..., 363)` without verifying identity equality between classified and total counts.
  * Executed live traceability check: `swe_refinement_total_count` is currently 366, `reverse_classified_count` is 366 (`100.0%`), and `unclassified_swe_refinement_reqs` is `[]`.
  * Audited all other tests in `tools/test_*.py`: Confirmed zero other tests assert brittle scalar counts on live repo data (ratchet tests assert `== 0` or `== len(baseline)`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Update `test_scenario_68` in `tools/test_verify_requirement_governance.py` to assert relational invariants:
     - `report["covered_count"] == report["sysreq_count"] == 12`
     - `report["swe_refinement_total_count"] >= 362`
     - `report["swe_refinement_covered_count"] >= 346`
     - `report["swe_refinement_coverage_pct"] >= 95.0`
     - Zero `REQ-DEX-*` or `REQ-PRO-*` pollution in `sys_to_swe_map`.
  2. Update `test_scenario_70` in `tools/test_verify_requirement_governance.py` to assert relational invariants:
     - `report["unclassified_swe_refinement_reqs"] == []`
     - `report["reverse_classified_count"] == report["swe_refinement_total_count"]`
     - `report["swe_refinement_total_count"] >= 362`
     - `report["reverse_classified_pct"] == 100.0`
  3. Ensure `python3 -m unittest tools/test_verify_requirement_governance.py` runs with 100% pass rate.
* **Out-of-Scope Non-Goals**:
  - Modifying `tools/verify_requirement_governance.py` runtime logic (the verifier correctly reports 100%).
  - Modifying production Android code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-102` (`REQ-PRO-102.3`), `REQ-PRO-103` (`REQ-PRO-103.2`), and test specs `TST-PRO-102.3`, `TST-PRO-103.2`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-8146` (`5a263429`) and `ATT-8147` (`5fb97345`) on Sprint 2026-47.1.
* **Root Reason for Existing Formulation**: Snapshot equality ensured that the exact batch of 362/363 requirements was classified during ticket verification.
* **Preservation of Core Invariants**: The lower bound `swe_refinement_total_count >= 362` ensures requirements are never lost; the identity `reverse_classified_count == swe_refinement_total_count` and `unclassified_swe_refinement_reqs == []` strictly enforces 100% classification.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PRO-102.3`, `REQ-PRO-103.2`; test specs `TST-PRO-102.3`, `TST-PRO-103.2`.
* **Affected Architecture Elements**: `tools/test_verify_requirement_governance.py`.
* **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py` (82 unit tests), `python3 tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (test assertion tightening on developer test suite; zero runtime code changes).

---

## 6. Architectural Strategy & High-Level Solution
* In `test_scenario_68`: Replace static snapshot assertions with relational lower bounds (`swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`) and exact forward coverage equality (`covered_count == sysreq_count == 12`).
* In `test_scenario_70`: Replace static snapshot assertions with exact mathematical identity (`reverse_classified_count == swe_refinement_total_count`), zero unclassified list (`unclassified_swe_refinement_reqs == []`), and 100.0% classification check.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) `app/src/main/` strictly untouched; (2) `tools/verify_requirement_governance.py` logic intact; (3) `ANT-Android-SDKs` submodule intact at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **LOW** (1 SP test suite hardening).
