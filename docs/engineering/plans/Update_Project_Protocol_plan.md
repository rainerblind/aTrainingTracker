# Implementation Plan - Behavioral Guardrail: Strictly Directed Research

Update the `project_protocol.md` to explicitly forbid preemptive analysis, background research, or exploratory code searches for any tasks or tickets not explicitly assigned by the user.

## User Review Required

> [!IMPORTANT]
> - **Zero-Exploration Rule**: The agent will be strictly prohibited from researching, searching, or analyzing any Jira ticket or code area that hasn't been explicitly designated as the current task by the user.
> - **Focus Enforcement**: This prevents the "preemptive analysis" behavior where the agent starts investigating related but unassigned bugs (like ATT-412/413) while the user is still wrapping up a different task.

## Proposed Changes

### 1. Process Layer: Forbidden Preemptive Research
#### [MODIFY] [project_protocol.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/project_protocol.md)
- **Refine "Selection & Focus"**:
    - Add a strict clause: "The agent SHALL NOT perform any preemptive research, code searches, or logic analysis for tickets or tasks that have not been explicitly assigned by the user for the current focus. This includes examining Jira ticket details, downloading attachments, or searching for related symbols in the codebase."
    - Add: "Exploratory analysis of the 'next potential task' is strictly forbidden."

## Verification Plan

### Manual Verification
1. Review the updated `docs/project_protocol.md`.
2. Confirm the language is sufficiently strict to prevent "never ever happen" scenarios for unauthorized background research.
