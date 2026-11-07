# Agile Agentic ASPICE (AAA) Protocol: Höchstmaß

> *„Power without control can become destructive. Power guided by intelligence becomes excellence.“*  
> — **ChatGPT**

---

## 1. Core Philosophy & The Three Pillars

**Höchstmaß** operationalizes Automotive SPICE (ASPICE) for autonomous AI pair-programming agents across three core pillars:

### Pillar 1: Intentional Engineering Tripwires (*Stolpersteine*)
Autonomous coding agents naturally drift toward "happy path" shortcuts, unverified refactorings, and suppressed tests when unconstrained. Höchstmaß embeds deliberate architectural tripwires:
* **Asymmetric Quality Gates (Gates 1–5)**: Every stage requires formal review and independent pass verdict by a separate agent role (`agent2`) using an alternate model tier.
* **Code Lock**: Implementation code changes are strictly blocked until Gate 3 (Implementation Plan) passes.
* **Bidirectional Traceability**: Every line of code links to a formal requirement (`REQ-XXX`) and test procedure (`TST-XXX`).
* **Chesterton's Fence Archaeology**: Modifying existing requirements or behavior requires answering all 4 historical audit questions before changes are permitted.

### Pillar 2: Artifact-Driven Infinite Context
LLM context windows degrade over multi-turn interactions. Höchstmaß solves context drift by offloading long-term memory to version-controlled artifacts:
* **Stage Deliverables** (`docs/engineering/`): Concise, structured markdown documents (`analysis`, `test_spec`, `plan`, `walkthrough`, `retros`) capture exact decisions and audit proofs at each stage boundary.
* **Living Specifications** (`docs/requirements/` & `docs/tests/`): Domain-sharded markdown specifications maintained as living sources of truth.
* **Stateless Subagent Resets**: Subagents enter each stage turn fresh, pre-warmed with canonical guidelines rather than bloated conversational histories.

### Pillar 3: Constructive Resistance & Documented Deviations (*Pflicht zum Widerspruch & Dokumentierte Abweichungserlaubnis*)
AI agents must not act as compliant sycophants when process boundaries are threatened:
* When a human user requests a process shortcut, skipping a gate, or deferring tests, the agent **MUST** first offer constructive resistance, explaining the rationale behind the guardrail and the concrete risk of deviation.
* If the human user explicitly insists on the exception, the deviation is permitted under **Dokumentierte Abweichungserlaubnis**: the rationale, trade-offs, and exact modifications must be cleanly recorded in the issue tracker and stage walkthrough.

---

## 2. Multi-Account Role Segregation & Actor Boundaries

Höchstmaß enforces a strict four-role separation of responsibilities:

1. **`agent1` (The Implementer)**:
   * Responsible for authoring the stage deliverable (`submit-stage`) or constructing code during Stage 4.
   * Alternates between model tiers according to the Zipper Principle.
2. **`agent2` (The Independent Auditor)**:
   * Responsible for auditing the deliverable or code diff against the stage checklist (`gate-verdict`).
   * Operates on a separate model tier from `agent1` to guarantee independent evaluation.
   * Rejection (`REVISION`) sends the stage back to `agent1` with concrete remediation feedback.
3. **`coordinator` (The Orchestrator)**:
   * Manages sprint backlog execution, dispatches subagents via `invoke_subagent` / `send_message`, and monitors watchdog timers.
   * **Boundary**: The coordinator cannot override `agent2` audit verdicts and cannot transition parent tickets to completion.
4. **`human` (The Decision Authority)**:
   * Retains exclusive authority to accept completed tickets (`Final Review (Human)`) and close parent issues.
   * Approves sprint scope during Sprint Planning (Ceremony 1) and accepts finished increments in Sprint Review (Ceremony 2).

---

## 3. Chesterton's Fence Requirement Archaeology

Whenever an existing requirement, test contract, or core architecture rule is modified, relaxed, or replaced, the Stage 2 specification and stage deliverable **MUST** include the four mandatory fields of Chesterton's Fence:

1. **Original Requirement ID & Target**: The exact identifier and component target of the existing specification.
2. **Historical Origin & Commit Trace**: When and why the original rule was introduced (commit hash, ticket key, sprint number).
3. **Root Reason for Existing Formulation**: The problem the original rule was designed to solve (*"Why was this fence built?"*).
4. **Preservation of Core Invariants**: Technical proof that the proposed modification will not re-introduce the original defect or compromise system invariants (*"Why is it safe to modify now?"*).

---

## 4. The Document Triad Architecture

Engineering standards in a Höchstmaß repository are structured into three distinct, living tiers:

1. **In-Sprint Core Governance Rules** (`.agents/rules/aspice_governance.md`): Binding operational rules governing agent behavior, subagent topologies, gate transitions, and git hygiene during active sprint execution.
2. **Coding Standards & Architectural Invariants** (`docs/coding_standards.md`): Core software engineering construction standards, DRY principles, memory safety, and thread-safety invariants.
3. **Design & Interaction Guidelines** (`docs/design_guidelines.md`): UI/UX component selection heuristics, modal dialog hierarchies, accessibility standards, and visual consistency rules.
