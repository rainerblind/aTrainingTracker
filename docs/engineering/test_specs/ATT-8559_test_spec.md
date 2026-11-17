# Stage 2: Requirement & Test Specification - ATT-8559: qa-audit CA Creation, Synthetic ATT-CA Guard & Heuristic Parity

**Ticket**: [ATT-8559](https://rainerblind.atlassian.net/browse/ATT-8559) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Target Release**: `V5.0.0` | **Requirement Mapping**: `REQ-PRO-118`, `REQ-PRO-091.3` | **Test Spec ID**: `TST-PRO-118`
**Branch**: `feature/ATT-8559` | **Author**: `agent1` | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-PRO-118` & `REQ-PRO-091.3` Fix)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Status: `[Planned]`
* **`REQ-PRO-091.3` Correction**: Updated `issuetype_id="10005"` -> `"10008"` (`Verbesserung`) and added Cloud v3 search / fail-loud reference (`REQ-PRO-118`).
* **`REQ-PRO-118.1` (Cloud v3 CA Deduplication & Issue Type)**: `qa_audit()` SHALL query CA duplicates via `paged_jql_search()` / `get_search_endpoint(config)` (`/rest/api/3/search/jql` on Cloud, `/rest/api/2/search` on Data Center), create CA tickets with `issuetype_id="10008"` (`Verbesserung`), and format browse links from `config['JIRA_URL'].rstrip('/')`.
* **`REQ-PRO-118.2` (Fail-Loud CA Errors)**: `qa_audit()` MUST NOT swallow Jira CA search/create errors or synthesize `ATT-CA-*` placeholders (`ATT-CA-<slug>` / `ATT-CA-{key}`); live CA failures SHALL raise explicitly.
* **`REQ-PRO-118.3` (4-Augen Gate Verdict Recognition)**: `qa_audit()` SHALL recognize genuine `agent2`/`qa` `accountId` gate comments matching `GATE_VERDICT:` / `GATE-VERDICT` / `VERDICT: PASS|REVISION` or standard `Gate <1..5> ... PASS|REVISION|FREIGABE` headers.
* **`REQ-PRO-118.4` (Commit Hygiene & Wildcard Parity)**: `qa_audit()` SHALL accept `^KEY:`, `[KEY]`, `type(KEY):`, `(KEY)`, and `Ticket: KEY` commits, and ignore `REQ-XXX-*` family wildcards before checking `master_req_ids`.
* **`REQ-PRO-118.5` (Verifier Synthetic `ATT-CA-*` Rejection)**: `verify_requirement_governance.py` (`--check-qa-audits`) SHALL reject synthetic `ATT-CA-*` tokens on post-cutoff/current sprint audits while handling legacy pre-cutoff audits cleanly (`exit 0`).
* **`REQ-PRO-118.6` (Rule 5 Offline HTTP Mocking)**: Automated tests in `test_jira_accounts.py` and `test_verify_requirement_governance.py` MUST mock all Jira HTTP calls and NEVER create live Jira tickets.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
* **Original Requirement ID & Target**: `REQ-PRO-091.3`, `REQ-PRO-091.5`, `REQ-PRO-091.6` (`docs/requirements/req_process_stability.md:5067-5083`) targeting `tools/jira_util.py` and `tools/verify_requirement_governance.py`.
* **Historical Origin & Commit Trace**: `ATT-7558` (Sprint `2026-46.6`), `ATT-8152` & `ATT-8153` (Sprint `2026-11-16_02_Spät`) introduced `"10005"` and allowed `ATT-CA-*` in `_validate_single_qa_audit()`.
* **Root Reason for Existing Formulation**: `ATT-8153` assumed `"10005"` mapped to `Verbesserung` and added `ATT-CA-*` to match `test_tst_pro_091_11`'s mock `ATT-CA-100` key without isolating test mocks from live audits.
* **Preservation of Core Invariants**: Independent role (`qa`/`agent2`), `>= max(2, ceil(0.10*N))` sampling, `ca_key != audited_key`, raw Backlog creation (`add_to_sprint=False`, `rank_top=True`), and `Rule 5` offline tests remain preserved.

---

## 3. Acceptance Criteria (Given-When-Then) & Localization Parity
* **Criterion 1 (Cloud CA Creation & Fail-Loud)**: *Given* a non-compliant `qa_audit()`, *When* CA deduplication/creation runs, *Then* it calls `get_search_endpoint` / `paged_jql_search` and `create_issue(issuetype_id="10008")`, using `JIRA_URL` browse links and raising loudly on API errors without `ATT-CA-*` fallback.
* **Criterion 2 (Audit Check Parity)**: *Given* authentic `agent2` `Gate N ... PASS` comments, `[ATT-X]` / `fix(ATT-X):` / `(ATT-X)` commits, and `REQ-FIL-*` wildcards, *When* evaluated by `qa_audit()`, *Then* 4-Augen, Commit Hygiene, and Living Docs Parity pass.
* **Criterion 3 (Verifier Guard & Offline Tests)**: *Given* `verify_requirement_governance.py --check-qa-audits`, *When* checking post-cutoff non-compliant reports with `ATT-CA-*` vs real `ATT-\d+` keys, *Then* `ATT-CA-*` fails (`1`) and valid/grandfathered reports pass (`0`), with zero live Jira issues created in unit tests.
* **Localization Parity**: `N/A - no UI strings`.

---

## 4. Test Specification (`TST-PRO-118`)
* **`TST-PRO-118.1` (Mocked Cloud v3 Search & `10008` CA Creation)**: `test_jira_accounts.py` verifies `get_search_endpoint` / `/rest/api/3/search/jql`, `issuetype_id="10008"`, and `JIRA_URL` links (100% HTTP mocked).
* **`TST-PRO-118.2` (Fail-Loud CA Error)**: `test_jira_accounts.py` verifies Jira CA API errors propagate loudly without `ATT-CA-*` fallback.
* **`TST-PRO-118.3` (4-Augen Verdict Matching)**: `test_jira_accounts.py` verifies `Gate N ... PASS` by `agent2` passes while `agent1` verdicts fail.
* **`TST-PRO-118.4` (Commit & Wildcard Parity)**: `test_jira_accounts.py` verifies `[KEY]` / `fix(KEY):` / `(KEY)` / `Ticket: KEY` and `REQ-FIL-*` handling.
* **`TST-PRO-118.5` (Verifier Synthetic `ATT-CA-*` Rejection)**: `test_verify_requirement_governance.py` verifies `_validate_single_qa_audit()` rejects `ATT-CA-*` on post-cutoff audits and passes real `ATT-\d+` / grandfathered audits.
* **`TST-PRO-118.6` (Re-Audit & Governance Gate)**: Re-audit `sprint_2026-11-16_03_nacht.md` (`--as agent2`) and verify `verify_requirement_governance.py` + `--check-qa-audits` exit `0`.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-118.1` | Unit (Python Mocked) | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-118.1`, `REQ-PRO-118.6`, `REQ-PRO-091.3` | Planned |
| `TST-PRO-118.2` | Unit (Python Mocked) | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-118.2`, `REQ-PRO-118.6` | Planned |
| `TST-PRO-118.3` | Unit (Python Mocked) | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-118.3` | Planned |
| `TST-PRO-118.4` | Unit (Python Mocked) | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-118.4` | Planned |
| `TST-PRO-118.5` | Unit (Python) | `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py` | `REQ-PRO-118.5` | Planned |
| `TST-PRO-118.6` | Re-Audit & CLI | `docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md`, `tools/verify_requirement_governance.py` | `REQ-PRO-118`, `REQ-PRO-091.3` | Planned |
