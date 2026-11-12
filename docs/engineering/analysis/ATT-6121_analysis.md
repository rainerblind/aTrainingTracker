# Forensic Analysis: Detect Dangling REQ and TST References in Governance Verifier (ATT-6121)

## 1. Executive Summary & Problem Context
* **Ticket Key**: `ATT-6121`
* **Summary**: `[Verbesserung] Detect dangling REQ and TST references in governance verifier`
* **Target Version**: `V5.0.0`
* **Target Branch**: `sprint/2026-46.4`
* **ASPICE Scope**: SWE.1 BP5, SWE.6 BP4, SUP.1, GP 2.2
* **Complexity**: 1 SP (Trivial / Tooling)

### Problem Description & User Directives
1. **Context**: `tools/verify_requirement_governance.py` validates heading counts, uniqueness of IDs (ATT-6120), Composable reachability, and directory layout. However, citations across living documentation fields are never cross-referenced against target headings.
2. **Current State**: 8 unique TST IDs cited by REQs (11 occurrences) and 11 unique REQ IDs cited by TSTs (16 occurrences) do not exist as headings in any domain shard (e.g. `TST-FUSION-001..004`, `REQ-UI-002`), breaking bidirectional traceability.
3. **User Directives**:
   * Add `find_dangling_references(req_shards, tst_shards)` in `tools/verify_requirement_governance.py` to extract cited TST IDs from the `Tests` field of every REQ and cited REQ IDs from the `Requirements` field of every TST, verifying they resolve to authentic headings.
   * Report every dangling reference with source file, line number, and missing target ID.
   * Provide a baseline allowlist for pre-existing legacy dangling references (to be resolved in A-08.x / ATT-6127..6135) to keep CI green while strictly failing on net-new dangling references.
   * Add CLI flag `--enforce-links` / `--fail-on-dangling` to trigger exit code 1 if any dangling reference exists.
   * Add unit tests in `tools/test_verify_requirement_governance.py` and register `REQ-PRO-070` / `TST-PRO-070` in living docs.

---

## 2. Forensic RCA & Independent Hypothesis Verification (4-Augen-Prinzip)
* **Root Cause**: While `find_duplicate_heading_ids()` collects heading identifiers (`### REQ-*`, `### TST-*`), no logic scans the metadata field lines (`- **Tests**: ...` in REQs, `- **Requirement(s)**: ...` in TSTs) or cross-validates citations against the universe of known headings.
* **Independent Hypothesis Verification**:
  * Scanned all 9 shards in `docs/requirements/` and `docs/tests/`:
    * **Dangling TST IDs cited by REQs (8 unique IDs, 11 occurrences)**: `TST-FUSION-001`, `TST-FUSION-002`, `TST-FUSION-003`, `TST-FUSION-004`, `TST-INT-007`, `TST-STR-010`, `TST-UI-041`, `TST-UI-044`.
    * **Dangling REQ IDs cited by TSTs (11 unique IDs, 16 occurrences)**: `REQ-MAP-037`, `REQ-STB-013`, `REQ-TRK-035`, `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`.
  * Total legacy baseline: 19 unique IDs (27 occurrences).
* **Side-Effect Audit (`Rule 68`)**:
  * Failing immediately on all dangling links would break CI across active sprint branches before the scheduled backfill tickets merge.
  * Solution: Baseline known legacy dangling targets (`LEGACY_DANGLING_TST_REFS`, `LEGACY_DANGLING_REQ_REFS`), report warnings for known legacy items, report errors for any net-new dangling reference, and support strict enforcement mode (`--enforce-links` / `--fail-on-dangling`).

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)
* **Original Requirement**: New requirement `REQ-PRO-070` in `docs/requirements/req_process_stability.md`.
* **Historical Origin**: ASPICE CL2 assessment identified missing link integrity verification.
* **Preservation of Core Invariants**: Existing verifier functions (master table parity, duplicate ID detection, architecture validation) remain unaffected.

---

## 4. User Scope Grounding (`ATT-1250`) & Complexity Split Guardrail
* **In-Scope**: `find_dangling_references()` and `verify_dangling_references()` in `tools/verify_requirement_governance.py`, CLI flags `--enforce-links` / `--fail-on-dangling`, baseline allowlist for legacy dangling references, unit tests in `tools/test_verify_requirement_governance.py`, and `REQ-PRO-070` / `TST-PRO-070` living doc registration.
* **Out-of-Scope**: Remediation of legacy dangling headings in domain shards (handled under A-08.x tickets ATT-6127..6135).
* **Complexity Rating**: 1 SP (Tooling / Verifier; no split needed).

---

## 5. Change-Request Impact Analysis (REQ-PRO-064 / ASPICE SUP.10)
* **Affected Requirements**: `REQ-PRO-070` ("Living Documentation Bidirectional Reference Integrity Governance").
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`.
* **Verification Impact & Test Surface**: Unit tests verifying detection of dangling TST citations in REQs, dangling REQ citations in TSTs, tolerance of allowlisted legacy refs, rejection of new dangling refs, and `--enforce-links` mode.
* **Risk Assessment & Invariants**: LOW risk. Python tooling only. Build/CI stability preserved via baseline allowlist.

---

## 6. Stage Gate Audit Criteria
- [x] Scope strictly bounded to governance verifier link integrity detection.
- [x] Legacy allowlist prevents CI breakage prior to A-08.x backfill tickets.
- [x] Exact file, line, source item, and missing target ID reporting designed.
- [x] Change-request impact analysis complete across all 4 mandatory dimensions.
- [x] Ready for Stage 1 submission.
