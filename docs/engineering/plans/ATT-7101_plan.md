# Stage 3 Implementation Plan: ATT-7101

## 1. Architectural Overview & System Invariants
- **Target Components**:
  1. `tools/jira_util.py` & `tools/hoechstmass/tools/jira_util.py`
  2. `tools/sprint_metrics.py` & `tools/hoechstmass/tools/sprint_metrics.py`
  3. `.agents/skills/stage6-final-review/SKILL.md` & `tools/hoechstmass/.agents/skills/stage6-final-review/SKILL.md`
  4. `.agents/skills/stage5-verification/SKILL.md` & `tools/hoechstmass/.agents/skills/stage5-verification/SKILL.md`
  5. `.agents/skills/sprint-planner/SKILL.md` & `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`
  6. `.agents/skills/jira-workflow/SKILL.md` & `tools/hoechstmass/.agents/skills/jira-workflow/SKILL.md`
  7. `.agents/rules/aspice_governance.md` & `tools/hoechstmass/.agents/rules/aspice_governance.md`
  8. `tools/test_jira_epics.py`, `tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, and `HoechstmassCoreFrameworkContractTest.kt`
- **System Invariants**:
  - **Rule 3**: Never transition standard parent tickets to `Erledigt`.
  - **Rule 8**: One atomic squashed commit per ticket on `sprint/<SPRINT_NAME>`.
  - **`REQ-TST-004`**: Preserve all Two-Tier Device Policy (`emulator-5554` vs `Pixel 10`) sections in `.agents/rules/aspice_governance.md`, `stage5-verification/SKILL.md`, and `sprint-planner/SKILL.md`.
  - **`TST-PRO-036` / `TST-PRO-040.4`**: `tools/hoechstmass/` files must remain parameterized and contain zero hardcoded `project = ATT` or proprietary account IDs.

## 2. Atomic Implementation Steps
1. **Step 1 — Upgrade `tools/jira_util.py` & `tools/hoechstmass/tools/jira_util.py` (`REQ-PRO-058.1`, `REQ-PRO-058.2`)**:
   - Update `PARENT_STAGE_ORDER` to `["zu erledigen", "analysis", "test spec", "implementation plan", "implementation", "test", "final review (agent)", "final review (human)"]`.
   - Update `STAGE_TO_SKILL_DIR` to include `"final review (agent)": "stage6-final-review"`.
   - Update `resolve_desired_parent_stage_for_subtask` so completed `[Test]` / `[Verification]` subtasks return `"final review (agent)"`.
   - Disambiguate `Final Review (Agent)` vs `Final Review (Human)` in `transition_issue` so `"final review (agent)"` (and aliases `"final_review_agent"`, `"agent_review"`, `"stage6"`) transitions to `Final Review (Agent)` and assigns to `"agent2"`, whereas `"final review (human)"` (and aliases `"final_review_human"`, `"human_review"`, `"final review"`, `"final_review"`, `"human"`, `"i.o."`) transitions to `Final Review (Human)` and assigns to `"human"`.
   - Implement `count_final_review_bounces(comments)` and `final_review_verdict(parent_key, verdict_arg, text_or_file, bounce_target="implementation", role="agent2", ...)` supporting `PASS` and `BOUNCE` (with `[FINAL REVIEW (AGENT) — BOUNCE X/3]` up to 3 bounces and escalation `[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]` on `> 3`), plus CLI command `final-review-verdict`.
2. **Step 2 — Author `.agents/skills/stage6-final-review/SKILL.md` & Update Existing Skills/Rules (`REQ-PRO-058.3`)**:
   - Create `.agents/skills/stage6-final-review/SKILL.md` and `tools/hoechstmass/.agents/skills/stage6-final-review/SKILL.md` specifying the Cold `Gemini 4 Carbon` (`Model: "pro"`) Reviewer, 4-part Cold Audit Checklist, Executive Review Summary, and 3-bounce healing loop.
   - Update `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md` (including Ceremony 2 Step 0.5 Retroactive Batch Mode), `jira-workflow/SKILL.md`, and `aspice_governance.md` (plus `tools/hoechstmass/` mirrors).
3. **Step 3 — Upgrade `tools/sprint_metrics.py` & `tools/hoechstmass/tools/sprint_metrics.py` (`REQ-PRO-058.4`)**:
   - Add `parse_final_review_agent_verdicts_from_comments(comments)` and integrate Gate 6 (`Final Review (Agent)`) into `parse_sprint_gate_metrics` and `format_metrics_markdown`, computing `gate_6` FTPR, bounce count, and `p6_final_review_agent_escape`.
4. **Step 4 — Unit & Contract Tests (`TST-PRO-058.1`..`058.4`)**:
   - Add unit tests in `tools/test_jira_epics.py`, `tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, and `HoechstmassCoreFrameworkContractTest.kt`.
