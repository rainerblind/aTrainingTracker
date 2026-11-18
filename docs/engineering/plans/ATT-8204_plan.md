# Stage 3: Implementation Plan - ATT-8204: Fix record-review human/AI classification and review records

**Ticket**: [ATT-8204](https://rainerblind.atlassian.net/browse/ATT-8204) (`1 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` (`9c1f0e53`)  
**Requirement Mapping**: `REQ-PRO-062` (`REQ-PRO-062.1`, `REQ-PRO-062.2`) | **Test Mapping**: `TST-PRO-062` (`TST-PRO-062.1`–`TST-PRO-062.4`)  
**Branch**: `feature/ATT-8204` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Problem Description & Scope
`record_review()` in `tools/jira_util.py:5474-5485` and `tools/hoechstmass/tools/jira_util.py:2335-2346` treats only `reviewer.lower() in ("human", "user")` as human, misclassifying named human reviewers (`"Rainer Blind"`) as `Cross-Vendor AI Review (Layer 5)` / `External AI Model (vN/A)` in `docs/engineering/reviews/sprint_2026-46.4.md` (`PASS`) and `sprint_2026-47.1.md` (`NOT PERFORMED`).

---

## 2. System Invariants & Preserved Behavior
1. **Stage 3 Code Lock (`Rule 4.1`)**: ZERO edits to `tools/*.py` or `docs/engineering/reviews/*` in Stage 3; execute `check-gate` before Stage 4 edits.
2. **Evidence & Verdict Integrity (`ATT-8142`)**: Preserve mandatory `--verdict`/`--findings` validation and `[PROCESS DEVIATION — Rule 1.3]` headers; preserve original dates, ticket lists, and exact verdicts (`PASS` in `2026-46.4`, `NOT PERFORMED` in `2026-47.1`).
3. **Backward & Mirror Parity**: Infer `cross-vendor-ai` when AI `--vendor`+`--model` are passed without `--review-type` (`test_jira_accounts.py:992`); `verify_independent_reviews()` continues accepting both `Layer 4` and legacy `Ceremony 2` records; 100% parity across root and `tools/hoechstmass/`.
4. **Shard Cap & Clean Workspace**: Keep `docs/tests/tst_process_stability.md` (`3977` lines) `<= 4000` lines in Stages 4–5; zero `app/src/main/` or `ANT-Android-SDKs` edits; leave PO dirty `tools/` files untouched; `Localization Parity: N/A - no UI strings`.

---

## 3. Proposed Architectural Changes
* **Classification & Anti-Spoofing Resolver (`tools/jira_util.py` & `tools/hoechstmass/tools/jira_util.py`)**:
  * Add `review_type=None` parameter to `record_review()` and `--review-type=human|cross-vendor-ai` (plus `--review-type VALUE`) to CLI arg parsing and `USAGE_TEXT`.
  * Validate `review_type` against `{"human", "cross-vendor-ai"}` when provided.
  * **Human (Layer 4)** when `review_type == "human"`, OR `vendor` is `"human"`/`"user"`/`"human process owner"` (case-insensitive), OR (`review_type is None` and neither AI `--vendor` nor AI `--model` is supplied) -> emit `Review Type: Human Sprint Review (Layer 4)`, `Vendor: Human`, `Model & Version: Human Process Owner (vN/A)`, default `Scope: Ceremony 2 Sprint Review` (zero `External AI Model` output). Reject contradictory non-human AI `--vendor`/`--model` under `--review-type=human` with `ValueError`.
  * **Cross-Vendor AI (Layer 5)** when `review_type == "cross-vendor-ai"`, OR (`review_type is None` and AI `--vendor`/`--model` is supplied) -> mandate non-empty `--vendor` and `--model`, rejecting missing/placeholder/spoofed values (`"human"`, `"user"`, `"external ai model"`, `"human process owner"`, `"n/a"`) with `ValueError`/exit `1`, emitting `Cross-Vendor AI Review (Layer 5)`.
* **UI Consistency**: `No UI changes. (Localization Parity: N/A - no UI strings)`

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 — Root `record_review()` & CLI**: Update `tools/jira_util.py` (`record_review`, `USAGE_TEXT`, `record-review` CLI parser) with `--review-type` and Layer 4 vs Layer 5 classification/anti-spoofing guards.
2. **Step 2 — Höchstmaß Mirror Parity**: Apply identical `record_review()` and CLI `--review-type` logic in `tools/hoechstmass/tools/jira_util.py`.
3. **Step 3 — Historical Review Evidence Repair**: Regenerate/correct `docs/engineering/reviews/sprint_2026-46.4.md` (`Verdict: PASS`) and `docs/engineering/reviews/sprint_2026-47.1.md` (`Verdict: NOT PERFORMED` + Rule 1.3 header) to `Reviewer: Rainer Blind`, `Human Sprint Review (Layer 4)`, `Vendor: Human`, `Human Process Owner (vN/A)` with `ATT-8204` correction notes.
4. **Step 4 — Offline Unit & Governance Tests (`TST-PRO-062.1`–`.4`)**:
   * `tools/test_jira_accounts.py`: Update `L975,982` (`Layer 4`) and add `TST-PRO-062.1` tests for `"Rainer Blind"` (default, `--review-type=human`, `--vendor human`), contradictory/spoofing guards, AI `--vendor`/`--model`, and CLI arg parsing.
   * `tools/hoechstmass/tools/test_hoechstmass_tools.py`: Add `TST-PRO-062.4` mirror parity assertions.
   * `tools/test_verify_requirement_governance.py`: Extend Scenario 17 (`TST-PRO-062.2`) for `Layer 4` + legacy `Ceremony 2` compatibility and verify `sprint_2026-46.4.md`/`sprint_2026-47.1.md` (`TST-PRO-062.3`).
5. **Step 5 — Living Docs Promotion & Verification**: Promote `REQ-PRO-062` (`req_process_stability_part2.md`) and `TST-PRO-062` (`tst_process_stability.md`, keeping `<= 4000` lines) to `[Implemented]` (Stage 4) then `[Verified]` (Stage 5), sync master indexes, and run targeted Python tests + `verify_requirement_governance.py` + `shard_living_docs.py --check-size --strict`.

---

## 5. Verification & Rollback Plan
* **Verification Commands (Stages 4–5)**:
  * `python3 -m unittest tools.test_jira_accounts tools.test_verify_requirement_governance tools.hoechstmass.tools.test_hoechstmass_tools`
  * `python3 tools/verify_requirement_governance.py --repo-root . --check-reviews`
  * `python3 tools/shard_living_docs.py --check-size --strict`
* **Rollback**: All changes isolated on `feature/ATT-8204`; reverting `feature/ATT-8204` restores prior state cleanly without touching `develop` or `sprint/2026-11-18_01_Früh`.
