# Stage 3: Implementation Plan - ATT-6124: Fix shard_living_docs.py corruption of Tests fields

**Ticket**: [ATT-6124](https://rainerblind.atlassian.net/browse/ATT-6124)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-065` (*Living Documentation Sharding Parser Code-Span Pipe Immunity & Master Index Safety Guard*)  
**Test Mapping**: `TST-PRO-065` (*Living Documentation Sharding Code-Span Pipe Splitting & Master Index Safety Guard Verification Suite*)  
**Branch**: `subagent-Lane-3-Agent---ATT-6124-self-2a46d42d`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
In `tools/shard_living_docs.py`, parsing monolithic living documentation using naive `row.split("|")` resulted in column shifting whenever cells contained inline backticks with pipes or `||` (such as `code == 200 || code == 201`), corrupting the `Tests` field with truncated fragments (e.g. `REQ-EXP-021`). In addition, executing the tool against an already-sharded repository wiped the 9 domain shards because `docs/requirements.md` was already a Master Index containing no table rows.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-065` (`REQ-PRO-065.1` Code-Span Pipe Immunity, `REQ-PRO-065.2` Column Normalization, `REQ-PRO-065.3` Master Index Safety Guard)
* **Test Mapping**: `TST-PRO-065` (`TST-PRO-065.1` Unit & Regression, `TST-PRO-065.2` Column Normalization, `TST-PRO-065.3` Overwrite Guard, `TST-PRO-065.4` Shard Parity)

---

## 3. System Invariants & Preserved Behavior
1. **Preservation of Table Content**: Sharding parser preserves all cell contents byte-identically without column shifting or truncation.
2. **Master Index Safety**: Living documentation domain shards are never overwritten when executed against a master index without `--force`.
3. **Pure Standard Library**: Zero external pip dependencies; uses pure Python 3 standard library.
4. **Zero Production Runtime Impact**: No UI, database, or Android runtime code in `app/src/main/` is touched.
5. **Governance Compliance**: `python3 tools/verify_requirement_governance.py` passes cleanly (exit code 0).

---

## 4. Proposed Architectural Changes

### Component 1: `tools/shard_living_docs.py`
* Implement `split_markdown_table_row(row: str) -> list[str]` character tokenizer tracking backtick runs (`` `...` ``, ```` ``...`` ````) and escaped backslashes (`\`).
* Refactor `parse_requirements_file` and `parse_tests_file` to use `split_markdown_table_row`, handling both 6-column and 7-column table layouts.
* Add safety guard in `main()` inspecting `docs/requirements.md` and `docs/tests.md` for `(Master Index)` or empty row sets, adding `--force` CLI flag to prevent accidental overwrite.

### Component 2: `tools/test_shard_living_docs.py`
* New unit and regression test suite using `unittest` verifying:
  - Pipe-in-code-span tokenizer behavior.
  - Exact reproduction of the pre-fix bug on `REQ-EXP-021` snippet.
  - Parsing of both 6-column and 7-column requirements and tests.
  - Abort behavior when source is a Master Index, and `--force` bypass.
  - Shard parity check verification (`--verify-only`).

### UI Consistency
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Tokenizer & Parser Normalization in `tools/shard_living_docs.py`
* Target: `tools/shard_living_docs.py`
* Add `split_markdown_table_row(row)` tokenizer.
* Update `parse_requirements_file` and `parse_tests_file` to support both 6-column and 7-column layouts using the tokenizer.

### Step 2: Implement Master Index Safety Guard in `tools/shard_living_docs.py`
* Target: `tools/shard_living_docs.py`
* Update `main()` with `--force` flag.
* Check if `docs/requirements.md` or `docs/tests.md` contains `(Master Index)` or contains zero `| **REQ-` / `| **TST-` rows. Abort with exit code 1 if true and `--force` is absent.

### Step 3: Author Automated Test Suite `tools/test_shard_living_docs.py`
* Target: `tools/test_shard_living_docs.py`
* Write unit and regression tests reproducing the failure on old logic and asserting success on new logic.

### Step 4: Verification & Governance Run
* Commands:
  - `python3 -m unittest tools/test_shard_living_docs.py`
  - `python3 tools/shard_living_docs.py --verify-only`
  - `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Run `tools/test_shard_living_docs.py` unit suite, `--verify-only`, and `verify_requirement_governance.py`.
* **Rollback**: Git branch isolation on `subagent-Lane-3-Agent---ATT-6124-self-2a46d42d` allows reverting commit without impacting active sprint.
