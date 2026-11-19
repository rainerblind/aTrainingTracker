# Sprint Review & Retrospective: Sprint `2026-11-18_03_Nacht` (`ATT-9028`)

* **Schicht Window**: `2026-11-18 22:00 CET` – `2026-11-19 05:00 CET` working shift (`Jira Sprint ID: 461`; Lean Tier-2 Gate & Ceremony 2 closeout executed at `05:00 CET` `Schichtwechsel`)
* **Sprint Branch**: `sprint/2026-11-18_03_Nacht` (`906504b8` pre-retro integration HEAD -> `--no-ff` into `develop` upon close)
* **Execution Mode**: 3 Parallel Lanes (`Lane A`: `ATT-235` Stability; `Lane B`: `ATT-166` / `ATT-5908` Sensor Fusion; `Lane C`: `ATT-68` / `ATT-5910` Workouts & BLE Connectivity), `Flash` for routine stages and `Pro` strictly for complex/graphical tasks and bounce escalations (`Rule 3.3`/`Rule 7.2`)

---

## 1. Sprint Review — Delivered & Verified (`3 / 4` Active Work Tickets = `7.0 SP`)

All 3 finished work tickets passed Stages 1–5 + independent zero-context Stage 6 Cold Review (`Pro`) + live `emulator-5554` verification into `Final Review (Human)` (labeled `sprint-2026-11-18_03_Nacht`, queued post-close in `Bereit für PO Abnahme` ID `453`):

| Lane | Ticket | SP | Final Commit | Stage 6 | Summary & Verification Highlight |
| :---: | :--- | :---: | :---: | :---: | :--- |
| C | [ATT-8916](https://rainerblind.atlassian.net/browse/ATT-8916) | 2 | `941a1ea5` | **Pro** `PASS` | Segmented activity & phone carriage mode timeline bar in Workout Details (`REQ-PER-045` / `TST-PER-053`); `152/152` across 30 suites + live `emulator-5554` 1,200-row UI verification |
| C | [ATT-8998](https://rainerblind.atlassian.net/browse/ATT-8998) | 2 | `d2209175` | **Pro** `Bounce 1 -> PASS` | Unblock `DeviceManager.mSearchQueue` on `MyBTLEDevice.mSearchTimeoutRunnable` timeout, decouple `autoConnect` background reconnect, and guard `CONNECTED_TO_GATT` inside `mGattLock` (`REQ-CON-051`); `125/125` across 19 suites + `emulator-5554` smoke |
| A | [ATT-8999](https://rainerblind.atlassian.net/browse/ATT-8999) | 3 | `906504b8` | **Pro** `Bounce 1 -> PASS` | Stop 1-Hz `WORKOUT_UPDATED_INTENT`, `updateExtremaValueInMemory`, and `_allWorkouts` OOM churn via `LiveWorkoutBroadcastTracker` (`REQ-STB-020`); `450/450` across 69 suites, `0` warnings, `emulator-5554` 0-churn logcat proof |

* **Untouched Returned to `Vom PO Freigegeben` (`447`, via `return-unstarted 461`)**: `ATT-9018` (`3 SP`), `ATT-8969` (backfill fix), `ATT-8891` (`3 SP`), `ATT-8917` (`3 SP`) — preserved untouched and top-ranked ahead of `ATT-8199` for immediate morning pickup.

### Continuous WIP Rollover (`carryover-wip` to `2026-11-19_01_Früh`, `1` Ticket = `3.0 SP`)
* **[ATT-8895](https://rainerblind.atlassian.net/browse/ATT-8895)** (`3 SP`, `ATT-166` / `ATT-5908`, `Pro`): Cleanly rebased atop `906504b8`/`e62a6ba4` on `feature/ATT-8895` (`75393e05`). Stage 5 Bounce 2 (`201/201` across 29 suites + live `emulator-5554`) and Gate 5 Bounce 2 (`ATT-9051` `Erledigt`, `254/254` across 34 `--rerun` suites, `0` new warnings) **PASSED**, advancing `ATT-8895` to **`Final Review (Agent)`** right at the `05:00 CET` cutoff. Ready for immediate Stage 6 Cold Final Review Bounce 2 (`Pro`) and squash merge onto `sprint/2026-11-19_01_Früh`.

---

## 2. Lean Tier-2 Integration Gate & Quantitative Process Metrics (PA 4.1 & PA 4.2)

* **Lean Tier-2 Integration Gate (`906504b8`, `--max-workers=1 --rerun --no-kover`, `24m 37s`)**: Executed `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`, and `:app:testDebugUnitTest` across all Sprint `461` touched suites (`ActivityCarriageTimeline*`, `WorkoutRepository*`, `TrackOnMapAftermathViewModel*`, `DeviceManagerSearchQueueTest`, `BLEReconnectRobustnessTest`, `MyBTLEDevice*`, `BANALService*`, `LiveWorkoutBroadcastTrackerTest`, `TrackerService*`, `PeriodsRepository*`, `AthleteProfileViewModel*`, `KnownLocationsViewModel*`), all `28` clean `build.*` contract/NFR suites, `4` clean methods of `DeadCodeEliminationContractTest` (isolating pre-existing `ATT-8823` `EditDeviceDialog.kt` `testComposePreviewsVisibilityAudit`), and all `integration.*`, `localization.*`, and `architecture.*` suites: **`625/625` PASS across `121` XML suites** (`0` failures, `0` errors, `0` skipped, suite sum `703.31s`).
* **Physical Device Deploy (`REQ-TST-004` / `Rule 7.6`)**: Attached physical **`Pixel 10` (`66020DLCR002FL`)** received `app-debug.apk` cleanly via `ANDROID_SERIAL=66020DLCR002FL ./gradlew :app:installDebug` (`9s`, `Installed on 1 device`).
* **Governance & Safety Verification**:
  * `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py`, `check_static_analysis_baselines.py` (`detekt=9509 <= 9516`, `lint=238 <= 238`), and `verify_requirement_governance.py --base-ref refs/heads/develop` (`21/21` checks incl. `21/21` `SYSREQ`s, `68` `SensorType` Javadocs, commit hygiene across `4` commits) **ALL PASS (`exit 0`)**.
  * `tools/hoechstmass/tools`: **`63/63` PASS (`3.195s`)**. `tools/test_*.py`: `509/515` PASS (`6` pre-existing baseline-drift assertions outside Sprint `461` scope: `detekt` ceiling `9509!=9516`, `SensorType` count `67!=68`, `ATT-8848` `TST-TRK-087`, and `test_scenario_3/4/11`; tracked below for PO-approved tooling sync).
* **Scorecard & PA 4.2 SPC Special Cause RCA (`docs/engineering/metrics/process_metrics.jsonl`)**:
  * **Throughput (`sprint-metrics`)**: `4/5` sprint issues (`80.0%` incl. `ATT-9028` / `ATT-8895` in `Final Review (Agent)` = `10.0 SP` Jira count; actual merged `Final Review (Human)` delivery = **`3/4` work tickets = `7.0 SP`**).
  * **Gate FTPR**: Overall Gate FTPR = **`81.5%`** (`27` subtasks; **below `LCL 89.68%`**), Gate 1–6 End-to-End FTPR = **`74.2%`**, Stage 6 Cold Review FTPR = **`25.0%`** (`3` bounce cycles out of `4` Stage 6 submissions).
  * **PA 4.2 Root-Cause Analysis (Special Cause Variation)**:
    1. *FTPR (`81.5% < 89.68%`)*: Sprint `461` deliberately tackled three high-complexity concurrency/OOM/IMU-DSP fixes (`ATT-8999`, `ATT-8998`, `ATT-8895`). Adversarial Stage 6 Cold `Pro` reviews surfaced genuine defects pre-merge (deprecated `SensorType.shortNameId` compiler warnings in `ATT-8999`, `mSearchTimeoutRunnable` `CONNECTED_TO_GATT` lock race + SWE.3 drift in `ATT-8998`, CHRE timestamp clamping + gyro-absent stride undercount + `MyAccumulatorSensor` initial listener broadcast dip in `ATT-8895`), confirming Stage 6 discrimination worked as intended while motivating stronger pre-Stage-6 subagent self-checks on listener rebind semantics and zero-warning compilation.
    2. *Velocity / Count (`7.0 SP` merged / `10.0 SP` metrics vs `mu = 25.0 SP`)*: Bounded `7h` night shift + multi-bounce Stage 6 verification serialization on a single Gradle lock and single `emulator-5554` (`3.0 SP` `ATT-8895` reached `Final Review (Agent)` right at cutoff and carries over intact).
  * **Static Analysis Baselines**: Detekt `9509 <= 9516` (`0` delta), Android Lint `238 <= 238` (`0` delta).
* **Exported Evidence**: `docs/engineering/gates/sprint_2026-11-18_03_nacht.md` (`41` records), `docs/engineering/reviews/sprint_2026-11-18_03_nacht.md` (`SKIPPED` autonomous closeout), `docs/engineering/test_evidence/sprint_461.{md,json}` (`625/625` PASS, `--no-kover`), `docs/engineering/qa_audits/sprint_2026-11-18_03_nacht.md`, `docs/management/risk_register.md` (`v1.2.6`).

---

## 3. Retrospective Insights & Proposed Tooling/Governance Improvements (`REQ-PRO-092.6`, `REQ-PRO-117`)

1. **Tooling Improvement Proposal (For Future PO-Approved Ticket)**:
   - Update `--merge-ticket` in `tools/parallel_sprint_lock.py` to auto-prepend `<KEY>: ` if `--summary` omits `ATT-\d+` (prevented manual commit rewording at `05:00 CET` close).
   - Sync `tools/test_verify_requirement_governance.py` (`68` `SensorType` constants, `ATT-8848` `TST-TRK-087` ratchet) and `tools/test_check_static_analysis_baselines.py` (`9509` detekt count) under a dedicated `ATT-8138` ticket.
2. **Audit Sampling Timing**: When `qa-audit` samples an active `carryover-wip` ticket (`ATT-8895`), its branch (`feature/ATT-8895`) is intentionally unmerged prior to Stage 6 PASS, causing branch-based checks against `sprint/<CLOSING_SCHICHT>` to flag missing merged artifacts.

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`)

* **QA Audit (`docs/engineering/qa_audits/sprint_2026-11-18_03_nacht.md`, `--as agent2`, seed `76626`)**:
  * `ATT-8916`: **`COMPLIANT`** across all 6 ASPICE process criteria (`6/6 PASS`).
  * `ATT-8895`: Flagged **`NON-COMPLIANT`** (`Deliverables`, `Living Docs Parity`, `Commit Hygiene`) strictly because `qa-audit` inspected `sprint/2026-11-18_03_Nacht` *before* `feature/ATT-8895` (`75393e05`) completed Stage 6 and merged. Auto-created corrective tracking items **[ATT-9052](https://atrainingtracker.atlassian.net/browse/ATT-9052)**, **[ATT-9053](https://atrainingtracker.atlassian.net/browse/ATT-9053)**, and **[ATT-9054](https://atrainingtracker.atlassian.net/browse/ATT-9054)** will be verified and resolved in `2026-11-19_01_Früh` immediately after `ATT-8895` passes Stage 6 and merges onto `sprint/2026-11-19_01_Früh`.
