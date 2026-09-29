# Sprint Review & Retrospective: Sprint 2026-40.3 (Release V4.9.38)

* **Ticket**: [ATT-1522](https://atrainingtracker.atlassian.net/browse/ATT-1522) (*Review & Retro*)
* **Sprint**: `2026-40.3`
* **Target Release Version**: `V4.9.38`
* **Branch**: `sprint/2026-40.3` -> `develop`

---

## 1. Executive Summary & Shipped Scope

During Sprint **2026-40.3**, 10 improvement, feature, and bug tickets were thoroughly implemented, rigorously verified through the unified 5-stage ASPICE process, validated on physical hardware (Google Pixel 10), and cleanly integrated into the sprint integration branch:

### 1.1 Summary of Shipped Tickets
| Ticket | Type | Summary | Key Impact & Solution | Traceability | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **[ATT-1553](https://atrainingtracker.atlassian.net/browse/ATT-1553)** | Bug | Map anti-flash overlay | Eliminated light map flashing in dark mode lists by enforcing black placeholder backgrounds and synchronized render timings. | `REQ-MAP-022`, `TST-MAP-024` | **Erledigt** (Approved by Human) |
| **[ATT-1547](https://atrainingtracker.atlassian.net/browse/ATT-1547)** | Verbesserung | Navigation drawer icon contrast tinting | Dynamic high-contrast tinting for monochrome drawer navigation icons in Dark & Light modes while preserving partner brand logos (Strava, Dropbox). | `REQ-UI-123`, `TST-UI-135` | **Erledigt** (Approved by Human) |
| **[ATT-1523](https://atrainingtracker.atlassian.net/browse/ATT-1523)** | Verbesserung | Universal delete-only long-press menu | Replaced legacy edit/delete popup menus across entity cards with a streamlined, non-conflicting delete-only long-press action. | `REQ-UI-061`, `TST-UI-131` | **Erledigt** (Approved by Human) |
| **[ATT-1454](https://atrainingtracker.atlassian.net/browse/ATT-1454)** | Feature | Phone battery & remaining runtime sensors | Added live phone battery percentage, power status, and predictive remaining runtime sensor metrics to tracking views. | `REQ-SEN-010`, `TST-SEN-010` | **Erledigt** (Approved by Human) |
| **[ATT-1530](https://atrainingtracker.atlassian.net/browse/ATT-1530)** | Verbesserung | Lieblingsorte card header & 3-dots removal | Removed cluttered 3-dots overflow icon and streamlined favorite location card layouts for clean touch interaction. | `REQ-UI-165`, `TST-UI-132` | **Erledigt** (Approved by Human) |
| **[ATT-1398](https://atrainingtracker.atlassian.net/browse/ATT-1398)** | Feature | Intelligent workout auto-naming | Automatically names workouts using recognized start and destination favorite locations (e.g., "Morgenrunde (Zuhause → Büro)"). | `REQ-DAT-015`, `TST-DAT-015` | **Erledigt** (Approved by Human) |
| **[ATT-1399](https://atrainingtracker.atlassian.net/browse/ATT-1399)** | Feature | Live Cockpit Location & Barometer Calibration Feedback | Reactive feedback confirming presence at a favorite location and barometric altitude calibration, placed at the top of the pre-workout setup tab. | `REQ-UI-183`, `TST-UI-136` | **Erledigt** (Approved by Human) |
| **[ATT-1400](https://atrainingtracker.atlassian.net/browse/ATT-1400)** | Feature | Display Start & Destination Locations in Workout Details and Map | Shows start and destination favorite locations on workout summary headers and preview map pins; coalesces round trips. | `REQ-UI-184`, `TST-UI-137` | **Erledigt** (Approved by Human) |
| **[ATT-1401](https://atrainingtracker.atlassian.net/browse/ATT-1401)** | Feature | Drill-Down Filter to View Workouts by Starting Location | Interactive starts badge on favorite location cards enables 1-tap navigation to filtered workout history with removable active filter chip. | `REQ-UI-185`, `TST-UI-138` | **Erledigt** (Approved by Human) |
| **[ATT-1402](https://atrainingtracker.atlassian.net/browse/ATT-1402)** | Feature | Lieblingsorte & Lieblingsstrecken Bridge | Enforces starting location invariant on route clusters, renders departure badges on cluster cards, and links route clusters from favorite location cards. | `REQ-UI-186`, `TST-UI-139` | **Erledigt** (Approved by Human) |

---

### 1.2 Backlog Discovery & Decoupled Scope Filed for Next Sprint
During the Joint Review of ATT-1401 and ATT-1402, two significant user experience improvements were identified and cleanly decoupled into the Jira product backlog without delaying release `V4.9.38`:
* **[ATT-1593](https://atrainingtracker.atlassian.net/browse/ATT-1593)** (*[Verbesserung] [Workout-Filter] Auswahl von Lieblingsorten im Workout-Filter-Dialog*):
  Enables selecting favorite start locations directly within the workout filter bottom sheet (`WorkoutFilterBottomSheet`) alongside dates, sports, and equipment.
* **[ATT-1594](https://atrainingtracker.atlassian.net/browse/ATT-1594)** (*[Verbesserung] [Lieblingsorte] Kompaktes Strecken-Badge auf Lieblingsort-Karten analog zum Starts-Badge*):
  Replaces the multi-chip list of route clusters on favorite location cards with a single, compact route badge (analogous to the interactive Starts badge) that navigates to filtered routes departing from that location.

---

## 2. Retrospective: Analysis of Observations & Countermeasures

### 2.1 Observation 1: Sub-Task Canonical Naming Scheme & Summary Standardization
* **Observation**:
  Sub-tasks across tickets occasionally varied in prefix formatting (e.g. `[Analysis]` vs `Stage 1 - Analysis` vs `Analysis:`), and some lacked the parent ticket title.
* **Human Feedback**:
  > *"Sub-Tasks must have the name of the main ticket in the summary."*  
  > *"The first part of the name of the sub-tasks must be identical over all main tickets. I.e, the creation of the sub-tickets must always get the same standard name scheme."*
* **Root Cause**:
  Manual subtask creation strings or varying prompt formulations in stage skills produced inconsistent naming syntax across subtasks.
* **Countermeasure & Permanent Rule**:
  Sub-tasks MUST follow the exact standardized prefix syntax:
  - `[Analysis] Problem Domain & Root Cause Analysis (<Parent Summary>)`
  - `[Test-Spec] Requirement & Test Specification (<Parent Summary>)`
  - `[Impl-Plan] Architecture & Implementation Plan (<Parent Summary>)`
  - `[Implementation] Software Construction & Unit Tests (<Parent Summary>)`
  - `[Test] Verification, Clean-Room Regression & Release Verification (<Parent Summary>)`
  Enforced directly in `tools/jira_util.py` via automated summary prefix normalization.

---

### 2.2 Observation 2: Active Parent Ticket Lifecycle Stage Advancement
* **Observation**:
  Parent tickets remained in earlier stages despite child subtasks advancing and completing.
* **Human Feedback**:
  > *"Jira Automation failes to move the main ticket forward. → should also be done by the agent."*
* **Root Cause**:
  Reliance on Jira Cloud backend automation rules that were either unconfigured or failed due to permission constraints.
* **Countermeasure & Permanent Rule**:
  AI agents MUST explicitly advance the parent ticket in lockstep with the ASPICE lifecycle stages (`Analysis` -> `Test Spec` -> `Impl Plan` -> `Implementation` -> `Final Review (Human)`). Do not rely on external Jira server-side automation.

---

### 2.3 Observation 3: Joint Review Revision Intent & Timing Clarity
* **Observation**:
  During the review of ATT-1399, the user intended to defer a layout revision to the next sprint, but the agent assumed immediate in-sprint execution and started refactoring right away.
* **Human Feedback**:
  > *"ATT-1399: During the Sprint Review, the human wanted to move this ticket back to Analysis to fix an issue within the next sprint but the agent(s) started to fix this immediately. From the question the human had to answer, it was not clear that the fix will be immediately."*
* **Root Cause**:
  The interactive choice presented during rejection was ambiguous regarding timing (*now in this sprint* vs. *deferred to next sprint*).
* **Countermeasure & Permanent Rule**:
  When the user expresses feedback or dissatisfaction during Ceremony 2 (Joint Review), the agent MUST explicitly ask for clarification with clear, mutually exclusive options:
  - **Option A (Immediate Revision)**: Fix and verify immediately on a feature branch within the current sprint before release.
  - **Option B (Defer to Next Sprint)**: Accept the current ticket as-is, keep the release on schedule, and file a separate backlog ticket for the next sprint.

---

### 2.4 Observation 4: Strict Separation of Ticket Creation vs. Implementation
* **Observation**:
  When asked to create a follow-up ticket in the backlog, agents have a tendency to immediately begin implementing it.
* **Human Feedback**:
  > *"Again: Do not start any implementation. Please create and update the jira tickets."*
* **Root Cause**:
  Agent bias towards proactive action conflates backlog recording with an execution directive.
* **Countermeasure & Permanent Rule**:
  When instructed to create or update a ticket, the agent MUST ONLY create/update the Jira ticket, report the issue key, and **STOP**. Autonomous branching, subtask creation, or stage execution is strictly forbidden.

---

### 2.5 Observation 5: Sandbox Network Isolation & Jira REST API Proxy Behavior
* **Observation**:
  Jira CLI operations run with `BypassSandbox: false` failed with `HTTP Error 403: Request to POST ... not allowed by policy`.
* **Root Cause**:
  The standard sandbox environment enforces strict network isolation; local security proxies intercept and reject outbound connections to external cloud APIs with plain-text 403 responses.
* **Countermeasure & Permanent Rule**:
  Commands requiring external network egress (such as `tools/jira_util.py` communicating with Jira Cloud) must run with `BypassSandbox: true` so requests reach `atrainingtracker.atlassian.net` cleanly.

---

## 3. Protocol Hardening & Permanent Enforcements

The following permanent updates are established across `.agents/rules/aspice_governance.md`, `sprint-planner` skill, and `docs/project_protocol.md`:
1. **Canonical Sub-Task Prefixes**: Strictly enforce `[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]` with parent ticket summary in parentheses.
2. **Explicit Parent Ticket Progression**: Agents actively transition parent tickets across all ASPICE stages.
3. **Unambiguous Review Revision Questions**: Always distinguish immediate in-sprint fixes from deferred next-sprint backlog tickets.
4. **Immediate Stop on Ticket Creation**: Never begin implementation after creating or updating a Jira ticket.
5. **Egress Sandbox Bypass**: Explicitly use `BypassSandbox: true` for Jira network commands.
