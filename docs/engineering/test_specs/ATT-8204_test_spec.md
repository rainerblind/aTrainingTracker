# Stage 2: Requirement & Test Specification - ATT-8204

**DOC-ID**: `DOC-TST-ATT-8204` | **Ticket**: [ATT-8204](https://rainerblind.atlassian.net/browse/ATT-8204) (`1 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8204`  
**Requirement Mapping**: `REQ-PRO-062` (`[Planned]`, refined in `req_process_stability_part2.md`) | **Test Spec ID**: `TST-PRO-062` (`[Planned]`)  
**Localization Parity**: `N/A - no UI strings` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-062` `[Planned]`)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **Implementation**: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `docs/engineering/reviews/sprint_2026-46.4.md`, `docs/engineering/reviews/sprint_2026-47.1.md`
* **`REQ-PRO-062.1` (Review Record Schema & Historical Evidence Correction)**: Review deliverables SHALL record `Reviewer`, `Review Type` (`Human Sprint Review (Layer 4)` or `Cross-Vendor AI Review (Layer 5)`), `Vendor` (`Human` or AI vendor), `Model & Version` (`Human Process Owner (vN/A)` or AI model), `Date`, `Scope`, and `Verdict`. Both `docs/engineering/reviews/sprint_2026-46.4.md` (`PASS`) and `docs/engineering/reviews/sprint_2026-47.1.md` (`NOT PERFORMED`) SHALL be repaired to `Review Type: Human Sprint Review (Layer 4)`, `Vendor: Human`, and `Model & Version: Human Process Owner (vN/A)` for `Reviewer: Rainer Blind`, with auditable `ATT-8204` correction notes preserving original timestamps, ticket lists, and verdicts.
* **`REQ-PRO-062.2` (`record-review` Explicit Review-Type Classification & Validation)**: `record_review()` and CLI parsing in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL support `--review-type=human|cross-vendor-ai`. When `--review-type=human`, `--vendor human`, or neither AI `--vendor` nor `--model` is supplied (without `--review-type=cross-vendor-ai`), ANY reviewer name (e.g., `"Rainer Blind"`) SHALL classify as `Human Sprint Review (Layer 4)`, `Vendor: Human`, `Model & Version: Human Process Owner (vN/A)` and MUST NOT emit `Cross-Vendor AI Review (Layer 5)` or `External AI Model`. When `--review-type=cross-vendor-ai` is passed, non-empty non-human `--vendor` and `--model` SHALL be mandatory (`ValueError`/exit `1` if omitted); omitting `--review-type` with AI `--vendor`/`--model` SHALL infer `cross-vendor-ai` for backward compatibility, and `verify_independent_reviews()` SHALL continue accepting legacy `Human Sprint Review (Ceremony 2)` records.
* **Given-When-Then Acceptance Criteria**:
  * *Given* `record-review --reviewer "Rainer Blind"` without AI `--vendor`/`--model` (or with `--review-type=human` / `--vendor human`), *When* root or Höchstmaß `record_review()` generates the markdown record, *Then* `Review Type` is `Human Sprint Review (Layer 4)`, `Vendor` is `Human`, `Model & Version` is `Human Process Owner (vN/A)`, and `External AI Model` does not appear.
  * *Given* `record-review --review-type=cross-vendor-ai` omitting `--vendor` or `--model` vs with explicit AI `--vendor` and `--model`, *When* `record_review()` executes, *Then* missing AI fields raise `ValueError`/exit `1`, whereas explicit AI metadata emits `Cross-Vendor AI Review (Layer 5)`.
  * *Given* `docs/engineering/reviews/sprint_2026-46.4.md` and `docs/engineering/reviews/sprint_2026-47.1.md`, *When* inspected and checked via `verify_requirement_governance.py --check-reviews`, *Then* both list `Reviewer: Rainer Blind`, `Human Sprint Review (Layer 4)`, `Vendor: Human`, `ATT-8204` correction notes, and original verdicts (`PASS` / `NOT PERFORMED`).
* **System Invariants**: Zero `app/src/main/` or `ANT-Android-SDKs` edits; PO dirty `tools/` files untouched; pure Python 3 stdlib; `--verdict`+`--findings` guards (`ATT-8142`) and Rule 3 Human Gate preserved; `Localization Parity: N/A - no UI strings`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-PRO-062` (`REQ-PRO-062.1`, `REQ-PRO-062.2`, `docs/requirements/req_process_stability_part2.md:49-88`) targeting `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `docs/engineering/reviews/sprint_2026-46.4.md`, and `docs/engineering/reviews/sprint_2026-47.1.md`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-6155` (`2d2f9663`, Sprint `2026-46.3`) and amended in `ATT-8142` (`7ccac4fc`, Finding K2) to mandate non-defaulting `--verdict` and `--findings`.
* `Root Reason for Existing Formulation`: `ATT-6155` used `reviewer.lower() in ("human", "user")` assuming human Ceremony 2 calls would pass `--reviewer "Human"`, while `test_jira_accounts.py:992` passes AI `--vendor`+`--model` without `--review-type`.
* `Preservation of Core Invariants`: Preserves `--verdict`/`--findings` validation (`ATT-8142`), `[PROCESS DEVIATION — Rule 1.3]` headers, backward compatibility when AI `--vendor`/`--model` are supplied without `--review-type`, legacy `Human Sprint Review (Ceremony 2)` validation in `verify_independent_reviews()`, and zero `app/src/main/` edits.

---

## 2. Test Specification (`TST-PRO-062` `[Planned]`)
> **Note**: `tools/test_review_tool.py` tests `tools/review_tool.py` (separate tool); `record_review()` unit tests reside in `tools/test_jira_accounts.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py` (`Rule 5` offline mocks) and MUST cite `TST-PRO-062` / `TST-PRO-062.1`–`TST-PRO-062.4`.
* **`TST-PRO-062.1` (`tools/test_jira_accounts.py`)**: Verify `record_review()` and CLI parsing for `--reviewer "Rainer Blind"` (default, `--review-type=human`, `--vendor=human`) emit `Human Sprint Review (Layer 4)`, `Vendor: Human`, `Model & Version: Human Process Owner (vN/A)` (no `External AI Model`), verify real AI `--vendor`/`--model` (with or without `--review-type=cross-vendor-ai`) emits `Cross-Vendor AI Review (Layer 5)`, and verify `--review-type=cross-vendor-ai` missing `--vendor`/`--model` fails with `ValueError`/exit `1`.
* **`TST-PRO-062.2` (`tools/test_verify_requirement_governance.py`)**: Verify `verify_independent_reviews()` accepts both `Human Sprint Review (Layer 4)` and legacy `Human Sprint Review (Ceremony 2)` records alongside `Cross-Vendor AI Review (Layer 5)`.
* **`TST-PRO-062.3` (`docs/engineering/reviews/sprint_2026-46.4.md`, `sprint_2026-47.1.md`, `sprint_2026-46.6.md`)**: Verify `sprint_2026-46.4.md` (`PASS`) and `sprint_2026-47.1.md` (`NOT PERFORMED`) accurately record `Rainer Blind` as `Human Sprint Review (Layer 4)` / `Vendor: Human` with `ATT-8204` correction notes and zero `External AI Model`.
* **`TST-PRO-062.4` (`tools/hoechstmass/tools/test_hoechstmass_tools.py`)**: Verify 100% Höchstmaß `record_review()` parity for named human reviewers, `--review-type=human|cross-vendor-ai`, `--vendor human`, and missing AI `--vendor`/`--model` rejection.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-062.1` | Python Unit (Offline) | `tools/jira_util.py` `record_review()` & CLI `--review-type` | `REQ-PRO-062.2` | Planned |
| `TST-PRO-062.2` | Python Unit (Offline) | `verify_independent_reviews()` (`Layer 4` & `Ceremony 2`) | `REQ-PRO-062.1`, `REQ-PRO-062.4` | Planned |
| `TST-PRO-062.3` | Evidence Audit / Governance | `sprint_2026-46.4.md`, `sprint_2026-47.1.md`, `sprint_2026-46.6.md` | `REQ-PRO-062.1` | Planned |
| `TST-PRO-062.4` | Python Unit (Offline Mirror) | `tools/hoechstmass/tools/jira_util.py` `record_review()` parity | `REQ-PRO-062.2` | Planned |
