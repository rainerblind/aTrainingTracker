---
name: stage1-analysis
description: Executes Stage 1 (Problem Domain & Root Cause Analysis) of the agile ASPICE lifecycle. Conducts forensic investigation, Chesterton's Fence requirement archaeology, scope bounding, and generates the Stage 1 Analysis deliverable.
---

# Skill: stage1-analysis

## Overview
This skill guides the agent through **Stage 1 (Analysis)** of the engineering lifecycle. The objective is to understand the problem domain completely before proposing test specifications or implementation code.

## Key Responsibilities
1. **Forensic Root Cause Analysis (RCA)**:
   - For defects: Investigate stack traces, system logs, SQLite table constraints, thread concurrency, and Android framework lifecycles.
   - For features: Perform gap analysis against current architectural components.
   - **Avoid Redundant Diagnostic Builds (Rule 24)**: Before invoking heavy Gradle tasks (e.g. `./gradlew signingReport`, dependency trees), check ticket comments, previous sprint retro documents, git logs, and local configuration.
2. **Chesterton's Fence Archaeology (`REQ-PRO-022`)**:
   - Inspect git history (`git log -S <REQ-ID> docs/requirements.md`) before proposing modifications to existing requirements.
   - If modifying an existing requirement, populate the 4 mandatory fields:
     - `Original Requirement ID & Target`
     - `Historical Origin & Commit Trace`
     - `Root Reason for Existing Formulation`
     - `Preservation of Core Invariants`
3. **User Scope Grounding (`ATT-1250`)**:
   - Explicitly delineate in-scope objectives from out-of-scope adjacent refactorings to prevent scope creep.
4. **Deliverable Production & Synchronous Parent Stage Progression (Rule 3 / Rule 45)**:
   - Author `docs/engineering/analysis/<TICKET_KEY>_analysis.md` using `templates/analysis_template.md`.
   - Update the Stage 1 Jira subtask (`[Analysis]`) Description with the complete text.
   - Transition subtask to `In Überprüfung` and run Gate 1 audit (`tools/review_agent.py audit <KEY>`).
   - Upon Gate 1 approval (`RECOMMEND PASS`), transition subtask directly to `Erledigt` via transition `freigabe` (`python3 tools/jira_util.py move <SUBTASK_KEY> freigabe`), which automatically advances the parent ticket from `Analysis` to `Test Spec`.
   - **One Commit per Ticket (Rule 44)**: Do not create a separate intermediate git commit for Stage 1; keep changes on the working branch until the single atomic ticket commit at the end of Stage 5 (or squash when merging into `sprint/<SPRINT_NAME>`).

## Deliverable Template
Use the standardized markdown template located at:
`templates/analysis_template.md`
