# Sprint Review & Retrospective: Schicht 2026-11-22_02_Spät (Sprint 472 — ATT-9643)

**Document ID**: `DOC-RETRO-ATT-9643`  
**Sprint**: `2026-11-22_02_Spät` (Jira Sprint ID `472`, `15:00–22:00 CET`, `aaa-v2` v1.0.22, PO-directed **2 parallel lanes**)  
**Retro Ticket**: [ATT-9643](https://rainerblind.atlassian.net/browse/ATT-9643) | **Rolling Weekly Retro**: [ATT-8223](https://rainerblind.atlassian.net/browse/ATT-8223)  
**Date**: `2026-11-22` | **Status**: Reviewed | **Approver**: Pending PO Sign-Off  

---

## 1. Delivered Scope & Story Point Velocity (`REQ-PRO-029`)

| Status Category | Tickets | Story Points | Keys & Highlights |
| :--- | :---: | :---: | :--- |
| **Delivered & Merged (`Final Review (Human)` $\rightarrow$ `453`)** | **4** (`4× tier-a`) | **12.0 SP** | `ATT-6034` (`5 SP` carryover WIP, `b22f95be`), `ATT-8127` (`2 SP`, `03c1a8c3`), `ATT-9498` (`3 SP` Critical FGS / Finding M11, `9c218678`), `ATT-8128` (`2 SP`, `5dce575f` — completes Milestone Epic **`ATT-8119`** 8/8!) |
| **Active WIP Carryover (`Final Review (Agent)`)** | **1** (`1× tier-b`) | **3.0 SP** | `ATT-8129` (`3 SP`, Epic `ATT-8120`, Bounce 1/5 healed & preserved intact on `feature/ATT-8129` @ `10518e65` for immediate Stage 6 Pass 2 in `2026-11-22_03_Nacht`) |
| **Untouched Returns (`447`)** | **0** | **0.0 SP** | Zero untouched return churn |
| **Total Committed Work** | **5 work (+1 retro)** | **15.0 SP** | **80.0% merged (`12.0 / 15.0 SP`), 100% active (`4/4` merged `tier-a` + `1/1` Stage-6-ready `tier-b`)** |

* **Ceremony 2 Human Review (`REQ-PRO-062`)**: Recorded truthfully as **`NOT PERFORMED`** in `docs/engineering/reviews/sprint_2026-11-22_02_spät.md` (autonomous `Schichtwechsel` cadence per `Rule 2.2` / `Rule 1.3`, deferred to `Bereit für PO Abnahme` ID `453` and the weekly PO decision session per `ATT-9545`).
* **Physical Device Deploy (`Rule 7.6` / `REQ-TST-004`)**: `Pixel 10` (`66020DLCR002FL`) was unplugged at Ceremony 1 & Ceremony 2 (only `emulator-5554` attached); best-effort `-PstampGitHash=true` install and debug heap hygiene check deferred cleanly without blocking rollover.

---

## 2. Quantitative Sprint Scorecard (`PA 4.1` & `PA 4.2`) & Tier-2 Verification

* **Process Version**: `aaa-v2` (2-sprint SPC baseline; **In Statistical Control** — all metrics within $\mu \pm 2\sigma$; zero PA 4.2 alerts).
* **Gate 1–5 FTPR**: **`75.0%`** (`4` Gate 3 subtasks, `3` first-time PASS; see spot-check note in §4) | **Stage 6 Cold Review FTPR**: **`20.0%`** (`1/5` first-pass `PASS` on `ATT-9498`, `4` high-value adversarial bounces catching real defects on `ATT-6034`, `ATT-8127`, `ATT-8128`, `ATT-8129`) | **Bug Resolution**: **`100.0%`** (`3/3`: 1 Critical `ATT-9498`, 2 Major `ATT-8127`/`ATT-8128`).
* **Second-Vendor Provenance (`ATT-9546` / `RISK-011`)**: Verified via `swarm(action="models", all=true)` that `claude-sonnet-5-5-high` remained at `0%` weekly quota until `2026-11-28T08:17:14Z`; all 4 `tier-a` Stage 6 Cold Reviews executed on sanctioned Gemini 4 Neon High fallback (`fallback=claude_quota_exhausted`) and still caught genuine multi-defect bounces on 3/4 `tier-a` tickets + 1/1 `tier-b` UI ticket.
* **Batched Lean Tier-2 Gate (`--verify-warm --max-workers=2 --no-kover`, zero CodeCache failures)**:
  * `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin`: **PASS**; `ANT-Android-SDKs` gitlink `160000` verified intact.
  * **Merged JVM Unit & Contract Suites (`347` XML suites, `2,017` tests)**: **`2,013 / 2,017` PASS** (Batch 1 `297` suites + Batch 2 `50` suites + `3/3` shared-worker isolation confirmations `TcxImportClusterParityTest`, `WorkoutClusterCorridorScoringTest`, `TrackerServiceStateMachineCharacterisationTest` **100% PASS** in `1m 8s`). Sole `4` failures are **pre-existing baseline issues** untouched by Sprint 472: `MeasuredNfrBenchmarkTest` (×2) + `NonFunctionalRequirementsContractTest` (×1) tracked in **`ATT-9639`**, and `AspiceSweAuditContractTest` (×1) triggered by `ATT-9501` Interactive-PO approval header `Approver: Rainer Blind (PO)`.
  * **Python Tooling & Linters**: **`593 / 593` `tools/test_*.py` PASS** (`OK`), `12/12` skills verified (`verify_skills.py`), `check_secrets_hygiene.py` clean (`11,093` files), `check_static_analysis_baselines.py` PASS (`detekt 9,509 <= 9,516`, `lint 216 <= 216`, `0` Recycle), `verify_requirement_governance.py --base-ref develop` **EXIT `0`** (`21/21` `SYSREQ`s `100%`).
  * **Live 12/12 Multi-OS Sweep (`ATT-9498`)**: Fresh `docs/engineering/emulator_sweeps/2026-11-22_sweep.md` **`12/12` PASS** across Android 12 (`Pixel_5_Android_12`), Android 15 (`Pixel_8_Android_15`, all 9 locales), and Android 16 (`Pixel_7_Android_36`), superseding the failing KW47 sweep (`2026-11-21_sweep.md`) with `0` `ForegroundServiceDidNotStartInTimeException` crashes.

---

## 3. Retrospective Insights & Process / Tooling Proposals (For Weekly PO Review in `ATT-8223`)

1. **Proposal 1 — Fix `qa-audit` Tier-Aware Gate Checking (`ATT-9595` / `ATT-9641` / `ATT-9642` / `ATT-9647`)**: Under `aaa-v2` (`ATT-9543`), `tier-a` tickets run Gate 3 + `auto_gate.py` + Stage 6 (omitting legacy agent2 prose Gates 1, 2, 4, 5), yet `jira_util.py qa-audit` Dimension 2 still flags `incomplete gate progression` on compliant `tier-a` tickets (`ATT-8127`, `ATT-8128`). Prioritizing `ATT-9595` / `ATT-9647` will eliminate recurring false-positive `NON-COMPLIANT` audit reports.
2. **Proposal 2 — Fix `export-test-evidence` Default Sprint Resolution**: Calling `jira_util.py export-test-evidence --no-kover` without `--sprint <NAME>` resolved to `sprint_2026-46.8` instead of the active sprint `2026-11-22_02_Spät` (caught immediately and re-run with explicit `--sprint 2026-11-22_02_Spät`). Propose fixing `export-test-evidence` to default strictly to `resolve_active_sprint()`.
3. **Proposal 3 — Fix `AspiceSweAuditContractTest` & `JavaCoreMigrationStrategyContractTest` for `ATT-9501` PO Approvals**: `ATT-9501`'s `Status: Approved | Approver: Rainer Blind (PO)` header causes pre-existing failures in `AspiceSweAuditContractTest` (false-positive regex match on `(PO)` / pending) and `JavaCoreMigrationStrategyContractTest` (`ADR-001` `Status: Approved`). Propose a small `tier-c` ticket to recognize `Rainer Blind (PO)` and `Status: Approved` after `ATT-9501`.
4. **Proposal 4 — Harden `agent_emulator.py exec` Against `adb shell` Unquoted Pipes**: A 20m watchdog caught Lane 2's `adb shell grep -qE "A|B"` hanging for 10m on `/tmp/att_emulator.lock` because Android `sh` interpreted unquoted `|` as a stdin pipe. Propose adding an optional per-command timeout (`--timeout 600` default) in `agent_emulator.py exec` so a hung `adb` subcommand cannot starve other lanes.
5. **What Went Exceptionally Well**:
   * **Gate 3 + Stage 6 Cold Review Synergy**: Every `tier-a` Gate 3 reviewer caught concrete edge cases *before* coding (e.g., `ATT-9498` `gen == 0L` boundary & `ATT-8128` `pendingNotifyInitialLoadedIds` coalescing race), and Stage 6 Cold Review still caught deep sibling bugs (`SensorQuotaReconciliationManager` UTC ranking shift, `FitWorkoutParser` `devField.appId` precedence, `StartOrResumeAlertDialog` German button truncation on `360dp`).
   * **2-Lane Discipline + `--verify-warm` + 2-Batch Lean Tier-2**: Zero CodeCache OOMs, zero merge conflicts, and 12.0 SP of high-complexity `tier-a` work (incl. Critical M11 12/12 Multi-OS sweep & completing Milestone Epic `ATT-8119` 8/8) shipped cleanly in 7h.

---

## 4. QA Findings, Gate Spot-Check (`REQ-PRO-092.6`) & Multi-Sprint PO Escalation (`REQ-PRO-091.4`)

* **Sprint QA Audit (`docs/engineering/qa_audits/sprint_2026-11-22_02_spät.md`, `agent2`, seed `12580`)**: Sampled `2/4` merged tickets (`ATT-8127`, `ATT-8128`). Both scored **PASS on 5/6 dimensions** (Deliverables, 4-Augen Role Separation, Living Docs Parity, Commit Hygiene, PO Approval) and failed **only Dimension 2 (`Stage Subtask Lifecycle`)** due to the legacy pre-`aaa-v2` 5-subtask check. Auto-created CA ticket: [ATT-9647](https://rainerblind.atlassian.net/browse/ATT-9647).
* **⚠️ PO Escalation: Persistent QA Non-Conformance (`REQ-PRO-091.4`, SUP.1 BP6)**: The same `qa-audit` legacy-subtask non-conformance category has now recurred across **3 consecutive `aaa-v2` Schichten** (`2026-11-21_03_Nacht` $\rightarrow$ [ATT-9595](https://rainerblind.atlassian.net/browse/ATT-9595), `2026-11-22_01_Früh` $\rightarrow$ [ATT-9641](https://rainerblind.atlassian.net/browse/ATT-9641) / [ATT-9642](https://rainerblind.atlassian.net/browse/ATT-9642), and `2026-11-22_02_Spät` $\rightarrow$ [ATT-9647](https://rainerblind.atlassian.net/browse/ATT-9647)). **Root Cause**: `tools/jira_util.py` `qa-audit` was not yet updated to recognize `aaa-v2` tiered gate semantics (`ATT-9543`: Gate 3 + `auto_gate.py` + Stage 6 on `tier-a`, `auto_gate.py` + Stage 6 on `tier-b`/`tier-c`). **Escalation Rationale**: Escalated to PO in `ATT-8223` to authorize consolidating `ATT-9595`/`ATT-9641`/`ATT-9642`/`ATT-9647` into one `tier-c` tooling fix.
* **Quality Gate Verdict Spot-Check (`REQ-PRO-092.6`)**: Spot-checked `docs/engineering/gates/sprint_2026-11-22_02_spät.md` (`13` records across `ATT-6034`, `ATT-8127`, `ATT-8128`, `ATT-9498`, `ATT-8129`). All 4 Gate 3 verdicts and all 9 Stage 6 verdicts correlate authentically with code diffs, test XMLs, and emulator screenshots (`0` rubber-stamps). One parser artifact noted: `ATT-9645` (`ATT-9498` Gate 3) passed first-time with 6 implementation guardrails, but an intermediate pre-verdict comment without `GATE_VERDICT: PASS` was counted as `1 REVISION` by `export-gates` (making true Gate 3 FTPR `4/4 = 100%` vs parsed `75.0%`).
