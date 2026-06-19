# Project Protocol: Requirement-Based Engineering

## Vision
The goal of **aTrainingTracker** is to be an awesome, professional, and world-class application for tracking training activities. To achieve this, every AI agent must produce high-quality, robust, and visually superior code and UI components. If instructions are unclear or ambiguous, the agent **must ask for clarification** before proceeding.

## Mandatory Development Workflow

Any AI assistant working on this project **must** follow these steps for every task:

1.  **Requirement Synchronization**:
    *   Before writing any code or plans, read `docs/requirements.md`.
    *   Add a new Requirement ID (e.g., `REQ-XXX-###`) or update an existing one to reflect the user's request.
    *   Define the **Rationale** (the "Why") clearly.

2.  **Implementation Planning**:
    *   Create an `implementation_plan.artifact.md` (as per standard AI workflow).
    *   Every proposed change **must** explicitly reference the Requirement ID it fulfills.

3.  **Execution & Verification**:
    *   Implement the changes as planned.
    *   Perform a verification (build, static analysis, or logic check).

4.  **Final Documentation**:
    *   Update the `Status` in `docs/requirements.md` to `Verified`.
    *   Update the `walkthrough.artifact.md` with a summary of the fulfilled requirements.

## How to use this in new sessions

At the start of any new session, the user should provide the following instruction:
> "Please read the `docs/project_protocol.md` and follow our requirement-based engineering approach for this task."

This ensures the AI assistant immediately adopts the correct mindset and uses the established tools.
