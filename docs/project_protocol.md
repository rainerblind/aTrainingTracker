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
    *   Define the **Rationale** (the "Why") clearly.
    *   Map the requirement to the relevant **Implementation File(s)**.

2.  **Test Definition (The TDD Hard Stop)**:
    *   **MANDATORY HARD STOP**: After requirement synchronization, the agent MUST define the verification criteria with the user.
    *   Identify which manual or automated tests in `docs/tests.md` will prove the requirement is met.
    *   If no suitable test exists, add a new one to `docs/tests.md` immediately.
    *   **Jira Integration**: For each identified or new test case, the agent MUST create a **Sub-task** in Jira linked to the main ticket.
        *   The sub-task **Summary** MUST follow the format: `[Test] TST-XXX-###: Summary`.
        *   The sub-task **Description** MUST be identical to the procedure and expected result defined in `docs/tests.md` to ensure absolute synchronization.
        *   After creation, the agent MUST update `docs/tests.md` to include the **Jira Ticket ID** of the sub-task (e.g., `ATT-123`) in the test case table for bidirectional traceability.
    *   **Iterative Refinement**: The agent must refine the test cases based on user feedback until the user explicitly agrees.
    *   **Enforcement**: The agent is strictly FORBIDDEN from proposing an implementation plan or writing any code until the user has formally agreed to the test cases in `docs/tests.md` and the corresponding sub-tasks have been created in Jira. This phase is used to clarify and freeze the requirements.

3.  **Impact Analysis (SWE.1.BP.5 Phase)**:
    *   Before implementation, perform a formal audit of existing code.
    *   Identify potential side effects on:
        *   **Android System**: Battery usage, WakeLock durations, Background execution rules.
        *   **Component Interfaces**: Will a change in `BANALService` break the `MutableStateFlow` used by the UI?
        *   **Data Integrity**: Will a schema change affect backward compatibility of existing workout files?
    *   Document these risks in the implementation plan.

4.  **Jira Ticket Management (Agile Phase)**:
    *   **Automation**: Use the local utility `./tools/jira_util.py` for Jira interactions (list, show, comment, download, download-all).
    *   **Syntax**: All Jira comments must use **Jira Wiki Markup** (e.g., `h1.`, `{code}`, `*bold*`).
    *   **Credentials**: Authentication details are stored in `.env.jira` (not tracked in Git).
    *   **State Control**: The agent **MUST NOT** transition tickets between states (e.g., move to "In Progress" or "Done") unless explicitly instructed by the user. The user maintains sole control over the workflow state.
    *   **Selection & Focus**: Multiple tickets may be "In Bearbeitung" (In Progress). The agent works on one chosen ticket at a time. While working on a ticket, it becomes the exclusive focus of the development session. The agent MUST fully complete the current topic (including documentation and verification) before concluding. The agent is strictly FORBIDDEN from asking to start a new ticket or suggesting the next task; the user holds sole initiative for task transitions. The agent SHALL NOT perform any preemptive research, code searches, or logic analysis for unrelated tickets or tasks that have not been explicitly assigned. However, to maintain **Contextual Awareness**, the agent SHOULD research other tickets and documentation within the active Epic to gain a holistic understanding of the feature area and ensure technical alignment. Exploratory analysis of unrelated 'next potential tasks' remains strictly forbidden.
    *   **Contextual Awareness**: Before starting work on a ticket, the agent MUST examine its **Epic** (if linked) to understand the overall picture and vision. The agent SHOULD ask clarifying questions about the Epic to ensure the current task aligns with the long-term goals. If the Epic's description is missing or vague, the agent SHOULD propose an updated description to the user. Once the overall idea of the Epic becomes clear, the agent MUST update the Epic's description in Jira using the `update-desc` command.
    *   **Clarification & Completeness**: If a ticket selected for work lacks a **Description**, specific failure logs, or clear technical context, the agent **MUST NOT** proceed with an implementation plan. Instead, the agent must ask the user for clarification and agreement on the problem statement first.
    *   **Type-Aware Engineering**: The agent MUST check the **Issue Type** (e.g., Bug, Task, Story) and adapt its strategy accordingly:
        *   **Bugs**: Require a formal **Root Cause Analysis (RCA)** and inspection of all attachments (logs, screenshots). Use `jira_util.py download-all KEY` to retrieve debugging artifacts. The agent MUST create a dedicated sub-task for the RCA (summary format: `[RCA] ATT-XXX: Summary`) and document the detailed technical results in its **Description** before proceeding to implementation.
        *   **Stories/Tasks**: Require detailed feature requirements and architectural impact analysis.
    *   **Documentation**: For any ticket in progress, the agent must:
        *   **Identity Disclaimer**: Every comment posted by the agent MUST start with a clear disclaimer: *"[Automated comment by AI Agent]"*.
        *   **Initial Analysis**: Immediately after moving to "In Progress", post a comment containing the **Implementation Strategy**, the **Impact Analysis**, and the **Agreed Verification Criteria (Test IDs)**. Reference the dedicated `[RCA]` (for bugs) and `[Plan]` sub-tasks for technical details. This ensures that the main ticket remains a high-level coordination hub while technical specifics are isolated in traceable sub-tasks.
        *   **Jira Sub-task Generation**: Immediately after the Initial Analysis, the agent MUST create a **Sub-task** for the implementation plan. 
            *   The sub-task **Summary** MUST follow the format: `[Plan] ATT-XXX: Summary`.
            *   The sub-task **Description** MUST be identical to the Implementation Plan defined in Step 6.
        *   **Verification & Closure**: When moving to "In Überprüfung", post the full text of the walkthrough as a comment. This provides a permanent record of the implemented changes and verification evidence.

5.  **Architectural Integrity (SWE.2 Phase)**:
    *   Identify which core components are affected (e.g., `BANALService`, `TrackerService`, `Repository`).
    *   Define or update the **Interfaces** and **Data Flow** between components in `docs/architecture.md`.
    *   Ensure that new code does not violate the established architecture (e.g., maintain clear separation between background services and UI layers).

6.  **Implementation Planning (SWE.3 Phase - The Implementation Hard Stop)**:
    *   **Plan Artifact**: Create an implementation plan at `docs/engineering/plans/ATT-XXX_plan.md` (where XXX is the ticket number).
    *   Every proposed change **must** explicitly reference the Requirement ID, the Component affected, and the corresponding Test ID it fulfills.
    *   **Jira Integration**: The agent MUST create a **Sub-task** for the implementation plan with the summary `[Plan] ATT-XXX: Summary`. The full text of the plan MUST be the sub-task's description.
    *   **MANDATORY HARD STOP**: The agent MUST present the full implementation plan to the user and ask for formal approval.
    *   **Iterative Refinement**: If the user provides feedback or asks for changes to the plan, the agent **MUST** update the plan and ask for approval again.
    *   **Jira Synchronization**: Upon presentation of the plan to the user, the agent MUST update the `[Plan]` sub-task's description with the final plan.
    *   **Enforcement**: The agent is strictly FORBIDDEN from performing any code modifications (writing files or replacing content) until the user has explicitly responded with "Implementation Plan approved" or a similar clear confirmation of the *entire* plan.

7.  **Execution & Multi-Stage Verification**:
    *   **SWE.4 (Unit Verification)**: Verify internal logic of the specific module (e.g., `NumericalEncodingUtilsTest`).
    *   **SWE.5 (Integration Verification)**: Verify that the interface between two modules remains stable (e.g., `TrackerService` correctly consumes `BANALService` data).
    *   Perform a verification (build, static analysis, or logic check).
    *   Refer to `docs/tests.md` to execute the agreed-upon tests.

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
