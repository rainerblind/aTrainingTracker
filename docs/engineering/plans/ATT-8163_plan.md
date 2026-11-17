# Stage 3: Implementation Plan - ATT-8163: Immutable Annotated Baselines, Content Manifest & CM Plan Alignment

**Ticket**: [ATT-8163](https://rainerblind.atlassian.net/browse/ATT-8163) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_02_Spät`  
**Requirement Mapping**: `REQ-PRO-119` (`REQ-PRO-119.1`–`REQ-PRO-119.5`) | **Test Mapping**: `TST-PRO-119` (`TST-PRO-119.1`–`TST-PRO-119.6`)  
**Branch**: `feature/ATT-8163` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-17

---

## 1. Problem Description & Background
Resolve ASPICE 4.0 Audit Finding `M11 / SUP.8` by eliminating mutable `git tag -f` lightweight tagging, generating deterministic baseline content manifests (`docs/releases/<baseline>/manifest.md`), aligning `docs/management/cm_plan.md` with actual `--no-ff` merge practices and full CI inventory, and introducing a dry-run/PO-gated branch & tag hygiene tool.

---

## 2. Traceability & Requirements Mapping
| Requirement | Implementation Target | Test Case |
| :--- | :--- | :--- |
| `REQ-PRO-119.1` (Immutable annotated tags & collision guard) | `tools/baseline_manager.py` (`tag`), `sprint-planner/SKILL.md` (`.agents/` + `tools/hoechstmass/`), `release-manager/SKILL.md` | `TST-PRO-119.1`, `TST-PRO-119.5` |
| `REQ-PRO-119.2` (Baseline content manifest) | `tools/baseline_manager.py` (`manifest`), `docs/releases/<baseline>/manifest.md` | `TST-PRO-119.2` |
| `REQ-PRO-119.3` (Ceremony 2 allowlist parity) | `tools/verify_requirement_governance.py` (`RETRO_ALLOWLIST_PATTERNS`) | `TST-PRO-119.4` |
| `REQ-PRO-119.4` (CM Plan alignment & SUP.8.BP7) | `docs/management/cm_plan.md` (`v1.1.0`), `docs/releases/README.md`, `ReleaseCiAndLintConfigContractTest.kt` | `TST-PRO-119.5` |
| `REQ-PRO-119.5` (Dry-run & PO-gated branch hygiene) | `tools/baseline_manager.py` (`cleanup-audit`), `docs/management/cm_plan.md` | `TST-PRO-119.3` |
| `REQ-PRO-119` (UI / Localization) | `N/A` (tooling & docs only; zero UI strings) | `TST-PRO-119.6` (`N/A`) |

---

## 3. System Invariants & Preserved Behavior
1. Zero edits to `app/src/main/` or submodule `ANT-Android-SDKs` (`0e8cea71`).
2. Preserve every existing contract marker in `ReleaseCiAndLintConfigContractTest.kt` (`master`, no `` `main` ``, `SUP.8.BP1..BP7`, `sprint/<SPRINT_NAME>`, `V<versionName>__<versionCode>`, `V<versionName>`, CI table paths, `REQ-STP-010`), `ReleaseManagerSystemEvidenceContractTest.kt` (`SYS.5`, `REQ-PRO-072`, `REQ-PRO-112`, `INT-SWE-01..08`, no `system_integration_results.json` gating), `AspiceSweAuditContractTest.kt` (`REQ-PRO-117` allowlist/protected tokens), and `HoechstmassCoreFrameworkContractTest.kt` (`sprint-metrics --record --post-to-retro`, `PA 4.2`, `PA 5.2`).
3. No unconfirmed remote branch deletion, no history-rewriting tag deletion, and no fabricated pre-release `V5.0.0` tag (`5.0.0-dev` / `SYS.5 OPEN`).
4. Offline unit tests only (`Rule 5`: isolated `tempfile` Git repos + mocked subprocesses/Jira).

---

## 4. Proposed Architectural Changes
* **Tooling (`tools/baseline_manager.py`)**: Pure-Python CLI with three subcommands:
  * `tag <baseline> [--target <ref>] [--message <msg>]`: Checks `refs/tags/<baseline>`, `refs/heads/<baseline>`, and `refs/remotes/origin/<baseline>`; refuses with non-zero exit if tag exists or branch/tag ref collides; runs `git tag -a <baseline> <target> -m <msg>`.
  * `manifest <baseline> [--target <ref>] [--test-evidence <path>] [--governance-status <str>] [--out <path>]`: Resolves commit SHA (rejects invalid refs), parses REQ/TST totals from `docs/requirements.md` and `docs/tests.md`, resolves `docs/engineering/test_evidence/<baseline_norm>.md` (and `docs/releases/<baseline>/verification_summary.md` if present), extracts open High/Medium risks from `docs/management/risk_register.md` / open deviations, and writes deterministic `docs/releases/<baseline>/manifest.md`.
  * `cleanup-audit [--base develop] [--execute --confirm-po]`: Audits local branches merged into `base`, remote stale branches, and lightweight tags (`objecttype == commit`) in dry-run mode; deletes only merged local branches when BOTH `--execute` and `--confirm-po` are passed.
* **Governance (`tools/verify_requirement_governance.py`)**: Add `re.compile(r"^docs/releases/[^/]+/manifest\.md$")` to `RETRO_ALLOWLIST_PATTERNS` (`REQ-PRO-119.3`) while keeping all `RETRO_PROTECTED_*` entries unchanged.
* **CM Plan & Dossier Docs (`docs/management/cm_plan.md`, `docs/releases/README.md`)**: Update `cm_plan.md` to `v1.1.0` with Revision History, `--no-ff` merge policy for sprint & release integrations, CI rows for `docs/engineering/gates/`, `qa_audits/`, `reviews/`, `docs/releases/`, §6.4 Configuration Status Accounting (`SUP.8.BP7`), and §3 Branch & Tag Hygiene Policy; document `manifest.md` in `docs/releases/README.md`.
* **Skills (`.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/release-manager/SKILL.md`)**: Replace `git tag -f` with `python3 tools/baseline_manager.py tag` + `manifest` generation at sprint merge & release finish; enforce pre-tag existence/collision checks in `release-manager/SKILL.md`.
* **UI Consistency**: `N/A — no UI changes.`

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 — Implement `tools/baseline_manager.py`** (`tag`, `manifest`, `cleanup-audit` subcommands + CLI).
2. **Step 2 — Update `tools/verify_requirement_governance.py`** (`RETRO_ALLOWLIST_PATTERNS` adding `^docs/releases/[^/]+/manifest\.md$`).
3. **Step 3 — Update CM Plan & Release Docs**: Update `docs/management/cm_plan.md` (`v1.1.0`, Revision History, `--no-ff`, complete CI table, `SUP.8.BP7` status accounting, cleanup policy) and `docs/releases/README.md` (`manifest.md`).
4. **Step 4 — Update Skills**: Remove `git tag -f` in `.agents/skills/sprint-planner/SKILL.md` and `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`; integrate `baseline_manager.py tag` + `manifest` in sprint close and `.agents/skills/release-manager/SKILL.md` Phase 4 while preserving all contract-test tokens.
5. **Step 5 — Author Tests**: Add `tools/test_baseline_manager.py`, extend `tools/test_verify_requirement_governance.py` (manifest allowlist test), and add assertions in `ReleaseCiAndLintConfigContractTest.kt` for `cm_plan.md` `v1.1.0` (`--no-ff`, `gates/`, `qa_audits/`, `reviews/`, `docs/releases/`, `SUP.8.BP7` status accounting, Revision History) and zero `git tag -f` in `sprint-planner/SKILL.md`.
6. **Step 6 — Execute Tests & Governance**:
   * `python3 -m unittest tools/test_baseline_manager.py tools/test_verify_requirement_governance.py`
   * `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" --tests "com.atrainingtracker.trainingtracker.build.ReleaseManagerSystemEvidenceContractTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest"`
   * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: All Stage 4 unit/contract tests pass (`0` failures) and `verify_requirement_governance.py` exits `0`; Stage 5 promotes `REQ-PRO-119`/`TST-PRO-119` to `[Verified]` and authors walkthrough.
* **Rollback**: Isolated on `feature/ATT-8163`; `git checkout sprint/2026-11-17_02_Spät` cleanly reverts all changes.
