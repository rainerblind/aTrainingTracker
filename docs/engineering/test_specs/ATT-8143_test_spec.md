# Stage 2: Requirement & Test Specification - ATT-8143: Derive Verified status from test results

**Ticket**: [ATT-8143](https://rainerblind.atlassian.net/browse/ATT-8143)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-PRO-099` (*Test Result-Driven Verification Status & Evidence Gating*)  
**Test Spec ID**: `TST-PRO-099`  
**Branch**: `feature/ATT-8143` | **Author**: AI Agent 2 (Reviewer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-PRO-099)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-099.1 (Test Result Ingestion)**: `tools/verify_requirement_governance.py` SHALL ingest machine-readable test results (JUnit XML files under `app/build/test-results/**/TEST-*.xml` or test result archives) and correlate testcases to `TST-XXX` IDs.
* **REQ-PRO-099.2 (Failing Test Gating & Evidence CLI)**: `tools/verify_requirement_governance.py` SHALL implement `--check-verified-evidence`. Any `[Verified]` TST whose associated testcase reports `failure` or `error` in test results MUST fail with exit code 1 naming the TST.
* **REQ-PRO-099.3 (Unbacked TST Gating & Ratchet Baseline)**: Net-new or modified `TST-XXX` specifications lacking passing test evidence or an attested manual verification protocol SHALL NOT be marked `[Verified]`. Legacy unbacked TSTs (405 items) SHALL be tolerated in `LEGACY_UNBACKED_VERIFIED_TSTS` without failing builds.
* **REQ-PRO-099.4 (Stage 5 Evidence-Conditioned Promotion)**: `auto_verify_living_docs_on_stage5()` in `tools/jira_util.py` (and Höchstmaß mirror) SHALL condition status promotion to `[Verified]` on verified test evidence; unbacked or failing items SHALL remain `[Implemented]`.
* **REQ-PRO-099.5 (Default Check Decoupling)**: Default governance checks and git hooks SHALL operate cleanly in clean worktrees before Gradle build outputs exist, reserving strict XML verification to `--check-verified-evidence` and Stage 5 verification gating.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-099` & `TST-PRO-099`. Targets `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `.agents/skills/stage5-verification/SKILL.md`.
* **Historical Origin & Commit Trace**: Sprint `2026-46.8` via ticket `ATT-8143` under Epic `ATT-8138` (ASPICE 4.0 Audit Remediation). Resolves finding `M1`.
* **Root Reason for Existing Formulation**: Prior workflow automatically flipped living doc status to `[Verified]` upon `submit-stage 5` without verifying whether tests passed or even existed.
* **Preservation of Core Invariants**: Retains living doc synchronization while grounding `[Verified]` in authentic test execution results; ratchet baseline preserves CI across legacy backlog; zero Android runtime changes; Rule 3 Human Gate intact.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Failing Test Evidence Detection)**: *Given* a TST marked `[Verified]` whose tagged test fails in JUnit XML, *When* `verify_requirement_governance.py --check-verified-evidence` runs, *Then* the check fails with exit code 1 naming the failing TST ID.
* **Criterion 2 (Unbacked TST Rejection on Stage 5)**: *Given* a new TST lacking passing test evidence, *When* Stage 5 is submitted, *Then* the specification remains `[Implemented]` and missing evidence is reported.
* **Criterion 3 (Ratchet Baseline Tolerance)**: *Given* the 405 legacy unbacked TST specifications, *When* `--check-verified-evidence` runs, *Then* legacy items within the ratchet baseline do not trigger failure.

---

## 4. Test Specification (TST-PRO-099)
* **TST-PRO-099.1 (JUnit XML Parser & Mapping Test)**: In `tools/test_verify_requirement_governance.py`, verify parsing JUnit XML files and mapping testcases to `TST-XXX` IDs with pass/fail/error counts.
* **TST-PRO-099.2 (Failing Evidence Rejection Test)**: In `tools/test_verify_requirement_governance.py`, verify `verify_verified_test_evidence()` rejects `[Verified]` when testcases report failure or error.
* **TST-PRO-099.3 (Unbacked TST Rejection & Ratchet Test)**: Verify net-new unbacked `[Verified]` TST fails while ratchet baseline items pass.
* **TST-PRO-099.4 (Stage 5 Evidence Gating Test)**: In `tools/test_jira_accounts.py`, verify `auto_verify_living_docs_on_stage5()` checks test evidence before promoting status.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-099.1` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-099.1` | Specified |
| `TST-PRO-099.2` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-099.2` | Specified |
| `TST-PRO-099.3` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-099.3` | Specified |
| `TST-PRO-099.4` | Unit | `tools/jira_util.py` | `REQ-PRO-099.4` | Specified |
