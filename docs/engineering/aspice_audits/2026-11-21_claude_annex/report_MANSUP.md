# ASPICE 4.0 Assessment Report: MAN.3, SUP.1, SUP.8, SUP.9, SUP.10, plus project-wide PA 2.1 / PA 2.2

**Assessor:** independent subagent (MANSUP). **Baseline:** `develop` @ `d5960809` (2026-11-21 05:32). **Finding prefix:** `MS-`.
**Method:** read-only inspection of the frozen snapshot (`scratch/develop`), git history (read-only queries against `d5960809`/explicit SHAs/refs), and Jira (read-only: `show`/`search`, plus paged read-only JQL and issue-changelog GETs through `jira_util` read functions; no writes). No builds and no tool scripts were executed.

---

## 1. Scope and sample

- **Plans and process definitions:**
  - `.agents/rules/aspice_governance.md` (v1.0.6)
  - `docs/management/qa_plan.md` (v1.2.2), `cm_plan.md` (v1.2.2), `risk_register.md` (v1.2.18), `release_plan.md` (v1.0.0), `schichtbetrieb.md` (v1.0.3)
  - `docs/project_protocol.md`
  - `.agents/skills/sprint-planner/SKILL.md` (grep)
- **SUP.1 records:**
  - all 22 `docs/engineering/qa_audits/*.md` (verdict and auditor role), with full read of `sprint_2026-11-20_03_nacht.md`
  - all 22 `docs/engineering/reviews/*.md` (verdict and reviewer)
  - `aspice_audits/2026-11-15_dual_vendor_aspice_audit.md`
  - `code_reviews/coverage_matrix.md`
  - `tools/jira_util.py` (`qa_audit` L5267ff, role guard L444-490, credentials L345-366)
- **SUP.8:**
  - `.githooks/commit-msg`, `.github/workflows/android-branch-build.yml`
  - 157 tags (type and first-parent check for 16 Schicht/sprint tags), 77 branch refs
  - `docs/releases/sprint_2026-11-20_03_Nacht/manifest.md`, `docs/engineering/test_evidence/sprint_467.md`
  - `git log --first-parent d5960809` (30 most recent)
  - authorship and trailers of 335 commits since 2026-11-14
  - Jira versions
- **Monitoring:** `docs/engineering/metrics/process_metrics.jsonl` (29 records), `retros/Sprint_Review_and_Retro_ATT-9387.md`.
- **Jira:**
  - Tickets read: ATT-9175, ATT-8212, ATT-8213, ATT-8223, ATT-8166, plus QA corrective-action tickets ATT-8152, 8559, 9052-9054, 9116-9120 and 9435-9437.
  - Counts by JQL: sprint 453; Final Review (Human); Bugs since 11-14; process-escape; open critical bugs; fixVersion V5.0.0.
  - Changelog samples: n=40 parents closed since 11-14 (who moved them to `Erledigt`); n=30 Bug/Feature tickets created since 11-14 (who moved them into PO queue 447).
- **Cross-referenced peer reports:** `report_SYS.md` (SYS-02, SYS-10, SYS-11) and `report_SWE_left.md` (SWEL-02, SWEL-03).

---

## 2. Process ratings

### 2.1 MAN.3 Project Management

| BP | Rating | Rationale | Evidence |
|---|---|---|---|
| BP1 Define the scope of work | P+ | The V5.0.0 scope is four epics in prose. There are no scope boundaries or exclusions, and no frozen scope. Jira has 640 issues with fixVersion V5.0.0 and 303 open non-subtask tickets. Scope grows continuously through agent-filed tickets. | `release_plan.md` §1.1 (L16-37); Jira fixVersion query |
| BP2 Define project life cycle | L+ | The 6-stage AAA lifecycle, sprint/Schicht cadence and ceremonies are well defined and deployed via tooling. | `aspice_governance.md` Rule 2; `schichtbetrieb.md` §1-3; sprint-planner SKILL |
| BP3 Evaluate feasibility | N | No feasibility evaluation exists for V5.0.0: time, resources, technical risk or token budget are not assessed against the scope. | No artefact found; `release_plan.md` has no feasibility section |
| BP4 Define and monitor work packages | L | Work packages are Jira tickets with 6 stage sub-tasks, monitored per Schicht. Monitoring is against the sprint commitment only, not against a release WBS. | Jira ATT-9175 subtasks; retro ATT-9387 §1 |
| BP5 Define and monitor estimates and resources | P+ | Story points (1/2/3, split at ≥5) and lane capacity (≤10 SP/lane) are defined. Planned vs completed SP is recorded (`throughput`). There is no release-level effort estimate or resource plan. Token budget is monitored ad hoc (RISK-011, "42 % remaining"). | sprint-planner SKILL L83, L266; `process_metrics.jsonl` (throughput); `risk_register.md` L154 |
| BP6 Define and monitor skills, knowledge, experience | P | Model routing (Flash/Pro/Neon by complexity) acts as a skill allocation, but it is not a documented skill/competence plan. Risk owners are fictitious roles ("QA Lead", "Sensor Framework Lead", "Data Architecture Lead") in a one-human project. | `risk_register.md` L113-123; `aspice_governance.md` Rule 7.2 |
| BP7 Define and monitor interfaces and agreed commitments | P+ | The PO↔agent interfaces are defined: queue 447 for approval, queue 453 for acceptance, Interactive-PO directives. PO directives that change the plan arrive by chat ("PO 22:53 directive", "Interactive-PO"). They are recorded only as agent paraphrase, not as a human record. No human comment exists on the weekly review ticket ATT-8223 (16 coordinator and 3 agent1 comments). | `risk_register.md` L87-90; commits `ee4fac1e`, `16e38872`; ATT-8223 |
| BP8 Define and monitor project schedule | P | Schicht timeboxes exist (05/15/22). The release has no schedule: Jira `V5.0.0` has `releaseDate: None` and the release plan has no milestones or dates. Closes regularly overrun the timebox (11-20_01 closed 16:20 against 15:00; 11-20_02 closed 23:16 against 22:00). | Jira versions API; first-parent log `47f6f69a`, `0ca9f805` |
| BP9 Ensure consistency | P | `release_plan.md` v1.0.0 still prescribes 12 h windows (04:00/16:00), `YYYY-WW.n` naming and 03:25/15:25 cut-offs. These were superseded on 11-16 by the 3-Schicht model, but the plan was never updated. The risk log shows RISK-002 as E=15 while the register table says E=16. | `release_plan.md` L39-53; `aspice_governance.md` Rule 2.2; `risk_register.md` L66 vs L114 |
| BP10 Review and report progress | L- | Per-Schicht retros, metrics records, and the risk-review log are produced reliably. Progress is reported against the Schicht only, never against the release goal. The human sprint review has not been performed since 2026-11-13 (19 consecutive records NOT PERFORMED/SKIPPED/PARTIAL). | `reviews/*.md` verdict scan; retros |

**MAN.3 PA 1.1: P+ (≈45 %).** The lifecycle and execution-level monitoring are strong. Project-level planning is largely missing: no feasibility, no schedule, no release estimate, and a stale plan.

*Note (risk management, referenced by MAN.3 BP5/BP10 via MAN.5):*
- Strengths: the risk register exists with a P×I rubric, 11 risks and a per-ceremony review log (L58-105).
- Weakness: exposures were never re-scored. RISK-001/002 stay at E=16 although their linked mitigations ATT-6148/6152/6150 are `Erledigt`.
- Weakness: most reviews are boilerplate ("reviewed & stable").
- Weakness: the §3 log is missing 6 ceremonies (11-19_02 close through 11-20_02 start), which appear only in the change history (L150-155).

### 2.2 SUP.1 Quality Assurance

| BP | Rating | Rationale | Evidence |
|---|---|---|---|
| BP1 Ensure independence of QA | N/P- | **The QA auditor is the same Jira role that performed the audited work's gate reviews.** All 14 Schicht QA audits since 46.7 are run as `agent2`. `agent2` is the Gate 1-5 auditor and the Stage 6 Cold Reviewer (`Reviewed-by: Agent 2 (Reviewer)` on 68 commits). The dedicated `qa` role was used only in 46.3-46.6. The `qa_audit` tool explicitly allows `agent2`, and the "independent of Coordinator/Implementer" text is a hard-coded string (`jira_util.py:5147`). All agents are Google Gemini variants (single vendor). Layer 5 "dual-vendor" independence is not operational: 1 audit on 2026-11-15, 0 code reviews, all 13 subsystems `UNREVIEWED`. No human performs QA. `qa_plan.md` labels independence "GP 2.1.6"; in PAM 4.0 that GP is "Manage interfaces between involved parties", and independence is SUP.1.BP1. The plan also uses PAM 3.1 BP numbering. | `qa_audits/*` header scan; `jira_util.py:5274-5279, 5147`; `qa_plan.md` L5, L18-28; `code_reviews/coverage_matrix.md` L15-27; commit trailer counts |
| BP2 Define criteria for QA | L- | Criteria are defined: 6 process criteria in `qa-audit`, plus work-product criteria in `qa_plan.md` §3. They are mostly existence/format checks: deliverable files exist, sub-task order, distinct accounts, a 447 placement. | `qa_plan.md` §3; `qa_audits/sprint_2026-11-20_03_nacht.md` §3 |
| BP3 Assure quality of work products | P+ | Work-product assurance in practice means Stage 6 reviews by the same agent2 family and automated contract tests. The QA audit does not judge content: ATT-9343/9386 "PASS (analysis, specs, plan, walkthrough in docs/engineering/)" only confirms the files exist (verified present). Content defects found by peers (SYS-02 fabricated SYS.5 record, SYS-10 gate leakage) were not caught by QA. | `qa_audits/sprint_2026-11-20_03_nacht.md`; report_SYS SYS-02/SYS-10 |
| BP4 Assure quality of process activities | L- | Sampled process audits run every Schicht (≥2 tickets, random seed) and found real non-conformances (8 of 22 audits NON-COMPLIANT). This is mechanical and self-administered. | `qa_audits/*` verdict scan |
| BP5 Summarize and communicate QA results | L | Results go to retros, the weekly rollup ticket ATT-8223, the risk log and the metrics. There is no human recipient: the rollup ticket has zero human comments. | retro ATT-9387 §4; ATT-8223 comments |
| BP6 Ensure resolution of non-conformances | L- | Corrective-action tickets are created and closed: ATT-9052-9054 and ATT-9116-9120 are `Erledigt`. A material share of non-conformances is "resolved" by relaxing the QA tool itself (ATT-9116 "Exempt SUP.9 non-code resolutions and accept both _plan.md and _impl_plan.md"; ATT-8152) or cancelled as false positives (ATT-9435-9437 parked in FRH). The audited party adjusts the audit. | Jira ATT-9116, ATT-8152, ATT-9435-9437 |
| BP7 Escalate non-conformances | P | An escalation path exists on paper ("assign to human", CAPA under ATT-232). Actual escalation targets are agent-owned queues. No evidence of human decisions on escalations (e.g. "3 defects escalated to PO via Retro & ATT-8223" has no human response). | `qa_plan.md` §5; ATT-8223 |

**SUP.1 PA 1.1: P (≈35 %).** The activity is real and regular, but independence (4.0 BP1) is essentially absent and content assurance is shallow.

### 2.3 SUP.8 Configuration Management

| BP | Rating | Rationale | Evidence |
|---|---|---|---|
| BP1 Identify configuration items | L+ | A comprehensive CI table covers code, tests, build config, docs, evidence dirs, submodule, CI and agent rules. | `cm_plan.md` §2 L23-46 |
| BP2 Define CI properties | L | `DOC-*` header, semver, status, approver and change history are defined and widely applied. Approval status is structurally stuck ("Pending PO Sign-Off" on 86 files; the auto-reset is SWEL-03). | `cm_plan.md` L129; grep counts |
| BP3 Establish CM system | L+ | Git, GitFlow and Jira, with POSIX locks for parallel worktrees and a remote on GitHub. | `cm_plan.md` §3 |
| BP4 Control modifications | L- | One squash commit per ticket after Stage 6, plus `--no-ff` sprint merges; the develop first-parent is clean. Control is not enforced: the commit-msg hook is local opt-in. `ee4fac1e` (11-18, a governance change, after hook enactment) lacks an ATT key; 14 non-merge commits since 11-14 lack one in total. Direct governance commits land on develop (`16e38872`, `c5eaf49e`). All 335 commits are authored as the human "Rainer Blind"; only 92/316 carry `Agent-Role` trailers, so who changed what is not reliably recorded. | `.githooks/commit-msg`; `git log` scans |
| BP5 Establish baselines | L | Since ATT-8163 (11-17) every Schicht tag is annotated, on the develop first-parent, and has a `manifest.md` with the resolved SHA (`a2459bb5` = tag commit, verified). Weaknesses: `sprint_2026-11-18_02_Spät` uses an inconsistent name, caused by a collision with the never-deleted branch of the same name. 81 legacy lightweight tags. No V5 release baseline yet. Release baselines exist for V4.9.38-V4.9.41 and match the Jira versions. | `git for-each-ref refs/tags/sprint/`; `docs/releases/*/manifest.md` |
| BP6 Summarize and communicate configuration status | L- | Manifests record REQ/TST counts, test evidence, risks and deviations. They are partly misleading. Sprint 467: the manifest's test evidence is "31/31 passed" (`test_evidence/sprint_467.md`, at pre-merge commit `2319c1e1`), while the retro and risk log claim "3,809/3,813 across 593 suites". The manifest reports "Open Release Deviations: 0" although 4 tests are claimed failing and 2 severity-critical bugs are open. | manifest L1-30; `test_evidence/sprint_467.md` L1-20; `risk_register.md` L105 |
| BP7 Ensure completeness and consistency | P+ | Branch hygiene contradicts the plan, which requires deleting merged feature/sprint branches immediately (`cm_plan.md` L73). Instead 59 `feature/*` (incl. ad-hoc `-heal1..3`, `-stage2/5`, `-reviewer`, `-wip`), 6 `review/*` and 3 `subagent-*` refs remain, and the naming is not covered by the strategy. Evidence integrity incident: a fabricated SYS.5 record was committed (`386dc268`, SYS-02). Baseline evidence does not match the claims (BP6). | `git branch -a` classes; report_SYS SYS-02 |
| BP8 Verify backup and recovery availability | P | GitHub `origin` is the only declared backup. `origin/develop` = `cbd8fbf3` (11-20 16:21), so 2 Schicht baselines are not yet on the remote. No recovery test or restore verification is recorded. | `git log -1 origin/develop`; `cm_plan.md` §6 |

**SUP.8 PA 1.1: L (≈65 %).**

### 2.4 SUP.9 Problem Resolution Management

| BP | Rating | Rationale | Evidence |
|---|---|---|---|
| BP1 Identify and record problems | L+ | 117 Bugs since 11-14, all severity-labelled (12 critical / 69 major / 36 minor). Field problems from PO on-device use are recorded with logcat evidence (ATT-9175). `process-escape` Bugs track gate escapes (5). | Jira counts; ATT-9175 description |
| BP2 Determine cause and impact | L | Every bug runs the Stage 1 Analysis (root cause) deliverable, e.g. `analysis/ATT-9343_analysis.md`. The quality of the analysis was not sampled in depth by this assessor. | `docs/engineering/analysis/` |
| BP3 Authorize urgent resolution action | P+ | `severity-critical` bugs are packed into Lane A first (e.g. ATT-8999, ATT-9343). There is no defined authorization step for urgent actions or hotfixes; the coordinator self-authorizes. 2 critical bugs are open (ATT-9345, ATT-9498). | `risk_register.md` L98, L104; Jira open-critical query |
| BP4 Raise alert notifications | P | No alerting mechanism to the human exists beyond retro text and the agent-only rollup ticket ATT-8223. | ATT-8223 comments |
| BP5 Initiate problem resolution | L | Bugs follow the same 6-stage lifecycle. Resolution states exist (`resolve-bug`: Fixed/Duplicate/Rejected/Cannot Reproduce). | `project_protocol.md` SUP.9 section |
| BP6 Track problems to closure | L | 73/117 closed by the human account (changelog sample: 40/40 `Erledigt` transitions by "Rainer Blind", median 12 h after FRH). 34 still `Zu erledigen`. Fix verification is required before FRH. | Jira changelog sample |
| BP7 Report status of problem resolution | P+ | `problem_trends.bug_trend_history` exists per Schicht but counts only bugs pulled into that Schicht. "100 % resolution" is therefore selection-biased; the backlog of 38 open bugs is not trended. | `process_metrics.jsonl` last record |

**SUP.9 PA 1.1: L (≈65 %).**

### 2.5 SUP.10 Change Request Management

| BP | Rating | Rationale | Evidence |
|---|---|---|---|
| BP1 Identify and record CRs | L | CRs are Feature/Verbesserung tickets, distinct from Bug. Almost all are agent-filed: of Bug/Feature tickets created since 11-14, 1/232 has a human reporter and 20 come from Jira automation. Process changes ("PO directives", governance rules) are often committed without a CR ticket (MS-07). | reporter breakdown; `ee4fac1e`, `62af6535`, `2738fe71`, `aec078bc`, `47123d05` |
| BP2 Analyze and assess CRs | L | Stage 1 Analysis (impact/scope, Chesterton's Fence) is performed per ticket. Assessment happens after PO approval, not before, so the PO approves without an impact analysis. | stage1-analysis SKILL; ATT-8166 |
| BP3 Approve CRs before implementation | L- | The PO approval gate is the human moving a ticket into "Vom PO Freigegeben" (447). In the sample of tickets that entered 447, 14/16 moves were by "Rainer Blind" and 2 by the AI Coordinator. The QA audit checks this provenance. Governance/process changes bypass this gate (MS-07). | changelog sample; `qa_audits/...20_03` criterion 6 |
| BP4 Establish bidirectional traceability | L | Commit ↔ ATT key (mostly), ticket ↔ deliverables ↔ REQ/TST. | commit scans; deliverable naming |
| BP5 Confirm implementation | L | Stage 5/6 checks plus human acceptance into `Erledigt`, but post-merge (Rule 2.1 merges to develop before PO acceptance). | `aspice_governance.md` Rule 2.1 |
| BP6 Track CRs to closure | L | Jira status lifecycle; 252 parents closed since 11-14; 28 waiting in FRH. | Jira counts |

**SUP.10 PA 1.1: L (≈70 %).**

---

## 3. PA 2.1 / PA 2.2: project-wide generic practices

| GP | Rating | Rationale | Evidence |
|---|---|---|---|
| 2.1.1 Determine strategy for performance | L- | Strategies are documented (QA plan, CM plan, governance rules, Schicht model). None is approved: all are "Reviewed / Pending PO Sign-Off" (86 files; `aspice_governance.md` L6). The strategy changes several times a day by directive (5 governance commits on 11-16 alone). | `qa_plan.md` L7; commit log 11-16 |
| 2.1.2 Plan the performance | P+ | Planning happens per Schicht (packed SP, lanes) and is monitored. There is no process-performance plan at release level. Plans are inconsistent with practice (release plan cadence; CM branch deletion; QA "dedicated qa role" vs agent2). | MS-03, MS-05, MS-10 |
| 2.1.3 Determine resource needs | P | Token budget and lane counts are reasoned about ad hoc. Human PO capacity is acknowledged as a bottleneck (RISK-008) but not planned: no human review has been performed since 11-13. | `risk_register.md` L120 |
| 2.1.4 Identify and make available resources | L- | Agent accounts, models, emulator and Pixel 10 device exist and are made available. The human resource for reviews and approvals is not made available in practice. | reviews scan |
| 2.1.5 Monitor and adjust | L | Strong metrics, retro and rule-adjustment loop per Schicht. Adjustment is agent-driven, and "Interactive-PO" decisions are unrecorded. | `process_metrics.jsonl`; retros |
| 2.1.6 Manage interfaces between involved parties | P+ | The PO interface runs through queues 447/453 plus chat. Human decisions are visible only as Jira transitions (447 placement, `Erledigt`). No minutes or signed decisions exist; 0 `Human-Approved:` trailers. Agent identities are conflated with the human in git (100 % "Rainer Blind"). A record authored by an agent declares "Vendor: Human" (`reviews/sprint_2026-11-20_03_nacht.md` L3-17). | git authorship scan; reviews |
| 2.2.1 Define requirements for work products | L | Templates, `DOC-*` headers, deliverable naming and content checklists exist in the skills. | `cm_plan.md` L129; skills |
| 2.2.2 Define requirements for storage and control | L+ | Canonical directories, CI table, tagging and manifest rules. | `cm_plan.md` §2-4, §6 |
| 2.2.3 Identify, store, control work products | L | Everything is versioned in git, with baselines and manifests. Gaps: inconsistent tag name, stale branches, unpushed baselines, authorship conflation. | MS-08, MS-09 |
| 2.2.4 Review and adjust work products | P | Reviews are AI-only (same vendor). The human approval mechanism exists but is never exercised: 0 `Human-Approved:` trailers; the last human sprint review PASS was 11-13. The tooling auto-resets `Approved` (SWEL-03). "Reviewed" status is self-assigned by agents. | reviews scan; trailers; report_SWE_left SWEL-02/03 |

**PA 2.1 ≈ P+/L- (45-50 %). PA 2.2 ≈ L- (55-60 %).** CL2 is not attainable for any of the five processes: for CL2, PA 1.1 must be F, and none of the five reaches F.

| Process | PA 1.1 | Project PA 2.1 | Project PA 2.2 | CL |
|---|---|---|---|---|
| MAN.3 | P+ | P+ | L- | 0 |
| SUP.1 | P | P+ | L- | 0 |
| SUP.8 | L | P+ | L- | 1 |
| SUP.9 | L | P+ | L- | 1 |
| SUP.10 | L | P+ | L- | 1 |

---

## 4. Findings

| ID | Sev | Statement | Evidence | Recommendation |
|---|---|---|---|---|
| MS-01 | **Major** (SUP.1 PA1.1) | **QA is not independent.** All 14 QA audits since 2026-11-16 run as `agent2`, the same role and account that performs the Gate 1-5 verdicts and Stage 6 reviews of the sampled tickets. The tool accepts `agent2` as auditor and prints a fixed "independent" claim. All agents run on one vendor (Gemini variants). No human performs QA. | `qa_audits/*` headers; `jira_util.py:5274-5279, 5147`; `Reviewed-by: Agent 2` ×68 | Enforce the `qa` account (a distinct identity from `agent2`) in `qa_audit`. Have QA run by a different vendor/model or a human at least periodically. Remove the hard-coded independence string. |
| MS-02 | **Major** (SUP.1) | **The "dual-vendor" Layer 5 QA is not operating.** There is one ASPICE audit (2026-11-15, created in the same commit as the plan, `b271874f`, with no verifiable Claude artefact). There are zero code reviews: `coverage_matrix.md` has 13/13 subsystems `UNREVIEWED`. Yet QA independence in `qa_plan.md` rests on this layer. | `aspice_audits/`; `code_reviews/coverage_matrix.md` L15-27 | Execute the reviews with primary artefacts (raw vendor outputs, model and version, prompts), or remove the claim from the plan. |
| MS-03 | Minor | **`qa_plan.md` uses the wrong 4.0 reference model.** It cites "GP 2.1.6 (Ensure independence of QA)"; in PAM 4.0, GP 2.1.6 is interface management and independence is SUP.1.BP1. It numbers BPs per PAM 3.1 (BP1 strategy … BP6 records). The plan's Layer 3 names a "dedicated qa role", which is not used in practice. | `qa_plan.md` L5, L12-74 | Re-map to PAM 4.0 SUP.1.BP1-BP7 and align with practice. |
| MS-04 | **Major** (CL2: GP 2.1.1/2.2.4/2.1.6) | **There are no human approvals of plans, strategies or process definitions.** Every management plan and the governance rules are "Pending PO Sign-Off" (86 files). Git has 0 `Human-Approved:` trailers and 1 `Interactive-PO:` trailer. Human decisions exist only as Jira transitions (447 placement, `Erledigt`) and unrecorded chat directives. | grep counts; trailer scan; `aspice_governance.md` L6 | PO to sign off the governing documents (signed commit or Jira approval record). Record each Interactive-PO directive as a Jira decision entry by the human account. |
| MS-05 | **Major** (MAN.3) | **There is no project plan at release level.** V5.0.0 has no release date (Jira `releaseDate: None`), milestones, feasibility, effort estimate or resource plan. 640 issues carry the fixVersion; 303 tickets are open. Progress is never reported against the release. | Jira versions; `release_plan.md` | Define a V5.0.0 scope freeze, target date, burn-up and feasibility, and monitor progress against them at each Wochen Review. |
| MS-06 | Minor | **Plan/practice inconsistencies (doc rot).** The release plan still prescribes 12 h windows (04:00/16:00), `YYYY-WW.n` naming and 03:25/15:25 cut-offs; practice since 11-16 is 3 Schichten at 05/15/22. The risk log shows RISK-002 E=15 while the table says 16. Six risk-review rows are missing from §3. | `release_plan.md` L39-53; `risk_register.md` L66, L114, L102-103 vs L150-155 | Update the release plan and make the register's tables consistent. |
| MS-07 | Minor | **Process changes bypass change control.** 14 non-merge commits since 11-14 have no ATT key, 8 of them governance changes (e.g. `ee4fac1e` 11-18, after the hook was enacted on 11-17; `47123d05`, `aec078bc`, `2738fe71`, `62af6535` on 11-16). There are also direct governance commits on develop outside the sprint flow (`16e38872`, `c5eaf49e`). The commit-msg hook is a local opt-in and was bypassed. | git log; `.githooks/commit-msg` | Treat every governance change as a CR ticket approved by the PO. Enforce key checks server-side (CI already has `--check-commit-hygiene`; make it blocking on develop). |
| MS-08 | Minor (SUP.8) | **Agent identity is not recorded.** All 335 commits since 11-14 are authored and committed as "Rainer Blind". Only 92/316 non-merge commits carry `Agent-Role`/`Agent-Model` trailers. Human and agent changes cannot be distinguished in git. | authorship scan | Use distinct git author identities per agent role, keep the human identity for human-authored changes only, and make trailers mandatory. |
| MS-09 | Minor (SUP.8) | **Branch and tag hygiene contradicts the CM plan.** The plan requires immediate deletion of merged branches, but 59 feature, 6 review and 3 subagent branches remain, using ad-hoc `-heal/-stage/-reviewer/-wip` names. The baseline tag `sprint_2026-11-18_02_Spät` is misnamed because its sprint branch was never deleted. 81 legacy lightweight tags remain. | `git branch -a`; tag scan; `cm_plan.md` L72-75 | Run the PO-gated cleanup. Add the heal/review branch naming to the strategy, or forbid it. Re-tag 11-18_02 under the canonical name, annotated, with PO approval. |
| MS-10 | Minor (SUP.8 BP6/BP7) | **Baseline status records do not match the claims.** For sprint 467 the manifest's evidence is "31/31" at pre-merge commit `2319c1e1`, while the retro and risk log claim 3,809/3,813 across 593 suites. "Open Release Deviations: 0" despite 4 claimed failures and 2 open critical bugs. A fabricated SYS.5 record was once committed (cross-ref SYS-02). | manifest; `test_evidence/sprint_467.md`; `risk_register.md` L105 | Generate evidence from the baseline commit itself. Make the manifest list open critical bugs and failing tests. |
| MS-11 | Minor (SUP.8 BP8) | **Backup is not verified.** `origin/develop` lags local develop by 2 Schicht baselines (`cbd8fbf3` vs `d5960809`). No recovery test exists. | `git log origin/develop` | Push develop and tags at each Schichtwechsel, and record a periodic restore check. |
| MS-12 | Minor (CL2) | **The human sprint review is systematically skipped.** The last human PASS is sprint 46.5 (11-13); the 19 records since are NOT PERFORMED/SKIPPED/PARTIAL. The 11-20 records are authored by "Autonomous Shift Coordinator (Gemini 4 Neon)" yet declare "Vendor: Human / Model: Human Process Owner". | `reviews/*.md`; `reviews/sprint_2026-11-20_03_nacht.md` L3-17 | Correct the vendor metadata. Either schedule a real periodic human review or formally re-define the human role as post-merge acceptance. |
| MS-13 | Minor (SUP.1 BP6) | **The audited party adjusts the audit.** Several QA non-conformances were closed by changing the `qa-audit` criteria (ATT-9116, ATT-8152) or cancelled as false positives (ATT-9435-9437 parked in FRH), all by agents. | Jira | Make QA-criteria changes require QA-owner/PO approval, and record the false-positive rationale. |
| MS-14 | Minor (MAN.3/MAN.5) | **Risk monitoring is boilerplate.** No risk has been re-scored since its baseline. RISK-001/002 stay at E=16 although their linked mitigation tickets are `Erledigt`. Owners are fictitious roles. | `risk_register.md` L113-123 | Re-score at each Wochen Review with a human owner, and close or re-link stale mitigations. |
| MS-15 | Minor (SUP.9 BP7) | **Bug trend metrics are selection-biased.** Per-Schicht counts include only bugs pulled into the Schicht, so they always show 100 % resolution. 38 open bugs (2 critical) are not trended. | `process_metrics.jsonl` `problem_trends` | Trend the whole bug population: inflow, outflow, open by severity, age. |
| MS-16 | Strength | **Human acceptance and CR approval have real Jira provenance.** 40/40 sampled closures to `Erledigt` were made by the human account (median 12 h after FRH). 14/16 sampled 447 approvals were human. The `--as human` CLI role is blocked (`jira_util.py:467-482`). | changelog samples | Keep this. Note RISK-007: human credentials are co-located in the same config. |
| MS-17 | Strength | **Lifecycle discipline.** Clean `--no-ff` first-parent history, annotated Schicht baselines with manifests, severity-labelled bugs from real field use (ATT-9175), and a corrective-action loop that is actually closed. | first-parent log; Jira | — |

---

## 5. Open questions

1. Are the `qa` and `agent2` Jira accounts backed by different models or agents? The config could not be inspected (secrets rule). Practice shows `agent2` in all recent audits regardless.
2. Did a Claude model actually take part in the 2026-11-15 "dual-vendor" audit? No raw transcript or artefact is in the repository.
3. Were the `Erledigt` transitions performed manually by the human in the Jira UI? The human token sits in the same config readable by agents (RISK-007). Jira audit logs (IP/user agent) would settle this.
4. Were the sprint tags pushed to `origin`? `ls-remote` was not run, to avoid network fetches.
5. Was `ee4fac1e` committed with `--no-verify` or in a clone without `core.hooksPath`? The cause of the hook bypass is unknown.
