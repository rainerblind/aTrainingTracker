# Stage 1 Analysis: ATT-7101 — Introduce 'Final Review (Agent)' Cold Carbon Review Stage with 3-Bounce Healing Before 'Final Review (Human)'

## 1. Problem Domain & Root Cause Analysis

### 1.1 Background & Motivation
During Sprint `2026-46.1` (`ATT-6655`) and Sprint `2026-46.2`, tickets completed Stages 1–5 (`Analysis` $\rightarrow$ `Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test`) using persistent Epic-scoped `pro`/`flash` subagent pairs (`Rules 60–63`). While persistent subagent pairing yields high context continuity and throughput across Stages 1–5, both subagents in the pair share the iterative implementation trajectory and conversational history of the ticket. Furthermore, once Stage 5 (`[Test]`) passed Gate 5, `tools/jira_util.py` transitioned the parent ticket directly to `Final Review (Human)` and assigned it to `human`, without a zero-context, fresh-eyes adversarial audit of the integrated commit on `sprint/<SPRINT_NAME>`.

To introduce an orthogonal Swiss Cheese defensive barrier before human review, the Jira workflow has been updated with two distinct post-`Test` review stages:
1. **`Final Review (Agent)`** (Stage 6 / Gate 6): Conducted by an independent, freshly spawned **Cold `Gemini 4 Carbon` (`Model: "pro"`)** reviewer (or executed in retroactive batch mode at Sprint Review) with zero shared conversational bias from Stages 1–5.
2. **`Final Review (Human)`**: Reached only after the Cold Reviewer passes the ticket (`I.O.` transition) or escalates after 3 bounces.

### 1.2 Forensic Investigation of Live Jira Transitions & `tools/jira_util.py`
Inspection of the live Jira workflow for issues in `Final Review (Agent)` (`/rest/api/3/issue/<KEY>/transitions`) confirmed the exact transitions configured in Jira:
- **Transition ID `8`**: `name="Zurück zum Start"` $\rightarrow$ `to="Zu erledigen"`
- **Transition ID `9`**: `name="I.O."` $\rightarrow$ `to="Final Review (Human)"`
- **Transition ID `10`**: `name="Zurück zur Implementierung"` $\rightarrow$ `to="Implementation"`

Forensic inspection of `tools/jira_util.py` (`lines 560–980`) revealed four critical code points requiring updates:
1. **`PARENT_STAGE_ORDER` (`line 667`)**: Currently lists `["zu erledigen", "analysis", "test spec", "implementation plan", "implementation", "test", "final review (human)"]`. It must include `"final review (agent)"` immediately after `"test"` and before `"final review (human)"`.
2. **`STAGE_TO_SKILL_DIR` (`line 723`)**: Needs `"final review (agent)": "stage6-final-review"` so that advancing a parent ticket into `Final Review (Agent)` automatically injects `.agents/skills/stage6-final-review/SKILL.md`.
3. **`resolve_desired_parent_stage_for_subtask` (`line 788`)**: Currently returns `"final review (human)"` when a `[Test]` / `[Verification]` subtask completes (`subtask_done=True`). It must return `"final review (agent)"` so Gate 5 `PASS` advances the parent ticket into `Final Review (Agent)` while retaining agent assignment (`agent2`), rather than prematurely assigning to `human`.
4. **Ambiguous `"final review"` Substring Matching & Auto-Assignment in `transition_issue` (`lines 869–972`)**:
   - Currently, `if "final review" in norm_target_lower:` matches **both** `"final review (agent)"` and `"final review (human)"`, and `if normalized_target in target_name:` (Pass 3 substring match) can match the wrong transition if `"final review"` is passed.
   - Even worse, lines 904 and 968 execute `if "final review" in norm_target_lower: assign_issue(issue_key, "human", role=role)`, which would erroneously assign `Final Review (Agent)` tickets to `human`!
   - Therefore, alias normalization and auto-assignment in `transition_issue` must explicitly disambiguate:
     - `"final review (agent)"`, `"final_review_agent"`, `"agent_review"`, `"stage6"` $\rightarrow$ target status `"final review (agent)"`, auto-assign to `"agent2"` (never `"human"`).
     - `"final review (human)"`, `"final_review_human"`, `"human_review"`, `"final review"`, `"final_review"`, `"human"` $\rightarrow$ target status `"final review (human)"`, auto-assign to `"human"`.
5. **Composite Command `final-review-verdict`**:
   - Needs `python3 tools/jira_util.py --as agent2 final-review-verdict <PARENT_KEY> PASS|BOUNCE <COMMENT_OR_@FILE> [--bounce-target=implementation|analysis|zu erledigen]`.
   - On `PASS`: Verifies or transitions parent ticket from `Final Review (Agent)` to `Final Review (Human)` (via `I.O.`), posts a structured `[ROLE: agent2]` comment tagged with `[FINAL REVIEW (AGENT) — PASS]`, and assigns the parent ticket to `human`.
   - On `BOUNCE`: Counts prior `[FINAL REVIEW (AGENT) — BOUNCE X/3]` comments on the parent ticket (`bounce_count = prior_bounces + 1`).
     - If `bounce_count <= 3`: Posts `[FINAL REVIEW (AGENT) — BOUNCE {bounce_count}/3]` with the findings, transitions the parent ticket back to the target stage (`Implementation` via ID `10`, `Zu erledigen` via ID `8`, or `Analysis` by stepping via `Zu erledigen` $\rightarrow$ `Analysis`), and assigns to `agent1`.
     - If `bounce_count > 3` (or upon exhausting 3 bounces when escalated): Escalates to `Final Review (Human)` with `[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]` and assigns to `human`.

## 2. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Refines `REQ-PRO-014` (*Dual-Gate Review Protocol*), `REQ-PRO-035` (*Höchstmaß Core Framework & Swiss Cheese Model*), `REQ-PRO-036` (*Parameterized Jira CLI Tooling*), and `REQ-PRO-037` (*PA 4.1 & PA 4.2 Quantitative Process Metrics*), introducing net-new requirement `REQ-PRO-058` (*Stage 6 Cold Carbon Final Review (Agent), 3-Bounce Healing Loop, and Gate 6 Metrics*) targeting `tools/jira_util.py`, `tools/sprint_metrics.py`, `.agents/skills/stage6-final-review/SKILL.md`, `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/jira-workflow/SKILL.md`, `.agents/rules/aspice_governance.md`, and their `tools/hoechstmass/` counterparts.
- **Historical Origin & Commit Trace**: Introduced across `ATT-523` / `ATT-670` (5-stage ASPICE workflow), `ATT-3922` / `ATT-5667` / `ATT-5668` (Höchstmaß framework, parameterized `jira_util.py`, and `sprint_metrics.py`), and `ATT-6506` (`REQ-TST-004` Two-Tier Device Policy).
- **Root Reason for Existing Formulation**: Previously, Jira had only one post-`Test` review state (`Final Review (Human)`), so Gate 5 `PASS` transitioned the parent ticket directly to `Final Review (Human)` and assigned it to `human`. Subagent pairs (`pro`/`flash`) reviewed each other's subtasks in Stages 1–5, leaving human review as the first zero-context inspection of the squashed commit.
- **Preservation of Core Invariants**:
  1. **Rule 3 (Human Decision Gate for `Erledigt`)**: AI agents remain 100% prohibited from transitioning standard parent tickets to `Erledigt`.
  2. **Rule 8 (One Atomic Squashed Commit per Ticket)**: Stage 5 continues to squash-merge 1 atomic commit onto `sprint/<SPRINT_NAME>`; Stage 6 bounce healing amends or adds a clean fixups squash so the sprint branch retains 1 atomic commit per ticket.
  3. **Two-Tier Device Policy (`REQ-TST-004`)**: Preserved 100% in `.agents/rules/aspice_governance.md`, `stage5-verification/SKILL.md`, and `sprint-planner/SKILL.md`.
  4. **Höchstmaß Tooling & Framework Contract Parity (`TST-PRO-035`, `TST-PRO-036`, `TST-PRO-040.4`)**: `tools/hoechstmass/` mirrors remain synchronized and free of hardcoded `project = ATT` or proprietary account IDs.

## 3. Scope & Impact Assessment
- **In Scope**:
  1. `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`: `PARENT_STAGE_ORDER`, `STAGE_TO_SKILL_DIR`, `resolve_desired_parent_stage_for_subtask`, disambiguated `Final Review (Agent)` vs `Final Review (Human)` transitions and role assignment, and `final-review-verdict` CLI command.
  2. `.agents/skills/stage6-final-review/SKILL.md` and `tools/hoechstmass/.agents/skills/stage6-final-review/SKILL.md`: New skill defining the Cold `Gemini 4 Carbon` (`Model: "pro"`) Reviewer, 4-part Cold Audit Checklist, Executive Review Summary format, and 3-bounce healing loop.
  3. `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/jira-workflow/SKILL.md`, `.agents/rules/aspice_governance.md` (and `tools/hoechstmass/` mirrors): Updated for Stage 6 (`Final Review (Agent)`) both in-sprint and in retroactive batch mode during Ceremony 2 Step 0.5.
  4. `tools/sprint_metrics.py` and `tools/hoechstmass/tools/sprint_metrics.py`: Gate 6 (`Final Review (Agent)`) FTPR, bounce count tracking (`1..3`), and empirical defect escape probability $p_6$ in the Swiss Cheese model.
  5. Living documentation (`docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, master indexes) and unit tests (`tools/test_sprint_metrics.py`, `tools/test_jira_epics.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`).
- **Out of Scope**:
  - Changes to Android production runtime code under `app/src/main/`.
