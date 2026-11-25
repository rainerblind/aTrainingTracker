# Sprint Review & Retrospective: Schicht `2026-11-25_01_Früh` (Sprint 480)

**Document ID**: `DOC-RET-480`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Ticket**: [ATT-9699](https://rainerblind.atlassian.net/browse/ATT-9699) | **Sprint Window**: `2026-11-25 05:00 – 15:00 CET` | **Process Version**: `aaa-v2` (`v1.0.22`) | **Target Release**: `V5.0.0`

---

## 1. Executive Summary & Delivery Velocity

Executed under the PO-directed **3 parallel lanes** policy across disjoint Epics (`ATT-5910` Equipment/Sensors → `ATT-9625` Unified Athlete Thresholds; `ATT-166` Sensor Filtering & Fusion → `ATT-8171` Offline Maps; `ATT-597` Strava Support), Schicht `2026-11-25_01_Früh` drained Sprint 479's Stage-6-ready WIP carryover (`ATT-8442`) immediately and delivered **10 / 10 merged Stage-6-verified work tickets (`27.0 SP`)** — completing Epics `ATT-5910` (2/2), `ATT-597` (2/2), and `ATT-166` (3/3) — into `Final Review (Human)` (`sprint-2026-11-25_01_Früh` → `Bereit für PO Abnahme` ID `453`) with **0 net regressions**, preserved **2 hot WIP carryovers (`8.0 SP`)** cleanly on `feature/ATT-9626` (`74853237`) and `feature/ATT-8174` (`246c0f2d`) for `2026-11-25_02_Spät`, and returned **1 untouched ticket (`ATT-8173`, `2.0 SP`)** cleanly to the top of `Vom PO Freigegeben` (ID `447`).

| Category | Tickets | Story Points | Keys & State |
| :--- | :---: | :---: | :--- |
| **Initially Committed + Refilled (Phase 1–2)** | 13 | 37.0 SP* | `1` WIP carryover (`ATT-8442`) + `12` disjoint-Epic tickets pulled from `447` (`*` `35.0 SP` active after returning untouched `ATT-8173` `2.0 SP` to `447`) |
| **Completed & Merged (`Final Review (Human)` → `453`)** | **10** | **27.0 SP** | `ATT-8442` (`1 SP` `tier-c`), `ATT-8101` (`1 SP` `tier-b`), `ATT-8100` (`3 SP` `tier-b`), `ATT-9694` (`3 SP` `tier-a`), `ATT-8082` (`2 SP` `tier-a`), `ATT-9628` (`3 SP` `tier-a`), `ATT-9693` (`3 SP` `tier-a`), `ATT-8175` (`5 SP` `tier-a`), `ATT-9627` (`5 SP` `tier-b`), `ATT-8111` (`1 SP` `tier-c`) |
| **Hot WIP Carryovers to `2026-11-25_02_Spät`** | **2** | **8.0 SP** | `ATT-9626` (`3 SP` `tier-b`, Epic `ATT-9625`, status `Implementation`, `feature/ATT-9626` @ `74853237`, Stage 6 Bounce 1/5 findings recorded); `ATT-8174` (`5 SP` `tier-b`, Epic `ATT-8171`, status `Implementation`, `feature/ATT-8174` @ `246c0f2d`, Bounce 1/5 healing committed awaiting final verification + Stage 6 Pass 2) |
| **Returned Untouched to `Vom PO Freigegeben` (`447`)** | **1** | **2.0 SP** | `ATT-8173` (`2 SP` `tier-b`, Epic `ATT-8171`, returned cleanly via `return-unstarted 480`) |

### Delivered Commits on `sprint/2026-11-25_01_Früh` (`2e4a8eb5`)
1. `3cffc021` — **`ATT-8442`** (`1 SP`, `tier-c`, `severity-major`, WIP carryover, Epic `ATT-5910`): Gated Polar H10 BLE-only switches in `EditDeviceDialog` and verified layout parity across locales (Stage 6 Pass 1 `Neon High`).
2. `c53eb348` — **`ATT-8101`** (`1 SP`, `tier-b`, Epic `ATT-5910` — Epic 2/2 complete): Persisted `DeviceSortOrder` across app restarts via `SettingsDataStore` (Stage 6 Pass 1 `Flash`; isolated pre-existing process-escape Bug `ATT-9701` under `Rule 1.3`).
3. `56e35bfe` — **`ATT-8100`** (`3 SP`, `tier-b`, Epic `ATT-597`): Implemented Strava API v3 Activity Photo Download + Settings toggle with HTTPS-only, bounded `callTimeout`, path-traversal guards, `Content-Type` validation, and KDoc parity (Stage 6 Pass 2 `Flash` after Bounce 1/5).
4. `4f9d06c4` — **`ATT-9694`** (`3 SP`, `tier-a`, `severity-major`, Gate 3 `ATT-9700` PASS, Epic `ATT-166`): Gated `BIKE` classification and multisport establishment at walking speeds + bounded `SportTypes` speed priors; real Pixel 10 replay confirmed `0%` BIKE / `0` transitions across all 3 walks and `55%` BIKE / `0` walk strides on cycling errand (Stage 6 Pass 2 `Neon High` fallback after Bounce 1/5 caught fresh-install `minBike=3.5` runner regression).
5. `9d44d97b` — **`ATT-8082`** (`2 SP`, `tier-a`, Gate 3 `ATT-9703` PASS, Epic `ATT-597` — Epic 2/2 complete): Migrated Strava upload pipeline from TCX to native binary FIT while preserving legacy `.tcx` queue fallback (Stage 6 Pass 1 `Neon High` fallback).
6. `7042f3e9` — **`ATT-9628`** (`3 SP`, `tier-a`, Gate 3 `ATT-9702` PASS, Epic `ATT-9625`): Delivered unified sport-specific physiological thresholds storage (`AthleteMetrics.db` v2), history tracking, hrTSS/HRV synchronization, and `.attbackup` round-trip parity (Stage 6 Pass 2 `Neon High` fallback after Bounce 1/5 caught upgrade seeding race, `resolveWorkoutThresholdsInMemory()` precedence, and `TST-SET-135.3` coverage).
7. `3930da5a` — **`ATT-9693`** (`3 SP`, `tier-a`, `severity-major`, Gate 3 `ATT-9704` PASS, Epic `ATT-166`): Fixed `KinematicAltitudeSlopeEkf` DEM negative-slope tug-of-war (`k2 = -k0`, exact 3×3 Joseph SPD) and walking-speed ERTS slope spikes, reducing Pixel 10 flat-walk ERTS p95 from `35.0%` to `1.9%–4.9%` (Stage 6 Pass 2 `Neon High` fallback after Bounce 1/5 caught Ref-before-Baro / missing-tick-0 QNH Huber saturation).
8. `f7f6ef97` — **`ATT-8175`** (`5 SP`, `tier-a`, Gate 3 `ATT-9705` PASS, Epic `ATT-8171`): Integrated Mapsforge `0.25.0` vector `TileProvider` and orthogonal `OFFLINE_OSM` mode into `ATrainingTrackerMap` with live Light/Dark Monaco `.map` rendering verification (Stage 6 Pass 1 `Neon High` fallback; `Rule 1.3` `TrainingApplication` 276-suite fan-out waiver documented).
9. `241b7c2d` — **`ATT-9627`** (`5 SP`, `tier-b`, Epic `ATT-9625`): Delivered Unified Expert-Gated Threshold Editor in Zones Tabs & Fitness Hub Option 1 separation, including unified bounds, debounce flush/`onCleared`, `THRESHOLD_ANCHORED` `lthr - lt1 >= 2` invariant, and `AppModalBottomSheet` alignment (Stage 6 Pass 2 `Neon High` after Bounce 1/5).
10. `2e4a8eb5` — **`ATT-8111`** (`1 SP`, `tier-c`, Epic `ATT-166` — Epic 3/3 complete): Removed `'ATT '` prefix from 9-locale Fused sensor titles and corrected `'a Training Tracker'`/`'Google'` manufacturers via `Devices.db` v15→v16 migration with empty-list SQL guard and living-doc/test parity (Stage 6 Pass 2 `Neon High` after Bounce 1/5).

---

## 2. Quantitative Sprint Scorecard (PA 4.1 & PA 4.2)

*Source*: `docs/engineering/metrics/process_metrics.jsonl` & `docs/engineering/gates/sprint_2026-11-25_01_früh.md` (`23` gate records; `claude-sonnet-5-5-high` verified at `0%` weekly quota until `2026-11-28T08:17:14Z` → sanctioned truthful Gemini 4 Neon High Stage 6 fallback active per `Rule 3.3`).

| Metric | Sprint 480 Value | 10-Sprint Baseline ($\mu \pm 2\sigma$) | Status |
| :--- | :---: | :---: | :--- |
| **Ticket Throughput (`Final Review (Human)`)** | **`10 / 13`** (`76.9%`) / **`27.0 SP`** (`planned=35.0 SP`) | `[2.39, 17.21]` tickets / `[6.23, 37.97]` SP | **In Statistical Control** |
| **Gate 3 (Impl-Plan, `tier-a`) FTPR** | **`100.0%`** (`5 / 5`: `ATT-9700`, `ATT-9702`, `ATT-9703`, `ATT-9704`, `ATT-9705`) | `[23.03, 100.0]` (`OVERALL_FTPR`) | **In Statistical Control** |
| **Stage 6 Cold Final Review FTPR** | **`33.3%`** (`4 / 12` 1st-pass PASS; `8` adversarial bounces caught real defects pre-merge: 6 healed & merged + 2 active WIP `ATT-9626`/`ATT-8174`) | — | **Strong Adversarial Scrutiny** |
| **End-to-End Gate FTPR (Gates 1–6)** | **`52.9%`** (`9 / 17` combined) | — | **In Control** |
| **Bug Resolution Rate (`SUP.9`)** | **`100.0%`** (`3 / 3` Major bugs resolved awaiting human: `ATT-8442`, `ATT-9694`, `ATT-9693`; `0` open, `0` `>7d`) | — | **Clean** |
| **Static Analysis Baselines (`SWE.4`)** | Detekt `9,509 / 9,516` (`Δ 0`), Lint `216 / 216` (`Δ 0`, `0` Recycle) | Ceilings enforced | **Clean** |

---

## 3. Retrospective Insights & Tooling Proposals (Rule 2.7 Allowlist Compliant)

*No protected governance, rule, skill, or tool files were modified during this autonomous closeout (`Rule 2.7`). All proposals below are rolled up into rolling `Wochen Review & Retro` ([ATT-8223](https://rainerblind.atlassian.net/browse/ATT-8223)) for PO prioritization:*

1. **Proposal 1 — Prioritize Newly Filed Process-Escape Bug `ATT-9701` (`DeviceListViewModelPairingTest` NPE & Coroutine Fork Leak)**:
   - **Measured Problem**: During `ATT-8101`, `ATT-9628`, `ATT-9626`, and `ATT-8111`, pre-existing `DeviceListViewModelPairingTest` failed with `DataStoreFile.android.kt:35` NPE (relaxed `mockApplication` missing `filesDir`) and leaked an uncaught `Dispatchers.Main`/`Default` coroutine into the shared JVM Gradle test fork, forcing `Rule 1.3` isolation runs across 4 separate tickets.
   - **Action**: Prioritize [ATT-9701](https://rainerblind.atlassian.net/browse/ATT-9701) (filed under `ATT-5910`) into `Vom PO Freigegeben` (`447`) so relaxed `Application` mocks stub `filesDir` and close the test scope cleanly.
2. **Proposal 2 — Prioritize Existing Process-Escape Bugs `ATT-9681` (`TuningPreferencesDataStoreRoundTripTest` Livelock) & `ATT-9639` (`MeasuredNfrBenchmarkTest` `REQ-NFR-014`)**:
   - **Measured Problem**: `ATT-9681` still requires explicit exclusion from every Ceremony 2 Lean Tier-2 run to prevent executor livelock, and `ATT-9639` accounted for `100%` of the `3` failures in Sprint 480's 2-batch Lean Tier-2 (`1,914 / 1,917` PASS across `298` suites).
   - **Action**: Prioritize `ATT-9681` and `ATT-9639` into `Vom PO Freigegeben` (`447`) to restore 100% zero-exclusion, zero-failure Lean Tier-2 green runs.
3. **Proposal 3 — Prioritize `ATT-9595` (`qa-audit` `aaa-v2` Alignment) & Batch-Resolve Duplicate False-Positive CAs**:
   - **Measured Problem**: Legacy pre-`aaa-v2` Dimension 2/3 heuristics in `tools/jira_util.py`'s `qa_audit()` generated their **10th consecutive Schicht** of false-positive CA tickets (`ATT-9706`, `ATT-9707`) despite `ATT-9693` and `ATT-9627` passing `5/6` checks and rigorous Stage 6 Cold Reviews.
   - **Action**: Prioritize [ATT-9595](https://atrainingtracker.atlassian.net/browse/ATT-9595) into `447` and batch-close the duplicate CA tickets (`ATT-9641`..`ATT-9707`) once landed.
4. **Proposal 4 — Reattach Worktree HEAD in `parallel_sprint_lock.py --verify-warm` & Bake `-Pkotlin.daemon.jvmargs=-Xmx3072m`**:
   - **Measured Problem**: `parallel_sprint_lock.py --verify-warm --ref sprint/...` left the temporary worktree in detached `HEAD` state (`HEAD (kein Branch)`), requiring manual `git checkout sprint/2026-11-25_01_Früh` before committing Ceremony 2 artifacts; meanwhile `-Pkotlin.daemon.jvmargs=-Xmx3072m` + `JAVA_TOOL_OPTIONS="-XX:ReservedCodeCacheSize=256m"` again produced **zero** GC thrashing or CodeCache warnings across 10 hours of 3-lane execution and 2-batch Tier-2.
   - **Action**: Preserve branch attachment (or use a separate verify worktree) in `tools/parallel_sprint_lock.py` and default `-Pkotlin.daemon.jvmargs=-Xmx3072m` + `-XX:ReservedCodeCacheSize=256m` across host/tooling config.

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`, `REQ-PRO-092.6`)

*Audit Source*: `docs/engineering/qa_audits/sprint_2026-11-25_01_früh.md` (`--as agent2`, seed `32052`, sampled `2/10` = `20% >= 10%`: `ATT-9693`, `ATT-9627`).

* **Overall Script Verdict**: `NON-COMPLIANT` (`0/2`), solely due to the recurring legacy pre-`aaa-v2` Dimension 2 / Dimension 3 script mismatch:
  * **`ATT-9693` (`tier-a`)**: Passed `5/6` checks (`Deliverables`, `4-Augen Role Separation`, `Living Docs Parity`, `Commit Hygiene`, `PO Approval`); flagged Dim 2 (`Stage Subtask Progression`) because `aaa-v2` `tier-a` intentionally runs Gate 3 (`ATT-9704`) + `auto_gate.py` + Stage 6 rather than 5 legacy prose subtasks → CA Ticket [ATT-9706](https://atrainingtracker.atlassian.net/browse/ATT-9706).
  * **`ATT-9627` (`tier-b`)**: Passed `5/6` checks (`Deliverables`, `Gate Sequence`, `Living Docs Parity`, `Commit Hygiene`, `PO Approval`); flagged Dim 3 (`4-Augen Role Separation`) because `aaa-v2` `tier-b` uses zero Stage 1–5 subtasks and enforces independent 4-Augen separation via `stage6-final-review` (`final-review-verdict` Bounce 1 + Pass 2) rather than legacy Gate 1–5 subtask authors → CA Ticket [ATT-9707](https://atrainingtracker.atlassian.net/browse/ATT-9707).

> [!WARNING]
> **⚠️ PO Escalation: Persistent QA Non-Conformance (`REQ-PRO-091.4`, SUP.1 BP6)**
> The same legacy pre-`aaa-v2` `qa-audit` Dimension 2/3 mismatch has now recurred across **10 consecutive Schichten (> 2 sprints)**: Sprint 470 (`ATT-9595`), Sprint 471 (`ATT-9641`, `ATT-9642`), Sprint 472 (`ATT-9647`), Sprint 473 (`ATT-9655`, `ATT-9656`), Sprint 474 (`ATT-9662`, `ATT-9663`), Sprint 475 (`ATT-9667`, `ATT-9668`), Sprint 476 (`ATT-9675`, `ATT-9676`), Sprint 477 (`ATT-9683`, `ATT-9684`), Sprint 478 (`ATT-9690`, `ATT-9691`), Sprint 479 (`ATT-9698`), and Sprint 480 (`ATT-9706`, `ATT-9707`).
> **Root Cause**: `tools/jira_util.py`'s `qa_audit()` still evaluates legacy 5-subtask rules against `aaa-v2` (`ATT-9543`) tickets, creating duplicate false-positive CA tickets every Schicht.
> **Escalation Action Required**: Prioritize [ATT-9595](https://atrainingtracker.atlassian.net/browse/ATT-9595) into `Vom PO Freigegeben` (`447`) and batch-resolve the duplicate false-positive CA tickets (`ATT-9641`..`ATT-9707`) once `ATT-9595` lands.

### Quality Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)
Spot-checked all 5 `tier-a` Gate 3 subtasks (`ATT-9700`, `ATT-9702`, `ATT-9703`, `ATT-9704`, `ATT-9705`) and all 12 Stage 6 Cold Reviews (`10` merged + `2` bounced WIPs) against `git diff` and `--verify-warm` logs:
- **Authentic Technical Discrimination**: `0` rubber-stamped false passes observed. Adversarial Stage 6 Cold Reviews caught high-severity defects on **8 distinct tickets** before merge (`ATT-8100` HTTPS/path-traversal/Content-Type; `ATT-9694` fresh-install `minBike=3.5` runner regression; `ATT-9628` DB upgrade seeding race & `resolveWorkoutThresholdsInMemory()` precedence; `ATT-9693` Ref-before-Baro / missing-tick-0 QNH Huber saturation; `ATT-9627` overwrite-confirm reset & `AppModalBottomSheet` parity; `ATT-8111` `REQ/TST` parity & empty-list SQL guard; plus preserved WIPs `ATT-9626` hardcoded colors & 1-Hz overwrite race and `ATT-8174` 404 catalog URLs & stream leak).

---

## 5. Lean Tier-2 Integration Gate & Device Status

* **Target Ref**: `sprint/2026-11-25_01_Früh` @ `2e4a8eb5` (2-Batch `--verify-warm --max-workers=2` with `JAVA_TOOL_OPTIONS="-XX:ReservedCodeCacheSize=256m"` and `-Pkotlin.daemon.jvmargs=-Xmx3072m`, zero CodeCache or GC failures)
* **Build, Duplicate Classes & AndroidTest Compilation**: `:app:assembleDebug` **PASS**, `:app:compileDebugAndroidTestKotlin` **PASS**, `:app:checkDebugDuplicateClasses` **PASS**, `ANT-Android-SDKs` gitlink `160000` (`0e8cea714a231fb9565170d4ed01eafa868f9798`) verified clean.
* **Unit / Contract / Integration / Localization Tests**: **`1,914 / 1,917` PASS across `298` XML suites** (`0` errors, `0` skips; Batch 1 `628 / 631` across `91` suites, Batch 2 `100%` PASS in `7m 28s`; sole `3` failures are pre-existing tracked Bug **`ATT-9639`** `MeasuredNfrBenchmarkTest` [2] + `NonFunctionalRequirementsContractTest` [1]; `TuningPreferencesDataStoreRoundTripTest` excluded per pre-existing livelock **`ATT-9681`**; zero Sprint 480 regressions). Archived via `export-test-evidence --sprint 2026-11-25_01_Früh --no-kover --allow-failure` (`docs/engineering/test_evidence/sprint_2026-11-25_01_Früh.{md,json}`).
* **Governance, Python & Safety Linters**: `verify_requirement_governance.py` **22/22 PASS** (`Total=1,397` REQs / `1,405` TSTs, `21/21` SYSREQs `100%`, `0` code orphans across `2,719` files, `0` conflict markers across `13,514` files, `68` `SensorType` constants); `check_secrets_hygiene.py` PASS (`14,459` tracked files); `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `baseline_manager.py cleanup-audit` **ALL PASS**; Python `tools/test_*.py` **`617 / 618` PASS** (sole failure pre-existing Sprint 476 `ATT-9607` `REQ-DAT-049` reverse-trace rationale suffix).
* **Physical Device Deploy (`Rule 7.6` / `REQ-TST-004`)**: `adb devices -l` confirmed only `emulator-5554` attached (physical `Pixel 10` unplugged → best-effort deploy deferred cleanly without blocking closeout).
* **Ceremony 2 Human Review (`REQ-PRO-062`)**: Truthfully recorded as `SKIPPED` (`docs/engineering/reviews/sprint_2026-11-25_01_früh.md`) pending async PO acceptance in `Bereit für PO Abnahme` (ID `453`) / weekly PO decision session (`ATT-9545`).
