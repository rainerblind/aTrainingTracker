# Agile Agentic ASPICE (AAA) Protocol & Architecture Pointer: aTrainingTracker

Agile Agentic ASPICE (AAA) operationalizes Automotive SPICE for autonomous AI pair-programming agents across three core pillars:
1. **Intentional Engineering Tripwires (*Stolpersteine*)**: Deliberate quality gates (Gates 1–5), bidirectional requirement/test traceability (`REQ-XXX` $\leftrightarrow$ `TST-XXX`), Chesterton's Fence archaeology (`REQ-PRO-022`), and independent `pro`/`flash` cross-auditing prevent autonomous agents from taking unverified shortcuts.
2. **Artifact-Driven Infinite Context**: Stage deliverables in `docs/engineering/` (`analysis/<KEY>_analysis.md`, `test_specs/<KEY>_test_spec.md`, `plans/<KEY>_plan.md`, `walkthroughs/<KEY>_walkthrough.md`, `retros/Sprint_Review_and_Retro_<KEY>.md`, and `test_results/index.html`), plus living specifications (`docs/requirements.md` + `docs/requirements/req_*.md`, `docs/tests.md` + `docs/tests/tst_*.md`), maintain persistent external long-term memory across context compactions and agent turns.
3. **Constructive Resistance & Documented Deviations (*Pflicht zum Widerspruch & Dokumentierte Abweichungserlaubnis*)**: Agents MUST NOT initiate shortcuts. When the human user requests an exception from standard process, the agent SHALL first offer constructive resistance (explaining the guardrail's purpose and associated risk). Only if the human explicitly insists may the exception proceed—and even then, the exception, its justification, and all changes made MUST be cleanly documented in Jira and the walkthrough/docs.

## Document Triad & In-Sprint Architecture
Authoritative engineering governance and living standards are decoupled into the **Document Triad** and modular `.agents/skills/`:
1. **In-Sprint Core Rules (8 Core Rules)**: [`.agents/rules/aspice_governance.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/rules/aspice_governance.md)
2. **UI & Interaction Design Guidelines**: [`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md)
3. **Technical Architecture & Coding Standards**: [`docs/coding_standards.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/coding_standards.md)
4. **Stage & Ceremony Skills**: [`.agents/skills/stage1-analysis/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/stage1-analysis/SKILL.md) through [`.agents/skills/stage5-verification/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/stage5-verification/SKILL.md), [`.agents/skills/sprint-planner/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/sprint-planner/SKILL.md), and [`.agents/skills/jira-workflow/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/jira-workflow/SKILL.md).
5. **Historical Rule Archive (Rules 1–55)**: [`docs/engineering/rule_history_archive.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/rule_history_archive.md)

## Multi-Account Role Segregation & Invariants (`REQ-PRO-020` / `REQ-PRO-022`)
* **Multi-Account Credentials (`.env.jira`)**: `tools/jira_util.py` resolves role-specific credentials for `agent1` (`JIRA_AGENT1_USER` / `JIRA_AGENT1_TOKEN`), `agent2` (`JIRA_AGENT2_USER` / `JIRA_AGENT2_TOKEN`), and `coordinator` (`JIRA_COORDINATOR_USER` / `JIRA_COORDINATOR_TOKEN`).
* **Coordinator Role Boundaries**: The **Coordinator** orchestrates sprint backlog queries and sub-task creation, **cannot override** independent Auditor (`agent2`) verdicts, cannot skip quality gates, and cannot transition parent tickets to `Erledigt` (reserved exclusively for the human user).
* **Requirement Archaeology (Chesterton's Fence — `REQ-PRO-022`)**: Any Stage 2 deliverable that modifies, relaxes, or replaces an existing requirement in `docs/requirements.md` must include a 4-field `Requirement Archaeology & Chesterton's Fence Audit` section (`Original Requirement ID & Target`, `Historical Origin & Commit Trace`, `Root Reason for Existing Formulation`, `Preservation of Core Invariants`), verified by `tools/verify_requirement_governance.py`.

## 5-Layer Swiss Cheese Defense Model & Statistical Decorrelation
Defects are prevented by stacking 5 statistically decorrelated defensive layers ($P(\text{escaped defect}) = \prod_{i=1}^5 p_i = p_1 \cdot p_2 \cdot p_3 \cdot p_4 \cdot p_5$):
1. **Layer 1 ($p_1$) — Executing Agent (Sub-Task Level)**: Bounded stage prompt + local compiler, unit tests, and static analyzers (`agent1`).
2. **Layer 2 ($p_2$) — Reviewing Agent (Sub-Task Gate Level)**: Asymmetric model tier (`pro` $\leftrightarrow$ `flash`) auditing deliverables at Gates 1–5 (`agent2`).
3. **Layer 3 ($p_3$) — Ticket-Wide Verification Session (Stage 5)**: Clean-room full regression suite, requirement-test parity, and visual walkthrough verification.
4. **Layer 4 ($p_4$) — Human Engineer (Final Review Gate)**: Live application testing and exclusive parent ticket closure authority.
5. **Layer 5 ($p_5$) — Periodic Cross-Vendor Audits**: Independent codebase reviews by **Claude** and UI screenshot/UX critiques by **ChatGPT** to break vendor-level blind spots.

## Isolated In-Place Code & Document Review Workflow (`.reviews/` + Emacs)
To allow frictionless human review of source code (`.kt`, `.java`, `.py`, `.xml`) and documentation (`.md`) while autonomous agents compile code and switch Git branches in the main workspace:
1. **Isolated Sandbox**: `.reviews/` is excluded from the main repository via `.git/info/exclude` and maintains its own standalone Git repository (`git init`).
2. **Editor Integration (`emacsclient`)**: The agent or CLI helper (`tools/review_tool.py`) stages files into `.reviews/`, commits the baseline, and launches `emacsclient -n`.
3. **Direct Edits & Inline Directives (`>>`)**: The human directly edits lines or writes inline review instructions (`>> ...` or `// >> ...`) without risk of breaking parallel builds.
4. **Automated Harvesting**: The agent inspects `git -C .reviews diff`, applies edits and directives to the authentic codebase, runs tests, and live-reloads the Emacs buffer.
