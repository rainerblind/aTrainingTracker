# Forensic Analysis: Enforce Unique REQ and TST IDs in Governance Verifier (ATT-6120)

## 1. Executive Summary & Problem Context
* **Ticket Key**: `ATT-6120`
* **Summary**: `[Verbesserung] Enforce unique REQ and TST IDs in governance verifier`
* **Target Version**: `V5.0.0`
* **Target Branch**: `sprint/2026-46.4`
* **ASPICE Scope**: SWE.1 BP5, SWE.6 BP4, SUP.1, GP 2.2
* **Complexity**: 1 SP (Trivial / Tooling)

### Problem Description & User Directives
1. **Context**: `tools/verify_requirement_governance.py` enforces master-table counts, directory layout, Composable islands, and Chesterton's Fence archaeology. However, heading IDs (`### REQ-*`, `### TST-*`) across domain shards (`docs/requirements/*.md` and `docs/tests/*.md`) are never checked for uniqueness.
2. **Current State**: 11 duplicate IDs exist undetected (6 in `docs/requirements/`, 5 in `docs/tests/`). Traceability links between REQ and TST become ambiguous when IDs collide.
3. **User Directives**:
   * Add duplicate heading ID detection across `docs/requirements/*.md` and `docs/tests/*.md`.
   * Report every duplicate occurrence with filename and line number.
   * Provide an allowlist / baseline for the 11 known legacy duplicates (to be remediated in ATT-6123) and an enforcement option (`--fail-on-duplicates` / `--enforce-unique`) so CI remains green while rejecting novel duplicates.
   * Add automated unit tests in `tools/test_verify_requirement_governance.py`.

---

## 2. Forensic RCA & Independent Hypothesis Verification (4-Augen-Prinzip)
* **Root Cause**: `verify_master_table_parity()` aggregates counts via `tot += 1` when parsing regex `^(#{2,4})\s*(REQ|TST-[A-Z0-9_-]+)`, discarding parsed identifier tokens after counting. No global dictionary or set (`seen[req_id] -> [(file, line)]`) is populated or evaluated.
* **Independent Hypothesis Verification**:
  * Scanned all 9 shards in `docs/requirements/` and `docs/tests/`.
  * Verified exact duplicate inventory:
    * `docs/requirements/`: `REQ-DAT-001` (lines 10, 2012), `REQ-MIG-018` (730, 1441), `REQ-DAT-026` (2052, 2086), `REQ-UI-116` (10, 388), `REQ-UI-123` (418, 5627), `REQ-UI-257` (5585, 5649).
    * `docs/tests/`: `TST-MIG-036` (629, 1359), `TST-DAT-001` (656, 1305), `TST-DAT-021` (1323, 1338), `TST-MAP-055` (778, 935), `TST-UI-216` (1159, 3210).
  * Confirmed: Exactly 11 duplicates exist; all are known legacy items tracked under ATT-6123.
* **Side-Effect Audit (`Rule 68`)**:
  * Unconditional failure would immediately break CI on `sprint/2026-46.4` and all active feature lanes before ATT-6123 merges.
  * Solution: Baseline the 11 legacy duplicates (`LEGACY_DUPLICATE_IDS`), emit warnings for legacy duplicates, report errors for any NEW duplicate, and provide `--enforce-unique` / `--fail-on-duplicates` CLI flag.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)
* **Original Requirement**: New sub-requirement `REQ-PRO-066` under `req_process_stability.md`.
* **Historical Origin**: `verify_master_table_parity()` added in ATT-5431 to guard count parity; ATT-6119 ASPICE CL2 assessment identified missing uniqueness verification.
* **Preservation of Core Invariants**: Existing master table parity, archaeology validation, and directory structure verification remain 100% untouched.

---

## 4. User Scope Grounding (`ATT-1250`) & Complexity Split Guardrail
* **In-Scope**: Uniqueness verification function `verify_unique_ids()`, CLI flag `--enforce-unique` / `--fail-on-duplicates`, baseline allowlist for ATT-6123 legacy duplicates, and unit test suite.
* **Out-of-Scope**: Renumbering or fixing the 11 legacy duplicates in shard files (reserved strictly for ATT-6123).
* **Complexity Rating**: 1 SP (Trivial / Tooling; no split needed).

---

## 5. Change-Request Impact Analysis (REQ-PRO-064 / ASPICE SUP.10)
* **Affected Requirements**: `REQ-PRO-066` ("Living Documentation Unique Requirement & Test Identifier Governance").
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`.
* **Verification Impact & Test Surface**: New unit test cases in `test_verify_requirement_governance.py` testing detection of duplicates with file/line, legacy baseline tolerance, rejection of new duplicates, and `--enforce-unique` gating.
* **Risk Assessment & Invariants**: LOW risk. Zero production app bytecode touched. Build/CI stability preserved via baseline allowlist until ATT-6123.

---

## 6. Stage Gate Audit Criteria
- [x] Scope strictly bounded to governance verifier duplicate detection tooling.
- [x] Legacy duplicate baseline prevents CI breakage prior to ATT-6123.
- [x] Exact file and line reporting designed for every duplicate.
- [x] Change-request impact analysis complete across all 4 mandatory dimensions.
- [x] Ready for Stage 1 submission.
