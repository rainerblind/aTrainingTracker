---
name: jira-workflow
description: Guides role-safe issue tracker operations, composite stage transitions (submit-stage and gate-verdict), subtask lifecycle management, and human gate governance.
---

# Skill: jira-workflow

## Overview
This skill provides standardized guidance for interacting with issue tracking systems (such as Jira, GitHub Issues, or Linear) in accordance with the project's Agile ASPICE workflow rules and multi-account CLI tooling (`tools/workflow_util.py`).

## Roles & Persistent Subagent Pairing (Core Rule 7)
* **`agent1` (Implementer)**: Role used during a stage's Doing turn for authoring deliverables, submitting stages (`--as agent1 submit-stage`), and constructing code.
* **`agent2` (Auditor)**: Independent role used during a stage's Gate 1–5 Review turn (`--as agent2 gate-verdict`). Hardwired in `gate-verdict` to prevent self-audit.
* **Persistent Asymmetric Zipper Pairing**: Maintain a dedicated pair of persistent subagents (`subagent_pro` and `subagent_flash`). Reuse them across all 5 stages via message passing, alternating roles with fused Gate-to-Next-Stage handoffs:
  * **Stage 1**: `subagent_pro` (`agent1` Doing) $\rightarrow$ `subagent_flash` (`agent2` Gate 1 + fused Stage 2 Doing)
  * **Stage 2**: `subagent_flash` (`agent1` Doing) $\rightarrow$ `subagent_pro` (`agent2` Gate 2 + fused Stage 3 Doing)
  * **Stage 3**: `subagent_pro` (`agent1` Doing) $\rightarrow$ `subagent_flash` (`agent2` Gate 3 + fused Standard Stage 4 Doing)
  * **Stage 4**: `subagent_flash` (`agent1` Doing) $\rightarrow$ `subagent_pro` (`agent2` Gate 4) *(or `pro`/`flash` for High-Risk)*
  * **Stage 5**: `subagent_flash` (`agent1` Doing) $\rightarrow$ `subagent_pro` (`agent2` Gate 5 + squash-merge onto `sprint/<SPRINT_NAME>`)
* **`coordinator` (Orchestrator)**: Backlog grooming, subtask creation, sprint queries. Cannot close tickets or override auditor verdicts.
* **`human` (User / Gatekeeper)**: Holds exclusive authority for approving release gates and transitioning parent tickets to completed.

## Composite Stage-Transition Commands (Preferred — 1 CLI Call per Stage/Gate)
```bash
# 1. Submit a stage deliverable as agent1 (1|analysis, 2|test-spec, 3|impl-plan, 4|impl, 5|test):
python3 tools/workflow_util.py --as agent1 submit-stage <PARENT_KEY> <STAGE> <TEXT_OR_@FILE>

# 2. Record an independent Gate 1-5 audit verdict as agent2 (PASS or REVISION):
python3 tools/workflow_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS "<AUDIT_REPORT_OR_@FILE>"
python3 tools/workflow_util.py --as agent2 gate-verdict <SUBTASK_KEY> REVISION "<AUDIT_REPORT_OR_@FILE>"

# 3. Record Stage 6 ('Final Review (Agent)') Cold Carbon verdict as agent2 (PASS or BOUNCE):
python3 tools/jira_util.py --as agent2 final-review-verdict <PARENT_KEY> PASS "<EXECUTIVE_SUMMARY_OR_@FILE>"
python3 tools/jira_util.py --as agent2 final-review-verdict <PARENT_KEY> BOUNCE "<DEFECT_REPORT_OR_@FILE>" --bounce-target=implementation
```

## Common Issue Tracking Commands
```bash
# Show parent issue details, comments, and subtasks
python3 tools/workflow_util.py show <KEY>

# Check issue status
python3 tools/workflow_util.py status <KEY>

# Search issues with query (always in rank order)
python3 tools/workflow_util.py search "sprint = <SPRINT_ID> ORDER BY rank ASC"

# Check gate status before Stage 4 implementation
python3 tools/workflow_util.py check-gate <SUBTASK_KEY>

# List all project Epics with Done/Open child ticket counts (Canonical Epic Taxonomy)
python3 tools/jira_util.py list-epics

# Close a completed Milestone Epic with triple guardrail enforcement (coordinator/human only, requires 0 open children)
python3 tools/jira_util.py --as coordinator close-epic <EPIC_KEY>

# Recalculate living documentation master index tables (or use verify_requirement_governance.py --sync-indexes)
python3 tools/jira_util.py recalc-docs

# Run commands under the sprint mutual exclusion lock (REQ-PRO-041.2)
python3 tools/parallel_sprint_lock.py <command>
```

## Critical Governance Invariants
1. **Parent Final Review Gate & Completion Prohibition**: Parent tickets mandate human evaluation. AI agents are strictly forbidden from transitioning parent tickets to completed / done.
2. **Triple Guardrail on Epic Closure (`REQ-PRO-040`)**: Milestone Epics can be closed via `close-epic <KEY>` only under `--as coordinator` or `human`, only if the issue type is `Epic`, and only if exactly 0 open child tickets remain. Standard parent tickets remain 100% blocked from completion by AI agents.
3. **Parallel Sprint Execution Across Disjoint Epics (`REQ-PRO-041`)**: Up to 2 tickets may run concurrently across disjoint Parent Epics using `Workspace: "share"` worktrees. All heavy Gradle test runs and Stage 5 squash-merges + index recalculations serialize under POSIX `flock` on `/tmp/att_build_merge.lock` (`python3 tools/parallel_sprint_lock.py <cmd>`).

## Deployment Modes: Jira Cloud vs. Jira Data Center (REQ-PRO-039)
The tooling supports both **Jira Cloud** (default) and enterprise on-premise **Jira Data Center** / **Jira Server**:
* **Configuration**: Set `deployment_type: "datacenter"` in `hoechstmass_config.json` or `JIRA_DEPLOYMENT=datacenter` in `.env.jira`.
* **Authentication**: In Data Center mode, configure Personal Access Tokens (PAT) via `Authorization: Bearer <PAT>` using `JIRA_API_TOKEN` / `JIRA_TOKEN` without username, or Basic auth when `JIRA_USER` is configured.
* **Corporate CA Bundles**: Support custom corporate CA certificates via `JIRA_CA_BUNDLE=/path/to/ca.crt` and toggle verification via `JIRA_SSL_VERIFY=true|false`.
* **Assignee Dispatch**: Data Center assignment emits `{"name": "<username>"}` (via `JIRA_<ROLE>_NAME` or username) instead of `{"accountId": "..."}`.
* **Epic Link Custom Field**: In Data Center mode, top-level issues link to Epics via custom field (default `customfield_10014`, configurable via `JIRA_EPIC_LINK_FIELD`), while sub-tasks strictly retain `fields.parent`.
* **JQL Search Routing**: Automatically queries `/rest/api/2/search` in Data Center mode vs `/rest/api/3/search/jql` in Cloud mode.
