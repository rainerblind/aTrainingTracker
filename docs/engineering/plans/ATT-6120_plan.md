# Stage 3: Implementation Plan - ATT-6120: Enforce Unique REQ and TST IDs in Governance Verifier

**Ticket**: [ATT-6120](https://rainerblind.atlassian.net/browse/ATT-6120)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-066` (`REQ-PRO-066.1`, `REQ-PRO-066.2`, `REQ-PRO-066.3`)  
**Test Mapping**: `TST-PRO-066` (`TST-PRO-066.1`, `TST-PRO-066.2`, `TST-PRO-066.3`, `TST-PRO-066.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE 4.0 assessment identified 11 duplicate requirement/test heading IDs across domain shards (`docs/requirements/*.md` and `docs/tests/*.md`). `verify_requirement_governance.py` previously counted headings for table parity without verifying ID uniqueness. This plan defines the implementation of duplicate heading detection, detailed file/line diagnostic reporting, legacy baseline allowlisting to maintain CI green status until ATT-6123, and a strict `--enforce-unique` / `--fail-on-duplicates` CLI flag.

---

## 2. Traceability & Requirements Mapping
* **Requirements**:
  * `REQ-PRO-066.1`: Duplicate heading ID detection and file/line reporting.
  * `REQ-PRO-066.2`: Legacy duplicate baseline allowlist (`LEGACY_DUPLICATE_IDS`) and CI non-regression.
  * `REQ-PRO-066.3`: Strict uniqueness enforcement flag (`--enforce-unique` / `--fail-on-duplicates`).
* **Test Mapping**:
  * `TST-PRO-066.1`: Synthetic shard duplicate detection with exact file and line assertions.
  * `TST-PRO-066.2`: Legacy baseline warning tolerance and novel duplicate rejection.
  * `TST-PRO-066.3`: Strict enforcement mode flag verification.
  * `TST-PRO-066.4`: Live repository living documentation clean verification.

---

## 3. System Invariants & Preserved Behavior
1. **Pure Python 3 Standard Library**: No third-party pip dependencies; uses only `re`, `os`, `sys`, `subprocess`, `argparse`.
2. **Deterministic Exit Codes**: 0 = PASS, 1 = FAIL.
3. **CI Non-Regression**: Pre-existing 11 legacy duplicates emit warnings in default mode so concurrent sprint lanes remain unblocked until ATT-6123.
4. **Tooling Contract Parity**: Existing checks (`verify_master_table_parity`, `verify_composable_islands`, `verify_repository_encapsulation`, `verify_architecture_and_system_specs`, archaeology) remain untouched.
5. **Code Lock**: Code modifications locked until Gate 3 audit passes (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **Constants**:
  * Define `LEGACY_DUPLICATE_IDS = frozenset({"REQ-DAT-001", "REQ-DAT-026", "REQ-MIG-018", "REQ-UI-116", "REQ-UI-123", "REQ-UI-257", "TST-DAT-001", "TST-DAT-021", "TST-MAP-055", "TST-MIG-036", "TST-UI-216"})`.
* **Functions**:
  * `verify_unique_ids(repo_root=None, fail_on_duplicates=False, legacy_allowlist=None)`: Scans all `.md` files under `docs/requirements/` and `docs/tests/`. Extracts heading IDs and their `(file, line)` occurrences. Categorizes duplicate IDs into legacy vs. novel. Formats error/warning messages. Returns `(success: bool, diagnostics: list[str], duplicates: dict[str, list[tuple[str, int]]])`.
* **Verifier Integration**:
  * Call `verify_unique_ids` from `verify_governance()`.
  * Add `--enforce-unique` / `--fail-on-duplicates` arguments to CLI `argparse`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `verify_unique_ids` & Constants in `tools/verify_requirement_governance.py`
* Add `LEGACY_DUPLICATE_IDS` constant.
* Implement `verify_unique_ids()` with file/line reporting and baseline filtering.
* Wire into `verify_governance()` and CLI parser (`--enforce-unique`, `--fail-on-duplicates`, `--check-unique-ids`).

### Step 2: Implement Unit Tests in `tools/test_verify_requirement_governance.py`
* Add test class or methods covering:
  * Detection of duplicate IDs with exact file and line assertions.
  * Allowance of baseline legacy duplicates with warning diagnostics.
  * Immediate rejection of novel duplicates.
  * Failure under `fail_on_duplicates=True`.
  * Authentic repository clean pass.

### Step 3: Verification & Execution
* Run unit test suite: `python3 tools/parallel_sprint_lock.py python3 -m unittest tools/test_verify_requirement_governance.py`.
* Run root verifier: `python3 tools/verify_requirement_governance.py`.
* Run strict verifier to assert detection of all 11 legacy duplicates: `python3 tools/verify_requirement_governance.py --enforce-unique` (verifying exit code 1 and all 11 IDs reported).

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 -m unittest tools/test_verify_requirement_governance.py` + `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest"`.
* **Rollback**: Clean worktree isolation allows immediate checkout/reset without impacting main repository or sibling worktrees.
