# Stage 5 Walkthrough & Verification Report: `ATT-7101`

- **Ticket**: `ATT-7101` — `[Verbesserung] jira_util.py, stage6-final-review & sprint-planner: Introduce 'Final Review (Agent)' cold Carbon review stage with 3-bounce healing before 'Final Review (Human)'`
- **Epic**: `ATT-232: Process`
- **Requirement & Test IDs**: `REQ-PRO-058`, `TST-PRO-058` (`TST-PRO-058.1`..`TST-PRO-058.4`)
- **Author**: `agent1` (Executing Agent)
- **Reviewer**: `agent2` (Reviewing Agent)

---

## 1. Summary of Changes

1. **Tooling & State Machine (`tools/jira_util.py` & `tools/hoechstmass/tools/jira_util.py`)**:
   - Inserted `"final review (agent)"` into `PARENT_STAGE_ORDER` between `"test"` and `"final review (human)"`, and mapped `"final review (agent)": "stage6-final-review"` in `STAGE_TO_SKILL_DIR`.
   - Updated `resolve_desired_parent_stage_for_subtask` so completed `[Test]` / `[Verification]` subtasks advance the parent ticket to `"final review (agent)"` (assigning to `agent2`) instead of jumping directly to `"final review (human)"`.
   - Disambiguated `transition_issue` aliases and role auto-assignment between `Final Review (Agent)` (`agent2`) and `Final Review (Human)` (`human`).
   - Added `count_final_review_bounces` and `final-review-verdict <PARENT-KEY> PASS|BOUNCE <COMMENT_OR_@FILE> [--bounce-target=implementation|analysis|zu erledigen]` implementing the 3-bounce self-healing loop (`BOUNCE 1/3`..`3/3`) and 4th-bounce escalation (`[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]`) to `Final Review (Human)`.

2. **Stage 6 Skill & Governance Synchronization (`.agents/` & `tools/hoechstmass/.agents/`)**:
   - Authored `.agents/skills/stage6-final-review/SKILL.md` and `tools/hoechstmass/.agents/skills/stage6-final-review/SKILL.md` defining the Cold Carbon Review protocol (context isolation, git diff & traceability audit, independent verification, and 3-bounce self-healing).
   - Updated `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md` (including Ceremony 2 Step 0.5 Retroactive Batch Mode), `jira-workflow/SKILL.md`, and `aspice_governance.md` across both root and `tools/hoechstmass/`.

3. **Quantitative Process Metrics (`tools/sprint_metrics.py` & `tools/hoechstmass/tools/sprint_metrics.py`)**:
   - Added `STAGE_MAP[6]` (`Final Review (Agent) (Gate 6)`), `parse_final_review_agent_verdicts_from_comments`, `gates["gate_6"]` FTPR/bounce tracking, and `swiss_cheese["p6_final_review_agent_escape"]`.

---

## 2. Verification & Test Evidence

- **Python Unit Tests**:
  - `python3 tools/test_jira_epics.py` (`16/16 PASSED`, including `TestStage6FinalReview`)
  - `python3 tools/test_sprint_metrics.py` (`7/7 PASSED`, including `test_gate6_final_review_agent_metrics`)
  - `python3 tools/hoechstmass/tools/test_hoechstmass_tools.py` (`15/15 PASSED`)
  - `python3 tools/hoechstmass/tools/test_sprint_metrics.py` (`7/7 PASSED`)
- **Gradle Contract Tests**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest" --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest" --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` (`34/34 PASSED`)
- **Requirement & Test Governance**:
  - `python3 tools/verify_requirement_governance.py` (`PASS`)
