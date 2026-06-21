# Project Protocol: Requirement-Based Engineering

## Vision & ASPICE Alignment
The goal of **aTrainingTracker** is to be an awesome, professional, and world-class application for tracking training activities. To achieve this, we follow a workflow inspired by **ASPICE (Automotive SPICE)** standards, emphasizing bidirectional traceability and architectural integrity. Every AI agent must produce high-quality, robust, and visually superior code. If instructions are unclear, the agent **must ask for clarification**.

## Mandatory Development Workflow (TDD-Based)

Any AI assistant working on this project **must** follow these steps for every task:

1.  **Requirement Synchronization**:
    *   Before writing any code or plans, read `docs/requirements.md`.
    *   Add a new Requirement ID (e.g., `REQ-XXX-###`) or update an existing one to reflect the user's request.
    *   Define the **Rationale** (the "Why") clearly.
    *   Map the requirement to the relevant **Implementation File(s)**.

2.  **Test Definition (The TDD Phase)**:
    *   **CRITICAL**: Before planning implementation, discuss and define the verification criteria with the user.
    *   Identify which manual or automated tests in `docs/tests.md` will prove the requirement is met.
    *   If no suitable test exists, add a new one to `docs/tests.md` immediately.
    *   Implementation may only start once the test criteria are agreed upon.

3.  **Jira Ticket Management (Agile Phase)**:
    *   **Automation**: Use the local utility `./tools/jira_util.py` for all Jira interactions.
    *   **Credentials**: Authentication details are stored in `.env.jira` (not tracked in Git).
    *   **Selection**: Identify the top-ranked ticket in the active Sprint from the "Zu erledigen" (To Do) status using `./tools/jira_util.py list`.
    *   **In Progress**: Transition the ticket to "In Bearbeitung" (`move KEY in_progress`). **CRITICAL**: Post the full text of the `implementation_plan.artifact.md` as a comment on the ticket. This ensures the design decisions are permanently recorded in Jira even after the transient artifact is deleted.
    *   **In Review**: Once implemented and verified (SWE.4/SWE.5), transition the ticket to "In Überprüfung" (`move KEY in_review`). Attach or comment with verification evidence.
    *   **Done**: Transition to "Erledigt" (`move KEY done`) only after user approval and final requirement verification.

4.  **Architectural Integrity (SWE.2 Phase)**:
    *   Identify which core components are affected (e.g., `BANALService`, `TrackerService`, `Repository`).
    *   Define or update the **Interfaces** and **Data Flow** between components.
    *   Ensure that new code does not violate the established architecture (e.g., maintain clear separation between background services and UI layers).

4.  **Implementation Planning (SWE.3 Phase)**:
    *   Create an `implementation_plan.artifact.md`.
    *   Every proposed change **must** explicitly reference the Requirement ID, the Component affected, and the corresponding Test ID it fulfills.

4.  **Execution & Verification**:
    *   Implement the changes as planned.
    *   Perform a verification (build, static analysis, or logic check).
    *   Refer to `docs/tests.md` to execute the agreed-upon tests.

5.  **Final Documentation & Release**:
    *   Update the `Status` in `docs/requirements.md` to `Verified`.
    *   Update the `walkthrough.artifact.md` with a summary of the fulfilled requirements.

## New Version / Release Workflow
Whenever preparing for a new version:
1.  **File Audit**: The agent identifies all files modified since the last release.
2.  **Impact Analysis**: Mapping modified files back to Requirement IDs in `docs/requirements.md`.
3.  **Test Collection**: Identifying all manual or automated tests in `docs/tests.md` that cover the affected Requirements.
4.  **Co-Verification**: The agent and user execute the collected tests together to ensure no regressions were introduced.

## Living Documentation Principle
To maintain a high-fidelity "Digital Twin" of the codebase, the agent must:
*   **Update Requirements**: Whenever a new logical rule or user constraint is discovered in the code, add it to `docs/requirements.md`.
*   **Refine Architecture**: Whenever a deeper understanding of component interactions is gained, update `docs/architecture.md`.
*   **Maintain Traceability**: Ensure the "Implementation File(s)" column in the requirements list is always kept up to date as files move or logic shifts.

## How to use this in new sessions

At the start of any new session, the user should provide the following instruction:
> "Please read the `docs/project_protocol.md` and follow our TDD and requirement-based engineering approach for this task."
