---
name: schicht-orchestrator
description: Lightweight Master Orchestrator for continuous 3-Schicht autonomous delivery. Runs on Gemini 4 Neon (Medium) and spawns a fresh top-level conversation on Gemini 4 Neon (High) via agentapi for each Schicht (01_Früh, 02_Spät, 03_Nacht), enforcing deterministic sequential Schichtwechsel handoffs with zero race conditions and zero cross-Schicht context drift.
---

# Skill: schicht-orchestrator

## Overview
This skill defines the **Two-Tier Master $\leftrightarrow$ Per-`Schicht` Coordinator** architecture (`aspice_governance.md` `Rule 2.1`, `Rule 2.2`, `Rule 7.2`):
* **Tier 0 — Master Orchestrator (this agent)**: One long-lived, ultra-lightweight top-level conversation running on **Gemini 4 Neon (Medium)** (`gemini-4-neon-medium`). It holds **zero** ticket, diff, or code context. Its sole responsibility is enforcing the `05:00` / `15:00` / `22:00` local-time `Schichtwechsel` state machine (`IDLE` $\rightarrow$ `RUNNING` $\rightarrow$ `CLOSING` $\rightarrow$ `RUNNING`) and verifying clean handoffs across `develop` and Jira.
* **Tier 1 — Per-`Schicht` Coordinator (fresh top-level conversation per `Schicht`)**: Spawned by the Master via `agentapi new-conversation --model=gemini-4-neon-high` (`nestingDepth: 0`, visible as its own conversation in the sidebar titled `Schicht YYYY-MM-DD_0x_Schichtname`). Each `Schicht` Coordinator owns **strictly one `Schicht`** — executing Ceremony 1, 3-lane Phase 2 execution (`Flash` for routine lanes, `Model: "inherit"` = Gemini 4 Neon High for complex/graphical tasks and Stage 6 escalations), and Ceremony 2 closeout into `develop`, then notifying the Master and stopping cleanly.

### Why `gemini-4-neon-high` on Spawned `Schicht` Coordinators?
1. `invoke_subagent` only accepts generic tiers (`inherit`, `flash_lite`, `flash`, `pro`), not exact model IDs.
2. Pinning each `Schicht` Coordinator to `--model=gemini-4-neon-high` guarantees that every complex or graphical subagent spawned with `Model: "inherit"` executes on **Gemini 4 Neon (High)** (`Rule 7.1`, `Rule 7.2`), while routine lane subagents and routine Stage 6 Cold Reviewers continue using `Model: "flash"`.
3. Because each `Schicht` runs in a brand-new `nestingDepth: 0` conversation, its 3-lane Author/Reviewer pairs and Stage 6 Cold Reviewers stay at a flat `nestingDepth: 1` (no nested 2-level subagents) and every `Schicht` begins with a 100% fresh context window.

---

## Schicht Windows & Naming Convention (`Rule 2.2`)
Local time (`Europe/Berlin` / host local timezone):
* **`01_Früh`**: `05:00 – 15:00` (10h) $\rightarrow$ `YYYY-MM-DD_01_Früh`
* **`02_Spät`**: `15:00 – 22:00` (7h) $\rightarrow$ `YYYY-MM-DD_02_Spät`
* **`03_Nacht`**: `22:00 – 05:00` (7h) $\rightarrow$ `YYYY-MM-DD_03_Nacht` (uses the date of the `22:00` start)

---

## Master Orchestrator Startup & State Tracking

When the human Product Owner launches the Master Orchestrator conversation (on **Gemini 4 Neon (Medium)**):

1. **Read Governance & Identify Master ID**:
   * Read `.agents/rules/aspice_governance.md` (`Rule 1.1`).
   * Note this conversation's `CONVERSATION_ID` (from `<user_information>`).
   * Persist minimal runtime state in `<Artifact Directory Path>/scratch/schicht_master_state.json`:
     ```json
     {
       "master_conversation_id": "<MASTER_ID>",
       "state": "IDLE | RUNNING | CLOSING",
       "active_schicht_name": "2026-11-22_01_Früh",
       "active_sprint_id": 470,
       "active_conversation_id": "<SCHICHT_CONV_ID>",
       "started_at": "2026-11-22T05:00:05+01:00"
     }
     ```
2. **Schedule the 3 Daily `Schichtwechsel` Triggers**:
   * Register **one** recurring cron job via `schedule`:
     ```text
     schedule(
       CronExpression="0 5,15,22 * * *",
       ExactTiming=true,
       Prompt="SCHICHTWECHSEL_TICK: Check current local time, trigger closeout of the active Schicht (if any), wait for SCHICHT_CLOSED, and spawn the next Schicht Coordinator if work exists."
     )
     ```
3. **Initial Reconciliation (Never Spawn Duplicate Overlapping `Schichten`)**:
   * Inspect git (`git branch --list 'sprint/*'`) and Jira active sprints (`python3 tools/jira_util.py list-sprints` or `search`).
   * If an existing `sprint/<NAME>` is already active and being run by an existing conversation, record/adopt it or wait for the next `Schichtwechsel` — **NEVER** spawn a second `Schicht` Coordinator while an unmerged `sprint/*` branch is active.
   * If `develop` is clean, no `sprint/*` branch is active, and the current window has eligible work (active carryover WIP or `Vom PO Freigegeben` ID `447` non-`human-execution` tickets), immediately run **Phase C (Spawn Next `Schicht`)**.

---

## Deterministic Sequential `Schichtwechsel` State Machine

### Phase A — Closeout Trigger (`05:00` / `15:00` / `22:00`)
When `SCHICHTWECHSEL_TICK` fires and `state == "RUNNING"`:
1. Transition state to `CLOSING` in `schicht_master_state.json`.
2. Send a structured closeout command to the active `Schicht` Coordinator via `agentapi send-message`:
   ```bash
   agentapi send-message <SCHICHT_CONV_ID> \
     "CLOSE_SCHICHT schicht=<CLOSING_SCHICHT> master_id=<MASTER_ID> reason=schichtwechsel. Freeze merges onto sprint/<CLOSING_SCHICHT>, run manage_subagents(Action='kill_all'), execute Ceremony 2 (Lean Tier-2 Gate, best-effort Pixel 10 installDebug -PstampGitHash=true, metrics/gates/test-evidence/qa-audit/reviews/retro/post-weekly-rollup/risk-register, merge --no-ff to develop, baseline tag+manifest, close Jira sprint, archive Final Review (Human) to 453), then send SCHICHT_CLOSED back to <MASTER_ID> and STOP. Do NOT start the next Schicht."
   ```
3. Set a single 45-minute closeout watchdog timer:
   ```text
   schedule(
     DurationSeconds=2700,
     TimerCondition="<SCHICHT_CONV_ID>",
     Prompt="CLOSEOUT_WATCHDOG: Schicht <CLOSING_SCHICHT> (<SCHICHT_CONV_ID>) did not report SCHICHT_CLOSED within 45 min. Inspect git/Jira state and recover or escalate."
   )
   ```
4. End the turn (zero further tool calls) and wait reactively for `SCHICHT_CLOSED`.

### Phase B — Closing `Schicht` Coordinator Obligations
*(Executed inside the closing `Schicht` conversation upon receiving `CLOSE_SCHICHT`)*:
1. **Freeze Merges & Clean Up Subagents**: Immediately stop dispatching new tickets, freeze merges onto `sprint/<CLOSING_SCHICHT>`, and call `manage_subagents(Action="kill_all")` so no background lane or Stage 6 subagent can touch git, hold `/tmp/att_build_merge.lock`, or leave orphaned worktrees behind.
2. **Execute Ceremony 2 (`sprint-planner/SKILL.md` & `Rule 2.2` / `Rule 2.7`)**:
   * Run Lean Tier-2 Gate (`Rule 7.5`) and `python3 tools/verify_requirement_governance.py` (+ Python `tools/test_*.py` and safety linters).
   * Best-effort physical `Pixel 10` deploy (`ANDROID_SERIAL=<physical_serial> ./gradlew installDebug -PstampGitHash=true`, skipping cleanly if unplugged).
   * Execute `sprint-metrics --record --post-to-retro`, `export-gates`, `export-test-evidence --no-kover`, `--as agent2 qa-audit`, `record-review` (`--verdict "SKIPPED"` with autonomous `Schichtwechsel` rationale when non-interactive), write `docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md`, run `post-weekly-rollup --retro-file ...`, `ensure-weekly-reviews`, update `docs/management/risk_register.md` Sprint Review Log, and verify `--check-retro-allowlist` (`Rule 2.7`).
   * Merge `sprint/<CLOSING_SCHICHT>` `--no-ff` into `develop`, delete local `sprint/<CLOSING_SCHICHT>`, create immutable annotated tag & SUP.8 manifest via `tools/baseline_manager.py`, push `develop` and tag, close the Jira sprint, and run `archive-po-acceptance` (moving completed `Final Review (Human)` items to `Bereit für PO Abnahme` ID `453`).
3. **Report Completion & Terminate**:
   ```bash
   agentapi send-message <MASTER_ID> \
     "SCHICHT_CLOSED schicht=<CLOSING_SCHICHT> develop_sha=$(git rev-parse --short develop) sprint_closed=true"
   ```
   Cancel any remaining local timers (`manage_task`) and **stop immediately**. A `Schicht` Coordinator **SHALL NEVER** branch or start the next `Schicht`.

### Phase C — Post-Close Verification & Next-`Schicht` Spawn (Master Orchestrator)
When the Master receives `SCHICHT_CLOSED` (or starts from `IDLE` at `Schichtwechsel`):
1. **Verify Clean Boundary Post-Conditions**:
   * Confirm `git branch --list 'sprint/*'` has no unmerged `sprint/<CLOSING_SCHICHT>` branch remaining and `git rev-parse --short develop` matches the reported SHA (with clean working tree on `develop`).
   * If post-conditions fail (e.g., `sprint/<CLOSING_SCHICHT>` still exists unmerged), do **NOT** spawn the next `Schicht`; send a corrective message back to `<SCHICHT_CONV_ID>` or escalate to the human PO.
2. **Check Eligible Work Queue (No Empty `Schicht` Conversations)**:
   * Query active carryover WIP and `Vom PO Freigegeben` (ID `447`, excluding `human-execution` tickets).
   * If **both** active WIP and eligible tickets in `447` are empty: set `state = "IDLE"`, log cleanly, and wait until the next `05:00` / `15:00` / `22:00` tick (or human ping).
3. **Pre-Spawn Neon Model Verification (Anti-Fallback Guard)**:
   * Call `swarm(action="models", all=true)` and confirm `gemini-4-neon-high` is listed and has quota remaining (`> 0%`).
   * If `gemini-4-neon-high` is temporarily unavailable or out of quota, pause/escalate to the PO (or fall back only if explicitly authorized by the PO) rather than silently running on Flash.
4. **Spawn Fresh Top-Level `Schicht` Coordinator**:
   ```bash
   agentapi new-conversation \
     --model=gemini-4-neon-high \
     --title="Schicht <NEW_SCHICHT>" \
     -- "<BOOTSTRAP_PROMPT>"
   ```
   * Parse the returned JSON `response.newConversation.conversationId` as `<NEW_CONV_ID>`.
5. **Post-Spawn Metadata Sanity Check**:
   * Run `agentapi get-conversation-metadata <NEW_CONV_ID>` to confirm:
     * `workspaceUris` contains `file:///home/rainer/AndroidStudioProjects/aTrainingTracker`.
     * `nestingDepth == 0` and `hideFromConversationList == false`.
     * `planModel` did not silently fall back to the Flash placeholder.
   * Persist `state = "RUNNING"`, `active_schicht_name = "<NEW_SCHICHT>"`, `active_conversation_id = "<NEW_CONV_ID>"` in `schicht_master_state.json`, and end the turn cleanly.

---

## Recovery & Edge Cases
* **Early Drain (`SCHICHT_IDLE`)**: If a `Schicht` Coordinator completes all tickets in its sprint and both WIP and `447` are drained before the window ends, it may send `SCHICHT_IDLE schicht=<NAME> reason=queue_empty` to `<MASTER_ID>` and remain idle waiting for mid-sprint human additions (`Rule 2.5`) until `CLOSE_SCHICHT` arrives at the scheduled `Schichtwechsel`.
* **Closeout Watchdog Timeout (`CLOSEOUT_WATCHDOG`)**: If 45 minutes pass after `CLOSE_SCHICHT` without `SCHICHT_CLOSED`:
  1. Check `git branch --list 'sprint/*'`, `git log -n 3 --oneline develop`, and active Jira sprints to see whether Ceremony 2 actually finished and only the callback message was lost.
  2. If already merged and closed cleanly, proceed to **Phase C**.
  3. If still open/stalled, send one `CLOSE_SCHICHT_RETRY` to `<SCHICHT_CONV_ID>` with a 20-minute watchdog; if it still does not respond, halt rollover and notify the human PO in the Master chat (never spawn a concurrent overlapping `Schicht`).
* **Post-Compaction Master Resume (`Rule 2.6`)**: Read `<Artifact Directory Path>/scratch/schicht_master_state.json`, cross-check against `git branch --list 'sprint/*'` and Jira active sprints, verify the `0 5,15,22 * * *` cron task via `manage_task(Action="list")` (re-registering if missing), and resume immediately.

---

## Standard Per-`Schicht` Bootstrap Prompt Template
When calling `agentapi new-conversation --model=gemini-4-neon-high --title="Schicht <NEW_SCHICHT>" -- "<PROMPT>"`, pass a concise, self-contained prompt containing:
1. **Identity & Scope**: `You are the dedicated Schicht Coordinator for Schicht <NEW_SCHICHT> (window <WINDOW> local time). Master Orchestrator Conversation ID: <MASTER_ID>. You own STRICTLY this single Schicht.`
2. **Mandatory Context Refresh (`Rule 1.1`)**: `Read .agents/rules/aspice_governance.md and .agents/skills/sprint-planner/SKILL.md immediately.`
3. **Schicht Startup**: `Branch sprint/<NEW_SCHICHT> off updated develop, create/start the Jira Schicht if needed, run carryover-wip + pack-sprint --lanes 3 --max-sp-per-lane 13 from Vom PO Freigegeben (447) ORDER BY rank ASC, run ensure-retro / ensure-weekly-retro / ensure-weekly-reviews, record Ceremony 1 Risk Review (and Pixel 10 heap hygiene check if attached per Rule 7.6), and execute Phase 2 across 3 parallel lanes (Model: 'flash' for routine tickets; Model: 'inherit' [= Gemini 4 Neon High] strictly for complex or graphical tasks per Rule 7.2).`
4. **Hard Single-Schicht Closeout Boundary**: `Do NOT start the next Schicht. Wait for CLOSE_SCHICHT from Master <MASTER_ID> (at <END_TIME> local time). Upon receiving CLOSE_SCHICHT: freeze merges, call manage_subagents(Action='kill_all'), run Ceremony 2 (Lean Tier-2 Gate, best-effort Pixel 10 deploy with -PstampGitHash=true, metrics/gates/test-evidence/qa-audit/reviews/retro/post-weekly-rollup/risk-register adhering strictly to Rule 2.7 allowlist), merge sprint/<NEW_SCHICHT> --no-ff into develop, tag & manifest via baseline_manager.py, close Jira sprint & archive Final Review (Human) to 453, notify Master via: agentapi send-message <MASTER_ID> "SCHICHT_CLOSED schicht=<NEW_SCHICHT> develop_sha=$(git rev-parse --short develop) sprint_closed=true", cancel local timers, and STOP.`
