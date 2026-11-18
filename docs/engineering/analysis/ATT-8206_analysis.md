# Stage 1 Analysis: ATT-8206 - Symmetric REQ-TST Parity, Scanner Audit & Shard Splitting

**DOC-ID**: `DOC-ANA-ATT-8206` | **Ticket**: [ATT-8206](https://rainerblind.atlassian.net/browse/ATT-8206) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8206` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
Living-doc governance in `tools/verify_requirement_governance.py` and `tools/shard_living_docs.py` shows three structural gaps: (1) `find_traceability_orphans()` validates only set coverage rather than symmetric `REQ <-> TST` edge equality; (2) CI does not verify `--coverage` or `--check-size` limits; (3) five domain shards exceed `REQ-PRO-093.3`'s `4,000`-line limit (`req_ui_design_system.md` reaches `17,599` lines), and `docs/requirements.md` / `docs/tests.md` omit active prefix families (`RAD`, `HRV`, `WKO`, `CLM`).

## 2. Root Cause Analysis & Independent Hypothesis Verification
### Independent Peer Review & Verification
1. **Gap 1 — Asymmetric Traceability Edges (CONFIRMED; shifted lines & higher count)**: `find_traceability_orphans()` is at `tools/verify_requirement_governance.py:1879-1963` (not `L1501-1602`). It checks `known_req_ids - cited_req_ids` (`15` baseline orphans in `tools/traceability_baseline.json`) without comparing `REQ.Tests` against `TST.Verifies`. Live audit finds **`37` `TST->REQ`** gaps (`--coverage` Cat 4: `1206/1243 = 97.02%`, vs `31` in ticket) and **`35` `REQ->TST`** asymmetric links (e.g. `REQ-SET-001 -> TST-SET-082`, `REQ-CON-002 -> TST-CON-003/005`).
2. **Gap 2 — Test Tag Scanner (HYPOTHESIS REFUTED / ALREADY IMPLEMENTED)**: `extract_test_code_tags()` is at `tools/verify_requirement_governance.py:2871-2964` (not `L2173`) and **already scans** `app/src/androidTest/` and `tools/test_*.py` since `ATT-8143` (`f9d9b9e5`); `--coverage` reports `1022/1243` (`82.22%`) Cat 2 and `5` Cat 3 gaps (`TST-PRO-999`, `TST-SIM-001..003`, `TST-TST-005`). Residual check: `L2954-2957` prefix allowlist for `tools/test_*.py` and unit-test coverage.
3. **Gap 3 — Oversized Shards, Prefix Drift & High Blast Radius (CONFIRMED + EXPANDED)**: `tools/shard_living_docs.py --check-size` (`DEFAULT_MAX_SHARD_LINES = 4000`) flags **5** oversized shards (ticket listed 3): `req_ui_design_system.md` (`17,599`), `tst_ui_design_system.md` (`8,721`), `req_tracking_cockpit.md` (`6,825`), `req_process_stability.md` (`6,265`), `req_data_migration_export.md` (`4,068`). `--check-size` does not exist in `verify_requirement_governance.py` and is missing from `.github/workflows/android-branch-build.yml` & `android-release.yml`. `docs/requirements.md` & `docs/tests.md` omit `RAD`, `HRV`, `WKO`, `CLM` (and list empty `PERF`).
   - **Blast Radius of Splitting Shards**:
     - `AspiceSweAuditContractTest.kt` (`L127-211,284-361`) and `verify_master_table_parity()` (`L1265-1407`) require 1:1 master-index rows plus `DOC-*`, semver `Version`, `Status`, `Approver`, and `Change History` on every `.md` under `docs/requirements/` & `docs/tests/`.
     - `SystemRequirementsSpecificationContractTest.kt:134`, `NonFunctionalRequirementsContractTest.kt:75-76,163-164,227-232`, and `SystemArchitectureDesignContractTest.kt:266-267` hard-code `req_process_stability.md`/`tst_process_stability.md` lookups (`REQ-PRO-055`, `REQ-PRO-056`, `REQ-PRO-113`, `REQ-NFR-003..020`).
     - `tools/shard_living_docs.py` `check_shard_sizes()`/`--verify-only` iterate a fixed 9-entry `DOMAINS` list, ignoring split `*_partN.md` sub-shards.

## 3. User Scope Grounding (ATT-1250) & Complexity Check
* **In-Scope (`2 SP` viable with clean sub-sharding)**: Enforce symmetric `REQ<->TST` link validation (resolving the `37` `TST->REQ` + `35` `REQ->TST` mismatches or explicit legacy ratchet), verify/test `androidTest`+`tools/test_*.py` tag scanning, register missing prefixes (`RAD`, `HRV`, `WKO`, `CLM`) in master indexes, split all 5 oversized shards (`<= 4,000` lines) with compliant headers/master rows, update the 3 Kotlin contract tests + `shard_living_docs.py`, and wire `--check-size` into CI (`android-branch-build.yml`, `android-release.yml`).
* **Out-of-Scope**: Editing `app/src/main/` production code, altering `REQ-PRO-087` orphan ratchet semantics, or authoring missing tests for the 221 Cat 2 specs.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-071` (`calculate_traceability_coverage`), `REQ-PRO-087` (`find_traceability_orphans` / `tools/traceability_baseline.json`), `REQ-PRO-093.3/.4` (`tools/shard_living_docs.py`), `REQ-PRO-120` (headers).
* **Historical Origin & Commit Trace**: `ATT-7561` (`traceability_baseline.json`, 15 orphans), `ATT-7565` (`REQ-PRO-093`), `ATT-8143` (`f9d9b9e5`, added `androidTest` + `tools/test_*.py` scanning), `ATT-8145` (`d5a3d1bb`), `ATT-8148` (`e1b4d615`), `ATT-8165` (`c50cbf25`), `ATT-8631` (`a3508e6b`).
* **Root Reason for Existing Formulation**: One-way set difference (`known_req_ids - cited_req_ids`) and 9 static domain shards prevented immediate breakage during legacy ASPICE migration while ratcheting orphans down.
* **Preservation of Core Invariants**: Preserve `tools/traceability_baseline.json` monotonic non-increasing ratchet (`|B_{t+1}| <= |B_t|`), `DOC-*`/semver work-product headers (`REQ-PRO-120`), and 100% ID uniqueness/master-table parity (`AspiceSweAuditContractTest`).

## 5. Change-Request Impact Analysis
* **Affected Requirements**: Refines `REQ-PRO-087` (symmetric `REQ<->TST` enforcement) & `REQ-PRO-093.3/.4` (CI `--check-size` + split sub-shards); touches `docs/requirements.md`, `docs/tests.md`, and the 5 oversized shards (`req_ui_design_system.md`, `tst_ui_design_system.md`, `req_tracking_cockpit.md`, `req_process_stability.md`, `req_data_migration_export.md`).
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/shard_living_docs.py`, `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `docs/architecture.md` (Section 2 prefix alignment).
* **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py`, `tools/test_shard_living_docs.py`, `AspiceSweAuditContractTest`, `SystemRequirementsSpecificationContractTest`, `NonFunctionalRequirementsContractTest`, `SystemArchitectureDesignContractTest`.
* **Risk Assessment & Invariants**: **MEDIUM** — Splitting `req_process_stability.md` / `tst_process_stability.md` and enforcing symmetric links can break existing contract tests and CI if master-table rows, `DOC-*` headers, or hard-coded shard path lookups are missed.

## 6. Architectural Strategy & High-Level Solution
1. **Symmetric Edge Validation**: Extend `find_traceability_orphans()` / `verify_traceability_orphans()` to validate symmetric `REQ.Tests <-> TST.Verifies` parity (and fix the `37` + `35` legacy mismatches in living docs so `find_traceability_orphans` passes cleanly with zero new baseline debt), plus confirm `extract_test_code_tags()` unit tests for `androidTest` and `tools/test_*.py`.
2. **Oversized Shard Splitting & Header/Index Parity**: Enhance `split_oversized_shard()` + `check_shard_sizes()` in `tools/shard_living_docs.py` (scanning all `docs/{requirements,tests}/*.md` dynamically and generating unique `DOC-*` headers + `Change History`), split the 5 `> 4,000`-line shards into compliant sub-shards, sync `docs/requirements.md` / `docs/tests.md` (rows, shard counts, and missing prefixes `RAD`, `HRV`, `WKO`, `CLM`), and update the 3 Kotlin contract tests to search `req_process_stability*.md` / `tst_process_stability*.md`.
3. **CI Enforcement**: Wire `python3 tools/shard_living_docs.py --check-size --strict` (and optionally `--check-size` wrapper in `verify_requirement_governance.py`) into `.github/workflows/android-branch-build.yml` and `android-release.yml`.

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Zero `app/src/main/` changes; (2) `100%` `REQ`/`TST` ID uniqueness and zero lost entries across split shards; (3) All existing `AspiceSweAuditContractTest` & governance checks pass `100%`; (4) Human Decision Gate preserved.
* **Risk Rating**: **MEDIUM** — Cross-cutting living-doc split and CI gate activation; mitigated via automated split parity checks, unit tests in `tools/test_*.py`, and targeted Kotlin contract test runs.
