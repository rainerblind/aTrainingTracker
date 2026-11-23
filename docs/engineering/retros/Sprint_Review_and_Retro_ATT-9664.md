# Sprint Review & Retrospective: Schicht 2026-11-23_02_Spät (Sprint 475 — `ATT-9664`)

**Document ID**: `DOC-RET-475`  
**Sprint**: `2026-11-23_02_Spät` (Jira ID `475`, Window `15:00–22:00 CET`, `2026-11-23`)  
**Process Version**: `aaa-v2` (`aspice_governance.md` `v1.0.22`, PO-directed strictly 2 parallel lanes)  
**Target Release**: `V5.0.0` | **Status**: Reviewed | **Approver**: Pending PO Sign-Off  
**Branch / Final Code SHA**: `sprint/2026-11-23_02_Spät` @ `e0f528b2` (branched off `develop` @ `a515c1fd`)

---

## 1. Delivered Scope & Planned vs. Completed Velocity (`REQ-PRO-029`)

| Metric | Planned (Active) | Delivered (`Final Review (Human)` → `453`) | Hot WIP Carryover (`03_Nacht`) | Returned Untouched (`447`) |
| :--- | :---: | :---: | :---: | :---: |
| **Work Tickets** | 8 (`6` active + `2` untouched) | **5** (`ATT-9611`, `ATT-9610`, `ATT-8136`, `ATT-8991`, `ATT-9065`) | **1** (`ATT-9607` `Final Review (Agent)` @ `d71f78e7`) | **2** (`ATT-8721`, `ATT-9021`) |
| **Story Points** | `21.0 SP` (`17.0 SP` active) | **`12.0 SP`** | `5.0 SP` | `4.0 SP` |

* **Delivered Tickets (all 5 Stage-6-verified, labeled `sprint-2026-11-23_02_Spät`, archived to `Bereit für PO Abnahme` ID `453`)**:
  * **ATT-9611** (`tier-a`, `3 SP`, `b17c6563`): Polar Verity Sense & H10 offline HR UTC timestamp anchoring, `<= 5 s` bounded `patchMissingHeartRateSamples()`, commit-gated flash deletion, and structured `Log.i` diagnostics (`REQ-SET-130`, `TST-SET-132`).
  * **ATT-9610** (`tier-b`, `2 SP`, `d74bdbf8`): `SensorSourceDialog` HR Active Backups & Not Connected ordering by `(healthTier, modalityTier, deviceName, deviceId)` (`REQ-UI-2804`, `TST-UI-2804`).
  * **ATT-8136** (`tier-c`, `3 SP`, `7b225dee`): Removed all production mock catchers (`KotlinNothingValueException`, `isRealFilesDir`, empty cursor `moveToNext()`, foreign singleton resets), moved `EncodingUtils` out of `ui/`, hardened `SourceTextReadingAuditContractTest`, and migrated 8 doc/system/ASPICE contracts to `tools/` — **completes Milestone Epic `ATT-8120` (8/8)**!
  * **ATT-8991** (`tier-a`, `2 SP`, `aed8c02d`): Capability-gated BLE Polar HRM sources (`HrmSensorCapabilityHelper`) for `CADENCE`/`STRIDES`/`VO`/`GCT` and removed `BIKE_POWER` from `SLOPE`/`VERTICAL_SPEED`/`ASCENT`/`DESCENT` (`REQ-CON-057`, `TST-CON-057`).
  * **ATT-9065** (`tier-b`, `2 SP`, `fb5c8b94`): Persisted `AntMissingAdapterDialog` suppression across composition exit & cold-start DataStore hydration races (`REQ-UI-353`, `TST-UI-313`) — **completes Lane 2 Epic `ATT-5910` (4/4)**!
* **Hot WIP Carryover**: **`ATT-9607`** (`tier-a`, `5 SP`, Epic `ATT-68`): Gate 3 `ATT-9666` PASSED; Stage 6 Bounce 1/5 caught 4 concurrency defects (`AthleteProfileViewModel` cold-start `MutableSharedFlow(replay=0)` lost-wakeup race, missing `@Volatile` on `cachedPowerBenchmarks`, uncancelled `SportTypeViewModel.loadSportTypes`, `WorkoutRepository` `ThreadLocal` residue & missing `markWorkoutFinished` `Finalized` event) — ALL 4 healed & cleanly committed on `feature/ATT-9607` @ `d71f78e7` (`61/61` Bounce-1 suites + `253/253` across 54 XML suites + SHA-256 bundle; status `Final Review (Agent)` ready for immediate Stage 6 Pass 2 in `2026-11-23_03_Nacht`).
* **Lean Tier-2 Integration Gate (`e0f528b2`, 2-Batch `--verify-warm --max-workers=2 --no-kover`)**:
  * `:app:assembleDebug` + `:app:compileDebugAndroidTestKotlin`: **PASS**.
  * Android Unit/Contract/Integration/Localization/Migration Tests: **`2,912 / 2,916` PASS across `461` JUnit XML suites** (`0` errors, `0` skips; `JavaCoreMigrationStrategyContractTest` & `AspiceSweAuditContractTest` now PASS after `ATT-8136`; caught & fixed 3 post-`ATT-8136` test-fixture DataStore isolation leaks in `ControlTrackingViewModel*Test` & `RadarBrakeLightConcurrencyStressTest` at `e0f528b2` -> `32/32` confirmation PASS; sole 4 remaining failures are pre-existing tracked `ATT-9639` [`MeasuredNfrBenchmarkTest#testNfr014` ×2 + `NonFunctionalRequirementsContractTest`] and `ATT-9580` [`WorkoutClusterPreviewPersistenceTest#TST_PERF_010`], documented via `--allow-failure` per `ATT-9497`).
  * Python `tools/test_*.py`: **`618 / 618` PASS** (`184.8s`).
  * Governance & Safety Linters: `verify_requirement_governance.py` **22/22 PASS** (`21/21` SYSREQs `100%`, `416/436` [`95.41%`] SYSREQ-linked, `436/436` classified, `67` governed docs, `0` conflict markers, `68/68` `SensorType` Javadocs); `4/4` safety/secrets linters PASS; `ANT-Android-SDKs` `160000` gitlink verified.
  * Physical `Pixel 10` (`66020DLCR002FL`): unplugged during Ceremony 1 & Ceremony 2 (`emulator-5554` attached) -> best-effort `-PstampGitHash=true` deploy and heap hygiene check cleanly deferred per `Rule 7.6`.

---

## 2. Quantitative Sprint Scorecard (`PA 4.1` & `PA 4.2`) & SPC RCA

* **Throughput**: `5/6` active work tickets merged (`12.0 SP`; Jira metrics reports `6/7` / `17.0 SP` counting `ATT-9607` in `Final Review (Agent)`), `0` human-closed yet (async weekly PO session per `ATT-9545`).
* **Quality Gates**: Gate 3 FTPR **`33.3%`** (`1/3` recorded by `sprint_metrics.py`), **Stage 6 Cold Review FTPR `83.3%`** (`5/6` 1st-pass PASS across 3× Neon High fallback & 3× Flash reviews; 1 bounce on `ATT-9607`), E2E FTPR **`66.7%`**, SUP.9 Bug Resolution **`75.0%`** (`3/4`, 1 open WIP `ATT-9607`, `0` bugs `>7d`).
* **Second-Vendor Provenance (`Rule 3.3`, `RISK-011`)**: Anthropic Claude (`claude-sonnet-5-5-high`) verified at `0%` weekly quota until `2026-11-28T08:17:14Z`; all 3 `tier-a` Stage 6 reviews (`ATT-9611`, `ATT-8991`, `ATT-9607`) executed with sanctioned truthful Gemini 4 Neon High fallback (`--fallback-reason=claude_quota_exhausted`).
* **PA 4.2 Special Cause Variation RCA (`OVERALL_FTPR = 33.3% < LCL 49.11%`)**:
  * **Root Cause**: Inspection of `docs/engineering/gates/sprint_2026-11-23_02_spät.md` shows the `33.3%` Gate 3 figure is a **small-sample (`N=3`) parser artifact** combined with 1 genuine carryover revision:
    1. `ATT-9661` (`ATT-9611`, carried from Sprint 474) had 1 genuine revision (`13:14`) plus an intermediate comment (`13:19`) misclassified as `REVISION`.
    2. `ATT-9666` (`ATT-9607`, Sprint 475) passed on **1st try**, yet the Gate 3 reviewer posted a summary comment (`19:35:07`: `"PASS with 6 Mandatory Guardrails"`) 49 seconds before calling `gate-verdict ATT-9666 PASS` (`19:35:56`); `parse_subtask_verdict_from_text()` misclassified that pre-verdict comment as `REVISION`.
    3. Authentic Sprint 475 Gate 3 FTPR was **`100.0%` (`2/2`: `ATT-9665`, `ATT-9666`)**, and Stage 6 FTPR rose to **`83.3%` (`5/6`)**.
  * **Countermeasure Proposal (logged to `ATT-8223`)**: Tighten `parse_subtask_verdict_from_text()` in `tools/sprint_metrics.py` so only explicit `REVISION` verdicts (not intermediate `PASS` prose comments) increment revision counts, and instruct Gate 3 reviewers to post verdicts strictly via a single `gate-verdict` call.
* **Gate Verdict Spot-Check Audit (`REQ-PRO-092.6`)**: Spot-checked `ATT-9665` (`ATT-8991`), `ATT-9666` (`ATT-9607`), and all 6 Stage 6 verdicts (`ATT-9611`, `ATT-9610`, `ATT-8136`, `ATT-8991`, `ATT-9065`, `ATT-9607` Bounce 1): every verdict cited exact line numbers, freshly executed warm-worktree test counts, SHA-256 bundle hashes, and live `emulator-5554` `logcat`/screenshot evidence (`0` rubber-stamp false-passes; Gate 3 `ATT-9666` caught `.tmp.salvage` suffix & `private` DB backdoor guardrails pre-coding, and Stage 6 `ATT-9607` caught 4 real multi-threaded concurrency bugs).

---

## 3. Retrospective Insights & Tooling Proposals (For Weekly PO Review `ATT-8223` — `Rule 2.7`)

1. **Proposal 1 — `tools/auto_gate.py` 768m Kover Fork Batching & Tier-2 Exclusion (`Measured Problem`: 30m hang on `ATT-9610` blocking `/tmp/att_build_merge.lock` + false FAIL on `ATT-9611`)**: `select_dependent_tests()` selected 62–70+ suites in one `:app:testDebugUnitTest` fork, causing a 30-minute 768m Kover CodeCache hang on `ATT-9610` and pulling in pre-existing broken Tier-2 `MeasuredNfrBenchmarkTest` (`ATT-9639`). Propose chunking `auto_gate.py` Android test runs into `<= 25`-class batches with `--max-workers=2` and excluding Tier-2-deferred `MeasuredNfrBenchmarkTest` per `Rule 7.5`.
2. **Proposal 2 — `KotlinCompileDaemon` `-Xmx2048m` GC Thrash Guard (`Measured Problem`: `26m` stall on `ATT-9607` Bounce 1, `100%` Old Gen / `704` Full GCs)**: Even though `gradle.properties` sets `kotlin.daemon.jvmargs=-Xmx3072m`, a `-Xmx2048m` `KotlinCompileDaemon` spawned in a worktree entered OOM GC thrash (`751s` GC time). Propose checking `gradle.properties` / daemon args in `parallel_sprint_lock.py` or auto-recycling `KotlinCompileDaemon` when Old Gen stays `>= 98%`.
3. **Proposal 3 — Fix `parse_subtask_verdict_from_text` False-`REVISION` Metric Distortion (`Measured Problem`: PA 4.2 false-alarm `OVERALL_FTPR = 33.3%` on `ATT-9666` 1st-pass PASS)**: Ignore non-`GATE_VERDICT:` / `PASS`-labeled comments when tallying `REVISION` counts in `tools/sprint_metrics.py`.
4. **Proposal 4 — Prioritize `ATT-9595` (`qa-audit` `aaa-v2` Dim 2/3 Parity)**: Eliminates recurring false-positive `NON-COMPLIANT` audit verdicts and CA ticket churn (`ATT-9595`, `ATT-9641`..`ATT-9668`).

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`, SUP.1)

* **Audit Verdict (`docs/engineering/qa_audits/sprint_2026-11-23_02_spät.md`, `agent2`, seed `10964`)**: **`0 / 2` COMPLIANT (`NON-COMPLIANT`)** on sampled tickets `ATT-8991` (`tier-a`: PASS 5/6 dims, FAIL Dim 2 legacy 5-subtask check) and `ATT-9065` (`tier-b`: PASS 4/6 dims, FAIL Dim 2 & Dim 3 legacy subtask-only checks). Both tickets authentically complied with `aaa-v2` (`Rule 3.2`/`Rule 4.1`), including independent `agent2` Gate 3 (`ATT-9665`) and independent Stage 6 Cold Reviews (`ATT-8991`, `ATT-9065`).
* **Corrective Action Tickets Created**: [`ATT-9667`](https://atrainingtracker.atlassian.net/browse/ATT-9667) (Stage Subtask Progression), [`ATT-9668`](https://atrainingtracker.atlassian.net/browse/ATT-9668) (4-Augen Role Separation).
* **⚠️ PO Escalation: Persistent QA Non-Conformance (`REQ-PRO-091.4`, SUP.1 BP6)**: The exact same legacy pre-`aaa-v2` `qa-audit` Dim 2/3 script mismatch has now recurred across **5 consecutive `aaa-v2` sprints** (`2026-11-21_03_Nacht`, `2026-11-22_01_Früh`, `2026-11-22_02_Spät`, `2026-11-22_03_Nacht`, `2026-11-23_01_Früh`, `2026-11-23_02_Spät`), generating open backlog CA tickets `ATT-9595` (already in `Vom PO Freigegeben` ID `447`), `ATT-9641`, `ATT-9642`, `ATT-9647`, `ATT-9655`, `ATT-9656`, `ATT-9662`, `ATT-9663`, `ATT-9667`, and `ATT-9668`. **Escalation Rationale**: Autonomous Schichten cannot edit protected `tools/jira_util.py` during Ceremony 2 (`Rule 2.7`); prioritizing `ATT-9595` in `447` (and deduplicating `ATT-9641`..`ATT-9668` to `ATT-9595`) will permanently resolve the script mismatch and restore authentic `aaa-v2` `qa-audit` signal.
