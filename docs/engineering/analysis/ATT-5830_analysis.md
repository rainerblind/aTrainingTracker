# Stage 1 Analysis: ATT-5830 - Höchstmaß Skill Set (tools/jira_util.py): Support Jira Data Center (PAT Bearer auth, username assignment, Epic Link custom field & API v2 search)

**Ticket**: [ATT-5830](https://rainerblind.atlassian.net/browse/ATT-5830)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5830`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In enterprise and automotive environments—the primary domain for ASPICE—Jira is overwhelmingly deployed on-premise as **Jira Data Center** (or Jira Server) rather than Jira Cloud.
The current Höchstmaß automation tooling (`tools/hoechstmass/tools/jira_util.py` and `tools/jira_util.py`) assumes Jira Cloud REST API conventions:
1. **Auth & TLS**: Cloud uses `Basic base64(user:token)`; Data Center uses Personal Access Tokens (`Authorization: Bearer <PAT>`), internal context paths (`/jira`), and corporate CAs (`JIRA_CA_BUNDLE`).
2. **Assignee**: Cloud requires `{"accountId": "..."}`; Data Center strictly requires `{"name": "<username>"}` and returns `fields.assignee.name`.
3. **Epic Linking**: Cloud links top-level issues via `fields.parent = {"key": "..."}`; Data Center rejects `parent` for non-subtasks (HTTP 400), requiring the `Epic Link` custom field (`customfield_10014`) or Agile API.
4. **JQL Search**: Cloud queries `/rest/api/3/search/jql`; Data Center exclusively supports `/rest/api/2/search`.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Architectural Gap Analysis**:
  - `tools/jira_util.py` (lines 144, 63, 1128, 1374) hardcodes Basic Auth, `accountId`, `/rest/api/3/search/jql`, and `fields.parent` for Epics.
  - `tools/hoechstmass/tools/jira_util.py` lacks Data Center deployment switching (`deployment_type: "datacenter" | "cloud"`), Bearer PAT headers, custom CA contexts, and Epic Link resolution.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Audited `tools/hoechstmass/hoechstmass_config.json` and `.env.jira.example`: No declarative keys exist for `deployment_type`, `epic_link`, or TLS bundles.
  - Confirmed sub-tasks in both Cloud and Data Center legitimately use `fields.parent`; only top-level issues require `Epic Link` custom field in Data Center.
  - Confirmed `/rest/api/2/search` is supported by both Cloud and Data Center, enabling a unified search path or conditional dispatch.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  - Add `deployment_type: "datacenter" | "cloud"` support to `hoechstmass_config.json` and `JIRA_DEPLOYMENT` in `.env.jira`.
  - Implement Bearer PAT authentication, corporate CA bundle (`JIRA_CA_BUNDLE`), and SSL verification toggles.
  - Support username assignment (`{"name": "<username>"}`) and `assignee.name` parsing when in Data Center mode.
  - Support `epic_link` custom field for top-level issues in Data Center while preserving `parent` for sub-tasks.
  - Route JQL searches to `/rest/api/2/search` in Data Center mode.
  - Keep `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` synchronized (`Rule 36`).
  - Update `tools/hoechstmass/README.md` and `.agents/skills/jira-workflow/SKILL.md`.
  - Author automated mock unit tests (`test_jira_datacenter.py` and `test_hoechstmass_tools.py`).
* **Out-of-Scope Non-Goals**: Modifying Jira Server v6/v7 legacy SOAP APIs, altering Android production code in `app/src/main/`, or redesigning the 5-stage ASPICE lifecycle.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Refines `REQ-PRO-036` (*Parameterized Issue Tracker Automation & Governance Tooling*) and extends `REQ-PRO-019` (*Multi-Account Authentication*).
* **Historical Trace**: `ATT-4520` (Multi-Account Auth), `ATT-5667` (Höchstmaß Tooling Parameterization).
* **Root Reason for Existing Formulation**: Initial tooling targeted the project's active Jira Cloud workspace. Expanding to Data Center is required for enterprise ASPICE deployment.
* **Preservation of Core Invariants**: Existing Jira Cloud workflows, 8 Core In-Sprint Governance Rules, secret masking (`REQ-PRO-021`), and human review gates remain 100% backward-compatible.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression for Jira Cloud users; `submit-stage` and `gate-verdict` composite workflows remain identical across deployments; zero runtime changes to `app/src/main/`.
* **Risk Rating**: **LOW** (External Python CLI enhancements and declarative configuration; zero impact on Android production runtime).
