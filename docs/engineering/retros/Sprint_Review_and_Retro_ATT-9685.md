# Sprint Review & Retrospective: Schicht `2026-11-24_02_Spät` (Sprint 478)

**Document ID**: `DOC-RET-478`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Ticket**: [ATT-9685](https://rainerblind.atlassian.net/browse/ATT-9685) | **Sprint Window**: `2026-11-24 15:00 – 22:00 CET` | **Process Version**: `aaa-v2` (`v1.0.22`) | **Target Release**: `V5.0.0`

---

## 1. Executive Summary & Delivery Velocity

Executed under the PO-directed **strictly 2 parallel lanes** policy across disjoint Epics (`ATT-1286` Cycling Radar & `ATT-5910` Equipment/BLE), Schicht `2026-11-24_02_Spät` delivered **5 / 5 merged Stage-6-verified work tickets (`13.0 SP`)** into `Final Review (Human)` (`sprint-2026-11-24_02_Spät` → `Bereit für PO Abnahme` ID `453`) with **0 regressions**, preserved **2 hot WIP carryovers (`3.0 SP`)** cleanly on intact feature branches for `2026-11-24_03_Nacht`, and returned **2 untouched tickets (`3.0 SP`)** cleanly to the top of `Vom PO Freigegeben` (ID `447`).

| Category | Tickets | Story Points | Keys & State |
| :--- | :---: | :---: | :--- |
| **Initially Committed (Ceremony 1)** | 9 | 19.0 SP | `5× tier-a`, `3× tier-b`, `1× tier-c` (0 WIP carryover from Sprint 477) |
| **Completed & Merged (`Final Review (Human)` → `453`)** | **5** | **13.0 SP** | `ATT-8672` (`3 SP` `tier-b`), `ATT-9178` (`3 SP` `tier-a`), `ATT-8735` (`3 SP` `tier-b`), `ATT-8996` (`2 SP` `tier-a`), `ATT-8731` (`2 SP` `tier-a`) |
| **Hot WIP Carryover to `2026-11-24_03_Nacht`** | **2** | **3.0 SP** | `ATT-8723` (`2 SP` `tier-a`, `Final Review (Agent)`, `feature/ATT-8723` @ `eb6f5fc0`, `178/178` tests PASS + 14-file SHA-256 bundle); `ATT-8106` (`1 SP` `tier-c`, `Test`, `feature/ATT-8106` @ `ecd7dab1`, code/tests/docs saved) |
| **Returned Untouched to `Vom PO Freigegeben` (`447`)** | **2** | **3.0 SP** | `ATT-8726` (`1 SP` `tier-a`), `ATT-8722` (`2 SP` `tier-b`) |

### Delivered Commits on `sprint/2026-11-24_02_Spät` (`54813693`)
1. `335476bd` — **`ATT-8672`** (`3 SP`, `tier-b`, Flash): Excised `RadarTrafficSimulator`, `SimulatedRadarDevice`, `RadarSource.SIMULATOR`, and 19 simulator strings across all 9 locales (`-2,109` LoC) while keeping real BLE/ANT+ radar & external Python simulators intact.
2. `44ca00f5` — **`ATT-9178`** (`3 SP`, `tier-a`, `severity-major`, Neon): Restricted periodic sensor re-search strictly to lost/MIA sensors (`hasConnectedInSession()` + workout-end reset), tightened steady-state presets to `15s/30s/1m/2m` (default `1m`, legacy `300/600 -> 120s` clamping) across all 9 locales, and eliminated mid-workout GATT-133 search-queue deadlocks.
3. `bc8e91bf` — **`ATT-8735`** (`3 SP`, `tier-b`, Neon graphical `Rule 8.5`): Replaced radar map teardrop pins with dual-contrast zoom-scaled closing-speed car/truck icons (`< 14.5f` zoom cutoff, buffered viewport culling for 1,000+ passes, `O(1)` bounded 40-entry bitmap cache, Brown/Red/Amber/Green priority, antimeridian-safe bounds, per-pass delta large-vehicle fix).
4. `27436048` — **`ATT-8996`** (`2 SP`, `tier-a`, `severity-minor`, Neon): Introduced shared `@JvmStatic` `BleDisStringSanitizer`, stripped NUL bytes across all BLE GATT/GAP/Polar DIS read sites, self-healed `PolarOfflineCapabilityStore`, and shipped idempotent `Devices.db` v14→v15 sanitization migration with schema parity.
5. `54813693` — **`ATT-8731`** (`2 SP`, `tier-a`, Neon): Added V2-capable unbonded radar fallback detection (`RadarStateRepository` SSoT + synchronized `mBondReceiverLock` lifecycle), Android 12+/13+ permission-safe `RadarBluetoothBondingHelper` (`BOND_BONDING` short-circuit + `FLAG_ACTIVITY_NEW_TASK` guard), WCAG AAA inline hint card in `EditDeviceDialog`, owned-only `READY` snackbar (`RadarV2BondingSnackbarVisuals`), and 9-language parity.

---

## 2. Quantitative Sprint Scorecard (PA 4.1 & PA 4.2) & Special Cause RCA

*Source*: `docs/engineering/metrics/process_metrics.jsonl` & `docs/engineering/gates/sprint_2026-11-24_02_spät.md` (`16` gate records; `claude-sonnet-5-5-high` verified at `0%` weekly quota until `2026-11-28T08:17:14Z` → sanctioned truthful Gemini 4 Neon High Stage 6 fallback active per `Rule 3.3`).

| Metric | Sprint 478 Value | 8-Sprint Baseline ($\mu \pm 2\sigma$) | Status |
| :--- | :---: | :---: | :---: |
| **Ticket Throughput (Jira `Final Review (Human)` + `Final Review (Agent)`)** | `5` merged / `13.0 SP` (`6/8` incl. Stage-5-done `ATT-8723`) | $\mu = 10.12$ / `23.38 SP` (`[2.31, 17.94]` / `[6.58, 40.17]`) | In Control |
| **Gate 3 (Impl-Plan, `tier-a`) FTPR** | `25.0%` recorded (`1/4`) / **`50.0%` authentic (`2/4`)** | LCL `30.36%` ($\mu = 66.65\%$, $\sigma = 29.94\%$) | ⚠️ PA 4.2 Alert (RCA below) |
| **Stage 6 Cold Final Review FTPR** | `20.0%` (`1/5`: `ATT-8672` 1st-pass; 4 high-value Bounce 1 catches healed on Pass 2) | — | Active Defect Filter |
| **Bug Resolution Rate (`SUP.9`)** | **`100.0%`** (`2/2`: `ATT-9178` Major, `ATT-8996` Minor; `0` open `> 7d`) | — | Clean |
| **Static Analysis Baselines (`SWE.4`)** | Detekt `9,509 / 9,516` (`Δ 0`), Lint `216 / 216` (`0` Recycle) | Ceilings enforced | Clean |

### Mandatory PA 4.2 Special Cause Root-Cause Analysis (`OVERALL_FTPR: 25.0% < LCL 30.36%`)
1. **Metric Parser Artifact (`ATT-9689` / `ATT-8723`)**: `ATT-9689` passed independent Gate 3 on the **first pass** (`21:07:14`), but its first comment omitted the literal `GATE_VERDICT: PASS` prefix before the follow-up `21:07:58` record, causing `sprint_metrics.py` to count `ATT-9689` as `REVISION` (`1/4 = 25.0%` instead of the authentic **`2/4 = 50.0%`** — identical to the `ATT-9666` parser defect documented in Sprint 475).
2. **High `tier-a` Density + High-Value Adversarial Pre-Coding Catches**: All 4 Gate 3 evaluations were high-risk `tier-a` BLE/concurrency/DB tickets (`ATT-9178`, `ATT-8996`, `ATT-8731`, `ATT-8723`). The 2 authentic Gate 3 revisions (`ATT-9686` on `ATT-9178` and `ATT-9688` on `ATT-8731`) caught **8 critical pre-coding concurrency/state defects** (search-queue deadlock on `TRACKING -> READY`, never-connected `READY` disconnect loop, 4–8 Hz `processTargets()` bonding-flag resurrection, and remote-disconnect `unregisterSensors()` bypass), while Stage 6 Cold Reviews caught 4 real implementation defects before merge (mid-workout GATT-133 deadlock on `ATT-9178`, antimeridian `containsBounds()` bug on `ATT-8735`, `RadarV2BondingSnackbarEffect` dismissing unrelated low-battery/Polar snackbars on `ATT-8731`, and empty `--skip-tests` JUnit bundle on `ATT-8996`).
3. **Countermeasure Proposal (Rolled up to `ATT-8223`)**: Fix `sprint_metrics.py` Gate 3 FTPR parser so pre-verdict/prefix-less `PASS` comments are never misclassified as `REVISION`, and embed the `ATT-9178` / `ATT-8731` concurrency checklists (shared `SnackbarHostState` ownership, `mBondReceiverLock`/remote-disconnect paths, `SEARCHING`-safe state resets) into `stage3-impl-plan` review heuristics via a PO-approved backlog ticket.

---

## 3. Retrospective Insights & Tooling Proposals (Rule 2.7 Allowlist Compliant)

*No protected governance, rule, skill, or tool files were modified during this autonomous closeout (`Rule 2.7`). All proposals below are rolled up into rolling `Wochen Review & Retro` ([ATT-8223](https://rainerblind.atlassian.net/browse/ATT-8223)) for PO prioritization:*

1. **Proposal 1 — Host `~/.gradle/gradle.properties` `-Xmx2048m` OOM-GC-Thrash Killed 4× in One Schicht**:
   - **Measured Problem**: `KotlinCompileDaemon` entered `99.97–99.98%` Old Gen Full-GC death spirals (`38`–`1,044` Full GCs / `526 s`–`1,132 s` FGCT) **four times** during Sprint 478 (`18:23`, `19:30`, `21:26`, `21:53 CET`), costing ~45 minutes of wall-clock latency across both lanes before watchdog intervention killed the thrashing daemon.
   - **Action**: Raise host `~/.gradle/gradle.properties` `kotlin.daemon.jvmargs` from `-Xmx2048m` to `-Xmx3072m` (matching repo `gradle.properties` per `Rule 8.6`) or add automatic daemon recycle after `N` builds in `parallel_sprint_lock.py`.
2. **Proposal 2 — Prioritize Existing Process-Escape Bug `ATT-9681` (`TuningPreferencesDataStore` Livelock)**:
   - **Measured Problem**: `ATT-8672`'s `auto_gate.py` run hung for **24 minutes** (blocking `/tmp/att_build_merge.lock` and stalling Lane 2 `ATT-9178`) inside pre-existing Sprint-476 livelock `ATT-9681` (`TuningPreferencesDataStore$1$1.emit` → `EnumUtils.safeValueOf` → `android.util.Log.w`).
   - **Action**: Prioritize `ATT-9681` into `Vom PO Freigegeben` (`447`) so `auto_gate.py` can run unfiltered dependent tests on settings/tuning tickets without livelocking.
3. **Proposal 3 — Teach `tools/auto_gate.py --skip-tests --write-bundle` to Collect Fresh JUnit XMLs (`--reuse-junit-xmls`)**:
   - **Measured Problem**: Because `auto_gate.py --skip-tests --write-bundle` left `android_xml_files = []`, `ATT-8996` suffered an avoidable Stage 6 Bounce 1/5 until authors manually populated `junit/` via Python API calls.
   - **Action**: Add `--reuse-junit-xmls` (or auto-collect fresh XMLs matching `app/build/test-results/testDebugUnitTest/TEST-*.xml` newer than commit timestamp when `--skip-tests` is passed) so bundles remain 100% authentic without re-triggering `ATT-9681` or `ATT-9639`.
4. **Proposal 4 — Prioritize `ATT-9595` (Update `qa-audit` for `aaa-v2` Tiers)**:
   - **Measured Problem**: Legacy pre-`aaa-v2` Dim 2/3 checks generated 2 more false-positive CA tickets (`ATT-9690`, `ATT-9691`), continuing an 8-sprint false-positive streak (see Section 4 PO Escalation).

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`, `REQ-PRO-092.6`)

*Audit Source*: `docs/engineering/qa_audits/sprint_2026-11-24_02_spät.md` (`--as agent2`, seed `94658`, sampled `2/5` = `40% >= 10%`: `ATT-8735`, `ATT-8731`).

* **Overall Script Verdict**: `NON-COMPLIANT` (`0/2`), solely due to the recurring legacy pre-`aaa-v2` Dimension 2/3 script mismatch:
  * **`ATT-8735` (`tier-b`)**: Passed `5/6` checks (`Deliverables`, `Gate Sequence`, `Living Docs Parity`, `Commit Hygiene`, `PO Approval`); flagged Dim 3 (`4-Augen Role Separation`) because `aaa-v2` `tier-b` omits agent2 prose Gate 1–5 subtasks and relies on `auto_gate.py` + independent `agent2` Stage 6 Cold Review → CA Ticket [ATT-9690](https://atrainingtracker.atlassian.net/browse/ATT-9690).
  * **`ATT-8731` (`tier-a`)**: Passed `5/6` checks (`Deliverables`, `4-Augen Role Separation`, `Living Docs Parity`, `Commit Hygiene`, `PO Approval`); flagged Dim 2 (`Stage Subtask Progression`) because `aaa-v2` `tier-a` intentionally runs only Gate 3 (`ATT-9688`) + `auto_gate.py` + Stage 6 instead of 5 legacy prose subtasks → CA Ticket [ATT-9691](https://atrainingtracker.atlassian.net/browse/ATT-9691).

> [!WARNING]
> **⚠️ PO Escalation: Persistent QA Non-Conformance (`REQ-PRO-091.4`, SUP.1 BP6)**
> The same legacy pre-`aaa-v2` `qa-audit` Dimension 2/3 mismatch has recurred across **8 consecutive Schichten (> 2 sprints)**: Sprint 470 (`ATT-9595`), Sprint 471 (`ATT-9641`, `ATT-9642`), Sprint 472 (`ATT-9647`), Sprint 473 (`ATT-9655`, `ATT-9656`), Sprint 474 (`ATT-9662`, `ATT-9663`), Sprint 475 (`ATT-9667`, `ATT-9668`), Sprint 476 (`ATT-9675`, `ATT-9676`), Sprint 477 (`ATT-9683`, `ATT-9684`), and Sprint 478 (`ATT-9690`, `ATT-9691`).
> **Root Cause**: `tools/jira_util.py`'s `qa_audit()` still evaluates legacy 5-subtask rules against `aaa-v2` (`ATT-9543`) tickets, creating duplicate false-positive CA tickets every Schicht.
> **Escalation Action Required**: Prioritize [ATT-9595](https://atrainingtracker.atlassian.net/browse/ATT-9595) into `Vom PO Freigegeben` (`447`) and batch-resolve the duplicate false-positive CA tickets (`ATT-9641`..`ATT-9691`) once `ATT-9595` lands.

### Quality Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)
Spot-checked all 4 Gate 3 subtasks (`ATT-9686`, `ATT-9687`, `ATT-9688`, `ATT-9689`) and all 5 Stage 6 Cold Reviews against `git diff` and `--verify-warm` logs:
- **Authentic Technical Discrimination**: `0` rubber-stamped false passes observed. Gate 3 revisions (`ATT-9686`, `ATT-9688`) cited exact file/line races verified in production code; Stage 6 bounces (`ATT-9178`, `ATT-8735`, `ATT-8996`, `ATT-8731`) caught genuine concurrency, compiler-warning, antimeridian math, shared-snackbar, and evidence-bundle defects, all independently re-verified via `--verify-warm` and live `emulator-5554` on Pass 2.

---

## 5. Lean Tier-2 Integration Gate & Device Status

* **Target Ref**: `sprint/2026-11-24_02_Spät` @ `54813693` (2-Batch `--verify-warm --max-workers=2` with `JAVA_TOOL_OPTIONS="-XX:ReservedCodeCacheSize=256m"`, zero CodeCache failures)
* **Build & AndroidTest Compilation**: `:app:assembleDebug` **PASS**, `:app:compileDebugAndroidTestKotlin` **PASS**, `ANT-Android-SDKs` gitlink `160000` verified clean.
* **Unit / Contract / Integration / Localization Tests**: **`2,095 / 2,098` PASS across `280` XML suites** (`0` errors, `0` skips; `RadarBrakeLightConcurrencyStressTest` `7/7` confirmed 100% PASS in isolation in `19 s`; sole `3` failures are pre-existing tracked **`ATT-9639`** `MeasuredNfrBenchmarkTest#testNfr014_cockpitTelemetryThrottling` + `NonFunctionalRequirementsContractTest`; `TuningPreferencesDataStoreTest` excluded per pre-existing livelock **`ATT-9681`**). Archived via `export-test-evidence --no-kover --allow-failure` (`docs/engineering/test_evidence/sprint_2026-11-24_02_Spät.{md,json}`).
* **Governance, Python & Safety Linters**: `verify_requirement_governance.py` **22/22 PASS** (`21/21` SYSREQs, `2,666` files, `0` orphans, `0` conflict markers); `check_secrets_hygiene.py` PASS (`13,344` files); `check_static_analysis_baselines.py` PASS (`detekt 9,509 <= 9,516`, `lint 216 <= 216`, `0` Recycle); `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` **3/3 PASS**; Python `tools/test_*.py` **`617/618` PASS** (sole failure pre-existing Sprint-476 `ATT-9607` `REQ-DAT-049` reverse-trace suffix).
* **Physical Device Deploy (`Rule 7.6` / `REQ-TST-004`)**: Google `Pixel 10` (`66020DLCR002FL`) not attached via ADB (`emulator-5554` only); best-effort `-PstampGitHash=true` deploy deferred cleanly without blocking rollover.
* **Ceremony 2 Human Review (`REQ-PRO-062`)**: Truthfully recorded as `SKIPPED` (`docs/engineering/reviews/sprint_2026-11-24_02_spät.md`) pending async PO acceptance in `Bereit für PO Abnahme` (ID `453`) / weekly PO decision session (`ATT-9545`).
