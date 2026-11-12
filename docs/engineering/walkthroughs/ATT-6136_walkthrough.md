# Stage 5 Walkthrough: ATT-6136 - Add priority, type and verification-method attributes to requirements

**Ticket**: [ATT-6136](https://rainerblind.atlassian.net/browse/ATT-6136)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-077` (`REQ-PRO-077.1`–`REQ-PRO-077.4`)  
**Test Spec ID**: `TST-PRO-077` (`TST-PRO-077.1`–`TST-PRO-077.4`)  
**Branch**: `feature/ATT-6136`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

Prior to this improvement (Remediation ID: A-09, ASPICE SWE.1 BP2), software requirements across the 9 living requirement domain shards in `docs/requirements/*.md` lacked explicit metadata for **Priority**, **Type** (Functional vs. Non-Functional), and **Verification Method**. This prevented automated verification planning, systematic prioritization, and architectural classification between functional capabilities and non-functional constraints.

This change standardizes the Stage 2 engineering lifecycle skill and living document templates, introduces automated verification functions (`find_missing_requirement_attributes()`, `verify_requirement_attributes()`) in `tools/verify_requirement_governance.py`, provides diff-based enforcement for newly authored requirements alongside legacy warning tolerance, and implements strict CLI options (`--enforce-attributes` / `--fail-on-missing-attributes` and `--check-attributes`).

---

## 2. Key Changes & Architecture

1. **Lifecycle Skill & Template Standardization**:
   - `.agents/skills/stage2-req-test-spec/SKILL.md`: Standardized the requirement formulation template and Gate 2 audit checklist with mandatory Priority (`Critical`, `High`, `Medium`, `Low`), Type (`Functional`, `Non-Functional`), and Verification Method (`Unit`, `Integration`, `On-Device Manual`, `Review`, `Formal Analysis`) attributes.
   - `templates/test_spec_template.md` & `tools/hoechstmass/templates/test_spec_template.md`: Added Section 1.2 Attributes.
   - `docs/requirements.md`: Updated Developer & AI Agent Guidelines snippet.
2. **Governance Tooling (`tools/verify_requirement_governance.py`)**:
   - Implemented `find_missing_requirement_attributes()`: parses requirement sections across all domain shards, extracting and validating Priority, Type, and Verification Method attributes against permitted domain vocabularies.
   - Implemented `verify_requirement_attributes()`:
     * *Diff-based enforcement*: Newly authored requirements in the git diff MUST declare valid Priority, Type, and Verification Method attributes; missing or invalid attributes fail with exit code 1.
     * *Legacy warning tolerance*: 1,076 pre-existing requirements awaiting batch backfill emit `WARNING (Legacy missing attributes)` without failing default verification.
     * *Strict CLI enforcement*: `--enforce-attributes` / `--fail-on-missing-attributes` fails if any requirement in the repository lacks attributes.
     * *Standalone check*: `--check-attributes` enables targeted attribute audits.
   - Integrated `verify_requirement_attributes()` into standard `verify_governance()` and `--sync-indexes`.
3. **Automated Unit Tests (`tools/test_verify_requirement_governance.py`)**:
   - Added scenarios 44–47 validating attribute presence/domain checks, git diff enforcement, legacy tolerance, strict CLI mode, and REQ-PRO-077 compliance.
4. **Living Documentation**:
   - Registered `REQ-PRO-077` and `TST-PRO-077` as `[Verified]` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md` with 100% master index parity (Total: 1077 REQs, 1081 TSTs).

---

## 3. Verification & Test Evidence

* **Tier 1 Targeted Verification**:
  - `python3 tools/parallel_sprint_lock.py python3 -m unittest tools/test_verify_requirement_governance.py`: **47/47 tests PASS** (100% green in 10.1s).
  - `python3 tools/verify_requirement_governance.py`: **PASS** across all governance tripwires with 100% master index parity (1076 legacy requirements tolerated pending backfill).
  - `python3 tools/verify_requirement_governance.py --enforce-attributes`: Verified to fail as expected with descriptive `ERROR` diagnostics for legacy requirements missing attributes.
  - `python3 tools/verify_requirement_governance.py --check-attributes`: **PASS** (emits warnings for legacy requirements and exits 0).

| Test Case | Scope | Method Under Test | Status | Evidence |
| :--- | :--- | :--- | :---: | :--- |
| `TST-PRO-077.1` | Tooling Unit | `test_scenario_44_requirement_attributes_validation` | **PASS** | Validated Priority, Type, Verification Method domain vocabularies and detected missing/invalid fields |
| `TST-PRO-077.2` | Tooling Unit | `test_scenario_45_diff_attribute_enforcement_and_legacy_tolerance` | **PASS** | Diff enforcement fails on new REQs without attributes; legacy REQs tolerated with warning |
| `TST-PRO-077.3` | Tooling Unit | `test_scenario_46_strict_attribute_enforcement_flag` | **PASS** | `--enforce-attributes` fails on any missing attribute across repository |
| `TST-PRO-077.4` | Tooling Unit / Parity | `test_scenario_47_req_pro_077_living_doc_attribute_presence` | **PASS** | Verified REQ-PRO-077 defines all three attributes with 100% master index parity |

---

## 4. System Invariants & Rollback Safety

- **Zero Android Production Runtime Code Modification**: Only developer governance tooling, test suites, and documentation altered.
- **Pure Python 3 Standard Library**: Zero external dependencies introduced.
- **Living Documentation Integrity**: Master table counts match domain shards with 100% mathematical parity.
- **Non-Breaking CI Compatibility**: Pre-existing legacy requirements do not break default verification while awaiting backfill batches.
