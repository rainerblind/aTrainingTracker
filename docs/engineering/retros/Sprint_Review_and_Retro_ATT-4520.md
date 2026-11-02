# Sprint Review & Retrospective: Sprint 2026-43.5 (`ATT-4520`)

**Date**: 2026-10-21
**Sprint**: `2026-43.5` (`id 385`)
**Integration Branch**: `sprint/2026-43.5` $\rightarrow$ `develop`
**Target Release Version**: `V4.9.40`

---

## 1. Sprint Summary & Scope Outcome

During Sprint `2026-43.5`, 14 tickets were executed autonomously across the full 5-stage ASPICE lifecycle (`Analysis` $\rightarrow$ `Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test`), verified against targeted and clean-room unit tests and 9-locale translation parity checks, deployed to the connected Pixel 10 (`./gradlew installDebug`), and integrated onto `sprint/2026-43.5` as strictly one atomic squashed commit per ticket (Core Rule 8):

| # | Ticket Key | Summary | Commit on `sprint/2026-43.5` | Status / Outcome | Fix Version |
| :---: | :--- | :--- | :---: | :---: | :---: |
| 1 | **`ATT-4518`** | Unify manual cloud backup button and automated backup subtitle to dynamically reflect enabled cloud providers (Dropbox & Google Drive) | `01b13096` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 2 | **`ATT-4513`** | Use a lighter checked track color for subordinate `'Show in Workout Summary'` switches in Light Mode | `96a7d396` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 3 | **`ATT-4521`** | Update default Cycling FTP (`240 W`) and Heart Rate / Power zone boundaries to realistic, calculator-aligned baselines | `7051a307` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 4 | **`ATT-4003`** | `[High]` Remove blocking per-workout I/O from mapper and DB queries from composition | `6073cf43` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 5 | **`ATT-4002`** | `[High]` WorkoutRepository loses concurrent updates and mutates state with side effects | `19b04b1e` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 6 | **`ATT-4543`** | Update default Cycling FTP to `270 W` and align default `PWR_BIKE` Coggan 7-zone cutoffs (`149, 203, 243, 284, 324, 405 W`) | `3793e424` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 7 | **`ATT-4008`** | `[Medium]` Add missing database indexes and remove N+1 queries in routes and clustering | `ea6de920` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 8 | **`ATT-4015`** | `[Medium]` Route progress tracking projects in raw degrees and jumps on out-and-back routes | `0da4472d` | **Returned to `Analysis`** *(wire `lastSegmentIndex` into callers)* | — |
| 9 | **`ATT-4016`** | `[Medium]` Period aggregation week and day boundaries wrong on DST transitions | `3819ba30` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 10 | **`ATT-4017`** | `[Medium]` Missing altitude is stored as `0 m` and corrupts profiles and extrema | `58daed58` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 11 | **`ATT-4018`** | `[Medium]` Fix aftermath loader races, process-death restore and stale gesture data | `145ee346` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 12 | **`ATT-4019`** | `[Medium]` Reduce redundant period rebuilds and triple tracking-finished handling | `0736bd5a` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 13 | **`ATT-4554`** | Remove obsolete BLE Pedal Power Balance correction checkboxes and honor BLE/ANT+ Left/Right reference flags automatically | `49fb3a9b` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 14 | **`ATT-4020`** | `[Medium]` Fix MainActivity service binding leaks, unscoped coroutines and main-thread DB access | `1be8b174` | **Accepted (`Final Review (Human)` $\rightarrow$ `Erledigt`)** | `V4.9.40` |

*Remaining sprint backlog items (`ATT-4592`, `ATT-4015`, `ATT-4021` .. `ATT-4033`) carry over to the next sprint.*

---

## 2. Retrospective & Process Hardening (`Rule 61`)

### 2.1 Root-Cause Synthesis of In-Sprint Anomalies
1. **Cold-Start Overhead Across Same-Epic Tickets (`ATT-4520` Description)**:
   * *Observation*: Spawning brand-new `pro` and `flash` subagents per ticket incurs a 3–5 minute cold-start tax (re-reading skills, `docs/requirements.md`, `docs/tests.md`, and subsystem source files) even when multiple tickets in the sprint belong to the same Parent Epic.
   * *Countermeasure (`Rule 61.1`)*: Maintain **Epic-Scoped Persistent `(subagent_pro, subagent_flash)` Pairs** in the Coordinator's active subagent registry keyed by Parent Epic (`epic_agents[epic_key] = (pro_id, flash_id)`). Subsequent tickets in the same Epic reuse that warm pair via `send_message`.
2. **Connected User Device Data Safety (`ATT-4521` / `ATT-4543` Incident)**:
   * *Observation*: During review of default FTP & zone baselines, an agent ran `adb uninstall com.atrainingtracker.debug` assuming the debug build was disposable, wiping live workouts and settings on the user's primary test phone (and exposing `ATT-4592` during restore).
   * *Countermeasure (`Rule 61.2`)*: **Strict Prohibition on Unconfirmed App Uninstall / Data Wipe (`adb uninstall` / `pm clear`)**. Never uninstall or clear app data on `com.atrainingtracker` or `com.atrainingtracker.debug` without explicit human confirmation naming the exact package and first verifying/pulling a complete `.attbackup` archive to the host machine.
3. **Premature Next-Stage `send_message` Queueing & `--rerun-tasks` Gradle Tax (`ATT-4002`, `ATT-4008`, `ATT-4015`, `ATT-4016`, `ATT-4017`, `ATT-4554`)**:
   * *Observation*: Sending a Gate Review `send_message` to the second subagent in the same turn as the Doing `send_message` (before the Doing subagent created its Jira subtask) caused both subagents to run concurrently and fail or poll for missing subtask keys. Additionally, passing `--rerun-tasks` to `./gradlew testDebugUnitTest` forced a 4m 15s full recompilation instead of a ~10s incremental Tier 1 test run.
   * *Countermeasure (`Rule 61.3`)*: Strictly send **at most one subagent message per turn** (wait for the active subagent's `<SYSTEM_MESSAGE>` containing the newly created Jira subtask key before messaging the reviewer subagent), never use `schedule` or `manage_subagents(Action="list")` polling loops, and **never pass `--rerun-tasks`** to Gradle unit test runs.
4. **Caller-Site Wiring Verification for Optional Default Parameters (`ATT-4015` Bounce)**:
   * *Observation*: `ATT-4015` added `lastSegmentIndex: Int? = null` with windowed search to `PolylineProgressTracker.projectLocation`, but left all 3 production callers using the default `null` argument.
   * *Countermeasure (`Rule 61.4`)*: Whenever a utility or repository method adds an optional parameter with a default fallback (`param: T? = null`), Stage 3 (`Implementation Plan`) and Gate 4 (`Implementation Review`) must explicitly verify that all intended production callers pass the parameter rather than silently falling back to legacy default behavior.
