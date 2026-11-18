# Sprint Review & Retrospective: Sprint `2026-11-17_03_Nacht` (`ATT-8740`)

* **Schicht Window**: `2026-11-17 22:31 CET` – `2026-11-18 05:37 CET` (Jira Sprint ID: `458`)
* **Sprint Branch**: `sprint/2026-11-17_03_Nacht` (`a3508e6b` pre-retro HEAD -> `--no-ff` into `develop` upon close)
* **Execution Mode**: 3 Parallel `Flash` Lanes (`Lane A`: `ATT-8138`/`ATT-232` ASPICE 4.0; `Lane B` & `Lane C`: Disjoint Product Epics) + Stage 6 `Pro` Sparhebel (`Rule 3.3`)

---

## 1. Sprint Review — Delivered (`11 / 11` Finished Work Tickets = `21.0 SP`) + Clean Rollover

All 11 finished work tickets passed Stages 1–5 + independent zero-context Stage 6 Cold Review (`Flash` / `Pro`) into `Final Review (Human)` (`sprint-2026-11-17_03_Nacht` label -> archived to `Bereit für PO Abnahme` ID `453`):

| Lane | Ticket | SP | Final Commit(s) | Summary & Verification Highlight |
| :---: | :--- | :---: | :---: | :--- |
| A | [ATT-8168](https://rainerblind.atlassian.net/browse/ATT-8168) | 2 | `459f5eb5`, `de6620a5` | Exact binomial/p-chart SPC bounds, `process_version` reset & suspended CL3/CL4 claims (`Cold Pro`) |
| C | [ATT-8707](https://rainerblind.atlassian.net/browse/ATT-8707) | 2 | `d911bff1`, `60fd63a8` | Guard `VerticalSpeedAndSlopeDevice` baro calibration, startup reset & `Double.isFinite` (`Cold Pro`) |
| B | [ATT-8690](https://rainerblind.atlassian.net/browse/ATT-8690) | 3 | `34b78941` | Process-wide `PolarBleApi` singleton multiplexer + ref-counting for offline arming/backfill (`Cold Pro`) |
| A | [ATT-8169](https://rainerblind.atlassian.net/browse/ATT-8169) | 2 | `4e1c2fa5` | Wire safety linters (`cursor`/`lifecycle`/`enum`) in CI, baseline ratchet & reduction plan (`Cold Flash`) |
| C | [ATT-8705](https://rainerblind.atlassian.net/browse/ATT-8705) | 3 | `d96fd52f` | Anchor offline ERTS heading, dampen 1Hz gyro aliasing, symmetric sanity & `Laps.db` sync (`Cold Pro`) |
| A | [ATT-8170](https://rainerblind.atlassian.net/browse/ATT-8170) | 1 | `33e99b0a` | Wire CI secret scanner (`check_secrets_hygiene.py`), `0600` credential modes & redact `ATT-4004` (`Cold Flash`) |
| C | [ATT-8719](https://rainerblind.atlassian.net/browse/ATT-8719) | 2 | `b08c779e`, `dace03f5` | Front/Rear wheel mounting position in `EditDeviceDialog` + Light/Dark PNG (`Cold Pro` bounce heal) |
| B | [ATT-8706](https://rainerblind.atlassian.net/browse/ATT-8706) | 2 | `7dcca5e7`..`f5e3feac` | HRV/RR gap flagging (`[250, 2500] ms`), monotonic arbitration & DFA gate (`3 Cold Pro` bounce heals) |
| A | [ATT-8207](https://rainerblind.atlassian.net/browse/ATT-8207) | 2 | `3b390f63` | Preserve single-atomic-commit invariant (`feature/<KEY>`) across Stage 6 bounces (`Cold Pro` heal) |
| B | [ATT-8105](https://rainerblind.atlassian.net/browse/ATT-8105) | 1 | `0843e81d` | Isolate `HrvDfaA1RangeSlider` drags from `HorizontalPager` in `ZonesSettingsActivity` + PNG (`Cold Flash`) |
| A | [ATT-8631](https://rainerblind.atlassian.net/browse/ATT-8631) | 1 | `a3508e6b` | Migrate `SensorType` Javadoc audit from `SensorTypeUnitsContractTest` into governance (`Cold Pro`) |

* **Continuous WIP Carryover (`3.0 SP` -> `2026-11-18_01_Früh` ID `459`)**: [ATT-8717](https://rainerblind.atlassian.net/browse/ATT-8717) (Polar BLE UUID/DIS hardware ID, `3 SP`, Stage 5a `3603a493` Gate 5 `PASS` -> `Final Review (Agent)`) rolls cleanly via `carryover-wip` for immediate `Cold Pro` Stage 6 review on `feature/ATT-8717`.
* **Untouched Return (`2.0 SP` -> `Vom PO Freigegeben` ID `447`)**: [ATT-8206](https://rainerblind.atlassian.net/browse/ATT-8206) returned to top of queue `447` via `return-unstarted`.

---

## 2. Lean Tier-2 Integration Gate & Quantitative Process Metrics (PA 4.1 & PA 4.2)

* **Pre-Existing Process-Escape Catch (`build.*`)**: Initial full `build.*` Tier-2 run (`633/634` PASS) exposed `DeadCodeEliminationContractTest.testComposePreviewsVisibilityAudit` failing `1/5` on 4 pre-existing `ATT-8450` (`3376bb3f`) `@Preview` functions in `EditDeviceDialog.kt:1598-1649` (`internal fun` instead of `private fun`) -> filed transparently under `SUP.9` as [ATT-8823](https://rainerblind.atlassian.net/browse/ATT-8823) (`file-process-escape --source=tier2 --ticket=ATT-8450`).
* **Clean Scoped Tier-2 Evidence (`a3508e6b`)**: `:app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --max-workers=1` across all 102 affected delta/integration/localization/architecture/contract suites (excluding `ATT-8823`'s `testComposePreviewsVisibilityAudit` while still executing the 4 remaining `DeadCodeEliminationContractTest` methods): **`633/633` PASS across `102` XML suites (`0` failures, `0` errors, `0` skipped, `385.07s` test execution / `8m 43s` wall-clock; `154` correlated TSTs)**.
* **Offline Python & Safety Linters (`a3508e6b`)**: **`486/486` tests PASS** (`110.44s`) + **`56/56` Hoechstmass** tests PASS; `lint_cursor_safety`, `lint_lifecycle_safety`, `lint_safe_enum`, `check_secrets_hygiene` (`8,510` files), and `check_static_analysis_baselines` (`9516` Detekt / `238` Lint) all **PASS (`exit 0`)**.
* **Governance Parity**: `verify_requirement_governance.py --check-doc-headers --check-conflict-markers --check-sensor-javadocs` **PASS (`100%`)** (`50` DOC headers, `7,838` files, `67/67` `SensorType` constants, `1,236` REQs / `1,243` TSTs).
* **Physical Device Deploy (`REQ-TST-004`)**: `:app:installDebug` on physical **`Pixel 10`** (`66020DLCR002FL`) **BUILD SUCCESSFUL (`26s`)** + launcher intent smoke launch clean.
* **Scorecard (`docs/engineering/metrics/process_metrics.jsonl`)**:
  * **Velocity / Throughput**: `11/12` sprint items incl. `ATT-8740` (`91.7%`; `11/11` finished work tickets = **`21.0 SP`**).
  * **Overall Gate 1–5 FTPR**: **`94.5%`** across `73` sub-tasks (Gate 1 `90.9%`, Gate 2 `100.0%`, Gate 3 `90.9%`, Gate 4 `90.0%`, Gate 5 `100.0%`).
  * **Stage 6 Cold Review Discrimination**: **`54.5%` FTPR** (`6/11` first-pass, `5` bounced and healed cleanly: `ATT-8707`, `ATT-8705`, `ATT-8706`, `ATT-8719`, `ATT-8207`; `0` escaped defects).
  * **ASPICE SUP.9 Bug Metrics**: `6` Bugs resolved / awaiting human (**`100.0%` Resolution Rate**, `Crit/Maj/Min/Cos = 1/3/2/0`, `0` open `>7d`).
  * **SPC (PA 4.2)**: Baseline initialized cleanly under `process_version=2.0` (`ATT-8168`).
* **Exported Evidence**: `docs/engineering/gates/sprint_2026-11-17_03_nacht.md` (`105` records), `docs/engineering/reviews/sprint_2026-11-17_03_nacht.md` (`SKIPPED` overnight rollover), `docs/engineering/test_evidence/sprint_2026-11-17_03_Nacht.{md,json}` (`633/633` PASS, `--no-kover`), `docs/engineering/qa_audits/sprint_2026-11-17_03_nacht.md`, `docs/management/risk_register.md`.

---

## 3. Retrospective Analysis, Process Escapes & PO Security Action (`REQ-PRO-092.6`)

* **Value of `Cold Pro` Sparhebel & 3-Lane Throughput**: Scaling to 3 parallel `Flash` lanes (`Rule 7.2`) with `ATT-8207`'s pre-merge Stage 6 bounce isolation (`feature/<KEY>` intact until PASS) yielded `21.0 SP` without merge conflicts while Stage 6 caught real concurrency, sensor arbitration, and doc parity bugs (`ATT-8705`, `ATT-8706`, `ATT-8719`, `ATT-8207`).
* **Tooling Hardening Observations**:
  1. `DeviceManagerRrSourceTest.tearDown` intermittently threw `ConcurrentModificationException` in `MockK`'s `JvmConstructorMockFactory$ConstructorMock.dispose()` under `--max-workers=2` (`task-9605`), passing deterministically under `--max-workers=1` (`task-9673`) and standalone (`4/4`); follow-up candidate: restrict `mockkConstructor(Intent::class)` scope or serialize tearDown.
  2. Running `qa-audit` before `carryover-wip` sampled unmerged WIP `ATT-8717` (`Final Review (Agent)`). Pre-carryover false-positive CA tickets `ATT-8824..8826` were documented and cancelled to `Final Review (Human)` for PO cleanup; follow-up candidate in `jira_util.py`: exclude unmerged `Final Review (Agent)` carryover items when `qa-audit` audits a closing sprint.
* **PO Security Action Reminder (`ATT-8170` / `ATT-4004_walkthrough.md`)**: Rotate legacy Google Maps, Strava client secret, and Dropbox key still reachable in pre-`33e99b0a` git history outside agent credential scope.

---

## 4. QA Findings & Corrective Actions (`REQ-PRO-091.4`)

* **QA Audit (`docs/engineering/qa_audits/sprint_2026-11-17_03_nacht.md`, `--as agent2`, seed `80507`)**: Verdict **`COMPLIANT` (`2/2` sampled tickets: `ATT-8690`, `ATT-8168`)** across all 6 ASPICE process criteria (`Deliverables`, `Gate Sequence`, `4-Augen`, `Living Docs Parity`, `Commit Hygiene`, `PO Approval`) after returning `ATT-8206` and carrying over `ATT-8717`. Pre-carryover CA tickets `ATT-8824`, `ATT-8825`, `ATT-8826` (caused solely by unmerged `feature/ATT-8717` sampling prior to `carryover-wip`) await human closure from `Final Review (Human)`.
