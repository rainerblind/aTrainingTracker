# Living Requirements: Engineering Process, Stability & Architecture (Part 2 of 2)

**Document ID**: `DOC-REQ-PRO-P2`  
**Version**: 1.0.28  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `process_stability_part2`  
**Prefixes**: `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`  
**Scope**: ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools.  
**Total Requirements**: 72

---

### REQ-PRO-061: Sprint-Level QA Process-Compliance Audit [Verified]
- **Tests**: `TST-PRO-061`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: Automated `qa-audit` CLI command in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`, structured markdown reporting in `docs/engineering/qa_audits/<sprint>.md`, canonical directory registration, and `--check-qa-audits` validation in `tools/verify_requirement_governance.py`.
- **Rationale**: Under ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Work Product Evidence), quality gates independently audit work products, but no separate audit validates lifecycle process conformity (Pre-Assessment Finding B-09). This requirement establishes an automated QA process-compliance audit tool (`qa-audit`) that samples $\ge 2$ tickets per sprint, evaluates a 5-point process checklist, records compliance in `docs/engineering/qa_audits/<sprint>.md`, and validates presence via `--check-qa-audits`:
  1. *QA Process Audit Tooling (`REQ-PRO-061.1`, tools/jira_util.py & tools/hoechstmass/tools/jira_util.py)*:
     • `jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL implement `qa-audit [--sprint <ID|NAME>] [--sample N] [--out <FILE>]` to sample $\ge 2$ engineering tickets and audit 5 core process criteria (artifacts, gate order, 4-Augen role separation, living doc parity, commit hygiene).
  2. *Audit Report Schema (`REQ-PRO-061.2`, docs/engineering/qa_audits/<sprint>.md)*:
     • The audit deliverable SHALL record: Sprint ID/Name, Auditor Role, Date, Sampled Tickets table, Checklist Results per ticket, Overall Compliance Verdict (`COMPLIANT` / `NON-COMPLIANT`), and links to any non-compliance tracking issues.
  3. *Directory Governance (`REQ-PRO-061.3`, ALLOWED_ENGINEERING_ENTRIES & hoechstmass_config.json)*:
     • `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `hoechstmass_config.json` SHALL register `"qa_audits"`.
  4. *Governance Enforcement CLI (`REQ-PRO-061.4`, tools/verify_requirement_governance.py & tools/hoechstmass/tools/verify_requirement_governance.py)*:
     • `tools/verify_requirement_governance.py` and Höchstmaß mirror SHALL implement `--check-qa-audits [SPRINT]`, failing with exit code 1 if the audit file is missing, empty, or has $<2$ sampled ticket evaluations.
  5. *Ceremony 2 Retrospective Mandate (`REQ-PRO-061.5`, .agents/skills/sprint-planner/SKILL.md)*:
     • `sprint-planner/SKILL.md` Ceremony 2 SHALL mandate executing `qa-audit` and `verify_requirement_governance.py --check-qa-audits` prior to sprint closure.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-061` (`REQ-PRO-061.1`–`REQ-PRO-061.5`). Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/engineering/qa_audits/`, and `tools/hoechstmass/`.
  2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.3 via ATT-6154 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-09.
  3. *Root Reason for Existing Formulation*: Gaps in independent QA process adherence audits; gate reviews verified artifacts but did not systematically audit end-to-end lifecycle conformity.
  4. *Preservation of Core Invariants*: Zero changes to Android runtime code; pure Python 3 standard library; offline mock testing resilience preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* Ceremony 2 of sprint `sprint/2026-46.3`,
  • *When* `qa-audit` is executed,
  • *Then* it generates `docs/engineering/qa_audits/sprint_2026-46.3.md` auditing $\ge 2$ sampled tickets with compliance verdicts.
  • *Given* a sprint without a QA audit record or with $<2$ sampled tickets,
  • *When* `verify_requirement_governance.py --check-qa-audits <sprint>` runs,
  • *Then* it aborts with exit code 1.
  • *Given* a valid QA audit record with $\ge 2$ sampled tickets,
  • *When* `verify_requirement_governance.py --check-qa-audits <sprint>` runs,
  • *Then* it passes with exit code 0.
- **Invariants**: Zero modifications to Android production runtime code in `app/src/main/`; pure Python standard library retained; Rule 3 (`Erledigt` human-only gate) preserved.

### REQ-PRO-062: Independent QA & Cross-Vendor Review Evidence Persistence [Verified]
- **Tests**: `TST-PRO-062`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: Automated `record-review` CLI command (`--review-type=human|cross-vendor-ai`) in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`, structured markdown reporting in `docs/engineering/reviews/<review_file>.md`, canonical directory registration, and `--check-reviews` validation in `tools/verify_requirement_governance.py`.
- **Rationale**: Under ASPICE SUP.1 (Quality Assurance, BP5) and GP 2.2.4, QA evidence must demonstrate independence and accurate attribution (Pre-Assessment Finding B-10, Remediation Finding K2, Audit Gap #7 `ATT-8204`). Previously, `record_review()` classified a reviewer as human only when `reviewer.lower() in ("human", "user")`, mislabeling named human reviewers (e.g., `"Rainer Blind"`) as `Cross-Vendor AI Review (Layer 5)` with `Vendor: Rainer Blind` and `Model & Version: External AI Model (vN/A)` in `sprint_2026-46.4.md` and `sprint_2026-47.1.md`:
  1. *Review Record Schema (`REQ-PRO-062.1`, docs/engineering/reviews/<sprint_or_review>.md)*:
     • Review deliverables SHALL record traceable metadata: Reviewer (`Human` / named human reviewer or external AI vendor), Review Type (`Human Sprint Review (Layer 4)` or `Cross-Vendor AI Review (Layer 5)`), Vendor (`Human` or AI vendor), Model/Version (`Human Process Owner (vN/A)` or AI model), Date/Timestamp, Reviewed Ticket(s) or Commit(s), Scope, and Findings / Verdict (`PASS`, `FAIL`, `SKIPPED`, `NOT PERFORMED`, `PARTIAL`).
     • Review records for skipped or unexecuted ceremonies SHALL explicitly record verdict `SKIPPED` or `NOT PERFORMED` with mandatory justification and process deviation citation (Rule 1.3), prohibiting false success claims. Historical inaccurate records (`docs/engineering/reviews/sprint_2026-46.6.md` corrected to `SKIPPED`; `docs/engineering/reviews/sprint_2026-46.4.md` and `docs/engineering/reviews/sprint_2026-47.1.md` corrected to `Human Sprint Review (Layer 4)`, `Vendor: Human`, `Model & Version: Human Process Owner (vN/A)` for `Reviewer: Rainer Blind`) SHALL carry auditable correction notes preserving original timestamps, ticket lists, and verdicts.
  2. *Independent Review CLI Helper (`REQ-PRO-062.2`, tools/jira_util.py & tools/hoechstmass/tools/jira_util.py)*:
     • Tooling SHALL implement `record-review` (`record_review()`) supporting both Human Sprint Reviews (Layer 4) and Cross-Vendor AI Reviews (Layer 5) with parameters `--sprint`, `--ticket`, `--reviewer`, `--review-type=human|cross-vendor-ai`, `--vendor`, `--model`, `--version`, `--scope`, `--verdict`, and `--findings`.
     • When `--review-type=human` is selected, `--vendor` is `"human"` (case-insensitive), or AI `--vendor`/`--model` are omitted without `--review-type=cross-vendor-ai`, `record_review()` SHALL classify ANY reviewer name (including named humans such as `"Rainer Blind"`) as `Human Sprint Review (Layer 4)` with `Vendor: Human` and `Model & Version: Human Process Owner (vN/A)`, and MUST NOT emit `Cross-Vendor AI Review (Layer 5)` or `External AI Model`.
     • When `--review-type=cross-vendor-ai` is selected, non-empty non-human `--vendor` and `--model` SHALL be mandatory (failing with `ValueError` / exit code `1` if omitted). When `--review-type` is omitted and non-human `--vendor` or `--model` is provided, `record_review()` SHALL infer `cross-vendor-ai` (`Cross-Vendor AI Review (Layer 5)`) for backward compatibility. `verify_independent_reviews` SHALL continue accepting legacy `Human Sprint Review (Ceremony 2)` records.
     • `record-review` SHALL NOT default verdict or findings to positive success claims. Invoking `record-review` with `--verdict PASS` or `FAIL` without `--findings` SHALL fail with a usage error. Setting `--verdict SKIPPED` or `NOT PERFORMED` SHALL require `--findings` containing the rationale and document a process deviation. Allowed verdicts SHALL be strictly validated (`PASS`, `FAIL`, `SKIPPED`, `NOT PERFORMED`, `PARTIAL`).
  3. *Directory Governance (`REQ-PRO-062.3`, ALLOWED_ENGINEERING_ENTRIES & hoechstmass_config.json)*:
     • `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `hoechstmass_config.json` SHALL register `"reviews"`.
  4. *Governance Enforcement CLI (`REQ-PRO-062.4`, tools/verify_requirement_governance.py & tools/hoechstmass/tools/verify_requirement_governance.py)*:
     • `tools/verify_requirement_governance.py` and Höchstmaß mirror SHALL implement `--check-reviews [SPRINT]`, validating existence and schema compliance of review records, recognizing `PASS`, `FAIL`, `SKIPPED`, `NOT PERFORMED`, and `PARTIAL`.
  5. *Autonomous Execution Decoupling (`REQ-PRO-062.5`)*:
     • Autonomous Phase 2 lifecycle execution (`Stage 5 -> Final Review`) SHALL NOT be blocked by missing human review records, preserving automated in-sprint throughput.
  6. *Ceremony 2 Review Mandate (`REQ-PRO-062.6`, .agents/skills/sprint-planner/SKILL.md)*:
     • `sprint-planner/SKILL.md` Ceremony 2 SHALL document recording human review acceptance into `docs/engineering/reviews/` prior to sprint closure.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Modified requirement `REQ-PRO-062` (`REQ-PRO-062.1`, `REQ-PRO-062.2`). Targets `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `docs/engineering/reviews/sprint_2026-46.4.md`, and `docs/engineering/reviews/sprint_2026-47.1.md`.
  2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.3 via `ATT-6155` (`2d2f9663`); amended in Sprint 2026-46.8 via `ATT-8142` (`7ccac4fc`, Finding K2); refined in `ATT-8204` (Audit Gap #7).
  3. *Root Reason for Existing Formulation*: `ATT-6155` assumed human Ceremony 2 calls would pass `--reviewer "Human"` (`reviewer.lower() in ("human", "user")`), and `test_jira_accounts.py:992` relies on omitting `--review-type` when AI `--vendor` + `--model` are supplied.
  4. *Preservation of Core Invariants*: Preserves mandatory `--verdict` + `--findings` validation (`ATT-8142`), `[PROCESS DEVIATION — Rule 1.3]` headers, backward compatibility when `--vendor`/`--model` are supplied without `--review-type`, legacy `Human Sprint Review (Ceremony 2)` compatibility in `verify_independent_reviews()`, zero `app/src/main/` edits, and Rule 3 Human Gate.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `record-review --reviewer "Rainer Blind"` without AI `--vendor`/`--model` (or with `--review-type human` / `--vendor human`),
  • *When* the review markdown is generated in root or Höchstmaß `record_review()`,
  • *Then* `Review Type` is `Human Sprint Review (Layer 4)`, `Vendor` is `Human`, `Model & Version` is `Human Process Owner (vN/A)`, and `External AI Model` / `Cross-Vendor AI Review (Layer 5)` does not appear.
  • *Given* `record-review --review-type cross-vendor-ai` missing `--vendor` or `--model` vs with explicit AI `--vendor` and `--model`,
  • *When* `record_review()` executes,
  • *Then* missing AI metadata raises `ValueError`/exit code `1` while complete AI metadata emits `Cross-Vendor AI Review (Layer 5)`.
  • *Given* `docs/engineering/reviews/sprint_2026-46.4.md` (`PASS`) and `docs/engineering/reviews/sprint_2026-47.1.md` (`NOT PERFORMED`),
  • *When* inspected,
  • *Then* both record `Reviewer: Rainer Blind`, `Review Type: Human Sprint Review (Layer 4)`, `Vendor: Human`, and an auditable `ATT-8204` correction note without altering original timestamps, ticket lists, or verdicts.
  • *Given* a skipped Ceremony 2 sprint review or `record-review` invoked with `--verdict PASS`/`FAIL` without `--findings`,
  • *When* `record-review` executes,
  • *Then* missing `--findings` fails with exit code `1`, `SKIPPED`/`NOT PERFORMED` records the Rule 1.3 deviation, and `verify_requirement_governance.py --check-reviews` passes.
- **Invariants**: Zero modifications to Android production runtime code in `app/src/main/`; pure Python standard library retained; `Localization Parity: N/A - no UI strings`; Rule 3 (`Erledigt` human-only gate) preserved.

### REQ-PRO-063: Problem Severity Classification & Defect Trend Metrics [Verified]
- **Tests**: `TST-PRO-063`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: Mandatory severity validation in `create_issue` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`, severity labeling (`severity-<level>`), problem trend metrics extraction in `tools/sprint_metrics.py`, and JSONL serialization to `docs/engineering/metrics/process_metrics.jsonl`.
- **Rationale**: Under ASPICE SUP.9 (Problem Resolution Management) and MAN.3 (Project Monitoring and Control), problem resolution requires classifying defects by severity independently of scheduling priority and tracking problem trends across iterations (Pre-Assessment Finding B-11). Previously, Jira issues recorded priority (`Highest`..`Lowest`) but lacked mandatory severity categorization (`Critical`, `Major`, `Minor`, `Cosmetic`), and quantitative metrics lacked defect trend telemetry:
  1. *Mandatory Severity on Bug Creation (`REQ-PRO-063.1`, tools/jira_util.py & tools/hoechstmass/tools/jira_util.py)*:
     • When creating an issue of type `Bug` / `Fehler` (`10006`) via `create_issue()` or `jira_util.py create-issue`, specifying a valid severity level (`Critical`, `Major`, `Minor`, `Cosmetic`) SHALL be mandatory. Omission MUST cause immediate validation failure with exit code 1.
     • Non-bug issue types (Feature, Improvement, Story, Task, Epic) SHALL NOT require a severity parameter.
  2. *Severity Labeling & Metadata (`REQ-PRO-063.2`, tools/jira_util.py & tools/hoechstmass/tools/jira_util.py)*:
     • When a Bug is created with a valid severity, the tooling SHALL append a standardized label `severity-<level>` (e.g. `severity-critical`) and embed severity metadata in the issue description.
  3. *Problem Trend Metrics Extraction (`REQ-PRO-063.3`, tools/sprint_metrics.py & tools/hoechstmass/tools/sprint_metrics.py)*:
     • `parse_sprint_gate_metrics()` SHALL identify all Bug issues in the sprint, aggregate counts by severity (`Critical`, `Major`, `Minor`, `Cosmetic`), track `open_bugs` vs `closed_bugs`, and calculate sprint defect escape and resolution rate metrics.
  4. *Quantitative Process Metrics Schema (`REQ-PRO-063.4`, docs/engineering/metrics/process_metrics.jsonl)*:
     • Sprint metrics records serialized to `docs/engineering/metrics/process_metrics.jsonl` SHALL include the `problem_trends` object containing `total_bugs`, `by_severity`, `open_bugs`, `closed_bugs`, and `resolution_rate_percent`.
  5. *Markdown Report Formatting (`REQ-PRO-063.5`, tools/sprint_metrics.py & tools/hoechstmass/tools/sprint_metrics.py)*:
     • `format_metrics_markdown()` SHALL render a dedicated "Problem Trends & Defect Severity" section displaying bug counts and resolution rates.
  6. *Governance Verification CLI (`REQ-PRO-063.6`, tools/verify_requirement_governance.py)*:
     • `verify_sprint_metrics()` in `tools/verify_requirement_governance.py` and Höchstmaß mirror SHALL accept and validate the `problem_trends` schema fields.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-063` (`REQ-PRO-063.1`–`REQ-PRO-063.6`). Targets `tools/jira_util.py`, `tools/sprint_metrics.py`, `docs/engineering/metrics/process_metrics.jsonl`, and `tools/hoechstmass/`.
  2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.3 via ATT-6156 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-11.
  3. *Root Reason for Existing Formulation*: Sprints previously lacked mandatory defect severity categorization and quantitative problem trend telemetry.
  4. *Preservation of Core Invariants*: Zero changes to Android runtime code; pure Python 3 standard library; backward compatibility for non-bug issue creation preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a new Bug issue being created via `jira_util.py create-issue`,
  • *When* `--severity` is omitted,
  • *Then* creation is rejected with exit code 1 and error message detailing mandatory severity levels.
  • *Given* a new Bug issue being created with `--severity=critical`,
  • *When* created,
  • *Then* the issue is created with label `severity-critical` and description metadata.
  • *Given* a non-bug issue being created,
  • *When* created without severity,
  • *Then* creation succeeds normally.
  • *Given* sprint issues processed by `sprint_metrics.py`,
  • *When* `parse_sprint_gate_metrics()` runs,
  • *Then* it outputs `problem_trends` with bug counts by severity and resolution rates.
- **Invariants**: Zero modifications to Android production runtime code in `app/src/main/`; pure Python standard library retained; Rule 3 (`Erledigt` human-only gate) preserved.

### REQ-PRO-064: Explicit Change-Request Impact Analysis Governance [Verified]
- **Tests**: `TST-PRO-064`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: Standardized `Impact Analysis` section in Stage 1 templates (`analysis_template.md`), automated schema validation `validate_impact_analysis_section()` in `tools/verify_requirement_governance.py` and `tools/hoechstmass/`, and Gate 1 checklist and CLI enforcement in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
- **Rationale**: Under ASPICE SUP.10 (Change Request Management) and Pre-Assessment Finding B-12, change request impact analysis must be formally and explicitly documented before Stage 1 approval rather than remaining implicit or scattered across analysis text. Formal evaluation of affected requirements, architecture components, verification test surface, and risks prevents downstream architecture drift and unexpected regressions:
  1. *Standardized Impact Analysis Template (`REQ-PRO-064.1`)*:
     • Stage 1 analysis templates in `.agents/skills/stage1-analysis/templates/analysis_template.md` and `tools/hoechstmass/templates/analysis_template.md` SHALL feature a dedicated "Change Impact Analysis" section mandating 4 discrete dimensions: Affected Requirements, Affected Architecture Elements, Affected Tests / Verification Impact, and Risk Assessment & Invariants.
  2. *Automated Impact Analysis Verifier (`REQ-PRO-064.2`, tools/verify_requirement_governance.py)*:
     • `tools/verify_requirement_governance.py` and Höchstmaß mirror SHALL implement `validate_impact_analysis_section(text)` and `verify_stage1_impact_analysis(analysis_file=None, repo_root=None)` with `--check-impact [FILE]`, validating that the section header exists and all 4 dimensions are populated with non-empty, non-placeholder content.
  3. *Gate 1 Automated & Checklist Enforcement (`REQ-PRO-064.3`, tools/jira_util.py)*:
     • The Gate 1 review process in `.agents/skills/stage1-analysis/SKILL.md` and `gate_verdict` in `tools/jira_util.py` (and Höchstmaß mirror) SHALL mandate that `gate-verdict <SUBTASK> PASS` on `[Analysis]` subtasks validates the presence and completeness of the Impact Analysis section, rejecting incomplete deliverables with an actionable error.
  4. *Core Invariants & Non-Analysis Gate Independence (`REQ-PRO-064.4`)*:
     • Gate verdict execution for non-analysis subtasks (Gates 2, 3, 4, 5) SHALL NOT be constrained by Gate 1 impact analysis checks; zero modifications to Android production runtime code in `app/src/main/`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-064` (`REQ-PRO-064.1`–`REQ-PRO-064.4`). Targets Stage 1 templates, `tools/verify_requirement_governance.py`, `tools/jira_util.py`, and `tools/hoechstmass/`.
  2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.3 via ATT-6157 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-12.
  3. *Root Reason for Existing Formulation*: Impact analysis was previously implicit or omitted in Stage 1 analyses.
  4. *Preservation of Core Invariants*: Zero changes to Android runtime code; pure Python 3 standard library; non-analysis gate workflows unaffected.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a Stage 1 analysis deliverable,
  • *When* `validate_impact_analysis_section()` runs,
  • *Then* it passes if and only if all 4 dimensions (Requirements, Architecture, Tests, Risks) are explicitly specified with non-empty content.
  • *Given* an `[Analysis]` subtask submitted for Gate 1 review,
  • *When* `gate-verdict` is called with `PASS`,
  • *Then* it verifies the deliverable contains a valid Impact Analysis section before allowing transition to Erledigt.
  • *Given* a non-analysis subtask (Gates 2..5),
  • *When* `gate-verdict` is called,
  • *Then* it executes normally without requiring Stage 1 impact analysis format.
- **Invariants**: Zero modifications to Android production runtime code in `app/src/main/`; pure Python standard library retained; Rule 3 (`Erledigt` human-only gate) preserved.

### REQ-PRO-065: Living Documentation Sharding Parser Code-Span Pipe Immunity & Master Index Safety Guard [Verified]
- **Tests**: `TST-PRO-065`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: Robust character tokenizer `split_markdown_table_row(row)` in `tools/shard_living_docs.py`, 6-column and 7-column normalization in `parse_requirements_file` and `parse_tests_file`, and Master Index overwrite abort guard in `main()`.
- **Rationale**: Monolithic living documentation parsing in `tools/shard_living_docs.py` previously used naive `row.split("|")[1:-1]`. When table cells contain backticked code spans with pipes or logical OR (`||`, e.g. `code == 200 || code == 201`), raw pipe splitting injects extraneous columns, shifting column assignments and corrupting the `Tests` field with string fragments while discarding downstream columns. Furthermore, running the tool without guards when `docs/requirements.md` is already a Master Index would wipe all domain shards:
  1. *Code-Span & Escaped Pipe Immunity (`REQ-PRO-065.1`)*:
     • `tools/shard_living_docs.py` SHALL implement a table row tokenizer `split_markdown_table_row(row)` that parses table row cells by `|` while ignoring escaped pipes (`\|`) and any pipe characters residing inside inline code spans delimited by single or multiple backticks (`` `...` ``, ```` ``...`` ````).
  2. *Column Count Normalization (`REQ-PRO-065.2`)*:
     • `parse_requirements_file()` and `parse_tests_file()` SHALL handle both 6-column (`ID`, `Description`, `Rationale`, `Implementation`, `Tests`, `Status`) and 7-column (`ID`, `Title`, `Description`, `Rationale`, `Implementation`, `Tests`, `Status`) rows without index shifting.
  3. *Master Index Overwrite Safety Guard (`REQ-PRO-065.3`)*:
     • `tools/shard_living_docs.py` SHALL inspect the target source files (`docs/requirements.md` and `docs/tests.md`) before sharding. If either file is already a Master Index (e.g. contains `(Master Index)` or contains 0 requirement/test table rows), the tool SHALL abort immediately with exit code 1 and an explanatory error, unless an explicit `--force` flag is provided.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-065` (`REQ-PRO-065.1`–`REQ-PRO-065.3`). Targets `tools/shard_living_docs.py` and `tools/test_shard_living_docs.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6124` under Epic `ATT-6119`.
  3. *Root Reason for Existing Formulation*: Sharding was created in ATT-4594 without code-span tokenizer or Master Index detection.
  4. *Preservation of Core Invariants*: Existing 9-domain shard structure, 100% ID parity, multi-line living doc formatting, and zero changes to Android runtime code in `app/src/main/`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a markdown table row containing backticks with `||` (e.g. `binary \`code == 200 || code == 201\` check`),
  • *When* `split_markdown_table_row()` executes,
  • *Then* the pipe characters inside the backticks SHALL NOT split the cell, and the resulting column count SHALL match the true table schema without shifting.
  • *Given* an execution of `tools/shard_living_docs.py` against a repository where `docs/requirements.md` is a Master Index,
  • *When* run without `--force`,
  • *Then* the process SHALL abort with exit code 1 and prevent overwriting of domain shards.
  • *Given* regression test reproducing the pre-fix naive `row.split("|")` behavior,
  • *When* executed on the old splitting logic,
  • *Then* it fails (proving reproduction), and passes on `split_markdown_table_row()`.
- **Invariants**: Sharding preserves cell contents byte-identically; zero Android runtime changes; pure Python standard library.

### REQ-PRO-066: Living Documentation Unique Requirement & Test Identifier Governance [Verified]
- **Tests**: `TST-PRO-066`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py` (`verify_unique_ids()`, `--fail-on-duplicates`, `--enforce-unique`), `tools/test_verify_requirement_governance.py`.
- **Rationale**: Requirement and test identifiers (`### REQ-*`, `### TST-*`) across domain shards in `docs/requirements/` and `docs/tests/` must be globally unique to guarantee unambiguous bidirectional traceability and reliable ASPICE evidence tracking. Duplicate IDs cause link ambiguity in verification matrices and prevent automated traceability tooling from accurately resolving test coverage:
  1. *Duplicate Heading Detection (`REQ-PRO-066.1`)*:
     • `tools/verify_requirement_governance.py` SHALL scan all `.md` shard files under `docs/requirements/` and `docs/tests/` for requirement (`### REQ-*`) and test (`### TST-*`) heading identifiers.
     • For every identifier encountered more than once across or within domain shards, the verifier SHALL report each duplicate instance specifying its identifier, file path, and line number.
  2. *Legacy Duplicate Baseline Allowlist & CI Non-Regression (`REQ-PRO-066.2`)*:
     • To prevent breaking active CI during concurrent development prior to the remediation of the 11 known legacy duplicate IDs in ATT-6123, `tools/verify_requirement_governance.py` SHALL maintain a controlled baseline allowlist (`LEGACY_DUPLICATE_IDS`) of the 11 known pre-existing duplicates.
     • When running in standard verification mode, known legacy duplicates SHALL emit warning diagnostics without failing the process. Any net-new duplicate (an identifier collision not present in the legacy allowlist) SHALL immediately fail verification with exit code 1.
  3. *Strict Uniqueness Enforcement Flag (`REQ-PRO-066.3`)*:
     • `tools/verify_requirement_governance.py` SHALL support a command-line flag (`--enforce-unique` / `--fail-on-duplicates`) that activates strict zero-tolerance mode, causing verification to fail with exit code 1 whenever any duplicate identifier (including legacy duplicates) is detected.
  4. *Remediation of Legacy Duplicate Identifiers (`REQ-PRO-066.4`)*:
     • In ATT-6123, all 11 legacy duplicate heading identifiers SHALL be renumbered to unique sequential IDs (`REQ-DAT-043`, `REQ-DAT-044`, `REQ-MIG-051`, `REQ-UI-521`, `REQ-UI-522`, `REQ-UI-523`, `TST-DAT-039`, `TST-DAT-040`, `TST-MAP-070`, `TST-MIG-049`, `TST-UI-519`) with all test citations, code tags, and tooling synchronized, and `LEGACY_DUPLICATE_IDS` SHALL be emptied to enforce strict zero tolerance globally.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-066` (`REQ-PRO-066.1`–`REQ-PRO-066.4`). Targets `tools/verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6120` and `ATT-6123` under Epic `ATT-6119` (ASPICE CL2 – Evidence Integrity & Bidirectional Traceability).
  3. *Root Reason for Existing Formulation*: ASPICE 4.0 assessment of 2026-11-08 identified 11 undetected duplicate IDs (SWE.1 BP5, SWE.6 BP4).
  4. *Preservation of Core Invariants*: Existing master table parity, directory structure checks, Composable islands, repository encapsulation, and Chesterton's Fence archaeology checks remain untouched; pure Python 3 standard library; zero production Android runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* two heading definitions in `docs/requirements/*.md` or `docs/tests/*.md` share an ID,
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it outputs an error listing the duplicate ID with all offending filenames and line numbers, and exits with code 1.
  • *Given* all headings have unique IDs,
  • *When* `verify_requirement_governance.py` runs (with or without `--enforce-unique`),
  • *Then* it outputs `PASS: All REQ and TST heading IDs are unique across living documentation shards.` and exits with code 0 (passing).
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-067: JUnit Test Results Archival & Evidence Integrity [Verified]
- **Tests**: `TST-PRO-067`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
- **Rationale**: Under ASPICE SWE.4 BP, SWE.6 BP3, and SUP.8 (Remediation ID: A-10), verification evidence must be baseline-controlled, integrity-preserved, and retrievable per CI run and release tag. Local transient test reports via `docs/engineering/test_results` provide effortless developer inspection, while CI artifact uploads and release tag attachments ensure persistent, auditable verification evidence:
  1. *CI Branch Run JUnit Archival (`REQ-PRO-067.1`, .github/workflows/android-branch-build.yml)*:
     • `.github/workflows/android-branch-build.yml` SHALL execute an `actions/upload-artifact@v4` step with `if: always()` immediately following unit tests, archiving both JUnit XML reports (`app/build/test-results/testDebugUnitTest/`) and HTML test reports (`app/build/reports/tests/testDebugUnitTest/`).
  2. *Release Tag JUnit Test Results Packaging & Attachment (`REQ-PRO-067.2`, .github/workflows/android-release.yml)*:
     • `.github/workflows/android-release.yml` SHALL package unit test results into a versioned archive `junit-test-results-<tag>.zip` (or `.tar.gz`) named with the release tag and attach it to the GitHub Release via `softprops/action-gh-release@v2` alongside release APK and AAB binaries.
     • The release workflow SHALL also upload the test results as a workflow artifact.
  3. *Transition to Versioned Test Evidence Archival (`REQ-PRO-067.3`, docs/engineering/test_evidence)*:
     • The ephemeral `docs/engineering/test_results` symlink into transient build outputs is retired and replaced with persistent, version-controlled test and coverage evidence in `docs/engineering/test_evidence/` per `REQ-PRO-100`.
  4. *Governance & Contract Verification (`REQ-PRO-067.4`, ReleaseCiAndLintConfigContractTest.kt & tools/verify_requirement_governance.py)*:
     • `ReleaseCiAndLintConfigContractTest.kt` and `tools/verify_requirement_governance.py` SHALL verify workflow YAML presence and configuration, ensure `if: always()` is configured for CI unit test uploads, verify release archive attachment patterns, and enforce canonical layout rules for `docs/engineering/test_evidence/`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Modified requirement `REQ-PRO-067` (`REQ-PRO-067.3`, `REQ-PRO-067.4`). Targets `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `docs/engineering/test_results` (retired), `docs/engineering/test_evidence/`, `tools/verify_requirement_governance.py`, and `ReleaseCiAndLintConfigContractTest.kt`.
  2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.4 via ATT-6137 under Epic ATT-6119 (ASPICE CL2 Remediation A-10); amended in Sprint 2026-46.8 via ATT-8144 under Epic ATT-8138 (ASPICE Finding M7).
  3. *Root Reason for Existing Formulation*: Historical CI workflows only uploaded lint and build binaries; local symlink provided quick inspection but lacked versioning and git baseline control.
  4. *Preservation of Core Invariants*: Local test inspection preserved via compact versioned markdown and JSON summaries in `docs/engineering/test_evidence/`; immutable baseline control achieved; zero production runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a CI branch build executing unit tests,
  • *When* the test execution step completes (pass or fail),
  • *Then* `actions/upload-artifact@v4` uploads JUnit XML and HTML reports (`if: always()`).
  • *Given* a release build triggered by a release tag,
  • *When* unit tests complete and release assets are published,
  • *Then* a versioned `junit-test-results-<tag>.zip` (or `.tar.gz`) archive is attached to the GitHub Release.
  • *Given* the repository checkout,
  • *When* inspecting `docs/engineering/`,
  • *Then* the ephemeral `test_results` symlink is retired and replaced with versioned evidence under `docs/engineering/test_evidence/`.
  • *Given* the automated contract tests and governance script,
  • *When* executed,
  • *Then* canonical directory structure and workflow YAML validity pass with 100% success.
- **Invariants**: Zero modifications to Android production runtime code in `app/src/main/`; Rule 67 canonical directory structure preserved.

### REQ-PRO-068: Developer Sensor Simulator Self-Test Harness & Honest System Boundary Specification [Verified]
- **Tests**: `TST-PRO-068`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemIntegrationTestContractTest.kt`, `docs/system/sys_integration_tests.md`
- **Rationale**: Under ASPICE SYS.4 / SUP.1 (Finding S-06, remediated for M6 honesty under `ATT-8157`), sensor simulator tools (`tools/ble_hr_simulator.py`, `tools/ble_power_simulator.py`, `tools/ble_radar_simulator.py`, `tools/fake_gps.py`, `tools/replay_workout.py`) require an explicit developer simulator self-test harness and honest documentation separating pure-Python simulator self-tests from real Android software integration (`INT-SWE-01..08`, `AppSmokeAndroidTest.kt`) and physical hardware-in-the-loop verification (`SYS.5`):
  1. *System Integration Boundary & Gap Disclosure (`REQ-PRO-068.1`, docs/system/sys_integration_tests.md)*:
     • `docs/system/sys_integration_tests.md` SHALL document external interfaces (`SYS-IF-01` BLE, `SYS-IF-02` ANT+, `SYS-IF-03` GNSS, `SYS-IF-05` Cloud), trace to `SYSREQ-TRK-*`, `SYSREQ-DEX-*`, and `SYS-EL-01..05`, and explicitly disclose that headless Python simulator tests do NOT execute the Android APK or physical RF hardware.
  2. *Simulator Self-Test Runner (`REQ-PRO-068.2`, tools/run_simulator_self_tests.py)*:
     • Renamed from `tools/run_system_integration_tests.py`, `tools/run_simulator_self_tests.py` SHALL execute deterministic pure-Python self-tests against the BLE simulator modules (`ble_hr_simulator`, `ble_power_simulator`, `ble_radar_simulator`) without claiming constant-arithmetic stubs represent Android integration or blocking releases (`--output` JSON optional).
  3. *Simulator Self-Test Runner Unit Suite (`REQ-PRO-068.3`, tools/test_simulator_self_tests.py)*:
     • Renamed from `tools/test_system_integration_tests.py`, `tools/test_simulator_self_tests.py` SHALL verify simulator self-test handlers, CLI `--output` arguments, and pure Python 3 stdlib compliance using `unittest`.
  4. *Contract Testing & Governance (`REQ-PRO-068.4`, SystemIntegrationTestContractTest.kt & verify_requirement_governance.py)*:
     • `SystemIntegrationTestContractTest.kt` SHALL verify `docs/system/sys_integration_tests.md` structural completeness, honest simulator-only gap disclosure, interface traceability, and existence of the simulator tools and `tools/run_simulator_self_tests.py` / `tools/test_simulator_self_tests.py`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PRO-068` (`REQ-PRO-068.1`–`REQ-PRO-068.4`). Targets `docs/system/sys_integration_tests.md`, `tools/run_system_integration_tests.py` (renamed to `tools/run_simulator_self_tests.py`), `tools/test_system_integration_tests.py` (renamed to `tools/test_simulator_self_tests.py`), and `SystemIntegrationTestContractTest.kt`.
  2. *Historical Origin & Commit Trace*: Originally created in Sprint 2026-46.4 via `ATT-6166` (`ATT-6142`); rescoped in Sprint `2026-11-17_01_Früh` via `ATT-8157` (`REQ-PRO-112`).
  3. *Root Reason for Existing Formulation*: `ATT-6166` sought repeatable headless sensor simulator checks in CI, but named and framed them as SYS.4 system integration tests (`SYS-INT-001..006`) despite executing zero Android code or emulator ADB workflows.
  4. *Preservation of Core Invariants*: Preserves pure-Python simulator self-testing, external interface traceability (`SYS-IF-01..05`, `SYSREQ-TRK-*`, `SYSREQ-DEX-*`), and zero `app/src/main/` modifications while eliminating misleading SYS.4 claims.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/system/sys_integration_tests.md`,
  • *When* inspected,
  • *Then* it documents external interfaces (`SYS-IF-01`..`05`), `SYSREQ-*` traceability, and explicitly classifies the Python runner as non-gating simulator self-tests alongside `INT-SWE-01..08` and manual `SYS.5`.
  • *Given* `tools/run_simulator_self_tests.py` and `tools/test_simulator_self_tests.py`,
  • *When* executed in Python,
  • *Then* simulator self-tests and `unittest` cases pass with exit code 0 using `--output` JSON export when requested.
  • *Given* `SystemIntegrationTestContractTest.kt`,
  • *When* executed via Gradle,
  • *Then* all contract tests pass with 100% success.
- **Invariants**: Zero Android production runtime code changes in `app/src/main/`; existing simulator scripts remain fully functional.

### REQ-PRO-069: System Verification Protocol & Real Sensor Hardware Reference Matrix Specification [Verified]
- **Tests**: `TST-PRO-069`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemVerificationProtocolContractTest.kt`
- **Rationale**: Under ASPICE SYS.5 (Finding S-07), system verification must validate product capabilities against system requirements (`SYSREQ-TRK-*`, `SYSREQ-DEX-*`) in the real operational target environment using a standardized hardware reference matrix, structured verification cases across diverse environmental conditions, objective pass/fail criteria, and formal release sign-off record templates:
  1. *Hardware Reference Matrix (`REQ-PRO-069.1`, docs/system/sys_verification_protocol.md)*:
     • `docs/system/sys_verification_protocol.md` SHALL specify a physical hardware reference matrix covering reference mobile devices (Google Pixel reference family, Android 14/15), BLE Heart Rate monitors (Polar H10, Garmin HRM-Pro), BLE/ANT+ Power meters (Favero Assioma Duo, Stages, Wahoo KICKR), Garmin Varia Cycling Radar (RTL515), and Barometric Altimeter / Multi-GNSS hardware.
  2. *Environmental Operating Conditions Matrix (`REQ-PRO-069.2`, docs/system/sys_verification_protocol.md)*:
     • The protocol SHALL define 5 quantified real-world operating environments: (E1) Urban Canyon (multipath GNSS reflection, building obstruction), (E2) Rural Road / High Speed (high-velocity vehicle approaches, continuous RF tracking), (E3) Alpine Climb & Barometric Variation (rapid elevation gradient $\ge 15\%$, pressure changes), (E4) Indoor Smart Trainer (stationary power/cadence, absence of GPS movement), and (E5) Extended Endurance & Battery Longevity ($\ge 3$ hours continuous multi-sensor tracking, battery drain rate $\le 8\%$/hr).
  3. *Structured System Verification Cases (`REQ-PRO-069.3`, docs/system/sys_verification_protocol.md)*:
     • The protocol SHALL specify formal system verification test cases (`SYS-VRF-001` through `SYS-VRF-008`) covering all system requirements (`SYSREQ-TRK-001..006` and `SYSREQ-DEX-001..006`).
     • Each verification case SHALL define prerequisite hardware/software setup, execution procedure, verifiable sensor telemetry, objective pass/fail criteria, and error recovery handling.
  4. *Data Integrity, Reconnection & Release Sign-Off Record (`REQ-PRO-069.4`, docs/system/sys_verification_protocol.md & docs/releases/<tag>/sys_verification_record.md)*:
     • The protocol SHALL define objective pass/fail criteria for data integrity (FIT/TCX export schema validity, Strava/Dropbox synchronization without corruption, sensor reconnection after RF dropout within exponential backoff schedule, $\le 1\%$ sample drop rate).
     • A release sign-off record template SHALL be provided under `docs/releases/<tag>/sys_verification_record.md` to record executed test cases, device/firmware serials, environment conditions, results, and approval signatures.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-069` (`REQ-PRO-069.1`–`REQ-PRO-069.4`). Targets `docs/system/sys_verification_protocol.md`, `docs/architecture.md`, and `SystemVerificationProtocolContractTest.kt`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6167` under Epic `ATT-6142` (ASPICE CL2 Remediation S-07).
  3. *Root Reason for Existing Formulation*: ASPICE 4.0 assessment of 2026-11-08 rated SYS.5 as CL0 due to reliance on informal, ad-hoc walkthroughs without release-level system verification against system requirements.
  4. *Preservation of Core Invariants*: Zero modifications to Android production runtime code in `app/src/main/`; pure governance and verification tooling.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/system/sys_verification_protocol.md`,
  • *When* reviewed against SYS.5,
  • *Then* it contains the hardware reference matrix, environmental conditions matrix, verification cases covering all `SYSREQ-TRK-*` and `SYSREQ-DEX-*` requirements, objective criteria, and release sign-off template.
  • *Given* `SystemVerificationProtocolContractTest.kt`,
  • *When* executed via Gradle,
  • *Then* all contract assertions pass with 100% success.
- **Invariants**: Zero Android production runtime code changes in `app/src/main/`.

### REQ-PRO-070: Living Documentation Bidirectional Reference Integrity Governance [Verified]
- **Tests**: `TST-PRO-070`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py` (`find_dangling_references()`, `verify_dangling_references()`, `--fail-on-dangling`, `--enforce-links`), `tools/test_verify_requirement_governance.py`.
- **Rationale**: Citations between requirements and test cases across domain shards in `docs/requirements/` and `docs/tests/` must point to authentic, existing target headings (`### REQ-*`, `### TST-*`) to guarantee evidence integrity, bidirectional traceability, and ASPICE SWE.1 BP5 / SWE.6 BP4 compliance. Dangling references (pointing to non-existent tests or non-existent requirements) compromise traceability and prevent automated verification of test coverage:
  1. *Dangling TST Citation Detection in Requirements (`REQ-PRO-070.1`)*:
     • `tools/verify_requirement_governance.py` SHALL extract all cited test identifiers (`TST-*`) from the `Tests` field of every requirement in `docs/requirements/*.md` and verify that each cited test matches an existing `### TST-*` heading in `docs/tests/*.md`.
     • Any missing target identifier SHALL be flagged as a dangling test reference with source file, line number, requirement ID, and missing test ID.
  2. *Dangling REQ Citation Detection in Tests (`REQ-PRO-070.2`)*:
     • `tools/verify_requirement_governance.py` SHALL extract all cited requirement identifiers (`REQ-*`) from the `Requirements` (or `Requirement(s)`) field of every test in `docs/tests/*.md` and verify that each cited requirement matches an existing `### REQ-*` heading in `docs/requirements/*.md`.
     • Any missing target identifier SHALL be flagged as a dangling requirement reference with source file, line number, test ID, and missing requirement ID.
  3. *Legacy Dangling Reference Allowlist & CI Non-Regression (`REQ-PRO-070.3`)*:
     • To maintain non-breaking CI compatibility while existing legacy dangling links await scheduled remediation in A-08.x (ATT-6127..6135), `tools/verify_requirement_governance.py` SHALL maintain controlled baseline allowlists (`LEGACY_DANGLING_TST_REFS`, `LEGACY_DANGLING_REQ_REFS`).
     • In standard verification mode, known legacy dangling references SHALL emit warning diagnostics without failing the process. Any net-new dangling reference (outside the allowlists) SHALL immediately fail verification with exit code 1.
  4. *Strict Link Enforcement Flag (`REQ-PRO-070.4`)*:
     • `tools/verify_requirement_governance.py` SHALL support a command-line flag (`--enforce-links` / `--fail-on-dangling`) that triggers exit code 1 if ANY dangling reference (including legacy allowlisted references) is detected.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-070` (`REQ-PRO-070.1`–`REQ-PRO-070.4`). Targets `tools/verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6121` under Epic `ATT-6119` (ASPICE CL2 – Evidence Integrity & Bidirectional Traceability).
  3. *Root Reason for Existing Formulation*: ASPICE 4.0 assessment of 2026-11-08 identified 8 dangling TST citations and 11 dangling REQ citations (SWE.1 BP5, SWE.6 BP4).
  4. *Preservation of Core Invariants*: Existing master table parity, unique ID checks, architecture validation, Composable island checks, and repository encapsulation remain untouched; pure Python 3 standard library; zero production Android runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a requirement cites a non-existent `TST-*` ID or a test cites a non-existent `REQ-*` ID outside the legacy allowlist,
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it outputs an error identifying the dangling reference with source file, line number, and missing ID, and exits with code 1.
  • *Given* `--enforce-links` or `--fail-on-dangling` is enabled and any dangling link exists,
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it fails with exit code 1 reporting all dangling references.
  • *Given* all citations resolve to existing headings (or match the legacy allowlist in default mode),
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it outputs a PASS diagnostic and exits with code 0.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-071: Living Documentation Bidirectional REQ-TST Traceability & Code Coverage Reporting [Verified]
- **Tests**: `TST-PRO-071`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py` (`calculate_traceability_coverage()`, `--coverage`, `--coverage-json`, `--min-req-tst-coverage`, `--min-tst-code-coverage`, `--max-code-without-spec`, `--min-bidirectional-coverage`), `tools/test_verify_requirement_governance.py`.
- **Rationale**: ASPICE SWE.6 BP4 (Verify Software Unit) and SWE.4 (Software Unit Verification) require bidirectional traceability between software requirements, test specifications, and verified test code. Coverage reporting identifies gaps and allows CI ratcheting so progress can be monitored and gated:
  1. *Category 1: REQs Without TST Citation (`REQ-PRO-071.1`)*:
     • `tools/verify_requirement_governance.py` SHALL scan all `docs/requirements/*.md` shards and identify all requirements whose `Tests` field contains no valid test citation (empty, None, or absent).
  2. *Category 2: TSTs Without Test Code (`REQ-PRO-071.2`)*:
     • `tools/verify_requirement_governance.py` SHALL scan all `docs/tests/*.md` shards and identify all `### TST-*` headings that have no corresponding test code tag in `app/src/test/`.
  3. *Category 3: Code Tags Without Spec (`REQ-PRO-071.3`)*:
     • `tools/verify_requirement_governance.py` SHALL scan `app/src/test/` for all test code tags (`TST-*`) and identify any tagged base test IDs that do not exist as valid headings in `docs/tests/*.md`.
  4. *Category 4: TSTs Not Cited Back by Their REQ (`REQ-PRO-071.4`)*:
     • `tools/verify_requirement_governance.py` SHALL analyze cross-citations and identify every test specification (`TST-*`) whose cited requirement does not cite that test back in its `Tests` field.
  5. *Structured Report Generation & Configurable Threshold Gates (`REQ-PRO-071.5`)*:
     • `tools/verify_requirement_governance.py` SHALL support generating structured Markdown and JSON reports detailing counts, totals, and percentages for all 4 categories.
     • The verifier SHALL support configurable threshold ratchets (`--min-req-tst-coverage`, `--min-tst-code-coverage`, `--max-code-without-spec`, `--min-bidirectional-coverage`) to enforce CI quality gates.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-071` (`REQ-PRO-071.1`–`REQ-PRO-071.5`). Targets `tools/verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6122` under Epic `ATT-6119` (ASPICE CL2 – Evidence Integrity & Bidirectional Traceability).
  3. *Root Reason for Existing Formulation*: ASPICE 4.0 pre-assessment identified 581 TSTs never cited back, 273 spec TSTs without test code, and 159 code tags missing from spec.
  4. *Preservation of Core Invariants*: Existing verifier routines (table parity, duplicate IDs, dangling references, tripwires) remain untouched; pure Python 3 standard library; zero Android runtime modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `--coverage` is invoked on the repository,
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it outputs a Markdown matrix report of the 4 gap categories with counts, totals, and percentages.
  • *Given* `--coverage-json` is specified,
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it outputs structured JSON containing exact metric numbers and category item listings.
  • *Given* a coverage threshold is configured and repository telemetry falls below that threshold,
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it exits with code 1 and outputs a descriptive gate violation error.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-072: Release Manager System Verification Evidence Integration & Release Gating Governance [Verified]
- **Tests**: `TST-PRO-072`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, `docs/architecture.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseManagerSystemEvidenceContractTest.kt`
- **Rationale**: Under ASPICE SYS.5 and SUP.8 (Finding S-08, updated for evidence honesty under `ATT-8157`), release decisions and release checklists must be gated strictly on real software/integration verification (`testDebugUnitTest` including `INT-SWE-01..08`, CI `AppSmokeAndroidTest.kt`) and authentic hardware-in-the-loop SYS.5 system verification records (`docs/releases/<tag>/sys_verification_record.md`), rather than synthetic Python simulator JSON (`system_integration_results.json`):
  1. *Mandatory System Evidence Gate Specification (`REQ-PRO-072.1`, .agents/skills/release-manager/SKILL.md)*:
     • `.agents/skills/release-manager/SKILL.md` SHALL specify mandatory release verification gates requiring unit/software integration tests (`INT-SWE-01..08` + CI `AppSmokeAndroidTest.kt`), SYS.5 System Verification Protocol sign-off (`docs/releases/<tag>/sys_verification_record.md` per `docs/system/sys_verification_protocol.md`), and `docs/releases/<tag>/verification_summary.md` — without mandating `system_integration_results.json` as a blocking release gate.
  2. *Release Tagging Blocking Gate (`REQ-PRO-072.2`, .agents/skills/release-manager/SKILL.md)*:
     • The release checklist and step-by-step procedures in `.agents/skills/release-manager/SKILL.md` SHALL explicitly block creating release candidate tags (`V<versionName>__<versionCode>`) and Google Play Store distribution if `sys_verification_record.md` or `verification_summary.md` is missing, incomplete, or contains failing verdicts / unapproved deviations.
  3. *Standardized Release Evidence Directory & Template (`REQ-PRO-072.3`, docs/releases/)*:
     • `docs/releases/README.md` and `docs/releases/TEMPLATE.md` SHALL define `docs/releases/<tag>/` layout (`sys_verification_record.md`, `verification_summary.md`, optional `release_dossier.md`), explicitly marking `system_integration_results.json` retired/non-gating.
  4. *Release Manager System Evidence Contract Testing (`REQ-PRO-072.4`, ReleaseManagerSystemEvidenceContractTest.kt)*:
     • `ReleaseManagerSystemEvidenceContractTest.kt` SHALL verify that `.agents/skills/release-manager/SKILL.md`, `docs/releases/`, and `docs/architecture.md` §6.4 enforce `SYS.5` / `verification_summary.md` blocking gates and do NOT mandate fake `system_integration_results.json` gating.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PRO-072` (`REQ-PRO-072.1`–`REQ-PRO-072.4`). Targets `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, `docs/architecture.md`, and `ReleaseManagerSystemEvidenceContractTest.kt`.
  2. *Historical Origin & Commit Trace*: Originally created in Sprint 2026-46.4 via `ATT-6169` (`ATT-6142`); updated in Sprint `2026-11-17_01_Früh` via `ATT-8157` (`REQ-PRO-112`).
  3. *Root Reason for Existing Formulation*: `ATT-6169` gated releases on `system_integration_results.json` alongside `sys_verification_record.md`, but `system_integration_results.json` came from pure-Python simulator arithmetic checks and `SKILL.md` even passed an invalid `--output-json` flag.
  4. *Preservation of Core Invariants*: Preserves the canonical 10-step release process (`ATT-170`), mandatory `SYS.5` real-device sign-off (`sys_verification_record.md`), `verification_summary.md` blocking gate, and zero `app/src/main/` changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`, and `docs/releases/TEMPLATE.md`,
  • *When* inspected,
  • *Then* they mandate `docs/releases/<tag>/sys_verification_record.md` and `verification_summary.md` as blocking release gates and do not treat `system_integration_results.json` as a blocking SYS.4 gate.
  • *Given* release candidate preparation,
  • *When* `sys_verification_record.md` or `verification_summary.md` has open deviations or missing sign-off,
  • *Then* tag creation and Play Store upload are blocked.
  • *Given* `ReleaseManagerSystemEvidenceContractTest.kt`,
  • *When* executed via Gradle,
  • *Then* all assertions pass with 100% success.
- **Invariants**: Zero Android production runtime code changes in `app/src/main/`.

### REQ-PRO-073: Evidence-Based Living Requirement Status Model and Governance Verification [Verified]
- **Tests**: `TST-PRO-073`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py` (`verify_master_table_parity()`, `recalculate_master_index_tables()`, `verify_requirement_evidence_status()`, `--sync-indexes`, `--check-evidence`, `--enforce-evidence`), `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `tools/test_verify_requirement_governance.py`, `docs/project_governance.md`.
- **Rationale**: ASPICE SWE.1 (Software Requirements Analysis), SWE.6 BP3 (Verify Software Units), and GP 2.2 require work product status claims to be grounded in controlled, versioned evidence, preventing status inflation:
  1. *Status Lifecycle Taxonomy (`REQ-PRO-073.1`)*:
     • Requirements and test specifications SHALL use multi-line headings ending with an explicit status: `[Planned]`, `[Open]`, `[Implemented]`, or `[Verified]`.
     • `[Planned]` / `[Open]`: Requirement is specified, but no verified test evidence or passing automated test exists yet.
     • `[Implemented]`: Production code exists in the repository, but automated test evidence (`TST-*`) is pending, incomplete, unintegrated, or failing.
     • `[Verified]`: Requires >= 1 authentic test specification (`TST-*`) with passing automated test code or formal archived verification record.
  2. *Master Index Table 4-Column Format (`REQ-PRO-073.2`)*:
     • Master index tables in `docs/requirements.md` and `docs/tests.md` SHALL use the 4-column summary format: `Total | Verified | Implemented | Planned / Open`.
  3. *Automated Index Synchronization (`REQ-PRO-073.3`)*:
     • `tools/verify_requirement_governance.py --sync-indexes` and `verify_master_table_parity()` SHALL support parsing and synchronizing `[Implemented]` status alongside `[Verified]` and `[Planned]` / `[Open]` with 100% mathematical parity across all 9 domain shards.
  4. *Evidence-Backed Verification Rule (`REQ-PRO-073.4`)*:
     • `tools/verify_requirement_governance.py` SHALL verify that any requirement claiming `[Verified]` cites >= 1 existing TST ID. In default CI checks (`verify_governance()`), any new or modified requirement claiming `[Verified]` without an existing TST ID SHALL fail validation. Under full-repository gating (`--enforce-evidence`), any requirement claiming `[Verified]` without valid test evidence SHALL fail.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-073` (`REQ-PRO-073.1`–`REQ-PRO-073.4`). Targets `tools/verify_requirement_governance.py` and `AspiceSweAuditContractTest.kt`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6126` under Epic `ATT-6119` (ASPICE CL2 – Evidence Integrity & Bidirectional Traceability). Preceding: ATT-6122 (commit `687e8388`, traceability coverage reporting) and ATT-6137 (commit `ddd1024a`, release JUnit archival).
  3. *Root Reason for Existing Formulation*: ASPICE 4.0 pre-assessment identified status inflation (98% `[Verified]` despite 59% lacking tests). Adding `[Implemented]` and 4-column master tables provides evidence-based status grounding.
  4. *Preservation of Core Invariants*: 100% parity across all 9 domain shards; pure Python 3 standard library; zero Android runtime modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a REQ claims `[Verified]` without citing an existing TST ID,
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it fails with a descriptive error.
  • *Given* a REQ claims `[Verified]` and cites an existing TST ID backed by test code,
  • *When* `verify_requirement_governance.py` runs,
  • *Then* it passes.
  • *Given* living doc shards with `[Implemented]` headings,
  • *When* `python3 tools/verify_requirement_governance.py --sync-indexes` runs,
  • *Then* master tables in `docs/requirements.md` and `docs/tests.md` reflect the 4-column format with 100% parity.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-074: Release Verification Summary Generation per Tag [Verified]
- **Tests**: `TST-PRO-074`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/generate_release_verification_summary.py`, `tools/test_generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`
- **Rationale**: Under ASPICE SWE.6 BP5 (Finding A-11, updated for `ATT-8157` release gate honesty and `ATT-8200` gate/key/tag-path remediation), release decisions must be based on recorded, baseline-controlled verification evidence. A standalone generator script SHALL consolidate unit/integration test counts/results, bidirectional requirement-to-test traceability matrices, optional informational simulator records (when present), and hardware-in-the-loop system verification protocol sign-off records (SYS.5) into an authoritative Markdown summary per release dossier (`docs/releases/<dossier_tag>/verification_summary.md`), explicitly highlighting all deviations requiring human approval:
  1. *Automated Release Verification Summary CLI & Tag Normalization (`REQ-PRO-074.1` [Verified (ATT-8200)], tools/generate_release_verification_summary.py)*:
     • `tools/generate_release_verification_summary.py` SHALL provide a command-line interface accepting `--tag <TAG>` (either `V<versionName>` or canonical CI tag `V<versionName>__<versionCode>`, e.g. `V5.0.0__273`), optional `--commit <SHA>` (defaulting to current HEAD), optional `--out <PATH>` (defaulting to `docs/releases/<dossier_tag>/verification_summary.md`), and optional threshold parameters (`--min-req-tst-coverage`, `--min-tst-code-coverage`, `--min-bidirectional-coverage`, `--max-code-without-spec`).
     • When `--tag` follows `V<versionName>__<versionCode>`, the generator SHALL normalize the dossier folder to `V<versionName>` (`docs/releases/V<versionName>/`, unless `docs/releases/<raw_tag>/` explicitly exists) while recording the full raw release tag, extracted `versionCode`, and resolved dossier folder in the summary header.
  2. *Unit & Regression Test Evidence Aggregation (`REQ-PRO-074.2`, tools/generate_release_verification_summary.py)*:
     • The generator SHALL aggregate total unit/integration test count, passed, failed, and skipped tests from `app/build/test-results/testDebugUnitTest/` or test archive files, checking raw-tag `build-evidence/junit-test-results-<raw_tag>.zip` first (matching CI workflow artifact naming) with fallback to `build-evidence/junit-test-results-<dossier_tag>.zip` (per ATT-6137).
  3. *Bidirectional REQ-TST Traceability Matrix Aggregation (`REQ-PRO-074.3` [Verified (ATT-8200)], tools/generate_release_verification_summary.py)*:
     • The generator SHALL query and embed bidirectional requirement-to-test coverage data via `tools/verify_requirement_governance.py --coverage-json` (per ATT-6122), reading exact `TraceabilityCoverageReport.to_dict()` keys (`category_1_reqs_without_tst`, `category_2_tsts_without_code`, `category_3_code_tags_without_spec`, `category_4_tsts_not_cited_back`) and parsing `stdout` JSON cleanly even when subprocess fallback exits `1` on a threshold failure.
  4. *System Verification Status & Non-Blocking Legacy Simulator Summary (`REQ-PRO-074.4` [Verified (ATT-8200)], tools/generate_release_verification_summary.py)*:
     • The generator SHALL parse and gate on SYS.5 System Verification Protocol sign-off records (`docs/releases/<dossier_tag>/sys_verification_record.md` per `ATT-6167`, resolved via normalized dossier tag). `collect_sys4_summary()` SHALL treat `docs/releases/<dossier_tag>/system_integration_results.json` as **optional non-blocking simulator/legacy telemetry** (raising zero missing-file deviation and never gating or falsely certifying Android app SYS.4 pass status if absent or legacy).
  5. *Open Deviations & Human Approval Gating (`REQ-PRO-074.5` [Verified (ATT-8200)], tools/generate_release_verification_summary.py)*:
     • The generator SHALL evaluate whether any unit/integration test failure, missing/incomplete SYS.5 evidence, or coverage gate failure exists—capturing **both** `"ERROR:"` (e.g. `"ERROR: Gate violated: ..."`) and `"FAIL:"` diagnostics from `verify_coverage_thresholds()` plus a fallback deviation whenever `gate_ok` is `False`—enumerate all violations under `Deviations Requiring Human Approval`, assign `BLOCKED / REQUIRES HUMAN APPROVAL` (or `PASS (READY FOR RELEASE)` when empty), and exit non-zero under `--fail-on-deviations`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PRO-074` (`REQ-PRO-074.1`–`REQ-PRO-074.5`). Targets `tools/generate_release_verification_summary.py`, `tools/test_generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, and `docs/releases/README.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via `ATT-6138` (`dc7b3d61`), building on `ATT-6137`, `ATT-6122`, `ATT-6166`, `ATT-6167`, `ATT-6169`; `REQ-PRO-074.4` updated in Sprint `2026-11-17_01_Früh` via `ATT-8157` (`1750ab64`, `REQ-PRO-112`); sharded in `ATT-8206` (`f55c9fee`); refined in `ATT-8200`.
  3. *Root Reason for Existing Formulation*: `collect_coverage_summary()` originally filtered only `FAIL:` prefixes and queried stale `category_3_code_without_spec`/`category_4_tst_without_req_citation` keys, while tag lookup assumed `docs/releases/<tag>/` matched raw Git tags rather than `V<versionName>`.
  4. *Preservation of Core Invariants*: Preserves unit test, traceability coverage, and SYS.5 sign-off blocking gates, `REQ-PRO-112` non-gating simulator rules, raw-tag `junit-test-results-<raw_tag>.zip` CI compatibility, `ReleaseManagerSystemEvidenceContractTest.kt` constraints, pure Python 3 stdlib CLI, and zero `app/src/main/` runtime changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `verify_coverage_thresholds()` emitting `"ERROR: Gate violated: ..."` or non-zero `category_3_code_tags_without_spec` / `category_4_tsts_not_cited_back` counts,
  • *When* `tools/generate_release_verification_summary.py --fail-on-deviations` executes (direct or subprocess fallback),
  • *Then* `overall_status` is `BLOCKED / REQUIRES HUMAN APPROVAL`, the script exits non-zero, and exact `cat3`/`cat4` gap data is preserved.
  • *Given* canonical Git tag `V5.0.0__273` and dossier directory `docs/releases/V5.0.0/`,
  • *When* `tools/generate_release_verification_summary.py --tag V5.0.0__273` executes,
  • *Then* it reads `docs/releases/V5.0.0/sys_verification_record.md`, writes `docs/releases/V5.0.0/verification_summary.md` with full tag + `versionCode` metadata, and finds `junit-test-results-V5.0.0__273.zip` when present.
  • *Given* valid unit test evidence, traceability coverage, and signed `sys_verification_record.md` without `system_integration_results.json`,
  • *When* `tools/generate_release_verification_summary.py --tag <TAG>` executes,
  • *Then* `docs/releases/<dossier_tag>/verification_summary.md` reports `PASS (READY FOR RELEASE)` with zero missing-SYS.4 deviations, and `tools/test_generate_release_verification_summary.py` passes 100%.
- **Invariants**: Zero Android production runtime code changes in `app/src/main/`; `Localization Parity: N/A - no UI strings`.

### REQ-PRO-075: Requirement Shards Tests Field Format & Machine-Readable Traceability Integrity [Verified]
- **Tests**: `TST-PRO-075`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.1 BP1 and evidence integrity governance, requirement-to-test traceability must be machine-checkable and unambiguous across all living documentation shards. The system SHALL enforce that the `Tests:` field of every requirement in `docs/requirements/*.md` contains strictly valid backticked test identifiers or standardized non-executable placeholders, preventing truncated sentence fragments, dangling code checks, misplaced production source files, or inline metadata:
  1. *Permitted Syntax Format (`REQ-PRO-075.1`, tools/verify_requirement_governance.py)*:
     • The `Tests:` field of every requirement section SHALL consist exclusively of a comma-separated sequence of backticked test identifiers conforming to regex pattern `` `TST-[A-Z0-9_-]+` `` or recognized placeholders (`None`, `Planned`, `Review`, `Manual inspection`).
     • Production source code files (`.kt`, `.java`, `.xml`), unclosed backticks, dangling boolean/code expressions (`code == ...`), and trailing pipe annotations (`| **Tickets**: ...`) SHALL NOT appear in the `Tests:` field.
  2. *Automated Governance Verifier Enforcement (`REQ-PRO-075.2`, tools/verify_requirement_governance.py)*:
     • `tools/verify_requirement_governance.py` SHALL include `verify_tests_field_format(repo_root=None)` as a mandatory verification gate during CI runs.
     • Any non-conforming line SHALL be reported with its relative shard path, line number, requirement ID, and offending content, causing the verifier to exit with a non-zero exit code.
  3. *Living Documentation & Master Table Synchronization (`REQ-PRO-075.3`)*:
     • All living requirement shards (`docs/requirements/*.md`) and master index tables (`docs/requirements.md`) SHALL maintain 100% parity with zero format violations.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-075` (`REQ-PRO-075.1`–`REQ-PRO-075.3`). Targets `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, and `docs/requirements/*.md`. References `REQ-PRO-028` and `REQ-PRO-065`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6125` under Epic `ATT-6119` (ASPICE CL2 Remediation A-06). Builds on `c9c2d428` (`ATT-6124`).
  3. *Root Reason for Existing Formulation*: Historical sharding in `06990c88` split rows with code-span pipes and conflated 6-column and 7-column table rows, shifting implementation files and sentence fragments into `Tests:`.
  4. *Preservation of Core Invariants*: 100% requirement ID uniqueness, living doc multi-line formatting, and master table parity invariants remain preserved. Zero changes to Android runtime code in `app/src/main/`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* all requirement shards under `docs/requirements/*.md`,
  • *When* `tools/verify_requirement_governance.py` validates `Tests:` field syntax,
  • *Then* zero format violations are detected.
  • *Given* a requirement containing a non-TST string, source file reference, or dangling syntax in `Tests:`,
  • *When* `verify_tests_field_format()` evaluates the shard,
  • *Then* a failure is recorded with exact file, line, and REQ ID, and execution fails.
  • *Given* `tools/test_verify_requirement_governance.py`,
  • *When* executed via pytest/unittest,
  • *Then* 100% of unit tests pass.
- **Invariants**: Zero Android production runtime code changes in `app/src/main/`.

### REQ-PRO-076: Bidirectional System-to-Software Requirement Traceability and Governance Verification [Verified]
- **Tests**: `TST-PRO-076`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system/system_software_traceability.md`, `docs/project_governance.md`
- **Rationale**: Under ASPICE SYS.2 BP5 and SWE.1 BP5 (Remediation S-04 / ATT-6163), software requirements must not exist in isolation. Every software requirement SHALL either trace to an authentic system requirement (`SYSREQ-*`) or be formally classified as software-derived with documented rationale:
  1. *Bidirectional System-to-Software Traceability Matrix (`REQ-PRO-076.1`)*:
     • The system engineering baseline SHALL establish explicit bidirectional traceability matrices between system requirements (`SYSREQ-TRK-001`..`006`, `SYSREQ-DEX-001`..`006`) and refining software requirements in `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system/system_software_traceability.md`.
  2. *Domain & Prefix Tailoring Rules (`REQ-PRO-076.2`)*:
     • Process governance (`docs/project_governance.md` and `docs/system/system_software_traceability.md`) SHALL define formal tailoring rules classifying purely internal UI, theme, calculation, and process requirement prefixes (`UI`, `UNT`, `PER`, `PERF`, `SPT`, `LOC`, `MON`, `PRI`, `SET`, `REV`, `STP`, `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`) as software-derived (`[SWE-Derived]`) with documented architectural rationale.
  3. *Automated Verification of Cited SYSREQ Existence (`REQ-PRO-076.3`)*:
     • `tools/verify_requirement_governance.py` SHALL verify that every `SYSREQ-*` identifier cited in living requirements or traceability matrices exists as a declared system requirement in `docs/system/sysreq_*.md`. Any dangling or invalid SYSREQ reference SHALL trigger verification failure.
  4. *SYSREQ Refinement Coverage Verification (`REQ-PRO-076.4`)*:
     • `tools/verify_requirement_governance.py` SHALL verify that 100% of declared system requirements are refined by $\ge 1$ software requirement, reporting unrefined system requirements and failing verification if any system requirement is unrefined.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-076` (`REQ-PRO-076.1`–`REQ-PRO-076.4`). Targets `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system/system_software_traceability.md`, and `docs/project_governance.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6163` under Epic `ATT-6142` (ASPICE CL2 Remediation S-04). Builds on `88525c79` (ATT-6121) and SYS.2 foundations `b5e7d58f` (ATT-6160) and `95eb871b` (ATT-6161).
  3. *Root Reason for Existing Formulation*: ASPICE 4.0 assessment finding S-04 identified that SWE requirements stood alone without machine-checked linkage to parent system requirements.
  4. *Preservation of Core Invariants*: Zero Android production runtime code changes in `app/src/main/`; pure Python 3 standard library CLI; living documentation master index mathematical parity preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* software requirements and traceability matrices citing `SYSREQ-*` IDs,
  • *When* `tools/verify_requirement_governance.py` runs,
  • *Then* all cited SYSREQs must exist in `docs/system/sysreq_*.md`, all declared SYSREQs must be covered by $\ge 1$ software requirement, and software requirements with tailored prefixes are classified as derived.
  • *Given* a requirement citing a non-existent `SYSREQ-XXX`,
  • *When* `tools/verify_requirement_governance.py` runs,
  • *Then* it fails with a descriptive dangling SYSREQ reference error.
  • *Given* `tools/test_verify_requirement_governance.py`,
  • *When* executed via pytest/unittest,
  • *Then* 100% of unit tests pass.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-077: Requirement Priority, Type, and Verification Method Specification & Governance [Verified]
- **Tests**: `TST-PRO-077`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `.agents/skills/stage2-req-test-spec/SKILL.md`, `.agents/skills/stage2-req-test-spec/templates/test_spec_template.md`, `docs/requirements.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.1 BP2 (Remediation A-09 / ATT-6136), software requirements must define explicit priority, type (functional vs. non-functional), and verification method attributes. Without these attributes, requirements cannot be systematically prioritized, non-functional requirements cannot be programmatically tracked, and verification methods cannot be audited:
  1. *Requirement Template Standardization (`REQ-PRO-077.1`)*:
     • The Stage 2 engineering lifecycle skill (`.agents/skills/stage2-req-test-spec/SKILL.md`), test specification deliverable template (`templates/test_spec_template.md`), and living requirements developer guidelines (`docs/requirements.md`) SHALL define and enforce mandatory presence of three core attributes in the requirement header metadata:
       - **Priority**: `Critical`, `High`, `Medium`, or `Low`.
       - **Type**: `Functional` or `Non-Functional`.
       - **Verification Method**: `Unit`, `Integration`, `On-Device Manual`, `Review`, `Formal Analysis` (or composite list thereof).
  2. *Automated Attribute Verification Tooling (`REQ-PRO-077.2`)*:
     • `tools/verify_requirement_governance.py` SHALL implement `verify_requirement_attributes()` to parse requirement sections across all domain shards (`docs/requirements/*.md`) and inspect for presence of `- **Priority**:`, `- **Type**:`, and `- **Verification Method**:`, validating values against their permitted domain vocabularies.
  3. *Legacy Requirement Warning Tolerance & Diff Enforcement (`REQ-PRO-077.3`)*:
     • In default verification mode, existing legacy requirements that have not yet had attributes backfilled SHALL emit warning diagnostics without failing the process.
     • Any newly added or modified requirement identified in the git diff against the baseline branch SHALL strictly mandate valid Priority, Type, and Verification Method attributes; missing or invalid attributes SHALL immediately fail verification with exit code 1.
  4. *Strict Attribute Enforcement CLI Flag (`REQ-PRO-077.4`)*:
     • `tools/verify_requirement_governance.py` SHALL support `--enforce-attributes` / `--fail-on-missing-attributes` command-line flags causing verification to exit with code 1 if ANY requirement in the repository lacks valid Priority, Type, or Verification Method attributes.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-077` (`REQ-PRO-077.1`–`REQ-PRO-077.4`). Targets `tools/verify_requirement_governance.py`, `.agents/skills/stage2-req-test-spec/SKILL.md`, `templates/test_spec_template.md`, and `docs/requirements.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6136` under Epic `ATT-6119` (ASPICE CL2 – Evidence Integrity & Bidirectional Traceability, Remediation A-09).
  3. *Root Reason for Existing Formulation*: ASPICE 4.0 assessment finding A-09 identified that requirements had no priority, no type (functional/non-functional), and no verification method.
  4. *Preservation of Core Invariants*: Existing master table parity, unique ID checks, dangling reference checks, and Python 3 stdlib execution preserved; zero production Android runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* any newly authored or modified requirement in the git diff,
  • *When* `tools/verify_requirement_governance.py` runs,
  • *Then* missing Priority, Type, or Verification Method fails verification with exit code 1.
  • *Given* legacy requirements without attributes,
  • *When* `tools/verify_requirement_governance.py` runs in default mode,
  • *Then* warning diagnostics are emitted without failing verification.
  • *Given* `--enforce-attributes` / `--fail-on-missing-attributes`,
  • *When* `tools/verify_requirement_governance.py` runs,
  • *Then* any missing attribute in the entire repository fails verification.
  • *Given* `tools/test_verify_requirement_governance.py`,
  • *When* executed via pytest/unittest,
  • *Then* 100% of unit tests pass.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-079: Aftermath Reviews Test Evidence Backfill & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-079`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_aftermath_reviews.md`, `docs/tests/tst_aftermath_reviews.md`, `ZoneDistributionCardTest.kt`, `tools/verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.2 (ATT-6128), all requirements in the `aftermath_reviews` domain shard must link to valid, existing test specifications in `docs/tests/tst_aftermath_reviews.md` with verified test code implementations in `app/src/test/` or be downgraded to `[Implemented]` per REQ-PRO-073. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references:
  1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-079.1`)*:
     • Every requirement in `docs/requirements/req_aftermath_reviews.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field.
     • All 12 `REQ-REV-*` requirements SHALL retain `[Verified]` status backed by automated unit, contract, or architecture tests in `app/src/test/`.
  2. *Bidirectional Citation Integrity (`REQ-PRO-079.2`)*:
     • Every test specification in `docs/tests/tst_aftermath_reviews.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field (`REQ-REV-002` SHALL cite `TST-REV-005` in addition to `TST-REV-002`).
     • Zero dangling references SHALL exist within the `aftermath_reviews` domain.
  3. *Test Code Tag Synchronization (`REQ-PRO-079.3`)*:
     • All 13 test specifications in `docs/tests/tst_aftermath_reviews.md` SHALL have corresponding test code tags recognized by `verify_requirement_governance.py --coverage`, including `TST-REV-009` tagged in `ZoneDistributionCardTest.kt`.
  4. *Attribute Compliance & Header Parity (`REQ-PRO-079.4`)*:
     • All requirements in `docs/requirements/req_aftermath_reviews.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
     • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 12`, `Total Tests: 13`) in 100% parity with master index tables.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-079` (`REQ-PRO-079.1`–`REQ-PRO-079.4`). Targets `docs/requirements/req_aftermath_reviews.md`, `docs/tests/tst_aftermath_reviews.md`, and test tags.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6128` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.2).
  3. *Root Reason for Existing Formulation*: Baseline assessment identified evidence and back-link gaps in the aftermath reviews domain shards following incremental feature expansions.
  4. *Preservation of Core Invariants*: Full functional test suite execution, master table mathematical parity, and pure Python 3 stdlib execution preserved with zero production Android APK modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/requirements/req_aftermath_reviews.md`,
  • *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  • *Then* 100% of requirements cite valid TSTs, 100% of TSTs have test code tags, and 100% of TSTs are cited back by all referenced REQs.
  • *Given* `docs/requirements/req_aftermath_reviews.md` and `docs/tests/tst_aftermath_reviews.md`,
  • *When* checked for dangling references and attributes,
  • *Then* zero dangling references and zero missing attributes are detected.
  • *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  • *When* executed,
  • *Then* master index tables achieve 100% mathematical parity.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-078: Living Documentation Tracking Cockpit Test Evidence Integrity & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-078`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`, `docs/project_governance.md`
- **Rationale**: Under ASPICE SWE.1 BP5 and SWE.6 BP4 (Remediation A-08.1 / ATT-6127), requirements in the Tracking Cockpit domain must maintain verified test evidence, 0 dangling references, and complete bidirectional linkage:
  1. *Tracking Cockpit Test Citation Completeness (`REQ-PRO-078.1`)*:
     • Every requirement in `docs/requirements/req_tracking_cockpit.md` SHALL cite at least one valid, existing `TST-*` identifier in its `Tests:` field, achieving $\ge 95\%$ test citation coverage.
  2. *Bidirectional Linkage Parity (`REQ-PRO-078.2`)*:
     • Every test specification in `docs/tests/tst_tracking_cockpit.md` SHALL be cited back by all of its referenced requirements in `docs/requirements/req_tracking_cockpit.md`, achieving $\ge 95\%$ bidirectional parity (Category 4).
  3. *Zero Dangling References (`REQ-PRO-078.3`)*:
     • The Tracking Cockpit domain SHALL maintain zero dangling references (`TST-FUSION-001`..`003`, `REQ-TRK-035` fully resolved and eliminated from legacy allowlists).
  4. *Evidence-Grounded Status Model Transition (`REQ-PRO-078.4`)*:
     • Requirements claiming `[Verified]` must cite $\ge 1$ test specification with verified automated test code. Requirements lacking automated evidence SHALL be transitioned to `[Implemented]` per `REQ-PRO-073`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-078` (`REQ-PRO-078.1`–`REQ-PRO-078.4`). Targets `tools/verify_requirement_governance.py`, `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6127` under Epic `ATT-6119` (ASPICE CL2 Remediation A-08.1). Builds on `e73eda46` (ATT-6126) and `88525c79` (ATT-6121).
  3. *Root Reason for Existing Formulation*: ASPICE 4.0 assessment finding A-08.1 identified ungrounded `[Verified]` claims, dangling references, and broken bidirectional links in the Tracking Cockpit domain.
  4. *Preservation of Core Invariants*: Zero Android production runtime code changes in `app/src/main/`; pure Python 3 standard library CLI; living documentation master index mathematical parity preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* living documentation shards `req_tracking_cockpit.md` and `tst_tracking_cockpit.md`,
  • *When* `tools/verify_requirement_governance.py --coverage` runs,
  • *Then* Tracking Cockpit Category 1 coverage is $\ge 95\%$, Category 4 coverage is $\ge 95\%$, and zero dangling references exist in this domain.
  • *Given* `tools/test_verify_requirement_governance.py`,
  • *When* executed via unittest,
  • *Then* 100% of unit tests pass.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-080: Aftermath Periods Test Evidence Backfill & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-080`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_aftermath_periods.md`, `docs/tests/tst_aftermath_periods.md`, `PeriodBarGraphTest.kt`, `WorkoutShareBottomSheetContractTest.kt`, `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`, `SportingJourneyCalculatorTest.kt`, `tools/verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.3 (ATT-6129), all requirements in the `aftermath_periods` domain shard must link to valid, existing test specifications in `docs/tests/tst_aftermath_periods.md` with verified test code implementations in `app/src/test/` or be downgraded to `[Implemented]` per REQ-PRO-073. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references:
  1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-080.1`)*:
     • Every requirement in `docs/requirements/req_aftermath_periods.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field.
     • All 41 `REQ-PER-*`, `REQ-RAD-*`, and `REQ-SPT-*` requirements SHALL retain `[Verified]` status backed by automated unit, contract, or architecture tests in `app/src/test/`.
     • `REQ-SPT-002` and `REQ-SPT-005` SHALL be eliminated from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
  2. *Bidirectional Citation Integrity (`REQ-PRO-080.2`)*:
     • Every test specification in `docs/tests/tst_aftermath_periods.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field.
     • Zero dangling references SHALL exist within the `aftermath_periods` domain.
  3. *Test Code Tag Synchronization (`REQ-PRO-080.3`)*:
     • Test source files in `app/src/test/` SHALL be annotated with standardized `TST-*` comment tags for automated scanner discovery: `TST-PER-024`, `TST-PER-029`, `TST-PER-036`, `TST-PER-037`, `TST-SPT-005`, and `TST-SPT-006`.
  4. *Attribute Compliance & Header Parity (`REQ-PRO-080.4`)*:
     • All 41 requirements in `docs/requirements/req_aftermath_periods.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
     • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 41`, `Total Tests: 50`) in 100% parity with master index tables.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-080` (`REQ-PRO-080.1`–`REQ-PRO-080.4`). Targets `docs/requirements/req_aftermath_periods.md`, `docs/tests/tst_aftermath_periods.md`, and test tags.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6129` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.3).
  3. *Root Reason for Existing Formulation*: Baseline pre-assessment identified evidence grounding gaps (`REQ-SPT-002`, `REQ-SPT-005` missing Tests field), header total drift, and bidirectional link gaps across the aftermath periods domain shards.
  4. *Preservation of Core Invariants*: Full functional test suite execution, master table mathematical parity, and pure Python 3 stdlib execution preserved with zero production Android APK modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/requirements/req_aftermath_periods.md`,
  • *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  • *Then* 100% of requirements cite valid TSTs, Category 4 bidirectional gaps for the domain are 0, and Category 3 unmapped tags for SPT are eliminated.
  • *Given* `docs/requirements/req_aftermath_periods.md` and `docs/tests/tst_aftermath_periods.md`,
  • *When* checked for dangling references and attributes,
  • *Then* zero dangling references and zero missing attributes are detected.

### REQ-PRO-081: Maps and Navigation Test Evidence Backfill & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-081`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_maps_navigation.md`, `docs/tests/tst_maps_navigation.md`, `WaypointBadgeMarkerTest.kt`, `tools/verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.5 (ATT-6131), all requirements in the `maps_navigation` domain shard must link to valid, existing test specifications in `docs/tests/tst_maps_navigation.md` with verified automated test code implementations in `app/src/test/` or be downgraded to `[Implemented]` per REQ-PRO-073. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references:
  1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-081.1`)*:
     • Every requirement in `docs/requirements/req_maps_navigation.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field.
     • Requirements lacking automated test code (`REQ-MAP-001`, `REQ-MAP-002`) SHALL be transitioned to `[Implemented]` per `REQ-PRO-073`. All other 73 requirements SHALL retain `[Verified]` status backed by automated unit tests in `app/src/test/`.
  2. *Dangling Reference Elimination & Bidirectional Citation Integrity (`REQ-PRO-081.2`)*:
     • Missing requirement `REQ-MAP-037` (*Route Waypoint Marker Badge Diameter Optimization & Glanceability Proportional Scaling*) SHALL be restored in `req_maps_navigation.md` citing `TST-MAP-039`, and removed from `LEGACY_DANGLING_REQ_REFS`.
     • Missing test specification `TST-MAP-038` (*Known Locations: Heim-Basis Redundant Badge Excision & Start-Count Home Resolution Alignment Verification*) SHALL be restored in `tst_maps_navigation.md` citing `REQ-MAP-036`.
     • Every test specification in `tst_maps_navigation.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field.
  3. *Test Code Tag Synchronization (`REQ-PRO-081.3`)*:
     • `WaypointBadgeMarkerTest.kt` SHALL be annotated with `TST-MAP-039` to eliminate unmapped test code tags.
  4. *Attribute Compliance & Header Parity (`REQ-PRO-081.4`)*:
     • All 75 requirements in `docs/requirements/req_maps_navigation.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
     • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 75`, `Total Tests: 70`) in 100% mathematical parity with master index tables.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-081` (`REQ-PRO-081.1`–`REQ-PRO-081.4`). Targets `docs/requirements/req_maps_navigation.md`, `docs/tests/tst_maps_navigation.md`, `WaypointBadgeMarkerTest.kt`, and `tools/verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6131` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.5).
  3. *Root Reason for Existing Formulation*: Baseline assessment identified evidence and back-link gaps in the Maps & Navigation domain shards following living document partitioning in `ATT-4594`.
  4. *Preservation of Core Invariants*: Full clean-room unit test suite execution, master table mathematical parity, and pure Python 3 stdlib execution preserved with zero production Android APK modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/requirements/req_maps_navigation.md`,
  • *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  • *Then* 100% of requirements cite valid TSTs, zero unmapped MAP/NAV tags exist in Category 3, and 100% of domain TSTs are cited back by all referenced REQs.
  • *Given* `docs/requirements/req_maps_navigation.md` and `docs/tests/tst_maps_navigation.md`,
  • *When* checked for dangling references and attributes,
  • *Then* zero dangling references and zero missing attributes are detected in maps_navigation.
  • *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  • *When* executed,
  • *Then* master index tables achieve 100% mathematical parity.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-083: Data Migration and Export Test Evidence Backfill & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-083`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_data_migration_export.md`, `docs/tests/tst_data_migration_export.md`, `WorkoutDeletionHelperTest.kt`, `tools/verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.8 (ATT-6134), all requirements in the `data_migration_export` domain shard must link to valid, existing test specifications in `docs/tests/tst_data_migration_export.md` with verified automated test code implementations in `app/src/test/` or be downgraded to `[Implemented]` per REQ-PRO-073. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references:
  1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-083.1`)*:
     • Every requirement in `docs/requirements/req_data_migration_export.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field.
     • `REQ-DAT-010` (*Bulk Workout Deletion & Relational Storage Purge*) SHALL cite `TST-DAT-004`, backed by automated unit tests in `WorkoutDeletionHelperTest.kt`. All 130 requirements in `req_data_migration_export.md` SHALL maintain `[Verified]` status backed by automated unit tests in `app/src/test/`.
  2. *Bidirectional Citation Integrity & Category 4 Gap Elimination (`REQ-PRO-083.2`)*:
     • Every test specification in `tst_data_migration_export.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field.
     • `REQ-DAT-007` SHALL cite `TST-DAT-001`, `TST-DAT-002`, `TST-DAT-009`.
     • `REQ-DAT-009` SHALL cite `TST-DAT-003`, `TST-DAT-028`.
     • `REQ-DAT-010` SHALL cite `TST-DAT-004`.
     • `TST-DAT-028` SHALL prune extraneous non-domain citation `REQ-MAP-020`.
     • `REQ-MIG-016` SHALL cite `TST-MIG-012`, `TST-MIG-018`.
     • `REQ-MIG-017` SHALL cite `TST-MIG-013`, `TST-MIG-017`.
     • `REQ-MIG-004` SHALL cite `TST-MIG-001`, `TST-MIG-037`.
     • `REQ-MIG-036` SHALL cite `TST-MIG-033`, `TST-MIG-036`, `TST-DAT-031`, `TST-MIG-045`, `TST-MIG-037`.
     • `REQ-MIG-039` SHALL cite `TST-MIG-049`.
  3. *Test Code Tag Synchronization (`REQ-PRO-083.3`)*:
     • `WorkoutDeletionHelperTest.kt` SHALL be annotated with `(REQ-DAT-010, TST-DAT-004 / ATT-296)` to eliminate Category 2 untagged test code gaps.
  4. *Attribute Compliance & Header Parity (`REQ-PRO-083.4`)*:
     • All 130 requirements in `docs/requirements/req_data_migration_export.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
     • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 130`, `Total Tests: 117`) in 100% mathematical parity with master index tables.
  5. *Tooling Allowlist Pruning (`REQ-PRO-083.5`)*:
     • `REQ-DAT-010` SHALL be removed from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-083` (`REQ-PRO-083.1`–`REQ-PRO-083.5`). Targets `docs/requirements/req_data_migration_export.md`, `docs/tests/tst_data_migration_export.md`, `WorkoutDeletionHelperTest.kt`, and `tools/verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6134` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.8).
  3. *Root Reason for Existing Formulation*: Baseline assessment identified evidence and back-link gaps in the Data Migration and Export domain shards following living document partitioning in `ATT-4594`.
  4. *Preservation of Core Invariants*: Full clean-room unit test suite execution, master table mathematical parity, and pure Python 3 stdlib execution preserved with zero production Android APK modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/requirements/req_data_migration_export.md`,
  • *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  • *Then* 100% of requirements cite valid TSTs, zero ungrounded Verified statuses exist in data_migration_export, and 100% of domain TSTs are cited back by all referenced REQs (0 Category 4 domain gaps).
  • *Given* `docs/requirements/req_data_migration_export.md` and `docs/tests/tst_data_migration_export.md`,
  • *When* checked for dangling references and attributes,
  • *Then* zero dangling references and zero missing attributes are detected in data_migration_export.
  • *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  • *When* executed,
  • *Then* master index tables achieve 100% mathematical parity.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-082: Settings and Profiles Test Evidence Backfill & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-082`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_settings_profiles.md`, `docs/tests/tst_settings_profiles.md`, `WorkoutClusterCounterTest.kt`, `WorkoutClusterStatsTest.kt`, `tools/verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.7 (ATT-6133), all requirements in the `settings_profiles` domain shard must link to valid, existing test specifications in `docs/tests/tst_settings_profiles.md` with verified test code implementations in `app/src/test/` or be downgraded to `[Implemented]` per REQ-PRO-073. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references:
  1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-082.1`)*:
     • Every requirement in `docs/requirements/req_settings_profiles.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field.
     • `REQ-PRI-002` SHALL be downgraded from `[Verified]` to `[Implemented]` per `REQ-PRO-073` due to lack of automated test fixtures for documentation policy (`privacy.md`).
     • `REQ-SET-001` SHALL cite `TST-SET-082`, `REQ-SET-002` SHALL cite `TST-SET-098`, `REQ-SET-004` SHALL cite `TST-SET-053`, `REQ-SET-005` SHALL cite `TST-NAV-005`, and `REQ-PRI-001` SHALL cite `TST-PRI-002`.
     • `REQ-SET-075` SHALL cite `TST-SET-077`, `TST-UI-318`, `TST-UI-321`.
     • `REQ-SET-083`, `REQ-SET-084`, `REQ-SET-085`, and `REQ-SET-087` SHALL cite `TST-LOC-001` alongside their respective `TST-SET-*` IDs.
  2. *Bidirectional Citation Integrity & Tag Synchronization (`REQ-PRO-082.2`)*:
     • Every test specification in `docs/tests/tst_settings_profiles.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field.
     • Shorthand numeric requirement citations (`007`, `008`, `011`, `030`, `038`) in `docs/tests/tst_settings_profiles.md` SHALL be expanded to canonical IDs (`REQ-SET-007`, `REQ-SET-008`, `REQ-SET-011`, `REQ-SET-030`, `REQ-SET-037`).
     • In test classes `WorkoutClusterCounterTest.kt` and `WorkoutClusterStatsTest.kt`, code tags SHALL align with living specifications `TST-SET-054` and `TST-SET-055`, eliminating Category 3 ungrounded tags.
     • Zero dangling references SHALL exist within the `settings_profiles` domain.
  3. *Attribute Compliance & Header Parity (`REQ-PRO-082.3`)*:
     • All 102 requirements in `docs/requirements/req_settings_profiles.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
     • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 102`, `Total Tests: 89`) in 100% parity with master index tables.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-082` (`REQ-PRO-082.1`–`REQ-PRO-082.3`). Targets `docs/requirements/req_settings_profiles.md`, `docs/tests/tst_settings_profiles.md`, and test code tags.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6133` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.7).
  3. *Root Reason for Existing Formulation*: Baseline pre-assessment identified evidence grounding gaps, header total drift, shorthand citation formatting, and bidirectional link gaps across settings and profiles domain shards.
  4. *Preservation of Core Invariants*: Full functional test suite execution, master table mathematical parity, and pure Python 3 stdlib execution preserved with zero production Android APK modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/requirements/req_settings_profiles.md`,
  • *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  • *Then* 100% of requirements cite valid TSTs, Category 4 bidirectional gaps for the domain are 0, and Category 3 unmapped tags for SET are eliminated.
  • *Given* `docs/requirements/req_settings_profiles.md` and `docs/tests/tst_settings_profiles.md`,
  • *When* checked for dangling references and attributes,
  • *Then* zero dangling references and zero missing attributes are detected.
  • *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  • *When* executed,
  • *Then* master index tables achieve 100% mathematical parity.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-084: Store Presence Test Evidence Backfill & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-084`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_store_presence.md`, `docs/tests/tst_store_presence.md`, `PlayStoreListingContractTest.kt`, `tools/verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.9 (ATT-6135), all requirements in the `store_presence` domain shard (historically known as `project_governance` prior to ATT-6140) must link to valid, existing test specifications in `docs/tests/tst_store_presence.md` with verified test code implementations in `app/src/test/` or be downgraded to `[Implemented]` per REQ-PRO-073. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references:
  1. *Test Code Tag Grounding & Category 2 Completeness (`REQ-PRO-084.1`)*:
     • In `PlayStoreListingContractTest.kt`, test methods `fullDescriptionsContainMandatoryV5Features()` and `storeListingsDoNotContainUnimplementedBacklogFeatures()` SHALL declare test code tag `TST-STP-002`.
     • In `PlayStoreListingContractTest.kt`, test method `storeScreenshotsAreUniqueAndValid()` SHALL declare test code tag `TST-STP-004`.
     • Class documentation header of `PlayStoreListingContractTest.kt` SHALL cite `REQ-STP-004`, `TST-STP-002`, and `TST-STP-004`, eliminating all Category 2 test code tag gaps for the domain.
  2. *Mandatory Attribute Compliance (`REQ-PRO-084.2`)*:
     • All 10 requirements in `docs/requirements/req_store_presence.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
  3. *Test Specification Alignment (`REQ-PRO-084.3`)*:
     • Test specifications `TST-STP-001` through `TST-STP-010` in `docs/tests/tst_store_presence.md` SHALL declare `Priority`, `Type`, and `Verification Method` attributes.
     • Zero dangling references SHALL exist within the `store_presence` domain and 100% of test specifications SHALL be cited back by their requirements.
  4. *Evidence Grounding & Index Parity (`REQ-PRO-084.4`)*:
     • All 10 requirements SHALL maintain `[Verified]` backed by authentic automated build contract tests in `com.atrainingtracker.trainingtracker.build.*`.
     • Living documentation master index tables in `docs/requirements.md` and `docs/tests.md` SHALL achieve 100% mathematical parity.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-084` (`REQ-PRO-084.1`–`REQ-PRO-084.4`). Targets `docs/requirements/req_store_presence.md`, `docs/tests/tst_store_presence.md`, and `PlayStoreListingContractTest.kt`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6135` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.9).
  3. *Root Reason for Existing Formulation*: Baseline pre-assessment identified test code tag omissions (`TST-STP-002`, `TST-STP-004`) in contract tests, missing mandatory attributes (`REQ-PRO-077`), and required formal verification evidence grounding across store presence living documentation.
  4. *Preservation of Core Invariants*: Full build contract test suite pass rate, master table mathematical parity, and pure Python 3 stdlib execution preserved with zero production Android APK modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/requirements/req_store_presence.md`,
  • *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  • *Then* 100% of requirements cite valid TSTs (10/10), 100% of TSTs have test code tags (10/10), and Category 4 bidirectional gaps for STP are 0.
  • *Given* `docs/requirements/req_store_presence.md` and `docs/tests/tst_store_presence.md`,
  • *When* checked for dangling references and attributes,
  • *Then* zero dangling references and zero missing attributes are detected.

### REQ-PRO-085: UI Design System Test Evidence Backfill & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-085`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_ui_design_system.md`, `docs/tests/tst_process_stability.md`, `tools/verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.6 (ATT-6132), all requirements in the `ui_design_system` domain shard must link to valid, existing test specifications in `docs/tests/tst_ui_design_system.md` with verified automated test code implementations in `app/src/test/` or be downgraded to `[Implemented]` per REQ-PRO-073. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references:
  1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-085.1`)*:
     • Every requirement in `docs/requirements/req_ui_design_system.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field.
     • The 8 omitted requirements (`REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`) SHALL be restored with full specifications. All 452 requirements in `req_ui_design_system.md` SHALL maintain `[Verified]` status backed by automated unit tests in `app/src/test/`.
  2. *Dangling Reference Elimination & Test Specification Backfill (`REQ-PRO-085.2`)*:
     • Test specifications `TST-UI-041` (*Period Peak Markers Audit*) and `TST-UI-044` (*Mappable Device Items Layout Verification*) SHALL be registered in `docs/tests/tst_ui_design_system.md`.
     • Test specification `TST-INT-007` (*Original Sport Icons Visual Identity Verification*) SHALL be registered in `docs/tests/tst_process_stability.md`.
     • Zero dangling references SHALL exist across UI requirements and tests.
  3. *Bidirectional Citation Integrity & Category 4 Gap Elimination (`REQ-PRO-085.3`)*:
     • Every test specification in `tst_ui_design_system.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field.
     • All 41 Category 4 gaps identified in the UI domain SHALL be updated in `req_ui_design_system.md` with reciprocal citations.
  4. *Attribute Compliance & Header Parity (`REQ-PRO-085.4`)*:
     • All 452 requirements in `docs/requirements/req_ui_design_system.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
     • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 452`, `Total Tests: 466`) in 100% mathematical parity with master index tables.
  5. *Tooling Allowlist Pruning (`REQ-PRO-085.5`)*:
     • All resolved UI references (`TST-UI-041`, `TST-UI-044`, `TST-INT-007`, `REQ-UI-002`, `REQ-UI-008`, `REQ-UI-009`, `REQ-UI-010`, `REQ-UI-062`, `REQ-UI-135`, `REQ-UI-166`, `REQ-UI-289`, and `REQ-UI-*` in `LEGACY_UNGROUNDED_VERIFIED_REQS`) SHALL be pruned from `tools/verify_requirement_governance.py`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-085` (`REQ-PRO-085.1`–`REQ-PRO-085.5`). Targets `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_ui_design_system.md`, `docs/tests/tst_process_stability.md`, and `tools/verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6132` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.6).
  3. *Root Reason for Existing Formulation*: Baseline assessment identified dangling references, missing requirement headings, unreciprocated Category 4 links, and missing metadata attributes across the UI Design System domain.
  4. *Preservation of Core Invariants*: Full clean-room unit test suite execution, master table mathematical parity, and pure Python 3 stdlib execution preserved with zero production Android APK modifications.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/requirements/req_ui_design_system.md`,
  • *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  • *Then* 100% of requirements cite valid TSTs, zero ungrounded Verified statuses exist in ui_design_system, and 100% of domain TSTs are cited back by all referenced REQs (0 Category 4 domain gaps).
  • *Given* `docs/requirements/req_ui_design_system.md` and `docs/tests/tst_ui_design_system.md`,
  • *When* checked for dangling references and attributes,
  • *Then* zero dangling references and zero missing attributes are detected in ui_design_system.
  • *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  • *When* executed,
  • *Then* master index tables achieve 100% mathematical parity.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-086: Process Stability Test Evidence Backfill & Bidirectional Traceability Governance [Verified]
- **Tests**: `TST-PRO-086`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.4 (ATT-6130), all requirements in the `process_stability` domain shard must link to valid, existing test specifications in `docs/tests/tst_process_stability.md` backed by authentic automated test code in `tools/` and `app/src/test/` or be downgraded to `[Implemented]`. All dangling references in this domain must be eliminated, mandatory attributes (`Priority`, `Type`, `Verification Method` per `REQ-PRO-077`, SWE.1 BP2) populated across all REQs and TSTs, missing `REQ-STB-013` restored, legacy ungrounded allowlists pruned, and master index tables synchronized with 100% mathematical parity:
  1. *Restoration of REQ-STB-013 & SQLite Streaming Governance (`REQ-PRO-086.1`)*:
     • `REQ-STB-013` SHALL be restored in `docs/requirements/req_process_stability.md` with complete attributes, rationale, Chesterton's Fence archaeology, and invariants.
     • `REQ-STB-013` SHALL be linked bidirectionally with `TST-STB-013` and `TST-STB-018`, backed by `WorkoutRepositoryStreamTest.kt`.
  2. *Dangling Reference Elimination (`REQ-PRO-086.2`)*:
     • Dangling citations `TST-STR-010` and `TST-FUSION-004` SHALL be removed from `REQ-NFR-001` and `REQ-NFR-002`.
     • Dangling citations in `docs/tests/tst_process_stability.md` (`TST-INT-004`, `TST-INT-005`, `TST-INT-006`, `TST-STR-001`, `TST-STR-003`) SHALL be resolved to authentic headings (`REQ-UI-110`, `REQ-UI-111`, `REQ-UI-109`).
     • `TST-STR-010`, `TST-FUSION-004`, and `REQ-STB-013` SHALL be pruned from legacy allowlists in `tools/verify_requirement_governance.py`.
  3. *Mandatory Attribute Compliance (`REQ-PRO-086.3`)*:
     • All requirements in `docs/requirements/req_process_stability.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
     • All test specifications in `docs/tests/tst_process_stability.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes.
  4. *Bidirectional Parity & Ungrounded Allowlist Pruning (`REQ-PRO-086.4`)*:
     • `TST-MAN-001` SHALL explicitly enumerate all 23 citing requirements across living shards.
     • `TST-PRO-052` SHALL explicitly list `REQ-NFR-003` through `REQ-NFR-014`.
     • All 58 verified `process_stability` requirements SHALL be pruned from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
  5. *Index Synchronization & Parity (`REQ-PRO-086.5`)*:
     • Master tables in `docs/requirements.md` and `docs/tests.md` SHALL be recalculated via `tools/verify_requirement_governance.py --sync-indexes`, achieving 100% mathematical parity.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-086` (`REQ-PRO-086.1`–`REQ-PRO-086.5`). Targets `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `tools/verify_requirement_governance.py`, and `tools/test_verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.4 via ticket `ATT-6130` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.4).
  3. *Root Reason for Existing Formulation*: Baseline pre-assessment identified ungrounded `[Verified]` claims, dangling citations (`TST-STR-010`, `TST-FUSION-004`, `REQ-STB-013`), missing mandatory attributes, and omitted `REQ-STB-013` following document sharding.
  4. *Preservation of Core Invariants*: 100% governance unit test pass rate, master table mathematical parity, pure Python 3 stdlib execution, and zero Android production runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`,
  • *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  • *Then* >= 95% of REQs cite valid TSTs (100%), Category 4 bidirectional parity is >= 95%, and zero ungrounded Verified statuses exist.
  • *Given* `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`,
  • *When* checked for dangling references and attributes,
  • *Then* zero dangling references and zero missing attributes are detected.
  • *Given* `python3 tools/verify_requirement_governance.py --sync-indexes`,
  • *When* executed,
  • *Then* master index tables achieve 100% mathematical parity.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

### REQ-PRO-087: Machine-Verified Bidirectional Traceability & Ratchet Baseline Governance [Verified]
- **Tests**: `TST-PRO-087`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/traceability_baseline.json`, `tools/test_verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.1 BP5 and SWE.4 BP5 (Finding F4), requirement-to-test traceability must be machine-verified rather than assumed by ID convention. The governance tooling must standardize machine-readable requirement verification fields (`Verifies:` / `Requirement(s):`), enforce bidirectional traceability across living documentation shards and test specifications, prevent orphan regressions via a strictly shrinking ratchet baseline (`tools/traceability_baseline.json`), resolve historical unreferenced requirements, and ensure zero governance failures:
  1. *Standardized Test Verification Field (`REQ-PRO-087.1`)*:
     • Living documentation test shards under `docs/tests/` and test specification deliverables SHALL standardize on a machine-readable `Verifies: REQ-...` field (backward-compatible with `Requirement(s):` / `Target Requirement:`).
  2. *Machine-Verified Bidirectional Traceability Engine (`REQ-PRO-087.2`)*:
     • `tools/verify_requirement_governance.py` SHALL verify that every declared requirement in `docs/requirements/` is cited by at least one test specification, and every test specification in `docs/tests/` cites valid, authentic requirement headings.
     • Any test referencing a non-existent requirement ID SHALL cause governance verification to fail with deterministic diagnostics.
  3. *Ratchet Baseline File & Monotonicity Enforcement (`REQ-PRO-087.3`)*:
     • Known existing orphan requirements and test specifications SHALL be recorded in a versioned baseline file (`tools/traceability_baseline.json`).
     • Adding any new requirement or test orphan absent from the baseline SHALL fail governance verification.
     • Resolving existing baseline orphans SHALL require updating the baseline file such that the orphan set is strictly monotonic non-increasing ($|B_{t+1}| \le |B_t|$).
  4. *Historical Orphan Resolution & Syntax Normalization (`REQ-PRO-087.4`)*:
     • The 6 historical unreferenced requirements (`REQ-CON-001`, `REQ-CON-004`, `REQ-CON-006`, `REQ-EXP-005`, `REQ-EXT-003`, `REQ-FIL-005`) SHALL be resolved with reciprocal test citations.
     • Malformed citations in `TST-MIG-001` and field inversion in `TST-UI-310` SHALL be normalized.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-087` (`REQ-PRO-087.1`–`REQ-PRO-087.4`). Targets `tools/verify_requirement_governance.py`, `tools/traceability_baseline.json`, `docs/requirements/`, `docs/tests/`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.5 via ticket `ATT-7561` under Epic `ATT-232` (Process). Addresses ASPICE evaluation finding F4.
  3. *Root Reason for Existing Formulation*: Prior governance scripts relied on convention (ID parity) and only verified dangling links and SYSREQ-to-REQ mappings without enforcing that every REQ has a verifying test or strictly ratcheting the orphan set down.
  4. *Preservation of Core Invariants*: Deterministic exit codes (0 = valid, 1 = failure); Python 3 standard library only; existing `--sync-indexes` and CI governance checks remain 100% green; zero Android APK runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* living requirement shards and a requirement without a verifying test that is not recorded in `tools/traceability_baseline.json`,
  • *When* `tools/verify_requirement_governance.py` executes,
  • *Then* verification fails and lists the orphan requirement ID.
  • *Given* living test shards and a test referencing a non-existent requirement ID,
  • *When* `tools/verify_requirement_governance.py` executes,
  • *Then* verification fails and identifies the dangling target.
  • *Given* `tools/traceability_baseline.json`,
  • *When* new orphans are added or the baseline count increases,
  • *Then* verification fails with a ratchet violation error.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.

---

### REQ-PRO-088: System Verification Records Execution & Release Pipeline Gating [Planned]
- **Tests**: `TST-PRO-088`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/releases/V5.0.0/sys_verification_record.md`, `tools/run_system_integration_tests.py`, `.github/workflows/android-release.yml`, `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`
- **Rationale**: Under ASPICE SYS.5 (BP1-BP7) and SWE.6 BP5 (Finding F3), system-level verification across real reference hardware, physical BLE/ANT+ sensors, and operating environments must be executed and filed as formal evidence for production releases. The release management pipeline must enforce blocking gates that prevent tag creation, Play Store publishing, and GitHub release creation when verification records are missing or failing:
  1. *Release System Verification Evidence Execution (`REQ-PRO-088.1`)* [Planned]:
     • For target production release `V5.0.0` (code `273`), a formal `sys_verification_record.md` SHALL be filed under `docs/releases/V5.0.0/` adhering to `docs/system/sys_verification_protocol.md`.
     • All 8 verification cases (`SYS-VRF-001` through `SYS-VRF-008`) SHALL report authentic telemetry or open status, requiring explicit human PO attestation and prohibiting synthetic placeholder serials (`*-REF-*`) or unbacked automated passes.
  2. *Automated System Integration Protocol Alignment (`REQ-PRO-088.2`)*:
     • `tools/run_system_integration_tests.py` SHALL maintain protocol parity with authentic Garmin Varia radar telemetry frames (`[0x02, 0x00]` for `SYS-INT-003`).
     • Automated integration results SHALL be filed under `docs/releases/V5.0.0/system_integration_results.json` with 100% test pass rate (6/6 PASS).
  3. *CI/CD Release Workflow Verification Gate (`REQ-PRO-088.3`)*:
     • `.github/workflows/android-release.yml` SHALL incorporate a mandatory release gate step invoking `tools/generate_release_verification_summary.py --tag ${TAG} --fail-on-deviations`.
     • Any missing SYS.4/SYS.5 evidence or failing test SHALL fail the release workflow and block artifact distribution.
  4. *Release Checklist Maintenance & Pre-ASPICE Backfill Governance (`REQ-PRO-088.4`)*:
     • `.agents/skills/release-manager/SKILL.md` checklist and procedures SHALL enforce verified existence of `sys_verification_record.md` prior to tag finalization.
     • `docs/releases/README.md` SHALL record an auditable backfill rationale documenting historical pre-ASPICE releases.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-088` (`REQ-PRO-088.1`–`REQ-PRO-088.4`). Targets `docs/releases/`, `.github/workflows/android-release.yml`, `tools/run_system_integration_tests.py`, `.agents/skills/release-manager/SKILL.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.5 via ticket `ATT-7560` under Epic `ATT-232` (Process). Addresses ASPICE evaluation finding F3. Reset to [Planned] in Sprint 2026-46.8 via `ATT-8141` (Epic `ATT-8138`, Finding K1).
  3. *Root Reason for Existing Formulation*: Baseline assessment identified that while `sys_verification_protocol.md` defined SYS.5 procedures, zero release dossiers existed in `docs/releases/` and CI release workflows bypassed system evidence checks.
  4. *Preservation of Core Invariants*: Deterministic release gating; `ReleaseManagerSystemEvidenceContractTest.kt` passes 100%; zero Android production runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a release candidate tag `V5.0.0`,
  • *When* the release evidence dossier is checked,
  • *Then* `docs/releases/V5.0.0/sys_verification_record.md` exists with protocol completeness and authentic human PO attestation.
  • *Given* a missing or incomplete system verification record,
  • *When* `tools/generate_release_verification_summary.py --fail-on-deviations` or release CI runs,
  • *Then* the process fails and blocks release candidate tag creation.
  • *Given* the system verification record,
  • *When* inspected,
  • *Then* it documents reference devices (`SYS-HW-01`..`SYS-HW-07`), physical sensor models, firmware versions, and formal gatekeeper signatures.
- **Invariants**: Pure Python 3 standard library; zero Android runtime changes; zero regression in existing governance checks.


---

### REQ-PRO-089: Kover Coverage Floor Ratcheting and Critical Subsystem Bounds [Verified]
- **Tests**: `TST-PRO-089`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/build.gradle`, `docs/tests.md`, `docs/coding_standards.md`
- **Rationale**: Under ASPICE F6 verification criteria, the project's verification floor must ratchet upwards as the test suite matures and establish package-level guardrails for safety-critical components.
- **Description**:
  1. *Global Verification Floor*: `app/build.gradle` SHALL enforce a minimum of 40% line coverage (`minBound(40)`) and 35% branch coverage (`CoverageUnit.BRANCH`, `minValue = 35`).
  2. *Subsystem Verification Bounds*: `app/build.gradle` SHALL define bounded verification rules for:
     - `Tracking`: packages `com.atrainingtracker.trainingtracker.tracker` (line $\ge 55\%$, branch $\ge 45\%$).
     - `Filters`: packages `com.atrainingtracker.banalservice.filters` (line $\ge 70\%$, branch $\ge 50\%$, noting actual baseline is 54.15%).
     - `Sensors & Devices`: packages `com.atrainingtracker.banalservice.devices` and `com.atrainingtracker.banalservice.sensor` (line $\ge 60\%$, branch $\ge 50\%$).
  3. *Test Suite Execution Precondition*: All unit tests SHALL pass cleanly before coverage verification is evaluated.
  4. *Documentation Synchronization*: `docs/tests.md` and `docs/coding_standards.md` SHALL document the new bounds.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-089`. Targets `app/build.gradle` Kover DSL and associated documentation.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.6 via ticket `ATT-7563` under Epic `ATT-235`.
  3. *Root Reason for Existing Formulation*: Baseline Kover floor was set to 35% line / 30% branch during early Java-to-Kotlin UI migration. Test maturity now exceeds ~45%+ line globally with ~6,000 unit tests, requiring ratcheting and package-level guardrails.
  4. *Preservation of Core Invariants*: Build deterministic pass/fail semantics preserved; zero Android production runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* the complete codebase and test suite,
  • *When* `./gradlew koverVerifyDebug` is executed,
  • *Then* all global and subsystem rules pass without violation.
  • *Given* any critical package falling below its designated line or branch bound,
  • *When* `./gradlew koverVerifyDebug` executes,
  • *Then* the build fails with a specific bounded rule violation.
- **Invariants**: Zero changes to production runtime logic; zero flaky or bypassed tests.

---

### REQ-PRO-090: Unified CI & Release Quality Gates and Automated Governance Gating [Verified]
- **Tests**: `TST-PRO-090`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `.github/workflows/android-release.yml`, `.github/workflows/android-branch-build.yml`, `ReleaseCiAndLintConfigContractTest.kt`
- **Rationale**: Continuous integration pipelines exhibited quality gate disparity between development and release branches (ASPICE evaluation finding F7). `.github/workflows/android-release.yml` previously executed only unit tests (`:app:testDebugUnitTest`), skipping Android Lint (`:app:lintDebug`), Detekt (`:app:detekt`), Kover coverage verification (`:app:koverVerifyDebug`), and requirement governance verification (`python3 tools/verify_requirement_governance.py`). Concurrently, `.github/workflows/android-branch-build.yml` omitted `tools/verify_requirement_governance.py`, allowing governance regressions to bypass automated CI gates on pull requests and branch pushes. Aligning quality gates across release and PR/branch workflows and enforcing this via automated contract tests guarantees ASPICE SUP.8 and SWE.4 process integrity (ATT-7564, Parent Epic ATT-232, refining and extending `REQ-BLD-002`, `REQ-PRO-046`, `REQ-PRO-047`, and `REQ-PRO-048`):
- **Description**:
  1. *Release Workflow Quality Gate Parity (`REQ-PRO-090.1`)*:
     • `.github/workflows/android-release.yml` SHALL execute Android Lint (`./gradlew :app:lintDebug`), Detekt (`./gradlew :app:detekt`), Kover coverage report and verification (`./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`), and Requirement Governance (`python3 tools/verify_requirement_governance.py`) prior to release assembly (`:app:assembleRelease :app:bundleRelease`).
     • Any failure in lint, detekt, kover, or requirement governance SHALL fail the release workflow and immediately abort APK/AAB assembly and distribution.
  2. *Release Workflow Report Artifact Archiving (`REQ-PRO-090.2`)*:
     • `.github/workflows/android-release.yml` SHALL upload report artifacts for lint (`app/build/reports/lint-results-debug.*`), detekt (`app/build/reports/detekt/`), and kover (`app/build/reports/kover/`) via `actions/upload-artifact@v4` with `if: always()`, ensuring static analysis and coverage evidence is preserved across release builds.
  3. *Branch & Pull Request Governance Script Gating (`REQ-PRO-090.3`)*:
     • `.github/workflows/android-branch-build.yml` SHALL execute `python3 tools/verify_requirement_governance.py` on all branch pushes and pull requests within the `build-debug` job.
     • Any failure in requirement archaeology, master table parity, identifier uniqueness, link integrity, or evidence status SHALL fail the branch/PR build.
  4. *Automated CI Contract Test Verification (`REQ-PRO-090.4`)*:
     • `ReleaseCiAndLintConfigContractTest.kt` SHALL verify via automated unit contract tests that `.github/workflows/android-release.yml` contains execution steps for `lintDebug`, `detekt`, `koverVerifyDebug`, and `verify_requirement_governance.py` prior to `assembleRelease`, archives lint/detekt/kover reports, and verifies that `.github/workflows/android-branch-build.yml` contains the `verify_requirement_governance.py` step.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-090` (`REQ-PRO-090.1`–`REQ-PRO-090.4`), refining and extending `REQ-BLD-002` (*Release CI Gating*), `REQ-PRO-046` (*Lint CI Gating*), `REQ-PRO-047` (*Detekt Static Analysis*), and `REQ-PRO-048` (*Kover Coverage Gating*). Targets `.github/workflows/android-release.yml`, `.github/workflows/android-branch-build.yml`, and `ReleaseCiAndLintConfigContractTest.kt`.
  2. *Historical Origin & Commit Trace*: Ticket ATT-4025 (commit `00155b5d`, `REQ-BLD-002`) introduced unit test gating in release workflow; ATT-6173 (commit `08a744c1`, `REQ-PRO-046`), ATT-6175 (`REQ-PRO-047`), and ATT-6171 (`REQ-PRO-048`) added lint, detekt, and kover to branch builds. ASPICE evaluation finding F7 (Sprint 2026-46.6, ATT-7564) identified gate disparity between release and branch builds.
  3. *Root Reason for Existing Formulation*: Workflows were created iteratively; release CI prioritized artifact generation and signing, while requirement governance was originally invoked manually by the coordinator rather than in CI.
  4. *Preservation of Core Invariants*: 100% clean-room test pass rate; release signing keystore and secret resolution chain preserved; zero Android production runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `.github/workflows/android-release.yml`,
  • *When* inspected,
  • *Then* it contains steps for `:app:lintDebug`, `:app:detekt`, `:app:koverVerifyDebug`, and `python3 tools/verify_requirement_governance.py` prior to `:app:assembleRelease :app:bundleRelease`, along with artifact upload steps for lint, detekt, and kover reports.
  • *Given* `.github/workflows/android-branch-build.yml`,
  • *When* inspected,
  • *Then* it contains a step executing `python3 tools/verify_requirement_governance.py` in the `build-debug` job.
  • *Given* `ReleaseCiAndLintConfigContractTest.kt`,
  • *When* executed as part of the unit test suite,
  • *Then* all contract tests verifying gate presence, sequencing, and artifact uploads across both workflows pass with 100% success.
- **Invariants**: Zero runtime code changes; existing release signing provider chain and secrets handling remain unmodified.

---

### REQ-PRO-091: Independent ASPICE SUP.1 QA Auditing, Representative Sampling & Corrective Action Tracking [Verified]
- **Tests**: `TST-PRO-091`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/jira-workflow/SKILL.md`, `.agents/rules/aspice_governance.md`, `docs/project_governance.md`, `docs/engineering/qa_audits/`
- **Rationale**: ASPICE evaluation finding F1 identified three systemic gaps in process quality assurance under SUP.1 (Quality Assurance): lack of auditor role independence (audits were authored by `coordinator`), under-sampling (static 2-ticket sample regardless of sprint size), and open-loop non-compliances (prior non-compliant findings had no tracked Jira corrective action tickets and were never reviewed at sprint planning). Establishing strict auditor independence, representative statistical sampling ($\ge 10\%$), closed-loop corrective action tracking, and Ceremony 1 verification satisfies ASPICE SUP.1 BP1, BP4, and BP5 (ATT-7558, Parent Epic ATT-232, refining and extending `REQ-PRO-061`):
- **Description**:
  1. *Independent QA Auditor Role Enforcement (`REQ-PRO-091.1`)*:
     • The QA process audit tool (`qa-audit`) and audit records in `docs/engineering/qa_audits/<sprint>.md` SHALL enforce an independent auditor role (`qa` / `agent2` / `quality-assurance`) that is strictly distinct from the sprint coordinator (`coordinator`) and ticket implementers (`agent1`).
     • Invoking `qa-audit` with `role="coordinator"` or `role="agent1"` SHALL be forbidden and rejected with exit code 1.
  2. *Representative Statistical Sampling Ratio & Seeded Randomization (`REQ-PRO-091.2`)*:
     • The QA audit tool SHALL dynamically compute the minimum required sample size as at least 10% of completed parent sprint tickets:
       $$\text{sample\_size} \ge \max(2, \lceil 0.10 \times N \rceil)$$
       where $N$ is the count of completed parent sprint tickets.
     • Sample selection SHALL use seeded pseudo-random sampling (`random.Random(seed).sample`), accepting an optional seed parameter (`--seed`) or generating a random integer seed.
     • The audit report in `docs/engineering/qa_audits/<sprint>.md` SHALL explicitly document the total sprint population, sample size, random seed, and sampled ticket keys in Section 1.
  3. *Closed-Loop Jira Corrective-Action Tracking (`REQ-PRO-091.3`)*:
     • For any audit evaluating a `NON-COMPLIANT` overall verdict or finding, Section 4 of `docs/engineering/qa_audits/<sprint>.md` SHALL link every non-compliant finding to a distinct, dedicated Jira corrective-action ticket (`ca_key != audited_key`). Self-referential links naming the audited ticket itself are strictly prohibited.
     • The QA audit tool SHALL idempotently create or link dedicated corrective action tickets (`issuetype_id="10008"` / `Verbesserung`, parent Epic `ATT-8138` or `ATT-232`) in raw Backlog (`add_to_sprint=False`, `rank_top=True`), deduplicated per non-conformance category per sprint (`[QA Corrective Action] <Category> - Sprint <Sprint>`), querying duplicates via `get_search_endpoint` / `paged_jql_search` (`/rest/api/3/search/jql` on Cloud) and failing loudly without synthetic `ATT-CA-*` fallback if live Jira creation fails (`REQ-PRO-118`).
     • `tools/verify_requirement_governance.py --check-qa-audits` SHALL enforce `ca_key != audited_key` for current and future sprints while grandfathering legacy audits prior to sprint `2026-11-16_02_Spät`.
  4. *Retrospective & Ceremony Corrective Action Tracking & Escalation (`REQ-PRO-091.4`)*:
     • `.agents/skills/sprint-planner/SKILL.md` Ceremony 1 (Sprint Planning) SHALL mandate reviewing open QA corrective action tickets from the prior sprint's audit.
     • Ceremony 2 (Sprint Review & Retrospective) SHALL mandate a dedicated 'QA Findings & Corrective Actions' section in `docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md`, auditing the status of all active corrective action tickets.
     • When an open QA corrective action remains unresolved for more than 2 sprints (> 2 sprints) or repeats across consecutive sprints, Ceremony 2 SHALL explicitly escalate the deviation to the Product Owner (PO) in the Retrospective deliverable.
  5. *Automated Governance Verifier Enforcement (`REQ-PRO-091.5`)*:
     • `tools/verify_requirement_governance.py --check-qa-audits` (and Höchstmaß mirror) SHALL validate that:
       (a) Auditor role is independent (not `coordinator` or `agent1`),
       (b) Sample size is $\ge \max(2, \lceil 0.10 \times N \rceil)$ or documented rationale, and
       (c) Every `NON-COMPLIANT` audit record contains valid Jira ticket links for each non-compliant finding.
  6. *Objective Process-Compliance Verification Criteria (`REQ-PRO-091.6`)*:
     • Living Documentation Parity: `qa-audit` SHALL load and evaluate referenced requirement IDs against all living requirement domain shards in `docs/requirements/req_*.md` (~1,194 active requirements) and `docs/tests/tst_*.md`, rather than summary-only master tables.
     • Issue Description Ingestion: `fetch_all_sprint_issues` SHALL include the `description` field in Jira Cloud queries to evaluate requirement references in ticket bodies.
     • AccountId-Based 4-Augen Role Separation: `qa-audit` SHALL verify author and reviewer separation by evaluating Atlassian `accountId`s against `ROLE_ACCOUNT_IDS` rather than matching display-name strings.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-091` (`REQ-PRO-091.1`–`REQ-PRO-091.6`), refining and extending `REQ-PRO-061` (*Sprint-Level QA Process-Compliance Audit*). Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/`, `.agents/skills/sprint-planner/SKILL.md`, and `docs/engineering/qa_audits/`.
  2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.3 via ATT-6154 (`REQ-PRO-061`); extended in Sprint 2026-46.6 via ATT-7558; bugfixed in Sprint 2026-11-16_02_Spät via ATT-8152 (Finding M5); corrective action escalation and retro tracking added via ATT-8153; corrected `issuetype_id="10008"` and Cloud v3 search / fail-loud guard via ATT-8559 (`REQ-PRO-118`).
  3. *Root Reason for Existing Formulation*: Early tooling prioritized basic automated markdown generation and checked only `docs/requirements.md` before domain shards were introduced; description was omitted from query; role separation relied on display-name heuristics; self-referential ticket keys were used as placeholders before closed-loop corrective action ticket creation under Epic ATT-8138/ATT-232 and multi-sprint PO escalation were integrated.
  4. *Preservation of Core Invariants*: Existing CLI commands (`qa-audit`, `--check-qa-audits`) remain backward-compatible; pure Python standard library; zero production Android runtime code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* an execution of `qa-audit`,
  • *When* the auditor role is evaluated,
  • *Then* it executes under an independent role (`qa` / `agent2`), and aborts if invoked under `coordinator` or `agent1`.
  • *Given* a sprint with $N$ completed parent tickets,
  • *When* `qa-audit` draws the sample,
  • *Then* the sample size is $\ge \max(2, \lceil 0.10 \times N \rceil)$ with seeded random selection, documenting seed and sampled keys.
  • *Given* an audit record with a `NON-COMPLIANT` finding,
  • *When* validated by `verify_requirement_governance.py --check-qa-audits`,
  • *Then* it passes only if every non-compliant finding links to an authentic Jira corrective-action ticket.
  • *Given* Ceremony 1 in `sprint-planner/SKILL.md`,
  • *When* executed at Sprint-Start,
  • *Then* it includes verification of open corrective action tickets from the prior sprint's QA audit.
  • *Given* a compliant ticket whose requirement resides in a domain shard,
  • *When* `qa-audit` evaluates Living Documentation Parity,
  • *Then* the requirement ID is resolved against `docs/requirements/req_*.md` shards and passes.
  • *Given* subtasks reviewed by `agent2`,
  • *When* `qa-audit` evaluates 4-Augen Role Separation,
  • *Then* `accountId`s are matched against `ROLE_ACCOUNT_IDS` and passes.
  • *Given* an execution of `qa-audit` with non-compliant tickets,
  • *When* Section 4 is formatted,
  • *Then* it generates or links dedicated corrective action tickets in raw Backlog deduplicated by category, ensuring `ca_key != audited_key`.
  • *Given* an audit report evaluated by `verify_qa_audits`,
  • *When* checked for current and future sprints,
  • *Then* it rejects reports containing self-referential corrective action links (`ca_key == audited_key`), while grandfathering legacy historical sprints prior to `2026-11-16_02_Spät`.
  • *Given* Ceremony 2 in `sprint-planner/SKILL.md`,
  • *When* executed at Sprint-End,
  • *Then* it includes a mandatory 'QA Findings & Corrective Actions' section in the Retrospective and escalates open actions older than 2 sprints to the PO.
- **Invariants**: Zero Android runtime code changes; pure Python 3 standard library; Rule 3 human-only gate preserved.

### REQ-PRO-092: Process Metrics Credibility, Bug Severity Rubric, and Downstream Gate Defect Accounting [Verified]
- **Tests**: `TST-PRO-092`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/`, `docs/project_governance.md`, `.agents/skills/sprint-planner/SKILL.md`
- **Rationale**: ASPICE evaluation finding F2 identified that quantitative process metrics recorded in `process_metrics.jsonl` lacked credibility: 100% first-time pass rates across Gates 1–5 contradicted 22 Stage 6 bounces, 100% of bugs were deflated to "Minor" because Jira fetch omitted labels and description, gate subtasks without verdicts inflated FTPR via fallback, ADF comment bodies crashed parsers, downstream escapes were unmodeled, and plausibility warnings were absent. Correcting metric ingestion, parsing ADF structures, segregating unverified subtasks, attributing downstream escapes, enforcing a bug severity rubric, and auditing gate spot-checks satisfies ASPICE PA 4.1, PA 4.2, and SUP.9 (ATT-7559, Parent Epic ATT-232, refining and extending `REQ-PRO-058`, `REQ-PRO-059`, and `REQ-PRO-063`):
- **Description**:
  1. *Jira API Metric Field Ingestion (`REQ-PRO-092.1`)*:
     • `collect_sprint_metrics_for_id` in `tools/jira_util.py` (and Höchstmaß mirror) SHALL include `labels` and `description` in the queried issue fields (`summary,status,issuetype,priority,subtasks,comment,labels,description,{STORY_POINTS_FIELD}`).
     • Bug severity detection SHALL prioritize explicit severity labels (`severity-critical`, `severity-major`, `severity-minor`, `severity-cosmetic`), followed by regex extraction of `**Severity**: <Level>` or `Severity: <Level>` from issue descriptions, before falling back to priority mappings.
  2. *Robust Atlassian Document Format (ADF) Comment Body Parsing (`REQ-PRO-092.2`)*:
     • `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments` SHALL support both string bodies and nested Jira Cloud v3 ADF dictionary structures without raising `AttributeError` or `TypeError`.
     • Text nodes in ADF document structures SHALL be extracted recursively prior to regex verdict matching.
  3. *Segregation of Unverified Gate Subtasks (`REQ-PRO-092.3`)*:
     • Gate subtasks (Gates 1–5) in status `Erledigt` (or done/closed) that lack an explicit, parsable reviewer verdict comment (`PASS` / `REVISION`) SHALL NOT be counted as `passed_first_time`.
     • Such subtasks SHALL be categorized as `unverified` and tracked separately in gate metrics (`gate_data[g]["unverified"]`).
  4. *Downstream Defect Escape Accounting across Swiss Cheese Layers (`REQ-PRO-092.4`)*:
     • Swiss Cheese barrier escape metrics ($p_g$) SHALL account for defects identified downstream at Gate 6 (Stage 6 Cold Review bounces and escalations).
     • Stage 6 bounces SHALL be attributed to the responsible upstream gate layers (Gates 3, 4, 5) when computing composite escape rates, ensuring $p_g > 0$ when defects escape to Stage 6.
  5. *Metric Plausibility Warning Thresholds (`REQ-PRO-092.5`)*:
     • When generating or recording sprint metrics, `sprint_metrics.py` SHALL evaluate a plausibility check: if the first-time pass rate across $\ge 20$ evaluated gate subtasks is 100.0% without a single revision or bounce, a prominent plausibility warning (`PA 4.1 Plausibility Warning: Potential Gate Discrimination Blind Spot`) SHALL be recorded in metrics metadata and markdown outputs.
  6. *Bug Severity Rubric Governance & Spot-Check Auditing (`REQ-PRO-092.6`)*:
     • `docs/project_governance.md` SHALL document the authoritative 4-tier Bug Severity Rubric:
       - **Critical**: Startup crash, tracking loop termination, data corruption/loss, ANR blocking core app workflow; release-blocking.
       - **Major**: Crash/ANR in non-critical screens, primary sensor disconnection recovery failure, severe battery drain, functional impairment with workaround.
       - **Minor**: Non-crashing functional defect, transient UI glitch, localized string formatting issue.
       - **Cosmetic**: Visual styling quirk, margin/alignment flaw, icon tint mismatch.
     • `create_issue` in `tools/jira_util.py` SHALL enforce that bugs originating from crashes, ANRs, or Crashlytics traces are assigned at least `Major` or `Critical` severity unless explicitly exempted with rationale.
     • Sprint QA audits and retrospective checklists SHALL record a periodic spot-check of gate verdicts with discrepancy rates documented.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-092` (`REQ-PRO-092.1`–`REQ-PRO-092.6`), extending `REQ-PRO-058` (*Stage 6 Final Review*), `REQ-PRO-059` (*PA 4.1 Quantitative Process Metrics*), and `REQ-PRO-063` (*Problem Resolution Management*). Targets `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/`, `docs/project_governance.md`, and `.agents/skills/sprint-planner/SKILL.md`.
  2. *Historical Origin & Commit Trace*: Finding F2 in ASPICE independent evaluation (2026-11-12) confirmed that 100% first-pass rates and 100% Minor bug ratings undermined the credibility of process data under PA 4.x and SUP.9.
  3. *Root Reason for Existing Formulation*: Initial implementations used minimal field queries and simple fallback assumptions (`Erledigt -> passed_first_time`) to simplify sprint metrics generation.
  4. *Preservation of Core Invariants*: Backward-compatible JSONL output schema; standard library only; zero runtime app code changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* sprint issues fetched for metrics collection,
  • *When* `collect_sprint_metrics_for_id` queries Jira,
  • *Then* `labels` and `description` are included and bug severities correctly reflect labels (e.g. 1 Critical and 5 Major in Sprint 441).
  • *Given* an ADF-formatted comment body dict,
  • *When* `parse_verdicts_from_comments` processes the comment,
  • *Then* text is extracted recursively and verdicts parsed without throwing exceptions.
  • *Given* gate subtasks without verdict comments in status `Erledigt`,
  • *When* gate metrics are evaluated,
  • *Then* they are recorded as `unverified` and not counted toward `passed_first_time`.
  • *Given* Stage 6 bounces in a sprint,
  • *When* escape rates are calculated,
  • *Then* downstream escapes are reflected in upstream gate escape probabilities.
  • *Given* 100% FTPR across $\ge 20$ gate reviews without rejections,
  • *When* sprint metrics are calculated,
  • *Then* a plausibility warning is emitted in the output.
  • *Given* a bug ticket created with crash/ANR keywords,
  • *When* validated against the severity rubric,
  • *Then* it is assigned at least `Major` or `Critical` severity.
- **Invariants**: Pure Python standard library; zero Android application runtime footprint; Rule 3 human-only gate preserved.


---

### REQ-PRO-093: Normative Governance Rules Restructuring and Living Document Shard Size Capping [Verified]
- **Tests**: `TST-PRO-093`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `.agents/rules/aspice_governance.md`, `tools/hoechstmass/.agents/rules/aspice_governance.md`, `docs/engineering/rule_history_archive.md`, `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py`
- **Rationale**: Under ASPICE PA 3.1 and evaluation finding F8, process governance rules must be concise, normative (who/what/when), and free of embedded ticket quotes, retrospective stories, and superseded contradictions, while preserving a permanent historical cross-reference archive. Furthermore, living documentation shards must have an enforced maximum line limit and automated tooling checks to flag and partition oversized shards.
- **Description**:
  1. *Normative Rule Consolidation (`REQ-PRO-093.1`)*: `.agents/rules/aspice_governance.md` and `tools/hoechstmass/.agents/rules/aspice_governance.md` SHALL define the 9 Core Rules strictly as concise normative requirements with stable sub-rule IDs (`Rule 1.1`–`Rule 9.5`), removing all inline ticket quotes and historical anecdotes.
  2. *Rule History & Rationale Archive (`REQ-PRO-093.2`)*: `docs/engineering/rule_history_archive.md` SHALL maintain the complete historical mapping of Rules 1–84+, capturing historical origin commits, retrospective rationales, and exact quotes.
  3. *Shard Size Limit & Tooling Check (`REQ-PRO-093.3`)*: `tools/shard_living_docs.py` SHALL define a default maximum shard size (`DEFAULT_MAX_SHARD_LINES = 4000`), provide a `--check-size` inspection CLI, and flag any requirement shard that exceeds the configured limit.
  4. *Oversized Shard Partitioning Facility (`REQ-PRO-093.4`)*: `tools/shard_living_docs.py` SHALL provide automated partitioning facilities (`split_oversized_shard`) to split oversized requirement shards into compliant sub-shards while strictly preserving 100% ID uniqueness, master index table parity, and bidirectional traceability.
  5. *Governance Contract Invariance (`REQ-PRO-093.5`)*: All existing governance contract tests (`AspiceSweAuditContractTest.kt`, `HoechstmassCoreFrameworkContractTest.kt`, `test_agent_emulator.py`, `test_verify_requirement_governance.py`) SHALL pass with 100% success.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-093`. Targets `.agents/rules/aspice_governance.md`, `docs/engineering/rule_history_archive.md`, and `tools/shard_living_docs.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.6 via ticket `ATT-7565` under Epic `ATT-232` (Finding F8).
  3. *Root Reason for Existing Formulation*: Rules accumulated inline rationale and quotes over 46 sprints; sharding was originally 9 domains without per-shard line limits.
  4. *Preservation of Core Invariants*: Human Decision Gate, two-tier device policy, 100% contract test pass rate, and zero runtime regression preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `.agents/rules/aspice_governance.md`,
  • *When* inspected,
  • *Then* each rule consists of concise normative clauses with stable IDs, and retrospective quotes/history live in `docs/engineering/rule_history_archive.md`.
  • *Given* a requirement shard exceeding the maximum line limit,
  • *When* `tools/shard_living_docs.py --check-size` is executed,
  • *Then* the tool flags the oversized shard with line counts.
  • *Given* the automated test suite,
  • *When* `python3 tools/test_shard_living_docs.py` and `python3 tools/verify_requirement_governance.py` are executed,
  • *Then* all tests pass with exit code 0.
- **Invariants**: Zero changes to production runtime logic; existing master table parity intact; Human Decision Gate inviolable.


---

### REQ-PRO-094: Legacy Requirement Attribute Backfill and Strict SWE.1 BP2 Enforcement [Verified]
- **Tests**: `TST-PRO-094`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/requirements/req_tracking_cockpit.md`, `docs/requirements/req_maps_navigation.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Rationale**: Under ASPICE SWE.1 BP2 and BP3, all software requirements must define Priority, Type, and Verification Method attributes. The legacy tolerance in `tools/verify_requirement_governance.py` permitted 115 legacy requirements to omit these attributes with non-fatal warnings (ASPICE finding F5). Backfilling attributes across all living shards and enabling strict enforcement by default guarantees 100% attribute completeness and prevents future regressions.
- **Description**:
  1. *Legacy Attribute Backfill (`REQ-PRO-094.1`)*: All 115 legacy requirements across `docs/requirements/req_tracking_cockpit.md` and `docs/requirements/req_maps_navigation.md` SHALL define explicit, valid `Priority` (`Critical`, `High`, `Medium`, `Low`), `Type` (`Functional`, `Non-Functional`), and `Verification Method` (`Unit`, `Integration`, `On-Device Manual`, `Review`, `Formal Analysis`) attributes.
  2. *Strict Attribute Enforcement by Default (`REQ-PRO-094.2`)*: `tools/verify_requirement_governance.py` (`verify_requirement_attributes` and `verify_governance`) SHALL enforce valid Priority, Type, and Verification Method attributes by default (`enforce_attributes=True`), treating any missing or invalid attribute in any requirement shard as a fatal verification error.
  3. *Zero Warning Requirement (`REQ-PRO-094.3`)*: Executing `tools/verify_requirement_governance.py` SHALL emit zero legacy-missing-attribute warnings and confirm 100% attribute compliance.
  4. *Regression Test Parity (`REQ-PRO-094.4`)*: `tools/test_verify_requirement_governance.py` SHALL verify that any requirement lacking attributes fails verification unconditionally.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-094`. Targets `docs/requirements/req_tracking_cockpit.md`, `docs/requirements/req_maps_navigation.md`, and `tools/verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.6 via ticket `ATT-7562` under Epic `ATT-232` (Finding F5).
  3. *Root Reason for Existing Formulation*: Legacy tolerance was introduced in `ATT-6147` as an interim bridge during ASPICE CL2 remediation.
  4. *Preservation of Core Invariants*: Human Decision Gate, zero Android runtime logic changes, and 100% test pass rate preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* all requirement shards under `docs/requirements/`,
  • *When* `tools/verify_requirement_governance.py` executes,
  • *Then* zero legacy-missing-attribute warnings are emitted and 100% attribute compliance is certified.
  • *Given* any requirement lacking Priority, Type, or Verification Method,
  • *When* the governance verifier runs,
  • *Then* it fails unconditionally with exit code 1.
- **Invariants**: Zero changes to production runtime logic; existing master table parity intact; Human Decision Gate inviolable.


---

### REQ-PRO-095: Weekly Dual-Vendor Holistic Code and Architecture Review Governance and Backlog Orchestration [Verified]
- **Tests**: `TST-PRO-095`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/management/cm_plan.md`, `docs/coding_standards.md`, `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/release-manager/SKILL.md`, `docs/engineering/code_reviews/coverage_matrix.md`
- **Rationale**: In-sprint Stage 6 reviews only inspect single-ticket diffs in isolation. To systematically uncover latent architectural debt, concurrency hazards, resource leaks, and test gaps across the ~200k+ LoC codebase, mandatory weekly dual-vendor reviews (Google Gemini + Anthropic Claude) inspecting entire subsystems alongside delta changes must be governed, tracked in a coverage matrix, and automatically scheduled at the top of the Product Backlog.
- **Description**:
  1. *Weekly Dual-Vendor Full-Subsystem Review Policy & Generic Model Specification (`REQ-PRO-095.1`, [Verified] ATT-8426)*: `docs/coding_standards.md` (§5.12), `docs/management/release_plan.md` (§1.1), `docs/engineering/code_reviews/template_dual_vendor_code_review.md`, `.agents/rules/aspice_governance.md`, and `tools/jira_util.py` review ticket templates SHALL generically specify the respective *current top model* ("current top model" / frontier model) of both Google Gemini and Anthropic Claude rather than hardcoding transient version strings (`Gemini 2.5 Pro / Gemini 4 Carbon`, `Claude 3.7 Sonnet`). The concrete model identifier, checkpoint, and version SHALL strictly be recorded dynamically in the completed review artifact (`YYYY-MM-DD_dual_vendor_code_review.md`) and `coverage_matrix.md` row upon execution. Furthermore, under the *Claude Non-Simulation Protocol*, Antigravity agents running on Google models SHALL NEVER simulate, fabricate, or mock Anthropic Claude evaluations; if Claude API or external execution is unavailable, the review deliverable SHALL explicitly mark the Claude evaluation as `PENDING / EXTERNAL HANDOFF REQUIRED` until executed via genuine external Claude CLI or session, preserving ASPICE Finding K2 / evidence integrity.
  2. *Subsystem Audit Coverage Matrix & Package Completeness (`REQ-PRO-095.2`, [Verified] ATT-8426)*: `docs/engineering/code_reviews/coverage_matrix.md` SHALL maintain an exhaustive mapping of the entire codebase into subsystem domains, explicitly incorporating previously omitted core packages including `repositories/` (data layer & StateFlows), `cloud/` & `onlinecommunities/` (remote sync & integration), and `segments/` & `climbs/` (real-time segment & ascent detection), tracking audit timestamps, commit SHAs, executed model versions, and status (`UNREVIEWED` $\to$ `AUDITED`) across continuous review cycles.
  3. *Automated Top-of-Backlog Review Ticket Creation & Decoupled Cadence (`REQ-PRO-095.3`, [Verified] ATT-8426)*: `tools/jira_util.py` (`ensure-weekly-reviews` / `ensure_weekly_review_ticket()`) SHALL select target subsystem slices via `get_next_review_subsystem()`, which prioritizes the first `UNREVIEWED` domain slice (or oldest audited slice once fully audited). Overdue evaluation SHALL be governed by actual 7-day review currency without blocking on `UNREVIEWED` status when an audit occurred within $\le 7$ days. When overdue and no open review ticket exists under `ATT-232`, it SHALL create a deduplicated review ticket ranked at `rank-top`.
  4. *Sprint Ceremony & 7-Day Release Currency Gate (`REQ-PRO-095.4`, [Verified] ATT-8426)*: `release-manager/SKILL.md` (pre-release gate) and `sprint-planner/SKILL.md` (Ceremonies 1 & 2) SHALL verify review currency via `tools/jira_util.py is_weekly_review_overdue()`. `is_weekly_review_overdue()` SHALL evaluate whether the latest completed review record in `docs/engineering/code_reviews/` is $\ge 7$ days old (or if zero completed reviews exist), strictly decoupling the release currency gate from requiring that all subsystem slices in `coverage_matrix.md` have completed their first cycle, preventing a 9-week release preparation deadlock. All PO operational rules (`05:00/15:00/22:00` cadence, 2-lane throttle + Stage 6 Pro Sparhebel, weekly Kover coupling) SHALL remain strictly preserved.
  5. *Review Tooling & Governance Contract Test Parity (`REQ-PRO-095.5`)*: `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py` SHALL verify `ensure-weekly-reviews` CLI parsing, target subsystem slice selection, deduplication logic, and `code_reviews` directory canonical registration.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-095`. Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/management/cm_plan.md`, `docs/coding_standards.md`, `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/release-manager/SKILL.md`, `docs/engineering/code_reviews/coverage_matrix.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.8 via ticket `ATT-8213` under Epic `ATT-8138` (ASPICE 4.0 Audit Remediation); amended in Sprint 2026-11-16_03_Nacht via `ATT-8426` to mandate generic top models, establish Claude non-simulation protocol, decouple 7-day release gate from UNREVIEWED deadlock, and expand package coverage.
  3. *Root Reason for Existing Formulation*: Prior review governance (`REQ-PRO-062`) was confined to per-sprint diff reviews without full-subsystem scope or automated backlog scheduling; initial implementation hardcoded contemporary model strings and conflated UNREVIEWED slice prioritization with 7-day release gating.
  4. *Preservation of Core Invariants*: ASPICE `GP 2.1.6` cross-vendor independence, 7-day review cadence, Finding K1/K2 zero-fabrication evidence integrity, PO operational rules (`05:00/15:00/22:00` cadence, 2-lane throttle, Stage 6 Pro Sparhebel, weekly Kover coupling), zero `app/src/main/` runtime code changes, and pure Python stdlib offline resilience strictly preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/coding_standards.md`, `docs/management/cm_plan.md`, `docs/engineering/code_reviews/template_dual_vendor_code_review.md`, and `tools/jira_util.py`,
  • *When* inspected,
  • *Then* they specify generic current top models for Google Gemini and Anthropic Claude without hardcoding transient version strings.
  • *Given* an Antigravity agent without external Claude API or CLI access executing a review ticket,
  • *When* generating the review artifact,
  • *Then* the agent does not fabricate Claude findings and marks Claude review as `PENDING / EXTERNAL HANDOFF REQUIRED`.
  • *Given* `docs/engineering/code_reviews/coverage_matrix.md`,
  • *When* inspected,
  • *Then* all core packages including `repositories/`, `cloud/`, `onlinecommunities/`, `segments/`, and `climbs/` are comprehensively mapped into domain slices.
  • *Given* at least one completed review in `docs/engineering/code_reviews/` $\le 7$ days old and remaining `UNREVIEWED` subsystems in `coverage_matrix.md`,
  • *When* `is_weekly_review_overdue()` executes,
  • *Then* it returns `False`, allowing release branch initiation without a 9-week deadlock.
  • *Given* `> 7 days` have elapsed since the last review or zero reviews exist,
  • *When* `ensure-weekly-reviews` runs,
  • *Then* a deduplicated ticket targeting the next subsystem is created under `ATT-232` and ranked at `rank-top`.
  • *Given* an overdue weekly review (> 7 days),
  • *When* `release-manager` checks release preconditions,
  • *Then* release readiness flags the overdue review.
- **Invariants**: Zero changes to production runtime logic; existing master table parity intact; Human Decision Gate inviolable.


---

### REQ-PRO-096: ASPICE 4.0 SUP.1 Quality Assurance Strategy, Evidence Storage, and Dual-Vendor Cross-Audit Automation [Verified]
- **Tests**: `TST-PRO-096`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/management/qa_plan.md`, `docs/management/cm_plan.md`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/engineering/aspice_audits/`
- **Rationale**: ASPICE 4.0 `SUP.1.BP1-BP6` and `GP 2.1.6` mandate a formal project QA plan ensuring independence of quality assurance from development. To avoid single-model blind spots, regular weekly independent blind ASPICE 4.0 cross-audits across two distinct AI vendors (Google Gemini + Anthropic Claude) must be institutionalized, verified in canonical storage (`docs/engineering/aspice_audits/`), and automatically scheduled at the top of the Product Backlog.
- **Description**:
  1. *Formal Project QA Strategy & Plan (`REQ-PRO-096.1`)*: `docs/management/qa_plan.md` SHALL establish the comprehensive ASPICE 4.0 `SUP.1` quality assurance strategy satisfying `BP1` (QA strategy), `BP2` (quality criteria), `BP3` (assurance of work product compliance), `BP4` (assurance of process compliance), `BP5` (problem escalation & resolution), and `BP6` (quality records), anchoring technical role independence (`GP 2.1.6`) across 5 verification layers.
  2. *Canonical ASPICE Audit Evidence Store (`REQ-PRO-096.2`)*: `docs/engineering/aspice_audits/` SHALL be established as a canonical directory in `cm_plan.md` and `verify_requirement_governance.py` (`ALLOWED_ENGINEERING_ENTRIES`), housing `README.md` and versioned dual-vendor audit evidence records (`YYYY-MM-DD_dual_vendor_aspice_audit.md`), starting with the `2026-11-15` baseline audit for Epic `ATT-8138`.
  3. *Automated Top-of-Backlog Audit Ticket Creation (`REQ-PRO-096.3`)*: `tools/jira_util.py` SHALL implement `ensure_weekly_aspice_audit_ticket()` (`ensure-weekly-reviews` CLI), which verifies whether `> 7 days` have elapsed since the latest completed ASPICE audit in `docs/engineering/aspice_audits/`; if overdue and no open weekly ASPICE audit ticket exists, it SHALL automatically create a review ticket under Epic `ATT-232` and place it at `rank-top`.
  4. *Sprint & Release Lifecycle Currency Enforcement (`REQ-PRO-096.4`)*: `sprint-planner/SKILL.md` (Ceremonies 1 & 2) and `release-manager/SKILL.md` SHALL verify that both weekly ASPICE audits and code reviews are current ($\le 7$ days) prior to closing sprint reviews or initiating production release branches.
  5. *QA Tooling & Contract Test Parity (`REQ-PRO-096.5`)*: `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py` SHALL maintain 100% test pass rate for `aspice_audits` canonical directory registration, audit currency calculation, and deduplicated ticket creation.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-096`. Targets `docs/management/qa_plan.md`, `docs/management/cm_plan.md`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/engineering/aspice_audits/`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.8 via ticket `ATT-8212` under Epic `ATT-8138` (ASPICE 4.0 Audit Remediation).
  3. *Root Reason for Existing Formulation*: Prior QA provisions (`REQ-PRO-061`, `REQ-PRO-091`) governed in-sprint ticket sampling (`qa-audit`) but lacked full SUP.1 plan governance and automated cross-vendor ASPICE audit scheduling.
  4. *Preservation of Core Invariants*: Human Decision Gate, commit hygiene, zero runtime code changes in `app/src/main/`, and pure Python stdlib offline resilience preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/management/qa_plan.md`,
  • *When* inspected against ASPICE 4.0 `SUP.1` and `GP 2.1.6`,
  • *Then* it specifies all 5 QA layers, independence guarantees, escalation paths, and the regular weekly dual-vendor ASPICE cross-audit procedure.
  • *Given* `docs/engineering/aspice_audits/` and `docs/management/cm_plan.md`,
  • *When* inspected,
  • *Then* `aspice_audits` is registered in `cm_plan.md` and contains the `2026-11-15` dual-audit baseline record referencing `ATT-8138`.
  • *Given* `> 7 days` have elapsed since the last ASPICE audit,
  • *When* `ensure-weekly-reviews` executes,
  • *Then* a deduplicated Weekly Dual-Vendor ASPICE Audit ticket is created under `ATT-232` at `rank-top`.
- **Invariants**: Zero changes to production runtime logic; existing master table parity intact; Human Decision Gate inviolable.


---

### REQ-PRO-097: Capacity-Bound Multi-Lane Sprint Commitment, V5.0.0 Release Planning, and Jira FixVersion Governance [Verified]
- **Tests**: `TST-PRO-097`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/management/release_plan.md`, `tools/jira_util.py`, `tools/test_jira_accounts.py`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`, `.agents/rules/aspice_governance.md`
- **Rationale**: ASPICE 4.0 `MAN.3` (Project Management, Finding `M10`), `SUP.8` (Configuration Management), and `SUP.10` (Change Request Management) mandate capacity-bound sprint commitments matching team velocity and clear release planning. Historically, sprints were overloaded up to 283 SP, completion fell to 35%, no `V5.0.0` release plan existed, and `show_issue()` in `jira_util.py` emitted false positive FixVersion warnings on active in-sprint tickets, conflicting with Rule 8.3 (FixVersion assigned only upon completion).
- **Description**:
  1. *Formal V5.0.0 Release Plan (`REQ-PRO-097.1`)*: `docs/management/release_plan.md` SHALL be established as a canonical configuration item in `cm_plan.md`, specifying target release `V5.0.0`, milestone epics, scheduled 12h sprint cycles, cumulative Google Pixel 10 physical daily-ride workout verification (`ATT-8141`), quality criteria, and gate exit criteria.
  2. *Lane-Aware Greedy Bin-Packing with Capacity Ceiling (`REQ-PRO-097.2`)*: `select_greedy_bin_pack_tickets()` in `tools/jira_util.py` SHALL iterate candidate tickets from `Vom PO Freigegeben` (ID `447`) in strict `ORDER BY rank ASC`, filling up to `max_lanes` (default 3) disjoint Epic lanes up to a configurable per-lane capacity ceiling (`max_sp_per_lane`, default 10 SP, or max tickets per lane). When a lane reaches capacity on Epic A, excess tickets of Epic A SHALL be left in `Vom PO Freigegeben` without skipping higher-ranked eligible tickets, while subsequent disjoint Epics fill Lanes 2 and 3.
  3. *Jira FixVersion Rule Alignment & Release Plan Verification Staging (`REQ-PRO-097.3`, [Verified] ATT-8426)*: In `tools/jira_util.py` (`show_issue()`), the warning `WARNING: Mandatory field missing!` for missing `FixVersion` SHALL be suppressed for active in-sprint and in-flight tickets (status != `Erledigt`), and SHALL be emitted strictly when an issue is in terminal status (`Erledigt`) without an assigned `fixVersions` array, aligning tooling with Core Rule 8.3. Furthermore, `docs/management/release_plan.md` Section 3 SHALL synchronize with Finding K1 / ATT-8141 evidence architecture by referencing `docs/releases/V5.0.0/sys_verification_record.md` (replacing deprecated `docs/engineering/field_tests/`) and staging physical on-device / human-execution verification tickets in protected pseudo-sprint `Bereit für PO Systemtest` (ID 454, ATT-8423) without contaminating development sprints.
  4. *Lifecycle Skills & Governance Parity (`REQ-PRO-097.4`)*: `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md` SHALL reflect the capacity-bound multi-lane packing from `Vom PO Freigegeben`, the 12h cadence (`04:00` / `16:00`), and `V5.0.0` release plan compliance.
  5. *Tooling Test Coverage Parity (`REQ-PRO-097.5`)*: `tools/test_jira_accounts.py` SHALL verify multi-ticket lane packing, capacity boundary constraints, rank order integrity, and FixVersion warning emission logic with 100% test pass rate.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-097`. Targets `docs/management/release_plan.md`, `tools/jira_util.py`, `tools/test_jira_accounts.py`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`, `.agents/rules/aspice_governance.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.8 via ticket `ATT-8162` under Epic `ATT-8138` (ASPICE 4.0 Audit Remediation); amended in Sprint 2026-11-16_03_Nacht via `ATT-8426` to synchronize release plan Section 3 field test path and stage verification into pseudo-sprint 454.
  3. *Root Reason for Existing Formulation*: Previous sprint planning lacked formal release plan grounding, allowed sprint over-commitment, and `show_issue()` warned indiscriminately on all tickets lacking FixVersion; Section 3 cited pre-K1 field test paths.
  4. *Preservation of Core Invariants*: Rule 3 human gates, detached worktree isolation, POSIX flock merge lock, PO operational rules (`05:00/15:00/22:00` cadence, 2-lane throttle, Stage 6 Pro Sparhebel, weekly Kover coupling), and pure Python stdlib offline resilience strictly preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `Vom PO Freigegeben` (ID 447) with multiple tickets across disjoint Epics,
  • *When* `select_greedy_bin_pack_tickets()` is invoked with `max_lanes=3` and `max_sp_per_lane`,
  • *Then* it selects tickets in strict rank order across disjoint Epics without exceeding per-lane capacity and leaves excess same-Epic tickets in the queue.
  • *Given* an active in-sprint ticket without `FixVersion`,
  • *When* `python3 tools/jira_util.py show <KEY>` is run,
  • *Then* no `WARNING: Mandatory field missing!` warning is emitted for `FixVersion`.
  • *Given* `docs/management/release_plan.md`,
  • *When* inspected against ASPICE 4.0 `MAN.3` and `SUP.8`,
  • *Then* it specifies `V5.0.0` scope epics, schedule, cumulative Pixel 10 workout verification, and release exit criteria.
  • *Given* `docs/management/release_plan.md` Section 3,
  • *When* inspected,
  • *Then* it references `docs/releases/V5.0.0/sys_verification_record.md` and documents staging of physical on-device verification into pseudo-sprint `Bereit für PO Systemtest` (ID 454).
- **Invariants**: Zero changes to production runtime logic (`app/src/main/`); existing master table parity intact; Human Decision Gate inviolable.


---

### REQ-PRO-098: Human-Attested Field Verification Evidence, Telemetry Extractor & Release Guarding [Verified]
- **Tests**: `TST-PRO-098`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/extract_pixel_field_evidence.py`, `tools/generate_release_verification_summary.py`, `docs/releases/TEMPLATE.md`, `docs/releases/V5.0.0/sys_verification_record.md`, `.agents/rules/aspice_governance.md`
- **Rationale**: ASPICE 4.0 Finding K1 (SYS.4/5, SWE.6, SUP.1, SUP.8) identified that autonomous agents must never fabricate physical hardware execution or test results. System-level verification for releases must derive from authentic workout sessions recorded by the PO on the reference Google Pixel 10. Automated tooling must extract authentic telemetry, pre-populate protocol tables (`EXTRACTED_AWAITING_PO_ATTESTATION`), guide upcoming test rides via a coverage-gap report, and enforce human PO attestation prior to release clearance.
- **Description**:
  1. *Authentic Pixel 10 Telemetry Extraction & Verification Gap Coaching (`REQ-PRO-098.1`, [Verified] ATT-8427)*: `tools/extract_pixel_field_evidence.py` SHALL extract authentic workout telemetry from Google Pixel 10 devices and exports. When parsing SQLite databases (`parse_sqlite_database`), it SHALL query authentic tables and columns matching `WorkoutSummariesDatabaseManager.java` (`WorkoutSummaries` columns `_id`, `timeStart`, `timeActive_s`, `distanceTotal_m`, `ascending`, `Sport`, `uploadToStrava`, and `ExtremumValues` columns `workoutID`, `sensorType`, `extremumType`, `value`). It SHALL adhere strictly to Finding K1 zero-fabrication standards: all unmeasured or unrecorded metrics (`sample_loss_pct`, `battery_drain_pct_hr`, `max_rf_dropout_sec`, `fit_valid`, radar presence without sensor stream proof) MUST evaluate to `None` / `"NOT MEASURED"`, across both SQLite and TCX extraction pipelines. Furthermore, `pull_device_evidence_adb` SHALL reliably pull app-private databases directly via `adb exec-out run-as <package> cat databases/WorkoutSummaries.db` with SQLite magic header validation.
  2. *Automated Release Guarding Against Unsubstantiated Verification Evidence (`REQ-PRO-098.2`)*: `tools/generate_release_verification_summary.py` (`collect_sys5_summary()`) SHALL reject any verification record containing placeholder serials (`*-REF-*`), unbacked `PASS` verdicts without authentic telemetry traces, or missing human PO attestation signatures, reporting status `BLOCKED`.
  3. *Release Template & V5.0.0 Verification Protocol Reset (`REQ-PRO-098.3`)*: `docs/releases/TEMPLATE.md` and `docs/releases/V5.0.0/sys_verification_record.md` SHALL be established with a formal Human PO Attestation block (device serial, build SHA, workout/FIT ID references) and reset to `OPEN / NOT PERFORMED`, removing all fabricated Pixel 8/6 data.
  4. *Agent Governance & Human-Execution Lifecycle Tripwire (`REQ-PRO-098.4`)*: `.agents/rules/aspice_governance.md` (and Gate 4/5 checklists) SHALL strictly prohibit autonomous agents from authoring physical hardware test results; tickets requiring real hardware execution SHALL carry label `human-execution` and be restricted to protocol preparation and telemetry extraction by agents.
  5. *Extractor & Release Summary Contract Test Parity (`REQ-PRO-098.5`)*: `tools/test_generate_release_verification_summary.py` and extractor unit tests SHALL verify placeholder rejection, human attestation enforcement, and gap reporting with 100% test pass rate.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-098`. Targets `tools/extract_pixel_field_evidence.py`, `tools/generate_release_verification_summary.py`, `docs/releases/TEMPLATE.md`, `docs/releases/V5.0.0/sys_verification_record.md`, `.agents/rules/aspice_governance.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.8 via ticket `ATT-8141` under Epic `ATT-8138` (ASPICE 4.0 Audit Remediation); amended in Sprint 2026-11-16_03_Nacht via `ATT-8427` to eliminate synthetic/hardcoded metrics and align SQLite queries with authentic `WorkoutSummariesDatabaseManager.java` schema.
  3. *Root Reason for Existing Formulation*: Evaluation finding K1 established that V5.0.0 release verification claimed hardware testing that did not occur; authentic continuous Pixel 10 workouts recorded by PO must drive release evidence through automated extraction and human attestation; initial extractor draft contained hardcoded dummy metrics and camelCase column fallbacks that violated zero-fabrication integrity.
  4. *Preservation of Core Invariants*: Release candidate gating remains strictly blocked until human PO attestation; zero Android runtime app bytecode changes; Human Decision Gate preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/releases/V5.0.0/sys_verification_record.md`,
  • *When* inspected,
  • *Then* all fabricated Pixel 8/6 entries are removed, and all rows report authentic Pixel 10 telemetry or `OPEN / NOT PERFORMED` awaiting human PO attestation.
  • *Given* a verification record with placeholder serials (`*-REF-*`), unbacked PASS claims, or missing human PO attestation,
  • *When* `tools/generate_release_verification_summary.py` runs,
  • *Then* status is reported as `BLOCKED` with specific deviations.
  • *Given* an authentic or exported `WorkoutSummaries.db` database,
  • *When* `parse_sqlite_database()` executes,
  • *Then* it queries authentic `WorkoutSummaries` and `ExtremumValues` tables/columns, extracts true sensor presence, and reports `None` for any unmeasured metric (`sample_loss_pct`, `battery_drain_pct_hr`, `fit_valid`).
  • *Given* an authentic TCX export file without radar or battery streams,
  • *When* `parse_tcx_file()` executes,
  • *Then* it extracts true duration, distance, and trackpoint sensors, while reporting `None` for `sample_loss_pct`, `battery_drain_pct_hr`, `fit_valid`, and `cloud_synced`, with `has_radar == False`.
  • *Given* a connected reference device via ADB,
  • *When* `pull_device_evidence_adb()` executes,
  • *Then* it pulls `WorkoutSummaries.db` directly via `exec-out run-as` and verifies the SQLite format 3 magic header.
- **Invariants**: Zero changes to production runtime logic (`app/src/main/`); existing master table parity intact; Human Decision Gate inviolable.


---

### REQ-PRO-099: Test Result-Driven Verification Status & Evidence Gating [Verified]
- **Tests**: `TST-PRO-099`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `.agents/skills/stage5-verification/SKILL.md`
- **Rationale**: Under ASPICE 4.0 Major Finding M1 (SWE.4 BP4/BP5, SWE.6 BP4/BP5, SWE.1 BP5), `[Verified]` status must not be set unconditionally by Stage 5 workflow transitions (`auto_verify_living_docs_on_stage5()`). Living test (`TST-XXX`) and requirement (`REQ-XXX`) verification statuses must be derived strictly from passing, machine-readable test execution evidence (JUnit XML / test runner results) or authentic human-attested protocols. Unbacked or failing tests must block `[Verified]` promotion, while a ratchet baseline protects existing repository builds.
- **Description**:
  1. *Machine-Readable Test Result Ingestion (`REQ-PRO-099.1`)*: `tools/verify_requirement_governance.py` SHALL parse machine-readable test results (JUnit XML files under `app/build/test-results/**/TEST-*.xml` or test result archives) and correlate testcases to `TST-XXX` IDs via test method names, class names, and code comments.
  2. *Verification Evidence Check CLI (`REQ-PRO-099.2`)*: `tools/verify_requirement_governance.py` SHALL implement `--check-verified-evidence` mode. Any `[Verified]` test specification (`TST-XXX`) whose associated testcase reports `failure` or `error` SHALL cause the check to fail with exit code 1 naming the failing TST ID.
  3. *Unbacked TST Gating & Ratchet Baseline (`REQ-PRO-099.3`)*: Any new or modified `TST-XXX` specification lacking passing machine-readable test evidence or an attested manual verification protocol SHALL NOT be marked `[Verified]`. Legacy unbacked TST specifications (405 baseline items) SHALL be maintained in an explicit ratchet baseline (`LEGACY_UNBACKED_VERIFIED_TSTS`), allowing legacy tolerance while failing on any new unbacked `[Verified]` specification.
  4. *Stage 5 Evidence-Conditioned Status Promotion (`REQ-PRO-099.4`)*: `auto_verify_living_docs_on_stage5()` in `tools/jira_util.py` (and Höchstmaß mirror) SHALL NOT flip `TST-XXX` or `REQ-XXX` headings or rows to `[Verified]` unless verified test execution evidence exists. When test results are absent, incomplete, or failing, items SHALL remain `[Implemented]`.
  5. *Default Check Decoupling & Clean Worktree Safety (`REQ-PRO-099.5`)*: Default governance invocations (`verify_requirement_governance.py` without `--check-verified-evidence`, `--check-gates`, `--check-reviews`, and git hooks) SHALL remain functional in clean worktrees before Gradle build outputs exist, reserving strict XML result verification to `--check-verified-evidence` and Stage 5 verification gating.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-099`. Targets `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, and `.agents/skills/stage5-verification/SKILL.md`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-46.8 via ticket `ATT-8143` under Epic `ATT-8138` (ASPICE 4.0 Audit Remediation). Resolves finding M1.
  3. *Root Reason for Existing Formulation*: Prior workflow automatically flipped living doc status to `[Verified]` upon `submit-stage 5` regardless of whether tests passed or even existed.
  4. *Preservation of Core Invariants*: Retains living doc synchronization but grounds `[Verified]` in authentic test execution results; ratchet baseline preserves CI across legacy backlog without breaking builds; zero changes to Android runtime code; Rule 3 Human Gate intact.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a test specification `TST-XXX` marked `[Verified]` whose tagged test fails in JUnit XML,
  • *When* `verify_requirement_governance.py --check-verified-evidence` runs,
  • *Then* the check fails with exit code 1 and identifies the failing TST ID.
  • *Given* a net-new `TST-XXX` lacking tagged passing test execution evidence,
  • *When* Stage 5 is submitted via `submit-stage <KEY> 5`,
  • *Then* the specification remains `[Implemented]` and missing evidence is reported.
  • *Given* the 405 legacy unbacked TST specifications,
  • *When* `verify_requirement_governance.py --check-verified-evidence` runs,
  • *Then* legacy unbacked items within the ratchet baseline do not trigger failure.
- **Invariants**: Zero changes to production runtime logic (`app/src/main/`); existing master table parity intact; pure Python standard library retained.

### REQ-PRO-100: Versioned Sprint and Release Test & Coverage Evidence Archival [Verified]
- **Tests**: `TST-PRO-100`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/generate_test_evidence.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/generate_test_evidence.py`, `tools/hoechstmass/tools/jira_util.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassToolingContractTest.kt`, `docs/management/cm_plan.md`, `.agents/rules/aspice_governance.md`
- **Rationale**: Under ASPICE 4.0 Major Finding M7 (SWE.4 BP5, SWE.5 BP7, SWE.6 BP5, GP 2.2.3), test execution results must be baseline-controlled, immutable, and versioned in git rather than pointing to ephemeral local build directories via a symlink (`docs/engineering/test_results`). Furthermore, while Kover enforces coverage floors in Gradle, measured coverage percentages (lines and branches) across project and critical packages must be committed and baselined alongside test totals, failures, and per-TST pass/fail results.
- **Description**:
  1. *Canonical Test Evidence Store & Layout (`REQ-PRO-100.1`)*:
     • The repository SHALL establish `docs/engineering/test_evidence/` as a canonical, version-controlled directory (replacing the volatile `docs/engineering/test_results` symlink per Rule 8.4 and `cm_plan.md`).
     • Every sprint completion / merge into develop and every tagged release SHALL persist an immutable evidence markdown report (`docs/engineering/test_evidence/<sprint|release>.md`) and machine-readable companion dataset (`docs/engineering/test_evidence/<sprint|release>.json`).
  2. *JUnit Test Metrics & Per-TST Verification Summary (`REQ-PRO-100.2`)*:
     • `tools/generate_test_evidence.py` SHALL parse machine-readable JUnit XML test results (`app/build/test-results/testDebugUnitTest/` or test archive) and correlate tests to `TST-XXX` IDs.
     • Persisted evidence records SHALL document git commit SHA, timestamp, total tests, passed, failures, errors, skipped, execution time, and individual pass/fail records mapped to each evaluated `TST-XXX` specification.
  3. *Kover Code Coverage Ingestion & Package Breakdown (`REQ-PRO-100.3`)*:
     • The test evidence generator SHALL parse the Kover XML report (`app/build/reports/kover/reportDebug.xml`) and record exact measured line and branch coverage percentages for:
       - Global project codebase.
       - Critical Tracking subsystem (`com.atrainingtracker.trainingtracker.tracking`).
       - Critical Filters subsystem (`com.atrainingtracker.trainingtracker.filters`).
       - Critical Sensors subsystem (`com.atrainingtracker.trainingtracker.sensors`).
  4. *Tooling Automation, Sibling-Scoped Import & Mirror Parity (`REQ-PRO-100.4` [Verified (ATT-8875)])*:
     • `tools/jira_util.py export-test-evidence [--sprint <ID>] [--release <TAG>] [--out-dir <DIR>] [--no-kover]` (and its Höchstmaß mirror `tools/hoechstmass/tools/jira_util.py`) SHALL automate generating and committing evidence records during Ceremony 2 (Sprint Review) and release pipelines.
     • Both `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` `export_test_evidence()` SHALL resolve `generate_test_evidence` strictly from their own sibling directory (`os.path.dirname(os.path.abspath(__file__))`, verifying any cached `sys.modules["generate_test_evidence"]` path matches before use and loading via `importlib.util` if mismatched), preventing cross-directory `sys.modules` cache collisions.
     • `tools/hoechstmass/tools/generate_test_evidence.py` and `tools/hoechstmass/tools/jira_util.py` SHALL support `skip_kover: bool = False` / `--no-kover` in parity with root `tools/` (`aec078bc`) while preserving `HoechstmassToolingContractTest.kt` project-agnostic invariants.
     • `tools/verify_requirement_governance.py --check-test-evidence` SHALL verify that evidence records exist, are non-empty, and match the active sprint/release without missing data or unresolved test failures.
     • `ReleaseCiAndLintConfigContractTest.kt` SHALL assert that `docs/engineering/test_results` symlink is retired and `docs/engineering/test_evidence/` is present and tracked.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PRO-100` (`REQ-PRO-100.4`). Targets `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/generate_test_evidence.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
  2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.8 via `ATT-8144` (`6bd56da5`) under Epic `ATT-8138`; `aec078bc` added `skip_kover` to root `tools/` without updating `tools/hoechstmass/tools/` or guarding `sys.modules["generate_test_evidence"]`; remediated in `ATT-8875`.
  3. *Root Reason for Existing Formulation*: Bare `from generate_test_evidence import ...` supported both direct CLI (`python3 tools/jira_util.py`) and package imports, but shared an un-namespaced `sys.modules` key between `tools/` and `tools/hoechstmass/tools/`.
  4. *Preservation of Core Invariants*: Preserves CLI and package import modes, `HoechstmassToolingContractTest.kt` project-agnostic rules, `ReleaseCiAndLintConfigContractTest.kt`, zero `app/src/main/` edits, and Rule 3 Human Gate.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `sys.modules["generate_test_evidence"]` pre-populated by `tools/hoechstmass/tools/generate_test_evidence.py` (or a module lacking `skip_kover`),
  • *When* `tools/jira_util.py export_test_evidence(...)` executes in `tools/test_jira_accounts.py`,
  • *Then* it resolves sibling `tools/generate_test_evidence.py` cleanly without raising `TypeError` on `skip_kover`.
  • *Given* `tools/hoechstmass/tools/jira_util.py export_test_evidence(..., skip_kover=True)` and `tools/hoechstmass/tools/generate_test_evidence.py --no-kover`,
  • *When* executed in isolation or via `HoechstmassToolingContractTest`,
  • *Then* `coverage_summary["measured"]` is `False`, all Höchstmaß tests pass, and zero ATT-proprietary constants appear in `tools/hoechstmass/tools/`.
  • *Given* a sprint completion or release candidate build,
  • *When* `tools/generate_test_evidence.py` or `tools/jira_util.py export-test-evidence` is executed,
  • *Then* `docs/engineering/test_evidence/<target>.md` and `.json` are generated and `verify_requirement_governance.py --check-test-evidence` passes.
- **Invariants**: Zero Android runtime bytecode changes; `HoechstmassToolingContractTest.kt` project-agnostic invariants preserved; `Localization Parity: N/A - no UI strings`.

---

### REQ-PRO-101: Unbacked Status Triage, Test-Tag Reconciliation & Ratchet Reduction (ATT-8145) [Verified]
- **Tests**: `TST-PRO-101`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`, `docs/requirements/*.md`, `docs/tests/*.md`
- **Rationale**: ASPICE 4.0 Major Finding M1 (`ATT-8145`) identified `369` active unbacked `[Verified]` TST specifications (out of the `405`-item `LEGACY_UNBACKED_VERIFIED_TSTS` ratchet baseline from `ATT-8143`), `332` `[Verified]` REQ headings citing only unbacked TSTs, and `14` `[Verified]` baselined orphans in `tools/traceability_baseline.json`. Extending `REQ-PRO-099`, legacy `[Verified]` claims must be triaged and reconciled so `[Verified]` strictly reflects executable test tags or attested verification evidence, backed by a machine-readable triage ledger and monotonic ratchet reduction.
- **Description**:
  1. *Deterministic Unbacked Verification Triage & Machine-Readable Ledger (`REQ-PRO-101.1`)*: `tools/verify_requirement_governance.py` SHALL implement `--triage-unbacked` (with deterministic CSV/report export under canonical `docs/engineering/test_evidence/`, e.g., `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`, preserving `ALLOWED_ENGINEERING_ENTRIES`) to classify all unbacked `[Verified]` TSTs across living-doc shards into `unit-test-backed`, `UI/manual-only`, `obsolete/duplicate`, and `missing test code`, and identify stale already-backed entries in `LEGACY_UNBACKED_VERIFIED_TSTS`.
  2. *Existing Test-Suite Tag Reconciliation (`REQ-PRO-101.2`)*: All `unit-test-backed` TST specifications (`157` items) whose verification logic already exists in `*Test.kt`, `*Test.java`, or `tools/test_*.py` suites SHALL retain `[Verified]` strictly after explicit `TST-*` tags are added to the corresponding test files so `extract_test_code_tags()` binds them deterministically.
  3. *Truthful Status Downgrade & Domain Follow-Up Allocation (`REQ-PRO-101.3`)*: Remaining unbacked `[Verified]` TSTs (`UI/manual-only` including `TST-MIG-006`, `obsolete/duplicate`, `missing test code`), the `14` `[Verified]` baselined orphans in `tools/traceability_baseline.json`, and `[Verified]` REQs whose cited TSTs are unbacked SHALL be downgraded to `[Implemented]` or `[Planned]` using strictly `AspiceSweAuditContractTest`-valid heading suffixes (`[Verified]`, `[Implemented]`, `[Planned]`, `[Open]`). Per-domain follow-up Jira backlog tickets (`1-3 SP` each) SHALL be logged for genuinely missing automated or human-attested verification.
  4. *Monotonic Ratchet Reduction & Master Index Parity (`REQ-PRO-101.4`)*: `LEGACY_UNBACKED_VERIFIED_TSTS` in `tools/verify_requirement_governance.py` SHALL be reconciled downward to `0` (or a strictly documented, ticket-referenced residual cap), `docs/requirements.md` and `docs/tests.md` summary tables SHALL be synchronized via `--sync-indexes`, and `python3 tools/verify_requirement_governance.py` + `--check-verified-evidence` SHALL exit `0`.
  5. *Höchstmaß Mirror Parity & Governance Test Maintenance (`REQ-PRO-101.5`)*: `tools/hoechstmass/tools/verify_requirement_governance.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py` SHALL maintain parameterized mirror parity (`HoechstmassToolingContractTest` passing without hardcoded `ATT` project/user IDs), and `tools/test_verify_requirement_governance.py` (`test_scenario_60`, `test_scenario_61`) SHALL be updated cleanly using parameterized `baseline_allowlist` / reconciled fixtures.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-PRO-099.3` / `TST-PRO-099.3` (`LEGACY_UNBACKED_VERIFIED_TSTS`, `405` items) via net-new `REQ-PRO-101` / `TST-PRO-101`. Targets `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/*.md`, `docs/tests/*.md`, and `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`.
  2. *Historical Origin & Commit Trace*: `ATT-8143` (`f9d9b9e5`, Sprint `2026-46.8`) introduced `LEGACY_UNBACKED_VERIFIED_TSTS` (`405` IDs) as a ratchet baseline; `ATT-8145` (Sprint `2026-47.1`) executes M1 triage remediation.
  3. *Root Reason for Existing Formulation*: Prevented legacy unbacked `[Verified]` claims left by unconditional Stage 5 auto-verification from breaking CI while blocking novel unbacked `[Verified]` items.
  4. *Preservation of Core Invariants*: Preserves monotonic non-increasing ratchet (`|B_{t+1}| <= |B_t|`), standard heading suffixes `[Verified|Implemented|Planned|Open]`, master index parity (`--sync-indexes`), clean-worktree safety (`REQ-PRO-099.5`), zero `app/src/main/` changes, and Human Decision Gate.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* the living-doc shards and test suites,
  • *When* `python3 tools/verify_requirement_governance.py --triage-unbacked` runs,
  • *Then* every unbacked `[Verified]` TST is deterministically classified and exported to `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv` without violating `ALLOWED_ENGINEERING_ENTRIES`.
  • *Given* reconciled `TST-*` tags on `unit-test-backed` suites and downgraded `UI/manual-only`, `obsolete/duplicate`, `missing test code`, and `14` orphan items,
  • *When* `python3 tools/verify_requirement_governance.py --sync-indexes` and `--check-verified-evidence` execute,
  • *Then* `LEGACY_UNBACKED_VERIFIED_TSTS` is reduced monotonically (zero unbacked `[Verified]` items outside any documented residual cap), all headings end with valid status tokens, and governance checks exit `0`.
  • *Given* `tools/test_verify_requirement_governance.py` (`test_scenario_60`, `test_scenario_61`), `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `HoechstmassToolingContractTest`, and `AspiceSweAuditContractTest`,
  • *When* executed,
  • *Then* all unit and contract tests pass with exit code `0`.
- **Invariants**: Zero `app/src/main/` runtime code changes; `ANT-Android-SDKs` untouched at `160000`; `ALLOWED_ENGINEERING_ENTRIES` canonical integrity preserved; standard heading suffixes `[Verified|Implemented|Planned|Open]` strictly enforced.

---

### REQ-PRO-102: Boundary-Safe SYSREQ Traceability & Bidirectional System-Refinement Coverage Reporting (ATT-8146) [Verified]
- **Tests**: `TST-PRO-102`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Rationale**: ASPICE 4.0 Major Finding M2 (`ATT-8146`) identified that `verify_system_software_traceability()` in `tools/verify_requirement_governance.py` matched `"REQ-" in line` and `re.findall(r"REQ-[A-Z0-9_-]+", line)` inside `SYSREQ-DEX-*` / `SYSREQ-TRK-*`, emitting bogus `REQ-DEX-001..006` IDs, misclassified `REQ-PRO-*` process specs as system refinements, and omitted reverse (`SWE -> SYSREQ`) coverage (`49/355` = `13.8%`). Extending `REQ-PRO-076`, boundary-safe matching and bidirectional reporting are required for truthful SWE.1 BP5 / SYS.2 BP5 traceability metrics.
- **Description**:
  1. *Word/Prefix Boundary Enforcement (`REQ-PRO-102.1`)*: `verify_system_software_traceability()` (`L3710-3716`) and secondary `REQ-*` scanners (`_collect_test_file_index`, `triage_unbacked_verified_tsts`) in `tools/verify_requirement_governance.py` SHALL enforce word/prefix boundary regex `(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])` (replacing `"REQ-" in line` substring checks) so `SYSREQ-DEX-001` or `SYSREQ-TRK-001` never false-match as `REQ-DEX-001` or `REQ-TRK-001`.
  2. *Strict `SYSTEM_REFINED_PREFIXES` Filtering (`REQ-PRO-102.2`)*: `verify_system_software_traceability()` SHALL filter `sys_to_swe` refinement sets strictly to requirements whose prefix belongs to `SYSTEM_REFINED_PREFIXES`, excluding `REQ-PRO-*` and all other `SWE_DERIVED_PREFIXES`.
  3. *Bidirectional Forward & Reverse Coverage Reporting (`REQ-PRO-102.3`, [Verified] ATT-8437)*: `verify_system_software_traceability()` and `--report-sys-coverage` SHALL populate `report` (`swe_refinement_total_count`, `swe_refinement_covered_count`, `swe_refinement_coverage_pct`, `swe_to_sys_map`) and print BOTH forward (`SYSREQ -> SWE`, `12/12` = `100.0%`) and authentic reverse `System-Refinement` (`SWE -> SYSREQ`) coverage without breaking `--verify-sys-trace` CI pass. In live repository regression testing (`test_scenario_68`), assertions SHALL evaluate structural invariants rather than brittle point-in-time snapshot equality counts: exact forward coverage parity (`covered_count == sysreq_count == 12`), lower-bound floors (`swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`), and zero pollution from `REQ-DEX-*` or `REQ-PRO-*`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-PRO-076.4` / `TST-PRO-076` (`tools/verify_requirement_governance.py`). Targets `verify_system_software_traceability()`, `_collect_test_file_index()`, `triage_unbacked_verified_tsts()`, and `--report-sys-coverage`.
  2. *Historical Origin & Commit Trace*: `REQ-PRO-076` introduced in `ATT-6163` (Sprint `2026-46.4`); amended in `ATT-8146` (Sprint `2026-47.1`, Epic `ATT-8138`) to remediate Finding M2.
  3. *Root Reason for Existing Formulation*: Enforced `12/12` forward `SYSREQ` coverage and zero dangling `SYSREQ`s across `docs/system/` and `docs/requirements/`.
  4. *Preservation of Core Invariants*: Preserves `--verify-sys-trace` CI gate (`12/12` forward `SYSREQ`s covered, zero dangling `SYSREQ`s), preserves `report` keys (`sysreq_count`, `covered_count`, `coverage_pct`, `sys_to_swe_map`) for `test_scenario_40..43`, keeps pure Python stdlib, and makes zero `app/src/main/` or `docs/system/*.md` changes.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a document containing only `SYSREQ-DEX-001` (with no standalone `REQ-DEX-001`),
  • *When* `verify_system_software_traceability()` parses it,
  • *Then* `REQ-DEX-001` is never extracted or added to `sys_to_swe`.
  • *Given* requirements in `SWE_DERIVED_PREFIXES` (e.g., `REQ-PRO-054`, `REQ-PRO-076`) mentioning `SYSREQ-*`,
  • *When* `verify_system_software_traceability()` computes system refinements,
  • *Then* `REQ-PRO-*` IDs are excluded from `sys_to_swe` and `swe_refinement_total_count`.
  • *Given* the live repository,
  • *When* `--report-sys-coverage` and `test_scenario_68` execute,
  • *Then* it reports `12/12` (`100.0%`) forward `SYSREQ -> SWE` coverage, asserts structural invariants (`covered_count == sysreq_count == 12`, `swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`), and `--verify-sys-trace` exits `0`.
- **Invariants**: Zero `app/src/main/` or `docs/system/*.md` edits; `ANT-Android-SDKs` untouched at `160000`; `test_scenario_40..43` compatibility preserved.

---

### REQ-PRO-103: Complete Bidirectional SYS-to-SWE Traceability & System-Refinement Classification (ATT-8147) [Verified]
- **Tests**: `TST-PRO-103`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/req_tracking_cockpit.md`, `docs/requirements/req_data_migration_export.md`, `docs/requirements/req_maps_navigation.md`, `docs/requirements/req_aftermath_periods.md`, `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system/system_software_traceability.md`
- **Rationale**: ASPICE 4.0 Major Finding M2 (`ATT-8147`) identified that `306` of `355` `SYSTEM_REFINED_PREFIXES` REQs lack a structured link to a parent `SYSREQ-*` or an explicit `Derived (SW-only)` classification with rationale, and `7` `RAD`/`CLM` REQs lack prefix classification in `SYSTEM_REFINED_PREFIXES` vs `SWE_DERIVED_PREFIXES`. Completing bidirectional traceability enables full SYS.2/SYS.3 to SWE.1 impact analysis.
- **Description**:
  1. *Structured `System Requirement(s)` Attribute & Prefix Closure (`REQ-PRO-103.1`)*: `verify_system_software_traceability()` in `tools/verify_requirement_governance.py` SHALL parse `- **System Requirement(s)**:` (accepting one or more declared `SYSREQ-*` IDs or `Derived (SW-only)` with documented rationale) across requirement shards, and SHALL classify the `7` `RAD` (`3`) and `CLM` (`4`) REQs cleanly in `SYSTEM_REFINED_PREFIXES` / `docs/system/system_software_traceability.md` §3.1 while keeping `REQ-PRO-*` in `SWE_DERIVED_PREFIXES`.
  2. *100% System-Refinement Coverage & Structural Invariant Verification (`REQ-PRO-103.2`, [Verified] ATT-8437)*: All `SYSTEM_REFINED_PREFIXES` software requirements SHALL define `- **System Requirement(s)**:` (`SYSREQ-*` or `Derived (SW-only)`), achieving `100.0%` forward (`12/12` `SYSREQ -> SWE`) and `100.0%` reverse (`SWE -> SYSREQ / Derived (SW-only)`) classification, with `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system/system_software_traceability.md` synchronized cleanly without violating `System*RequirementsContractTest` or `verify_architecture_and_system_specs()` (`declared == allocated` for the `12` `SYSREQ-*` IDs). In live repository testing (`test_scenario_70`), assertions SHALL enforce structural invariants rather than brittle point-in-time snapshot equality counts: `reverse_classified_count == swe_refinement_total_count`, `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`, `unclassified_swe_refinement_reqs == []`, `swe_refinement_total_count >= 362`, and `reverse_classified_pct == 100.0`.
  3. *Reverse Trace Enforcement CLI & Ratchet Gate (`REQ-PRO-103.3`)*: `tools/verify_requirement_governance.py` SHALL expose `--check-reverse-sys-trace` (with `fail_on_unclassified_swe` and `LEGACY_UNLINKED_SYS_REFINEMENT_REQS = frozenset()` ratchet) and extend `--report-sys-coverage` to report `SYSREQ`-linked, `Derived (SW-only)`, and unclassified counts/percentages without breaking `test_scenario_41` or `test_scenario_67` synthetic fixtures.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-PRO-076` (`ATT-6163`) and `REQ-PRO-102` (`ATT-8146`) via net-new `REQ-PRO-103`. Targets `tools/verify_requirement_governance.py`, `req_tracking_cockpit.md`, `req_data_migration_export.md`, `req_maps_navigation.md`, `req_aftermath_periods.md`, `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system/system_software_traceability.md`.
  2. *Historical Origin & Commit Trace*: `ATT-6163` established `REQ-PRO-076` (`12/12` forward gate); `ATT-8146` (`5a263429`) exposed the `49/355` (`13.8%`) reverse gap; `ATT-8147` (Sprint `2026-47.1`, Epic `ATT-8138`) completes bidirectional classification; `ATT-8437` (Sprint `2026-11-16_03_Nacht`) replaces brittle snapshot equality counts with relational invariants in `test_scenario_68/70`.
  3. *Root Reason for Existing Formulation*: Forward-only `12/12` check left `306` product requirements untraced to `SYSREQ-*` or `Derived (SW-only)` rationale; initial test suites asserted exact snapshot counts (`== 363`) that broke when new refinement requirements were added.
  4. *Preservation of Core Invariants*: Preserves `12` declared `SYSREQ-*` IDs (`declared == allocated`), `REQ-PRO-102.2` exclusion of `REQ-PRO-*` (`SWE_DERIVED_PREFIXES`), `test_scenario_40..43` & `66..68` compatibility, `System*ContractTest` section/filename invariants, zero `app/src/main/` changes, and `ANT-Android-SDKs@160000`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* every requirement in `SYSTEM_REFINED_PREFIXES` (including `RAD` and `CLM`),
  • *When* `verify_requirement_governance.py --check-reverse-sys-trace` and `test_scenario_70` execute,
  • *Then* `100.0%` carry either a valid `SYSREQ-*` link or explicit `Derived (SW-only)` rationale (`0` unclassified, `reverse_classified_count == swe_refinement_total_count`, `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`, `unclassified_swe_refinement_reqs == []`) and exit code is `0`.
  • *Given* `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system/system_software_traceability.md`,
  • *When* `--verify-sys-trace --report-sys-coverage` and `System*ContractTest` run,
  • *Then* all `12/12` forward `SYSREQ`s list their refining `REQ-*` IDs, reverse classification is `100.0%`, and all contract checks pass.
  • *Given* existing synthetic tests `test_scenario_41` and `test_scenario_67`,
  • *When* `tools/test_verify_requirement_governance.py` runs,
  • *Then* all existing and new `TST-PRO-103.*` tests pass with exit code `0`.
- **Invariants**: Zero `app/src/main/` production changes; `ANT-Android-SDKs` untouched at `160000`; `12` declared `SYSREQ-*` IDs unchanged; `REQ-PRO-*` remains `SWE-Derived`.

---

### REQ-PRO-104: Code-to-Doc Orphan Guard, Shard Header Count Sync & REQ Description Governance (ATT-8148) [Verified]
- **Tests**: `TST-PRO-104`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`
- **Rationale**: ASPICE 4.0 Minor Finding (SWE.1 BP1/BP5, GP 2.2.4) identified three documentation drift vectors: code citations lacking requirement/test shard entries, stale per-shard header counts, and missing description fields in legacy requirements. Eliminating code-level orphans and automating shard header parity guarantees full bidirectional traceability between codebase implementation and living documentation.
- **Description**:
  1. *Code-to-Shard Orphan Detection & Exclusion (`REQ-PRO-104.1`)*: `tools/verify_requirement_governance.py` SHALL implement code-to-shard orphan verification (`verify_code_traceability_orphans()`) scanning `app/src/main/`, `app/src/test/`, `app/src/androidTest/`, and `tools/` for `REQ-*` and `TST-*` citations, failing verification if any cited ID is absent from all 18 living documentation shards (`docs/requirements/` and `docs/tests/`), resolving verified typos (`REQ-UI-2780` -> `REQ-UI-314`, `REQ-EXP-041` -> `REQ-MIG-053`), and restoring authentic missing specifications (`REQ-WKO-035..039`, `REQ-HRV-038..042`, `REQ-SPT-001/003/004`, `REQ-UI-292` and matching `TST-*` IDs).
  2. *Living Doc Shard Header Synchronization (`REQ-PRO-104.2`)*: `recalculate_master_index_tables()` (invoked via `--sync-indexes` and `recalc-docs`) SHALL automatically synchronize the `**Total Requirements**: N` and `**Total Tests**: N` header counts across all 18 domain shards in `docs/requirements/` and `docs/tests/` with exact parsed heading counts, and `verify_requirement_governance.py` SHALL fail CI if any shard header count diverges from its actual section count.
  3. *Description Field Ratchet & Field Coverage Reporting (`REQ-PRO-104.3`)*: `tools/verify_requirement_governance.py` SHALL enforce mandatory `- **Description**:` fields on all newly added requirements via `--check-req-description` backed by a frozen legacy baseline ratchet (`LEGACY_MISSING_DESCRIPTION_REQS`), and expose `--report-req-field-coverage` to report field completion metrics (Description, Given-When-Then, Allocation).
  4. *Cross-Check Integrity Safeguards (`REQ-PRO-104.4`)*: Any requirement restored in `SYSTEM_REFINED_PREFIXES` (e.g. `req_aftermath_periods.md`) MUST declare `- **System Requirement(s)**:`, and NO restored test specification may be marked `[Verified]` unless backed by passing test code evidence matching `--check-verified-evidence` and leaving `--check-reverse-sys-trace` with zero unclassified requirements.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-PRO-075` (syntax governance), `REQ-PRO-087` (traceability orphans), and `REQ-PRO-041.4` (index synchronization) via net-new `REQ-PRO-104`. Targets `tools/verify_requirement_governance.py`, living documentation shards, and code comments.
  2. *Historical Origin & Commit Trace*: `ATT-6163` (`REQ-PRO-075`), `ATT-6129` (`REQ-PRO-087`), `ATT-5431` (`REQ-PRO-041.4`), `06990c88` (shard split).
  3. *Root Reason for Existing Formulation*: Shard-to-shard checks verified internal document consistency but left code citations and per-shard header counts unverified against drift.
  4. *Preservation of Core Invariants*: Zero production runtime logic changes; `ANT-Android-SDKs` untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; preserve existing test suite and synthetic test compatibility.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* source files under `app/src/` and `tools/`,
  • *When* `verify_requirement_governance.py` runs code orphan verification,
  • *Then* zero undeclared `REQ-*` or `TST-*` citations exist across the codebase and exit code is `0`.
  • *Given* all 18 living documentation shards in `docs/requirements/` and `docs/tests/`,
  • *When* `recalculate_master_index_tables()` executes,
  • *Then* all 18 shard header counts match the actual parsed heading counts exactly.
  • *Given* requirement definitions across living documentation shards,
  • *When* `--check-req-description` and `--report-req-field-coverage` execute,
  • *Then* `REQ-PRO-104` and all post-baseline requirements declare `- **Description**:`, and coverage metrics are reported cleanly.
  • *Given* restored specifications in `SYSTEM_REFINED_PREFIXES` and `[Verified]` test specifications,
  • *When* `--check-reverse-sys-trace` and `--check-verified-evidence` execute,
  • *Then* zero unclassified SWE requirements exist and zero unbacked `[Verified]` tests are detected.
- **Invariants**: Zero runtime behavior changes in `app/src/main/`; `ANT-Android-SDKs` untouched at `160000`; existing synthetic governance test fixtures continue to pass.

---

### REQ-PRO-105: Explicit PO Approval & Changelog Provenance for Agent-Originated Change Requests (ATT-8166) [Verified]
- **Tests**: `TST-PRO-105`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Rationale**: ASPICE 4.0 Finding SUP.10 (Change Request Management, SUP.10 BP3/BP4) requires explicit, auditable Product Owner authorization for all change requests before work commences. Autonomous sprint injection by AI agents undermines scope control. Tooling must enforce that agent-created tickets remain in raw Backlog, prohibit agents from moving issues from Backlog into `Vom PO Freigegeben` (ID 447) or active sprints, verify human PO changelog provenance in QA process compliance audits, and record formal disposition records for approved historical exceptions.
- **Description**:
  1. *Raw Backlog Enforcement on Agent Issue Creation (`REQ-PRO-105.1`)*: When `create-issue` is invoked by any agent role (`agent1`, `agent2`, `coordinator`), issues SHALL always be created in raw Backlog with `sprint` unset. Any attempt by agents to specify `--add-to-sprint` or directly target an active sprint SHALL be rejected with an error, preserving ceremony exceptions (`ensure-retro`, `ensure-weekly-reviews`).
  2. *Two-Way Barrier on PO Queue (`Vom PO Freigegeben`) (`REQ-PRO-105.2`)*: Moving raw Backlog issues into `Vom PO Freigegeben` (ID 447) or an active sprint by ANY AI agent role (`agent1`, `agent2`, `coordinator`) SHALL be strictly prohibited and hard-rejected with a clear error. ONLY authorized Coordinator ceremonies (`pack-sprint` moving from `447 -> target_sprint_id`, and `return-unstarted` moving from `active_sprint -> 447`) SHALL be permitted.
  3. *Jira Changelog Provenance Verification in QA Audits (`REQ-PRO-105.3`)*: `tools/jira_util.py` SHALL implement `verify_po_approval_changelog()` querying the issue changelog (`/rest/api/2/issue/{key}?expand=changelog`) to confirm human PO `accountId` (`HUMAN_ACCOUNT_ID`) provenance (placement into `Vom PO Freigegeben` or direct creation by human PO) for all regular sprint tickets, integrating Dimension 6 (`po_approval_pass`) into `qa-audit` without failing on legitimate ceremony tickets (`Review & Retro`, `Weekly Dual-Vendor Code Review`).
  4. *Audit Trail & Exception Disposition (`REQ-PRO-105.4`)*: Approved historical change-request exceptions (e.g. `ATT-7902` tests-only core service scaffolding approved in Jira comment on `2026-11-14T20:28:50`) SHALL have formal SUP.10 disposition comments recorded in Jira and living documentation.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-PRO-064` (Change Impact Analysis / SUP.10), `REQ-PRO-091` (QA audit protocol), and `REQ-PRO-097` (Sprint bin-packing) via net-new `REQ-PRO-105`. Targets `tools/jira_util.py` and `tools/test_jira_accounts.py`.
  2. *Historical Origin & Commit Trace*: `ATT-7749` (Sprint 46.6 Retro), `ATT-7902` (`43846f1a`), `ATT-8030` (Three-Tier Board Guard), `ATT-8162` (12h continuous sprint).
  3. *Root Reason for Existing Formulation*: Prior conventions lacked automated changelog verification, allowing unverified backlog items to enter active sprints without documented PO approval.
  4. *Preservation of Core Invariants*: Preserve `return-unstarted` returning untouched sprint tickets to ID 447; preserve `ensure-retro` active sprint placement; keep `ANT-Android-SDKs` untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* an agent attempting to create an issue with `--add-to-sprint`,
  • *When* `create-issue` runs,
  • *Then* direct sprint placement is blocked and the issue is created strictly in raw Backlog.
  • *Given* an issue in raw Backlog,
  • *When* an agent role invokes `move` or `pack-sprint` targeting `Vom PO Freigegeben` (ID 447) or an active sprint,
  • *Then* the operation is hard-rejected with a descriptive governance error.
  • *Given* an active sprint with regular sampled tickets and ceremony tickets,
  • *When* `qa-audit` executes Dimension 6,
  • *Then* regular tickets verify human PO changelog provenance, ceremony tickets pass exemption, and audit reports reflect `po_approval_pass`.
  • *Given* `ATT-7902`,
  • *When* audited under SUP.10,
  • *Then* formal disposition documentation confirms tests-only authorization by the Product Owner.
- **Invariants**: Zero `app/src/main/` production changes; `ANT-Android-SDKs` untouched at `160000`; coordinator ceremony flows (`pack-sprint`, `return-unstarted`, `ensure-retro`) preserved.

---

### REQ-PRO-106: Atomic REQ and TST ID Reservation Across Parallel Sprint Lanes (ATT-8149) [Verified]
- **Tests**: `TST-PRO-106`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Rationale**: ASPICE 4.0 Audit Finding Minor (Jira sample ATT-7758, SWE.1 BP1, SUP.8 BP4) revealed that parallel sprint execution across decoupled git worktrees (`REQ-PRO-041`) introduces specification-time ID collision hazards. Local filesystem scans cannot observe unmerged ID allocations in parallel sibling lanes. To prevent collisions, silent renumbering drift, and audit trail inconsistencies, a POSIX `flock`-protected reservation registry anchored at `git rev-parse --git-common-dir` must coordinate ID allocation atomically across all worktrees, and Gate 2 must verify reservation provenance before approving specifications.
- **Description**:
  1. *Common-Dir Cross-Worktree ID Reservation Registry (`REQ-PRO-106.1`)*: `tools/jira_util.py` SHALL implement atomic ID reservation (`reserve-id <DOMAIN> <REQ|TST|BOTH> <TICKET_KEY> [--count N]`) backed by POSIX `flock` on `/tmp/att_id_reservation.lock`. Reservations SHALL be persisted to a shared JSON ledger located in the common git directory (`$(git rev-parse --git-common-dir)/att_id_reservations.json`), guaranteeing instantaneous cross-worktree visibility across all concurrent `Workspace: "share"` branches.
  2. *Next-ID Calculation with Active Reservation Awareness (`REQ-PRO-106.2`)*: When allocating next available IDs, the tooling SHALL evaluate both committed living documentation headings across `docs/requirements/*.md` and `docs/tests/*.md` as well as active reservations in the common-dir ledger, assigning strictly greater monotonic IDs (`max(committed, reserved) + 1`).
  3. *Gate 2 Reservation Provenance Verification (`REQ-PRO-106.3`)*: In `gate-verdict` on `[Req & Test Spec]` subtasks, `tools/jira_util.py` SHALL parse all newly introduced `REQ-*` and `TST-*` IDs in the deliverable and verify that each ID is registered to the target parent ticket in the shared reservation ledger, rejecting unreserved IDs to prevent collisions and uncoordinated drift.
  4. *Reservation Hygiene & Expiration Management (`REQ-PRO-106.4`)*: The tooling SHALL support explicit release (`release-id <TICKET_KEY>`) upon ticket completion or cancellation, clean reconciliation upon merge into sprint branches, and stale reservation expiration safeguards, while ensuring reservation files never trigger false-positive code orphan detections under `verify_requirement_governance.py --check-code-orphans`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-PRO-041` (Parallel sprint execution / `parallel_sprint_lock.py`) and `REQ-PRO-075` (Syntax governance) via net-new `REQ-PRO-106`. Targets `tools/jira_util.py` and `tools/test_jira_accounts.py`.
  2. *Historical Origin & Commit Trace*: `ATT-6163` (`REQ-PRO-041`), `ATT-6129` (`REQ-PRO-075`), `ATT-7758` / `ATT-8075` (`608c4e31`), `ATT-8149`.
  3. *Root Reason for Existing Formulation*: Prior tooling relied exclusively on local static regex scanning of worktree files, leaving concurrent sprint lanes blind to sibling allocations until Stage 5 merge.
  4. *Preservation of Core Invariants*: Build/merge locks on `/tmp/att_build_merge.lock` remain untouched; existing baselined and legacy requirement numbers remain immutable; `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* concurrent sprint lanes running in disjoint worktrees,
  • *When* `jira_util.py reserve-id` is called simultaneously for the same domain prefix,
  • *Then* POSIX `flock` serializes access, each lane receives unique non-overlapping monotonic IDs, and the shared common-dir ledger is updated atomically.
  • *Given* a Stage 2 deliverable containing newly introduced `REQ-*` and `TST-*` IDs,
  • *When* `gate-verdict` executes for `[Req & Test Spec]`,
  • *Then* the audit verifies that all new IDs are properly reserved for the parent ticket, passing if registered and rejecting with an informative error if unreserved.
  • *Given* a reservation ledger file,
  • *When* `verify_requirement_governance.py --check-code-orphans` executes,
  • *Then* zero false-positive code orphan violations are reported.
- **Invariants**: Zero Android production code impact (`app/src/main/`); `ANT-Android-SDKs` untouched at `160000`; existing synthetic test fixtures and `parallel_sprint_lock.py` functionality preserved.

---

### REQ-PRO-107: Separated Gate Review Authorship, Evidence Checklists & Zero-Revision Alerts (ATT-8150) [Verified]
- **Tests**: `TST-PRO-107`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/jira_util.py`, `tools/sprint_metrics.py`, `tools/test_jira_accounts.py`, `.agents/rules/aspice_governance.md`, `.agents/skills/jira-workflow/SKILL.md`
- **Rationale**: ASPICE 4.0 Finding M3 (SUP.1 Quality Assurance, GP 2.1.4, GP 2.2.4) highlighted systemic gate rubber-stamping caused by fused same-agent transitions (Gate N auditor immediately authoring Stage N+1), unstructured free-form gate comments, and multi-sprint 100% FTPR without audit warnings. Establishing independent review authorship, mandatory evidence-backed checklists in gate verdicts, and consecutive zero-revision retro alarms guarantees authentic adversarial evaluation across quality gates.
- **Description**:
  1. *Separated Gate N to Stage N+1 Authorship (`REQ-PRO-107.1`)*: `tools/jira_util.py submit-stage` SHALL enforce author-reviewer role separation by rejecting the submission of Stage N+1 if the calling actor/role matches the reviewer who recorded Gate N on that same ticket. Subagent topologies SHALL alternate `agent1` (Author) and `agent2` (Reviewer) turns without fused same-subagent transitions, while preserving backwards compatibility with already-closed prior tickets.
  2. *Mandatory Evidence-Backed Gate 1–5 Checklists (`REQ-PRO-107.2`)*: `tools/jira_util.py gate-verdict` on Gates 1–5 SHALL validate that the verdict submission contains structured checklist items (`[x]`, `PASS:`, or `✓`) and at least one concrete, verifiable evidence reference (`file:line`, commit SHA `[0-9a-f]{7,40}`, or test/command output indicator like `BUILD SUCCESSFUL`, `testDebugUnitTest`, or `OK`), rejecting superficial or ungrounded verdicts. Suspiciously rapid reviews (< 5 seconds elapsed between subtask start and verdict) SHALL be explicitly flagged in audit logging.
  3. *Multi-Sprint Rubber-Stamping Retro Alarm (`REQ-PRO-107.3`)*: `tools/sprint_metrics.py` SHALL evaluate multi-sprint Gate 1–5 First-Time Pass Rates in `process_metrics.jsonl`, automatically generating an ASPICE Finding M3 Rubber-Stamping Alert and posting it to the active sprint's Retro ticket whenever two or more consecutive sprints exhibit 100.0% Gate 1–5 FTPR with zero recorded revisions.
  4. *Governance & Protocol Harmonization (`REQ-PRO-107.4`)*: Rules `7.1` and `7.4` in `.agents/rules/aspice_governance.md` and `.agents/skills/jira-workflow/SKILL.md` SHALL be formally updated to deprecate same-agent fused handoffs, codifying persistent alternating pair reviews with independent Gate N to Stage N+1 transitions.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Modifies `REQ-PRO-060` (Composite stage CLI / fused handoffs) and extends `REQ-PRO-092` (Sprint metrics & plausibility warnings) via net-new `REQ-PRO-107`. Targets `tools/jira_util.py`, `tools/sprint_metrics.py`, and project governance rules.
  2. *Historical Origin & Commit Trace*: `ATT-4290` (`REQ-PRO-060`), `ATT-6163` (`REQ-PRO-092`), `ATT-8149` (`REQ-PRO-106`), `ATT-8150`.
  3. *Root Reason for Existing Formulation*: Fused transitions were introduced in ATT-4290 to reduce subagent invocation latency, but created an inherent conflict of interest that eliminated Gate 1–5 review friction.
  4. *Preservation of Core Invariants*: Single composite CLI call per stage submission (`submit-stage`) and gate verdict (`gate-verdict`) is preserved; subtasks transition directly to `Erledigt` on PASS; `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* an agent attempting to submit Stage N+1 on a ticket,
  • *When* the caller actor matches the reviewer who recorded Gate N on that ticket,
  • *Then* `submit-stage` rejects the operation with an ASPICE SUP.1 separated authorship violation error.
  • *Given* a Gate 1–5 audit submission,
  • *When* `gate-verdict` executes,
  • *Then* it verifies structured checklist items and concrete evidence references (file:line, commit SHA, or command output hash), failing if missing or superficial.
  • *Given* two or more consecutive sprints in `process_metrics.jsonl` with 100.0% Gate 1–5 FTPR,
  • *When* `sprint-metrics` runs,
  • *Then* a prominent Rubber-Stamping Alert is emitted and posted to the sprint Retro ticket.
- **Invariants**: Zero Android runtime impact (`app/src/main/`); `ANT-Android-SDKs` untouched at `160000`; Rule 3 human decision gate preserved.

---

### REQ-PRO-108: Technical Role Separation for Agent Credentials & Git Commit Identity (ATT-8151, ATT-8876) [Verified]
- **Tests**: `TST-PRO-108`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, `tools/hoechstmass/tools/parallel_sprint_lock.py`, `tools/locked_build_merge.py`, `tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `docs/management/risk_register.md`
- **Rationale**: ASPICE 4.0 Finding M4 (SUP.1 Quality Assurance, GP 2.1.4, SUP.8 Configuration Management, BP4) identified that role separation between implementer and reviewer existed only at the prompt level. Any agent could assume any role via `--as`, `qa` had no credential mapping, and all commits were authored under a single human identity without agent or model provenance. Binding role selection to subagent identity, mapping dedicated QA credentials, appending verifiable commit trailers across both root and Höchstmaß mirrors, isolating cross-directory module imports, and documenting residual single-OS user risks establishes technical and auditable role independence.
- **Description**:
  1. *Subagent Identity & Role Binding (`REQ-PRO-108.1`)*: When the `JIRA_ACTOR` environment variable is defined in the subagent environment, `tools/jira_util.py` SHALL strictly reject any mismatched CLI `--as <role>` override with a role mismatch error. When `JIRA_ACTOR` is unset (manual developer CLI or coordinator invocation), `--as coordinator` and manual `--as <role>` calls SHALL continue to operate without rejection.
  2. *Dedicated QA Role Credential Mapping & Account Independence (`REQ-PRO-108.2`)*: `tools/jira_util.py` SHALL map role `qa` to dedicated credentials (`JIRA_QA_USER`, `JIRA_QA_TOKEN`), support `.env.jira.qa`, include `qa` in `get_config` role credential filtering, and enforce that `agent2` and `qa` operate under distinct Jira user accounts (different account IDs or usernames).
  3. *Producing Agent Identity & Reviewer Git Commit Trailers + Cross-Directory Isolation (`REQ-PRO-108.3`)*: Both `tools/parallel_sprint_lock.py` and its project-agnostic Höchstmaß mirror `tools/hoechstmass/tools/parallel_sprint_lock.py` SHALL implement `format_commit_with_trailers()`, `validate_commit_trailers()`, and `_merge_ticket_impl()` / `run_merge_ticket()` / CLI `--agent-role`, `--agent-model`, `--reviewed-by` trailer options using generic defaults (`"agent1"`, `"flash"`, `"Agent 2 (Reviewer)"`) so atomic squash merge commits append `Agent-Role:`, `Agent-Model:`, and `Reviewed-by:` without breaking `HoechstmassToolingContractTest`. Root test callers (`tools/test_jira_accounts.py` and `tools/test_flock_serialization.py`) MUST resolve sibling `tools/parallel_sprint_lock.py` deterministically even when `sys.modules["parallel_sprint_lock"]` or `sys.path` is pre-populated by `tools/hoechstmass/tools/`.
  4. *Single-OS User Residual Risk Documentation (`REQ-PRO-108.4`)*: `docs/management/risk_register.md` SHALL formally register the residual risk of local subagent process co-location under a single operating system user account and document the layered mitigations (token isolation, role binding, commit trailers, CI gating).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Extends `REQ-PRO-060` (Composite CLI role operations) and `REQ-PRO-041` (Parallel sprint lock & atomic commit merging) via `REQ-PRO-108` (`REQ-PRO-108.3`). Targets `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, `tools/hoechstmass/tools/parallel_sprint_lock.py`, `tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, and `docs/management/risk_register.md`.
  2. *Historical Origin & Commit Trace*: `ATT-4290` (`REQ-PRO-060`), `ATT-7025` (`REQ-PRO-041`), `ATT-8150` (`REQ-PRO-107`), `ATT-8151` (`f0dfe76d`), `ATT-8207` (`3b390f63`); cross-directory `sys.modules` shadowing pattern matches `ATT-8875` (`bc1a50c7`), refined under `ATT-8876`.
  3. *Root Reason for Existing Formulation*: `ATT-8151` added trailer helpers only to root `tools/parallel_sprint_lock.py`, and root tests relied on bare `import parallel_sprint_lock` assuming unshadowed `sys.path` / `sys.modules`.
  4. *Preservation of Core Invariants*: Standalone manual CLI usage when `JIRA_ACTOR` is unset remains functional; `HoechstmassToolingContractTest.kt` project-agnostic checks (zero `ATT-`/proprietary IDs, stdlib-only), POSIX flock locking, and `ANT-Android-SDKs` submodule (`160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`) are preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a subagent process launched with `JIRA_ACTOR=agent1`,
  • *When* it calls `tools/jira_util.py --as agent2 gate-verdict ...`,
  • *Then* the invocation is rejected with a role mismatch error.
  • *Given* an execution environment without `JIRA_ACTOR`,
  • *When* `tools/jira_util.py --as coordinator show ATT-8151` is executed,
  • *Then* the command succeeds without role mismatch rejection.
  • *Given* configured `agent2` and `qa` credentials,
  • *When* `tools/jira_util.py` resolves accounts,
  • *Then* `qa` resolves to dedicated credentials and accounts are verified distinct.
  • *Given* `sys.modules["parallel_sprint_lock"]` pre-populated by an unrelated directory or `tools/hoechstmass/tools` on `sys.path[0]`,
  • *When* `test_tst_pro_108_3_git_commit_trailers_formatting` (and `test_flock_serialization.py`) executes,
  • *Then* it resolves sibling `tools/parallel_sprint_lock.py` cleanly without raising `AttributeError`.
  • *Given* `tools/hoechstmass/tools/parallel_sprint_lock.py`,
  • *When* `format_commit_with_trailers`, `validate_commit_trailers`, or `--merge-ticket` is executed,
  • *Then* valid `Agent-Role:`, `Agent-Model:`, and `Reviewed-by:` trailers are appended and validated while `HoechstmassToolingContractTest` passes with zero proprietary strings.
- **Invariants**: Zero Android production code impact (`app/src/main/`); `ANT-Android-SDKs` untouched at `160000`; `HoechstmassToolingContractTest.kt` invariants preserved; existing test fixtures and POSIX flock semantics preserved; `Localization Parity: N/A - no UI strings`.

---

### REQ-PRO-109: Stage Transition Precondition Enforcement, Impl-Plan Gate Validation & Bypass Prevention (ATT-8154) [Verified]
- **Tests**: `TST-PRO-109`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Rationale**: ASPICE 4.0 Audit Finding `Observation (SUP)` (SUP.1 BP3, GP 2.1.4) identified four critical gate bypass paths in tooling: `submit_stage` auto-advanced parent tickets through multiple stages at once without gate approvals, `final_review_verdict` moved parents past Gate 5 from any prior status, `check_gate` accepted arbitrary non-Impl-Plan keys as long as status was `Erledigt`, and the Stage 6 Pros/Cons check was a naive substring search susceptible to keyword stuffing. Enforcing strict predecessor status preconditions, validating `[Impl-Plan]` subtasks, and demanding structured review ceremony sections guarantees that quality gates cannot be skipped programmatically.
- **Description**:
  1. *Strict Predecessor Stage Validation in `submit-stage` (`REQ-PRO-109.1`, [Verified])*: `tools/jira_util.py submit-stage` SHALL enforce that the parent ticket is in the exact expected predecessor stage before creating or advancing a stage sub-task. For Stage 1 (`spec["parent_stage"] == "analysis"`), the parent SHALL be in `zu erledigen` (advancing single-step to `analysis` per Rule 78) or already in `analysis`; for Stages 2 through 5, `submit-stage` SHALL strictly require that `current_parent_status == desired_parent_stage` (`test spec`, `implementation plan`, `implementation`, `test` respectively), rejecting any multi-stage skip (such as calling Stage 4 while parent is in `Analysis`) with an exit code of 1.
  2. *Strict Stage 6 Gate 5 Prerequisite Validation in `final-review-verdict` (`REQ-PRO-109.2`, [Verified])*: `tools/jira_util.py final-review-verdict` SHALL strictly reject execution on parent tickets that are not in `Final Review (Agent)`, prohibiting premature parent stage advancement past Gate 5, while preserving `--bounce-target` healing and standard bounce transitions back to `Implementation`, `Analysis`, or `Zu erledigen`.
  3. *Strict Subtask & Impl-Plan Summary Validation in `check-gate` (`REQ-PRO-109.3`, [Verified])*: `tools/jira_util.py check-gate` SHALL query `issuetype`, `summary`, `status`, and `parent`, validating that `issue_key` is a sub-task (`issuetype.subtask == True`) and that its summary matches the `[Impl-Plan]` prefix/pattern (Gate 3). If the issue is not a sub-task or does not represent an `[Impl-Plan]` subtask, it SHALL reject verification with an exit code of 1 regardless of issue status.
  4. *Structured Pros/Cons & Recommendation Validation in `final-review-verdict` (`REQ-PRO-109.4`, [Verified])*: `tools/jira_util.py final-review-verdict` on `PASS` SHALL validate that the report text contains structured markdown sections with non-empty content for Pros (`* *Pros*:` or `Pros (Was ist gut/gelungen?)`), Cons / Defects (`* *Cons*:` or `Cons / Auffälligkeiten / Defects Identified`), and Recommendation (`* *Würdest du das freigeben?*:` or `Freigabe-Empfehlung`), or a structured markdown table with >= 2 evaluated options, rejecting bare keyword stuffing.
  5. *Sanctioned Workflow & Protection Invariants (`REQ-PRO-109.5`, [Verified])*: Tooling changes SHALL strictly preserve all sanctioned Coordinator workflows (`pack-sprint`, `return-unstarted`, `carryover-wip`, `archive-po-acceptance`, `defer-systemtest`), `ATT-8153` QA non-conformance escalation, `ATT-8426` decoupled 7-day review currency, and post-bounce parent transitions back to `Final Review (Agent)`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-109` refining `REQ-PRO-016` (Human Gate & Preconditions), `REQ-PRO-058` (Stage 6 Carbon Review), and `REQ-PRO-060` (Stage submissions). Targets `tools/jira_util.py` and `tools/test_jira_accounts.py`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-11-16_03_Nacht via ticket `ATT-8154` under Epic `ATT-8138` (ASPICE 4.0 Audit Remediation).
  3. *Root Reason for Existing Formulation*: Early composite CLI commands auto-walked parent stages forward in a loop for local development convenience, creating bypass paths observed during ASPICE audit.
  4. *Preservation of Core Invariants*: Inviolable human decision gate (`REQ-PRO-016`), role separation (`agent1` vs `agent2`), 3-bounce healing loop, POSIX flock merge lock, pure Python stdlib offline resilience strictly preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a parent ticket in `Analysis`,
  • *When* `tools/jira_util.py submit-stage <PARENT> 4 ...` is invoked,
  • *Then* the command fails with an error and non-zero exit code, rejecting multi-stage forward jumping.
  • *Given* a parent ticket not in `Final Review (Agent)` (e.g. in `Implementation` or `Test`),
  • *When* `tools/jira_util.py final-review-verdict <PARENT> PASS ...` is invoked,
  • *Then* the command fails with an error and non-zero exit code.
  • *Given* an issue key that is not an `[Impl-Plan]` subtask (e.g. an Analysis subtask or parent ticket),
  • *When* `tools/jira_util.py check-gate <KEY>` is run,
  • *Then* the command fails with an error and non-zero exit code even if the status is `Erledigt`.
  • *Given* a Stage 6 review report with plain keyword stuffing but without structured section headers,
  • *When* `tools/jira_util.py final-review-verdict <PARENT> PASS ...` is invoked,
  • *Then* the command rejects the report with an error.
- **Invariants**: Zero Android production code impact (`app/src/main/`); `ANT-Android-SDKs` untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; existing test fixtures and POSIX flock semantics preserved.

---

### REQ-PRO-110: Integration Verification Test Cases for INT-SWE-01 to INT-SWE-04 (ATT-8155) [Verified]
- **Tests**: `TST-PRO-110`, `TST-INT-009`, `TST-INT-010`, `TST-INT-011`, `TST-INT-012`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/UiPresentationIntegrationTest.kt`, `ViewModelRepositoryIntegrationTest.kt`, `InterServiceIngestionIntegrationTest.kt`, `HardwareHalIngestionIntegrationTest.kt`, `build/AspiceSweAuditContractTest.kt`
- **Rationale**: ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7, rating PA 1.1 = P) established that while `docs/architecture/integration_strategy.md` defines integration steps `INT-SWE-01..08` across interfaces `IF-SWE-01..08`, integration test verification was non-effective: `INT-SWE-01..04` were never refined into traceable test specifications (`TST-INT-*`), instrumented tests were limited to 3 smoke checks with zero Compose UI tests, and no versioned machine-readable JUnit XML evidence existed for component boundary interactions. Implementing formal test specifications, Robolectric JVM integration tests, and evidence export for `INT-SWE-01..04` establishes verifiable SWE.5 compliance.
- **Description**:
  1. *INT-SWE-01: UI to ViewModel MVI Integration (`REQ-PRO-110.1`, [Verified])*: Jetpack Compose Composables (`ControlTrackingButton.kt`) and ViewModels (`TrackingViewModel.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsViewModel.kt`) SHALL have integration verification test coverage via Robolectric `createComposeRule()` asserting unidirectional MVI `StateFlow` / `LiveData` collection (`uiState`, `screenMode`, `trackingMode`) and action dispatch (`onStartTracking()`, `onPauseTracking()`, `onResumeTracking()`, `onStopTracking()`, `updateScreenMode()`, `setScreenMode()`) with `waitForIdle()` Compose node recomposition checks without emulator dependency.
  2. *INT-SWE-02: ViewModel to Repository Data Layer Integration (`REQ-PRO-110.2`, [Verified])*: ViewModels (`WorkoutSummariesViewModel.kt`) and repositories (`WorkoutRepository.kt`, `BANALServiceRepository.kt`) SHALL have integration test coverage asserting real SQLite updates (`WorkoutSummariesDatabaseManager.java` via `RealSqliteTestHelper.kt`) trigger reactive `Flow` emissions (`getWorkoutById`, `allWorkouts`) to ViewModel `StateFlow` without main-thread deadlocks.
  3. *INT-SWE-03: Inter-Service Ingestion Binder IPC Integration (`REQ-PRO-110.3`, [Verified])*: Inter-service communication between `TrackerService.java` and `BANALService.java` via `BANALServiceComm` local binder SHALL have integration test coverage asserting 1-Hz batch polling (`sampleAndWriteToDb`, `getBestSensorData()`, `getAllSensorData()`) into `LiveWorkoutSession` and SQLite (`WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`), sensor priority arbitration via `MySensorManager.java`, and unbind null-safety resilience.
  4. *INT-SWE-04: Hardware HAL Byte Stream Ingestion & Sensor Replay (`REQ-PRO-110.4`, [Verified])*: Ingestion of Bluetooth SIG GATT characteristic byte streams (`BTLEHeartRateDevice.java` `0x2A37`, `BTLEBikePowerDevice.java` `0x2A63`, `BTLERadarDevice.java` `6a4e3204` V1 & V2 `parseV2ThreatPacket`) and Location updates (`SpeedAndLocationDevice_GPS.java` `onNewLocation()`) into `MySensorManager.java` and `RadarStateRepository.kt` SHALL have integration test coverage asserting binary packet parsing, location accumulation, and real `BTLERadarDevice` watchdog timeout debounce (`WATCHDOG_TIMEOUT_MS` = 3,500 ms).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: `REQ-PRO-110` refining `REQ-PRO-045` (`docs/architecture/integration_strategy.md`) and `docs/architecture/interfaces.md` (`IF-SWE-01..04`). Targets `app/src/test/java/com/atrainingtracker/trainingtracker/integration/` and `tools/generate_test_evidence.py`.
  2. *Historical Origin & Commit Trace*: Sprint `2026-11-16_03_Nacht` / `2026-11-17_01_Früh` via ticket `ATT-8155` (`96bfc669`, `23009d93`, Bounce 2/3 Rule 8.7 grounding remediation) resolving ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7, PA 1.1 = P).
  3. *Root Reason for Existing Formulation*: Initial `REQ-PRO-110` text inherited unverified component aliases before exact class/method verification under Rule 8.7; Bounce 2/3 aligned every cited path/symbol with real production code and added `TrackingViewModel` (`uiState`, `updateScreenMode()`) Compose recomposition verification.
  4. *Preservation of Core Invariants*: Preserves the 8-step bottom-up integration order, legacy `TST-INT-002..008`, Rule 79 / Level 1 Robolectric fast gate, and zero modifications to `app/src/main/` or `ANT-Android-SDKs` (`0e8cea71`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `ControlTrackingButton.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsViewModel.kt`, and `TrackingViewModel.kt` composed via `createComposeRule()`,
  • *When* `onStartTracking()`, `onPauseTracking()`, `onResumeTracking()`, `onStopTracking()`, `updateScreenMode()`, or `setScreenMode()` are dispatched,
  • *Then* `uiState` / `screenMode` / `trackingMode` state changes recompose Compose UI nodes (`waitForIdle()` + `assertTextEquals()`) without exceptions.
  • *Given* `WorkoutRepository.kt` and `WorkoutSummariesViewModel.kt` backed by real `WorkoutSummariesDatabaseManager.java` SQLite,
  • *When* workout summary records are inserted and updated,
  • *Then* `getWorkoutById` and `allWorkouts` emit updated records to the ViewModel StateFlow.
  • *Given* `TrackerService.java` connected to real `BANALService.java` (`BANALServiceComm`),
  • *When* `sampleAndWriteToDb()` polls `getBestSensorData()` and `getAllSensorData()`,
  • *Then* sensor telemetry is arbitrated by `MySensorManager.java`, persisted to SQLite, and unbind handles null gracefully.
  • *Given* GATT byte streams for HR (`0x2A37`), Power (`0x2A63`), Radar V1/V2 (`6a4e3204`), and `Location` fixes,
  • *When* ingested via `BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLERadarDevice`, and `SpeedAndLocationDevice_GPS`,
  • *Then* packets parse into `MySensorManager` / `RadarStateRepository` and `BTLERadarDevice` watchdog (`3,500 ms`) transitions to `DISCONNECTED_ERROR`.
- **Invariants**: Zero Android production code impact (`app/src/main/`); `ANT-Android-SDKs` untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; existing test fixtures and POSIX flock semantics preserved.

---

### REQ-PRO-111: Integration Verification Test Cases for INT-SWE-05 to INT-SWE-08 (ATT-8156) [Verified]
- **Tests**: `TST-PRO-111`, `TST-INT-013`, `TST-INT-014`, `TST-INT-015`, `TST-INT-016`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/DomainCalculatorsIntegrationTest.kt`, `PersistenceSqliteIntegrationTest.kt`, `FileExportersIntegrationTest.kt`, `CloudHealthConnectIntegrationTest.kt`, `build/AspiceSweAuditContractTest.kt`, `docs/architecture/integration_strategy.md`
- **Rationale**: Completes ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7) remediation after `ATT-8155` (`INT-SWE-01..04`) by establishing genuine real-component cross-boundary integration tests for `INT-SWE-05..08` (`IF-SWE-05..08`) and reconciling stale metrics and emulator policy in `docs/architecture/integration_strategy.md` against `docs/engineering/test_evidence/sprint_455.md` and `.agents/skills/stage5-verification/SKILL.md`.
- **Description**:
  1. *INT-SWE-05: Domain Calculators & Algorithms (`REQ-PRO-111.1`, [Verified])*: Pure-JVM calculators (`AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `WorkoutClusterEngine`, `ClimbDetector`) SHALL have integration test coverage driven by synthetic telemetry and elevation time-series without primitive constant stubs.
  2. *INT-SWE-06: Persistence SQLite Foundation (`REQ-PRO-111.2`, [Verified])*: Database managers (`WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `KnownLocationsDatabaseManager`, `LapsDatabaseManager`) and `KnownLocationsRepository` SHALL have Robolectric SQLite integration test coverage asserting WAL concurrency, dynamic sample table creation, bulk inserts, and non-destructive `onUpgrade` schema parity.
  3. *INT-SWE-07: Repository/ExportManager to File Exporters (`REQ-PRO-111.3`, [Verified])*: Streaming file writers (`FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`) driven from real SQLite records via `ExportManager` / `WorkoutRepository` SHALL have integration test coverage asserting valid FIT/GPX/TCX output, `.tmp` atomic cleanup, and `ExportStatusDatabaseManager` updates.
  4. *INT-SWE-08: Repository/ExportManager to Cloud & Health Connect (`REQ-PRO-111.4`, [Verified])*: External uploaders and Health Connect connectors (`StravaUploader`, `DropboxUploader`, `HealthConnectManager`, `HealthConnectHrvImporter`) wired to real `ExportStatusDatabaseManager` / `HrvReadinessRepository` with authentic OkHttp `Interceptor` / `HealthConnectClient` doubles SHALL have integration test coverage asserting bearer-token injection, multipart upload + polling + PUT update, HTTP 503 retry classification, `DropboxUploader` missing-credential guard, session mapping, and nocturnal HRV import.
  5. *Integration Strategy Factual Sync (`REQ-PRO-111.5`, [Verified])*: `docs/architecture/integration_strategy.md` MUST be synchronized with authentic `sprint_455.md` metrics (`6,540` unit tests / `2,635.73s` sum; `1,119` suites / `29m 21s` Tier-2 wall-clock per `Sprint_Review_and_Retro_ATT-8500.md`), Level 1 `INT-SWE-01..08` coverage, and `emulator-5554` (`Pixel_8_Android_15`) Stage 5 policy.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-111` refining `REQ-PRO-045` (`docs/architecture/integration_strategy.md`) and `docs/architecture/interfaces.md` (`IF-SWE-05..08`).
  2. *Historical Origin & Commit Trace*: Sprint `2026-11-17_01_Früh` via `ATT-8156`, extending healed `ATT-8155` (`b81888c8`) for ASPICE 4.0 Finding `M6`.
  3. *Root Reason for Existing Formulation*: `integration_strategy.md` specified `INT-SWE-05..08` without executable test suites and retained outdated test counts (`3,660+`), runtime estimates (`~2–3 minutes`), and retired-emulator text.
  4. *Preservation of Core Invariants*: Preserves `REQ-PRO-045`, `REQ-PRO-110`, `TST-INT-002..012`, zero `app/src/main/` modifications, and `ANT-Android-SDKs` at `0e8cea71`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* synthetic telemetry and elevation profiles,
  • *When* evaluated by real `AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `WorkoutClusterEngine`, and `ClimbDetector`,
  • *Then* calculated physiology, cluster, climb, and auto-pause transitions match deterministic invariants.
  • *Given* real SQLite database managers and `KnownLocationsRepository`,
  • *When* dynamic tables are created, concurrent WAL writes occur, and `onUpgrade` runs,
  • *Then* schema parity and persisted records remain intact without locks or data loss.
  • *Given* persisted SQLite workout data and temp storage,
  • *When* `ExportManager` executes `FitFileWriter`, `GPXFileWriter`, and `TCXFileWriter`,
  • *Then* valid FIT/GPX/TCX outputs are generated, `.tmp` artifacts are cleaned up, and `ExportStatusDatabaseManager` records completion.
  • *Given* `StravaUploader`/`DropboxUploader` (OkHttp `Interceptor` double) and `HealthConnectManager`/`HealthConnectHrvImporter` (fake `HealthConnectClient`) wired to real `ExportStatusDatabaseManager` and `HrvReadinessRepository`,
  • *When* upload, HTTP 503 retry check, Dropbox credential guard, workout sync, and HRV import execute,
  • *Then* bearer-token multipart upload/PUT + retry classification, Health Connect record mapping, and nocturnal HRV persistence succeed.
  • *Given* `docs/architecture/integration_strategy.md`,
  • *When* reviewed against `sprint_455.md` and `stage5-verification/SKILL.md`,
  • *Then* test counts (`6,540`), suite counts (`1,119`), runtime (`29m 21s` Tier-2 / `2,635.73s` sum), and `emulator-5554` policy match reality.
- **Invariants**: Zero Android production code impact (`app/src/main/`); `ANT-Android-SDKs` untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; existing test fixtures and POSIX flock semantics preserved.

---

### REQ-PRO-112: Simulator Self-Test Scoping & Release Gate Honesty (ATT-8157) [Verified]
- **Tests**: `TST-PRO-112`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemIntegrationTestContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseManagerSystemEvidenceContractTest.kt`, `tools/generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/architecture.md`, `docs/system/sys_integration_tests.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`
- **Rationale**: Resolves ASPICE 4.0 Assessment (2026-11-15) Finding `M6` on system-level testing honesty (`ATT-8157`): `tools/run_system_integration_tests.py` ran pure-Python simulator checks and constant-arithmetic assertions without executing the Android APK, ADB, or emulator, while `.agents/skills/release-manager/SKILL.md` (with broken `--output-json` CLI drift vs `--output`), `tools/generate_release_verification_summary.py`, `docs/architecture.md`, `docs/releases/`, and `docs/releases/V5.0.0/` represented `system_integration_results.json` as a blocking SYS.4 app-integration release gate.
- **Description**:
  1. *Simulator Self-Test Rename & Rescoping (`REQ-PRO-112.1`, [Verified])*: `tools/run_system_integration_tests.py` and `tools/test_system_integration_tests.py` SHALL be renamed and rescoped to `tools/run_simulator_self_tests.py` and `tools/test_simulator_self_tests.py` (`SimulatorSelfTestRunner`), explicitly classified as pure-Python developer simulator self-tests (testing `ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py` contracts), eliminating misleading constant-arithmetic claims (`(10 - 65530) & 0xFFFF`, local `backoffs`/`oauth_response` literals) and aligning CLI `--output` parity with `SystemIntegrationTestContractTest.kt`.
  2. *Non-Blocking Release Gate Removal (`REQ-PRO-112.2`, [Verified])*: `system_integration_results.json` SHALL be removed as a mandatory blocking SYS.4 release gate in `tools/generate_release_verification_summary.py` (`collect_sys4_summary()`), `tools/test_generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, and `ReleaseManagerSystemEvidenceContractTest.kt`; blocking release gating remains anchored strictly on unit/integration tests (`INT-SWE-01..08`, CI `AppSmokeAndroidTest.kt`) and hardware-in-the-loop `SYS.5` sign-off (`sys_verification_record.md`) + `verification_summary.md`.
  3. *Honest Documentation & V5.0.0 Reclassification (`REQ-PRO-112.3`, [Verified])*: `docs/architecture.md` (§6.2, §6.4), `docs/system/sys_integration_tests.md`, and `docs/releases/V5.0.0/` (`verification_summary.md`, `system_integration_results.json`) SHALL disclose honestly that Python simulators are developer tools only, physical-sensor system verification is manual hardware-in-the-loop (`SYS.5`), and legacy `V5.0.0` `system_integration_results.json` reflects historical simulator self-tests rather than app execution (without fabricating hardware logs).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-112` refining `REQ-PRO-068`, `REQ-PRO-072`, and `REQ-PRO-074.4` across `tools/`, `.agents/skills/release-manager/SKILL.md`, `docs/architecture.md`, `docs/system/sys_integration_tests.md`, `docs/releases/`, and build contract tests.
  2. *Historical Origin & Commit Trace*: Sprint `2026-11-17_01_Früh` via `ATT-8157`, following `ATT-8155` (`b81888c8`) and `ATT-8156` (`84767320`) under Epic `ATT-8138`.
  3. *Root Reason for Existing Formulation*: `ATT-6166`, `ATT-6169`, and `ATT-6138` wired Python simulator JSON into release blocking gates while `SKILL.md` referenced a nonexistent `--output-json` flag, producing false SYS.4 confidence without executing Android code.
  4. *Preservation of Core Invariants*: Preserves simulator tool unit testing (`ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py`, `fake_gps.py`, `replay_workout.py`), `SYS.5` real-device release gating, `verification_summary.md` deviation blocking, zero `app/src/main/` changes, and `ANT-Android-SDKs` (`0e8cea71`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `tools/run_simulator_self_tests.py` and `tools/test_simulator_self_tests.py`,
  • *When* executed via `python3`,
  • *Then* simulator self-tests pass against real simulator modules without claiming Android/emulator integration, and `--output` JSON export works cleanly.
  • *Given* `tools/generate_release_verification_summary.py` run for a release tag without `system_integration_results.json`,
  • *When* unit tests, traceability coverage, and `sys_verification_record.md` pass,
  • *Then* `collect_sys4_summary()` raises zero missing-gate deviations and `verification_summary.md` evaluates to `PASS (READY FOR RELEASE)`.
  • *Given* `docs/architecture.md`, `docs/system/sys_integration_tests.md`, `docs/releases/{README,TEMPLATE}.md`, `docs/releases/V5.0.0/`, `.agents/skills/release-manager/SKILL.md`, `SystemIntegrationTestContractTest.kt`, and `ReleaseManagerSystemEvidenceContractTest.kt`,
  • *When* audited via Gradle contract tests and `verify_requirement_governance.py`,
  • *Then* all tests pass with explicit honest simulator classification and zero false SYS.4 blocking claims.
- **Invariants**: Zero Android production code impact (`app/src/main/`); `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`; pure Python 3 stdlib for `tools/` test runners.

---

### REQ-PRO-113: Measured NFR Verification, Manual Protocol Honesty & Evidence Pipeline (ATT-8158) [Verified]
- **Tests**: `TST-PRO-113`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/MeasuredNfrBenchmarkTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`, `docs/engineering/test_evidence/nfr_manual_protocol_template.md`, `tools/generate_test_evidence.py`, `tools/test_generate_test_evidence.py`, `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`
- **Rationale**: Resolves ASPICE 4.0 Assessment (2026-11-15) Finding `M8` (`SWE.2 BP3`, `SWE.6 BP3`, `ATT-8158`): `NonFunctionalRequirementsContractTest.kt` previously marked all 18 `REQ-NFR-001`..`018` and `TST-NFR-001`..`018` as `[Verified]` solely via markdown substring matching, without runtime timing, memory, storage, or throttle measurements, and without distinguishing JVM-measurable NFRs from hardware-only manual protocols.
- **Description**:
  1. *Measured JVM/Robolectric NFR Benchmarks (`REQ-PRO-113.1`, [Verified])*: The 11 JVM-measurable NFRs (`REQ-NFR-001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014`) SHALL be verified by executable JVM/Robolectric benchmarks/tests that execute JIT warmup, measure actual elapsed timing, heap delta, SQLite file growth, or emission rate metrics against the documented §4.9 budgets, and emit machine-readable NFR metrics (`app/build/reports/nfr/nfr_measurements.json`).
  2. *Honest Attested Manual Protocol Classification (`REQ-PRO-113.2`, [Verified])*: The 7 hardware/battery/Doze/Play-Vitals NFRs (`REQ-NFR-004, 006, 012, 015, 016, 017, 018`) SHALL use `Verification Method: On-Device Manual` and MUST remain `[Implemented]` / `[Planned]` (never `[Verified]` via unit tests) until an attested physical-device measurement report is recorded.
  3. *Evidence Ingestion & Contract Guardrail (`REQ-PRO-113.3`, [Verified])*: `tools/generate_test_evidence.py` SHALL ingest measured NFR JSON metrics and manual-protocol attestation status into `docs/engineering/test_evidence/<target>.{md,json}`, and `NonFunctionalRequirementsContractTest.kt` SHALL enforce measured benchmark & manual-protocol artifact integrity rather than raw markdown threshold-string presence.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-113` refining `REQ-NFR-001`..`018`, `REQ-PRO-052`, and `REQ-PRO-100` across `NonFunctionalRequirementsContractTest.kt`, `tools/generate_test_evidence.py`, and living documentation shards.
  2. *Historical Origin & Commit Trace*: Sprint `2026-11-17_01_Früh` via `ATT-8158`, following `ATT-8157` (`1750ab64`) under Epic `ATT-8138`.
  3. *Root Reason for Existing Formulation*: `ATT-6139` / `ATT-6181` / `ATT-6735` relied on `NonFunctionalRequirementsContractTest.kt` doc-string assertions and had no mechanism to measure budgets or feed NFR metrics into `docs/engineering/test_evidence/`.
  4. *Preservation of Core Invariants*: Preserves all 18 numeric budgets, `ArchitectureConformanceContractTest.kt` §4.9 checks, `TrackerService` / `WorkoutRepository` SQLite persistence correctness (offloading synchronous extrema SQLite I/O from the 1-Hz tick to `mDbExecutor` while keeping in-memory StateFlow updates reactive), and `ANT-Android-SDKs` (`0e8cea71`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* the 11 JVM-measurable NFR benchmarks executed under `./gradlew testDebugUnitTest`,
  • *When* warmup and measured loops complete,
  • *Then* all 11 measured metrics satisfy documented budgets and emit structured values to `app/build/reports/nfr/nfr_measurements.json`.
  • *Given* `tools/generate_test_evidence.py` executed after unit tests,
  • *When* generating `docs/engineering/test_evidence/<target>.{md,json}`,
  • *Then* measured Group A metrics and Group B `On-Device Manual` `[Planned]`/`[Implemented]` statuses appear transparently without fabricated hardware results.
  • *Given* `NonFunctionalRequirementsContractTest.kt` and `tools/verify_requirement_governance.py`,
  • *When* executed,
  • *Then* string-only doc-presence verification is replaced by authentic benchmark/protocol verification and governance exits `0`.
- **Invariants**: Zero unverified hardware `[Verified]` statuses; `ANT-Android-SDKs` untouched; zero main-thread or 1-Hz tick synchronous SQLite extrema writes.

---

### REQ-PRO-114: Living Detailed Design & State Machine Models for Core Services (ATT-8159) [Verified]
- **Tests**: `TST-PRO-114`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/architecture/detailed_design/tracker_service.md`, `docs/architecture/detailed_design/banal_service.md`, `docs/architecture/detailed_design/my_btle_device.md`, `docs/architecture.md`, `.agents/skills/stage3-impl-plan/SKILL.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DetailedDesignParityContractTest.kt`
- **Rationale**: Resolves ASPICE 4.0 Assessment (2026-11-15) Finding `M9` (`SWE.3 BP1/BP2/BP4`, `ATT-8159`): detailed design previously existed only as frozen per-ticket implementation plans, KDoc, and requirement prose, with no unit-level dynamic state machine models for the three core services (`TrackerService`, `BANALService`, `MyBTLEDevice`) and no Stage 3 governance rule enforcing living detailed-design maintenance.
- **Description**:
  1. *Unit Detailed Design Documents & State Machine Models (`REQ-PRO-114.1`, [Verified])*: `docs/architecture/detailed_design/` SHALL maintain three unit-level detailed-design documents (`tracker_service.md`, `banal_service.md`, `my_btle_device.md`), each specifying a Mermaid `stateDiagram-v2` model, event/guard/transition tables, external/internal interface tables (`IF-SWE-*` / SWE.2 alignment), threading & concurrency contracts, error-handling & fault-recovery mechanisms, and bidirectional traceability to `ATT-7902` characterisation and unit test suites (`TrackerServiceStateMachineCharacterisationTest`, `TrackerServiceCrashRecoveryCharacterisationTest`, `TrackerServiceIntegrationTest`, `TrackerServiceFinalizationTest`, `TrackerServiceResilienceTest`, `TrackerServiceAsyncInitTest`, `TrackerServiceResumeTest`, `TrackerServiceRrFlushTest`, `TrackerServiceExportGatingTest`, `BANALServiceArbitrationCharacterisationTest`, `BANALServiceLifecycleTest`, `MyBTLEDeviceLifecycleTest`, `MyBTLEDeviceQueueTest`, `MyBTLEDeviceAddressAndSearchTest`).
  2. *SWE.2 Cross-Reference & Stage 3 Living Detailed-Design Rule (`REQ-PRO-114.2`, [Verified])*: `docs/architecture.md` SHALL cross-reference `docs/architecture/detailed_design/` as the authoritative SWE.3 unit-design layer, and `.agents/skills/stage3-impl-plan/SKILL.md` SHALL require scheduling a living detailed-design update step whenever an implementation plan modifies state handling, transitions, or concurrency contracts of `TrackerService`, `BANALService` / `DeviceManager`, or `MyBTLEDevice`.
  3. *Automated Code-Parity Contract Verification (`REQ-PRO-114.3`, [Verified])*: `DetailedDesignParityContractTest.kt` SHALL parse the living detailed-design documents under `docs/architecture/detailed_design/` and assert that every documented state/enum constant (`TrackingMode`, `AutoPauseState`, `TrackerService.StartType`, `MyBTLEDevice.State`), key lifecycle/concurrency field & method symbol (`mIsStopping`, `mTrackingInterrupted`, `mIsRehydrating`, `mSessionGeneration`, `mDbExecutor`, `mScheduler`, `mRrFlushExecutor`, `cMyRemoteDeviceCurrentlySearchingFor`, `mSearchQueue`, `mMyRemoteDeviceTries`, `mGattQueue`, `mGattLock`, `pauseGattOperations`, `resumeGattOperations`, `startScanAssistance`, `readNextCharacteristic`, `configureServices`), and cited test class exists verbatim in `app/src/main/` and `app/src/test/` with zero drift.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-114` refining `REQ-PRO-034`, `REQ-PRO-043`, and `REQ-ARC-008` across `docs/architecture/detailed_design/`, `docs/architecture.md`, `.agents/skills/stage3-impl-plan/SKILL.md`, and `app/src/test/java/com/atrainingtracker/trainingtracker/build/DetailedDesignParityContractTest.kt`.
  2. *Historical Origin & Commit Trace*: `053531cb` (`ATT-5431` SWE.2 `docs/architecture.md` & Stage 3 SWE.2 rule), `0d437b9c` (`ATT-6727` dynamic SWE.2 symbol parity), `ATT-7902` (`REQ-ARC-008` Golden Master characterisation tests); extended in Sprint `2026-11-17_01_Früh` via `ATT-8159` under Epic `ATT-8138`.
  3. *Root Reason for Existing Formulation*: SWE.2 component architecture (`docs/architecture.md`, `interfaces.md`) and Golden Master characterisation tests (`ATT-7902`) were established prior to unit-level SWE.3 state machine modeling so behavioral baselines could first be locked down without production code changes.
  4. *Preservation of Core Invariants*: Preserves all existing `AspiceSweAuditContractTest` and `ArchitectureConformanceContractTest` checks, keeps `app/src/main/` and `ANT-Android-SDKs` (`0e8cea71`) strictly untouched, and accurately models the real code split (`BANALService` delegating search state to `DeviceManager` without a `SearchingState` enum; `MyBTLEDevice.State` having 4 states; `TrackerService` combining `TrackingMode`, `AutoPauseState`, `StartType`, and volatile lifecycle flags).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/architecture/detailed_design/{tracker_service,banal_service,my_btle_device}.md`,
  • *When* audited for SWE.3 BP1/BP2/BP4 completeness,
  • *Then* each document contains a Mermaid `stateDiagram-v2`, event/guard/transition table, interface & threading contracts, error-handling mechanisms, and test traceability.
  • *Given* a change to state handling in `TrackerService`, `BANALService` / `DeviceManager`, or `MyBTLEDevice`,
  • *When* a Stage 3 plan is authored or reviewed per `.agents/skills/stage3-impl-plan/SKILL.md`,
  • *Then* the plan includes the corresponding living detailed-design update step.
  • *Given* `DetailedDesignParityContractTest.kt` executed under `./gradlew testDebugUnitTest`,
  • *When* comparing documented states, symbols, and test classes against `app/src/main/` and `app/src/test/`,
  • *Then* 100% of documented states/transitions/symbols resolve verbatim in source with zero drift.
- **Invariants**: Zero edits to `app/src/main/` production behavior; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`; all existing characterisation and contract tests remain 100% green.


---

### REQ-PRO-115: Architecture & Interface Documentation Drift Remediation and JVM-Reflection Signature Check (ATT-8160) [Verified]
- **Tests**: `TST-PRO-115`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/architecture.md`, `docs/architecture/interfaces.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/InterfaceSignatureDriftContractTest.kt`
- **Rationale**: Resolves ASPICE 4.0 Audit Finding `Minor (SWE.2)` (`SWE.2 BP1/BP2/BP4`, `ATT-8160`), refining `REQ-PRO-044`, `REQ-PRO-052`, and `REQ-PRO-049`: `docs/architecture/interfaces.md` drifted from live `app/src/main/` signatures (parameter counts, parameter types, return types, nullability), `docs/architecture.md` cited brittle `TrackerService.java:842-857` line numbers, omitted requirement prefix families (`SPT` etc.) from Section 2, and mixed ASPICE 3.1 and 4.0 SWE.2 Base Practice labels.
- **Description**:
  1. *Interface Signature & Nullability Remediation (`REQ-PRO-115.1`, `docs/architecture/interfaces.md`)*: `docs/architecture/interfaces.md` SHALL accurately match live `app/src/main/` declarations across all drifted signatures: `IF-SWE-02` `WorkoutRepository.updateExtremaValue(workoutId, sensorType, extremaType, value, position?)` (+ 6-param overload), `IF-SWE-03` `BANALServiceComm.getAllFilteredSensorData(): List<FilteredSensorData<?>>` and `getBestSensorData(sensorType: SensorType): SensorData`, `IF-SWE-05` `AutoPauseManager.evaluateTick` (7 params), `PowerPhysiologyCalculator.calculateNormalizedPower(List<Double?>, Double = 1.0): Double?`, `PowerHrDynamicsCalculator.calculate(List<Double?>?, List<Double?>?, Long = 0L, Double = 1.0, List<Long>? = null): CardioPowerDynamicsRecord?`, `WorkoutClusterEngine.suggestCluster` (11 params), `ClimbDetector.detectClimbs` (6 params), `IF-SWE-06` `WorkoutRepository.recordLiveSample` (8 params returning `Boolean`), `IF-SWE-08` `HealthConnectManager.syncWorkout` (3 params) and `HealthConnectHrvImporter.importRecentHrv` (4 params).
  2. *Symbol Anchors, Prefix Allocation & ASPICE 4.0 BP Alignment (`REQ-PRO-115.2`, `docs/architecture.md`, `ArchitectureConformanceContractTest.kt`)*: `docs/architecture.md` SHALL replace brittle `:line` citations (`TrackerService.java:842-857`) with stable symbol anchors (`TrackerService#acquireWakeLock` / `releaseWakeLock`), add missing requirement prefixes (`SPT`, `HRV`, `RAD`, `WKO`, `CLM`) to Section 2, and standardize all SWE.2 labels across `docs/architecture.md` and `docs/architecture/interfaces.md` to **ASPICE 4.0** (`BP1` Static & Interfaces, `BP2` Dynamic Behavior, `BP3` Analyze Architecture & Resource/Timing Budgets, `BP4` Consistency & Bidirectional Traceability/Allocation, `BP5` Communicate Agreed Architecture). `ArchitectureConformanceContractTest.kt` SHALL check the WakeLock symbol anchors while preserving `PARTIAL_WAKE_LOCK`, `mScheduler`, `mDbExecutor`, and `REQ-NFR-003`..`014` assertions.
  3. *JVM-Reflection Signature-Drift Contract Verification (`REQ-PRO-115.3`, `InterfaceSignatureDriftContractTest.kt`)*: `InterfaceSignatureDriftContractTest.kt` SHALL parse documented operation signatures in `docs/architecture/interfaces.md` and verify via **JVM reflection** (`Class.forName`, `declaredMethods`, parameter arity, parameter types, return types, and `@Nullable`/`@NotNull` nullability) against compiled `app/src/main/` classes, performing ZERO `.readText()` / `.readLines()` reads on `app/src/main/` in compliance with `REQ-PRO-049` (`SourceTextReadingAuditContractTest`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-115` refining `REQ-PRO-044` (`interfaces.md` & `AspiceSweAuditContractTest.kt`), `REQ-PRO-052` (`docs/architecture.md` §4.9 & `ArchitectureConformanceContractTest.kt`), and `REQ-PRO-049` (`SourceTextReadingAuditContractTest.kt`).
  2. *Historical Origin & Commit Trace*: `ATT-6180`/`ATT-6732` (`REQ-PRO-044` added name-only regex grounding), `0777baa6` (`ATT-6735`, `REQ-PRO-052` hardcoded `TrackerService.java:842-857`), and `ATT-6186` (`REQ-PRO-049` prohibits unallowlisted `src/main` file reads in tests).
  3. *Root Reason for Existing Formulation*: Regex name checks missed parameter arity, type, and nullability evolution; `:842-857` predated later `TrackerService.java` growth (`acquireWakeLock` at L1079-1092, `releaseWakeLock` at L1094-1102); ASPICE 3.1 BP numbering persisted in early headings.
  4. *Preservation of Core Invariants*: Preserves all `AspiceSweAuditContractTest` Section 2/5/6 checks, all `ArchitectureConformanceContractTest` `PARTIAL_WAKE_LOCK`/`mScheduler`/`mDbExecutor`/`REQ-NFR-003..014` checks, `DetailedDesignParityContractTest` anchors, `SourceTextReadingAuditContractTest` counts (`22` guards, `105` baseline), and `ANT-Android-SDKs` (`0e8cea71`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/architecture/interfaces.md` and compiled `app/src/main/` classes,
  • *When* `InterfaceSignatureDriftContractTest` executes under `./gradlew testDebugUnitTest`,
  • *Then* all documented interface signatures match compiled JVM reflection signatures (names, parameter arity/types, return types, nullability) with zero `src/main/` text reads.
  • *Given* `docs/architecture.md`,
  • *When* inspected and validated by `ArchitectureConformanceContractTest` and `AspiceSweAuditContractTest`,
  • *Then* WakeLock uses symbol anchors (`acquireWakeLock`/`releaseWakeLock`), Section 2 lists `SPT` (and `HRV`, `RAD`, `WKO`, `CLM`), and all SWE.2 headings use ASPICE 4.0 `BP1`–`BP5` labels.
- **Invariants**: Zero edits to `app/src/main/` and `ANT-Android-SDKs`; `Localization Parity: N/A - no UI strings`; `SourceTextReadingAuditContractTest` remains 100% green.


---

### REQ-PRO-116: Retrospective Architecture Decision Records (ADR-002..ADR-007) & Reflection Contract (ATT-8161) [Verified]
- **Tests**: `TST-PRO-116`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/architecture/adr_002_foreground_tracking_service.md`, `docs/architecture/adr_003_banal_sensor_bus.md`, `docs/architecture/adr_004_sqlite_managers_vs_room.md`, `docs/architecture/adr_005_hybrid_preferences_datastore.md`, `docs/architecture/adr_006_incremental_compose_navigation.md`, `docs/architecture/adr_007_ble_ant_sensor_abstraction.md`, `docs/architecture.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AdrCompletenessContractTest.kt`
- **Rationale**: Resolves ASPICE 4.0 Audit Finding `Minor (SWE.2)` (`SWE.2 BP3/BP5`, `ATT-8161`), refining `REQ-ARC-005`, `REQ-PRO-043`, and `REQ-PRO-049`: only `ADR-001` exists (`adr_001_java_core_services_migration.md`), leaving the 6 foundational architectural decisions without documented criteria, evaluated alternatives, or consequences to protect Chesterton's Fence invariants.
- **Description**:
  1. *Six Retrospective ADRs (`REQ-PRO-116.1`, `docs/architecture/adr_002..007_*.md`)*: The repository SHALL document six retrospective ADRs matching the `ADR-001` structure (`Status`, `Deciders`, `Date`, `Context & Problem Statement`, `Decision Drivers & Evaluated Alternatives` with `>= 2` alternatives explicitly evaluated as `Rejected` or `Selected`, `Decision Outcome`, `Architectural Consequences & Trade-offs`):
     - `ADR-002`: Foreground Tracking Service (`TrackerService`, `foregroundServiceType="location|health"`, `START_STICKY`, `PARTIAL_WAKE_LOCK`, `mScheduler`/`mDbExecutor`/`mRrFlushExecutor`) vs `WorkManager`/`JobScheduler`.
     - `ADR-003`: Sensor Ingestion Bus (`BANALService`, `BANALServiceComm`, `MySensorManager`, `ProxySensor`, `FilterManager`, `BANALServiceRepository` `StateFlow`) vs static EventBus or cross-process AIDL.
     - `ADR-004`: SQLite Managers vs Room ORM (`WorkoutSamplesDatabaseManager` dynamic per-workout tables, `WorkoutSummariesDatabaseManager`, `LapsDatabaseManager`, `DevicesDatabaseManager`, WAL + repository encapsulation via `WorkoutRepository`, `DeviceDataRepository`, `KnownLocationsRepository`) vs full Room ORM migration.
     - `ADR-005`: Hybrid Preferences (`PreferenceManager.getDefaultSharedPreferences` in legacy/service hotpaths alongside `SettingsDataStore`, `TuningPreferencesDataStore`, `MyPreferenceManager`, `AttFusedPreferences`, `SettingsDataStoreJavaHelper`) vs pure `SharedPreferences` or big-bang DataStore rewrite.
     - `ADR-006`: Incremental Single-Activity Compose Navigation (`MainActivityWithNavigation`, `ATrainingTrackerApp`, `NavHost`, `AppNavigationDrawer`, MVI `StateFlow` ViewModels coexisting with legacy XML/fragments `ConfigViewsActivityClassic`, `ConfigViewsFragment`, `TrackOnMapHelper`) vs pure multi-Fragment XML or big-bang Compose rewrite.
     - `ADR-007`: Unified BLE & ANT+ Hardware Abstraction (`MyDevice` -> `MyRemoteDevice` -> `MyANTDevice` over pinned `ANT-Android-SDKs` `0e8cea71` & `MyBTLEDevice` + `PolarOnlineStreamingBridge`, unified by `DeviceManager` into `MySensor`/`ProxySensor` `SensorType`) vs protocol-specific tracking/UI branches.
  2. *Honest PO Sign-Off & Architecture Index (`REQ-PRO-116.2`, `docs/architecture.md`)*: Per `ATT-8138` evidence integrity, each ADR MUST record `Status: Retrospective (Pending PO Sign-Off)` and `Deciders: Architecture Board, Product Owner (Pending Sign-Off)` unless explicitly signed off by the PO (zero fabricated approval). `docs/architecture.md` SHALL index `ADR-001`..`ADR-007` and cite only real `app/src/main/` classes (zero phantom `TrackPointsDatabaseManager`, `ANTDevice`, or `BTLEDevice`).
  3. *JVM-Reflection & Structure Contract Test (`REQ-PRO-116.3`, `AdrCompletenessContractTest.kt`)*: `AdrCompletenessContractTest.kt` SHALL validate `ADR-002`..`ADR-007` structural sections, `>= 2` evaluated alternatives per ADR, honest PO sign-off status, `docs/architecture.md` index links, and verify cited production classes via **JVM reflection (`Class.forName`)** with ZERO `app/src/main/` `.readText()` file reads (`REQ-PRO-049`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-116` refining `REQ-ARC-005` (`ADR-001` & `JavaCoreMigrationStrategyContractTest.kt`), `REQ-PRO-043` (`docs/architecture.md` & `AspiceSweAuditContractTest.kt`), and `REQ-PRO-049` (`SourceTextReadingAuditContractTest.kt`).
  2. *Historical Origin & Commit Trace*: `ATT-7566` / `ATT-8115` (`REQ-ARC-005`), `ATT-5431` / `ATT-6727` (`REQ-PRO-043`), `ATT-6186` (`REQ-PRO-049`), extended via `ATT-8161`.
  3. *Root Reason for Existing Formulation*: `ADR-001` covered only Java-to-Kotlin migration scope, leaving the 6 underlying platform/persistence/UI/HAL architectural choices undocumented as formal ADRs.
  4. *Preservation of Core Invariants*: Preserves `ADR-001` (`JavaCoreMigrationStrategyContractTest.kt`), all `AspiceSweAuditContractTest` & `ArchitectureConformanceContractTest` assertions, `SourceTextReadingAuditContractTest` (`22` guards, `105` baseline), `app/src/main/`, and `ANT-Android-SDKs` (`0e8cea71`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/architecture/adr_002..007_*.md` and `docs/architecture.md`,
  • *When* `AdrCompletenessContractTest` and `AspiceSweAuditContractTest` execute under `./gradlew testDebugUnitTest`,
  • *Then* all 6 ADRs pass structural, `>= 2`-alternative, honest PO sign-off, index, and JVM-reflection class checks with zero `src/main/` text reads.
  • *Given* `tools/verify_requirement_governance.py`,
  • *When* executed across the repository,
  • *Then* all governance and code-grounding checks exit `0`.
- **Invariants**: Zero edits to `app/src/main/`, `ANT-Android-SDKs`, or `ADR-001`; `Localization Parity: N/A - no UI strings`; zero phantom class names.

### REQ-PRO-117: Autonomous Schicht Retro Write-Allowlist & Protected Governance Boundary (ATT-8584) [Verified]
- **Tests**: `TST-PRO-117`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `tools/verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
- **Rationale**: In continuous 3-Schicht autonomous operation (`Rule 2.2`), per-Schicht Ceremony 2 (`Review & Retro`) closes execute without the human PO present. Leaving `.agents/skills/sprint-planner/SKILL.md` L257 with unrestricted permission to modify rules, skills, and standards risks unapproved weakening of quality gates or governance rules, bypassing `REQ-PRO-105` (`ATT-8166`).
- **Description**:
  1. *Autonomous Schicht-End Close Write-Allowlist (`REQ-PRO-117.1`)*: `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md` SHALL restrict autonomous `chore(ceremony2)` writes strictly to evidence and log paths: `docs/engineering/retros/Sprint_Review_and_Retro_*.md`, `docs/engineering/gates/*`, `docs/engineering/qa_audits/*`, `docs/engineering/reviews/*`, `docs/engineering/test_evidence/*`, `docs/engineering/metrics/process_metrics.jsonl`, and `docs/management/risk_register.md` (Sprint Review Log updates per `REQ-STP-010.2`).
  2. *Protected Governance Paths & Escalation Routing (`REQ-PRO-117.2`)*: During autonomous Schicht-end closes without interactive PO authorization or a dedicated PO-approved ticket, agents MUST NOT modify `.agents/rules/**`, `.agents/skills/**`, `docs/coding_standards.md`, `docs/design_guidelines.md`, `docs/project_governance.md`, `docs/project_protocol.md`, `docs/engineering/rule_history_archive.md`, or `tools/**`. Proposed rule, skill, or standard changes SHALL be recorded as structured proposals in `docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md` and the rolling `Wochen Review & Retro` ticket, while preserving `HoechstmassCoreFrameworkContractTest.kt` markers (`sprint-metrics --record --post-to-retro`, `PA 4.2`, `PA 5.2`) and `REQ-STP-010.2`.
  3. *Automated Write-Allowlist Verifier (`REQ-PRO-117.3`)*: `tools/verify_requirement_governance.py` SHALL provide `verify_retro_write_allowlist()` (`--check-retro-allowlist`) rejecting any autonomous `chore(ceremony2)` commit or provided file set that modifies protected or non-allowlisted paths, while cleanly exempting pre-cutoff historical commits (`<= 8aa881df`), dedicated PO-approved ticket commits (`ATT-*`), and commits carrying explicit interactive PO authorization (`Interactive-PO:` trailer).
  4. *Dual-Layer Verification (`REQ-PRO-117.4`)*: `tools/test_verify_requirement_governance.py` SHALL verify allowlist passes and protected-path rejections, and `AspiceSweAuditContractTest.kt` SHALL assert the allowlist and protected-path escalation rules in `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-PRO-105` (`ATT-8166`, PO approval provenance) and `REQ-STP-010.2` (`ATT-8167`, Ceremony 2 risk review) without altering `[Verified]` `REQ-PRO-105` or `REQ-STP-010`.
  2. *Historical Origin & Commit Trace*: `REQ-PRO-105` (`ATT-8166`) established Jira changelog PO provenance; `Rule 2.2` (`ATT-8030`) introduced continuous autonomous 3-Schicht rollovers and rolling `Wochen Review & Retro`.
  3. *Root Reason for Existing Formulation*: Interactive Ceremony 2 sessions co-facilitated by the human PO previously allowed direct rule/skill edits in `SKILL.md` L257.
  4. *Preservation of Core Invariants*: Preserves `[Verified]` `REQ-PRO-105`, `REQ-STP-010.2` (`risk_register.md` Sprint Review Log), `ensure-weekly-retro`, `HoechstmassCoreFrameworkContractTest` markers, and dedicated PO-approved ticket authority (`Vom PO Freigegeben` `447`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md`,
  • *When* inspected by `AspiceSweAuditContractTest` and `HoechstmassCoreFrameworkContractTest`,
  • *Then* both define the 7-path autonomous Retro write-allowlist, protect rules/skills/standards/tools, route proposals to Retro + `Wochen Review & Retro`, and preserve `sprint-metrics` / `PA 4.2` / `PA 5.2` / `REQ-STP-010.2`.
  • *Given* `tools/verify_requirement_governance.py --check-retro-allowlist`,
  • *When* evaluated against allowlisted `chore(ceremony2)` files vs protected-path violations vs ticket-backed or pre-cutoff (`8aa881df`) commits,
  • *Then* allowlisted, ticket-backed, and pre-cutoff commits exit `0` while unauthorized protected/non-allowlisted edits in `chore(ceremony2)` fail with exit `1`.

---

### REQ-PRO-118: Reliable qa-audit Corrective Action Creation, Synthetic ATT-CA Guard & Heuristic Parity (ATT-8559) [Verified]
- **Tests**: `TST-PRO-118`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md`
- **Rationale**: `qa_audit()` in `tools/jira_util.py` previously queried CA duplicates via deprecated `GET /rest/api/2/search` (`410 Gone` on Cloud), passed invalid `issuetype_id="10005"` instead of `"10008"` (`Verbesserung`), swallowed exceptions into dead `ATT-CA-*` links with a hardcoded host, and triggered false-positive `FAIL`s on valid `agent2` gate comments, standard commit formats, and `REQ-XXX-*` wildcards, while `tools/verify_requirement_governance.py:781` accepted `ATT-CA-*` tokens in live audits.
- **Description**:
  1. *Cloud-Compatible CA Deduplication & Valid Issue Type (`REQ-PRO-118.1`)*: `qa_audit()` SHALL query existing CA tickets via `paged_jql_search()` / `get_search_endpoint(config)` (`/rest/api/3/search/jql` on Cloud, `/rest/api/2/search` on Data Center), create missing CA tickets using `issuetype_id="10008"` (`Verbesserung`), and construct browse URLs from `config['JIRA_URL'].rstrip('/')` rather than hardcoded hosts.
  2. *Fail-Loud CA Error Handling (`REQ-PRO-118.2`)*: `qa_audit()` MUST NOT swallow Jira CA search/create failures or synthesize placeholder `ATT-CA-*` keys (`ATT-CA-<slug>` / `ATT-CA-{key}`); live CA search or creation errors SHALL raise explicitly so audit corruption fails loudly.
  3. *4-Augen Gate Verdict Recognition (`REQ-PRO-118.3`)*: `qa_audit()` SHALL recognize genuine `agent2` / `qa` `accountId` gate review comments matching either explicit `GATE_VERDICT:` / `GATE-VERDICT` / `VERDICT: PASS|REVISION` markers or standard Gate review headers (`Gate <1..5> ... PASS|REVISION|FREIGABE` or `[Automated comment by AI Agent 2 (Auditor)]` with `PASS|REVISION`).
  4. *Commit Hygiene & Living-Docs Wildcard Parity (`REQ-PRO-118.4`)*: `qa_audit()` Commit Hygiene SHALL accept standard squashed/bounce commit formats (`^KEY:`, `[KEY]`, `type(KEY):`, `(KEY)`, and `Ticket: KEY` trailer), and Living Documentation Parity SHALL ignore family-wildcard patterns ending in `*` (e.g., `REQ-FIL-*`) prior to subset evaluation against `master_req_ids`.
  5. *Verifier Synthetic `ATT-CA-*` Rejection (`REQ-PRO-118.5`)*: `tools/verify_requirement_governance.py` (`_validate_single_qa_audit()` / `--check-qa-audits`) SHALL reject synthetic `ATT-CA-*` tokens on post-cutoff/current sprint QA audits (requiring real `ATT-\d+` or `PROJ-\d+` Jira keys), while cleanly handling legacy pre-cutoff audits (including `sprint_2026-11-17_01_früh.md` or re-audited reports) so `--check-qa-audits` passes deterministically (`0`).
  6. *Strict Offline HTTP Mocking (`Rule 5` / `REQ-PRO-118.6`)*: Automated unit tests in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py` MUST mock all Jira HTTP calls (`jira_request` / `get_search_endpoint`) and MUST NEVER create live Jira issues during test execution.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines and fixes `REQ-PRO-091.3` / `REQ-PRO-091.5` / `REQ-PRO-091.6` (`ATT-7558`, `ATT-8152`, `ATT-8153`) targeting `tools/jira_util.py` and `tools/verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: `ATT-8153` introduced `issuetype_id="10005"` (`# Verbesserung`) and added `ATT-CA-*` to `_validate_single_qa_audit()` alongside `test_tst_pro_091_11`'s mock `ATT-CA-100` key.
  3. *Root Reason for Existing Formulation*: `ATT-8153` assumed `"10005"` mapped to `Verbesserung` and allowed `ATT-CA-*` in the verifier regex without separating offline test mocks from live sprint audits.
  4. *Preservation of Core Invariants*: Independent auditor enforcement (`qa`/`agent2`), dynamic `>= max(2, ceil(0.10*N))` sampling, `ca_key != audited_key`, deduplicated raw Backlog creation (`add_to_sprint=False`, `rank_top=True`), and `Rule 5` offline test mocking remain preserved.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a non-compliant live `qa_audit()` run, *When* CA deduplication and creation execute, *Then* `qa_audit()` queries via `get_search_endpoint` / `paged_jql_search`, creates `Verbesserung` (`"10008"`) tickets with `config['JIRA_URL']` browse links, and fails loudly without `ATT-CA-*` fallback on API errors.
  • *Given* tickets with authentic `agent2` `Gate N ... PASS` comments, `[ATT-XXXX]` / `fix(ATT-XXXX):` / `(ATT-XXXX)` commits, and `REQ-FIL-*` wildcards, *When* audited by `qa_audit()`, *Then* 4-Augen Role Separation, Commit Hygiene, and Living Documentation Parity evaluate `PASS`.
  • *Given* `verify_requirement_governance.py --check-qa-audits`, *When* evaluating a post-cutoff non-compliant report containing `ATT-CA-*` vs real `ATT-\d+` keys, *Then* `ATT-CA-*` fails with exit `1` and real/grandfathered reports pass with exit `0`.
- **Invariants**: Zero `app/src/main/` changes; `ANT-Android-SDKs` untouched; zero live Jira tickets created by automated unit tests (`Rule 5`).

---

### REQ-PRO-119: Immutable Annotated Baselines, Content Manifest & CM Plan Alignment (ATT-8163) [Verified]
- **Tests**: `TST-PRO-119`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/baseline_manager.py`, `tools/verify_requirement_governance.py`, `docs/management/cm_plan.md`, `docs/releases/README.md`, `.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/release-manager/SKILL.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
- **Rationale**: ASPICE 4.0 Audit Finding `M11 / SUP.8` (`SUP.8 BP5/BP6/BP7`, `GP 2.2.3`) identified mutable `git tag -f` lightweight tagging in `sprint-planner`, absence of baseline content manifests (`docs/releases/<baseline>/manifest.md`), stale `ff-only` and incomplete CI/status-accounting documentation in `docs/management/cm_plan.md`, and uncurated stale branches/lightweight tags.
- **Description**:
  1. *Immutable Annotated Baseline Tagging (`REQ-PRO-119.1`)*: `tools/baseline_manager.py` (`tag`), `.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`, and `.agents/skills/release-manager/SKILL.md` SHALL create strictly annotated Git tags (`git tag -a <tag> <commit> -m ...`), eliminate all `git tag -f` force-overwrite commands, verify `refs/tags/<tag>` does not already exist (refusing with non-zero exit if present), and reject tag/branch ref collisions where `refs/heads/<tag>` or `refs/remotes/origin/<tag>` exists.
  2. *Baseline Content Manifest (`REQ-PRO-119.2`)*: `tools/baseline_manager.py` (`manifest`) SHALL deterministically generate `docs/releases/<baseline>/manifest.md` at sprint close (Ceremony 2) and release finish recording: baseline identifier/tag, target commit SHA, master living-doc REQ and TST counts (`docs/requirements.md`, `docs/tests.md`), linked `docs/engineering/test_evidence/` artifact path and status, open risks/deviations (`docs/management/risk_register.md` and `verification_summary.md` when present), and `tools/verify_requirement_governance.py` verdict.
  3. *Ceremony 2 Manifest Allowlist Parity (`REQ-PRO-119.3`)*: `tools/verify_requirement_governance.py` (`RETRO_ALLOWLIST_PATTERNS`) SHALL allow `docs/releases/[^/]+/manifest\.md` in autonomous `chore(ceremony2)` commits alongside the existing 7 evidence/log paths while keeping all protected rule/skill/standard/tool paths (`REQ-PRO-117.2`) blocked.
  4. *CM Plan Alignment & SUP.8.BP7 Status Accounting (`REQ-PRO-119.4`)*: `docs/management/cm_plan.md` (updated to `v1.1.0` with a Revision History table) SHALL align merge policy to `--no-ff` merge commits for sprint (`sprint/<SPRINT_NAME>` -> `develop`) and release (`release/<VERSION>` -> `master`) integrations (removing the inaccurate `ff-only` claim), include `docs/engineering/gates/`, `docs/engineering/qa_audits/`, `docs/engineering/reviews/`, and `docs/releases/` in the Configuration Items table, document Configuration Status Accounting & Reporting (`SUP.8.BP7`) referencing `docs/releases/<baseline>/manifest.md`, and preserve all `REQ-STP-009` and `REQ-PRO-057` markers (`master`, no `` `main` ``, `SUP.8.BP1..BP7`); `docs/releases/README.md` SHALL document `manifest.md`.
  5. *Safe Branch & Tag Hygiene Policy (`REQ-PRO-119.5`)*: `docs/management/cm_plan.md` and `tools/baseline_manager.py` (`cleanup-audit`) SHALL define a dry-run-by-default branch/tag hygiene policy reporting merged local branches, stale remote branches, and legacy lightweight tags without mutating refs; any destructive branch deletion or remote cleanup SHALL require `--execute` plus explicit PO confirmation (`--confirm-po`), preserve commit history, never touch `ANT-Android-SDKs`, and never fabricate a pre-release `V5.0.0` tag while `V5.0.0` remains `5.0.0-dev` / `SYS.5 OPEN`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-PRO-057` (`ATT-6733`, CM plan repo grounding), `REQ-STP-009` (`ATT-6152`, CM plan & submodule pinning), and `REQ-PRO-117` (`ATT-8584`, Ceremony 2 allowlist) across `docs/management/cm_plan.md`, skills, and `tools/`.
  2. *Historical Origin & Commit Trace*: `ATT-6152` introduced `cm_plan.md` with boilerplate `ff-only` wording; `ATT-6733` aligned `master` and `sprint/<SPRINT_NAME>`; `ATT-8584` introduced `RETRO_ALLOWLIST_PATTERNS` before baseline manifests existed.
  3. *Root Reason for Existing Formulation*: `sprint-planner` used `git tag -f` for idempotent re-runs without immutability checks, and `cm_plan.md` predated `gates/`, `qa_audits/`, `reviews/`, and `docs/releases/`.
  4. *Preservation of Core Invariants*: Preserves all `[Verified]` `REQ-STP-009`, `REQ-PRO-057`, `REQ-PRO-072`, `REQ-PRO-100`, `REQ-PRO-112`, and `REQ-PRO-117` invariants and contract test markers (`ReleaseCiAndLintConfigContractTest`, `ReleaseManagerSystemEvidenceContractTest`, `AspiceSweAuditContractTest`, `HoechstmassCoreFrameworkContractTest`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* an existing sprint or release tag, *When* Ceremony 2 or release tagging runs via `tools/baseline_manager.py tag`, *Then* it refuses overwrite with a non-zero exit code instead of forcing.
  • *Given* a new sprint or release baseline, *When* baselined via `tools/baseline_manager.py`, *Then* an annotated Git tag (`tag` object) and `docs/releases/<baseline>/manifest.md` referencing commit SHA, REQ/TST counts, test evidence, open risks/deviations, and governance status exist.
  • *Given* `docs/management/cm_plan.md` and `tools/baseline_manager.py cleanup-audit`, *When* audited by contract tests and unit tests, *Then* `--no-ff` policy, `gates/`/`qa_audits/`/`reviews/`/`releases/` CIs, `SUP.8.BP7` status accounting, Revision History, and dry-run/PO-gated cleanup are verified.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `Localization Parity: N/A - no UI strings`; zero unconfirmed remote/history mutations; zero live Jira API calls in unit tests (`Rule 5`).

---

### REQ-PRO-120: Work-Product Version/Status/Approval Headers, Human-Only Approval Guard & Governance Contradiction Reconciliation (ATT-8164) [Verified]
- **Tests**: `TST-PRO-120`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/coding_standards.md`, `docs/management/cm_plan.md`, `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `docs/project_protocol.md`, `docs/management/risk_register.md`, `tools/hoechstmass/`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt`
- **Rationale**: ASPICE 4.0 Audit Finding `GP 2.2` (`GP 2.2.1/2.2.3/2.2.4`, `SUP.8 BP2`) identified missing version, status, approver, and change-history metadata across work products, agent-self-declared `Approved` statuses that never reset on revisions (e.g. `docs/management/cm_plan.md`), 5 governance contradictions (`8 vs 9` core rules, `Rules 1–55 vs 1–84+`, `5 vs 6` stages, `concurrency 2 vs 3`, `Rule 5 vs Rule 83`), and diverged `tools/hoechstmass/` copies.
- **Description**:
  1. *Standardized Work-Product Header (`REQ-PRO-120.1`)*: `docs/coding_standards.md` and `docs/management/cm_plan.md` SHALL define the canonical work-product control header (`Document ID` using `DOC-*` prefix, semver `Version`, `Status` in `{Draft, Reviewed, Approved}` with ADR `Retrospective (Pending PO Sign-Off)` compatibility, `Approver`, and `Change History` table), and that header SHALL be applied across `docs/management/*.md`, `docs/architecture/` (`docs/architecture.md`, `adr_001`..`007`, `integration_strategy.md`, `interfaces.md`, `sensor_units.md`, `detailed_design/*.md`), `docs/system/*.md`, `docs/requirements.md` + 9 domain shards, `docs/tests.md` + 9 domain shards, `.agents/rules/aspice_governance.md`, and root governance docs (`docs/design_guidelines.md`, `docs/project_governance.md`, `docs/project_protocol.md`), resetting agent-self-declared `Approved` statuses to `Reviewed` (`Approver: Pending PO Sign-Off`) without breaking `verify_shard_header_counts` or `verify_architecture_and_system_specs`.
  2. *Human-Only Approval & Version-Bump Verifier (`REQ-PRO-120.2`)*: `tools/verify_requirement_governance.py` (`verify_work_product_headers()`, invoked via `--check-doc-headers` and the default CI `verify_governance()` flow) SHALL validate headers on all governed work products, reject `Status: Approved` when `Approver` is empty, `Pending*`, or an AI agent identifier, and on `git diff` body changes (cleanly exempting pre-cutoff historical commits) MUST require a monotonic semver `Version` bump and reject `Status: Approved` unless the modifying commit carries an explicit human sign-off trailer (`Human-Approved:` or `Interactive-PO:`).
  3. *Governance Contradiction Reconciliation (`REQ-PRO-120.3`)*: Governance documents and `tools/jira_util.py` SHALL consistently state `9 Core Rules` (`.agents/rules/aspice_governance.md`, `docs/project_protocol.md`, `tools/jira_util.py` reminder printing Rules 1–9), `Rules 1–85+` archive span (`docs/project_protocol.md`, `aspice_governance.md`, `docs/engineering/rule_history_archive.md`), `6-Stage` lifecycle (`Stages 1–5` sub-task execution + `Stage 6` Cold Final Review), `max 3` parallel lanes (`2 lanes` active token-budget operational default per `Rule 7.2`), and `Rule 3.4 / Rule 83` in `docs/management/risk_register.md` (correcting the `Rule 5` mislabel).
  4. *`tools/hoechstmass/` Alignment (`REQ-PRO-120.4`)*: `tools/hoechstmass/.agents/rules/aspice_governance.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/stage6-final-review/SKILL.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md` SHALL be synchronized with the reconciled rule counts (`9 Core Rules`, preserving `Rule 1`..`Rule 8`), 6-stage lifecycle phrasing (preserving `Stage 1`..`Stage 5`), and `max 3` / `2 active` lane concurrency policy while keeping `HoechstmassCoreFrameworkContractTest`, `HoechstmassToolingContractTest`, and `ReleaseCiAndLintConfigContractTest` green.
  5. *Offline & Contract Verification (`REQ-PRO-120.5`)*: `tools/test_verify_requirement_governance.py` SHALL verify header checks, version bumps, and human-only `Approved` enforcement using isolated temporary Git repos/mocked diffs (`Rule 5`: zero live Jira API calls), and Kotlin contract tests (`AspiceSweAuditContractTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`, `JavaCoreMigrationStrategyContractTest.kt`) SHALL verify aligned headers and contract markers without reading `app/src/main/` (`REQ-PRO-049.1`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-PRO-035` (`tools/hoechstmass/` rule/stage framing), `REQ-PRO-041` (`concurrency = 3` vs `Rule 7.2` 2-lane token budget), `REQ-PRO-119` (`cm_plan.md` v1.1.0), and `REQ-ARC-005` (`ADR-001` `Approved` status assertion).
  2. *Historical Origin & Commit Trace*: `REQ-PRO-035` (`ATT-3922`, `54f85e4`), `REQ-PRO-041` (`ATT-5914`, `d002a9bd`; `ATT-6743`), `REQ-ARC-005` (`ATT-7566`), `REQ-PRO-119` (`ATT-8163`, `c43cd960`).
  3. *Root Reason for Existing Formulation*: Early templates hardcoded `8 Core Rules` / `5-Stage` / `Rules 1–55` before Rule 9, Stage 6, and Rules 56–85+ were added, and `cm_plan.md` / `ADR-001` contract tests pinned literal strings (`**Version**: 1.1.0`, `Status: Approved`).
  4. *Preservation of Core Invariants*: Preserves `Rule 1`..`Rule 8` and `Stage 1`..`Stage 5` contract markers, `REQ-PRO-117` Ceremony 2 allowlist, `verify_shard_header_counts` 100% parity, and `ADR-002..007` `Retrospective (Pending PO Sign-Off)` compatibility while replacing agent-self-declared `Approved` claims with verifiable human sign-off.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* any governed document under `docs/management/`, `docs/architecture/`, `docs/system/`, `docs/requirements/`, `docs/tests/`, or `.agents/rules/`, *When* inspected by `verify_work_product_headers()`, *Then* it contains `Document ID` (`DOC-*`), semver `Version`, `Status`, `Approver`, and `Change History`, and zero unapproved `Approved` statuses.
  • *Given* a governed document whose body changed in `git diff`, *When* `python3 tools/verify_requirement_governance.py` runs in CI, *Then* it fails unless `Version` bumped monotonically and `Status` reset from `Approved` (or the commit carries `Human-Approved:` / `Interactive-PO:` with a human `Approver`).
  • *Given* `.agents/rules/aspice_governance.md`, `docs/project_protocol.md`, `tools/jira_util.py`, `docs/management/risk_register.md`, `.agents/skills/sprint-planner/SKILL.md`, and `tools/hoechstmass/`, *When* compared, *Then* rule counts (`9 Core Rules`), archive scope (`1–85+`), stage counts (`6 Stages`), concurrency (`max 3`, `2` active default), and `Rule 3.4 / Rule 83` are 100% consistent.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched at `0e8cea71`; `Localization Parity: N/A - no UI strings`; zero live Jira API calls in unit tests (`Rule 5`).

---

### REQ-PRO-121: Problem Resolution Hardening — Bug Trends, Resolution States, Fix Verification, Commit/Conflict Guards & Process-Escape Records (ATT-8165) [Verified]
- **Tests**: `TST-PRO-121`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `.githooks/commit-msg`, `.githooks/pre-commit`, `.github/workflows/android-branch-build.yml`, `docs/project_governance.md`, `.agents/skills/sprint-planner/SKILL.md`
- **Rationale**: ASPICE 4.0 Audit Finding `SUP.9` (`SUP.9 BP3/BP4/BP6`, `ATT-8165`) identified that `docs/engineering/metrics/process_metrics.jsonl` recorded `17` open bugs (`3 Critical`, `0` closed, `0.0%` resolution) in Sprint `443` with no Retro trend/age analysis, bugs lacked `Rejected`/`Duplicate`/`Cannot Reproduce` resolution tracking and pre-`Final Review (Human)` fix verification, hotfixes/fixes (`7e5ff312`, `24ceaf94`, `6a694bb1`) lacked `ATT-` keys, stale conflict markers reached `develop` (`d7bf7698`), and gate/Tier-2 escapes did not generate `process-escape` problem records.
- **Description**:
  1. *Bug Metrics, Open Age & Multi-Sprint Trend Analysis (`REQ-PRO-121.1`)*: `collect_sprint_metrics_for_id()` in `tools/jira_util.py` SHALL fetch `created`, `resolution`, and `resolutiondate` alongside `labels` and `description`. `parse_sprint_gate_metrics()` in `tools/sprint_metrics.py` SHALL classify Bug (`10006`) lifecycle states (`open_bugs`, `closed_bugs` for terminal/resolved bugs, `fixed_awaiting_human` for `Final Review (Human)`/`Final Review`), compute open-bug age statistics (`open_bug_age_days`: `avg`, `max`, `gt_7d` per severity), and emit cross-sprint trend deltas (`bug_trend_history`) from `process_metrics.jsonl` without breaking `REQ-PRO-063.4` schema fields; `format_metrics_markdown()` (`sprint-metrics --post-to-retro`) SHALL render per-severity open/closed/age counts and a multi-sprint Bug Trend table/ASCII chart.
  2. *Bug Resolution States & Fix-Verification Gate (`REQ-PRO-121.2`)*: `tools/jira_util.py` SHALL provide `resolve-bug <KEY> <rejected|duplicate|cannot-reproduce|fixed> [--duplicate-of=KEY] [--reason=...]` supporting `Rejected`, `Duplicate`, `Cannot Reproduce`, and `Fixed` resolutions—setting Jira's `resolution` field when the resolution exists in `/rest/api/3/resolution` (gracefully handling Jira Cloud's current single `id=10000` `Fertig` resolution) while persistently recording `resolution-<state>` labels and structured audit comments, strictly preserving `REQ-PRO-016` (`Erledigt` human-only gate). Advancing a `Bug` (`10006`) to `Final Review (Human)` without a non-fix resolution (`rejected`/`duplicate`/`cannot-reproduce`) MUST require fix-verification evidence (reproduction and regression test verified).
  3. *Commit Hygiene, Conflict-Marker CI Gate & Git Hooks (`REQ-PRO-121.3`)*: `tools/verify_requirement_governance.py` (`verify_conflict_markers()` and `verify_commit_hygiene()`, invoked via CLI flags and the default `verify_governance()` CI path in `.github/workflows/android-branch-build.yml`) SHALL fail with exit code `1` if any tracked file contains Git conflict markers (`^<{7}(?: .*)?$`, `^={7}$`, `^>{7}(?: .*)?$`) or if any post-cutoff non-merge commit on `develop`/`sprint/*` lacks an `ATT-\d+` issue key (cleanly exempting pre-cutoff historical commits `7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1`). Repository hooks `.githooks/commit-msg` and `.githooks/pre-commit` SHALL enforce `ATT-\d+` commit keys and zero conflict markers locally.
  4. *Deduplicated `process-escape` Problem Record Creation (`REQ-PRO-121.4`)*: `tools/jira_util.py` SHALL provide `create_process_escape_bug()` (exposed via `file-process-escape` CLI and optional `gate-verdict REVISION --file-escape-bug`) using Cloud v3 `paged_jql_search` deduplication to create a `Bug` (`10006`) labeled `process-escape` with mandatory `--severity` (`REQ-PRO-063.1`) for gate `REVISION` or Tier-2 CI escapes without duplicates.
  5. *Offline & Isolated Verification (`REQ-PRO-121.5`)*: Unit tests (`tools/test_sprint_metrics.py`, `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`) SHALL validate all behavior offline via `tempfile` Git repos and mocked Jira HTTP APIs (`Rule 5`: zero live Jira mutations).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-121` (`REQ-PRO-121.1`–`REQ-PRO-121.5`), extending `REQ-PRO-063` (*Problem Severity Classification & Defect Trend Metrics*) and `REQ-PRO-092` (*Process Metrics Credibility & Bug Severity Rubric*) across `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `.githooks/`, and `.github/workflows/android-branch-build.yml`.
  2. *Historical Origin & Commit Trace*: `REQ-PRO-063` (`ATT-6156`, Sprint `2026-46.3`) and `REQ-PRO-092` (`ATT-7559`), prompted by ASPICE 4.0 Audit Finding `SUP.9` (`ATT-8165`).
  3. *Root Reason for Existing Formulation*: `REQ-PRO-063`/`REQ-PRO-092` tracked per-sprint bug severity counts but omitted bug age, multi-sprint trends, non-fix resolution states, fix-verification gating, commit/conflict CI enforcement, and `process-escape` bug logging.
  4. *Preservation of Core Invariants*: Preserves `REQ-PRO-063.4` JSONL backward compatibility on existing `process_metrics.jsonl` records, `REQ-PRO-016` Human Decision Gate (`Erledigt` human-only), `REQ-PRO-120` header versioning, and pre-cutoff historical commits (`7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `sprint-metrics --record --post-to-retro` runs during Ceremony 2 across multi-sprint `process_metrics.jsonl` history, *When* rendered via `format_metrics_markdown()`, *Then* the Retro output includes bug counts per severity, open-bug age (`avg`/`max`/`>7d`), and a multi-sprint trend comparison vs previous sprints.
  • *Given* a commit on `develop` post-cutoff lacking an `ATT-\d+` key or a tracked file containing `<<<<<<<`/`=======`/`>>>>>>>` conflict markers, *When* `python3 tools/verify_requirement_governance.py` (or `.githooks/`) runs, *Then* it exits `1` while pre-cutoff historical commits (`7e5ff312`, `d7bf7698`, `24ceaf94`, `6a694bb1`) pass cleanly.
  • *Given* `resolve-bug` or `file-process-escape` / `gate-verdict REVISION --file-escape-bug` executes under mocked Jira Cloud v3 HTTP responses, *When* a Bug lacks a fix-verification check or matches an existing open `process-escape` ticket, *Then* premature `Final Review (Human)` transition is blocked, non-fix resolutions record `resolution-*` metadata cleanly, and `paged_jql_search` prevents duplicate `process-escape` Bugs.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `Localization Parity: N/A - no UI strings`; `REQ-PRO-016` Human Gate preserved; zero live Jira API calls in unit tests (`Rule 5`).

---

### REQ-PRO-122: Quantitative Sprint Metrics Statistical Correctness & Capability Level Claim Suspension (ATT-8168) [Verified]
- **Tests**: `TST-PRO-122`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassCoreFrameworkContractTest.kt`
- **Rationale**: ASPICE 4.0 Audit Finding `Observation (PA 4.x)` (`ATT-8168`) identified unbounded `UCL > 100%` SPC calculations (`115.86%`/`117.93%` on `n=19`), unpartitioned mixed-era history, `human_acceptance_rate_percent` conflated with `completion_rate_percent`, synthetic `p = 0.02` escape probability substitution (`2.37e-08`), Gate 1–5 plausibility warnings masked by Gate 6 bounces, and premature CL3/CL4/CL5 operational claims before CL2 attainment.
- **Description**:
  1. *Bounded SPC Limits & `process_version` Baseline Resets (`REQ-PRO-122.1`)*: `calculate_spc_limits()` in `tools/sprint_metrics.py` SHALL compute binomial p-chart control limits (or sample-bounded `[0.0, 100.0]` limits when denominators are absent) for percentage metrics (`overall_ftpr`, `human_acceptance`) strictly clamped to `[0.0, 100.0]`, and MUST partition `process_metrics.jsonl` history by `process_version` so baseline changes reset control bands without mixing incompatible eras.
  2. *Truthful Human Acceptance vs. Completion Separation (`REQ-PRO-122.2`)*: `parse_sprint_gate_metrics()` SHALL decouple `human_acceptance_rate_percent` from `completion_rate_percent`, counting only terminal human-accepted statuses (`Erledigt`, `Done`, `Closed`) and never counting `Final Review (Human)` or `Final Review (Agent)` as human-accepted.
  3. *Removal of Fabricated `p = 0.02` Escape Floor (`REQ-PRO-122.3`)*: `parse_sprint_gate_metrics()` MUST NOT substitute nominal `p = 0.02` when gate escape rate is zero, reporting strictly empirical escape metrics without fabricated `10^-8`–`10^-11` products.
  4. *Unmasked Gate 1–5 FTPR Plausibility Warning (`REQ-PRO-122.4`)*: `parse_sprint_gate_metrics()` SHALL evaluate `total_revisions_all` strictly over Gates 1–5 (excluding `rev6` and `bounces6`) so 100.0% Gate 1–5 FTPR across $\ge 20$ evaluated subtasks emits `PA 4.1 Plausibility Warning` even when Gate 6 records bounces or revisions.
  5. *Höchstmaß Mirror Parity (`REQ-PRO-122.5`)*: `tools/hoechstmass/tools/sprint_metrics.py` SHALL implement full statistical and plausibility parity with `tools/sprint_metrics.py`.
  6. *Suspension of Unsupported CL3/CL4/CL5 Claims (`REQ-PRO-122.6`)*: `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`, and `sprint_metrics.py` docstrings SHALL mark Capability Level 3, 4, and 5 entries as `[Target / Not Yet Claimed]` pending verified CL2 attainment while preserving exact `"Level 1: Performed"`..`"Level 5: Innovating"` and `"PA 1.1"`..`"PA 5.2"` strings required by `HoechstmassCoreFrameworkContractTest.kt`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-PRO-038` (`ATT-5765`) and `REQ-PRO-092` (`ATT-7559`) across `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md`.
  2. *Historical Origin & Commit Trace*: `REQ-PRO-038` established rapid normal $\mu \pm 2\sigma$ SPC and PA 1.1–5.2 matrices; `REQ-PRO-092` added plausibility warnings and Swiss Cheese escape tracking (`2de2a41c` added `REQ-PRO-120` headers).
  3. *Root Reason for Existing Formulation*: Early SPC used unclamped normal approximation, `0.02` avoided zero-product collapse, and PA 3.x–5.x rows used operational wording to describe roadmap capabilities.
  4. *Preservation of Core Invariants*: Preserves `process_metrics.jsonl` schema compatibility (`verify_sprint_metrics()`), `TST-PRO-107.3` rubber-stamping alert, `HoechstmassCoreFrameworkContractTest` / `AspiceSweAuditContractTest` matrix keys, and `REQ-PRO-120` header/version governance.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `process_metrics.jsonl` history spanning multiple `process_version` eras and high FTPR variance, *When* `calculate_spc_limits()` runs, *Then* percentage `lcl`/`ucl` remain within `[0.0, 100.0]` and limits compute only within the active `process_version` baseline.
  • *Given* sprint tickets in `Final Review (Human)` and `Final Review (Agent)` alongside `Erledigt` tickets, *When* `parse_sprint_gate_metrics()` runs, *Then* `human_acceptance_rate_percent` reflects only terminal human-accepted tickets while `completion_rate_percent` remains independent.
  • *Given* zero Gate 1–5 revisions across $\ge 20$ subtasks with Gate 6 bounces and `p_g == 0`, *When* `parse_sprint_gate_metrics()` runs, *Then* no `0.02` floor is injected and `plausibility_warnings` is non-empty.
  • *Given* `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md`, *When* `HoechstmassCoreFrameworkContractTest` runs, *Then* CL3–CL5 claims are explicitly `[Target / Not Yet Claimed]` and all contract tests pass.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `Localization Parity: N/A - no UI strings`; backward-compatible JSONL schema; zero live Jira API calls in unit tests (`Rule 5`).

---

### REQ-PRO-123: CI Custom Safety Guard Gating, Baseline Ratcheting & Java Static Analysis Evaluation (ATT-8169) [Verified]
- **Tests**: `TST-PRO-123`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, `tools/lint_safe_enum.py`, `tools/check_static_analysis_baselines.py`, `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `docs/management/qa_plan.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
- **Rationale**: ASPICE 4.0 Audit Finding `Minor (SWE.4)` (`SWE.4 BP1/BP3`) identified orphaned custom Python safety linters in `tools/`, static baseline ceilings (`<= 9550` Detekt vs 9,516 actual; `<= 239` Lint vs 238 actual) without base-ref ratcheting or sprint metrics reporting, and missing static-analysis tooling evaluation for the 120 retained Java files referenced in `ADR-001`.
- **Description**:
  1. *CI Custom Safety Guard Gating (`REQ-PRO-123.1`)*: `.github/workflows/android-branch-build.yml` (with `.github/workflows/android-release.yml` parity) SHALL invoke `tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, and `tools/lint_safe_enum.py` prior to `:app:assembleDebug` / `:app:assembleRelease` and MUST fail the build on any non-zero exit code (any new violation).
  2. *Base-Ref Baseline Ratcheting & Sprint Metrics Reporting (`REQ-PRO-123.2`)*: A deterministic baseline ratchet helper in `tools/` invoked in CI SHALL parse `<ID>` entries in `app/detekt-baseline.xml` (`9,516`) and `<issue>` entries in `app/lint-baseline.xml` (`238`), MUST fail the build if either count increases above its base-ref count or ratcheted ceiling (`9516` / `238`), and `tools/sprint_metrics.py` + `tools/hoechstmass/tools/sprint_metrics.py` SHALL report current Detekt/Lint baseline counts and base-ref/sprint deltas.
  3. *CI & Lint Contract Preservation (`REQ-PRO-123.3`)*: Existing assertions in `ReleaseCiAndLintConfigContractTest.kt` (`./gradlew :app:lintDebug`, `./gradlew :app:detekt`, `verify_requirement_governance.py` before `:app:assembleDebug`, baseline bounds) SHALL remain intact alongside new contract assertions for CI safety-guard and ratchet steps plus offline Python unit tests.
  4. *Java Static Analysis Evaluation & Burn-Down Plan (`REQ-PRO-123.4`)*: Engineering documentation outside architecture lock (referencing `ADR-001` / `REQ-ARC-005`) SHALL evaluate **PMD vs. SpotBugs vs. Error Prone** for the remaining `120` `.java` files on Gradle `9.6.0` / AGP `9.4.0` / JDK `21` (Java `17` target) and define an incremental Detekt/Lint baseline burn-down schedule.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `.github/workflows/android-branch-build.yml` and `android-release.yml`, *When* `tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, and `tools/lint_safe_enum.py` execute, *Then* clean `HEAD` exits `0` and any violation fails CI prior to assembly.
  • *Given* `app/detekt-baseline.xml` (`9,516`) and `app/lint-baseline.xml` (`238`), *When* a change increases baseline entry counts vs base ref or ceiling, *Then* the ratchet fails CI while equal or reduced counts pass and `sprint_metrics.py` + mirror report exact counts and deltas.
  • *Given* `ReleaseCiAndLintConfigContractTest.kt` and `verify_requirement_governance.py`, *When* executed, *Then* preserved pins plus new CI safety-linter/ratchet assertions pass and governance exits `0`.
  • *Given* the Java tooling evaluation referencing `ADR-001`, *When* reviewed, *Then* PMD, SpotBugs, and Error Prone trade-offs on AGP 9.4 / JDK 21 and the Detekt/Lint burn-down schedule are documented.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `Localization Parity: N/A - no UI strings`; existing `ReleaseCiAndLintConfigContractTest` pins preserved.

---

### REQ-PRO-124: Secret Hygiene, Local Credential File Permissions & CI Secret-Pattern Scanning (ATT-8170) [Verified]
- **Tests**: `TST-PRO-124`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/check_secrets_hygiene.py`, `.gitignore`, `docs/ci-secrets.md`, `docs/engineering/walkthroughs/ATT-4004_walkthrough.md`, `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
- **Rationale**: ASPICE 4.0 Audit Finding `Minor (SUP.8)` (`SUP.8 BP2/BP3`, `ATT-8170`) identified overly permissive local Unix file modes (`0644`/`0664` instead of `0600`) on credential/signing files, missing `.jira_audit.log` entry in `.gitignore`, absence of an automated CI secret-pattern scan on tracked files, and concrete `BuildConfig.java` credentials leaked in `docs/engineering/walkthroughs/ATT-4004_walkthrough.md:L78-L81`.
- **Description**:
  1. *Local Credential File Permission Hygiene (`REQ-PRO-124.1`)*: Local untracked credential and signing files (`.env.claude`, `.env.gemini`, `secrets.properties`, `release.properties`, `.env.jira*`) MUST be restricted to owner-only `0600` mode on developer workstations, verified deterministically via `tools/check_secrets_hygiene.py --check-local-perms` (with `--fix-local-perms` remediation helper) and documented in `docs/ci-secrets.md` (preserving `REQ-PRO-120` header governance where applicable).
  2. *Git Ignore & Tracked Leak Redaction (`REQ-PRO-124.2`)*: `.gitignore` SHALL exclude `.jira_audit.log`. Exposed `BuildConfig.java` credentials in `docs/engineering/walkthroughs/ATT-4004_walkthrough.md` (`L78-L81`) SHALL be redacted to non-sensitive placeholders with an explicit PO key-rotation notice for the exposed Google Maps, Strava, and Dropbox keys.
  3. *Deterministic Offline CI Secret-Pattern Scanning (`REQ-PRO-124.3`)*: `tools/check_secrets_hygiene.py` SHALL execute an offline stdlib Python secret-pattern scan across tracked files (`git ls-files`), wired into `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml` prior to assembly, failing non-zero on real API keys/tokens/private keys while explicitly allowlisting documented build placeholders (`secrets.defaults.properties`, `app/build.gradle`, `docs/engineering/plans/ATT-4004_plan.md`) and explicit unit test fixtures (`StravaHelperTest.kt`, `tools/test_jira_accounts.py`).
  4. *Offline Unit & CI Contract Verification (`REQ-PRO-124.4`)*: Offline Python unit tests (`tools/test_check_secrets_hygiene.py`, `Rule 5`) and `ReleaseCiAndLintConfigContractTest` (`REQ-PRO-049.1`: zero `app/src/main/` reads) SHALL verify secret-pattern detection, allowlist precision, `0600` permission checking/remediation, and CI workflow wiring alongside preserved `REQ-SEC-042`, `REQ-STP-008`, and `REQ-PRO-123` assertions.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-124` (`REQ-PRO-124.1`–`REQ-PRO-124.4`), extending `REQ-SEC-042` (`secrets.defaults.properties` placeholders), `REQ-STP-008` (`release.properties` signing hygiene), and `REQ-PRO-123` (CI Python safety guards).
  2. *Historical Origin & Commit Trace*: `REQ-SEC-042` (`ATT-4004`), `REQ-STP-008` (`ATT-6148`), and `REQ-PRO-123` (`ATT-8169`).
  3. *Root Reason for Existing Formulation*: Existing controls secured `BuildConfig` defaults and signing password resolution, but did not enforce `0600` local file permissions, ignore `.jira_audit.log`, or scan tracked repo files in CI (allowing `ATT-4004_walkthrough.md:L78-L81` to persist unnoticed).
  4. *Preservation of Core Invariants*: Preserves `secrets.defaults.properties` (`REQ-SEC-042`), `BuildConfigSecretsContractTest`, `ReleaseCiAndLintConfigContractTest` pins, and `REQ-PRO-049.1`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* local credential files (`.env.claude`, `.env.gemini`, `secrets.properties`, `release.properties`, `.env.jira*`) with group/world permissions (`0644`/`0664`), *When* `tools/check_secrets_hygiene.py --check-local-perms` runs, *Then* it exits non-zero reporting each file, and after `--fix-local-perms` all files have `0600` and `--check-local-perms` exits `0`.
  • *Given* tracked files including `secrets.defaults.properties`, `app/build.gradle`, `StravaHelperTest.kt`, `tools/test_jira_accounts.py`, and redacted `ATT-4004_walkthrough.md`, *When* `tools/check_secrets_hygiene.py` executes in CI, *Then* clean tracked files exit `0` while any unallowlisted API key/token/private key fails CI with exit code `1`.
  • *Given* `.gitignore`, `docs/ci-secrets.md`, CI workflows, and `ReleaseCiAndLintConfigContractTest`, *When* `ReleaseCiAndLintConfigContractTest` and `verify_requirement_governance.py` execute, *Then* `.jira_audit.log` is ignored, CI invokes `check_secrets_hygiene.py` before assembly, and all contract/governance checks exit `0`.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `secrets.defaults.properties` untouched (`REQ-SEC-042`); `Localization Parity: N/A - no UI strings`; `Rule 5` zero live API calls in tests; `REQ-PRO-049.1` zero `app/src/main/` reads in tests.

---

### REQ-PRO-125: Single-Atomic-Commit Sprint Integration Post-Stage-6 & Subtask Reuse on Bounce (ATT-8207) [Verified]
- **Tests**: `TST-PRO-125`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `.agents/rules/aspice_governance.md`, `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/stage6-final-review/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, `docs/management/cm_plan.md`, `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, `tools/locked_build_merge.py`, `tools/sprint_metrics.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/parallel_sprint_lock.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `tools/hoechstmass/.agents/`
- **Rationale**: Squashing `feature/<KEY>` onto `sprint/<SPRINT_NAME>` and deleting `feature/<KEY>` at Gate 5 `PASS` *before* Stage 6 Cold Review (`Rule 3.3`) pollutes `sprint/<SPRINT_NAME>` with multiple unverified commits whenever Stage 6 bounces (`ATT-7991`, `ATT-7902`), interleaves behind sibling lane commits, breaks `tools/sprint_bisect.py`, and leaves `[Implementation]`/`[Test]` subtasks `Erledigt` so healing creates duplicate subtask pairs (`ATT-8207`, ASPICE `SUP.8 BP4/BP5`, `SUP.1 BP3`).
- **Description**:
  1. *Deferred Post-Stage-6 Atomic Sprint Integration (`REQ-PRO-125.1`)*: Gate 5 `PASS` SHALL keep `feature/<KEY>` intact without executing `--merge-ticket` or `git merge --squash` onto `sprint/<SPRINT_NAME>`, and SHALL transition the parent ticket to `Final Review (Agent)`. `tools/parallel_sprint_lock.py --merge-ticket` (and `tools/locked_build_merge.py`) SHALL squash-merge `feature/<KEY>` onto `sprint/<SPRINT_NAME>` as strictly one atomic commit (preserving `REQ-PRO-108.3` trailers and `--sync-indexes` recalculation) and delete `feature/<KEY>` ONLY after Stage 6 `final-review-verdict PASS`, guaranteeing zero unapproved intermediate commits on `sprint/<SPRINT_NAME>` across `BOUNCE` loops and `Schicht` rollovers (`Rule 2.2`, `Rule 8.2`).
  2. *Zero-Build / Zero-Lock Stage 6 Branch-Diff Audit (`REQ-PRO-125.2`, Rule 3.3)*: During Stage 6 (`Final Review (Agent)`), the Cold Reviewer (`stage6-final-review/SKILL.md`) SHALL audit the candidate ticket strictly read-only via `git diff sprint/<SPRINT_NAME>...feature/<KEY>` (and full surrounding source files in the ticket worktree) with **zero `./gradlew` invocations and zero `/tmp/att_build_merge.lock` acquisitions** during the review itself. Post-`PASS` `--merge-ticket` under POSIX `flock` SHALL cleanly rebase `feature/<KEY>` onto current `sprint/<SPRINT_NAME>` (auto-recovering conflicts strictly on `docs/requirements.md` / `docs/tests.md`, failing fast on dirty unrelated worktrees) and recalculate shared master indexes via `--sync-indexes` if `sprint/<SPRINT_NAME>` advanced while waiting in Stage 6.
  3. *Subtask Reuse & Locked-`Erledigt` Bounce Fallback (`REQ-PRO-125.3`)*: When `tools/jira_util.py final-review-verdict <PARENT_KEY> BOUNCE ...` returns a parent ticket to `Implementation` (or `Analysis` / `Zu erledigen` with `bounce_count <= 3`), it SHALL reopen existing `Erledigt`/`Done` stage subtasks matching the target and downstream stages (e.g. `[Implementation]` and `[Test]` for `implementation`) when Jira exposes outgoing reopen transitions, and skip gracefully when `Erledigt` has `transitions: []`. `submit-stage` SHALL reuse active or reopenable subtasks without duplication, or create a clearly labeled `[<Prefix> - Bounce N]` subtask when `Erledigt` exposes `transitions: []` (with `STAGE_SPECS`, `resolve_desired_parent_stage_for_subtask`, `gate_verdict`, `create_subtask`, and `sprint_metrics.identify_gate_number` matching bounce suffixes and `REQ-PRO-107.1` inspecting the active/latest preceding subtask).
  4. *`tools/hoechstmass/` Mirror Parity & Governance Alignment (`REQ-PRO-125.4`)*: `tools/hoechstmass/tools/{jira_util.py,parallel_sprint_lock.py,sprint_metrics.py}` and `tools/hoechstmass/.agents/{rules,skills}/` SHALL mirror post-Stage-6 atomic integration and `BOUNCE` subtask reuse/fallback without hardcoding `ATT` or proprietary IDs (`HoechstmassToolingContractTest`), and `.agents/rules/aspice_governance.md`, `.agents/skills/{stage5-verification,stage6-final-review,sprint-planner}/SKILL.md`, and `docs/management/cm_plan.md` SHALL reflect the unified workflow.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-125` (`REQ-PRO-125.1`–`REQ-PRO-125.4`), refining integration timing in `REQ-PRO-058` (`REQ-PRO-058.1`–`.3`) and `REQ-PRO-041` (`.2`, `.4`) while preserving `REQ-PRO-109` (`.2`, `.5`).
  2. *Historical Origin & Commit Trace*: `REQ-PRO-058` (`ATT-7101`), `REQ-PRO-041` (`ATT-7025` `49936dce`), `ATT-8030` (Zero-Build/Zero-Lock Stage 6), `REQ-PRO-109` (`ATT-8154`), and `ATT-8163` (`cm_plan.md`).
  3. *Root Reason for Existing Formulation*: Stage 6 was appended after an existing Gate 5 squash-merge pipeline, leaving `sprint/<SPRINT_NAME>` exposed to post-merge bounce commits and duplicate subtasks.
  4. *Preservation of Core Invariants*: Preserves `Rule 3.1` (human-only `Erledigt`), `Rule 3.3` (zero `./gradlew` and zero `flock` during Stage 6 audit), `Rule 8.2` (one atomic squashed commit with `REQ-PRO-108.3` trailers), `Rule 9.1`–`9.5` (disjoint worktrees and `--sync-indexes`), `REQ-PRO-107.1` authorship separation, and `HoechstmassToolingContractTest` / `AspiceSweAuditContractTest` contracts.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a ticket passing Gate 5 (`gate-verdict <TEST_SUBTASK> PASS`), *When* it enters `Final Review (Agent)`, *Then* `feature/<KEY>` remains intact, `sprint/<SPRINT_NAME>` has zero commits for `<KEY>`, and Stage 6 audits `git diff sprint/<SPRINT_NAME>...feature/<KEY>` strictly read-only without `./gradlew` or `flock`.
  • *Given* a ticket in `Final Review (Agent)` that triggers `final-review-verdict <KEY> BOUNCE` (1–3 times), *When* healing re-runs Stage 4/5 and Stage 6 subsequently passes (`PASS`) and runs `--merge-ticket`, *Then* existing `[Implementation]`/`[Test]` subtasks were reused without duplication and `git log sprint/<SPRINT_NAME>` contains strictly one squashed atomic commit for `<KEY>`.
  • *Given* `sprint/<SPRINT_NAME>` advanced with sibling ticket index updates while `feature/<KEY>` waited in Stage 6, *When* post-`PASS` `--merge-ticket` runs under `flock`, *Then* `feature/<KEY>` rebases cleanly, `--sync-indexes` reconciles `docs/requirements.md` and `docs/tests.md`, and `feature/<KEY>` is deleted.
  • *Given* `tools/hoechstmass/` mirrors and governance docs/skills, *When* offline Python tests and governance/contract checks execute, *Then* mirror parity, project-agnostic decoupling, and governance checks pass `0`.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `tools/sprint_bisect.py` algorithm unchanged; `Localization Parity: N/A - no UI strings`; `Rule 5` zero live API calls in tests; `REQ-PRO-049.1` zero `app/src/main/` reads in tests.

---

### REQ-PRO-126: Source-Text Reading Elimination in SensorTypeUnitsContractTest & Governance Javadoc Enforcement (ATT-8631) [Verified]
- **Tests**: `TST-PRO-126`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Rationale**: `SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders` (`REQ-PRO-049.1`) failed on `SensorTypeUnitsContractTest.kt` (`:50, 138, 140` reading `SensorType.java` for per-constant Javadoc checks per `REQ-TRK-083.2`/`REQ-TRK-083.3`), while `AdvancedTuningZoneDisplayTest.kt` (`:310`) had already been deleted upstream in `ATT-8498` (`d7dec447`). Migrating static `SensorType.java` Javadoc enforcement into `tools/verify_requirement_governance.py` (`verify_sensor_type_javadocs()`) eliminates the sole remaining unallowlisted `app/src/main/` test reader without expanding `LEGITIMATE_GOVERNANCE_GUARDS` (`22`) or `app/source-reading-baseline.txt` (`105`) (`ATT-8631`, Epic `ATT-8138`).
- **Description**:
  1. *Zero `app/src/main/` Source Reads in `SensorTypeUnitsContractTest.kt` (`REQ-PRO-126.1`)*: `SensorTypeUnitsContractTest.kt` SHALL NOT reference `app/src/main/` paths or invoke source-reading APIs (`.readText()`, `readLines()`, `readString()`, `useLines()`, `bufferedReader()`), while preserving JVM reflection verification of all `SensorType.values()` (`getInternalUnit()`, `SensorCategory`, `SensorSubCategory`, `SensorValueType`) and bidirectional parity with `docs/architecture/sensor_units.md` and `docs/architecture.md` (`REQ-TRK-083.1`, `REQ-TRK-083.3`).
  2. *Static `SensorType.java` Javadoc Enforcement in Governance Tooling (`REQ-PRO-126.2`)*: `tools/verify_requirement_governance.py` SHALL implement `verify_sensor_type_javadocs()` (executed by `verify_governance()`) to parse `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java` and fail non-zero if any enum constant lacks a preceding `/** ... */` Javadoc block documenting `"Internal unit:"` and its declared `internalUnit` literal (cross-checked against `docs/architecture/sensor_units.md`), migrating static source Javadoc verification out of JUnit (`REQ-TRK-083.3`, `TST-TRK-083.1`).
  3. *Preservation of `REQ-PRO-049` Guard & Baseline Ratchets (`REQ-PRO-126.3`)*: `SourceTextReadingAuditContractTest.LEGITIMATE_GOVERNANCE_GUARDS` MUST remain strictly capped at `22` (`REQ-PRO-049.2`) and `app/source-reading-baseline.txt` at `105` with `0` stale entries (`REQ-PRO-049.3`), passing `4/4` tests in `SourceTextReadingAuditContractTest`.
  4. *Offline Governance Unit Testing (`REQ-PRO-126.4`)*: `tools/test_verify_requirement_governance.py` SHALL verify `verify_sensor_type_javadocs()` offline using isolated `tempfile` fixtures covering pass cases, missing Javadoc blocks, missing `"Internal unit:"` or mismatched `internalUnit` tokens, and live repository verification (`Rule 5`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-126` (`REQ-PRO-126.1`–`REQ-PRO-126.4`), refining `REQ-PRO-049` (`ATT-6186`, `ATT-6731`, `ATT-7525`) and migrating static `SensorType.java` Javadoc verification from `REQ-TRK-083.3` / `TST-TRK-083.1` (`ATT-8579`) across `SensorTypeUnitsContractTest.kt`, `tools/verify_requirement_governance.py`, and `tools/test_verify_requirement_governance.py`.
  2. *Historical Origin & Commit Trace*: `REQ-PRO-049` (`c91e62b6`, `6ec52d7b`, `97133154`) banned unallowlisted `src/main` test reads; `ATT-8498` (`d7dec447`) deleted `AdvancedTuningZoneDisplayTest.kt`; `ATT-8579` (`367a412f`) added `SensorTypeUnitsContractTest.kt` reading `SensorType.java`.
  3. *Root Reason for Existing Formulation*: `ATT-8579` co-located static `SensorType.java` Javadoc regex parsing inside the JUnit reflection test `SensorTypeUnitsContractTest.kt` without registering it in `LEGITIMATE_GOVERNANCE_GUARDS` (`22`).
  4. *Preservation of Core Invariants*: Preserves `REQ-PRO-049.2` (`22` guards), `REQ-PRO-049.3` (`105` baseline, `0` stale), `SensorType.java` `ordinal()`/`name()`/`getInternalUnit()` runtime behavior, `docs/architecture/sensor_units.md` bidirectional parity, and continuous per-constant Javadoc verification inside `verify_requirement_governance.py`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `SensorTypeUnitsContractTest.kt` and `app/src/test/java/`, *When* `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest --tests com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest` runs, *Then* `SourceTextReadingAuditContractTest` passes `4/4` (`22` guards, `105` baseline, `0` unallowlisted) and `SensorTypeUnitsContractTest` passes `2/2` via JVM reflection and `docs/architecture/`.
  • *Given* synthetic `SensorType.java` `tempfile` fixtures (valid, missing Javadoc, or missing/mismatched `"Internal unit:"`) and the live repository, *When* `verify_sensor_type_javadocs()` and `python3 tools/verify_requirement_governance.py` execute, *Then* invalid fixtures fail deterministically and live repository checks exit `0`.
- **Invariants**: Zero `app/src/main/` production edits; `SensorType.java` `ordinal()`/`name()`/`getInternalUnit()` unchanged; `LEGITIMATE_GOVERNANCE_GUARDS` == `22`; `app/source-reading-baseline.txt` == `105`; `Localization Parity: N/A - no UI strings`; zero live network/Jira calls in unit tests (`Rule 5`).

---

### REQ-PRO-127: Symmetric REQ-TST Edge Parity, Test Tag Scanner Verification & Oversized Shard Splitting (ATT-8206) [Verified]
- **Tests**: `TST-PRO-127`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/verify_requirement_governance.py`, `tools/shard_living_docs.py`, `tools/test_verify_requirement_governance.py`, `tools/test_shard_living_docs.py`, `docs/requirements.md`, `docs/tests.md`, `docs/requirements/*.md`, `docs/tests/*.md`, `docs/architecture.md`, `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemRequirementsSpecificationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`
- **Rationale**: `find_traceability_orphans()` (`REQ-PRO-087`) checked only set coverage (`known_req_ids - cited_req_ids`, tolerating `15` baseline orphans in `tools/traceability_baseline.json`) while silently missing `37` `TST->REQ` and `35` `REQ->TST` asymmetric edge mismatches; meanwhile 5 living-doc shards exceed `REQ-PRO-093.3`'s `4,000`-line cap (`req_ui_design_system.md` at `17,599` lines), master indexes omit active prefixes (`RAD`, `HRV`, `WKO`, `CLM`), and CI lacks `--check-size` enforcement (`ATT-8206`, Epic `ATT-8138`).
- **Description**:
  1. *Symmetric `REQ <-> TST` Edge Equality (`REQ-PRO-127.1`, refining `REQ-PRO-087`)*: `tools/verify_requirement_governance.py` (`find_traceability_orphans()` / `verify_traceability_orphans()`) SHALL validate symmetric edge equality between `REQ.Tests` citations and `TST.Verifies`/`Requirement(s):` citations (`(req, tst) in req_to_tsts <=> (req, tst) in tst_to_reqs`), rejecting asymmetric links outside `tools/traceability_baseline.json` (`15` tolerated legacy orphans, strictly non-increasing $|B_{t+1}| \le |B_t|$), and all `37` `TST->REQ` + `35` `REQ->TST` legacy mismatches across living shards SHALL be reconciled to `0` (`--coverage` Cat 4 `100%`).
  2. *`androidTest/` & `tools/test_*.py` Tag Scanner Verification (`REQ-PRO-127.2`, refining `REQ-PRO-071`)*: `extract_test_code_tags()` (`tools/verify_requirement_governance.py:2871-2964`) SHALL preserve scanning of `app/src/test/`, `app/src/androidTest/`, and `tools/test_*.py` (validating `L2954-2957` prefix allowlists and `5` Cat-3 tag hygiene), backed by unit tests in `tools/test_verify_requirement_governance.py`.
  3. *Oversized Shard Splitting, Dynamic Headers & Contract Alignment (`REQ-PRO-127.3`, refining `REQ-PRO-093.3/.4`, `REQ-PRO-120`)*: `tools/shard_living_docs.py` SHALL dynamically discover all `docs/{requirements,tests}/*.md` shards (including `*_partN.md`) and emit compliant `DOC-*` IDs, semver `Version`, `Status`, `Approver`, and `Change History` on every split part; all `5` shards exceeding `4,000` lines SHALL be split `<= 4,000` lines with `100%` ID uniqueness; `docs/requirements.md` & `docs/tests.md` (and `docs/architecture.md` Sec 2) SHALL list all sub-shards and missing prefixes (`RAD`, `HRV`, `WKO`, `CLM`, pruning unused `PERF`); and `SystemRequirementsSpecificationContractTest.kt`, `NonFunctionalRequirementsContractTest.kt`, and `SystemArchitectureDesignContractTest.kt` SHALL resolve across `req_process_stability*.md` / `tst_process_stability*.md` so `AspiceSweAuditContractTest` & NFR tests pass `100%`.
  4. *CI `--check-size --strict` Gate (`REQ-PRO-127.4`, refining `REQ-PRO-090`, `REQ-PRO-093.3`)*: `tools/verify_requirement_governance.py` SHALL support `--check-size`, and `.github/workflows/android-branch-build.yml` & `.github/workflows/android-release.yml` SHALL enforce `python3 tools/shard_living_docs.py --check-size --strict` (limit `4000`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-127` (`REQ-PRO-127.1`–`.4`), refining `REQ-PRO-071`, `REQ-PRO-087` (`ATT-7561`), `REQ-PRO-093.3/.4` (`ATT-7565`), `REQ-PRO-120` (`ATT-8164`).
  2. *Historical Origin & Commit Trace*: `ATT-7561` (`traceability_baseline.json`, `15` orphans), `ATT-7565` (`DEFAULT_MAX_SHARD_LINES = 4000`), `ATT-8143` (`f9d9b9e5`, added `androidTest` + `tools/test_*.py` scanning), `ATT-8631` (`a3508e6b`).
  3. *Root Reason for Existing Formulation*: One-way set subtraction and a static 9-shard list prevented immediate breakage during legacy migration while growing shards past `4,000` lines.
  4. *Preservation of Core Invariants*: Preserves `tools/traceability_baseline.json` (`15` tolerated legacy orphans, $|B_{t+1}| \le |B_t|$), `REQ-PRO-120` headers on every sub-shard, `100%` ID uniqueness/master-table parity (`AspiceSweAuditContractTest`), and zero `app/src/main/` edits.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* living shards with symmetric `REQ<->TST` citations and an injected asymmetric `REQ->TST` or `TST->REQ` mismatch outside `tools/traceability_baseline.json`, *When* `verify_traceability_orphans()` and `--coverage` execute, *Then* the asymmetric fixture fails deterministically while live repo yields `0` Cat-4 gaps and `15` tolerated baseline orphans.
  • *Given* `tools/shard_living_docs.py --check-size --strict`, `AspiceSweAuditContractTest`, `NonFunctionalRequirementsContractTest`, `SystemRequirementsSpecificationContractTest`, and `SystemArchitectureDesignContractTest`, *When* executed post-split, *Then* all living shards are `<= 4,000` lines, every sub-shard carries unique `DOC-*` + `Change History`, and all governance & contract tests pass `100%`.
- **Invariants**: Zero `app/src/main/` production edits; `tools/traceability_baseline.json` never exceeds `15` orphans; `100%` `REQ`/`TST` ID uniqueness; `Localization Parity: N/A - no UI strings`; Human Decision Gate preserved.

---

### REQ-PRO-128: Credible Verdict Parsing, Additive End-to-End FTPR, Risk & Bug Status Parity, and Gate Subtask Closure Guard (ATT-8205) [Verified]
- **Tests**: `TST-PRO-128`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py`
- **Rationale**: ASPICE 4.0 Gap #8 (`SUP.1 BP3/BP4`, `SUP.9 BP7`, `PA 4.1`) identified 5 parser and workflow-guard distortions across `tools/sprint_metrics.py`, `tools/jira_util.py`, and `tools/hoechstmass/tools/sprint_metrics.py`: missing verdict synonyms (`APPROVED`/`FREIGABE`/`REWORK`/`FAIL`), `extract_sprint_gate_records()` lacking ADF extraction and fabricating `PASS` on zero-comment subtasks, Gate 6 excluded from an end-to-end FTPR view, `risk_distribution` flattened to `100% Medium`, missing German/English terminal bug statuses (`abgelehnt`/`rejected`/`geschlossen`/`resolved`) + Höchstmaß bug-status parity, and silent zero-comment stage subtask closure via `jira_util.py move` (`ATT-8205`, Epic `ATT-8138`).
- **Description**:
  1. *Extended Verdict Keywords, ADF Extraction & Zero-Comment `unverified` Parity (`REQ-PRO-128.1`)*: `parse_verdicts_from_comments()` (`tools/sprint_metrics.py`) and `extract_sprint_gate_records()` (`tools/jira_util.py`) SHALL parse both `PASS`/`APPROVED`/`FREIGABE` (pass) and `REVISION`/`REWORK`/`FAIL` (revision) across plain-text and Jira Cloud v3 ADF comment bodies (`extract_text_from_comment_body()`), SHALL NOT default untagged comments from non-reviewer authors to `agent2`, and SHALL segregate zero-comment closed stage subtasks (`Erledigt`/`Closed`/`Done`) as `unverified` (`REQ-PRO-092.2/.3`) rather than fabricating `passed_first_time += 1` or synthetic `PASS` records.
  2. *Additive End-to-End FTPR Preserving Gate 1–5 `overall_ftpr_percent` (`REQ-PRO-128.2`)*: `parse_sprint_gate_metrics()` (`tools/sprint_metrics.py`) SHALL expose an additive `end_to_end_ftpr_percent` field in `quality_gates` (and `format_metrics_markdown()`) computed across Gates 1–6 (`sum(pft[1..6]) / sum(total[1..6]) * 100.0`), while strictly preserving `overall_ftpr_percent` over Gates 1–5 (`range(1, 6)`) so `plausibility_warnings` (`REQ-PRO-122.4`) and multi-sprint rubber-stamping alerts (`REQ-PRO-107.3`) remain unmasked by Gate 6 bounces.
  3. *Multi-Signal Risk Distribution Classification (`REQ-PRO-128.3`)*: `parse_sprint_gate_metrics()` SHALL derive `risk_distribution` (`Low`/`Medium`/`High`/`Highest`) from `severity-*` labels (`severity-critical`→`Highest`, `severity-major`→`High`, `severity-minor`→`Medium`, `severity-cosmetic`→`Low`), summary tags (`[Highest]`, `[High]`, `[Low]`), and Stage 1 `**Risk Rating**` / `Risk Rating:` metadata in description/analysis artifacts before falling back to `fields.priority.name`.
  4. *Extended Bug & Terminal Status Mapping + Höchstmaß Parity (`REQ-PRO-128.4`)*: `parse_sprint_gate_metrics()` in `tools/sprint_metrics.py` and `tools/hoechstmass/tools/sprint_metrics.py` SHALL recognize German/English terminal statuses (`erledigt`, `closed`, `done`, `abgelehnt`, `rejected`, `geschlossen`, `resolved`) alongside `fixed_awaiting_human` (`Final Review (Human)` / `Final Review`) and `resolution_rate_percent` parity (`REQ-PRO-121.1`).
  5. *Zero-Comment Stage Subtask Closure Guard (`REQ-PRO-128.5`)*: `transition_issue()` (`tools/jira_util.py`, invoked by `move`) SHALL block transitions of stage subtasks (`identify_gate_number(issue_summary) is not None`) to `freigabe`/`Erledigt`/`done` unless an `agent2` `PASS` verdict comment exists on the subtask, while preserving transitions on summary-less or non-stage subtasks (`tools/test_jira_accounts.py:312`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-128` (`REQ-PRO-128.1`–`REQ-PRO-128.5`), refining `REQ-PRO-059.2` (`ATT-6149`), `REQ-PRO-092.2/.3` (`ATT-7559`), `REQ-PRO-107.3` (`ATT-8164`), `REQ-PRO-121.1` (`ATT-8165`), and `REQ-PRO-122.4` (`ATT-8168`).
  2. *Historical Origin & Commit Trace*: `ATT-7559` (`sprint_metrics.py` `unverified`), `ATT-6149` (`extract_sprint_gate_records()` fallback), `c50cbf25` (`ATT-8165` `fixed_awaiting_human`), and `459f5eb5` (`ATT-8168` Gate 1–5 plausibility lock).
  3. *Root Reason for Existing Formulation*: `REQ-PRO-122.4` and `REQ-PRO-107.3` isolated Gates 1–5 FTPR from Gate 6 so Stage 6 bounces cannot mask upstream blind spots; `test_subtask_freigabe_transitions_to_erledigt` (`test_jira_accounts.py:312`) mocks a summary-less subtask (`ATT-9999`).
  4. *Preservation of Core Invariants*: Preserves Gate 1–5 `overall_ftpr_percent` for `plausibility_warnings` and M3 rubber-stamping alarms, preserves `gate_verdict()` `assign`+`add_comment` ordering before `transition_issue()`, preserves summary-less non-stage subtask transitions, and keeps `process_metrics.jsonl` schema backward-compatible.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* stage subtask comments with `APPROVED`/`FREIGABE` or `REWORK`/`FAIL` in plain text or Jira v3 ADF dicts and zero-comment `Erledigt` subtasks, *When* `parse_verdicts_from_comments()` and `extract_sprint_gate_records()` execute, *Then* all synonym verdicts are extracted without error and zero-comment subtasks are counted as `unverified` with zero fabricated `PASS` entries.
  • *Given* sprint issues with Gate 1–5 subtasks and Gate 6 reviews, *When* `parse_sprint_gate_metrics()` runs, *Then* `overall_ftpr_percent` reflects Gates 1–5 (preserving `plausibility_warnings`), while `end_to_end_ftpr_percent` reports Gates 1–6 combined.
  • *Given* issues carrying `severity-*` labels, `[Highest]`/`[High]`/`[Low]` summary tags, Stage 1 `Risk Rating`, or German/English bug statuses (`abgelehnt`/`rejected`/`geschlossen`/`resolved`), *When* `parse_sprint_gate_metrics()` executes in root and `tools/hoechstmass/tools/sprint_metrics.py`, *Then* `risk_distribution` and `problem_trends` classify risks and terminal/awaiting-human bug counts consistently.
  • *Given* a `[Analysis]`..`[Test]` subtask without an `agent2` `PASS` comment vs a summary-less subtask (`ATT-9999`), *When* `transition_issue(..., "freigabe")` runs, *Then* the stage subtask transition exits non-zero while the summary-less subtask succeeds.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `process_metrics.jsonl` backward-compatible; `Localization Parity: N/A - no UI strings`; `Rule 5` offline mocked unit tests only.

---

### REQ-PRO-129: ADR-001 Keep-vs-Migrate Reconciliation, God-Class LoC Ratchet, WorkoutRepository SWE.3 Design & Living-Doc Layout Enforcement (ATT-8203) [Verified]
- **Tests**: `TST-PRO-129`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/architecture/adr_001_java_core_services_migration.md`, `docs/architecture.md`, `docs/architecture/detailed_design/workout_repository.md`, `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DetailedDesignParityContractTest.kt`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/tests/tst_process_stability_part1.md`, `docs/tests/tst_process_stability_part2.md`
- **Rationale**: ASPICE 4.0 Gap #6 (`SWE.2 BP5`, `SWE.3 BP1/BP3`, `SUP.8 BP1`) identified three architectural and directory-hygiene gaps: (1) residual `Keep`-vs-`Migrate` contradictions in `docs/architecture/adr_001_java_core_services_migration.md` (§2 Alt 2/3, §5 Mermaid, §8, stale `149` vs `120` `.java` count, `ATT-7903` marked `Planned`) and `docs/architecture.md` §5.3/§5.5 without automated decision-consistency assertions in `JavaCoreMigrationStrategyContractTest.kt`; (2) `16` production files in `app/src/main/` exceeding `1,500` LoC (`WorkoutRepository.kt` at `5,117` LoC .. `ImportBackupTabsScreen.kt` at `1,513` LoC) lacking a downward-ratcheting LoC ceiling test and an SWE.3 detailed design document for `WorkoutRepository`; and (3) `tools/verify_requirement_governance.py` silently ignoring non-`req_*.md` / non-`tst_*.md` files in `docs/requirements/` and `docs/tests/` instead of failing closed (`ATT-8203`, Epic `ATT-8138`).
- **Description**:
  1. *ADR-001 `Keep`-vs-`Migrate` & Java Count Reconciliation (`REQ-PRO-129.1`, refining `REQ-ARC-005`)*: `docs/architecture/adr_001_java_core_services_migration.md` (§§1–8) and `docs/architecture.md` (§5.3, §5.5) SHALL consistently reflect the PO-frozen `Keep` decision (`ATT-8115`) for core tracking/BLE/ANT+/SQLite services alongside the `29` completed Tier 1–2 Kotlin migrations (`ATT-7903` status `Completed`, `ATT-7905`, `ATT-7906`), reconcile active `.java` inventory to `120` remaining `.java` files (`<= 149` ceiling), update `Target Release` to `V5.0.0`, preserve `Status: Reviewed` (`Pending PO Sign-Off`), and `JavaCoreMigrationStrategyContractTest.kt` SHALL enforce `Keep`-vs-`Migrate` consistency and `120 <= 149` count reconciliation (`Risk: MEDIUM`).
  2. *God-Class LoC Ratchet & `WorkoutRepository` SWE.3 Detailed Design (`REQ-PRO-129.2`, refining `REQ-PRO-050`, `REQ-PRO-114`)*: `ArchitectureConformanceContractTest.kt` (already listed in the `22` `SourceTextReadingAuditContractTest.LEGITIMATE_GOVERNANCE_GUARDS`) SHALL enforce a monotonic downward-ratcheting LoC ceiling across all `16` `> 1,500` LoC `.kt`/`.java` files in `app/src/main/` (`WorkoutRepository.kt` `5,117` .. `ImportBackupTabsScreen.kt` `1,513`), failing on any net-new `> 1,500` LoC file, upward LoC growth beyond baseline, or stale lowered entries; `docs/architecture/detailed_design/workout_repository.md` SHALL document `WorkoutRepository` state/threading/error-handling/interfaces (`DOC-ARC-DD-WKO`, `Status: Reviewed`); `docs/architecture.md` §3.5 SHALL link `workout_repository.md` (expanding SWE.3 detailed design docs from `3` to `4`); and `DetailedDesignParityContractTest.kt` SHALL verify `workout_repository.md` structural/Mermaid/reflection parity alongside the existing `3` docs.
  3. *Fail-Closed Requirements/Tests Directory Layout Enforcement (`REQ-PRO-129.3`, refining `REQ-PRO-093`)*: `tools/verify_requirement_governance.py` SHALL reject any non-`req_*.md` file or unexpected entry in `docs/requirements/` (including legacy `SCRUM-*_plan.md`) and any non-`tst_*.md` file or unexpected entry in `docs/tests/`, backed by offline `tempfile` unit tests in `tools/test_verify_requirement_governance.py` (`Rule 5`).
  4. *Living Doc Shard Split & Invariant Preservation (`REQ-PRO-129.4`, refining `REQ-PRO-093.3/.4`, `REQ-PRO-127.3`)*: `docs/tests/tst_process_stability.md` SHALL be split via `tools/shard_living_docs.py` into `docs/tests/tst_process_stability_part1.md` and `docs/tests/tst_process_stability_part2.md` (every living shard `<= 4,000` lines under `--check-size --strict`), with `docs/requirements.md` and `docs/tests.md` synchronized (`--sync-indexes`), zero `app/src/main/` edits, `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, and `Localization Parity: N/A - no UI strings`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-129` (`REQ-PRO-129.1`–`REQ-PRO-129.4`), refining `REQ-ARC-005` (`ADR-001` & `JavaCoreMigrationStrategyContractTest.kt`), `REQ-PRO-050` (`ArchitectureConformanceContractTest.kt`), `REQ-PRO-114` (`DetailedDesignParityContractTest.kt` & `docs/architecture/detailed_design/`), and `REQ-PRO-093` (`tools/shard_living_docs.py` & `docs/requirements/` / `docs/tests/` layout).
  2. *Historical Origin & Commit Trace*: `1e95d2c3` (`ATT-7566`), `195a9f1f` (`ATT-8115` PO `Keep` pivot), `2de2a41c` (`ATT-8164` `DOC-ARC-ADR001` header), `ATT-8159` (`REQ-PRO-114`), and `ATT-8206` (`REQ-PRO-127` shard splitting).
  3. *Root Reason for Existing Formulation*: `REQ-ARC-005` locked `<= 149` Java classes and 3 child tickets (`ATT-7903`/`7905`/`7906`); `REQ-PRO-049.2` locked `22` `LEGITIMATE_GOVERNANCE_GUARDS`; `verify_requirement_governance.py` ignored non-`req_*.md` files during legacy sharding transition.
  4. *Preservation of Core Invariants*: Preserves `<= 149` bound, `3` registered child tickets (`ATT-7903`/`7905`/`7906`), `22` `LEGITIMATE_GOVERNANCE_GUARDS`, `105` `app/source-reading-baseline.txt`, `<= 4,000`-line shard cap, and zero `app/src/main/` edits.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/architecture/adr_001_java_core_services_migration.md` and `docs/architecture.md`, *When* `JavaCoreMigrationStrategyContractTest` executes, *Then* all sections reflect PO-frozen `Keep` alignment, `120` remaining `.java` files (`<= 149`), `29` migrated `.kt` (`ATT-7903`/`7905`/`7906` `Completed`), and `V5.0.0`, while contradictory `Migrate` claims fail deterministically.
  • *Given* `app/src/main/` (`16` files `> 1,500` LoC) and `docs/architecture/detailed_design/workout_repository.md`, *When* `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, and `SourceTextReadingAuditContractTest` run, *Then* LoC growth above baseline or net-new `> 1,500` LoC files fail, `WorkoutRepository` SWE.3 parity passes across all `4` detailed-design docs, and `LEGITIMATE_GOVERNANCE_GUARDS` remains `22`.
  • *Given* `docs/requirements/` and `docs/tests/` (live clean tree vs synthetic `non-req_*.md` / `non-tst_*.md` `tempfile` fixtures) and split `tst_process_stability_part{1,2}.md`, *When* `python3 tools/verify_requirement_governance.py`, `python3 tools/shard_living_docs.py --check-size --strict`, and `python3 -m unittest tools/test_verify_requirement_governance.py` run, *Then* misplaced files fail closed in fixtures while live governance and shard-size checks exit `0` (`<= 4,000` lines on every shard).
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `LEGITIMATE_GOVERNANCE_GUARDS == 22`; `app/source-reading-baseline.txt == 105`; `ADR-001` & `workout_repository.md` retain `Status: Reviewed` (`Pending PO Sign-Off`); `Localization Parity: N/A - no UI strings`.

---

### REQ-PRO-130: SWE.2 Singleton Heap/Cache/Map Budgets, Bounded HRV Threshold Cache, Subsystem Parity & IF-SWE-05 Upward-Import Ratchet (ATT-8202) [Verified]
- **Tests**: `TST-PRO-130`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/architecture.md`, `docs/architecture/interfaces.md`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/ClusterWorkoutDataAdapter.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/ClusterWorkoutDataPort.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/InterfaceSignatureDriftContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`
- **Rationale**: ASPICE 4.0 Audit Gap #5 (`SWE.2 BP1/BP3/BP4`, `ATT-8202`) identified three architectural specification and conformance gaps refining `REQ-PRO-050`, `REQ-PRO-052`, and `REQ-PRO-115`: (1) `docs/architecture.md` §4.9 quantifies heap (`< 40 MB`, `REQ-NFR-005`) solely for `TrackerService`, omitting quantitative cache, vertex, and `GoogleMap` (`liteMode` vs interactive GL) ceilings for singleton repositories (`WorkoutRepository`, `PeriodsRepository`/`PeriodMapUtils`, `RadarStateRepository`, `WorkoutClusterRepository`), while `WorkoutRepository.recentHrvThresholdsCache` (`ConcurrentHashMap`) lacks a hard entry bound; (2) `docs/architecture.md` §2 and `docs/architecture/interfaces.md` omit Swim, RadarLight, TPMS, and Polar PFTP subsystem entries and misclassify `IF-SWE-05` `WorkoutClusterEngine` as a stateless pure-JVM calculator despite `Context`/`clusterLock`/SQLite and `ui.aftermath.WorkoutRepository` coupling; and (3) `ArchitectureConformanceContractTest.kt:55` restricts `database/` upward `.ui.` import detection to UI suffixes, missing `11` `trainingtracker/database/` files importing `.ui.*` (`0` in `banalservice/database/`).
- **Description**:
  1. *SWE.2 Heap, Cache, Vertex & GoogleMap Instance Budgets (`REQ-PRO-130.1`, refining `REQ-PRO-052`, `docs/architecture.md` §4.9 & `WorkoutRepository.kt`)*: `docs/architecture.md` §4.9 SHALL document exact bounds and lifecycle cleanup for: `WorkoutRepository` (`MAX_OVERLAY_POINTS = 800`, lazy track loading with zero eager `LatLng` lists in `_allWorkouts`, and bounded `recentHrvThresholdsCache` with deterministic capacity eviction and deletion/refresh cleanup keeping `WorkoutRepository.kt <= 5,117` LoC); `PeriodsRepository` / `PeriodMapUtils` (`MAX_SUMMARY_THUMBNAIL_TRACKS = 8`, `<= 5` spatial anchors in `workoutIdToPolylineMap`, `recentlyFinishedWorkoutIds <= 50`, `LruCache(64)`, `MAX_CARD_MINI_MAP_VERTICES = 2_000` / `MAX_DETAILED_MAP_VERTICES = 8_000`, and nullified `workoutIdToPathMap` / `workoutIdToAllPolylinesMap`); `RadarStateRepository` (`sourceStates` bounded by `RadarSource` enum `<= 4`); `WorkoutClusterRepository` (`MutableSharedFlow(extraBufferCapacity = 16)`); and `GoogleMap` (`liteMode(true)` thumbnails in `PeriodSummaryCard` `!hasHeatmap`, `WorkoutClusterComponents`, `KnownLocationsScreen`, `PathPreviewMap`, plus `beyondViewportPageCount = 0` GL isolation in `TrackingTabsScreen` for interactive maps). `WorkoutRepository.recentHrvThresholdsCache` SHALL enforce a bounded capacity ceiling with eviction verified by behavioral unit tests (`WorkoutRepository*Test`), while `ArchitectureConformanceContractTest`, `NonFunctionalRequirementsContractTest`, and `SystemArchitectureDesignContractTest` enforce `docs/architecture.md` §4.9 & SYS.3/NFR traceability parity.
  2. *Subsystem Coverage & Truthful `IF-SWE-05` Boundary Alignment (`REQ-PRO-130.2`, refining `REQ-PRO-115`, `docs/architecture.md` §2/§5.1, `docs/architecture/interfaces.md`)*: `docs/architecture.md` §2 and `docs/architecture/interfaces.md` SHALL document Swim (`SwimWorkoutAdjustCard`, `SwimmingSection`, `WorkoutRepository.recalculateSwimWorkout`), RadarLight (`RadarLightController`, `RadarLightDeviceClient`, `SmartRadarLightEngine`, `SmartBrakeLightDetector`), TPMS (`IndirectTpmsEstimator`, `DualWheelTpmsEstimator`), and Polar PFTP (`PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `PolarOnlineStreamingBridge`, `PolarOfflineGattCoordinator`) components/interfaces. `WorkoutClusterEngine` (`<= 1,635` LoC) SHALL be decoupled from `com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository` via an injected/callback persistence boundary, and `IF-SWE-05` in `docs/architecture/interfaces.md` & `docs/architecture.md` §5.1 SHALL truthfully distinguish `WorkoutClusterEngine`'s `Context`/SQLite/callback boundary from stateless pure-JVM calculators (`AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `ClimbDetector`), maintaining `100%` JVM reflection parity in `InterfaceSignatureDriftContractTest` and `AspiceSweAuditContractTest`.
  3. *Full `.ui.*` Database Upward-Import Guard & Downward-Ratcheting Baseline (`REQ-PRO-130.3`, refining `REQ-PRO-050`, `ArchitectureConformanceContractTest.kt`)*: `ArchitectureConformanceContractTest.kt` SHALL replace the weak suffix regex (`L55`) with full `\bcom\.atrainingtracker\..*\.ui\b` import detection across `trainingtracker/database/` and `banalservice/database/` against an explicit downward-ratcheting baseline covering the remaining legacy `database -> ui.*` files (initially `11` legacy files -> `10` after decoupling `WorkoutClusterEngine`, `0` in `banalservice/database/`), failing deterministically on any net-new upward `.ui.*` import (`0` unallowlisted), any stale baseline entry (`0` stale), or baseline growth, plus a tripwire unit test verifying `ui.aftermath` detection.
  4. *Invariants & Governance Parity (`REQ-PRO-130.4`)*: `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, `MAX_MONOLITH_FILE_LOC_BASELINE` (`<= 16` files, `WorkoutRepository.kt <= 5,117` LoC, `WorkoutClusterEngine.kt <= 1,635` LoC), zero SQLite schema changes, `ANT-Android-SDKs` untouched, `Localization Parity: N/A - no UI strings`, and all `28` living-doc shards `<= 4,000` lines.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-130` (`REQ-PRO-130.1`–`REQ-PRO-130.4`), refining `REQ-PRO-050` (`ArchitectureConformanceContractTest.kt`), `REQ-PRO-052` (`docs/architecture.md` §4.9), and `REQ-PRO-115` (`docs/architecture/interfaces.md` & `InterfaceSignatureDriftContractTest.kt`).
  2. *Historical Origin & Commit Trace*: `f1bc836e` (`ATT-6182`), `fd1c481e` (`ATT-6734` introduced suffix-restricted `database/` `.ui.` regex), `074bd857` (`ATT-7666`), `6dffe9e7` (`ATT-7874`), and `0e294feb` (`ATT-8203` `MAX_MONOLITH_FILE_LOC_BASELINE`).
  3. *Root Reason for Existing Formulation*: `ATT-6734` restricted `.ui.` matching to Compose/View class suffixes so pre-existing DTO and repository imports (`WorkoutData`, `PathPoint`, `WorkoutRepository`) inside `database/` would not break CI prior to dedicated package cleanup; `§4.9` focused on `TrackerService` first (`REQ-NFR-003`..`014`).
  4. *Preservation of Core Invariants*: Broadening to `\bcom\.atrainingtracker\..*\.ui\b` with a downward-ratcheting legacy baseline (excluding decoupled `WorkoutClusterEngine -> WorkoutRepository`) blocks all net-new upward layering violations without forcing an out-of-scope 100+ file DTO relocation, while preserving `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, `MAX_MONOLITH_FILE_LOC_BASELINE` (`WorkoutRepository.kt <= 5,117`, `WorkoutClusterEngine.kt <= 1,635`), and all `REQ-NFR-003`..`014` contract assertions.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/architecture.md` §4.9 and `WorkoutRepository.recentHrvThresholdsCache`, *When* `WorkoutRepository*Test`, `ArchitectureConformanceContractTest`, `NonFunctionalRequirementsContractTest`, and `SystemArchitectureDesignContractTest` execute, *Then* all repository/cache/vertex/`GoogleMap` budgets are documented and enforced, `recentHrvThresholdsCache` evicts deterministically beyond its bound, and `WorkoutRepository.kt <= 5,117` LoC holds.
  • *Given* `docs/architecture.md` §2/§5.1, `docs/architecture/interfaces.md`, and decoupled `WorkoutClusterEngine` (`<= 1,635` LoC), *When* `InterfaceSignatureDriftContractTest` and `AspiceSweAuditContractTest` execute, *Then* Swim, RadarLight, TPMS, Polar PFTP, and truthful `IF-SWE-05` boundaries match compiled bytecode via JVM reflection (`100%` pass).
  • *Given* `ArchitectureConformanceContractTest` scanning `trainingtracker/database/` and `banalservice/database/` with `\bcom\.atrainingtracker\..*\.ui\b`, *When* executed against the live tree and a negative `ui.aftermath` tripwire fixture, *Then* the live tree passes with `0` unallowlisted upward imports, `0` stale baseline entries, `banalservice/database/` == `0`, and the tripwire detects `ui.aftermath` deterministically.
- **Invariants**: `WorkoutRepository.kt <= 5,117` LoC; `WorkoutClusterEngine.kt <= 1,635` LoC; `LEGITIMATE_GOVERNANCE_GUARDS == 22`; `app/source-reading-baseline.txt == 105`; zero SQLite schema changes; `ANT-Android-SDKs` untouched; `Localization Parity: N/A - no UI strings`.

---

### REQ-PRO-131: System-Level Capabilities (`SYS.2`), System Architecture (`SYS.3`) Synchronization, Verification Protocol Alignment & `SYS.2/SYS.3` Skill Impact Gate (ATT-8201) [Verified]
- **Tests**: `TST-PRO-131`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system_requirements.md`, `docs/system/system_context.md`, `docs/system/system_architecture.md`, `docs/system/sys_verification_protocol.md`, `docs/system/system_software_traceability.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `.agents/skills/stage1-analysis/SKILL.md`, `.agents/skills/stage2-req-test-spec/SKILL.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemContextContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemRequirementsSpecificationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemVerificationProtocolContractTest.kt`
- **Rationale**: ASPICE 4.0 Gap #4 (`SYS.2 BP1–BP6`, `SYS.3 BP1–BP5`, `SYS.5 BP1`, `ATT-8201`, Epic `ATT-8138`) identified four system-level synchronization deficits across `docs/system/`: (1) only `12` `SYSREQ-*` IDs (`SYSREQ-TRK-001..006`, `SYSREQ-DEX-001..006`) exist, omitting `9` core hardware, RF actuation, navigation, IMU, swimming, HRV/DFA, recovery, Polar PFTP/PMD, and Open-Meteo DEM capabilities; (2) `docs/system/system_architecture.md` (`v1.1.0`) omits `6` `system_context.md` elements plus Smartphone IMU (absent from both `system_context.md` and `system_architecture.md`), models `SYS-IF-01`/`SYS-IF-02` unidirectionally, and omits synchronous Polar PFTP 30s backfill on `mDbExecutor` in §4.2; (3) `docs/system/sys_verification_protocol.md:66` cites a hallucinated Varia BLE UUID (`6a4e3200-6628-eecd-2140-d68103ba32e3` vs `6a4e3200-667b-11e3-949a-0800200c9a66`), lines `99, 195, 266` (`<= 8.0%/h`) conflict with `docs/architecture.md:311` & `REQ-NFR-006` (`< 5.0%/h` screen-off / `<= 12.0%/h` screen-on), and `13` obsolete IDs remain in `GRANDFATHERED_SYSREQS` (`verify_requirement_governance.py:4159-4173`); and (4) `stage1-analysis/SKILL.md` and `stage2-req-test-spec/SKILL.md` lack an explicit `SYS.2/SYS.3` impact gate.
- **Description**:
  1. *Net-New `SYSREQ-*` Capabilities (`REQ-PRO-131.1`, refining `REQ-PRO-054`, `REQ-PRO-055`, `REQ-PRO-076`)*: `docs/system/sysreq_live_tracking.md` (`SYSREQ-TRK-007` through `SYSREQ-TRK-013`: bidirectional BLE/ANT+ Radar & Tail-Light Actuation/Smart Braking; Barometer/EKF Altitude/Slope/Live Climbs & Segments; Google Maps GPX Follow-Me & Turn-by-Turn Audio Navigation; Smartphone IMU Kinematics/Odometry/iTPMS; Swimming Telemetry & Lap Adjustment; Live HRV/RR & DFA Alpha-1 Threshold Estimation; Three-Tier Process-Death Session Recovery) and `docs/system/sysreq_data_exchange.md` (`SYSREQ-DEX-007`, `SYSREQ-DEX-008`: Polar BLE SDK `8.6.0` PFTP/PMD Offline Recording & 30s `mDbExecutor` Backfill; Open-Meteo DEM External Elevation Enrichment), along with `docs/system_requirements.md` and `docs/system/system_software_traceability.md`, SHALL formally specify all `9` new `SYSREQ-*` capabilities (`21` total `SYSREQ-*`) with 100% bidirectional traceability to existing `REQ-*` software requirements.
  2. *`SYS.3` & `system_context.md` Synchronization (`REQ-PRO-131.2`, refining `REQ-PRO-053`, `REQ-PRO-056`)*: `docs/system/system_context.md` and `docs/system/system_architecture.md` (`v1.1.0` -> `v1.2.0`) SHALL synchronize all external system elements (Smartphone IMU sensors `TYPE_GYROSCOPE`/`TYPE_ROTATION_VECTOR`/`TYPE_GAME_ROTATION_VECTOR`/`TYPE_LINEAR_ACCELERATION`/`TYPE_GRAVITY`/`TYPE_ACCELEROMETER`, Barometer/Proximity, Google Maps SDK v2, Open-Meteo, Firebase, Play Auth/Review, PebbleKit), update `SYS-IF-01` & `SYS-IF-02` to bidirectional (`<-->`) covering `0x180A`, `6a4e3202`/`6a4e3203`/`6a4e2f00`, Polar PFTP/PMD, and ANT+ `0x28`/`0x23`, model 30s Polar PFTP backfill on `mDbExecutor` in §4.2, and allocate `100%` of all `21` `SYSREQ-*` IDs (`declaredSysreqs == allocatedSysreqs`).
  3. *`SYS.5` Protocol Alignment, Battery Reconciliation & `GRANDFATHERED_SYSREQS` Purge (`REQ-PRO-131.3`, refining `REQ-PRO-069`, `REQ-NFR-006`)*: `docs/system/sys_verification_protocol.md:66` SHALL correct the Garmin Varia BLE UUID to `6a4e3200-667b-11e3-949a-0800200c9a66`; lines `99, 195, 266` SHALL explicitly reconcile endurance power targets (`< 5.0%/h` screen-off per `REQ-NFR-006`, `<= 8.0%/h` mixed screen-on/screen-off `E5` endurance, `<= 12.0%/h` screen-on cockpit, preserving `REQ-NFR-006` `On-Device Manual` `[Implemented]`); and `tools/verify_requirement_governance.py:4159-4173` SHALL remove/empty `GRANDFATHERED_SYSREQS` (`0` phantom citations) with offline unit tests in `tools/test_verify_requirement_governance.py`.
  4. *`SYS.2`/`SYS.3` Skill Impact Gate (`REQ-PRO-131.4`)*: `.agents/skills/stage1-analysis/SKILL.md` and `.agents/skills/stage2-req-test-spec/SKILL.md` SHALL add an explicit `SYS.2/SYS.3` impact check while preserving `"Documentation Code-Grounding"` (`AspiceSweAuditContractTest.kt:223`) and `HoechstmassCoreFrameworkContractTest` parity.
  5. *Contract Test Parity & Invariants (`REQ-PRO-131.5`)*: `SystemContextContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `SystemDataExchangeRequirementsContractTest.kt`, `SystemRequirementsSpecificationContractTest.kt`, and `SystemVerificationProtocolContractTest.kt` SHALL be updated in lockstep with `docs/system/`; zero `app/src/main/` edits, `Localization Parity: N/A - no UI strings`, and all living-doc shards `<= 4,000` lines (`_part2.md`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-131` (`REQ-PRO-131.1`–`REQ-PRO-131.5`), refining `REQ-PRO-053..056`, `REQ-PRO-069`, `REQ-PRO-076`, `REQ-NFR-006`; `GRANDFATHERED_SYSREQS` (`verify_requirement_governance.py:4159-4173`); `SystemContextContractTest.kt:126-127,240,249`; `SystemArchitectureDesignContractTest.kt:177`.
  2. *Historical Origin & Commit Trace*: `52b49a02` (`ATT-6163` added `GRANDFATHERED_SYSREQS`), `1660a7de` (`ATT-6741` purged 13 orphan IDs & locked `12` `SYSREQ-*`), `ATT-6738` (locked `SystemContextContractTest` to `TYPE_PRESSURE`/`TYPE_PROXIMITY`), and `ATT-8158` (`REQ-NFR-006` `On-Device Manual`).
  3. *Root Reason for Existing Formulation*: Prevented phantom `SYSREQ-*` links and hallucinated Android sensors (`TYPE_STEP_COUNTER`) prior to IMU/Polar/Radar-Light specs and kept `REQ-NFR-006` honest pending physical device testing.
  4. *Preservation of Core Invariants*: Preserves `declared_ids == allocated_ids` 1:1 parity, `REQ-NFR-006` `On-Device Manual` `[Implemented]`, `<= 8.0%/h` mixed `E5` threshold in `SystemVerificationProtocolContractTest.kt:174`, `<= 4,000`-line shard cap (`_part2.md`), `REQ-PRO-120` `Status: Reviewed` headers, `"Documentation Code-Grounding"` (`AspiceSweAuditContractTest.kt:223`), `HoechstmassCoreFrameworkContractTest` parity, and `Localization Parity: N/A - no UI strings`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `docs/system/` (`21` `SYSREQ-*` IDs), `system_context.md`, `system_architecture.md` (`v1.2.0`), and `sys_verification_protocol.md`, *When* all `System*ContractTest` and `NonFunctionalRequirementsContractTest` suites execute, *Then* `declaredSysreqs == allocatedSysreqs == 21`, IMU/Polar/Radar/DEM grounding passes, `6a4e3200-667b-11e3-949a-0800200c9a66` and reconciled battery budgets pass, and `REQ-NFR-006` remains `On-Device Manual` `[Implemented]`.
  • *Given* `tools/verify_requirement_governance.py` with `GRANDFATHERED_SYSREQS` emptied/removed and updated Stage 1/2 `SKILL.md` files, *When* `tools/test_verify_requirement_governance.py`, `tools/verify_requirement_governance.py`, `AspiceSweAuditContractTest`, and `HoechstmassCoreFrameworkContractTest` execute, *Then* phantom `SYSREQ-PER/ACQ/ENV-*` citations fail closed in tests and all live governance/contract checks exit `0`.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `REQ-NFR-006` `On-Device Manual` `[Implemented]` preserved; all shards `<= 4,000` lines; `REQ-PRO-120` headers (`Status: Reviewed`, `Pending PO Sign-Off`); `Localization Parity: N/A - no UI strings`.

---

### REQ-PRO-132: Behavioral Compose UI Testing Mandate & Reflection-Only UI Test Ratchet (`SWE.4` `CL 2`) (ATT-8198) [Verified]
- **Tests**: `TST-PRO-132`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/UiReflectionOnlyTestAuditContractTest.kt`, `app/ui-reflection-baseline.txt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/radar/RadarSettingsSectionTest.kt`, `docs/coding_standards.md`, `.agents/skills/stage4-implementation/SKILL.md`, `.agents/skills/stage5-verification/SKILL.md`
- **Rationale**: Following `REQ-TST-002` (`ATT-4028`) and `REQ-PRO-049` (`ATT-6564`–`ATT-6569`), `189` test files across `trainingtracker/ui/` (`178`) and `banalservice/ui/` (`11`) rely on `Class.forName("...Kt")` JVM reflection without `createComposeRule()`, and `RadarSettingsSectionTest.kt` uses tautological `TuningConfig` assertions (including retired `RadarLightControlMode` controls) while claimed as a Compose UI test (`ATT-7991_walkthrough.md:29`). Enforcing behavioral `createComposeRule()` verification and ratcheting out new reflection-only UI tests closes ASPICE 4.0 Gap #1 (`SWE.4 BP1/BP2/BP3`, `SUP.1 BP3`, `ATT-8198`, refining `REQ-TST-002` and `REQ-PRO-049` under Epic `ATT-8138`):
- **Description**:
  1. *Reflection-Only UI Test Ratchet & Baseline (`REQ-PRO-132.1`, refining `REQ-PRO-049`)*: `UiReflectionOnlyTestAuditContractTest.kt` SHALL scan all `.kt`/`.java` files under `app/src/test/java/` whose relative path matches `(?:^|/)ui/` (explicitly covering both `com/atrainingtracker/trainingtracker/ui/` [`178`] and `com/atrainingtracker/banalservice/ui/` [`11`]) and flag any file invoking multiline-aware `Class.forName("...Kt")` without `createComposeRule`. Existing legacy entries SHALL be isolated in `app/ui-reflection-baseline.txt` (`189` entries ceiling), enforcing `(detected - baseline).isEmpty()` and `(baseline - detected).isEmpty()` (zero stale entries), while scanning zero `app/src/main/` files (`REQ-PRO-049.1` preserved: `22` guards, `105` source-reading baseline).
  2. *Protection of Legitimate Architectural & Excision Reflection Guards (`REQ-PRO-132.2`, refining `REQ-TST-002`)*: The ratchet SHALL NOT flag `build/` contract suites (`AndroidViewModelReflectionContractTest`, `InterfaceSignatureDriftContractTest`, `AdrCompletenessContractTest`), non-`*Kt` classpath/decoupling checks (`CoreDependencyAlignmentTest`, `WorkoutRepositoryDeleteTest`), or tests that execute `createComposeRule()` alongside signature checks (`RadarLightQuickSwitchSheetTest`, `SensorSourceDialogContractTest`).
  3. *Coding Standard & Stage 4/5 Behavioral Compose Mandate (`REQ-PRO-132.3`)*: `docs/coding_standards.md` §5.4, `.agents/skills/stage4-implementation/SKILL.md`, and `.agents/skills/stage5-verification/SKILL.md` SHALL explicitly distinguish legitimate excision/ABI reflection (`build/` & non-`*Kt`) from prohibited reflection-only UI tests and mandate Robolectric/instrumented `createComposeRule()` behavioral assertions (semantics, conditional visibility, user callbacks) whenever `@Composable` screens/dialogs/sheets are created or modified.
  4. *`RadarSettingsSectionTest` Behavioral Remediation (`REQ-PRO-132.4`, strengthening `TST-CON-031.5b` & `TST-TRK-062.5`)*: `RadarSettingsSectionTest.kt` SHALL retire stale `RadarLightControlMode` / `TuningConfig` tautologies (`:38-129`) and compose `RadarSettingsSection` via `@RunWith(RobolectricTestRunner::class)` + `createComposeRule()`, asserting conditional visibility (`FULL_SCREEN` hides `radar_overlay_clear_mode`; `radarSpatialAudioEnabled` reveals spatial sub-controls), retired-control absence, and interactive callback/DataStore dispatches, while preserving existing 9-locale XML budget/disambiguation tests (`:131-226`) with `0` `src/main/` reads.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new `REQ-PRO-132` (`REQ-PRO-132.1`–`REQ-PRO-132.4`), refining `REQ-TST-002` (`docs/coding_standards.md` §5.4) and `REQ-PRO-049` (`SourceTextReadingAuditContractTest.kt`), and remediating `TST-CON-031.5b` / `TST-TRK-062.5` (`RadarSettingsSectionTest.kt`).
  2. *Historical Origin & Commit Trace*: `REQ-TST-002` (`ATT-4028`) prescribed `Class.forName`/`declaredMethods` to replace UI `File.readText()` greps; `REQ-PRO-049` (`ATT-6186`, `c91e62b6`) & `ATT-6564`–`ATT-6569` (`12f5f47c`, `1298a643`, `ef50449a`, `3b5a5b41`) eliminated `src/main` reads but normalized `*Kt` reflection stubs in `ui/`.
  3. *Root Reason for Existing Formulation*: Robolectric Compose `createComposeRule()` was not yet standardized across `ui/` at `ATT-4028`, while reflection protected architectural excision/decoupling invariants.
  4. *Preservation of Core Invariants*: Preserves all `build/` & non-`*Kt` excision/ABI reflection tests, preserves `SourceTextReadingAuditContractTest` (`22` guards, `105` baseline, `0` new `src/main/` reads), preserves `RadarSettingsSectionTest` XML locale checks (`:131-226`), and makes zero edits to `app/src/main/` or `values*/strings*.xml`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a new test under `trainingtracker/ui/` or `banalservice/ui/` calling `Class.forName("...Kt")` without `createComposeRule()`, *When* `UiReflectionOnlyTestAuditContractTest` runs, *Then* the audit fails unless the file is on the `189`-entry `app/ui-reflection-baseline.txt` (and fails on stale baseline entries or synthetic tripwires).
  • *Given* `AndroidViewModelReflectionContractTest`, `InterfaceSignatureDriftContractTest`, `AdrCompletenessContractTest`, `CoreDependencyAlignmentTest`, and `RadarLightQuickSwitchSheetTest`, *When* `UiReflectionOnlyTestAuditContractTest` executes, *Then* all pass with `0` false positives.
  • *Given* `RadarSettingsSectionTest.kt`, *When* `:app:testDebugUnitTest --tests "*.RadarSettingsSectionTest"` executes, *Then* `RadarSettingsSection` renders via `createComposeRule()`, verifies conditional visibility, retired-control absence, interactive callbacks, and all preserved 9-locale XML checks with `0` `src/main/` reads.
- **Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `SourceTextReadingAuditContractTest` (`22` guards, `105` baseline) preserved; `LEGITIMATE_GOVERNANCE_GUARDS == 22`; `docs/coding_standards.md` §1.1–§1.5 & `AspiceSweAuditContractTest` skill markers preserved; `Localization Parity: N/A - no UI strings`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.1` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Promoted `REQ-PRO-127` to `[Verified]` after Stage 5 verification (`490/490` `tools/` + `56/56` Hoechstmass Python tests, `113/113` Gradle contract tests across 16 suites, `27/27` shards `<= 4,000` lines, `--coverage` Cat 4 `1244/1244` `100%`, governance `exit 0`). |
| `1.0.2` | 2026-11-18 | AI Agent (`ATT-8205`) | `ATT-8205` | Added `[Planned]` `REQ-PRO-128` (`REQ-PRO-128.1`–`REQ-PRO-128.5`: extended verdict keywords & ADF/`unverified` parity, additive `end_to_end_ftpr_percent`, multi-signal `risk_distribution`, extended German/English bug statuses + Höchstmaß parity, zero-comment stage subtask closure guard). |
| `1.0.3` | 2026-11-18 | AI Agent (`ATT-8205`) | `ATT-8205` | Promoted `REQ-PRO-128` to `[Implemented]` in Stage 4 (`tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py`). |
| `1.0.4` | 2026-11-18 | AI Agent (`ATT-8205`) | `ATT-8205` | Promoted `REQ-PRO-128` to `[Verified]` after Stage 5 verification (`test_sprint_metrics.py` `23/23`, `hoechstmass/test_sprint_metrics.py` `18/18`, `test_jira_accounts.py` `TST-PRO-128` PASS, governance `exit 0`). |
| `1.0.5` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Refined `REQ-PRO-062` (`[Planned]`) for explicit `--review-type=human|cross-vendor-ai` classification of named human reviewers (root + Höchstmaß) and correction of `sprint_2026-46.4.md` & `sprint_2026-47.1.md`. |
| `1.0.6` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Promoted `REQ-PRO-062` to `[Implemented]` in Stage 4 (`tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `sprint_2026-46.4.md`, `sprint_2026-47.1.md`). |
| `1.0.7` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Promoted `REQ-PRO-062` to `[Verified]` after Stage 5 verification (`4/4` ATT-8204 tests PASS, `--check-reviews` `13/13`, governance `exit 0`). |
| `1.0.8` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Added `[Planned]` `REQ-PRO-129` (`REQ-PRO-129.1`–`REQ-PRO-129.4`: ADR-001 `Keep`-vs-`Migrate` & `120` `.java` reconciliation, `16`-file `> 1,500` LoC ratchet + `workout_repository.md` SWE.3 design, fail-closed `docs/requirements/` & `docs/tests/` layout enforcement, `tst_process_stability_part{1,2}.md` split). |
| `1.0.9` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Promoted `REQ-PRO-129` to `[Implemented]` in Stage 4 (`adr_001_java_core_services_migration.md`, `docs/architecture.md`, `workout_repository.md`, `JavaCoreMigrationStrategyContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `tools/verify_requirement_governance.py`). |
| `1.0.10` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Promoted `REQ-PRO-129` to `[Verified]` after Stage 5 verification (`35/35` Gradle contract tests across 6 suites, `TestLivingDocDirectoryLayout` `4/4` PASS, `28/28` shards `<= 4,000` lines, `TST-PRO-129` verified evidence `True`, governance `exit 0`). |
| `1.0.11` | 2026-11-18 | AI Agent (`ATT-8202`) | `ATT-8202` | Added `[Planned]` `REQ-PRO-130` (`REQ-PRO-130.1`–`REQ-PRO-130.4`: SWE.2 heap/cache/vertex/`GoogleMap` budgets & bounded `recentHrvThresholdsCache`, Swim/RadarLight/TPMS/Polar PFTP & truthful `IF-SWE-05` parity, full `.ui.*` database upward-import ratchet, monolith & governance invariants). |
| `1.0.12` | 2026-11-18 | AI Agent (`ATT-8202`) | `ATT-8202` | Promoted `REQ-PRO-130` to `[Implemented]` in Stage 4 (`WorkoutRepository.kt` `LruCache(64)`, `ClusterWorkoutDataPort.kt`, `ClusterWorkoutDataAdapter.kt`, `WorkoutClusterEngine.kt` decoupled, `docs/architecture.md`, `docs/architecture/interfaces.md`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`). |
| `1.0.13` | 2026-11-18 | AI Agent (`ATT-8202`) | `ATT-8202` | Promoted `REQ-PRO-130` to `[Verified]` after Stage 5 verification (`48` XML suites: `246/247` PASS, `0` skipped, sole fail pre-existing unrelated `EditDeviceDialog.kt` `DeadCodeEliminationContractTest`; `:app:lintDebug` `0` errors; `WorkoutRepository.kt` `5,049` LoC, `WorkoutClusterEngine.kt` `1,634` LoC). |
| `1.0.14` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Added `[Planned]` `REQ-PRO-131` (`REQ-PRO-131.1`–`REQ-PRO-131.5`: `9` net-new `SYSREQ-*` capabilities `SYSREQ-TRK-*` `007`–`013` & `SYSREQ-DEX-*` `007`–`008`, `SYS.3` & `system_context.md` sync, Varia UUID & battery budget reconciliation, `GRANDFATHERED_SYSREQS` removal, Stage 1/2 `SYS.2/SYS.3` skill impact gate). |
| `1.0.15` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Promoted `REQ-PRO-131` to `[Implemented]` in Stage 4 (`sysreq_live_tracking.md` `SYSREQ-TRK-007..013`, `sysreq_data_exchange.md` `SYSREQ-DEX-007..008`, `docs/system_requirements.md`, `system_context.md`, `system_architecture.md` `v1.2.0`, `sys_verification_protocol.md` `v1.2.0`, `system_software_traceability.md` `v1.2.0`, `verify_requirement_governance.py`, Stage 1/2 `SKILL.md`s, and 6 `System*ContractTest.kt` suites). |
| `1.0.16` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Promoted `REQ-PRO-131` to `[Verified]` after Stage 5 verification (`13/13` Gradle contract suites `79/79` PASS, `test_hoechstmass_tools.py` `37/37` OK, `test_verify_requirement_governance.py` `104/105` with `TestGrandfatheredSysreqsRemoved` PASS & pre-existing `TST-TRK-087` fail, `21/21` `SYSREQ`s `100%` covered, `28/28` shards `<= 4,000` lines, governance `exit 0`). |
| `1.0.17` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Refined `REQ-PRO-074` (`[Planned]`, `REQ-PRO-074.1`/`.3`/`.4`/`.5`) for `ERROR:`/`FAIL:` threshold gating, `category_3_code_tags_without_spec`/`category_4_tsts_not_cited_back` JSON keys, subprocess non-zero JSON parsing, and `V<ver>__<code>` dossier normalization. |
| `1.0.18` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Promoted `REQ-PRO-074` to `[Implemented]` in Stage 4 (`tools/generate_release_verification_summary.py`, `docs/releases/README.md`, `tools/test_generate_release_verification_summary.py`). |
| `1.0.19` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Promoted `REQ-PRO-074` to `[Verified]` after Stage 5 verification (`test_generate_release_verification_summary.py` `22/22` OK, `ReleaseManagerSystemEvidenceContractTest` `4/4` PASS, governance `exit 0`). |
| `1.0.20` | 2026-11-18 | AI Agent (`ATT-8875`) | `ATT-8875` | Refined `REQ-PRO-100` (`[Planned]`, `REQ-PRO-100.4`) for sibling-scoped `generate_test_evidence` import isolation and `skip_kover`/`--no-kover` Höchstmaß mirror parity. |
| `1.0.21` | 2026-11-18 | AI Agent (`ATT-8875`) | `ATT-8875` | Promoted `REQ-PRO-100` to `[Implemented]` in Stage 4 (`tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/generate_test_evidence.py`, `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`). |
| `1.0.22` | 2026-11-18 | AI Agent (`ATT-8875`) | `ATT-8875` | Promoted `REQ-PRO-100` to `[Verified]` after Stage 5 verification (`tools.test_jira_accounts` `102/102` OK, `tools/hoechstmass/tools` `62/62` OK, `tools.test_generate_test_evidence` `8/8` OK, `HoechstmassToolingContractTest` `4/4` PASS, governance `exit 0`). |
| `1.0.23` | 2026-11-18 | AI Agent (`ATT-8876`) | `ATT-8876` | Refined `REQ-PRO-108` (`[Planned]`, `REQ-PRO-108.3`) for sibling-scoped `parallel_sprint_lock` import isolation against cross-directory `sys.modules` shadowing and Höchstmaß `parallel_sprint_lock.py` commit trailer parity. |
| `1.0.24` | 2026-11-18 | AI Agent (`ATT-8876`) | `ATT-8876` | Promoted `REQ-PRO-108` to `[Implemented]` in Stage 4 (`tools/hoechstmass/tools/parallel_sprint_lock.py`, `tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`, `tools/test_jira_datacenter.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`). |
| `1.0.25` | 2026-11-18 | AI Agent (`ATT-8876`) | `ATT-8876` | Promoted `REQ-PRO-108` to `[Verified]` after Stage 5 verification (`tools.test_jira_accounts` + `tools.test_flock_serialization` + `tools.test_jira_datacenter` `136/136` OK, `tools/hoechstmass/tools` `63/63` OK, poisoned-cache cross-dir `78/78` OK, `HoechstmassToolingContractTest` `4/4` PASS, governance `exit 0`). |
| `1.0.26` | 2026-11-18 | AI Agent (`ATT-8198`) | `ATT-8198` | Added `[Planned]` `REQ-PRO-132` (`REQ-PRO-132.1`–`REQ-PRO-132.4`: behavioral `createComposeRule()` UI test mandate, `189`-entry `(?:^\|/)ui/` `*Kt` reflection ratchet across `trainingtracker/ui/` + `banalservice/ui/`, `docs/coding_standards.md` §5.4 + Stage 4/5 skills update, and `RadarSettingsSectionTest` Compose remediation). |
| `1.0.27` | 2026-11-18 | AI Agent (`ATT-8198`) | `ATT-8198` | Promoted `REQ-PRO-132` to `[Implemented]` in Stage 4 (`UiReflectionOnlyTestAuditContractTest.kt`, `app/ui-reflection-baseline.txt`, `docs/coding_standards.md` v`1.0.1` §5.4, Stage 4/5 `SKILL.md`s, `RadarSettingsSectionTest.kt`). |
| `1.0.28` | 2026-11-18 | AI Agent (`ATT-8198`) | `ATT-8198` | Promoted `REQ-PRO-132` to `[Verified]` after Stage 5 verification (`31/31` targeted Gradle tests PASS across 5 suites, `189`-entry `(?:^\|/)ui/` baseline locked, `SourceTextReadingAuditContractTest` `4/4` PASS, governance `exit 0`). |
