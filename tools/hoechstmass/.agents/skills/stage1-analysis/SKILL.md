---
name: stage1-analysis
description: Executes Stage 1 (Problem Domain & Root Cause Analysis) of the agile ASPICE lifecycle. Conducts forensic investigation, Chesterton's Fence requirement archaeology, scope bounding, and generates the Stage 1 Analysis deliverable.
---

# Skill: stage1-analysis

## Overview
This skill guides the agent through **Stage 1 (Analysis)** of the engineering lifecycle. The objective is to understand the problem domain completely before proposing test specifications or implementation code.

## Subagent Model Pairing ("Zipper Principle" — Core Rule 7)
Maintain an asymmetric pair of persistent subagents across the sprint:
- **Doing (`--as agent1`)**: **`subagent_pro`** (High-reasoning model tier) — Forensic root-cause investigation, distinguishing symptoms from true causes, concurrency/lifecycle analysis, and blast-radius discovery require deep reasoning.
- **Concurrent Background Pre-Warming & Gate 1 Review (`--as agent2`)**: **`subagent_flash`** (Fast-execution model tier, pre-warmed concurrently in background) — Independent verification of cited call sites, analysis sections, and scope grounding, followed by executing the gate-verdict command:
  ```bash
  python3 tools/workflow_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS "<Audit Report>"
  ```
  Upon `PASS`, immediately proceeds to execute Stage 2 Doing (`--as agent1`) in the same turn.

## Key Responsibilities (`subagent_pro` as `agent1`)
1. **Forensic Root Cause Analysis (RCA)**:
   - For defects: Investigate stack traces, system logs, database constraints, thread concurrency, and framework lifecycles.
   - For features: Perform gap analysis against current architectural components.
2. **Independent Hypothesis Verification (4-Eyes Principle)**:
   - When a ticket already includes a pre-formulated problem domain or technical proposal, treat it strictly as a hypothesis.
   - Independently audit the hypothesis against the live codebase: cross-check cited files, line ranges, and architectural assumptions to identify blind spots or potential regressions.
   - Include an explicit **"Independent Peer Review & Verification"** subsection in the Stage 1 deliverable (`docs/engineering/analysis/<TICKET_KEY>_analysis.md`).
3. **Chesterton's Fence Archaeology & Side-Effect Audit**:
   - Inspect git history before proposing modifications to existing requirements or architecture.
   - If modifying an existing requirement, populate the 4 mandatory fields:
     - `Original Requirement ID & Target`
     - `Historical Origin & Commit Trace`
     - `Root Reason for Existing Formulation`
     - `Preservation of Core Invariants`
   - Trace all callers, decorators, and shared state flows to audit cross-cutting blast radius.
4. **Scope Grounding & Complexity Split Guardrail**:
   - Explicitly delineate in-scope objectives from out-of-scope adjacent refactorings to prevent scope creep.
   - Audit ticket complexity against the 1/2/3/5+ Story Point scale. If true complexity exceeds 3 SP ($\ge 5\text{ SP}$ due to cross-system coupling), immediately flag the ticket for splitting before proceeding to Stage 2.
5. **Single-Turn Stage Submission (`submit-stage`) & Concise Deliverable Cap**:
   - Author `docs/engineering/analysis/<TICKET_KEY>_analysis.md` using `templates/analysis_template.md` in a **concise bullet-and-table format (target $\le 60\text{--}80$ lines)**.
   - Submit Stage 1 in a single composite CLI call:
     ```bash
     python3 tools/workflow_util.py --as agent1 submit-stage <TICKET_KEY> 1 @docs/engineering/analysis/<TICKET_KEY>_analysis.md
     ```

## Gate 1 Independent Review Checklist (`subagent_flash` as `agent2`)
Instruct `subagent_flash` to audit the Stage 1 deliverable against the codebase using this checklist:
1. **Scrutinize Problem Domain & Analysis**:
   - For defects: Stress-test RCA conclusions (symptoms vs. cause, call-site audit, reproducible traces).
   - For features: Verify domain comprehension, user motivation, scope boundaries, and architectural side effects.
2. **Call Site Audit**: Verify that all affected callers, classes, and resources are identified in the codebase.
3. **Requirement Mapping Audit**: Check mapped Requirement IDs against living documentation.
4. **System Invariants Verification**: Confirm explicit system invariants (what existing behavior, data precision, schema, or API contracts MUST NOT change).
5. **Risk Rating & Gate Verdict (`gate-verdict`)**:
   - Assign risk rating (`LOW`, `MEDIUM`, `HIGH`) with technical justification and record verdict via `--as agent2 gate-verdict`.
   - If `PASS`, **immediately proceed in the same turn** to execute Stage 2 (`--as agent1`).
