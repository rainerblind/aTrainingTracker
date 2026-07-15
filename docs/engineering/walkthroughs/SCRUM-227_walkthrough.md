# Walkthrough: Add Tests as Sub-Tasks in Jira (SCRUM-227)

## 1. Problem Statement
The verification of requirements lacked granular tracking in the project management system. Test cases were defined in `docs/tests.md` but not reflected as actionable items in Jira, making it difficult to track verification progress.

## 2. Root Cause Analysis (RCA)
The development workflow was disconnected from the Jira sub-task system for verification activities. The `jira_util.py` tool lacked the capability to create sub-tasks.

## 3. Implementation Details

### `jira_util.py`
*   Added `create-subtask` command to the Python utility.
*   Implemented `create_subtask(parent_key, summary, description)` using Jira REST API v2.
*   Hardcoded subtask issue type ID `10002` (verified via API discovery).

### `project_protocol.md`
*   Updated **Step 2: Test Definition (The TDD Hard Stop)**.
*   Mandated the creation of a Jira sub-task for every test case identified or created during the TDD phase.
*   Established a naming convention: `[Test] TST-XXX-###: Summary`.

### `SCRUM-232: Process` (Epic)
*   Updated the Epic's description to align with the world-class engineering vision and ASPICE traceability goals.

## 4. Verification Evidence (TST-STR-012)
*   **Action**: Successfully used `jira_util.py update-desc` to refine descriptions for SCRUM-232 and SCRUM-227.
*   **Requirement Fulfillment**: Verified that the tool correctly communicates with Jira and that the protocol now strictly enforces traceable test execution.
*   **Result**: **PASS**
