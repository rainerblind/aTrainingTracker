# Project Protocol: Requirement-Based Engineering

## Vision & ASPICE Alignment
The goal of **aTrainingTracker** is to be an awesome, professional, and world-class application for tracking training activities. To achieve this, we follow a workflow inspired by **ASPICE (Automotive SPICE)** standards, emphasizing bidirectional traceability and architectural integrity. Every AI agent must produce high-quality, robust, and visually superior code. If instructions are unclear, the agent **must ask for clarification**.

## Mandatory Development Workflow (TDD-Based)

Any AI assistant working on this project **must** follow these steps for every task:

1.  **Requirement Synchronization**:
    *   Before writing any code or plans, read `docs/requirements.md`.
    *   Add a new Requirement ID (e.g., `REQ-XXX-###`) or update an existing one to reflect the user's request.
    *   **Phasing Standards**: Requirements must be written with the precision of a professional requirements engineer:
        *   **SHALL**: For mandatory functional behavior (e.g., "The system SHALL...").
        *   **MUST**: For strict technical constraints or quality attributes (e.g., "The database MUST...").
        *   **Atomic & Unambiguous**: One requirement per entry; avoid vague terms like "easy", "improved", or "better".
        *   **System-Centric**: Describe system behavior, not user desires. (Avoid "The user wants...", "I would like...").
        *   **State-Oriented**: Requirements MUST describe the intended *behavior* or *state* of the system, NOT the *change process* or *implementation steps*. (Strictly avoid "The system SHALL change...", "The system SHALL rename...", "Update the...").
        *   **Lifecycle Management**: When introducing new functionality, create a new requirement. When modifying existing behavior that is already documented, **update the existing requirement's description** to reflect the new state instead of adding a "change" requirement.
        *   **System Invariants & Preserved Behavior**: Every requirement MUST explicitly specify what existing behavior, data precision, schema structure, or layer contract MUST NOT change (e.g., "The system MUST preserve existing TCX XML schema ordering and non-target metrics").
        *   **Given-When-Then Acceptance Criteria**: Complex functional requirements SHOULD include explicit Given-When-Then scenarios to remove ambiguity for implementation and testing.
    *   Define the **Rationale** (the "Why") clearly.
    *   Map the requirement to the relevant **Implementation File(s)**.

2.  **Test Definition (The TDD Hard Stop)**:
    *   **MANDATORY HARD STOP**: After requirement synchronization, the agent MUST define the verification criteria with the user.
    *   Identify which manual or automated tests in `docs/tests.md` will prove the requirement is met.
    *   If no suitable test exists, add a new one to `docs/tests.md` immediately.
    *   **Jira Integration**: For each identified or new test case, the agent MUST create a **Sub-task** in Jira linked to the main ticket.
        *   The sub-task **Summary** MUST follow the format: `[Test] ATT-XXX: TST-XXX-### - Summary`.
        *   The sub-task **Description** MUST be identical to the procedure and expected result defined in `docs/tests.md` to ensure absolute synchronization.
        *   After creation, the agent MUST update `docs/tests.md` to include the **Jira Ticket ID** of the sub-task (e.g., `ATT-123`) in the test case table for bidirectional traceability.
    *   **Iterative Refinement**: The agent must refine the test cases based on user feedback until the user explicitly agrees.
    *   **Enforcement**: The agent is strictly FORBIDDEN from proposing an implementation plan or writing any code until the user has formally agreed to the test cases in `docs/tests.md` and the corresponding sub-tasks have been created in Jira. This phase is used to clarify and freeze the requirements.

3.  **Impact Analysis (SWE.1.BP.5 Phase)**:
    *   Before implementation, perform a formal audit of existing code.
    *   **Mandatory `find_usages` Audit**: The agent MUST run `find_usages` or `grep` on all classes, methods, or string resource IDs slated for modification.
    *   **Mapped Requirements Cross-Check**: The agent MUST inspect `docs/requirements.md` to identify ALL Requirement IDs mapped to the target files. The agent MUST explicitly confirm that proposed edits will NOT break any of those mapped requirements.
    *   Identify potential side effects on:
        *   **Android System**: Battery usage, WakeLock durations, Background execution rules.
        *   **Component Interfaces**: Will a change in `BANALService` break the `MutableStateFlow` used by the UI?
        *   **Data Integrity**: Will a schema change affect backward compatibility of existing workout files?
    *   Document these risks in the implementation plan.

4.  **Jira Ticket Management & Dual-Agent Workflow (Agile Phase)**:
    *   **Automation**: Use the local utility `./tools/jira_util.py` for Jira interactions (list, show, comment, download, download-all, move).
    *   **Syntax**: All Jira comments must use **Jira Wiki Markup** (e.g., `h1.`, `{code}`, `*bold*`).
    *   **Credentials**: Authentication details are stored in `.env.jira` (not tracked in Git).
    *   **Sub-Task Workflow (`Zu erledigen` -> `In Bearbeitung` -> `In Überprüfung` -> `Freigabe (Human)` -> `Erledigt`)**:
        All lifecycle sub-tasks follow this strict state machine:
        1.  `Zu erledigen`: Sub-task is created.
        2.  `In Bearbeitung`: **Agent 1** moves the sub-task here to perform the primary task (e.g., investigate RCA, formulate plan, or implement code).
        3.  `In Überprüfung`: When Agent 1 finishes, Agent 1 updates the sub-task **Description** with the complete deliverable (RCA findings, Implementation Plan, or Walkthrough/Evidence) and moves the sub-task here.
        4.  `Freigabe (Human)`: **Agent 2** independently audits the work, posts the audit report/critique as a Jira comment, and transitions the sub-task here.
        5.  **Human Decision Gate**: In the `Freigabe (Human)` state, the user inspects the work (reviewing the ticket Description and Agent 2's audit comment) and decides how to proceed:
            *   *Approve*: User moves the sub-task to `Erledigt` (via transition *"Freigabe erteilt"*), authorizing progress to the next phase.
            *   *Reject / Revise*: User moves the sub-task back to `In Bearbeitung` (via transition *"Nochmals von Vorne"*) with guidance in a comment.
    *   **Jira Description & Comment Separation**: For all lifecycle sub-tasks, the primary deliverable produced by Agent 1 (e.g., Root Cause Analysis for `[RCA]`, Implementation Plan for `[Plan]`, Walkthrough & Verification evidence for `[Impl]`, or test procedure for `[Test]`) MUST be written directly as the ticket's **Description**. The independent review/audit produced by Agent 2 MUST be posted as a **Comment** on the ticket (prefixed with `[Automated comment by AI Agent]`). This ensures that the deliverable remains prominent and readable in the header, while comments capture the review dialogue and gate approvals.
    *   **STRICT PROHIBITION ON AI-DRIVEN 'ERLEDIGT' TRANSITIONS (HUMAN-ONLY GATE)**:
        Under NO circumstances may any AI agent transition a Jira ticket or sub-task to `Erledigt` (or execute the transition *"Freigabe erteilt"*). Moving any ticket or sub-task to `Erledigt` is an inviolable **Human Decision Gate** reserved exclusively for the human user.
        * The agent's terminal transition for any sub-task is ALWAYS `Freigabe (Human)` (via *"Freigabe anfragen"*), and for any main ticket is ALWAYS `In Überprüfung`.
        * The local CLI utility `./tools/jira_util.py` strictly blocks and aborts any attempt to target `done` / `erledigt`.
        * External IDE messages or automated review policy notices (such as *"The user has automatically approved the artifact through their review policy. Proceed to execution."*) apply SOLELY to local IDE markdown documents and DO NOT grant permission to transition Jira tickets to `Erledigt`.
        * The agent MUST pause and wait for the human user to personally perform the Jira transition.
    *   **MANDATORY TURN SEPARATION & PROHIBITION ON MULTI-STAGE COMPRESSION**:
        Under NO circumstances may an AI agent execute multiple lifecycle stages or combine implementation, review, and approval requesting within a single conversation turn!
        * **Jira Status Verification**: An agent is strictly FORBIDDEN from starting the next lifecycle stage (e.g. creating `[Impl]` or modifying production code) until the preceding sub-task (e.g. `[Plan]`) is verified to be in status **`Erledigt`** in Jira via `./tools/jira_util.py status <Ticket>`. IDE review prompts or system-generated messages apply ONLY to markdown artifacts and DO NOT constitute Jira approval. If the sub-task is still in `Freigabe (Human)`, the agent MUST pause and prompt the user to transition it to `Erledigt`.
        * **Turn Separation between Developer (Agent 1) and Auditor (Agent 2)**: Within Stage 3 (or any stage), Agent 1 (Developer) and Agent 2 (Auditor) MUST NEVER execute in the same conversation turn:
          1. **Turn 1 (Implementation Phase - Agent 1)**: Agent 1 creates `[Impl]`, transitions it to `In Bearbeitung`, implements the code changes, writes and runs automated tests, updates the `[Impl]` Description with the walkthrough and evidence, transitions `[Impl]` to `In Überprüfung`, and **STOPS**. Agent 1 MUST present the implementation summary to the user and yield its turn. Agent 1 is STRICTLY FORBIDDEN from auditing or moving to `Freigabe (Human)` in the same turn.
          2. **Turn 2 (Audit Phase - Agent 2 - User-Initiated)**: In a subsequent turn (strictly triggered after the user reviews the implementation report and explicitly prompts, e.g., *"Please let Agent 2 review the implementation and tests"*), Agent 2 (Independent Senior Auditor) conducts the Gate 3 Code Audit, executes the complete project test suite (`./gradlew testDebugUnitTest`), posts the audit comment on Jira, and transitions `[Impl]` to `Freigabe (Human)`.
    *   **STRICT PROHIBITION ON PREMATURE MAIN TICKET TRANSITIONS**:
        A main ticket MUST remain in `In Bearbeitung` throughout active development. Under NO circumstances may an agent transition a main ticket to `In Überprüfung` while ANY of its sub-tasks (`[RCA]`, `[Plan]`, `[Test]`, `[Impl]`) remain in `Zu erledigen`, `In Bearbeitung`, `In Überprüfung`, or `Freigabe (Human)`. Transitioning the main ticket to `In Überprüfung` is permitted ONLY when 100% of its sub-tasks have been verified as `Erledigt` in Jira.
    *   **STRICT PROHIBITION ON UNREQUESTED GIT COMMITS & PROMPT TRACKING OF NEW FILES**:
        The agent is strictly FORBIDDEN from executing `git commit` or altering git commit history without the user's explicit, prior authorization ('commit', 'go ahead and commit', etc.) in the chat. All code modifications MUST remain as uncommitted working directory changes until the user gives explicit instruction to commit. However, whenever the agent creates a new relevant file (e.g., plan artifact, walkthrough artifact, source file, or test suite), the agent SHALL immediately add it to git (`git add <file>`) so that it is properly tracked in git.
    *   **Git Branching Strategy & Lifecycle**:
        *   **Branch Isolation**: Every ticket starts by branching directly off `develop`:
            *   `feature/ATT-XXX`: For new features, functional additions, or architectural enhancements.
            *   `bugfix/ATT-XXX`: For defect resolution, bug fixes, or regressions.
        *   **Dedicated Workspace**: All exploratory code, implementation edits, test suites, and documentation artifacts (`docs/engineering/plans/`, `docs/engineering/walkthroughs/`) must reside exclusively on this branch.
        *   **Integration**: Merging back into `develop` occurs only after 100% of sub-tasks are approved into `Erledigt` and the user explicitly authorizes the git commit and merge in the chat.
    *   **Selection & Focus**: Multiple tickets may be "In Bearbeitung" (In Progress). The agent works on one chosen ticket at a time. While working on a ticket, it becomes the exclusive focus of the development session. The agent MUST fully complete the current topic (including documentation and verification) before concluding. The agent is strictly FORBIDDEN from asking to start a new ticket or suggesting the next task; the user holds sole initiative for task transitions. The agent SHALL NOT perform any preemptive research, code searches, or logic analysis for unrelated tickets or tasks that have not been explicitly assigned. However, to maintain **Contextual Awareness**, the agent SHOULD research other tickets and documentation within the active Epic to gain a holistic understanding of the feature area and ensure technical alignment. Exploratory analysis of unrelated 'next potential tasks' remains strictly forbidden.
    *   **Contextual Awareness**: Before starting work on a ticket, the agent MUST examine its **Epic** (if linked) to understand the overall picture and vision. The agent SHOULD ask clarifying questions about the Epic to ensure the current task aligns with the long-term goals. If the Epic's description is missing or vague, the agent SHOULD propose an updated description to the user. Once the overall idea of the Epic becomes clear, the agent MUST update the Epic's description in Jira using the `update-desc` command.
    *   **Clarification & Completeness**: If a ticket selected for work lacks a **Description**, specific failure logs, or clear technical context, the agent **MUST NOT** proceed with an implementation plan. Instead, the agent must ask the user for clarification and agreement on the problem statement first.
    *   **Type-Aware Engineering & 3-Stage Dual-Agent Lifecycle**:
        Depending on ticket type (Bug vs. Feature), development proceeds through three strictly sequential stages, each modeled as dedicated sub-tasks executing the 5-state lifecycle:

        ### A. Bug Tickets Lifecycle
        *   **Stage 1: Root Cause Analysis (`[RCA]`)**:
            1.  Sub-task created: `[RCA] ATT-XXX: Root Cause Analysis & Auditor Review`.
            2.  **Agent 1 (Forensic Investigator)**: Transitions sub-task from `Zu erledigen` to `In Bearbeitung`. Analyzes logs, traces, and code paths. Formulates technical root cause vs. symptoms and corrective concept. Sets the complete RCA as the ticket's **Description**. Transitions sub-task to `In Überprüfung`.
            3.  **Agent 2 (Independent Senior Auditor)**: Picks up sub-task in `In Überprüfung`. Conducts the Gate 1 review (scrutinizes findings, call sites, and system invariants). Posts the Gate 1 review report as a comment in Jira. Transitions sub-task to `Freigabe (Human)`.
            4.  **MANDATORY HARD STOP 1 (RCA Approval Gate)**: In `Freigabe (Human)`, the user reviews the analysis (in ticket Description) and audit comments:
                *   *Approve*: User moves sub-task to `Erledigt` (via *"Freigabe erteilt"*). **Agent 1 is strictly FORBIDDEN from starting the planning stage until the user has approved the RCA.**
                *   *Reject / Revise*: User moves sub-task back to `In Bearbeitung` (via *"Nochmals von Vorne"*) with feedback comments.

        *   **Stage 2: Implementation Planning (`[Plan]`)**:
            1.  Prerequisite: `[RCA]` sub-task is verified in status `Erledigt` in Jira.
            2.  Sub-task created: `[Plan] ATT-XXX: Implementation Plan & Auditor Review` (and verification test sub-tasks `[Test] ATT-XXX: TST-XXX-###`).
            3.  **Agent 1 (Architect / Planner)**: Transitions sub-task to `In Bearbeitung`. Formulates the plan at `docs/engineering/plans/ATT-XXX_plan.md`, maps requirements, affected components, and tests. Sets the full plan as the ticket's **Description**. Transitions sub-task to `In Überprüfung`.
            4.  **Agent 2 (Plan Auditor)**: Picks up sub-task in `In Überprüfung`. Audits the plan for architectural integrity, invariant safety, and complete test coverage (Gate 2 Plan Review). Posts the plan review report as a comment in Jira. Transitions sub-task to `Freigabe (Human)`.
            5.  **MANDATORY HARD STOP 2 (Plan Approval Gate)**: In `Freigabe (Human)`, the user reviews the plan (in ticket Description) and audit comments:
                *   *Approve*: User moves sub-task to `Erledigt` (via *"Freigabe erteilt"*). **Agent 1 is strictly FORBIDDEN from writing any production code until the user has approved the plan.**
                *   *Reject / Revise*: User moves sub-task back to `In Bearbeitung` (via *"Nochmals von Vorne"*) with revision requests.

        *   **Stage 3: Implementation & Verification (`[Impl]`)**:
            1.  Prerequisite: `[Plan]` sub-task is explicitly verified in status `Erledigt` via Jira CLI.
            2.  Sub-task created: `[Impl] ATT-XXX: Implementation & Code Review`.
            3.  **Agent 1 (Developer - Turn 1)**: Transitions sub-task to `In Bearbeitung`. Implements the approved changes, ensures KDoc headers and 9-language localization, and executes unit/integration tests (`docs/tests.md`). Sets the walkthrough and test verification evidence as the ticket's **Description**. Transitions sub-task to `In Überprüfung`. **MANDATORY STOP**: Agent 1 yields control to the user. Agent 1 MUST NOT conduct the audit or move to `Freigabe (Human)` in the same turn.
            4.  **Agent 2 (Code Auditor - Turn 2 - User-Initiated)**: Triggered strictly upon explicit user request. Conducts Gate 3 review (`git diff` scrutiny, side-effects, localization compliance, invariant check, and full-suite regression run `./gradlew testDebugUnitTest`). Posts the Gate 3 audit report as a comment in Jira. Transitions sub-task to `Freigabe (Human)`.
            5.  **MANDATORY HARD STOP 3 (Implementation Approval Gate)**: In `Freigabe (Human)`, the user reviews the walkthrough (in ticket Description) and code audit comments:
                *   *Approve*: User moves sub-task to `Erledigt` (via *"Freigabe erteilt"*).
                *   *Reject / Revise*: User moves sub-task back to `In Bearbeitung` (via *"Nochmals von Vorne"*) with change requests.

        ### B. Feature Tickets Lifecycle
        *   **Stage 1: Feature Scope & Test Specification (`[Test]`)**:
            1.  Sub-task created: `[Test] ATT-XXX: TST-XXX-### - Summary`.
            2.  Requirements synchronized in `docs/requirements.md` (SHALL/MUST, atomic, system invariants, Given-When-Then acceptance criteria).
            3.  Verification procedures and expected results defined in `docs/tests.md` and set as the sub-task's **Description**.
            4.  **MANDATORY HARD STOP 1 (Test Definition Gate)**: The user reviews and approves the test criteria and requirements:
                *   *Approve*: User moves sub-task to `Erledigt` (via *"Freigabe erteilt"*). **Agent 1 is strictly FORBIDDEN from proposing an implementation plan or writing code until test criteria are approved.**
                *   *Reject / Revise*: User moves sub-task back to `In Bearbeitung` with revision requests.

        *   **Stage 2: Implementation Planning (`[Plan]`)**:
            1.  Prerequisite: `[Test]` sub-task is verified in status `Erledigt` in Jira.
            2.  Sub-task created: `[Plan] ATT-XXX: Implementation Plan & Auditor Review`.
            3.  **Agent 1 (Architect / Planner)**: Transitions sub-task to `In Bearbeitung`. Formulates the plan at `docs/engineering/plans/ATT-XXX_plan.md`, maps requirements, affected components, and tests. Sets the full plan as the ticket's **Description**. Transitions sub-task to `In Überprüfung`.
            4.  **Agent 2 (Plan Auditor)**: Picks up sub-task in `In Überprüfung`. Audits the plan for architectural integrity, invariant safety, and complete test coverage (Gate 2 Plan Review). Posts the plan review report as a comment in Jira. Transitions sub-task to `Freigabe (Human)`.
            5.  **MANDATORY HARD STOP 2 (Plan Approval Gate)**: In `Freigabe (Human)`, the user reviews the plan and audit comments:
                *   *Approve*: User moves sub-task to `Erledigt` (via *"Freigabe erteilt"*). **Agent 1 is strictly FORBIDDEN from writing any production code until the user has approved the plan.**
                *   *Reject / Revise*: User moves sub-task back to `In Bearbeitung` with revision requests.

        *   **Stage 3: Implementation & Verification (`[Impl]`)**:
            1.  Prerequisite: `[Plan]` sub-task is explicitly verified in status `Erledigt` via Jira CLI.
            2.  Sub-task created: `[Impl] ATT-XXX: Implementation & Code Review`.
            3.  **Agent 1 (Developer - Turn 1)**: Transitions sub-task to `In Bearbeitung`. Implements the approved changes, ensures KDoc headers and 9-language localization, and executes unit/integration tests (`docs/tests.md`). Sets the walkthrough and test verification evidence as the ticket's **Description**. Transitions sub-task to `In Überprüfung`. **MANDATORY STOP**: Agent 1 yields control to the user.
            4.  **Agent 2 (Code Auditor - Turn 2 - User-Initiated)**: Triggered strictly upon explicit user request. Conducts Gate 3 review (`git diff` scrutiny, side-effects, localization compliance, invariant check, and full-suite regression run `./gradlew testDebugUnitTest`). Posts the Gate 3 audit report as a comment in Jira. Transitions sub-task to `Freigabe (Human)`.
            5.  **MANDATORY HARD STOP 3 (Implementation Approval Gate)**: In `Freigabe (Human)`, the user reviews the walkthrough and code audit comments:
                *   *Approve*: User moves sub-task to `Erledigt` (via *"Freigabe erteilt"*).
                *   *Reject / Revise*: User moves sub-task back to `In Bearbeitung` with change requests.

    *   **Traceable Sub-task Generation**: The agent creates dedicated Jira sub-tasks to maintain 100% transparency:
        *   `[RCA] ATT-XXX: Root Cause Analysis & Auditor Review`: Stage 1 RCA for bugs (in Description) & Gate 1 review (in Comments).
        *   `[Test] ATT-XXX: TST-XXX-### - Summary`: Stage 1 Test Definition for features (procedure in Description).
        *   `[Plan] ATT-XXX: Implementation Plan & Auditor Review`: Stage 2 Plan (in Description) & Gate 2 architectural review (in Comments).
        *   `[Impl] ATT-XXX: Implementation & Code Review`: Stage 3 Implementation Walkthrough (in Description) & Gate 3 code review (in Comments).
    *   **Documentation & Main Ticket Closure**:
        *   **Identity Disclaimer**: Every comment posted by the agent MUST start with a clear disclaimer: *"[Automated comment by AI Agent]"*.
        *   **Main Ticket Transitions**: When a main ticket is assigned, the agent transitions it to "In Bearbeitung". Only when 100% of all sub-tasks (`[RCA]`/`[Test]`, `[Plan]`, `[Impl]`) have been formally verified as `Erledigt` in Jira, the agent moves the main ticket to "In Überprüfung" and posts the final walkthrough comment. The user retains sole authority to move the main ticket to "Erledigt".

5.  **Architectural Integrity (SWE.2 Phase)**:
    *   Identify which core components are affected (e.g., `BANALService`, `TrackerService`, `Repository`).
    *   Define or update the **Interfaces** and **Data Flow** between components in `docs/architecture.md`.
    *   Ensure that new code does not violate the established architecture (e.g., maintain clear separation between background services and UI layers).

6.  **Implementation Planning (SWE.3 Phase - The Implementation Hard Stop)**:
    *   **Prerequisite**: The `[RCA]` sub-task MUST be in `Erledigt` status (formally approved by the user via *"Freigabe erteilt"*). Agent 1 is strictly FORBIDDEN from starting the planning stage without this prior approval.
    *   **Plan Artifact**: Create an implementation plan at `docs/engineering/plans/ATT-XXX_plan.md` (where XXX is the ticket number).
    *   Every proposed change **must** explicitly reference the Requirement ID, the Component affected, and the corresponding Test ID it fulfills.
    *   **Jira Sub-task Workflow**: The agent creates the dedicated sub-task `[Plan] ATT-XXX: Implementation Plan & Auditor Review`.
        1. Agent 1 transitions the sub-task to `In Bearbeitung`, formulates the plan, and sets it as the sub-task **Description**.
        2. Agent 1 transitions the sub-task to `In Überprüfung`.
        3. Agent 2 reviews the plan for architectural integrity, invariant safety, and test coverage, posts the review report as a comment in Jira, and transitions the sub-task to `Freigabe (Human)`.
    *   **MANDATORY HARD STOP 2 (Plan Approval Gate)**: The user reviews the plan and audit in `Freigabe (Human)`:
        *   *Approval*: Moving to `Erledigt` (via *"Freigabe erteilt"*) authorizes Agent 1 to begin code execution (Stage 3).
        *   *Revisions*: Moving back to `In Bearbeitung` (via *"Nochmals von Vorne"*) with feedback requires iterative refinement.
    *   **Enforcement**: The agent is strictly FORBIDDEN from performing any code modifications (writing files or replacing content) until the `[Plan]` sub-task has been approved by the user into `Erledigt`.

7.  **Execution & Multi-Stage Verification**:
    *   **Prerequisite**: The `[Plan]` sub-task MUST be explicitly verified in status `Erledigt` in Jira via `./tools/jira_util.py status <Ticket>`.
    *   **Jira Sub-task Workflow (Strict Turn Separation)**:
        1. **Turn 1 (Execution & Implementation - Agent 1)**: The agent creates the dedicated sub-task `[Impl] ATT-XXX: Implementation & Code Review`, transitions it to `In Bearbeitung`, implements the code changes, runs automated tests, sets the walkthrough and test verification evidence as the sub-task **Description**, transitions to `In Überprüfung`, and **MANDATORY STOP**. The agent presents the implementation report to the user and yields control. Agent 1 MUST NOT proceed to Agent 2 audit or transition to `Freigabe (Human)` in the same turn.
        2. **Turn 2 (Independent Audit & Review Gate - Agent 2)**: In a subsequent turn, Agent 2 conducts the Gate 3 Code Audit (`git diff` scrutiny, side-effects, localization compliance, invariant check), posts the audit report as a Jira comment, and transitions to `Freigabe (Human)`.
    *   **SWE.4 (Unit Verification)**: Verify internal logic of the specific module (e.g., `NumericalEncodingUtilsTest`).
    *   **SWE.5 (Integration Verification)**: Verify that the interface between two modules remains stable (e.g., `TrackerService` correctly consumes `BANALService` data).
    *   Perform a verification (build, static analysis, or logic check).
    *   Refer to `docs/tests.md` to execute the agreed-upon tests.
    *   **Mandatory Adversarial Self-Review ("Red Team" Pass)**: Before committing or presenting a walkthrough, the agent MUST review the complete `git diff` with a critical "Senior Auditor" persona, asking: *"What adjacent features, edge cases, state flows, or caller assumptions could this change inadvertently break?"*
    *   **MANDATORY HARD STOP 3 (Implementation Approval Gate)**: The user reviews the walkthrough and Gate 3 code audit in `Freigabe (Human)`:
        *   *Approval*: User moves sub-task to `Erledigt` (via *"Freigabe erteilt"*).
        *   *Reject / Revise*: User moves sub-task back to `In Bearbeitung` (via *"Nochmals von Vorne"*) with change requests.

8.  **Final Documentation & Release (SWE.6)**:
    *   **Pass/Fail Recording**: Document verification evidence in Jira using the following format:
        > **Verification Result: PASS**
        > * **Test ID**: TST-UNT-001
        > * **Scope**: SWE.4 Unit Verification
        > * **Artifact**: [Link to log/screenshot]
    *   Update the `Status` in `docs/requirements.md` to `Verified`.
    *   **Walkthrough Artifact**: Create a summary of the fulfilled requirements at `docs/engineering/walkthroughs/ATT-XXX_walkthrough.md`.
    *   **Git Commit Message**: Provide a clear, comprehensive commit message covering all changes for the **entire ticket**, following the Conventional Commits standard. The agent SHALL use the `*` symbol for bullet points within the commit body (avoiding dots or dashes). The commit message **MUST** be presented inside a literal markdown code block to ensure formatting characters are preserved for copy-paste compatibility.

9.  **Post-Implementation Review**:
    *   **MANDATORY FINAL STEP**: Before concluding the task, the agent MUST review the newly implemented logic against the requirements and tests defined in Steps 1 and 2.
    *   **Technical Debt Discovery**: If the implementation revealed new constraints, legacy "code smells", or architectural weaknesses outside the current scope, the agent **MUST** document these as new Requirement entries in `docs/requirements.md` with status `Backlog`.
    *   **Sync Discovery**: Update `docs/requirements.md` and `docs/tests.md` to reflect the *actual* final state of the implemented feature.
    *   **Truth Verification**: Ensure the documentation remains a "Single Source of Truth" that accurately describes the code as it exists after implementation.

10. **Localization Compliance (Mandatory Standard)**:
    *   Whenever a new user-facing string is introduced, the agent MUST translate it to ALL supported languages (EN, DE, ES, FR, IT, PT, NL, PL, JA) before the task is considered complete.
    *   All translations MUST be externalized in the respective `strings.xml` files.
    *   This is a non-negotiable quality standard for a world-class application.

11. **UI Visual Standards (Mandatory Design Rules)**:
    *   **Original Sport Icons**: Sport type icons MUST always be displayed in their original colors to ensure quick identification and maintain branding. Agents are FORBIDDEN from applying theme-based tinting (e.g., `primary` color) to these icons, except when they are explicitly in a muted background state or disabled.

## Internal Documentation Standards

To maintain world-class architectural clarity, every code component MUST be self-documenting:

1.  **Class-Level Headers**: Every class MUST have a KDoc (Kotlin) or JavaDoc (Java) block describing its **purpose**, its **architectural role**, and any critical threading or lifecycle constraints.
2.  **Method-Level Headers**: Every public and protected method MUST have a header describing:
    *   **Functional Description**: What the method does from a system perspective.
    *   **Implementation Logic**: Briefly explain *how* it achieves its goal if the logic is non-trivial (e.g., synchronization patterns, background offloading).
    *   **Parameters & Returns**: Explicitly document inputs and outputs.
3.  **Future Enforcement**: These standards apply to ALL new code and any significant refactoring of existing modules.

## Format String Hardening (Crash Prevention)

To prevent `UnknownFormatConversionException` runtime crashes, all developers and AI agents MUST adhere to these strict syntax rules for string resources:

1.  **Fully-Qualified Positional Specifiers**: All string placeholders MUST use positional indices and type characters.
    *   **Correct**: `%1$s`, `%2$d`, `%3$.2f`.
    *   **Prohibited**: `%s`, `%d`, `%1`, `%2`.
2.  **Type Suffix Requirement**: Placeholders MUST explicitly include the data type suffix (e.g., `$s` for String, `$d` for Decimal).
3.  **Literal Percent Signs**: Literal `%` characters in a format string MUST be escaped as `%%`. For standalone usage, prefer referencing the `@string/units_percent` resource.
4.  **Mandatory Static Audit**: Every task involving string modification MUST conclude with a static audit phase. The agent SHALL use `grep` to verify that zero instances of invalid positional specifiers (e.g., `%[0-9]` without `$`) exist across all affected locales.

## Unit Test Framework Integrity & Mocking Rules

To guarantee test reliability, prevent state pollution, and avoid subtle cross-suite test failures:

1.  **Strict Prohibition on `returnDefaultValues`**:
    *   `testOptions { unitTests.returnDefaultValues = true }` is STRICTLY FORBIDDEN in `app/build.gradle`.
    *   *Rationale*: Returning default values silently stubs Android framework static/native methods (such as `Location.distanceBetween` silently returning `0.0`), masking genuine calculation errors and breaking downstream modules (e.g., workout clustering, displacement markers).
2.  **Framework Mocking & State Isolation**:
    *   When mocking Android framework objects (such as `ContentValues` or `Cursor`), ensure each invocation receives distinct, isolated object state (e.g., via real instances or reflection helpers on `originalCall`) to prevent shared-reference collisions in `verify` or capture blocks.
3.  **Clean-Room Full-Suite Standard**:
    *   Every gate approval requires zero regressions: all project unit tests (`./gradlew testDebugUnitTest`) must pass green before Stage 3 completion.

## Three-Gate AI Review Protocol (Pre-Implementation, Planning & Post-Implementation Quality Gates)

To prevent side-effect regressions, "destroyed features", and architectural drift, all development workflows MUST pass through three explicit, AI-driven quality gates embedded in the `Zu erledigen -> In Bearbeitung -> In Überprüfung -> Freigabe (Human) -> Erledigt` sub-task lifecycle:

### Gate 1: Pre-Implementation Risk & Impact Review (Auditor Review on `[RCA]` Sub-task)
*   **Applicability**: Bug tickets.
*   **Timing**: Executed immediately after Root Cause Analysis (RCA) is generated by Agent 1, **BEFORE** defining tests, plans, or modifying any source files.
*   **Workflow Integration**: Agent 1 transitions the sub-task to `In Überprüfung`. Agent 2 executes the Gate 1 review, posts the evaluation as an automated Jira comment, and transitions the sub-task to `Freigabe (Human)`.
*   **Required Auditor Checks**:
    1.  **Forensic Scrutiny**: Stress-test Agent 1's conclusions: Do they explain all occurrences? Are alternative causes ruled out?
    2.  **Call Site Audit**: Run `find_usages` or `grep` on all classes, methods, and resources slated for editing. List every caller.
    3.  **Requirement Mapping Audit**: Cross-reference all files to be edited with `docs/requirements.md` and explicitly list all mapped `REQ-XXX` IDs.
    4.  **System Invariant Checklist**: Explicitly state what existing system behavior, precision, schema, or API contracts MUST NOT change.
    5.  **Risk Rating & Recommendation**: Assign a risk level (`LOW`, `MEDIUM`, `HIGH`) with technical justification and issue an explicit recommendation (`RECOMMEND PASS`, `CHALLENGED`, or `REVISE`).
*   **Human Gate Decision**: The user reviews the sub-task in `Freigabe (Human)`. Code modification is strictly FORBIDDEN until the user moves the sub-task to `Erledigt` (approving progress) or returns it to `In Bearbeitung` (via transition *"Nochmals von Vorne"*).

### Gate 2: Architectural & Invariant Plan Review (Auditor Review on `[Plan]` Sub-task)
*   **Applicability**: Both Bug and Feature tickets.
*   **Timing**: Executed after the implementation plan is formulated at `docs/engineering/plans/ATT-XXX_plan.md` by Agent 1, **BEFORE** writing any production code.
*   **Workflow Integration**: Agent 1 transitions the sub-task to `In Überprüfung`. Agent 2 executes the Gate 2 review, posts the evaluation as an automated Jira comment, and transitions the sub-task to `Freigabe (Human)`.
*   **Required Auditor Checks**:
    1.  **Architectural Integrity**: Ensure component boundaries and layering rules (`SWE.2`) are respected.
    2.  **Invariant Verification**: Confirm that all system invariants, non-target metrics, and file schemas are explicitly protected.
    3.  **Verification Coverage**: Verify that every proposed change maps to an automated or manual test case in `docs/tests.md`.
    4.  **Recommendation**: Issue an explicit recommendation (`RECOMMEND PASS` or `RECOMMEND REVISION`).
*   **Human Gate Decision**: The user reviews the plan and audit in `Freigabe (Human)`. Writing production code is strictly FORBIDDEN until the user moves the sub-task to `Erledigt` (approving progress).

### Gate 3: Post-Implementation Code & Full-Suite Regression Review (Auditor Review on `[Impl]` Sub-task)
*   **Applicability**: Both Bug and Feature tickets.
*   **Timing**: Executed immediately after coding and unit verification (SWE.4/SWE.5) by Agent 1, strictly upon explicit user request (e.g., *"Please let Agent 2 review the implementation and tests"*).
*   **Workflow Integration**: Agent 1 sets the walkthrough as the sub-task Description, transitions to `In Überprüfung`, and yields control. When prompted by the user, Agent 2 executes the Gate 3 review, posts the evaluation as an automated Jira comment, and transitions the sub-task to `Freigabe (Human)`.
*   **Required Auditor Checks**:
    1.  **Diff Scrutiny**: Inspect the complete `git diff` against the approved plan. Confirm zero unapproved files or unintended modifications.
    2.  **Side-Effect Audit**: Verify that adjacent callers and non-target metrics/features were NOT altered or broken.
    3.  **Mandatory Full-Suite Regression Execution**: Execute `./gradlew testDebugUnitTest` across the entire project repository. Confirm 100% pass rate (0 failures, 0 regressions) across all modules.
    4.  **Quality & Compliance**: Verify that class/method headers comply with self-documenting standards and all user-facing strings are fully localized across all 9 supported languages.
    5.  **Recommendation**: Issue an explicit recommendation (`RECOMMEND PASS` or `RECOMMEND REVISION`) with itemized audit notes.
*   **Human Gate Decision**: The user reviews the Gate 3 audit report in `Freigabe (Human)` to approve the implementation (`Erledigt`) or request revisions (`In Bearbeitung`).

## New Version / Release Workflow
Whenever preparing for a new version:
1.  **File Audit**: The agent identifies all files modified since the last release.
2.  **Impact Analysis**: Mapping modified files back to Requirement IDs in `docs/requirements.md`.
3.  **Test Collection**: Identifying all manual or automated tests in `docs/tests.md` that cover the affected Requirements.
4.  **Co-Verification**: The agent and user execute the collected tests together to ensure no regressions were introduced.

## Living Documentation Principle
To maintain a high-fidelity "Digital Twin" of the codebase, the agent must:
*   **Continuous Updates**: Whenever a new logical rule or user constraint is discovered in the code, add it to `docs/requirements.md`.
*   **Final Session Audit**: Perform a rigorous final review of all documentation at the end of each task to ensure it matches the final implementation.
*   **Refine Architecture**: Whenever a deeper understanding of component interactions is gained, update `docs/architecture.md`.
*   **Maintain Traceability**: Ensure the "Implementation File(s)" column in the requirements list is always kept up to date as files move or logic shifts.

## How to use this in new sessions

At the start of any new session, the user should provide the following instruction:
> "Please read the `docs/project_protocol.md` and follow our TDD and requirement-based engineering approach for this task."
