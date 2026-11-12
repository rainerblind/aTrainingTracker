# Stage 2: Requirement & Test Specification - ATT-6124: Fix shard_living_docs.py corruption of Tests fields

**Ticket**: [ATT-6124](https://rainerblind.atlassian.net/browse/ATT-6124)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-065` (*Living Documentation Sharding Parser Code-Span Pipe Immunity & Master Index Safety Guard*)  
**Test Spec ID**: `TST-PRO-065`  
**Branch**: `subagent-Lane-3-Agent---ATT-6124-self-2a46d42d`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-065)

### 1.1 Problem Statement & Rationale
Under ASPICE SUP.8 and SUP.9, process and documentation tooling must guarantee data integrity without silent corruption. In `tools/shard_living_docs.py`, table rows were split using naive `row.split("|")`. When cells contained backticked inline code or blocks with pipes or `||` (e.g. `binary \`code == 200 || code == 201\` check`), extra column splits shifted indices, corrupting `Tests` fields and discarding subsequent columns. In addition, `shard_living_docs.py` had no safety guard against running when `docs/requirements.md` was already a Master Index, which would wipe all domain shards with empty data.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-065.1 (Code-Span & Escaped Pipe Immunity)**:
  `tools/shard_living_docs.py` SHALL implement a character-level table row tokenizer `split_markdown_table_row(row)` that splits cells by `|` while strictly ignoring escaped pipes (`\|`) and any pipe characters residing inside inline code spans delimited by single or multiple backticks (`` `...` ``, ```` ``...`` ````).
* **REQ-PRO-065.2 (Column Count Normalization)**:
  `parse_requirements_file()` and `parse_tests_file()` SHALL correctly parse both 6-column (`ID`, `Description`, `Rationale`, `Implementation`, `Tests`, `Status`) and 7-column (`ID`, `Title`, `Description`, `Rationale`, `Implementation`, `Tests`, `Status`) rows without index shifting.
* **REQ-PRO-065.3 (Master Index Overwrite Safety Guard)**:
  `tools/shard_living_docs.py` SHALL inspect source files before sharding. If `docs/requirements.md` or `docs/tests.md` is already a Master Index (e.g. contains `(Master Index)` or contains 0 requirement/test table rows), the tool SHALL abort with exit code 1 and an error message unless `--force` is passed.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Code-Span Pipe Immunity)**:
  * *Given* a table row containing backticks with `||` (e.g. `binary \`code == 200 || code == 201\` check`),
  * *When* `split_markdown_table_row()` executes,
  * *Then* the pipe characters inside backticks SHALL NOT split the cell, and the resulting column count matches the true schema without shifting.
* **Criterion 2 (Master Index Safety Guard)**:
  * *Given* `docs/requirements.md` is a Master Index,
  * *When* `tools/shard_living_docs.py` executes without `--force`,
  * *Then* it SHALL abort with exit code 1 and prevent overwriting of domain shards.
* **Criterion 3 (Pre-Fix Reproduction & Regression)**:
  * *Given* regression test reproducing pre-fix `row.split("|")` behavior on the REQ-EXP-021 snippet,
  * *When* executed on old logic,
  * *Then* it fails (proving reproduction), and passes on `split_markdown_table_row()`.

### 1.4 System Invariants
1. Sharding parser preserves cell contents byte-identically without column shifting.
2. Zero modifications to Android runtime code in `app/src/main/`.
3. Living documentation governance checks (`tools/verify_requirement_governance.py`) pass with exit code 0.

---

## 2. Test Specification (TST-PRO-065)

### Test Case 1: `test_pipe_in_code_span_splitting_and_reproduction` (`TST-PRO-065.1`)
* **Scope**: Automated Unit & Regression Test (`tools/test_shard_living_docs.py`)
* **Target**: `split_markdown_table_row` in `tools/shard_living_docs.py`
* **Preconditions**: Synthetic markdown table rows containing single backticks, double backticks, escaped pipes, and the authentic REQ-EXP-021 row with `binary \`code == 200 || code == 201\` check`.
* **Action**: Execute `split_markdown_table_row()` and compare against pre-fix naive `row.split("|")[1:-1]`.
* **Expected Result**: Naive split yields 9 parts and shifts `Tests`; `split_markdown_table_row()` yields exactly 7 parts with intact `Tests = \`TST-EXP-018\``.

### Test Case 2: `test_parse_requirements_file_6_and_7_columns` (`TST-PRO-065.2`)
* **Scope**: Automated Unit Test (`tools/test_shard_living_docs.py`)
* **Target**: `parse_requirements_file` in `tools/shard_living_docs.py`
* **Preconditions**: Synthetic markdown file with 6-column rows and 7-column rows.
* **Action**: Parse with `parse_requirements_file()`.
* **Expected Result**: Both rows produce valid requirement dicts with correct `id`, `title`, `description`, `rationale`, `implementation`, `tests`, and `status`.

### Test Case 3: `test_master_index_overwrite_guard` (`TST-PRO-065.3`)
* **Scope**: Integration CLI Test (`tools/test_shard_living_docs.py`)
* **Target**: `main()` in `tools/shard_living_docs.py`
* **Preconditions**: A temporary directory containing a Master Index `docs/requirements.md` and pre-existing domain shards.
* **Action**: Run `tools/shard_living_docs.py` without `--force`, then with `--force`.
* **Expected Result**: Without `--force`, exits with code 1 and leaves shards untouched; with `--force`, allows execution.

### Test Case 4: `test_verify_existing_shards_parity` (`TST-PRO-065.4`)
* **Scope**: Governance Integration Test (`tools/test_shard_living_docs.py`)
* **Target**: `tools/shard_living_docs.py --verify-only`
* **Preconditions**: Current repository workspace with 9 domain shards.
* **Action**: Run `python3 tools/shard_living_docs.py --verify-only`.
* **Expected Result**: Returns exit code 0 (`PASS: All 18 domain shards exist.`).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-065.1` | Unit / Regression | `split_markdown_table_row` | `REQ-PRO-065.1` | Specified |
| `TST-PRO-065.2` | Unit | `parse_requirements_file`, `parse_tests_file` | `REQ-PRO-065.2` | Specified |
| `TST-PRO-065.3` | CLI Integration | `main` safety guard | `REQ-PRO-065.3` | Specified |
| `TST-PRO-065.4` | Verification | `verify_sharding_parity`, `--verify-only` | `REQ-PRO-028`, `REQ-PRO-065` | Specified |
