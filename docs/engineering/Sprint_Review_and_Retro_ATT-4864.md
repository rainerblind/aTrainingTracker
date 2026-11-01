# Sprint Review & Retrospective — Sprint 2026-44.1 (`ATT-4864`)

* **Sprint**: `2026-44.1` (`id = 391`)
* **Sprint Branch**: `sprint/2026-44.1` $\rightarrow$ `develop`
* **Release Target**: `V4.9.41`
* **Date**: 2026-11-01

---

## 1. Sprint Review Summary (11 / 11 Tickets Accepted)

| Rank | Ticket | Summary | Commit | Verdict | Follow-ups |
| :---: | :--- | :--- | :---: | :--- | :--- |
| 1 | `ATT-4843` | Analyse Georgenfelder Hochmoor (distance plausibility gate, canonical summary sensor) | `40d429c5` | Accepted | — |
| 2 | `ATT-4863` | OOM at `Parcel.obtain`: 1-Hz allocation / recomposition hotspots | `06195f2a` | Accepted | `ATT-4923` |
| 3 | `ATT-4836` | Own section for auto pause in settings | `941137f5` | Accepted | `ATT-4924` |
| 4 | `ATT-4839` | Different times for entering / leaving auto pause | `5ee5653f` | Accepted | `ATT-4924` (label) |
| 5 | `ATT-4840` | Optionally start in auto paused | `e64ff057` | Accepted | `ATT-4925` |
| 6 | `ATT-4856` | Make clear when paused | `db77640e` | Accepted | — |
| 7 | `ATT-4838` | Config tracking tabs open the first tab | `f89644b1` | Accepted | — |
| 8 | `ATT-4841` | Per-field Value / Bar flags | `0ea00b1d` | Accepted | `ATT-4927` |
| 9 | `ATT-4860` | Zone bar: highlight current zone | `9d5fd7ff` | Accepted | `ATT-4928` |
| 10 | `ATT-4833` | Periods screen polish (heatmap, mini-maps) | `2bea16b7` | Accepted (human not yet satisfied) | `ATT-4929`–`ATT-4935` |
| 11 | `ATT-4862` | Lieblingsstrecke created although 'Offen' selected | `3c866d33` | Accepted | — |

Additional commits: `a3eba166` (docs: Target Release `V4.9.44` $\rightarrow$ `V4.9.41`), `6babf39e` (test: 4 Tier 2 failures).

## 2. Tier 2 Sprint Integration Gate

* Review start (`task-9`): **4 / 4017 failed** — stale tests from ATT-4836 (`NavigationDrawerStateTest`), ATT-4856 (`SensorGridScreenLayoutTest`), ATT-4841 (`TrackingViewsDatabaseManagerTest`, missing `sqlite_master` stub) and a cross-test leak from ATT-4840 (`TrainingApplicationTrackingTest` started a real `TuningPreferencesDataStore` on `Dispatchers.Default` with a mocked Context $\rightarrow$ `UncaughtExceptionsBeforeTest` in `BatterySaverControllerTest`).
* After `6babf39e` (`task-266`): **100% PASSED**, governance clean.

## 3. Retrospective Root Causes & Countermeasures (`Rule 66`)

1. **Phase 2 stalled after Stage 1 of the first ticket** (*"Warum wurde das erste Ticket nicht komplett selbstständig abgearbeitet?"*)
   * *Observation*: The Ceremony 1 conversation (`cd079772`) acted as Coordinator. The Stage 1 subagent (`cbd8944c`) submitted Stage 1 and reported via `send_message`; the report stayed undelivered and the Coordinator was not resumed. The human then typed into the **subagent's** tab, which took over as de-facto Coordinator and still ended its first turn with a status report only, so a second nudge was needed (00:38). Afterwards the sprint ran autonomously until 04:24.
   * *Countermeasure*: one `schedule` watchdog per dispatch (`TimerCondition` = subagent), no status-only Coordinator turns while tickets remain, subagents never take over the Coordinator role (Core Rule 7, sprint-planner Phase 2).
2. **Full-suite failures surfaced only in the human review** — Tier 2 gate moved to the very end of Phase 2 with fixes before Ceremony 2; walkthroughs must name the test scope (Core Rule 7, sprint-planner, stage5, coding standards 5.2). Test-isolation rule for DataStore/`Dispatchers.Default` with mocked Contexts (coding standards 5.5).
3. **Invented version `V4.9.44`** in 15 stage deliverables — `Target Release` must come from `jira_util.py versions` (`ACTIVE/UNRELEASED`), verified at Gate 5; `FixVersion` still set on acceptance (Core Rule 8, stage5).
4. **Gates passed substantive defects** (no-speed-source dead end ATT-4840, upgrade forcing bars ATT-4841, heatmap per-segment accumulation ATT-4833, partial ACs ATT-4863/4833) — Gate 4 audit now requires an AC-by-AC table, upgrade-path, missing/invalid-input and shared-component blast-radius checks (stage4; coding standards 5.6).
5. **Byte-identical walkthrough screenshots** (`ATT-4840_autopause_on/off.png`) — Gate 5 evidence integrity check via `md5sum` (stage5).
6. **Design guideline produced bad UI** (collapsing header on a short settings form, ATT-4836) — collapsing headers only for long list screens; settings/forms use a fixed header (design guidelines 5.1; code change in `ATT-4924`).
7. **Same Compose anti-pattern twice** (state writes inside `remember`, removed in ATT-4863, re-added in ATT-4838) — coding standards 1.10 + Gate 4 check.

## 4. What Went Well

* 11 tickets realized autonomously overnight with clean one-commit-per-ticket history.
* Strong forensic root-cause analysis (ATT-4843 with on-device DB evidence).
* Code review during Ceremony 2 caught real defects before release; all were captured as follow-up tickets (`ATT-4923`–`ATT-4935`).
