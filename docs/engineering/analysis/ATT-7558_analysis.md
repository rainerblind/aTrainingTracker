# Stage 1 Analysis: ATT-7558 - Make SUP.1 QA audits independent and track corrective actions

**Ticket**: [ATT-7558](https://rainerblind.atlassian.net/browse/ATT-7558)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7558`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Independent ASPICE evaluation (2026-11-12) finding F1 revealed three critical gaps in ASPICE SUP.1 (Quality Assurance) process compliance:
1. **Auditor Independence Deficit**: Sprint QA audit reports in `docs/engineering/qa_audits/` record `Auditor Role: coordinator`. Because the Coordinator actively administers the sprint and backlog, it lacks organizational independence from the audited work products.
2. **Under-Sampling**: Current audits sample a static count of 2 tickets, falling short of representative coverage for sprints containing 10–20+ tickets ($\ge 10\%$ sample required).
3. **Open-Loop Non-Compliance Handling**: Audits for sprints 2026-46.3 and 2026-46.4 recorded `NON-COMPLIANT` verdicts across several criteria, but Section 4 merely noted generic follow-up recommendations without creating or linking Jira corrective-action tickets. Furthermore, Ceremony 1 (Sprint Planning) never verifies closure of previous audit non-compliances.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Root Cause**: `tools/jira_util.py` (`qa_audit`) originally defaulted to `role="coordinator"` and fixed `sample_size=2` during the bootstrap of `REQ-PRO-061` (ATT-6154). No programmatic validation existed in `verify_requirement_governance.py` to enforce independent auditor identity, representative sample proportions, or Jira issue links for non-compliant findings.
- **Independent Peer Review & Verification**:
  - Inspected `tools/jira_util.py` (lines 2698, 2789): `auditor_role` defaults to `"coordinator"`; `VALID_ROLES` omits a dedicated `qa` role; `sample_size` defaults to 2; Section 4 generates plain text without issue links.
  - Inspected `.agents/skills/sprint-planner/SKILL.md` (lines 195–201): Explicitly commands `python3 tools/jira_util.py --as coordinator qa-audit`.
  - Inspected `tools/verify_requirement_governance.py` (`verify_qa_audits`, lines 613–700): Checks only directory presence, `COMPLIANT`/`NON-COMPLIANT` tokens, and $\ge 2$ tickets. Does not check auditor independence, sample percentage, or corrective-action issue keys.
  - Inspected `docs/engineering/qa_audits/`: Sprints 2026-46.3 and 2026-46.4 are marked `NON-COMPLIANT` for `ATT-7025`, `ATT-6506`, `ATT-5088`, and `ATT-5096` with zero Jira tickets linked.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Introduce dedicated independent QA auditor role (`qa` / `agent2` as auditor) in `jira_util.py`, `hoechstmass/`, `.agents/rules/aspice_governance.md`, `jira-workflow` skill, and `docs/project_governance.md`.
  2. Enhance `qa-audit` in `jira_util.py` to default to `qa`, enforce dynamic sample size $\ge 10\%$ (`max(2, ceil(N * 0.10))`) with documented rationale, and link each non-compliant finding to a Jira corrective action ticket.
  3. Upgrade `verify_qa_audits` in `verify_requirement_governance.py` to enforce independent auditor role, $\ge 10\%$ sample size, and mandatory Jira issue key linkage for all non-compliant findings.
  4. Update `sprint-planner/SKILL.md` Ceremony 1 to mandate verifying closure of prior audit corrective actions, and Ceremony 2 to run `qa-audit` under independent QA role.
  5. Retroactively open/link corrective action tickets for 2026-46.3 and 2026-46.4 audit findings and update the corresponding markdown records.
  6. Extend Python unit tests in `test_jira_accounts.py`, `test_verify_requirement_governance.py`, and `test_hoechstmass_tools.py`.
* **Out-of-Scope Non-Goals**:
  1. No changes to Android application runtime code in `app/src/main/`.
  2. No modification of git branching or parallel merge locking mechanics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-091` (*Independent ASPICE SUP.1 QA Auditing, Representative Sampling & Corrective Action Tracking*), refining and extending `REQ-PRO-061` (*Sprint-Level QA Process-Compliance Audit*). Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/`, `.agents/skills/sprint-planner/SKILL.md`, and `docs/engineering/qa_audits/`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.3 via ATT-6154 (`REQ-PRO-061`). Finding F1 in ASPICE independent evaluation (2026-11-12) identified that `coordinator` authored audits, under-sampled, and left non-compliances open-loop.
* **Root Reason for Existing Formulation**: Early tooling prioritized basic automated markdown generation over strict organizational independence and closed-loop corrective action tracking.
* **Preservation of Core Invariants**: Existing CLI commands (`qa-audit`, `--check-qa-audits`) remain backward-compatible; pure Python standard library; zero production Android runtime code changes.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-091` (`REQ-PRO-091.1`–`REQ-PRO-091.5`), extending `REQ-PRO-061`.
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/jira-workflow/SKILL.md`, `.agents/rules/aspice_governance.md`, `docs/project_governance.md`, `docs/engineering/qa_audits/`.
* **Verification Impact & Test Surface**: Unit tests in `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
* **Risk Assessment & Invariants**: Low risk. Strengthens ASPICE SUP.1 evidence and audit integrity without affecting mobile runtime or build pipeline.

---

## 6. Architectural Strategy & High-Level Solution
1. **Dedicated QA Role**: Add `"qa"` to `VALID_ROLES` in `tools/jira_util.py` (resolving to independent auditor credentials or `agent2`), rejecting execution of `qa-audit` under `coordinator` or `agent1`.
2. **Representative Sampling**: In `qa_audit`, calculate `target_sample = max(2, math.ceil(len(parent_issues) * 0.10))` and document the statistical sampling rationale in Section 1.
3. **Corrective Action Linkage**: In `format_qa_audit_markdown`, mandate that every non-compliant finding in Section 4 includes a Jira issue link (`[ATT-XXXX](...)`), and update `verify_qa_audits` to enforce this pattern.
4. **Ceremony 1 Closed-Loop Review**: Add a mandatory verification step in `sprint-planner/SKILL.md` Ceremony 1 requiring the Coordinator to check that all open QA corrective action tickets from prior audits are resolved or scheduled.
5. **Retroactive Remediation**: Generate Jira corrective-action tickets for the 4 non-compliant tickets in 2026-46.3 and 2026-46.4, and update their audit markdown records.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% unit test pass rate; zero Android production runtime code changes.
  2. Pure Python 3 standard library (zero external pip packages).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Process tooling, governance verification, and markdown records).
