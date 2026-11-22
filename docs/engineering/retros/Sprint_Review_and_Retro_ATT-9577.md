# Sprint Review & Retrospective: `2026-11-21_03_Nacht` (Sprint `470` — `ATT-9577`)

**Document ID**: `DOC-ENG-RETRO-S470`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Target Release**: `V5.0.0`  
**Sprint Window**: `2026-11-21 22:00 – 2026-11-22 05:00 CET` (first full `aaa-v2` Schicht, `aspice_governance.md` v1.0.17 → v1.0.19, strictly 2 parallel lanes per PO directive)  
**Sprint Integration Branch**: `sprint/2026-11-21_03_Nacht` (code tip `74c95820`, merged `--no-ff` into `develop`, tagged `sprint/2026-11-21_03_Nacht`)  

---

## 1. Delivered Scope & Velocity Summary (`21.0 SP` — `10 / 11` Active Work Tickets)

Ten completed work tickets (`21.0 SP`) passed all required `aaa-v2` tier gates and independent Stage 6 Cold Final Reviews (`Rule 3.3`, including Second-Vendor Anthropic Claude `claude-sonnet-5-5-high` for `tier-a`), were integrated atomically onto `sprint/2026-11-21_03_Nacht` (`Rule 8.2`), and were archived post-close to **`Bereit für PO Abnahme` (`453`)** with label `sprint-2026-11-21_03_Nacht` (`Final Review (Human)`, assigned to `human`):

| Ticket | SP | Tier | Epic | Commit | Stage 6 Reviewer / Bounces | Delivered Outcome |
| :--- | :---: | :---: | :--- | :---: | :--- | :--- |
| **`ATT-9569`** | 3 | `tier-c` | `ATT-232` | `0d236950` | Cold Flash -> Neon (1 bounce) | Weekly Full Test Suite & Static Analysis `2026-KW47`: `7,567` tests, Kover `86.38%` line / `64.96%` branch, `:app:lintDebug` `0` errors, `weekly_2026_KW47.{md,json}`, 7 localized fixes (incl. `BottomSheetVisualContractTest` & `PolarRawTelemetryPruner` Objenesis MockK heal), filed `ATT-9578`/`ATT-9579`/`ATT-9580`, rollup posted to `ATT-8223`. |
| **`ATT-9558`** | 1 | `tier-c` | `ATT-3539` | `ec3a24ca` | Cold Neon (0 bounces) | Replaced thick top-only accent bar on PMC Chart KPI Cards with uniform `1.5.dp` colored borders (`Rule 8.5` Light/Dark PNG & live emulator verified). |
| **`ATT-9567`** | 2 | `tier-c` | `ATT-232` | `90ee9265` | Cold Flash (0 bounces) | Added Weekly Full Test Suite as 4th pillar in `ensure-weekly-reviews` with 6-day recency threshold (`REQ-PRO-136`/`TST-PRO-136` `[Verified]`, `aspice_governance.md` v1.0.19). |
| **`ATT-6037`** | 5 | `tier-b` | `ATT-6033` | `7522ab4d` | Cold Neon (1 bounce) | `ElevationProfile` Canvas hoisting/coalescing, `TrackingViewModel`/`SensorGridScreen`/`MapScreenWithTrack` `mapState` decoupling + `MapContentScopeImpl` Strong Skipping `contentRevision` heal, shared `AppHttpClient` (`REQ-UI-2793`/`TST-UI-2793`). |
| **`ATT-9510`** | 1 | `tier-c` | `ATT-2466` | `b037046e` | Cold Flash (0 bounces) | Switched `run_multi_os_emulator_sweep.py` `uiautomator dump` target to `/data/local/tmp/att_sweep_dump.xml` for Android 16 compatibility. |
| **`ATT-9363`** | 1 | `tier-c` | `ATT-2466` | `fed9b361` | Cold Flash (0 bounces) | Filtered `run_multi_os_emulator_sweep.py` logcat strictly to app process PIDs to eliminate unrelated system log noise. |
| **`ATT-9174`** | 2 | `tier-b` | `ATT-5910` | `ac4c954a` | Cold Neon (0 bounces) | Surfaced Polar pending retry sync banner in `EditWorkoutScreen` immediately after stopping a workout (`REQ-SET-127`/`TST-SET-129`, 9-locale parity & live emulator verified). |
| **`ATT-9553`** | 2 | `tier-b` | `ATT-3438` | `d186addd` | Cold Neon (0 bounces) | Added compact peek header for selected Climbs in `MapScreenWithTrack` via `BottomSheetScaffold` (`REQ-UI-2794`/`TST-UI-2794`, Light/Dark PNG & live emulator verified). |
| **`ATT-8949`** | 3 | `tier-a` | `ATT-5910` | `f4da6b32` | **Second-Vendor Claude (`claude-sonnet-5-5-high`)** (1 bounce) | Fixed Polar BLE detection non-Polar name-heuristic bleed, renamed existing H10 lost options, and empty-name Verity Sense bug (`REQ-CON-049`/`TST-CON-049`, `DeviceManagementUiTest` fix `d7d42580`, Claude Pass 2 PASS). |
| **`ATT-9364`** | 1 | `tier-c` | `ATT-3438` | `74c95820` | Cold Neon (0 bounces) | Fixed zero-bounds `(0,0,0,0)` crush on `'No routes nearby'` subtitle in `ControlTrackingScreen` via `BoxWithConstraints` + `verticalScroll` (`Rule 8.5` verified). |

* **Interactive-PO Tooling Fix (`f1a3b724`)**: Authorized interactively during Schicht execution — added exclusive POSIX `/tmp/att_emulator.lock` session serialization (`exec --ensure -- ...`) with lock-order deadlock guard rejecting `ATT_SPRINT_LOCK_HELD=1` in `tools/agent_emulator.py` (`26/26` tests), updated `aspice_governance.md` v1.0.18 + `stage5-verification` + `stage6-final-review`, and filed `ATT-9581` (`2 SP`, `tier-c`) for warm Stage 6 worktree reuse.
* **Active Carryover WIP (`5.0 SP` — 1 ticket)**: **`ATT-6036`** (`5 SP`, `tier-a`, Epic `ATT-6033`, status `Test`, Gate 4 Bounce 1 `ATT-9592` PASS after Second-Vendor Claude Stage 6 caught `SqliteSchemaUtils.addColumnIfNotExists` swallowing `Throwable` on `RISK-006`) preserved cleanly on local branch **`feature/ATT-6036` @ `bfd66b6a`** with full handover comment for `2026-11-22_01_Früh`.
* **Untouched Tickets Returned to `447`**: `ATT-6035` (`3 SP`), `ATT-9508` (`2 SP`), `ATT-9355` (`1 SP`) — returned cleanly via `return-unstarted 470` preserving top-of-queue rank order.

---

## 2. Verification, CI Gate & Physical Hardware Evidence

* **Scoped Lean Tier-2 Gradle Gate (`Rule 7.5`, 3-batch execution on `sprint/2026-11-21_03_Nacht` @ `74c95820`)**:
  * `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin`: **PASS**.
  * Batched Lean Tier-2 across all sprint-touched packages (Batch A1 UI/Map/Tracking/Elevation/HTTP, Batch A2 Polar/BTLE/Devices/Settings/Periods) + Batch B (`build.*`, `integration.*`, `localization.*`): **`1,707 / 1,707` PASS across `294` XML suites** (`0` failures, `0` errors, `0` skipped — exported to `docs/engineering/test_evidence/sprint_2026-11-21_03_Nacht.md` & `.json` with `--no-kover`).
  * Note: Splitting Tier-2 into 3 smaller batches resolved an initial single-fork `--max-workers=1` JVM `CodeCache is full (49152Kb)` stall observed when running all suites in one Kover-instrumented worker.
* **Governance, Linters & Python Suite**:
  * `verify_requirement_governance.py`: **21/21 PASS** (`1,284 / 1,284` backed `[Verified]` TSTs, `0` legacy unbacked TSTs, `100%` parity across `28` shards `<= 4,000` lines, `64` governed docs).
  * Safety & baseline checks: **5/5 PASS** (`lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py`, `check_static_analysis_baselines.py` — Detekt `9509 <= 9516`, Lint `216 <= 216`, `0` Recycle issues).
  * Python tooling tests (`tools/test_*.py`): **`562 / 562` PASS** (`12/12` skills PASS; `ANT-Android-SDKs` `160000` gitlink verified).
* **Physical `Pixel 10` (`66020DLCR002FL`) Deployment & `ATT-9352` Field Heap Hygiene (`Rule 7.6`)**:
  * Read-only `ATT-9352` heap watchdog check on attached `Pixel 10` confirmed **`0` HPROF dumps** (`files/debug_hprof` absent; `5` historical `.heap.csv` files from `2026-11-21`, latest `17:29` previously reviewed @ `17.3%` peak Java heap, no new overnight workouts).
  * Deployed commit-stamped debug APK (`-PstampGitHash=true`) onto physical `Pixel 10` (`66020DLCR002FL`).
* **Quantitative Process Metrics (`PA 4.1` & `PA 4.2`, `process_version=aaa-v2` baseline reset)**:
  * **Planned / Completed Velocity**: `26.0 SP` active (`12` tickets incl. retro) / **`21.0 SP` completed** (`10/11` work tickets, `83.3%` overall completion rate).
  * **Gate 1–5 FTPR (`tier-a` subtasks)**: **`92.3%`** (`13` subtasks evaluated: Gate 1 `100%`, Gate 2 `100%`, Gate 3 `100%`, Gate 4 `75.0%` [1 revision on `ATT-6036`], Gate 5 `100%`).
  * **Stage 6 Cold Review FTPR**: **`63.6%`** (`11` tickets evaluated, `7` first-pass PASS, `4` high-value defect bounces: `ATT-9569`, `ATT-6037`, `ATT-6036`, `ATT-8949`).
  * **End-to-End Gate 1–6 FTPR**: **`79.2%`** (`28` total gate records in `docs/engineering/gates/sprint_2026-11-21_03_nacht.md`).
  * **Bug Resolution Rate**: **`100.0%`** (`5 / 5` bugs fixed awaiting human acceptance: `1` Major, `4` Minor, `0` open).
  * **Ceremony 2 Review Record**: Recorded truthfully as `SKIPPED` (`docs/engineering/reviews/sprint_2026-11-21_03_nacht.md`, async human PO review in `453`).
  * **Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)**: Spot-checked Stage 4/5/6 verdicts across `ATT-8949`, `ATT-6036`, `ATT-6037`, `ATT-9569`, `ATT-9553`, `ATT-9174`, `ATT-9558`, `ATT-9364`. High adversarial discrimination confirmed across all three tiers (`4` genuine defects caught and remediated prior to merge).

---

## 3. Retrospective Insights & Process Improvement Proposals (`Rule 2.7`)

### Empirical Second-Vendor Anthropic Claude (`claude-sonnet-5-5-high`) Evaluation (`ATT-9546`)
This Schicht was the first operational deployment of `aaa-v2` with **Second-Vendor Anthropic Claude (`claude-sonnet-5-5-high`)** executing Stage 6 Cold Reviews on `tier-a` tickets:
* **Catch Rate**: **`2 / 2` (`100%`) first-pass high-value defect catches** on `tier-a` tickets (`ATT-6036`, `ATT-8949`), compared with `2 / 9` (`22.2%`) first-pass bounces on Gemini-reviewed `tier-b`/`tier-c` tickets (`ATT-6037`, `ATT-9569`).
* **Defect Depth**:
  1. On **`ATT-6036`**, Claude independently caught `SqliteSchemaUtils.addColumnIfNotExists` silently swallowing `Throwable` instead of failing fast on non-duplicate schema migration errors (`RISK-006`), which had slipped past internal Gemini Gate 4 (`ATT-9588`) and Gate 5 (`ATT-9589`).
  2. On **`ATT-8949`**, Claude caught 4 concrete defects missed by internal Gemini Gate 4 (`ATT-9590`): legacy `offline_rec_`-only Verity Sense rename bleed, unguarded `loadAllDevices` backfill race, a dead re-evaluation block, and `--skip-tests` omitting JUnit XMLs + a stray smoke dialog — after healing (`1f134abd` + `DeviceManagementUiTest` fix `d7d42580`), Claude cleanly approved Pass 2 (`f4da6b32`).
* **Verdict**: Cross-vendor Stage 6 review on `tier-a` is demonstrably high-signal and directly mitigates `RISK-002` and `RISK-009`.

### What Went Well
1. **First Full `aaa-v2` Schicht Throughput (`21.0 SP` across 2 lanes)**: Tiered ceremony (`tier-c`/`tier-b`/`tier-a`) doubled throughput relative to `02_Spät` (`10.0 SP`) under the same 2-lane cap while maintaining strict adversarial Stage 6 quality gates.
2. **Immediate Interactive-PO Emulator Serialization (`f1a3b724`)**: Exclusive `/tmp/att_emulator.lock` eliminated dual-lane `emulator-5554` contention cleanly during live parallel Stage 5/6 UI verification.
3. **Weekly Full Suite (`ATT-9569`) + 4th Weekly Pillar (`ATT-9567`)**: Completed `2026-KW47` full-repo Kover verification (`7,567` tests, `86.38%` line / `64.96%` branch) and institutionalized the 6-day weekly full-suite cadence in `ensure-weekly-reviews`.

### Concrete Tooling & Governance Proposals (For PO Review in `ATT-8223`)
1. **Proposal 1 — Fix `qa-audit` Dimension 3 (`4-Augen Role Separation`) for `aaa-v2` `tier-b`/`tier-c` Tickets (`ATT-9595`)**: Under `aaa-v2`, `tier-b`/`tier-c` tickets legitimately bypass stage subtasks and enforce 4-Augen separation via independent `agent2` Stage 6 parent comments (`[FINAL REVIEW (AGENT) — PASS]`). `qa-audit` currently inspects only subtasks and falsely flagged `ATT-9558` (`tier-c`) as `NON-COMPLIANT` (generating CA `ATT-9595`). **Proposal**: Update `qa-audit` Dimension 3 to recognize `agent2` parent Stage 6 verdicts alongside subtask reviewers for `tier-b`/`tier-c`.
2. **Proposal 2 — Prevent Single-Fork JVM `CodeCache` Exhaustion (`49152Kb`) in Lean Tier-2 & `auto_gate.py`**: Running `200+` test suites in one `--max-workers=1` Kover-instrumented Gradle fork fills the 49 MB C1 CodeCache (`-XX:TieredStopAtLevel=1`) and stalls indefinitely. **Proposal**: Raise `-XX:ReservedCodeCacheSize=256m` (or disable Kover agent during `--no-kover` Tier-2 / `auto_gate.py` runs) and/or batch Lean Tier-2 into scoped groups.
3. **Proposal 3 — Warm Stage 6 Worktree Reuse (`ATT-9581`) & `auto_gate.py` Baseline-Failure Isolation**: Reusing a warm Stage 6 worktree (`ATT-9581`) cuts Stage 6 Gradle startup latency, and allowing `auto_gate.py` to distinguish known tracked baseline failures (`ATT-9578`/`ATT-9579`) from ticket-introduced regressions prevents false blocks on unrelated tickets while preserving full XML evidence (avoiding `--skip-tests` blind spots seen on `ATT-8949`).

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`)

* **Sprint `470` QA Process-Compliance Audit (`docs/engineering/qa_audits/sprint_2026-11-21_03_nacht.md`)**:
  * **Auditor**: `agent2` (independent of Coordinator/Implementer) | **Random Seed**: `66678` | **Sample**: `2 / 10` tickets (`ATT-8949`, `ATT-9558`, `>= 10%` threshold).
  * **Verdict**: **`1 / 2 COMPLIANT`** (`ATT-8949` `tier-a`: **6/6 PASS — COMPLIANT**; `ATT-9558` `tier-c`: `5/6 PASS`, flagged `FAIL` on Dimension 3 `4-Augen Role Separation` due to the `aaa-v2` subtask-only audit script gap — verified manually that `ATT-9558` passed independent `agent2` Cold Neon Stage 6 Review at `2026-11-22T00:36:08+0100`).
* **Open QA Corrective Action Tickets**: **1** — **[ATT-9595](https://atrainingtracker.atlassian.net/browse/ATT-9595)** (auto-generated for `ATT-9558` Dimension 3 `aaa-v2` tooling gap; non-recurring single-sprint tooling finding -> tracked for PO prioritization in `ATT-8223`).
* **Risk Register Review (`MAN.5.BP6` / `REQ-STP-010.2`)**: Updated `docs/management/risk_register.md` to `v1.2.24` (`Status: Reviewed`, `Last Review Date: 2026-11-22`); `ATT-8949` substantially mitigates `RISK-004` (Polar BLE detection & dual-sensor hygiene), Second-Vendor Claude (`2/2` catch rate on `ATT-6036`/`ATT-8949`) validates `RISK-002`/`RISK-006`/`RISK-009` defenses, and `ATT-9569`/`ATT-9567` harden weekly full-suite verification.
