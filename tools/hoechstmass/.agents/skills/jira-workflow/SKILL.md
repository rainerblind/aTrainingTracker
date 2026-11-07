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
```
