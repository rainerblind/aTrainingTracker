# Stage 1 Analysis: ATT-7561 - Enforce Machine-Verified REQ-TST Bidirectional Traceability

**Ticket**: [ATT-7561](https://rainerblind.atlassian.net/browse/ATT-7561)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7561`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
ASPICE evaluation finding F4 noted requirement-to-test traceability was by convention (assuming same-numbered IDs), not machine-verified.
Currently, `tools/verify_requirement_governance.py` checks SYSREQ -> REQ and dangling references, but does not strictly enforce that every software requirement has a verifying test specification or that every test declares which requirement it verifies.
Historical audit identified 6 REQs appearing in no test document (`REQ-CON-001`, `REQ-CON-004`, `REQ-CON-006`, `REQ-EXP-005`, `REQ-EXT-003`, `REQ-FIL-005`), malformed shorthand in `TST-MIG-001`, and swapped fields in `TST-UI-310`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - Live inspection of `tools/verify_requirement_governance.py` confirms `find_dangling_references()` and `calculate_traceability_coverage()` only match `Requirement(s):` / `Target Requirement:`, missing the standardized `Verifies: REQ-...` syntax.
  - Default governance runs (`verify_governance()`) do not validate that all declared REQs have verifying TSTs or enforce a shrinking orphan baseline.
  - Across all 9 living doc shards (1102 REQs, 1102 TSTs), exactly 24 REQs currently lack a reverse-verifying TST specification, including the 6 completely unreferenced REQs.
  - A formal bidirectional check with a baseline file (`tools/traceability_baseline.json`) is required to prevent orphan regressions (ratchet principle).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Standardize machine-readable `Verifies: REQ-...` field across living test docs and test specs (backward-compatible with `Requirement(s):`).
  - Extend `verify_requirement_governance.py` with bidirectional REQ <-> TST verification and baseline file enforcement (`tools/traceability_baseline.json`).
  - Enforce 3 acceptance criteria: fail on unverified REQs outside baseline; fail on tests referencing non-existent REQs; ratchet baseline (only shrinks).
  - Resolve the 6 unreferenced REQs in test shards and fix malformed citations in `TST-MIG-001` and `TST-UI-310`.
  - Add comprehensive unit tests in `tools/test_verify_requirement_governance.py`.
* **Out-of-Scope Non-Goals**:
  - Backfilling 137 missing requirement attributes (scoped to `ATT-7562`).
  - Rewriting existing 1100+ TST headings to replace `Requirement(s):` en masse.
  - Release/PR CI workflow reorganization (`ATT-7564`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-PRO-070` (*Reference Integrity*) and `REQ-PRO-071` (*Coverage Ratchet*).
* **Historical Origin & Commit Trace**: `ATT-6125`/`ATT-6126` introduced reference and evidence checks; coverage was left as an advisory CLI flag (`--coverage`) due to unmapped legacy volume.
* **Preservation of Core Invariants**: Existing `--sync-indexes` and CI passes must remain 100% green; legacy allowlists must not expand; ratchet must strictly enforce $|B_{t+1}| \le |B_t|$.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PRO-070`, `REQ-PRO-071`, and new `REQ-PRO-078` in `docs/requirements/req_process_stability.md`.
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/traceability_baseline.json`, `docs/tests/` shards.
* **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py`, living docs parity suite.
* **Risk Assessment & Invariants**: Low-to-Medium risk (governance CLI and living doc references).

---

## 6. Architectural Strategy & High-Level Solution
- Parser: Support `r"^\s*[-*]\s*\*{0,2}(?:Requirements?|Requirement\(s\)|Target Requirement|Requirements Covered|Verifies)\*{0,2}\s*:\s*(.*)"`.
- Verification Engine: Add `verify_traceability_orphans()` checking REQ -> TST and TST -> REQ, validating against `tools/traceability_baseline.json`.
- Baseline Ratchet: Fail if new orphans appear. If an orphan is resolved, require updating baseline to smaller set.
- Resolution: Add authentic verifying test entries/tags for the 6 unreferenced REQs and fix malformed entries in `tst_data_migration_export.md` and `tst_ui_design_system.md`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Deterministic error reporting; baseline ratchet monotonicity; zero breakage of existing governance checks.
* **Risk Rating**: **LOW** (Deterministic tooling and documentation governance).
