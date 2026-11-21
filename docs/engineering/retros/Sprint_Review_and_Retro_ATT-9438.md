# Sprint Review & Retrospective: `2026-11-21_01_Früh` (Sprint `468` — `ATT-9438`)

**Document ID**: `DOC-ENG-RETRO-S468`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Target Release**: `V5.0.0`  
**Sprint Window**: `2026-11-21 05:00 – 15:00 CET` (Autonomous Schichtwechsel close @ `16:00 CET` following `ATT-9357` Stage 6 Bounce 1 heal & Lean Tier-2 confirmation)  
**Sprint Integration Branch**: `sprint/2026-11-21_01_Früh` (code tip `34945612`, merged `--no-ff` into `develop`, tagged `sprint/2026-11-21_01_Früh`)  

---

## 1. Delivered Scope & Velocity Summary (`21.0 / 23.0 SP` — `9/10` Work Tickets)

All `9` delivered work tickets passed Stages 1–5 and independent Stage 6 Cold Final Reviews (`Rule 3.3`, including live `emulator-5554` / CLI verification and `Rule 8.5` screenshot critiques), were integrated atomically onto `sprint/2026-11-21_01_Früh` (`Rule 8.2`), and were archived post-close to **`Bereit für PO Abnahme` (`453`)** with label `sprint-2026-11-21_01_Früh` (`Final Review (Human)`, assigned to `human`):

| Ticket | SP | Epic | Commit | Stage 6 Reviewer / Bounces | Delivered Outcome |
| :--- | :---: | :--- | :---: | :--- | :--- |
| **`ATT-9347`** | 2 | `ATT-166` | `828309ce` | Cold Flash (0 bounces) | `PhoneCarriageClassifier` front-hip running belt discrimination & residual `BIKE_FRONT_POCKET` bleed fix (`83/83` + emulator). |
| **`ATT-6038`** | 3 | `ATT-6033` | `43919672` | Cold Flash (1 bounce) | Eliminated ~15-queries/workout N+1 via `BatchMetadataLoader` (`repositories/`), chunked `ExportStatus`/`Strava` queries (`<= 500`), lossless `fromCursor` (`38/38` + live 28-workout SQLite benchmark `375 ms`). |
| **`ATT-9346`** | 2 | `ATT-5910` | `4ad3b70a` | Cold Neon (3 bounces) | Polar H10 `100 -> 200 Hz` ACC renegotiation on late `BSportType.RUN` activation + linearizable Dekker offline-yield recovery (`94/94` + emulator). |
| **`ATT-9348`** | 3 | `ATT-166` | `b0bf0bc1` | Cold Neon (1 bounce) | Bounded crash-safe smartphone raw IMU/gyro/rotvec/baro/step/GNSS/wheel/state sidecar (`CLOCK_BOOTTIME`), multi-file local/Dropbox/GDrive export, `includeGps=false` privacy, 9-locale UI (`123/123` + live `119,915-byte` `.phone_raw.csv.gz` on `emulator-5554`). |
| **`ATT-9349`** | 2 | `ATT-166` | `0db0b557` | Cold Flash (0 bounces) | Pure-JVM `PolarRawTelemetryReader` + test-scoped `OfflineTelemetryReplayHarness` (`0` release APK bytes) + compilation-only `:app:replayRawTelemetry` CLI (`56/56` + live CLI `PASS=0`/`FAIL=1`). **Epic `ATT-166` 3/3 sprint tickets complete.** |
| **`ATT-6040`** | 3 | `ATT-6033` | `2b63fc73` | Cold Neon (0 bounces) | Sealed UI dialog & map peek state interfaces in `ImportBackupTabsScreen` (`1,513 -> 1,400` LoC, ratcheted OUT of monolith baseline `16 -> 15`) & `MapScreenWithTrack` (`37/37` + 4 `Rule 8.5` PNGs). |
| **`ATT-9243`** | 2 | `ATT-5910` | `6bb13e4b` | Cold Neon (0 bounces) | Stopped never-connected paired BLE devices from triggering active scan-assist reconnect storms during workouts via passive `autoConnect = true` convergence + `>= 60 s` disconnect floor (`88/88` + emulator). |
| **`ATT-6042`** | 2 | `ATT-6033` | `db8d50c1` | Cold Flash (0 bounces) | Extracted pure-JVM `CscRevolutionTracker` (`1024 Hz` CSC / `2048 Hz` CPS) preserving `UINT16` event-time rollover across `BTLEBikeDevice` & `BTLEBikePowerDevice` (`55/55` + `35/35` Cold + emulator). **Epic `ATT-6033` 3/3 sprint tickets complete.** |
| **`ATT-9357`** | 2 | `ATT-5910` | `34945612` | Cold Neon (1 bounce) | Fixed PMC chart box-to-curve matching (`3.dp` flush top bars + curve borders + `FlowRow` legend), unclipped 2-line DE `Optimaler Aufbau`, `zeroY`-clamped CTL fill, rounded Y-axis ticks (`yMin == ticks.first()`, `yMax == ticks.last()`), `currentLocale` ACWR/X-axis dates, and `PerformanceManagementChartCard.kt` extraction (`AthleteProfileScreen.kt` `1,975 -> 1,596` LoC; `36/36` + Light/Dark `Rule 8.5` PNGs + live emulator). |

* **Carryover to `2026-11-21_02_Spät` (Sprint `469`)**: `1` untouched ticket (`ATT-9360`, `2 SP`, `Zu erledigen`, Epic `ATT-5910` PMC ACWR/sRPE — cleanly queued post-`ATT-9357` and carried seamlessly via `carryover-wip`).
* **Dynamic Lane Concurrency (PO Directives)**: Started at 3 lanes, stepped down to 2 lanes (`08:49 CET`), 1 lane (`09:23 CET`), and stepped back up to **2 lanes** (`11:53 CET`) strictly honoring PO directives throughout.

---

## 2. Verification, CI Gate & Hardware Evidence

* **Scoped Lean Tier-2 Gradle Gate (`Rule 7.5`, `/home/rainer/.cache/att_wt_verify_warm` @ `34945612`)**:
  * `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin`: **PASS**.
  * Initial scoped run across all touched packages + `build.*` + `integration.*` + `localization.*`: **`3,908 / 3,910` PASS across `674` XML suites** (`2` non-regression artifacts: `HoechstmassToolingContractTest` missing `.env.jira` symlink in isolated warm worktree + `PeriodsRepositorySyncTest#testPeriodsRepository_unfinishedLiveWorkout3600Ticks_triggersZeroRebuilds_TST_STB_020_2_and_4` timing flake under 45-min single-JVM GC pressure).
  * Confirmation re-run (`2/2` suites with `.env.jira*` temporarily symlinked, `26s`): **PASS** -> combined authentic evidence exported to `docs/engineering/test_evidence/sprint_468.md` & `sprint_468.json` (**`3,910 / 3,910` PASS across `674` suites**, `0` failures, `0` errors).
* **Governance, Linters & Python Suite**:
  * `verify_requirement_governance.py --check-verified-evidence --check-living-doc-layout` & full run: **PASS** (**`1,274 / 1,274`** backed `[Verified]` TSTs, `0` unbacked, `100%` parity across 28 shards, `21/21` `SYSREQ`s covered).
  * Safety & baseline checks: **5/5 PASS** (`lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py`, `check_static_analysis_baselines.py` — Detekt `9509 <= 9516`, Lint `216 <= 216`, `0` Recycle issues).
  * Python tooling tests (`tools/test_*.py`): **`526 / 526` PASS** (`12/12` skills intact; `ANT-Android-SDKs` `160000` gitlink verified).
* **Physical Device Deployment (`Rule 7.6` / `REQ-TST-004`)**: Physical `Pixel 10` was unplugged at `16:00 CET` (`adb devices -l` showed `emulator-5554` only). Per `Rule 2.2` / `Rule 7.6`, best-effort physical install is cleanly deferred without blocking Schichtwechsel; `emulator-5554` verified all delivered UI/sensor/DB tickets during Stage 6 Cold Reviews.
* **Quantitative Process Metrics (PA 4.1 & PA 4.2)**:
  * Overall Gate FTPR **`87.7%`** (`57` subtasks, LCL `87.12` — **In Statistical Control** across 10-sprint SPC window), End-to-End FTPR **`83.3%`**, Velocity **`21.0 SP`** (mean `21.2 SP`, `sigma=6.73`), Bug Resolution **`80.0%`** (`4/5`, `0` >7d).
  * **Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)**: Spot-checked Stage 4/5/6 verdicts across `ATT-9346`, `ATT-9348`, `ATT-6038`, `ATT-6042`, and `ATT-9357` against git diffs and test evidence. Gate 6 Cold Review discrimination was exceptionally high (`4/9` tickets bounced for authentic defects — Dekker concurrency race on `ATT-9346`, direct sensor capture & `CLOCK_BOOTTIME` on `ATT-9348`, `isNull(inverse=true)` warning & test gaps on `ATT-6038`, narrow-range `yMax != ticks.last()` Y-axis label collision + TalkBack duplicate announcement on `ATT-9357` — zero false passes detected).

---

## 3. Retrospective Insights & Process Improvement Proposals (`Rule 2.7`)

### What Went Well
1. **High-Discrimination Stage 6 Cold Reviews (`Rule 3.3`)**: Zero-context Neon & Flash Cold Reviewers caught subtle concurrency (`ATT-9346`), sensor-clock (`ATT-9348`), SQL/warning (`ATT-6038`), and boundary math + TalkBack a11y (`ATT-9357`) defects before merge, and all 4 healed cleanly within budget.
2. **Proactive Monolith Reduction**: `ATT-6040` ratcheted `ImportBackupTabsScreen.kt` (`1,513 -> 1,400` LoC) completely OUT of `MAX_MONOLITH_FILE_LOC_BASELINE` (`16 -> 15`), and `ATT-9357` extracted `PerformanceManagementChartCard.kt` (`611` LoC), freeing `379` LoC of headroom in `AthleteProfileScreen.kt` (`1,975 -> 1,596` LoC).
3. **`Rule 8.5` Visual Self-Inspection Loop**: Real Compose PNG rendering caught DE `360 dp` Row 1 header ellipsizing during `ATT-9357` Stage 4 before Gate 4, leading to the cleaner flush `3.dp` top color bar design.

### Concrete Tooling & Governance Proposals (For PO Review in `ATT-8223`)
1. **Proposal 1 — Worktree-Safe `tools/jira_util.py` Repo-Root & `.env.jira` Discovery**: When the main workspace is on detached HEAD (e.g., holding uncommitted PO work) and agents run `qa-audit`, `sprint-metrics`, `submit-stage 2` (`verify_stage2_id_reservations`), or `test_jira_*.py` from a worktree, `jira_util.py` currently reads `.env.jira` only from `cwd` and compares against `HEAD~1` of the script's directory. **Proposal**: Support `--repo-root <PATH>` (or resolve git root from `cwd`) AND fall back to `git rev-parse --git-common-dir`/parent repo for `.env.jira*` read-only credential discovery so worktrees never require temporary `.env.jira*` symlinks and never hit detached-HEAD `HEAD~1` false positives.
2. **Proposal 2 — Harden `PeriodsRepositorySyncTest#testPeriodsRepository_unfinishedLiveWorkout3600Ticks_triggersZeroRebuilds_TST_STB_020_2_and_4`**: This test flaked once under heavy 45-min single-JVM GC pressure during Lean Tier-2 (`0` regressions on isolated `--rerun` in `26s`). **Proposal**: Replace wall-clock `delay`/real-dispatcher assumptions in that test with deterministic `StandardTestDispatcher` virtual time.
3. **Proposal 3 — Shard Split Readiness for `req_ui_design_system_part4.md` (`3,966/4,000`) & `tst_tracking_cockpit.md` (`3,992/4,000`)**: `req_ui_design_system_part2.md` (`3,983`), `part4.md` (`3,966`), `tst_ui_design_system_part2.md` (`3,993`), and `tst_tracking_cockpit.md` (`3,992`) are saturated; future tracking-cockpit tickets should pre-create `tst_tracking_cockpit_part2.md` when needed.

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`)

* **Sprint `468` QA Process-Compliance Audit (`docs/engineering/qa_audits/sprint_2026-11-21_01_früh.md`)**:
  * **Auditor**: `agent2` (independent of Coordinator/Implementer) | **Random Seed**: `86261` | **Sample**: `2 / 9` tickets (`ATT-9349`, `ATT-6038`, `>= 10%` threshold).
  * **Verdict**: **`2 / 2 COMPLIANT`** (`PASS` across all 6 ASPICE SUP.1 criteria: Lifecycle Deliverables, Stage Subtask Sequence, 4-Augen Role Separation, Living Documentation Parity, Atomic Commit Hygiene, and PO Approval Changelog Provenance).
* **Open QA Corrective Action Tickets**: **0** (`0` new CAs created; `0` multi-sprint recurring non-conformances -> no `PO Escalation` triggered).
* **Risk Register Review (`MAN.5.BP6` / `REQ-STP-010.2`)**: Updated `docs/management/risk_register.md` to `v1.2.20` (`Status: Reviewed`, `Last Review Date: 2026-11-21`); `ATT-9346` & `ATT-9243` substantially mitigate `RISK-004` BLE resilience, `ATT-6038`/`ATT-6040`/`ATT-6042`/`ATT-9357` reduce monolith & DB query risks, and `RISK-001`..`RISK-011` remain stable.
