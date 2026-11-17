# Stage 3: Implementation Plan - ATT-8559: qa-audit CA Creation, Synthetic ATT-CA Guard & Heuristic Parity

**Ticket**: [ATT-8559](https://rainerblind.atlassian.net/browse/ATT-8559) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Target Release**: `V5.0.0` | **Requirement Mapping**: `REQ-PRO-118` (`REQ-PRO-118.1`..`.6`), `REQ-PRO-091.3`
**Test Mapping**: `TST-PRO-118` (`TST-PRO-118.1`..`.6`)
**Branch**: `feature/ATT-8559` | **Author**: `agent1` | **Date**: 2026-11-17

---

## 1. Problem Description & Architectural Scope
`qa_audit()` in `tools/jira_util.py` fails CA deduplication via deprecated `GET /rest/api/2/search` (`410 Gone`), fails CA creation via invalid `issuetype_id="10005"` (`Verbesserung` is `"10008"`), swallows errors into synthetic `ATT-CA-*` links with hardcoded `rainerblind.atlassian.net`, and produces false-positive `FAIL`s on `4-Augen Role Separation`, `Commit Hygiene`, and `Living Documentation Parity` (`REQ-FIL-*`). Meanwhile, `tools/verify_requirement_governance.py:781` permits `ATT-CA-*` tokens on live sprint audits.
* **UI Consistency**: `N/A` (Python governance/CLI tooling & unit tests only; zero `app/src/main/` changes).

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-091.3`, `REQ-PRO-118.1`, `REQ-PRO-118.2` $\rightarrow$ `TST-PRO-118.1`, `TST-PRO-118.2`**: `tools/jira_util.py` uses `get_search_endpoint` / `paged_jql_search` (`/rest/api/3/search/jql`), `issuetype_id="10008"`, `config['JIRA_URL']` browse links, and fails loudly without `ATT-CA-*` synthesis.
* **`REQ-PRO-118.3`, `REQ-PRO-118.4` $\rightarrow$ `TST-PRO-118.3`, `TST-PRO-118.4`**: `tools/jira_util.py` matches `agent2`/`qa` `accountId` on `Gate <1..5> ... PASS|REVISION|FREIGABE` + `GATE_VERDICT:`, accepts `^KEY:` / `[KEY]` / `type(KEY):` / `(KEY)` / `Ticket: KEY` commits, and ignores `REQ-XXX-*` wildcards.
* **`REQ-PRO-118.5` $\rightarrow$ `TST-PRO-118.5`**: `tools/verify_requirement_governance.py` rejects `ATT-CA-*` on post-cutoff/current audits while cleanly handling pre-cutoff legacy audits (`sprint_2026-11-17_01_*` etc.) so `--check-qa-audits` exits `0`.
* **`REQ-PRO-118.6` $\rightarrow$ `TST-PRO-118.1`..`.6`**: `tools/test_jira_accounts.py` & `tools/test_verify_requirement_governance.py` 100% HTTP-mocked (`Rule 5`); re-audit `sprint_2026-11-16_03_nacht.md` and pass `verify_requirement_governance.py`.

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock & Zero Production Edits**: Zero edits to `app/src/main/` or `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`); zero `.py`/audit edits until Gate 3 `check-gate` passes.
2. **Existing `REQ-PRO-091` Invariants**: Independent role (`qa`/`agent2`), dynamic `>= max(2, ceil(0.10*N))` sampling, `ca_key != audited_key`, deduplicated raw Backlog creation (`add_to_sprint=False`, `rank_top=True`), and `Rule 5` offline mocked tests remain intact.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-PRO-118.1`..`.4`, `REQ-PRO-091.3` / `tools/jira_util.py`)**:
  - Confirm Gate 3 via `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.
  - In `qa_audit()` (`L4868-4927`): replace `L4874` `GET /rest/api/2/search` and `L4880` swallow with `paged_jql_search(jql, ["summary"], role=role, config=config, max_total=5)` (propagating API failures loudly); change `L4910` `issuetype_id="10005"` to `"10008"`; remove `L4917-4919` `ATT-CA-*` fallback and raise `RuntimeError` on creation failure; replace `L4926` & `format_qa_audit_markdown()` (`L4545-4550`) hardcoded `https://rainerblind.atlassian.net/browse/` and `ATT-CA-{r['key']}` fallback with `base_url` (`config['JIRA_URL'].rstrip('/')`) and `RuntimeError` if non-compliant `corrective_action` is missing.
  - In `qa_audit()` `4-Augen Role Separation` (`L4757`): extend `is_verdict` to recognize `re.search(r"\bGate\s+[1-5]\b.*\b(?:PASS|REVISION|FREIGABE)\b", body, re.IGNORECASE)` or (`"[Automated comment by AI Agent 2 (Auditor)]"` in `body` and `re.search(r"\b(?:PASS|REVISION|FREIGABE)\b", body, re.IGNORECASE)`), preserving `agent1` vs `agent2`/`qa` `accountId` checks (`L4759-4773`).
  - In `qa_audit()` `Living Documentation Parity` (`L4778-4795`): strip `REQ-[A-Z0-9_-]+-\*` family wildcards before extracting `referenced_reqs` so bare `REQ-FIL` is never checked against `master_req_ids`.
  - In `qa_audit()` `Commit Hygiene` (`L4804-4820`): check `git log --grep={key} -n 20 --format=%s%n%b` and match `^{key}:`, `\[{key}\]`, `^[a-z]+\({key}\):`, `\({key}\)`, or `^Ticket:\s*{key}\b`.
* **Step 2 (`REQ-PRO-118.5` / `tools/verify_requirement_governance.py`)**:
  - In `_validate_single_qa_audit()` (`L774-812`): separate real links `\[(ATT-\d+|PROJ-\d+)\]` from synthetic `\[(ATT-CA-[A-Za-z0-9_-]+)\]`; grandfather pre-fix closed audits (`sprint_2026-46.*`, `sprint_2026-47.*`, `sprint_2026-11-16_02_*`, `sprint_2026-11-17_01_*`) for legacy `ATT-CA-*`, and fail loudly (`return False, [...]`) if any current/post-cutoff live audit contains `ATT-CA-*` or lacks real `[ATT-\d+|PROJ-\d+]` links.
* **Step 3 (`REQ-PRO-118.6`, `TST-PRO-118.1`..`.5` / `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`)**:
  - Update `test_tst_pro_091_11` (`test_jira_accounts.py:2292-2357`) for `/rest/api/3/search/jql`, `issuetype_id="10008"`, and real-format mock key (`ATT-9991`, `Rule 5` HTTP-mocked).
  - Add unit tests in `test_jira_accounts.py` for fail-loud CA errors, `Gate N ... PASS` `agent2` vs `agent1` 4-Augen checks, `[KEY]` / `fix(KEY):` / `(KEY)` / `Ticket: KEY` commits, and `REQ-FIL-*` wildcard exclusion, plus tests in `test_verify_requirement_governance.py` rejecting `ATT-CA-*` on post-cutoff audits (`sprint_2026-11-17_02_spät.md`) while passing real `ATT-\d+` and grandfathered audits.
* **Step 4 (`TST-PRO-118.6` / Re-Audit & Verification)**:
  - Run `python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py` (offline), re-run `--as agent2 qa-audit --sprint "2026-11-16_03_Nacht"` updating `docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md`, and run `python3 tools/verify_requirement_governance.py` + `--check-qa-audits` (`exit 0`).

---

## 5. Verification & Rollback Plan
* **Verification**: 100% offline unit pass in `test_jira_accounts.py` & `test_verify_requirement_governance.py`, authentic `sprint_2026-11-16_03_nacht.md` re-audit, and `verify_requirement_governance.py` (`--check-qa-audits`) exiting `0`.
* **Rollback**: `git checkout -- tools/ docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md` cleanly reverts Stage 4 edits.
