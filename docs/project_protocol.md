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

3.  **Impact Analysis (SWE.1.BP.5 Phase)**:
    *   Before implementation, perform a formal audit of existing code.
    *   Identify potential side effects on:
        *   **Android System**: Battery usage, WakeLock durations, Background execution rules.
        *   **Component Interfaces**: Will a change in `BANALService` break the `MutableStateFlow` used by the UI?
        *   **Data Integrity**: Will a schema change affect backward compatibility of existing workout files?
    *   Document these risks in the `implementation_plan.artifact.md`.

4.  **Jira Ticket Management (Agile Phase)**:
    *   **Automation**: Use the local utility `./tools/jira_util.py` for Jira interactions (list, comment).
    *   **Credentials**: Authentication details are stored in `.env.jira` (not tracked in Git).
    *   **State Control**: The agent **MUST NOT** transition tickets between states (e.g., move to "In Progress" or "Done") unless explicitly instructed by the user. The user maintains sole control over the workflow state.
    *   **Selection & Focus**: Multiple tickets may be "In Bearbeitung" (In Progress). The agent works on one chosen ticket at a time. While working on a ticket, it becomes the exclusive focus of the development session.
    *   **Documentation**: For any ticket in progress, the agent must:
        *   Post the full text of the `implementation_plan.artifact.md` as a comment on the ticket.
        *   Post verification evidence (logs, summaries) as a comment once implementation is complete.

5.  **Architectural Integrity (SWE.2 Phase)**:
    *   Identify which core components are affected (e.g., `BANALService`, `TrackerService`, `Repository`).
    *   Define or update the **Interfaces** and **Data Flow** between components in `docs/architecture.md`.
    *   Ensure that new code does not violate the established architecture (e.g., maintain clear separation between background services and UI layers).

6.  **Implementation Planning (SWE.3 Phase)**:
    *   Create an `implementation_plan.artifact.md`.
    *   Every proposed change **must** explicitly reference the Requirement ID, the Component affected, and the corresponding Test ID it fulfills.

7.  **Execution & Multi-Stage Verification**:
    *   **SWE.4 (Unit Verification)**: Verify internal logic of the specific module (e.g., `NumericalEncodingUtilsTest`).
    *   **SWE.5 (Integration Verification)**: Verify that the interface between two modules remains stable (e.g., `TrackerService` correctly consumes `BANALService` data).
    *   Perform a verification (build, static analysis, or logic check).
    *   Refer to `docs/tests.md` to execute the agreed-upon tests.

8.  **Final Documentation & Release (SWE.6)**:
    *   **Pass/Fail Recording**: Document verification evidence in Jira using the following format:
        > **Verification Result: PASS**
        > - **Test ID**: TST-UNT-001
        > - **Scope**: SWE.4 Unit Verification
        > - **Artifact**: [Link to log/screenshot]
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
