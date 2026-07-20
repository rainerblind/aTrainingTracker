# Walkthrough: Workflow Improvements (ATT-270, ATT-271)

## Fulfilling REQ-PRO-008 & REQ-PRO-009: Type-Aware Jira Management

The Jira interaction script and the project protocol were updated to provide better visibility into issue types and automated access to debugging artifacts.

### Implemented Changes

#### 1. `tools/jira_util.py`
- **Type Visibility**: Updated `list`, `show`, and `search` commands to fetch and display the `issuetype` field. This allows agents to immediately distinguish between Bugs, Stories, and Tasks.
- **Batch Download**: Added the `download-all KEY` command. This command fetches all attachments associated with an issue and downloads them to `docs/attachments/`.

#### 2. `docs/project_protocol.md`
- **Attachment Mandate**: Added a rule to Section 4 requiring agents to download and inspect all attachments for **Bugs** before performing Root Cause Analysis.
- **Type-Aware Engineering**: Formalized the requirement for agents to adapt their strategy based on the issue type (e.g., formal RCA for Bugs vs. feature analysis for Stories).

### Verification Evidence
- **TST-PRO-001 (Pass)**: Successfully batch-downloaded attachments using the new command.
- **TST-PRO-002 (Pass)**: Verified that `jira_util.py list` now shows types like `[Bug]` or `[Task]` next to each ticket.

## Final Status: Verified
The development workflow is now more robust and type-aware.
