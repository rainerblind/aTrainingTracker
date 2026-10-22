---
name: stage1-analysis
description: Executes Stage 1 (Problem Domain & Root Cause Analysis) of the agile ASPICE lifecycle. Conducts forensic investigation, Chesterton's Fence requirement archaeology, scope bounding, and generates the Stage 1 Analysis deliverable.
---

# Skill: stage1-analysis

## Overview
This skill guides the agent through **Stage 1 (Analysis)** of the engineering lifecycle. The objective is to understand the problem domain completely before proposing test specifications or implementation code.

## Subagent Model Pairing ("Zipper Principle" & Persistent Ping-Pong — Core Rule 7)
At Ticket Start (`ATT-XXXX`), spawn two persistent `TypeName: "self"` subagents (`subagent_pro` with `Model: "pro"` and `subagent_flash` with `Model: "flash"`) that will be reused across all 5 stages via `send_message`:
- **Doing (`--as agent1`)**: **`subagent_pro`** (`TypeName: "self"`, `Model: "pro"`) — Open-ended forensic root-cause investigation, distinguishing symptoms from true causes, concurrency/lifecycle analysis, and blast-radius discovery require deep reasoning.
- **Gate 1 Review (`--as agent2`)**: **`subagent_flash`** (`TypeName: "self"`, `Model: "flash"`) — Independent verification of cited call sites, all 5 required analysis sections, and scope grounding, followed by executing `python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS ...` (warming `subagent_flash`'s context for Stage 2 Doing!).

## Key Responsibilities (`subagent_pro` as `agent1` — `pro`)
1. **Forensic Root Cause Analysis (RCA)**:
   - For defects: Investigate stack traces, system logs, SQLite table constraints, thread concurrency, and Android framework lifecycles.
   - For features: Perform gap analysis against current architectural components.
2. **Chesterton's Fence Archaeology (`REQ-PRO-022`)**:
   - Inspect git history (`git log -S <REQ-ID> docs/requirements.md`) before proposing modifications to existing requirements.
   - If modifying an existing requirement, populate the 4 mandatory fields:
     - `Original Requirement ID & Target`
     - `Historical Origin & Commit Trace`
     - `Root Reason for Existing Formulation`
     - `Preservation of Core Invariants`
3. **User Scope Grounding (`ATT-1250`)**:
   - Explicitly delineate in-scope objectives from out-of-scope adjacent refactorings to prevent scope creep.
4. **Single-Turn Stage Submission (`submit-stage`) & Concise Deliverable Cap (Rule 60)**:
   - Author `docs/engineering/analysis/<TICKET_KEY>_analysis.md` using `templates/analysis_template.md` in a **concise bullet-and-table format (target $\le 60\text{--}80$ lines)**—do not repeat the ticket description verbatim or add narrative filler.
   - Submit Stage 1 in a single composite CLI call (creates/updates `[Analysis]` sub-task and moves it to `In Überprüfung`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 1 @docs/engineering/analysis/<TICKET_KEY>_analysis.md
     ```
   - **One Commit per Ticket (Core Rule 8)**: Do not create an intermediate git commit for Stage 1; keep changes on the working branch until Stage 5.

## Gate 1 Independent Review Checklist (`subagent_flash` as `agent2` — `flash`, Fused with Stage 2 Doing)
Instruct the ticket's persistent `subagent_flash` (`Model: "flash"`) to audit the Stage 1 deliverable against the codebase using this checklist, record the verdict via `--as agent2 gate-verdict`, and **immediately execute Stage 2 Doing (`--as agent1`) in the same turn if Gate 1 is `PASS`**:
1. **Scrutinize Problem Domain & Analysis**:
   - For bug tickets: Stress-test RCA conclusions (symptoms vs. cause, call-site audit, reproducible traces).
   - For features/improvements: Verify deep domain comprehension, user motivation, scope boundaries, and architectural side effects.
   - **User Scope Grounding (`ATT-1250`)**: Ground review expectations strictly in the user's specific problem statement and parent ticket description. Do NOT challenge deliverables or mandate out-of-scope refactorings, speculative edge cases, or theoretical redesigns not requested by the user.
2. **Call Site Audit**: Verify that all affected callers, classes, and resources are identified in the codebase.
3. **Requirement Mapping Audit**: Check mapped Requirement IDs against `docs/requirements.md`.
4. **System Invariants Verification**: Confirm explicit system invariants (what existing behavior, data precision, schema, or API contracts MUST NOT change).
5. **Risk Rating & Gate Verdict (`gate-verdict`) + Fused Stage 2 Continuation (Rule 60)**:
   - Assign risk rating (`LOW`, `MEDIUM`, `HIGH`) with technical justification and record the verdict in a single composite CLI call (which closes the sub-task to `Erledigt`, advances the parent ticket to `Test Spec`, and automatically injects `stage2-req-test-spec/SKILL.md` into stdout):
     ```bash
     python3 tools/jira_util.py --as agent2 gate-verdict <ANALYSIS_SUBTASK_KEY> PASS "<Audit Report>"
     ```
   - If `PASS`, **immediately proceed in the same turn** to execute Stage 2 (`--as agent1`) following the injected `stage2-req-test-spec/SKILL.md`. *(If `REVISION`, stop and report back.)*

## Deliverable Template
Use the standardized markdown template located at:
`templates/analysis_template.md`
