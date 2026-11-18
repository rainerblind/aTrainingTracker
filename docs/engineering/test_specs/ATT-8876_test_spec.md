# Stage 2: Requirement & Test Specification - ATT-8876: Fix Cross-Directory `parallel_sprint_lock` Shadowing & Höchstmaß Trailer Parity

**Ticket**: [ATT-8876](https://rainerblind.atlassian.net/browse/ATT-8876)  
**Sub-task**: [TBD](https://rainerblind.atlassian.net/browse/ATT-8876) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_02_Spät`  
**Requirement Mapping**: `REQ-PRO-108` (`REQ-PRO-108.3`)  
**Test Spec ID**: `TST-PRO-108` (`TST-PRO-108.3`)  
**Branch**: `feature/ATT-8876`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-18  

---

## 1. Requirement Specification (`REQ-PRO-108.3`)

### 1.1 Problem Statement & Rationale
- Tier-2 combined Python execution failed with `AttributeError: module 'parallel_sprint_lock' has no attribute 'format_commit_with_trailers'` at `test_tst_pro_108_3_git_commit_trailers_formatting` (`tools/test_jira_accounts.py:2215`) because: (a) `tools/hoechstmass/tools/parallel_sprint_lock.py` lacked `REQ-PRO-108.3` commit-trailer support, and (b) `tools/test_jira_accounts.py` / `tools/test_flock_serialization.py` used bare `import parallel_sprint_lock`, picking up cached `sys.modules["parallel_sprint_lock"]` or `sys.path[0]` from `tools/hoechstmass/tools/`.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **SYS.2 / SYS.3 Impact**: None (`tools/` Python tooling & `tools/hoechstmass/tools/` mirror only; zero `app/` changes).

### 1.3 Functional & Architectural Requirements
1. **Höchstmaß Commit Trailer Parity (`REQ-PRO-108.3`)**: `tools/hoechstmass/tools/parallel_sprint_lock.py` SHALL implement `format_commit_with_trailers()`, `validate_commit_trailers()`, and `_merge_ticket_impl()` / `run_merge_ticket()` / CLI `--agent-role`, `--agent-model`, `--reviewed-by` flags using generic defaults (`"agent1"`, `"flash"`, `"Agent 2 (Reviewer)"`) preserving `HoechstmassToolingContractTest`.
2. **Cross-Directory `parallel_sprint_lock` Isolation (`REQ-PRO-108.3`)**: `tools/test_jira_accounts.py` (`test_tst_pro_108_3_git_commit_trailers_formatting`) and `tools/test_flock_serialization.py` SHALL resolve sibling `tools/parallel_sprint_lock.py` deterministically via path-checked `importlib.util.spec_from_file_location`, ignoring poisoned `sys.modules["parallel_sprint_lock"]` / shadowed `sys.path` entries.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Poisoned `sys.modules` Isolation)**:
  * *Given* `sys.modules["parallel_sprint_lock"]` pre-populated from `tools/hoechstmass/tools/` (or an unrelated module lacking `format_commit_with_trailers`) and `tools/hoechstmass/tools` at `sys.path[0]`,
  * *When* `test_tst_pro_108_3_git_commit_trailers_formatting` (or `test_flock_serialization.py`) executes,
  * *Then* sibling `tools/parallel_sprint_lock.py` is loaded cleanly and trailer formatting/validation passes with exit code `0`.
* **Criterion 2 (Höchstmaß Parity & Project-Agnostic Contract)**:
  * *Given* `tools/hoechstmass/tools/parallel_sprint_lock.py`,
  * *When* `format_commit_with_trailers` / `validate_commit_trailers` and `HoechstmassToolingContractTest` execute,
  * *Then* `Agent-Role:`, `Agent-Model:`, and `Reviewed-by:` trailers are appended/validated and zero `ATT-`/proprietary strings exist in `tools/hoechstmass/`.

### 1.5 System Invariants
- Zero Android `app/` runtime changes; 4 pre-existing `tools/` files untouched; POSIX `flock` (`REQ-PRO-041.2`) and `HoechstmassToolingContractTest` contracts preserved. `Localization Parity: N/A` (Python CLI/test tooling only, zero `strings.xml` entries).

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-PRO-108` (`REQ-PRO-108.3`); targets `tools/parallel_sprint_lock.py`, `tools/hoechstmass/tools/parallel_sprint_lock.py`, `tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
2. **Historical Origin & Commit Trace**: Introduced in `f0dfe76d` (`ATT-8151`), touched in `3b390f63` (`ATT-8207`); matches `ATT-8875` (`bc1a50c7`) cross-directory `sys.modules` shadowing pattern; refined under `ATT-8876`.
3. **Root Reason for Existing Formulation**: `ATT-8151` implemented commit trailers only in root `tools/parallel_sprint_lock.py`, while root tests used bare `import parallel_sprint_lock` assuming unshadowed `sys.path`/`sys.modules`.
4. **Preservation of Core Invariants**: Standalone CLI behavior, POSIX `flock`, `HoechstmassToolingContractTest` project-agnostic rules, `app/` zero-diff, and `ANT-Android-SDKs` (`160000`) remain intact.

---

## 2. Test Specification (`TST-PRO-108.3`)

### Test Case 1: Cross-Directory `sys.modules["parallel_sprint_lock"]` Isolation (`TST-PRO-108.3`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`)
* **Target**: `TestSeparatedAuthorshipAndGateEvidence.test_tst_pro_108_3_isolates_cross_directory_sys_modules_cache`
* **Preconditions**: Pre-populate `sys.modules["parallel_sprint_lock"]` with a stub lacking `format_commit_with_trailers` and prepend unrelated directory to `sys.path[0]`.
* **Action**: Execute `test_tst_pro_108_3_git_commit_trailers_formatting` and restore `sys.modules`/`sys.path`.
* **Expected Result**: Resolves sibling `tools/parallel_sprint_lock.py`, formats/validates trailers, passes with zero `AttributeError`.

### Test Case 2: Höchstmaß Commit Trailer Parity (`TST-PRO-108.3`)
* **Scope**: Unit Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py`) + Contract Test (`HoechstmassToolingContractTest`)
* **Preconditions**: Import `format_commit_with_trailers` and `validate_commit_trailers` from `tools.hoechstmass.tools.parallel_sprint_lock`.
* **Action**: Call `format_commit_with_trailers` with explicit/default roles and run `validate_commit_trailers`, plus `./gradlew :app:testDebugUnitTest --tests "...HoechstmassToolingContractTest"`.
* **Expected Result**: Valid `Agent-Role:`, `Agent-Model:`, `Reviewed-by:` trailers; all Höchstmaß and contract tests pass.

### Test Case 3: Localization Parity (`N/A`) & Tier 1 Targeted Regression
* **Localization Parity**: `N/A` (zero UI strings).
* **Command**: `python3 -m unittest -v tools.test_jira_accounts tools.test_flock_serialization`, `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py" -v`, combined cross-directory run, `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Target | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-108.3` | Unit | `test_tst_pro_108_3_isolates_cross_directory_sys_modules_cache` | `REQ-PRO-108.3` | Planned |
| `TST-PRO-108.3` | Unit / Contract | `test_hoechstmass_parallel_sprint_lock_commit_trailers` & `HoechstmassToolingContractTest` | `REQ-PRO-108.3` | Planned |
| Localization | N/A | `N/A (Python tooling only)` | `REQ-PRO-108.3` | N/A |
