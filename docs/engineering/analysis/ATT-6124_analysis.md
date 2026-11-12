# Stage 1 Analysis: ATT-6124 - Fix shard_living_docs.py corruption of Tests fields

**Ticket**: [ATT-6124](https://rainerblind.atlassian.net/browse/ATT-6124)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-6124-self-2a46d42d`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Approximately 70 `Tests` fields in living requirements (e.g. `docs/requirements/req_data_migration_export.md:436` for `REQ-EXP-021`) contain truncated sentence fragments such as `Tests: code == 201\` check that inadvertently classified...`, caused by the sharding tool `tools/shard_living_docs.py`. Furthermore, `shard_living_docs.py` lacks a safety guard to prevent execution against `docs/requirements.md` when it is already a Master Index, which would wipe all 9 domain shards with 0 parsed requirements.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause Analysis**:
  * In `tools/shard_living_docs.py`, `parse_requirements_file()` and `parse_tests_file()` execute naive `[p.strip() for p in row.split("|")[1:-1]]`.
  * When requirement descriptions or code references contain inline code with pipes or logical OR (`||`, e.g. `code == 200 || code == 201`), splitting on raw `|` inside code spans produces extra columns.
  * Because column assignment is hardcoded positionally (`desc=parts[1]`, `rationale=parts[2]`, `impl=parts[3]`, `tests=parts[4]`, `status=parts[5]`), extra splits shift column indices: `parts[4]` receives an inline code fragment instead of the test ID, and subsequent columns are lost or shifted.
  * In addition, monolithic tables contained both 6-column rows (`ID`, `Description`, `Rationale`, `Impl`, `Tests`, `Status`) and 7-column rows (`ID`, `Title`, `Description`, `Rationale`, `Impl`, `Tests`, `Status`), which positional assignment conflated.
* **Independent Peer Review & Verification**:
  * Inspected commit `06990c88` where `shard_living_docs.py` was introduced. In parent commit `06990c88~1:docs/requirements.md`, line 186 (`REQ-EXP-021`) contains `binary \`code == 200 || code == 201\` check`.
  * Naive `row.split("|")` produces 9 parts instead of 7, causing `tests` to be assigned to the string fragment following `||`.
  * Live codebase audit confirms `docs/requirements/req_data_migration_export.md:436` contains this exact corruption.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Fix table row parser in `tools/shard_living_docs.py` to tokenize markdown table rows ignoring pipes inside backtick code spans (`` `...` `` and ```` ``...`` ````) and escaped pipes (`\|`), correctly handling both 6-column and 7-column formats.
  2. Add safety guards in `tools/shard_living_docs.py` aborting execution if `docs/requirements.md` or `docs/tests.md` is already a Master Index (e.g., contains "Master Index" or 0 `| **REQ-` rows) or if parsed rows are 0 / suspiciously low, preventing destructive shard overwriting unless explicit `--force` is given.
  3. Create an automated regression test suite (`tools/test_shard_living_docs.py`) reproducing the column shifting failure on backticked pipes and verifying the fix and safety guard.
* **Out-of-Scope Non-Goals**:
  * Healing existing corrupted requirement shard entries in `docs/requirements/*.md` is explicitly assigned to sibling ticket `ATT-6125` (`[Verbesserung] Repair corrupted Tests fields in requirement shards`).
  * No changes to Android runtime code (`app/src/main/`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-065` (*Living Documentation Sharding Parser Code-Span Pipe Immunity & Master Index Safety Guard*) and test `TST-PRO-065`. References `REQ-PRO-028` (*Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Governance*).
* **Historical Origin & Commit Trace**: `shard_living_docs.py` was created in Sprint 2026-44.1 via `ATT-4594` (`06990c88`).
* **Root Reason for Existing Formulation**: Sharding was designed as a one-time migration script with a simplistic pipe splitter, unaware of inline code blocks containing logical OR operators (`||`).
* **Preservation of Core Invariants**: 100% ID preservation, living doc multi-line formatting invariants, and existing governance verifier checks remain fully preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-065` (`REQ-PRO-065.1`–`REQ-PRO-065.3`), referencing `REQ-PRO-028`.
* **Affected Architecture Elements**: `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py`.
* **Verification Impact & Test Surface**: Automated unit and regression test suite `tools/test_shard_living_docs.py` testing pipe-in-backticks parsing, 6 vs 7 column handling, reproduction of pre-fix truncation, and master index overwrite guard. `tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: LOW risk. Pure developer tooling improvement. Invariant: existing shard files are never overwritten when requirements.md is a master index.

---

## 6. Architectural Strategy & High-Level Solution
1. **Tokenizing Markdown Rows**: Implement `split_markdown_table_row(row: str) -> list[str]` in `tools/shard_living_docs.py` that processes rows character-by-character, tracking backtick runs and escaped backslashes, ignoring `|` inside code spans.
2. **Column Normalization**: Correctly parse both 6-column and 7-column formats into structured dictionaries.
3. **Master Index Safety Guard**: In `main()` of `tools/shard_living_docs.py`, inspect `docs/requirements.md` and `docs/tests.md` before proceeding. If either file contains `(Master Index)` or contains zero `| **REQ-` / `| **TST-` rows, abort with exit code 1 and error message unless `--force` is specified.
4. **Regression Test Suite**: Author `tools/test_shard_living_docs.py` using `unittest` covering:
   - Reproduction of the pre-fix truncation bug on REQ-EXP-021 snippet.
   - Proper parsing of inline code with single and double backticks and multiple pipes.
   - Master Index overwrite abort guard.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Sharding parser preserves all table cell contents byte-identically without column shifting.
  2. Sharding tool never destructively overwrites living documentation shards when executed against master indexes.
  3. `tools/verify_requirement_governance.py` passes cleanly (exit code 0).
* **Risk Rating**: **LOW** — Isolated developer tooling fix and test additions with zero impact on production runtime code.
