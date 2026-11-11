# Stage 2 Requirement & Test Specification: ATT-7101

## 1. Formal Requirement (`REQ-PRO-058`)
- **ID**: `REQ-PRO-058` (*Stage 6 Cold Carbon Final Review (Agent), 3-Bounce Healing Loop, and Gate 6 Metrics*)
- **Shard**: `docs/requirements/req_process_stability.md`
- **Clauses**:
  1. `REQ-PRO-058.1`: `PARENT_STAGE_ORDER` in `tools/jira_util.py` SHALL place `"final review (agent)"` immediately after `"test"` and before `"final review (human)"`; `STAGE_TO_SKILL_DIR` SHALL map `"final review (agent)"` to `"stage6-final-review"`; `resolve_desired_parent_stage_for_subtask` SHALL advance completed Stage 5 `[Test]` subtasks to `"final review (agent)"`; `transition_issue` SHALL disambiguate `Final Review (Agent)` (assigning to `agent2`) from `Final Review (Human)` (assigning to `human`).
  2. `REQ-PRO-058.2`: `final-review-verdict <PARENT_KEY> PASS|BOUNCE <COMMENT_OR_@FILE> [--bounce-target=implementation|analysis|zu erledigen]` SHALL post `[FINAL REVIEW (AGENT) — PASS]` and transition to `Final Review (Human)` (assigned to `human`) on `PASS`, or track `[FINAL REVIEW (AGENT) — BOUNCE X/3]` across up to 3 bounces back to `agent1` before escalating on a 4th bounce (`[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]`) to `Final Review (Human)`.
  3. `REQ-PRO-058.3`: `.agents/skills/stage6-final-review/SKILL.md` (and `tools/hoechstmass/` mirror) SHALL specify the Cold `Gemini 4 Carbon` (`Model: "pro"`) Reviewer, 4-part Cold Audit Checklist, Executive Review Summary, and 3-bounce healing loop; `sprint-planner/SKILL.md` SHALL support retroactive batch execution in Ceremony 2 Step 0.5.
  4. `REQ-PRO-058.4`: `tools/sprint_metrics.py` (and `tools/hoechstmass/tools/sprint_metrics.py`) SHALL parse `[FINAL REVIEW (AGENT) — ...]` parent comments to compute `gate_6` FTPR, bounce count, and `p6_final_review_agent_escape`.

## 2. Test Specification (`TST-PRO-058`)
| Sub-Test | Target File | Verification Procedure | Expected Result |
| :--- | :--- | :--- | :--- |
| `TST-PRO-058.1` | `tools/test_jira_epics.py` | Verify `PARENT_STAGE_ORDER`, `STAGE_TO_SKILL_DIR`, `resolve_desired_parent_stage_for_subtask("[Test] ...", True)`, and `transition_issue` alias disambiguation | `"final review (agent)"` precedes `"final review (human)"`, assigns to `agent2` |
| `TST-PRO-058.2` | `tools/test_jira_epics.py` | Unit-test `final_review_verdict` for `PASS`, `BOUNCE` (`1/3..3/3`), and 4th-bounce escalation (`>3`), plus `tools/hoechstmass/tools/jira_util.py` parity | Accurate status transitions, comment tags, and assignee routing |
| `TST-PRO-058.3` | `HoechstmassCoreFrameworkContractTest.kt` | Verify `.agents/skills/stage6-final-review/SKILL.md` and `tools/hoechstmass/.agents/skills/stage6-final-review/SKILL.md` existence and frontmatter | Contract test passes 100% |
| `TST-PRO-058.4` | `tools/test_sprint_metrics.py` | Unit-test `parse_final_review_agent_verdicts_from_comments` and `gate_6` / `p6_final_review_agent_escape` in `parse_sprint_gate_metrics` | `gate_6` FTPR, bounces, and $p_6$ calculated accurately |

## 3. Localization Audit
- Pure Python developer tooling, ASPICE skills, and unit/contract tests (`0` Android UI string changes required across the 9 `values*` locales).
