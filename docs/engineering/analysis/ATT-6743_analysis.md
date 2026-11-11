# Stage 1 Analysis: ATT-6743 - Enforce Rule 81 via Deterministic Python Tripwires & Sync REQ-PRO-041.1 Concurrency=3

**Ticket**: [ATT-6743](https://rainerblind.atlassian.net/browse/ATT-6743)  
**Sub-task**: [ATT-6782](https://rainerblind.atlassian.net/browse/ATT-6782) (`[Analysis]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6743` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During Sprint `2026-46.1` (`66` tickets, `146.0 SP`), Stage Gates 1–5 recorded a 100.0% First-Time-Pass-Rate (0 revisions across 330 sub-tasks), yet Ceremony 2 Joint Reviews uncovered 62 follow-up tickets (`ATT-6680`–`ATT-6741`).
Approximately 35–40% of those escaped defects were mechanical integration and documentation violations that prose rules (`Rule 81`) alone cannot prevent under multi-ticket subagent context load:
1. **Unmounted `@Composable` Island Components**: Production composables added in `app/src/main/` with callers only in `@Preview` functions or tests (`ATT-6702`, `ATT-6711`, `ATT-6712`).
2. **Repository Backdoor Persistence Getters**: Non-private getters exposing `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, or `Cursor` on repositories (`ATT-6696`, `ATT-6698`).
3. **Ungrounded Architecture & System Spec Symbols & `SYSREQ` Mismatches**: Non-existent backticked source files in architecture/system documentation, and `SYSREQ-*` set mismatches between `sysreq_*.md` and `system_architecture.md` (`ATT-6727`, `ATT-6738`–`ATT-6741`).
Furthermore, living requirement `REQ-PRO-041.1` in `docs/requirements/req_process_stability.md` remains documented at `concurrency = 2`, whereas PO calibration in Sprint 2026-46.1 established `concurrency = 3` across disjoint Epics with POSIX `flock` serialization (`REQ-PRO-041.2`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: LLM subagents reliably obey hard tool-enforced gates (e.g. `verify_requirement_governance.py` schema checks, `TranslationParityTest`), but prose guidelines in skills/rules suffer attention decay under heavy multi-ticket context loads. Without deterministic tripwires exiting with code 1 on git diffs, unmounted composables and repository leaks escape through green unit tests.
* **Independent Peer Review & Verification**:
  * Codebase inspection confirms `tools/verify_requirement_governance.py` already successfully enforces Chesterton's Fence archaeology, canonical engineering folders, and 100% master index parity.
  * Audit of `@Composable` declarations confirms AST/regex parsing of added diff lines can detect newly added non-`@Preview`, non-`private` composables and verify at least one non-preview call site in `app/src/main/` in <0.5s.
  * Audit of repositories (`*Repository.kt` / `*Repository.java`) confirms non-private methods/properties returning or exposing `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, or `Cursor` can be deterministically caught on modified diff lines.
  * Audit of `docs/system/` confirms historical symbol rot (`sysreq_data_exchange.md` referencing deleted `WorkoutDataHelper.java`, 14 declared vs 25 allocated SYSREQs). Restricting checks to modified files in the branch diff prevents breaking on un-migrated baseline files while enforcing zero tolerance on new edits.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Implement 3 hard deterministic tripwires in `tools/verify_requirement_governance.py`:
    1. `verify_composable_islands`: Detect new non-preview, non-private `@Composable` functions in modified `app/src/main/**/*.kt` without production callers.
    2. `verify_repository_encapsulation`: Reject new non-private methods/properties exposing database managers, helpers, databases, or cursors in modified `*Repository` files.
    3. `verify_architecture_and_system_specs`: When architecture/system docs are modified, enforce that backticked source files exist and `SYSREQ-*` IDs satisfy bidirectional set equality (`declared == allocated`).
  * Wire tripwires into `verify_governance()` and CLI runner exiting with code 1 on violations.
  * Synchronize `REQ-PRO-041.1` (`concurrency = 3`) and `REQ-PRO-041.2` (`flock` mutual exclusion for tests, `installDebug` + `adb`, and squash-merges) in `docs/requirements/req_process_stability.md`.
  * Add unit tests in `tools/test_verify_requirement_governance.py` covering passing and failing cases for all 3 tripwires.
* **Out-of-Scope Non-Goals**:
  * Fixing pre-existing ungrounded references in `docs/system/` reserved for `ATT-6727`, `ATT-6732`, `ATT-6738`–`ATT-6741`.
  * Altering Android runtime code in `app/src/main/`.
  * Introducing external pip dependencies (strictly Python 3 standard library).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-041` (*Parallel Sprint Execution Protocol for Disjoint Epics*), specifically `REQ-PRO-041.1` (*Subagent Worktree Isolation*) and `REQ-PRO-041.2` (*POSIX File Lock Mutual Exclusion*).
* **Historical Origin & Commit Trace**: Commit `d002a9bd` (`ATT-5914`, Sprint 2026-45.2); calibrated during Sprint 2026-46.1 retrospective (`ATT-6289` / `Rule 81`).
* **Root Reason for Existing Formulation**: Concurrency was conservatively capped at 2 to validate 'Workspace: share' worktree isolation and prevent host resource exhaustion during the initial parallel sprint rollout.
* **Preservation of Core Invariants**: Bumping concurrency to 3 across strictly disjoint Parent Epics preserves worktree isolation, POSIX `flock` mutual exclusion on `./gradlew` and `adb`, zero cross-epic file collisions, and 100% human gate governance.

---

## 5. Architectural Strategy & High-Level Solution
* **Tripwire 1 (`verify_composable_islands`)**: Parses diff hunks for added non-preview `@Composable fun <Name>(`. Greps `app/src/main/` for `<Name>(` call sites outside `@Preview` functions and outside the definition.
* **Tripwire 2 (`verify_repository_encapsulation`)**: Scans added/modified lines in `*Repository.kt`/`*Repository.java` for public/internal signatures returning or exposing `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, or `Cursor`.
* **Tripwire 3 (`verify_architecture_and_system_specs`)**: When diff touches `docs/architecture/` or `docs/system/`, verifies backticked `*.{kt,java,xml}` files exist in repo, and compares `sysreq_*.md` declared IDs vs `system_architecture.md` allocated IDs.
* **Living Documentation Sync**: Update `REQ-PRO-041.1` to `concurrency = 3` and align `REQ-PRO-041.2` flock lock descriptions in `docs/requirements/req_process_stability.md`. Run `tools/verify_requirement_governance.py --sync-indexes`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% Python 3 standard library footprint (no external pip dependencies).
  2. Deterministic exit codes: 0 = compliant, 1 = failure with clear actionable diagnostic messages.
  3. Clean working tree against `sprint/2026-46.2` passes all checks cleanly with exit code 0.
  4. Inviolable ASPICE Human Decision Gate (`REQ-PRO-016`).
* **Risk Rating**: **LOW** (Pure developer tooling and living documentation synchronization; zero risk to Android runtime; strictly bounded at 2 SP).
