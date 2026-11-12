# Stage 1 Analysis: ATT-6125 - Repair corrupted Tests fields in requirement shards

**Ticket**: [ATT-6125](https://rainerblind.atlassian.net/browse/ATT-6125)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-6125-self-09c91856`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In the living requirements documentation (`docs/requirements/*.md`), approximately 70 requirements have corrupted `Tests` fields containing truncated sentence fragments (e.g. `Tests: code == 201\` check...` or dangling code checks), caused by the legacy sharding tool (`tools/shard_living_docs.py`) prior to the fix in ATT-6124 (commit `c9c2d428`). Furthermore, 452 requirements list production Kotlin/Java/XML source files in their `Tests` field instead of valid backticked test identifiers (`TST-*`), due to historical column index shifting between 6-column and 7-column monolithic markdown tables during sharding.

To establish machine-readable bidirectional traceability under ASPICE SWE.1 BP1 and evidence integrity, all requirement shards must be systematically repaired: moving misplaced source files to the `Implementation` field, restoring pristine test IDs from git history (`06990c88~1:docs/requirements.md`) and test shards (`docs/tests/*.md`), and enforcing a strict validation rule in `tools/verify_requirement_governance.py`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause 1: Markdown Table Cell Pipe Splitting**:
  * In the original monolithic `docs/requirements.md`, several requirement descriptions and rationales contained inline code spans with logical OR (`||`, e.g. `code == 200 || code == 201`) or LaTeX math pipes.
  * The legacy sharding script split rows naively using `row.split("|")`, producing extra tokens that shifted columns to the right, causing truncated fragments of descriptions to land in the `Tests` field.
* **Root Cause 2: 6-Column vs. 7-Column Table Schema Mismatch**:
  * The monolithic requirements table contained both 6-column rows (`[ID, Description, Rationale, Impl, Tests, Status]`) and 7-column rows (`[ID, Title, Description, Rationale, Impl, Tests, Status]`).
  * The legacy script hardcoded 6-column indices (`desc=parts[1]`, `rationale=parts[2]`, `impl=parts[3]`, `tests=parts[4]`, `status=parts[5]`).
  * On 7-column rows, index `4` corresponded to `Implementation File(s)` and index `5` corresponded to `Tests`. Consequently, implementation source files (`.kt`, `.java`, `.xml`) were placed into `Tests:`, while rationale text landed in `Implementation:`.
* **Root Cause 3: In-line Ticket Metadata Concatenation**:
  * Subsequent tickets (e.g. `ATT-4849`) appended `| **Tickets**: ATT-...` directly into the `Tests:` row without dedicated field separation.
* **Independent Peer Review & Verification**:
  * Inspected pre-sharding baseline `06990c88~1:docs/requirements.md`. Parsing this commit using the ATT-6124 fixed `split_markdown_table_row` successfully recovered pristine test IDs and implementation files for 438 of the 452 source-file occurrences and 68 of 68 truncated fragment occurrences.
  * The remaining 14 non-historical occurrences were cross-referenced against `docs/tests/tst_*.md` reverse-traceability mappings, successfully mapping every single requirement to its authentic `TST-*` identifier.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Author an automated repair script to repair all requirement shards (`docs/requirements/req_*.md`):
     - Restore pristine `TST-*` IDs for all ~70 truncated fragment fields.
     - Move misplaced source files (`.kt`, `.java`, `.xml`) from `Tests:` to `Implementation:` and populate `Tests:` with corresponding `TST-*` IDs.
     - Strip dangling `| **Tickets**: ...` annotations from `Tests:` lines.
  2. Implement an automated validation rule in `tools/verify_requirement_governance.py` asserting that all `Tests:` fields contain only backticked TST IDs (format `` `TST-[A-Z0-9_-]+` ``) or standardized placeholders (`None`, `Planned`, `Review`, `Manual inspection`).
  3. Register net-new governance requirement `REQ-PRO-075` and test `TST-PRO-075` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
  4. Author comprehensive unit tests in `tools/test_verify_requirement_governance.py`.
  5. Synchronize living docs master index with 100% parity (`python3 tools/verify_requirement_governance.py --sync-indexes`).
* **Out-of-Scope Non-Goals**:
  * No modification to Android runtime code (`app/src/main/`).
  * No changes to test shards (`docs/tests/*.md`) other than registering `TST-PRO-075`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-075` (*Requirement Shard Tests Field Format & Traceability Integrity*) and test `TST-PRO-075`. References `REQ-PRO-028` (*Living Documentation Sharding*) and `REQ-PRO-065` (*Code-Span Pipe Immunity*).
* **Historical Origin & Commit Trace**: Commit `06990c88` (Sprint 2026-44.1, `ATT-4594`) introduced domain-sharded requirements. Commit `c9c2d428` (`ATT-6124`) repaired the parser in `tools/shard_living_docs.py`.
* **Root Reason for Existing Formulation**: Shards were generated once by the initial script and never systematically retrofitted, leaving historical formatting artifacts in place.
* **Preservation of Core Invariants**: All 1072+ requirement identifiers, statuses, descriptions, and rationales are preserved. Bidirectional linkage to `docs/tests/` is elevated to 100% valid format.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-075` (`REQ-PRO-075.1`–`REQ-PRO-075.3`).
* **Affected Architecture Elements**: `docs/requirements/*.md` (all 9 shards), `docs/requirements.md` (master index), `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`.
* **Verification Impact & Test Surface**: Unit tests in `tools/test_verify_requirement_governance.py` testing positive matching of valid TST IDs/placeholders, rejection of source files, unclosed backticks, and dangling text. `tools/verify_requirement_governance.py` execution across all 9 shards.
* **Risk Assessment & Invariants**: **LOW** risk. Non-runtime developer tooling and documentation governance. Invariant: Master table parity and requirement unique ID checks remain 100% green.

---

## 6. Architectural Strategy & High-Level Solution
1. **Automated Repair Pipeline**:
   - Parse pre-sharding commit `06990c88~1:docs/requirements.md` using `split_markdown_table_row` to extract baseline `(impl, tests)` mappings for historical REQs.
   - Build reverse-index from `docs/tests/tst_*.md` (`Requirement(s): REQ-*`) to resolve net-new REQs not in `06990c88~1`.
   - Update `docs/requirements/req_*.md`:
     * For 7-column shifted rows: Move source files to `Implementation:` (preserving any existing non-file implementation notes) and assign `Tests:` to the resolved `TST-*` IDs.
     * For truncated fragment rows: Replace truncated fragment with the authentic `TST-*` ID.
     * Clean trailing `| **Tickets**: ...` from `Tests:` fields.
2. **Governance Verifier Rule (`REQ-PRO-075`)**:
   - In `tools/verify_requirement_governance.py`, add `verify_tests_field_format(repo_root=None)`.
   - Validate each `Tests:` line: must match `^(`TST-[A-Z0-9_-]+`|None|Planned|Review|Manual inspection)(\s*,\s*(`TST-[A-Z0-9_-]+`|None|Planned|Review|Manual inspection))*$`.
   - Report any violations with file name and line number, returning failure count.
3. **Master Index Parity**:
   - Execute `python3 tools/verify_requirement_governance.py --sync-indexes` to update `docs/requirements.md`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero requirement deletions or duplicate ID introductions across all shards.
  2. Master table parity strictly 100% green with zero drift.
  3. Every `Tests:` line satisfies regex `^(`TST-[A-Z0-9_-]+`|None|Planned|Review|Manual inspection)(,\s*(`TST-[A-Z0-9_-]+`|None|Planned|Review|Manual inspection))*$`.
* **Risk Rating**: **LOW** — Documentation formatting repair and CI governance tooling without changes to Android application binaries or runtime behavior.
