# Sprint Review & Retrospective: 2026-11-20_03_Nacht (Sprint 467 / ATT-9387)

**Document ID**: `DOC-RET-467`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Sprint**: `2026-11-20_03_Nacht` (ID: `467`, `22:00–05:00 CET`)  
**Governing Ticket**: `ATT-9387` | **Weekly Rollup**: `ATT-8223`  

---

## 1. Sprint Delivery Summary (`24.0 SP` across `11/11` Delivered Tickets, `0` WIP Carryover)

| Ticket | SP | Commit | Summary | Stage 6 Verdict |
| :--- | :---: | :--- | :--- | :--- |
| `ATT-9175` | 2 | `ed05d679` | Pause `PolarOnlineStreamingBridge` reconnect & 5s GATT teardown during offline backfill sync | Bounce 1 Neon PASS |
| `ATT-8995` | 1 | `d60bf458` | Give `SensorFieldView` sparkline canvas adequate vertical height when Value + Graph are both enabled (`Rule 8.5` PNGs) | 1st-Pass Neon Visual PASS |
| `ATT-6048` | 3 | `a88a0672` | Persist missing 4 Auto-Pause keys in `saveTuningConfig`, introduce `BoundedPref` (`-264` LoC) & centralize `SettingsDataStore` zone defaults (`-17` LoC) | 1st-Pass Flash PASS |
| `ATT-9352` | 2 | `b3ea47f6` | Headless `BuildConfig.DEBUG` `DebugHeapWatchdog` (`files/debug_heap/` CSV + one-shot `files/debug_hprof/`), backup XML exclusions & `Rule 7.6` Pixel 10 heap hygiene | Bounce 1 Flash PASS |
| `ATT-9343` | 3 | `f8c03d6f` | Fix 512 MB Heap OOM during Dual Polar Live-IMU + live ATT Fused EKF (`UnicycleOdometryEkf(recordSnapshots=false)`, `BleLogger.setLoggerInterface(null)`, `DROP_OLDEST` PMD/recorder backpressure, zero-`String.format` CSV, `scope.cancel()`) | 1st-Pass Neon PASS |
| `ATT-6047` | 3 | `e9919500` | Encapsulate `WorkoutCluster` centroid/extrema/bounds math (`-71` LoC in `WorkoutClusterEngine`) & fix move/delete altitude extrema/bounds | 1st-Pass Flash PASS |
| `ATT-6045` | 2 | `6748d691` | Replace `SimpleDateFormat` singletons in `Gpx/TcxWorkoutParser` with thread-/DST-safe `java.time` `WorkoutTimestampParser` & fix `BaseExporter` column index `0` | 1st-Pass Flash PASS |
| `ATT-9244` | 1 | `33a060a1` | Stop `PebbleService` immediately (`23 ms` live) when appMessages unsupported & close `PebbleView.db` connections cleanly | 1st-Pass Flash PASS |
| `ATT-9386` | 2 | `8a10a18a` | Archive 2026-KW47 Weekly Multi-OS & 9-Language Emulator UI Verification Sweep (`docs/engineering/emulator_sweeps/2026-11-21_sweep.md` + 4 PNGs) | 1st-Pass Flash PASS |
| `ATT-9344` | 2 | `4d741c1d` | Fix mid-workout crash recovery Lap 1 drop (`MyAccumulatorSensor` `mZeroValue`), Polar raw sidecar `.tmp` salvage + export fallback, silent barometer re-anchor & pre-slew ERTS compensation (`TrackerService.java` `+0` net LoC; live `am crash` verified) | 1st-Pass Neon PASS |
| `ATT-6041` | 3 | `2319c1e1` | Consolidate marginal histograms, X-axis ticks (`TelemetryChartCanvasHelpers.kt`) & cluster Lite-Map thumbnails via `PathPreviewMap` (`-49`/`-108`/`-164` LoC; Light/Dark `Rule 8.5` PNGs + live emulator verified) | 1st-Pass Neon Visual PASS |

* **Zero WIP Carryover**: All `11/11` sprint work tickets (`24.0 SP`) reached `Final Review (Human)` before the `05:00 CET` Schichtwechsel (`0` unfinished carryovers, `0` untouched returns).
* **Quantitative Metrics (PA 4.1 / PA 4.2)**:
  * **Overall Gate FTPR**: **`94.9%`** across `59` sub-tasks (Gate 1 `90.9%`, Gate 2 `100.0%`, Gate 3 `100.0%`, Gate 4 `84.6%`, Gate 5 `100.0%`, Stage 6 `81.8%` — `2` Stage 6 bounces on `ATT-9175` & `ATT-9352` healed cleanly before merge).
  * **Bug Resolution (SUP.9)**: **`100.0%`** across `8/8` bugs (`1` Critical `ATT-9343`, `3` Major `ATT-9175`/`ATT-9344`/`ATT-9244`, `3` Minor `ATT-6048`/`ATT-6047`/`ATT-6045`, `1` Cosmetic `ATT-8995`).
  * **SPC Stability (9-Sprint Window)**: All 4 monitored metrics (`OVERALL_FTPR` `mu=93.38`, `HUMAN_ACCEPTANCE`, `COMPLETED_TICKETS` `mu=10.44`, `COMPLETED_SP` `mu=20.89`) **In Statistical Control** within `±2σ`.
  * **Living Documentation Ratchet**: **`1,265/1,265`** backed `[Verified]` TSTs (`0` unbacked, `+10` net backed TSTs vs `1,255`).

---

## 2. Scoped Lean Tier-2 Integration Gate & Physical Device Deployment

* **Scoped Lean Tier-2 (`2319c1e1`)**:
  * `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin` -> **BUILD SUCCESSFUL**.
  * Scoped unit & contract suite across all Sprint `467` touched packages + `build.*` + `integration.*` + `localization.*`: **`3,809 / 3,813` PASS** across `593` XML suites (`4` environment/cross-test-isolation failures: `HoechstmassToolingContractTest` missing `.env.jira*` in warm build worktree, `WorkoutClusterPreviewPersistenceTest` pre-existing `Dispatchers.IO`/`MutableStateFlow` batch-conflation flake, and `UncaughtExceptionsBeforeTest` cross-test coroutine bleed on `WorkoutPostProcessingCoordinatorTest` & `WorkoutClustersViewModelTest`). Re-running all 4 suites cleanly with `.env.jira*` present passed **`31 / 31` (`100%` PASS in `56s`, `0` failures)** — exported to `docs/engineering/test_evidence/sprint_467.{md,json}`.
  * `verify_requirement_governance.py --base-ref develop --check-living-doc-layout --check-verified-evidence --check-metrics --check-gates --check-qa-audits --check-reviews`: **ALL PASS** (`1,265/1,265` backed `[Verified]` TSTs).
  * Python tools suite: **`526/526` PASS**, `4/4` safety linters PASS, `11/11` skills PASS, `ANT-Android-SDKs` submodule intact (`160000`). Static baselines unchanged (`detekt=9509/9516`, `lint=216/216`).
* **Physical Pixel 10 Deployment (`Rule 7.6` / `Rule 8.6`)**:
  * Freshly stamped debug APK (`-PstampGitHash=true`, including the new `ATT-9352` headless `DebugHeapWatchdog` and `ATT-9343` OOM fix) deployed onto physical **`Pixel 10`** (`66020DLCR002FL`) from the verified Sprint `467` closure tip.

---

## 3. Retrospective Insights & Process Improvement Proposals

### What Went Well
1. **100% Delivery of Critical Stability & Refactoring Scope (`24.0 SP`, `0` WIP)**: Delivered both high-risk stability fixes (`ATT-9343` 512 MB Dual Polar Live-IMU OOM, `ATT-9344` mid-workout crash recovery verified via live `am crash` on `emulator-5554`), proactive debug heap diagnostics (`ATT-9352`), KW47 weekly emulator sweep (`ATT-9386`), and 4 major clean-code/UI-deduplication tickets (`ATT-6048`, `ATT-6047`, `ATT-6045`, `ATT-6041`) with `0` production regressions across `3,813` Tier-2 tests.
2. **Stage 6 Cold Reviews High Signal & First-Pass Quality (`81.8%` Stage 6 FTPR)**: `9/11` tickets passed Stage 6 Cold Review on the first pass (including `ATT-9343`, `ATT-9344`, `ATT-6041`, `ATT-8995`), while the 2 bounces (`ATT-9175`, `ATT-9352`) caught real GATT/watchdog sensor-count issues cleanly before merge.

### Defects & Follow-Up Proposals for PO Prioritization (`ATT-8223`)
1. **3 Genuine Defects Surfaced by KW47 Multi-OS & 9-Locale Emulator Sweep (`ATT-9386`, `docs/engineering/emulator_sweeps/2026-11-21_sweep.md`)**:
   * **Defect 1 (High — Android 12+ Crash)**: Rapid Start$\rightarrow$Stop race in `MultiLocaleUiSweepAndroidTest.kt:103-132` + `TrainingApplication.java:2020-2026`: `stopService(TrackerService)` fires before `TrackerService.performStartForeground()` executes, triggering `ForegroundServiceDidNotStartInTimeException` in `7/12` runs and leaving an orphaned `"Training unterbrochen"` modal. **Proposal**: File `severity-major` Bug under `ATT-235` to guard rapid `stopService()` against pending FGS startup.
   * **Defect 2 (Localization)**: Persisted SQLite tracking tab names (`"Standard"`, `"Karte"`, `"Runden"`) remain in German when OS locale switches to `es`/`ja`/`pt` (only Tab 1 re-localizes). **Proposal**: File `severity-minor` Bug under `ATT-5908`/`ATT-232` to store canonical keys or re-localize default tab titles on locale change.
   * **Defect 3 (API 36 Tooling)**: `tools/run_multi_os_emulator_sweep.py:602-604` dumps UI XML to `/sdcard/att_sweep_dump.xml`, failing on Android 16 (`Pixel_7_Android_36`) with `No such file or directory`. **Proposal**: File `1 SP` `ATT-8138` tooling fix to use `/data/local/tmp/att_sweep_dump.xml`.
2. **3 Living-Doc Shards at `>99%` of the `4,000`-Line Cap (`REQ-PRO-093.3`)**:
   * `docs/tests/tst_ui_design_system_part2.md`: **`3,993 / 4,000`** lines (`7` lines left)
   * `docs/requirements/req_ui_design_system_part2.md`: **`3,983 / 4,000`** lines (`17` lines left)
   * `docs/tests/tst_tracking_cockpit.md`: **`3,980 / 4,000`** lines (`20` lines left)
   * **Proposal**: File a `1 SP` `ATT-8138` shard-split maintenance ticket to split all three (`part3` / `part1-2`) before the next UI or tracking-cockpit ticket hits the hard cap.
3. **Global `~/.gradle/gradle.properties` 2 GB Kotlin Daemon Cap**: User-global `~/.gradle/gradle.properties` sets `kotlin.daemon.jvmargs=-Xmx2048m`, overriding the repo's `-Xmx3072m` and causing sporadic Kotlin daemon OOMs unless `-Dkotlin.daemon.jvm.options=-Xmx3072m` is explicitly passed. **Proposal**: Raise `~/.gradle/gradle.properties` to `-Xmx3072m` (or remove the user-global override so repo `gradle.properties` wins).
4. **Latent `WorkoutClusterEngine.splitCluster` Key Mismatch (Untouched by `ATT-6047`)**: `WorkoutClusterEngine.kt:1459` (`splitCluster`) still references `"BoundMinLat"` instead of `WorkoutSummaries.BOUND_MIN_LAT` (`"boundMinLat"`). **Proposal**: File a `1 SP` Bug under `ATT-68`/`ATT-6033`.
5. **Worktree Alignment for `qa-audit` & `sprint-metrics` When Root Repo Is Detached**: Invoking `tools/jira_util.py` from a detached root repo (`104a028f`) caused `qa-audit` to inspect an older working tree and emit 3 false-positive CAs (`ATT-9435`–`ATT-9437`, cancelled to `Final Review (Human)`). **Proposal**: Add a `--repo-root` CLI flag to `qa-audit` and `sprint-metrics` (and warn when `HEAD` is detached behind `sprint/<NAME>`).

---

## 4. QA Findings & Corrective Actions

* **QA Process Audit (`agent2`, seed `66471`, `docs/engineering/qa_audits/sprint_2026-11-20_03_nacht.md`)**: **`2/2 COMPLIANT`** (`ATT-9343`, `ATT-9386`), **0 process non-compliances** and **0 valid corrective action tickets** created. *(Three false-positive CA tickets `ATT-9435`–`ATT-9437` generated by an initial detached-root invocation were annotated and routed to `Final Review (Human)` for PO dismissal.)*
* **Gate 1–5 Verdict Spot-Check (`REQ-PRO-092.6`)**: Spot-checked `ATT-9343` (`ATT-9394`/`ATT-9399`/`ATT-9401`/`ATT-9406`/`ATT-9411`), `ATT-9344` (`ATT-9414`/`ATT-9417`/`ATT-9418`/`ATT-9429`/`ATT-9431`), and `ATT-6041` (`ATT-9423`–`ATT-9425`/`ATT-9433`/`ATT-9434`). All audited verdicts maintain strict `agent1` vs `agent2` role separation, cite exact commit SHAs and test/emulator/PNG evidence, and show **0 false-pass rubber-stamps**. Two transient Gate 4 `REVISION -> PASS` retries within `27–44s` on identical SHAs (`ATT-6048`/`ATT-9391` @ `2038d89b` and `ATT-9343`/`ATT-9406` @ `344ad639`, where the initial `REVISION` log body already stated `PASS`) are noted transparently as CLI invocation slips rather than code defects.
* **Multi-Sprint Escalation Check (`REQ-PRO-091.4`)**: No open valid QA corrective action tickets `> 2` sprints old.
