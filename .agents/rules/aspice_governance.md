# ASPICE In-Sprint Core Governance Rules: aTrainingTracker

These **8 Core Rules** are strictly binding on all AI agents during in-sprint execution.
* **Domain Standards**: See [`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md) (UI/Compose/Charts) and [`docs/coding_standards.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/coding_standards.md) (Architecture/DRY/Android Lifecycles).
* **Sprint Ceremonies & Hotfixes**: See [`.agents/skills/sprint-planner/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/sprint-planner/SKILL.md) (Ceremony 1 & 2 rules) and [`.agents/skills/release-manager/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/release-manager/SKILL.md).
* **Historical Archive**: Legacy Rules 1–55 are indexed in [`docs/engineering/rule_history_archive.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/rule_history_archive.md).

---

## Rule 1: Obey the Rules & Two-Tier Context Refresh
* **Ticket Start / Ceremony Start / Post-Compaction Resume**: Before starting a new ticket, starting a sprint ceremony, or resuming after a context compaction, explicitly read `.agents/rules/aspice_governance.md` and the relevant domain standard (`docs/design_guidelines.md` for UI tickets, `docs/coding_standards.md` for logic/service/DB tickets) via `view_file`.
* **Intra-Ticket Stage Transitions (Stages 1 $\rightarrow$ 2 $\rightarrow$ 3 $\rightarrow$ 4 $\rightarrow$ 5)**: Whenever `tools/jira_util.py` transitions a parent ticket into a lifecycle stage (via `move <KEY> analysis` or `gate-verdict <SUBTASK> PASS`), it **automatically prints** the target stage's `SKILL.md` and core governance rules to `stdout`, eliminating separate manual `view_file` turns between stages unless a context compaction occurred.

## Rule 2: Autonomous Full-Sprint Execution
* Execute the entire active sprint backlog (`ORDER BY rank ASC`) continuously from ticket to ticket across Stages 1–5 without pausing or asking the user whether to continue.
* When resuming from a context compaction, never treat the compaction summary's "Next Steps" as a question for the user—immediately query Jira / git state and resume execution autonomously.

## Rule 3: Human Decision Gate on Parent Tickets & Synchronous Stage Progression
* **Parent Tickets**: Moving any parent ticket to `Erledigt` is reserved **exclusively** for the human user. Agents always end parent tickets at `Final Review (Human)` (assigned to `human`).
* **Sub-Tasks**: Stage sub-tasks that pass Gate 1–5 audit close directly to `Erledigt` via `python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS "<Audit Report>"`, which synchronously advances the parent ticket to the next stage column (`Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test` $\rightarrow$ `Final Review (Human)`).

## Rule 4: Code Lock Until Gate 3 Passes
* Before creating or modifying any production source code in `app/src/...` during Stage 4, run:
  ```bash
  python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>
  ```
  and confirm exit code `0` (`GATE_PASSED`). If Gate 3 is not `Erledigt`, production code edits are strictly blocked.

## Rule 5: Ticket Creation Stops Immediately
* When asked to create a backlog or follow-up ticket (`python3 tools/jira_util.py create-issue ...`, which automatically ranks it at the top of the backlog), report the new issue key and **STOP IMMEDIATELY**. Never branch, spawn sub-tasks, or start implementation unless explicitly instructed.

## Rule 6: Unattended Escalation via Jira
* If a requirement ambiguity or design decision blocks a sub-task mid-sprint, never block the console:
  1. `python3 tools/jira_util.py comment <SUBTASK_KEY> "Question for Human: ..."`
  2. `python3 tools/jira_util.py assign <SUBTASK_KEY> human`
  3. Immediately continue with the next unblocked ticket in the sprint backlog.

## Rule 7: Asymmetric `pro` / `flash` Zipper Principle & Persistent Ping-Pong Subagents per Ticket
* **Two Persistent Subagents per Ticket**: At the start of each ticket (`ATT-XXXX`), spawn **exactly two persistent `self` subagents** (`invoke_subagent` with `TypeName: "self"`):
  * `subagent_pro` (`Model: "pro"`, `Role: "ATT-XXXX Pro Agent"`)
  * `subagent_flash` (`Model: "flash"`, `Role: "ATT-XXXX Flash Agent"`)
* **Context-Warm Ping-Pong via `send_message`**: Reuse their `conversationId`s across all 5 stages via `send_message`, swapping their roles (`--as agent1` Doing vs. `--as agent2` Gate Review) on every stage so no model tier audits its own tier and each subagent enters its Doing turn already warm from auditing the previous Gate:

| Stage | Doing (`--as agent1` `submit-stage`) | Gate Review (`--as agent2` `gate-verdict`) | Context Synergy & Fused Handoff |
| :--- | :---: | :---: | :--- |
| **Stage 1: Analysis** (`[Analysis]` $\rightarrow$ Gate 1) | **`subagent_pro`** | **`subagent_flash`** | `subagent_flash` audits Gate 1 (`--as agent2`) and **immediately executes Stage 2 Doing (`--as agent1`) in the same turn** upon `PASS` |
| **Stage 2: Req & Test Spec** (`[Req & Test Spec]` $\rightarrow$ Gate 2) | **`subagent_flash`** *(fused with Gate 1)* | **`subagent_pro`** *(via `send_message`)* | `subagent_pro` audits Gate 2 (`--as agent2`) and **immediately executes Stage 3 Doing (`--as agent1`) in the same turn** upon `PASS` |
| **Stage 3: Implementation Plan** (`[Impl-Plan]` $\rightarrow$ Gate 3) | **`subagent_pro`** *(fused with Gate 2)* | **`subagent_flash`** *(via `send_message`)* | `subagent_flash` audits Gate 3 (`--as agent2`) and (for Standard tickets) **immediately executes Stage 4 Doing (`--as agent1`) in the same turn** upon `PASS` |
| **Stage 4: Implementation** (`[Implementation]` $\rightarrow$ Gate 4) | **`subagent_flash`** *(Standard, fused)* / **`subagent_pro`** *(High-Risk)* | **`subagent_pro`** *(Standard)* / **`subagent_flash`** *(High-Risk)* | `subagent_pro` audits `git diff` against its Stage 3 plan |
| **Stage 5: Verification** (`[Test]` $\rightarrow$ Gate 5) | **`subagent_flash`** *(via `send_message`)* | **`subagent_pro`** *(via `send_message`)* | `subagent_flash` runs Tier 1 verification & walkthrough; `subagent_pro` executes Gate 5 `PASS` & squash-merge |

* **Fused Same-Agent Handoffs & Concise Deliverables (Rule 60 — `ATT-4465`)**:
  * **Fused Gate-to-Next-Stage Turns (6 Turns Instead of 10)**: Because `gate-verdict PASS` automatically advances the parent ticket and injects the next stage's `SKILL.md`, instruct `subagent_flash` (at Gate 1 $\rightarrow$ Stage 2 and Standard Gate 3 $\rightarrow$ Stage 4) and `subagent_pro` (at Gate 2 $\rightarrow$ Stage 3) to execute the Gate Review (`--as agent2`) and immediately perform the subsequent Stage Doing (`--as agent1`) in the **same subagent turn** whenever the verdict is `PASS`.
  * **Concise Deliverable Cap ($\le 60\text{--}80$ Lines)**: Keep Stage 1–5 markdown deliverables (`analysis`, `test_spec`, `plan`, `walkthrough`) compact and structured (bullet points, tables, exact file/line references, target $\le 60\text{--}80$ lines) without repeating ticket descriptions verbatim or adding narrative filler.
  * **Two-Tier Unit Test & Conditional ADB Strategy**: Run targeted package/contract unit tests (`--tests ...`), `TranslationParityTest` (when strings change), and `verify_requirement_governance.py` in Stages 4 & 5 (~10–20s; full suite per-ticket only for High-Risk core infrastructure changes), and run the full `./gradlew testDebugUnitTest` suite once at the Sprint Integration Gate (`Review & Retro`) before merging `sprint/<ID>` into `develop`. Run `./gradlew installDebug` + ADB screenshot verification in Stage 5 only for tickets touching UI, Compose, resources, navigation, or foreground services.
* **Reactive Turn Boundaries (No Polling Loops or Premature `send_message` Queueing, Rule 56)**: After invoking or messaging a subagent for the active stage, immediately stop calling tools to end the turn and wait for the subagent's completion message. Never poll `manage_subagents(Action="list")` or `manage_task(Action="status")` in a loop, and never send next-stage `send_message` instructions before the current stage has completed and created its Jira subtask.
* **Ticket Cleanup**: Once `ATT-XXXX` reaches `Final Review (Human)` and its branch is squashed onto `sprint/<SPRINT_NAME>`, terminate the ticket's subagents via `manage_subagents(Action="kill", ConversationIds=[...])` (or `kill_all`) so the next ticket starts with a clean context.

## Rule 8: One Atomic Commit per Ticket & Clean Jira Hygiene
* **Composite Stage CLI**: Use `python3 tools/jira_util.py --as agent1 submit-stage <PARENT_KEY> <1..5> <TEXT_OR_@FILE>` to submit a stage in 1 call, and `python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> <PASS|REVISION> <TEXT_OR_@FILE>` to record Gate 1–5 verdicts in 1 call.
* **One Commit per Ticket**: Integrate each verified ticket (`feature/<KEY>`) onto `sprint/<SPRINT_NAME>` at the end of Stage 5 as **strictly one squashed atomic commit** (`git merge --squash feature/<KEY> && git commit -m "<KEY>: <Summary>" && git branch -D feature/<KEY>`).
* **Jira Hygiene**: Sub-tasks never receive a `FixVersion` (`Lösungsversion`); parent tickets receive `FixVersion` only when finished/accepted; ticket summaries omit category/epic prefixes while preserving priority tags (`[Highest]`..`[Low]`); log any in-sprint process anomalies immediately as comments on the sprint's `Review & Retro` ticket.
