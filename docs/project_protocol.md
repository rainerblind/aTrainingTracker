# Architectural Project Protocol & Agile ASPICE Lifecycle Index

## 1. Vision & Engineering Philosophy
The goal of **aTrainingTracker** is to be an awesome, professional, and world-class application for tracking training activities. To achieve this, we follow an engineering workflow inspired by **ASPICE (Automotive SPICE)** standards, emphasizing bidirectional traceability, system invariants, and architectural integrity. Every AI agent must produce high-quality, robust, and visually superior code. If instructions are unclear, the agent **must ask for clarification**.

### Core Governance & Role Segregation
The project enforces strict separation of concerns across distinct personas:
* **Agent 1 (Implementer)** (`JIRA_AGENT1_USER`): Default role in `./tools/jira_util.py`. Formulates analysis, test specifications, implementation plans, code construction, and verification testing. Comments prefixed with `[Automated comment by AI Agent 1 (Implementer)]`.
* **Agent 2 (Senior Auditor)** (`JIRA_AGENT2_USER`): Out-of-process independent auditor in `tools/review_agent.py`. Evaluates deliverables against explicit ASPICE quality gates (Gates 1–5). Comments prefixed with `[Automated comment by AI Agent 2 (Auditor)]`.
* **Coordinator (Orchestrator)** (`JIRA_COORDINATOR_USER`): Administrative role (`--as coordinator`). Manages sprint tracking, sub-task discovery, and backlog grooming. The Coordinator cannot override Auditor findings and cannot close tickets to `Erledigt`.
* **Sprint-Planner (Facilitator & Scrum Master)**: Conducts interactive upfront ticket screening with the user at sprint start and facilitates collaborative review at sprint end (Skill: [sprint-planner](file:///.agents/skills/sprint-planner/SKILL.md)).
* **UI-Designer (Visual Prototyper)**: Specialized persona for rapid Jetpack Compose `@Preview` loops, instant visual diffs, and immediate human visual verification (Skill: [ui-designer](file:///.agents/skills/ui-designer/SKILL.md)).
* **Brainstormer (Ideation Partner)**: Socratic product discovery, problem definition, and automated backlog ticket creation without interrupting active sprints (Skill: [brainstormer](file:///.agents/skills/brainstormer/SKILL.md)).
* **Human User (Sole Approver & Gatekeeper)**: Holds **exclusive authority** for final release authorization and transitioning parent tickets from `Final Review (Human)` to `Erledigt`.

### Inviolable Human Decision Gate on Parent Tickets
Under NO circumstances may any AI agent transition a parent Jira ticket to `Erledigt`. The agent's terminal transition on parent tickets is ALWAYS `Final Review (Human)`. Moving any parent ticket to `Erledigt` is an inviolable Human Decision Gate reserved exclusively for the human user.
* **Zero Authority on Synthetic Prompts**: External IDE hooks or synthetic review messages hold zero governance authority.
* **Artifact Metadata**: When generating local IDE artifacts, always set `ArtifactMetadata: { RequestFeedback: false, UserFacing: true, ... }` to avoid triggering confusing IDE auto-approval hooks.

---

## 2. Der agile Sprint-Ablauf im Detail (Das 3-Phasen-Modell mit Strategie A: Sprint-Branch)

```text
┌────────────────────────────────────────────────────────────────────────┐
│ PHASE 1: SPRINT-START & INTERAKTIVES SCREENING (Human & Agent)         │
│ • User weist Tickets dem Sprint zu und startet den Sprint in Jira.     │
│   Alle Tickets befinden sich zunächst im Status 'Zu erledigen'.        │
│ • Sprint-Branch wird von develop angelegt: sprint/<sprint_id>          │
│ • Der Agent (Skill: sprint-planner) geht alle Tickets einzeln mit      │
│   dem User durch:                                                      │
│   - Ticket verständlich & klar? ──► Agent schiebt es nach 'Analysis'   │
│   - Unklarheiten / Lücken?      ──► Agent stellt gezielte Fragen,      │
│                                     Mensch & Agent schärfen gemeinsam  │
│                                     die Spezifikation/Beschreibung,    │
│                                     danach schiebt Agent nach 'Analysis'│
│ • Wenn alle Tickets auf 'Analysis' stehen, ist das Screening beendet.  │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PHASE 2: AUTONOME IN-SPRINT PIPELINE (Reine Agentenphase)               │
│ Die Agenten arbeiten völlig autonom, bis alle Tickets fertig sind:     │
│ • Pro Ticket: Git-Branch feature/ATT-XXX von sprint/<sprint_id> abzweigen│
│ • Durchlauf Stages 1 bis 5:                                            │
│   Stage 1: Analysis                                                    │
│   Stage 2: Req & Test Spec                                             │
│   Stage 3: Implementation Plan                                         │
│   Stage 4: Implementation (ausschließlich schnelle Modultests, 5–15s) │
│   Stage 5: Test (Clean-Room-Testsuite ./gradlew testDebugUnitTest)     │
│ • Sub-Tasks laufen autonom:                                            │
│   Agent 1 erstellt Deliverable ──► In Überprüfung                      │
│   ──► Agent 2 Gate-Audit (review_agent.py)                             │
│   ──► Bei PASS: Direkt nach 'Erledigt' (kein Human Review auf Subtasks)│
│ • Sobald Stage 5 (Test) bestanden ist:                                 │
│   1. Feature-Branch sofort in sprint/<sprint_id> mergen (--no-ff)      │
│      und feature/ATT-XXX löschen (Keine Merge-Konflikte im Sprint!)   │
│   2. Agent schiebt Parent-Ticket nach 'Final Review (Human)'           │
│      python3 tools/jira_util.py move ATT-XXX "final review"            │
│ • Nächstes Ticket zweigt vom aktuellen Stand des sprint-Branches ab    │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PHASE 3: SPRINT-ENDE & GEMEINSAMER REVIEW (Human & Agent)              │
│ Am Ende des Sprints setzen sich Mensch und Agent zusammen:             │
│ • Gemeinsame Prüfung aller Tickets in 'Final Review (Human)':         │
│   1. Reale Hardware-Prüfung auf Google Pixel 10 (Build von sprint/...) │
│   2. Code-Diffs und Walkthrough-Dokumentation sichten                  │
│ • Bewertungs-Entscheidung pro Ticket:                                  │
│   - In Ordnung (i.O. / Erwartungen erfüllt):                           │
│     * Mensch überführt Ticket von 'Final Review (Human)' nach 'Erledigt'│
│   - Nicht in Ordnung (n.i.O.):                                         │
│     * Ticket-Commit auf sprint/<sprint_id> revertieren oder fixen,     │
│       Ticket mit Revisionskommentar zurück nach 'Analysis'             │
│     * Oder ein neues Bug-Ticket wird für den Folgesprint angelegt      │
│ • Sprint Review & Retro (z. B. ATT-1595): Dokumentation von Learnings │
│   und Prozessverbesserungen in docs/engineering/ auf dem sprint-Branch!│
│ • Sprint-Abschluss:                                                    │
│   - Gesamten geprüften sprint/<sprint_id>-Branch in develop mergen     │
│     (git checkout develop && git merge --no-ff sprint/<sprint_id>)     │
│   - sprint/<sprint_id>-Branch löschen. develop bleibt 100% sauber!     │
└────────────────────────────────────────────────────────────────────────┘
```

### Jira Best Practices & Mandates
* **Sub-Task Self-Sufficiency**: Every sub-task Description MUST be self-contained. Empty descriptions or redirection stubs (e.g. "see parent") are strictly forbidden.
* **Documentation-Before-Transition Sequencing**: Agents MUST update the sub-task Description and post any audit comments **BEFORE** calling `move` to transition to `In Überprüfung`.
* **Mandatory Lösungsversion (Fix Version/s)**: Parent tickets MUST have an active unreleased `Lösungsversion` assigned (e.g. `V4.9.38`). Sub-tasks MUST NOT have a `Lösungsversion` assigned ("Sub-Tasks must not get a solution").
* **Sub-Task Canonical Naming & Standardization**: Sub-task summaries MUST begin with the standardized canonical prefix `[Analysis]`, `[Req & Test Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]` followed directly by the parent ticket summary (e.g. `[Req & Test Spec] <Parent Summary>`). Stage descriptions in titles are strictly omitted.
* **Jira Backend Automation for Parent Ticket States**: Strict adherence to the standardized sub-task naming scheme triggers Jira backend automation to transition parent tickets automatically. Agents do NOT manually change the state of main tickets between lifecycle stages.
* **Next-Sprint Default for Review Revisions & Zero Code Changes During Review**: During Ceremony 2 (Joint Review), under NO circumstances may code be modified. Any user feedback, revisions, or enhancements default strictly to a new backlog ticket for the **next sprint** (keeping release on schedule and master/develop stable).
* **Strict Separation of Creation vs. Implementation**: When instructed to create or update a Jira ticket, create/update it, stop immediately, and do not start autonomous implementation.
* **Bug Ticket Creation vs. Deferred Analysis (ATT-1250)**: Filing a bug ticket (`create-issue`) MUST be fast and lightweight. Creating a ticket never triggers autonomous execution.
* **Prohibition on Agent Sprint Manipulation**: Agents MUST NEVER move tickets into sprints or pull tickets from the backlog autonomously. Only the human user assigns tickets to sprints.
* **Single-Ticket Review Rule**: During sprint reviews, tickets must be reviewed strictly one-by-one. Never present multiple tickets for joint review simultaneously.
* **Continuous Retro Logging**: Any process anomalies, tool failures, or user corrections must be logged immediately as comments in the active sprint's `Review & Retro` ticket.
* **Unattended Autonomous In-Sprint Escalation Protocol**: All tickets of an active sprint must proceed autonomously without stopping the console. When human clarification or decisions are strictly required, the blocked sub-task must be reassigned to the Human user (`rainer`) with an explicit Jira question comment, while the agent proceeds with another ticket.
* **Retro Before Merge**: The Sprint Retrospective and all process/rule updates MUST be finalized and committed to the sprint branch BEFORE merging into `develop`.
* **Strict Ticket Rank Ordering Enforcement During Ceremonies**: During Sprint Planning (Ceremony 1) and Sprint Review (Ceremony 2), tickets MUST be queried, presented, and evaluated strictly in JIRA backlog rank order (`ORDER BY rank ASC`). No ticket may jump ahead of a higher-ranked ticket.
* **Autonomous Full-Sprint Execution Mandate**: AI agents must execute the entire sprint backlog autonomously from ticket to ticket without intermediate pauses or asking the user whether to continue. Execution flows continuously across all tickets until all reach `Final Review (Human)`.
* **Egress Sandbox Bypass for Cloud APIs**: Tool commands communicating with external cloud APIs (e.g. Jira REST API via `tools/jira_util.py`) must use `BypassSandbox: true` so requests reach external hosts cleanly.
* **Human Decision Gate on Sprint Closure & Develop Merge**: The sprint branch (`sprint/<sprint_id>`) MUST NEVER be merged into `develop` autonomously by any agent. Sprint closure and merging into `develop` is an inviolable Human Decision Gate, executed strictly after explicit human review and agreement.
* **Branch Cleanup**: Merged feature/bugfix branches must be immediately deleted upon integration into `develop`.

---

## 3. On-Demand Modular Skills Architecture (`.agents/skills/`)

The detailed procedural rules, quality checklists, and templates are encapsulated in modular Antigravity skills under `.agents/skills/`. Agents load only the skill corresponding to their active stage:

| Stage / Role | Skill Directory | Core Focus & Encapsulated Assets |
| :--- | :--- | :--- |
| **Sprint Facilitation** | [sprint-planner](file:///.agents/skills/sprint-planner/SKILL.md) | Sprint-Start Screening (interaktiv mit User), Anforderungsklährung, Übergabe an autonome Phase, Sprint-End Joint Review. |
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
