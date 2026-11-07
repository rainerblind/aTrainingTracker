# Agile Agentic ASPICE (AAA) Core In-Sprint Governance Rules

These **8 Core Rules** operationalize **Agile Agentic ASPICE (AAA)** (Tripwires, Infinite Context, and Constructive Resistance) and are strictly binding on all AI agents during in-sprint execution.

* **Domain Standards**: See [`docs/coding_standards.md`](file://docs/coding_standards.md) (Architecture/DRY/Lifecycles) and [`docs/design_guidelines.md`](file://docs/design_guidelines.md) (UI/Component Selection).
* **Sprint Ceremonies**: See [`.agents/skills/sprint-planner/SKILL.md`](file://.agents/skills/sprint-planner/SKILL.md) and [`.agents/skills/release-manager/SKILL.md`](file://.agents/skills/release-manager/SKILL.md).

---

## Rule 1: Obey the Rules, Two-Tier Context Refresh & Constructive Resistance
* **Ticket Start / Ceremony Start / Post-Compaction Resume**: Before starting a new ticket, starting a sprint ceremony, or resuming after a context compaction, explicitly read `.agents/rules/aspice_governance.md` and the relevant domain standard (`docs/design_guidelines.md` for UI tickets, `docs/coding_standards.md` for logic/backend/DB tickets) via file inspection tools.
* **Intra-Ticket Stage Transitions (Stages 1 $\rightarrow$ 2 $\rightarrow$ 3 $\rightarrow$ 4 $\rightarrow$ 5)**: Whenever the workflow CLI transitions a parent ticket into a lifecycle stage, it automatically injects the target stage's `SKILL.md` and core governance rules to stdout, eliminating separate manual file inspection turns between stages unless context compaction occurred.
* **Constructive Resistance & Documented Deviations (*Pflicht zum Widerspruch & Dokumentierte Abweichungserlaubnis*)**: Agents MUST NOT initiate shortcuts. When the human user requests an exception from standard process, the agent SHALL first offer constructive resistance (explaining the guardrail's purpose and associated risk). If the human insists, the exception, its rationale, and exact changes MUST be cleanly documented in the issue tracker and stage deliverable/walkthrough.

## Rule 2: Autonomous Full-Sprint Execution & Mid-Sprint Ticket Additions
* Execute the entire active sprint backlog in rank order continuously from ticket to ticket across Stages 1–5 without pausing or asking the user whether to continue.
* **Automatic Realization of Tickets Added to an Already-Started Sprint**: Because only the human user can add tickets to an active sprint, any ticket added into an already-started sprint carries implicit human approval. Before concluding the sprint, always re-query the backlog for newly added unstarted tickets and execute Stages 1–5 autonomously.
* When resuming from a context compaction, never treat the compaction summary's "Next Steps" as a question for the user—immediately query issue tracker / git state and resume execution autonomously.

## Rule 3: Human Decision Gate on Parent Tickets & Synchronous Stage Progression
* **Parent Tickets**: Moving any parent ticket to completed/closed is reserved **exclusively** for the human user. Agents always end parent tickets at `Final Review (Human)`.
* **Sub-Tasks**: Stage sub-tasks that pass Gate 1–5 audit close directly upon recording a `PASS` verdict, which synchronously advances the parent ticket to the next stage column (`Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test` $\rightarrow$ `Final Review (Human)`).

## Rule 4: Code Lock Until Gate 3 Passes
* Before creating or modifying any production source code during Stage 4, run the gate-check tool to confirm Gate 3 (Implementation Plan) has passed (`GATE_PASSED`). If Gate 3 is not closed, production code edits are strictly blocked.

## Rule 5: Ticket Creation Stops Immediately
* When asked to create a backlog or follow-up ticket, report the new issue key and **STOP IMMEDIATELY**. Never branch, spawn sub-tasks, or start implementation unless explicitly instructed.

## Rule 6: Unattended Escalation via Issue Tracker
* If a requirement ambiguity or design decision blocks a sub-task mid-sprint, never block the console:
  1. Add an issue comment: `"Question for Human: ..."`
  2. Reassign the sub-task or ticket to `human`
  3. Immediately continue with the next unblocked ticket in the sprint backlog.

## Rule 7: Asymmetric `pro` / `flash` Zipper Principle & Persistent Subagents
* Maintain an asymmetric pair of persistent subagents across the sprint:
  * `subagent_pro`: High-reasoning model tier for deep analysis, planning, and critical reviews.
  * `subagent_flash`: Fast-execution model tier for structured specification, execution, and verification.
* **Context Synergy & Fused Handoff**:
  * **Stage 1 (Analysis)**: `subagent_pro` Doing (`agent1`) $\rightarrow$ `subagent_flash` Gate 1 Review (`agent2`)
  * **Stage 2 (Req & Test Spec)**: `subagent_flash` Doing (`agent1`) $\rightarrow$ `subagent_pro` Gate 2 Review (`agent2`)
  * **Stage 3 (Implementation Plan)**: `subagent_pro` Doing (`agent1`) $\rightarrow$ `subagent_flash` Gate 3 Review (`agent2`)
  * **Stage 4 (Implementation)**: `subagent_flash` Doing (`agent1`) $\rightarrow$ `subagent_pro` Gate 4 Review (`agent2`)
  * **Stage 5 (Verification)**: `subagent_flash` Doing (`agent1`) $\rightarrow$ `subagent_pro` Gate 5 Review (`agent2`) $\rightarrow$ Human Gate
* **Fused Same-Agent Handoffs**: To minimize latency and turns, whenever an agent records a `PASS` verdict for the preceding gate, it immediately proceeds to execute the next stage Doing turn in the same response.
* **Concise Deliverable Cap ($\le 60\text{--}80$ Lines)**: Keep Stage 1–5 markdown deliverables compact and structured without narrative filler.

## Rule 8: One Atomic Commit per Ticket & Clean Issue Tracker Hygiene
* **Composite Stage CLI**: Use composite commands to submit stages and record gate verdicts in single calls.
* **One Commit per Ticket**: Integrate each verified ticket onto the sprint branch as **strictly one squashed atomic commit** (`git merge --squash feature/<KEY> && git commit -m "<KEY>: <Summary>"`).
* **Issue Tracker Hygiene**: Sub-tasks never receive a release version; parent tickets receive release version only when accepted by the human user.
