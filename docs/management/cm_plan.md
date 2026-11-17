# Configuration Management Plan: aTrainingTracker

**Document ID**: `DOC-MGT-CM`  
**Document Identifier**: `CM-PLAN-ATT`  
**Standard**: ASPICE 4.0 `SUP.8` (Configuration Management)  
**Target Release**: `V5.0.0` | **Version**: 1.2.0  
**Status**: Reviewed | **Approver**: Pending PO Sign-Off | **Date**: 2026-11-17  
**Governing Ticket**: [ATT-6152](https://rainerblind.atlassian.net/browse/ATT-6152), [ATT-6733](https://rainerblind.atlassian.net/browse/ATT-6733), [ATT-8163](https://rainerblind.atlassian.net/browse/ATT-8163), [ATT-8164](https://rainerblind.atlassian.net/browse/ATT-8164) | **Requirement**: `REQ-STP-009`, `REQ-PRO-057`, `REQ-PRO-119`, `REQ-PRO-120`  

---

## 1. Purpose & Scope (SUP.8.BP1 - Configuration Management Strategy)

This Configuration Management (CM) Plan defines the authoritative strategy, responsibilities, tooling, and governance for identifying, versioning, controlling, baselining, and archiving all project artifacts across **aTrainingTracker**.

### Objectives
* Guarantee complete, auditable traceability across requirements, code, tests, and releases.
* Enforce deterministic, reproducible builds from version control at any historical milestone.
* Ensure strict separation of source code, configuration, ephemeral build outputs, and cryptographic credentials.

---

## 2. Configuration Items (CIs) (SUP.8.BP2 - Identify Configuration Items)

All work products subject to formal configuration control are classified as Configuration Items (CIs):

| CI Category | Target Artifacts | Storage / Tracking Mechanism |
| :--- | :--- | :--- |
| **Source Code** | `app/src/main/` (`.kt`, `.java`, `.xml`) | Git (`master`, `develop`, feature branches) |
| **Test Suites** | `app/src/test/`, `app/src/androidTest/` | Git (JUnit 4, MockK, Robolectric) |
| **Build Configuration** | `build.gradle`, `app/build.gradle`, `settings.gradle`, `gradle/` | Git |
| **System Architecture** | `docs/architecture.md`, `docs/architecture/*.md`, `docs/coding_standards.md`, `docs/design_guidelines.md` | Git (SWE.2 System Architecture models) |
| **Management & CM Plans** | `docs/management/*.md` (`cm_plan.md`, `qa_plan.md`, `risk_register.md`) | Git (ASPICE SUP.8 CM Plan & Policies) |
| **Project Release Plan** | `docs/management/release_plan.md` | Git (V5.0.0 milestone scope, 12h cadence, Pixel 10 verification per REQ-PRO-097) |
| **Living Requirements & Tests** | `docs/requirements/`, `docs/tests/`, `docs/requirements.md`, `docs/tests.md` | Git (9 domain shards, synchronized master indexes) |
| **Engineering Deliverables** | `docs/engineering/analysis/`, `plans/`, `test_specs/`, `walkthroughs/`, `retros/`, `gates/`, `qa_audits/`, `reviews/`, `code_reviews/`, `aspice_audits/`, `test_evidence/` | Git (Canonical directories per Rule 8.4, `REQ-PRO-059`, `REQ-PRO-061`, `REQ-PRO-062`, `REQ-PRO-100`) |
| **Release & Baseline Dossiers** | `docs/releases/` (`manifest.md`, `sys_verification_record.md`, `verification_summary.md`, `release_dossier.md`) | Git (SUP.8 baseline manifests & SYS.5/SWE.6 evidence per `REQ-PRO-072`, `REQ-PRO-119`) |
| **Code Review Coverage Matrix** | `docs/engineering/code_reviews/coverage_matrix.md`, review reports | Git (Weekly Dual-Vendor review evidence per REQ-PRO-095) |
| **ASPICE Audit Records** | `docs/engineering/aspice_audits/` (`YYYY-MM-DD_dual_vendor_aspice_audit.md`) | Git (Weekly Dual-Vendor ASPICE audits per REQ-PRO-096) |
| **Store & Visual Assets** | `docs/store_presence/`, `docs/guide/` | Git (Markdown, verified PNGs/SVGs) |
| **Code Quality & Linting** | `config/detekt/` | Git (Static analysis & Detekt configuration) |
| **Tooling & Automation** | `tools/` | Git (Python verification, build & merge locks) |
| **Governance Rules & Skills** | `.agents/` | Git (ASPICE workflow rules & AI subagent skills) |
| **Submodule Dependencies** | `ANT-Android-SDKs` | Git Submodule (pinned commit gitlink) |
| **Process Metrics** | `docs/engineering/metrics/process_metrics.jsonl` | Git (Quantitative PA 4.1/4.2 data) |
| **CI/CD Automation** | `.github/workflows/*.yml` | Git |

### Ephemeral & Excluded Artifacts
* Ephemeral agent task outputs (`.active_task.json`, `.artifacts/`, `*.artifact.md`), compilation caches (`.gradle/`, `build/`, `app/build/`), and secret keystore properties (`release.properties` with passwords) are strictly excluded from version control via `.gitignore`.

---

## 3. Branching, Tagging & Worktree Strategy (SUP.8.BP3 - Branching & Merging)

### GitFlow Branch Hierarchy & Merge Policy
1. **`master`**: Production release baseline (`origin/master`). Integrated via `--no-ff` merge commits from `git flow release finish` or hotfix branches. Every release commit on `master` is tagged with immutable annotated tags.
2. **`develop`**: Active development integration baseline. Contains all merged sprint increments integrated via `--no-ff` merge commits from `sprint/<SPRINT_NAME>` at Ceremony 2 close (and `--no-ff` back-merges from `release/<VERSION>`).
3. **`sprint/<SPRINT_NAME>`** (e.g. `sprint/2026-46.1`): Active sprint baseline branched from `develop` at Sprint-Start (Ceremony 1). All sprint tickets merge into this branch.
4. **`feature/<KEY>` / `bugfix/<KEY>`**: Isolated ticket branches. Merged into `sprint/<SPRINT_NAME>` as **strictly one squashed atomic commit** (`git merge --squash feature/<KEY>`) upon Stage 5 Gate approval.
5. **`release/<VERSION>`**: Release stabilization branch for version bumping, signed APK generation, and Store deployment.

### Parallel Worktree Isolation & POSIX Concurrency (`REQ-PRO-041`)
* Subagents operate in dedicated worktrees (`Workspace: "share"`).
* Heavy resource operations (compilation, unit tests, branch squashing) are serialized via POSIX `flock` (`/tmp/att_build_merge.lock` via `tools/parallel_sprint_lock.py`).

### Milestone & Release Tagging Nomenclature (Immutable Annotated Baselines)
* **Immutability & Collision Guard (`REQ-PRO-119.1`)**: All sprint and release baseline tags MUST be created as annotated Git tag objects (`git tag -a` via `tools/baseline_manager.py tag`). Force-overwriting existing tags (`git tag -f`) is strictly prohibited; tooling refuses with non-zero exit if `refs/tags/<baseline>` already exists or collides with `refs/heads/<baseline>` / `refs/remotes/origin/<baseline>`.
* **Sprint Milestone Tags**: At sprint close (Ceremony 2 Step 6), an immutable annotated milestone tag `sprint/<SPRINT_NAME>` (e.g. `sprint/2026-46.1`) is cut on the `--no-ff` merge commit in `develop`, accompanied by `docs/releases/sprint_<SPRINT_NAME>/manifest.md`.
* **Production Release Tags**: Every production release commit on `master` follows the immutable annotated convention (never fabricated prior to SYS.5 sign-off):
  - Dual format: `V<versionName>__<versionCode>` (e.g. `V5.0.0__273`) alongside `V<versionName>` (e.g. `V5.0.0`), accompanied by `docs/releases/V<versionName>/manifest.md`.

### Branch & Tag Hygiene Policy (`REQ-PRO-119.5`)
* Local merged `feature/<KEY>` / `sprint/<SPRINT_NAME>` branches are deleted immediately after squash-merge or `--no-ff` sprint closure (`git branch -d`).
* Periodic repository hygiene runs `python3 tools/baseline_manager.py cleanup-audit` (dry-run by default), listing merged local branches, stale remote branches, and legacy lightweight tags without mutating refs.
* Destructive cleanup of merged local branches requires `--execute --confirm-po` (history-preserving `git branch -d` only); remote branch deletions and historical tag conversions require explicit Product Owner confirmation and MUST preserve commit history.

### Formal Release Deliverables Package (SUP.8.BP4 - Release Contents)
Every formal production release baseline on `master` comprises the following auditable deliverables:
1. **Signed Production APK**: Cryptographically signed release APK (`app-release.apk`) built against the production keystore.
2. **Google Play Android App Bundle (AAB)**: Production AAB (`app-release.aab`) verified for Google Play Store publishing.
3. **9-Language Localized Release Notes**: Multi-locale changelogs maintained under `docs/store_presence/` across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
4. **Jira FixVersion Traceability**: Baseline release traceability in Jira (`FixVersion`) linking all implemented and verified user stories, bugs, and improvements.

---

## 4. Baseline Management & Audit Integrity (SUP.8.BP4, BP5 - Baselines & Integrity)

### Agile ASPICE Lifecycle Gates (AAA)
Changes must traverse 5 sequential verification gates before baseline integration:
1. **Gate 1 (Analysis)**: Forensic investigation, scope grounding (`ATT-1250`), invariant definition.
2. **Gate 2 (Test Spec)**: Formal `REQ-XXX` and `TST-XXX` specification, 9-locale localization parity.
3. **Gate 3 (Plan)**: Architecture decomposition, atomic sequence, rollback plan. Code Lock enforced (`check-gate`).
4. **Gate 4 (Implementation)**: Verification of git diff against plan, KDoc compliance, zero `app/src/main/` test-grep violations.
5. **Gate 5 (Verification)**: Clean test execution, walkthrough documentation, living docs update to `[Verified]`.

### Mathematical Parity & Verification Tooling
* `tools/verify_requirement_governance.py` enforces 100% mathematical parity between master index tables and individual domain shards in `docs/requirements/` and `docs/tests/`.
* Tier 1 targeted tests run per ticket; Tier 2 full suite (`testDebugUnitTest`) runs at the Sprint Integration Gate before Ceremony 2.

---

## 5. Submodule Change Control & Pinning (SUP.8.BP6 - Submodule Control)

* **Immutable Commit Pinning**: Submodule `ANT-Android-SDKs` is tracked as an exact gitlink SHA-1.
* **Prohibition of Floating Branches**: `.gitmodules` MUST NOT contain `branch = ...` directives. Submodule updates are executed explicitly via `git checkout <SHA>` and committed as attributed configuration changes.
* **Submodule Upgrade Procedure**:
  1. File dedicated Jira ticket under parent epic.
  2. Perform API compatibility audit in `ANT-Android-SDKs`.
  3. Update gitlink pointer in main repository and verify clean compilation via `./gradlew compileDebugSources`.

---

## 6. Storage, Archiving & Disaster Recovery (SUP.8.BP7 - Configuration Status & Recovery)

### Repository Hosting & Redundancy
* Primary repository is hosted on GitHub (`origin`), with local mirrors maintained in developer environments.
* In-sprint work products are checkpointed via detached worktrees and atomic feature branches.

### Credential & Key Security (`REQ-STP-008`)
* Plaintext signing passwords are decoupled from version control. Sourced via CI environment variables, local OS keyring (`secret-tool`), or external `~/.config/atrainingtracker/release.properties` (`0600`).

### Athlete Data Disaster Recovery
* Standalone Full-App Backup & Restore (`.attbackup`) ensures athlete data sovereignty and disaster recovery independent of cloud infrastructure.

### Configuration Status Accounting & Reporting (SUP.8.BP7)
* **Baseline Content Manifest (`docs/releases/<baseline>/manifest.md`)**: Generated deterministically at every sprint closure (Ceremony 2) and production release finish via `python3 tools/baseline_manager.py manifest <baseline>`. Records the baseline tag, resolved commit SHA, living-doc REQ/TST totals (`docs/requirements.md`, `docs/tests.md`), linked `docs/engineering/test_evidence/` verdict, active High/Medium risks (`docs/management/risk_register.md`) and open release deviations, and `tools/verify_requirement_governance.py` status.
* **Sprint Status Records**: Coupled with `docs/engineering/gates/<sprint>.md`, `qa_audits/<sprint>.md`, `reviews/<sprint>.md`, `test_evidence/<sprint>.md`, and `docs/engineering/metrics/process_metrics.jsonl` for end-to-end SUP.8 configuration status accounting.
* **Work-Product Control Header & Human-Only Approval (`SUP.8.BP2`, `GP 2.2`, `REQ-PRO-120`)**: Every governed work product carries `Document ID` (`DOC-*`), semver `Version`, `Status` (`Draft` / `Reviewed` / `Approved`), `Approver`, and `Change History` (`docs/coding_standards.md` §5.13). Agents default to `Reviewed`/`Draft`; only the human Product Owner (via `Human-Approved:` / `Interactive-PO:` commit sign-off) may grant or retain `Approved` across content revisions, enforced by `tools/verify_requirement_governance.py`.

---

## 7. Change History (Revision History)

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-11 | AI Agent / PO | `ATT-6152`, `ATT-6733` | Initial ASPICE `SUP.8` CM Plan, CI inventory, `master` grounding, and submodule pinning. |
| `1.1.0` | 2026-11-17 | AI Agent (`ATT-8163`) | `ATT-8163` | Aligned `--no-ff` sprint/release merge policy, added `gates/`/`qa_audits/`/`reviews/`/`docs/releases/` CIs, enforced immutable annotated tags (`baseline_manager.py`, zero `git tag -f`), added `SUP.8.BP7` Configuration Status Accounting (`manifest.md`), and defined dry-run/PO-gated branch & tag hygiene policy. |
| `1.2.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Standardized `DOC-MGT-CM` work-product header, reset self-declared `Approved` to `Reviewed` (`Pending PO Sign-Off`), and codified human-only approval governance (`REQ-PRO-120`). |
