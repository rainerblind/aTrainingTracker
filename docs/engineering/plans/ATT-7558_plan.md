# Stage 3: Implementation Plan - ATT-7558: Make SUP.1 QA audits independent and track corrective actions

**Ticket**: [ATT-7558](https://rainerblind.atlassian.net/browse/ATT-7558)
**Sub-task**: `[Impl-Plan]`
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)
**Target Release**: `V5.1.0`
**Active Sprint**: `2026-46.6`
**Requirement Mapping**: `REQ-PRO-091` (`REQ-PRO-091.1`, `REQ-PRO-091.2`, `REQ-PRO-091.3`, `REQ-PRO-091.4`, `REQ-PRO-091.5`)
**Test Mapping**: `TST-PRO-091` (`TST-PRO-091.1`, `TST-PRO-091.2`, `TST-PRO-091.3`, `TST-PRO-091.4`, `TST-PRO-091.5`, `TST-PRO-091.6`)
**Branch**: `feat/ATT-7558`
**Author**: AI Agent 1 (Implementer)
**Reviewer**: AI Agent 2 (Tester / Reviewer)
**Date**: 2026-11-14

---

## 1. Executive Summary & Architecture Overview (SWE.2)

### 1.1 Goal
Remediate ASPICE SUP.1 finding F1 to ensure quality assurance audits possess structural independence, statistically grounded dynamic ticket sampling, closed-loop corrective action tracking, and formal verification during sprint planning. Additionally, retroactively remediate historical audit reports (`sprint_2026-46.3.md` and `sprint_2026-46.4.md`) to establish full traceability across historical non-compliant findings.

### 1.2 Target Architecture & Affected Modules
The changes span four operational subsystems:
1. **Audit CLI Tooling (`tools/jira_util.py`)**:
   - Introduce `qa` into valid roles (`VALID_ROLES`, `ROLE_ACCOUNT_IDS`).
   - Update `qa_audit` and `format_qa_audit_markdown` to require `qa` or `agent2` role.
   - Reject execution if `--as coordinator` or `--as agent1` is specified for `qa_audit`.
   - Calculate sample size dynamically as $\ge \max(2, \lceil 0.10 \times N \rceil)$ when not explicitly passed, documenting the rationale in Section 1.
   - Add structured corrective action placeholders (`[ATT-XXXX]`) to Section 4 for any non-compliant findings.
2. **Governance Audit Verification (`tools/verify_requirement_governance.py`)**:
   - Enhance `verify_qa_audits(sprint_name, repo_root)` to:
     - Verify auditor role in markdown is independent (`qa` or `agent2`, rejecting `coordinator` or `agent1`).
     - Verify sampled ticket count meets $\ge \max(2, \lceil 0.10 \times N \rceil)$ or has documented justification.
     - Verify Section 4 of non-compliant audits contains valid Jira issue key references `[ATT-\d+]` for each non-compliant finding.
3. **Sprint Planner Ceremony Specification (`.agents/skills/sprint-planner/SKILL.md`)**:
   - Update Ceremony 1 (Sprint-Start Planning & Audit) checklist and steps to mandate verifying the closure of corrective action tickets from the prior sprint's QA audit.
4. **Historical QA Audit Artifacts (`docs/engineering/qa_audits/`)**:
   - Update `sprint_2026-46.3.md` and `sprint_2026-46.4.md` Section 4 to map non-compliant findings to remediation tickets (`ATT-7025`, `ATT-6506`, `ATT-5088`, `ATT-5096`).
5. **Unit & Regression Test Suites**:
   - Update `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py` to cover all new invariants and behaviors.

---

## 2. Invariants & Guardrails

- **Invariant 1 (Standard Library Only)**: All tooling logic in `tools/jira_util.py` and `tools/verify_requirement_governance.py` must use pure Python 3 standard library modules (`math`, `re`, `argparse`, `pathlib`, `json`, etc.) without third-party pip dependencies.
- **Invariant 2 (Zero Android Runtime Footprint)**: No modifications may be made to `app/src/main/` or Android application runtime classes.
- **Invariant 3 (ASPICE Role Independence)**: A QA audit cannot be conducted by `coordinator` or `agent1`. The allowed roles are `qa` or `agent2`.
- **Invariant 4 (Sample Size Lower Bound)**: The audited sample count $S$ must satisfy $S \ge \max(2, \lceil 0.10 \times N \rceil)$, where $N$ is the total number of parent tickets in the sprint.
- **Invariant 5 (Closed-Loop Non-Compliance)**: Every NON-COMPLIANT finding must specify an assigned Jira tracking issue `[ATT-XXXX]`.

---

## 3. Atomic Implementation Steps (Stage 4 Plan)

### Step 1: Update `tools/jira_util.py` Role Definitions & QA Audit Sampling
- **Files**: `tools/jira_util.py`
- **Actions**:
  1. Add `"qa"` to `VALID_ROLES`:
     ```python
     VALID_ROLES = ["agent1", "agent2", "coordinator", "qa"]
     ```
  2. Map `"qa"` in `ROLE_ACCOUNT_IDS` to the designated QA account ID (or alias to `agent2` default ID `63b82d41071d2b8b9ef5e966`).
  3. In `qa_audit(sprint_arg, sample_size, out_path, role, as_role, repo_root)`:
     - Check auditor role: enforce role is `"qa"` or `"agent2"`. If role is `"coordinator"` or `"agent1"`, raise `ValueError` / error message and exit with non-zero status.
     - Calculate dynamic sample size if not provided:
       ```python
       min_sample = max(2, math.ceil(len(parent_issues) * 0.10))
       sample_size = min_sample if sample_size is None else max(sample_size, min_sample)
       ```
     - Pass `total_tickets=len(parent_issues)` and `sample_size=sample_size` to `format_qa_audit_markdown`.
  4. In `format_qa_audit_markdown`:
     - Update Section 1 header to display Auditor Role (e.g. `Auditor Role: qa (Quality Assurance Auditor, independent of Coordinator/Implementer)`).
     - Include sampling rationale: `Sample Size: {sample_count} / {total_count} tickets (>= 10% dynamic threshold enforced)`.
     - Update Section 4: If findings are NON-COMPLIANT, format each finding with an explicit `Corrective Action Ticket: [ATT-XXXX]` field.

### Step 2: Enhance `tools/verify_requirement_governance.py` QA Audit Verification
- **Files**: `tools/verify_requirement_governance.py`
- **Actions**:
  1. In `verify_qa_audits(sprint_name, repo_root)`:
     - Parse Section 1 `Auditor Role: ...`. Validate that it contains `qa` or `agent2` and does not match `coordinator` or `agent1`.
     - Parse Section 1 `Sample Size: (\d+) / (\d+)`. Validate that `sample_size >= max(2, math.ceil(total_count * 0.10))`.
     - If overall status is `NON-COMPLIANT` or individual items are `FAIL` / `NON-COMPLIANT`, parse Section 4 to ensure each finding references a valid Jira issue key matching pattern `ATT-\d+`.
     - Emit clear governance errors if any condition fails.

### Step 3: Update Historical QA Audit Reports
- **Files**: `docs/engineering/qa_audits/sprint_2026-46.3.md`, `docs/engineering/qa_audits/sprint_2026-46.4.md`
- **Actions**:
  1. In `docs/engineering/qa_audits/sprint_2026-46.3.md`:
     - In Section 4 Corrective Actions, link the missing Stage 2/3 and gate audit non-compliances to corrective action tickets `ATT-7025` and `ATT-6506`.
  2. In `docs/engineering/qa_audits/sprint_2026-46.4.md`:
     - In Section 4 Corrective Actions, link the Gate 5 transition non-compliances to corrective action tickets `ATT-5088` and `ATT-5096`.

### Step 4: Update Sprint Planner Ceremony 1 Guidelines
- **Files**: `.agents/skills/sprint-planner/SKILL.md`
- **Actions**:
  1. In Ceremony 1 (Sprint-Start Planning & Risk Audit), add an explicit mandatory step to verify that all corrective action Jira tickets from the previous sprint's QA audit report are either completed (`Erledigt`) or scheduled in the active sprint backlog.

### Step 5: Update Unit Tests & Mock Fixtures
- **Files**: `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`
- **Actions**:
  1. In `tools/test_jira_accounts.py`:
     - Test `qa_audit` rejects `--as coordinator` and `--as agent1`.
     - Test `qa_audit` accepts `--as qa` and `--as agent2`.
     - Test dynamic sample size calculation ($\ge \max(2, \lceil 0.10 \times N \rceil)$).
  2. In `tools/test_verify_requirement_governance.py`:
     - Test `verify_qa_audits` validates auditor independence.
     - Test `verify_qa_audits` catches insufficient sample size.
     - Test `verify_qa_audits` catches missing Jira issue links in Section 4 for non-compliant audits.

---

## 4. Verification & Validation Plan (Stage 5 Blueprint)

### 4.1 Automated Test Execution Commands
```bash
# 1. Run governance tool suite tests
python3 -m unittest tools/test_jira_accounts.py -v
python3 -m unittest tools/test_verify_requirement_governance.py -v

# 2. Run living doc governance check and index verification
python3 tools/verify_requirement_governance.py --check-all
python3 tools/verify_requirement_governance.py --sync-indexes

# 3. Test qa-audit CLI dry-run / generation with mock data
python3 tools/jira_util.py --as qa qa-audit --sprint "2026-46.6" --help
```

### 4.2 Living Documentation Transition
- Update `REQ-PRO-091` status in `docs/requirements/req_process_stability.md` from `[Specified]` to `[Verified]`.
- Update `TST-PRO-091` status in `docs/tests/tst_process_stability.md` from `[Specified]` to `[Verified]`.
- Run `python3 tools/verify_requirement_governance.py --sync-indexes` to maintain 100% index parity.

### 4.3 Walkthrough Document
- Author `docs/engineering/walkthroughs/ATT-7558_walkthrough.md` documenting:
  - Role separation enforcement and validation errors for disallowed roles.
  - Dynamic sample size formula and test verification.
  - Section 4 corrective action tracking and retroactive audit link validation.
  - Test run outputs confirming all tests pass.

---

## 5. Gate 3 Readiness Checklist

| Criterion | Status | Description |
|---|---|---|
| Traceability to REQ-PRO-091 | PASS | All 5 sub-requirements mapped to implementation steps |
| Traceability to TST-PRO-091 | PASS | All 6 test cases mapped to test steps and commands |
| Independence Guardrails | PASS | Explicit rejection of coordinator/agent1 for QA audits |
| Historical Data Remediation | PASS | Specific audit files and Jira keys identified |
| Clean Architecture & Zero Drift | PASS | Confined strictly to `tools/`, `docs/`, and `.agents/skills/` |
