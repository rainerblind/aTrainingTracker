# Stage 2: Requirement & Test Specification - ATT-8164: Work-Product Headers & Governance Reconciliation

**Ticket**: [ATT-8164](https://rainerblind.atlassian.net/browse/ATT-8164) | **Sub-task**: `[Req & Test Spec]` | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_02_Spät` | **Branch**: `feature/ATT-8164`  
**Requirement Mapping**: `REQ-PRO-120` | **Test Spec ID**: `TST-PRO-120` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-PRO-120` [Planned])
* **Rationale**: Resolve ASPICE 4.0 `GP 2.2` / `SUP.8 BP2` findings: missing version/status/approver/history headers, agent-self-declared `Approved` statuses, 5 governance contradictions, and diverged `tools/hoechstmass/` copies.
* **Attributes**: **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit / Review
* **Functional & Architectural Requirements**:
  1. `REQ-PRO-120.1`: Define canonical header (`Document ID: DOC-*`, semver `Version`, `Status` in `{Draft, Reviewed, Approved}` + ADR `Retrospective (Pending PO Sign-Off)`, `Approver`, `Change History`) in `docs/coding_standards.md` & `docs/management/cm_plan.md`; apply across `docs/management/`, `docs/architecture/` (`docs/architecture.md`, `adr_001`..`007`, `integration_strategy.md`, `interfaces.md`, `sensor_units.md`, `detailed_design/`), `docs/system/`, `docs/requirements.md` + 9 shards, `docs/tests.md` + 9 shards, `.agents/rules/`, and root governance docs; reset agent-self-declared `Approved` to `Reviewed` (`Pending PO Sign-Off`) while preserving `verify_shard_header_counts` & `verify_architecture_and_system_specs`.
  2. `REQ-PRO-120.2`: `tools/verify_requirement_governance.py` (`verify_work_product_headers()`, CLI `--check-doc-headers` + default `verify_governance()` flow) SHALL validate headers on all governed docs, reject `Status: Approved` with AI/Pending/empty `Approver`, and on `git diff` body changes (exempting pre-cutoff commits) enforce monotonic semver `Version` bump and reset/reject `Approved` unless the commit carries `Human-Approved:` or `Interactive-PO:`.
  3. `REQ-PRO-120.3`: Reconcile `9 Core Rules` (`.agents/rules/aspice_governance.md`, `docs/project_protocol.md`, `tools/jira_util.py` reminder), `Rules 1–85+` archive span (`docs/project_protocol.md`, `aspice_governance.md`, `rule_history_archive.md`), `6-Stage` lifecycle (`Stages 1–5` + `Stage 6` Cold Review), concurrency (`max 3 lanes`, `2 lanes` active token-budget default), and `Rule 3.4 / Rule 83` in `docs/management/risk_register.md:66`.
  4. `REQ-PRO-120.4`: Sync `tools/hoechstmass/` (`.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/stage6-final-review/SKILL.md`, `docs/project_protocol.md`, `README.md`) while keeping `HoechstmassCoreFrameworkContractTest`, `HoechstmassToolingContractTest`, and `ReleaseCiAndLintConfigContractTest` green.
  5. `REQ-PRO-120.5`: Offline unit tests (`tools/test_verify_requirement_governance.py`, `Rule 5` mocked diffs/temp repos) + Gradle contract tests reading docs directly (zero `app/src/main/` reads per `REQ-PRO-049.1`).
* **Acceptance Criteria (Given-When-Then)**:
  * *Given* any governed work product, *When* `verify_work_product_headers()` runs, *Then* `DOC-*` ID, semver `Version`, valid `Status`, `Approver`, and `Change History` exist with zero unapproved `Approved` claims.
  * *Given* a governed doc with changed body in `git diff`, *When* CI runs `verify_requirement_governance.py`, *Then* it fails unless `Version` bumped and `Status` reset from `Approved` (or `Human-Approved:`/`Interactive-PO:` trailer + human `Approver` is present).
  * *Given* `.agents/rules/`, `docs/project_protocol.md`, `tools/jira_util.py`, `risk_register.md`, `sprint-planner/SKILL.md`, and `tools/hoechstmass/`, *When* compared, *Then* rule count (`9`), archive (`1–85+`), stages (`6`), concurrency (`max 3`, `2` default), and `Rule 3.4 / Rule 83` match.
* **Requirement Archaeology & Chesterton's Fence Audit**:
  * *Original Target*: Refines `REQ-PRO-035` (`ATT-3922`), `REQ-PRO-041` (`ATT-5914`/`ATT-6743`), `REQ-ARC-005` (`ATT-7566`), `REQ-PRO-119` (`ATT-8163`).
  * *Root Reason & Preservation*: Legacy assertions pinned `8 Core Rules`, `5-Stage`, `Rules 1–55`, `**Version**: 1.1.0`, and `Status: Approved`; `REQ-PRO-120` preserves `Rule 1..8`/`Stage 1..5` markers, `REQ-PRO-117` allowlist, and shard parity while enforcing human sign-off.
* **System Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched at `0e8cea71`; `Localization Parity: N/A - no UI strings`.

---

## 2. Test Specification (`TST-PRO-120` [Planned])
* **Test Case 1 (`TST-PRO-120.1`, Header Coverage)**: `tools/test_verify_requirement_governance.py` & `AspiceSweAuditContractTest.kt` verify all governed docs carry `DOC-*`, semver `Version`, valid `Status`, `Approver`, `Change History`, and zero AI/Pending `Approved` claims.
* **Test Case 2 (`TST-PRO-120.2`, Human-Only Approval Guard)**: Offline temp-Git-repo tests in `tools/test_verify_requirement_governance.py` (`Rule 5`: zero live Jira calls) verify failure on missing headers, unbumped body edits, and unapproved `Approved` set/retain; pass on `Approved->Reviewed` + version bump, `Human-Approved:` commits, and pre-cutoff commits.
* **Test Case 3 (`TST-PRO-120.3`, Contradictions & Hoechstmass Parity)**: `AspiceSweAuditContractTest.kt`, `HoechstmassCoreFrameworkContractTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`, `JavaCoreMigrationStrategyContractTest.kt` verify `9 Core Rules`, `Rules 1–85+`, `6-Stage` framing, `2–3` lane concurrency, `Rule 3.4 / Rule 83` in `risk_register.md`, and `tools/hoechstmass/` parity (zero `app/src/main/` reads).
* **Test Case 4 (`TST-PRO-120.4`, Localization & Tier 1 Regression)**: `Localization Parity: N/A - no UI strings`. Run `python3 tools/test_verify_requirement_governance.py`, `python3 tools/verify_requirement_governance.py`, and `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"`.

---

## 3. Traceability Matrix
| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-120.1` | Unit + Contract | `verify_work_product_headers()`, `AspiceSweAuditContractTest` | `REQ-PRO-120.1` | Planned |
| `TST-PRO-120.2` | Offline Git Unit | `verify_work_product_headers()` diff/trailer guard | `REQ-PRO-120.2` | Planned |
| `TST-PRO-120.3` | Contract | `AspiceSweAuditContractTest`, `HoechstmassCoreFrameworkContractTest`, `ReleaseCiAndLintConfigContractTest`, `JavaCoreMigrationStrategyContractTest` | `REQ-PRO-120.3`, `REQ-PRO-120.4` | Planned |
| `TST-PRO-120.4` | Regression + Loc | Full Python + Gradle build contract suite (`N/A - no UI strings`) | `REQ-PRO-120.5` | Planned |
