# Stage 3: Implementation Plan - ATT-8143: Derive Verified status from test results

**Ticket**: [ATT-8143](https://rainerblind.atlassian.net/browse/ATT-8143) | **Sub-task**: [ATT-8278](https://rainerblind.atlassian.net/browse/ATT-8278) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8143`  
**Requirement Mapping**: `REQ-PRO-099` (`REQ-PRO-099.1`..`REQ-PRO-099.5`)  
**Test Mapping**: `TST-PRO-099` (`TST-PRO-099.1`..`TST-PRO-099.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
Under ASPICE 4.0 Major Finding `M1` (SWE.4/6 BP4/5, SWE.1 BP5), `[Verified]` status was set unconditionally during Stage 5 submission via `auto_verify_living_docs_on_stage5()` in `tools/jira_util.py`, decoupled from test execution results. 405 of 1156 test specifications (35%) lack test code tags, and failing tests left items falsely marked `[Verified]`. This plan establishes result-driven verification: ingesting machine-readable JUnit XML (`TEST-*.xml`), creating `verify_requirement_governance.py --check-verified-evidence` (rejecting failing tests and unbacked new items while tolerating the 405 legacy items via `LEGACY_UNBACKED_VERIFIED_TSTS` ratchet baseline), conditioning Stage 5 status promotion on passing evidence, and preserving clean-worktree safety for default checks.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-099` (*Test Result-Driven Verification Status & Evidence Gating*)
* **Test Mapping**: `TST-PRO-099` (`TST-PRO-099.1`..`TST-PRO-099.4`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Android Production Code Changes**: Pure developer tooling and governance scripts; `app/src/main/` 100% untouched.
2. **Clean-Worktree Safety (REQ-PRO-099.5)**: Default `verify_requirement_governance.py` (and git hooks) pass cleanly without pre-existing XML artifacts.
3. **Ratchet Baseline Stability**: 405 legacy unbacked TSTs tolerated; novel unbacked or failing tests fail builds.
4. **Human Decision Gate**: Rule 3 Human Gate remains inviolable.

---

## 4. Proposed Architectural Changes
* **Component 1 (`tools/verify_requirement_governance.py`)**:
  - Implement `parse_junit_xml_results(results_dir)` to parse XML testcases and aggregate `{status: PASS|FAIL|ERROR, message}` mapped to class/method names and `TST-XXX` IDs.
  - Implement `verify_verified_test_evidence(repo_root)` and CLI flag `--check-verified-evidence`:
    * Scan all living `TST-XXX` specs marked `[Verified]`.
    * Fail (exit 1) if associated testcase reports `failure` or `error` (Criterion 1).
    * Fail (exit 1) on net-new `[Verified]` TST lacking passing test evidence outside `LEGACY_UNBACKED_VERIFIED_TSTS` ratchet baseline (Criterion 2).
* **Component 2 (`tools/jira_util.py` & Höchstmaß mirror)**:
  - Update `auto_verify_living_docs_on_stage5()`: check for available passing test evidence before promoting status; keep unbacked/failing items as `[Implemented]`.
* **Component 3 (`.agents/skills/stage5-verification/SKILL.md` & `docs/tests.md`)**:
  - Align Stage 5 documentation with evidence-conditioned promotion; update line 34 of `docs/tests.md`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Medium Process Risk, Zero Runtime Risk)** — tooling and governance logic; executed by **`subagent_flash`**.
* **Step 1: Test Result Ingestion & Mapping**: Implement `parse_junit_xml_results()` and tag correlator in `tools/verify_requirement_governance.py`.
* **Step 2: Verification Evidence Gate & Ratchet Baseline**: Add `verify_verified_test_evidence()`, `LEGACY_UNBACKED_VERIFIED_TSTS` baseline, and `--check-verified-evidence` CLI mode in `tools/verify_requirement_governance.py`.
* **Step 3: Evidence-Conditioned Stage 5 Promotion**: Update `auto_verify_living_docs_on_stage5()` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
* **Step 4: Skill & Living Documentation Update**: Update `.agents/skills/stage5-verification/SKILL.md` and `docs/tests.md`; promote `REQ-PRO-099`/`TST-PRO-099` to `[Implemented]`.
* **Step 5: Unit Tests**: Author comprehensive unit tests in `tools/test_verify_requirement_governance.py` and `tools/test_jira_accounts.py`.
* **Tier 1 Test Command**:
  ```bash
  python3 -m unittest tools/test_verify_requirement_governance.py tools/test_jira_accounts.py && python3 tools/verify_requirement_governance.py --check-verified-evidence
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests and `--check-verified-evidence` on mock passing/failing XML suites.
* **Rollback**: Branch isolation (`feature/ATT-8143`) allows complete revert without impact on `develop` or sprint branch.
