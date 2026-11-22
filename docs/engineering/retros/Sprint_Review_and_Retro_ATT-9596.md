# Sprint Review & Retrospective: Schicht `2026-11-22_01_Früh` (Jira Sprint 471)

**Document ID**: `DOC-RETRO-471`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Date**: 2026-11-22  
**Jira Review & Retro Ticket**: [ATT-9596](https://rainerblind.atlassian.net/browse/ATT-9596)  
**Rolling Weekly Retro Ticket**: [ATT-8223](https://rainerblind.atlassian.net/browse/ATT-8223)  
**Sprint Branch**: `sprint/2026-11-22_01_Früh` (frozen at `959c15d0`, branched off `develop` @ `5dd12fec`)  
**Process Regime**: `aaa-v2` (`aspice_governance.md` v1.0.20 $\rightarrow$ v1.0.21 via `ATT-9581`, 3 Parallel Lanes)

---

## 1. Executive Summary & Planned vs. Completed Story Points (`REQ-PRO-029`)

Schicht `2026-11-22_01_Früh` (`05:00–15:00 CET`, 10h) executed 3 parallel disjoint-Epic lanes under `aaa-v2`, achieving **94.1% ticket completion (`16/17` active work tickets)** and **88.1% Story Point delivery (`37.0 / 42.0 SP`)** merged atomically onto `sprint/2026-11-22_01_Früh`:
* **Two entire Epics reached 100% completion**: **Epic `ATT-68` (6/6 tickets, `13.0 SP`)** and **Epic `ATT-8119` (6/6 tickets, `13.0 SP`)**.
* **One PO-accepted ticket already closed during the Schicht**: `ATT-6035` (`3 SP`, `Erledigt`, `V5.0.0`); the remaining `15` merged work tickets sit in `Final Review (Human)` labeled `sprint-2026-11-22_01_Früh` for async PO acceptance in `Bereit für PO Abnahme` (`453`).
* **One hot Stage-6-ready WIP carryover (`5.0 SP`)**: `ATT-6034` (`tier-a`, `5 SP`, Epic `ATT-6033`) completed implementation, verification (`34` suites / `224/224` PASS), and Bounce 1/5 remediation (lock-free `ConcurrentHashMap.newKeySet()` + lazy Stage-5 `activeSessionDeviceIdsProvider`), and is preserved cleanly on `feature/ATT-6034` (`c5eff65a`) in `Final Review (Agent)` for immediate Stage 6 Pass 2 completion in `2026-11-22_02_Spät`.

### Planned vs. Completed Velocity Table

| Metric | Planned / Active Scope | Completed & Merged | Carryover WIP | Completion % |
| :--- | :---: | :---: | :---: | :---: |
| **Work Tickets** | 17 *(1 carryover + 10 initial + 6 Lane 3 replenish)* | **16** *(1 `Erledigt` + 15 `Final Review (Human)`)* | 1 (`ATT-6034`, `Final Review (Agent)`) | **94.1%** |
| **Story Points (SP)** | **42.0 SP** | **37.0 SP** | 5.0 SP | **88.1%** |
| **Tier A (`tier-a`)** | 7 tickets (`20.0 SP`) | **6** (`15.0 SP`) | 1 (`5.0 SP`) | 85.7% |
| **Tier B (`tier-b`)** | 8 tickets (`19.0 SP`) | **8** (`19.0 SP`) | 0 | **100.0%** |
| **Tier C (`tier-c`)** | 2 tickets (`3.0 SP`) | **2** (`3.0 SP`) | 0 | **100.0%** |
| **Ceremony Ticket** | 1 (`ATT-9596`, `0 SP`) | `Final Review (Human)` | 0 | — |

### Delivered & Merged Tickets (`sprint/2026-11-22_01_Früh`)

| # | Ticket | Tier | SP | Epic | Sprint Commit | Stage 6 Cold Review Provenance & Passes |
| :---: | :--- | :---: | :---: | :---: | :---: | :--- |
| 1 | `ATT-9581` | `tier-c` | 2 | `ATT-232` | `1c6713e6` | Gemini Flash — Pass 1 PASS (`--verify-warm`, `check-install-required`, emulator lock telemetry) |
| 2 | `ATT-6036` | `tier-a` | 5 | `ATT-6033` | `15282892` | **Second-Vendor Claude (`claude-sonnet-5-5-high`)** — Pass 2 PASS (carryover WIP from Sprint 470) |
| 3 | `ATT-9508` | `tier-b` | 2 | `ATT-68` | `9851e2ba` | Gemini 4 Neon (High) — Pass 3 PASS (caught doc version drift & `auto_gate.py --skip-tests --write-bundle` XML wipe) |
| 4 | `ATT-8121` | `tier-a` | 2 | `ATT-8119` | `603458a8` | **Second-Vendor Claude (`claude-sonnet-5-5-high`)** — Pass 1 PASS |
| 5 | `ATT-6035` | `tier-b` | 3 | `ATT-6033` | `980d9564` | Gemini 4 Neon (High) — Pass 1 PASS (PO accepted $\rightarrow$ `Erledigt` / `V5.0.0`) |
| 6 | `ATT-9355` | `tier-c` | 1 | `ATT-68` | `a9923713` | Gemini Flash — Pass 1 PASS |
| 7 | `ATT-8122` | `tier-a` | 2 | `ATT-8119` | `01f03b20` | **Second-Vendor Claude (`claude-sonnet-5-5-high`)** — Pass 1 PASS *(Claude quota reached 0% post-review)* |
| 8 | `ATT-9172` | `tier-b` | 3 | `ATT-68` | `ae28c368` | Gemini 4 Neon (High) — Pass 2 PASS (healed 3 Bounce 1 defects) |
| 9 | `ATT-6178` | `tier-a` | 1 | `ATT-6033` | `d83ffd65` | Gemini 4 Neon (High) fallback (`claude_quota_exhausted`) — Pass 1 PASS |
| 10 | `ATT-8123` | `tier-a` | 2 | `ATT-8119` | `c75d331f` | Gemini 4 Neon (High) fallback (`claude_quota_exhausted`) — Pass 2 PASS (healed 3 `BTLERadarDevice` concurrency races) |
| 11 | `ATT-9170` | `tier-b` | 2 | `ATT-68` | `1c3fbfd8` | Gemini 4 Neon (High) — Pass 1 PASS |
| 12 | `ATT-9164` | `tier-b` | 2 | `ATT-68` | `e6151ce2` | Gemini 4 Neon (High) — Pass 1 PASS |
| 13 | `ATT-8125` | `tier-b` | 2 | `ATT-8119` | `4775b863` | Gemini 4 Neon (High) — Pass 1 PASS |
| 14 | `ATT-8124` | `tier-a` | 3 | `ATT-8119` | `0bc45492` | Gemini 4 Neon (High) fallback (`claude_quota_exhausted`) — Pass 2 PASS (healed 3 `MyBTLEDevice` `mIsGattPaused` races) |
| 15 | `ATT-9069` | `tier-b` | 3 | `ATT-68` | `e106c1e9` | Gemini 4 Neon (High) — Pass 2 PASS (healed `extractSensorSample` suffix bug + unit cache key) |
| 16 | `ATT-8126` | `tier-a` | 2 | `ATT-8119` | `959c15d0` | Gemini 4 Neon (High) fallback (`claude_quota_exhausted`) — Pass 2 PASS (healed compiler warning + KDoc) |

* **Carryover WIP Preserved Intact (`5.0 SP`)**: `ATT-6034` (`tier-a`, `5 SP`, Epic `ATT-6033`) on `feature/ATT-6034` (`c5eff65a`) in `Final Review (Agent)` after Stage 6 Pass 1 caught an unsynchronized eager `new HashSet<>(mActiveSessionDeviceIds)` iteration outside Stage 5 `try/catch` (healed cleanly at `c5eff65a`; handover comment posted for `2026-11-22_02_Spät`).

---

## 2. Quantitative Sprint Scorecard (`PA 4.1 & PA 4.2`) & Tier-2 Gate Evidence

Source: `docs/engineering/metrics/process_metrics.jsonl` (33rd record), `docs/engineering/gates/sprint_2026-11-22_01_früh.md` (43 records), `docs/engineering/test_evidence/sprint_2026-11-22_01_Früh.json`.

| Metric | Sprint `2026-11-22_01_Früh` Value | Control Band / Baseline | Status |
| :--- | :---: | :---: | :--- |
| **Jira-Recorded Throughput** | 17/18 (`94.4%`) / `42.0 SP` *(16/17 merged = `37.0 SP` + `ATT-6034` in `Final Review (Agent)`)* | `mu=10.0` / `21.0 SP` (`n=1` `aaa-v2`) | High throughput (+76% SP vs Sprint 470) |
| **Overall Gate 1–5 FTPR** | **69.2%** (`9/13` subtasks; Gate 3 `5/8 = 62.5%`) | `LCL 77.51%`, `mu=92.3%` (`n=1`) | ⚠️ **PA 4.2 Special Cause Variation** (RCA below) |
| **Stage 6 Cold Review FTPR** | **52.9%** (`9/17` 1st-pass; `8` adversarial bounces) | — | Healthy adversarial defect interception |
| **End-to-End Gate FTPR (1–6)** | **60.0%** | — | Reflects high `tier-a` concurrency density (`41%` of tickets) |
| **Bug Resolution Rate (SUP.9)** | **100.0%** (`7/7` bugs resolved: `6` Major, `1` Cosmetic; avg age `0.0d`) | 100% target | PASS |
| **Static Analysis Ratchets** | Detekt `9509 / 9516` (`Δ 0`); Android Lint `216 / 216` (`Δ 0`) | `<= 9516` / `<= 216` | PASS |
| **Python Tools & Safety Linters** | `575 / 575` `tools/test_*.py` OK; `3/3` safety linters PASS; `verify_requirement_governance.py` EXIT `0`; `ANT-Android-SDKs` `160000` | 100% | PASS |
| **Batched Lean Tier-2 Gate** | **`2,218 / 2,224` PASS across `367` XML suites** (`6` failures: `4` Sprint-471 escapes filed as `ATT-9639`/`ATT-9640`, `1` cross-suite flake `WorkoutClusterPreviewPersistenceTest` PASS `1/1` standalone, `1` pre-existing upstream `ATT-9348` `DataStoreHotPathContractTest`) | `--max-workers=2 --no-kover` | **Process-Escape Bugs Filed (`ATT-9639`, `ATT-9640`)** |
| **Physical Pixel 10 (`REQ-TST-004`)** | Ceremony 1 heap check clean (`0` HPROF, `5` `.heap.csv` pruned, max `22.0%`); Ceremony 2 deploy **skipped cleanly** (`66020DLCR002FL` unplugged) | Best-effort per `Rule 2.2` / `7.6` | Handled per policy |

### PA 4.2 Special Cause Variation Root-Cause Analysis (`OVERALL_FTPR = 69.2% < LCL 77.51%`)
* **Root Cause**:
  1. **Small `aaa-v2` SPC Baseline (`n = 1` sprint)**: `sprint_metrics` reset its SPC reference baseline on the `aaa-v2` cutover (Sprint 470, `mu=92.3%, sigma=0.0%`), where `tier-b` and `tier-c` tickets no longer generate prose Gate 1–5 subtasks (`ATT-9543`). Consequently, `OVERALL_FTPR` in Sprint 471 (`13` subtasks) is dominated by **`tier-a` Gate 3 (`Impl-Plan`) reviews (`8/13 = 61.5%` of subtasks)** on heavy concurrency/BLE/architecture tickets (`ATT-6036`, `ATT-8121..8124`, `ATT-8126`, `ATT-6034`).
  2. **Proactive Pre-Implementation Rigor**: `3/8` Gate 3 revisions (`ATT-8124`, `ATT-8126`, `ATT-6034`) caught deep threading, lock-granularity, and lazy-supplier design gaps *before* coding began, preventing far costlier late rework.
* **Countermeasure**:
  * Retain strict Gate 3 adversarial scrutiny on `tier-a` tickets; allow the `aaa-v2` SPC window to stabilize over $\ge 5$ sprints (`n >= 5`) before treating `tier-a`-weighted Gate 3 revision rates as degradation; and track `tier-a` vs `tier-b`/`tier-c` FTPR separately in weekly reviews (`ATT-8223`).

### Tier-2 Integration Gate Process-Escape Bugs Filed (`SUP.9`)
1. **`ATT-9639` (Major, `source=tier2`, linked to `ATT-8126`)**:
   * `MeasuredNfrBenchmarkTest.testNfr014_cockpitTelemetryThrottling_measuredMaxOneHzOverFiveSeconds` (and cascading `NonFunctionalRequirementsContractTest`) failed with `0` emissions in 5.0 s virtual time because `ATT-8126` moved `BANALServiceRepository.repositoryScope` off-Main to `Dispatchers.Default` without calling `BANALServiceRepository.setDispatcherForTesting(testDispatcher)` at `MeasuredNfrBenchmarkTest.kt:1485`.
   * Additionally, in Batch 2a, an uncancelled `Dispatchers.Default` coroutine in `BANALServiceRepository.startObservingBANALService` hit unmocked `Log.i` after an earlier `BANALServiceRepository*Test` ran `unmockkAll()`, leaking `UncaughtExceptionsBeforeTest` into `KnownLocationsRepositoryTest` (which passes `9/9` cleanly in Batch 2b).
2. **`ATT-9640` (Major, `source=tier2`, linked to `ATT-6036`)**:
   * `DatabaseBackfillManagerTest.testExecutePendingBackfillsConvergesAndIsIdempotent` failed reproducibly (both in Batch 2b and standalone) with `SQLiteException: table WorkoutSummaries already exists` at `WorkoutSummariesDbHelper.onCreate(WorkoutSummariesDatabaseManager.java:2593)` invoked via `BaseDatabaseManager.getDatabase(BaseDatabaseManager.kt:77)` introduced by `ATT-6036` (`15282892`).
3. **Pre-Existing Upstream Baseline (Not Introduced by Sprint 471)**:
   * `DataStoreHotPathContractTest.testHotPathFiles_containZeroRunBlockingCalls` (`SettingsDataStoreJavaHelper.kt:381`, upstream `ATT-9348` `b0bf0bc1`).

---

## 3. Retrospective Insights & Tooling Proposals (`Rule 2.7` Allowlist Compliant)

### What Worked Well
* **Warm Stage 6 Verification Worktree (`ATT-9581`)**: Landing `ATT-9581` first on Lane 3 immediately cut Stage 6 Cold Review turnaround by preserving `~/.cache/att_wt_verify_warm` and skipping unnecessary `installDebug` on non-UI diffs across the remaining 15 tickets.
* **Second-Vendor Claude + Truthful Gemini Fallback (`Rule 3.3`)**: Claude (`claude-sonnet-5-5-high`) executed 3 high-value `tier-a` Cold Reviews (`ATT-6036`, `ATT-8121`, `ATT-8122`) before exhausting its weekly quota (`7% -> 0%`, resets `2026-11-28T08:17:14Z`). Seamless fallback to Gemini 4 Neon (High) with explicit `--allow-same-vendor-fallback --fallback-reason=claude_quota_exhausted` still intercepted genuine `BTLERadarDevice`/`MyBTLEDevice`/`TrackerService` concurrency races on `ATT-8123`, `ATT-8124`, and `ATT-6034`.
* **Dependency-Aware Sequencing & Clean Shard Expansion**: Sequencing `ATT-6178 -> ATT-8124 -> ATT-6034` avoided merge conflicts on `TrackerService.java`; opening continuation shards `tst_tracking_cockpit_part2.md` and `req_tracking_cockpit_part3.md` kept all 30 living-doc shards safely `< 4,000` lines.

### Concrete Process & Tooling Proposals (For Weekly PO Review — `ATT-8223`)
Per `Rule 2.7`, protected `tools/**` and `.agents/**` paths were NOT edited during autonomous Ceremony 2 closeout; the following measured proposals are queued for PO review:
1. **`tools/auto_gate.py`: Guard `--skip-tests --write-bundle` Against Wiping Existing `junit/` XMLs**:
   * *Observed Defect*: On `ATT-9508` (caught by Stage 6 Pass 2), running `auto_gate.py --skip-tests --write-bundle` executed `shutil.rmtree(bundle_dir)` and wiped `37` authentic JUnit XMLs without regenerating them.
   * *Proposal*: Refuse `--skip-tests` combined with `--write-bundle` unless `--preserve-junit` is active (preserving existing `junit/` and `screenshots/` while re-hashing `manifest.json`).
2. **`tools/auto_gate.py`: Batch Gradle `--tests` Invocations to `<= 25` Suites per JVM Call**:
   * *Observed Defect*: On `ATT-9164`, passing `~70+` `--tests` filters to a single `./gradlew :app:testDebugUnitTest` call triggered a Gradle 9.6.0 `TestWorker` AB-BA deadlock (`TestWorker.submitToRun()` holding `synchronized(this)` while blocking on full `ArrayBlockingQueue.put()`, deadlocked against `"Test worker"` waiting on `synchronized(this)` in `TestWorker.execute()`), stalling the lane until the 12:10 CET watchdog intervened.
   * *Proposal*: Split `--tests` lists in `auto_gate.py` into deterministic chunks of `<= 25` classes per Gradle invocation (and merge the JUnit XML outputs cleanly).
3. **`tools/jira_util.py` (`qa-audit`): Align Dimension 2 & Dimension 3 Checks with `aaa-v2` Tier B / Tier C Lifecycles**:
   * *Observed Defect*: Consecutive-sprint false-positive `NON-COMPLIANT` verdicts on `tier-c` (`ATT-9558` $\rightarrow$ `ATT-9595` in Sprint 470) and `tier-b` (`ATT-9508`, `ATT-6035` $\rightarrow$ `ATT-9641`, `ATT-9642` in Sprint 471) because `qa-audit` still checks for legacy `agent2` prose Gate 1–5 subtasks superseded by `ATT-9543` (`auto_gate.py`) and Stage 6 Cold Reviews.
   * *Proposal*: Update `qa-audit` Dimension 2/3 to inspect `tier-a` / `tier-b` / `tier-c` labels and verify `auto_gate.py` machine reports + Stage 6 Cold Review verdicts (`agent2` actor) for `tier-b`/`tier-c`.
4. **`tools/parallel_sprint_lock.py` (`--merge-ticket`): Additive Index Resolution & Pre-Merged Feature Branch Support**:
   * *Observed Defect*: `--ours` auto-resolution dropped the newly added `tst_tracking_cockpit_part2.md` row in `docs/tests.md` on `ATT-6178`, and internal `git rebase` failed on feature branches that had already merged `sprint/<SPRINT>` (`ATT-8123`, `ATT-8124`, `ATT-9069`).
   * *Proposal*: Reconcile `docs/requirements.md` / `docs/tests.md` via `verify_requirement_governance.py --sync-indexes` + additive Change History merge, and fall back to `git merge --squash` when the feature branch already contains merge commits from `sprint/<SPRINT>`.

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`, `REQ-PRO-092.6`)

### 4.1 Independent QA Process-Compliance Audit (`docs/engineering/qa_audits/sprint_2026-11-22_01_früh.md`)
* **Auditor**: `agent2` (independent of Coordinator/Implementer) | **Seed**: `52448` | **Sample**: `2 / 16` (`12.5% >= 10%`): `ATT-9508` (`tier-b`), `ATT-6035` (`tier-b`).
* **Overall Verdict**: `NON-COMPLIANT` (`0 / 2` compliant under legacy heuristic):
  * **PASS on 4/6 dimensions for both tickets**: Deliverables (`PASS`), Living Docs Parity (`PASS`), Commit Hygiene (`PASS`), PO Approval Provenance (`PASS`).
  * **FAIL on legacy pre-`aaa-v2` subtask checks**: `ATT-9508` flagged on Dimension 3 (`4-Augen Role Separation` $\rightarrow$ **`ATT-9641`**); `ATT-6035` flagged on Dimension 2 (`Stage Subtask Progression` $\rightarrow$ **`ATT-9642`**) and Dimension 3 (**`ATT-9641`**).
  * **Truthful Assessment**: Both tickets authentically passed independent `agent2` Stage 6 Cold Reviews (`ATT-9508` 3 passes, `ATT-6035` 1 pass + PO accepted to `Erledigt`) and `auto_gate.py` machine gates; the `NON-COMPLIANT` flags stem from `qa-audit` not yet recognizing `aaa-v2` (`ATT-9543`) machine gates on `tier-b` tickets.

### 4.2 Corrective Action Ticket Status
| Ticket | Sprint Filed | Category | Status | Summary |
| :--- | :---: | :--- | :---: | :--- |
| `ATT-9595` | `2026-11-21_03_Nacht` (470) | QA Dim 3 (`tier-c` script gap) | Open (Backlog) | `ATT-9558` `tier-c` flagged for missing legacy Gate 1–5 subtasks |
| `ATT-9641` | `2026-11-22_01_Früh` (471) | QA Dim 3 (`tier-b` script gap) | Open (Top of Backlog) | `ATT-9508` & `ATT-6035` `tier-b` flagged for missing legacy Gate 1–5 `agent2` subtasks |
| `ATT-9642` | `2026-11-22_01_Früh` (471) | QA Dim 2 (`tier-b` script gap) | Open (Top of Backlog) | `ATT-6035` `tier-b` flagged for incomplete legacy Gate 1–5 subtask progression |
| `ATT-9639` | `2026-11-22_01_Früh` (471) | Tier-2 Escape (`ATT-8126`) | Open (Top of Backlog) | `MeasuredNfrBenchmarkTest` `setDispatcherForTesting` + `BANALServiceRepository*Test` teardown |
| `ATT-9640` | `2026-11-22_01_Früh` (471) | Tier-2 Escape (`ATT-6036`) | Open (Top of Backlog) | `DatabaseBackfillManagerTest` `SQLiteException: table WorkoutSummaries already exists` |

> [!WARNING]
> **⚠️ PO Escalation: Persistent QA Non-Conformance (`REQ-PRO-091.4`, ASPICE `SUP.1 BP6`)**
> Dimension 3 (`4-Augen Role Separation`) non-conformance recurred across **2 consecutive `aaa-v2` sprints** (`2026-11-21_03_Nacht` $\rightarrow$ [`ATT-9595`](https://atrainingtracker.atlassian.net/browse/ATT-9595); `2026-11-22_01_Früh` $\rightarrow$ [`ATT-9641`](https://atrainingtracker.atlassian.net/browse/ATT-9641) and [`ATT-9642`](https://atrainingtracker.atlassian.net/browse/ATT-9642)).
> **Root Cause**: `tools/jira_util.py`'s `qa-audit` command still validates `tier-b` and `tier-c` tickets against the retired pre-`aaa-v2` 5-subtask `agent2` prose model rather than `ATT-9543` (`auto_gate.py` + `agent2` Stage 6 Cold Review).
> **Escalation Rationale**: Prioritizing a single PO-approved tooling ticket to align `qa-audit` with `aaa-v2` (consolidating `ATT-9595`, `ATT-9641`, `ATT-9642`) will restore genuine SUP.1 signal-to-noise ratio and prevent repeated false-positive `NON-COMPLIANT` audits in future Schichten.

### 4.3 Quality Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)
Spot-checked completed Gate 3 (`ATT-9635` `ATT-8126`, `ATT-9636` `ATT-6034`, `ATT-9600` `ATT-8124`) and Stage 6 Cold Review verdicts (`ATT-6036`, `ATT-8121`, `ATT-8122` Claude; `ATT-9508`, `ATT-9172`, `ATT-8123`, `ATT-8124`, `ATT-9069`, `ATT-8126`, `ATT-6034` Gemini Neon High):
* **Observed False-Pass Rate**: **`0%`** across sampled Gate 3 and Stage 6 verdicts; every sampled `BOUNCE` / `REVISION` cited concrete line-level defects (concurrency races, missing `@VisibleForTesting` isolation, XML bundle erasure, compiler warnings) verified against the repository diff.
* **Escape Insight**: The two Tier-2 escapes (`ATT-9639` in `MeasuredNfrBenchmarkTest` and `ATT-9640` in `DatabaseBackfillManagerTest`) occurred in *cross-package* test suites outside the author/Stage-6 targeted package filters, validating the continued necessity of Ceremony 2's batched `build.*` + `database.*` Lean Tier-2 Gate.

---

## 5. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-22 | Sprint Coordinator (`ATT-9596`) | `ATT-9596` | Initial Ceremony 2 Sprint Review & Retrospective for Schicht `2026-11-22_01_Früh` (Sprint 471). |
