# Sprint Review & Retrospective: Sprint `2026-11-16_03_Nacht` (`ATT-8500`)

* **Schicht Window**: `2026-11-16 23:07 CET` – `2026-11-17 05:00 CET` (Jira Sprint ID: `455`)
* **Sprint Branch HEAD**: `c4795aee72d100138868ed80e8c29478d85ea7df` (`sprint/2026-11-16_03_Nacht` -> merged `--no-ff` to `develop`)
* **Execution Mode**: 2 Parallel `Flash` Lanes (`Lane A`: `ATT-8138` ASPICE 4.0; `Lane B`: Product Features/Bugs) + Stage 6 `Pro` Sparhebel (`Pro` reserved exclusively for high-risk `ATT-8451`)

---

## 1. Sprint Review — Delivered & Verified (`11 / 14` Tickets = `15.0 SP`)

All 11 completed tickets passed Stage 1–5 + independent zero-context Stage 6 Cold Review into `Final Review (Human)` (labeled `sprint-2026-11-16_03_Nacht`, queued in `Bereit für PO Abnahme` ID `453`):

| Lane | Ticket | SP | Stage 6 | Summary & Verification Highlight |
| :---: | :--- | :---: | :---: | :--- |
| A | [ATT-8153](https://rainerblind.atlassian.net/browse/ATT-8153) | 2 | Flash `PASS` | QA corrective action escalation & Ceremony 2 tracking (`e6c9b892`) |
| A | [ATT-8167](https://rainerblind.atlassian.net/browse/ATT-8167) | 1 | Flash `PASS` | Risk register ASPICE 4.0 `MAN.5` + process risks `RISK-008..011` (`ec57013f`, 161/161 contract PASS) |
| A | [ATT-8437](https://rainerblind.atlassian.net/browse/ATT-8437) | 1 | Flash `PASS` | Replaced brittle `== 363` assertions with invariants (`e95d8ced`, 182/182 Python PASS) |
| A | [ATT-8427](https://rainerblind.atlassian.net/browse/ATT-8427) | 1 | Flash `PASS` | Authentic `WorkoutSummaries`/`ExtremumValues` SQLite evidence (`643096fc`, 190/190 Python PASS) |
| A | [ATT-8426](https://rainerblind.atlassian.net/browse/ATT-8426) | 2 | Flash `PASS` | Dual-Vendor generic model policy, Claude non-simulation & 7-day release gate (`4479f2af`) |
| A | [ATT-8154](https://rainerblind.atlassian.net/browse/ATT-8154) | 2 | Flash `PASS` | Closed gate bypass paths & enforced structured Stage 6 validation (`3a670e08`, 432/432 unittests PASS) |
| B | [ATT-8451](https://rainerblind.atlassian.net/browse/ATT-8451) | 2 | **Pro** `PASS` | `PolarOnlineStreamingBridge` deterministic timeout & `polarApi != null` reconnect (`14070717`, 124/124 PASS) |
| B | [ATT-8469](https://rainerblind.atlassian.net/browse/ATT-8469) | 1 | Flash `PASS` | Morning Check start CTA & cutoff slider in wellness mode (`241df3a3`, 415/415 PASS) |
| B | [ATT-8468](https://rainerblind.atlassian.net/browse/ATT-8468) | 1 | Flash `PASS` | Removed DFA α1 info card from `PhysiologyModelsSection` + Light/Dark visual check (`b85b892c`, 348/348 PASS) |
| B | [ATT-8464](https://rainerblind.atlassian.net/browse/ATT-8464) | 1 | Flash `PASS` | Removed cluster split menu item from long-click while keeping Heatmap split (`ed75497b`, 353/353 PASS) |
| B | [ATT-8462](https://rainerblind.atlassian.net/browse/ATT-8462) | 1 | Flash `PASS` | `EditSensorFieldDialog` 3-group logical order + Light/Dark screenshots (`c4795aee`, 71/71 PASS) |

### Seamless WIP Rollover (`2` Tickets = `5.0 SP` -> `2026-11-17_01_Früh`)
* **[ATT-8155](https://rainerblind.atlassian.net/browse/ATT-8155) (`3 SP`, `ATT-8138`)**: Stages 1–5a complete on `feature/ATT-8155` (`1cff5fff`, `51/51` targeted integration/contract tests PASS, `ATT-8558` in `In Überprüfung`). Ready for immediate Gate 5 Strategy A merge at `01_Früh` start.
* **[ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498) (`2 SP`, `ATT-5908`)**: Stages 1–5a docs-only complete on `feature/ATT-8498` (`458893d1`, Stage 4 `191/191` PASS, `ATT-8561` in `In Überprüfung`). Ready for Gate 5 Strategy A + Light/Dark walkthrough screenshot verification in `01_Früh`.

---

## 2. Tier-2 Integration Gate & Process Metrics

* **Tier-2 Verification (`c4795aee`)**: `./gradlew testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug --max-workers=2` **BUILD SUCCESSFUL in `29m 21s`** (**`6,540` tests, `1,119` suites, `0` failures, `0` errors, `0` skipped**).
* **Governance Parity**: `python3 tools/verify_requirement_governance.py` — **14/14 checks PASS**, 100% parity (`1,200` REQs / `1,199` TSTs).
* **Physical Device Smoke**: `Pixel 10` (`66020DLCR002FL`) `installDebug` **PASS in `8s`**.
* **Artifacts Exported**: `docs/engineering/gates/sprint_2026-11-16_03_nacht.md` (62 records), `docs/engineering/reviews/sprint_2026-11-16_03_nacht.md` (`NOT PERFORMED`, deferred to PO queue `453`), `docs/engineering/test_evidence/sprint_455.{md,json}`, `docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md`, `docs/management/risk_register.md`.

---

## 3. Retrospective Analysis & PO Morning Directives

### What Worked Well
* **2-Lane Flash + Stage 6 `Pro` Sparhebel (`Rule 7.2`)**: Delivered **11 tickets (`15.0 SP`)** in ~6 hours without a single Gate 1–6 defect escape, reserving `Pro` strictly for high-risk `ATT-8451` BLE/Polar concurrency.
* **`ATT-8154` Live Dogfooding**: `ATT-8154` closed stage-skip bypasses & structured Stage 6 checks (`validate_stage6_review_structure()`) and passed its own enforcement during Stage 6 (`db21fa15`).

### Action Items & Frühschicht (`2026-11-17_01_Früh`) Handover
1. **PO Directive (`22:53`) — Eliminate Per-Sprint Full-Test Overhead**: Running full `./gradlew testDebugUnitTest` (`6,540` tests) took **`29m 21s`** during Nachtschicht Tier-2 closure. Per PO direction (`22:53`), `01_Früh` must formalize the simplified, low-overhead Tier-2 / per-sprint test policy (delta/impacted suites + smoke contracts during regular Schicht cutoffs, full `6,540`-test + Kover reserved for weekly/release cadence).
2. **Untracked PO Concept (`docs/management/schichtbetrieb.md`)**: Preserved untouched in the workspace; defines the formal 3-phase `Schichtwechsel` & `Schichtbuch` template for Frühschicht alignment/review with the PO.
3. **SPC M3 Rubber-Stamping Alert (`PA 4.2`)**: Consecutive sprints (`5 >= 2`) recorded `100.0%` post-submission Gate 1–5 FTPR (`51` subtasks). Note real catches did occur *pre-submission* (`ATT-8498` `TuningDesignGuidelinesContractTest` trap, `ATT-8426` premature check), artificially inflating post-submission FTPR. Encourage logging genuine Gate revisions cleanly when pre-Gate checks uncover gaps.

---

## 4. QA Findings & Corrective Actions

* **QA Audit (`docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md`, `--as agent2`, seed `22737`)**: Verdict **`NON-COMPLIANT`** on `2/2` sampled tickets:
  * `ATT-8426`: Flagged `4-Augen Role Separation` = `FAIL` and `Commit Hygiene` = `FAIL`.
  * `ATT-8462`: Flagged `4-Augen Role Separation` = `FAIL`.
* **Root Cause & Tooling Defect Discovered**: During `qa-audit`, `tools/jira_util.py` failed to query/create Jira corrective actions because (1) its deduplication search hits removed endpoint `/rest/api/3/search` (`410 Gone` instead of `/rest/api/3/search/jql`) and (2) `create_issue` passes hardcoded `issuetype_id="10005"` (`HTTP 400 invalid issuetype`), causing silent fallback to placeholder strings `[ATT-CA-4-Augen-Role-Separation]` / `[ATT-CA-Commit-Hygiene]`.
* **Corrective Action Tracked**: Filed real Jira backlog bug **[ATT-8559](https://rainerblind.atlassian.net/browse/ATT-8559)** (`[Bug] qa-audit Corrective Action Creation fails with /rest/api/3/search 410 and invalid issuetype 10005`, `severity-major`, Epic `ATT-8138`) to fix `qa-audit` Jira API calls and audit why squashed `sprint/*` commits / `agent2` stage-vs-Stage-6 attribution triggered `4-Augen` and `Commit Hygiene` flags.
