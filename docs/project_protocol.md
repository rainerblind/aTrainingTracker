# Agile Agentic ASPICE (AAA) Protocol & Architecture Pointer: aTrainingTracker

**Document ID**: `DOC-GOV-PROTOCOL`  
**Version**: 1.0.3  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

Agile Agentic ASPICE (AAA) operationalizes Automotive SPICE for autonomous AI pair-programming agents across three core pillars:
1. **Intentional Engineering Tripwires (*Stolpersteine*)**: Deliberate quality gates (Gates 1–5 plus Stage 6 Cold Final Review), bidirectional requirement/test traceability (`REQ-XXX` $\leftrightarrow$ `TST-XXX`), Chesterton's Fence archaeology (`REQ-PRO-022`), and independent `pro`/`flash` cross-auditing prevent autonomous agents from taking unverified shortcuts.
2. **Artifact-Driven Infinite Context**: Stage deliverables in `docs/engineering/` (`analysis/<KEY>_analysis.md`, `test_specs/<KEY>_test_spec.md`, `plans/<KEY>_plan.md`, `walkthroughs/<KEY>_walkthrough.md`, `retros/Sprint_Review_and_Retro_<KEY>.md`, and `test_results/index.html`), plus living specifications (`docs/requirements.md` + `docs/requirements/req_*.md`, `docs/tests.md` + `docs/tests/tst_*.md`), maintain persistent external long-term memory across context compactions and agent turns.
3. **Constructive Resistance & Documented Deviations (*Pflicht zum Widerspruch & Dokumentierte Abweichungserlaubnis*)**: Agents MUST NOT initiate shortcuts. When the human user requests an exception from standard process, the agent SHALL first offer constructive resistance (explaining the guardrail's purpose and associated risk). Only if the human explicitly insists may the exception proceed—and even then, the exception, its justification, and all changes made MUST be cleanly documented in Jira and the walkthrough/docs.

## Document Triad & In-Sprint Architecture
Authoritative engineering governance and living standards are decoupled into the **Document Triad** and modular `.agents/skills/`:
1. **In-Sprint Core Rules (9 Core Rules)**: [`.agents/rules/aspice_governance.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/rules/aspice_governance.md)
2. **UI & Interaction Design Guidelines**: [`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md)
3. **Technical Architecture & Coding Standards**: [`docs/coding_standards.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/coding_standards.md)
4. **Stage & Ceremony Skills**: [`.agents/skills/stage1-analysis/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/stage1-analysis/SKILL.md) through [`.agents/skills/stage6-final-review/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/stage6-final-review/SKILL.md), [`.agents/skills/sprint-planner/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/sprint-planner/SKILL.md), and [`.agents/skills/jira-workflow/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/jira-workflow/SKILL.md).
5. **Historical Rule Archive (Rules 1–85+)**: [`docs/engineering/rule_history_archive.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/rule_history_archive.md)

## Multi-Account Role Segregation & Invariants (`REQ-PRO-020` / `REQ-PRO-022`)
* **Per-Role Credential Partitioning (`.env.jira.<role>` — `REQ-PRO-019` / ATT-6147)**: To enforce ASPICE SUP.1 and SUP.8 role segregation, credentials are partitioned into gitignored per-role files (`.env.jira.agent1`, `.env.jira.agent2`, `.env.jira.coordinator`) with `.gitignore` covering `.env.jira*`. `tools/jira_util.py` dynamically resolves the active role's file (`agent1` via `JIRA_AGENT1_USER` / `JIRA_AGENT1_TOKEN`, `agent2` via `JIRA_AGENT2_USER` / `JIRA_AGENT2_TOKEN`, `coordinator` via `JIRA_COORDINATOR_USER` / `JIRA_COORDINATOR_TOKEN`) with zero cross-role fallback or disclosure. Human credentials (`JIRA_USER`/`JIRA_TOKEN`) are absent from the workspace.
* **Coordinator Role Boundaries**: The **Coordinator** orchestrates sprint backlog queries and sub-task creation, **cannot override** independent Auditor (`agent2`) verdicts, cannot skip quality gates, and cannot transition parent tickets to `Erledigt` (reserved exclusively for the human user).
* **Requirement Archaeology (Chesterton's Fence — `REQ-PRO-022`)**: Any Stage 2 deliverable that modifies, relaxes, or replaces an existing requirement in `docs/requirements.md` must include a 4-field `Requirement Archaeology & Chesterton's Fence Audit` section (`Original Requirement ID & Target`, `Historical Origin & Commit Trace`, `Root Reason for Existing Formulation`, `Preservation of Core Invariants`), verified by `tools/verify_requirement_governance.py`.

## 5-Layer Swiss Cheese Defense Model & Statistical Decorrelation
Defects are prevented by stacking 5 statistically decorrelated defensive layers ($P(\text{escaped defect}) = \prod_{i=1}^5 p_i = p_1 \cdot p_2 \cdot p_3 \cdot p_4 \cdot p_5$):
1. **Layer 1 ($p_1$) — Executing Agent (Sub-Task Level)**: Bounded stage prompt + local compiler, unit tests, and static analyzers (`agent1`).
2. **Layer 2 ($p_2$) — Reviewing Agent (Sub-Task Gate Level)**: Asymmetric model tier (`pro` $\leftrightarrow$ `flash`) auditing deliverables at Gates 1–5 (`agent2`).
3. **Layer 3 ($p_3$) — Ticket-Wide Verification Session (Stage 5)**: Clean-room full regression suite, requirement-test parity, and visual walkthrough verification.
4. **Layer 4 ($p_4$) — Human Engineer (Final Review Gate)**: Live application testing and exclusive parent ticket closure authority.
5. **Layer 5 ($p_5$) — Periodic Cross-Vendor Audits**: Independent codebase reviews by **Claude** and UI screenshot/UX critiques by **ChatGPT** to break vendor-level blind spots.

## Isolated In-Place Code & Document Review Workflow (`.reviews/` + Emacs)
To allow frictionless human review of source code (`.kt`, `.java`, `.py`, `.xml`) and documentation (`.md`) while autonomous agents compile code and switch Git branches in the main workspace:
1. **Isolated Sandbox**: `.reviews/` is excluded from the main repository via `.git/info/exclude` and maintains its own standalone Git repository (`git init`).
2. **Editor Integration (`emacsclient`)**: The agent or CLI helper (`tools/review_tool.py`) stages files into `.reviews/`, commits the baseline, and launches `emacsclient -n`.
3. **Direct Edits & Inline Directives (`>>`)**: The human directly edits lines or writes inline review instructions (`>> ...` or `// >> ...`) without risk of breaking parallel builds.
4. **Automated Harvesting**: The agent inspects `git -C .reviews diff`, applies edits and directives to the authentic codebase, runs tests, and live-reloads the Emacs buffer.

## ASPICE Process Capability Level 1–5 Governance Matrix (ISO/IEC 33020 / PAM)

Höchstmaß systematically maps Automotive SPICE Process Attributes (`PA 1.1` through `PA 5.2`) to deterministic AI pair-programming and verification mechanisms *(**Assessed status 2026-11-21: Capability Level 1 partially achieved; Level 2 is `[Target / Not Yet Claimed]`** — see [Assessed Capability Profile](#assessed-capability-profile-2026-11-21) below. Level 3–5 entries remain `[Target / Not Yet Claimed]` pending verified CL2 attainment. The Level 2 row describes the target mechanisms, not an attained level.)*:

| Capability Level | Process Attribute (PA) | ISO/IEC 33020 Definition | Höchstmaß Operational Implementation / Target Status |
| :--- | :--- | :--- | :--- |
| **Level 1: Performed** `[Partially Achieved — 9/15 processes]` | **PA 1.1: Process Performance** | The process achieves its defined outcomes. | **6-Stage ASPICE Lifecycle**: Stages 1–5 execution (`SWE.1`–`SWE.6` sub-tasks) + Stage 6 Cold Final Review (`Final Review (Agent)`); atomic squash-merge per ticket; verifiable artifact deliverables at each stage boundary. |
| **Level 2: Managed** `[Target / Not Yet Claimed]` | **PA 2.1: Performance Management** | Process performance is planned, monitored, and adjusted. | **Sprint Capacity Envelope**: Capacity bounding (~24–30 SP across 12–15 atomic tickets); story point estimation split guardrail ($\le 3$ SP); autonomous in-sprint zipper ping-pong; velocity monitoring. |
| | **PA 2.2: Work Product Management** | Work products are documented, controlled, and integrity is maintained. | **Rule 67 Canonical Deliverables**: Standardized `docs/engineering/` directories; living documentation sharding and master index parity (`verify_requirement_governance.py`); strict branch isolation (`feature/` $\rightarrow$ `sprint/` $\rightarrow$ `develop`). |
| **Level 3: Established** `[Target / Not Yet Claimed]` | **PA 3.1: Process Definition** | A standard process is established across the organization. | **`[Target / Not Yet Claimed]` — Document Triad & Skills**: Universal 9 Core In-Sprint Governance Rules (`aspice_governance.md`); 11 parameterized agent skills (`SKILL.md`); standardized deliverable templates; coding standards and design guidelines. |
| | **PA 3.2: Process Deployment** | The standard process is systematically deployed and enforced. | **`[Target / Not Yet Claimed]` — Tooling Enforcement & Role Isolation**: Automated CLI enforcement (`tools/jira_util.py`); pre-warmed prompt injection; separate role accounts and API tokens (`agent1`, `agent2`, `coordinator`, `human`). |
| **Level 4: Predictable** `[Target / Not Yet Claimed]` | **PA 4.1: Quantitative Analysis** | Quantitative objectives are established and measured. | **`[Target / Not Yet Claimed]` — Quantitative Sprint Scorecard**: Automated aggregation of Gate 1–5 First-Time-Pass-Rates (FTPR); empirical Swiss Cheese escape rates ($p_1 \dots p_5$); terminal Human Acceptance Rate; historical persistence in `docs/engineering/metrics/process_metrics.jsonl`. |
| | **PA 4.2: Quantitative Control** | Process is statistically controlled to achieve predictability. | **`[Target / Not Yet Claimed]` — Statistical Process Control (SPC)**: Bounded `[0, 100]` p-chart / clamped control bands ($UCL$, $LCL$) partitioned by `process_version`; automated detection and warning of out-of-control conditions (Special Cause Variations) in Ceremony 2. |
| **Level 5: Innovating** `[Target / Not Yet Claimed]` | **PA 5.1: Process Innovation** | Continuous, systematic evaluation of process innovations. | **`[Target / Not Yet Claimed]` — AI Architectural Innovations**: Multi-model adversarial reviews (intra-sprint `pro` $\leftrightarrow$ `flash` plus cross-vendor **Claude** & **ChatGPT**); isolated in-place `.reviews/` Emacs sandbox; dynamic contract tests. |
| | **PA 5.2: Process Optimization** | Continuous closed-loop improvement based on quantitative feedback. | **`[Target / Not Yet Claimed]` — Closed-Loop Ceremony 2 Retro Countermeasures**: Mandatory root-cause analysis (RCA) and concrete process/skill adjustments in the Retrospective prior to sprint merge whenever a PA 4.2 Special Cause Variation is detected. |

### Assessed Capability Profile (2026-11-21)

Source: independent dual-vendor ASPICE 4.0 assessment of `develop` @ `d5960809` — Anthropic Claude pass ([`2026-11-21_claude_aspice_audit.md`](engineering/aspice_audits/2026-11-21_claude_aspice_audit.md)) and Google Gemini pass ([`2026-11-21_dual_vendor_aspice_audit.md`](engineering/aspice_audits/2026-11-21_dual_vendor_aspice_audit.md)); remediation in Epic `ATT-9494`. Both passes conclude **CL2 is not attained**. The stricter (Claude) rating is used as the claimed status.

| Process | Claimed CL (Claude) | Gemini CL | Key blocker to next level |
| :--- | :---: | :---: | :--- |
| SYS.2 System Requirements Analysis | 1 | 1 | Stakeholder requirements, PO agreement (`ATT-9521`, `ATT-9501`) |
| SYS.3 System Architectural Design | 1 | 1 | System mode model, rationale, PO agreement (`ATT-9521`, `ATT-9501`) |
| SYS.4 System Integration & Integration Verification | **0** | 0 | Real integration verification per SYS-IF on the APK (`ATT-9520`) |
| SYS.5 System Verification | **0** | 0 | Execute attested Pixel 10 field runs (`ATT-8423`..`ATT-8434`) |
| SWE.1 Software Requirements Analysis | 1 | 1 | Requirement structure & agreement (`ATT-9522`, `ATT-9501`) |
| SWE.2 Software Architectural Design | 1 | 1 | Architecture/code conformance (`ATT-9523`) |
| SWE.3 Software Detailed Design & Unit Construction | 1 | 1 | Living detailed design (`ATT-9523`), static analysis (`ATT-9503`) |
| SWE.4 Software Unit Verification | 1 | 1 | Result-based `[Verified]` status, regression selection (`ATT-9515`, `ATT-9518`) |
| SWE.5 Software Component & Integration Verification | **0** | 1 | Primary, untruncated integration results (`ATT-9497`) |
| SWE.6 Software Verification | **0** | 0 | Release-level SW verification record (`ATT-9495`) |
| MAN.3 Project Management | **0** | 1 | V5.0.0 release plan, risk re-scoring (`ATT-9524`) |
| SUP.1 Quality Assurance | **0** | 1 | QA independent of authoring/gate roles (`ATT-9517`) |
| SUP.8 Configuration Management | 1 | 1 | Commit attribution, baseline/evidence integrity (`ATT-9525`, `ATT-9500`) |
| SUP.9 Problem Resolution Management | 1 | 1 | Rejection/defect feedback into `[Verified]` status (`ATT-9516`) |
| SUP.10 Change Request Management | 1 | 1 | Recorded human approval of changes (`ATT-9516`, `ATT-9501`) |

**Claim rule:** This profile SHALL only be raised on the basis of a subsequent independent assessment record in `docs/engineering/aspice_audits/` and PO sign-off; agents SHALL NOT upgrade claimed levels on their own.

## Two-Tier Device Verification Policy (ATT-6506 / REQ-TST-004)
To ensure 24/7 autonomous agent execution without disrupting the Product Owner's physical daily driver smartphone (`Pixel 10`), a strict Two-Tier Device Policy is enforced across all tooling and governance:
1. **In-Sprint Per-Ticket Validation (Phase 2, Stages 1–5)**: Autonomous agents execute on-device verification exclusively against an emulated device (`emulator-5554`, `Pixel_8_Android_15` via `tools/agent_emulator.py`). Desk simulation tools (`fake_gps.py`, `simulate_kill.py`, `replay_workout.py`) enforce physical phone guardrails and abort if targeting physical USB hardware in agent mode.
2. **Full-Sprint Integrated Evaluation (Ceremony 2, Sprint Review)**: The integrated release candidate on `sprint/<SPRINT_NAME>` is built and verified on the **real physical phone** (`Pixel 10`, `ANDROID_SERIAL=<physical_serial> ./gradlew installDebug`), guaranteeing real hardware validation before sprint completion.

## Problem Resolution & Commit Hygiene (`SUP.9` / `REQ-PRO-121`)
* **Every Fix Has a Problem Record**: Post-cutoff commits (including hotfixes and Tier-2 fixes) MUST reference an `ATT-\d+` ticket key (`.githooks/commit-msg`, `verify_requirement_governance.py --check-commit-hygiene`). Unresolved Git conflict markers are blocked locally (`.githooks/pre-commit`) and in CI (`--check-conflict-markers`). Activate local hooks via `git config core.hooksPath .githooks`.
* **SUP.9 Resolution States & `process-escape` Tracking**: Bugs support explicit resolution states (`Rejected`, `Duplicate`, `Cannot Reproduce`, `Fixed` via `jira_util.py resolve-bug`), require fix verification before `Final Review (Human)`, and track gate/Tier-2 escapes via deduplicated `process-escape` Bugs (`file-process-escape`).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added `DOC-GOV-PROTOCOL` header and reconciled `9 Core Rules`, `Rules 1–85+`, and `6-Stage` lifecycle (`Stages 1–5` + `Stage 6`). |
| `1.0.1` | 2026-11-17 | AI Agent (`ATT-8165`) | `ATT-8165` | Documented SUP.9 commit-key/conflict-marker hooks, bug resolution states, and `process-escape` problem records (`REQ-PRO-121`). |
| `1.0.2` | 2026-11-17 | AI Agent (`ATT-8168`) | `ATT-8168` | Suspended unsupported CL3–CL5 claims as `[Target / Not Yet Claimed]` pending verified CL2 attainment (`REQ-PRO-122.6`). |
| `1.0.3` | 2026-11-21 | Independent ASPICE Assessor (Anthropic Claude Opus 5.5) | `ATT-9494` | Interactive-PO: replaced unsubstantiated "Level 1–2 active baseline" claim with assessed status (CL1 partially achieved, CL2 `[Target / Not Yet Claimed]`); added per-process Assessed Capability Profile from the 2026-11-21 dual-vendor audit and claim rule. |
