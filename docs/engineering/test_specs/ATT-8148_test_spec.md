# Stage 2: Requirement & Test Specification - ATT-8148: Clean up orphan REQ and TST IDs in code, stale shard header counts and missing REQ description fields

**Ticket**: [ATT-8148](https://rainerblind.atlassian.net/browse/ATT-8148)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-104` (*Code-to-Doc Orphan Guard, Shard Header Count Sync & REQ Description Governance*; extends `REQ-PRO-075`, `REQ-PRO-087`, `REQ-PRO-041.4`)  
**Test Spec ID**: `TST-PRO-104`  
**Branch**: `feature/ATT-8148` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-PRO-104)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Planned]`
* **REQ-PRO-104.1 (Code-to-Shard Orphan Detection & Typo Resolution)**: `tools/verify_requirement_governance.py` SHALL implement code-to-shard orphan verification (`verify_code_traceability_orphans()`) scanning `app/src/main/`, `app/src/test/`, `app/src/androidTest/`, and `tools/` for `REQ-*` and `TST-*` citations, failing verification if any cited ID is absent from all 18 living documentation shards (`docs/requirements/` and `docs/tests/`), resolving verified typos (`REQ-UI-2780` -> `REQ-UI-314`, `REQ-EXP-041` -> `REQ-MIG-053`), and restoring authentic missing specifications (`REQ-WKO-035..039`, `REQ-HRV-038..042`, `REQ-SPT-001/003/004`, `REQ-UI-292` and matching `TST-*` IDs).
* **REQ-PRO-104.2 (Living Doc Shard Header Synchronization)**: `recalculate_master_index_tables()` (invoked via `--sync-indexes` and `recalc-docs`) SHALL automatically synchronize `**Total Requirements**: N` and `**Total Tests**: N` header counts across all 18 domain shards in `docs/requirements/` and `docs/tests/` with exact parsed heading counts, and `verify_requirement_governance.py` SHALL fail CI if any shard header count diverges from its actual section count.
* **REQ-PRO-104.3 (Description Field Ratchet & Field Coverage Reporting)**: `tools/verify_requirement_governance.py` SHALL enforce mandatory `- **Description**:` fields on all newly added requirements via `--check-req-description` backed by a frozen legacy baseline ratchet (`LEGACY_MISSING_DESCRIPTION_REQS`), and expose `--report-req-field-coverage` to report field completion metrics (Description, Given-When-Then, Allocation).
* **REQ-PRO-104.4 (Cross-Check Integrity Safeguards)**: Any requirement restored in `SYSTEM_REFINED_PREFIXES` (e.g. `req_aftermath_periods.md`) MUST declare `- **System Requirement(s)**:`, and NO restored test specification may be marked `[Verified]` unless backed by passing test code evidence matching `--check-verified-evidence` and leaving `--check-reverse-sys-trace` with zero unclassified requirements.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-075` (syntax governance), `REQ-PRO-087` (traceability orphans), and `REQ-PRO-041.4` (index synchronization) via net-new `REQ-PRO-104`. Targets `tools/verify_requirement_governance.py`, living documentation shards, and code comments.
* **Historical Origin & Commit Trace**: `ATT-6163` introduced `REQ-PRO-075`; `ATT-6129` added `REQ-PRO-087`; `ATT-5431` added `REQ-PRO-041.4`; `06990c88` split documents into 18 domain shards; `ATT-8148` (Sprint `2026-47.1`, Epic `ATT-8138`) closes code-to-doc and header drift.
* **Root Reason for Existing Formulation**: Shard-to-shard checks verified internal document consistency but left code citations and per-shard header counts unverified against drift.
* **Preservation of Core Invariants**: Zero runtime logic modifications in `app/src/main/`; `ANT-Android-SDKs` untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; preserve existing test suite and synthetic test compatibility.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Code-to-Shard Orphan Detection)**: *Given* source files under `app/src/` and `tools/`, *When* `verify_requirement_governance.py` runs code orphan verification, *Then* zero undeclared `REQ-*` or `TST-*` citations exist across the codebase and exit code is `0`.
* **Criterion 2 (Shard Header Synchronization)**: *Given* all 18 living documentation shards in `docs/requirements/` and `docs/tests/`, *When* `recalculate_master_index_tables()` executes, *Then* all 18 shard header counts match the actual parsed heading counts exactly.
* **Criterion 3 (Description Ratchet & Field Coverage)**: *Given* requirement definitions across living documentation shards, *When* `--check-req-description` and `--report-req-field-coverage` execute, *Then* `REQ-PRO-104` and all post-baseline requirements declare `- **Description**:`, and coverage metrics are reported cleanly.
* **Criterion 4 (SYS Trace & Verification Integrity)**: *Given* restored specifications in `SYSTEM_REFINED_PREFIXES` and `[Verified]` test specifications, *When* `--check-reverse-sys-trace` and `--check-verified-evidence` execute, *Then* zero unclassified SWE requirements exist and zero unbacked `[Verified]` tests are detected.

---

## 4. Test Specification (TST-PRO-104)
* **TST-PRO-104.1 (Code-to-Shard Orphan Detection & Typo Resolution Test)**: Verify `verify_code_traceability_orphans()` scans `app/src/main`, `app/src/test`, `app/src/androidTest`, and `tools/`, flags undeclared `REQ-*` and `TST-*` tokens, passes cleanly with 0 orphans on the live repo after restoring missing specs and fixing `REQ-UI-314` and `REQ-MIG-053` typos, and validates synthetic orphan injection failure.
* **TST-PRO-104.2 (Living Doc Shard Header Synchronization & Audit Test)**: Verify `recalculate_master_index_tables()` parses and writes exact header counts across all 18 shards in `docs/requirements/` and `docs/tests/`, and verify governance check rejects any shard with divergent `**Total Requirements**` or `**Total Tests**` headers.
* **TST-PRO-104.3 (Description Field Ratchet & Field Coverage Reporting Test)**: Verify `--check-req-description` enforces `- **Description**:` on non-baselined requirements (including `REQ-PRO-104`), rejects new REQs lacking description, and verify `--report-req-field-coverage` outputs accurate percentages for Description, Acceptance Criteria, and Allocation fields.
* **TST-PRO-104.4 (Restored Requirement Governance & Evidence Cross-Check Test)**: Verify all restored requirements in `SYSTEM_REFINED_PREFIXES` define valid `- **System Requirement(s)**:`, no restored test is marked `[Verified]` without test code evidence, and `--check-verified-evidence` and `--check-reverse-sys-trace` exit `0`.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-104.1` | Unit | `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py` | `REQ-PRO-104.1` | Planned |
| `TST-PRO-104.2` | Unit | `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py` | `REQ-PRO-104.2` | Planned |
| `TST-PRO-104.3` | Unit | `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py` | `REQ-PRO-104.3` | Planned |
| `TST-PRO-104.4` | Unit / Review | `docs/requirements/*.md`, `docs/tests/*.md` | `REQ-PRO-104.4` | Planned |
