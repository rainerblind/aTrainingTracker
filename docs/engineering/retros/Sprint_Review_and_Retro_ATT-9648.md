# Sprint Review & Retrospective: Schicht 2026-11-22_03_Nacht (Sprint 473)

**Document ID**: `DOC-RETRO-ATT-9648`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Sprint**: `2026-11-22_03_Nacht` (ID `473`, `22:00–05:00 CET`, `aaa-v2` v1.0.22, PO-directed **2 parallel lanes**)  
**Jira Ticket**: [ATT-9648](https://atrainingtracker.atlassian.net/browse/ATT-9648) | **Weekly Roll-Up**: [ATT-8223](https://atrainingtracker.atlassian.net/browse/ATT-8223)

---

## 1. Delivered Scope & Planned vs. Completed Velocity (`REQ-PRO-029`)

| Metric | Planned / Committed | Completed (`Final Review (Human)`) | Carryover WIP | Completion Rate |
| :--- | :---: | :---: | :---: | :---: |
| **Work Tickets** | 11 | **10** | 1 (`ATT-8132`) | **90.9%** |
| **Story Points (SP)** | 24.0 SP | **21.0 SP** | 3.0 SP | **87.5%** |

* **Delivered & Stage-6-Verified (`10` tickets = `21.0 SP`, merged onto `sprint/2026-11-22_03_Nacht`, archived to `Bereit für PO Abnahme` ID `453` labeled `sprint-2026-11-22_03_Nacht`)**:
  * **Lane 1 (`ATT-8120` Code-Vereinfachung Stufe 2 — `9.0 SP` merged)**: `ATT-8129` (`3 SP`, `tier-b`, Legacy-UI-Rückbau Teil 1 — carryover Stage 6 Pass 3), `ATT-8130` (`3 SP`, `tier-b`, `-5,071` net LoC: Deprecated Screens, Pebble & vendored OpenCSV removed), `ATT-8131` (`3 SP`, `tier-a`, `-1,025` net prod LoC: `PeriodSummaries.db` replaced by reactive in-memory `PeriodsRepository` projection).
  * **Lane 2 (`ATT-431` 3/3 DONE `4.0 SP` + `ATT-166` 4/4 DONE `8.0 SP` = `12.0 SP` merged)**: `ATT-9365` (`2 SP`, `tier-c`, Gradle `nonFinalResIds`/`enableAppCompileTimeRClass` -> **~15× faster** `:app:compileDebugKotlin` on `strings.xml` edit `136s -> 9s`), `ATT-9603` (`1 SP`, `tier-b`, `LiveSegmentsRepository` 150-lines/sec logcat flood eliminated), `ATT-8669` (`1 SP`, `tier-c`, SUP.9 duplicate of `ATT-8631` verified), `ATT-9604` (`1 SP`, `tier-a`, `GNSS`/`GNSS_FUSED` sidecar dedup & provider tagging), `ATT-9602` (`3 SP`, `tier-a`, `BarometricBucketDispersion` pocket-compression anti-aliasing & 3-state startup grace), `ATT-9601` (`2 SP`, `tier-a`, stationary rejected GNSS zero-speed & post-pause EKF/base-class spike fix), `ATT-9600` (`2 SP`, `tier-a`, `AttFusedTimestampNormalizer` monotonic `elapsedRealtime` unification; `SpeedAndLocationDevice_ATTFused.kt` shrunk `1,498 -> 1,472/1,500` LoC).
* **Active WIP Carryover (`1` ticket = `3.0 SP`)**: `ATT-8132` (`3 SP`, `tier-a`, status `Test`) cleanly committed on `feature/ATT-8132` @ `cdf19d4d` (`569/569` in-scope tests PASS incl. AB-BA deadlock stress test, live `emulator-5554` smoke PASS, governance EXIT `0`) — ready for `auto_gate.py --write-bundle` + Stage 6 in `2026-11-23_01_Früh`.
* **Ceremony 2 Tier-2 Gate & Device Deploy**:
  * **2-Batch Lean Tier-2 (`--verify-warm --max-workers=2 --no-kover`, zero CodeCache failures)**: **`1,153 / 1,157` PASS across `185` JUnit XML suites** (`Batch 1`: `272/276` across `50` suites + `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin`; `Batch 2`: **100% PASS** across all `135` sprint-touched suites; sole `4` failures are pre-existing baseline `ATT-9639` & `ATT-9501`, archived truthfully with `--allow-failure`).
  * **Governance, Python & Safety Linters**: `verify_requirement_governance.py` EXIT `0` (`408/428 [95.33%]` SYSREQ-linked, `428/428 [100.0%]` classified after `9eee0407` Tier-2 traceability reconciliation of `ATT-8130`/`ATT-8131`/`ATT-9601`/`ATT-9602`), `593/593` Python tooling tests PASS, `check_secrets_hygiene.py` PASS (`11,431` files), `check_static_analysis_baselines.py` PASS (`detekt` `9,509/9,516`, `lint` `216/216`, `0` Recycle), `ANT-Android-SDKs` `160000` gitlink verified.
  * **Physical `Pixel 10` (`Rule 7.6`)**: Unplugged (`only emulator-5554 attached`); best-effort commit-stamped `installDebug` and `REQ-STB-022.4` heap hygiene check deferred cleanly.

---

## 2. Quantitative Sprint Scorecard (`PA 4.1 & PA 4.2`) & Gate Spot-Check (`REQ-PRO-092.6`)

* **Throughput**: `10/12` issues (`83.3%` incl. retro; `10/11` work tickets = `90.9%`) | **Velocity**: **`21.0 / 24.0 SP`** (`87.5%`).
* **Gate FTPR**: Gate 3 (`Impl-Plan`) **`83.3%`** (`5/6` first-pass; `ATT-9653` caught 5 real architectural/AB-BA gaps on `ATT-8132` pre-coding), Stage 6 (`Final Review (Agent)`) **`70.0%`** (`7/10` first-pass; `3` adversarial bounces caught `MigrationEngine.appRestarter` `Runtime.exit(0)` worker kill on `ATT-8131`, index totals on `ATT-8129`, and master-index version parity on `ATT-9601`), **End-to-End FTPR (Gates 1–6)**: **`75.0%`**.
* **SPC Status (`PA 4.2`)**: **In Statistical Control** across all 4 control charts (`OVERALL_FTPR` `83.3%` within `[54.68, 100.0]`, `COMPLETED_SP` `21.0` within `[0.0, 54.35]`).
* **SUP.9 Bug Metrics**: `7` bugs (`6` resolved / awaiting human = `85.7%`, `1` open `ATT-8132` ready for Stage 6 carryover).
* **Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)**: Spot-checked Gate 3 verdicts (`ATT-9649`, `ATT-9650`, `ATT-9651`, `ATT-9652`, `ATT-9653`, `ATT-9654`) and Stage 6 verdicts (`ATT-8129`, `ATT-8130`, `ATT-8131`, `ATT-9600`, `ATT-9601`, `ATT-9602`): **0% false-pass / rubber-stamping** observed — every `tier-a` Gate 3 recorded concrete code-grounded guardrails (and bounced `ATT-9653` on real circular-fallback & lock-hierarchy flaws), and Stage 6 caught real isolated-JVM worker termination (`ATT-8131` Bounce 1/5) prior to merge. All `tier-a` Stage 6 reviews truthfully recorded `--vendor google --model gemini-4-neon-high --allow-same-vendor-fallback --fallback-reason=claude_quota_exhausted` while Claude weekly quota remained `0%` until `2026-11-28T08:17:14Z`.

---

## 3. Retrospective Insights & Structured Proposals (For Weekly PO Review `ATT-8223`)

1. **What Went Exceptionally Well**:
   * **High-Impact Code Simplification & Build Speed**: `ATT-9365` (`nonFinalResIds=true`, `enableAppCompileTimeRClass=true`) immediately cut `strings.xml`-triggered Kotlin recompiles from `136s -> 9s` (~15× speedup), while `ATT-8129` + `ATT-8130` + `ATT-8131` deleted **`>6,500` net LoC** including Pebble, vendored OpenCSV, 27 legacy fragments, and the entire `PeriodSummaries.db` SQLite database — all verified live on `emulator-5554`.
   * **Proactive Watchdog Intervention**: `jstack` inspection at `04:45 CET` caught `RadarSnoozeController.kt:441` spinning in an infinite `TestCoroutineScheduler` loop on `ATT-8132`, unblocking `/tmp/att_build_merge.lock` so `ATT-9600` passed Stage 6 and merged cleanly before `05:00 CET` while `ATT-8132` fixed the loop and committed clean WIP at `cdf19d4d`.
2. **Structured Process & Tooling Proposals (`Rule 2.7` / `Rule 8.10` — Measured Problems)**:
   * **Proposal 1 — Modernize `tools/jira_util.py qa-audit` for `aaa-v2` Tiers (`ATT-9595` / `ATT-9641` / `ATT-9647` / `ATT-9655` / `ATT-9656`)**: Measured problem: `0/2 COMPLIANT` across 4 consecutive `aaa-v2` Schichten (`470`–`473`) solely because Dim 2/3 still expects 5 legacy prose subtasks instead of `tier-a` Gate 3 + `auto_gate.py` + Stage 6 (`ATT-9543`). Prioritize `ATT-9595` into `447` so `qa-audit` evaluates real `aaa-v2` compliance without generating duplicate CA tickets every Schicht.
   * **Proposal 2 — Pre-Merge `--check-verified-evidence` & `--check-reverse-sys-trace` in `auto_gate.py`**: Measured problem: `ATT-8130`/`ATT-8131` deleted obsolete test files (`PebbleServiceLifecycleTest`, `PeriodSummariesDbTest`) referenced by 6 legacy `[Verified]` TSTs, and `ATT-9601`/`ATT-9602` used `Derived (SW-only)` on `REQ-FIL-040`/`041` dropping SYSREQ ratio from `95.07%` to `94.63%`, caught only at Ceremony 2 (`9eee0407`). Adding `--check-verified-evidence --check-reverse-sys-trace` to `tools/auto_gate.py` would catch these at Stage 5 per ticket.
   * **Proposal 3 — Prioritize Pre-Existing Baseline Fixes (`ATT-9578`, `ATT-9639`, `ATT-9501`)**: Measured problem: `ATT-9578` (`SettingsDataStoreJavaHelper.kt:381` `runBlocking`) and `ATT-9639` (`MeasuredNfrBenchmarkTest.testNfr014`) triggered `--allow-failure` / documented baseline waivers on 4/5 `tier-a` tickets in Sprint 473 (`ATT-9604`, `ATT-9602`, `ATT-9601`, `ATT-9600`) and Tier-2 Batch 1 (`4` failures). Scheduling these restores 100% zero-waiver `auto_gate.py --advance-jira` automation.

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`)

* **Audit Report**: [`docs/engineering/qa_audits/sprint_2026-11-22_03_nacht.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/qa_audits/sprint_2026-11-22_03_nacht.md) (`--as agent2`, seed `96063`, sampled `2/10` tickets: `ATT-8131`, `ATT-8129`).
* **Audit Result**: `0/2 COMPLIANT` (`NON-COMPLIANT` strictly on legacy pre-`aaa-v2` Dimension 2 Stage Subtask Progression for both `ATT-8131`/`ATT-8129` and Dimension 3 subtask-only actor check for `tier-b` `ATT-8129`; both tickets PASSED Deliverables, Living Docs Parity, Commit Hygiene, and PO Approval Provenance, and `ATT-8131` PASSED 4-Augen Role Separation via Gate 3 `ATT-9651` + Stage 6).
* **Corrective Action Tickets Created**: [ATT-9655](https://atrainingtracker.atlassian.net/browse/ATT-9655) (Stage Subtask Progression), [ATT-9656](https://atrainingtracker.atlassian.net/browse/ATT-9656) (4-Augen Role Separation).

> [!WARNING]
> **⚠️ PO Escalation: Persistent QA Non-Conformance (`REQ-PRO-091.4` / ASPICE SUP.1 BP6)**  
> The same non-conformance category (*Stage Subtask Progression* & *4-Augen Role Separation* legacy pre-`aaa-v2` heuristic in `tools/jira_util.py qa-audit`) has recurred across **4 consecutive `aaa-v2` Schichten** (`2026-11-21_03_Nacht` -> [ATT-9595](https://atrainingtracker.atlassian.net/browse/ATT-9595), `2026-11-22_01_Früh` -> [ATT-9641](https://atrainingtracker.atlassian.net/browse/ATT-9641) & [ATT-9642](https://atrainingtracker.atlassian.net/browse/ATT-9642), `2026-11-22_02_Spät` -> [ATT-9647](https://atrainingtracker.atlassian.net/browse/ATT-9647), and `2026-11-22_03_Nacht` -> [ATT-9655](https://atrainingtracker.atlassian.net/browse/ATT-9655) & [ATT-9656](https://atrainingtracker.atlassian.net/browse/ATT-9656)).  
> **Root Cause**: `ATT-9543` (`aaa-v2`) intentionally replaced agent2 prose Gate 1–5 subtasks with deterministic `tools/auto_gate.py` + `tier-a` Gate 3 + Stage 6 Cold Review, but `qa-audit` in `tools/jira_util.py` still checks for 5 legacy stage subtasks on `tier-a`/`tier-b`/`tier-c` issues.  
> **Escalation Rationale & Requested PO Action**: Consolidate `ATT-9641`/`ATT-9642`/`ATT-9647`/`ATT-9655`/`ATT-9656` as duplicates into `ATT-9595` and promote `ATT-9595` into `Vom PO Freigegeben` (`447`) so `qa-audit` checks `aaa-v2` tier-appropriate gate & Stage 6 evidence authentically.
