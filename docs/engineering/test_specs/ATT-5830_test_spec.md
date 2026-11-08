# Stage 2: Requirement & Test Specification - ATT-5830: Höchstmaß Skill Set (tools/jira_util.py): Support Jira Data Center (PAT Bearer auth, username assignment, Epic Link custom field & API v2 search)

**Ticket**: [ATT-5830](https://rainerblind.atlassian.net/browse/ATT-5830)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5830`  
**Requirement Mapping**: `REQ-PRO-039` (`REQ-PRO-039.1`–`REQ-PRO-039.6`) | **Test Spec ID**: `TST-PRO-039` (`TST-PRO-039.1`–`TST-PRO-039.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-PRO-036` (*Parameterized Issue Tracker Automation & Governance Tooling*) and extends `REQ-PRO-019` (*Multi-Account Authentication*). Targets `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, and `tools/hoechstmass/hoechstmass_config.json`.
* `Historical Origin & Commit Trace`: Multi-account auth introduced in `ATT-4520`; Höchstmaß tool extraction and configuration parameterization introduced in `ATT-5667`.
* `Root Reason for Existing Formulation`: Tooling initially assumed Jira Cloud REST API semantics (`accountId`, Basic Auth base64, `/rest/api/3/search/jql`, and `fields.parent` for epics).
* `Preservation of Core Invariants`: 100% backward compatibility for existing Jira Cloud setups (`deployment_type: "cloud"`); 8 Core In-Sprint Governance Rules, secret masking (`REQ-PRO-021`), and human review gates remain strictly unchanged; zero runtime changes to `app/src/main/`.

---

## 1. Requirement Specification (`REQ-PRO-039`)

### 1.1 Problem Statement & Rationale
In enterprise and automotive ASPICE environments, Jira is predominantly deployed on-premise as Jira Data Center. The existing automation scripts assume Jira Cloud conventions, failing on PAT bearer authentication, username assignment, Epic Link custom fields, and API v3 JQL search endpoints.

### 1.2 Functional & Architectural Requirements
1. **Deployment Mode Configuration (`REQ-PRO-039.1`)**: Support declarative `deployment_type: "datacenter" | "cloud"` in `hoechstmass_config.json` with `JIRA_DEPLOYMENT` environment override, defaulting to `"cloud"`.
2. **Bearer PAT & CA Bundle Authentication (`REQ-PRO-039.2`)**: In Data Center mode, support Personal Access Token authentication via `Authorization: Bearer <token>` when `JIRA_API_TOKEN` is provided without `JIRA_USER`. Support corporate CA bundle via `JIRA_CA_BUNDLE` and configurable `ssl_verify`.
3. **Username Assignment Dispatch (`REQ-PRO-039.3`)**: In Data Center mode, issue/subtask assignment SHALL emit `{"name": "<username>"}` (instead of `accountId`), and parse `fields.assignee.name`.
4. **Epic Link Custom Field Dispatch (`REQ-PRO-039.4`)**: In Data Center mode, linking top-level issues (Stories, Tasks, Bugs) to Epics SHALL populate the configured Epic Link custom field (default `customfield_10014`), while sub-tasks strictly retain `fields.parent` across both modes.
5. **JQL Search API v2 Routing (`REQ-PRO-039.5`)**: In Data Center mode, JQL searches SHALL query `/rest/api/2/search` instead of Cloud `/rest/api/3/search/jql`.
6. **Tooling Synchronization & Parity (`REQ-PRO-039.6`)**: `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL maintain identical logic and helper implementations (`Rule 36`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Bearer PAT Header)**: *Given* `deployment_type: "datacenter"` and `JIRA_API_TOKEN`, *When* an HTTP request is made, *Then* the header contains `Authorization: Bearer <token>`.
* **AC-2 (CA Bundle Verification)**: *Given* `JIRA_CA_BUNDLE` path, *When* requests are sent, *Then* the SSL context verifies against the corporate CA bundle.
* **AC-3 (Username Assignment)**: *Given* Data Center mode, *When* assigning an issue or subtask, *Then* the payload sets `{"name": "<username>"}`.
* **AC-4 (Epic Link Custom Field vs Parent)**: *Given* Data Center mode, *When* linking a Story to an Epic, *Then* `customfield_10014` is set and `fields.parent` is omitted; *When* linking a subtask, *Then* `fields.parent` is used.
* **AC-5 (API v2 Search Routing)**: *Given* Data Center mode, *When* executing `search_issues`, *Then* the endpoint called is `/rest/api/2/search`.
* **AC-6 (Cloud Compatibility)**: *Given* default `deployment_type: "cloud"`, *When* executing commands, *Then* existing Cloud auth, accountId assignment, and v3 search endpoints operate without regression.

---

## 2. Test Specification (`TST-PRO-039`) & Traceability Matrix

| Test Case | Scope | Target Class / File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-039.1` | Config & Environment Parser Contract | `test_jira_datacenter.py` | `REQ-PRO-039.1` | Specified |
| `TST-PRO-039.2` | Bearer PAT & CA Bundle Verification | `test_jira_datacenter.py` | `REQ-PRO-039.2` | Specified |
| `TST-PRO-039.3` | Username vs AccountId Assignment Contract | `test_jira_datacenter.py` | `REQ-PRO-039.3` | Specified |
| `TST-PRO-039.4` | Epic Link Custom Field vs Parent Dispatch | `test_jira_datacenter.py` | `REQ-PRO-039.4` | Specified |
| `TST-PRO-039.5` | JQL Search API v2 Endpoint Dispatch | `test_jira_datacenter.py` | `REQ-PRO-039.5` | Specified |
| `TST-PRO-039.6` | Tooling Synchronization & Parity Regression | `verify_requirement_governance.py`, `test_hoechstmass_tools.py` | `REQ-PRO-039.6` | Specified |
