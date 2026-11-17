# Weekly Dual-Vendor Code & Architecture Review: YYYY-MM-DD

**Date**: YYYY-MM-DD  
**Governing Ticket**: ATT-XXXX  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*QA & Process Improvement*) / [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Standard**: ASPICE 4.0 `SWE.2`, `SWE.3.BP4`, `SWE.4.BP2`, `SUP.1.BP1/BP3`, `GP 2.1.6`  
**Audited Subsystems**: `<Subsystem Slice>` (e.g. `tracker/`) + Recent Delta (`<START_SHA>..<END_SHA>`)  
**Gemini Model**: `<Current Top Google Model, e.g. Gemini 2.5 Pro / Gemini 4 Carbon>`  
**Claude Model**: `<Current Top Anthropic Model, e.g. Claude 3.7 Sonnet / Claude 4 Sonnet>`  

---

## 1. Audited Scope & Package Inventory
* **Subsystem Slice**: `<Path>` (Files: `...`)
* **Delta Commit Range**: `<Commit SHA Range>`
* **LoC Audited**: ~...

---

## 2. Independent Vendor Findings Summary

### 2.1 Google Gemini Findings
* **Architecture & Concurrency**: ...
* **Resource Leaks & Lifecycles**: ...
* **Test Validity & Edge Cases**: ...

### 2.2 Anthropic Claude Findings (Claude Non-Simulation Protocol — ATT-8426)
<!-- NOTE (Strict Evidence Integrity - ASPICE Finding K2):
Antigravity agents running on Google models SHALL NEVER simulate, fabricate, or mock Anthropic Claude evaluations.
If executed via authentic external Claude session/CLI, record findings below.
If Claude execution is pending or unavailable, record status strictly as:
"Status: PENDING / EXTERNAL HANDOFF REQUIRED (External Claude session required before marking AUDITED)" -->
* **Status**: `[COMPLETED | PENDING / EXTERNAL HANDOFF REQUIRED]`
* **Architecture & Concurrency**: ...
* **Resource Leaks & Lifecycles**: ...
* **Test Validity & Edge Cases**: ...

---

## 3. Reconciled Findings & Backlog Action Items

| Item | Finding Summary | Severity | Consensus (Gemini / Claude / Both) | Action / Jira Ticket Key |
| :--- | :--- | :--- | :--- | :--- |
| 1 | ... | Major / Minor | Both | `ATT-...` |

---

## 4. Coverage Matrix Update
* Subsystem `<domain>` status updated to `AUDITED` in `docs/engineering/code_reviews/coverage_matrix.md`.
