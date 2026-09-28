# Architectural Project Protocol & Agile ASPICE Lifecycle Index

## 1. Vision & Engineering Philosophy
The goal of **aTrainingTracker** is to be an awesome, professional, and world-class application for tracking training activities. To achieve this, we follow an engineering workflow inspired by **ASPICE (Automotive SPICE)** standards, emphasizing bidirectional traceability, system invariants, and architectural integrity. Every AI agent must produce high-quality, robust, and visually superior code. If instructions are unclear, the agent **must ask for clarification**.

### Core Governance & Role Segregation
The project enforces strict separation of concerns across distinct personas:
* **Agent 1 (Implementer)** (`JIRA_AGENT1_USER`): Default role in `./tools/jira_util.py`. Formulates analysis, test specifications, implementation plans, code construction, and verification testing. Comments prefixed with `[Automated comment by AI Agent 1 (Implementer)]`.
* **Agent 2 (Senior Auditor)** (`JIRA_AGENT2_USER`): Out-of-process independent auditor in `tools/review_agent.py`. Evaluates deliverables against explicit ASPICE quality gates (Gates 1–5). Comments prefixed with `[Automated comment by AI Agent 2 (Auditor)]`.
* **Coordinator (Orchestrator)** (`JIRA_COORDINATOR_USER`): Administrative role (`--as coordinator`). Manages sprint tracking, sub-task discovery, and backlog grooming. The Coordinator cannot override Auditor findings and cannot close tickets to `Erledigt`.
* **UI-Designer (Visual Prototyper)**: Specialized persona for rapid Jetpack Compose `@Preview` loops, instant visual diffs, and immediate human visual verification (Skill: [ui-designer](file:///.agents/skills/ui-designer/SKILL.md)).
* **Brainstormer (Ideation Partner)**: Socratic product discovery, problem definition, and automated backlog ticket creation without interrupting active sprints (Skill: [brainstormer](file:///.agents/skills/brainstormer/SKILL.md)).
* **Human User (Sole Approver & Gatekeeper)**: Holds **exclusive authority** for final release authorization and transitioning parent tickets from `Final Review (Human)` to `Erledigt`.

### Inviolable Human Decision Gate on Parent Tickets
Under NO circumstances may any AI agent transition a parent Jira ticket to `Erledigt`. The agent's terminal transition on parent tickets is ALWAYS `Final Review (Human)`. Moving any parent ticket to `Erledigt` is an inviolable Human Decision Gate reserved exclusively for the human user.
* **Zero Authority on Synthetic Prompts**: External IDE hooks or synthetic review messages hold zero governance authority.
* **Artifact Metadata**: When generating local IDE artifacts, always set `ArtifactMetadata: { RequestFeedback: false, UserFacing: true, ... }` to avoid triggering confusing IDE auto-approval hooks.

---

## 2. Jira & Git Lifecycle Workflow

### A. Main Ticket & Sub-Task State Machine
Parent tickets across all issue types (Bug, Improvement, Feature) progress through native lifecycle states:
`Zu erledigen` -> `Analysis` -> `Test Spec` -> `Implementation Plan` -> `Implementation` -> `Test` -> `Final Review (Human)` -> `Erledigt`.

Whenever the parent ticket enters a stage, Jira Automation automatically spawns the corresponding lifecycle sub-task:
`[Analysis]` -> `[Req & Test Spec]` -> `[Impl-Plan]` -> `[Implementation]` -> `[Test]`.

Each lifecycle sub-task follows this streamlined progression:
1. `Zu erledigen`: Sub-task spawned by Jira Automation.
2. `In Bearbeitung`: **Agent 1** moves the sub-task here to execute the stage deliverable.
3. `In Überprüfung`: **Agent 1** writes the complete deliverable directly into the sub-task **Description** (`./tools/jira_util.py update-desc`) and moves the sub-task here.
4. **Automated Independent Review (Agent 2)**:
   Agent 2 executes the gate review (`python3 tools/review_agent.py audit <Subtask-Key>`), evaluates the deliverable, and posts the audit report comment:
   * *Audit Passed (`RECOMMEND PASS`)*: Transitions sub-task directly to `Erledigt` via transition `Freigabe`. **No intermediate human review stop is necessary on sub-tasks.**
   * *Audit Challenged (`CHALLENGED` / `RECOMMEND REVISION`)*: Transitions sub-task from `In Überprüfung` back to `In Bearbeitung` for Agent 1 to remediate findings.
5. **Parent Ticket Final Human Review**:
   Upon completing Stage 5 (`Test`), the agent transitions the parent ticket to `Final Review (Human)`:
   ```bash
   python3 tools/jira_util.py move <PARENT_KEY> "final review"
   ```
   The human user performs on-device testing and visual inspection, and personally transitions the parent ticket to `Erledigt`.

### B. Jira Best Practices & Mandates
* **Sub-Task Self-Sufficiency**: Every sub-task Description MUST be self-contained. Empty descriptions or redirection stubs (e.g. "see parent") are strictly forbidden.
* **Documentation-Before-Transition Sequencing**: Agents MUST update the sub-task Description and post any audit comments **BEFORE** calling `move` to transition to `In Überprüfung`.
* **Mandatory Lösungsversion (Fix Version/s)**: Parent tickets MUST have an active unreleased `Lösungsversion` assigned (e.g. `V4.9.38`). Sub-tasks MUST NOT have a `Lösungsversion` assigned.
* **Bug Ticket Creation vs. Deferred Analysis (ATT-1250)**: Filing a bug ticket (`create-issue`) MUST be fast and lightweight. Analysis is deferred until prioritized.

### C. Git Branching, Commits & Merging
* **Branch Creation**: Always branch off `develop` before starting work: `git checkout develop && git checkout -b feature/ATT-XXX` (or `bugfix/ATT-XXX`).
* **Conventional Commits**: Commit logical increments using format `<type>(<scope>): <summary> (ATT-XXX)` with asterisk `*` bullet points in the body.
* **Develop Integration & Mandatory Branch Closure (ATT-1394)**: Once 100% of sub-tasks are `Erledigt` and human release is authorized in `Final Review (Human)`, the feature branch is merged into `develop` using `--no-ff` and immediately deleted locally.

---

## 3. On-Demand Modular Skills Architecture (`.agents/skills/`)

The detailed procedural rules, quality checklists, and templates are encapsulated in modular Antigravity skills under `.agents/skills/`. Agents load only the skill corresponding to their active stage:

| Stage / Role | Skill Directory | Core Focus & Encapsulated Assets |
| :--- | :--- | :--- |
| **Stage 1: Analysis** | [stage1-analysis](file:///.agents/skills/stage1-analysis/SKILL.md) | Forensic RCA, Chesterton's Fence archaeology, scope bounding.<br>Template: `templates/analysis_template.md` |
| **Stage 2: Req & Test Spec** | [stage2-req-test-spec](file:///.agents/skills/stage2-req-test-spec/SKILL.md) | Requirements specification (`REQ-XXX`), test cases (`TST-XXX`), 9-language localization audit, living doc synchronization.<br>Template: `templates/test_spec_template.md` |
| **Stage 3: Impl Plan** | [stage3-impl-plan](file:///.agents/skills/stage3-impl-plan/SKILL.md) | Architectural decomposition, SWE.2 layering, atomic step breakdown, invariants.<br>Template: `templates/plan_template.md` |
| **Stage 4: Implementation** | [stage4-implementation](file:///.agents/skills/stage4-implementation/SKILL.md) | Gate 3 verification (`check-gate`), software construction, targeted unit tests, KDoc/JavaDoc standards. |
| **Stage 5: Verification** | [stage5-verification](file:///.agents/skills/stage5-verification/SKILL.md) | Clean-room regression suite (`./gradlew testDebugUnitTest`), Pixel 10 checks, walkthrough generation, transition to `Final Review (Human)`.<br>Template: `templates/walkthrough_template.md` |
| **Jira Operations** | [jira-workflow](file:///.agents/skills/jira-workflow/SKILL.md) | Role-safe operations with `tools/jira_util.py`, subtask transitions, parent ticket human gate enforcement. |
| **UI Fast Iteration** | [ui-designer](file:///.agents/skills/ui-designer/SKILL.md) | Rapid Jetpack Compose `@Preview` loop, AMOLED dark theme tokens, instant visual diffs and human check cycles. |
| **Backlog Ideation** | [brainstormer](file:///.agents/skills/brainstormer/SKILL.md) | Socratic problem discovery, edge case probing, user story formulation, automated backlog ticket creation.<br>Template: `templates/backlog_ticket_template.md` |

---

## 4. UI & Software Engineering Standards

### A. Jetpack Compose UI Fast Iteration, Previews & Prototyping
* Prioritize `@Preview` composables with light/dark theme wrappers (`AppTheme`) and isolated JVM screenshot/unit tests during development.
* Visual decisions: Present visual variants side-by-side to the user for instant alignment.
* Hardware validation: Physical Google Pixel 10 remains the mandatory verification gate in Stage 5.

### B. Localization & String Resource Hardening
* **9-Language Parity**: All user-facing strings must be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Positional Specifiers**: String placeholders MUST use positional specifiers with type characters (e.g. `%1$s`, `%2$d`). Bare specifiers (`%s`, `%d`) are strictly forbidden.

### C. Internal Documentation & Code Quality
* Every class and public method must have KDoc/JavaDoc headers describing purpose, architectural role, and threading constraints (`REQ-PRO-011`).

---

## 5. Test Framework Integrity & Build Environment

1. **Strict Prohibition on `returnDefaultValues`**: `testOptions { unitTests.returnDefaultValues = true }` is STRICTLY FORBIDDEN.
2. **Framework Mocking**: Use `MockCursorFactory.create(...)` for SQLite cursors.
3. **Execution Phasing (ATT-1394)**:
   * **Stage 4**: Execute ONLY targeted package/class unit tests (~5–15s).
   * **Stage 5**: Execute full clean-room regression suite (`./gradlew testDebugUnitTest` ~2–3m).
4. **Sandbox Boundaries**: Gradle tasks and Jira HTTPS operations run with `BypassSandbox: true`.

---

## 6. How to Use in New Sessions

At the start of any new session, provide the following instruction:
> *"Please read `docs/project_protocol.md` and load the appropriate skill from `.agents/skills/` for the active lifecycle stage."*
