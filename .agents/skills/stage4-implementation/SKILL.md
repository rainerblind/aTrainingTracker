---
name: stage4-implementation
description: Executes Stage 4 (Software Construction & Implementation) of the agile ASPICE lifecycle. Verifies Gate 3 sign-off, executes atomic implementation steps, writes production code and targeted unit tests, and maintains documentation integrity.
---

# Skill: stage4-implementation

## Overview
This skill guides the agent through **Stage 4 (Implementation / Software Construction)**. It covers modifying production Android code (`app/src/main/...`) and unit tests (`app/src/test/...`) in strict accordance with the approved Stage 3 Implementation Plan.

## Subagent Model Pairing ("Zipper Principle" & Persistent Ping-Pong — Core Rule 7)
Reuse the ticket's two persistent `TypeName: "self"` subagents via `send_message`:
- **Doing (`--as agent1`)**: **`subagent_flash`** *(Standard Backend/Non-Visual)* / **`subagent_pro`** *(High-Risk & All Visual/UI)* (`send_message` to the ticket's `flash` subagent — which just audited the Stage 3 plan in Gate 3! — or `subagent_pro` for **all Graphical/UI/Compose Screens, Custom Sliders/Tracks, Canvas Charts, Vector Graphics / Icon Design / Visual Asset Reconstruction**, `HIGH`-risk concurrency/DB/service lifecycle tickets, **Binary Protocols & File Parsers [BLE GATT, ANT+, FIT, TCX/GPX]**, **Physiological DSP / Dynamical Math Engines**, and **Store Presence / Video Generation** tickets per `Rules 71, 81 & 84` — `ATT-5313`, `ATT-6289`, `ATT-7525`) — Executes the Stage 3 file-and-method blueprint, unit tests, 9-locale XML updates, and (for all Visual/UI/Icon tickets) renders a PNG screenshot and inspects it via `read_file` against the 4-point Visual Self-Inspection Checklist before submitting Stage 4.
- **Gate 4 Review (`--as agent2`)**: **`subagent_pro`** *(Standard)* / **`subagent_flash`** *(High-Risk)* (`send_message` to the ticket's `pro` subagent — which authored the Stage 3 plan! — or `subagent_flash` when `pro` authored a High-Risk implementation) — Conducts an **adversarial code review** of the `git diff` (actively probing byte/bitfield layouts, SQLite N+1 queries, subtle race conditions, Compose recomposition traps, resource leaks, and caller reachability independently of its own Stage 3 plan rather than rubber-stamping plan conformance), followed by executing `python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS ...`.

## Key Responsibilities (`subagent_flash` / `subagent_pro` as `agent1`)
1. **Pre-Implementation Gate Verification (Core Rule 4 — `REQ-PRO-016`)**:
   - Before modifying any source files in `app/src/...`, verify that the Stage 3 Plan subtask is in status `Erledigt`:
     ```bash
     python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>
     ```
   - If exit code is not 0, halt immediately.
2. **Isolated In-Place Code & Document Review Workflow (`.reviews/` + Emacs)**:
   - For concurrent, conflict-free human review of code (`.kt`, `.java`, `.py`, `.xml`) or documents (`.md`) while agents compile or switch branches:
     - Stage target files: `python3 tools/review_tool.py stage <file...>` or `python3 tools/review_tool.py stage-ticket <TICKET_KEY>`.
     - Human edits lines in Emacs or inserts inline review directives (`>> ...` / `// >> ...`).
     - Harvest diffs and synchronize: `python3 tools/review_tool.py diff` and `python3 tools/review_tool.py apply`.
3. **Software Construction, Universal Image Audit & Code-Grounding (`ATT-5431`)**:
   - Execute the planned atomic steps sequentially.
   - Follow clean architecture and Kotlin/Java best practices (`docs/coding_standards.md` and `docs/design_guidelines.md`).
   - Add comprehensive internal documentation (KDoc/JavaDoc) for all new or modified public methods and classes (`REQ-PRO-011`).
   - For string additions: ensure entries exist across all 9 supported language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with consistent format specifiers. String additions may be partitioned into domain-scoped `strings_<domain>.xml` files (`REQ-PRO-041.3`) to prevent git merge conflicts during parallel sprint execution.
   - **Universal `read_file` Image Inspection & Mandatory `pro` 4-Point Visual Self-Inspection (`Rules 71 & 84`, `ATT-5431`, `ATT-7525`)**: Visually inspect every created, modified, or referenced image asset (`.png`, `.jpg`, `.svg` across `docs/guide/img/*`, `docs/store_presence/*`, drawables) via `read_file`. For every Graphical/UI/Compose/Slider/Icon ticket, `subagent_pro` MUST render/capture a PNG preview or screenshot during Stage 4, inspect it with `read_file`, and verify: (1) *Orientation & Geometry*, (2) *Color Mapping & M3 Track/Stop-Dot Artifacts*, (3) *Typography & Multi-Line Wrapping / Ellipsizing*, and (4) *Touch Ergonomics & Finger Occlusion*.
   - **Documentation Code-Grounding (`ATT-5431`)**: Every statement in guides or docs must be verified against actual code (`AndroidManifest.xml`, Compose screens, Strings, Repositories).
4. **Targeted Unit Testing**:
   - Run exclusively targeted unit tests for modified packages/classes (5–15s runtime) to verify logic rapidly during development. When running under parallel sprint execution (Core Rule 9 / `REQ-PRO-041.2`), wrap Gradle test executions with `python3 tools/parallel_sprint_lock.py`:
     ```bash
     python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*"
     ```
   - Defer full test suite execution (`./gradlew testDebugUnitTest`) to Stage 5.
5. **Single-Turn Stage Submission (`submit-stage`)**:
   - Submit Stage 4 summary and targeted test results in a single composite CLI call (creates/updates `[Implementation]` sub-task and moves it to `In Überprüfung`). **Never** create markdown files or folders inside `docs/engineering/` (such as `summaries/` or `implementation/`) for Stage 4 (Rule 67 — `ATT-4936`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 4 "<Implementation Summary & Targeted Test Results>"
     ```
   - **One Commit per Ticket (Core Rule 8)**: Defer committing until Stage 5 verification completes so the entire ticket is committed as a single atomic commit on `sprint/<SPRINT_NAME>`.

## Gate 4 Independent Review Checklist (`subagent_pro` / `subagent_flash` as `agent2`)
Message the ticket's reviewing persistent subagent (`send_message` to `subagent_pro` for Standard tickets, `subagent_flash` for High-Risk tickets authored by `pro`) to audit the `git diff` against this checklist and record the verdict via `--as agent2 gate-verdict`:
1. **Diff Scrutiny**: Inspect `git diff` against the approved Stage 3 implementation plan. Confirm zero unapproved files or unintended modifications.
2. **Side-Effect Audit**: Verify adjacent callers, interfaces, and non-target metrics/features were NOT altered or broken.
3. **Acceptance-Criteria & Robustness Audit (Rule 66 — `ATT-4864`)**: Plan conformance alone is not sufficient (Sprint 2026-44.1: all gates passed, yet the review found AC gaps and edge-case defects in ATT-4840, ATT-4841, ATT-4833, ATT-4863). Include in the audit report:
   - **AC-by-AC table**: every acceptance criterion of the parent ticket with verdict `met` / `partially met` / `not met` and evidence (file:line or test). Any `partially` / `not met` → `REVISION`, or an explicit, justified deviation documented in the ticket.
   - **Upgrade path**: for DB schema, migrations, persisted settings or removed settings — what exactly changes for an *existing* user after the update (previous preferences carried over? visible behaviour change?).
   - **Missing / invalid input**: for state machines, sensors and resolvers — behaviour with no source, `null`, stale data, `UNKNOWN` sport type, empty lists. A state must never become a dead end (e.g. stuck in `AUTO_PAUSED`) or render empty UI.
   - **Shared-component blast radius**: when a shared class/default or composable layout constraint (`.fillMaxHeight()`, `.weight(1f)`) is changed (e.g. `SensorFieldView`, `ThermalColorLut`, `PolylineHeatmapTileProvider`), list all other consumers (`grep -rn`) and confirm they work in both bounded (`IntrinsicSize.Min`) and scrollable (`verticalScroll`) containers (`ATT-5071`).
   - **Broadcast listener ordering & Java-to-Kotlin nullability**: when modifying a broadcast receiver (`RESET_ACCUMULATORS_INTENT`, `STOP_TRACKING`) or `reset()` method, verify the execution order across all registered listeners (`ClockDevice` + `FilterManager`) and ensure Java formatters/getters never return unannotated `null` to Kotlin non-null `String` callers (`ATT-5080`).
   - **Startup self-healing**: when adding recovery for records stuck in a terminal DB state (`FINISHED_FAILED`), verify it triggers automatically on app startup in addition to manual settings dialogs (`ATT-5081`).
   - **Compose state hygiene**: no `MutableState` writes inside `remember {}` calculations or during composition (`docs/coding_standards.md` Sec 1.10).
   - **Production Caller & Reachability `grep` Audit (`Rule 81` — `ATT-6289`)**: For every newly created `@Composable` screen/section/dialog, `SensorType` enum, or background importer/backfill class, run `grep -rn "<Symbol>" app/src/main/` and verify live production reachability (never only `@Preview` or unit tests; new `SensorType`s MUST be registered in `ActivityType.getSensorTypeArray()`). Reject (`REVISION`) any Repository exposing public/internal `get*DatabaseManager()`, `get*DbHelper()`, or `Cursor` getters (`docs/coding_standards.md` Sec 1.12 & Sec 2.5).
   - **Architecture & System Spec Code-Grounding (`Rule 81` — `ATT-6179`, `ATT-6289`)**: For documentation/architecture/system tickets (`docs/architecture*.md`, `docs/system/*.md`), programmatically verify that every backticked `.kt`/`.java`/`.xml` filename, class/DTO symbol, method signature, and manifest attribute exists in `app/src/main/` and that `SYS.2 <-> SYS.3` requirement IDs satisfy exact bidirectional set equality (`declared == allocated`).
4. **Quality, Compliance & Universal Image Certification (`ATT-5431`)**:
   - Verify class/method headers comply with self-documenting standards (KDoc/JavaDoc).
   - Verify 9-language localization parity across all user-facing strings (`EN, DE, ES, FR, IT, JA, NL, PL, PT`).
   - **Universal `read_file` Image Certification**: For all created, modified, or markdown-referenced image assets (`.png`, `.jpg`, `.svg`), `agent2` MUST inspect them with `read_file` and explicitly certify in the audit report that the visual image content matches accompanying documentation and real UI 1:1.
5. **Gate Verdict (`gate-verdict`)**:
   - Record the verdict in a single composite CLI call (which closes the sub-task to `Erledigt`, advances the parent ticket to `Test`, and automatically injects `stage5-verification/SKILL.md` into stdout):
     ```bash
     python3 tools/jira_util.py --as agent2 gate-verdict <IMPL_SUBTASK_KEY> PASS "<Audit Report>"
     ```
     *(Or `REVISION` if changes are required.)*
