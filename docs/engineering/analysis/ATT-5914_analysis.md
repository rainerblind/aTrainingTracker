# Stage 1 Analysis: ATT-5914 - Parallel Sprint Execution (2 Disjoint Epics): Use 'Workspace: share' worktrees, conflict-free strings/docs & flock build/merge serialization

**Ticket**: [ATT-5914](https://rainerblind.atlassian.net/browse/ATT-5914)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5914`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
Phase 2 sprint execution is currently strictly sequential: the Coordinator dispatches one ticket at a time. However, tickets from disjoint domain Epics (e.g. Cockpit vs. Import/Backup or Workouts vs. KnownLocations) touch isolated code packages and distinct living-doc shards. Running two tickets from disjoint Epics concurrently is blocked by four shared-resource bottlenecks:
1. **Shared Git Working Tree**: Subagents using `Workspace: "inherit"` share a single working directory and `HEAD`; concurrent checkouts collide.
2. **`strings.xml` Append Collisions**: Appending new string keys at EOF before `</resources>` across 9 locales produces git merge conflicts.
3. **Living Docs Master Index Tables**: Concurrent counter recalculations in `docs/requirements.md` and `docs/tests.md` conflict.
4. **Gradle Daemon Memory & Merge Contention**: Concurrent Robolectric unit test runs risk RAM exhaustion/worker collision, and concurrent squash-merges onto `sprint/<SPRINT_NAME>` race.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Architectural Gap**: Governance (`aspice_governance.md` Rule 7, Rule 65) mandates single-ticket dispatch. Tooling lacks worktree orchestration and mutual exclusion primitives for tests and branch merges.
* **Independent Peer Review & Verification**:
  - `Workspace: "share"`: Backed by Git worktrees sharing local `.git` storage, giving each subagent pair an independent working directory and `HEAD` without storage duplication.
  - Build & Merge Serialization: POSIX `flock` on `/tmp/att_build_merge.lock` provides robust, zero-dependency serialization for heavy `./gradlew` test runs and `sprint/<SPRINT_NAME>` squash-merges. Stages 1–3 and Stage 4 code authoring run 100% concurrently.
  - String Resource Partitioning: AGP merges all `res/values*/strings*.xml` into `R.string`. Adding support for domain-scoped string files (`strings_<domain>.xml`) and updating `TranslationParityTest.kt` to dynamically discover all string XML files eliminates EOF collisions.
  - Deterministic Living Docs Sync: Deferring `recalculate_master_index_tables()` (`python3 tools/jira_util.py recalc-docs`) to Stage 5 under the merge lock after rebasing onto `sprint/<SPRINT_NAME>` avoids shard index merge conflicts.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  - Update `.agents/rules/aspice_governance.md` and skills (`jira-workflow`, `stage4-implementation`, `stage5-verification`, `sprint-planner`) to define the 2-ticket parallel dispatch protocol across disjoint Epics using `Workspace: "share"`.
  - Implement `flock` serialization tooling/wrappers in `tools/` for `./gradlew` test suites and Stage 5 `sprint/<SPRINT_NAME>` squash-merges.
  - Update `TranslationParityTest.kt` to dynamically discover domain string files (`strings_*.xml`), enabling conflict-free localization partitioning.
  - Enforce Stage 5 rebase-then-recalculate living-doc sequence under the merge lock.
  - Maintain strict synchronization between root tooling and `tools/hoechstmass/` (Rule 36 DRY).
* **Out-of-Scope Non-Goals**: Permitting >2 concurrent tickets, parallelizing tickets within the same Epic, or modifying Android runtime code in `app/src/main/java/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Extends `REQ-PRO-014` (*ASPICE Dual-Agent Quality Gates*), `REQ-PRO-015` (*Branching & Commits*), `REQ-PRO-024` (*Modular Skills*), and `REQ-PRO-025` (*Test Parallelism & Acceleration*). Defines `REQ-PRO-041`.
* **Historical Trace**: `ATT-3323` (ASPICE Dual-Agent Lifecycle), `ATT-4520` (Persistent Epic Pairs), `ATT-4776` (Robolectric `--max-workers=2` limit), `ATT-5431` (Sharded living docs).
* **Root Reason for Existing Formulation**: Strict sequential execution was established when all subagents ran under `Workspace: "inherit"`, sharing a single `HEAD` and worktree.
* **Preservation of Core Invariants**: Dual-agent 5-stage quality gates, one atomic squash commit per ticket, and human review gates remain inviolable.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Strict disjoint Epic requirement (zero package/shard overlap); heavy tests and merges serialized via `flock`; dual-agent independent audit preserved on every stage; zero Android production runtime logic changes.
* **Risk Rating**: **LOW** (Build/merge process tooling, test suite dynamic discovery, and governance documentation).
