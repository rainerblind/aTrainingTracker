# Stage 1 Analysis: ATT-6156 - Add severity classification and problem trend metrics

**Ticket**: [ATT-6156](https://rainerblind.atlassian.net/browse/ATT-6156)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `feature/ATT-6156`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Under ASPICE SUP.9 (Problem Resolution Management) and MAN.3 (Project Monitoring and Control), defect tracking requires classifying problem severity (impact on system operation/user) independently of scheduling priority, as well as tracking problem resolution trends over time (Finding B-11). Currently, Jira issues carry scheduling priority (`Highest`..`Lowest`) but lack a mandatory, standardized severity classification (`Critical`, `Major`, `Minor`, `Cosmetic`). Furthermore, `sprint_metrics.py` computes gate escape rates and velocity, but aggregates no defect trends or bug counts by severity across sprint retrospectives.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `create_issue` in `tools/jira_util.py` allows creating Bug tickets (`10006`) without specifying problem severity, leading to ad-hoc title tags (`[High]`) without structured machine-readable metadata.
  * `tools/sprint_metrics.py` parses parent issues for story points and subtask gate comments, but lacks extraction of bug issues, severity distributions, and open/new/closed problem resolution trends.
* **Independent Hypothesis Verification**:
  * Live Jira API query confirms no dedicated `severity` customfield exists; severity must be technically standardized via labels (`severity-critical`, `severity-major`, `severity-minor`, `severity-cosmetic`) and description metadata.
  * Audit of `docs/engineering/metrics/process_metrics.jsonl` confirms zero problem trend fields across all 7 historical sprint records.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Mandate `--severity=critical|major|minor|cosmetic` in `jira_util.py create-issue` and `create_issue()` when creating Bugs (`10006`), tagging `severity-<level>` labels and embedding metadata in description.
  2. Implement problem trend extraction in `tools/sprint_metrics.py` and `tools/hoechstmass/tools/sprint_metrics.py` (`open_bugs`, `new_bugs`, `closed_bugs` by severity, defect resolution rate).
  3. Update `process_metrics.jsonl` schema and `format_metrics_markdown()` to report problem trends.
  4. Formulate formal requirement `REQ-PRO-063` and test specification `TST-PRO-063` in living documentation with 100% parity.
  5. Add unit tests in `tools/test_jira_accounts.py`, `tools/test_sprint_metrics.py`, and `tools/hoechstmass/tools/test_sprint_metrics.py`.
* **Out-of-Scope Non-Goals**:
  * Zero runtime Android production code modifications in `app/src/main/`.
  * Non-bug issue types (Features, Improvements, Tasks) are not required to specify severity.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-063` (*Problem Severity Classification & Defect Trend Metrics*). Targets `tools/jira_util.py`, `tools/sprint_metrics.py`, `docs/engineering/metrics/process_metrics.jsonl`, and `tools/hoechstmass/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6156 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-11.
* `Root Reason for Existing Formulation`: Sprints previously tracked only velocity and gate FTPR; problem resolution lacked quantitative severity telemetry.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; backward compatibility for non-bug issue creation preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Severity Taxonomy & Enforcement**: Standardize 4 ASPICE SUP.9 severity levels (`Critical`, `Major`, `Minor`, `Cosmetic`). In `create_issue`, fail with exit code 1 if `issuetype == Bug` and severity is omitted. Add label `severity-<level>`.
2. **Metrics Engine Extension**: In `parse_sprint_gate_metrics`, inspect sprint bugs, parse severity labels/tags, calculate `open_bugs`, `closed_bugs`, and `resolution_rate_percent`, and serialize under `problem_trends` in `process_metrics.jsonl`.
3. **Counterpart Parity & Documentation**: Replicate logic in `tools/hoechstmass/`; define `REQ-PRO-063` and `TST-PRO-063` with 100% parity across living docs.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Pure Python standard library; non-bug creation unaffected; zero Android runtime changes.
* **Risk Rating**: **LOW** — Developer tooling and quantitative process metrics extension.
