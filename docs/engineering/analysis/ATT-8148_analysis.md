# Stage 1 Analysis: ATT-8148 - Clean up orphan REQ and TST IDs in code, stale shard header counts and missing REQ description fields

**Ticket**: [ATT-8148](https://rainerblind.atlassian.net/browse/ATT-8148)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Branch**: `feature/ATT-8148` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Finding `Minor (SWE.1)` (SWE.1 BP1/BP5, GP 2.2.4), living documentation integrity suffers from three distinct drift vectors:
1. **Code-to-Doc Orphans**: 14 REQ IDs and 5 TST IDs cited in `app/src/` do not exist in any requirement/test shard (`req_*.md` / `tst_*.md`).
2. **Stale Header Counts**: 7 of 9 REQ shards and 7 of 9 TST shards publish outdated `**Total Requirements / Tests**` header counts (e.g. `req_process_stability.md` claims 90, actual 168; `req_tracking_cockpit.md` claims 87, actual 152).
3. **Missing Description & Criteria**: 860 / 1169 REQs (73.6%) lack the standard `- **Description**:` field; 293 lack Given-When-Then criteria; 44 lack explicit allocation.
`tools/verify_requirement_governance.py` currently validates internal shard-to-shard links but has no scanner for code-level orphan citations or shard header synchronization.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Code-Level Orphan Archaeology**:
  * `REQ-WKO-035..039` / `TST-WKO-035..039`: Introduced in `2c999318` (`ATT-6879`, Workout RPE/Feel). Engineering plans and tests were written, but headings were omitted from `req_workout_management.md` and `tst_workout_management.md`.
  * `REQ-HRV-038`, `040`, `041`, `042` (plus `039` in engineering docs): Introduced in `a77ce176` (`ATT-7034`, Morning HRV / Wellness). Specs were omitted from `req_heart_rate_variability.md` and `tst_heart_rate_variability.md`.
  * `REQ-SPT-001`, `003`, `004`: Introduced in `b64ad058` (`ATT-7022`, Trophy Vault). Only `REQ-SPT-002` and `005` were placed in `req_aftermath_periods.md`; 001, 003, and 004 were omitted.
  * `REQ-UI-2780`: Tagged in `SensorSearchTuningSection.kt:93`. Code comment typo conflating ticket `ATT-2780` with `REQ-UI-314` (commit `68bddc6f`).
  * `REQ-UI-292`: Tagged in `SensorFieldStyle.kt:166`. Valid verified requirement from `ATT-2624` accidentally dropped during documentation sharding in `06990c88`.
  * `REQ-EXP-041`: Tagged in `FitFileWriter.kt:110` alongside `REQ-MIG-053`. `REQ-MIG-053` is the authentic verified requirement; `REQ-EXP-041` is a legacy alias.
* **Header Count Discrepancy**:
  * `recalculate_master_index_tables()` (`--sync-indexes` / `recalc-docs`) updates `docs/requirements.md` and `docs/tests.md`, but never updates the per-shard `**Total Requirements**: X` / `**Total Tests**: X` headers.
* **Field Coverage Baseline & Enforcement Strategy**:
  * Enforcing `- **Description**:` unconditionally would fail 860 existing legacy REQs. A ratchet/baseline strategy must be established: mandatory `- **Description**:` for new REQs, accompanied by a coverage report.
* **Next Available Process Requirement ID**:
  * Audit on `5fb97345` confirms highest existing IDs are `REQ-PRO-103` / `TST-PRO-103`. `REQ-PRO-104` and `TST-PRO-104` are available.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `verify_code_traceability_orphans()` to `tools/verify_requirement_governance.py` and CI scanning `app/src/` (`main`, `test`, `androidTest`) for undeclared `REQ-*` and `TST-*` IDs.
  2. Resolve all code orphans:
     - Add missing specifications to `req_workout_management.md`, `tst_workout_management.md`, `req_heart_rate_variability.md`, `tst_heart_rate_variability.md`, `req_aftermath_periods.md`, `tst_aftermath_periods.md`, `req_ui_design_system.md`, `tst_ui_design_system.md`.
     - Correct code comment tags in `SensorSearchTuningSection.kt` (`REQ-UI-314`) and `FitFileWriter.kt` (`REQ-MIG-053`).
  3. Extend `recalculate_master_index_tables()` to synchronize per-shard header counts and enforce equality in `verify_requirement_governance.py`.
  4. Define `REQ-PRO-104` / `TST-PRO-104` in `req_process_stability.md` / `tst_process_stability.md` mandating `- **Description**:` for new REQs and reporting field coverage.
* **Out-of-Scope Non-Goals**:
  - Mass-generating synthetic descriptions for 860 legacy requirements in this ticket (must be remediated incrementally per domain).
  - Production code behavior changes (strictly docstrings/comments tag corrections).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-075` (syntax governance), `REQ-PRO-087` (traceability orphans), and `REQ-PRO-041.4` (index synchronization) in `docs/requirements/req_process_stability.md`.
* **Historical Origin & Commit Trace**: `ATT-6163` (`REQ-PRO-075`), `ATT-6129` (`REQ-PRO-087`), `ATT-5431` (`REQ-PRO-041.4`), `06990c88` (shard split).
* **Root Reason for Existing Formulation**: Shard-to-shard checks verified internal document consistency but left the code-to-documentation boundary unverified.
* **Preservation of Core Invariants**: Maintain 100% pass on existing governance suites (`verify_system_software_traceability`, `verify_traceability_orphans`, `testDebugUnitTest`), keep `ANT-Android-SDKs` at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-104`; backfilled `REQ-WKO-035..039`, `REQ-HRV-038..042`, `REQ-SPT-001/003/004`, `REQ-UI-292`.
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `app/src/main/` comments (2 files), and 18 living documentation shards in `docs/requirements/` and `docs/tests/`.
* **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py`, CI workflows (`.github/workflows/`), and full unit test suite.
* **Risk Assessment & Invariants**: **LOW-MEDIUM** (`2 SP`, governance tooling and documentation alignment; production code modifications strictly comment/docstring tag fixes; zero runtime logic impact).

---

## 6. Architectural Strategy & High-Level Solution
* Add code-to-shard orphan scanner `verify_code_traceability_orphans()` with regex token extraction over `app/src/` (`.kt`, `.java`, `.xml`), matching against `known_req_ids` and `known_tst_ids`.
* Update `recalculate_master_index_tables()` to replace `**Total Requirements**: \d+` and `**Total Tests**: \d+` in all 18 shards, and add validator ensuring 0 header drift.
* Introduce `--report-req-field-coverage` to calculate and report Description, Given-When-Then, and Allocation coverage percentages across all shards.
