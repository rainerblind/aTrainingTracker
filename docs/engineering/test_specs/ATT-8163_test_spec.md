# Stage 2: Requirement & Test Specification - ATT-8163: Immutable Annotated Baselines, Content Manifest & CM Plan Alignment

**Ticket**: [ATT-8163](https://rainerblind.atlassian.net/browse/ATT-8163) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_02_Spät`  
**Requirement Mapping**: `REQ-PRO-119` (`docs/requirements/req_process_stability.md`)  
**Test Spec ID**: `TST-PRO-119` (`docs/tests/tst_process_stability.md`)  
**Branch**: `feature/ATT-8163` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-PRO-119`)

### 1.1 Attributes & Rationale
* **Priority**: `High` | **Type**: `Non-Functional` | **Verification Method**: `Unit / Review`
* **Rationale**: Remediate ASPICE 4.0 Audit Finding `M11 / SUP.8` (`SUP.8 BP5/BP6/BP7`, `GP 2.2.3`) by enforcing immutable annotated baseline tags, deterministic baseline content manifests, accurate CM Plan policies/inventories, and PO-gated branch hygiene.

### 1.2 Normative Clauses (`REQ-PRO-119.1` – `REQ-PRO-119.5`)
1. **Immutable Annotated Tagging (`REQ-PRO-119.1`)**: `tools/baseline_manager.py` (`tag`), `.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`, and `.agents/skills/release-manager/SKILL.md` SHALL create annotated tags (`git tag -a <tag> <commit> -m ...`), remove all `git tag -f` force-overwrites, refuse existing tags with non-zero exit, and reject branch/tag ref collisions (`refs/heads/<tag>` or `refs/remotes/origin/<tag>`).
2. **Baseline Content Manifest (`REQ-PRO-119.2`)**: `tools/baseline_manager.py` (`manifest`) SHALL generate `docs/releases/<baseline>/manifest.md` at sprint close and release finish with: baseline tag, commit SHA, master `docs/requirements.md` & `docs/tests.md` counts, linked `docs/engineering/test_evidence/` path & verdict, open risks/deviations (`docs/management/risk_register.md` / `verification_summary.md`), and `verify_requirement_governance.py` status.
3. **Ceremony 2 Allowlist Parity (`REQ-PRO-119.3`)**: `tools/verify_requirement_governance.py` (`RETRO_ALLOWLIST_PATTERNS`) SHALL permit `docs/releases/[^/]+/manifest\.md` in autonomous `chore(ceremony2)` commits while keeping all protected rule/skill/tool paths (`REQ-PRO-117.2`) blocked.
4. **CM Plan Alignment & SUP.8.BP7 (`REQ-PRO-119.4`)**: `docs/management/cm_plan.md` (`v1.1.0` + Revision History) SHALL specify `--no-ff` merge policy for sprint & release integrations (removing `ff-only`), add `docs/engineering/gates/`, `qa_audits/`, `reviews/`, and `docs/releases/` to CIs, add Configuration Status Accounting (`SUP.8.BP7`), and preserve all `REQ-STP-009` / `REQ-PRO-057` markers; `docs/releases/README.md` SHALL document `manifest.md`.
5. **Safe Branch & Tag Hygiene (`REQ-PRO-119.5`)**: `docs/management/cm_plan.md` and `tools/baseline_manager.py` (`cleanup-audit`) SHALL default to dry-run reporting (merged local branches, stale remote branches, lightweight tags) and require `--execute` + `--confirm-po` before deleting merged local branches, preserving commit history and never fabricating a pre-release `V5.0.0` tag.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1**: *Given* an existing sprint/release tag, *When* Ceremony 2 or release tagging runs via `baseline_manager.py tag`, *Then* it refuses overwrite with non-zero exit instead of forcing.
* **AC-2**: *Given* a new sprint or release baseline, *When* baselined, *Then* an annotated Git tag and `docs/releases/<baseline>/manifest.md` referencing commit SHA, REQ/TST counts, test evidence, open risks/deviations, and governance status exist.
* **AC-3**: *Given* `cm_plan.md` and `cleanup-audit`, *When* audited, *Then* `--no-ff`, complete CI table, `SUP.8.BP7`, Revision History, and dry-run/PO-gated cleanup are verified.
* **System Invariants**: Zero `app/src/main/` changes; `ANT-Android-SDKs` untouched; `Localization Parity: N/A - no UI strings`; preserve `ReleaseCiAndLintConfigContractTest`, `ReleaseManagerSystemEvidenceContractTest`, `AspiceSweAuditContractTest`, and `HoechstmassCoreFrameworkContractTest` markers.

---

## 2. Test Specification (`TST-PRO-119`)

* **`TST-PRO-119.1` — Annotated Tagging & Collision Refusal (`tools/test_baseline_manager.py`)**: Temp Git repo tests assert `baseline_manager.py tag` creates `git cat-file -t == "tag"`, exits non-zero on existing tag (no `-f`), and exits non-zero when `refs/heads/<tag>` exists.
* **`TST-PRO-119.2` — Manifest Generation (`tools/test_baseline_manager.py`)**: Offline unit tests verify `baseline_manager.py manifest` writes `docs/releases/<baseline>/manifest.md` with baseline tag, commit SHA, parsed REQ/TST counts, `test_evidence` link, open risks/deviations, and governance status.
* **`TST-PRO-119.3` — Branch Hygiene Dry-Run & PO Gate (`tools/test_baseline_manager.py`)**: Unit tests verify `cleanup-audit` makes zero ref changes in dry-run, rejects `--execute` without `--confirm-po`, and deletes only merged local branches with `--execute --confirm-po`.
* **`TST-PRO-119.4` — Ceremony 2 Allowlist (`tools/test_verify_requirement_governance.py`)**: Unit tests verify `verify_retro_write_allowlist()` accepts `docs/releases/<baseline>/manifest.md` in `chore(ceremony2)` while rejecting protected tool/skill/rule paths.
* **`TST-PRO-119.5` — Contract Tests & Governance (`ReleaseCiAndLintConfigContractTest.kt` + `verify_requirement_governance.py`)**: Targeted JVM contract tests verify `cm_plan.md`, `docs/releases/README.md`, and zero `git tag -f` in `sprint-planner/SKILL.md`; `verify_requirement_governance.py` exits `0`.
* **Localization Parity (`TST-PRO-119.6`)**: `Localization Parity: N/A - no UI strings`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-119.1` | Python Unit (Temp Git) | `baseline_manager.py tag` | `REQ-PRO-119.1` | Specified |
| `TST-PRO-119.2` | Python Unit | `baseline_manager.py manifest` | `REQ-PRO-119.2` | Specified |
| `TST-PRO-119.3` | Python Unit (Temp Git) | `baseline_manager.py cleanup-audit` | `REQ-PRO-119.5` | Specified |
| `TST-PRO-119.4` | Python Unit | `verify_retro_write_allowlist()` | `REQ-PRO-119.3` | Specified |
| `TST-PRO-119.5` | JVM Contract + Governance | `ReleaseCiAndLintConfigContractTest` + governance | `REQ-PRO-119.1`, `REQ-PRO-119.4` | Specified |
| `TST-PRO-119.6` | Localization Audit | `N/A - no UI strings` | `REQ-PRO-119` | N/A |
