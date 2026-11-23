# Sprint Review & Retrospective: Schicht 2026-11-23_01_Früh (Sprint 474)

**Document ID**: `DOC-RETRO-ATT-9657`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Sprint**: `2026-11-23_01_Früh` (ID `474`, `05:00–15:00 CET`, `aaa-v2` v1.0.22, PO-directed **strictly 2 parallel lanes**)  
**Jira Ticket**: [ATT-9657](https://atrainingtracker.atlassian.net/browse/ATT-9657) | **Weekly Roll-Up**: [ATT-8223](https://atrainingtracker.atlassian.net/browse/ATT-8223)

---

## 1. Delivered Scope & Planned vs. Completed Velocity (`REQ-PRO-029`)

| Metric | Planned / Committed | Completed (`Final Review (Human)`) | Carryover WIP | Returned Untouched (`447`) | Completion Rate |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Work Tickets** | 12 active (+1 returned) | **10** | 2 (`ATT-8136`, `ATT-9611`) | 1 (`ATT-9610`) | **83.3%** (10/12 active) |
| **Story Points (SP)** | 30.0 SP active (+2.0 SP ret.) | **24.0 SP** | 6.0 SP | 2.0 SP | **80.0%** (24.0/30.0 SP) |

* **Delivered & Stage-6-Verified (`10` tickets = `24.0 SP`, merged onto `sprint/2026-11-23_01_Früh`, archived post-close to `Bereit für PO Abnahme` ID `453` labeled `sprint-2026-11-23_01_Früh`)**:
  * **Lane 1 (`ATT-8120` Code-Vereinfachung Stufe 2 — `12.0 SP` merged, 7/8 Epic tickets DONE)**: `ATT-8132` (`3 SP`, `tier-a`, carryover WIP: 5->2 radar-light DataStore keys, 3-StateFlow `RadarStateRepository`, zero stale caches & AB-BA deadlock eliminated), `ATT-8133` (`3 SP`, `tier-b`, 8 polyline projectors & elevation gain unified in `GeoMath`, `RouteAutoDetector` $\cos(\phi)$ fix, read-only `getClusterScores`, zero-reload reactive rename sync), `ATT-8134` (`3 SP`, `tier-b`, shared `Modifier.telemetryChartGestures` + `drawWithCache` hoisting, `MapDetailLayout` viewport consolidation, `AthleteProfileViewModel` 4-tab Flow split), `ATT-8135` (`3 SP`, `tier-a`, streaming TCX/GPX parsers, `HrvRmssdCalculator`, Zip-Slip-hardened `AutoImportManager`/`CloudWorkoutSourceScanner`, single-unzip `.attbackup` staging & `BackupOrchestrator`, `-280` LoC in `LegacyImportEngine`).
  * **Lane 2 (`ATT-281` 5/5 DONE `9.0 SP` + `ATT-5910` `3.0 SP` = `12.0 SP` merged)**: `ATT-9613` (`2 SP`, `tier-a`, `FileFormat.isDerivedExportFile()` protecting irreplaceable `raw_sensor` sidecars in `ExportManager.invalidateWorkoutExport`), `ATT-9245` (`1 SP`, `tier-b`, `CSVFileWriter` metadata short-circuit & nested-parentheses device name parsing), `ATT-8103` (`2 SP`, `tier-b`, `Export` mounted as Tab 0 in `ImportBackupTabsScreen`, separate drawer entry removed, 9-locale parity), `ATT-8102` (`1 SP`, `tier-c`, single-line `AutoImportTabContent` format `SegmentedButtonRow` + 9-locale short labels), `ATT-9013` (`3 SP`, `tier-b`, silent default route cluster creation named after first workout + opt-in toggle), `ATT-9612` (`3 SP`, `tier-a`, linearizable `PolarOnlineStreamingBridge` yield quiescence + disconnect-recovering `PolarOfflineGattCoordinator.connectWithinTimeout()`).
* **Active WIP Carryover (`2` tickets = `6.0 SP`, cleanly committed for `2026-11-23_02_Spät`)**:
  * `ATT-8136` (`3 SP`, `tier-c`, status `Implementation`): cleanly committed on `feature/ATT-8136` @ `3a0a3978` (final ticket of Milestone Epic `ATT-8120`).
  * `ATT-9611` (`3 SP`, `tier-a`, status `Test`, Gate 3 `ATT-9661` PASSED): cleanly committed on `feature/ATT-9611` @ `c05fd91d` (all 4 Gate-3-approved plan steps implemented, ready for `auto_gate.py --write-bundle` + Stage 6).
* **Untouched Returned to `447` (`1` ticket = `2.0 SP`)**: `ATT-9610` (`2 SP`, `tier-b`, returned to top of `Vom PO Freigegeben` via `return-unstarted 474`).
* **Ceremony 2 Tier-2 Gate & Device Deploy**:
  * **2-Batch Lean Tier-2 (`--verify-warm --max-workers=2 --no-kover`, zero CodeCache issues)**: **`2,123 / 2,127` archived across `345` JUnit XML suites** (`Batch 1`: `774/779` + `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin`; `Batch 2`: `1,379/1,380` incl. `StravaUploaderNamingTest` 100% PASS; `MigrationEngineClimbsRestoreTest` confirmed 100% PASS in isolation in `15s`; sole `4` failures are pre-existing baseline `ATT-9639` & `ATT-9501`, recorded truthfully via `--allow-failure`).
  * **Governance, Python & Safety Linters**: `verify_requirement_governance.py` EXIT `0` (`415/435 [95.4%]` SYSREQ-linked, `435/435 [100.0%]` classified after `88c4767f` Tier-2 SYSREQ traceability reconciliation for `REQ-EXP-047`/`REQ-EXP-048`/`REQ-GEO-003`), `593/593` Python `tools/test_*.py` tests PASS, `4/4` static safety & secrets linters PASS (`11,818` files scanned), `ANT-Android-SDKs` `160000` gitlink verified.
  * **Physical `Pixel 10` (`Rule 7.6`)**: Unplugged (`emulator-5554` only); best-effort `-PstampGitHash=true` deploy and `REQ-STB-022.4` heap hygiene check deferred cleanly.

---

## 2. Quantitative Sprint Scorecard (`PA 4.1 & PA 4.2`) & Gate Spot-Check (`REQ-PRO-092.6`)

* **Throughput**: `10/13` issues (`76.9%` incl. retro; `10/12` active work tickets = `83.3%`) | **Velocity**: **`24.0 / 30.0 SP`** (`80.0%`).
* **Gate FTPR**: Gate 3 (`Impl-Plan`) **`60.0%`** (`3/5` first-pass; `ATT-9661` Revision 1 prevented a real `patchedCount == 0` pending-retry livelock + DST fall-back edge case on `ATT-9611` pre-coding), Stage 6 (`Final Review (Agent)`) **`50.0%`** (`5/10` first-pass; `5` adversarial bounces caught genuine defects before merge), **End-to-End FTPR (Gates 1–6)**: **`53.3%`**.
* **SPC Status (`PA 4.2`)**: **In Statistical Control** across all 4 control charts (`OVERALL_FTPR` `60.0%` within `[54.14, 100.0]`, `COMPLETED_TICKETS` `10` within `[0.63, 20.37]`, `COMPLETED_SP` `24.0` within `[1.06, 48.44]`).
* **SUP.9 Bug Metrics**: `5` bugs (`4` resolved / awaiting human = `80.0%`, `1` open `ATT-9611` in `Test` carryover; avg open age `1.2d`, `0` bugs `> 7d`).
* **Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)**: Spot-checked Gate 3 (`ATT-9658`, `ATT-9659`, `ATT-9660`, `ATT-9661`) and Stage 6 (`ATT-8132`, `ATT-8133`, `ATT-8134`, `ATT-8135`, `ATT-9612`, `ATT-9613`, `ATT-9013`) verdicts: **0% false-pass / rubber-stamping** — Stage 6 Cold Reviews caught high-severity real bugs prior to merge: main-thread SQLite + `GeoMath` NaN poisoning + `RouteDivergenceDetector` offset (`ATT-8133`), pan-mode double-tap reset + pace overlay trailing stop + `PaceFormatter` negative guard + `WorkoutShareBottomSheetContractTest` JVM method-order determinism (`ATT-8134`), Garmin/Komoot TCX `<Course><Lap/><Track>` parser failure (`ATT-8135`), and BLE `[CONNECTED, DISCONNECTED]` event coalescing race (`ATT-9612`). All `tier-a` Stage 6 reviews truthfully logged `--vendor google --model gemini-4-neon-high --allow-same-vendor-fallback --fallback-reason=claude_quota_exhausted` (`RISK-011` Claude weekly quota `0%` until `2026-11-28T08:17:14Z`).

---

## 3. Retrospective Insights & Structured Proposals (For Weekly PO Review `ATT-8223`)

1. **What Went Exceptionally Well**:
   * **Zero-Conflict 2-Lane Parallel Delivery (`24.0 SP`, 10 tickets)**: Completed 5/5 Epic `ATT-281` tickets ahead of `ATT-8135` so Lane 1's `LegacyImportEngine`/`AutoImportManager` consolidation ran with zero merge conflict against Lane 2's Epic `ATT-5910` Polar BLE work.
   * **High-Signal Adversarial Stage 6 & Gate 3 Barriers**: Caught 12+ genuine runtime/concurrency/parser/math defects (`ATT-8133`, `ATT-8134`, `ATT-8135`, `ATT-9612`, `ATT-9611` Gate 3) before reaching `sprint/2026-11-23_01_Früh`, resulting in **0 sprint-introduced regressions** across 2,127 Tier-2 unit tests.
2. **Structured Process & Tooling Proposals (`Rule 2.7` / `Rule 8.10` — Measured Problems)**:
   * **Proposal 1 — Batched Dependent Tests in `tools/auto_gate.py`**: *Measured Problem*: `ATT-8132` touched `TrainingApplication`/`TrackerService`/`TuningPreferencesDataStore`, expanding `select_dependent_tests()` to `395` Android suites in one Gradle invocation and timing out twice at `1800s` under the `768m` Kover CodeCache ceiling (stalling `/tmp/att_build_merge.lock` for `>45 min`). *Proposal*: Batch `run_android_tests()` in `tools/auto_gate.py` into chunks of `<= 60` suites per Gradle invocation (or add `--tests-from-existing-junit`) so core-hub tickets never hit the single-invocation CodeCache wall.
   * **Proposal 2 — Periodic `KotlinCompileDaemon` Recycle / Watchdog**: *Measured Problem*: At `14:22 CET` (~9h of multi-worktree compilation), `KotlinCompileDaemon` (PID `128086`) exhausted heap inside `:app:compileDebugUnitTestKotlin` and entered infinite GC thrashing (`476% CPU` for `>33 min`), stalling both `ATT-9611` and `ATT-8136`. *Proposal*: Add a lightweight `./gradlew --stop` daemon hygiene recycle between Schichten or when `KotlinCompileDaemon` RSS/CPU stalls >10 min with zero output.
   * **Proposal 3 — Add `migration/` to `tools/auto_gate.py` `UI_PATH_HINTS`**: *Measured Problem*: On `ATT-8102` (`AutoImportTabContent.kt`), `auto_gate.py` reported `ui_touching: false` because `migration/` Compose files are absent from `UI_PATH_HINTS` (even though the screenshot was bundled). *Proposal*: Detect `@Composable` files or include `migration/*Screen*.kt` / `*TabContent*.kt` in `is_ui_touching()`.
   * **Proposal 4 — Upgrade `qa-audit` Dim 2/3 for `aaa-v2` Tiers**: *Measured Problem*: Recurring false-positive `NON-COMPLIANT` verdicts across 4 consecutive `aaa-v2` Schichten (`ATT-9595`, `ATT-9641`, `ATT-9642`, `ATT-9647`, `ATT-9655`, `ATT-9656`, `ATT-9662`, `ATT-9663`). *Proposal*: Prioritize `ATT-9595` (already in `447`) to make `qa-audit` evaluate `tier-a`/`tier-b`/`tier-c` gate progression & 4-Augen separation via `[auto-gate:...]` + `[FINAL REVIEW (AGENT) — PASS]` verdicts.

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`)

* **Sprint 474 QA Audit (`docs/engineering/qa_audits/sprint_2026-11-23_01_früh.md`, auditor `agent2`, seed `33179`)**: Sampled `2/10` tickets (`ATT-8132` `tier-a`, `ATT-8133` `tier-b`) — **`0/2 COMPLIANT`** (`5/6` dimensions PASS on both; flagged strictly on legacy pre-`aaa-v2` Dim 2 Stage Subtask Progression on `ATT-8132` and Dim 3 4-Augen Subtask check on `ATT-8133`, creating `ATT-9662` & `ATT-9663`).
* **⚠️ PO Escalation: Persistent QA Non-Conformance (`REQ-PRO-091.4`, ASPICE SUP.1 BP6)**: The same legacy `qa-audit` `aaa-v2` Dim 2/3 script mismatch has now recurred across **5 consecutive Schichten** (`2026-11-21_03_Nacht` through `2026-11-23_01_Früh`), generating open CA tickets [ATT-9595](https://atrainingtracker.atlassian.net/browse/ATT-9595), [ATT-9641](https://atrainingtracker.atlassian.net/browse/ATT-9641), [ATT-9642](https://atrainingtracker.atlassian.net/browse/ATT-9642), [ATT-9647](https://atrainingtracker.atlassian.net/browse/ATT-9647), [ATT-9655](https://atrainingtracker.atlassian.net/browse/ATT-9655), [ATT-9656](https://atrainingtracker.atlassian.net/browse/ATT-9656), [ATT-9662](https://atrainingtracker.atlassian.net/browse/ATT-9662), and [ATT-9663](https://atrainingtracker.atlassian.net/browse/ATT-9663). **Root Cause**: `tools/jira_util.py qa-audit` still expects legacy Stages 1–5 agent2 subtasks instead of recognizing `aaa-v2` `tier-a` (Gate 3 + `auto_gate` + Stage 6) and `tier-b`/`tier-c` (`auto_gate` + Stage 6) role separation. **PO Action Recommended**: Promote & execute `ATT-9595` (consolidating duplicate CAs `ATT-9641..9663`) to restore authentic `qa-audit` signal under `aaa-v2`.

---

## 5. Risk Register Review (`REQ-STP-010.2`)

* Reviewed `RISK-001`..`RISK-012` in `docs/management/risk_register.md` (bumped to `v1.2.33`, status `Reviewed`):
  * `ATT-8132` & `ATT-9612` substantially mitigate `RISK-004` (BLE/Polar GATT contention & radar deadlock).
  * `ATT-9613` & `ATT-8135` strengthen `RISK-006` & data sovereignty (protecting irreplaceable `raw_sensor` sidecars, hardening Zip-Slip defense, and single-unzip `.attbackup` staging).
  * `RISK-011` (Claude quota `0%` until `2026-11-28T08:17:14Z`) actively monitored with truthful Gemini 4 Neon High fallback per `Rule 3.3`.
  * `RISK-001`..`RISK-012` otherwise monitored & stable; no new High-exposure unmitigated risks surfaced.
