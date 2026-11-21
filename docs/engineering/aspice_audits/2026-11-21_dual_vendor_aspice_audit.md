# Weekly Dual-Vendor ASPICE 4.0 Cross-Audit: 2026-11-21

**Document ID**: `DOC-AUD-20261121`  
**Version**: 1.1.0  
**Status**: Draft  
**Approver**: Pending PO Sign-Off  
**Date**: 2026-11-21  
**Governing Epic**: [ATT-9494](https://atrainingtracker.atlassian.net/browse/ATT-9494) (*ASPICE 4.0 Audit Remediation 2026-11-21 – Evidence Integrity, CL2 Attainment & V5.0.0 Unblocking*)  
**Prior Baseline**: [`2026-11-15_dual_vendor_aspice_audit.md`](2026-11-15_dual_vendor_aspice_audit.md) (`sprint/2026-46.7`, `c49efff3`)  
**Standard**: ASPICE 4.0 `SUP.1.BP1–BP6`, `GP 2.1.6`, `GP 2.2.4`, ISO/IEC 33020 (`REQ-PRO-096`)  
**Audited Baseline**: `develop` @ `d5960809` (`sprint/2026-11-20_03_Nacht`, Sprint 467, merged `2026-11-21T05:32:10+01:00`)  
**Dual-Vendor Attestation (`REQ-PRO-096` / `ATT-8426`)**:
* **Google Gemini Pass**: Completed `2026-11-21` (Independent Assessor Session, read-only forensic inspection of `develop` @ `d5960809` and Jira).
* **Anthropic Claude Pass**: Completed `2026-11-21` (Claude Opus 5.5, independent read-only assessment of `develop` @ `d5960809` and Jira; tracked in [ATT-9502](https://atrainingtracker.atlassian.net/browse/ATT-9502)). Full record: [`2026-11-21_claude_aspice_audit.md`](2026-11-21_claude_aspice_audit.md); reconciliation in §4 below. Record remains `Draft` pending PO acceptance of the reconciled ratings.
**Scope**: ASPICE 4.0 VDA Scope + `SYS.2`–`SYS.5` + `MAN.5` (Target: Capability Level 2)

---

## 1. Executive Summary & Capability Rating Matrix

Since the 2026-11-15 baseline audit, the team completed **`69/75`** remediation tickets under Epic `ATT-8138` with substantial engineering rigor (`INT-SWE-01..08` integration test suites, `1,265/1,265` executable-backed `[Verified]` TSTs, 11 JVM-measured NFR benchmarks, 4 code-grounded `SWE.3` state-machine specifications, and 194 PO-accepted tickets in queue `453`). Both **`SWE.5`** and **`SUP.1`** advanced from **CL 0 → CL 1**.

However, **ASPICE 4.0 Capability Level 2 (`PA 1.1 = F`, `PA 2.1 = L/F`, `PA 2.2 = L/F`) is NOT YET ATTAINED**, and **Release `V5.0.0` remains BLOCKED**, due to:
1. **`0/62`** governed `DOC-*` work products holding formal PO `Status: Approved` sign-off (`PA 2.2`).
2. Concrete `PA 1.1` regressions/gaps: corrupted Sprint `463–467` test-evidence archives (`7–31` tests archived), Layer-4 Human review attribution regression in Sprints `466–467`, unfiled `ForegroundServiceDidNotStartInTimeException` crashes in `8/12` KW47 emulator sweep runs (`ATT-9386`), a QA sign-off parser bug in `generate_release_verification_summary.py`, and `0/14` weekly dual-vendor code reviews executed.

| Process | 2026-11-15 | **2026-11-21 PA 1.1** | **PA 2.1 / 2.2** | **Current CL** | Key Evidence & Blocking Gaps |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **SYS.2 / SYS.3** System Req & Arch | *(unrated)* | **F** | L / L | **CL 1** *(CL2-ready)* | `21/21` `SYSREQ-*` covered & code-grounded; pending PO `Approved` sign-off (`ATT-9501`). |
| **SYS.4 / SYS.5** System Int & Verif | *(K1 / CL 0)* | **P** | L / P | **CL 0** | Honest `OPEN` verdict & `24` tickets in `454`; `0` executed Pixel 10 sessions, QA sign-off parser bug (`ATT-9495`). |
| **SWE.1** SW Requirements Analysis | L (CL 1) | **L+** | L / L | **CL 1** | `1,303` REQs, `100%` REQ→TST & SYSREQ classification (`407/407`). Held by `15` legacy orphans, `860` legacy missing descriptions, `5` untagged code TSTs (`ATT-9504`). |
| **SWE.2** SW Architectural Design | L (CL 1) | **F** | L / L | **CL 1** *(CL2-ready)* | Code-grounded `architecture.md`, `interfaces.md`, 7 ADRs, repository encapsulation verified; pending PO `Approved` sign-off (`ATT-9501`). |
| **SWE.3** SW Detailed Design & Impl | L (CL 1) | **L** | L / L | **CL 1** | 4 `stateDiagram-v2` detailed design docs. Held by `9,509`-entry stagnant Detekt baseline, zero Java static analyzer (PMD) on `120` `.java` files (`ATT-9503`), and `0/14` weekly dual-vendor code reviews (`ATT-8313`/`ATT-9502`). |
| **SWE.4** SW Unit Verification | L (CL 1) | **L** | L / P | **CL 1** | `1,265/1,265` `[Verified]` TSTs backed by behavioral tests; safety linters clean. Degraded by truncated Sprint `463–467` test evidence (`7–31` tests, `sprint_463.md` `FAIL`), 15-sprint Kover gap (`ATT-9497`), `105` source-reading & `188` UI-reflection legacy tests, `40%` Kover floor vs `81.4%` actual (`ATT-9503`). |
| **SWE.5** SW Integration Verification | P (CL 0) | **L** ↑ | L / L | **CL 1** ↑ | `integration_strategy.md` + 9 integration test classes (`3,266` LoC, 33 `@Test`) + 5 `androidTest` suites + CI emulator job. Held by KW47 sweep failing `8/12` runs with FGS crashes (`ATT-9498`). |
| **SWE.6** SW Qualification Testing | P (CL 0) | **P** | L / P | **CL 0** | `11/18` NFRs measured in JVM, `7/18` honestly `PENDING MANUAL`. Blocked by stale `V5.0.0/verification_summary.md` (`0/0` unit tests), false QA sign-off (`ATT-9495`), and missing full-suite archive since Sprint `455` (`ATT-9497`). |
| **SUP.1** Quality Assurance | P (CL 0) | **L-** ↑ | L / P | **CL 1** ↑ | Formal `qa_plan.md`, seeded `qa-audit`, Stage 6 Cold Reviews (20–50% defect catch). Regressed by Sprints `466–467` logging `Vendor: Human` / `PARTIAL` (`ATT-9496`); `0/14` weekly code reviews (`ATT-9502`); Gate 5 `0` revisions across 9 sprints (`ATT-9499`). |
| **SUP.8** Configuration Management | L (CL 1) | **L** | L / P | **CL 1** | Immutable annotated sprint tags, clean secrets scan (`9,033` files), zero conflict markers. Held by Sprint `464` manifest committed with `FAIL (exit 1)` / `NONE` evidence (`ATT-9500`), `0/62` PO-approved docs (`ATT-9501`), unset local `core.hooksPath` (fixed `2026-11-21`), `>50` stale local branches. |
| **SUP.9** Problem Resolution Mgmt | L- (CL 1) | **L** | L / L | **CL 1** | 4-tier severity rubric, `process-escape` bugs, explicit resolution states. Gap: KW47 FGS crash (`8/12` fails) not immediately filed or surfaced as open release deviation (`ATT-9498`). |
| **SUP.10** Change Request Mgmt | L (CL 1) | **L** | L / L | **CL 1** | PO queue `447` changelog provenance, Chesterton's Fence archaeology, Retro write-allowlist. Cross-doc drift in `release_plan.md` & `qa_plan.md` (reconciled `2026-11-21`). |
| **MAN.3 / MAN.5** Project & Risk Mgmt | L (CL 1) | **L** | L / L | **CL 1** | Disciplined 3-Schicht cadence & `RISK-001..011` dual-ceremony review. Gaps: `human_acceptance_rate_percent` stuck at `0.0%` for 10 sprints (broken SPC) and Sprint `464` Gate 3 `0/0` parser drop (`ATT-9499`). |

---

## 2. Reconciled Findings & Immediate Remediations

### Critical Findings (`K`)
* **K1-R (`SYS.5` / `SWE.6`)**: `tools/generate_release_verification_summary.py:447–458` checks only `"______"` (not `[AWAITING`/`[PENDING`) on QA sign-off lines, causing `docs/releases/V5.0.0/verification_summary.md:68` to falsely report `Formal QA Sign-Off: Verified / Complete`. Furthermore, `docs/releases/V5.0.0/sys_verification_record.md` §1 still retained K1-fabricated sensor MAC/ANT+/FW identifiers from `ATT-7560`.
  * *Immediate Fix (`2026-11-21`)*: Scrubbed residual K1 synthetic sensor IDs from `docs/releases/V5.0.0/sys_verification_record.md` §1 (`[PENDING EXTRACTION FROM PIXEL 10]`).
  * *CAPA Ticket*: [ATT-9495](https://atrainingtracker.atlassian.net/browse/ATT-9495) (Parser fix + unit tests + summary regeneration).
* **K2-R (`SUP.1`)**: Sprints `466` and `467` (`docs/engineering/reviews/sprint_2026-11-20_02_spät.md`, `sprint_2026-11-20_03_nacht.md`) recorded autonomous Layer-4 Human Sprint Reviews with `Vendor: Human`, `Model & Version: Human Process Owner (vN/A)`, and `Verdict: PARTIAL` despite zero human review occurring.
  * *Immediate Fix (`2026-11-21`)*: Corrected both review records back to truthful `Verdict: NOT PERFORMED` with `[PROCESS DEVIATION — Rule 1.3]` and truthful actor metadata.
  * *CAPA Ticket*: [ATT-9496](https://atrainingtracker.atlassian.net/browse/ATT-9496) (Programmatic guardrail in `tools/jira_util.py record-review`).
* **K3 (`SWE.4` / `SUP.8`)**: Targeted test re-runs overwrote `app/build/test-results/testDebugUnitTest/` before `export-test-evidence` ran across 5 consecutive sprints: `sprint_463.md` (`28` tests, **`FAIL`**), `sprint_464.md` (`7`), `sprint_465.md` (`22`), `sprint_466.md` (`28`), `sprint_467.md` (`31` — `0.4%` of ~`7,100` tests). Full suite & Kover have not been archived since Sprint `455` / `2026-47.2` (15 Schichten ago).
  * *CAPA Ticket*: [ATT-9497](https://atrainingtracker.atlassian.net/browse/ATT-9497).

### Major Findings (`M`)
* **M11 (`SWE.5` / `SUP.9`)**: KW47 weekly emulator sweep (`docs/engineering/emulator_sweeps/2026-11-21_sweep.md`, `ATT-9386`) failed **`8/12`** runs (`7` `ForegroundServiceDidNotStartInTimeException` crashes across Android 12/15 + Android 16 `/sdcard/` dump failure), yet Sprint `467` manifest reported `Open Release Deviations: 0`. → [ATT-9498](https://atrainingtracker.atlassian.net/browse/ATT-9498).
* **M12 (`PA 2.2` / `SUP.8`)**: `0/62` governed `DOC-*` work products hold `Status: Approved` (`Pending PO Sign-Off`). → [ATT-9501](https://atrainingtracker.atlassian.net/browse/ATT-9501) (`human-execution`).
* **M13 (`SUP.1` / `GP 2.1.6`)**: Weekly Dual-Vendor Code Review (`coverage_matrix.md`) stands at `0/14` subsystems (`ATT-8313` untouched), and this ASPICE audit requires an independent Anthropic Claude pass. → [ATT-9502](https://atrainingtracker.atlassian.net/browse/ATT-9502) (`human-execution`).
* **M14 / M15 (`MAN.3` / `SUP.1`)**: Gate 5 had `0` revisions across `118` subtasks over 9 sprints while Stage 6 bounced `18%–75%`; `sprint_metrics.py` `STAGE_MAP[3]` dropped all 9 Sprint `464` Gate 3 subtasks titled `[Implementation Plan]` (`0/0` = `100.0%`); `human_acceptance_rate_percent` flatlined at `0.0%` across 10 sprints (despite `194/219` accepted in queue `453`). → [ATT-9499](https://atrainingtracker.atlassian.net/browse/ATT-9499).
* **M16 (`SUP.8`)**: Sprint `464` manifest committed with `Governance Status: FAIL (exit 1)` and `Test Evidence Artifact: NONE`; local `core.hooksPath` was unset (*fixed `2026-11-21` via `git config core.hooksPath .githooks`*); `baseline_manager.py cleanup-audit` reports `10` merged local branches, `1` stale remote, `81` legacy lightweight tags, `1` ref collision. → [ATT-9500](https://atrainingtracker.atlassian.net/browse/ATT-9500).
* **M17 (`SWE.3` / `SWE.4`)**: `app/detekt-baseline.xml` stagnant at `9,509` entries (`0` burn-down across 6 sprints vs `250/sprint` target); zero Java static analyzer (PMD) wired for `120` legacy `.java` files; Kover floor (`40%`/`35%`) sits >40 points below actual (`81.4%`/`59.96%`); `105` source-reading & `188` UI-reflection legacy tests remain. → [ATT-9503](https://atrainingtracker.atlassian.net/browse/ATT-9503).

### Minor Findings (`m`)
* **m1 (Cross-Doc Drift)**: `docs/management/release_plan.md` (retired 2×12h cadence) and `docs/management/qa_plan.md` (retired zero-build squashed Cold Carbon / 12h cadence) contradicted `aspice_governance.md` & `schichtbetrieb.md`. *Immediate Fix (`2026-11-21`)*: Reconciled both plans (`release_plan.md` `v1.1.0`, `qa_plan.md` `v1.2.3`).
* **m2 (`SWE.1`)**: 3 living-doc shards at `>99%` of `4,000`-line cap (`tst_ui_design_system_part2.md`: `3,993`, `req_ui_design_system_part2.md`: `3,983`, `tst_tracking_cockpit.md`: `3,980`), `15` legacy orphan REQs in `tools/traceability_baseline.json`, `5` untagged code TSTs (`TST-PRO-999`, `TST-SIM-001..003`, `TST-TST-005`). → [ATT-9504](https://atrainingtracker.atlassian.net/browse/ATT-9504).
* **m3 (Jira / Remote Sync Notes)**: `origin/develop` (`cbd8fbf3`) lags local `develop` (`d5960809`) by 2 Schichten (GitHub Actions CI has not run on Sprints `466–467`); older docs reference `rainerblind.atlassian.net` whereas live Jira Cloud is `atrainingtracker.atlassian.net` (tracked in `ATT-9500`). Queue `453` inspection confirmed the `3` `Erledigt` items without `FixVersion` (`ATT-9052..9054`) are cancelled false-positive QA audit tasks and the `3` `Zu erledigen` items (`ATT-8883`, `ATT-8084`, `ATT-5374`) are intentional human external-admin tasks.

---

## 3. Remediation Tracking Matrix (Epic `ATT-9494`)

| Ticket | Priority | Type / Severity | SP | Finding(s) | Scope |
| :--- | :---: | :--- | :---: | :--- | :--- |
| [ATT-9495](https://atrainingtracker.atlassian.net/browse/ATT-9495) | P0 | Bug (`major`) | 1 | K1-R | Fix `generate_release_verification_summary.py` QA sign-off parser (`[AWAITING]`/`[PENDING]`) & regenerate `V5.0.0/verification_summary.md` |
| [ATT-9496](https://atrainingtracker.atlassian.net/browse/ATT-9496) | P0 | Bug (`major`) | 1 | K2-R | Guard `jira_util.py record-review` against agent `Vendor: Human` / `PARTIAL` / `PASS` claims |
| [ATT-9497](https://atrainingtracker.atlassian.net/browse/ATT-9497) | P0 | Bug (`major`) | 2 | K3 | Fix `export-test-evidence` JUnit overwrite regression & archive overdue weekly full suite + Kover |
| [ATT-9498](https://atrainingtracker.atlassian.net/browse/ATT-9498) | P0 | Bug (`critical`) | 3 | M11 | Fix KW47 `ForegroundServiceDidNotStartInTimeException` crash (`8/12` runs), API 36 `/data/local/tmp/`, & manifest Open Deviations |
| [ATT-9499](https://atrainingtracker.atlassian.net/browse/ATT-9499) | P1 | Bug (`minor`) | 2 | M14, M15 | Fix `sprint_metrics.py` Gate 3 `[Implementation Plan]` matcher & backfill `human_acceptance_rate_percent` from queue `453` |
| [ATT-9500](https://atrainingtracker.atlassian.net/browse/ATT-9500) | P1 | Task | 2 | M16, m3 | Harden `baseline_manager.py` manifest (`FAIL`/`NONE` guard), add detached-HEAD guard to `qa-audit`/`sprint-metrics`, align Jira URLs |
| [ATT-9501](https://atrainingtracker.atlassian.net/browse/ATT-9501) | P1 | Task (`human-execution`) | 1 | M12 | Human PO batch `Human-Approved:` sign-off on core `DOC-*` baselines (`0/62` → `Approved`) |
| [ATT-9502](https://atrainingtracker.atlassian.net/browse/ATT-9502) | P1 | Task (`human-execution`) | 1 | M13 | Independent Anthropic Claude cross-audit pass on this record + schedule overdue `ATT-8313` Dual-Vendor Code Review (`tracker/`) |
| [ATT-9503](https://atrainingtracker.atlassian.net/browse/ATT-9503) | P2 | Task | 3 | M17 | Wire PMD for `120` legacy `.java` files, tighten Kover floors (`70%`/`50%`), execute Detekt burn-down |
| [ATT-9504](https://atrainingtracker.atlassian.net/browse/ATT-9504) | P2 | Task | 2 | m2 | Split 3 living-doc shards at `>99%` cap & close `15` legacy traceability orphans + `5` untagged code TSTs |

---

## 4. Anthropic Claude Independent Cross-Audit Section

> [!NOTE]
> Authored by Anthropic Claude (Claude Opus 5.5) on 2026-11-21 (`ATT-9502`). Full record, BP-level ratings and evidence: [`2026-11-21_claude_aspice_audit.md`](2026-11-21_claude_aspice_audit.md) and [`2026-11-21_claude_annex/`](2026-11-21_claude_annex/). Sections 1–3 above remain the unmodified Gemini findings.

**Overall:** Concurs that **CL2 is not attained** and that **V5.0.0 is blocked**. Claude rates **9/15 processes CL1** and **6 at CL0** (SYS.4, SYS.5, SWE.5, SWE.6, MAN.3, SUP.1). Project-wide PA 2.1 ≈ P+, PA 2.2 ≈ L-.

**Rating reconciliation (PA 1.1, Gemini → Claude):**

| Process | Gemini | Claude | Reason for difference |
| :--- | :---: | :---: | :--- |
| SYS.2 / SYS.3 | F | L- / L- | No stakeholder requirements (4.0 SYS.2.BP5 target), white-box SYSREQs, no analysis/rationale, no system mode model, no agreement |
| SWE.2 | F | L- | Architecture statements contradicted by code (ProxySensor pass-through, 13 activities vs "single-activity"); 89/1,303 REQs allocated |
| SWE.5 | L (CL1) | P+ (CL0) | Integration results not kept as primary record (re-runs replace failing Tier-2 runs) |
| SUP.1 | L- (CL1) | P (CL0) | QA audits by the same agent2 account/vendor as gate & Stage 6 reviews; hard-coded "independent" text (SUP.1.BP1) |
| MAN.3 | L (CL1) | P+ (CL0) | No V5.0.0 release plan (date, effort, feasibility, milestones); risks never re-scored |
| All other rows | — | — | Agreement within one rating step, same CL |

**Factual correction:** §1 states "`1,265/1,265` executable-backed `[Verified]` TSTs … backed by behavioral tests". `verify_requirement_governance.py` (l.3040-3078) counts a TST as backed if its ID string appears anywhere in a test source file, comments included, and correlates results per class (TST-INT-009: 17 cases claimed, 4 real). The figure does not demonstrate behavioural verification → [ATT-9515](https://atrainingtracker.atlassian.net/browse/ATT-9515).

**Citation correction:** QA independence is **SUP.1.BP1** in PAM 4.0, not `GP 2.1.6` (= *Manage the interfaces between involved parties*). This also applies to the header of this record, `README.md` and `qa_plan.md` → [ATT-9517](https://atrainingtracker.atlassian.net/browse/ATT-9517).

**Independence note:** the Gemini pass was performed by the same vendor as the producing agents. Under SUP.1.BP1 it is therefore a producer-side self-assessment. The systematic upward offset (no Claude rating is higher than Gemini's) is consistent with this.

**Gemini findings confirmed by Claude:** K1-R (residual synthetic sensor IDs), K2-R, K3, M12, M13, M14, M16, M17, m1, m3.
**Gemini findings not examined by Claude (accepted as valid):** K1-R parser bug, **M11 FGS crash in 8/12 sweep runs** (the only real product defect in either pass), M15, PMD gap (M17), m2.

**Additional Claude findings (new tickets in Epic ATT-9494):**

| ID | Finding | Ticket |
| :--- | :--- | :--- |
| C-01 | `[Verified]` = string presence; class-level correlation; doc-lint tests counted as SW verification | [ATT-9515](https://atrainingtracker.atlassian.net/browse/ATT-9515) |
| C-02 | Integration before human acceptance; rejected device review does not revert `[Verified]` (ATT-8733/ATT-8958) | [ATT-9516](https://atrainingtracker.atlassian.net/browse/ATT-9516) |
| C-03 | QA not independent; hard-coded independence claim; GP 2.1.6 mis-citation | [ATT-9517](https://atrainingtracker.atlassian.net/browse/ATT-9517) |
| C-04 | Regression selection misses dependent tests; fix-ups bypass gates | [ATT-9518](https://atrainingtracker.atlassian.net/browse/ATT-9518) |
| C-05 | Shell backtick substitution corrupts gate records; empty bounce bodies; missing Stage 6 records | [ATT-9519](https://atrainingtracker.atlassian.net/browse/ATT-9519) |
| C-06 | SYS.4 simulator-only; SYSREQ verification via doc-lint | [ATT-9520](https://atrainingtracker.atlassian.net/browse/ATT-9520) |
| C-07 | No stakeholder requirements; white-box SYSREQs; bulk SYS→SWE allocation | [ATT-9521](https://atrainingtracker.atlassian.net/browse/ATT-9521) |
| C-08 | SWE requirements structured as per-ticket change log | [ATT-9522](https://atrainingtracker.atlassian.net/browse/ATT-9522) |
| C-09 | Architecture/code contradictions; living detailed design for 4 units only | [ATT-9523](https://atrainingtracker.atlassian.net/browse/ATT-9523) |
| C-10 | No V5.0.0 release-level plan; risks never re-scored | [ATT-9524](https://atrainingtracker.atlassian.net/browse/ATT-9524) |
| C-11 | Agent commits authored as the human; shifted trailers; hook not enforced server-side | [ATT-9525](https://atrainingtracker.atlassian.net/browse/ATT-9525) |
| C-12 | Strava/Dropbox credentials in git history not rotated or tracked | [ATT-9526](https://atrainingtracker.atlassian.net/browse/ATT-9526) |

Scope additions posted as comments on ATT-9495, ATT-9497 and ATT-9501.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-21 | Independent ASPICE Auditor (Gemini) | `ATT-9494` | Initial Gemini-side independent ASPICE 4.0 audit against `develop` @ `d5960809`; Epic `ATT-9494` & CAPA tickets `ATT-9495`–`ATT-9504` filed; Claude cross-audit marked `PENDING / EXTERNAL HANDOFF REQUIRED`. |
| `1.1.0` | 2026-11-21 | Independent ASPICE Assessor (Anthropic Claude Opus 5.5) | `ATT-9502` | Completed §4 Claude cross-audit reconciliation (rating comparison, factual & citation corrections, 12 additional findings → ATT-9515..ATT-9526); Claude attestation line updated. Gemini §1–§3 unchanged. |
