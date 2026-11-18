# Stage 2: Requirement & Test Specification - ATT-8207: Preserve Single-Atomic-Commit Invariant Across Stage 6 Cold Review Bounces

**Document ID**: `DOC-ENG-TESTSPEC-ATT-8207`  
**Version**: `1.0.0`  
**Status**: `Draft`  
**Approver**: `Pending PO Sign-Off`  
**Ticket**: [ATT-8207](https://rainerblind.atlassian.net/browse/ATT-8207) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8207`  
**Requirement Mapping**: `REQ-PRO-125` | **Test Spec ID**: `TST-PRO-125` | **Author**: AI Agent 1 (`flash`) | **Date**: `2026-11-18`

---

## 1. Requirement Specification (`REQ-PRO-125`)
* **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit / Review

### 1.1 Functional & Architectural Requirements
1. **Deferred Post-Stage-6 Atomic Sprint Integration (`REQ-PRO-125.1`)**: Gate 5 `PASS` SHALL keep `feature/<KEY>` intact without executing `--merge-ticket` or `git merge --squash` onto `sprint/<SPRINT_NAME>`, and SHALL advance the parent ticket to `Final Review (Agent)`. `tools/parallel_sprint_lock.py --merge-ticket` (and `tools/locked_build_merge.py`) SHALL squash-merge `feature/<KEY>` onto `sprint/<SPRINT_NAME>` as strictly one atomic commit (with `REQ-PRO-108.3` trailers and `--sync-indexes`) and delete `feature/<KEY>` ONLY after Stage 6 `final-review-verdict PASS`, preventing bounce pollution and unapproved `Schicht` rollover leaks (`Rule 2.2`, `Rule 8.2`).
2. **Zero-Build / Zero-Lock Stage 6 Branch-Diff Audit (`REQ-PRO-125.2`, `Rule 3.3`)**: During Stage 6 (`Final Review (Agent)`), the Cold Reviewer SHALL inspect the ticket strictly read-only via `git diff sprint/<SPRINT_NAME>...feature/<KEY>` (and surrounding source files) with **zero `./gradlew` invocations and zero `/tmp/att_build_merge.lock` acquisitions** during review. Post-`PASS` `--merge-ticket` under POSIX `flock` SHALL cleanly rebase `feature/<KEY>` onto current `sprint/<SPRINT_NAME>` and reconcile shared `docs/requirements.md` / `docs/tests.md` counters.
3. **Subtask Reuse & Reopening on Stage 6 `BOUNCE` (`REQ-PRO-125.3`)**: When `tools/jira_util.py final-review-verdict <PARENT_KEY> BOUNCE ...` returns a ticket to `Implementation` (or `Analysis` / `Zu erledigen` with `bounce_count <= 3`), it SHALL reopen existing `Erledigt`/`Done` subtasks matching the target and downstream stages (e.g. `[Implementation]` and `[Test]` for `implementation`) so `submit-stage` reuses existing subtasks instead of duplicating `[Implementation]`/`[Test]` pairs and preserves `REQ-PRO-107.1` separated-authorship validation.
4. **`tools/hoechstmass/` Mirror Parity & Governance Alignment (`REQ-PRO-125.4`)**: `tools/hoechstmass/tools/{jira_util.py,parallel_sprint_lock.py}` and `tools/hoechstmass/.agents/{rules,skills}/` SHALL mirror post-Stage-6 integration and `BOUNCE` subtask reuse without hardcoding `ATT` or proprietary IDs (`HoechstmassToolingContractTest`), and `.agents/rules/aspice_governance.md`, `.agents/skills/{stage5-verification,stage6-final-review,sprint-planner}/SKILL.md`, and `docs/management/cm_plan.md` SHALL reflect the unified workflow.

### 1.2 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* a ticket passing Gate 5 (`gate-verdict <TEST_SUBTASK> PASS`), *When* it enters `Final Review (Agent)`, *Then* `feature/<KEY>` remains intact, `sprint/<SPRINT_NAME>` has zero commits for `<KEY>`, and Stage 6 audits `git diff sprint/<SPRINT_NAME>...feature/<KEY>` strictly read-only without `./gradlew` or `flock`.
* **Criterion 2**: *Given* a ticket bouncing 1–3 times in Stage 6 before `PASS` + `--merge-ticket`, *When* it reaches `Final Review (Human)`, *Then* existing `[Implementation]`/`[Test]` subtasks were reused without duplication and `git log sprint/<SPRINT_NAME>` has strictly one atomic commit for `<KEY>`.
* **Criterion 3**: *Given* `sprint/<SPRINT_NAME>` advanced with sibling index updates while `<KEY>` waited in Stage 6, *When* post-`PASS` `--merge-ticket` executes under `flock`, *Then* `<KEY>` rebases cleanly, `--sync-indexes` reconciles master counters, and `feature/<KEY>` is deleted.

### 1.3 System Invariants & Localization
* Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `tools/sprint_bisect.py` algorithm unchanged; `Rule 3.1` human-only `Erledigt`, `Rule 3.3` zero-build/zero-lock Stage 6 audit, `REQ-PRO-107.1` authorship separation, and `Hoechstmass` decoupling preserved.
* **Localization Parity**: `N/A - no UI strings` (process/tooling ticket; zero `values*/strings*.xml` edits).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-058` (`REQ-PRO-058.1`–`.3`), `REQ-PRO-041` (`.2`, `.4`), `REQ-PRO-109` (`.2`, `.5`); refined by net-new `REQ-PRO-125`.
* **Historical Origin & Commit Trace**: `ATT-7101` (`REQ-PRO-058`), `ATT-7025` (`49936dce`, `--merge-ticket`), `ATT-8030` (Zero-Build/Zero-Lock Stage 6), `ATT-8154` (`REQ-PRO-109`), `ATT-8163` (`cm_plan.md`).
* **Root Reason for Existing Formulation**: Stage 6 was appended onto an existing Gate 5 squash-merge pipeline, exposing `sprint/<SPRINT_NAME>` to intermediate bounce commits and subtask duplication.
* **Preservation of Core Invariants**: `Rule 3.1`, `Rule 3.3` (zero `./gradlew`/lock during Stage 6 review), `Rule 8.2` (one squashed commit with `REQ-PRO-108.3` trailers), `Rule 9.1`–`9.5`, and `REQ-PRO-107.1` remain intact.

---

## 3. Test Specification (`TST-PRO-125`) & Traceability Matrix

| Test Case | Scope | Target / Verification Command | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-125.1` | Unit (Temp Git Repo) | `tools/test_flock_serialization.py`: post-`PASS` `--merge-ticket`, sibling rebase, `--sync-indexes`, single atomic commit + branch deletion | `REQ-PRO-125.1`, `.2` | Planned |
| `TST-PRO-125.2` | Doc / Skill Review | Audit `aspice_governance.md`, `stage5-verification`, `stage6-final-review`, `sprint-planner`, `cm_plan.md` for `git diff sprint/...feature/` + zero-build/zero-lock Stage 6 | `REQ-PRO-125.1`, `.2` | Planned |
| `TST-PRO-125.3` | Unit (Mocked Jira, Rule 5) | `tools/test_jira_accounts.py`: `final_review_verdict BOUNCE` reopens `[Implementation]`/`[Test]`, `submit_stage` reuses without duplicates, `REQ-PRO-107.1` preserved | `REQ-PRO-125.3` | Planned |
| `TST-PRO-125.4` | Mirror & Governance | `python3 -m unittest discover -s tools -p "test_*.py"`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `verify_requirement_governance.py` | `REQ-PRO-125.4` | Planned |

---

## 4. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent 1 (`flash`) | `ATT-8207` | Initial Stage 2 specification (`REQ-PRO-125`, `TST-PRO-125`). |
