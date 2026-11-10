# Configuration Management Plan: aTrainingTracker

**Document Identifier**: `CM-PLAN-ATT`  
**Standard**: ASPICE 4.0 `SUP.8` (Configuration Management)  
**Target Release**: `V5.0.0` | **Version**: 1.0.0  
**Status**: Approved | **Date**: 2026-11-10  
**Governing Ticket**: [ATT-6152](https://rainerblind.atlassian.net/browse/ATT-6152) | **Requirement**: `REQ-STP-009`  

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
| **Source Code** | `app/src/main/` (`.kt`, `.java`, `.xml`) | Git (`main`, `develop`, feature branches) |
| **Test Suites** | `app/src/test/`, `app/src/androidTest/` | Git (JUnit 4, MockK, Robolectric) |
| **Build Configuration** | `build.gradle`, `app/build.gradle`, `settings.gradle`, `gradle/` | Git |
| **System Architecture** | `docs/architecture.md`, `docs/coding_standards.md`, `docs/design_guidelines.md` | Git (Markdown living docs) |
| **Living Requirements & Tests** | `docs/requirements/`, `docs/tests/`, `docs/requirements.md`, `docs/tests.md` | Git (9 domain shards, synchronized master indexes) |
| **Engineering Deliverables** | `docs/engineering/analysis/`, `plans/`, `test_specs/`, `walkthroughs/`, `retros/` | Git (Canonical directories per Rule 67) |
| **Store & Visual Assets** | `docs/store_presence/`, `docs/guide/` | Git (Markdown, verified PNGs/SVGs) |
| **Submodule Dependencies** | `ANT-Android-SDKs` | Git Submodule (pinned commit gitlink) |
| **Process Metrics** | `docs/engineering/metrics/process_metrics.jsonl` | Git (Quantitative PA 4.1/4.2 data) |
| **CI/CD Automation** | `.github/workflows/*.yml` | Git |

### Ephemeral & Excluded Artifacts
* Ephemeral agent task outputs (`.active_task.json`, `.artifacts/`, `*.artifact.md`), compilation caches (`.gradle/`, `build/`, `app/build/`), and secret keystore properties (`release.properties` with passwords) are strictly excluded from version control via `.gitignore`.

---

## 3. Branching, Tagging & Worktree Strategy (SUP.8.BP3 - Branching & Merging)

### GitFlow Branch Hierarchy
1. **`main`**: Production release baseline. Only fast-forward merges from release finish or hotfix branches. Every release commit on `main` is tagged.
2. **`develop`**: Active development integration baseline. Contains all merged sprint increments.
3. **`sprint/<SPRINT_NAME>`** (e.g. `sprint/2026-46.1`): Active sprint baseline branched from `develop` at Sprint-Start (Ceremony 1). All sprint tickets merge into this branch.
4. **`feature/<KEY>` / `bugfix/<KEY>`**: Isolated ticket branches. Merged into `sprint/<SPRINT_NAME>` as **strictly one squashed atomic commit** (`git merge --squash feature/<KEY>`) upon Stage 5 Gate approval.
5. **`release/<VERSION>`**: Release stabilization branch for version bumping, signed APK generation, and Store deployment.

### Parallel Worktree Isolation & POSIX Concurrency (`REQ-PRO-041`)
* Subagents operate in dedicated worktrees (`Workspace: "share"`).
* Heavy resource operations (compilation, unit tests, branch squashing) are serialized via POSIX `flock` (`/tmp/att_build_merge.lock` via `tools/parallel_sprint_lock.py`).

### Release Tagging Nomenclature
* Every production release tag on `main` follows the immutable convention:
  `V<versionName>__<versionCode>` (e.g. `V5.0.0__273`).

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
