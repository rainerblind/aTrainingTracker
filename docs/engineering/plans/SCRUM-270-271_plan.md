# Implementation Plan: Workflow Improvements (ATT-270, ATT-271)

## 1. Requirement Traceability

| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-PRO-008** | Automated downloading of all attachments from a Jira ticket. | `jira_util.py` | `TST-PRO-001` |
| **REQ-PRO-009** | Visibility into Jira issue types (Bug, Task, Story). | `jira_util.py` | `TST-PRO-002` |

## 2. Proposed Changes

### `tools/jira_util.py`
- **Ticket Type Awareness**:
    - Update `list_sprint_issues` to fetch and display the `issuetype` field.
    - Update `show_issue` to fetch and display the `issuetype` field prominently.
    - Update `search_issues` to include `issuetype` in the results.
- **Attachment Management**:
    - Implement `download_all_attachments(issue_key)` function.
    - This function will:
        1. Fetch the issue details (specifically the `attachment` field).
        2. Iterate through all attachments.
        3. Call `download_attachment(url, filename)` for each one.
    - Add `download-all KEY` command to the CLI argument parser.

### `docs/project_protocol.md`
- Update Section 4 (**Jira Ticket Management**) to:
    - Mention the new `download-all` command.
    - Specify that agents SHOULD download and inspect all attachments (e.g., log files, screenshots) for Bugs before performing Root Cause Analysis.
    - Emphasize checking the `issuetype` to adapt the engineering approach (e.g., Bugs require RCA, Stories require detailed feature analysis).

## 3. Impact Analysis
- **Workflow**: Faster access to debugging artifacts from Jira.
- **Protocol**: Stronger alignment with ASPICE standards for bug analysis.
- **Script**: No breaking changes to existing commands.

## 4. Verification Plan
- **TST-PRO-001**: Run `jira_util.py download-all ATT-270` (or another ticket with test attachments) and verify local files.
- **TST-PRO-002**: Run `jira_util.py list` and verify that types like [Bug] or [Task] appear next to titles.
