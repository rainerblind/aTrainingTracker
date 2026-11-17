# Stage 1 Analysis: ATT-8163 - Immutable Annotated Baselines, Content Manifest & CM Plan Alignment

**Ticket**: [ATT-8163](https://rainerblind.atlassian.net/browse/ATT-8163) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_02_Spät` | **Branch**: `feature/ATT-8163`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit Finding `M11 / SUP.8` (`SUP.8 BP5/BP6/BP7`, `GP 2.2.3`) identified four configuration-management integrity gaps:
1. Sprint closure scripts overwrite sprint tags with `git tag -f` (lightweight, mutable) instead of refusing overwrites and creating annotated tags.
2. No baseline content manifest (`docs/releases/<baseline>/manifest.md`) links each sprint/release baseline to its commit SHA, REQ/TST counts, test evidence, open risks/bugs, and governance status.
3. `docs/management/cm_plan.md` contradicts actual `--no-ff` release/sprint merges (`ff-only` claim), omits `gates/`, `qa_audits/`, `reviews/`, and `releases/` from the CI inventory, lacks Configuration Status Accounting (`SUP.8.BP7`), and has no revision history.
4. Legacy merged/stale local and remote branches remain without a safe cleanup policy or PO-gated cleanup script.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Tagging scripts**: Ticket cites `sprint-planner L231`; live `.agents/skills/sprint-planner/SKILL.md` L268 (and mirror `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md` L224) runs `git tag -f sprint/<SPRINT_NAME>`. `.agents/skills/release-manager/SKILL.md` L280 (`git flow release finish`) and L292 (`git tag -a "V${VERSION_NAME}"`) lack explicit pre-checks refusing existing tags.
  * **Live tag & ref audit (`b1329131`)**: Ticket claims "All 65 sprint tags and 12 release tags are lightweight" and "`sprint/2026-46.6` exists both as tag and remote branch". Live `git for-each-ref refs/tags` shows **146 tags** (65 annotated `tag`, 81 lightweight `commit`): `sprint/*` has **69 lightweight + 3 annotated** (`2026-11-16_02_Spät`, `2026-11-16_03_Nacht`, `2026-47.1`); `v*`/`V*` has **10 lightweight + 51 annotated**. `refs/tags/sprint/2026-46.6` (`656f8189`) has no branch collision locally/in `refs/remotes/origin`, while `origin/sprint/2026-41.3` (`702743c1`), `origin/feature/runkeeper`, `origin/feature/drag_and_drop_to_move_sensor_fields`, and 5 merged local `feature/ATT-*` branches remain stale.
  * **V5.0.0 baseline**: Confirmed no `V5.0.0` git tag exists (`app/build.gradle` L50–51 is `5.0.0-dev`, code `273`); `docs/releases/V5.0.0/verification_summary.md` L50 and `system_integration_results.json` L8 cite `9a3b72d9`, verified as a Stage-3 plan commit (`docs(ATT-7560): Stage 3 Implementation Plan...`).
  * **CM Plan (`docs/management/cm_plan.md`)**: Ticket cites L51 (`## 3.` header); actual fast-forward contradiction is L54 (`Only fast-forward merges from release finish or hotfix branches`) vs `--no-ff` in `release-manager` L283 and `sprint-planner` L266. CI table (L26–44) omits `docs/engineering/gates/`, `qa_audits/`, `reviews/`, and `docs/releases/`. Revision history and Status Accounting (`SUP.8.BP7`) are absent.
  * **Ceremony 2 interaction**: `tools/verify_requirement_governance.py` L994–1002 (`RETRO_ALLOWLIST_PATTERNS`, `REQ-PRO-117`) restricts `chore(ceremony2)` commits; manifest generation must be allowed or executed at Step 6 merge/release outside `chore(ceremony2)`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Replace `git tag -f` in skills and build a safe baseline CLI helper creating immutable annotated tags (`git tag -a`) that strictly refuses overwriting existing tags.
  * Generate `docs/releases/<baseline>/manifest.md` (commit SHA, REQ/TST counts, `docs/engineering/test_evidence/` link, open risks/deviations, governance verdict) at sprint close and release finish.
  * Update `docs/management/cm_plan.md` (accurate `--no-ff` merge policy, complete CI inventory with `gates/`, `qa_audits/`, `reviews/`, `releases/`, Configuration Status Accounting, branch cleanup policy, revision history).
  * Provide a dry-run-by-default branch/tag hygiene tool preserving commit history and gating destructive remote/branch deletions behind explicit `--execute` + PO confirmation.
* **Out-of-Scope Non-Goals**: No Android `app/src/main/` changes; no unconfirmed deletion of remote/shared branches or history-rewriting tag deletion; no premature `V5.0.0` release tag creation (release still `5.0.0-dev` / `V5.0.0` SYS.5 OPEN).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-STP-009` (`docs/requirements/req_store_presence.md` L164–179) & `REQ-PRO-057` (`docs/requirements/req_process_stability.md` L3791–3827) -> `docs/management/cm_plan.md`; `REQ-PRO-072`/`REQ-PRO-112` -> `docs/releases/`; `REQ-PRO-100` -> `test_evidence/`; `REQ-PRO-117` -> `verify_requirement_governance.py` & `sprint-planner/SKILL.md`.
* **Historical Origin & Commit Trace**: `ATT-6152` (`REQ-STP-009`) created `cm_plan.md`; `ATT-6733` (`REQ-PRO-057`) aligned `master`, `sprint/<SPRINT_NAME>`, and CI table; `ATT-8144` (`REQ-PRO-100`) & `ATT-8584` (`REQ-PRO-117`) added `test_evidence/` and `chore(ceremony2)` allowlist.
* **Root Reason for Existing Formulation**: `cm_plan.md` inherited `ff-only` boilerplate, and `sprint-planner` used `git tag -f` for idempotent re-runs without immutability checks.
* **Preservation of Core Invariants**: Preserve all assertions in `ReleaseCiAndLintConfigContractTest.kt` L684–760 & L836–851 (`master`, no `` `main` ``, `SUP.8.BP1..BP7`, `sprint/<SPRINT_NAME>`, `V<versionName>__<versionCode>`, `REQ-STP-010`), `ReleaseManagerSystemEvidenceContractTest.kt` (`SYS.5`, `INT-SWE-01..08`, no `system_integration_results.json` gating), `AspiceSweAuditContractTest.kt` (`REQ-STP-010.2`), and `HoechstmassCoreFrameworkContractTest.kt` (`sprint-metrics --record --post-to-retro`, `PA 4.2`, `PA 5.2`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-*` / `TST-PRO-*` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md` extending `REQ-PRO-057`, `REQ-STP-009`, `REQ-PRO-072`, `REQ-PRO-100` (zero existing requirements weakened).
* **Affected Architecture Elements**: `tools/` (new baseline tag + manifest + branch-hygiene CLI & unit tests), `docs/management/cm_plan.md`, `docs/releases/README.md` & `<baseline>/manifest.md`, `.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/release-manager/SKILL.md`.
* **Verification Impact & Test Surface**: Python `unittest` suite for baseline CLI (refuse overwrite, annotated tag creation, manifest generation, dry-run branch cleanup) + JVM contract tests in `ReleaseCiAndLintConfigContractTest.kt` / `AspiceSweAuditContractTest.kt` + `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (`2 SP`, docs/tooling/contract tests only; zero `app/src/main/` or `ANT-Android-SDKs` edits; destructive git branch/tag operations disabled unless explicitly confirmed).

---

## 6. Architectural Strategy & High-Level Solution
1. Add a Python CLI module in `tools/` (e.g., `tools/baseline_manager.py`) supporting: (a) `tag`: checks `git rev-parse -q --verify refs/tags/<name>` and **refuses** if present, else creates `git tag -a <name> -m ...`; (b) `manifest`: writes `docs/releases/<baseline>/manifest.md` from git SHA, `docs/requirements.md` & `docs/tests.md` / `verify_requirement_governance.py` totals, `docs/engineering/test_evidence/`, open `risk_register.md` / deviation counts, and governance exit status; (c) `cleanup-audit`: reports stale/merged branches and lightweight tags without modifying refs unless `--execute` + PO confirmation are provided.
2. Replace `git tag -f` with `baseline_manager.py` / `git tag -a` + pre-check in both `sprint-planner/SKILL.md` files and `release-manager/SKILL.md`.
3. Update `docs/management/cm_plan.md` (`v1.1.0`) and `docs/releases/README.md` while preserving every existing contract-test marker.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero `app/src/main/` changes; `ANT-Android-SDKs` pinned at `0e8cea71`; all existing `ReleaseCiAndLintConfigContractTest`, `ReleaseManagerSystemEvidenceContractTest`, `AspiceSweAuditContractTest`, and `HoechstmassCoreFrameworkContractTest` markers preserved; zero unconfirmed remote/history mutations.
* **Risk Rating**: **LOW** — Self-contained tooling, CM documentation, skill updates, and unit/contract tests (`2 SP`).
