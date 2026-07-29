# Implementation Plan - ATT-324: Implementation plan as Jira sub-ticket

Formalize the process of creating a dedicated Jira sub-task for every Implementation Plan to improve traceability and board visibility.

## User Review Required

> [!IMPORTANT]
> This change modifies the mandatory development workflow defined in `project_protocol.md`. Every future task will now require the creation of an "Implementation Plan" sub-task in Jira, in addition to the existing test sub-tasks.

## Proposed Changes

### Process & Documentation

#### [MODIFY] [project_protocol.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/project_protocol.md)
- Update Section 4 (Jira Ticket Management) to include the requirement for an Implementation Plan sub-task.
- Update Section 6 (Implementation Planning) to mandate the creation of a sub-task instead of just a comment.
- Define the naming convention: `[Plan] SCRUM-XXX: Summary`.

#### [MODIFY] [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)
- Add **REQ-PRO-010**: "The development workflow SHALL include the creation of a dedicated Jira sub-task for every Implementation Plan to ensure high-visibility traceability."

#### [MODIFY] [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
- Update **TST-STR-012** (Sub-Task Automation Verification) or add a new test case to verify both test sub-tasks and plan sub-tasks.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **Process Audit**: Verify that for this ticket (ATT-324), a sub-task for the implementation plan is created in Jira.
- **Protocol Review**: Ensure the updated `project_protocol.md` is clear and unambiguous.
