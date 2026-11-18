# Stage 1 Analysis: ATT-8876 — Cross-Directory `parallel_sprint_lock` Shadowing & Höchstmaß Trailer Parity

- **Ticket**: `ATT-8876` | **Type**: Process-Escape Bug (`1 SP`) | **Target Release**: `V5.0.0` (ID `10372`)

## 1. Problem Statement & Scope
- Tier-2 verification reported `AttributeError: module 'parallel_sprint_lock' has no attribute 'format_commit_with_trailers'` at `test_tst_pro_108_3_git_commit_trailers_formatting` (`tools/test_jira_accounts.py:2050` in `3b390f63`, now `:2215`).
- **User Scope Grounding (`ATT-1250`)**: Strictly `tools/` Python test/tooling scope and `tools/hoechstmass/tools/` mirror; zero `app/` changes, zero edits to the 4 pre-existing `tools/` files, and zero `SYS.2`/`SYS.3` impact (`REQ-PRO-131.4`: `docs/system_requirements.md` and `docs/system_architecture.md` unaffected).
- **Complexity Check**: `1 SP` — deterministic dual fix (sibling-scoped module loading + Höchstmaß mirror parity + regression tests).

## 2. Forensic Root Cause Analysis
1. **Independent Peer Review & Verification (Hypothesis Check)**:
   - *Ticket premise*: `3b390f63` (`ATT-8207`) refactored `tools/parallel_sprint_lock.py` while `tools/test_jira_accounts.py` still calls `format_commit_with_trailers`.
   - *Verified refutation*: `git show 3b390f63 -- tools/parallel_sprint_lock.py` and current `tools/parallel_sprint_lock.py:190-223` confirm `format_commit_with_trailers` and `validate_commit_trailers` (added in `f0dfe76d` / `ATT-8151`) were **never removed**, and `test_tst_pro_108_3_git_commit_trailers_formatting` passes in isolation (`1/1` in `0.001s`).
2. **True Dual Root Cause** (reproduced in `sys.modules` + `sys.path` collision test):
   - *Mirror Divergence*: `tools/hoechstmass/tools/parallel_sprint_lock.py` was synced in `3b390f63` for Stage 6 merge mechanics, yet never received `REQ-PRO-108.3` (`format_commit_with_trailers`, `validate_commit_trailers`, `--agent-role`/`--agent-model`/`--reviewed-by` in `_merge_ticket_impl`/`run_merge_ticket`/`main`).
   - *Cross-Directory `sys.modules["parallel_sprint_lock"]` Poisoning*: `tools/hoechstmass/tools/jira_util.py:22-23` prepends `tools/hoechstmass/tools` to `sys.path[0]` (left unstripped by callers such as `tools/test_jira_datacenter.py:29-32`). Whenever `parallel_sprint_lock` is cached in `sys.modules` from `tools/hoechstmass/tools/parallel_sprint_lock.py` (e.g., via `locked_build_merge.py:9` or mixed-directory execution), bare `import parallel_sprint_lock` in `tools/test_jira_accounts.py:2214` (and `tools/test_flock_serialization.py:19,323`) binds the Höchstmaß module lacking `format_commit_with_trailers`, raising `AttributeError`.

## 3. Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-PRO-108.3` / `TST-PRO-108.3` (`docs/requirements/req_process_stability_part2.md:1707-1738`, `docs/tests/tst_process_stability_part2.md:1039-1055`), targeting `tools/parallel_sprint_lock.py`, `tools/locked_build_merge.py`, and `tools/test_jira_accounts.py`.
2. **Historical Origin & Commit Trace**: Introduced in `f0dfe76d` (`ATT-8151`), touched in `3b390f63` (`ATT-8207`); matches the `sys.modules` collision class fixed for `generate_test_evidence` in `bc1a50c7` (`ATT-8875`).
3. **Root Reason for Existing Formulation**: Bare `import parallel_sprint_lock` assumed a single unshadowed `tools/` directory on `sys.path`, and `ATT-8151` did not extend trailer parity to `tools/hoechstmass/tools/parallel_sprint_lock.py`.
4. **Preservation of Core Invariants**: Preserves all `REQ-PRO-108.1`..`108.4` contracts, POSIX `flock` locking (`REQ-PRO-041.2`), `HoechstmassToolingContractTest` project-agnostic invariants (zero `ATT-`/proprietary IDs, stdlib-only), and `ANT-Android-SDKs` submodule (`160000`).

## 4. Change Impact Analysis
- **Affected Requirements**:
  - `REQ-PRO-108.3` (`docs/requirements/req_process_stability_part2.md`) and `TST-PRO-108.3` (`docs/tests/tst_process_stability_part2.md`): extend contract to sibling-scoped loading and `tools/hoechstmass/tools/parallel_sprint_lock.py` trailer parity.
  - `SYS.2` / `SYS.3` Impact (`REQ-PRO-131.4`): None (`tools/` CI/governance tooling only; no `docs/system_requirements.md` or `docs/system_architecture.md` changes).
- **Affected Architecture Elements**:
  - `tools/hoechstmass/tools/parallel_sprint_lock.py`: add project-agnostic `format_commit_with_trailers`, `validate_commit_trailers`, and trailer CLI/merge options (`--agent-role`, `--agent-model`, `--reviewed-by`).
  - `tools/test_jira_accounts.py` (`L2212-2227`) & `tools/test_flock_serialization.py`: enforce sibling-directory `parallel_sprint_lock` resolution even when `sys.modules["parallel_sprint_lock"]` is pre-populated from another directory.
- **Verification Impact & Test Surface**:
  - `tools/test_jira_accounts.py`: add cross-directory `sys.modules["parallel_sprint_lock"]` collision regression test.
  - `tools/hoechstmass/tools/test_hoechstmass_tools.py`: add Höchstmaß `format_commit_with_trailers` / `validate_commit_trailers` parity test.
  - Gates: `python3 -m unittest -v tools.test_jira_accounts tools.test_flock_serialization`, `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py" -v`, combined cross-directory run, `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest"`.
- **Risk Assessment & Invariants**:
  - Low risk (`1 SP`). Guard against duplicating non-generic defaults in `tools/hoechstmass/tools/parallel_sprint_lock.py` (keep generic `"agent1"`/`"flash"`/`"Agent 2 (Reviewer)"` to preserve `HoechstmassToolingContractTest`). Keep `test_tst_pro_108_3` behavior unchanged for clean environments.
