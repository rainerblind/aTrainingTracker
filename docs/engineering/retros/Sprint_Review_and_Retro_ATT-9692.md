# Sprint Review & Retrospective: Schicht `2026-11-24_03_Nacht` (Sprint 479)

**Document ID**: `DOC-RET-479`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Ticket**: [ATT-9692](https://rainerblind.atlassian.net/browse/ATT-9692) | **Sprint Window**: `2026-11-24 22:00 – 2026-11-25 05:00 CET` | **Process Version**: `aaa-v2` (`v1.0.22`) | **Target Release**: `V5.0.0`

---

## 1. Executive Summary & Delivery Velocity

Executed under the PO-directed **3 parallel lanes** policy across disjoint Epics (`ATT-1286` Cycling Radar → `ATT-6033` Architecture/Perf → `ATT-166` Sensor Filtering; `ATT-5910` Equipment/BLE/SportTypes; `ATT-166` Fused Sensors UI/Naming), Schicht `2026-11-24_03_Nacht` drained both Sprint 478 WIP carryovers immediately and delivered **10 / 10 merged Stage-6-verified work tickets (`18.0 SP`)** into `Final Review (Human)` (`sprint-2026-11-24_03_Nacht` → `Bereit für PO Abnahme` ID `453`) with **0 net regressions** (Lean Tier-2 caught and resolved 1 mock-parity regression `aa2fb662` on `ATT-8445` before merge), preserved **1 hot Stage-6-ready WIP carryover (`1.0 SP`)** cleanly on `feature/ATT-8442` for `2026-11-25_01_Früh`, and returned **4 untouched tickets** cleanly to the top of `Vom PO Freigegeben` (ID `447`).

| Category | Tickets | Story Points | Keys & State |
| :--- | :---: | :---: | :--- |
| **Initially Committed + Refilled (Phase 1–2)** | 15 | 25.0 SP* | `2` WIP carryovers (`ATT-8106`, `ATT-8723`) + `13` disjoint-Epic tickets pulled from `447` (`*` `ATT-9693`/`ATT-9694` unestimated in `447`) |
| **Completed & Merged (`Final Review (Human)` → `453`)** | **10** | **18.0 SP** | `ATT-8106` (`1 SP` `tier-c`), `ATT-8723` (`2 SP` `tier-a`), `ATT-8722` (`2 SP` `tier-b`), `ATT-8113` (`2 SP` `tier-b`), `ATT-8726` (`1 SP` `tier-a`), `ATT-8446` (`2 SP` `tier-b`), `ATT-8112` (`2 SP` `tier-c`), `ATT-9019` (`2 SP` `tier-b`), `ATT-9695` (`2 SP` `tier-a`), `ATT-8445` (`2 SP` `tier-b`) |
| **Hot WIP Carryover to `2026-11-25_01_Früh`** | **1** | **1.0 SP** | `ATT-8442` (`1 SP` `tier-c`, `severity-major`, status `Final Review (Agent)`, `feature/ATT-8442` @ `fe95024c`, `109/109` `auto_gate.py` PASS + `de-DE` emulator screenshots committed; ready for immediate Stage 6 Cold Review) |
| **Returned Untouched to `Vom PO Freigegeben` (`447`)** | **4** | — | `ATT-9694` (`tier-a`), `ATT-9693` (`tier-a`), `ATT-8101` (`1 SP` `tier-b`), `ATT-8111` (`1 SP` `tier-c`) |

### Delivered Commits on `sprint/2026-11-24_03_Nacht` (`aa2fb662`)
1. `0636c865` — **`ATT-8106`** (`1 SP`, `tier-c`, WIP carryover, Flash): Placed Developer Menu under Settings directly below Expert Settings across all 9 locales with clean navigation parity.
2. `43e541e0` — **`ATT-8723`** (`2 SP`, `tier-a`, WIP carryover, Neon): Completed BLE DIS reconnect backfill for `MyBTLEDevice` (Model/Serial `0x2A24`/`0x2A25`) and eliminated Polar DIS deduplication gaps (healed on Stage 6 Bounce 1/5).
3. `ff6aa385` — **`ATT-8722`** (`2 SP`, `tier-b`, Flash): Replaced hardcoded manufacturer lookup with Garmin FIT SDK `Manufacturer` resolution and fixed the `337–350` index-shift defect.
4. `969a878e` — **`ATT-8113`** (`2 SP`, `tier-b`, Neon `Rule 8.5`): Added Post-Workout ERTS smoothing toggle inside Fused `EditDeviceDialog` backed by `editAndSync` persistence and 9-language localization.
5. `2f50e1d0` — **`ATT-8726`** (`1 SP`, `tier-a`, `severity-major`, Gate 3 `ATT-9696` PASS, Neon `Rule 8.5`): Prevented `RadarEdgeOverlay` targets from jumping back to the bottom edge when crossing 140m on V1/ANT+ radar streams.
6. `bdc89438` — **`ATT-8446`** (`2 SP`, `tier-b`, Flash): Listed paired BLE Polar H10 / Verity Sense sensors with Live-IMU capability under Cadence in `SensorSourceDialog`.
7. `e677878d` — **`ATT-8112`** (`2 SP`, `tier-c`, Neon `Rule 8.5`): Unified Fused source toggles into `AttFusedDeviceSections.kt` and ratcheted `EditDeviceDialog.kt` from `1,967` down to `1,480` LoC (`<= 1,500` ceiling).
8. `96e969be` — **`ATT-9019`** (`2 SP`, `tier-b`, Neon `Rule 8.5`): Replaced `ClusterNamingDialog` full `GoogleMap` with lightweight `PathPreviewMap`, added bounded `createSensorMarker` LRU cache, and hoisted/cached polyline stations in `WorkoutClusterEngine`.
9. `b1fc22af` — **`ATT-9695`** (`2 SP`, `tier-a`, `severity-major`, Gate 3 `ATT-9697` PASS, Neon): Propagated resolved walking `BSportType` into `SpeedAndLocationDevice_ATTFused` and verified against real Pixel 10 sidecar walk/cycling replay fixtures.
10. `88783988` + `aa2fb662` — **`ATT-8445`** (`2 SP`, `tier-b`, `severity-minor`, Flash + Coordinator Tier-2 fix): Added reactive `SportTypesRepository.sportTypesChanged` flow so `SportTypeListFragment` / `SportTypesTabsScreen` and Compose pickers refresh immediately after Swimming activation without app restart (healed on Stage 6 Bounce 1/5; Ceremony 2 Lean Tier-2 caught and fixed `rememberSportTypesList()` default routing via `repository.sportTypesList` in `aa2fb662` to preserve `WorkoutClusterDialogsUiTest` mock parity).

---

## 2. Quantitative Sprint Scorecard (PA 4.1 & PA 4.2)

*Source*: `docs/engineering/metrics/process_metrics.jsonl` & `docs/engineering/gates/sprint_2026-11-24_03_nacht.md` (`17` gate records; `claude-sonnet-5-5-high` verified at `0%` weekly quota until `2026-11-28T08:17:14Z` → sanctioned truthful Gemini 4 Neon High Stage 6 fallback active per `Rule 3.3`).

| Metric | Sprint 479 Value | 9-Sprint Baseline ($\mu \pm 2\sigma$) | Status |
| :--- | :---: | :---: | :---: |
| **Ticket Throughput (Jira `Final Review (Human)` + `Final Review (Agent)`)** | `10` merged / `18.0 SP` (`11/12` `19.0 SP` incl. Stage-5-done `ATT-8442`) | $\mu = 9.67$ / `22.44 SP` (`[1.86, 17.48]` / `[5.77, 39.12]`) | **In Statistical Control** |
| **Gate 3 (Impl-Plan, `tier-a`) FTPR** | `33.3%` recorded (`1/3`) / **`100.0%` authentic (`3/3`)** | LCL `25.91%` ($\mu = 63.78\%$, $\sigma = 30.07\%$) | **In Control** *(parser note below)* |
| **Stage 6 Cold Final Review FTPR** | **`80.0%`** (`8/10` 1st-pass PASS; 2 bounces healed on Pass 2) | — | **Strong Quality** |
| **End-to-End Gate FTPR (Gates 1–6)** | **`69.2%`** recorded (`84.6%` authentic) | — | **In Control** |
| **Bug Resolution Rate (`SUP.9`)** | `75.0%` (`3/4` resolved awaiting human: `ATT-8726`, `ATT-9695`, `ATT-8445`; sole open `ATT-8442` is Stage-6-ready WIP carryover, `8.5d`) | — | Tracked |
| **Static Analysis Baselines (`SWE.4`)** | Detekt `9,509 / 9,516` (`Δ 0`), Lint `216 / 216` (`0` Recycle) | Ceilings enforced | **Clean** |

### Note on Gate 3 FTPR Parser Artifact (`33.3%` recorded vs `100.0%` authentic)
All 3 `tier-a` Gate 3 evaluations (`ATT-9696` on `ATT-8726`, `ATT-9689` on `ATT-8723`, and `ATT-9697` on `ATT-9695`) passed on the **first pass**. Both `ATT-9689` and `ATT-9697` had their initial `PASS` verdict comment posted without the literal `GATE_VERDICT: PASS` prefix followed 45–53 seconds later by the prefixed record, causing `sprint_metrics.py` to classify them as `REVISION` (`1/3 = 33.3%` instead of `3/3 = 100.0%`). All SPC metrics remained strictly **In Statistical Control**.

---

## 3. Retrospective Insights & Tooling Proposals (Rule 2.7 Allowlist Compliant)

*No protected governance, rule, skill, or tool files were modified during this autonomous closeout (`Rule 2.7`). All proposals below are rolled up into rolling `Wochen Review & Retro` ([ATT-8223](https://rainerblind.atlassian.net/browse/ATT-8223)) for PO prioritization:*

1. **Proposal 1 — Bake `-Pkotlin.daemon.jvmargs=-Xmx3072m` + `JAVA_TOOL_OPTIONS="-XX:ReservedCodeCacheSize=256m"` into Host/Tooling Defaults**:
   - **Measured Problem**: Host `~/.gradle/gradle.properties` overrides `kotlin.daemon.jvmargs=-Xmx2048m`, causing two cold-compile `KotlinCompileDaemon` `99.97–99.99%` Old Gen GC death spirals early in Sprint 479 (`22:57` & `23:18 CET`). Passing BOTH `-Dorg.gradle.jvmargs="-Xmx4096m -XX:MaxMetaspaceSize=512m -Dkotlin.daemon.jvm.options=-Xmx3072m"` AND project property `-Pkotlin.daemon.jvmargs=-Xmx3072m` plus `JAVA_TOOL_OPTIONS="-XX:ReservedCodeCacheSize=256m"` completely eliminated GC thrashing and Kover 49MB CodeCache warnings for the remaining 6 hours across all 3 parallel lanes and Ceremony 2!
   - **Action**: Update host `~/.gradle/gradle.properties` to `-Xmx3072m` and inject `-Pkotlin.daemon.jvmargs=-Xmx3072m` + `-XX:ReservedCodeCacheSize=256m` by default in `tools/auto_gate.py` and `tools/parallel_sprint_lock.py`.
2. **Proposal 2 — Prioritize Existing Process-Escape Bug `ATT-9681` (`TuningPreferencesDataStoreRoundTripTest` Livelock) & Add `auto_gate.py --reuse-junit-xmls`**:
   - **Measured Problem**: `ATT-9681` livelocked test executors twice in Sprint 479 (Lane 3 `ATT-8113` at `00:23 CET` and Lane 2 `ATT-8445` 301-suite `auto_gate.py` run at `04:08 CET`), forcing manual exclusion + `auto_gate.build_evidence_bundle(...)` workarounds.
   - **Action**: Prioritize `ATT-9681` into `Vom PO Freigegeben` (`447`) and add `--reuse-junit-xmls` to `tools/auto_gate.py`.
3. **Proposal 3 — Fix `sprint_metrics.py` Gate 3 `GATE_VERDICT: PASS` Prefix Parser**:
   - **Measured Problem**: Same parser misclassification recurred on `ATT-9689` and `ATT-9697`, suppressing authentic `100.0%` Gate 3 FTPR to `33.3%`.
   - **Action**: Ignore non-prefixed `PASS` audit comments (or treat `PASS` followed by `GATE_VERDICT: PASS` without an intervening `REVISION` verdict as 1st-time pass) in `tools/sprint_metrics.py`.
4. **Proposal 4 — Prioritize `ATT-9595` (`qa-audit` `aaa-v2` Update) & `ATT-9639` (`MeasuredNfrBenchmarkTest` `REQ-NFR-014`)**:
   - **Measured Problem**: `ATT-9595` generated its 9th consecutive false-positive CA ticket (`ATT-9698`), and `ATT-9639` accounted for 100% of the `3` failures in Ceremony 2 Lean Tier-2 (`1,376/1,379`).

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`, `REQ-PRO-092.6`)

*Audit Source*: `docs/engineering/qa_audits/sprint_2026-11-24_03_nacht.md` (`--as agent2`, seed `23977`, sampled `2/10` = `20% >= 10%`: `ATT-8723`, `ATT-8726`).

* **Overall Script Verdict**: `NON-COMPLIANT` (`0/2`), solely due to the recurring legacy pre-`aaa-v2` Dimension 2 (`Stage Subtask Progression`) script mismatch:
  * **`ATT-8723` (`tier-a`)**: Passed `5/6` checks (`Deliverables`, `4-Augen Role Separation`, `Living Docs Parity`, `Commit Hygiene`, `PO Approval`); flagged Dim 2 because `aaa-v2` `tier-a` intentionally runs Gate 3 (`ATT-9689`) + `auto_gate.py` + Stage 6 rather than 5 legacy prose subtasks → CA Ticket [ATT-9698](https://atrainingtracker.atlassian.net/browse/ATT-9698).
  * **`ATT-8726` (`tier-a`)**: Passed `5/6` checks (`Deliverables`, `4-Augen Role Separation`, `Living Docs Parity`, `Commit Hygiene`, `PO Approval`); flagged Dim 2 for the identical `aaa-v2` `tier-a` Gate 3 (`ATT-9696`) schema → CA Ticket [ATT-9698](https://atrainingtracker.atlassian.net/browse/ATT-9698).

> [!WARNING]
> **⚠️ PO Escalation: Persistent QA Non-Conformance (`REQ-PRO-091.4`, SUP.1 BP6)**
> The same legacy pre-`aaa-v2` `qa-audit` Dimension 2/3 mismatch has now recurred across **9 consecutive Schichten (> 2 sprints)**: Sprint 470 (`ATT-9595`), Sprint 471 (`ATT-9641`, `ATT-9642`), Sprint 472 (`ATT-9647`), Sprint 473 (`ATT-9655`, `ATT-9656`), Sprint 474 (`ATT-9662`, `ATT-9663`), Sprint 475 (`ATT-9667`, `ATT-9668`), Sprint 476 (`ATT-9675`, `ATT-9676`), Sprint 477 (`ATT-9683`, `ATT-9684`), Sprint 478 (`ATT-9690`, `ATT-9691`), and Sprint 479 (`ATT-9698`).
> **Root Cause**: `tools/jira_util.py`'s `qa_audit()` still evaluates legacy 5-subtask rules against `aaa-v2` (`ATT-9543`) tickets, creating duplicate false-positive CA tickets every Schicht.
> **Escalation Action Required**: Prioritize [ATT-9595](https://atrainingtracker.atlassian.net/browse/ATT-9595) into `Vom PO Freigegeben` (`447`) and batch-resolve the duplicate false-positive CA tickets (`ATT-9641`..`ATT-9698`) once `ATT-9595` lands.

### Quality Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)
Spot-checked all 3 Gate 3 subtasks (`ATT-9696`, `ATT-9689`, `ATT-9697`) and all 10 Stage 6 Cold Reviews against `git diff` and `--verify-warm` logs:
- **Authentic Technical Discrimination**: `0` rubber-stamped false passes observed. Stage 6 Bounce 1 catches on `ATT-8723` (BLE DIS reconnect/Polar dedup gaps) and `ATT-8445` (live refresh & evidence completeness) verified real code/evidence gaps prior to Pass 2 merge, and Ceremony 2 Lean Tier-2 caught a cross-module mock-parity regression in `WorkoutClusterDialogsUiTest` caused by `ATT-8445`'s `rememberSportTypesList()`, fixed cleanly at `aa2fb662` and confirmed `4/4` PASS.

---

## 5. Lean Tier-2 Integration Gate & Device Status

* **Target Ref**: `sprint/2026-11-24_03_Nacht` @ `aa2fb662` (2-Batch `--verify-warm --max-workers=2` with `JAVA_TOOL_OPTIONS="-XX:ReservedCodeCacheSize=256m"` and `-Pkotlin.daemon.jvmargs=-Xmx3072m`, zero CodeCache or GC failures)
* **Build & AndroidTest Compilation**: `:app:assembleDebug` **PASS**, `:app:compileDebugAndroidTestKotlin` **PASS**, `ANT-Android-SDKs` gitlink `160000` (`0e8cea714a231fb9565170d4ed01eafa868f9798`) verified clean.
* **Unit / Contract / Integration / Localization Tests**: **`1,376 / 1,379` PASS across `183` XML suites** (`0` errors, `0` skips; `WorkoutClusterDialogsUiTest` `4/4`, `ClusterImperialAndLocaleUiTest`, and `SportTypesComposeHelpersTest` confirmed 100% PASS after `aa2fb662`; sole `3` failures are pre-existing tracked **`ATT-9639`** `MeasuredNfrBenchmarkTest#testNfr014_cockpitTelemetryThrottling` + `NonFunctionalRequirementsContractTest`; `TuningPreferencesDataStoreRoundTripTest` excluded per pre-existing livelock **`ATT-9681`**). Archived via `export-test-evidence --no-kover --allow-failure` (`docs/engineering/test_evidence/sprint_2026-11-24_03_Nacht.{md,json}`).
* **Governance, Python & Safety Linters**: `verify_requirement_governance.py` **22/22 PASS** (`Total=1,389` REQs / `1,397` TSTs, `435/456 [95.39%]` SYSREQ-linked, `0` orphans, `0` conflict markers); `check_secrets_hygiene.py` PASS (`13,671` tracked files); `check_static_analysis_baselines.py` PASS (`detekt 9,509 <= 9,516`, `lint 216 <= 216`, `0` Recycle); `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` **3/3 PASS**; Python `tools/test_*.py` **`617/618` PASS** (sole failure pre-existing Sprint-476 `ATT-9607` `REQ-DAT-049` reverse-trace suffix).
* **Physical Device Deploy & Heap Hygiene (`Rule 7.6` / `REQ-TST-004` / `REQ-STB-022.4`)**: Google `Pixel 10` (`66020DLCR002FL`) verified clean at Ceremony 1 (`0` `.hprof` dumps; `7` healthy `.heap.csv` logs incl. 2.7h ride max `33.6%` Java heap / `66.7 MB` native pruned); commit-stamped debug APK (`aa2fb662`, `-PstampGitHash=true`) built and installed cleanly onto physical **`Pixel 10` (`66020DLCR002FL`)** at Ceremony 2 (`BUILD SUCCESSFUL in 1m 29s`).
* **Ceremony 2 Human Review (`REQ-PRO-062`)**: Truthfully recorded as `SKIPPED` (`docs/engineering/reviews/sprint_2026-11-24_03_nacht.md`) pending async PO acceptance in `Bereit für PO Abnahme` (ID `453`) / weekly PO decision session (`ATT-9545`).
