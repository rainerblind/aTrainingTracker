# Stage 3: Implementation Plan - ATT-5830: Höchstmaß Skill Set (tools/jira_util.py): Support Jira Data Center (PAT Bearer auth, username assignment, Epic Link custom field & API v2 search)

**Ticket**: [ATT-5830](https://rainerblind.atlassian.net/browse/ATT-5830)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5830`  
**Requirement Mapping**: `REQ-PRO-039` (`REQ-PRO-039.1`–`REQ-PRO-039.6`)  
**Test Mapping**: `TST-PRO-039` (`TST-PRO-039.1`–`TST-PRO-039.6`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Scope
Jira Data Center is standard in enterprise and automotive ASPICE environments. Existing automation tooling (`tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`) fails against Data Center due to 4 protocol differences: PAT Bearer authentication/TLS, username assignment (`{"name": "..."}`), Epic Link custom field (`customfield_10014`), and API v2 JQL search routing.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-039` (*Jira Data Center & Enterprise Support*)
  * `REQ-PRO-039.1`: Declarative `deployment_type: "datacenter" | "cloud"` in `hoechstmass_config.json` and `JIRA_DEPLOYMENT` env var.
  * `REQ-PRO-039.2`: Bearer PAT (`Authorization: Bearer <PAT>`) and corporate TLS bundle (`JIRA_CA_BUNDLE`, `ssl_verify`).
  * `REQ-PRO-039.3`: Assignee dispatch (`{"name": "<username>"}` vs `{"accountId": "..."}`) and `assignee.name` parsing.
  * `REQ-PRO-039.4`: Epic Link custom field (`customfield_10014`) for top-level issues; preserve `fields.parent` for sub-tasks.
  * `REQ-PRO-039.5`: Route JQL searches to `/rest/api/2/search` in Data Center mode.
  * `REQ-PRO-039.6`: Tooling synchronization between `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` (`Rule 36`).
* **Test Mapping**: `TST-PRO-039` (`TST-PRO-039.1`–`TST-PRO-039.6` in `tools/test_jira_datacenter.py` and `test_hoechstmass_tools.py`).

---

## 3. System Invariants & Preserved Behavior
1. **Cloud Backward Compatibility**: Default `deployment_type: "cloud"` retains 100% identical behavior across all existing commands.
2. **Subtask Hierarchy Invariance**: Subtasks (`[Analysis]`, `[Test Spec]`, etc.) continue using `fields.parent` in both Cloud and Data Center.
3. **Secret Masking Invariance**: PAT tokens and Basic Auth credentials are masked in logs/exceptions (`REQ-PRO-021`).
4. **App Isolation**: Zero modifications to Android production code in `app/src/main/`.

---

## 4. Proposed Architectural Changes
* **`hoechstmass_config.json` & `.env.jira.example`**: Add `deployment_type`, `custom_fields.epic_link`, `JIRA_CA_BUNDLE`, and `JIRA_AUTH_TYPE`.
* **`jira_util.py` & `tools/hoechstmass/tools/jira_util.py`**:
  - `is_datacenter()` and `get_epic_link_field()` helpers.
  - `get_headers()`: Emit `Authorization: Bearer <PAT>` when in Data Center / Bearer mode.
  - `jira_request()`: Build custom `ssl.SSLContext` from `cafile=JIRA_CA_BUNDLE` or disable verification if `ssl_verify=False`.
  - `assign_issue()`: Emit `{"name": "<username>"}` in Data Center mode.
  - `create_issue()` / `set_parent()`: Populate `epic_link` custom field for Epics in Data Center; keep `fields.parent` for sub-tasks.
  - `search_issues()`: Query `/rest/api/2/search` in Data Center mode.
* **Documentation**: Update `tools/hoechstmass/README.md` and `jira-workflow/SKILL.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Config & Schema**: Update `hoechstmass_config.json` and `.env.jira.example` with Data Center parameters (`REQ-PRO-039.1`).
2. **Auth & TLS Layer**: Implement Bearer PAT headers and CA bundle SSL context in `jira_util.py` and `tools/hoechstmass/tools/jira_util.py` (`REQ-PRO-039.2`).
3. **Assignee & Epic Link Dispatch**: Implement username assignment and Epic Link custom field dispatch (`REQ-PRO-039.3`, `REQ-PRO-039.4`).
4. **Search Routing & Tool Parity**: Implement API v2 search routing in both scripts (`REQ-PRO-039.5`, `REQ-PRO-039.6`).
5. **Documentation & Tests**: Update README and SKILL.md; author `tools/test_jira_datacenter.py` and run tests (`TST-PRO-039.1..6`).

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 tools/test_jira_datacenter.py`, `python3 tools/hoechstmass/tools/test_hoechstmass_tools.py`, `python3 tools/test_jira_accounts.py`, and `python3 tools/verify_requirement_governance.py`.
* **Rollback Plan**: Atomic commit on `feature/ATT-5830`; revert via `git reset --hard` if regression occurs.
