# Sprint Review & Retrospective: Sprint 2026-40.2 (Release V4.9.38)

* **Ticket**: [ATT-1511](https://atrainingtracker.atlassian.net/browse/ATT-1511) (*Review & Retro*)
* **Sprint**: `2026-40.2`
* **Target Release Version**: `V4.9.38`
* **Branch**: `sprint/2026-40.2` -> `develop`

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.2**, 2 core improvement and bug tickets were implemented, verified through the unified 5-stage ASPICE process, validated on physical hardware (Google Pixel 10), and cleanly integrated into the sprint integration branch:

### 1.1 Summary of Shipped Tickets
| Ticket | Type | Summary | Key Impact & Solution | Traceability | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-1503](https://atrainingtracker.atlassian.net/browse/ATT-1503)** | Verbesserung | Standardize edit action symbols across the entire application | Standardized the Material 3 `Icons.Default.Edit` pencil icon across all entity edit touchpoints (clusters, sports, heart rate zones, power zones, equipment, lap editing, locations) while preserving `#000000`-tinted table edit icon for tracking layouts drawer navigation. | `REQ-UI-182`, `TST-UI-134` | **Erledigt** (Approved by Human) |
| **[ATT-1479](https://atrainingtracker.atlassian.net/browse/ATT-1479)** | Bug | Maps display light/white background and lack dark theme styling in dark mode | Implemented universal dynamic map property resolution (`DarkMapStyle.resolveMapProperties`), applying `MapType.NORMAL` + `res/raw/map_style_dark.json` in dark mode and `MapType.TERRAIN` in light mode, with double-layer `#121212` anti-flash background protection across 9 `GoogleMap` instances in 8 files. | `REQ-MAP-021`, `TST-MAP-023` | **Erledigt** (Approved by Human) |

### 1.2 Backlog Discovery & Decoupled Scope
* **[ATT-1553](https://atrainingtracker.atlassian.net/browse/ATT-1553)** (*[Bug] [Map] [Dark Mode] Initial bright/white map flash when loading list views with maps*):
  During on-device testing of ATT-1479 on Google Pixel 10, an occasional brief flash of a light map was observed when list views containing map previews (e.g. period summary lists) mount or scroll before Google Maps SDK finishes async style dispatch. Rather than attempting a hasty, unverified on-the-fly fix at sprint close, a dedicated Bug ticket was created, linked to parent Epic **ATT-1157** (*Optimize dark mode*), and ranked at the **top of the backlog** for systematic analysis in the next sprint.

---

## 2. Retrospective: Analysis of Observations & Countermeasures

### 2.1 Observation 1: Strict Single-Ticket Focus During Joint Review
* **Observation**:
  During Sprint-End Joint Review, presenting multiple tickets or bundling review requests creates fragmented attention and increases review friction.
* **Human Feedback**:
  > *"Please make one ticket after the other. Please also keep this in mind for the retro."*
* **Root Cause**:
  AI agent attempted to summarize the entire sprint state rather than conducting sequential, focused reviews.
* **Countermeasure & Protocol Rule**:
  In Ceremony 2 (Joint Review) and across sprint reviews, agents MUST strictly present and evaluate tickets **one-by-one**. A ticket must be fully reviewed, verified on-device, and accepted by the user before the next ticket is introduced.

### 2.2 Observation 2: Strict Prohibition on Autonomous Sprint Scope Alterations
* **Observation**:
  An agent erroneously touched an out-of-sprint ticket (`ATT-1523`) and attempted to pull it into the sprint without authorization.
* **Human Feedback**:
  > *"Agents must not move tickets to sprints!"*
  > *"ATT-1523 is not part of the current sprint."*
* **Root Cause**:
  Over-eager automation attempting to pre-empt sprint scope or pull backlog items autonomously.
* **Countermeasure & Protocol Rule**:
  AI agents are strictly forbidden from moving tickets into active sprints or pulling backlog tickets autonomously. Only the human user assigns tickets to sprints during Ceremony 1 (Sprint-Start Screening).

### 2.3 Observation 3: Ticket Creation vs. Implementation Separation
* **Observation**:
  When requested to file a newly discovered bug or improvement ticket into Jira, the agent immediately attempted to start implementing it.
* **Human Feedback**:
  > *"When an agent is asked to create a ticket, he immediately wants to start realizing it. This must never ever happen!"*
* **Root Cause**:
  Confusing ticket logging/backlog creation with an execution command.
* **Countermeasure & Protocol Rule**:
  When instructed to create a ticket (e.g. via `jira_util.py create-issue`), the agent MUST ONLY create the issue, link its epic/parent, set its backlog rank, report the issue key to the user, and **STOP**. Autonomous branching, subtask creation, or stage execution on newly created tickets without explicit user authorization is strictly prohibited.

### 2.4 Observation 4: Sub-Tasks Must Not Have a "Lösungsversion" (Fix Version)
* **Observation**:
  Sub-tasks in Jira were occasionally assigned a `Lösungsversion` (Fix Version/s) alongside parent tickets.
* **Human Feedback**:
  > *"Again: Sub-Tasks must not get a solution!"*
* **Root Cause**:
  Automated tooling or command flags applying parent fix versions recursively to subtasks.
* **Countermeasure & Protocol Rule**:
  Sub-tasks represent stage activities (Analysis, Req/Test Spec, Impl Plan, Implementation, Verification), not deliverable release solutions. Only parent user-facing tickets (Stories, Bugs, Improvements) may have a `Lösungsversion` assigned. Sub-tasks must always have `fixVersions` empty.

### 2.5 Observation 5: Immediate In-Sprint Issue Logging in Review & Retro Ticket
* **Observation**:
  Process hiccups and observations were sometimes only discussed in conversation rather than being systematically tracked where the team can review them.
* **Human Feedback**:
  > *"Agent(en) sollten Auffälligkeiten / Probleme gleich als Kommentar im Review & Retro Ticket des Sprints hinterlegen."*
* **Countermeasure & Protocol Rule**:
  During the active sprint, any observed process anomalies, tool failures, or user corrections must be immediately posted as comments on the active sprint's `Review & Retro` ticket (e.g. `ATT-1511`) so they are documented in real time for Ceremony 2.

---

## 3. Protocol Hardening & Permanent Enforcements

The following permanent updates are established across `docs/project_protocol.md`, `sprint-planner` skill, and `jira-workflow` skill:
1. **Rule: Single-Ticket Joint Review**: Reviews in Ceremony 2 must proceed strictly sequentially, one ticket at a time.
2. **Rule: No Agent Sprint Manipulation**: Agents never move issues into sprints.
3. **Rule: Pure Backlog Ticket Creation**: Creating a ticket in Jira never triggers autonomous implementation.
4. **Rule: No FixVersion on Subtasks**: Sub-tasks must never receive a `Lösungsversion`.
5. **Rule: Continuous Retro Commenting**: Anomalies and feedback are logged immediately into the sprint's Review & Retro ticket.
